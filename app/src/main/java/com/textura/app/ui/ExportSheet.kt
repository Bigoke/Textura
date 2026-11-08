package com.textura.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.textura.app.data.ImageFormat
import com.textura.app.model.Look
import com.textura.app.render.Renderer
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportMode { Save, Share }

private val Resolutions = listOf(1080 to "HD", 2048 to "2K", 3840 to "4K")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: AppViewModel, look: Look, mode: ExportMode, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var longEdge by remember { mutableStateOf(vm.settings.defaultLongEdge) }
    var format by remember { mutableStateOf(vm.settings.defaultFormat) }
    var transparent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val (dw, dh) = Renderer.designSize(vm.shape)
    val scale = longEdge / max(dw, dh)
    val sizeText = "${(dw * scale).roundToInt()} × ${(dh * scale).roundToInt()} px"

    fun run(share: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val result = vm.export(look, share, longEdge, format, transparent)
            busy = false
            when (result) {
                ExportResult.Saved -> {
                    Toast.makeText(ctx, "Saved to Pictures/Textura", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
                ExportResult.Shared -> onDismiss()
                ExportResult.Failed ->
                    Toast.makeText(ctx, "Export failed. Try a smaller size.", Toast.LENGTH_LONG).show()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Export", style = MaterialTheme.typography.titleLarge)
            Text(sizeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("Resolution (longest side)")
            val selectedRes = Resolutions.firstOrNull { it.first == longEdge } ?: Resolutions[1]
            ChipRow(Resolutions, selectedRes, { "${it.second} · ${it.first}" }) {
                longEdge = it.first
            }

            SectionTitle("Format")
            ChipRow(ImageFormat.entries, format, { it.label }) {
                format = it
                if (it == ImageFormat.JPG) transparent = false
            }

            if (format == ImageFormat.PNG) {
                SwitchRow("Transparent background (text only)", transparent) { transparent = it }
            }

            Spacer(Modifier.height(18.dp))
            if (busy) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Rendering…")
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val saveModifier = Modifier.weight(1f)
                    if (mode == ExportMode.Save) {
                        Button(onClick = { run(false) }, modifier = saveModifier) {
                            Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save")
                        }
                        OutlinedButton(onClick = { run(true) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
                        }
                    } else {
                        OutlinedButton(onClick = { run(false) }, modifier = saveModifier) {
                            Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save")
                        }
                        Button(onClick = { run(true) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
                        }
                    }
                }
            }
        }
    }
}
