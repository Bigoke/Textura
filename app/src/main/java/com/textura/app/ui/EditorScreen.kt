package com.textura.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot

private val TabTitles = listOf("Text", "Font", "Background", "Depth", "Layout", "Paper", "Scene", "Stickers")
private const val STICKER_TAB = 7

@Composable
fun EditorScreen(vm: AppViewModel, onDone: () -> Unit, onHandwriting: () -> Unit = {}) {
    val look = vm.editing
    if (look == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var exportMode by remember { mutableStateOf<ExportMode?>(null) }
    var saveDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var selectedSticker by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    fun finish() {
        vm.finishEditing()
        onDone()
    }
    BackHandler { finish() }

    Column(Modifier.fillMaxSize()) {
        // ---- top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Edit look", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
            IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
            IconButton(onClick = vm::resetToTemplate) { Icon(Icons.Rounded.Restore, "Reset to template") }
            TextButton(onClick = ::finish) { Text("Done") }
        }

        // ---- live preview: pinch to zoom, drag to pan, double-tap to reset; drag stickers on the Stickers tab
        BoxWithConstraints(
            Modifier
                .weight(0.5f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = vm.shape.ratio
            val w = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
            val h = w / ratio
            val density = LocalDensity.current
            val wPx = with(density) { w.toPx() }
            val hPx = with(density) { h.toPx() }
            val stickerMode = tab == STICKER_TAB && look.stickers.any { it.id == selectedSticker }

            fun clampPan(p: Offset, z: Float): Offset {
                val mx = (z - 1f) * wPx / 2f
                val my = (z - 1f) * hPx / 2f
                return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(stickerMode, selectedSticker, wPx, hPx) {
                        if (stickerMode) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val id = selectedSticker ?: return@detectDragGestures
                                vm.edit { l ->
                                    l.copy(
                                        stickers = l.stickers.map {
                                            if (it.id == id) it.copy(
                                                x = (it.x + drag.x / (wPx * zoom)).coerceIn(0f, 1f),
                                                y = (it.y + drag.y / (hPx * zoom)).coerceIn(0f, 1f),
                                            ) else it
                                        },
                                    )
                                }
                            }
                        } else {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                val nz = (zoom * zoomChange).coerceIn(1f, 4f)
                                zoom = nz
                                pan = clampPan(pan + panChange, nz)
                            }
                        }
                    }
                    .pointerInput(tab, wPx, hPx) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = 1f
                                pan = Offset.Zero
                            },
                            onTap = { offset ->
                                if (tab == STICKER_TAB) {
                                    // select the sticker nearest to the tap
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val lx = (offset.x - cx - pan.x) / zoom + wPx / 2f
                                    val ly = (offset.y - cy - pan.y) / zoom + hPx / 2f
                                    val near = vm.editing?.stickers?.minByOrNull {
                                        hypot(it.x * wPx - lx, it.y * hPx - ly)
                                    }
                                    if (near != null && hypot(near.x * wPx - lx, near.y * hPx - ly) < wPx * 0.25f) {
                                        selectedSticker = near.id
                                    }
                                } else {
                                    tab = 0
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                val res = if (zoom > 2.4f) 3 else if (zoom > 1.25f) 2 else 1
                Box(
                    Modifier
                        .width(w)
                        .height(h)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                        .shadow(10.dp, RoundedCornerShape(14.dp)),
                ) {
                    LookPreview(
                        look = look,
                        text = vm.text,
                        shape = vm.shape,
                        longEdge = 800 * res,
                        modifier = Modifier.fillMaxSize(),
                        debounceMs = 35,
                    )
                    if (stickerMode) {
                        val sel = look.stickers.first { it.id == selectedSticker }
                        Canvas(Modifier.fillMaxSize()) {
                            val r = (sel.size * size.width / 2f).coerceAtLeast(18.dp.toPx()) + 6.dp.toPx()
                            drawCircle(
                                color = Color.White.copy(alpha = 0.9f),
                                radius = r.coerceAtMost(size.minDimension / 2f),
                                center = Offset(sel.x * size.width, sel.y * size.height),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }

        // ---- tabbed controls
        Surface(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.fillMaxSize()) {
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    edgePadding = 8.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                ) {
                    TabTitles.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp, bottom = 24.dp),
                ) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                        label = "tabContent",
                    ) { t -> Column(Modifier.fillMaxWidth()) { when (t) {
                        0 -> TextTab(vm, look)
                        1 -> FontTab(vm, look, onHandwriting)
                        2 -> BackgroundTab(vm, look)
                        3 -> DepthTab(vm, look)
                        4 -> LayoutTab(vm, look)
                        5 -> PaperTab(vm, look)
                        6 -> SceneTab(vm, look)
                        else -> StickersTab(vm, look, selectedSticker) { selectedSticker = it }
                    } } }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { templateName = ""; saveDialog = true }, modifier = Modifier.weight(1f)) {
                            Text("Save as my template")
                        }
                        Button(onClick = { exportMode = ExportMode.Save }, modifier = Modifier.weight(1f)) {
                            Text("Export")
                        }
                    }
                }
            }
        }
    }

    if (saveDialog) {
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("Save as my template") },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    singleLine = true,
                    label = { Text("Template name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveAsTemplate(look, templateName)
                    saveDialog = false
                    Toast.makeText(ctx, "Saved to My templates", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Cancel") } },
        )
    }

    exportMode?.let { mode -> ExportSheet(vm, look, mode) { exportMode = null } }
}
