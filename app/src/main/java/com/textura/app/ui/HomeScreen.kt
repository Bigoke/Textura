package com.textura.app.ui

import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.textura.app.data.Quotes
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.textura.app.data.HistoryItem
import com.textura.app.model.CanvasShape
import com.textura.app.render.Markup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel, onCreate: () -> Unit, onOpenHistory: (HistoryItem) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var scanDialog by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var tools by remember { mutableStateOf<ToolsRequest?>(null) }

    fun runOcr(uri: Uri) {
        scanning = true
        try {
            val image = InputImage.fromFilePath(ctx, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { res ->
                    scanning = false
                    val found = res.textBlocks.joinToString("\n\n") { b -> b.lines.joinToString(" ") { it.text } }
                    if (found.isBlank()) {
                        Toast.makeText(ctx, "No text found in that photo", Toast.LENGTH_SHORT).show()
                    } else {
                        tools = ToolsRequest(found, true, 0)
                    }
                }
                .addOnFailureListener {
                    scanning = false
                    Toast.makeText(ctx, "Could not read that photo", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            scanning = false
            Toast.makeText(ctx, "Could not open that photo", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runOcr(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) runOcr(uri)
    }
    val plain = Markup.strip(vm.text)
    val words = plain.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 110.dp),
        ) {
            FadeInItem(0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Textura", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "Turn any text into a designed page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OverflowMenu(
                        listOf(
                            MenuAction("Translate", Icons.Rounded.Translate, plain.isNotBlank()) { tools = ToolsRequest(vm.text, false, 0) },
                            MenuAction("Spelling & grammar", Icons.Rounded.Spellcheck, plain.isNotBlank()) { tools = ToolsRequest(vm.text, false, 1) },
                            MenuAction("Sample text", Icons.Rounded.TextFields) { vm.randomSample() },
                            MenuAction("Clear text", Icons.Rounded.Clear, plain.isNotBlank()) { vm.updateText("") },
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            FadeInItem(1) {
                Column {
                    OutlinedTextField(
                        value = vm.text,
                        onValueChange = vm::updateText,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 7,
                        maxLines = 12,
                        shape = RoundedCornerShape(22.dp),
                        placeholder = { Text("Paste or type your text…") },
                        visualTransformation = TagDimTransformation(MaterialTheme.colorScheme.outline),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        "${plain.length} characters · $words words",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            FadeInItem(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { scanDialog = true }, enabled = !scanning, modifier = Modifier.weight(1f).pressScale()) {
                        if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.CameraAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Photo to text")
                    }
                    OutlinedButton(onClick = {
                        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(vm::updateText)
                    }, modifier = Modifier.weight(1f).pressScale()) {
                        Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Paste")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            FadeInItem(3) {
                Column {
                    Group("Format · ${vm.shape.label}", stateKey = "home-format") {
                        ShapePicker(vm.shape) { vm.updateShape(it) }
                    }
                    Group("Quote of the day", stateKey = "home-quote") {
                        QuoteCard { vm.updateText(it) }
                    }
                }
            }

            if (vm.history.isNotEmpty()) {
                FadeInItem(4) {
                    Column {
                        SectionTitle("Recent designs")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(vm.history.take(12), key = { it.id }) { item ->
                                HistoryThumb(vm, item, 120.dp, Modifier.pressScale(0.96f)) { onOpenHistory(item) }
                            }
                        }
                    }
                }
            }
        }

        tools?.let { req -> TextToolsSheet(vm, req, onDismiss = { tools = null }) }

        if (scanDialog) {
            AlertDialog(
                onDismissRequest = { scanDialog = false },
                title = { Text("Import text from a photo") },
                text = { Text("Pick a photo of a page, sign or note, or take one now. The text is read on your device. You can then fix it, translate it between Afrikaans and English, and check the spelling.") },
                confirmButton = {
                    TextButton(onClick = {
                        scanDialog = false
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Choose photo") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        scanDialog = false
                        val f = File(ctx.cacheDir, "shared/scan.jpg").also { it.parentFile?.mkdirs() }
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                        cameraUri = uri
                        camera.launch(uri)
                    }) { Text("Take photo") }
                },
            )
        }

        ExtendedFloatingActionButton(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .pressScale(),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null)
            Spacer(Modifier.width(10.dp))
            Text("Create", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A saved-design thumbnail, loaded from app storage. */
@Composable
fun HistoryThumb(vm: AppViewModel, item: HistoryItem, height: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) { vm.thumb(item.id) }
    }
    Box(
        modifier
            .height(height)
            .aspectRatio(item.shape.ratio)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF22222A))
            .clickable(onClick = onClick),
    ) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun QuoteCard(onUse: (String) -> Unit) {
    var quote by remember { mutableStateOf(Quotes.ofTheDay()) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Quote of the day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("\u201C${quote.text}\u201D", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            Text("\u2014 ${quote.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = { onUse(quote.asText()) }) { Text("Use this") }
                TextButton(onClick = { quote = Quotes.random(quote.text) }) { Text("Another") }
            }
        }
    }
}
