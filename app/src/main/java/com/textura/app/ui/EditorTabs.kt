package com.textura.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.textura.app.model.Align
import com.textura.app.model.Background
import com.textura.app.model.BgKind
import com.textura.app.model.CanvasShape
import com.textura.app.model.Depth
import com.textura.app.model.AppFont
import com.textura.app.model.FontGroup
import com.textura.app.model.Fonts
import com.textura.app.model.GradientType
import com.textura.app.model.Look
import com.textura.app.model.PaperType
import com.textura.app.model.c
import com.textura.app.model.ca
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ------------------------------------------------------------------------------------------------
// TEXT
// ------------------------------------------------------------------------------------------------
@Composable
fun TextTab(vm: AppViewModel, look: Look) {
    OutlinedTextField(
        value = vm.text,
        onValueChange = vm::updateText,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        minLines = 2,
        maxLines = 5,
        label = { Text("Your text") },
        shape = RoundedCornerShape(16.dp),
    )
    SectionTitle("Style")
    SwitchRow("UPPERCASE", look.uppercase) { on -> vm.edit { it.copy(uppercase = on) } }
    SwitchRow("Bold", look.bold) { on -> vm.edit { it.copy(bold = on) } }
    SectionTitle("Colour")
    ColorField("Text colour", look.textColor, withAlpha = true) { col -> vm.edit { it.copy(textColor = col) } }
    SwitchRow("Gradient fill", look.textColor2 != null) { on ->
        vm.edit { l ->
            l.copy(textColor2 = if (on) (l.textColor2 ?: ColorUtils.blendARGB(l.textColor, android.graphics.Color.BLACK, 0.45f)) else null)
        }
    }
    look.textColor2?.let { second ->
        ColorField("Gradient end colour", second) { col -> vm.edit { it.copy(textColor2 = col) } }
    }
}

// ------------------------------------------------------------------------------------------------
// FONT
// ------------------------------------------------------------------------------------------------
@Composable
fun FontTab(vm: AppViewModel, look: Look) {
    var group by remember { mutableStateOf<FontGroup?>(null) }
    ChipRow(listOf<FontGroup?>(null) + FontGroup.entries, group, { it?.label ?: "All" }) { group = it }
    val fonts = Fonts.all.filter { group == null || it.group == group }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        items(fonts, key = { it.id }) { f ->
            FontCard(f, f.id == look.fontId) { vm.edit { it.copy(fontId = f.id) } }
        }
    }
    LabeledSlider("Letter spacing", look.letterSpacing, -0.05f..0.3f, { v -> vm.edit { it.copy(letterSpacing = v) } })
    SwitchRow("Handwritten movement", look.handwritten) { on -> vm.edit { it.copy(handwritten = on) } }
    LabeledSlider(
        "Realism (ink and hand imperfection)", look.imperfection, 0f..1f,
        { v -> vm.edit { it.copy(imperfection = v) } },
        "${(look.imperfection * 100).toInt()}%",
    )
}

@Composable
private fun FontCard(font: AppFont, selected: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val family = remember(font.id) { FontFamily(Font(font.assetPath, ctx.assets)) }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aa", fontFamily = family, fontSize = 30.sp, maxLines = 1)
        Text(font.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------------------
// BACKGROUND
// ------------------------------------------------------------------------------------------------
private val GradientPresets: List<List<Int>> = listOf(
    listOf(0xFF512F, 0xDD2476), listOf(0x0F2027, 0x2C7A94), listOf(0xFF4E50, 0xF9D423),
    listOf(0xFFD6E8, 0xD6E4FF), listOf(0x232526, 0x414345), listOf(0x11998E, 0x38EF7D),
    listOf(0x4776E6, 0x8E54E9), listOf(0xFFB347, 0xFFCC33), listOf(0x1A1A24, 0x0B0B10),
    listOf(0xFF6FB5, 0x7A2BFF, 0x16D9FF), listOf(0xF9F586, 0xFFB5A7, 0xA0C4FF), listOf(0x0F0C29, 0x302B63, 0x24C6DC),
).map { list -> list.map { c(it) } }

@Composable
fun BackgroundTab(vm: AppViewModel, look: Look) {
    val bg = look.bg
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun setBg(t: (Background) -> Background) = vm.edit { l -> l.copy(bg = t(l.bg)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { vm.importPhoto(uri) }
                if (path != null) setBg { it.copy(kind = BgKind.Image, imagePath = path) }
                else Toast.makeText(ctx, "Could not open that photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ChipRow(
        BgKind.entries, bg.kind,
        { when (it) { BgKind.Solid -> "Colour"; BgKind.Gradient -> "Gradient"; BgKind.Image -> "Photo" } },
    ) { kind ->
        setBg { b ->
            when (kind) {
                BgKind.Gradient -> b.copy(
                    kind = kind,
                    colors = if (b.colors.size >= 2) b.colors else listOf(b.colors.first(), c(0x3A3A52)),
                )
                else -> b.copy(kind = kind)
            }
        }
    }

    when (bg.kind) {
        BgKind.Solid -> {
            SectionTitle("Colour")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Palette) { p ->
                    ColorDot(p, 36.dp, selected = bg.colors.firstOrNull() == p) { setBg { it.copy(colors = listOf(p)) } }
                }
            }
            ColorField("Custom colour", bg.colors.firstOrNull() ?: c(0x14141A)) { col -> setBg { it.copy(colors = listOf(col)) } }
        }

        BgKind.Gradient -> {
            SectionTitle("Type")
            ChipRow(GradientType.entries, bg.gradientType, { it.name }) { t -> setBg { it.copy(gradientType = t) } }
            SectionTitle("Colours (2 to 5)")
            ColorsEditor(bg.colors, 2, 5) { list -> setBg { it.copy(colors = list) } }
            if (bg.gradientType == GradientType.Linear) {
                LabeledSlider("Angle", bg.angle, 0f..360f, { v -> setBg { it.copy(angle = v) } }, "${bg.angle.toInt()}°")
            }
            SectionTitle("Presets")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(GradientPresets) { preset ->
                    GradientSwatch(preset) { setBg { it.copy(colors = preset) } }
                }
            }
        }

        BgKind.Image -> {
            SectionTitle("Photo")
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (bg.imagePath == null) "Choose photo" else "Change photo")
            }
            if (bg.imagePath != null) {
                LabeledSlider("Blur", bg.blur, 0f..1f, { v -> setBg { it.copy(blur = v) } })
                LabeledSlider("Dim", bg.dim, 0f..0.9f, { v -> setBg { it.copy(dim = v) } })
                SwitchRow("Colour overlay", bg.overlay.isNotEmpty()) { on ->
                    setBg {
                        if (on) it.copy(overlay = listOf(c(0xFF512F), c(0x4A1B6B)), overlayAlpha = 0.5f)
                        else it.copy(overlay = emptyList(), overlayAlpha = 0f)
                    }
                }
                if (bg.overlay.isNotEmpty()) {
                    ColorsEditor(bg.overlay, 1, 5) { list -> setBg { it.copy(overlay = list) } }
                    LabeledSlider("Overlay strength", bg.overlayAlpha, 0f..1f, { v -> setBg { it.copy(overlayAlpha = v) } })
                    LabeledSlider("Overlay angle", bg.angle, 0f..360f, { v -> setBg { it.copy(angle = v) } }, "${bg.angle.toInt()}°")
                }
            }
        }
    }
}

@Composable
private fun ColorsEditor(colors: List<Int>, min: Int, max: Int, onChange: (List<Int>) -> Unit) {
    var editIdx by remember { mutableIntStateOf(-1) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { i, col -> ColorDot(col, 40.dp) { editIdx = i } }
        if (colors.size < max) {
            IconButton(onClick = { onChange(colors + colors.last()) }) { Icon(Icons.Rounded.Add, "Add colour") }
        }
        if (colors.size > min) {
            IconButton(onClick = { onChange(colors.dropLast(1)) }) { Icon(Icons.Rounded.Remove, "Remove colour") }
        }
    }
    if (editIdx in colors.indices) {
        val idx = editIdx
        ColorDialog(colors[idx], false, { editIdx = -1 }) { picked ->
            onChange(colors.toMutableList().also { it[idx] = picked })
        }
    }
}

// ------------------------------------------------------------------------------------------------
// DEPTH
// ------------------------------------------------------------------------------------------------
@Composable
fun DepthTab(vm: AppViewModel, look: Look) {
    ChipRow(Depth.entries, look.depth, { it.label }) { d -> vm.edit { it.copy(depth = d) } }

    when (look.depth) {
        Depth.Flat -> {
            SectionTitle("Outline")
            SwitchRow("Outline", look.outline) { on -> vm.edit { it.copy(outline = on) } }
            if (look.outline) {
                ColorField("Outline colour", look.outlineColor) { col -> vm.edit { it.copy(outlineColor = col) } }
                LabeledSlider("Outline width", look.outlineWidth, 0.01f..0.15f, { v -> vm.edit { it.copy(outlineWidth = v) } })
            }
            SectionTitle("Shadow and glow")
            SwitchRow("Shadow", look.shadow) { on -> vm.edit { it.copy(shadow = on) } }
            if (look.shadow) {
                ColorField("Shadow colour", look.shadowColor, withAlpha = true) { col -> vm.edit { it.copy(shadowColor = col) } }
                LabeledSlider("Blur", look.shadowBlur, 0f..0.4f, { v -> vm.edit { it.copy(shadowBlur = v) } })
                LabeledSlider("Offset", look.shadowOffset, 0f..0.2f, { v -> vm.edit { it.copy(shadowOffset = v) } })
            }
        }

        Depth.Extrude -> {
            SectionTitle("3D extrusion")
            LabeledSlider("Depth", look.depthLength, 0.05f..1f, { v -> vm.edit { it.copy(depthLength = v) } })
            LabeledSlider(
                "Direction", look.depthAngle, 0f..360f,
                { v -> vm.edit { it.copy(depthAngle = v) } }, "${look.depthAngle.toInt()}°",
            )
            ColorField("Side colour", look.sideColor) { col -> vm.edit { it.copy(sideColor = col) } }
            SwitchRow("Shade sides for depth", look.gradientSide) { on -> vm.edit { it.copy(gradientSide = on) } }
            SwitchRow("Edge highlight", look.highlight) { on -> vm.edit { it.copy(highlight = on) } }
        }

        Depth.Emboss, Depth.Engrave -> {
            Text(
                if (look.depth == Depth.Emboss) "Text rises out of the surface. Works best when the text colour is close to the paper or background colour."
                else "Text is pressed into the surface. Pick a text colour a little darker than the surface.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// LAYOUT
// ------------------------------------------------------------------------------------------------
@Composable
fun LayoutTab(vm: AppViewModel, look: Look) {
    SectionTitle("Shape (text always fills it)")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CanvasShape.entries.forEach { s -> ShapeGlyph(s, s == vm.shape) { vm.updateShape(s) } }
    }
    SectionTitle("Alignment")
    ChipRow(Align.entries, look.align, { it.name }) { a -> vm.edit { it.copy(align = a) } }
    Spacer(Modifier.height(4.dp))
    LabeledSlider("Margins", look.margin, 0f..0.2f, { v -> vm.edit { it.copy(margin = v) } })
    LabeledSlider("Line spacing", look.lineSpacing, 0.7f..2f, { v -> vm.edit { it.copy(lineSpacing = v) } })
    LabeledSlider("Letter spacing", look.letterSpacing, -0.05f..0.3f, { v -> vm.edit { it.copy(letterSpacing = v) } })
    SwitchRow("Spread lines to fill the height", look.fillHeight) { on -> vm.edit { it.copy(fillHeight = on) } }
}

// ------------------------------------------------------------------------------------------------
// PAPER
// ------------------------------------------------------------------------------------------------
private fun defaultPaperColor(type: PaperType): Int = when (type) {
    PaperType.Lined -> c(0xFBF8EF)
    PaperType.Graph -> c(0xF7FAFC)
    PaperType.Dotted -> c(0xF3EFE6)
    PaperType.Parchment -> c(0xE8D5A8)
    PaperType.Letter -> c(0xE6D2A2)
    PaperType.Sticky -> c(0xFFE76B)
    PaperType.Receipt -> c(0xFAFAF4)
    PaperType.Chalkboard -> c(0x2F4F43)
    PaperType.Newsprint -> c(0xE4DFD3)
    PaperType.Kraft -> c(0xB98B5E)
    PaperType.Glass -> ca(70, 0xFFFFFF)
    PaperType.None -> c(0xF6F1E7)
}

@Composable
fun PaperTab(vm: AppViewModel, look: Look) {
    ChipRow(PaperType.entries, look.paper, { it.label }) { type ->
        vm.edit { it.copy(paper = type, paperColor = defaultPaperColor(type)) }
    }
    if (look.paper == PaperType.None) {
        Text(
            "Pick a paper to write on. The page background then becomes the desk behind it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    } else {
        SectionTitle("Sheet")
        ColorField("Paper colour", look.paperColor) { col -> vm.edit { it.copy(paperColor = col) } }
        LabeledSlider("Texture", look.grain, 0f..1.2f, { v -> vm.edit { it.copy(grain = v) } })
        LabeledSlider("Float on desk", look.paperInset, 0f..0.15f, { v -> vm.edit { it.copy(paperInset = v) } })
        LabeledSlider(
            "Tilt", look.tilt, -8f..8f, { v -> vm.edit { it.copy(tilt = v) } },
            String.format(Locale.US, "%.1f°", look.tilt),
        )
    }
}
