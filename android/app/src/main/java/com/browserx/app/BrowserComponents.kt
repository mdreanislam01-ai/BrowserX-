package com.browserx.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

@Composable
fun CandyAddressBar(
    tab: BrowserTab,
    tabCount: Int,
    isAtTop: Boolean,
    isChromeHidden: Boolean,
    glowPulse: Int,
    isBookmarked: Boolean,
    hapticsEnabled: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onOpenTabOverview: () -> Unit,
    onSwipeTab: (Int) -> Unit,
    onOpenPrivacyXRay: () -> Unit,
    onNewTab: (Boolean) -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenFindInPage: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenToppings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    var isEditing by remember(tab.id) { mutableStateOf(false) }
    var inputText by remember(tab.id) { mutableStateOf(tab.url) }
    var menuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // --- Candy-style morph -------------------------------------------------------------
    // The pill starts as a narrow floating chip and springs open into a full-width editor.
    val editProgress by animateFloatAsState(
        targetValue = if (isEditing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "editProgress"
    )
    val outerPadding by animateDpAsState(
        targetValue = if (isEditing) 10.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "outerPadding"
    )
    val barElevation by animateDpAsState(
        targetValue = if (isEditing) 12.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "barElevation"
    )
    val barShape = RoundedCornerShape(lerp(30.dp, 16.dp, editProgress))

    // --- Candy light ring -----------------------------------------------------------------
    // A band of coloured lights travels around the pill: bright while loading or editing,
    // a gentle shimmer at rest, and a short flash every time the page is scrolled.
    val glowTransition = rememberInfiniteTransition(label = "candyLightRing")
    val glowPhase by glowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowPhase"
    )
    var scrollGlow by remember { mutableStateOf(0f) }
    LaunchedEffect(glowPulse) {
        if (glowPulse > 0) {
            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1100, easing = LinearEasing)
            ) { value, _ -> scrollGlow = value }
        }
    }
    val baseGlow = when {
        isEditing -> 1f
        tab.isLoading -> 0.95f
        else -> 0f
    }
    val glowStrength = maxOf(baseGlow, scrollGlow)
    val glowBrush: Brush? = if (glowStrength > 0.04f) {
        RotatingGlowBrush(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary
            ),
            phase = glowPhase
        )
    } else {
        null
    }
    val glowCorner = lerp(30.dp, 16.dp, editProgress)
    val idleAlpha by animateFloatAsState(
        targetValue = if (isEditing) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "idleAlpha"
    )
    val editorAlpha by animateFloatAsState(
        targetValue = if (isEditing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "editorAlpha"
    )

    LaunchedEffect(isEditing) {
        if (isEditing) {
            inputText = tab.url
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    fun submitInput() {
        val text = inputText.trim()
        isEditing = false
        if (text.isNotEmpty()) onNavigate(text)
    }

    AnimatedVisibility(
        visible = !isChromeHidden || isEditing,
        enter = expandVertically(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            expandFrom = Alignment.Top
        ) + fadeIn(),
        exit = shrinkVertically(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            shrinkTowards = Alignment.Top
        ) + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPadding, vertical = 8.dp)
                .drawWithContent {
                    drawContent()
                    if (glowBrush == null) {
                        val hairline = 1.dp.toPx()
                        val radius = glowCorner.toPx()
                        drawRoundRect(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            topLeft = Offset(hairline / 2f, hairline / 2f),
                            size = Size(size.width - hairline, size.height - hairline),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = hairline)
                        )
                    }
                    if (glowBrush != null) {
                        val ring = (1.4f + 1.1f * glowStrength).dp.toPx()
                        val radius = glowCorner.toPx()
                        drawRoundRect(
                            brush = glowBrush,
                            topLeft = Offset(ring / 2f, ring / 2f),
                            size = Size(size.width - ring, size.height - ring),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = ring),
                            alpha = 0.45f + 0.55f * glowStrength
                        )
                        // soft bloom leaking out of the pill
                        val bloom = 10.dp.toPx()
                        drawRoundRect(
                            brush = glowBrush,
                            topLeft = Offset(-bloom / 2.5f, -bloom / 2.5f),
                            size = Size(size.width + bloom / 1.25f, size.height + bloom / 1.25f),
                            cornerRadius = CornerRadius(radius + bloom / 2.5f, radius + bloom / 2.5f),
                            style = Stroke(width = bloom),
                            alpha = 0.18f * glowStrength
                        )
                    }
                }
                .pointerInput(tab.id, isAtTop) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount },
                        onDragEnd = {
                            if (totalDragX > 90f) {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSwipeTab(-1)
                            } else if (totalDragX < -90f) {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSwipeTab(1)
                            }
                        }
                    )
                }
                .pointerInput(tab.id, isAtTop) {
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onVerticalDrag = { _, dragAmount -> totalDragY += dragAmount },
                        onDragEnd = {
                            // Top toolbar: pull down for the tab overview. Bottom toolbar: pull up.
                            val triggered = if (isAtTop) totalDragY > 70f else totalDragY < -70f
                            if (triggered && !isEditing) {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenTabOverview()
                            }
                        }
                    )
                },
            shape = barShape,
            color = if (isEditing) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
            },
            tonalElevation = barElevation,
            shadowElevation = barElevation
        ) {
            Column {
                if (tab.isLoading && tab.progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { tab.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .padding(horizontal = 6.dp)
                ) {
                    // ---------- Idle pill: shield, site chip, reload, tabs, 3-dot ----------
                    if (!isEditing) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(idleAlpha),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onOpenPrivacyXRay,
                                modifier = Modifier.size(38.dp)
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (tab.blockedCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text(
                                                    text = if (tab.blockedCount > 99) "99+" else "${tab.blockedCount}",
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (tab.isPrivate) Icons.Default.VisibilityOff else Icons.Default.Shield,
                                        contentDescription = "Privacy X-Ray",
                                        tint = if (tab.isPrivate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Site chip — tap to expand into the editor.
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        inputText = tab.url
                                        isEditing = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val display = when {
                                    tab.url.isBlank() -> "Search or type URL"
                                    else -> hostOf(tab.url).ifBlank { tab.url }
                                }
                                Icon(
                                    imageVector = if (tab.isSecure()) Icons.Default.Lock else Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = display,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (tab.url.isBlank())
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (tab.url.isBlank()) FontWeight.Normal else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (tab.url.isNotBlank()) {
                                IconButton(onClick = onReload, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        imageVector = if (tab.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                        contentDescription = if (tab.isLoading) "Stop" else "Reload",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onOpenTabOverview() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$tabCount",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box {
                                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", modifier = Modifier.size(20.dp))
                                }
                                AddressBarMenu(
                                    expanded = menuExpanded,
                                    tab = tab,
                                    isBookmarked = isBookmarked,
                                    onDismiss = { menuExpanded = false },
                                    onBack = onBack,
                                    onForward = onForward,
                                    onToggleBookmark = onToggleBookmark,
                                    onShare = onShare,
                                    onNewTab = onNewTab,
                                    onOpenReader = onOpenReader,
                                    onOpenFindInPage = onOpenFindInPage,
                                    onToggleDesktopMode = onToggleDesktopMode,
                                    onPrint = onPrint,
                                    onOpenBookmarks = onOpenBookmarks,
                                    onOpenHistory = onOpenHistory,
                                    onOpenDownloads = onOpenDownloads,
                                    onOpenToppings = onOpenToppings,
                                    onOpenProfiles = onOpenProfiles,
                                    onOpenSettings = onOpenSettings,
                                    onCheckForUpdates = onCheckForUpdates
                                )
                            }
                        }
                    }

                    // ---------- Editor: search icon, text field, clear, cancel ----------
                    if (isEditing) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(editorAlpha),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { submitInput() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                            )
                            if (inputText.isNotEmpty()) {
                                IconButton(onClick = { inputText = "" }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                            TextButton(onClick = {
                                isEditing = false
                                inputText = tab.url
                            }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** True for https URLs, used for the lock / search glyph in the address pillar. */
private fun BrowserTab.isSecure(): Boolean = url.startsWith("https://")

@Composable
private fun AddressBarMenu(
    expanded: Boolean,
    tab: BrowserTab,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit,
    onNewTab: (Boolean) -> Unit,
    onOpenReader: () -> Unit,
    onOpenFindInPage: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onPrint: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenToppings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { onDismiss(); onBack() }, enabled = tab.canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = { onDismiss(); onForward() }, enabled = tab.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = { onDismiss(); onToggleBookmark() }, enabled = tab.url.isNotBlank()) {
                Icon(
                    if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
            IconButton(onClick = { onDismiss(); onShare() }, enabled = tab.url.isNotBlank()) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("New Tab") },
            leadingIcon = { Icon(Icons.Default.Add, null) },
            onClick = { onDismiss(); onNewTab(false) }
        )
        DropdownMenuItem(
            text = { Text("New Private Tab") },
            leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
            onClick = { onDismiss(); onNewTab(true) }
        )
        if (tab.url.isNotBlank()) {
            DropdownMenuItem(
                text = { Text("Reader Studio") },
                leadingIcon = { Icon(Icons.Default.MenuBook, null) },
                onClick = { onDismiss(); onOpenReader() }
            )
            DropdownMenuItem(
                text = { Text("Find in Page") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                onClick = { onDismiss(); onOpenFindInPage() }
            )
            DropdownMenuItem(
                text = { Text(if (tab.desktopMode) "Mobile Site" else "Desktop Site") },
                leadingIcon = { Icon(Icons.Default.DesktopWindows, null) },
                onClick = { onDismiss(); onToggleDesktopMode() }
            )
            DropdownMenuItem(
                text = { Text("Print / Save PDF") },
                leadingIcon = { Icon(Icons.Default.Print, null) },
                onClick = { onDismiss(); onPrint() }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Bookmarks") },
            leadingIcon = { Icon(Icons.Default.Bookmarks, null) },
            onClick = { onDismiss(); onOpenBookmarks() }
        )
        DropdownMenuItem(
            text = { Text("History") },
            leadingIcon = { Icon(Icons.Default.History, null) },
            onClick = { onDismiss(); onOpenHistory() }
        )
        DropdownMenuItem(
            text = { Text("Downloads") },
            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
            onClick = { onDismiss(); onOpenDownloads() }
        )
        DropdownMenuItem(
            text = { Text("Toppings") },
            leadingIcon = { Icon(Icons.Default.Extension, null) },
            onClick = { onDismiss(); onOpenToppings() }
        )
        DropdownMenuItem(
            text = { Text("Profiles") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            onClick = { onDismiss(); onOpenProfiles() }
        )
        DropdownMenuItem(
            text = { Text("Check for Updates") },
            leadingIcon = { Icon(Icons.Default.SystemUpdate, null) },
            onClick = { onDismiss(); onCheckForUpdates() }
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = { Icon(Icons.Default.Settings, null) },
            onClick = { onDismiss(); onOpenSettings() }
        )
    }
}

@Composable
fun CandyStartPage(
    profile: BrowserProfile,
    isPrivate: Boolean,
    searchEngine: SearchEngine,
    bookmarks: List<BookmarkItem>,
    onSearch: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenProfiles: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val defaultLinks = remember {
        listOf(
            "Google" to "https://www.google.com",
            "Wikipedia" to "https://www.wikipedia.org",
            "GitHub" to "https://github.com",
            "YouTube" to "https://m.youtube.com",
            "Reddit" to "https://www.reddit.com",
            "Hacker News" to "https://news.ycombinator.com"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            AssistChip(
                onClick = onOpenProfiles,
                label = { Text("${profile.emoji} ${profile.name}") },
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(Modifier.height(36.dp))

        // Brand / Mode Header
        Text(
            text = if (isPrivate) "Private Browsing" else "BrowserX³",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isPrivate)
                "History and site data are not saved"
            else
                "Gesture-first • Local Privacy • ${searchEngine.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        // Expressive Search Pill
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search ${searchEngine.label} or enter URL") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (query.isNotBlank()) onSearch(query)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        // Favorites / Quick Links
        Text(
            text = if (bookmarks.isNotEmpty()) "Bookmarks" else "Quick Links",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        val itemsToShow: List<Pair<String, String>> = if (bookmarks.isNotEmpty()) {
            bookmarks.take(8).map { (it.title.ifBlank { hostOf(it.url) }) to it.url }
        } else {
            defaultLinks
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsToShow.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { (title, url) ->
                        ElevatedCard(
                            onClick = { onOpenUrl(url) },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title.firstOrNull()?.uppercase() ?: "W",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TabOverviewSheet(
    tabs: List<BrowserTab>,
    activeTabId: String,
    viewMode: TabViewMode,
    onChangeViewMode: (TabViewMode) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var showPrivateOnly by remember { mutableStateOf(false) }
    // Computed on every recomposition so closing / opening / pinning a tab updates the sheet.
    val filteredTabs = (if (showPrivateOnly) tabs.filter { it.isPrivate } else tabs.filter { !it.isPrivate })
        .sortedByDescending { it.isPinned }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            // Header: Normal / Private + View Mode Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showPrivateOnly,
                        onClick = { showPrivateOnly = false },
                        label = { Text("Tabs (${tabs.count { !it.isPrivate }})") }
                    )
                    FilterChip(
                        selected = showPrivateOnly,
                        onClick = { showPrivateOnly = true },
                        label = { Text("Private (${tabs.count { it.isPrivate }})") },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null, Modifier.size(16.dp)) }
                    )
                }

                IconButton(onClick = {
                    val next = TabViewMode.entries[(viewMode.ordinal + 1) % TabViewMode.entries.size]
                    onChangeViewMode(next)
                }) {
                    Icon(
                        imageVector = when (viewMode) {
                            TabViewMode.COVER_FLOW -> Icons.Default.ViewCarousel
                            TabViewMode.GRID -> Icons.Default.GridView
                            TabViewMode.LIST -> Icons.Default.ViewList
                        },
                        contentDescription = "Switch Tab View"
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tab List / Grid / Cover-Flow
            Box(modifier = Modifier.weight(1f)) {
                if (filteredTabs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (showPrivateOnly) "No private tabs open" else "No tabs open",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    when (viewMode) {
                        TabViewMode.COVER_FLOW -> {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredTabs, key = { it.id }) { item ->
                                    TabCard(
                                        tab = item,
                                        isActive = item.id == activeTabId,
                                        modifier = Modifier
                                            .width(260.dp)
                                            .fillMaxHeight(0.85f),
                                        onSelect = { onSelectTab(item.id) },
                                        onClose = { onCloseTab(item.id) },
                                        onTogglePin = { onTogglePin(item.id) }
                                    )
                                }
                            }
                        }
                        TabViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredTabs, key = { it.id }) { item ->
                                    TabCard(
                                        tab = item,
                                        isActive = item.id == activeTabId,
                                        modifier = Modifier.height(170.dp),
                                        onSelect = { onSelectTab(item.id) },
                                        onClose = { onCloseTab(item.id) },
                                        onTogglePin = { onTogglePin(item.id) }
                                    )
                                }
                            }
                        }
                        TabViewMode.LIST -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredTabs, key = { it.id }) { item ->
                                    ListItem(
                                        headlineContent = {
                                            Text(item.title.ifBlank { "New Tab" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        supportingContent = {
                                            Text(
                                                item.url.ifBlank { "Start Page" },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingContent = {
                                            Icon(
                                                if (item.isPinned) Icons.Default.PushPin else Icons.Default.Public,
                                                contentDescription = null,
                                                tint = if (item.id == activeTabId) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                            )
                                        },
                                        trailingContent = {
                                            Row {
                                                IconButton(onClick = { onTogglePin(item.id) }) {
                                                    Icon(Icons.Default.PushPin, contentDescription = "Pin")
                                                }
                                                IconButton(onClick = { onCloseTab(item.id) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (item.id == activeTabId)
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { onSelectTab(item.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCloseAll) {
                    Text("Close All")
                }
                Button(
                    onClick = { onNewTab(showPrivateOnly) },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (showPrivateOnly) "New Private Tab" else "New Tab")
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onTogglePin: () -> Unit
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
    ElevatedCard(
        onClick = onSelect,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.border(2.dp, borderColor, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pin",
                        tint = if (tab.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            Column {
                Text(
                    text = tab.title.ifBlank { "New Tab" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hostOf(tab.url).ifBlank { "Start Page" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyXRaySheet(
    tab: BrowserTab,
    totalBlocked: Int,
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Privacy X-Ray", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = hostOf(tab.url).ifBlank { "Local Start Page" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedCard(modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${tab.blockedCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Blocked here", style = MaterialTheme.typography.labelMedium)
                    }
                }
                ElevatedCard(modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("$totalBlocked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Total blocked", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (tab.blockedDomains.isNotEmpty()) {
                Text("Blocked on this page:", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = tab.blockedDomains.distinct().take(8).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ad & Tracker Blocker")
                Switch(
                    checked = settings.adBlockEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(adBlockEnabled = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cookie Banner Hider")
                Switch(
                    checked = settings.cookieBannerHiderEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(cookieBannerHiderEnabled = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("HTTPS-Only Mode")
                Switch(
                    checked = settings.httpsOnlyEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(httpsOnlyEnabled = it)) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderStudioSheet(
    article: ReaderArticle,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit,
    onDismiss: () -> Unit
) {
    var readerTheme by remember { mutableStateOf(ReaderTheme.LIGHT) }
    var fontSize by remember { mutableStateOf(17) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = readerTheme.bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
        ) {
            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReaderTheme.entries.forEach { t ->
                        FilterChip(
                            selected = readerTheme == t,
                            onClick = { readerTheme = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (fontSize > 13) fontSize -= 2 }) {
                        Text("A-", color = readerTheme.textColor, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { if (fontSize < 26) fontSize += 2 }) {
                        Text("A+", color = readerTheme.textColor, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onToggleSpeak) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = readerTheme.textColor
                        )
                    }
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = article.title,
                    fontSize = (fontSize + 6).sp,
                    fontWeight = FontWeight.Bold,
                    color = readerTheme.textColor
                )
                if (article.byline.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = article.byline,
                        fontSize = (fontSize - 2).sp,
                        color = readerTheme.textColor.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = article.content,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.55f).sp,
                    color = readerTheme.textColor
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun FindInPageBar(
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                placeholder = { Text("Find in page") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }
}

/** Overlay shown when the main frame fails to load, instead of a blank white page. */
@Composable
fun PageErrorCard(
    message: String,
    onRetry: () -> Unit,
    onHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Can't reach this page",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry, shape = RoundedCornerShape(20.dp)) { Text("Retry") }
                    OutlinedButton(onClick = onHome, shape = RoundedCornerShape(20.dp)) { Text("Home") }
                }
            }
        }
    }
}

/**
 * Gradient that repeats, so a band of coloured "lights" travels along the address pill.
 * [phase] is driven by an infinite transition (0..1).
 */
private class RotatingGlowBrush(
    private val colors: List<Color>,
    private val phase: Float
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val travel = (size.width + size.height).coerceAtLeast(1f)
        val shift = -travel + phase * travel * 2.6f
        return LinearGradientShader(
            from = Offset(shift, 0f),
            to = Offset(shift + travel * 0.9f, size.height),
            colors = colors,
            tileMode = TileMode.Repeated
        )
    }
}
