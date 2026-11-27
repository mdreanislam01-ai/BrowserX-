package com.browserx.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.math.roundToInt

/** Each private session uses a fresh WebView storage profile; never fall back to the regular cookie jar. */
internal object SiteStorage {
    val isolated
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    private const val PREFIX = "bx-temporary-"
    private val inUse = mutableSetOf<String>()

    fun temporary() = (PREFIX + UUID.randomUUID()).also { inUse.add(it) }

    fun release(name: String) {
        inUse.remove(name)
        cleanUnused()
    }

    fun cleanUnused() {
        if (!isolated) return
        val store = ProfileStore.getInstance()
        store.allProfileNames
            .filter { it.startsWith(PREFIX) && it !in inUse }
            .forEach {
                // Some providers finish unloading a profile asynchronously. Retry on the next launch.
                try {
                    store.deleteProfile(it)
                } catch (_: IllegalStateException) {}
            }
    }
}

/** One active workspace. WebView listeners close over a tab ID, never the currently selected tab. */
internal class BrowserSession(
    private val context: Context,
    private val store: PrefsStore,
    val profileId: String,
    private val options: () -> AppSettings,
    inMemory: TabSession?,
    private val downloaded: (DownloadItem) -> Unit,
) {
    private val restored =
        inMemory ?: if (options().restoreTabs) store.loadSession(profileId) else TabSession(emptyList(), null)
    val tabs = mutableStateListOf<BrowserTab>().apply { addAll(restored.tabs.ifEmpty { listOf(newTab()) }) }
    var activeId by mutableStateOf(restored.selected ?: tabs.first().id)
        private set

    val active
        get() = tabs.first { it.id == activeId }

    var history by mutableStateOf(store.loadHistory(profileId))
        private set

    var totalBlocked by mutableIntStateOf(store.getTotalBlocked())
        private set

    var peekUrl by mutableStateOf<String?>(null)
    var matchCount by mutableStateOf(0 to 0)
        private set

    val previews = mutableStateMapOf<String, Bitmap>()
    val icons = mutableStateMapOf<String, Bitmap>()
    private val engines = LinkedHashMap<String, WebView>(12, 0.75f, true)
    private val previewOrder = ArrayDeque<String>()
    private val regularStore = if (profileId == "default") null else "bx-workspace-$profileId"
    private var privateStore: String? = null
    val view
        get() = engines[activeId]

    private val main = Handler(Looper.getMainLooper())

    fun notify(message: String) {
        // I/O and engine callbacks may complete on a worker without a Looper.
        main.post { Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    fun save() {
        if (options().restoreTabs) store.saveSession(profileId, tabs, activeId)
    }

    private fun change(id: String, edit: (BrowserTab) -> BrowserTab) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i >= 0) tabs[i] = edit(tabs[i])
    }

    fun select(id: String) {
        if (id == activeId || tabs.none { it.id == id }) return
        capture()
        activeId = id
        peekUrl = null
        matchCount = 0 to 0
        save()
    }

    fun step(delta: Int) {
        val visible = tabs.filter { it.isPrivate == active.isPrivate }
        val i = visible.indexOfFirst { it.id == activeId }
        select(visible[(i + delta + visible.size) % visible.size].id)
    }

    fun add(privateMode: Boolean, url: String = "", trail: PageTrail = PageTrail()): Boolean {
        if (privateMode && !SiteStorage.isolated) {
            notify("Update Android System WebView to use isolated private tabs")
            return false
        }
        val dest = if (url.isEmpty()) "" else checkedUrl(url) ?: return false
        val tab = newTab(privateMode).copy(url = dest, trail = trail)
        tabs.add(tab)
        select(tab.id)
        return true
    }

    fun navigate(url: String, stop: Int? = null) {
        val dest = checkedUrl(url) ?: return
        change(activeId) {
            it.copy(url = dest, trail = if (stop != null) it.trail.copy(current = stop) else it.trail)
        }
        view?.loadUrl(dest)
        save()
    }

    private fun checkedUrl(url: String): String? {
        if (!isWebUrl(url)) {
            notify("Enter a valid web address")
            return null
        }
        return secureUrl(url, options().httpsOnlyEnabled)
    }

    fun home() {
        releaseEngine(activeId)
        previews.remove(activeId)
        icons.remove(activeId)
        change(activeId) { BrowserTab(it.id, isPrivate = it.isPrivate, isPinned = it.isPinned) }
        save()
    }

    fun pin(id: String) {
        change(id) { it.copy(isPinned = !it.isPinned) }
        save()
    }

    fun desktop() {
        change(activeId) { it.copy(desktopMode = !it.desktopMode) }
        view?.let {
            applyDesktop(it, active.desktopMode)
            it.reload()
        }
        save()
    }

    fun close(ids: Set<String>) {
        val remove = tabs.filter { it.id in ids && !it.isPinned }
        if (remove.isEmpty()) return
        val previous = active
        val index = tabs.indexOf(previous)
        remove.forEach {
            releaseEngine(it.id)
            previews.remove(it.id)
            previewOrder.remove(it.id)
            icons.remove(it.id)
        }
        tabs.removeAll(remove.toSet())
        if (tabs.isEmpty()) tabs.add(newTab())
        if (tabs.none { it.id == activeId }) {
            val adjacent = tabs[index.coerceAtMost(tabs.lastIndex)]
            activeId =
                if (adjacent.isPrivate == previous.isPrivate) adjacent.id
                else (tabs.firstOrNull { it.isPrivate == previous.isPrivate } ?: adjacent).id
        }
        if (tabs.none { it.isPrivate }) releasePrivate()
        peekUrl = null
        save()
    }

    fun clearHistory() {
        history = emptyList()
        store.saveHistory(profileId, history)
        tabs.indices.forEach { i -> tabs[i] = tabs[i].copy(trail = PageTrail()) }
        save()
    }

    fun clearData() {
        // Close tabs too: a late onPageFinished must not write cleared history back to the library.
        val cleaner = WebView(context)
        bindStorage(cleaner, false)
        val profile = if (SiteStorage.isolated) WebViewCompat.getProfile(cleaner) else null
        (profile?.cookieManager ?: CookieManager.getInstance()).removeAllCookies(null)
        (profile?.webStorage ?: WebStorage.getInstance()).deleteAllData()
        cleaner.clearCache(true)
        cleaner.destroy()
        engines.keys.toList().forEach(::releaseEngine)
        releasePrivate()
        previews.clear()
        previewOrder.clear()
        icons.clear()
        tabs.clear()
        tabs.add(newTab())
        activeId = tabs.first().id
        clearHistory()
    }

    /** Bounded, in-memory thumbnails. Private pages never produce pixels for the overview. */
    fun capture() {
        val w = view ?: return
        if (active.isPrivate || w.width <= 0 || w.height <= 0 || !w.isAttachedToWindow) return
        val scale = minOf(320f / w.width, 520f / w.height, 1f)
        val image =
            Bitmap.createBitmap(
                (w.width * scale).roundToInt().coerceAtLeast(1),
                (w.height * scale).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(image)
        canvas.scale(scale, scale)
        canvas.translate(-w.scrollX.toFloat(), -w.scrollY.toFloat())
        try {
            w.draw(canvas)
        } catch (_: IllegalArgumentException) {
            // Hardware-only layers may not support a software snapshot. Keep browsing available.
            return
        }
        previews[activeId] = image
        previewOrder.remove(activeId)
        previewOrder.addLast(activeId)
        if (previewOrder.size > 12) previews.remove(previewOrder.removeFirst())
    }

    private fun bindStorage(w: WebView, privateMode: Boolean) {
        val name =
            if (privateMode) (privateStore ?: SiteStorage.temporary().also { privateStore = it })
            else regularStore
        if (name != null && SiteStorage.isolated) WebViewCompat.setProfile(w, name)
    }

    private fun releasePrivate() {
        privateStore?.let(SiteStorage::release)
        privateStore = null
    }

    private fun releaseEngine(id: String) {
        engines.remove(id)?.let(::disposeView)
    }

    fun dispose() {
        save()
        engines.keys.toList().forEach(::releaseEngine)
        releasePrivate()
        previews.clear()
        icons.clear()
    }

    fun refreshSiteTheme() {
        // Chromium derives prefers-color-scheme from the WebView context at creation.
        engines.keys.toList().forEach(::releaseEngine)
        previews.clear()
        previewOrder.clear()
        tabs.indices.forEach { i ->
            tabs[i] = tabs[i].copy(canGoBack = false, canGoForward = false, isLoading = false)
        }
    }

    fun applyAppearance(w: WebView, darkWeb: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(w.settings, darkWeb)
    }

    private fun applyDesktop(w: WebView, enabled: Boolean) {
        w.settings.userAgentString =
            if (enabled)
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            else WebSettings.getDefaultUserAgent(context)
        w.settings.useWideViewPort = true
        w.settings.loadWithOverviewMode = enabled
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeView(privateMode: Boolean): WebView =
        WebView(
                if (options().forceDarkWebEnabled)
                    ContextThemeWrapper(context, android.R.style.Theme_Material_NoActionBar)
                else context,
            )
            .apply {
                bindStorage(this, privateMode)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                if (privateMode) settings.cacheMode = WebSettings.LOAD_NO_CACHE
                applyAppearance(this, options().forceDarkWebEnabled)
            }

    private open inner class NetworkClient(private val blocked: (String) -> Unit = {}) : WebViewClient() {
        private fun override(view: WebView, url: String, main: Boolean): Boolean {
            if (!isWebUrl(url)) return true
            val upgraded = secureUrl(url, options().httpsOnlyEnabled)
            if (url == upgraded) return false
            if (main) view.loadUrl(upgraded)
            return true
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
            override(view, request.url.toString(), request.isForMainFrame)

        @Deprecated("API 23 callback")
        override fun shouldOverrideUrlLoading(view: WebView, url: String) = override(view, url, true)

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val url = request.url.toString()
            val domain = if (options().adBlockEnabled) AdBlockEngine.matchedBlockedDomain(url) else null
            if (domain != null || (options().httpsOnlyEnabled && request.url.scheme.equals("http", true))) {
                if (domain != null) view.post { blocked(domain) }
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))
            }
            return null
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (options().cookieBannerHiderEnabled)
                view.evaluateJavascript(AdBlockEngine.COOKIE_BANNER_HIDER_JS, null)
        }
    }

    fun peekView(url: String, location: (String) -> Unit): WebView =
        makeView(active.isPrivate).apply {
            webViewClient =
                object : NetworkClient() {
                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        if (isWebUrl(url)) location(url)
                    }
                }
            loadUrl(secureUrl(url, options().httpsOnlyEnabled))
        }

    fun engine(tab: BrowserTab): WebView {
        engines[tab.id]?.let {
            return it
        }
        val id = tab.id
        val w = makeView(tab.isPrivate)
        if (engines.size >= 8) releaseEngine(engines.keys.first())
        engines[id] = w
        applyDesktop(w, tab.desktopMode)
        val indexToStop = mutableMapOf<Int, Int>()
        w.webViewClient =
            object :
                NetworkClient(
                    { domain ->
                        if (engines[id] === w) {
                            change(id) {
                                it.copy(
                                    blockedCount = it.blockedCount + 1,
                                    blockedDomains = (it.blockedDomains + domain).takeLast(50),
                                )
                            }
                            if (!tab.isPrivate) {
                                store.incrementTotalBlocked()
                                totalBlocked = store.getTotalBlocked()
                            }
                        }
                    },
                ) {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (engines[id] !== view) return
                    change(id) {
                        it.copy(
                            url = url,
                            isLoading = true,
                            progress = 0,
                            blockedCount = 0,
                            blockedDomains = emptyList(),
                            canGoBack = view.canGoBack(),
                            canGoForward = view.canGoForward(),
                        )
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (engines[id] !== view || view.url != url || !isWebUrl(url)) return
                    super.onPageFinished(view, url)
                    val title = view.title?.takeIf { it.isNotBlank() } ?: hostOf(url)
                    val index = view.copyBackForwardList().currentIndex
                    change(id) { t ->
                        val trail =
                            t.trail.visit(
                                url,
                                title,
                                indexToStop[index]?.takeIf { node ->
                                    t.trail.stops.any { it.id == node && it.url == url }
                                },
                            )
                        trail.current?.let { indexToStop[index] = it }
                        t.copy(
                            url = url,
                            title = title,
                            isLoading = false,
                            progress = 100,
                            canGoBack = view.canGoBack(),
                            canGoForward = view.canGoForward(),
                            trail = trail,
                        )
                    }
                    if (!tab.isPrivate) {
                        history =
                            (listOf(HistoryItem(url, title)) + history.filterNot { it.url == url }).take(500)
                        store.saveHistory(profileId, history)
                        save()
                    }
                }
            }
        w.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (engines[id] === view) change(id) { it.copy(progress = newProgress) }
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    if (engines[id] === view && !title.isNullOrBlank()) change(id) { it.copy(title = title) }
                }

                override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                    if (engines[id] === view && icon != null && !tab.isPrivate)
                        icons[id] = Bitmap.createScaledBitmap(icon, 48, 48, true)
                }
            }
        w.setFindListener { ordinal, count, done ->
            if (done && activeId == id) matchCount = (if (count > 0) ordinal + 1 else 0) to count
        }
        w.setOnLongClickListener {
            val hit = w.hitTestResult
            val link = hit.extra
            if (
                id == activeId &&
                    hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE &&
                    link != null &&
                    isWebUrl(link)
            ) {
                peekUrl = link
                true
            } else false
        }
        w.setDownloadListener { url, agent, disposition, type, _ ->
            if (isWebUrl(url))
                runCatching {
                    val name = URLUtil.guessFileName(url, disposition, type).substringAfterLast('/')
                    val req =
                        DownloadManager.Request(Uri.parse(secureUrl(url, options().httpsOnlyEnabled))).apply {
                            setTitle(name)
                            setMimeType(type)
                            addRequestHeader("User-Agent", agent)
                            setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                            )
                            if (Build.VERSION.SDK_INT >= 29)
                                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                        }
                    val downloadId =
                        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    if (!tab.isPrivate) downloaded(DownloadItem(downloadId, name, url))
                    notify("Downloading $name")
                }
                    .onFailure { notify("This download could not be started") }
        }
        w.loadUrl(secureUrl(tab.url, options().httpsOnlyEnabled))
        return w
    }
}

internal fun disposeView(w: WebView) {
    (w.parent as? ViewGroup)?.removeView(w)
    w.stopLoading()
    w.setOnLongClickListener(null)
    w.setDownloadListener(null)
    w.webChromeClient = null
    w.webViewClient = WebViewClient()
    w.destroy()
}
