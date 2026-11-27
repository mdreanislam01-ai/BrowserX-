package com.browserx.app

import java.net.URI
import java.net.URLEncoder

/** Search engines offered in Settings. Google is the default. */
enum class SearchEngine(val id: String, val label: String, val searchUrl: String) {
    GOOGLE("google", "Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("bing", "Bing", "https://www.bing.com/search?q=");

    fun searchUrlFor(query: String): String =
        searchUrl + URLEncoder.encode(query, "UTF-8")

    companion object {
        fun fromId(id: String?): SearchEngine =
            values().firstOrNull { it.id == id } ?: GOOGLE
    }
}

/**
 * Turn what the user typed in the address bar into a URL to load:
 * a full URL stays as-is, a bare domain gets https://, anything else is
 * searched with the chosen search engine.
 */
fun resolveInputToUrl(input: String, engine: SearchEngine): String {
    val text = input.trim()
    if (text.isEmpty()) return ""
    if (text.startsWith("http://") || text.startsWith("https://")) return text
    val looksLikeDomain = !text.contains(" ") &&
        text.contains(".") &&
        !text.startsWith(".") &&
        !text.endsWith(".") &&
        text.substringAfterLast(".").length >= 2
    if (looksLikeDomain) return "https://$text"
    return engine.searchUrlFor(text)
}

/** Host part of a URL, lower-cased, or "" when the URL cannot be parsed. */
fun hostOf(url: String): String {
    return try {
        (URI(url).host ?: "").lowercase()
    } catch (_: Exception) {
        ""
    }
}

/** Well-known ad / tracker hosts blocked by the v1 ad-block. */
val AD_HOSTS: Set<String> = setOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adsafeprotected.com",
    "adnxs.com",
    "criteo.com",
    "taboola.com",
    "outbrain.com",
    "scorecardresearch.com",
    "ads.yahoo.com",
    "moatads.com",
    "amazon-adsystem.com",
    "adsrvr.org",
    "pubmatic.com",
    "rubiconproject.com",
    "smartadserver.com",
    "advertising.com",
    "adsystem.com"
)

/** True when [host] is an ad/tracker host itself or a subdomain of one. */
fun isAdHost(host: String, adHosts: Set<String> = AD_HOSTS): Boolean {
    val h = host.lowercase()
    if (h.isEmpty()) return false
    return adHosts.any { ad -> h == ad || h.endsWith(".$ad") }
}

/** A short display title for a URL (host, or the URL itself when unparsable). */
fun displayTitleFor(url: String): String {
    val host = hostOf(url)
    return if (host.isNotEmpty()) host else url
}
