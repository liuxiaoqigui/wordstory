package com.wordstory.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.coroutineContext

internal const val MICROSOFT_VOICE = "en-US-JennyNeural"

/** 所有调用在主线程；预取和点击共享请求，停止一次播放不会打断预取。 */
internal class AudioRequests(private val scope: CoroutineScope, private val load: suspend (String) -> File) {
    private val pending = mutableMapOf<String, Deferred<File>>()

    fun get(word: String): Deferred<File> {
        pending[word]?.let { return it }
        lateinit var request: Deferred<File>
        request = scope.async(start = CoroutineStart.LAZY) {
            try { load(word) }
            finally { if (pending[word] === request) pending.remove(word) }
        }
        pending[word] = request
        return request
    }

    fun cancel() {
        val requests = pending.values.toList()
        pending.clear()
        requests.forEach { it.cancel() }
    }
}

internal fun microsoftSsml(word: String): String {
    require(word.isNotBlank() && word.length <= 200 && word.none { it.code < 32 })
    val escaped = word.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
    return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
        "<voice name='Microsoft Server Speech Text to Speech Voice (en-US, JennyNeural)'>" +
        "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>$escaped</prosody></voice></speak>"
}

internal fun microsoftAudioChunk(frame: ByteArray): ByteArray? {
    require(frame.size >= 2) { "Missing audio frame header" }
    val length = ((frame[0].toInt() and 255) shl 8) or (frame[1].toInt() and 255)
    require(length <= frame.size - 2) { "Invalid audio frame header" }
    val headers = frame.copyOfRange(2, length + 2).toString(Charsets.UTF_8).split("\r\n")
    require("Path:audio" in headers) { "Unexpected audio frame" }
    val data = frame.copyOfRange(length + 2, frame.size)
    if (data.isEmpty()) return null
    require("Content-Type:audio/mpeg" in headers) { "Unexpected audio format" }
    return data
}

private class MicrosoftAudioClient {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).build()

    suspend fun fetch(word: String): ByteArray = withTimeout(20000) {
        val ssml = microsoftSsml(word)
        suspendCancellableCoroutine { continuation ->
            val now = Instant.now()
            val id = UUID.randomUUID().toString().replace("-", "")
            val url = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=${EdgeToken.CLIENT}&ConnectionId=$id" +
                "&Sec-MS-GEC=${EdgeToken.generate(now.epochSecond)}&Sec-MS-GEC-Version=2-143.0.3650.75"
            val request = Request.Builder().url(url)
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                .header("Cookie", "muid=${UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT)};")
                .build()
            val timestamp = DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
                .withZone(ZoneOffset.UTC).format(now)
            val audio = ByteArrayOutputStream()
            val socket = client.newWebSocket(request, object : WebSocketListener() {
                private fun fail(socket: WebSocket, error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                    socket.cancel()
                }

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\"," +
                        "\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n")
                    webSocket.send("X-RequestId:$id\r\nContent-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${timestamp}Z\r\nPath:ssml\r\n\r\n$ssml")
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    try {
                        require(bytes.size <= 512 * 1024) { "Audio frame too large" }
                        microsoftAudioChunk(bytes.toByteArray())?.let {
                            require(audio.size() + it.size <= 512 * 1024) { "Audio response too large" }
                            audio.write(it)
                        }
                    } catch (e: Exception) { fail(webSocket, e) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if ("Path:turn.end" in text.substringBefore("\r\n\r\n").split("\r\n")) {
                        if (audio.size() == 0) fail(webSocket, IOException("Microsoft returned no audio"))
                        else {
                            if (continuation.isActive) continuation.resume(audio.toByteArray())
                            webSocket.close(1000, null)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = fail(webSocket, t)
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    fail(webSocket, IOException("Speech connection closed before completion"))
                }
            })
            continuation.invokeOnCancellation { socket.cancel() }
        }
    }

    fun shutdown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

/** 微软 Jenny 正常语速；缓存命中时直接播放，联网失败时使用手机 TTS。 */
class WordSpeech(private val context: Context) {
    private val offline = OfflineWordSpeech(context)
    private val network = MicrosoftAudioClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = AudioRequests(scope, ::cachedAudio)
    private val downloadSlots = Semaphore(2)
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var requested: String? = null
    private var closed = false
    private var warmWords = emptyList<String>()
    var preparingWord by mutableStateOf<String?>(null); private set

    fun prefetch(words: List<String>) {
        if (closed) return
        warmWords = words.take(3).filter { it.isNotBlank() }.distinct()
        warmWords.forEach { requests.get(it).start() }
    }

    fun resumePrefetch() = prefetch(warmWords)

    fun speak(word: String) {
        if (closed || word.isBlank() || (requested == word && job?.isActive == true)) return
        stopPlayback()
        requested = word
        preparingWord = word
        job = scope.launch {
            try {
                val audio = requests.get(word).await()
                val current = MediaPlayer()
                player = current
                current.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build())
                current.setDataSource(audio.path)
                current.setOnPreparedListener {
                    if (player === it) { preparingWord = null; it.start() }
                }
                current.setOnCompletionListener { if (player === it) { it.release(); player = null } }
                current.setOnErrorListener { failed, _, _ ->
                    if (player === failed) {
                        failed.release(); player = null; audio.delete(); fallback(word)
                    }
                    true
                }
                current.prepareAsync()
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                Log.w("WordSpeech", "Microsoft pronunciation unavailable", e)
                player?.release(); player = null
                fallback(word)
            }
        }
    }

    private suspend fun cachedAudio(word: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest("$MICROSOFT_VOICE|normal-v1|$word".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = File(context.cacheDir, "word-audio/$key.mp3")
        if (withContext(Dispatchers.IO) { file.isFile && file.length() > 0 }) return file
        val bytes = downloadSlots.withPermit { network.fetch(word) }
        withContext(Dispatchers.IO) {
            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            val temp = File(file.parentFile, "$key.${UUID.randomUUID()}.tmp")
            try {
                temp.writeBytes(bytes)
                check(temp.renameTo(file)) { "Could not save pronunciation" }
            } finally { temp.delete() }
        }
        return file
    }

    private fun fallback(word: String) {
        preparingWord = null
        Toast.makeText(context, "微软发音暂时不可用，改用系统发音", Toast.LENGTH_LONG).show()
        offline.speak(word)
    }

    private fun stopPlayback() {
        preparingWord = null
        requested = null
        job?.cancel(); job = null
        player?.release(); player = null
        offline.stop()
    }

    fun stop() {
        stopPlayback()
        requests.cancel()
    }

    fun shutdown() {
        closed = true
        stop()
        scope.cancel()
        network.shutdown()
        offline.shutdown()
    }
}
