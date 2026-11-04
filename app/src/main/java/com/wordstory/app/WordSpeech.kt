package com.wordstory.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

val LocalWordSpeech = staticCompositionLocalOf<WordSpeech?> { null }

/** 不使用联网音色，也不使用尚未下载的语音；优先美式英语。 */
internal fun offlineEnglishVoice(voices: Collection<Voice>): Voice? = voices
    .filter {
        it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
    }
    .maxWithOrNull(compareBy<Voice> { it.locale.country == Locale.US.country }.thenBy { it.quality })

internal class OfflineWordSpeech(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var initialized: Boolean? = null
    private var pending: String? = null
    private var closed = false

    init {
        engine = TextToSpeech(context) { status ->
            // 初始化失败时回调可能先于构造器返回，统一延后到主线程处理。
            main.post {
                if (!closed) {
                    initialized = status == TextToSpeech.SUCCESS
                    if (initialized == true) {
                        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {}
                            override fun onError(utteranceId: String?) {
                                main.post {
                                    if (!closed) message("发音失败，请检查系统英文离线语音包")
                                }
                            }
                        })
                    }
                    val word = pending
                    pending = null
                    if (word != null) speak(word)
                }
            }
        }
    }

    fun speak(word: String) {
        if (closed || word.isBlank()) return
        when (initialized) {
            null -> {
                pending = word
                message("语音正在准备，请稍候")
                return
            }
            false -> {
                message("语音引擎不可用，请先在系统设置中安装或启用文字转语音引擎，再重新打开 App")
                return
            }
            true -> Unit
        }
        val tts = engine ?: return
        val voice = offlineEnglishVoice(tts.voices.orEmpty())
        if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            message("请先在系统“文字转语音”设置中下载英文离线语音包")
            return
        }
        if (tts.setSpeechRate(1f) != TextToSpeech.SUCCESS) {
            message("无法设置发音语速，请检查系统语音设置")
            return
        }
        if (tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word") == TextToSpeech.ERROR) {
            message("暂时无法发音，请检查系统语音设置")
        }
    }

    fun stop() {
        pending = null
        engine?.stop()
    }

    fun shutdown() {
        closed = true
        stop()
        engine?.shutdown()
        engine = null
    }

    private fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

@Composable
fun PronounceableWord(word: String, fontSize: TextUnit = 40.sp, lineHeight: TextUnit = 44.sp) {
    val speech = LocalWordSpeech.current
    LaunchedEffect(word, speech) { speech?.prefetch(listOf(word)) }
    val preparing = speech?.preparingWord == word
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(word, modifier = Modifier.weight(1f), fontSize = fontSize, lineHeight = lineHeight,
            fontWeight = FontWeight.Black, color = DuoEel)
        IconButton(onClick = { speech?.speak(word) }, enabled = !preparing) {
            if (preparing) {
                CircularProgressIndicator(Modifier.size(22.dp).semantics { contentDescription = "正在准备 $word 的发音" },
                    color = DuoBlue, strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_volume_up), contentDescription = "播放 $word 的发音", tint = DuoBlue)
            }
        }
    }
}
