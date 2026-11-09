package com.textura.app.render

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.textura.app.model.Align
import com.textura.app.model.CanvasShape
import com.textura.app.model.Depth
import com.textura.app.model.Look
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Formatting that can change from word to word. */
data class WordStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val color: Int? = null,
    val scale: Float = 1f,
    val fontId: String? = null,
)

/**
 * Inline formatting tags typed into the text:
 * [b] [i] [u] [c=#RRGGBB] [big] [huge] [small] [size=1.5] [font=caveat], each closed by [/tag].
 */
object Markup {
    class Tok(val s: String, val st: WordStyle, val glue: Boolean = false)

    val TAG = Regex("\\[(/?)(b|i|u|c|size|font|big|huge|small)(?:=([^\\]]*))?\\]")

    fun strip(text: String): String = TAG.replace(text, "")

    fun parse(raw: String, upper: Boolean): MutableList<List<Tok>> {
        val paras = ArrayList<List<Tok>>()
        var cur = ArrayList<Tok>()
        var bold = false
        var italic = false
        var underline = false
        val colors = ArrayList<Int>()
        val sizes = ArrayList<Float>()
        val fonts = ArrayList<String>()
        val buf = StringBuilder()
        var bufStyle = WordStyle()

        fun style() = WordStyle(bold, italic, underline, colors.lastOrNull(), sizes.lastOrNull() ?: 1f, fonts.lastOrNull())
        fun flush() {
            if (buf.isNotEmpty()) {
                cur.add(Tok(buf.toString(), bufStyle))
                buf.setLength(0)
            }
        }
        fun feed(segment: String) {
            val seg = if (upper) segment.uppercase() else segment
            for (ch in seg) {
                when {
                    ch == '\n' -> { flush(); paras.add(cur); cur = ArrayList() }
                    ch.isWhitespace() -> flush()
                    else -> {
                        if (buf.isEmpty()) bufStyle = style()
                        buf.append(ch)
                    }
                }
            }
        }

        var last = 0
        for (m in TAG.findAll(raw)) {
            feed(raw.substring(last, m.range.first))
            val closing = m.groupValues[1] == "/"
            val arg = m.groupValues[3]
            when (m.groupValues[2]) {
                "b" -> bold = !closing
                "i" -> italic = !closing
                "u" -> underline = !closing
                "c" -> if (closing) { if (colors.isNotEmpty()) colors.removeAt(colors.size - 1) }
                else runCatching { colors.add(android.graphics.Color.parseColor(if (arg.startsWith("#")) arg else "#$arg")) }
                "size", "big", "huge", "small" -> if (closing) {
                    if (sizes.isNotEmpty()) sizes.removeAt(sizes.size - 1)
                } else {
                    val v = when (m.groupValues[2]) {
                        "big" -> 1.6f
                        "huge" -> 2.4f
                        "small" -> 0.7f
                        else -> arg.toFloatOrNull()?.coerceIn(0.4f, 4f) ?: 1f
                    }
                    sizes.add(v)
                }
                "font" -> if (closing) { if (fonts.isNotEmpty()) fonts.removeAt(fonts.size - 1) } else fonts.add(arg)
            }
            last = m.range.last + 1
        }
        feed(raw.substring(last))
        flush()
        paras.add(cur)

        while (paras.isNotEmpty() && paras.last().isEmpty()) paras.removeAt(paras.size - 1)
        while (paras.isNotEmpty() && paras.first().isEmpty()) paras.removeAt(0)
        return paras
    }
}

/**
 * Fit-to-shape text layout. The font size is searched so the text fills the content area (a rectangle
 * or a shape such as a heart), lines are justified/aligned, and per-word imperfections are added for typed
 * and handwritten looks. Everything is in a 1000-unit wide design space, so previews and exports wrap identically.
 */
object TextEngine {
    private const val MIN_SIZE = 4f

    data class Placed(
        val s: String,
        val x: Float,
        val y: Float,
        val rot: Float,
        val alpha: Float,
        val st: WordStyle,
        val w: Float,
        /** 0..1: only this fraction of the word's width is drawn (left to right), used by video reveals. */
        val clip: Float = 1f,
        /** Handwriting progress 0..1 (follows each stroke of each letter); 1 means fully written. */
        val hw: Float = 1f,
        /** Upward offset in px while a word is moving into place. */
        val rise: Float = 0f,
        /** Scale used by the pop animation. */
        val pop: Float = 1f,
    )

    class Result(
        val size: Float,
        val pitch: Float,
        val placed: List<Placed>,
        val baselines: List<Float>,
        val ddx: Float,
        val ddy: Float,
    )

    private class Ln(
        val toks: List<Markup.Tok>,
        val widths: List<Float>,
        val lastOfPara: Boolean,
        val blank: Boolean,
        val top: Float,
        val pitch: Float,
        val x0: Float,
        val x1: Float,
        val scale: Float,
    )

    private class Wrapped(val lines: List<Ln>, val broke: Boolean, val overflow: Boolean, val bottom: Float)

    fun depthLength(look: Look, size: Float): Float =
        if (look.depth == Depth.Extrude) look.depthLength * size * 0.45f else 0f

    fun depthVector(look: Look, size: Float): Pair<Float, Float> {
        val d = depthLength(look, size)
        val a = Math.toRadians(look.depthAngle.toDouble())
        return (d * cos(a)).toFloat() to (d * sin(a)).toFloat()
    }

    private fun isRtl(text: String): Boolean {
        var rtl = 0
        var letters = 0
        for (ch in text) {
            if (!Character.isLetter(ch)) continue
            letters++
            val d = Character.getDirectionality(ch)
            if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) rtl++
        }
        return letters > 0 && rtl * 2 > letters
    }

    fun layout(
        textIn: String,
        look: Look,
        tfOf: (String) -> Typeface,
        content: RectF,
        shape: CanvasShape,
        mask: ShapeMask?,
    ): Result {
        val rtl = isRtl(Markup.strip(textIn))
        val baseStyle = WordStyle()
        val paras = Markup.parse(textIn.replace("\r", ""), look.uppercase)
        if (paras.isEmpty()) paras.add(listOf("Your", "text", "here").map { Markup.Tok(it, baseStyle) })

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fun configure(st: WordStyle, size: Float) {
            paint.typeface = tfOf(st.fontId ?: look.fontId)
            paint.textSize = size * st.scale
            paint.letterSpacing = look.letterSpacing
            paint.isFakeBoldText = look.bold || st.bold
            paint.textSkewX = if (st.italic) -0.22f else 0f
        }
        fun measure(t: Markup.Tok, size: Float): Float {
            configure(t.st, size)
            return paint.measureText(t.s)
        }
        fun spaceW(size: Float): Float {
            configure(baseStyle, size)
            return paint.measureText(" ")
        }

        fun splitWord(t: Markup.Tok, size: Float, maxW: Float): List<Markup.Tok> {
            configure(t.st, size)
            val out = ArrayList<Markup.Tok>()
            val sb = StringBuilder()
            var i = 0
            while (i < t.s.length) {
                val cp = t.s.codePointAt(i)
                val n = Character.charCount(cp)
                val piece = t.s.substring(i, i + n)
                sb.append(piece)
                if (sb.length > n && paint.measureText(sb.toString()) > maxW) {
                    sb.setLength(sb.length - n)
                    out.add(Markup.Tok(sb.toString(), t.st, glue = out.isNotEmpty()))
                    sb.setLength(0)
                    sb.append(piece)
                }
                i += n
            }
            if (sb.isNotEmpty()) out.add(Markup.Tok(sb.toString(), t.st, glue = out.isNotEmpty()))
            return out
        }

        fun wrap(size: Float, yStart: Float, yMax: Float, spanOf: (Float, Float) -> Pair<Float, Float>?): Wrapped {
            val out = ArrayList<Ln>()
            var broke = false
            var overflow = false
            var y = yStart
            val sp = spaceW(size)
            val basePitch = size * 1.2f * look.lineSpacing
            val shaped = mask != null

            outer@ for (para in paras) {
                if (para.isEmpty()) {
                    if (y + basePitch > yMax + 0.5f) { overflow = true; break@outer }
                    val s = spanOf(y, y + basePitch) ?: (0f to 0f)
                    out.add(Ln(emptyList(), emptyList(), true, true, y, basePitch, s.first, s.second, 1f))
                    y += basePitch
                    continue
                }
                val queue = ArrayDeque(para)
                var guard = 0
                while (queue.isNotEmpty()) {
                    if (++guard > 100000) { overflow = true; break@outer }
                    val first = queue.first()
                    val estPitch = basePitch * max(1f, first.st.scale)
                    if (y + estPitch > yMax + 0.5f) { overflow = true; break@outer }
                    val span = spanOf(y, y + estPitch)
                    val maxW = if (span == null) 0f else span.second - span.first
                    if (span == null || maxW < size * 0.6f) { y += max(1f, size * 0.25f); continue }

                    val lineToks = ArrayList<Markup.Tok>()
                    val lineW = ArrayList<Float>()
                    var len = 0f
                    var skipDown = false
                    while (queue.isNotEmpty()) {
                        val t = queue.first()
                        if (t.glue && lineToks.isNotEmpty()) break
                        val w = measure(t, size)
                        if (w > maxW) {
                            if (lineToks.isNotEmpty()) break
                            if (shaped) { skipDown = true; break }
                            broke = true
                            val pieces = splitWord(t, size, maxW)
                            queue.removeFirst()
                            for (k in pieces.indices.reversed()) queue.addFirst(pieces[k])
                            continue
                        }
                        val need = if (lineToks.isEmpty()) w else len + sp + w
                        if (lineToks.isEmpty() || need <= maxW) {
                            lineToks.add(t); lineW.add(w); len = need; queue.removeFirst()
                        } else break
                    }
                    if (skipDown) { y += max(1f, size * 0.25f); continue }
                    if (lineToks.isEmpty()) { y += max(1f, size * 0.25f); continue }

                    val sc = lineToks.maxOf { it.st.scale }.coerceAtLeast(0.6f)
                    val pitch = basePitch * sc
                    if (y + pitch > yMax + 0.5f) { overflow = true; break@outer }
                    out.add(Ln(lineToks, lineW, queue.isEmpty(), false, y, pitch, span.first, span.second, sc))
                    y += pitch
                }
            }
            return Wrapped(out, broke, overflow, y)
        }

        fun spansFor(size: Float): (Float, Float) -> Pair<Float, Float>? {
            val (ddx, _) = depthVector(look, size)
            val l = content.left + max(0f, -ddx)
            val r = content.right - max(0f, ddx)
            return if (mask == null) {
                { _, _ -> l to r }
            } else {
                { t, b ->
                    mask.span(t, b)?.let { (ml, mr) -> max(ml, l) to min(mr, r) }?.takeIf { it.second - it.first > 2f }
                }
            }
        }

        fun fits(s: Float): Boolean {
            val (ddx, ddy) = depthVector(look, s)
            val aw = content.width() - abs(ddx)
            val ah = content.height() - abs(ddy)
            if (aw <= 8f || ah <= 8f) return false
            val y0 = content.top + max(0f, -ddy)
            val w = wrap(s, y0, y0 + ah, spansFor(s))
            return !w.broke && !w.overflow
        }

        var lo = MIN_SIZE
        var hi = content.height().coerceAtLeast(MIN_SIZE + 1f)
        repeat(20) {
            val mid = (lo + hi) / 2f
            if (fits(mid)) lo = mid else hi = mid
        }
        val size = (lo * look.textScale.coerceIn(0.3f, 1f)).coerceAtLeast(MIN_SIZE)

        val (ddx, ddy) = depthVector(look, size)
        val ah = content.height() - abs(ddy)
        val y0 = content.top + max(0f, -ddy)
        val spans = spansFor(size)

        // final wrap
        val lines: List<Ln>
        if (mask == null) {
            val w = wrap(size, 0f, Float.MAX_VALUE / 4f, spans)
            val total = w.bottom
            val spread = look.fillHeight && w.lines.size > 1 && look.textScale >= 0.999f && total > 0f
            val factor = if (spread) (ah / total).coerceIn(1f, 1.6f) else 1f
            val blockTop = y0 + (ah - total * factor) / 2f
            lines = w.lines.map {
                Ln(it.toks, it.widths, it.lastOfPara, it.blank, blockTop + it.top * factor, it.pitch * factor, it.x0, it.x1, it.scale)
            }
        } else {
            var w = wrap(size, y0, y0 + ah, spans)
            val leftover = (y0 + ah) - w.bottom
            if (!w.overflow && leftover > size * 0.8f) {
                val w2 = wrap(size, y0 + leftover / 2f, y0 + ah, spans)
                if (!w2.overflow && !w2.broke) w = w2
            }
            lines = w.lines
        }

        val space = spaceW(size)
        val mode = if (shape.forceJustify) Align.Justify else look.align
        val rnd = Random(textIn.hashCode() * 31 + look.fontId.hashCode())
        val hw = look.imperfection
        val yAmp = size * (if (look.handwritten) 0.06f else 0.012f) * hw
        val rotAmp = (if (look.handwritten) 1.6f else 0.3f) * hw
        val alphaDrop = (if (look.handwritten) 0.14f else 0.35f) * hw

        val placed = ArrayList<Placed>()
        val baselines = ArrayList<Float>()
        for (line in lines) {
            configure(baseStyle, size * line.scale)
            val fm = paint.fontMetrics
            val glyphBox = fm.descent - fm.ascent
            val baseline = line.top + (line.pitch - glyphBox) / 2f - fm.ascent
            baselines.add(baseline)
            if (line.blank) continue

            val aw = line.x1 - line.x0
            val count = line.toks.size
            val sumW = line.widths.sum()
            val gaps = count - 1
            val natural = sumW + gaps * space
            var gap = space
            var x = line.x0
            val canJustify = mode == Align.Justify && gaps > 0 &&
                (!line.lastOfPara || (shape.forceJustify && natural >= 0.6f * aw)) &&
                (shape.forceJustify || (aw - sumW) / gaps <= space * 4.5f)
            if (canJustify) {
                gap = (aw - sumW) / gaps
            } else when (mode) {
                Align.Center -> x = line.x0 + (aw - natural) / 2f
                Align.Right -> x = line.x0 + aw - natural
                else -> {}
            }
            val slope = if (look.handwritten) (rnd.nextFloat() - 0.5f) * 0.012f * hw else 0f
            val lineStart = x
            for (k in 0 until count) {
                var y = baseline + slope * (x - lineStart)
                var rot = 0f
                var alpha = 1f
                if (hw > 0f) {
                    y += (rnd.nextFloat() - 0.5f) * 2f * yAmp
                    rot = (rnd.nextFloat() - 0.5f) * 2f * rotAmp
                    alpha = 1f - rnd.nextFloat() * alphaDrop
                }
                val wk = line.widths[k]
                val px = if (rtl) line.x0 + line.x1 - (x + wk) else x
                placed.add(Placed(line.toks[k].s, px, y, rot, alpha, line.toks[k].st, wk))
                x += wk + gap
            }
        }
        val avgPitch = if (lines.isEmpty()) size * 1.2f else lines.sumOf { it.pitch.toDouble() }.toFloat() / lines.size
        return Result(size, avgPitch, placed, baselines, ddx, ddy)
    }

}
