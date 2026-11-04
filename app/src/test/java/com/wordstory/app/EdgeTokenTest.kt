package com.wordstory.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EdgeTokenTest {
    @Test
    fun matchesReferenceClientTokens() {
        assertEquals("52BC7A17B50DFEC9-28C4394775C8751CD2F6859C030C06DB1489ACAB3B3A7651831FC26895177ED6", EdgeToken.generate(0))
        assertEquals("2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2", EdgeToken.generate(1700000000))
        assertEquals("DCBCC2B88AB07670-A93B43B1FE047563CE638A4F2DCAEA79D6BEB30E2BC200CBFB6E5C3033B443DB", EdgeToken.generate(1793754000))
    }

    @Test
    fun changesAtFiveMinuteBoundary() {
        assertEquals(EdgeToken.generate(1793754000), EdgeToken.generate(1793754299))
        assertNotEquals(EdgeToken.generate(1793754000), EdgeToken.generate(1793754300))
    }
}
