package com.browserx.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A newer GitHub release that can be installed over the running build. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String
)

/** Everything the UI needs to know about the update flow. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class Ready(val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * In-app updater: reads the newest GitHub release of this repository, downloads the APK and
 * hands it to the system package installer. The release body carries a machine readable
 * `versionCode=<n>` line written by the CI workflow, so the app can tell whether it is stale.
 */
object Updater {
    const val GITHUB_REPO = "mdreanislam01-ai/BrowserX-"
    const val RELEASES_PAGE = "https://github.com/$GITHUB_REPO/releases"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=10"
    private const val USER_AGENT = "BrowserX-Updater"

    private val VERSION_CODE_RE = Regex("""versionCode\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val VERSION_NAME_RE = Regex("""versionName\s*[=:]\s*([\w.\-+]+)""", RegexOption.IGNORE_CASE)

    /** Pulls `versionCode=123` out of a release body / notes blob. */
    fun parseVersionCode(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        return VERSION_CODE_RE.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /** Pulls `versionName=1.1.0` out of a release body / notes blob. */
    fun parseVersionName(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return VERSION_NAME_RE.find(text)?.groupValues?.getOrNull(1)
    }

    fun isNewer(remoteVersionCode: Int?, installedVersionCode: Int): Boolean =
        remoteVersionCode != null && remoteVersionCode > installedVersionCode

    /**
     * Fetches the latest published (non pre-release) release and returns it only when it is
     * newer than [installedVersionCode].
     */
    @Throws(IOException::class)
    fun fetchLatest(installedVersionCode: Int): UpdateInfo? {
        // Preferred path: GitHub's "latest release" pointer.
        requestJsonOrNull(LATEST_RELEASE_API)?.let { payload ->
            runCatching { parseRelease(JSONObject(payload), installedVersionCode) }
                .getOrNull()
                ?.let { return it }
        }
        // Fallback: walk the release list in case the pointer is missing or stale.
        val array = JSONArray(requestJson(RELEASES_API))
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .filterNot { it.optBoolean("prerelease") || it.optBoolean("draft") }
            .mapNotNull { runCatching { parseRelease(it, installedVersionCode) }.getOrNull() }
            .maxByOrNull { it.versionCode }
    }

    private fun requestJsonOrNull(url: String): String? = try {
        requestJson(url)
    } catch (_: Exception) {
        null
    }

    @Throws(IOException::class)
    private fun requestJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub returned HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Pure parsing step, kept separate so it can be unit tested. */
    fun parseRelease(release: JSONObject, installedVersionCode: Int): UpdateInfo? {
        val notes = release.optString("body", "")
        val remoteCode = parseVersionCode(notes) ?: return null
        if (!isNewer(remoteCode, installedVersionCode)) return null

        val assets = release.optJSONArray("assets")
        val apkUrl = if (assets != null) {
            (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
        } else {
            null
        }
        if (apkUrl.isNullOrBlank()) return null

        val versionName = parseVersionName(notes)
            ?: release.optString("tag_name").removePrefix("browserx-").ifBlank { "new build" }

        return UpdateInfo(
            versionName = versionName,
            versionCode = remoteCode,
            apkUrl = apkUrl,
            notes = notes.lineSequence()
                .filterNot { it.contains("versionCode", ignoreCase = true) || it.contains("versionName", ignoreCase = true) }
                .joinToString("\n")
                .trim()
        )
    }

    /** Downloads the APK into app-private storage and reports 0..100 progress. */
    @Throws(IOException::class)
    fun downloadApk(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val root = context.getExternalFilesDir(null) ?: context.cacheDir
        val dir = File(root, "updates").apply { mkdirs() }
        val target = File(dir, "browserx-${info.versionCode}.apk")
        if (target.exists() && target.length() > 0L) {
            onProgress(100)
            return target
        }

        val partial = File(dir, "browserx-${info.versionCode}.part")
        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Download failed (HTTP $code)")
            }
            val total = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        if (!partial.exists() || partial.length() == 0L) throw IOException("The update file was empty")
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) throw IOException("Could not save the update file")
        onProgress(100)
        return target
    }

    /** Intent that opens the system installer for a downloaded APK. */
    fun installIntent(context: Context, apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Whether Android allows this app to start the package installer. */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Screen where the user grants "install unknown apps" for BrowserX. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the permission screen, falling back to app details on OEMs without it. */
    fun openInstallPermissionSettings(context: Context) {
        val candidates = listOf(
            installPermissionIntent(context),
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // try the next candidate
            }
        }
    }
}
