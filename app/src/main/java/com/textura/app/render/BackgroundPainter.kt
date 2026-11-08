package com.textura.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.LruCache
import com.textura.app.model.Background
import com.textura.app.model.BgKind
import com.textura.app.model.GradientType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object BackgroundPainter {

    private val imageCache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun gradientPaint(colors: List<Int>, type: GradientType, angle: Float, w: Float, h: Float): Paint {
        val first = colors.firstOrNull() ?: Color.BLACK
        val cs = if (colors.size >= 2) colors.toIntArray() else intArrayOf(first, first)
        val cx = w / 2f
        val cy = h / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = when (type) {
            GradientType.Linear -> {
                val rad = Math.toRadians(angle.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                val half = (abs(w * dx) + abs(h * dy)) / 2f
                LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, cs, null, Shader.TileMode.CLAMP)
            }
            GradientType.Radial ->
                RadialGradient(cx, cy, max(w, h) * 0.75f, cs, null, Shader.TileMode.CLAMP)
            GradientType.Sweep ->
                SweepGradient(cx, cy, cs + cs.first(), null)
        }
        return paint
    }

    fun draw(canvas: Canvas, bg: Background, w: Float, h: Float, scale: Float) {
        val rect = RectF(0f, 0f, w, h)
        when (bg.kind) {
            BgKind.Solid -> canvas.drawColor(bg.colors.firstOrNull() ?: Color.BLACK)
            BgKind.Gradient -> canvas.drawRect(rect, gradientPaint(bg.colors, bg.gradientType, bg.angle, w, h))
            BgKind.Image -> {
                val bmp = bg.imagePath?.let { loadImage(it, (w * scale).roundToInt()) }
                if (bmp == null) {
                    canvas.drawColor(Color.rgb(24, 24, 30))
                } else {
                    drawCropped(canvas, bmp, rect, bg.blur)
                }
            }
        }
        if (bg.overlay.isNotEmpty() && bg.overlayAlpha > 0f) {
            val p = gradientPaint(bg.overlay, GradientType.Linear, bg.angle, w, h)
            p.alpha = (bg.overlayAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
            canvas.drawRect(rect, p)
        }
        if (bg.dim > 0f) {
            canvas.drawColor(Color.argb((bg.dim.coerceIn(0f, 1f) * 255f).roundToInt(), 0, 0, 0))
        }
    }

    private fun drawCropped(canvas: Canvas, bmp: Bitmap, dst: RectF, blur: Float) {
        var source = bmp
        if (blur > 0.01f) {
            val f = 1f + blur * 26f
            val sw = (bmp.width / f).roundToInt().coerceAtLeast(2)
            val sh = (bmp.height / f).roundToInt().coerceAtLeast(2)
            source = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(bmp, sw, sh, true), bmp.width, bmp.height, true)
        }
        val dstRatio = dst.width() / dst.height()
        val srcRatio = source.width.toFloat() / source.height.toFloat()
        val src = if (srcRatio > dstRatio) {
            val sw = (source.height * dstRatio).roundToInt()
            val left = (source.width - sw) / 2
            Rect(left, 0, left + sw, source.height)
        } else {
            val sh = (source.width / dstRatio).roundToInt()
            val top = (source.height - sh) / 2
            Rect(0, top, source.width, top + sh)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, src, dst, paint)
    }

    private fun loadImage(path: String, targetW: Int): Bitmap? {
        val bucket = when {
            targetW <= 700 -> 700
            targetW <= 1400 -> 1400
            else -> 2800
        }
        val key = "$path|$bucket"
        imageCache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= bucket) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        imageCache.put(key, bmp)
        return bmp
    }
}
