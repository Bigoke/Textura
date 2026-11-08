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
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fit-to-shape text layout. The font size is searched so the text fills the content rectangle,
 * lines are justified/aligned, and per-word imperfections are added for typed / handwritten looks.
 * All coordinates are in "design space" (1000 units wide) so previews and exports break lines identically.
 */
object TextEngine {
    private const val MIN_SIZE = 4f

    class Placed(val s: String, val x: Float, val y: Float, val rot: Float, val alpha: Float)

    class Result(
        val size: Float,
        val pitch: Float,
        val placed: List<Placed>,
        val baselines: List<Float>,
        val ddx: Float,
        val ddy: Float,
    )

    private class Line(
        val words: List<String>,
        val widths: List<Float>,
        val lastOfPara: Boolean,
        val blank: Boolean = false,
    )

    private class Wrapped(val lines: List<Line>, val brokeWord: Boolean)

    fun depthLength(look: Look, size: Float): Float =
        if (look.depth == Depth.Extrude) look.depthLength * size * 0.45f else 0f

    fun depthVector(look: Look, size: Float): Pair<Float, Float> {
        val d = depthLength(look, size)
        val a = Math.toRadians(look.depthAngle.toDouble())
        return (d * cos(a)).toFloat() to (d * sin(a)).toFloat()
    }

    fun layout(textIn: String, look: Look, tf: Typeface, content: RectF, shape: CanvasShape): Result {
        var text = textIn.replace("\r", "")
        if (look.uppercase) text = text.uppercase()
        val paras: MutableList<List<String>> = text.split("\n")
            .map { p -> p.split(Regex("\\s+")).filter { it.isNotEmpty() } }
            .toMutableList()
        while (paras.isNotEmpty() && paras.last().isEmpty()) paras.removeAt(paras.size - 1)
        while (paras.isNotEmpty() && paras.first().isEmpty()) paras.removeAt(0)
        if (paras.isEmpty()) paras.add(listOf("Your", "text", "here"))

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            letterSpacing = look.letterSpacing
            isFakeBoldText = look.bold
        }

        fun availW(s: Float) = content.width() - abs(depthVector(look, s).first)
        fun availH(s: Float) = content.height() - abs(depthVector(look, s).second)

        fun fits(s: Float): Boolean {
            paint.textSize = s
            val aw = availW(s)
            val ah = availH(s)
            if (aw <= 8f || ah <= 8f) return false
            val wrapped = wrap(paras, paint, aw)
            if (wrapped.brokeWord) return false
            val pitch = s * 1.2f * look.lineSpacing
            return wrapped.lines.size * pitch <= ah + 0.5f
        }

        var lo = MIN_SIZE
        var hi = content.height().coerceAtLeast(MIN_SIZE + 1f)
        repeat(20) {
            val mid = (lo + hi) / 2f
            if (fits(mid)) lo = mid else hi = mid
        }
        val size = lo
        paint.textSize = size

        val (ddx, ddy) = depthVector(look, size)
        val aw = content.width() - abs(ddx)
        val ah = content.height() - abs(ddy)
        val x0 = content.left + maxOf(0f, -ddx)
        val y0 = content.top + maxOf(0f, -ddy)

        val lines = wrap(paras, paint, aw).lines
        val n = lines.size
        val basePitch = size * 1.2f * look.lineSpacing
        val pitch = if (look.fillHeight && n > 1) {
            min(basePitch + (ah - n * basePitch) / n, basePitch * 1.6f).coerceAtLeast(basePitch * 0.5f)
        } else basePitch
        val blockTop = y0 + (ah - n * pitch) / 2f
        val fm = paint.fontMetrics
        val glyphBox = fm.descent - fm.ascent
        val space = paint.measureText(" ")
        val mode = if (shape.forceJustify) Align.Justify else look.align

        val rnd = Random(textIn.hashCode() * 31 + look.fontId.hashCode())
        val hw = look.imperfection
        val yAmp = size * (if (look.handwritten) 0.06f else 0.012f) * hw
        val rotAmp = (if (look.handwritten) 1.6f else 0.3f) * hw
        val alphaDrop = (if (look.handwritten) 0.14f else 0.35f) * hw

        val placed = ArrayList<Placed>()
        val baselines = ArrayList<Float>()
        for ((i, line) in lines.withIndex()) {
            val baseline = blockTop + i * pitch + (pitch - glyphBox) / 2f - fm.ascent
            baselines.add(baseline)
            if (line.blank) continue
            val count = line.words.size
            val sumW = line.widths.sum()
            val gaps = count - 1
            val natural = sumW + gaps * space
            var gap = space
            var x = x0
            val canJustify = mode == Align.Justify && gaps > 0 &&
                (!line.lastOfPara || (shape.forceJustify && natural >= 0.6f * aw)) &&
                (shape.forceJustify || (aw - sumW) / gaps <= space * 4.5f)
            if (canJustify) {
                gap = (aw - sumW) / gaps
            } else when (mode) {
                Align.Center -> x = x0 + (aw - natural) / 2f
                Align.Right -> x = x0 + aw - natural
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
                placed.add(Placed(line.words[k], x, y, rot, alpha))
                x += line.widths[k] + gap
            }
        }
        return Result(size, pitch, placed, baselines, ddx, ddy)
    }

    private fun wrap(paras: List<List<String>>, paint: TextPaint, maxW: Float): Wrapped {
        val space = paint.measureText(" ")
        val out = ArrayList<Line>()
        var broke = false
        for (words in paras) {
            if (words.isEmpty()) {
                out.add(Line(emptyList(), emptyList(), lastOfPara = true, blank = true))
                continue
            }
            var cur = ArrayList<String>()
            var curW = ArrayList<Float>()
            var curLen = 0f
            for (word in words) {
                val whole = paint.measureText(word)
                val pieces = if (whole > maxW) {
                    broke = true
                    splitWord(word, paint, maxW)
                } else listOf(word)
                for ((idx, piece) in pieces.withIndex()) {
                    val pw = if (pieces.size == 1) whole else paint.measureText(piece)
                    if (idx > 0 && cur.isNotEmpty()) {
                        out.add(Line(cur, curW, lastOfPara = false))
                        cur = ArrayList()
                        curW = ArrayList()
                        curLen = 0f
                    }
                    val need = if (cur.isEmpty()) pw else curLen + space + pw
                    if (cur.isEmpty() || need <= maxW) {
                        cur.add(piece)
                        curW.add(pw)
                        curLen = need
                    } else {
                        out.add(Line(cur, curW, lastOfPara = false))
                        cur = arrayListOf(piece)
                        curW = arrayListOf(pw)
                        curLen = pw
                    }
                }
            }
            out.add(Line(cur, curW, lastOfPara = true))
        }
        return Wrapped(out, broke)
    }

    private fun splitWord(word: String, paint: TextPaint, maxW: Float): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in word) {
            sb.append(ch)
            if (sb.length > 1 && paint.measureText(sb.toString()) > maxW) {
                sb.setLength(sb.length - 1)
                result.add(sb.toString())
                sb.setLength(0)
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }
}
