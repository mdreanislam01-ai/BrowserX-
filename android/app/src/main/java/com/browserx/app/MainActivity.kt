package com.browserx.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.tts.TextToSpeech
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.UUID

private enum class ActiveScreen {
    BROWSER,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    TOPPINGS,
    PROFILES,
    SETTINGS
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this, this)

        val initialUrl = intent?.dataString?.takeIf { it.startsWith("http") } ?: ""

        setContent {
            BrowserXApp(
                initialUrl = initialUrl,
                onSpeak = { text ->
                    if (ttsReady) {
                        tts?.speak(text.take(3900), TextToSpeech.QUEUE_FLUSH, null, "reader_tts")
                    }
                },
                onStopSpeak = {
                    tts?.stop()
                }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            ttsReady = true
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserXApp(
    initialUrl: String,
    onSpeak: (String) -> Unit,
    onStopSpeak: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { PrefsStore(context) }

    var settings by remember { mutableStateOf(store.loadSettings()) }
    var totalBlocked by remember { mutableStateOf(store.getTotalBlocked()) }
    var bookmarks by remember { mutableStateOf(store.loadBookmarks()) }
    var history by remember { mutableStateOf(store.loadHistory()) }
    var downloads by remember { mutableStateOf(store.loadDownloads()) }
    var profiles by remember { mutableStateOf(store.loadProfiles()) }
    var activeProfileId by remember { mutableStateOf(store.getActiveProfileId()) }

    val activeProfile = profiles.find { it.id == activeProfileId } ?: profiles.first()

    val tabs = remember {
        mutableStateListOf(
            BrowserTab(
                id = UUID.randomUUID().toString(),
                url = initialUrl
            )
        )
    }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

    val webViews = remember { mutableMapOf<String, WebView>() }

    var activeScreen by remember { mutableStateOf(ActiveScreen.BROWSER) }
    var showTabOverview by remember { mutableStateOf(false) }
    var showPrivacyXRay by remember { mutableStateOf(false) }
    var showFindInPage by remember { mutableStateOf(false) }
    var readerArticle by remember { mutableStateOf<ReaderArticle?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    fun updateTab(id: String, transform: (BrowserTab) -> BrowserTab) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx != -1) {
            tabs[idx] = transform(tabs[idx])
        }
    }

    fun navigateTab(tabId: String, rawInput: String) {
        var resolved = resolveInputToUrl(rawInput, settings.searchEngine)
        if (settings.httpsOnlyEnabled && resolved.startsWith("http://")) {
            resolved = "https://" + resolved.removePrefix("http://")
        }
        updateTab(tabId) { it.copy(url = resolved, blockedCount = 0, blockedDomains = emptyList()) }
        webViews[tabId]?.loadUrl(resolved)
    }

    fun createTab(isPrivate: Boolean, url: String = "") {
        val newTab = BrowserTab(
            id = UUID.randomUUID().toString(),
            url = url,
            isPrivate = isPrivate
        )
        tabs.add(newTab)
        activeTabId = newTab.id
        showTabOverview = false
        activeScreen = ActiveScreen.BROWSER
    }

    fun closeTab(id: String) {
        val tab = tabs.find { it.id == id } ?: return
        if (tab.isPinned && tabs.size > 1) {
            Toast.makeText(context, "Unpin tab before closing", Toast.LENGTH_SHORT).show()
            return
        }
        webViews.remove(id)?.destroy()
        val idx = tabs.indexOfFirst { it.id == id }
        tabs.removeAt(idx)
        if (tabs.isEmpty()) {
            val fresh = BrowserTab(id = UUID.randomUUID().toString())
            tabs.add(fresh)
            activeTabId = fresh.id
        } else if (activeTabId == id) {
            activeTabId = tabs[idx.coerceAtMost(tabs.lastIndex)].id
        }
    }

    BackHandler(
        enabled = activeScreen != ActiveScreen.BROWSER ||
            showTabOverview ||
            showPrivacyXRay ||
            showFindInPage ||
            readerArticle != null ||
            activeTab.canGoBack
    ) {
        when {
            readerArticle != null -> {
                onStopSpeak()
                isSpeaking = false
                readerArticle = null
            }
            showPrivacyXRay -> showPrivacyXRay = false
            showTabOverview -> showTabOverview = false
            showFindInPage -> {
                showFindInPage = false
                webViews[activeTab.id]?.clearMatches()
            }
            activeScreen != ActiveScreen.BROWSER -> activeScreen = ActiveScreen.BROWSER
            activeTab.canGoBack -> webViews[activeTab.id]?.goBack()
        }
    }

    BrowserXTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        accentColor = Color(activeProfile.accentHex),
        isPrivate = activeTab.isPrivate
    ) {
        when (activeScreen) {
            ActiveScreen.BOOKMARKS -> BookmarksScreen(
                bookmarks = bookmarks,
                onOpen = { url ->
                    activeScreen = ActiveScreen.BROWSER
                    navigateTab(activeTab.id, url)
                },
                onDelete = { item ->
                    bookmarks = bookmarks.filterNot { it.url == item.url }
                    store.saveBookmarks(bookmarks)
                },
                onBack = { activeScreen = ActiveScreen.BROWSER }
            )
            ActiveScreen.HISTORY -> HistoryScreen(
                history = history,
                onOpen = { url ->
                    activeScreen = ActiveScreen.BROWSER
                    navigateTab(activeTab.id, url)
                },
                onClearAll = {
                    history = emptyList()
                    store.saveHistory(history)
                },
                onBack = { activeScreen = ActiveScreen.BROWSER }
            )
            ActiveScreen.DOWNLOADS -> DownloadsScreen(
                downloads = downloads,
                onClearAll = {
                    downloads = emptyList()
                    store.saveDownloads(downloads)
                },
                onBack = { activeScreen = ActiveScreen.BROWSER }
            )
            ActiveScreen.TOPPINGS -> ToppingsScreen(
                settings = settings,
                onUpdateSettings = {
                    settings = it
                    store.saveSettings(it)
                },
                onBack = { activeScreen = ActiveScreen.BROWSER }
            )
            ActiveScreen.PROFILES -> ProfilesScreen(
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSelectProfile = { id ->
                    activeProfileId = id
                    store.setActiveProfileId(id)
                    activeScreen = ActiveScreen.BROWSER
                },
                onAddProfile = { name, emoji, colorHex ->
                    val created = BrowserProfile(UUID.randomUUID().toString(), name, emoji, colorHex)
                    profiles = profiles + created
                    store.saveProfiles(profiles)
                    activeProfileId = created.id
                    store.setActiveProfileId(created.id)
                },
                onBack = { activeScreen = ActiveScreen.BROWSER }
            )
            ActiveScreen.SETTINGS -> SettingsScreen(
                settings = settings,
                onUpdateSettings = {
                    settings = it
                    store.saveSettings(it)
                },
                onClearBrowsingData = {
                    CookieManager.getInstance().removeAllCookies(null)
                    webViews.values.forEach { it.clearCache(true) }
                    history = emptyList()
                    store.saveHistory(history)
                    Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                },
                onBack = { activeScreen = ActiveScreen.BROWSER }
            )
            ActiveScreen.BROWSER -> {
                val isBookmarked = bookmarks.any { it.url == activeTab.url && activeTab.url.isNotBlank() }

                val addressBar: @Composable () -> Unit = {
                    CandyAddressBar(
                        tab = activeTab,
                        tabCount = tabs.size,
                        isBookmarked = isBookmarked,
                        hapticsEnabled = settings.hapticsEnabled,
                        onNavigate = { input -> navigateTab(activeTab.id, input) },
                        onBack = { webViews[activeTab.id]?.goBack() },
                        onForward = { webViews[activeTab.id]?.goForward() },
                        onReload = {
                            val wv = webViews[activeTab.id]
                            if (activeTab.isLoading) wv?.stopLoading() else wv?.reload()
                        },
                        onOpenTabOverview = { showTabOverview = true },
                        onSwipeTab = { delta ->
                            if (tabs.size > 1) {
                                val cur = tabs.indexOfFirst { it.id == activeTab.id }
                                val next = (cur + delta + tabs.size) % tabs.size
                                activeTabId = tabs[next].id
                            }
                        },
                        onOpenPrivacyXRay = { showPrivacyXRay = true },
                        onNewTab = { priv -> createTab(priv) },
                        onToggleBookmark = {
                            if (activeTab.url.isNotBlank()) {
                                bookmarks = if (isBookmarked) {
                                    bookmarks.filterNot { it.url == activeTab.url }
                                } else {
                                    listOf(BookmarkItem(activeTab.url, activeTab.title)) + bookmarks
                                }
                                store.saveBookmarks(bookmarks)
                            }
                        },
                        onOpenReader = {
                            webViews[activeTab.id]?.evaluateJavascript(AdBlockEngine.READER_EXTRACTOR_JS) { raw ->
                                try {
                                    val clean = JSONObject(raw)
                                    val content = clean.optString("content")
                                    if (content.isNotBlank()) {
                                        readerArticle = ReaderArticle(
                                            title = clean.optString("title", activeTab.title),
                                            byline = clean.optString("byline", ""),
                                            content = content
                                        )
                                    } else {
                                        Toast.makeText(context, "No article text found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Reader mode unavailable here", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onOpenFindInPage = { showFindInPage = true },
                        onToggleDesktopMode = {
                            val nextDesktop = !activeTab.desktopMode
                            updateTab(activeTab.id) { it.copy(desktopMode = nextDesktop) }
                            webViews[activeTab.id]?.let { wv ->
                                wv.settings.userAgentString = if (nextDesktop) {
                                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                                } else null
                                wv.settings.useWideViewPort = nextDesktop
                                wv.reload()
                            }
                        },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, activeTab.url)
                                putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share link"))
                        },
                        onPrint = {
                            val wv = webViews[activeTab.id]
                            if (wv != null) {
                                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                val adapter = wv.createPrintDocumentAdapter(activeTab.title.ifBlank { "BrowserX_Page" })
                                pm.print("BrowserX Document", adapter, PrintAttributes.Builder().build())
                            }
                        },
                        onOpenBookmarks = { activeScreen = ActiveScreen.BOOKMARKS },
                        onOpenHistory = { activeScreen = ActiveScreen.HISTORY },
                        onOpenDownloads = { activeScreen = ActiveScreen.DOWNLOADS },
                        onOpenToppings = { activeScreen = ActiveScreen.TOPPINGS },
                        onOpenProfiles = { activeScreen = ActiveScreen.PROFILES },
                        onOpenSettings = { activeScreen = ActiveScreen.SETTINGS }
                    )
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    topBar = {
                        Column {
                            if (settings.addressBarPosition == AddressBarPosition.TOP) {
                                addressBar()
                            }
                            if (showFindInPage) {
                                FindInPageBar(
                                    onQueryChange = { q -> webViews[activeTab.id]?.findAllAsync(q) },
                                    onNext = { webViews[activeTab.id]?.findNext(true) },
                                    onPrev = { webViews[activeTab.id]?.findNext(false) },
                                    onClose = {
                                        showFindInPage = false
                                        webViews[activeTab.id]?.clearMatches()
                                    }
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (settings.addressBarPosition == AddressBarPosition.BOTTOM) {
                            addressBar()
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (activeTab.url.isBlank()) {
                            CandyStartPage(
                                profile = activeProfile,
                                isPrivate = activeTab.isPrivate,
                                searchEngine = settings.searchEngine,
                                bookmarks = bookmarks,
                                onSearch = { q -> navigateTab(activeTab.id, q) },
                                onOpenUrl = { u -> navigateTab(activeTab.id, u) },
                                onOpenProfiles = { activeScreen = ActiveScreen.PROFILES }
                            )
                        } else {
                            key(activeTab.id) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        webViews.getOrPut(activeTab.id) {
                                            WebView(ctx).apply {
                                                settings.javaScriptEnabled = true
                                                settings.domStorageEnabled = true
                                                settings.databaseEnabled = !activeTab.isPrivate
                                                settings.builtInZoomControls = true
                                                settings.displayZoomControls = false
                                                settings.loadWithOverviewMode = true
                                                settings.useWideViewPort = activeTab.desktopMode
                                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                                                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                                                        this.settings,
                                                        settings.forceDarkWebEnabled
                                                    )
                                                }

                                                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                                    val req = DownloadManager.Request(Uri.parse(url)).apply {
                                                        setMimeType(mimetype)
                                                        addRequestHeader("User-Agent", userAgent)
                                                        setTitle(fileName)
                                                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                                    }
                                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                    val dlId = dm.enqueue(req)
                                                    downloads = listOf(DownloadItem(dlId, fileName, url)) + downloads
                                                    store.saveDownloads(downloads)
                                                    Toast.makeText(ctx, "Downloading $fileName", Toast.LENGTH_SHORT).show()
                                                }

                                                webViewClient = object : WebViewClient() {
                                                    override fun shouldInterceptRequest(
                                                        view: WebView?,
                                                        request: WebResourceRequest?
                                                    ): WebResourceResponse? {
                                                        val reqUrl = request?.url?.toString() ?: return null
                                                        if (settings.adBlockEnabled) {
                                                            val matched = AdBlockEngine.matchedBlockedDomain(reqUrl)
                                                            if (matched != null) {
                                                                view?.post {
                                                                    updateTab(activeTab.id) { t ->
                                                                        t.copy(
                                                                            blockedCount = t.blockedCount + 1,
                                                                            blockedDomains = (t.blockedDomains + matched).takeLast(30)
                                                                        )
                                                                    }
                                                                    store.incrementTotalBlocked(1)
                                                                    totalBlocked = store.getTotalBlocked()
                                                                }
                                                                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                                            }
                                                        }
                                                        return super.shouldInterceptRequest(view, request)
                                                    }

                                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                        updateTab(activeTab.id) {
                                                            it.copy(
                                                                url = url ?: it.url,
                                                                isLoading = true,
                                                                canGoBack = view?.canGoBack() == true,
                                                                canGoForward = view?.canGoForward() == true
                                                            )
                                                        }
                                                    }

                                                    override fun onPageFinished(view: WebView?, url: String?) {
                                                        if (settings.cookieBannerHiderEnabled) {
                                                            view?.evaluateJavascript(AdBlockEngine.COOKIE_BANNER_HIDER_JS, null)
                                                        }
                                                        val pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: (url ?: "Page")
                                                        val pageUrl = url ?: activeTab.url
                                                        updateTab(activeTab.id) {
                                                            it.copy(
                                                                url = pageUrl,
                                                                title = pageTitle,
                                                                isLoading = false,
                                                                progress = 100,
                                                                canGoBack = view?.canGoBack() == true,
                                                                canGoForward = view?.canGoForward() == true
                                                            )
                                                        }
                                                        if (!activeTab.isPrivate && pageUrl.startsWith("http")) {
                                                            history = (listOf(HistoryItem(pageUrl, pageTitle)) + history).take(500)
                                                            store.saveHistory(history)
                                                        }
                                                    }
                                                }

                                                webChromeClient = object : WebChromeClient() {
                                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                        updateTab(activeTab.id) { it.copy(progress = newProgress) }
                                                    }

                                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                                        if (!title.isNullOrBlank()) {
                                                            updateTab(activeTab.id) { it.copy(title = title) }
                                                        }
                                                    }
                                                }

                                                loadUrl(activeTab.url)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTabOverview) {
            TabOverviewSheet(
                tabs = tabs,
                activeTabId = activeTab.id,
                viewMode = settings.tabViewMode,
                onChangeViewMode = { mode ->
                    settings = settings.copy(tabViewMode = mode)
                    store.saveSettings(settings)
                },
                onSelectTab = { id ->
                    activeTabId = id
                    showTabOverview = false
                },
                onCloseTab = { id -> closeTab(id) },
                onTogglePin = { id ->
                    updateTab(id) { it.copy(isPinned = !it.isPinned) }
                },
                onNewTab = { priv -> createTab(priv) },
                onCloseAll = {
                    val pinned = tabs.filter { it.isPinned }
                    tabs.filterNot { it.isPinned }.forEach { webViews.remove(it.id)?.destroy() }
                    tabs.clear()
                    if (pinned.isNotEmpty()) {
                        tabs.addAll(pinned)
                        activeTabId = pinned.first().id
                    } else {
                        val fresh = BrowserTab(id = UUID.randomUUID().toString())
                        tabs.add(fresh)
                        activeTabId = fresh.id
                    }
                    showTabOverview = false
                },
                onDismiss = { showTabOverview = false }
            )
        }

        if (showPrivacyXRay) {
            PrivacyXRaySheet(
                tab = activeTab,
                totalBlocked = totalBlocked,
                settings = settings,
                onUpdateSettings = {
                    settings = it
                    store.saveSettings(it)
                },
                onDismiss = { showPrivacyXRay = false }
            )
        }

        readerArticle?.let { article ->
            ReaderStudioSheet(
                article = article,
                isSpeaking = isSpeaking,
                onToggleSpeak = {
                    if (isSpeaking) {
                        onStopSpeak()
                        isSpeaking = false
                    } else {
                        onSpeak("${article.title}. ${article.content}")
                        isSpeaking = true
                    }
                },
                onDismiss = {
                    onStopSpeak()
                    isSpeaking = false
                    readerArticle = null
                }
            )
        }
    }
}
