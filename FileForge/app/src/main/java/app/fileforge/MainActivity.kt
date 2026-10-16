package app.fileforge

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.contract.ActivityResultContract
import androidx.activity.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import app.fileforge.editor.DocState
import app.fileforge.ui.EditorScreen
import app.fileforge.ui.FileForgeTheme
import app.fileforge.ui.HomeScreen
import app.fileforge.ui.SettingsScreen
import app.fileforge.ui.useDarkTheme

/** Opens the system "create document" picker with a suggested name and MIME type. */
class CreateDoc : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.openPicked(it) } }
    private val newLauncher =
        registerForActivityResult(CreateDoc()) { uri -> uri?.let { vm.createdNew(it) } }
    private val saveAsLauncher =
        registerForActivityResult(CreateDoc()) { uri -> uri?.let { vm.saveAs(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val dark = useDarkTheme(vm.themeMode)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            FileForgeTheme(dark) {
                AppRoot(
                    vm = vm,
                    onOpen = { openLauncher.launch(arrayOf("*/*")) },
                    onNew = { newLauncher.launch("untitled.txt" to "text/plain") },
                    onSaveAs = { name, mime -> saveAsLauncher.launch(name to mime) },
                    onShare = { share(it) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { vm.openExternal(it) }
    }

    private fun share(d: DocState) {
        val mime = try { contentResolver.getType(d.uri) } catch (e: Exception) { null } ?: d.type.mime
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, d.uri)
            clipData = ClipData.newRawUri(d.name, d.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share ${d.name}"))
    }
}

@Composable
private fun AppRoot(
    vm: AppViewModel,
    onOpen: () -> Unit,
    onNew: () -> Unit,
    onSaveAs: (String, String) -> Unit,
    onShare: (DocState) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    val doc = vm.doc
    Box {
        when {
            vm.showSettings -> {
                BackHandler { vm.showSettings = false }
                SettingsScreen(vm)
            }
            doc != null -> EditorScreen(vm, doc, onSaveAs, { onShare(doc) })
            else -> HomeScreen(vm, onOpen, onNew)
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
        )
    }
}
