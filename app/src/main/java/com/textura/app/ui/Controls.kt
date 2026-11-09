package com.textura.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.textura.app.model.CanvasShape
import com.textura.app.model.Look
import com.textura.app.render.Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import android.graphics.Color as AColor
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.textura.app.render.Markup

// ------------------------------------------------------------------------------------------------
// Preview rendering with an in-memory cache
// ------------------------------------------------------------------------------------------------

object PreviewCache {
    private val cache = object : android.util.LruCache<String, Bitmap>(72 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun get(context: android.content.Context, look: Look, text: String, shape: CanvasShape, longEdge: Int): Bitmap {
        val key = "${look.hashCode()}|${text.hashCode()}|${shape.name}|$longEdge"
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.Default) { Renderer.render(context, look, text, shape, longEdge) }
        cache.put(key, bmp)
        return bmp
    }
}

/** A rendered look inside a rounded card with the correct aspect ratio. */
@Composable
fun LookPreview(
    look: Look,
    text: String,
    shape: CanvasShape,
    longEdge: Int,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    debounceMs: Long = 0L,
) {
    val ctx = LocalContext.current.applicationContext
    val bmp by produceState<Bitmap?>(null, look, text, shape, longEdge) {
        if (debounceMs > 0) delay(debounceMs)
        value = PreviewCache.get(ctx, look, text, shape, longEdge)
    }
    Box(
        modifier
            .aspectRatio(shape.ratio)
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF22222A)),
    ) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = look.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Generic controls
// ------------------------------------------------------------------------------------------------

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    valueText: String = String.format(Locale.US, "%.2f", value),
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

/** Small outline of a canvas shape, used in shape pickers. */
@Composable
fun ShapeGlyph(shape: CanvasShape, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            val r = shape.ratio
            val m = if (r >= 1f) Modifier.width(38.dp).height((38f / r).dp) else Modifier.height(38.dp).width((38f * r).dp)
            Box(
                m
                    .clip(RoundedCornerShape(5.dp))
                    .border(BorderStroke(2.dp, if (selected) primary else outline), RoundedCornerShape(5.dp)),
            )
        }
        Text(shape.label, style = MaterialTheme.typography.labelMedium)
    }
}

// ------------------------------------------------------------------------------------------------
// Colour picking
// ------------------------------------------------------------------------------------------------

val Palette: List<Int> = listOf(
    0x000000, 0x1A1A1A, 0x3B3B44, 0x8A8F98, 0xD9D9D9, 0xFFFFFF,
    0xE0312B, 0xFF6A3D, 0xFFB000, 0xFFE76B, 0xA8D86B, 0x39B36A,
    0x16A39A, 0x16D9FF, 0x2F7DE1, 0x1F3A93, 0x7A2BFF, 0xC13BD6,
    0xFF2BD6, 0xFF9CC8, 0x8B5E3C, 0xB98B5E, 0xF2E7CC, 0x2F4F43,
).map { it or -0x1000000 }

@Composable
fun ColorDot(color: Int, size: Dp = 30.dp, selected: Boolean = false, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
            .border(BorderStroke(if (selected) 3.dp else 1.dp, border), CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
fun ColorField(label: String, color: Int, withAlpha: Boolean = false, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        ColorDot(color, 32.dp) { open = true }
    }
    if (open) ColorDialog(color, withAlpha, { open = false }, onChange)
}

@Composable
fun ColorDialog(initial: Int, withAlpha: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { AColor.colorToHSV(initial, it) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    var alpha by remember { mutableStateOf(AColor.alpha(initial) / 255f) }
    val current = AColor.HSVToColor(if (withAlpha) (alpha * 255f).toInt() else 255, floatArrayOf(hue, sat, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour") },
        text = {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Palette) { p ->
                        ColorDot(p, 30.dp, selected = (p and 0xFFFFFF) == (current and 0xFFFFFF)) {
                            val tmp = FloatArray(3)
                            AColor.colorToHSV(p, tmp)
                            hue = tmp[0]; sat = tmp[1]; value = tmp[2]
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LabeledSlider("Hue", hue, 0f..360f, { hue = it }, "${hue.toInt()}°")
                LabeledSlider("Saturation", sat, 0f..1f, { sat = it })
                LabeledSlider("Brightness", value, 0f..1f, { value = it })
                if (withAlpha) LabeledSlider("Opacity", alpha, 0f..1f, { alpha = it })
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(current)),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current); onDismiss() }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small round gradient swatch used by the background presets. */
@Composable
fun GradientSwatch(colors: List<Int>, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    if (colors.size >= 2) colors.map { Color(it) } else listOf(Color(colors.first()), Color(colors.first())),
                ),
            )
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}


// ------------------------------------------------------------------------------------------------
// Shared helpers
// ------------------------------------------------------------------------------------------------

/** Scrollable row of every canvas size. */
@Composable
fun ShapePicker(selected: CanvasShape, onSelect: (CanvasShape) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CanvasShape.entries.forEach { s -> ShapeGlyph(s, s == selected) { onSelect(s) } }
    }
}

/** Dims the [b]...[/b] style formatting tags inside a text field so the words stay readable. */
class TagDimTransformation(private val tagColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            Markup.TAG.findAll(text.text).forEach {
                addStyle(SpanStyle(color = tagColor), it.range.first, it.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** A colour from the look that suits the interface accent (most saturated, not too dark). */
fun accentOf(look: Look, fallback: Color): Color {
    val candidates = ArrayList<Int>()
    look.shapeColor?.let { candidates.add(it) }
    look.textColor2?.let { candidates.add(it) }
    candidates.addAll(look.bg.colors)
    candidates.add(look.textColor)
    var best: Int? = null
    var bestScore = 0.3f
    val hsv = FloatArray(3)
    for (col in candidates) {
        AColor.colorToHSV(col, hsv)
        if (hsv[2] < 0.35f) continue
        if (hsv[1] > bestScore) { bestScore = hsv[1]; best = col }
    }
    return best?.let { Color(it or -0x1000000) } ?: fallback
}
