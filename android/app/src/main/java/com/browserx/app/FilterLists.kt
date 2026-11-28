package com.browserx.app

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads free EasyList / EasyPrivacy rules and keeps only the host rules (`||domain^`),
 * which is what the WebView request filter can use. Nothing else is stored.
 */
object FilterLists {
    private const val USER_AGENT = "BrowserX-Filter/1.0"

    private val SOURCES = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    )

    private val HOST_RULE = Regex("""^\|\|([a-z0-9][a-z0-9.\-]*)\^""", RegexOption.IGNORE_CASE)
    private val HOSTS_LINE = Regex("""^0\.0\.0\.0\s+([a-z0-9][a-z0-9.\-]*)$""", RegexOption.IGNORE_CASE)

    /**
     * Fetches every source, extracts blocked domains and stores them on disk.
     * Returns the number of domains kept.
     */
    @Throws(IOException::class)
    fun download(context: Context): Int {
        val hosts = HashSet<String>()
        var failures = 0

        SOURCES.forEach { source ->
            try {
                val text = httpGet(source)
                text.lineSequence().forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isEmpty() || line.startsWith("!") || line.startsWith("#") ||
                        line.startsWith("[")
                    ) {
                        return@forEach
                    }
                    val domain = HOST_RULE.find(line)?.groupValues?.getOrNull(1)
                        ?: HOSTS_LINE.find(line)?.groupValues?.getOrNull(1)
                    if (domain != null) {
                        val clean = domain.lowercase().trim('.')
                        if (clean.length in 4..120 && clean.contains('.')) hosts.add(clean)
                    }
                }
            } catch (_: Exception) {
                failures++
            }
        }

        if (hosts.size < 100) {
            throw IOException("Could not download the filter lists (kept 0 rules)")
        }
        AdBlockEngine.saveDownloadedHosts(context, hosts)
        if (failures == SOURCES.size) throw IOException("All filter sources failed")
        return hosts.size
    }

    @Throws(IOException::class)
    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for $url")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
