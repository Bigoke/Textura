package com.textura.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val TabTitles = listOf("Text", "Font", "Background", "Depth", "Layout", "Paper")

@Composable
fun EditorScreen(vm: AppViewModel, onDone: () -> Unit) {
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

        // ---- live preview
        Box(
            Modifier
                .weight(0.5f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ratio = vm.shape.ratio
                val w = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
                LookPreview(
                    look = look,
                    text = vm.text,
                    shape = vm.shape,
                    longEdge = 800,
                    modifier = Modifier
                        .width(w)
                        .shadow(10.dp, RoundedCornerShape(14.dp)),
                    debounceMs = 35,
                )
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
                    when (tab) {
                        0 -> TextTab(vm, look)
                        1 -> FontTab(vm, look)
                        2 -> BackgroundTab(vm, look)
                        3 -> DepthTab(vm, look)
                        4 -> LayoutTab(vm, look)
                        else -> PaperTab(vm, look)
                    }
                    SectionTitle("Finish")
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
