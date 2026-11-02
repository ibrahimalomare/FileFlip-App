package com.fileflip.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { FileFlipScreen() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileFlipScreen(vm: MainViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onFilePicked(uri)
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("FileFlip", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("PDF to image converter", style = MaterialTheme.typography.bodyLarge)

        Button(
            onClick = { picker.launch(arrayOf("application/pdf")) },
            enabled = !s.converting,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Select PDF") }

        s.error?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = vm::dismissError) { Text("Dismiss") }
                }
            }
        }

        s.file?.let { f ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium)
                    val size = if (f.sizeBytes >= 0) Formatter.formatShortFileSize(ctx, f.sizeBytes) else "unknown size"
                    Text("PDF · $size · ${f.pages} page${if (f.pages == 1) "" else "s"}")
                }
            }

            Text("Convert to", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ImageFormat.values().forEachIndexed { i, fmt ->
                    SegmentedButton(
                        selected = s.format == fmt,
                        onClick = { vm.setFormat(fmt) },
                        enabled = !s.converting,
                        shape = SegmentedButtonDefaults.itemShape(i, ImageFormat.values().size)
                    ) { Text(fmt.label) }
                }
            }

            if (s.converting) {
                LinearProgressIndicator(
                    progress = { if (s.total > 0) s.done / s.total.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Converting page ${s.done} of ${s.total}…")
                OutlinedButton(onClick = vm::cancel, Modifier.fillMaxWidth()) { Text("Cancel") }
            } else {
                Button(onClick = vm::convert, Modifier.fillMaxWidth().height(56.dp)) { Text("Convert") }
            }
        }

        if (s.results.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Done!", style = MaterialTheme.typography.titleLarge)
                    Text("${s.results.size} image${if (s.results.size == 1) "" else "s"} saved to Pictures/FileFlip.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openFirst(ctx, s.results, s.format) }) { Text("Open") }
                        FilledTonalButton(onClick = { share(ctx, s.results, s.format) }) { Text("Share") }
                    }
                }
            }
        }
    }
}

private fun openFirst(ctx: Context, uris: List<Uri>, fmt: ImageFormat) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uris.first(), fmt.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app found to open images", Toast.LENGTH_SHORT).show()
    }
}

private fun share(ctx: Context, uris: List<Uri>, fmt: ImageFormat) {
    val i = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    i.type = fmt.mime
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Share images"))
}
