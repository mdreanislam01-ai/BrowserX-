package com.browserx.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TabOverview(
    session: BrowserSession,
    profiles: List<BrowserProfile>,
    profile: BrowserProfile,
    settings: AppSettings,
    chooseProfile: (String) -> Unit,
    manageProfiles: () -> Unit,
    layout: (TabViewMode) -> Unit,
    onBack: () -> Unit,
) {
    var privateMode by remember(profile.id) { mutableStateOf(session.active.isPrivate) }
    val tabs = session.tabs.filter { it.isPrivate == privateMode }.sortedByDescending { it.isPinned }
    var profileMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(sky(privateMode)).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            Modifier.padding(top = 8.dp),
            shape = CircleShape,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(onClick = { profileMenu = true }) {
                        Text("${profile.emoji}  ${profile.name}", maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, "Switch profile")
                    }
                    DropdownMenu(profileMenu, { profileMenu = false }) {
                        profiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.emoji}  ${p.name}") },
                                onClick = {
                                    profileMenu = false
                                    chooseProfile(p.id)
                                },
                                trailingIcon = {
                                    if (p.id == profile.id) Icon(Icons.Default.Check, "Current profile")
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Manage profiles") },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                            onClick = {
                                profileMenu = false
                                manageProfiles()
                            },
                        )
                    }
                }
                IconToggleButton(checked = privateMode, onCheckedChange = { privateMode = it }) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        if (privateMode) "Show regular tabs" else "Show private tabs",
                        tint =
                            if (privateMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "${tabs.size} ${if (privateMode) "private " else ""}${if (tabs.size == 1) "tab" else "tabs"}",
            Modifier.padding(vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val cover = settings.tabViewMode == TabViewMode.COVER_FLOW
            if (tabs.isEmpty())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (privateMode) Icons.Default.VisibilityOff else Icons.Default.Tab,
                        null,
                        Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(if (privateMode) "No private tabs" else "No open tabs")
                    Text("Tap + to open one", style = MaterialTheme.typography.bodySmall)
                }
            else {
                val select: (BrowserTab) -> Unit = {
                    session.select(it.id)
                    onBack()
                }
                when (settings.tabViewMode) {
                    TabViewMode.COVER_FLOW ->
                        key(privateMode) {
                            val pager =
                                rememberPagerState(
                                    initialPage =
                                        tabs.indexOfFirst { it.id == session.activeId }.coerceAtLeast(0),
                                    pageCount = { tabs.size },
                                )
                            val cardWidth = minOf(maxWidth * .73f, 480.dp)
                            HorizontalPager(
                                state = pager,
                                contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                                pageSpacing = 12.dp,
                                key = { tabs[it].id },
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { index ->
                                val t = tabs[index]
                                val distance =
                                    abs(pager.currentPage - index + pager.currentPageOffsetFraction)
                                        .coerceAtMost(1f)
                                val haptic = LocalHapticFeedback.current
                                var drag by remember(t.id) { mutableFloatStateOf(0f) }
                                val y by animateFloatAsState(drag, spring(), label = "Tab dismissal")
                                val swipe =
                                    Modifier.pointerInput(t.id, t.isPinned, settings.hapticsEnabled) {
                                        if (!t.isPinned)
                                            detectVerticalDragGestures(
                                                onDragStart = { drag = 0f },
                                                onDragCancel = { drag = 0f },
                                                onDragEnd = {
                                                    if (drag < -64.dp.toPx()) {
                                                        if (settings.hapticsEnabled)
                                                            haptic.performHapticFeedback(
                                                                HapticFeedbackType.LongPress,
                                                            )
                                                        session.close(setOf(t.id))
                                                    }
                                                    drag = 0f
                                                },
                                            ) { change, dy ->
                                                change.consume()
                                                drag = (drag + dy).coerceAtMost(0f)
                                            }
                                    }
                                PreviewCard(
                                    t,
                                    session,
                                    cover,
                                    Modifier.fillMaxWidth()
                                        .fillMaxHeight(.78f)
                                        .graphicsLayer {
                                            scaleX = 1f - .10f * distance
                                            scaleY = scaleX
                                            translationY = y
                                        }
                                        .then(swipe),
                                    { select(t) },
                                )
                            }
                        }
                    TabViewMode.GRID ->
                        LazyVerticalGrid(
                            GridCells.Adaptive(148.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(tabs, key = { it.id }) { t ->
                                PreviewCard(t, session, cover, Modifier.aspectRatio(.73f), { select(t) })
                            }
                        }
                    TabViewMode.LIST ->
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            items(tabs, key = { it.id }) { t ->
                                Surface(
                                    onClick = { select(t) },
                                    shape = RoundedCornerShape(18.dp),
                                    color =
                                        if (t.id == session.activeId)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TabIcon(t, session)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                t.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(
                                                hostOf(t.url).ifBlank { "Start page" },
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                            )
                                        }
                                        TabButtons(t, session)
                                    }
                                }
                            }
                        }
                }
            }
        }
        Surface(
            Modifier.padding(bottom = 12.dp, top = 6.dp),
            shape = CircleShape,
            shadowElevation = 6.dp,
            tonalElevation = 4.dp,
        ) {
            Row(Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to browsing") }
                FilledTonalIconButton(onClick = { if (session.add(privateMode)) onBack() }) {
                    Icon(Icons.Default.Add, if (privateMode) "New private tab" else "New tab")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Tab options") }
                    DropdownMenu(menu, { menu = false }) {
                        TabViewMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label) },
                                leadingIcon = {
                                    Icon(
                                        when (m) {
                                            TabViewMode.COVER_FLOW -> Icons.Default.ViewCarousel
                                            TabViewMode.GRID -> Icons.Default.GridView
                                            TabViewMode.LIST -> Icons.Default.ViewList
                                        },
                                        null,
                                    )
                                },
                                trailingIcon = {
                                    if (m == settings.tabViewMode) Icon(Icons.Default.Check, "Selected")
                                },
                                onClick = {
                                    menu = false
                                    layout(m)
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Close unpinned tabs") },
                            enabled = tabs.any { !it.isPinned },
                            onClick = {
                                menu = false
                                confirm = true
                            },
                        )
                    }
                }
            }
        }
    }
    if (confirm)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Close ${tabs.count { !it.isPinned }} tabs?") },
            text = { Text("Pinned tabs, other profiles and the other browsing mode are not affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        session.close(tabs.map { it.id }.toSet())
                    },
                ) {
                    Text("Close tabs")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
}

@Composable
private fun TabIcon(t: BrowserTab, session: BrowserSession) {
    val icon = session.icons[t.id]
    if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(22.dp))
    else
        Icon(
            if (t.isPrivate) Icons.Default.VisibilityOff else Icons.Default.Public,
            null,
            Modifier.size(22.dp),
        )
}

@Composable
private fun TabButtons(t: BrowserTab, session: BrowserSession) {
    IconButton(onClick = { session.pin(t.id) }, modifier = Modifier.size(36.dp)) {
        Icon(
            Icons.Default.PushPin,
            if (t.isPinned) "Unpin tab" else "Pin tab",
            Modifier.size(16.dp),
            tint =
                if (t.isPinned) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(
        onClick = { session.close(setOf(t.id)) },
        enabled = !t.isPinned,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(Icons.Default.Close, "Close tab", Modifier.size(19.dp))
    }
}

@Composable
private fun PreviewCard(
    t: BrowserTab,
    session: BrowserSession,
    cover: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(if (cover) 26.dp else 18.dp),
        shadowElevation = if (cover) 4.dp else 0.dp,
        border =
            BorderStroke(
                if (t.id == session.activeId) 2.dp else 1.dp,
                if (t.id == session.activeId) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cover) {
                    TabIcon(t, session)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    t.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TabButtons(t, session)
            }
            val preview = session.previews[t.id]
            if (preview != null && !t.isPrivate)
                Image(
                    preview.asImageBitmap(),
                    "Page preview",
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            else
                Column(
                    Modifier.fillMaxSize().background(sky(t.isPrivate)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BrandOrb(t.isPrivate, Modifier.size(if (cover) 58.dp else 38.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (t.isPrivate) "Private tab" else hostOf(t.url).ifBlank { "BrowserX³" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (t.isPrivate) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
        }
    }
}
