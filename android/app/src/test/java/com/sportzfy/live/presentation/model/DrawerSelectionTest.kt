package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.SportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerSelectionTest {

    private fun selected(section: AppSection, state: CatalogUiState = CatalogUiState()) =
        DrawerSelection.selected(section, state)

    @Test
    fun `tabs map to their drawer entry`() {
        assertEquals(DrawerItem.HOME, selected(AppSection.HOME))
        assertEquals(DrawerItem.LIVE_TV, selected(AppSection.LIVE_TV))
        assertEquals(DrawerItem.FIXTURES, selected(AppSection.ALL_MATCHES))
        assertNull(selected(AppSection.TOMORROW))
        assertNull(selected(AppSection.OTHER))
    }

    @Test
    fun `an active filter wins over the tab`() {
        assertEquals(DrawerItem.SAVED, selected(AppSection.ALL_MATCHES, CatalogUiState(favoritesOnly = true)))
        assertEquals(DrawerItem.FOOTBALL, selected(AppSection.ALL_MATCHES, CatalogUiState(selectedCategory = SportCategory.FOOTBALL)))
        assertEquals(DrawerItem.CRICKET, selected(AppSection.HOME, CatalogUiState(selectedCategory = SportCategory.CRICKET)))
        assertEquals(DrawerItem.OTHER_SPORTS, selected(AppSection.ALL_MATCHES, CatalogUiState(otherSports = true)))
        assertEquals(DrawerItem.SEARCH, selected(AppSection.ALL_MATCHES, CatalogUiState(searchActive = true)))
    }

    @Test
    fun `a sport without its own entry selects only the tab`() {
        assertEquals(DrawerItem.FIXTURES, selected(AppSection.ALL_MATCHES, CatalogUiState(selectedCategory = SportCategory.TENNIS)))
    }

    @Test
    fun `filters don't leak onto Live TV`() {
        assertEquals(DrawerItem.LIVE_TV, selected(AppSection.LIVE_TV, CatalogUiState(favoritesOnly = true)))
    }

    @Test
    fun `every entry sits in exactly one section`() {
        val all = DrawerItem.MAIN + DrawerItem.SPORTS + DrawerItem.FOOTER
        assertEquals(DrawerItem.entries.toSet(), all.toSet())
        assertEquals(all.size, all.toSet().size)
    }
}
