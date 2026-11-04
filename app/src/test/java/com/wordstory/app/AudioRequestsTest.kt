package com.wordstory.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import java.io.IOException

class AudioRequestsTest {
    @Test
    fun clickingUsesTheDownloadAlreadyStartedByPrefetch() = runBlocking {
        val ready = CompletableDeferred<File>()
        var loads = 0
        val requests = AudioRequests(this) { loads++; ready.await() }
        val prefetch = requests.get("beautiful")
        prefetch.start()
        yield()
        assertEquals(1, loads)
        val playback = requests.get("beautiful")
        assertSame(prefetch, playback)
        ready.complete(File("beautiful.mp3"))
        assertEquals(File("beautiful.mp3"), playback.await())
        assertEquals(1, loads)
    }

    @Test
    fun changingPlaybackDoesNotCancelSharedDownload() = runBlocking {
        val ready = CompletableDeferred<File>()
        val requests = AudioRequests(this) { ready.await() }
        val audio = requests.get("beautiful")
        val playback = launch { audio.await() }
        yield()
        playback.cancelAndJoin()
        assertFalse(audio.isCancelled)
        ready.complete(File("beautiful.mp3"))
        assertEquals(File("beautiful.mp3"), audio.await())
    }

    @Test
    fun failedPrefetchCanBeRetried() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var loads = 0
            val requests = AudioRequests(owner) {
                loads++
                if (loads == 1) throw IOException("network unavailable")
                File("beautiful.mp3")
            }
            try { requests.get("beautiful").await() } catch (_: IOException) { }
            assertEquals(File("beautiful.mp3"), requests.get("beautiful").await())
            assertEquals(2, loads)
        } finally { owner.cancel() }
    }

    @Test
    fun cancelledDownloadDoesNotRemoveItsReplacement() = runBlocking {
        val ready = CompletableDeferred<File>()
        val requests = AudioRequests(this) { ready.await() }
        requests.get("beautiful").start()
        yield()
        requests.cancel()
        val replacement = requests.get("beautiful")
        yield()
        assertSame(replacement, requests.get("beautiful"))
        ready.complete(File("beautiful.mp3"))
        assertEquals(File("beautiful.mp3"), replacement.await())
    }
}
