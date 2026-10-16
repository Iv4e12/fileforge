package app.fileforge.ui

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fileforge.AppViewModel
import app.fileforge.model.FileType

internal fun iconFor(type: FileType): ImageVector = when (type) {
    FileType.PDF -> Icons.Filled.PictureAsPdf
    FileType.CSV -> Icons.Filled.TableChart
    FileType.JSON, FileType.XML, FileType.HTML -> Icons.Filled.Code
    FileType.MARKDOWN, FileType.TEXT -> Icons.Filled.Description
    FileType.UNSUPPORTED -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpen: () -> Unit, onNew: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FileForge") },
                actions = {
                    IconButton(onClick = { vm.showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open file")
                        }
                        OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                            Icon(Icons.Filled.NoteAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("New file")
                        }
                    }
                }
                item {
                    Text(
                        "Recent files",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                if (vm.recents.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.Description, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.heightIn(min = 8.dp))
                            Text(
                                "No recent files yet.\nOpen a file or create a new one to get started.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(vm.recents, key = { it.uri }) { f ->
                        val type = FileType.detect(f.name, null)
                        ListItem(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.open(Uri.parse(f.uri)) },
                            headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                val ago = DateUtils.getRelativeTimeSpanString(
                                    f.openedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                                )
                                Text("${type.label} · $ago")
                            },
                            leadingContent = { Icon(iconFor(type), contentDescription = type.label) },
                            trailingContent = {
                                IconButton(onClick = { vm.removeRecent(f.uri) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove ${f.name} from recent files")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
