package com.textura.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.textura.app.model.CanvasShape
import com.textura.app.model.Depth
import com.textura.app.model.Fonts
import com.textura.app.model.Look
import com.textura.app.model.PaperType
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    ): Bitmap {
        val (dw, dh) = designSize(shape)
        val scale = longEdgePx / max(dw, dh)
        val w = (dw * scale).roundToInt().coerceAtLeast(2)
        val h = (dh * scale).roundToInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(w / dw, h / dh)
        draw(canvas, context, look, text, shape, scale, transparent)
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
    ) {
        val (dw, dh) = designSize(shape)
        val tf = Fonts.typeface(context, look.fontId)
        val hasSheet = look.paper != PaperType.None && !transparent

        if (!transparent) BackgroundPainter.draw(canvas, look.bg, dw, dh, scale)

        canvas.save()
        var sheet = RectF(0f, 0f, dw, dh)
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
            if (inset > 0f || look.tilt != 0f) {
                val corner = 6f
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = look.paperColor
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

        val result = TextEngine.layout(text.ifBlank { "Your text\nappears here" }, look, tf, content, shape)

        if (hasSheet) PaperPainter.draw(canvas, look, sheet, result.baselines, result.pitch, scale)

        drawText(canvas, look, tf, result, content)
        canvas.restore()
    }

    // ---------------------------------------------------------------------------------------------

    private fun drawText(canvas: Canvas, look: Look, tf: Typeface, r: TextEngine.Result, content: RectF) {
        val size = r.size
        fun base(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = size
            letterSpacing = look.letterSpacing
            isFakeBoldText = look.bold
        }

        fun face(): TextPaint = base().apply {
            style = Paint.Style.FILL
            val second = look.textColor2
            if (second != null) {
                shader = LinearGradient(0f, content.top, 0f, content.bottom, look.textColor, second, Shader.TileMode.CLAMP)
            } else {
                color = look.textColor
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
                    pass(canvas, r.placed, p, look.shadowOffset * size, look.shadowOffset * size)
                }
                if (look.outline) {
                    val p = base().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = max(0.8f, look.outlineWidth * size)
                        strokeJoin = Paint.Join.ROUND
                        color = look.outlineColor
                    }
                    pass(canvas, r.placed, p, 0f, 0f)
                }
                val f = face()
                if (look.imperfection > 0f && !look.handwritten && !look.outline && !look.shadow) {
                    // typed ink bleed
                    f.setShadowLayer(max(0.6f, size * 0.012f), 0f, 0f, ColorUtils.setAlphaComponent(look.textColor, 110))
                }
                pass(canvas, r.placed, f, 0f, 0f)
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
                pass(canvas, r.placed, light, -d * sign, -d * sign)
                pass(canvas, r.placed, dark, d * sign, d * sign)
                pass(canvas, r.placed, face(), 0f, 0f)
            }

            Depth.Extrude -> {
                val len = TextEngine.depthLength(look, size)
                val steps = ceil(len / 0.8f).toInt().coerceIn(6, 40)
                val ddx = r.ddx
                val ddy = r.ddy

                // cast shadow
                val cast = base().apply {
                    color = Color.argb(95, 0, 0, 0)
                    maskFilter = BlurMaskFilter(max(1f, len * 0.35f), BlurMaskFilter.Blur.NORMAL)
                }
                pass(canvas, r.placed, cast, ddx * 1.18f, ddy * 1.18f)

                // side layers, back to front
                val side = base().apply {
                    style = Paint.Style.FILL_AND_STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = max(1f, len / steps * 1.6f)
                }
                for (i in steps downTo 1) {
                    val t = i / steps.toFloat()
                    side.color = if (look.gradientSide) ColorUtils.blendARGB(look.sideColor, Color.BLACK, 0.5f * t) else look.sideColor
                    pass(canvas, r.placed, side, ddx * t, ddy * t)
                }

                pass(canvas, r.placed, face(), 0f, 0f)

                if (look.highlight) {
                    val edge = base().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = max(0.8f, size * 0.014f)
                        strokeJoin = Paint.Join.ROUND
                        color = Color.argb(85, 255, 255, 255)
                    }
                    pass(canvas, r.placed, edge, 0f, 0f)
                }
            }
        }
    }

    /** Draws every placed word with the given paint, shifted by (dx, dy), keeping each word's own jitter. */
    private fun pass(canvas: Canvas, placed: List<TextEngine.Placed>, paint: Paint, dx: Float, dy: Float) {
        val baseAlpha = Color.alpha(paint.color)
        for (p in placed) {
            val px = p.x + dx
            val py = p.y + dy
            paint.alpha = (baseAlpha * p.alpha).roundToInt().coerceIn(0, 255)
            if (p.rot != 0f) {
                canvas.save()
                canvas.rotate(p.rot, px, py)
                canvas.drawText(p.s, px, py, paint)
                canvas.restore()
            } else {
                canvas.drawText(p.s, px, py, paint)
            }
        }
    }
}
