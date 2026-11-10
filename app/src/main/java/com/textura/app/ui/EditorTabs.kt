package com.textura.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.textura.app.model.Align
import com.textura.app.model.AppFont
import com.textura.app.model.Background
import com.textura.app.model.BgKind
import com.textura.app.model.Depth
import com.textura.app.model.FoilType
import com.textura.app.model.FontGroup
import com.textura.app.model.Fonts
import com.textura.app.model.GradientType
import com.textura.app.model.LightType
import com.textura.app.model.Look
import com.textura.app.model.PaperType
import com.textura.app.model.Sticker
import com.textura.app.model.StickerType
import com.textura.app.model.TextShape
import com.textura.app.model.c
import com.textura.app.model.ca
import com.textura.app.render.Markup
import com.textura.app.render.Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

// ------------------------------------------------------------------------------------------------
// TEXT
// ------------------------------------------------------------------------------------------------
@Composable
fun TextTab(vm: AppViewModel, look: Look) {
    val ctx = LocalContext.current
    var tfv by remember { mutableStateOf(TextFieldValue(vm.text)) }
    LaunchedEffect(vm.text) {
        if (tfv.text != vm.text) tfv = TextFieldValue(vm.text)
    }
    var colorDialog by remember { mutableStateOf(false) }
    var fontMenu by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf<ToolsRequest?>(null) }

    fun wrapSelection(open: String, close: String) {
        val sel = tfv.selection
        if (sel.collapsed) {
            Toast.makeText(ctx, "Select some words in the text box first", Toast.LENGTH_SHORT).show()
            return
        }
        val t = tfv.text
        val s = sel.min
        val e = sel.max
        val next = t.substring(0, s) + open + t.substring(s, e) + close + t.substring(e)
        tfv = TextFieldValue(next, TextRange(s + open.length, e + open.length))
        vm.updateText(next)
    }

    OutlinedTextField(
        value = tfv,
        onValueChange = {
            tfv = it
            vm.updateText(it.text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        minLines = 2,
        maxLines = 5,
        label = { Text("Your text") },
        shape = RoundedCornerShape(16.dp),
        visualTransformation = TagDimTransformation(MaterialTheme.colorScheme.outline),
    )

    SectionTitle("Format selected words")
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(onClick = { wrapSelection("[b]", "[/b]") }, label = { Text("Bold") })
        AssistChip(onClick = { wrapSelection("[i]", "[/i]") }, label = { Text("Italic") })
        AssistChip(onClick = { wrapSelection("[u]", "[/u]") }, label = { Text("Underline") })
        AssistChip(onClick = { wrapSelection("[big]", "[/big]") }, label = { Text("Big") })
        AssistChip(onClick = { wrapSelection("[huge]", "[/huge]") }, label = { Text("Huge") })
        AssistChip(onClick = { wrapSelection("[small]", "[/small]") }, label = { Text("Small") })
        AssistChip(onClick = { tools = ToolsRequest(vm.text, false, 1) }, label = { Text("Spelling & grammar") })
        AssistChip(onClick = { tools = ToolsRequest(vm.text, false, 0) }, label = { Text("Translate") })
        AssistChip(onClick = { colorDialog = true }, label = { Text("Colour") })
        AssistChip(onClick = { fontMenu = true }, label = { Text("Font") })
        AssistChip(onClick = {
            val cleaned = Markup.strip(tfv.text)
            tfv = TextFieldValue(cleaned)
            vm.updateText(cleaned)
        }, label = { Text("Clear formatting") })
    }
    tools?.let { req -> TextToolsSheet(vm, req, onDismiss = { tools = null }) }
    DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
        Fonts.all.forEach { f ->
            DropdownMenuItem(
                text = { Text(f.label) },
                onClick = {
                    fontMenu = false
                    wrapSelection("[font=${f.id}]", "[/font]")
                },
            )
        }
    }
    if (colorDialog) {
        ColorDialog(look.textColor, false, { colorDialog = false }) { picked ->
            wrapSelection("[c=" + String.format(Locale.US, "#%06X", picked and 0xFFFFFF) + "]", "[/c]")
        }
    }

    SectionTitle("Style")
    SwitchRow("UPPERCASE", look.uppercase) { on -> vm.edit { it.copy(uppercase = on) } }
    SwitchRow("Bold", look.bold) { on -> vm.edit { it.copy(bold = on) } }

    SectionTitle("Colour")
    ColorField("Text colour", look.textColor, withAlpha = true) { col -> vm.edit { it.copy(textColor = col, foil = FoilType.None) } }
    SwitchRow("Gradient fill", look.textColor2 != null) { on ->
        vm.edit { l ->
            l.copy(textColor2 = if (on) (l.textColor2 ?: ColorUtils.blendARGB(l.textColor, android.graphics.Color.BLACK, 0.45f)) else null)
        }
    }
    look.textColor2?.let { second ->
        ColorField("Gradient end colour", second) { col -> vm.edit { it.copy(textColor2 = col) } }
    }
    OutlinedButton(
        onClick = { vm.edit { it.copy(textColor = Renderer.autoTextColor(ctx, it, vm.shape), foil = FoilType.None) } },
        modifier = Modifier.padding(top = 6.dp),
    ) { Text("Auto contrast (readable on this background)") }

    SectionTitle("Foil finish")
    ChipRow(FoilType.entries, look.foil, { it.label }) { f -> vm.edit { it.copy(foil = f) } }
}

// ------------------------------------------------------------------------------------------------
// FONT
// ------------------------------------------------------------------------------------------------
@Composable
fun FontTab(vm: AppViewModel, look: Look, onHandwriting: () -> Unit = {}) {
    var group by remember { mutableStateOf<FontGroup?>(null) }
    @Suppress("UNUSED_VARIABLE") val fontsVersion = vm.fontsVersion
    val groups = FontGroup.entries.filter { g -> Fonts.all.any { it.group == g } }
    ChipRow(listOf<FontGroup?>(null) + groups, group, { it?.label ?: "All" }) { group = it }
    val fonts = Fonts.all.filter { group == null || it.group == group }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        items(fonts, key = { it.id }) { f ->
            FontCard(f, f.id == look.fontId) { vm.edit { it.copy(fontId = f.id) } }
        }
    }
    val ctx = LocalContext.current
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = vm.importFont(uri)
            Toast.makeText(ctx, if (ok) "Font added under My fonts" else "That file is not a usable font", Toast.LENGTH_SHORT).show()
            if (ok) group = FontGroup.Mine
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")) }) {
            Text("Import font (.ttf / .otf)")
        }
        if (Fonts.user.any { it.id == look.fontId }) {
            OutlinedButton(onClick = { vm.deleteFont(look.fontId); vm.edit { it.copy(fontId = "montserrat") } }) { Text("Delete this font") }
        }
    }
    OutlinedButton(onClick = onHandwriting) { Text("Make a font from my handwriting") }
    Spacer(Modifier.height(8.dp))
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
    val family = remember(font.id, font.filePath) { Fonts.composeFamily(ctx, font) }
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
    SectionTitle("Canvas size")
    ShapePicker(vm.shape) { vm.updateShape(it) }

    SectionTitle("Text shape (the text fills it)")
    ChipRow(TextShape.entries, look.textShape, { it.label }) { t -> vm.edit { it.copy(textShape = t) } }
    SwitchRow(
        if (look.textShape == TextShape.Rect) "Colour panel behind the text" else "Fill the shape with colour",
        look.shapeColor != null,
    ) { on ->
        vm.edit { l -> l.copy(shapeColor = if (on) (l.shapeColor ?: c(0xFFFFFF)) else null) }
    }
    look.shapeColor?.let { col ->
        ColorField("Shape colour", col, withAlpha = true) { picked -> vm.edit { it.copy(shapeColor = picked) } }
    }

    SectionTitle("Alignment")
    ChipRow(Align.entries, look.align, { it.name }) { a -> vm.edit { it.copy(align = a) } }
    Spacer(Modifier.height(4.dp))
    LabeledSlider("Margins", look.margin, 0f..0.2f, { v -> vm.edit { it.copy(margin = v) } })
    LabeledSlider("Line spacing", look.lineSpacing, 0.7f..2f, { v -> vm.edit { it.copy(lineSpacing = v) } })
    LabeledSlider("Letter spacing", look.letterSpacing, -0.05f..0.3f, { v -> vm.edit { it.copy(letterSpacing = v) } })
    SwitchRow("Spread lines to fill the height", look.fillHeight) { on -> vm.edit { it.copy(fillHeight = on) } }

    SectionTitle("Size and position")
    LabeledSlider(
        "Text size (1.00 = fill the shape)", look.textScale, 0.3f..1f,
        { v -> vm.edit { it.copy(textScale = v) } },
    )
    LabeledSlider("Move left / right", look.offsetX, -0.3f..0.3f, { v -> vm.edit { it.copy(offsetX = v) } })
    LabeledSlider("Move up / down", look.offsetY, -0.3f..0.3f, { v -> vm.edit { it.copy(offsetY = v) } })
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
        return
    }
    SectionTitle("Sheet")
    ColorField("Paper colour", look.paperColor, withAlpha = look.paper == PaperType.Glass) { col ->
        vm.edit { it.copy(paperColor = col) }
    }
    LabeledSlider("Texture", look.grain, 0f..1.2f, { v -> vm.edit { it.copy(grain = v) } })
    LabeledSlider("Float on desk", look.paperInset, 0f..0.15f, { v -> vm.edit { it.copy(paperInset = v) } })
    LabeledSlider(
        "Tilt", look.tilt, -8f..8f, { v -> vm.edit { it.copy(tilt = v) } },
        String.format(Locale.US, "%.1f°", look.tilt),
    )

    SectionTitle("Wear and damage")
    SwitchRow("Coffee stains", look.stains) { on -> vm.edit { it.copy(stains = on) } }
    SwitchRow("Folds", look.folds) { on -> vm.edit { it.copy(folds = on) } }
    SwitchRow("Burnt edges", look.burnt) { on -> vm.edit { it.copy(burnt = on) } }
    SwitchRow("Torn edges", look.torn) { on -> vm.edit { it.copy(torn = on) } }
    SwitchRow("Ink blots", look.blots) { on -> vm.edit { it.copy(blots = on) } }
    LabeledSlider("Amount of wear", look.wear, 0f..1.5f, { v -> vm.edit { it.copy(wear = v) } })
}

// ------------------------------------------------------------------------------------------------
// SCENE (perspective and light)
// ------------------------------------------------------------------------------------------------
@Composable
fun SceneTab(vm: AppViewModel, look: Look) {
    SectionTitle("Perspective")
    LabeledSlider(
        "Lean back / forward", look.tiltX, -35f..35f, { v -> vm.edit { it.copy(tiltX = v) } },
        String.format(Locale.US, "%.0f°", look.tiltX),
    )
    LabeledSlider(
        "Turn left / right", look.tiltY, -35f..35f, { v -> vm.edit { it.copy(tiltY = v) } },
        String.format(Locale.US, "%.0f°", look.tiltY),
    )
    SectionTitle("Light")
    ChipRow(LightType.entries, look.light, { it.label }) { t -> vm.edit { it.copy(light = t) } }
    if (look.light != LightType.None) {
        LabeledSlider("Strength", look.lightStrength, 0f..1f, { v -> vm.edit { it.copy(lightStrength = v) } })
    }
}

// ------------------------------------------------------------------------------------------------
// STICKERS
// ------------------------------------------------------------------------------------------------
private fun defaultStickerSize(t: StickerType): Float = when (t) {
    StickerType.QuoteOpen, StickerType.QuoteClose -> 0.14f
    StickerType.Frame, StickerType.Corners -> 0.9f
    StickerType.Line, StickerType.DoubleLine, StickerType.Squiggle, StickerType.Arrow -> 0.4f
    StickerType.Tape -> 0.3f
    else -> 0.18f
}

@Composable
fun StickersTab(vm: AppViewModel, look: Look, selected: String?, onSelect: (String?) -> Unit) {
    fun update(id: String, f: (Sticker) -> Sticker) = vm.edit { l ->
        l.copy(stickers = l.stickers.map { if (it.id == id) f(it) else it })
    }

    SectionTitle("Add a sticker")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StickerType.entries.toList()) { type ->
            AssistChip(
                onClick = {
                    val id = UUID.randomUUID().toString()
                    val first = look.stickers.size
                    vm.edit { l ->
                        l.copy(
                            stickers = l.stickers + Sticker(
                                id = id, type = type,
                                x = 0.5f, y = if (first == 0) 0.5f else 0.35f + 0.1f * (first % 4),
                                size = defaultStickerSize(type), color = l.textColor or -0x1000000,
                            ),
                        )
                    }
                    onSelect(id)
                },
                label = { Text(type.symbol + "  " + type.label) },
            )
        }
    }

    if (look.stickers.isEmpty()) {
        Text(
            "Tap a sticker to place it on the page, then drag it on the preview.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        return
    }

    SectionTitle("On the page")
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        look.stickers.forEach { s ->
            FilterChip(selected = s.id == selected, onClick = { onSelect(s.id) }, label = { Text(s.type.symbol + " " + s.type.label) })
        }
    }

    val sel = look.stickers.firstOrNull { it.id == selected } ?: return
    Spacer(Modifier.height(6.dp))
    LabeledSlider("Size", sel.size, 0.03f..1f, { v -> update(sel.id) { it.copy(size = v) } })
    LabeledSlider("Rotation", sel.rot, -180f..180f, { v -> update(sel.id) { it.copy(rot = v) } }, "${sel.rot.toInt()}°")
    LabeledSlider("Opacity", sel.alpha, 0.1f..1f, { v -> update(sel.id) { it.copy(alpha = v) } })
    LabeledSlider("Position left / right", sel.x, 0f..1f, { v -> update(sel.id) { it.copy(x = v) } })
    LabeledSlider("Position up / down", sel.y, 0f..1f, { v -> update(sel.id) { it.copy(y = v) } })
    ColorField("Colour", sel.color) { col -> update(sel.id) { it.copy(color = col) } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        OutlinedButton(onClick = {
            val copy = sel.copy(id = UUID.randomUUID().toString(), x = (sel.x + 0.06f).coerceAtMost(1f), y = (sel.y + 0.06f).coerceAtMost(1f))
            vm.edit { l -> l.copy(stickers = l.stickers + copy) }
            onSelect(copy.id)
        }) {
            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Duplicate")
        }
        OutlinedButton(onClick = {
            vm.edit { l -> l.copy(stickers = l.stickers.filter { it.id != sel.id }) }
            onSelect(null)
        }) {
            Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Delete")
        }
    }
}
