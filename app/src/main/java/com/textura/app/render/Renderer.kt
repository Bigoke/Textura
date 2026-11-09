package com.textura.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.textura.app.model.CanvasShape
import com.textura.app.model.Depth
import com.textura.app.model.FoilType
import com.textura.app.model.Fonts
import com.textura.app.model.Look
import com.textura.app.model.PaperType
import com.textura.app.model.TextShape
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Optional signature added at export time. [position]: 0 left, 1 centre, 2 right. */
enum class RevealMode { Typewriter, Handwriting, Words, Lines, Sweep, LineWipe, Pop, Scatter, Highlight, Fade }

/** Which parts of the picture a draw call paints; lets video frames reuse the unchanging background. */
enum class Stage { All, Below, Text, Above }

class LayoutHolder {
    var result: TextEngine.Result? = null
    var content: RectF? = null
}

class TipHolder {
    var valid = false
    var x = 0f
    var y = 0f
}

data class Watermark(val text: String, val position: Int = 1)

object Renderer {
    /** Every layout is computed in a 1000-unit wide design space, then scaled to the output size. */
    const val DESIGN_WIDTH = 1000f

    fun designSize(shape: CanvasShape): Pair<Float, Float> =
        DESIGN_WIDTH to DESIGN_WIDTH * shape.hr / shape.wr

    /** Renders the look at [longEdgePx] pixels on the longest side. */
    fun render(
        context: Context,
        look: Look,
        text: String,
        shape: CanvasShape,
        longEdgePx: Int,
        transparent: Boolean = false,
        reveal: Float = 1f,
        watermark: Watermark? = null,
        exactSize: Pair<Int, Int>? = null,
        revealMode: RevealMode = RevealMode.Typewriter,
        pen: Boolean = false,
    ): Bitmap {
        val (dw, dh) = designSize(shape)
        val scale = longEdgePx / max(dw, dh)
        val w = exactSize?.first ?: (dw * scale).roundToInt().coerceAtLeast(2)
        val h = exactSize?.second ?: (dh * scale).roundToInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(w / dw, h / dh)
        draw(canvas, context, look, text, shape, scale, transparent, reveal, watermark, revealMode, pen)
        return bmp
    }

    fun draw(
        canvas: Canvas,
        context: Context,
        look: Look,
        text: String,
        shape: CanvasShape,
        scale: Float,
        transparent: Boolean,
        reveal: Float = 1f,
        watermark: Watermark? = null,
        revealMode: RevealMode = RevealMode.Typewriter,
        pen: Boolean = false,
        stage: Stage = Stage.All,
        holder: LayoutHolder? = null,
    ) {
        val doBelow = stage == Stage.All || stage == Stage.Below
        val doText = stage == Stage.All || stage == Stage.Text
        val doAbove = stage == Stage.All || stage == Stage.Above
        val (dw, dh) = designSize(shape)
        val appContext = context.applicationContext
        val tfOf: (String) -> Typeface = { id -> Fonts.typeface(appContext, id) }
        val hasSheet = look.paper != PaperType.None && !transparent

        if (!transparent && doBelow) BackgroundPainter.draw(canvas, look.bg, dw, dh, scale)

        // ---- perspective for the whole composition
        canvas.save()
        if (look.tiltX != 0f || look.tiltY != 0f) {
            val cam = Camera()
            cam.setLocation(0f, 0f, -30f)
            cam.rotateX(look.tiltX)
            cam.rotateY(look.tiltY)
            val m = Matrix()
            cam.getMatrix(m)
            m.preTranslate(-dw / 2f, -dh / 2f)
            m.postTranslate(dw / 2f, dh / 2f)
            val fit = 1f / (1f + 0.011f * (abs(look.tiltX) + abs(look.tiltY)))
            canvas.concat(m)
            canvas.scale(fit, fit, dw / 2f, dh / 2f)
        }

        // ---- the sheet (tilted paper)
        canvas.save()
        var sheet = RectF(0f, 0f, dw, dh)
        var corner = 0f
        if (hasSheet) {
            val inset = look.paperInset * dw
            sheet = RectF(inset, inset, dw - inset, dh - inset)
            if (look.tilt != 0f) {
                val f = PaperPainter.fitFactor(dw, dh, look.tilt)
                canvas.translate(dw / 2f, dh / 2f)
                canvas.rotate(look.tilt)
                canvas.scale(f, f)
                canvas.translate(-dw / 2f, -dh / 2f)
            }
            val floating = inset > 0f || look.tilt != 0f
            corner = if (look.paper == PaperType.Glass) 40f else if (floating) 6f else 0f
            if (floating && doBelow) {
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (look.paper == PaperType.Glass) Color.argb(60, 0, 0, 0) else look.paperColor or -0x1000000
                    setShadowLayer(dw * 0.022f, dw * 0.004f, dw * 0.012f, Color.argb(120, 0, 0, 0))
                }
                canvas.drawPath(PaperPainter.sheetPath(look, sheet, corner), shadow)
            }
        }

        val paperInfo = if (hasSheet) look.paper else PaperType.None
        val m = look.margin * min(sheet.width(), sheet.height()) * shape.marginScale
        val pad = paperInfo.pad * sheet.width()
        val content = RectF(
            sheet.left + m + pad + paperInfo.leftPad * sheet.width(),
            sheet.top + m + pad,
            sheet.right - m - pad,
            sheet.bottom - m - pad,
        )

        val mask = if (look.textShape != TextShape.Rect) ShapeMask.get(look.textShape, content) else null
        val result = holder?.result ?: TextEngine.layout(text.ifBlank { "Your text\nappears here" }, look, tfOf, content, shape, mask)
            .also { holder?.result = it; holder?.content = content }

        if (hasSheet && doBelow) {
            if (look.paper == PaperType.Glass) drawGlass(canvas, look, sheet, corner, dw, dh, scale)
            else PaperPainter.draw(canvas, look, sheet, result.baselines, result.pitch, scale)
            PaperPainter.drawEffects(canvas, look, sheet, scale)
        }

        if (!transparent && look.shapeColor != null && doBelow) {
            val fillPath = if (mask != null) ShapePaths.path(look.textShape, content) else {
                val pad2 = dw * 0.03f
                Path().apply {
                    addRoundRect(RectF(content.left - pad2, content.top - pad2, content.right + pad2, content.bottom + pad2), 28f, 28f, Path.Direction.CW)
                }
            }
            val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.shapeColor }
            canvas.drawPath(fillPath, fp)
        }

        if (doText) {
            canvas.save()
            canvas.translate(look.offsetX * dw, look.offsetY * dh)
            drawText(canvas, look, tfOf, result, content, reveal, revealMode, pen, com.textura.app.render.Markup.strip(text))
            canvas.restore()
        }

        canvas.restore() // sheet

        if (doAbove) StickerPainter.draw(canvas, look.stickers, dw, dh, tfOf)
        canvas.restore() // perspective

        if (!transparent && doAbove) LightPainter.draw(canvas, look.light, look.lightStrength, dw, dh)
        if (watermark != null && doAbove) WatermarkPainter.draw(canvas, watermark.text, watermark.position, dw, dh)
    }

    // ---------------------------------------------------------------------------------------------

    private fun drawGlass(canvas: Canvas, look: Look, sheet: RectF, corner: Float, dw: Float, dh: Float, scale: Float) {
        val path = PaperPainter.sheetPath(look.copy(torn = false), sheet, corner)
        canvas.save()
        canvas.clipPath(path)
        // frosted backdrop: the background again, blurred, then a milky veil
        BackgroundPainter.draw(canvas, look.bg.copy(blur = max(look.bg.blur, 0.75f)), dw, dh, scale)
        val veilAlpha = Color.alpha(look.paperColor)
        canvas.drawColor(ColorUtils.setAlphaComponent(look.paperColor, veilAlpha.coerceIn(0, 255)))
        val sheen = Paint().apply {
            shader = LinearGradient(
                sheet.left, sheet.top, sheet.right, sheet.bottom,
                Color.argb(70, 255, 255, 255), Color.argb(8, 255, 255, 255), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(sheet, sheen)
        canvas.restore()
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb(140, 255, 255, 255)
        }
        canvas.drawPath(path, border)
    }

    private fun foilShader(type: FoilType, r: RectF): Shader? {
        val colors: IntArray = when (type) {
            FoilType.None -> return null
            FoilType.Gold -> intArrayOf(0x7A5311, 0xF6E27A, 0xD9A521, 0xFFF3B0, 0xB8821D, 0xF3D36A, 0x8A5D12)
            FoilType.Silver -> intArrayOf(0x6B7280, 0xF4F6F8, 0xAAB1BB, 0xFFFFFF, 0x8B93A0, 0xE7EAEE, 0x6B7280)
            FoilType.RoseGold -> intArrayOf(0x8C4F4A, 0xF7CDBF, 0xD99A8C, 0xFFE6DF, 0xB57166, 0xF2BFAE, 0x8C4F4A)
            FoilType.Copper -> intArrayOf(0x5A2A10, 0xE89A62, 0xB5622E, 0xFFCFA2, 0x8E4A1F, 0xE08C52, 0x5A2A10)
            FoilType.Holo -> intArrayOf(0xFF6EC7, 0x7873F5, 0x4ADEDE, 0x7BFF9A, 0xFFF36E, 0xFF9A6E, 0xFF6EC7)
        }.map { it or -0x1000000 }.toIntArray()
        return LinearGradient(r.left, r.top, r.right, r.bottom, colors, null, Shader.TileMode.CLAMP)
    }

    private fun drawText(
        canvas: Canvas,
        look: Look,
        tfOf: (String) -> Typeface,
        r: TextEngine.Result,
        content: RectF,
        reveal: Float,
        mode: RevealMode,
        pen: Boolean,
        plain: String,
    ) {
        val size = r.size
        val revealed = if (reveal >= 0.999f) null else applyReveal(r.placed, reveal, mode, size, content, plain)
        val placed = revealed?.placed ?: r.placed
        val tipH = TipHolder()

        fun base(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tfOf(look.fontId)
            textSize = size
            letterSpacing = look.letterSpacing
            isFakeBoldText = look.bold
        }

        fun face(): TextPaint = base().apply {
            style = Paint.Style.FILL
            val second = look.textColor2
            val foil = foilShader(look.foil, content)
            when {
                foil != null -> { shader = foil; color = Color.WHITE }
                second != null -> shader = LinearGradient(0f, content.top, 0f, content.bottom, look.textColor, second, Shader.TileMode.CLAMP)
                else -> color = look.textColor
            }
        }

        when (look.depth) {
            Depth.Flat -> {
                if (look.shadow) {
                    val p = base().apply {
                        color = look.shadowColor
                        val blur = look.shadowBlur * size
                        if (blur > 0.2f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                    }
                    pass(canvas, placed, size, look, tfOf, p, look.shadowOffset * size, look.shadowOffset * size)
                }
                if (look.outline) {
                    val p = base().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = max(0.8f, look.outlineWidth * size)
                        strokeJoin = Paint.Join.ROUND
                        color = look.outlineColor
                    }
                    pass(canvas, placed, size, look, tfOf, p, 0f, 0f)
                }
                val f = face()
                if (look.imperfection > 0f && !look.handwritten && !look.outline && !look.shadow) {
                    // typed ink bleed
                    f.setShadowLayer(max(0.6f, size * 0.012f), 0f, 0f, ColorUtils.setAlphaComponent(look.textColor, 110))
                }
                pass(canvas, placed, size, look, tfOf, f, 0f, 0f, wordColors = true, underline = true, tip = tipH)
            }

            Depth.Emboss, Depth.Engrave -> {
                val d = max(0.8f, size * 0.025f)
                val sign = if (look.depth == Depth.Emboss) 1f else -1f
                val light = base().apply {
                    color = Color.argb(190, 255, 255, 255)
                    maskFilter = BlurMaskFilter(d * 0.7f, BlurMaskFilter.Blur.NORMAL)
                }
                val dark = base().apply {
                    color = Color.argb(130, 0, 0, 0)
                    maskFilter = BlurMaskFilter(d * 0.7f, BlurMaskFilter.Blur.NORMAL)
                }
                pass(canvas, placed, size, look, tfOf, light, -d * sign, -d * sign)
                pass(canvas, placed, size, look, tfOf, dark, d * sign, d * sign)
                pass(canvas, placed, size, look, tfOf, face(), 0f, 0f, wordColors = true, underline = true, tip = tipH)
            }

            Depth.Extrude -> {
                val len = TextEngine.depthLength(look, size)
                val steps = ceil(len / 0.8f).toInt().coerceIn(6, 40)
                val ddx = r.ddx
                val ddy = r.ddy

                val cast = base().apply {
                    color = Color.argb(95, 0, 0, 0)
                    maskFilter = BlurMaskFilter(max(1f, len * 0.35f), BlurMaskFilter.Blur.NORMAL)
                }
                pass(canvas, placed, size, look, tfOf, cast, ddx * 1.18f, ddy * 1.18f)

                val side = base().apply {
                    style = Paint.Style.FILL_AND_STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = max(1f, len / steps * 1.6f)
                }
                for (i in steps downTo 1) {
                    val t = i / steps.toFloat()
                    side.color = if (look.gradientSide) ColorUtils.blendARGB(look.sideColor, Color.BLACK, 0.5f * t) else look.sideColor
                    pass(canvas, placed, size, look, tfOf, side, ddx * t, ddy * t)
                }

                pass(canvas, placed, size, look, tfOf, face(), 0f, 0f, wordColors = true, underline = true, tip = tipH)

                if (look.highlight) {
                    val edge = base().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = max(0.8f, size * 0.014f)
                        strokeJoin = Paint.Join.ROUND
                        color = Color.argb(85, 255, 255, 255)
                    }
                    pass(canvas, placed, size, look, tfOf, edge, 0f, 0f)
                }
            }
        }

        if (pen && revealed != null) {
            if (mode == RevealMode.Handwriting) {
                if (tipH.valid) drawPenTip(canvas, look, mode, Revealed(placed, true, tipH.x, tipH.y, 0f), size, content)
            } else if (revealed.hasTip) {
                drawPenTip(canvas, look, mode, revealed, size, content)
            }
        }
    }



    private class Revealed(
        val placed: List<TextEngine.Placed>,
        val hasTip: Boolean = false,
        val tipX: Float = 0f,
        val tipY: Float = 0f,
        val tipRot: Float = 0f,
    )

    private fun applyReveal(
        placed: List<TextEngine.Placed>,
        fraction: Float,
        mode: RevealMode,
        size: Float,
        content: RectF,
        plain: String,
    ): Revealed {
        val f = fraction.coerceIn(0f, 1f)
        val n = placed.size
        // 0..1 progress of the item at [rank] when [n] items appear one after another, [len] of them at once
        fun stag(rank: Int, count: Int, len: Float): Float = ((f * (count + len - 1f)) - rank) / len

        // line index of every word (words on the same baseline share a line)
        fun lineIndex(): IntArray {
            val order = placed.indices.sortedBy { placed[it].y }
            val lineOf = IntArray(n)
            var line = 0
            for ((k, i) in order.withIndex()) {
                if (k > 0 && placed[i].y - placed[order[k - 1]].y > size * 0.45f) line++
                lineOf[i] = line
            }
            return lineOf
        }

        when (mode) {
            RevealMode.Typewriter -> {
                val schedule = Timing.typeSchedule(plain)
                val totalPlaced = placed.sumOf { it.s.length }
                var remaining = if (schedule.isEmpty()) (totalPlaced * f).roundToInt()
                else (Timing.typedCount(schedule, f).toFloat() / schedule.size * totalPlaced).roundToInt()
                val out = ArrayList<TextEngine.Placed>()
                var tip: Revealed? = null
                for (p in placed) {
                    if (remaining <= 0) break
                    var cut = min(remaining, p.s.length)
                    if (cut > 0 && cut < p.s.length && Character.isHighSurrogate(p.s[cut - 1])) cut--
                    if (cut > 0) {
                        out.add(p.copy(s = p.s.substring(0, cut)))
                        tip = Revealed(out, true, p.x + p.w * cut / max(1, p.s.length), p.y, p.rot)
                    }
                    remaining -= p.s.length
                }
                return tip ?: Revealed(out)
            }
            RevealMode.Handwriting -> {
                val weights = FloatArray(n)
                val lead = FloatArray(n)
                for (i in 0 until n) {
                    if (i > 0 && abs(placed[i].y - placed[i - 1].y) > size * 0.5f) lead[i] = Timing.LINE_TRAVEL
                    weights[i] = Timing.wordWeight(placed[i].s) + lead[i]
                }
                val target = weights.sum() * f
                var acc = 0f
                val out = ArrayList<TextEngine.Placed>()
                for (i in 0 until n) {
                    val w = weights[i]
                    if (acc + w <= target) {
                        out.add(placed[i])
                    } else {
                        if (acc < target) {
                            val hw = ((target - acc - lead[i]) / (w - lead[i])).coerceIn(0.0001f, 0.9999f)
                            out.add(placed[i].copy(hw = hw))
                        }
                        break
                    }
                    acc += w
                }
                return Revealed(out)
            }
            RevealMode.Words, RevealMode.Pop, RevealMode.Scatter, RevealMode.Highlight -> {
                val ranks = if (mode == RevealMode.Scatter) (0 until n).shuffled(kotlin.random.Random(5)) else (0 until n).toList()
                val len = when (mode) { RevealMode.Words -> 3f; RevealMode.Pop -> 2.5f; RevealMode.Scatter -> 3.5f; else -> 1.6f }
                val out = ArrayList<TextEngine.Placed>(n)
                for (i in 0 until n) {
                    val local = stag(ranks[i], n, len).coerceIn(0f, 1f)
                    val p = placed[i]
                    when (mode) {
                        RevealMode.Words -> if (local > 0f) {
                            val e = Timing.easeOutCubic(local)
                            out.add(p.copy(alpha = p.alpha * e, rise = (1f - e) * size * 0.4f))
                        }
                        RevealMode.Pop -> if (local > 0f) {
                            out.add(p.copy(alpha = p.alpha * min(1f, local * 4f), pop = max(0.02f, Timing.easeOutBack(local))))
                        }
                        RevealMode.Scatter -> if (local > 0f) {
                            val e = Timing.easeOutCubic(local)
                            out.add(p.copy(alpha = p.alpha * e, pop = 0.85f + 0.15f * e))
                        }
                        else -> { // Highlight: all words are visible but dim, then light up as they are read
                            val e = Timing.easeOutCubic(local)
                            out.add(p.copy(alpha = p.alpha * (0.22f + 0.78f * e)))
                        }
                    }
                }
                return Revealed(out)
            }
            RevealMode.Fade -> {
                val e = f * f * (3f - 2f * f)
                return Revealed(placed.filter { e > 0.01f }.map { it.copy(alpha = it.alpha * e) })
            }
            RevealMode.Lines -> {
                val lineOf = lineIndex()
                val lines = (lineOf.maxOrNull() ?: 0) + 1
                val out = ArrayList<TextEngine.Placed>()
                for (i in 0 until n) {
                    val local = stag(lineOf[i], lines, 1.6f).coerceIn(0f, 1f)
                    if (local <= 0f) continue
                    val e = Timing.easeOutCubic(local)
                    out.add(placed[i].copy(alpha = placed[i].alpha * e, rise = (1f - e) * size * 0.5f))
                }
                return Revealed(out)
            }
            RevealMode.LineWipe -> {
                val lineOf = lineIndex()
                val lines = (lineOf.maxOrNull() ?: 0) + 1
                val pos = f * lines
                val k = min(pos.toInt(), lines - 1)
                val part = (pos - k).coerceIn(0f, 1f)
                var left = Float.MAX_VALUE
                var right = -Float.MAX_VALUE
                var lineY = 0f
                for (i in 0 until n) if (lineOf[i] == k) {
                    left = min(left, placed[i].x); right = max(right, placed[i].x + placed[i].w); lineY = placed[i].y
                }
                val xLimit = left + part * (right - left)
                val out = ArrayList<TextEngine.Placed>()
                for (i in 0 until n) {
                    val p = placed[i]
                    if (lineOf[i] < k || (f >= 1f)) out.add(p)
                    else if (lineOf[i] == k) {
                        val c = ((xLimit - p.x) / max(p.w, 1f)).coerceIn(0f, 1f)
                        if (c > 0f) out.add(if (c >= 1f) p else p.copy(clip = c))
                    }
                }
                return if (left < right) Revealed(out, true, xLimit, lineY, 0f) else Revealed(out)
            }
            RevealMode.Sweep -> {
                val clipX = content.left + f * content.width()
                val out = ArrayList<TextEngine.Placed>()
                for (p in placed) {
                    val c = ((clipX - p.x) / max(p.w, 1f)).coerceIn(0f, 1f)
                    if (c > 0f) out.add(if (c >= 1f) p else p.copy(clip = c))
                }
                return Revealed(out)
            }
        }
    }

    /**
     * Draws a word that is being handwritten: finished letters normally, and the current letter revealed along its
     * own centre line so the ink follows every stroke the way a pen would.
     */
    private fun drawWriting(canvas: Canvas, p: TextEngine.Placed, px: Float, py: Float, paint: TextPaint, fontKey: String, tip: TipHolder?) {
        val s = p.s
        val n = s.length
        val sc = paint.textSize / 100f
        val starts = ArrayList<Int>()
        var i = 0
        while (i < n) { starts.add(i); i += Character.charCount(s.codePointAt(i)) }
        val ends = IntArray(starts.size) { if (it + 1 < starts.size) starts[it + 1] else n }
        val skels = starts.indices.map { k ->
            val ch = s.substring(starts[k], ends[k])
            if (ch.isBlank()) null else GlyphStrokes.get(paint.typeface, fontKey, ch)
        }
        val gap = 14f
        val weights = FloatArray(starts.size) { gap + (skels[it]?.total ?: 6f) }
        val target = p.hw * weights.sum()

        if (p.rot != 0f) { canvas.save(); canvas.rotate(p.rot, px, py) }
        var acc = 0f
        for (k in starts.indices) {
            val x = if (starts[k] == 0) 0f else paint.getRunAdvance(s, 0, n, 0, n, false, starts[k])
            val w = weights[k]
            if (acc + w <= target) {
                canvas.drawText(s, starts[k], ends[k], px + x, py, paint)
                acc += w
                continue
            }
            val local = target - acc
            val sk = skels[k]
            var tx = px + x
            var ty = py
            if (sk != null) {
                val dist = max(0f, local - gap)
                if (dist > 0f) {
                    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = sk.stem * 1.5f
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    val outline = Path()
                    stroke.getFillPath(sk.pathUpTo(dist), outline)
                    val m = Matrix()
                    m.setScale(sc, sc)
                    m.postTranslate(px + x, py)
                    outline.transform(m)
                    canvas.save()
                    canvas.clipPath(outline)
                    canvas.drawText(s, starts[k], ends[k], px + x, py, paint)
                    canvas.restore()
                }
                val pt = sk.pointAt(dist)
                tx = px + x + sc * pt.first
                ty = py + sc * pt.second
            }
            if (tip != null) {
                tip.valid = true
                if (p.rot != 0f) {
                    val rad = Math.toRadians(p.rot.toDouble())
                    val dx = tx - px
                    val dyy = ty - py
                    tip.x = px + (dx * Math.cos(rad) - dyy * Math.sin(rad)).toFloat()
                    tip.y = py + (dx * Math.sin(rad) + dyy * Math.cos(rad)).toFloat()
                } else { tip.x = tx; tip.y = ty }
            }
            break
        }
        if (p.rot != 0f) canvas.restore()
    }

    private fun drawPenTip(canvas: Canvas, look: Look, mode: RevealMode, r: Revealed, size: Float, content: RectF) {
        val ps = size.coerceIn(16f, max(16f, content.height() * 0.09f))
        canvas.save()
        canvas.translate(r.tipX, r.tipY)
        if (r.tipRot != 0f) canvas.rotate(r.tipRot)
        if (mode == RevealMode.Typewriter) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.textColor; alpha = 220 }
            canvas.drawRect(ps * 0.02f, -size * 0.78f, ps * 0.02f + max(1.5f, size * 0.055f), size * 0.12f, p)
        } else {
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF2B2B33.toInt(); strokeWidth = ps * 0.17f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
                setShadowLayer(ps * 0.12f, ps * 0.05f, ps * 0.08f, Color.argb(90, 0, 0, 0))
            }
            canvas.drawLine(ps * 0.13f, -ps * 0.36f, ps * 0.95f, -ps * 1.85f, body)
            val nib = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9A227.toInt(); style = Paint.Style.FILL }
            canvas.drawPath(Path().apply {
                moveTo(0f, 0f); lineTo(ps * 0.02f, -ps * 0.42f); lineTo(ps * 0.24f, -ps * 0.32f); close()
            }, nib)
            val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(130, 255, 255, 255); strokeWidth = ps * 0.04f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
            }
            canvas.drawLine(ps * 0.2f, -ps * 0.55f, ps * 0.85f, -ps * 1.7f, shine)
        }
        canvas.restore()
    }

    /** Keeps only the first [fraction] of all characters, used by the typewriter animation. */
    private fun truncate(placed: List<TextEngine.Placed>, fraction: Float): List<TextEngine.Placed> {
        val total = placed.sumOf { it.s.length }
        var remaining = (total * fraction.coerceIn(0f, 1f)).roundToInt()
        val out = ArrayList<TextEngine.Placed>()
        for (p in placed) {
            if (remaining <= 0) break
            var cut = min(remaining, p.s.length)
            if (cut > 0 && cut < p.s.length && Character.isHighSurrogate(p.s[cut - 1])) cut--
            if (cut > 0) out.add(p.copy(s = p.s.substring(0, cut)))
            remaining -= p.s.length
        }
        return out
    }

    /**
     * Draws every placed word with the given paint, shifted by (dx, dy), keeping each word's own jitter and
     * its own formatting (font, size, bold, italic, colour, underline).
     */
    private fun pass(
        canvas: Canvas,
        placed: List<TextEngine.Placed>,
        size: Float,
        look: Look,
        tfOf: (String) -> Typeface,
        paint: TextPaint,
        dx: Float,
        dy: Float,
        wordColors: Boolean = false,
        underline: Boolean = false,
        tip: TipHolder? = null,
    ) {
        val shader0 = paint.shader
        val color0 = paint.color
        val under = Paint(Paint.ANTI_ALIAS_FLAG)
        for (p in placed) {
            val px = p.x + dx
            val py = p.y + dy + p.rise
            paint.typeface = tfOf(p.st.fontId ?: look.fontId)
            paint.textSize = size * p.st.scale
            paint.isFakeBoldText = look.bold || p.st.bold
            paint.textSkewX = if (p.st.italic) -0.22f else 0f
            val wc = p.st.color
            if (wordColors && wc != null) {
                paint.shader = null
                paint.color = wc
            } else {
                paint.shader = shader0
                paint.color = color0
            }
            paint.alpha = (Color.alpha(paint.color) * p.alpha).roundToInt().coerceIn(0, 255)
            if (p.hw < 1f && p.s.isNotEmpty()) {
                drawWriting(canvas, p, px, py, paint, p.st.fontId ?: look.fontId, tip)
                continue
            }
            if (p.rot != 0f || p.clip < 1f || p.pop != 1f) {
                canvas.save()
                if (p.rot != 0f) canvas.rotate(p.rot, px, py)
                if (p.pop != 1f) canvas.scale(p.pop, p.pop, px + p.w / 2f, py - size * 0.3f)
                if (p.clip < 1f) {
                    val sc = size * p.st.scale
                    canvas.clipRect(px - sc * 0.4f, py - sc * 1.3f, px + p.w * p.clip, py + sc * 0.6f)
                }
                canvas.drawText(p.s, px, py, paint)
                canvas.restore()
            } else {
                canvas.drawText(p.s, px, py, paint)
            }
            if (underline && p.st.underline) {
                under.shader = paint.shader
                under.color = paint.color
                under.alpha = paint.alpha
                val th = max(1f, size * p.st.scale * 0.05f)
                val uy = py + size * p.st.scale * 0.13f
                canvas.drawRect(px, uy, px + p.w * p.clip, uy + th, under)
            }
        }
        paint.shader = shader0
        paint.color = color0
    }

    // ---------------------------------------------------------------------------------------------

    /** White or near-black, whichever reads best on the look's background (and paper, if any). */
    fun autoTextColor(context: Context, look: Look, shape: CanvasShape): Int {
        val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(48f / 1000f, 48f / 1000f)
        BackgroundPainter.draw(canvas, look.bg, 1000f, 1000f, 0.05f)
        if (look.paper != PaperType.None && look.paper != PaperType.Glass) {
            val inset = look.paperInset * 1000f
            val p = Paint().apply { color = look.paperColor or -0x1000000 }
            canvas.drawRect(inset, inset, 1000f - inset, 1000f - inset, p)
        }
        var sum = 0.0
        var n = 0
        for (y in 8 until 40) for (x in 8 until 40) {
            val px = bmp.getPixel(x, y)
            sum += ColorUtils.calculateLuminance(px)
            n++
        }
        bmp.recycle()
        return if (sum / max(1, n) > 0.45) 0xFF16161C.toInt() else Color.WHITE
    }

    /**
     * A prepared picture for animation: the background, paper and layout are made once, so each frame only has to
     * draw the text being revealed.
     */
    class Scene(
        private val context: Context,
        private val look: Look,
        private val text: String,
        private val shape: CanvasShape,
        val w: Int,
        val h: Int,
        private val watermark: Watermark?,
    ) {
        private val holder = LayoutHolder()
        private val dw: Float
        private val dh: Float
        private val scale: Float
        private val below: Bitmap

        init {
            val d = designSize(shape)
            dw = d.first; dh = d.second
            scale = w / dw
            below = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(below)
            c.scale(w / dw, h / dh)
            draw(c, context, look, text, shape, scale, false, 1f, watermark, RevealMode.Typewriter, false, Stage.Below, holder)
        }

        /** Draws the picture with [reveal] of the text shown. Pass [into] to reuse a bitmap of the same size. */
        fun frame(reveal: Float, mode: RevealMode, pen: Boolean, into: Bitmap? = null): Bitmap {
            val bmp = into ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val over = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC) }
            c.drawBitmap(below, 0f, 0f, over)
            c.save()
            c.scale(w / dw, h / dh)
            draw(c, context, look, text, shape, scale, false, reveal, watermark, mode, pen && reveal < 1f, Stage.Text, holder)
            draw(c, context, look, text, shape, scale, false, reveal, watermark, mode, false, Stage.Above, holder)
            c.restore()
            return bmp
        }

        fun release() { below.recycle() }
    }
}
