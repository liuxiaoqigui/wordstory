package com.wordstory.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class MicrosoftSpeechTest {
    @Test
    fun escapesWordAndUsesJennyAtNormalSpeed() {
        val word = "a<&\"'b"
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(microsoftSsml(word).toByteArray()))
        assertEquals(word, doc.documentElement.textContent)
        assertTrue(doc.getElementsByTagName("voice").item(0).attributes.getNamedItem("name").nodeValue.contains("JennyNeural"))
        assertEquals("+0%", doc.getElementsByTagName("prosody").item(0).attributes.getNamedItem("rate").nodeValue)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsControlCharacters() { microsoftSsml("word\u0000") }

    private fun frame(header: String, audio: ByteArray): ByteArray {
        val bytes = header.toByteArray()
        return byteArrayOf((bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes + audio
    }

    @Test
    fun separatesAudioFromBinaryHeaders() {
        val audio = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(audio, microsoftAudioChunk(frame("Path:audio\r\nContent-Type:audio/mpeg\r\n", audio)))
        assertNull(microsoftAudioChunk(frame("Path:audio\r\n", byteArrayOf())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedFrame() { microsoftAudioChunk(byteArrayOf(0, 50, 1)) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonAudioPayload() { microsoftAudioChunk(frame("Path:audio\r\nContent-Type:text/html\r\n", byteArrayOf(1))) }
}
