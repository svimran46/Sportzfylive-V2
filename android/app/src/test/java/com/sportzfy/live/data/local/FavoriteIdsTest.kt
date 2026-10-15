package com.sportzfy.live.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteIdsTest {

    @Test
    fun `toggle adds newest first and removes existing`() {
        val saved = FavoriteIds.toggle(listOf("a", "b"), "c")
        assertEquals(listOf("c", "a", "b"), saved)
        assertEquals(listOf("c", "b"), FavoriteIds.toggle(saved, "a"))
    }

    @Test
    fun `list is capped, dropping the oldest`() {
        assertEquals(listOf("d", "a", "b"), FavoriteIds.toggle(listOf("a", "b", "c"), "d", max = 3))
    }

    @Test
    fun `encode and decode round-trip`() {
        val ids = listOf("streamed-1", "match two", "x/y")
        assertEquals(ids, FavoriteIds.decode(FavoriteIds.encode(ids)))
        assertEquals(emptyList<String>(), FavoriteIds.decode(null))
        assertEquals(emptyList<String>(), FavoriteIds.decode(""))
    }
}
