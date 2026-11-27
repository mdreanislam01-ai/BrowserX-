package com.browserx.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LibrarySearch(query: String, hint: String, update: (String) -> Unit) {
    OutlinedTextField(query, update, Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 12.dp), placeholder = { Text(hint) }, leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { update("") }) { Icon(Icons.Default.Close, "Clear search") } }, singleLine = true, shape = CircleShape)
}

@Composable
internal fun ConfirmClear(message: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Clear browsing data?") }, text = { Text(message) }, confirmButton = { TextButton(onClick = confirm) { Text("Clear") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

private enum class Section(val title: String, val detail: String, val group: String, val icon: ImageVector) {
    SEARCH("Search", "Search engine and local address suggestions", "Browsing", Icons.Default.Search),
    TABS("Tabs & gestures", "Overview layout, menu style, address bar position and haptics", "Browsing", Icons.Default.ViewCarousel),
    BROWSER("Browser", "Restore tabs at startup and show favorites on the start page", "Browsing", Icons.Default.Public),
    APPEARANCE("Appearance", "Theme, dark mode and device colors", "Personalization", Icons.Default.Palette),
    PRIVACY("Privacy & protection", "Tracker blocking, HTTPS-only mode, cookies and clear browsing data", "Privacy", Icons.Default.Shield),
    ABOUT("About BrowserX³", "Installed version and browser engine", "About", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(settings: AppSettings, save: (AppSettings) -> Unit, clearData: () -> Unit, onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<Section?>(null) }; var query by rememberSaveable { mutableStateOf("") }; var confirm by remember { mutableStateOf(false) }
    fun back() { if (page != null) page = null else if (query.isNotEmpty()) query = "" else onBack() }
    BackHandler(onBack = ::back)
    Scaffold(topBar = { TopAppBar(title = { Text(page?.title ?: "Settings") }, navigationIcon = { IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        key(page) { Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            when (page) {
                null -> {
                    LibrarySearch(query, "Search settings") { query = it }
                    val matches = Section.entries.filter { "${it.title} ${it.detail}".contains(query.trim(), true) }
                    matches.groupBy { it.group }.forEach { (group, rows) ->
                        Text(group, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        rows.forEach { s -> Surface(onClick = { page = s }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(s.icon, null, Modifier.size(21.dp)) }; Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) { Text(s.title, fontWeight = FontWeight.Medium); Text(s.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                            }
                        } }
                    }
                    if (matches.isEmpty()) Text("No matching settings", Modifier.padding(16.dp))
                    Text("BrowserX³  ·  ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Section.SEARCH -> {
                    ToggleRow("Local suggestions", "Match open tabs, favorites and history as you type. No suggestions are sent to a server.", settings.localSuggestions) { save(settings.copy(localSuggestions = it)) }
                    Text("Default search engine", style = MaterialTheme.typography.titleSmall)
                    SearchEngine.entries.forEach { engine -> Row(Modifier.fillMaxWidth().clickable { save(settings.copy(searchEngine = engine)) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(engine == settings.searchEngine, null); Spacer(Modifier.width(12.dp)); Text(engine.label) } }
                }
                Section.TABS -> {
                    ChoiceRow("Tab overview", TabViewMode.entries.map { it.label }, settings.tabViewMode.ordinal) { save(settings.copy(tabViewMode = TabViewMode.entries[it])) }
                    ChoiceRow("Menu style", listOf("Bottom sheet", "Floating"), if (settings.floatingMenu) 1 else 0) { save(settings.copy(floatingMenu = it == 1)) }
                    ChoiceRow("Address bar position", AddressBarPosition.entries.map { it.label }, settings.addressBarPosition.ordinal) { save(settings.copy(addressBarPosition = AddressBarPosition.entries[it])) }
                    ToggleRow("Gesture haptics", "Feel tab changes and swipe dismissals.", settings.hapticsEnabled) { save(settings.copy(hapticsEnabled = it)) }
                    Text("Swipe across the phone address bar to change tabs. Swipe toward the page to open the overview. In Coverflow, swipe an unpinned card up to close it.", style = MaterialTheme.typography.bodyMedium)
                }
                Section.BROWSER -> {
                    ToggleRow("Restore regular tabs", "Reopen this profile’s tabs after restart. Private tabs are never restored.", settings.restoreTabs) { save(settings.copy(restoreTabs = it)) }
                    ToggleRow("Favorites on start page", "Show your saved sites below the search button.", settings.showFavorites) { save(settings.copy(showFavorites = it)) }
                }
                Section.APPEARANCE -> {
                    ChoiceRow("Theme", ThemeMode.entries.map { it.label }, settings.themeMode.ordinal) { save(settings.copy(themeMode = ThemeMode.entries[it])) }
                    ToggleRow("Material You colors", "Use device colors on Android 12 and newer.", settings.dynamicColor) { save(settings.copy(dynamicColor = it)) }
                    ToggleRow("Dark websites", "Use WebView darkening when the device and website support it.", settings.forceDarkWebEnabled) { save(settings.copy(forceDarkWebEnabled = it)) }
                }
                Section.PRIVACY -> {
                    ToggleRow("Ad & tracker blocker", "Block requests to known tracker domains.", settings.adBlockEnabled) { save(settings.copy(adBlockEnabled = it)) }
                    ToggleRow("HTTPS-only mode", "Upgrade web addresses and block insecure requests.", settings.httpsOnlyEnabled) { save(settings.copy(httpsOnlyEnabled = it)) }
                    OutlinedButton(onClick = { confirm = true }) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear browsing data") }
                }
                Section.ABOUT -> {
                    BrandOrb(false, Modifier.size(68.dp)); Text("BrowserX³", style = MaterialTheme.typography.headlineMedium); Text("Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}")
                    HorizontalDivider(); Text("Browser engine", style = MaterialTheme.typography.labelLarge); Text("Android System WebView"); Text("Toppings are built-in page tools. This edition does not load Firefox extensions.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
        } }
    }
    if (confirm) ConfirmClear("Close this profile’s tabs and remove its history, trails and site data. WebView cache may be shared. Favorites and downloaded files are kept." + if (!SiteStorage.isolated) " This WebView also shares cookies between profiles." else "", { confirm = false }, { confirm = false; clearData() })
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, update: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = update).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Spacer(Modifier.width(10.dp)); Switch(checked, null)
        }
    }
}

@Composable
private fun ChoiceRow(title: String, choices: List<String>, selected: Int, update: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEachIndexed { i, label -> FilterChip(selected == i, { update(i) }, { Text(label) }) }
    } }
}
