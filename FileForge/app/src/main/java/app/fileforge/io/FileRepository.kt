package app.fileforge.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FileTooLargeException(val limit: Long) : IOException("File exceeds $limit bytes")

data class FileInfo(val name: String, val mime: String?)

/** All file access goes through the Storage Access Framework (content URIs). */
class FileRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun info(uri: Uri): FileInfo {
        var name: String? = null
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        } catch (e: Exception) {
            // fall through to the URI-based name
        }
        val mime = try { resolver.getType(uri) } catch (e: Exception) { null }
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled"
        return FileInfo(name ?: fallback, mime)
    }

    fun persist(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e2: Exception) {
                // Not every provider offers persistable grants; the file just won't be in Recent after restart.
            }
        }
    }

    fun release(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
        }
    }

    suspend fun readBytes(uri: Uri, maxBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open file")
        input.use { s ->
            val out = ByteArrayOutputStream()
            copyLimited(s, out, maxBytes)
            out.toByteArray()
        }
    }

    /**
     * Writes [bytes] to [uri], then reads the size back to confirm the write.
     * SAF has no atomic replace, so callers must keep the in-memory text until this succeeds.
     */
    suspend fun writeBytes(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open the file for writing")
        out.use {
            it.write(bytes)
            it.flush()
        }
        val written: Long? = try {
            resolver.openInputStream(uri)?.use { s ->
                var n = 0L
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = s.read(buf)
                    if (r < 0) break
                    n += r
                }
                n
            }
        } catch (e: Exception) {
            null // read-back is best effort (some cloud providers don't allow it)
        }
        if (written != null && written != bytes.size.toLong()) {
            throw IOException("The saved file has an unexpected size ($written of ${bytes.size} bytes)")
        }
    }

    suspend fun copyToFile(uri: Uri, file: File, maxBytes: Long) = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open file")
        input.use { i -> file.outputStream().use { o -> copyLimited(i, o, maxBytes) } }
    }

    suspend fun copy(from: Uri, to: Uri) = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(from) ?: throw FileNotFoundException("Cannot open the original file")
        val output = resolver.openOutputStream(to, "wt") ?: throw IOException("Cannot open the destination")
        input.use { i -> output.use { o -> i.copyTo(o) } }
    }

    private fun copyLimited(input: InputStream, out: OutputStream, maxBytes: Long) {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw FileTooLargeException(maxBytes)
            out.write(buf, 0, n)
        }
    }
}
