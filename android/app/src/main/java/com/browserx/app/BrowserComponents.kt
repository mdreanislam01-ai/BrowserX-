package com.browserx.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                .verticalScroll(rememberScrollState())
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
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
    matches: Pair<Int, Int>,
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
            Text("${matches.first}/${matches.second}", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onPrev, enabled = matches.second > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
            }
            IconButton(onClick = onNext, enabled = matches.second > 0) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }
}
