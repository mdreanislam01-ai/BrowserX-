package com.browserx.app

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TabUnselected
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Persistence (SharedPreferences + JSON). No accounts, no servers, no secrets.
// ---------------------------------------------------------------------------

data class BookmarkItem(val title: String, val url: String, val time: Long)
data class HistoryItem(val title: String, val url: String, val time: Long)
data class DownloadItem(val title: String, val url: String, val time: Long, val downloadId: Long)

class BrowserStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("browserx", Context.MODE_PRIVATE)

    var searchEngine: SearchEngine
        get() = SearchEngine.fromId(prefs.getString("search_engine", SearchEngine.GOOGLE.id))
        set(value) = prefs.edit().putString("search_engine", value.id).apply()

    /** "system" | "light" | "dark" */
    var darkMode: String
        get() = prefs.getString("dark_mode", "system") ?: "system"
        set(value) = prefs.edit().putString("dark_mode", value).apply()

    var adBlock: Boolean
        get() = prefs.getBoolean("ad_block", true)
        set(value) = prefs.edit().putBoolean("ad_block", value).apply()

    var bookmarks: List<BookmarkItem>
        get() = readList("bookmarks") { BookmarkItem(it.getString("title"), it.getString("url"), it.getLong("time")) }
        set(value) = writeList("bookmarks", value) {
            JSONObject().put("title", it.title).put("url", it.url).put("time", it.time)
        }

    var history: List<HistoryItem>
        get() = readList("history") { HistoryItem(it.getString("title"), it.getString("url"), it.getLong("time")) }
        set(value) = writeList("history", value) {
            JSONObject().put("title", it.title).put("url", it.url).put("time", it.time)
        }

    var downloads: List<DownloadItem>
        get() = readList("downloads") {
            DownloadItem(it.getString("title"), it.getString("url"), it.getLong("time"), it.getLong("id"))
        }
        set(value) = writeList("downloads", value) {
            JSONObject().put("title", it.title).put("url", it.url).put("time", it.time).put("id", it.downloadId)
        }

    private fun <T> readList(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val out = ArrayList<T>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) out.add(parse(arr.getJSONObject(i)))
        } catch (_: Exception) {
            // A corrupt list just starts empty; never crash the browser over storage.
        }
        return out
    }

    private fun <T> writeList(key: String, items: List<T>, toJson: (T) -> JSONObject) {
        val arr = JSONArray()
        for (item in items) arr.put(toJson(item))
        prefs.edit().putString(key, arr.toString()).apply()
    }
}

// ---------------------------------------------------------------------------
// Tabs and app state
// ---------------------------------------------------------------------------

enum class Screen { BROWSER, BOOKMARKS, HISTORY, DOWNLOADS, SETTINGS }

class BrowserTab(val id: Long, val isPrivate: Boolean) {
    var title by mutableStateOf("New tab")
    var url by mutableStateOf("")
    var progress by mutableStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var webView: WebView? = null
}

class BrowserState(context: Context) {
    private val store = BrowserStore(context.applicationContext)
    private val appContext = context.applicationContext

    val tabs = mutableStateListOf<BrowserTab>()
    var activeTabId by mutableStateOf(0L)
    var screen by mutableStateOf(Screen.BROWSER)
    var showTabs by mutableStateOf(false)
    var editingAddress by mutableStateOf(false)
    var addressText by mutableStateOf("")
    var menuOpen by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)

    var searchEngine by mutableStateOf(store.searchEngine)
    var darkMode by mutableStateOf(store.darkMode)
    var adBlock by mutableStateOf(store.adBlock)

    val bookmarks = mutableStateListOf<BookmarkItem>().apply { addAll(store.bookmarks) }
    val history = mutableStateListOf<HistoryItem>().apply { addAll(store.history) }
    val downloads = mutableStateListOf<DownloadItem>().apply { addAll(store.downloads) }

    private var nextTabId = 1L

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    fun newTab(private: Boolean = false, url: String = "", focusAddress: Boolean = true): BrowserTab {
        val tab = BrowserTab(nextTabId++, private)
        if (url.isNotEmpty()) {
            tab.url = url
            tab.title = displayTitleFor(url)
        }
        tabs.add(tab)
        activeTabId = tab.id
        screen = Screen.BROWSER
        showTabs = false
        editingAddress = focusAddress && url.isEmpty()
        addressText = url
        return tab
    }

    fun closeTab(tab: BrowserTab) {
        val wasActive = tab.id == activeTabId
        val index = tabs.indexOf(tab)
        tabs.remove(tab)
        tab.webView?.let { wv ->
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        tab.webView = null
        if (tabs.isEmpty()) {
            newTab()
            return
        }
        if (wasActive) {
            val next = tabs.getOrNull(index.coerceAtMost(tabs.size - 1)) ?: tabs.last()
            activeTabId = next.id
        }
    }

    fun switchTab(delta: Int) {
        if (tabs.size < 2) return
        val i = tabs.indexOfFirst { it.id == activeTabId }.takeIf { it >= 0 } ?: 0
        val next = (i + delta + tabs.size) % tabs.size
        activeTabId = tabs[next].id
        editingAddress = false
    }

    fun navigate(url: String) {
        val tab = activeTab ?: return
        tab.url = url
        tab.title = displayTitleFor(url)
        tab.webView?.loadUrl(url)
        editingAddress = false
        screen = Screen.BROWSER
    }

    fun navigateInput(text: String) {
        val url = resolveInputToUrl(text, searchEngine)
        if (url.isNotEmpty()) navigate(url)
    }

    fun isBookmarked(url: String): Boolean = bookmarks.any { it.url == url }

    fun toggleBookmark() {
        val tab = activeTab ?: return
        val url = tab.url
        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) return
        val existing = bookmarks.firstOrNull { it.url == url }
        if (existing != null) {
            bookmarks.remove(existing)
            toast = "Bookmark removed"
        } else {
            bookmarks.add(0, BookmarkItem(tab.title.ifEmpty { displayTitleFor(url) }, url, System.currentTimeMillis()))
            toast = "Bookmarked"
        }
        store.bookmarks = bookmarks.toList()
    }

    fun addHistory(title: String, url: String) {
        val tab = activeTab ?: return
        if (tab.isPrivate) return
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        if (history.firstOrNull()?.url == url) return
        history.add(0, HistoryItem(title.ifEmpty { displayTitleFor(url) }, url, System.currentTimeMillis()))
        while (history.size > 200) history.removeAt(history.size - 1)
        store.history = history.toList()
    }

    fun clearHistory() {
        history.clear()
        store.history = emptyList()
        toast = "History cleared"
    }

    fun clearCookies() {
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        toast = "Cookies cleared"
    }

    fun clearCache() {
        for (tab in tabs) tab.webView?.clearCache(true)
        toast = "Cache cleared"
    }

    fun removeBookmark(item: BookmarkItem) {
        bookmarks.remove(item)
        store.bookmarks = bookmarks.toList()
    }

    fun removeDownload(item: DownloadItem) {
        downloads.remove(item)
        store.downloads = downloads.toList()
    }

    fun saveSettings() {
        store.searchEngine = searchEngine
        store.darkMode = darkMode
        store.adBlock = adBlock
    }

    fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            userAgent?.let { request.addRequestHeader("User-Agent", it) }
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(request)
            downloads.add(0, DownloadItem(fileName, url, System.currentTimeMillis(), id))
            store.downloads = downloads.toList()
            toast = "Download started: $fileName"
        } catch (_: Exception) {
            toast = "Download failed to start"
        }
    }
}

fun formatTime(time: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(time))

fun downloadStatus(context: Context, downloadId: Long): String {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
    return try {
        if (!cursor.moveToFirst()) return "Removed"
        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
            DownloadManager.STATUS_SUCCESSFUL -> "Completed"
            DownloadManager.STATUS_RUNNING -> "Downloading"
            DownloadManager.STATUS_PAUSED -> "Paused"
            DownloadManager.STATUS_PENDING -> "Waiting"
            DownloadManager.STATUS_FAILED -> "Failed"
            else -> "Unknown"
        }
    } catch (_: Exception) {
        "Unknown"
    } finally {
        cursor.close()
    }
}

// ---------------------------------------------------------------------------
// Activity + theme
// ---------------------------------------------------------------------------

class MainActivity : ComponentActivity() {
    private lateinit var state: BrowserState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = BrowserState(this)
        if (state.tabs.isEmpty()) state.newTab(focusAddress = false)
        // Open links shared from other apps in a new tab.
        intent?.let { handleIntent(it) }
        setContent {
            val dark = when (state.darkMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            BrowserXTheme(dark = dark) {
                val view = LocalView.current
                LaunchedEffect(dark) {
                    val window = (view.context as Activity).window
                    WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !dark
                }
                BrowserApp(state = state, onExit = { finish() })
                state.toast?.let { message ->
                    LaunchedEffect(message) {
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        state.toast = null
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            val url = intent.dataString ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!url.isNullOrEmpty()) {
                val target = if (url.startsWith("http://") || url.startsWith("https://")) url
                else resolveInputToUrl(url, state.searchEngine)
                if (target.isNotEmpty()) {
                    val current = state.activeTab
                    if (current != null && current.url.isEmpty()) state.navigate(target)
                    else state.newTab(url = target, focusAddress = false)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        state.activeTab?.webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        state.activeTab?.webView?.onResume()
    }

    override fun onDestroy() {
        for (tab in state.tabs) {
            tab.webView?.let { wv ->
                (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                wv.destroy()
            }
        }
        super.onDestroy()
    }
}

@Composable
fun BrowserXTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8AB4FF),
            secondary = Color(0xFF7DD3FC),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1565C0),
            secondary = Color(0xFF0277BD),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------------------------------------------------------------------------
// WebView creation (one per tab, kept alive in the tab while it exists)
// ---------------------------------------------------------------------------

fun createBrowserWebView(context: Context, tab: BrowserTab, state: BrowserState): WebView {
    val webView = WebView(context)
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadsImagesAutomatically = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.setSupportZoom(true)
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.allowFileAccess = false
    settings.allowContentAccess = true
    // Dark pages: follow the app theme when the WebView supports it.
    val dark = when (state.darkMode) {
        "dark" -> true
        "light" -> false
        else -> (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        WebSettingsCompat.setForceDark(
            settings,
            if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
        )
    }
    if (dark && WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
    }

    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val scheme = request.url.scheme ?: return false
            if (scheme == "http" || scheme == "https") return false
            return try {
                context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                true
            } catch (_: Exception) {
                true
            }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (state.adBlock) {
                val host = request.url.host ?: ""
                if (isAdHost(host)) {
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            tab.loading = true
            // "about:blank" is the Home action's reset page, not a real navigation.
            if (url != "about:blank") tab.url = url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.loading = false
            tab.progress = 100
            if (url.startsWith("http://") || url.startsWith("https://")) {
                tab.url = url
                if (view.title != null && view.title!!.isNotEmpty()) tab.title = view.title!!
                state.addHistory(tab.title, url)
            }
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: android.webkit.WebResourceError
        ) {
            if (request.isForMainFrame) tab.loading = false
        }
    }

    webView.webChromeClient = object : android.webkit.WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
            tab.loading = newProgress < 100
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrEmpty()) tab.title = title
        }
    }

    webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        state.startDownload(url, userAgent, contentDisposition, mimeType)
    }

    return webView
}

@Composable
fun TabWebView(tab: BrowserTab, state: BrowserState) {
    AndroidView(
        factory = { ctx ->
            tab.webView ?: createBrowserWebView(ctx, tab, state).also { tab.webView = it }
        },
        update = { view ->
            if (tab.url.isNotEmpty() && view.url.isNullOrEmpty()) {
                view.loadUrl(tab.url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ---------------------------------------------------------------------------
// Root + browser screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(state: BrowserState, onExit: () -> Unit) {
    BackHandler(enabled = true) {
        when {
            state.showTabs -> state.showTabs = false
            state.editingAddress -> state.editingAddress = false
            state.screen != Screen.BROWSER -> state.screen = Screen.BROWSER
            state.activeTab?.webView?.canGoBack() == true -> state.activeTab?.webView?.goBack()
            state.tabs.size > 1 -> state.activeTab?.let { state.closeTab(it) }
            else -> onExit()
        }
    }

    Scaffold(
        topBar = {
            if (state.screen == Screen.BROWSER && !state.showTabs) {
                BrowserTopBar(state = state)
            }
        },
        bottomBar = {
            if (state.screen == Screen.BROWSER && !state.showTabs) {
                BrowserBottomBar(state = state)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                state.showTabs -> TabOverviewScreen(state = state)
                state.screen == Screen.BOOKMARKS -> BookmarksScreen(state = state)
                state.screen == Screen.HISTORY -> HistoryScreen(state = state)
                state.screen == Screen.DOWNLOADS -> DownloadsScreen(state = state)
                state.screen == Screen.SETTINGS -> SettingsScreen(state = state)
                else -> {
                    val tab = state.activeTab
                    if (tab == null) {
                        HomeContent(state = state)
                    } else if (tab.url.isEmpty()) {
                        HomeContent(state = state)
                    } else {
                        key(tab.id) {
                            TabWebView(tab = tab, state = state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrowserTopBar(state: BrowserState) {
    val tab = state.activeTab
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .pointerInput(tab?.id) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (total > 120f) state.switchTab(-1)
                            else if (total < -120f) state.switchTab(1)
                            total = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.toggleBookmark() }) {
                Icon(
                    imageVector = if (tab != null && state.isBookmarked(tab.url)) Icons.Filled.Star
                    else Icons.Filled.StarBorder,
                    contentDescription = "Bookmark this page"
                )
            }
            if (state.editingAddress) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                OutlinedTextField(
                    value = state.addressText,
                    onValueChange = { state.addressText = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    placeholder = { Text("Search or type a web address") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { state.navigateInput(state.addressText) })
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            state.addressText = tab?.url ?: ""
                            state.editingAddress = true
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tab?.isPrivate == true) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = "Private tab",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = when {
                            tab == null -> "BrowserX³"
                            tab.url.isEmpty() -> "Search or type a web address"
                            else -> tab.title.ifEmpty { tab.url }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp
                    )
                }
            }
            IconButton(onClick = { state.activeTab?.webView?.reload() }) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh")
            }
            Box(contentAlignment = Alignment.Center) {
                IconButton(onClick = { state.showTabs = true }) {
                    Icon(imageVector = Icons.Filled.TabUnselected, contentDescription = "Tabs")
                }
                Text(
                    text = state.tabs.size.toString(),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 14.dp, start = 14.dp)
                )
            }
        }
        if (tab?.loading == true) {
            // Thin loading bar (drawn by hand so no Material3 progress API is needed).
            val fraction = (tab.progress.coerceIn(0, 100)) / 100f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun BrowserBottomBar(state: BrowserState) {
    val tab = state.activeTab
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { tab?.webView?.goBack() },
            enabled = tab?.webView?.canGoBack() == true
        ) {
            Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
        }
        IconButton(
            onClick = { tab?.webView?.goForward() },
            enabled = tab?.webView?.canGoForward() == true
        ) {
            Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = "Forward")
        }
        IconButton(onClick = {
            val t = state.activeTab
            if (t != null) {
                t.url = ""
                t.title = "New tab"
                t.webView?.loadUrl("about:blank")
                t.webView?.clearHistory()
            }
        }) {
            Icon(imageVector = Icons.Filled.Home, contentDescription = "Home")
        }
        IconButton(onClick = { state.screen = Screen.BOOKMARKS }) {
            Icon(imageVector = Icons.Filled.Star, contentDescription = "Bookmarks")
        }
        Box {
            IconButton(onClick = { state.menuOpen = true }) {
                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(expanded = state.menuOpen, onDismissRequest = { state.menuOpen = false }) {
                DropdownMenuItem(text = { Text("New tab") }, onClick = {
                    state.menuOpen = false
                    state.newTab()
                })
                DropdownMenuItem(text = { Text("New private tab") }, onClick = {
                    state.menuOpen = false
                    state.newTab(private = true)
                })
                DropdownMenuItem(text = { Text("History") }, onClick = {
                    state.menuOpen = false
                    state.screen = Screen.HISTORY
                })
                DropdownMenuItem(text = { Text("Downloads") }, onClick = {
                    state.menuOpen = false
                    state.screen = Screen.DOWNLOADS
                })
                DropdownMenuItem(text = { Text("Share page") }, onClick = {
                    state.menuOpen = false
                    shareCurrentPage(context, state)
                })
                DropdownMenuItem(text = { Text("Settings") }, onClick = {
                    state.menuOpen = false
                    state.screen = Screen.SETTINGS
                })
            }
        }
    }
}

fun shareCurrentPage(context: Context, state: BrowserState) {
    val tab = state.activeTab ?: return
    if (tab.url.isEmpty()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, tab.url)
        putExtra(Intent.EXTRA_SUBJECT, tab.title)
    }
    context.startActivity(Intent.createChooser(intent, "Share page"))
}

@Composable
fun HomeContent(state: BrowserState) {
    var query by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "BrowserX³", fontSize = 34.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Search the web", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search or type a web address") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { state.navigateInput(query) })
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { state.navigateInput(query) }, modifier = Modifier.fillMaxWidth()) {
            Text("Search")
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(text = "Quick links", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.navigate("https://www.google.com") }) { Text("Google") }
            OutlinedButton(onClick = { state.navigate("https://www.youtube.com") }) { Text("YouTube") }
            OutlinedButton(onClick = { state.navigate("https://www.facebook.com") }) { Text("Facebook") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabOverviewScreen(state: BrowserState) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tabs (${state.tabs.size})") },
            navigationIcon = {
                IconButton(onClick = { state.showTabs = false }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close tab overview")
                }
            },
            actions = {
                IconButton(onClick = { state.newTab(private = true) }) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = "New private tab")
                }
                IconButton(onClick = { state.newTab() }) {
                    Icon(Icons.Filled.Add, contentDescription = "New tab")
                }
            }
        )
        if (state.tabs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No open tabs")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(state.tabs, key = { it.id }) { tab ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                state.activeTabId = tab.id
                                state.showTabs = false
                                state.screen = Screen.BROWSER
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tab.title.ifEmpty { "New tab" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (tab.url.isEmpty()) "New tab" else tab.url,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (tab.isPrivate) {
                                    Text(
                                        text = "Private",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { state.closeTab(tab) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close tab")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bookmarks / History / Downloads / Settings
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenBar(title: String, state: BrowserState, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = { state.screen = Screen.BROWSER }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = actions
    )
}

@Composable
fun BookmarksScreen(state: BrowserState) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenBar(title = "Bookmarks", state = state)
        if (state.bookmarks.isEmpty()) {
            EmptyState(text = "No bookmarks yet.\nTap the star on any page to save it.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.bookmarks, key = { it.url }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.navigate(item.url) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                            Text(
                                item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { state.removeBookmark(item) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove bookmark")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(state: BrowserState) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenBar(title = "History", state = state) {
            IconButton(onClick = { state.clearHistory() }, enabled = state.history.isNotEmpty()) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear history")
            }
        }
        if (state.history.isEmpty()) {
            EmptyState(text = "No history yet.\nPages you visit will appear here.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.history, key = { it.url + it.time }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.navigate(item.url) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                            Text(
                                item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatTime(item.time), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(state: BrowserState) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenBar(title = "Downloads", state = state)
        if (state.downloads.isEmpty()) {
            EmptyState(text = "No downloads yet.\nFiles you download will appear here.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.downloads, key = { it.downloadId }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                            Text(
                                item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${downloadStatus(context, item.downloadId)} · ${formatTime(item.time)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { state.removeDownload(item) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove from list")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen(state: BrowserState) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenBar(title = "Settings", state = state)
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                SettingsHeading("Search engine")
                for (engine in SearchEngine.values()) {
                    RadioRow(
                        label = engine.label,
                        selected = state.searchEngine == engine,
                        onClick = {
                            state.searchEngine = engine
                            state.saveSettings()
                        }
                    )
                }
            }
            item {
                SettingsHeading("Appearance")
                val modes = listOf("system" to "System default", "light" to "Light", "dark" to "Dark")
                for ((id, label) in modes) {
                    RadioRow(
                        label = label,
                        selected = state.darkMode == id,
                        onClick = {
                            state.darkMode = id
                            state.saveSettings()
                        }
                    )
                }
            }
            item {
                SettingsHeading("Privacy")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block ads & trackers", fontSize = 16.sp)
                        Text(
                            "Blocks well-known ad hosts",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.adBlock,
                        onCheckedChange = {
                            state.adBlock = it
                            state.saveSettings()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { state.clearHistory() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear history")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { state.clearCookies() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear cookies")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { state.clearCache() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear cache")
                }
            }
            item {
                SettingsHeading("About")
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BrowserX³", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", fontSize = 14.sp)
                        Text("Package com.browserx.app", fontSize = 14.sp)
                        Text("Engine: Android System WebView", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Private tabs don't save history. Cookies are shared with normal tabs in this version.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsHeading(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 16.sp)
        if (selected) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
