package com.browserx.app

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal val BrowserAction.icon: ImageVector get() = when (this) {
    BrowserAction.BACK -> Icons.AutoMirrored.Filled.ArrowBack
    BrowserAction.FORWARD -> Icons.AutoMirrored.Filled.ArrowForward
    BrowserAction.RELOAD -> Icons.Default.Refresh
    BrowserAction.FAVORITE, BrowserAction.FAVORITES -> Icons.Default.FavoriteBorder
    BrowserAction.PIN -> Icons.Default.PushPin
    BrowserAction.EDIT -> Icons.Default.Search
    BrowserAction.NEW -> Icons.Default.Add
    BrowserAction.PRIVATE -> Icons.Default.VisibilityOff
    BrowserAction.TABS -> Icons.Default.Tab
    BrowserAction.HOME -> Icons.Default.Home
    BrowserAction.CLOSE -> Icons.Default.Close
    BrowserAction.PRIVACY -> Icons.Default.Shield
    BrowserAction.TRAIL -> Icons.Default.AccountTree
    BrowserAction.READER -> Icons.Default.MenuBook
    BrowserAction.FIND -> Icons.Default.FindInPage
    BrowserAction.DESKTOP -> Icons.Default.DesktopWindows
    BrowserAction.SHARE -> Icons.Default.Share
    BrowserAction.PRINT -> Icons.Default.Print
    BrowserAction.DOWNLOADS -> Icons.Default.FileDownload
    BrowserAction.HISTORY -> Icons.Default.History
    BrowserAction.TOPPINGS -> Icons.Default.Extension
    BrowserAction.PROFILES -> Icons.Default.People
    BrowserAction.SETTINGS -> Icons.Default.Settings
}

@Composable
internal fun sky(privateMode: Boolean = false): Brush {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .4f
    val colors = when {
        privateMode -> listOf(Color(0xff30294c), Color(0xff211f38), Color(0xff141321))
        dark -> listOf(Color(0xff283551), Color(0xff292942), Color(0xff191b28))
        else -> listOf(Color(0xffc0cff5), Color(0xffdcd9fa), Color(0xfff3eafc))
    }
    return Brush.linearGradient(colors)
}

@Composable
internal fun BrandOrb(privateMode: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, shadowElevation = 8.dp, color = MaterialTheme.colorScheme.primaryContainer) {
        if (privateMode) Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.VisibilityOff, "Private tab", Modifier.fillMaxSize(.45f)) }
        else Image(painterResource(R.drawable.app_icon), "BrowserX³", modifier = Modifier.fillMaxSize().clip(CircleShape))
    }
}

@Composable
internal fun StartPage(tab: BrowserTab, favorites: List<BookmarkItem>, showFavorites: Boolean, onEdit: () -> Unit, onAdd: () -> Unit, onManage: () -> Unit, onVisit: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(sky(tab.isPrivate)).safeDrawingPadding().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 500.dp).fillMaxWidth().padding(vertical = 68.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BrandOrb(tab.isPrivate, Modifier.size(88.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Edit address", onClick = onEdit))
            Spacer(Modifier.height(12.dp))
            Text(if (tab.isPrivate) "Private browsing" else "BrowserX³", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (tab.isPrivate) {
                Text("This tab won’t be saved to your history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (showFavorites) {
                Spacer(Modifier.height(24.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .48f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FavoriteBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text("Favorites", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = onManage) { Icon(Icons.Default.Tune, "Manage favorites", Modifier.size(19.dp)) }
                            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add favorite") }
                        }
                        favorites.take(20).chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { f ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onVisit(f.url) }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        val title = f.title.ifBlank { hostOf(f.url) }
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(title.take(1).uppercase(), fontWeight = FontWeight.Bold) } }
                                        Text(title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserChrome(
    session: BrowserSession, settings: AppSettings, favorites: List<BookmarkItem>, editing: Boolean, menu: Boolean,
    onEdit: (Boolean) -> Unit, onMenu: (Boolean) -> Unit, onAction: (BrowserAction) -> Unit, onInput: (String) -> Unit
) {
    val tab = session.active
    val favorite = favorites.any { it.url == tab.url }
    val focus = remember { FocusRequester() }
    val ime = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    var field by remember(tab.id, editing) { mutableStateOf(TextFieldValue(tab.url, TextRange(0, tab.url.length))) }
    val matches = if (editing) addressMatches(field.text, tab, session.tabs, favorites, session.history, settings.localSuggestions) else emptyList()
    fun finishEdit() { ime?.hide(); onEdit(false) }
    fun choose(match: AddressMatch) {
        finishEdit()
        when {
            match.action != null -> onAction(match.action)
            match.tabId != null -> session.select(match.tabId)
            else -> session.navigate(match.url)
        }
    }
    fun submit() {
        if (field.text.isBlank()) { finishEdit(); return }
        if (field.text.trimStart().startsWith('>')) {
            matches.firstOrNull()?.let(::choose) ?: session.notify("No matching command")
        } else {
            finishEdit(); onInput(field.text)
        }
    }
    LaunchedEffect(editing, tab.id) { if (editing) { focus.requestFocus(); ime?.show() } }
    val suggestions: @Composable () -> Unit = {
        if (editing) {
            Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 4.dp, shadowElevation = 5.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = 210.dp).verticalScroll(rememberScrollState()).padding(6.dp)) {
                    if (matches.isEmpty()) {
                        val commands = field.text.trimStart().startsWith('>')
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { field = TextFieldValue(">", TextRange(1)) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(if (commands) "No match · show all commands" else "Type > for browser commands", style = MaterialTheme.typography.bodySmall)
                        }
                    } else matches.forEach { m ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { choose(m) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(m.action?.icon ?: if (m.tabId != null) Icons.Default.Tab else Icons.Default.Public, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                            Column { Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium); Text(m.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
    val bottom = settings.addressBarPosition == AddressBarPosition.BOTTOM
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        val wide = maxWidth >= 576.dp // 600 dp window minus outer insets
        val gesture = Modifier.pointerInput(tab.id, editing, settings.hapticsEnabled, bottom, wide) {
            if (!editing) {
                var x = 0f; var y = 0f
                val end = {
                    val overview = (if (bottom) -y else y) > 40.dp.toPx() && abs(y) > abs(x)
                    val switch = !wide && abs(x) > 48.dp.toPx() && abs(x) > abs(y)
                    if ((overview || switch) && settings.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (overview) onAction(BrowserAction.TABS) else if (switch) session.step(if (x > 0) -1 else 1)
                }
                if (wide) detectVerticalDragGestures(onDragStart = { y = 0f }, onDragEnd = end) { _, dy -> y += dy }
                else detectDragGestures(onDragStart = { x = 0f; y = 0f }, onDragEnd = end) { change, d -> change.consume(); x += d.x; y += d.y }
            }
        }
        Column(Modifier.widthIn(max = 940.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (bottom) suggestions()
            Surface(Modifier.fillMaxWidth().then(gesture), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .96f), shadowElevation = 8.dp,
                border = BorderStroke(if (tab.isLoading) 2.dp else 1.dp, if (tab.isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!editing) IconButton(onClick = { onAction(BrowserAction.TABS) }) {
                        Box(Modifier.size(25.dp).border(1.8.dp, LocalContentColor.current, RoundedCornerShape(7.dp)).semantics { contentDescription = "Tab overview" }, contentAlignment = Alignment.Center) {
                            Text("${session.tabs.count { it.isPrivate == tab.isPrivate }}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(Modifier.weight(1f), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        if (editing) Row(Modifier.heightIn(min = 44.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp))
                            BasicTextField(value = field, onValueChange = { field = it }, modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Address input" },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrect = false),
                                keyboardActions = KeyboardActions(onGo = { submit() }), decorationBox = { input -> Box { if (field.text.isEmpty()) Text("Search or enter an address", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); input() } })
                            if (field.text.isNotEmpty()) IconButton(onClick = { field = TextFieldValue("") }) { Icon(Icons.Default.Close, "Clear address", Modifier.size(18.dp)) }
                        } else if (wide) {
                            val group = session.tabs.filter { it.isPrivate == tab.isPrivate }; val state = rememberLazyListState()
                            LaunchedEffect(tab.id) { state.scrollToItem(group.indexOfFirst { it.id == tab.id }.coerceAtLeast(0)) }
                            LazyRow(state = state, modifier = Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                                items(group, key = { it.id }) { t -> Surface(onClick = { if (t.id == tab.id) onEdit(true) else session.select(t.id) }, shape = CircleShape, color = if (t.id == tab.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                                    Text(t.title, Modifier.widthIn(max = 148.dp).padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } }
                            }
                        } else Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tab.url.isNotBlank() || tab.isPrivate) IconButton(onClick = { onAction(BrowserAction.PRIVACY) }, modifier = Modifier.width(38.dp)) { Icon(if (tab.isPrivate) Icons.Default.VisibilityOff else Icons.Default.Shield, "Privacy X-Ray", Modifier.size(17.dp)) }
                            Box(Modifier.weight(1f).heightIn(min = 44.dp).combinedClickable(onClickLabel = "Edit address", onClick = { onEdit(true) }, onLongClick = { if (tab.url.isNotBlank()) onAction(BrowserAction.READER) else onEdit(true) }).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                                Text(hostOf(tab.url).ifBlank { "Search or enter an address" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (editing) IconButton(onClick = { if (field.text.isBlank()) finishEdit() else submit() }) { Icon(if (field.text.isBlank()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowForward, if (field.text.isBlank()) "Cancel editing" else "Go") }
                    else {
                        IconButton(onClick = { onAction(BrowserAction.NEW) }) { Icon(Icons.Default.Add, "New tab") }
                        Box {
                            IconButton(onClick = { onMenu(true) }) { Icon(Icons.Default.MoreVert, "More options") }
                            if (settings.floatingMenu) DropdownMenu(expanded = menu, onDismissRequest = { onMenu(false) }, modifier = Modifier.widthIn(max = 320.dp)) {
                                MenuEntries(tab, favorite, onAction); MenuFooter(tab, favorite, onAction)
                            }
                        }
                    }
                }
            }
            if (!bottom) suggestions()
        }
    }
    if (!settings.floatingMenu && menu) ModalBottomSheet(onDismissRequest = { onMenu(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(horizontal = 16.dp)) {
            Surface(onClick = { onAction(BrowserAction.EDIT) }, color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, Modifier.size(19.dp)); Spacer(Modifier.width(10.dp)); Text(hostOf(tab.url).ifBlank { "Search or enter an address" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { MenuEntries(tab, favorite, onAction) }
            HorizontalDivider(); MenuFooter(tab, favorite, onAction)
        }
    }
}

@Composable
private fun MenuEntries(tab: BrowserTab, favorite: Boolean, act: (BrowserAction) -> Unit) {
    val groups = listOf(
        "Tabs" to listOf(BrowserAction.NEW, BrowserAction.PRIVATE, BrowserAction.TABS, BrowserAction.CLOSE),
        "Page" to listOf(BrowserAction.DESKTOP, BrowserAction.FIND, BrowserAction.READER, BrowserAction.TRAIL, BrowserAction.PRIVACY, BrowserAction.SHARE, BrowserAction.PRINT, BrowserAction.HOME),
        "Browser" to listOf(BrowserAction.FAVORITES, BrowserAction.DOWNLOADS, BrowserAction.HISTORY, BrowserAction.TOPPINGS, BrowserAction.PROFILES, BrowserAction.SETTINGS)
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(BrowserAction.FAVORITES, BrowserAction.DOWNLOADS, BrowserAction.HISTORY, BrowserAction.SETTINGS).forEach { a -> IconButton(onClick = { act(a) }) { Icon(a.icon, a.label) } }
    }
    groups.forEach { (title, actions) ->
        Text(title, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            actions.filter { it.available(tab) || it == BrowserAction.CLOSE }.forEach { a ->
                Row(Modifier.fillMaxWidth().clickable(enabled = a.available(tab)) { act(a) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(a.icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(14.dp)); Text(a.title(tab, favorite), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (a == BrowserAction.DESKTOP) Switch(tab.desktopMode, null) else Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun MenuFooter(tab: BrowserTab, favorite: Boolean, act: (BrowserAction) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(BrowserAction.BACK, BrowserAction.FORWARD, BrowserAction.RELOAD, BrowserAction.FAVORITE, BrowserAction.PIN).forEach { a ->
            val selected = (a == BrowserAction.FAVORITE && favorite) || (a == BrowserAction.PIN && tab.isPinned)
            FilledTonalIconButton(onClick = { act(a) }, enabled = a.available(tab), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Icon(when { a == BrowserAction.RELOAD && tab.isLoading -> Icons.Default.Close; a == BrowserAction.FAVORITE && favorite -> Icons.Default.Favorite; else -> a.icon }, a.title(tab, favorite), Modifier.size(20.dp))
            }
        }
    }
}
