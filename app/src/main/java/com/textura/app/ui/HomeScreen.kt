package com.textura.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
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

@Composable
fun HomeScreen(vm: AppViewModel, onCreate: () -> Unit, onOpenHistory: (HistoryItem) -> Unit) {
    val clipboard = LocalClipboardManager.current
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
            Text("Textura", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Turn any text into a designed page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = vm.text,
                onValueChange = vm::updateText,
                modifier = Modifier.fillMaxWidth(),
                minLines = 7,
                maxLines = 12,
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Paste or type your text…") },
                visualTransformation = TagDimTransformation(MaterialTheme.colorScheme.outline),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${plain.length} characters · $words words",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(vm::updateText)
                }) {
                    Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste")
                }
                OutlinedButton(onClick = { vm.updateText("") }) {
                    Icon(Icons.Rounded.Clear, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
                OutlinedButton(onClick = vm::randomSample) {
                    Icon(Icons.Rounded.TextFields, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sample")
                }
            }

            SectionTitle("Shape")
            ShapePicker(vm.shape) { vm.updateShape(it) }

            if (vm.history.isNotEmpty()) {
                SectionTitle("Recent designs")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vm.history.take(12), key = { it.id }) { item ->
                        HistoryThumb(vm, item, 120.dp) { onOpenHistory(item) }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
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
