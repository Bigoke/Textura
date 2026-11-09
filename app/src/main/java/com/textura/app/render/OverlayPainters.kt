package com.textura.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.textura.app.model.LightType
import com.textura.app.model.Sticker
import com.textura.app.model.StickerType
import com.textura.app.model.TextShape
import com.textura.app.model.ca
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Window light, lamp glow and spotlight laid over the finished picture. */
object LightPainter {
    fun draw(canvas: Canvas, type: LightType, strength: Float, w: Float, h: Float) {
        if (type == LightType.None) return
        val k = strength.coerceIn(0f, 1f)
        val rect = RectF(0f, 0f, w, h)
        val screen = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
        val longest = max(w, h)

        when (type) {
            LightType.Window -> {
                canvas.save()
                canvas.rotate(-18f, w / 2f, h / 2f)
                // soft beam
                screen.shader = LinearGradient(
                    0f, 0f, w, 0f,
                    intArrayOf(0, ca((150 * k).roundToInt(), 0xFFF2D0), ca((110 * k).roundToInt(), 0xFFF2D0), 0),
                    floatArrayOf(0.05f, 0.35f, 0.65f, 0.95f), Shader.TileMode.CLAMP,
                )
                canvas.drawRect(-w * 0.3f, -h * 0.3f, w * 1.3f, h * 1.3f, screen)
                // window bars cast darker stripes
                val bar = Paint().apply { color = ca((70 * k).roundToInt(), 0x000000) }
                val barW = w * 0.035f
                canvas.drawRect(w * 0.48f, -h * 0.3f, w * 0.48f + barW, h * 1.3f, bar)
                canvas.drawRect(-w * 0.3f, h * 0.42f, w * 1.3f, h * 0.42f + barW, bar)
                canvas.restore()
            }

            LightType.Lamp -> {
                screen.shader = RadialGradient(
                    w * 0.22f, h * 0.12f, longest * 0.85f,
                    intArrayOf(ca((190 * k).roundToInt(), 0xFFD9A0), ca((60 * k).roundToInt(), 0xFFB060), 0),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
                )
                canvas.drawRect(rect, screen)
                val vig = Paint().apply {
                    shader = RadialGradient(
                        w * 0.3f, h * 0.2f, longest * 1.1f,
                        intArrayOf(0, 0, ca((170 * k).roundToInt(), 0x000000)),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(rect, vig)
            }

            LightType.Spotlight -> {
                val vig = Paint().apply {
                    shader = RadialGradient(
                        w / 2f, h * 0.45f, longest * 0.75f,
                        intArrayOf(0, 0, ca((215 * k).roundToInt(), 0x000000)),
                        floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(rect, vig)
                screen.shader = RadialGradient(
                    w / 2f, h * 0.45f, longest * 0.5f,
                    intArrayOf(ca((70 * k).roundToInt(), 0xFFFFFF), 0),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
                )
                canvas.drawRect(rect, screen)
            }

            LightType.None -> {}
        }
    }
}

/** Optional signature drawn in a corner or centre at the bottom. */
object WatermarkPainter {
    fun draw(canvas: Canvas, text: String, position: Int, w: Float, h: Float) {
        if (text.isBlank()) return
        val size = w * 0.03f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            color = Color.argb(215, 255, 255, 255)
            setShadowLayer(size * 0.22f, 0f, size * 0.05f, Color.argb(190, 0, 0, 0))
            textAlign = when (position) {
                0 -> Paint.Align.LEFT
                2 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
        }
        val m = w * 0.04f
        val x = when (position) {
            0 -> m
            2 -> w - m
            else -> w / 2f
        }
        canvas.drawText(text, x, h - m * 0.7f, paint)
    }
}

/** Decorations that sit on top of the page: quote marks, lines, frames, tape, stars... */
object StickerPainter {
    fun draw(canvas: Canvas, stickers: List<Sticker>, w: Float, h: Float, tfOf: (String) -> Typeface) {
        for (s in stickers) drawOne(canvas, s, w, h, tfOf)
    }

    private fun fillPaint(s: Sticker, alphaScale: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = s.color
        alpha = (Color.alpha(s.color) * s.alpha * alphaScale).roundToInt().coerceIn(0, 255)
    }

    private fun strokePaint(s: Sticker, width: Float) = fillPaint(s).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun drawOne(canvas: Canvas, s: Sticker, w: Float, h: Float, tfOf: (String) -> Typeface) {
        val cx = s.x * w
        val cy = s.y * h
        val size = s.size * w
        val half = size / 2f
        canvas.save()
        canvas.rotate(s.rot, cx, cy)
        canvas.translate(cx, cy)

        when (s.type) {
            StickerType.QuoteOpen, StickerType.QuoteClose -> {
                val p = fillPaint(s).apply {
                    typeface = tfOf("playfair")
                    textSize = size * 1.9f
                    textAlign = Paint.Align.CENTER
                }
                val glyph = if (s.type == StickerType.QuoteOpen) "“" else "”"
                canvas.drawText(glyph, 0f, size * 0.62f, p)
            }
            StickerType.Line -> canvas.drawLine(-half, 0f, half, 0f, strokePaint(s, max(2f, size * 0.025f)))
            StickerType.DoubleLine -> {
                val sp = strokePaint(s, max(2f, size * 0.02f))
                val gap = size * 0.035f
                canvas.drawLine(-half, -gap, half, -gap, sp)
                canvas.drawLine(-half, gap, half, gap, sp)
            }
            StickerType.Frame -> {
                val fw = s.size * w
                val fh = s.size * h
                canvas.drawRect(-fw / 2f, -fh / 2f, fw / 2f, fh / 2f, strokePaint(s, max(3f, w * 0.006f)))
            }
            StickerType.Corners -> {
                val fw = s.size * w / 2f
                val fh = s.size * h / 2f
                val len = w * 0.07f
                val sp = strokePaint(s, max(3f, w * 0.007f))
                for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                    val x = sx * fw
                    val y = sy * fh
                    canvas.drawLine(x, y, x - sx * len, y, sp)
                    canvas.drawLine(x, y, x, y - sy * len, sp)
                }
            }
            StickerType.Tape -> {
                val th = size * 0.32f
                val path = Path()
                val tooth = th / 5f
                path.moveTo(-half, -th / 2f)
                path.lineTo(half, -th / 2f)
                var y = -th / 2f
                var flip = true
                while (y < th / 2f) {
                    y += tooth
                    path.lineTo(half + if (flip) tooth else 0f, y.coerceAtMost(th / 2f))
                    flip = !flip
                }
                path.lineTo(-half, th / 2f)
                y = th / 2f
                while (y > -th / 2f) {
                    y -= tooth
                    path.lineTo(-half - if (flip) tooth else 0f, y.coerceAtLeast(-th / 2f))
                    flip = !flip
                }
                path.close()
                canvas.drawPath(path, fillPaint(s, 0.72f))
                val edge = fillPaint(s, 0.5f).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f; color = Color.argb(60, 0, 0, 0) }
                canvas.drawPath(path, edge)
            }
            StickerType.Star -> {
                val path = Path()
                for (i in 0 until 10) {
                    val a = -PI / 2 + i * PI / 5
                    val r = if (i % 2 == 0) half else half * 0.45f
                    val px = (r * cos(a)).toFloat()
                    val py = (r * sin(a)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, fillPaint(s))
            }
            StickerType.Heart -> {
                canvas.drawPath(ShapePaths.path(TextShape.Heart, RectF(-half, -half, half, half)), fillPaint(s))
            }
            StickerType.Sparkle -> {
                val path = Path()
                val pts = listOf(0f to -1f, 0.2f to -0.2f, 1f to 0f, 0.2f to 0.2f, 0f to 1f, -0.2f to 0.2f, -1f to 0f, -0.2f to -0.2f)
                pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * half, y * half) else path.lineTo(x * half, y * half) }
                path.close()
                canvas.drawPath(path, fillPaint(s))
            }
            StickerType.Squiggle -> {
                val path = Path()
                val amp = size * 0.07f
                val periods = 5
                val steps = 60
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = -half + t * size
                    val y = (amp * sin(t * periods * 2 * PI)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, strokePaint(s, max(2.5f, size * 0.03f)))
            }
            StickerType.Arrow -> {
                val sp = strokePaint(s, max(2.5f, size * 0.03f))
                canvas.drawLine(-half, 0f, half, 0f, sp)
                val head = size * 0.16f
                canvas.drawLine(half, 0f, half - head, -head, sp)
                canvas.drawLine(half, 0f, half - head, head, sp)
            }
            StickerType.Ring -> canvas.drawCircle(0f, 0f, half, strokePaint(s, max(3f, size * 0.035f)))
            StickerType.Dots -> {
                val p = fillPaint(s)
                val r = size * 0.07f
                canvas.drawCircle(-size * 0.3f, 0f, r, p)
                canvas.drawCircle(0f, 0f, r, p)
                canvas.drawCircle(size * 0.3f, 0f, r, p)
            }
        }
        canvas.restore()
    }
}
