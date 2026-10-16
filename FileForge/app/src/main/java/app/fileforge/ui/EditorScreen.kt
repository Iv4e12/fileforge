package app.fileforge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fileforge.AppViewModel
import app.fileforge.editor.DocState
import app.fileforge.editor.countMatches
import app.fileforge.model.FileType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun findLine(lines: List<String>, q: String, start: Int, forward: Boolean): Int {
    if (q.isEmpty() || lines.isEmpty()) return -1
    val n = lines.size
    val from = if (start < 0) (if (forward) -1 else n) else start
    for (k in 1..n) {
        val i = if (forward) (from + k).mod(n) else (from - k).mod(n)
        if (lines[i].contains(q, ignoreCase = true)) return i
    }
    return -1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    vm: AppViewModel,
    d: DocState,
    onSaveAs: (String, String) -> Unit,
    onShare: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmShare by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    var roMatch by remember { mutableIntStateOf(-1) }
    val focus = remember { FocusRequester() }
    val roList = rememberLazyListState()
    val pdfList = rememberLazyListState()
    var zoom by remember { mutableFloatStateOf(1f) }
    val currentPage by remember { derivedStateOf { pdfList.firstVisibleItemIndex + 1 } }

    fun requestClose() {
        if (d.dirty) confirmClose = true else vm.closeDoc()
    }
    BackHandler {
        if (searching) searching = false else requestClose()
    }

    val ready = !d.loading && d.error == null && d.type != FileType.UNSUPPORTED
    val editingText = ready && d.isPlainText && !d.readOnly && !preview

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close file")
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (d.dirty) {
                                Text(
                                    "● ",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.semantics { contentDescription = "Unsaved changes" },
                                )
                            }
                            Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            buildString {
                                append(d.type.label)
                                if (d.readOnly) append(" · read-only (large file)")
                                if (d.dirty) append(" · unsaved changes")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (editingText) {
                        IconButton(onClick = d::undo, enabled = d.canUndo) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = d::redo, enabled = d.canRedo) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                    }
                    if (ready && d.type == FileType.MARKDOWN) {
                        IconButton(onClick = { preview = !preview }) {
                            Icon(
                                if (preview) Icons.Filled.Edit else Icons.Filled.Visibility,
                                contentDescription = if (preview) "Edit" else "Preview",
                            )
                        }
                    }
                    if (ready && d.type != FileType.PDF && !preview) {
                        IconButton(onClick = { searching = !searching }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                    if (ready && d.type.isText && !d.readOnly) {
                        IconButton(onClick = { vm.save(d) }, enabled = d.dirty && !d.saving) {
                            Icon(Icons.Filled.Save, contentDescription = "Save")
                        }
                    }
                    if (ready) {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (d.type == FileType.CSV) {
                                    DropdownMenuItem(text = { Text("Add row") }, onClick = {
                                        menu = false
                                        d.addRow(d.rows.maxOfOrNull { it.size } ?: 2)
                                    })
                                    DropdownMenuItem(text = { Text("Add column") }, onClick = {
                                        menu = false
                                        d.addColumn()
                                    })
                                }
                                DropdownMenuItem(
                                    text = { Text(if (d.type == FileType.PDF || d.readOnly) "Save a copy…" else "Save as…") },
                                    onClick = { menu = false; onSaveAs(d.name, d.type.mime) },
                                )
                                DropdownMenuItem(text = { Text("Share") }, onClick = {
                                    menu = false
                                    if (d.dirty) confirmShare = true else onShare()
                                })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            val pdf = d.pdf
            if (ready && d.type == FileType.PDF && pdf != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = { scope.launch { pdfList.animateScrollToItem((currentPage - 2).coerceAtLeast(0)) } },
                            enabled = currentPage > 1,
                        ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous page") }
                        TextButton(onClick = { goTo = true }) { Text("$currentPage / ${pdf.pageCount}") }
                        IconButton(
                            onClick = { scope.launch { pdfList.animateScrollToItem(currentPage.coerceAtMost(pdf.pageCount - 1)) } },
                            enabled = currentPage < pdf.pageCount,
                        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next page") }
                        Spacer(Modifier.width(16.dp))
                        IconButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(1f) }, enabled = zoom > 1f) {
                            Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                        }
                        Text("${(zoom * 100).roundToInt()}%")
                        IconButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(4f) }, enabled = zoom < 4f) {
                            Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            if (d.loading || d.saving) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (searching && ready && d.type != FileType.PDF && !preview) {
                val count = remember(query, d.value.text) {
                    if (!d.isPlainText || d.readOnly || d.value.text.length > 2_000_000) -1 else countMatches(d.value.text, query)
                }
                val info = when {
                    query.isEmpty() -> ""
                    d.type == FileType.CSV -> ""
                    d.readOnly -> if (roMatch >= 0) "Line ${roMatch + 1}" else ""
                    count == 0 -> "No matches"
                    count > 0 -> "$count matches"
                    else -> ""
                }
                fun jump(forward: Boolean) {
                    if (d.type == FileType.CSV) return
                    if (d.readOnly) {
                        val lines = d.lines ?: return
                        val idx = findLine(lines, query, roMatch, forward)
                        if (idx < 0) { vm.message = "No matches"; return }
                        roMatch = idx
                        scope.launch { roList.animateScrollToItem(idx) }
                    } else if (d.find(query, forward)) {
                        try { focus.requestFocus() } catch (e: Exception) {}
                    } else {
                        vm.message = "No matches"
                    }
                }
                FindBar(
                    query = query,
                    onQuery = { query = it },
                    replacement = if (d.isPlainText && !d.readOnly) replacement else null,
                    onReplacement = { replacement = it },
                    info = info,
                    showNav = d.type != FileType.CSV,
                    onPrev = { jump(false) },
                    onNext = { jump(true) },
                    onReplace = {
                        if (!d.replaceCurrent(query, replacement)) vm.message = "No matches"
                        try { focus.requestFocus() } catch (e: Exception) {}
                    },
                    onReplaceAll = {
                        val n = d.replaceAll(query, replacement)
                        vm.message = if (n == 0) "No matches" else "Replaced $n occurrence${if (n == 1) "" else "s"}"
                    },
                    onClose = { searching = false },
                )
            }

            when {
                d.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                d.error != null -> ErrorState(d.error ?: "", onBack = { vm.closeDoc() }, onRemove = {
                    vm.removeRecent(d.uri.toString()); vm.closeDoc()
                })
                d.type == FileType.UNSUPPORTED -> UnsupportedState(d, onText = { vm.reopenAsText(d) }, onBack = { vm.closeDoc() })
                d.type == FileType.PDF -> d.pdf?.let { PdfViewer(it, pdfList, zoom, { z -> zoom = z }) }
                d.type == FileType.CSV -> CsvTable(d, if (searching) query else "")
                d.readOnly -> ReadOnlyLines(d.lines.orEmpty(), vm.fontSize, roList, if (searching) roMatch else -1)
                preview -> MarkdownPreview(d.value.text, Modifier.fillMaxSize())
                else -> TextBody(d, vm.wordWrap, vm.fontSize, focus)
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes to ${d.name} before closing?") },
            confirmButton = {
                Row {
                    TextButton(onClick = { confirmClose = false }) { Text("Cancel") }
                    TextButton(onClick = { confirmClose = false; vm.closeDoc() }) { Text("Discard") }
                    Button(onClick = { confirmClose = false; vm.save(d, thenClose = true) }) { Text("Save") }
                }
            },
        )
    }
    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            title = { Text("Unsaved changes") },
            text = { Text("Sharing sends the last saved version of the file. Your unsaved edits won't be included.") },
            confirmButton = { TextButton(onClick = { confirmShare = false; onShare() }) { Text("Share saved version") } },
            dismissButton = { TextButton(onClick = { confirmShare = false }) { Text("Cancel") } },
        )
    }
    d.saveError?.let { msg ->
        AlertDialog(
            onDismissRequest = { d.saveError = null },
            title = { Text("Couldn't save") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { d.saveError = null }) { Text("OK") } },
            dismissButton = {
                if (d.type.isText && !d.readOnly) {
                    TextButton(onClick = { d.saveError = null; onSaveAs(d.name, d.type.mime) }) { Text("Save as…") }
                }
            },
        )
    }
    if (goTo) {
        val total = d.pdf?.pageCount ?: 1
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { goTo = false },
            title = { Text("Go to page (1–$total)") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(6) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = text.toIntOrNull()
                    goTo = false
                    if (p != null) scope.launch { pdfList.scrollToItem((p - 1).coerceIn(0, total - 1)) }
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { goTo = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FindBar(
    query: String,
    onQuery: (String) -> Unit,
    replacement: String?,
    onReplacement: (String) -> Unit,
    info: String,
    showNav: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Find") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                )
                if (showNav) {
                    IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match") }
                    IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match") }
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
            }
            if (info.isNotEmpty()) {
                Text(info, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (replacement != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = onReplacement,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Replace with") },
                    )
                    TextButton(onClick = onReplace) { Text("Replace") }
                    TextButton(onClick = onReplaceAll) { Text("All") }
                }
            }
        }
    }
}

@Composable
private fun TextBody(d: DocState, wrap: Boolean, fontSize: Int, focus: FocusRequester) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val big = d.value.text.length > 200_000
    val transformation = remember(d.type, dark, big) {
        if (big || d.type !in setOf(FileType.JSON, FileType.XML, FileType.HTML)) {
            VisualTransformation.None
        } else {
            SyntaxTransformation(
                d.type,
                SyntaxColors(
                    key = scheme.primary,
                    string = if (dark) androidx.compose.ui.graphics.Color(0xFF8FD3B0) else androidx.compose.ui.graphics.Color(0xFF17795A),
                    number = if (dark) androidx.compose.ui.graphics.Color(0xFFE9AE78) else androidx.compose.ui.graphics.Color(0xFFB45309),
                    tag = if (dark) androidx.compose.ui.graphics.Color(0xFFC7A6FF) else androidx.compose.ui.graphics.Color(0xFF7A3E9D),
                    comment = scheme.onSurfaceVariant,
                ),
            )
        }
    }
    val prose = d.type == FileType.TEXT || d.type == FileType.MARKDOWN
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minH = maxHeight
        val minW = maxWidth
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())),
        ) {
            BasicTextField(
                value = d.value,
                onValueChange = d::onValueChange,
                modifier = Modifier
                    .then(if (wrap) Modifier.fillMaxWidth() else Modifier.widthIn(min = minW))
                    .heightIn(min = minH)
                    .padding(12.dp)
                    .focusRequester(focus),
                textStyle = TextStyle(
                    color = scheme.onBackground,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.45f).sp,
                ),
                cursorBrush = SolidColor(scheme.primary),
                softWrap = wrap,
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(
                    capitalization = if (prose) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                    autoCorrect = prose,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                ),
            )
        }
    }
}

@Composable
private fun ReadOnlyLines(lines: List<String>, fontSize: Int, state: LazyListState, matchLine: Int) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Text(
                "This file is large, so it is open read-only to keep the app responsive.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            items(lines.size) { i ->
                Text(
                    lines[i].ifEmpty { " " },
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    modifier = if (i == matchLine) Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp))
        Button(onClick = onBack) { Text("Back") }
        TextButton(onClick = onRemove) { Text("Remove from recent files") }
    }
}

@Composable
private fun UnsupportedState(d: DocState, onText: () -> Unit, onBack: () -> Unit) {
    val ext = d.name.substringAfterLast('.', "").lowercase()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(
            buildString {
                append("FileForge can't open this type of file")
                if (ext.isNotEmpty()) append(" (.$ext)")
                append(".\n\nSupported: PDF, TXT, MD, HTML, CSV, JSON and XML. DOCX and RTF are not supported in this version.")
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onText) { Text("Open as plain text") }
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
