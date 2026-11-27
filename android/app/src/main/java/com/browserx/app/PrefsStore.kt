package com.browserx.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class PrefsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("browserx_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        return AppSettings(
            themeMode = ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", 0)) { ThemeMode.SYSTEM },
            dynamicColor = prefs.getBoolean("dynamic_color", true),
            addressBarPosition =
                AddressBarPosition.entries.getOrElse(prefs.getInt("address_bar_pos", 0)) {
                    AddressBarPosition.BOTTOM
                },
            tabViewMode =
                TabViewMode.entries.getOrElse(prefs.getInt("tab_view_mode", 0)) { TabViewMode.COVER_FLOW },
            searchEngine =
                SearchEngine.entries.getOrElse(prefs.getInt("search_engine", 0)) { SearchEngine.GOOGLE },
            adBlockEnabled = prefs.getBoolean("ad_block", true),
            cookieBannerHiderEnabled = prefs.getBoolean("cookie_hider", true),
            httpsOnlyEnabled = prefs.getBoolean("https_only", true),
            forceDarkWebEnabled = prefs.getBoolean("force_dark_web", false),
            hapticsEnabled = prefs.getBoolean("haptics", true),
            restoreTabs = prefs.getBoolean("restore_tabs", true),
            showFavorites = prefs.getBoolean("home_favorites", true),
            localSuggestions = prefs.getBoolean("local_suggestions", true),
            floatingMenu = prefs.getBoolean("floating_menu", false),
            leadingButton =
                BarButton.entries.find { it.name == prefs.getString("bar_leading", null) } ?: BarButton.TABS,
            trailingButton =
                BarButton.entries.find { it.name == prefs.getString("bar_trailing", null) } ?: BarButton.NEW,
            readerTheme =
                ReaderTheme.entries.find { it.name == prefs.getString("reader_theme", null) }
                    ?: ReaderTheme.LIGHT,
            readerSize = prefs.getInt("reader_size", 17).coerceIn(13, 27),
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs
            .edit()
            .putInt("theme_mode", s.themeMode.ordinal)
            .putBoolean("dynamic_color", s.dynamicColor)
            .putInt("address_bar_pos", s.addressBarPosition.ordinal)
            .putInt("tab_view_mode", s.tabViewMode.ordinal)
            .putInt("search_engine", s.searchEngine.ordinal)
            .putBoolean("ad_block", s.adBlockEnabled)
            .putBoolean("cookie_hider", s.cookieBannerHiderEnabled)
            .putBoolean("https_only", s.httpsOnlyEnabled)
            .putBoolean("force_dark_web", s.forceDarkWebEnabled)
            .putBoolean("haptics", s.hapticsEnabled)
            .putBoolean("restore_tabs", s.restoreTabs)
            .putBoolean("home_favorites", s.showFavorites)
            .putBoolean("local_suggestions", s.localSuggestions)
            .putBoolean("floating_menu", s.floatingMenu)
            .putString("bar_leading", s.leadingButton.name)
            .putString("bar_trailing", s.trailingButton.name)
            .putString("reader_theme", s.readerTheme.name)
            .putInt("reader_size", s.readerSize)
            .apply()
    }

    fun getTotalBlocked(): Int = prefs.getInt("total_blocked", 0)

    fun incrementTotalBlocked(delta: Int = 1) {
        prefs.edit().putInt("total_blocked", getTotalBlocked() + delta).apply()
    }

    fun loadBookmarks(): List<BookmarkItem> {
        val raw = prefs.getString("bookmarks_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            BookmarkItem(
                url = o.optString("url"),
                title = o.optString("title"),
                timestamp = o.optLong("ts", System.currentTimeMillis()),
            )
        }
    }

    fun saveBookmarks(items: List<BookmarkItem>) {
        val arr = JSONArray()
        items.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("url", b.url)
                    put("title", b.title)
                    put("ts", b.timestamp)
                },
            )
        }
        prefs.edit().putString("bookmarks_json", arr.toString()).apply()
    }

    // Keep the original library when upgrading: old unscoped history belongs to Personal.
    private fun historyKey(profile: String) = if (profile == "default") "history_json" else "history_$profile"

    fun loadHistory(profile: String): List<HistoryItem> {
        val raw = prefs.getString(historyKey(profile), "[]") ?: "[]"
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            HistoryItem(
                url = o.optString("url"),
                title = o.optString("title"),
                timestamp = o.optLong("ts", System.currentTimeMillis()),
            )
        }
    }

    fun saveHistory(profile: String, items: List<HistoryItem>) {
        val arr = JSONArray()
        items.take(500).forEach { h ->
            arr.put(
                JSONObject().apply {
                    put("url", h.url)
                    put("title", h.title)
                    put("ts", h.timestamp)
                },
            )
        }
        prefs.edit().putString(historyKey(profile), arr.toString()).apply()
    }

    fun loadDownloads(): List<DownloadItem> {
        val raw = prefs.getString("downloads_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            DownloadItem(
                id = o.optLong("id"),
                fileName = o.optString("fileName"),
                url = o.optString("url"),
                timestamp = o.optLong("ts", System.currentTimeMillis()),
            )
        }
    }

    fun saveDownloads(items: List<DownloadItem>) {
        val arr = JSONArray()
        items.take(200).forEach { d ->
            arr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("fileName", d.fileName)
                    put("url", d.url)
                    put("ts", d.timestamp)
                },
            )
        }
        prefs.edit().putString("downloads_json", arr.toString()).apply()
    }

    fun loadProfiles(): List<BrowserProfile> {
        val raw = prefs.getString("profiles_json", null)
        if (raw == null) {
            return listOf(
                BrowserProfile("default", "Personal", "🍬", 0xFF6750A4),
                BrowserProfile("work", "Work", "💼", 0xFF0061A4),
            )
        }
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            BrowserProfile(
                id = o.optString("id"),
                name = o.optString("name"),
                emoji = o.optString("emoji", "🍬"),
                accentHex = o.optLong("accentHex", 0xFF6750A4),
            )
        }
    }

    fun saveProfiles(profiles: List<BrowserProfile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("emoji", p.emoji)
                    put("accentHex", p.accentHex)
                },
            )
        }
        prefs.edit().putString("profiles_json", arr.toString()).apply()
    }

    fun loadSession(profile: String) = SessionCodec.decode(prefs.getString("session_$profile", null))

    fun saveSession(profile: String, tabs: List<BrowserTab>, activeId: String) {
        prefs.edit().putString("session_$profile", SessionCodec.encode(tabs, activeId)).apply()
    }

    fun forgetSessions() {
        prefs
            .edit()
            .apply { prefs.all.keys.filter { it.startsWith("session_") }.forEach { remove(it) } }
            .apply()
    }

    fun getActiveProfileId(): String = prefs.getString("active_profile_id", "default") ?: "default"

    fun setActiveProfileId(id: String) {
        prefs.edit().putString("active_profile_id", id).apply()
    }
}
