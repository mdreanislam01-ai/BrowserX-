package com.browserx.app

import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class TabViewMode(val label: String) {
    COVER_FLOW("Cover-Flow"),
    GRID("Grid"),
    LIST("List")
}

enum class AddressBarPosition(val label: String) {
    // Top is the default layout: search pill, tab counter and overflow (3-dot) menu sit up top.
    TOP("Top"),
    BOTTOM("Bottom");

    companion object {
        /** The layout the app ships with: toolbar on top. */
        val DEFAULT: AddressBarPosition get() = AddressBarPosition.TOP
        val displayOrder: List<AddressBarPosition>
            get() = listOf(AddressBarPosition.TOP, AddressBarPosition.BOTTOM)
    }
}

enum class ReaderTheme(val label: String, val bgColor: Color, val textColor: Color) {
    LIGHT("Light", Color(0xFFFAFAFA), Color(0xFF1C1B1F)),
    SEPIA("Sepia", Color(0xFFF4ECD8), Color(0xFF3E2F1C)),
    DARK("Dark", Color(0xFF141416), Color(0xFFE6E1E5))
}

data class BrowserTab(
    val id: String,
    val url: String = "",
    val title: String = "New Tab",
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val desktopMode: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val blockedCount: Int = 0,
    val blockedDomains: List<String> = emptyList(),
    /** Non-null when the main frame failed to load; shown as an error card over the page. */
    val errorText: String? = null
)

data class BookmarkItem(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class HistoryItem(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DownloadItem(
    val id: Long,
    val fileName: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class BrowserProfile(
    val id: String,
    val name: String,
    val emoji: String,
    val accentHex: Long
)

data class ReaderArticle(
    val title: String,
    val byline: String,
    val content: String
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val addressBarPosition: AddressBarPosition = AddressBarPosition.DEFAULT,
    val tabViewMode: TabViewMode = TabViewMode.COVER_FLOW,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val adBlockEnabled: Boolean = true,
    val cookieBannerHiderEnabled: Boolean = true,
    val httpsOnlyEnabled: Boolean = false,
    val forceDarkWebEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    /** Off by default: the toolbar stays pinned on top instead of sliding away on scroll. */
    val hideToolbarOnScroll: Boolean = false
)
