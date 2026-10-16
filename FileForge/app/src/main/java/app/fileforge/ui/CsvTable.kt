package app.fileforge.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fileforge.editor.DocState

/** Editable CSV table. Tap a cell to edit it. Rows are filtered (not removed) while searching. */
@Composable
fun CsvTable(d: DocState, filter: String, modifier: Modifier = Modifier) {
    val rows = d.rows
    if (rows.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { d.addRow(2) }) { Text("This file is empty. Add a row") }
        }
        return
    }
    val cols = remember(rows) { (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1) }
    val visible = remember(rows, filter) {
        rows.indices.filter { r -> filter.isBlank() || rows[r].any { it.contains(filter, ignoreCase = true) } }
    }
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val hScroll = rememberScrollState()
    val cellW = 140.dp
    val numW = 48.dp
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Box(modifier.fillMaxSize().horizontalScroll(hScroll)) {
        LazyColumn(Modifier.width(numW + cellW * cols).fillMaxHeight()) {
            items(visible, key = { it }) { r ->
                Row {
                    Box(
                        Modifier.width(numW).heightIn(min = 48.dp).border(0.5.dp, line).padding(8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("${r + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                    for (c in 0 until cols) {
                        val text = rows[r].getOrElse(c) { "" }
                        Box(
                            Modifier
                                .width(cellW)
                                .heightIn(min = 48.dp)
                                .border(0.5.dp, line)
                                .clickable { editing = r to c }
                                .semantics { contentDescription = "Row ${r + 1}, column ${c + 1}: $text. Tap to edit." }
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { (r, c) ->
        var text by remember(r, c) { mutableStateOf(rows.getOrNull(r)?.getOrNull(c) ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Row ${r + 1}, column ${c + 1}") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { d.setCell(r, c, text); editing = null }) { Text("OK") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { d.deleteRow(r); editing = null }) { Text("Delete row") }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
