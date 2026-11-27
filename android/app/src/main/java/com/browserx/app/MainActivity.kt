package com.browserx.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.tts.TextToSpeech
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.UUID

/** A download the WebView asked for, waiting on the legacy storage permission. */
private data class PendingDownload(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?
)

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

    // Toolbar that slides away while the page is scrolled down (Candy-style).
    val chromeHidden = remember { mutableStateOf(false) }
    // Cooldown so resizing the WebView (which happens when the bar hides) cannot make it flap.
    val chromeToggleAt = remember { mutableStateOf(0L) }
    // Bumped (throttled) while scrolling so the address bar light ring lights up.
    val glowPulses = remember { mutableStateOf(0) }
    val lastGlowAt = remember { mutableStateOf(0L) }

    // Downloadable ad filter list (EasyList / EasyPrivacy hosts).
    var filterHostCount by remember { mutableStateOf(AdBlockEngine.downloadedHostCount) }
    var filterUpdating by remember { mutableStateOf(false) }

    // In-app update state.
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()

    // Downloads on Android 9 and older need the WRITE_EXTERNAL_STORAGE runtime permission.
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }

    fun updateTab(id: String, transform: (BrowserTab) -> BrowserTab) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx != -1) {
            tabs[idx] = transform(tabs[idx])
        }
    }

    fun navigateTab(tabId: String, rawInput: String) {
        var resolved = resolveInputToUrl(rawInput, settings.searchEngine)
        if (resolved.isBlank()) return
        if (settings.httpsOnlyEnabled && resolved.startsWith("http://")) {
            resolved = "https://" + resolved.removePrefix("http://")
        }
        // Navigating away from the start page starts a fresh history, so Back returns home.
        val startingFromHome = tabs.find { it.id == tabId }?.url?.isBlank() ?: true
        updateTab(tabId) {
            it.copy(url = resolved, blockedCount = 0, blockedDomains = emptyList(), errorText = null)
        }
        chromeHidden.value = false
        webViews[tabId]?.let { wv ->
            if (startingFromHome) wv.clearHistory()
            wv.loadUrl(resolved)
        }
    }

    /** Shows the start page for [tabId] without destroying the tab. */
    fun goHome(tabId: String) {
        webViews[tabId]?.stopLoading()
        chromeHidden.value = false
        updateTab(tabId) {
            it.copy(
                url = "",
                title = "New Tab",
                isLoading = false,
                progress = 0,
                canGoBack = false,
                canGoForward = false,
                blockedCount = 0,
                blockedDomains = emptyList(),
                errorText = null
            )
        }
    }

    fun startDownload(request: PendingDownload) {
        val fileName = URLUtil.guessFileName(request.url, request.contentDisposition, request.mimeType)
        val req = DownloadManager.Request(Uri.parse(request.url)).apply {
            request.mimeType?.let { setMimeType(it) }
            request.userAgent?.let { addRequestHeader("User-Agent", it) }
            setTitle(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val dlId = dm.enqueue(req)
            downloads = listOf(DownloadItem(dlId, fileName, request.url)) + downloads
            store.saveDownloads(downloads)
            Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingDownload
        pendingDownload = null
        if (granted && request != null) {
            startDownload(request)
        } else if (!granted) {
            Toast.makeText(context, "Storage permission is needed to save downloads", Toast.LENGTH_LONG).show()
        }
    }

    fun installReadyUpdate() {
        val ready = updateState as? UpdateState.Ready ?: return
        if (Updater.canInstallPackages(context)) {
            try {
                context.startActivity(Updater.installIntent(context, ready.file))
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open the installer: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Allow BrowserX to install apps, then tap Install again", Toast.LENGTH_LONG).show()
            Updater.openInstallPermissionSettings(context)
        }
    }

    fun checkForUpdates(manual: Boolean) {
        if (updateState is UpdateState.Checking ||
            updateState is UpdateState.Downloading ||
            updateState is UpdateState.Ready
        ) {
            return
        }
        updateState = UpdateState.Checking
        uiScope.launch {
            val result: UpdateState = withContext(Dispatchers.IO) {
                try {
                    val info = Updater.fetchLatest(BuildConfig.VERSION_CODE)
                    if (info == null) UpdateState.UpToDate else UpdateState.Available(info)
                } catch (e: Exception) {
                    UpdateState.Failed(e.message ?: "Could not check for updates")
                }
            }
            updateState = result
            when {
                result is UpdateState.Available -> showUpdateDialog = true
                manual && result is UpdateState.UpToDate -> Toast.makeText(
                    context,
                    "BrowserX is up to date (build ${BuildConfig.VERSION_CODE})",
                    Toast.LENGTH_SHORT
                ).show()
                manual && result is UpdateState.Failed -> Toast.makeText(
                    context,
                    "Update check failed: ${result.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun updateFilterLists() {
        if (filterUpdating) return
        filterUpdating = true
        uiScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { FilterLists.download(context) } }
            filterUpdating = false
            result.onSuccess { count ->
                filterHostCount = count
                Toast.makeText(context, "Ad filter updated: $count domains", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Filter update failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        updateState = UpdateState.Downloading(info, 0)
        uiScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    Updater.downloadApk(context, info) { percent ->
                        updateState = UpdateState.Downloading(info, percent)
                    }
                }
                updateState = UpdateState.Ready(file)
                installReadyUpdate()
            } catch (e: Exception) {
                updateState = UpdateState.Failed(e.message ?: "Download failed")
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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

    // Back: close whatever is open -> go back in the page history -> go home -> leave the app.
    BackHandler(
        enabled = activeScreen != ActiveScreen.BROWSER ||
            showTabOverview ||
            showPrivacyXRay ||
            showFindInPage ||
            showUpdateDialog ||
            readerArticle != null ||
            activeTab.canGoBack ||
            activeTab.url.isNotBlank()
    ) {
        when {
            readerArticle != null -> {
                onStopSpeak()
                isSpeaking = false
                readerArticle = null
            }
            showUpdateDialog -> showUpdateDialog = false
            showPrivacyXRay -> showPrivacyXRay = false
            showTabOverview -> showTabOverview = false
            showFindInPage -> {
                showFindInPage = false
                webViews[activeTab.id]?.clearMatches()
            }
            activeScreen != ActiveScreen.BROWSER -> activeScreen = ActiveScreen.BROWSER
            activeTab.canGoBack -> webViews[activeTab.id]?.goBack()
            else -> goHome(activeTab.id)
        }
    }

    // Switching tabs should always show the toolbar again.
    LaunchedEffect(activeTabId) {
        chromeHidden.value = false
    }

    // Load the cached ad-filter list, then do a silent update check once per launch.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { AdBlockEngine.loadDownloadedHosts(context) }
        filterHostCount = AdBlockEngine.downloadedHostCount
        delay(1500)
        checkForUpdates(manual = false)
    }

    // "Force dark web" must apply to pages that are already open.
    LaunchedEffect(settings.forceDarkWebEnabled) {
        webViews.values.forEach { wv ->
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, settings.forceDarkWebEnabled)
                wv.reload()
            }
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
                appVersion = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                filterHostCount = filterHostCount,
                builtInFilterCount = AdBlockEngine.builtInHostCount(),
                filterUpdating = filterUpdating,
                onUpdateFilters = { updateFilterLists() },
                onClearFilters = {
                    AdBlockEngine.clearDownloadedHosts(context)
                    filterHostCount = 0
                },
                updateState = updateState,
                onCheckForUpdates = { checkForUpdates(manual = true) },
                onInstallUpdate = {
                    val available = updateState as? UpdateState.Available
                    if (available != null) downloadUpdate(available.info) else installReadyUpdate()
                },
                onOpenReleases = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(Updater.RELEASES_PAGE))
                    )
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
                        isAtTop = settings.addressBarPosition == AddressBarPosition.TOP,
                        isChromeHidden = settings.hideToolbarOnScroll && chromeHidden.value,
                        glowPulse = glowPulses.value,
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
                        onOpenSettings = { activeScreen = ActiveScreen.SETTINGS },
                        onCheckForUpdates = { checkForUpdates(manual = true) }
                    )
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                                this.settings.javaScriptEnabled = true
                                                this.settings.domStorageEnabled = true
                                                this.settings.databaseEnabled = !activeTab.isPrivate
                                                this.settings.builtInZoomControls = true
                                                this.settings.displayZoomControls = false
                                                this.settings.loadWithOverviewMode = true
                                                this.settings.useWideViewPort = activeTab.desktopMode
                                                this.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                                                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                                                        this.settings,
                                                        settings.forceDarkWebEnabled
                                                    )
                                                }

                                                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                                    val request = PendingDownload(url, userAgent, contentDisposition, mimetype)
                                                    val needsLegacyPermission =
                                                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                                            ContextCompat.checkSelfPermission(
                                                                ctx,
                                                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                            ) != PackageManager.PERMISSION_GRANTED
                                                    if (needsLegacyPermission) {
                                                        pendingDownload = request
                                                        storagePermissionLauncher.launch(
                                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                        )
                                                    } else {
                                                        startDownload(request)
                                                    }
                                                }

                                                // Candy-style chrome: slide the toolbar away while scrolling
                                                // down and bring it back when scrolling up.
                                                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                                    val delta = scrollY - oldScrollY
                                                    if (delta > 8 || delta < -8) {
                                                        val now = SystemClock.uptimeMillis()
                                                        // Light ring flash, throttled so scrolling stays smooth.
                                                        if (now - lastGlowAt.value > 300L) {
                                                            lastGlowAt.value = now
                                                            glowPulses.value = glowPulses.value + 1
                                                        }
                                                        if (settings.hideToolbarOnScroll &&
                                                            now - chromeToggleAt.value > 400L
                                                        ) {
                                                            if (delta > 12 && scrollY > 200 && !chromeHidden.value) {
                                                                chromeHidden.value = true
                                                                chromeToggleAt.value = now
                                                            } else if (delta < -12 && chromeHidden.value) {
                                                                chromeHidden.value = false
                                                                chromeToggleAt.value = now
                                                            }
                                                        }
                                                    }
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
                                                        if (url == null || url == "about:blank") return
                                                        chromeHidden.value = false
                                                        updateTab(activeTab.id) {
                                                            it.copy(
                                                                url = url,
                                                                isLoading = true,
                                                                canGoBack = view?.canGoBack() == true,
                                                                canGoForward = view?.canGoForward() == true,
                                                                errorText = null
                                                            )
                                                        }
                                                    }

                                                    override fun onReceivedError(
                                                        view: WebView?,
                                                        request: WebResourceRequest?,
                                                        error: WebResourceError?
                                                    ) {
                                                        if (request?.isForMainFrame != true) return
                                                        val description = error?.description?.toString().orEmpty()
                                                        updateTab(activeTab.id) {
                                                            it.copy(
                                                                isLoading = false,
                                                                errorText = description.ifBlank {
                                                                    "This page could not be loaded"
                                                                }
                                                            )
                                                        }
                                                    }

                                                    override fun onPageFinished(view: WebView?, url: String?) {
                                                        if (url == null || url == "about:blank") return
                                                        if (settings.cookieBannerHiderEnabled) {
                                                            view?.evaluateJavascript(AdBlockEngine.COOKIE_BANNER_HIDER_JS, null)
                                                        }
                                                        if (settings.adBlockEnabled) {
                                                            view?.evaluateJavascript(AdBlockEngine.AD_SLOT_HIDER_JS, null)
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
                                                        if (!activeTab.isPrivate && pageUrl.startsWith("http") &&
                                                            history.firstOrNull()?.url != pageUrl
                                                        ) {
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

                                                if (this.url != activeTab.url) loadUrl(activeTab.url)
                                            }
                                        }
                                    }
                                )
                            }
                            // Failed page loads get a readable card instead of a blank screen.
                            activeTab.errorText?.let { message ->
                                PageErrorCard(
                                    message = message,
                                    onRetry = {
                                        updateTab(activeTab.id) { it.copy(errorText = null) }
                                        webViews[activeTab.id]?.reload()
                                    },
                                    onHome = { goHome(activeTab.id) }
                                )
                            }
                        }

                        // Drawn last so it sits above the WebView: tapping the top edge brings
                        // the hidden toolbar back.
                        if (chromeHidden.value && activeTab.url.isNotBlank() && activeTab.errorText == null) {
                            Box(
                                modifier = Modifier
                                    .align(
                                        if (settings.addressBarPosition == AddressBarPosition.TOP) {
                                            Alignment.TopCenter
                                        } else {
                                            Alignment.BottomCenter
                                        }
                                    )
                                    .fillMaxWidth()
                                    .height(22.dp)
                                    .clickable {
                                        chromeHidden.value = false
                                        chromeToggleAt.value = SystemClock.uptimeMillis()
                                    }
                            )
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

        if (showUpdateDialog) {
            UpdateDialog(
                state = updateState,
                onDownload = { (updateState as? UpdateState.Available)?.let { downloadUpdate(it.info) } },
                onDismiss = { showUpdateDialog = false }
            )
        }
    }
}
