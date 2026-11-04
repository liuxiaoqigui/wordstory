/*
 * Edge TTS v2 handshake token, adapted from edge-tts 7.3.1 drm.py.
 * Copyright (c) rany and edge-tts contributors. LGPL-3.0-or-later.
 * Kotlin adaptation: WordStory contributors, 2026.
 * Source: https://github.com/rany2/edge-tts
 * License: assets/licenses/edge-tts-LGPL-3.0.txt
 */
package com.wordstory.app

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object EdgeToken {
    // Public Edge client identifier, not an Azure account key.
    const val CLIENT = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private fun ints(vararg values: Long) = values.map { it.toInt() }.toIntArray()
    private val initial = ints(0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
        0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19)
    private val prefixState = ints(0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A,
        0x6C006F49, 0x04BD35AB, 0xFEB0A7E4, 0x03EFFBD2)
    private val schedule = ints(0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA,
        0xA622EE8C, 0xD1DD9491, 0xC0DD25A0, 0xF9E70AB6, 0xF0C31573, 0x7058CE4E,
        0x7188E606, 0x00E5AB33, 0xD258B060, 0x72766564)
    private val rounds = ints(
        0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
        0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
        0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
        0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
        0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
        0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
        0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
        0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
    )
    private val coefficients = ints(0xE82E91B1, 0x44DE24C6, 0x3BEB65BC, 0xC89C5933, 0x8D1EC211,
        0x314EF5D5, 0xE92491B1, 0xF0A5B9D5, 0x06064CF3, 0xDA79FA60, 0xBED20560, 0x89F9C9F8, 0x35FA38C6, 0xCB49E3C6)
    private val offsets = ints(0xB32700D0, 0x88DFF51D, 0x29514038, 0xB328CBDF, 0xD405B4D5,
        0x26E55F3C, 0x607801C6, 0x82B2C1FB, 0xFA51A3DA, 0x207CC464, 0x3C3C74F3, 0xD2D14B61, 0xBB04A2C1, 0xC5626167)

    fun generate(epochSeconds: Long): String {
        val seconds = epochSeconds + 11644473600L
        val salt = "${seconds / 300 * 300 * 10000000}$CLIENT".toByteArray(Charsets.US_ASCII)
        val digest = digest(salt)
        return hex(checksum(salt, digest)) + "-" + hex(digest)
    }

    private fun sigma0(x: Int) = Integer.rotateRight(x, 7) xor Integer.rotateRight(x, 18) xor (x ushr 3)
    private fun sigma1(x: Int) = Integer.rotateRight(x, 17) xor Integer.rotateRight(x, 19) xor (x ushr 10)
    private fun expand(w: IntArray, start: Int = 16) {
        for (i in start..63) w[i] = sigma1(w[i - 2]) + w[i - 7] + sigma0(w[i - 15]) + w[i - 16]
    }

    private fun compress(state: IntArray, w: IntArray, start: Int = 0): IntArray {
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        for (i in start..63) {
            val k = if (start == 14 && i == 14) 0xC9DC06A7.toInt() else rounds[i]
            val t1 = h + (Integer.rotateRight(e, 6) xor Integer.rotateRight(e, 11) xor Integer.rotateRight(e, 25)) +
                (g xor (e and (f xor g))) + k + w[i]
            val t2 = (Integer.rotateRight(a, 2) xor Integer.rotateRight(a, 13) xor Integer.rotateRight(a, 22)) +
                ((a and b) or (c and (a or b)))
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        return intArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun digest(data: ByteArray): ByteArray {
        val w = IntArray(64)
        w[14] = ByteBuffer.wrap(byteArrayOf(0) + data.copyOfRange(0, 3)).int
        w[15] = ByteBuffer.wrap(data, 3, 4).int
        for (i in 16..29) {
            var v = schedule[i - 16]
            if (i - 2 >= 14) v += sigma1(w[i - 2]) xor (if (i == 16) 0x000B92C0 else 0)
            if (i - 7 >= 14) v += w[i - 7]
            if (i - 15 >= 14) v += sigma0(w[i - 15]) xor (if (i == 29) 0x059C0B80 else 0)
            w[i] = v
        }
        w[30] = sigma1(w[28]) + w[23] + sigma0(w[15]) + w[14] + 0xC9DC06A7.toInt() - rounds[14]
        expand(w, 31)
        val first = compress(prefixState, w, 14)
        var state = IntArray(8) { initial[it] + first[it] }
        val length = 57 + data.size
        val tail = data.copyOfRange(7, data.size) + byteArrayOf(0x80.toByte()) +
            ByteArray(Math.floorMod(55 - length, 64)) + ByteBuffer.allocate(8).putLong(length * 8L).array()
        for (offset in tail.indices step 64) {
            val words = IntArray(64)
            val block = ByteBuffer.wrap(tail, offset, 64)
            for (i in 0..15) words[i] = block.int
            expand(words)
            val next = compress(state, words)
            state = IntArray(8) { state[it] + next[it] }
        }
        return ByteBuffer.allocate(32).also { buffer -> state.forEach { buffer.putInt(it) } }.array()
    }

    private fun checksum(data: ByteArray, digest: ByteArray): ByteArray {
        val keys = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN)
        val a = keys.int or 1
        val b = keys.int or 1
        val factors = intArrayOf(a * 0x82893933.toInt(), b * 0x82893933.toInt())
        val terms = coefficients.mapIndexed { i, v -> v * factors[i % 2] }.toMutableList()
        val extra = offsets.toMutableList()
        val bytes = (byteArrayOf(0) + data).let { it.copyOf((it.size + 7) / 8 * 8) }
        val words = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var index = 0
        while (words.hasRemaining()) {
            val word = words.int
            var term = word * (if (index % 2 == 0) a else b)
            var offset = Integer.rotateLeft(word, 16) * (if (index % 2 == 0) 0xEF0569FB.toInt() else 0xC31713DB.toInt())
            if (index == 0) { term += 0xF2F90B1A.toInt() * factors[0]; offset += 0x0B1A0000 }
            terms += term; extra += offset; index++
        }
        var swap = 0; var last = 0; var state = 0; var acc = 0
        for (i in terms.indices step 2) {
            var q = Integer.rotateLeft(extra[i] + terms[i], 16) * 0x76F50F34
            swap += Integer.rotateLeft(q, 16) * 0xC3EFEA97.toInt() + q
            q = Integer.rotateLeft(extra[i + 1] + terms[i + 1], 16) * 0x1881CE3C
            last = Integer.rotateLeft(q, 16) * 0x35BD1EC9 + q
            swap += last
            state = state * a + terms[i]
            for (factor in ints(0xCF98B111, 0x87085B9F, 0x12CEB96D, 0x257E1D83)) state = Integer.rotateLeft(state, 16) * factor
            acc += state
            var t = state * b + terms[i + 1]
            for (factor in ints(0xA27416F5, 0xD38396FF, 0x7C932B89, 0xBFA49F69)) t = Integer.rotateLeft(t, 16) * factor
            state = t
            acc += state
        }
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(last xor state).putInt(swap xor acc).array()
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }
}
