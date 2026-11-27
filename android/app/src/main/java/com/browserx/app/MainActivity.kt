package com.browserx.app

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class Screen {
    BROWSE,
    TABS,
    TRAIL,
    FAVORITES,
    HISTORY,
    DOWNLOADS,
    TOPPINGS,
    PROFILES,
    SETTINGS,
    READING,
}

private data class Incoming(val url: String, val id: String = UUID.randomUUID().toString())

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var incoming by mutableStateOf<Incoming?>(null)
    private var tts: TextToSpeech? = null
    private var ready = false
    private var speechId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SiteStorage.cleanUnused()
        if (savedInstanceState == null) accept(intent)
        setContent { BrowserApp(incoming, { incoming = null }, speechId != null, ::speak, ::stopSpeaking) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }

    private fun accept(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW)
            intent.dataString?.takeIf(::isWebUrl)?.let { incoming = Incoming(it) }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        runOnUiThread { if (id == speechId) speechId = null }
                    }

                    @Deprecated("TTS platform callback")
                    override fun onError(id: String?) {
                        onDone(id)
                    }
                },
            )
            pendingSpeech?.let {
                pendingSpeech = null
                speak(it)
            }
        } else {
            pendingSpeech = null
            Toast.makeText(this, "Read aloud is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingSpeech: String? = null

    private fun speak(text: String) {
        if (!ready) {
            pendingSpeech = text
            if (tts == null) tts = TextToSpeech(this, this)
            else Toast.makeText(this, "Waiting for the speech engine", Toast.LENGTH_SHORT).show()
            return
        }
        val key = UUID.randomUUID().toString()
        speechId = key
        val parts = text.chunked(TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1))
        parts.forEachIndexed { i, part ->
            val result =
                tts?.speak(
                    part,
                    if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                    null,
                    if (i == parts.lastIndex) key else "$key-$i",
                )
            if (result != TextToSpeech.SUCCESS) {
                stopSpeaking()
                Toast.makeText(this, "Read aloud is not available", Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    private fun stopSpeaking() {
        pendingSpeech = null
        speechId = null
        tts?.stop()
    }

    override fun onDestroy() {
        stopSpeaking()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun BrowserApp(
    incoming: Incoming?,
    consume: () -> Unit,
    speaking: Boolean,
    speak: (String) -> Unit,
    stop: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { PrefsStore(context) }
    var settings by remember { mutableStateOf(store.loadSettings()) }
    var profiles by remember { mutableStateOf(store.loadProfiles()) }
    var profileId by remember { mutableStateOf(store.getActiveProfileId()) }
    val profile = profiles.firstOrNull { it.id == profileId } ?: profiles.first()
    var favorites by remember { mutableStateOf(store.loadBookmarks()) }
    var downloads by remember { mutableStateOf(store.loadDownloads()) }
    val openSessions = remember { mutableMapOf<String, TabSession>() }
    val session =
        remember(profile.id) {
            BrowserSession(context, store, profile.id, { settings }, openSessions.remove(profile.id)) { item
                ->
                downloads = (listOf(item) + downloads).take(200)
                store.saveDownloads(downloads)
            }
        }
    DisposableEffect(session) {
        onDispose {
            // Keep regular workspaces when switching even if disk restoration is turned off.
            val regular = session.tabs.filterNot { it.isPrivate }
            openSessions[session.profileId] =
                TabSession(
                    regular,
                    regular.find { it.id == session.activeId }?.id ?: regular.firstOrNull()?.id,
                )
            stop()
            session.dispose()
        }
    }
    val life = (context as ComponentActivity).lifecycle
    DisposableEffect(session, life) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    session.save()
                    session.view?.onPause()
                }
                Lifecycle.Event.ON_RESUME -> session.view?.onResume()
                else -> Unit
            }
        }
        life.addObserver(observer)
        onDispose { life.removeObserver(observer) }
    }
    var screen by remember { mutableStateOf(Screen.BROWSE) }
    fun chooseProfile(id: String) {
        profileId = id
        store.setActiveProfileId(id)
    }
    fun saveSettings(next: AppSettings) {
        val changed = next.restoreTabs != settings.restoreTabs
        if (next.forceDarkWebEnabled != settings.forceDarkWebEnabled) session.refreshSiteTheme()
        settings = next
        store.saveSettings(next)
        if (changed) {
            if (next.restoreTabs) session.save() else store.forgetSessions()
        }
    }
    fun saveFavorites(new: List<BookmarkItem>) {
        favorites = new
        store.saveBookmarks(new)
    }
    val tab = session.active
    BrowserXTheme(
        settings.themeMode,
        settings.dynamicColor,
        Color(profile.accentHex),
        isPrivate = tab.isPrivate,
    ) {
        val decor = LocalView.current
        val light = MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            WindowCompat.getInsetsController(context.window, decor).let {
                it.isAppearanceLightStatusBars = light
                it.isAppearanceLightNavigationBars = light
            }
        }
        key(session) {
            var editing by remember(tab.id) { mutableStateOf(false) }
            var menu by remember(tab.id) { mutableStateOf(false) }
            var privacy by remember(tab.id) { mutableStateOf(false) }
            var find by remember(tab.id) { mutableStateOf(false) }
            val library = remember { ReadingLibrary(context, profile.id) }
            val scope = rememberCoroutineScope()
            var article by remember(tab.id) { mutableStateOf<ReadingCopy?>(null) }
            var addFavorite by remember { mutableStateOf(false) }
            var returnTo by remember { mutableStateOf(Screen.BROWSE) }
            fun closeReader() {
                stop()
                article = null
            }
            fun closeFind() {
                find = false
                session.view?.clearMatches()
            }
            fun resetChrome() {
                editing = false
                menu = false
                closeFind()
            }
            fun page(next: Screen) {
                resetChrome()
                returnTo =
                    if (screen == Screen.TABS && next == Screen.PROFILES) Screen.TABS else Screen.BROWSE
                screen = next
            }
            fun back() {
                screen = returnTo
                returnTo = Screen.BROWSE
            }
            fun share(url: String) {
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                context.startActivity(Intent.createChooser(intent, "Share link"))
            }
            fun action(a: BrowserAction) {
                if (!a.available(tab)) return
                if (a != BrowserAction.DESKTOP) menu = false
                when (a) {
                    BrowserAction.EDIT -> {
                        closeFind()
                        editing = true
                    }
                    BrowserAction.BACK -> session.view?.goBack()
                    BrowserAction.FORWARD -> session.view?.goForward()
                    BrowserAction.RELOAD ->
                        session.view?.let { if (tab.isLoading) it.stopLoading() else it.reload() }
                    BrowserAction.HOME -> {
                        resetChrome()
                        session.home()
                    }
                    BrowserAction.NEW,
                    BrowserAction.PRIVATE -> {
                        resetChrome()
                        session.add(a == BrowserAction.PRIVATE)
                    }
                    BrowserAction.CLOSE -> {
                        resetChrome()
                        session.close(setOf(tab.id))
                    }
                    BrowserAction.PIN -> session.pin(tab.id)
                    BrowserAction.TABS -> {
                        session.capture()
                        page(Screen.TABS)
                    }
                    BrowserAction.DESKTOP -> session.desktop()
                    BrowserAction.PRIVACY -> privacy = true
                    BrowserAction.TRAIL -> page(Screen.TRAIL)
                    BrowserAction.FAVORITE ->
                        saveFavorites(
                            if (favorites.any { it.url == tab.url }) favorites.filterNot { it.url == tab.url }
                            else listOf(BookmarkItem(tab.url, tab.title)) + favorites,
                        )
                    BrowserAction.FIND -> {
                        editing = false
                        find = true
                    }
                    BrowserAction.READER ->
                        session.view?.evaluateJavascript(AdBlockEngine.READER_EXTRACTOR_JS) { raw ->
                            if (
                                screen == Screen.BROWSE &&
                                    session.activeId == tab.id &&
                                    session.active.url == tab.url
                            ) {
                                val data = runCatching { JSONObject(raw) }.getOrNull()
                                val text = data?.optString("content").orEmpty()
                                if (data != null && text.isNotBlank())
                                    article =
                                        ReadingCopy(
                                            tab.url,
                                            ReaderArticle(
                                                data.optString("title", tab.title),
                                                data.optString("byline"),
                                                text,
                                            ),
                                        )
                                else session.notify("No readable article found on this page")
                            }
                        }
                    BrowserAction.SHARE -> share(tab.url)
                    BrowserAction.PRINT ->
                        session.view?.let { w ->
                            val name = tab.title.ifBlank { "BrowserX page" }
                            (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(
                                name,
                                w.createPrintDocumentAdapter(name),
                                PrintAttributes.Builder().build(),
                            )
                        }
                    BrowserAction.READING -> page(Screen.READING)
                    BrowserAction.FAVORITES -> page(Screen.FAVORITES)
                    BrowserAction.HISTORY -> page(Screen.HISTORY)
                    BrowserAction.DOWNLOADS -> page(Screen.DOWNLOADS)
                    BrowserAction.TOPPINGS -> page(Screen.TOPPINGS)
                    BrowserAction.PROFILES -> page(Screen.PROFILES)
                    BrowserAction.SETTINGS -> page(Screen.SETTINGS)
                }
            }
            LaunchedEffect(incoming?.id) {
                incoming?.let {
                    consume()
                    closeReader()
                    resetChrome()
                    privacy = false
                    addFavorite = false
                    session.peekUrl = null
                    screen = Screen.BROWSE
                    if (tab.url.isBlank() && !tab.isPrivate) session.navigate(it.url)
                    else session.add(false, it.url)
                }
            }
            BackHandler(
                enabled =
                    screen != Screen.BROWSE ||
                        editing ||
                        find ||
                        menu ||
                        tab.canGoBack ||
                        tab.url.isNotBlank() ||
                        session.tabs.size > 1 ||
                        tab.isPrivate,
            ) {
                when {
                    screen != Screen.BROWSE -> back()
                    menu -> menu = false
                    editing -> editing = false
                    find -> closeFind()
                    tab.canGoBack -> session.view?.goBack()
                    tab.url.isNotBlank() -> session.home()
                    !tab.isPinned -> session.close(setOf(tab.id))
                    else -> action(BrowserAction.TABS)
                }
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (screen) {
                    Screen.BROWSE ->
                        Box(Modifier.fillMaxSize()) {
                            if (tab.url.isBlank())
                                StartPage(
                                    tab,
                                    favorites,
                                    settings.showFavorites,
                                    { action(BrowserAction.EDIT) },
                                    { addFavorite = true },
                                    { page(Screen.FAVORITES) },
                                    { session.navigate(it) },
                                )
                            else
                                key(tab.id) {
                                    // Keep the engine when switching tabs/screens; only its temporary
                                    // AndroidView host is released.
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                                        factory = { ctx ->
                                            FrameLayout(ctx).apply {
                                                val engine = session.engine(tab)
                                                (engine.parent as? ViewGroup)?.removeView(engine)
                                                addView(engine)
                                                engine.onResume()
                                            }
                                        },
                                        onRelease = { holder ->
                                            (holder.getChildAt(0) as? android.webkit.WebView)?.onPause()
                                            holder.removeAllViews()
                                        },
                                        update = { holder ->
                                            (holder.getChildAt(0) as? android.webkit.WebView)?.let {
                                                session.applyAppearance(it, settings.forceDarkWebEnabled)
                                            }
                                        },
                                    )
                                }
                            val bottom = settings.addressBarPosition == AddressBarPosition.BOTTOM
                            if (find)
                                Box(Modifier.align(Alignment.TopCenter).safeDrawingPadding()) {
                                    FindInPageBar(
                                        session.matchCount,
                                        { q -> session.view?.findAllAsync(q) },
                                        { session.view?.findNext(true) },
                                        { session.view?.findNext(false) },
                                        ::closeFind,
                                    )
                                }
                            if (!find)
                                Box(
                                    Modifier.align(
                                            if (bottom) Alignment.BottomCenter else Alignment.TopCenter,
                                        )
                                        .safeDrawingPadding()
                                        .imePadding(),
                                ) {
                                    BrowserChrome(
                                        session,
                                        settings,
                                        favorites,
                                        editing,
                                        menu,
                                        { editing = it },
                                        { menu = it },
                                        ::action,
                                    ) { input ->
                                        session.navigate(resolveInputToUrl(input, settings.searchEngine))
                                    }
                                }
                        }
                    Screen.TABS ->
                        TabOverview(
                            session,
                            profiles,
                            profile,
                            settings,
                            ::chooseProfile,
                            { page(Screen.PROFILES) },
                            { saveSettings(settings.copy(tabViewMode = it)) },
                            { screen = Screen.BROWSE },
                        )
                    Screen.READING -> ReadingScreen(library, { article = it }, ::back)
                    Screen.TRAIL ->
                        TrailScreen(
                            tab,
                            { s ->
                                back()
                                session.navigate(s.url, s.id)
                            },
                            { s ->
                                session.add(tab.isPrivate, s.url, tab.trail.copy(current = s.id))
                                back()
                            },
                            ::back,
                        )
                    Screen.FAVORITES ->
                        BookmarksScreen(
                            favorites,
                            { url ->
                                back()
                                if (tab.isPrivate) session.add(false, url) else session.navigate(url)
                            },
                            { item -> saveFavorites(favorites.filterNot { it.url == item.url }) },
                            { addFavorite = true },
                            ::back,
                        )
                    Screen.HISTORY ->
                        HistoryScreen(
                            session.history,
                            { url ->
                                back()
                                if (tab.isPrivate) session.add(false, url) else session.navigate(url)
                            },
                            session::clearHistory,
                            ::back,
                        )
                    Screen.DOWNLOADS ->
                        DownloadsScreen(
                            downloads,
                            { item ->
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                val uri = dm.getUriForDownloadedFile(item.id)
                                val open =
                                    if (uri == null) Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                                    else
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(
                                                uri,
                                                dm.getMimeTypeForDownloadedFile(item.id) ?: "*/*",
                                            )
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(open) }
                                    .onFailure { session.notify("No app is available to open this download") }
                            },
                            {
                                downloads = emptyList()
                                store.saveDownloads(downloads)
                            },
                            ::back,
                        )
                    Screen.TOPPINGS -> ToppingsScreen(settings, ::saveSettings, ::back)
                    Screen.PROFILES ->
                        ProfilesScreen(
                            profiles,
                            profile.id,
                            SiteStorage.isolated,
                            {
                                chooseProfile(it)
                                back()
                            },
                            { name, emoji, accent ->
                                val p = BrowserProfile(UUID.randomUUID().toString(), name, emoji, accent)
                                profiles = profiles + p
                                store.saveProfiles(profiles)
                                chooseProfile(p.id)
                                back()
                            },
                            ::back,
                        )
                    Screen.SETTINGS -> SettingsScreen(settings, ::saveSettings, session::clearData, ::back)
                }
            }
            if (privacy)
                PrivacyXRaySheet(tab, session.totalBlocked, settings, ::saveSettings, { privacy = false })
            article?.let { copy ->
                ReaderStudioSheet(
                    copy.article,
                    settings.readerTheme,
                    settings.readerSize,
                    { saveSettings(settings.copy(readerTheme = it)) },
                    { saveSettings(settings.copy(readerSize = it)) },
                    saveCopy =
                        if (tab.isPrivate || screen == Screen.READING) null
                        else
                            ({
                                scope.launch {
                                    try {
                                        library.save(copy)
                                        session.notify("Saved for offline reading")
                                    } catch (_: java.io.IOException) {
                                        session.notify("Could not save the offline copy")
                                    } catch (_: IllegalStateException) {
                                        session.notify("Offline library is full. Remove a copy first.")
                                    }
                                }
                                Unit
                            }),
                    isSpeaking = speaking,
                    onToggleSpeak = {
                        if (speaking) stop() else speak("${copy.article.title}. ${copy.article.content}")
                    },
                    onDismiss = ::closeReader,
                )
            }
            session.peekUrl?.let { url ->
                LinkPeek(
                    session,
                    url,
                    { link ->
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Link", link))
                    },
                    ::share,
                    { session.peekUrl = null },
                )
            }
            if (addFavorite)
                AddFavoriteDialog(
                    favorites,
                    { addFavorite = false },
                    { saveFavorites(listOf(it) + favorites) },
                )
        }
    }
}
