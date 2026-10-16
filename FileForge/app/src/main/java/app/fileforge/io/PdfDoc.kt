package app.fileforge.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * A PDF opened read-only with Android's built-in [PdfRenderer].
 * The source is copied to the app cache first so the descriptor is always seekable.
 * The original file is never modified.
 */
class PdfDoc private constructor(
    private val file: File,
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageSizes: List<Pair<Int, Int>>,
    private val renderWidth: Int,
) {
    val pageCount: Int get() = pageSizes.size

    private val lock = Mutex() // PdfRenderer allows only one open page at a time
    @Volatile private var closed = false
    private val cache = object : LruCache<Int, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    /** Returns the rendered page, or null if it could not be rendered. */
    suspend fun render(index: Int): Bitmap? {
        cache.get(index)?.let { return it }
        return lock.withLock {
            val cached = cache.get(index)
            if (cached != null) return@withLock cached
            if (closed) return@withLock null
            withContext(Dispatchers.IO) {
                try {
                    renderPage(index).also { cache.put(index, it) }
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }

    private fun renderPage(index: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val w = renderWidth
            val h = (w.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }

    fun close() {
        closed = true
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                try { renderer.close() } catch (e: Exception) {}
                try { pfd.close() } catch (e: Exception) {}
                file.delete()
                cache.evictAll()
            }
        }
    }

    companion object {
        private const val MAX_PDF_BYTES = 300L * 1024 * 1024

        fun cleanCache(context: Context) {
            context.cacheDir.listFiles()?.forEach {
                if (it.name.startsWith("pdf_") && it.name.endsWith(".pdf")) it.delete()
            }
        }

        suspend fun open(context: Context, repo: FileRepository, uri: Uri): PdfDoc =
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "pdf_${System.nanoTime()}.pdf")
                var descriptor: ParcelFileDescriptor? = null
                try {
                    repo.copyToFile(uri, file, MAX_PDF_BYTES)
                    val p = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    descriptor = p
                    val renderer = try {
                        PdfRenderer(p)
                    } catch (e: SecurityException) {
                        throw IOException("This PDF is password-protected, which FileForge doesn't support.")
                    }
                    val sizes = ArrayList<Pair<Int, Int>>()
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { sizes.add(it.width to it.height) }
                    }
                    if (sizes.isEmpty()) {
                        renderer.close()
                        throw IOException("This PDF has no pages.")
                    }
                    val w = (context.resources.displayMetrics.widthPixels * 2).coerceIn(900, 1600)
                    PdfDoc(file, p, renderer, sizes, w)
                } catch (e: Throwable) {
                    try { descriptor?.close() } catch (x: Exception) {}
                    file.delete()
                    throw e
                }
            }
    }
}
