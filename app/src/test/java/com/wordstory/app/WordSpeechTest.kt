package com.wordstory.app

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

class WordSpeechTest {
    // 本地单测的 android.jar 不保存构造参数，用替身提供引擎返回的真实属性。
    private class TestVoice(
        private val language: Locale,
        private val network: Boolean = false,
        private val installed: Boolean = true,
        private val rating: Int = Voice.QUALITY_NORMAL,
    ) : Voice("test", language, rating, Voice.LATENCY_NORMAL, network, emptySet()) {
        override fun getLocale() = language
        override fun isNetworkConnectionRequired() = network
        override fun getQuality() = rating
        override fun getFeatures(): Set<String> =
            if (installed) emptySet() else setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
    }

    @Test
    fun skipsNetworkVoicesMissingDownloadsAndOtherLanguages() {
        val english = TestVoice(Locale.UK)
        assertSame(english, offlineEnglishVoice(listOf(
            TestVoice(Locale.US, network = true),
            TestVoice(Locale.US, installed = false),
            TestVoice(Locale.CHINA),
            english,
        )))
    }

    @Test
    fun prefersAmericanEnglishThenVoiceQuality() {
        val best = TestVoice(Locale.US, rating = Voice.QUALITY_HIGH)
        assertSame(best, offlineEnglishVoice(listOf(
            TestVoice(Locale.US, rating = Voice.QUALITY_LOW),
            TestVoice(Locale.UK, rating = Voice.QUALITY_VERY_HIGH),
            best,
        )))
    }

    @Test
    fun hasNoFallbackToNetworkOrUninstalledVoices() {
        assertNull(offlineEnglishVoice(emptyList()))
        assertNull(offlineEnglishVoice(listOf(
            TestVoice(Locale.US, network = true),
            TestVoice(Locale.UK, installed = false),
            TestVoice(Locale.CHINA),
        )))
    }

}
