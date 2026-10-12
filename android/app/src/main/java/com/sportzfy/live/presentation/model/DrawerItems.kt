package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.SportCategory

/** Entries of the side drawer, in display order within their section. */
enum class DrawerItem(val label: String) {
    HOME("Live Matches"),
    LIVE_TV("Live TV"),
    FIXTURES("Fixtures"),
    SAVED("Saved Matches"),
    SEARCH("Search"),
    FOOTBALL("Football"),
    CRICKET("Cricket"),
    OTHER_SPORTS("Other Sports"),
    SETTINGS("Settings"),
    ABOUT("About"),
    ;

    companion object {
        val MAIN = listOf(HOME, LIVE_TV, FIXTURES, SAVED, SEARCH)
        val SPORTS = listOf(FOOTBALL, CRICKET, OTHER_SPORTS)
        val FOOTER = listOf(SETTINGS, ABOUT)
    }
}

/** Where the user is, as far as the drawer cares. */
enum class AppSection { HOME, TOMORROW, LIVE_TV, ALL_MATCHES, OTHER }

object DrawerSelection {

    /**
     * The one drawer entry that describes what is on screen, or null. On the
     * fixture tabs an active filter (Saved, a sport, search) wins over the tab,
     * because that is what the drawer entry put there.
     */
    fun selected(section: AppSection, catalog: CatalogUiState): DrawerItem? = when (section) {
        AppSection.LIVE_TV -> DrawerItem.LIVE_TV
        AppSection.OTHER -> null
        AppSection.HOME, AppSection.TOMORROW, AppSection.ALL_MATCHES -> when {
            catalog.favoritesOnly -> DrawerItem.SAVED
            catalog.otherSports -> DrawerItem.OTHER_SPORTS
            catalog.selectedCategory == SportCategory.FOOTBALL -> DrawerItem.FOOTBALL
            catalog.selectedCategory == SportCategory.CRICKET -> DrawerItem.CRICKET
            catalog.searchActive -> DrawerItem.SEARCH
            section == AppSection.HOME -> DrawerItem.HOME
            section == AppSection.ALL_MATCHES -> DrawerItem.FIXTURES
            else -> null
        }
    }
}
