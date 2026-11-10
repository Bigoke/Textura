package com.textura.app.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.textura.app.model.FrameStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws a border around the whole picture. All sizes are in the 1000-unit design space. */
object FramePainter {
    private fun stroke(color: Int, w: Float, cap: Paint.Cap = Paint.Cap.BUTT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = w; strokeCap = cap; strokeJoin = Paint.Join.MITER
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun heart(cx: Float, cy: Float, s: Float) = Path().apply {
        moveTo(cx, cy + s * 0.9f)
        cubicTo(cx - s * 1.5f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.2f, cx, cy - s * 0.35f)
        cubicTo(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.5f, cy - s * 0.1f, cx, cy + s * 0.9f)
        close()
    }

    private fun star(cx: Float, cy: Float, s: Float) = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) s else s * 0.45f
            val a = -PI / 2 + i * PI / 5
            val x = cx + (cos(a) * r).toFloat()
            val y = cy + (sin(a) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** Calls [at] with evenly spaced points (and the direction angle in degrees) all the way round [r]. */
    private fun along(r: RectF, step: Float, at: (Float, Float) -> Unit) {
        val nx = (r.width() / step).roundToInt().coerceAtLeast(1)
        val ny = (r.height() / step).roundToInt().coerceAtLeast(1)
        for (i in 0 until nx) {
            val x = r.left + r.width() * i / nx
            at(x, r.top); at(r.right - (x - r.left), r.bottom)
        }
        for (i in 0 until ny) {
            val y = r.top + r.height() * i / ny
            at(r.right, y); at(r.left, r.bottom - (y - r.top))
        }
    }

    /** A full-bleed band: everything outside [inner] is filled. */
    private fun band(w: Float, h: Float, inner: RectF) = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addRect(0f, 0f, w, h, Path.Direction.CW)
        addRect(inner, Path.Direction.CW)
    }

    fun draw(c: Canvas, style: FrameStyle, color: Int, color2: Int, thick: Float, inset: Float, w: Float, h: Float) {
        if (style == FrameStyle.None) return
        val u = w / 1000f
        val th = (3f + thick * 20f) * u
        val ins = (12f + inset * 70f) * u
        val r = RectF(ins, ins, w - ins, h - ins)
        val m = min(w, h)

        when (style) {
            FrameStyle.None -> {}
            FrameStyle.Thin -> c.drawRect(r, stroke(color, th * 0.45f))
            FrameStyle.Bold -> c.drawRect(r, stroke(color, th * 1.7f))
            FrameStyle.Double -> {
                c.drawRect(r, stroke(color, th * 0.7f))
                val g = th * 2.4f
                c.drawRect(RectF(r.left + g, r.top + g, r.right - g, r.bottom - g), stroke(color, th * 0.35f))
            }
            FrameStyle.Triple -> {
                for (k in 0..2) {
                    val g = k * th * 1.9f
                    c.drawRect(RectF(r.left + g, r.top + g, r.right - g, r.bottom - g), stroke(color, th * (0.8f - k * 0.22f)))
                }
            }
            FrameStyle.Rounded -> c.drawRoundRect(r, 48f * u, 48f * u, stroke(color, th))
            FrameStyle.RoundedDouble -> {
                c.drawRoundRect(r, 56f * u, 56f * u, stroke(color, th * 0.8f))
                val g = th * 2.6f
                c.drawRoundRect(RectF(r.left + g, r.top + g, r.right - g, r.bottom - g), 40f * u, 40f * u, stroke(color, th * 0.4f))
            }
            FrameStyle.Dashed -> c.drawRect(r, stroke(color, th * 0.8f).apply { pathEffect = DashPathEffect(floatArrayOf(th * 4f, th * 2.4f), 0f) })
            FrameStyle.Dotted -> c.drawRect(r, stroke(color, th * 1.1f, Paint.Cap.ROUND).apply { pathEffect = DashPathEffect(floatArrayOf(0.01f, th * 2.6f), 0f) })
            FrameStyle.Stitched -> {
                c.drawRect(r, stroke(color, th * 0.35f))
                val g = th * 2f
                c.drawRect(RectF(r.left + g, r.top + g, r.right - g, r.bottom - g), stroke(color2, th * 0.5f, Paint.Cap.ROUND).apply { pathEffect = DashPathEffect(floatArrayOf(th * 2.6f, th * 1.8f), 0f) })
            }
            FrameStyle.Brackets -> {
                val len = m * 0.13f
                val p = Path()
                p.moveTo(r.left, r.top + len); p.lineTo(r.left, r.top); p.lineTo(r.left + len, r.top)
                p.moveTo(r.right - len, r.top); p.lineTo(r.right, r.top); p.lineTo(r.right, r.top + len)
                p.moveTo(r.right, r.bottom - len); p.lineTo(r.right, r.bottom); p.lineTo(r.right - len, r.bottom)
                p.moveTo(r.left + len, r.bottom); p.lineTo(r.left, r.bottom); p.lineTo(r.left, r.bottom - len)
                c.drawPath(p, stroke(color, th * 1.2f))
            }
            FrameStyle.Notched -> {
                val k = 46f * u + th * 2
                val p = Path().apply {
                    moveTo(r.left + k, r.top); lineTo(r.right - k, r.top); lineTo(r.right, r.top + k)
                    lineTo(r.right, r.bottom - k); lineTo(r.right - k, r.bottom); lineTo(r.left + k, r.bottom)
                    lineTo(r.left, r.bottom - k); lineTo(r.left, r.top + k); close()
                }
                c.drawPath(p, stroke(color, th * 0.9f))
            }
            FrameStyle.Scallop -> {
                val s = 38f * u + th * 1.5f
                val pt = stroke(color, th * 0.7f)
                val nx = (r.width() / s).roundToInt().coerceAtLeast(1); val sx = r.width() / nx
                val ny = (r.height() / s).roundToInt().coerceAtLeast(1); val sy = r.height() / ny
                for (i in 0 until nx) {
                    val x = r.left + i * sx
                    c.drawArc(RectF(x, r.top - sx / 2, x + sx, r.top + sx / 2), 180f, 180f, false, pt)
                    c.drawArc(RectF(x, r.bottom - sx / 2, x + sx, r.bottom + sx / 2), 0f, 180f, false, pt)
                }
                for (i in 0 until ny) {
                    val y = r.top + i * sy
                    c.drawArc(RectF(r.left - sy / 2, y, r.left + sy / 2, y + sy), 90f, 180f, false, pt)
                    c.drawArc(RectF(r.right - sy / 2, y, r.right + sy / 2, y + sy), -90f, 180f, false, pt)
                }
            }
            FrameStyle.Zigzag -> {
                val s = 26f * u + th
                val amp = s * 0.45f
                val p = Path()
                val nx = (r.width() / s).roundToInt().coerceAtLeast(2); val ny = (r.height() / s).roundToInt().coerceAtLeast(2)
                for (i in 0..nx) { val x = r.left + r.width() * i / nx; val y = r.top + if (i % 2 == 0) -amp else amp; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                for (i in 0..ny) p.lineTo(r.right + if (i % 2 == 0) -amp else amp, r.top + r.height() * i / ny)
                for (i in 0..nx) p.lineTo(r.right - r.width() * i / nx, r.bottom + if (i % 2 == 0) amp else -amp)
                for (i in 0..ny) p.lineTo(r.left + if (i % 2 == 0) amp else -amp, r.bottom - r.height() * i / ny)
                p.close()
                c.drawPath(p, stroke(color, th * 0.6f).apply { strokeJoin = Paint.Join.ROUND })
            }
            FrameStyle.Ticket -> {
                val rad = 34f * u + th * 2
                val my = (r.top + r.bottom) / 2
                val p = Path().apply {
                    moveTo(r.left, r.top); lineTo(r.right, r.top); lineTo(r.right, my - rad)
                    arcTo(RectF(r.right - rad, my - rad, r.right + rad, my + rad), 270f, -180f)
                    lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); lineTo(r.left, my + rad)
                    arcTo(RectF(r.left - rad, my - rad, r.left + rad, my + rad), 90f, -180f)
                    close()
                }
                c.drawPath(p, stroke(color, th * 0.9f))
                val g = th * 3f
                c.drawRect(RectF(r.left + g, r.top + g, r.right - g, r.bottom - g), stroke(color, th * 0.35f).apply { pathEffect = DashPathEffect(floatArrayOf(th * 2.5f, th * 2f), 0f) })
            }
            FrameStyle.Stamp -> {
                val b = th * 3.4f + 14f * u
                val inner = RectF(b, b, w - b, h - b)
                val path = band(w, h, inner)
                val holes = Path()
                val rr = b * 0.3f
                along(RectF(0f, 0f, w, h), rr * 3.2f) { x, y -> holes.addCircle(x, y, rr, Path.Direction.CW) }
                path.op(holes, Path.Op.DIFFERENCE)
                c.drawPath(path, fill(color))
            }
            FrameStyle.Polaroid -> {
                val b = th * 3f + 18f * u
                c.drawPath(band(w, h, RectF(b, b, w - b, h - b * 3.6f)), fill(color))
                c.drawRect(RectF(b, b, w - b, h - b * 3.6f), stroke(ColorUtils.setAlphaComponent(color2, 70), 2f * u))
            }
            FrameStyle.Mat -> {
                val b = th * 3.6f + 20f * u
                c.drawPath(band(w, h, RectF(b, b, w - b, h - b)), fill(color))
                val g = b * 0.22f
                c.drawRect(RectF(b - g, b - g, w - b + g, h - b + g), stroke(color2, th * 0.3f))
            }
            FrameStyle.Bevel -> {
                val b = th * 3f + 16f * u
                val light = ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.55f)
                val dark = ColorUtils.blendARGB(color, 0xFF000000.toInt(), 0.45f)
                val p = fill(color).apply {
                    shader = LinearGradient(0f, 0f, w, h, intArrayOf(light, color, dark, color, light), null, Shader.TileMode.CLAMP)
                }
                c.drawPath(band(w, h, RectF(b, b, w - b, h - b)), p)
                c.drawRect(RectF(b, b, w - b, h - b), stroke(dark, 2.5f * u))
                c.drawRect(RectF(1.5f * u, 1.5f * u, w - 1.5f * u, h - 1.5f * u), stroke(light, 3f * u))
            }
            FrameStyle.Film -> {
                val b = 64f * u + th * 2
                val p = fill(color)
                c.drawRect(0f, 0f, b, h, p); c.drawRect(w - b, 0f, w, h, p)
                val hole = fill(color2)
                val hw = b * 0.42f; val hh = b * 0.3f; val step = b * 0.72f
                var y = step * 0.35f
                while (y < h) {
                    c.drawRoundRect(RectF(b / 2 - hw / 2, y, b / 2 + hw / 2, y + hh), 3f * u, 3f * u, hole)
                    c.drawRoundRect(RectF(w - b / 2 - hw / 2, y, w - b / 2 + hw / 2, y + hh), 3f * u, 3f * u, hole)
                    y += step
                }
            }
            FrameStyle.Checker -> {
                val s = 18f * u + th
                val a = fill(color); val bb = fill(color2)
                val nx = (w / s).roundToInt().coerceAtLeast(2); val sx = w / nx
                val ny = (h / s).roundToInt().coerceAtLeast(2); val sy = h / ny
                for (i in 0 until nx) {
                    c.drawRect(i * sx, 0f, (i + 1) * sx, sy, if (i % 2 == 0) a else bb)
                    c.drawRect(i * sx, h - sy, (i + 1) * sx, h, if ((i + ny - 1) % 2 == 0) a else bb)
                }
                for (j in 1 until ny - 1) {
                    c.drawRect(0f, j * sy, sx, (j + 1) * sy, if (j % 2 == 0) a else bb)
                    c.drawRect(w - sx, j * sy, w, (j + 1) * sy, if ((j + nx - 1) % 2 == 0) a else bb)
                }
            }
            FrameStyle.ArtDeco -> {
                c.drawRect(r, stroke(color, th * 0.6f))
                val g = th * 3.2f; val k = 36f * u + g
                val ir = RectF(r.left + g, r.top + g, r.right - g, r.bottom - g)
                val p = Path().apply {
                    moveTo(ir.left + k, ir.top); lineTo(ir.right - k, ir.top); lineTo(ir.right, ir.top + k)
                    lineTo(ir.right, ir.bottom - k); lineTo(ir.right - k, ir.bottom); lineTo(ir.left + k, ir.bottom)
                    lineTo(ir.left, ir.bottom - k); lineTo(ir.left, ir.top + k); close()
                }
                c.drawPath(p, stroke(color, th * 0.4f))
                val d = fill(color)
                val ds = th * 1.8f
                for ((x, y) in listOf((r.left + r.right) / 2 to r.top, (r.left + r.right) / 2 to r.bottom, r.left to (r.top + r.bottom) / 2, r.right to (r.top + r.bottom) / 2)) {
                    c.drawPath(Path().apply { moveTo(x, y - ds); lineTo(x + ds, y); lineTo(x, y + ds); lineTo(x - ds, y); close() }, d)
                }
            }
            FrameStyle.Vintage -> {
                c.drawRect(r, stroke(color, th * 0.8f))
                val g = th * 2.4f
                c.drawRect(RectF(r.left + g, r.top + g, r.right - g, r.bottom - g), stroke(color, th * 0.3f))
                val sq = th * 2.6f
                for ((x, y) in listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)) {
                    c.drawRect(x - sq, y - sq, x + sq, y + sq, fill(color))
                    c.drawRect(x - sq * 0.45f, y - sq * 0.45f, x + sq * 0.45f, y + sq * 0.45f, fill(color2))
                }
                val mx = (r.left + r.right) / 2; val my = (r.top + r.bottom) / 2
                for ((x, y) in listOf(mx to r.top, mx to r.bottom, r.left to my, r.right to my)) c.drawCircle(x, y, sq * 0.6f, fill(color))
            }
            FrameStyle.Neon -> {
                val widths = floatArrayOf(th * 5f, th * 3f, th * 1.6f)
                val alphas = intArrayOf(40, 90, 255)
                for (i in 0..2) {
                    c.drawRoundRect(r, 20f * u, 20f * u, stroke(ColorUtils.setAlphaComponent(color, alphas[i]), widths[i]))
                }
                c.drawRoundRect(r, 20f * u, 20f * u, stroke(ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.7f), th * 0.6f))
            }
            FrameStyle.Shadow -> {
                val o = th * 1.8f
                c.drawRect(RectF(r.left + o, r.top + o, r.right + o, r.bottom + o), stroke(color2, th * 0.9f))
                c.drawRect(r, stroke(color, th * 0.9f))
            }
            FrameStyle.Sketch -> {
                for (pass in 0..1) {
                    val rnd = java.util.Random(7L + pass * 31L)
                    val j = th * 0.9f
                    val p = Path()
                    fun jit() = (rnd.nextFloat() - 0.5f) * 2f * j
                    val pts = ArrayList<Pair<Float, Float>>()
                    val n = 14
                    for (i in 0..n) pts += (r.left + r.width() * i / n) to (r.top + jit())
                    for (i in 1..n) pts += (r.right + jit()) to (r.top + r.height() * i / n)
                    for (i in 1..n) pts += (r.right - r.width() * i / n) to (r.bottom + jit())
                    for (i in 1..n) pts += (r.left + jit()) to (r.bottom - r.height() * i / n)
                    pts.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                    p.lineTo(r.left + jit(), r.top + jit() + th)
                    c.drawPath(p, stroke(color, th * (0.55f - pass * 0.15f), Paint.Cap.ROUND).apply { strokeJoin = Paint.Join.ROUND })
                }
            }
            FrameStyle.Floral -> {
                c.drawRect(r, stroke(color, th * 0.35f))
                val s = m * 0.07f
                for ((x, y, sx, sy) in listOf(
                    Quad(r.left, r.top, 1f, 1f), Quad(r.right, r.top, -1f, 1f),
                    Quad(r.left, r.bottom, 1f, -1f), Quad(r.right, r.bottom, -1f, -1f),
                )) {
                    c.save(); c.translate(x, y); c.scale(sx, sy)
                    val petal = fill(ColorUtils.setAlphaComponent(color, 215))
                    for (k in 0..4) {
                        val a = (k * 22.5f + 0f) * PI.toFloat() / 180f
                        c.drawCircle(cos(a) * s * 1.1f, sin(a) * s * 1.1f, s * 0.42f, petal)
                    }
                    c.drawCircle(0f, 0f, s * 0.55f, fill(color2))
                    c.drawCircle(s * 2.1f, s * 0.15f, s * 0.2f, petal); c.drawCircle(s * 0.15f, s * 2.1f, s * 0.2f, petal)
                    c.restore()
                }
            }
            FrameStyle.Hearts -> {
                val s = 7f * u + th * 0.9f
                along(r, s * 3.4f) { x, y -> c.drawPath(heart(x, y, s), fill(color)) }
            }
            FrameStyle.Stars -> {
                val s = 8f * u + th
                along(r, s * 3.2f) { x, y -> c.drawPath(star(x, y, s), fill(color)) }
            }
            FrameStyle.PhotoCorners -> {
                val len = m * 0.11f
                val p = fill(color)
                c.drawPath(Path().apply { moveTo(r.left, r.top); lineTo(r.left + len, r.top); lineTo(r.left, r.top + len); close() }, p)
                c.drawPath(Path().apply { moveTo(r.right, r.top); lineTo(r.right - len, r.top); lineTo(r.right, r.top + len); close() }, p)
                c.drawPath(Path().apply { moveTo(r.left, r.bottom); lineTo(r.left + len, r.bottom); lineTo(r.left, r.bottom - len); close() }, p)
                c.drawPath(Path().apply { moveTo(r.right, r.bottom); lineTo(r.right - len, r.bottom); lineTo(r.right, r.bottom - len); close() }, p)
            }
            FrameStyle.Tape -> {
                val tw = m * 0.2f; val tt = m * 0.055f
                val p = fill(ColorUtils.setAlphaComponent(color, 205))
                for ((x, y, ang) in listOf(Triple(r.left, r.top, -45f), Triple(r.right, r.top, 45f), Triple(r.left, r.bottom, 45f), Triple(r.right, r.bottom, -45f))) {
                    c.save(); c.rotate(ang, x, y)
                    c.drawRect(x - tw / 2, y - tt / 2, x + tw / 2, y + tt / 2, p)
                    c.drawRect(x - tw / 2, y - tt / 2, x - tw / 2 + 2f * u, y + tt / 2, fill(ColorUtils.setAlphaComponent(color2, 90)))
                    c.restore()
                }
            }
        }
    }

    private data class Quad(val x: Float, val y: Float, val sx: Float, val sy: Float)
}
