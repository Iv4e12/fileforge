package app.fileforge.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.fileforge.io.PdfDoc
import app.fileforge.io.TextEncoding
import app.fileforge.model.FileType
import app.fileforge.parse.Csv

fun countMatches(text: String, query: String): Int {
    if (query.isEmpty()) return 0
    var count = 0
    var i = text.indexOf(query, 0, true)
    while (i >= 0) {
        count++
        i = text.indexOf(query, i + query.length, true)
    }
    return count
}

/** State of the currently open document (text, CSV table or PDF). */
class DocState(uri: Uri, name: String, val type: FileType) {
    var uri by mutableStateOf(uri)
    var name by mutableStateOf(name)
    var loading by mutableStateOf(true)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var saveError by mutableStateOf<String?>(null)
    var dirty by mutableStateOf(false)
    /** Very large text files are shown read-only (line by line) to keep the UI responsive. */
    var readOnly by mutableStateOf(false)

    var encoding = TextEncoding(Charsets.UTF_8, false, false)

    // ---- text ----
    var value by mutableStateOf(TextFieldValue(""))
    var lines by mutableStateOf<List<String>?>(null)

    // ---- csv ----
    var rows by mutableStateOf<List<List<String>>>(emptyList())
    var delimiter: Char = ','

    // ---- pdf ----
    var pdf by mutableStateOf<PdfDoc?>(null)

    val isPlainText: Boolean get() = type.isText && type != FileType.CSV

    // ---- undo / redo ----
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var lastPush = 0L
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    fun currentText(): String = if (type == FileType.CSV) Csv.serialize(rows, delimiter) else value.text

    /** Called after a successful write. [text]/[rowsSnapshot] are what was written. */
    fun markSaved(text: String, rowsSnapshot: List<List<String>>) {
        dirty = if (type == FileType.CSV) rows !== rowsSnapshot else value.text != text
        savedText = text
    }

    private var savedText = ""

    fun markLoaded(text: String) {
        savedText = text
        dirty = false
    }

    fun onValueChange(new: TextFieldValue) {
        val old = value
        if (new.text != old.text) {
            val now = System.currentTimeMillis()
            if (undoStack.isEmpty() || now - lastPush > 700) {
                undoStack.addLast(old)
                lastPush = now
                val max = if (old.text.length > 200_000) 10 else 100
                while (undoStack.size > max) undoStack.removeFirst()
            }
            redoStack.clear()
            dirty = new.text != savedText
        }
        value = new
        updateFlags()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(value)
        value = prev
        dirty = prev.text != savedText
        lastPush = 0
        updateFlags()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(value)
        value = next
        dirty = next.text != savedText
        lastPush = 0
        updateFlags()
    }

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun edit(v: TextFieldValue) {
        lastPush = 0
        onValueChange(v)
    }

    // ---- search & replace (text) ----
    fun find(query: String, forward: Boolean): Boolean {
        if (query.isEmpty()) return false
        val t = value.text
        val idx = if (forward) {
            t.indexOf(query, value.selection.max, true).takeIf { it >= 0 } ?: t.indexOf(query, 0, true)
        } else {
            val from = value.selection.min - 1
            (if (from >= 0) t.lastIndexOf(query, from, true) else -1).takeIf { it >= 0 }
                ?: t.lastIndexOf(query, t.length, true)
        }
        if (idx < 0) return false
        value = value.copy(selection = TextRange(idx, idx + query.length))
        return true
    }

    fun replaceCurrent(query: String, replacement: String): Boolean {
        val sel = value.selection
        val t = value.text
        if (query.isNotEmpty() && sel.length == query.length &&
            t.regionMatches(sel.min, query, 0, query.length, ignoreCase = true)
        ) {
            edit(TextFieldValue(t.replaceRange(sel.min, sel.max, replacement), TextRange(sel.min + replacement.length)))
        }
        return find(query, true)
    }

    fun replaceAll(query: String, replacement: String): Int {
        val n = countMatches(value.text, query)
        if (n == 0) return 0
        edit(TextFieldValue(value.text.replace(query, replacement, ignoreCase = true), TextRange(0)))
        return n
    }

    // ---- csv editing ----
    fun setCell(r: Int, c: Int, v: String) {
        val cur = rows.getOrNull(r) ?: return
        if (cur.getOrNull(c) == v) return
        val newRow = cur.toMutableList()
        while (newRow.size <= c) newRow.add("")
        newRow[c] = v
        rows = rows.toMutableList().also { it[r] = newRow }
        dirty = true
    }

    fun addRow(columns: Int) {
        rows = rows + listOf(List(columns.coerceAtLeast(1)) { "" })
        dirty = true
    }

    fun addColumn() {
        if (rows.isEmpty()) { addRow(2); return }
        rows = rows.map { it + "" }
        dirty = true
    }

    fun deleteRow(r: Int) {
        if (r !in rows.indices) return
        rows = rows.toMutableList().also { it.removeAt(r) }
        dirty = true
    }
}
