package app.fileforge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fileforge.AppViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { vm.showSettings = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                listOf(2 to "Dark (default)", 1 to "Light", 0 to "Follow system").forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = vm.themeMode == mode,
                            onClick = { vm.setTheme(mode) },
                            role = Role.RadioButton,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = vm.themeMode == mode, onClick = null)
                        Text(label, Modifier.padding(start = 12.dp))
                    }
                }
            }

            Text("Editor font size: ${vm.fontSize} sp", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = vm.fontSize.toFloat(),
                onValueChange = { vm.setFontSize(it.roundToInt()) },
                valueRange = 12f..24f,
                steps = 11,
            )
            Text(
                "The quick brown fox jumps over the lazy dog.",
                fontFamily = FontFamily.Monospace,
                fontSize = vm.fontSize.sp,
            )

            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wrap long lines", Modifier.weight(1f))
                Switch(checked = vm.wordWrap, onCheckedChange = { vm.setWordWrap(it) })
            }

            OutlinedButton(onClick = { vm.clearRecents(); vm.message = "Recent files cleared." }) {
                Text("Clear recent files")
            }

            Text("Privacy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "FileForge works fully offline. It requests no internet or storage permissions, " +
                    "has no accounts, ads or tracking, and never uploads your files. " +
                    "Files are accessed only when you pick them with the system file picker.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Version 1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
