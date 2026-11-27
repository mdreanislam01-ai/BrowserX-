package com.browserx.app

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal fun newTab(privateMode: Boolean = false) =
    BrowserTab(UUID.randomUUID().toString(), isPrivate = privateMode)

data class TrailStop(val id: Int, val parent: Int?, val url: String, val title: String)

/** A visit branches off the selected stop. Going back and following another link keeps both paths. */
data class PageTrail(val stops: List<TrailStop> = emptyList(), val current: Int? = null) {
    fun visit(url: String, title: String, returnTo: Int? = null): PageTrail {
        if (!isWebUrl(url)) return this
        val stop = stops.find { it.id == (returnTo ?: current) && it.url == url }
        if (stop != null)
            return copy(
                stops = stops.map { if (it.id == stop.id) it.copy(title = title) else it },
                current = stop.id,
            )
        val next = (stops.lastOrNull()?.id ?: 0) + 1
        val kept = (stops + TrailStop(next, current, url, title)).takeLast(64)
        val first = kept.first().id
        return PageTrail(
            kept.map { if (it.parent != null && it.parent < first) it.copy(parent = null) else it },
            next,
        )
    }
}

data class TabSession(val tabs: List<BrowserTab>, val selected: String?)

/** Persist only regular-tab navigation metadata. Never serialize private IDs, page pixels or page bodies. */
object SessionCodec {
    fun encode(tabs: List<BrowserTab>, activeId: String): String {
        val regular = tabs.filterNot { it.isPrivate }
        val arr = JSONArray()
        regular.forEach { tab ->
            val stops = JSONArray()
            tab.trail.stops.forEach { s ->
                stops.put(JSONArray().put(s.id).put(s.parent ?: -1).put(s.url).put(s.title))
            }
            arr.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("url", tab.url)
                    .put("title", tab.title)
                    .put("pin", tab.isPinned)
                    .put("desktop", tab.desktopMode)
                    .put("trail", stops)
                    .put("current", tab.trail.current ?: -1),
            )
        }
        return JSONObject()
            .put("tabs", arr)
            .put("selected", regular.find { it.id == activeId }?.id ?: regular.firstOrNull()?.id)
            .toString()
    }

    fun decode(raw: String?): TabSession {
        val data = runCatching { JSONObject(raw ?: "") }.getOrNull() ?: return TabSession(emptyList(), null)
        val rows = data.optJSONArray("tabs") ?: return TabSession(emptyList(), null)
        val tabs = buildList {
            val seen = mutableSetOf<String>()
            for (i in 0 until rows.length()) {
                val o = rows.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() && seen.add(it) } ?: continue
                val url = o.optString("url").takeIf { it.isEmpty() || isWebUrl(it) } ?: continue
                val a = o.optJSONArray("trail") ?: JSONArray()
                val stops = buildList {
                    for (j in maxOf(0, a.length() - 64) until a.length()) {
                        val s = a.optJSONArray(j) ?: continue
                        if (isWebUrl(s.optString(2)))
                            add(
                                TrailStop(
                                    s.optInt(0),
                                    s.optInt(1).takeIf { it >= 0 },
                                    s.optString(2),
                                    s.optString(3),
                                ),
                            )
                    }
                }
                add(
                    BrowserTab(
                        id,
                        url,
                        o.optString("title", "New tab"),
                        isPinned = o.optBoolean("pin"),
                        desktopMode = o.optBoolean("desktop"),
                        trail = PageTrail(stops, stops.find { it.id == o.optInt("current", -1) }?.id),
                    ),
                )
            }
        }
        return TabSession(
            tabs,
            tabs.find { it.id == data.optString("selected") }?.id ?: tabs.firstOrNull()?.id,
        )
    }
}

enum class BrowserAction(val label: String, val command: String? = null) {
    BACK("Back"),
    FORWARD("Forward"),
    RELOAD("Reload", "reload"),
    FAVORITE("Add favorite"),
    PIN("Pin tab", "pin"),
    EDIT("Edit address"),
    NEW("New tab", "new"),
    PRIVATE("New private tab", "private"),
    TABS("Tab overview", "tabs"),
    HOME("Home", "home"),
    CLOSE("Close tab", "close"),
    PRIVACY("Privacy X-Ray", "privacy"),
    TRAIL("Open page trail", "trail"),
    READER("Reader Studio", "reader"),
    READING("Offline reading", "reading"),
    FIND("Find in page", "find"),
    DESKTOP("Desktop view"),
    SHARE("Share link"),
    PRINT("Print / Save PDF"),
    FAVORITES("Favorites", "favorites"),
    DOWNLOADS("Downloads", "downloads"),
    HISTORY("History", "history"),
    TOPPINGS("Toppings", "toppings"),
    PROFILES("Profiles", "profiles"),
    SETTINGS("Settings", "settings");

    fun available(tab: BrowserTab): Boolean =
        when (this) {
            BACK -> tab.canGoBack
            FORWARD -> tab.canGoForward
            CLOSE -> !tab.isPinned
            FAVORITE -> !tab.isPrivate && isWebUrl(tab.url)
            TRAIL -> tab.trail.stops.isNotEmpty()
            RELOAD,
            HOME,
            READER,
            FIND,
            DESKTOP,
            SHARE,
            PRINT -> isWebUrl(tab.url)
            else -> true
        }

    fun title(tab: BrowserTab, favorite: Boolean = false) =
        when {
            this == FAVORITE && favorite -> "Remove favorite"
            this == PIN && tab.isPinned -> "Unpin tab"
            this == RELOAD && tab.isLoading -> "Stop loading"
            else -> label
        }
}

data class AddressMatch(
    val title: String,
    val detail: String,
    val url: String = "",
    val tabId: String? = null,
    val action: BrowserAction? = null,
)

fun addressMatches(
    input: String,
    active: BrowserTab,
    tabs: List<BrowserTab>,
    favorites: List<BookmarkItem>,
    history: List<HistoryItem>,
    suggest: Boolean,
): List<AddressMatch> {
    val q = input.trim()
    if (q.startsWith('>')) {
        val prefix = q.drop(1).trim()
        return BrowserAction.entries
            .filter {
                it.command != null &&
                    it.available(active) &&
                    (it.command.startsWith(prefix, true) || it.label.contains(prefix, true))
            }
            .sortedByDescending { it.command.equals(prefix, true) }
            .map { AddressMatch(it.title(active), ">${it.command}", action = it) }
    }
    if (!suggest || q.isBlank()) return emptyList()
    fun matches(title: String, url: String) = title.contains(q, true) || url.contains(q, true)
    return buildList {
            tabs
                .filter {
                    it.id != active.id &&
                        it.isPrivate == active.isPrivate &&
                        isWebUrl(it.url) &&
                        matches(it.title, it.url)
                }
                .forEach { add(AddressMatch(it.title, "Switch to tab · ${hostOf(it.url)}", it.url, it.id)) }
            if (!active.isPrivate) {
                favorites
                    .filter { matches(it.title, it.url) }
                    .forEach { add(AddressMatch(it.title, "Favorite · ${hostOf(it.url)}", it.url)) }
                history
                    .filter { matches(it.title, it.url) }
                    .forEach { add(AddressMatch(it.title, "History · ${hostOf(it.url)}", it.url)) }
            }
        }
        .distinctBy { it.url }
        .take(5)
}

/** Stable IDs for the two user-configurable controls around the address field. More is always present. */
enum class BarButton(val action: BrowserAction?) {
    TABS(BrowserAction.TABS),
    NEW(BrowserAction.NEW),
    RELOAD(BrowserAction.RELOAD),
    FAVORITE(BrowserAction.FAVORITE),
    PIN(BrowserAction.PIN),
    HOME(BrowserAction.HOME),
    BACK(BrowserAction.BACK),
    FORWARD(BrowserAction.FORWARD),
    PRIVACY(BrowserAction.PRIVACY),
    READER(BrowserAction.READER),
    FIND(BrowserAction.FIND),
    SHARE(BrowserAction.SHARE),
    NONE(null);

    val label: String
        get() = action?.label ?: "None"
}

fun AppSettings.placeButton(leading: Boolean, choice: BarButton): AppSettings {
    // Moving the other slot's action swaps slots instead of duplicating buttons.
    val swap = choice != BarButton.NONE && choice == if (leading) trailingButton else leadingButton
    return if (leading)
        copy(leadingButton = choice, trailingButton = if (swap) leadingButton else trailingButton)
    else copy(trailingButton = choice, leadingButton = if (swap) trailingButton else leadingButton)
}
