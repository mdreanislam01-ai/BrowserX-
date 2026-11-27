package com.browserx.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add favorite") } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No favorites yet · tap + to add a site",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item { LibrarySearch(query, "Search favorites") { query = it } }
                val matches = bookmarks.filter {
                    it.title.contains(query, true) || it.url.contains(query, true)
                }
                if (matches.isEmpty()) item { Text("No matching favorites", Modifier.padding(16.dp)) }
                items(matches, key = { it.url }) { item ->
                    ListItem(
                        headlineContent = {
                            Text(
                                item.title.ifBlank { item.url },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        },
                        modifier = Modifier.clickable { onOpen(item.url) },
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
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    if (confirm)
        ConfirmClear(
            "Clear this profile’s history and page trails?",
            { confirm = false },
            {
                confirm = false
                onClearAll()
            },
        )
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
                        TextButton(onClick = { confirm = true }) { Text("Clear") }
                    }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No history yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item { LibrarySearch(query, "Search history") { query = it } }
                val matches = history.filter {
                    it.title.contains(query, true) || it.url.contains(query, true)
                }
                if (matches.isEmpty()) item { Text("No matching visits", Modifier.padding(16.dp)) }
                items(matches) { item ->
                    ListItem(
                        headlineContent = {
                            Text(
                                item.title.ifBlank { item.url },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { Icon(Icons.Default.History, null) },
                        modifier = Modifier.clickable { onOpen(item.url) },
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
    onOpen: (DownloadItem) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm)
        ConfirmClear(
            "Clear the download list? Files on your device are kept.",
            { confirm = false },
            {
                confirm = false
                onClearAll()
            },
        )
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
                        TextButton(onClick = { confirm = true }) { Text("Clear") }
                    }
                },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No downloads yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(downloads) { item ->
                    ListItem(
                        headlineContent = {
                            Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { Icon(Icons.Default.FileDownload, null) },
                        modifier = Modifier.clickable { onOpen(item) },
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
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Toppings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Local page tools for Android System WebView. Reload an open page after changing a tool.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text("Cookie banner hider") },
                        supportingContent = { Text("Automatically hide annoying cookie consent banners") },
                        trailingContent = {
                            Switch(
                                checked = settings.cookieBannerHiderEnabled,
                                onCheckedChange = {
                                    onUpdateSettings(settings.copy(cookieBannerHiderEnabled = it))
                                },
                            )
                        },
                    )
                }
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text("Ad & Tracker Blocker") },
                        supportingContent = {
                            Text("Block known ad networks and analytics trackers locally")
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.adBlockEnabled,
                                onCheckedChange = { onUpdateSettings(settings.copy(adBlockEnabled = it)) },
                            )
                        },
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
                                onCheckedChange = {
                                    onUpdateSettings(settings.copy(forceDarkWebEnabled = it))
                                },
                            )
                        },
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
    isolated: Boolean,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String, String, Long) -> Unit,
    onBack: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val emojis = listOf("🍬", "🚀", "💼", "🎮", "📚", "🌙")
    var selectedEmoji by remember { mutableStateOf("🚀") }
    val accents = listOf(0xFF4B609A, 0xFF865093, 0xFF006D3B, 0xFF98501C, 0xFF006A78)
    var accent by remember { mutableLongStateOf(accents.first()) }

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
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    if (isolated) "Each profile has its own tabs, history and website logins."
                    else
                        "Tabs and history are separate. This WebView shares website logins between regular profiles.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(profiles, key = { it.id }) { profile ->
                val isSelected = profile.id == activeProfileId
                ElevatedCard(
                    onClick = { onSelectProfile(profile.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor =
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    ListItem(
                        headlineContent = { Text(profile.name, fontWeight = FontWeight.SemiBold) },
                        leadingContent = {
                            Box(
                                modifier =
                                    Modifier.size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(profile.accentHex)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(profile.emoji)
                            }
                        },
                        trailingContent = {
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = "Active")
                        },
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
                        singleLine = true,
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        emojis.forEach { e ->
                            FilterChip(
                                selected = selectedEmoji == e,
                                onClick = { selectedEmoji = e },
                                label = { Text(e) },
                            )
                        }
                    }
                    Text("Profile color", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        accents.forEachIndexed { i, color ->
                            IconButton(
                                onClick = { accent = color },
                                modifier = Modifier.size(36.dp).background(Color(color), CircleShape),
                            ) {
                                if (accent == color)
                                    Icon(Icons.Default.Check, "Selected color ${i + 1}", tint = Color.White)
                                else Text("${i + 1}", color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        if (newName.isNotBlank()) {
                            onAddProfile(newName.trim().take(32), selectedEmoji, accent)
                            showAdd = false
                            newName = ""
                        }
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            },
        )
    }
}
