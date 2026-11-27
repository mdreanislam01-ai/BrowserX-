package com.browserx.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun AddFavoriteDialog(existing: List<BookmarkItem>, dismiss: () -> Unit, save: (BookmarkItem) -> Unit) {
    var name by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }
    val dest = url.trim().let { if ("://" in it) it else "https://$it" }
    val duplicate = existing.any { it.url.trimEnd('/') == dest.trimEnd('/') }
    val valid = isWebUrl(dest) && !duplicate
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add favorite") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name (optional)") })
        OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Web address") }, placeholder = { Text("example.com") }, isError = url.isNotBlank() && !valid)
        if (duplicate) Text("This site is already in Favorites", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { Button(enabled = valid, onClick = { save(BookmarkItem(dest, name.trim().ifBlank { hostOf(dest) })); dismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkPeek(session: BrowserSession, url: String, copy: (String) -> Unit, share: (String) -> Unit, close: () -> Unit) {
    var location by remember(url) { mutableStateOf(url) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TravelExplore, null); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text("Link Peek", style = MaterialTheme.typography.titleMedium); Text(hostOf(location), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }; IconButton(onClick = close) { Icon(Icons.Default.Close, "Dismiss preview") }
            }
            HorizontalDivider()
            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { session.peekView(url) { location = it } }, onRelease = ::disposeView)
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { copy(location) }) { Icon(Icons.Default.ContentCopy, "Copy preview link") }; IconButton(onClick = { share(location) }) { Icon(Icons.Default.Share, "Share preview") }
                Button(onClick = { if (session.add(session.active.isPrivate, location)) close() }) { Text("Open in tab") }
            }
        }
    }
}

private data class Spot(val depth: Int, val row: Float)
private fun positions(stops: List<TrailStop>): Map<Int, Spot> {
    val spots = mutableMapOf<Int, Spot>(); val children = stops.groupBy { it.parent }; var row = 0
    fun place(stop: TrailStop, depth: Int): Float {
        val branches = children[stop.id].orEmpty().filter { it.id > stop.id }.map { place(it, depth + 1) }
        val y = if (branches.isEmpty()) (row++).toFloat() else (branches.first() + branches.last()) / 2f
        spots[stop.id] = Spot(depth, y); return y
    }
    stops.filter { it.parent == null || stops.none { p -> p.id == it.parent } }.forEach { place(it, 0) }
    return spots
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrailScreen(tab: BrowserTab, visit: (TrailStop) -> Unit, fork: (TrailStop) -> Unit, back: () -> Unit) {
    val stops = tab.trail.stops; val spots = remember(stops) { positions(stops) }; var zoom by remember { mutableFloatStateOf(1f) }
    Scaffold(topBar = { TopAppBar(title = { Column { Text("Page trail"); Text(tab.title, maxLines = 1, style = MaterialTheme.typography.labelSmall, overflow = TextOverflow.Ellipsis) } }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets).background(sky(tab.isPrivate))) {
            val tile = 164.dp * zoom; val height = 76.dp; val dx = tile + 30.dp; val dy = 94.dp * zoom.coerceAtLeast(1f)
            val xScroll = rememberScrollState(); val yScroll = rememberScrollState(); val density = LocalDensity.current
            val plotWidth = maxOf(maxWidth, dx * ((spots.values.maxOfOrNull { it.depth } ?: 0) + 1) + 30.dp)
            val plotHeight = maxOf(maxHeight, dy * ((spots.values.maxOfOrNull { it.row } ?: 0f) + 1f) + 100.dp)
            val top = (plotHeight - dy * ((spots.values.maxOfOrNull { it.row } ?: 0f) + 1f)) / 2
            LaunchedEffect(tab.trail.current, zoom) { spots[tab.trail.current]?.let { s -> with(density) { xScroll.scrollTo((16.dp + dx * s.depth + tile / 2 - maxWidth / 2).roundToPx().coerceAtLeast(0)); yScroll.scrollTo((top + dy * s.row - maxHeight / 2 + height / 2).roundToPx().coerceAtLeast(0)) } } }
            Box(Modifier.fillMaxSize().horizontalScroll(xScroll).verticalScroll(yScroll)) { Box(Modifier.size(plotWidth, plotHeight)) {
                val line = MaterialTheme.colorScheme.outline
                Canvas(Modifier.fillMaxSize()) { stops.forEach { s ->
                    val child = spots[s.id]; val parent = spots[s.parent]
                    if (child != null && parent != null) {
                        val a = Offset((16.dp + dx * parent.depth + tile).toPx(), (top + dy * parent.row + height / 2).toPx()); val b = Offset((16.dp + dx * child.depth).toPx(), (top + dy * child.row + height / 2).toPx()); val mid = (a.x + b.x) / 2
                        drawPath(Path().apply { moveTo(a.x, a.y); cubicTo(mid, a.y, mid, b.y, b.x, b.y) }, line, style = Stroke(1.5.dp.toPx()))
                    }
                } }
                stops.forEach { s -> spots[s.id]?.let { p -> Surface(onClick = { visit(s) }, modifier = Modifier.offset(16.dp + dx * p.depth, top + dy * p.row).size(tile, height), shape = RoundedCornerShape(18.dp), color = if (s.id == tab.trail.current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
                    Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(hostOf(s.url), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }; IconButton(onClick = { fork(s) }, modifier = Modifier.width(36.dp)) { Icon(Icons.Default.CallSplit, "Fork in new tab", Modifier.size(18.dp)) } }
                } } }
            } }
            Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), shape = CircleShape, tonalElevation = 5.dp) { Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { zoom = (zoom - .2f).coerceAtLeast(.8f) }) { Icon(Icons.Default.Remove, "Zoom out") }; Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium); IconButton(onClick = { zoom = (zoom + .2f).coerceAtMost(1.8f) }) { Icon(Icons.Default.Add, "Zoom in") }
            } }
        }
    }
}
