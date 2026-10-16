package app.fileforge.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.fileforge.io.PdfDoc

/**
 * Scrollable PDF pages with pinch-to-zoom (two fingers) — zoom resizes the pages and the
 * viewer scrolls horizontally when zoomed in. One-finger drags scroll as normal.
 */
@Composable
fun PdfViewer(doc: PdfDoc, listState: LazyListState, zoom: Float, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    val zoomNow by rememberUpdatedState(zoom)
    val onZoomNow by rememberUpdatedState(onZoom)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val z = event.calculateZoom()
                            if (z != 1f) {
                                onZoomNow((zoomNow * z).coerceIn(1f, 4f))
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val viewportWidth = maxWidth
        Box(Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(viewportWidth * zoom).fillMaxHeight(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = doc.pageCount, key = { it }) { index -> PdfPage(doc, index) }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int) {
    val (w, h) = doc.pageSizes[index]
    var bitmap by remember(doc, index) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(doc, index) { mutableStateOf(false) }
    LaunchedEffect(doc, index) {
        val b = doc.render(index)
        if (b == null) failed = true else bitmap = b
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(w.toFloat() / h.toFloat()).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        when {
            b != null -> Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            failed -> Text("Page ${index + 1} could not be rendered", color = Color.DarkGray)
            else -> CircularProgressIndicator()
        }
    }
}
