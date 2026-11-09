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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.textura.app.data.Exporter
import com.textura.app.data.ImageFormat
import com.textura.app.model.Look
import com.textura.app.render.Renderer
import com.textura.app.render.Watermark
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportMode { Save, Share }

private val Resolutions = listOf(1080 to "HD", 2048 to "2K", 3840 to "4K")
private val SigPositions = listOf(0 to "Left", 1 to "Centre", 2 to "Right")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: AppViewModel, look: Look, mode: ExportMode, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var longEdge by remember { mutableIntStateOf(vm.settings.defaultLongEdge) }
    var format by remember { mutableStateOf(vm.settings.defaultFormat) }
    var transparent by remember { mutableStateOf(false) }
    var pages by remember { mutableIntStateOf(1) }
    var signOn by remember { mutableStateOf(vm.settings.signatureOn && vm.settings.signature.isNotBlank()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val (dw, dh) = Renderer.designSize(vm.shape)
    val scale = longEdge / max(dw, dh)
    val sizeText = "${(dw * scale).roundToInt()} × ${(dh * scale).roundToInt()} px" +
        if (pages > 1) " · $pages pages" else ""
    val signature = if (signOn && vm.settings.signature.isNotBlank()) Watermark(vm.settings.signature, vm.settings.signaturePos) else null

    fun run(share: Boolean, pkg: String? = null) {
        if (busy) return
        busy = true
        job = scope.launch {
            val result = vm.export(look, share, longEdge, format, transparent, pages, signature, pkg)
            busy = false
            when (result) {
                ExportResult.Saved -> {
                    Toast.makeText(ctx, "Saved to Pictures/Textura", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
                ExportResult.Shared -> onDismiss()
                ExportResult.NoApp ->
                    Toast.makeText(ctx, "That app isn't installed. Use More… to pick another.", Toast.LENGTH_LONG).show()
                ExportResult.Failed ->
                    Toast.makeText(ctx, "Export failed. Try a smaller size.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun runBatch(favoritesOnly: Boolean) {
        if (busy) return
        val list = if (favoritesOnly) vm.favoriteLooks() else vm.looks.filter { it.id != AppViewModel.WORKING_ID }
        if (list.isEmpty()) {
            Toast.makeText(ctx, "No favorites yet. Tap the heart on looks you like.", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        progress = 0 to list.size
        job = scope.launch {
            val saved = vm.exportBatch(list, longEdge.coerceAtMost(2048), format, signature) { done, total ->
                progress = done to total
            }
            busy = false
            progress = null
            Toast.makeText(ctx, "Saved $saved images to Pictures/Textura/All looks", Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Export", style = MaterialTheme.typography.titleLarge)
            Text(sizeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("Resolution (longest side)")
            val selectedRes = Resolutions.firstOrNull { it.first == longEdge } ?: Resolutions[1]
            ChipRow(Resolutions, selectedRes, { "${it.second} · ${it.first}" }) { longEdge = it.first }

            SectionTitle("Format")
            ChipRow(ImageFormat.entries, format, { it.label }) {
                format = it
                if (it == ImageFormat.JPG) transparent = false
            }
            if (format == ImageFormat.PNG) {
                SwitchRow("Transparent background (text only)", transparent) { transparent = it }
            }

            SectionTitle("Carousel (split long text over several pages)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pages = (pages - 1).coerceAtLeast(1) }, enabled = !busy) { Icon(Icons.Rounded.Remove, "Fewer pages") }
                Text(if (pages == 1) "Single image" else "$pages pages", modifier = Modifier.width(110.dp))
                IconButton(onClick = { pages = (pages + 1).coerceAtMost(10) }, enabled = !busy) { Icon(Icons.Rounded.Add, "More pages") }
            }

            SectionTitle("Signature")
            SwitchRow("Add my signature", signOn) { signOn = it }
            if (signOn) {
                OutlinedTextField(
                    value = vm.settings.signature,
                    onValueChange = { v -> vm.updateSettings { it.copy(signature = v, signatureOn = true) } },
                    singleLine = true,
                    label = { Text("Signature text, e.g. @yourname") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipRow(SigPositions, SigPositions.first { it.first == vm.settings.signaturePos }, { it.second }) { p ->
                    vm.updateSettings { it.copy(signaturePos = p.first) }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (busy) {
                val p = progress
                if (p != null) {
                    Text("Saving ${p.first} / ${p.second}", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { if (p.second == 0) 0f else p.first.toFloat() / p.second },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    TextButton(onClick = { job?.cancel(); busy = false; progress = null }) { Text("Stop") }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Rendering…")
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mode == ExportMode.Save) {
                        Button(onClick = { run(false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save")
                        }
                        OutlinedButton(onClick = { run(true) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("More…")
                        }
                    } else {
                        OutlinedButton(onClick = { run(false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save")
                        }
                        Button(onClick = { run(true) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("More…")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { run(true, Exporter.WHATSAPP) }, modifier = Modifier.weight(1f)) { Text("WhatsApp") }
                    OutlinedButton(onClick = { run(true, Exporter.INSTAGRAM) }, modifier = Modifier.weight(1f)) { Text("Instagram") }
                    OutlinedButton(onClick = { run(true, Exporter.FACEBOOK) }, modifier = Modifier.weight(1f)) { Text("Facebook") }
                }

                SectionTitle("Batch (this text in many looks)")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { runBatch(false) }, modifier = Modifier.weight(1f)) { Text("All looks") }
                    OutlinedButton(onClick = { runBatch(true) }, modifier = Modifier.weight(1f)) { Text("Favorites") }
                }
            }
        }
    }
}
