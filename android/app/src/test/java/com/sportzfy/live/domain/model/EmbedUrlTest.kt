package com.sportzfy.live.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmbedUrlTest {

    @Test
    fun `builds the deterministic embed pattern`() {
        assertEquals("https://embed.st/embed/alpha/match-101/1", EmbedUrl.build("alpha", "match-101", 1))
    }

    @Test
    fun `encodes path segments`() {
        assertEquals("https://embed.st/embed/a/x%20y%2Fz/2", StreamItem("a", "x y/z", 2, "", false).embedUrl)
    }

    @Test
    fun `rejects invalid input`() {
        assertThrows(IllegalArgumentException::class.java) { EmbedUrl.build("alpha", "id", 0) }
        assertThrows(IllegalArgumentException::class.java) { EmbedUrl.build("", "id", 1) }
    }
}
