package com.browserx.app

import android.content.Context
import android.util.AtomicFile
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class ReadingCopy(val url: String, val article: ReaderArticle)

/** Explicit offline saves only: one bounded, atomic text document per URL in app-private storage. */
internal class ReadingLibrary(context: Context, profile: String) {
    private val dir = File(context.noBackupFilesDir, "reader/" + digest(profile))

    private fun digest(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun file(url: String) = File(dir, digest(url) + ".json")

    suspend fun save(copy: ReadingCopy) =
        withContext(Dispatchers.IO) {
            require(isWebUrl(copy.url))
            dir.mkdirs()
            val target = file(copy.url)
            check(target.exists() || (dir.listFiles()?.count { it.extension == "json" } ?: 0) < 40) {
                "Offline library is full"
            }
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            try {
                val a = copy.article
                stream.write(
                    JSONObject()
                        .put("url", copy.url)
                        .put("title", a.title.take(2000))
                        .put("byline", a.byline.take(2000))
                        .put("text", a.content.take(120000))
                        .toString()
                        .toByteArray(),
                )
                atomic.finishWrite(stream)
            } catch (e: Exception) {
                atomic.failWrite(stream)
                throw e
            }
        }

    suspend fun copies(): List<ReadingCopy> =
        withContext(Dispatchers.IO) {
            dir.listFiles()
                .orEmpty()
                .filter { it.extension == "json" }
                .sortedByDescending { it.lastModified() }
                .take(40)
                .mapNotNull { f ->
                    runCatching {
                        val o = JSONObject(AtomicFile(f).openRead().bufferedReader().use { it.readText() })
                        ReadingCopy(
                            o.getString("url"),
                            ReaderArticle(
                                o.getString("title"),
                                o.optString("byline"),
                                o.getString("text"),
                            ),
                        )
                    }
                        .getOrNull()
                }
        }

    suspend fun remove(url: String) = withContext(Dispatchers.IO) { AtomicFile(file(url)).delete() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingScreen(library: ReadingLibrary, open: (ReadingCopy) -> Unit, back: () -> Unit) {
    var entries by remember { mutableStateOf<List<ReadingCopy>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(library) { entries = library.copies() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline reading") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { insets ->
        val rows = entries
        if (rows == null || rows.isEmpty())
            Box(Modifier.fillMaxSize().padding(insets).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (rows == null) "Opening library…"
                    else "Save a page in Reader Studio to read it here offline.",
                )
            }
        else
            LazyColumn(Modifier.fillMaxSize().padding(insets)) {
                items(rows, key = { it.url }) { copy ->
                    ListItem(
                        headlineContent = {
                            Text(copy.article.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(hostOf(copy.url)) },
                        leadingContent = { Icon(Icons.Default.MenuBook, null) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        library.remove(copy.url)
                                        entries = library.copies()
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Delete, "Delete offline copy")
                            }
                        },
                        modifier = Modifier.clickable { open(copy) },
                    )
                }
            }
    }
}
