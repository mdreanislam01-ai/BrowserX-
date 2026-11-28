package com.browserx.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkItem>,
    onOpen: (String) -> Unit,
    onDelete: (BookmarkItem) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(bookmarks, key = { it.url }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title.ifBlank { item.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        },
                        modifier = Modifier.clickable { onOpen(item.url) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<HistoryItem>,
    onOpen: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = onClearAll) { Text("Clear") }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No history yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(history) { item ->
                    ListItem(
                        headlineContent = { Text(item.title.ifBlank { item.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Default.History, null) },
                        modifier = Modifier.clickable { onOpen(item.url) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (downloads.isNotEmpty()) {
                        TextButton(onClick = onClearAll) { Text("Clear") }
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No downloads yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(downloads) { item ->
                    ListItem(
                        headlineContent = { Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Default.FileDownload, null) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToppingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Toppings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Built-in page enhancements inspired by Candy Toppings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text("I Still Don't Care About Cookies") },
                        supportingContent = { Text("Automatically hide annoying cookie consent banners") },
                        trailingContent = {
                            Switch(
                                checked = settings.cookieBannerHiderEnabled,
                                onCheckedChange = { onUpdateSettings(settings.copy(cookieBannerHiderEnabled = it)) }
                            )
                        }
                    )
                }
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text("Ad & Tracker Blocker") },
                        supportingContent = { Text("Block known ad networks and analytics trackers locally") },
                        trailingContent = {
                            Switch(
                                checked = settings.adBlockEnabled,
                                onCheckedChange = { onUpdateSettings(settings.copy(adBlockEnabled = it)) }
                            )
                        }
                    )
                }
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text("Force Dark Mode on Web") },
                        supportingContent = { Text("Apply algorithmic dark theme to light websites") },
                        trailingContent = {
                            Switch(
                                checked = settings.forceDarkWebEnabled,
                                onCheckedChange = { onUpdateSettings(settings.copy(forceDarkWebEnabled = it)) }
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    profiles: List<BrowserProfile>,
    activeProfileId: String,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String, String, Long) -> Unit,
    onBack: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val emojis = listOf("🍬", "🚀", "💼", "🎮", "📚", "🌙")
    var selectedEmoji by remember { mutableStateOf("🚀") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Profile")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isSelected = profile.id == activeProfileId
                ElevatedCard(
                    onClick = { onSelectProfile(profile.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    ListItem(
                        headlineContent = { Text(profile.name, fontWeight = FontWeight.SemiBold) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(profile.accentHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(profile.emoji)
                            }
                        },
                        trailingContent = {
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = "Active")
                        }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Profile Name") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        emojis.forEach { e ->
                            FilterChip(
                                selected = selectedEmoji == e,
                                onClick = { selectedEmoji = e },
                                label = { Text(e) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        onAddProfile(newName.trim(), selectedEmoji, 0xFF006D3B)
                        showAdd = false
                        newName = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onClearBrowsingData: () -> Unit,
    appVersion: String,
    filterHostCount: Int,
    builtInFilterCount: Int,
    filterUpdating: Boolean,
    onUpdateFilters: () -> Unit,
    onClearFilters: () -> Unit,
    shieldRunning: Boolean,
    onToggleShield: (Boolean) -> Unit,
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenReleases: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onUpdateSettings(settings.copy(themeMode = mode)) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Material You Dynamic Color")
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { onUpdateSettings(settings.copy(dynamicColor = it)) }
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Address Bar Position", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddressBarPosition.displayOrder.forEach { pos ->
                            FilterChip(
                                selected = settings.addressBarPosition == pos,
                                onClick = { onUpdateSettings(settings.copy(addressBarPosition = pos)) },
                                label = { Text(pos.label) }
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default Search Engine", style = MaterialTheme.typography.bodyLarge)
                    SearchEngine.entries.forEach { engine ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onUpdateSettings(settings.copy(searchEngine = engine)) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.searchEngine == engine,
                                onClick = { onUpdateSettings(settings.copy(searchEngine = engine)) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(engine.label)
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Privacy & Gestures", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Haptic Feedback")
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(hapticsEnabled = it)) }
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = onClearBrowsingData,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Cookies, Cache & History")
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ad blocking",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Block ads & trackers", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Removes ad requests and hides leftover ad slots",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.adBlockEnabled,
                                onCheckedChange = { onUpdateSettings(settings.copy(adBlockEnabled = it)) }
                            )
                        }
                        HorizontalDivider()
                        Text(
                            "$builtInFilterCount built-in domains" +
                                if (filterHostCount > 0) " + $filterHostCount downloaded" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (filterHostCount > 0) {
                                "Extra filter lists are active. Update them when sites change."
                            } else {
                                "Download free EasyList / EasyPrivacy filter lists for stronger blocking."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (filterUpdating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onUpdateFilters,
                                enabled = !filterUpdating,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(if (filterHostCount > 0) "Update filters" else "Download filters")
                            }
                            if (filterHostCount > 0) {
                                OutlinedButton(
                                    onClick = onClearFilters,
                                    enabled = !filterUpdating,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Hide toolbar while scrolling")
                                Text(
                                    "Off: the search bar stays pinned on top",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.hideToolbarOnScroll,
                                onCheckedChange = { onUpdateSettings(settings.copy(hideToolbarOnScroll = it)) }
                            )
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "VPN · BrowserX Shield",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("BrowserX Shield", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (shieldRunning) "Connected — only BrowserX uses it"
                                    else "Off",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (shieldRunning) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = shieldRunning,
                                onCheckedChange = onToggleShield
                            )
                        }
                        Text(
                            "একটি লোকাল VPN যা শুধু BrowserX-এর DNS ট্রাফিক নেয় এবং বিজ্ঞাপন/ট্র্যাকার " +
                                "ডোমেইন ব্লক করে। কোনো সার্ভার নেই, তাই ইন্টারনেট ধীর হয় না এবং অন্য " +
                                "অ্যাপ প্রভাবিত হয় না।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Note: this is not an IP-hiding VPN — your IP address and country stay " +
                                "the same. It cannot unblock geo-blocked sites.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "About & Updates",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "BrowserX³",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Version $appVersion",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        val status = when (updateState) {
                            is UpdateState.Checking -> "Checking GitHub for a newer build…"
                            is UpdateState.UpToDate -> "You are on the latest build."
                            is UpdateState.Available -> "New build ${updateState.info.versionName} is available."
                            is UpdateState.Downloading -> "Downloading update… ${updateState.percent}%"
                            is UpdateState.Ready -> "Update downloaded — tap Install to finish."
                            is UpdateState.Failed -> "Check failed: ${updateState.message}"
                            UpdateState.Idle -> "Updates download inside the app — no repeat downloads."
                        }
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (updateState is UpdateState.Downloading) {
                            LinearProgressIndicator(
                                progress = { updateState.percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onCheckForUpdates,
                                enabled = updateState !is UpdateState.Checking &&
                                    updateState !is UpdateState.Downloading,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Check for updates")
                            }
                            when (updateState) {
                                is UpdateState.Available -> Button(
                                    onClick = onInstallUpdate,
                                    shape = RoundedCornerShape(20.dp)
                                ) { Text("Download & install") }
                                is UpdateState.Ready -> Button(
                                    onClick = onInstallUpdate,
                                    shape = RoundedCornerShape(20.dp)
                                ) { Text("Install") }
                                else -> Unit
                            }
                        }

                        TextButton(onClick = onOpenReleases) {
                            Text("Open the release page")
                        }
                    }
                }
            }
        }
    }
}

/** Shown when a newer release exists (also used by the launch-time silent check). */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        else -> null
    } ?: return

    val downloading = state is UpdateState.Downloading

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text(if (downloading) "Downloading update…" else "Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BrowserX ${info.versionName} is ready to install.")
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { (state as UpdateState.Downloading).percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (info.notes.isNotBlank()) {
                    Text(
                        info.notes.take(400),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDownload, enabled = !downloading) {
                Text(if (downloading) "Downloading…" else "Download & install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (downloading) "Hide" else "Later") }
        }
    )
}
