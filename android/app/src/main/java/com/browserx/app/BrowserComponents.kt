package com.browserx.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Privacy X-Ray",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = hostOf(tab.url).ifBlank { "Local Start Page" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(sky(tab.isPrivate))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(78.dp)
                        .border(5.dp, MaterialTheme.colorScheme.primary.copy(alpha = .7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${tab.blockedCount}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Requests blocked here", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (tab.isPrivate) "Private visits are not added to your totals."
                        else "$totalBlocked blocked across regular tabs",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                "Recent affected domains",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val domains =
                tab.blockedDomains
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(5)
            if (domains.isEmpty())
                Text("No blocked domains on this page yet.", style = MaterialTheme.typography.bodySmall)
            else
                domains.forEach { (domain, count) ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(domain, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count")
                        }
                    }
                }
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ad & Tracker Blocker")
                Switch(
                    checked = settings.adBlockEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(adBlockEnabled = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Cookie Banner Hider")
                Switch(
                    checked = settings.cookieBannerHiderEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(cookieBannerHiderEnabled = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HTTPS-Only Mode")
                Switch(
                    checked = settings.httpsOnlyEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(httpsOnlyEnabled = it)) },
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
    readerTheme: ReaderTheme,
    fontSize: Int,
    setTheme: (ReaderTheme) -> Unit,
    resize: (Int) -> Unit,
    saveCopy: (() -> Unit)?,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit,
    onDismiss: () -> Unit,
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = readerTheme.bgColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Reader Studio",
                    Modifier.weight(1f),
                    color = readerTheme.textColor,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (saveCopy != null)
                    TextButton(onClick = saveCopy) { Text("Save offline", color = readerTheme.textColor) }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close reader", tint = readerTheme.textColor)
                }
            }
            // Controls
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReaderTheme.entries.forEach { t ->
                        FilterChip(
                            selected = readerTheme == t,
                            onClick = { setTheme(t) },
                            label = { Text(t.label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        enabled = fontSize > 13,
                        onClick = { resize((fontSize - 2).coerceAtLeast(13)) },
                    ) {
                        Text("A-", color = readerTheme.textColor, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        enabled = fontSize < 27,
                        onClick = { resize((fontSize + 2).coerceAtMost(27)) },
                    ) {
                        Text("A+", color = readerTheme.textColor, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onToggleSpeak) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = readerTheme.textColor,
                        )
                    }
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = article.title,
                    fontSize = (fontSize + 6).sp,
                    fontWeight = FontWeight.Bold,
                    color = readerTheme.textColor,
                )
                if (article.byline.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = article.byline,
                        fontSize = (fontSize - 2).sp,
                        color = readerTheme.textColor.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = article.content,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.55f).sp,
                    color = readerTheme.textColor,
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
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                placeholder = { Text("Find in page") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${matches.first}/${matches.second}",
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
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
