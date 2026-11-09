package com.textura.app.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.textura.app.model.Look
import com.textura.app.model.PaperType
import com.textura.app.model.ca
import com.textura.app.model.c
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Draws the sheet (paper, board, sticky note...) that the text sits on. Design-space coordinates. */
object PaperPainter {

    private val noiseTile: Bitmap by lazy {
        val n = 256
        val px = IntArray(n * n)
        val rnd = Random(7)
        for (i in px.indices) {
            val a = rnd.nextInt(0, 60)
            px[i] = if (rnd.nextBoolean()) Color.argb(a, 0, 0, 0) else Color.argb(a / 2, 255, 255, 255)
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    private fun fill(canvas: Canvas, r: RectF, color: Int, radius: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(r, radius, radius, p)
    }

    private fun grain(canvas: Canvas, r: RectF, strength: Float, scale: Float, radius: Float) {
        if (strength <= 0.01f) return
        val shader = BitmapShader(noiseTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val m = Matrix()
        val s = 1.5f / scale
        m.setScale(s, s)
        shader.setLocalMatrix(m)
        val p = Paint().apply {
            this.shader = shader
            alpha = (strength.coerceIn(0f, 1.5f) * 255f).toInt().coerceIn(0, 255)
        }
        canvas.drawRoundRect(r, radius, radius, p)
    }

    private fun vignette(canvas: Canvas, r: RectF, color: Int, strength: Float) {
        val radius = max(r.width(), r.height()) * 0.75f
        val shader = RadialGradient(
            r.centerX(), r.centerY(), radius,
            intArrayOf(0, 0, ColorUtils.setAlphaComponent(color, (60 * strength).toInt()), ColorUtils.setAlphaComponent(color, (170 * strength).toInt())),
            floatArrayOf(0f, 0.5f, 0.82f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(r, Paint().apply { this.shader = shader })
    }

    private fun blobs(canvas: Canvas, r: RectF, color: Int, count: Int, seed: Int) {
        val rnd = Random(seed)
        repeat(count) {
            val cx = r.left + rnd.nextFloat() * r.width()
            val cy = r.top + rnd.nextFloat() * r.height()
            val rad = (0.05f + rnd.nextFloat() * 0.18f) * r.width()
            val shader = RadialGradient(
                cx, cy, rad,
                ColorUtils.setAlphaComponent(color, 14 + rnd.nextInt(26)), 0, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, rad, Paint().apply { this.shader = shader })
        }
    }

    private fun crease(canvas: Canvas, x: Float, r: RectF, vertical: Boolean, w: Float) {
        val p = Paint()
        val colors = intArrayOf(0, ca(46, 0x000000), ca(30, 0xFFFFFF), 0)
        val pos = floatArrayOf(0f, 0.45f, 0.6f, 1f)
        if (vertical) {
            p.shader = LinearGradient(x - w, 0f, x + w, 0f, colors, pos, Shader.TileMode.CLAMP)
            canvas.drawRect(x - w, r.top, x + w, r.bottom, p)
        } else {
            p.shader = LinearGradient(0f, x - w, 0f, x + w, colors, pos, Shader.TileMode.CLAMP)
            canvas.drawRect(r.left, x - w, r.right, x + w, p)
        }
    }

    fun draw(
        canvas: Canvas,
        look: Look,
        sheet: RectF,
        baselines: List<Float>,
        pitch: Float,
        scale: Float,
    ) {
        val corner = if (look.paperInset > 0f || look.tilt != 0f) 6f else 0f
        val w = sheet.width()
        val h = sheet.height()
        val seed = look.paper.ordinal * 977 + 13
        canvas.save()
        canvas.clipPath(sheetPath(look, sheet, corner))
        when (look.paper) {
            PaperType.None -> {}

            PaperType.Lined -> {
                fill(canvas, sheet, look.paperColor, corner)
                val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ColorUtils.blendARGB(look.paperColor, c(0x5B8DB8), 0.6f)
                    strokeWidth = max(1.1f, pitch * 0.03f)
                }
                val offset = pitch * 0.1f
                val first = baselines.firstOrNull() ?: (sheet.top + pitch)
                var y = first + offset
                while (y - pitch > sheet.top) y -= pitch
                while (y < sheet.bottom) {
                    canvas.drawLine(sheet.left, y, sheet.right, y, rule)
                    y += pitch
                }
                val mx = sheet.left + w * 0.062f
                val margin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ca(170, 0xD9534F)
                    strokeWidth = 1.8f
                }
                canvas.drawLine(mx, sheet.top, mx, sheet.bottom, margin)
                val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ca(46, 0x000000) }
                for (f in floatArrayOf(0.18f, 0.5f, 0.82f)) canvas.drawCircle(sheet.left + w * 0.026f, sheet.top + h * f, w * 0.0125f, hole)
                grain(canvas, sheet, look.grain * 0.5f, scale, corner)
            }

            PaperType.Graph -> {
                fill(canvas, sheet, look.paperColor, corner)
                val cell = w / 28f
                val minor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ColorUtils.blendARGB(look.paperColor, c(0x5B8DB8), 0.35f); strokeWidth = 0.9f
                }
                val major = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ColorUtils.blendARGB(look.paperColor, c(0x5B8DB8), 0.6f); strokeWidth = 1.6f
                }
                var i = 0
                var x = sheet.left
                while (x <= sheet.right) { canvas.drawLine(x, sheet.top, x, sheet.bottom, if (i % 5 == 0) major else minor); x += cell; i++ }
                i = 0
                var y = sheet.top
                while (y <= sheet.bottom) { canvas.drawLine(sheet.left, y, sheet.right, y, if (i % 5 == 0) major else minor); y += cell; i++ }
                grain(canvas, sheet, look.grain * 0.4f, scale, corner)
            }

            PaperType.Dotted -> {
                fill(canvas, sheet, look.paperColor, corner)
                val cell = w / 26f
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.blendARGB(look.paperColor, c(0x6B6B73), 0.65f) }
                var y = sheet.top + cell / 2f
                while (y < sheet.bottom) {
                    var x = sheet.left + cell / 2f
                    while (x < sheet.right) { canvas.drawCircle(x, y, 1.7f, dot); x += cell }
                    y += cell
                }
                grain(canvas, sheet, look.grain * 0.4f, scale, corner)
            }

            PaperType.Parchment, PaperType.Letter -> {
                fill(canvas, sheet, look.paperColor, corner)
                blobs(canvas, sheet, c(0x8A5A24), 14, seed)
                grain(canvas, sheet, look.grain, scale, corner)
                vignette(canvas, sheet, c(0x6B3F12), if (look.paper == PaperType.Letter) 1.25f else 1f)
                if (look.paper == PaperType.Letter) {
                    crease(canvas, sheet.left + w / 3f, sheet, true, 7f)
                    crease(canvas, sheet.left + 2f * w / 3f, sheet, true, 7f)
                    crease(canvas, sheet.top + h / 2f, sheet, false, 7f)
                    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = ca(52, 0x5A3410)
                        strokeWidth = 7f
                        maskFilter = BlurMaskFilter(2.5f, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawCircle(sheet.right - w * 0.2f, sheet.bottom - h * 0.16f, w * 0.085f, ring)
                }
            }

            PaperType.Sticky -> {
                val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        sheet.left, sheet.top, sheet.right, sheet.bottom,
                        ColorUtils.blendARGB(look.paperColor, Color.WHITE, 0.25f),
                        ColorUtils.blendARGB(look.paperColor, Color.BLACK, 0.08f),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRoundRect(sheet, corner, corner, base)
                val strip = Paint().apply {
                    shader = LinearGradient(0f, sheet.top, 0f, sheet.top + h * 0.09f, ca(40, 0x000000), 0, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(sheet.left, sheet.top, sheet.right, sheet.top + h * 0.09f, strip)
                val curl = Paint().apply {
                    shader = LinearGradient(0f, sheet.bottom - h * 0.07f, 0f, sheet.bottom, 0, ca(50, 0x000000), Shader.TileMode.CLAMP)
                }
                canvas.drawRect(sheet.left, sheet.bottom - h * 0.07f, sheet.right, sheet.bottom, curl)
                grain(canvas, sheet, look.grain * 0.35f, scale, corner)
            }

            PaperType.Receipt -> {
                fill(canvas, sheet, look.paperColor, 0f)
                val band = Paint().apply {
                    shader = LinearGradient(sheet.left, 0f, sheet.right, 0f, intArrayOf(ca(18, 0x000000), 0, 0, ca(18, 0x000000)), floatArrayOf(0f, 0.12f, 0.88f, 1f), Shader.TileMode.CLAMP)
                }
                canvas.drawRect(sheet, band)
                grain(canvas, sheet, look.grain * 0.3f, scale, 0f)
            }

            PaperType.Chalkboard -> {
                val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        sheet.left, sheet.top, sheet.right, sheet.bottom,
                        look.paperColor, ColorUtils.blendARGB(look.paperColor, Color.BLACK, 0.35f), Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRoundRect(sheet, corner, corner, base)
                val rnd = Random(seed)
                val smudge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ca(14, 0xFFFFFF)
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = w * 0.06f
                    maskFilter = BlurMaskFilter(w * 0.03f, BlurMaskFilter.Blur.NORMAL)
                }
                repeat(12) {
                    val x1 = sheet.left + rnd.nextFloat() * w
                    val y1 = sheet.top + rnd.nextFloat() * h
                    canvas.drawLine(x1, y1, x1 + (rnd.nextFloat() - 0.5f) * w * 0.6f, y1 + (rnd.nextFloat() - 0.5f) * h * 0.3f, smudge)
                }
                grain(canvas, sheet, look.grain * 0.8f, scale, corner)
                val frameW = w * 0.03f
                val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = frameW
                    color = c(0x8B5E3C)
                }
                canvas.drawRect(sheet.left + frameW / 2f, sheet.top + frameW / 2f, sheet.right - frameW / 2f, sheet.bottom - frameW / 2f, frame)
                val lip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = ca(90, 0x000000) }
                canvas.drawRect(sheet.left + frameW, sheet.top + frameW, sheet.right - frameW, sheet.bottom - frameW, lip)
            }

            PaperType.Newsprint -> {
                fill(canvas, sheet, look.paperColor, corner)
                grain(canvas, sheet, look.grain * 1.1f, scale, corner)
                val col = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ca(40, 0x000000); strokeWidth = 1.2f }
                canvas.drawLine(sheet.left + w / 3f, sheet.top, sheet.left + w / 3f, sheet.bottom, col)
                canvas.drawLine(sheet.left + 2f * w / 3f, sheet.top, sheet.left + 2f * w / 3f, sheet.bottom, col)
                vignette(canvas, sheet, c(0x7A6A48), 0.5f)
            }

            PaperType.Glass -> {}

            PaperType.Kraft -> {
                fill(canvas, sheet, look.paperColor, corner)
                val rnd = Random(seed)
                val fiber = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.1f; strokeCap = Paint.Cap.ROUND }
                repeat(260) {
                    val x1 = sheet.left + rnd.nextFloat() * w
                    val y1 = sheet.top + rnd.nextFloat() * h
                    val len = 14f + rnd.nextFloat() * 48f
                    val a = rnd.nextFloat() * 6.2832f
                    fiber.color = if (rnd.nextBoolean()) ca(34, 0x4A2E14) else ca(40, 0xE3C79A)
                    canvas.drawLine(x1, y1, x1 + kotlin.math.cos(a) * len, y1 + kotlin.math.sin(a) * len, fiber)
                }
                grain(canvas, sheet, look.grain * 0.9f, scale, corner)
                vignette(canvas, sheet, c(0x5A3A18), 0.55f)
            }
        }
        canvas.restore()
    }

    /** Outline of the sheet: rounded rectangle, or torn-off zigzag top and bottom for a floating receipt. */
    fun sheetPath(look: Look, sheet: RectF, corner: Float): Path {
        val path = Path()
        val floating = look.paperInset > 0f || look.tilt != 0f
        if (look.torn) {
            val rnd = Random(333)
            val amp = sheet.width() * 0.012f
            fun jag(): Float = (rnd.nextFloat() - 0.5f) * 2f * amp
            path.moveTo(sheet.left + jag(), sheet.top + jag())
            var x = sheet.left
            while (x < sheet.right) { x += 6f + rnd.nextFloat() * 12f; path.lineTo(min(x, sheet.right) + jag() * 0.3f, sheet.top + jag()) }
            var y = sheet.top
            while (y < sheet.bottom) { y += 6f + rnd.nextFloat() * 12f; path.lineTo(sheet.right + jag(), min(y, sheet.bottom)) }
            x = sheet.right
            while (x > sheet.left) { x -= 6f + rnd.nextFloat() * 12f; path.lineTo(max(x, sheet.left), sheet.bottom + jag()) }
            y = sheet.bottom
            while (y > sheet.top) { y -= 6f + rnd.nextFloat() * 12f; path.lineTo(sheet.left + jag(), max(y, sheet.top)) }
            path.close()
        } else if (look.paper == PaperType.Receipt && floating) {
            val tooth = 9f
            path.moveTo(sheet.left, sheet.top + tooth)
            var x = sheet.left
            while (x < sheet.right) {
                path.lineTo(x + tooth / 2f, sheet.top)
                path.lineTo(x + tooth, sheet.top + tooth)
                x += tooth
            }
            path.lineTo(sheet.right, sheet.bottom - tooth)
            x = sheet.right
            while (x > sheet.left) {
                path.lineTo(x - tooth / 2f, sheet.bottom)
                path.lineTo(x - tooth, sheet.bottom - tooth)
                x -= tooth
            }
            path.close()
        } else {
            path.addRoundRect(sheet, corner, corner, Path.Direction.CW)
        }
        return path
    }

    /** Wear and damage drawn over the sheet: stains, folds, burn marks, ink blots. */
    fun drawEffects(canvas: Canvas, look: Look, sheet: RectF, scale: Float) {
        if (!(look.stains || look.folds || look.burnt || look.blots)) return
        val w = sheet.width()
        val h = sheet.height()
        val corner = if (look.paperInset > 0f || look.tilt != 0f) 6f else 0f
        val k = look.wear.coerceIn(0f, 1.5f)
        canvas.save()
        canvas.clipPath(sheetPath(look, sheet, corner))

        if (look.stains) {
            val rnd = Random(91)
            repeat(2) {
                val cx = sheet.left + w * (0.22f + 0.56f * rnd.nextFloat())
                val cy = sheet.top + h * (0.22f + 0.56f * rnd.nextFloat())
                val rad = w * (0.07f + 0.05f * rnd.nextFloat())
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = rad * 0.13f
                    color = ca((105 * k).toInt().coerceIn(0, 255), 0x5A3410)
                    maskFilter = BlurMaskFilter(rad * 0.05f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawCircle(cx, cy, rad, ring)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ca((30 * k).toInt().coerceIn(0, 255), 0x7A4A1C) }
                canvas.drawCircle(cx, cy, rad * 0.97f, fill)
                val drop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ca((90 * k).toInt().coerceIn(0, 255), 0x5A3410) }
                repeat(7) {
                    val a = rnd.nextFloat() * 6.2832f
                    val d = rad * (1.05f + rnd.nextFloat() * 0.5f)
                    canvas.drawCircle(cx + kotlin.math.cos(a) * d, cy + kotlin.math.sin(a) * d, 1.5f + rnd.nextFloat() * 4f, drop)
                }
            }
        }

        if (look.folds) {
            crease(canvas, sheet.left + w / 2f, sheet, true, 9f)
            crease(canvas, sheet.top + h / 2f, sheet, false, 9f)
        }

        if (look.burnt) {
            val radius = max(w, h) * 0.72f
            val shader = RadialGradient(
                sheet.centerX(), sheet.centerY(), radius,
                intArrayOf(0, 0, ca((80 * k).toInt().coerceIn(0, 255), 0xFF7A18), ca((235 * k).toInt().coerceIn(0, 255), 0x1A0C04)),
                floatArrayOf(0f, 0.55f, 0.82f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRect(sheet, Paint().apply { this.shader = shader })
        }

        if (look.blots) {
            val rnd = Random(55)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ca(165, 0x14213D)
                maskFilter = BlurMaskFilter(1.4f, BlurMaskFilter.Blur.NORMAL)
            }
            repeat(5) {
                val cx = sheet.left + w * (0.08f + 0.84f * rnd.nextFloat())
                val cy = sheet.top + h * (0.08f + 0.84f * rnd.nextFloat())
                val rad = (5f + rnd.nextFloat() * 16f) * (0.6f + k * 0.6f)
                canvas.drawCircle(cx, cy, rad, ink)
                repeat(5) {
                    val a = rnd.nextFloat() * 6.2832f
                    val d = rad * (1.3f + rnd.nextFloat() * 2.2f)
                    canvas.drawCircle(cx + kotlin.math.cos(a) * d, cy + kotlin.math.sin(a) * d, 0.8f + rnd.nextFloat() * 3f, ink)
                }
            }
        }
        canvas.restore()
    }

    /** Smallest scale so a w x h sheet rotated by [degrees] still fits inside the w x h canvas. */
    fun fitFactor(w: Float, h: Float, degrees: Float): Float {
        val rad = Math.toRadians(kotlin.math.abs(degrees).toDouble())
        val cs = kotlin.math.cos(rad).toFloat()
        val sn = kotlin.math.sin(rad).toFloat()
        return min(w / (w * cs + h * sn), h / (w * sn + h * cs))
    }
}
