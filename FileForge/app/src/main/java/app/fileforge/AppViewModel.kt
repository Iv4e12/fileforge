package app.fileforge

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fileforge.data.RecentFile
import app.fileforge.data.RecentFilesStore
import app.fileforge.data.SettingsStore
import app.fileforge.editor.DocState
import app.fileforge.io.FileRepository
import app.fileforge.io.FileTooLargeException
import app.fileforge.io.PdfDoc
import app.fileforge.io.TextCodec
import app.fileforge.model.FileType
import app.fileforge.parse.Csv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

class AppViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        /** Files above this size open read-only. */
        const val EDIT_LIMIT = 1_500_000L
        /** Files above this size are refused. */
        const val MAX_READ = 25_000_000L
    }

    private val repo = FileRepository(app)
    private val recentStore = RecentFilesStore(app)
    private val settings = SettingsStore(app)

    var themeMode by mutableIntStateOf(settings.themeMode)
        private set
    var fontSize by mutableIntStateOf(settings.fontSize)
        private set
    var wordWrap by mutableStateOf(settings.wordWrap)
        private set

    val recents = mutableStateListOf<RecentFile>().apply { addAll(recentStore.load()) }
    var showSettings by mutableStateOf(false)
    var doc by mutableStateOf<DocState?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    init {
        PdfDoc.cleanCache(app)
    }

    // ---------- settings ----------
    fun setTheme(mode: Int) { themeMode = mode; settings.themeMode = mode }
    fun setFontSize(size: Int) { fontSize = size; settings.fontSize = size }
    fun setWordWrap(wrap: Boolean) { wordWrap = wrap; settings.wordWrap = wrap }

    // ---------- recents ----------
    private fun setRecents(list: List<RecentFile>) {
        recents.clear()
        recents.addAll(list)
    }

    private fun addRecent(d: DocState) = setRecents(recentStore.add(d.uri.toString(), d.name))

    fun removeRecent(uri: String) {
        setRecents(recentStore.remove(uri))
        repo.release(Uri.parse(uri))
    }

    fun clearRecents() {
        recentStore.clear()
        setRecents(emptyList())
    }

    // ---------- opening ----------
    fun openPicked(uri: Uri) {
        repo.persist(uri)
        open(uri)
    }

    fun openExternal(uri: Uri) {
        if (doc?.dirty == true) {
            message = "Close the current file first. It has unsaved changes."
            return
        }
        showSettings = false
        open(uri)
    }

    fun createdNew(uri: Uri) {
        repo.persist(uri)
        open(uri, forceText = true)
    }

    fun reopenAsText(d: DocState) = open(d.uri, forceText = true)

    fun open(uri: Uri, forceText: Boolean = false) {
        viewModelScope.launch {
            doc?.pdf?.close()
            val info = withContext(Dispatchers.IO) { repo.info(uri) }
            var type = FileType.detect(info.name, info.mime)
            if (forceText && type == FileType.UNSUPPORTED) type = FileType.TEXT
            val d = DocState(uri, info.name, type)
            doc = d
            if (type == FileType.UNSUPPORTED) {
                d.loading = false
                return@launch
            }
            load(d)
        }
    }

    private suspend fun load(d: DocState) {
        d.loading = true
        d.error = null
        try {
            if (d.type == FileType.PDF) {
                d.pdf = PdfDoc.open(getApplication(), repo, d.uri)
            } else {
                loadText(d)
            }
            addRecent(d)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            d.error = describeOpenError(e)
        } finally {
            d.loading = false
        }
    }

    private suspend fun loadText(d: DocState) {
        val bytes = repo.readBytes(d.uri, MAX_READ)
        val decoded = withContext(Dispatchers.Default) { TextCodec.decode(bytes) }
        d.encoding = decoded.encoding
        val text = decoded.text
        when {
            d.type == FileType.CSV -> {
                val (rows, delim) = withContext(Dispatchers.Default) {
                    val dl = Csv.detectDelimiter(text)
                    Csv.parse(text, dl) to dl
                }
                d.delimiter = delim
                d.rows = rows
            }
            bytes.size > EDIT_LIMIT -> {
                d.readOnly = true
                d.lines = withContext(Dispatchers.Default) { text.split('\n') }
            }
            else -> d.value = TextFieldValue(text)
        }
        d.markLoaded(text)
    }

    fun closeDoc() {
        doc?.pdf?.close()
        doc = null
    }

    // ---------- saving ----------
    fun save(d: DocState, thenClose: Boolean = false) {
        if (d.saving || d.readOnly || !d.type.isText) return
        viewModelScope.launch {
            if (writeDoc(d, d.uri) && thenClose) closeDoc()
        }
    }

    fun saveAs(target: Uri) {
        val d = doc ?: return
        if (d.type == FileType.UNSUPPORTED) return
        viewModelScope.launch {
            try {
                repo.persist(target)
                if (d.type == FileType.PDF || d.readOnly) {
                    repo.copy(d.uri, target) // byte-for-byte copy of the original
                    message = "Copy saved."
                } else if (writeDoc(d, target)) {
                    d.uri = target
                    d.name = withContext(Dispatchers.IO) { repo.info(target).name }
                    addRecent(d)
                    message = "Saved as ${d.name}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                d.saveError = "The copy could not be saved: ${describeWriteError(e)}"
            }
        }
    }

    private suspend fun writeDoc(d: DocState, target: Uri): Boolean {
        d.saving = true
        return try {
            val rowsSnapshot = d.rows
            val text = d.currentText()
            val enc = withContext(Dispatchers.Default) { TextCodec.encode(text, d.encoding) }
            repo.writeBytes(target, enc.bytes)
            d.encoding = enc.encoding
            d.markSaved(text, rowsSnapshot)
            message = if (enc.reencoded) {
                "Saved. Some characters can't be stored in the original encoding, so the file is now UTF-8."
            } else {
                "Saved ${d.name}"
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            d.saveError = "The file could not be saved: ${describeWriteError(e)}\n\n" +
                "Your changes are still open in the editor. Try again, or use Save as to write them somewhere else."
            false
        } finally {
            d.saving = false
        }
    }

    // ---------- error text ----------
    private fun describeOpenError(e: Throwable): String = when (e) {
        is FileTooLargeException -> "This file is too large to open (limit ${e.limit / 1_000_000} MB)."
        is FileNotFoundException -> "The file could not be found. It may have been moved or deleted."
        is SecurityException -> "Permission to read this file was denied or has expired. Open it again with Open file."
        is OutOfMemoryError -> "There isn't enough memory to open this file."
        else -> e.message?.let { "The file could not be opened: $it" } ?: "The file could not be opened."
    }

    private fun describeWriteError(e: Throwable): String = when (e) {
        is SecurityException -> "write permission was denied. The location may be read-only."
        is OutOfMemoryError -> "not enough memory."
        else -> e.message ?: "unknown error."
    }
}
