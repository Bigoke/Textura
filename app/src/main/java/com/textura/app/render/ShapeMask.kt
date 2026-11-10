package com.textura.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.textura.app.model.TextShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Outlines for the text shapes, in design coordinates, fitted into a rectangle. */
object ShapePaths {

    private fun fitUniform(pts: List<Pair<Float, Float>>, r: RectF): Path {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for ((x, y) in pts) {
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
        }
        val s = min(r.width() / (maxX - minX), r.height() / (maxY - minY))
        val ox = r.centerX() - (minX + maxX) / 2f * s
        val oy = r.centerY() - (minY + maxY) / 2f * s
        val path = Path()
        pts.forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(x * s + ox, y * s + oy) else path.lineTo(x * s + ox, y * s + oy)
        }
        path.close()
        return path
    }

    private fun stretch(unit: List<Pair<Float, Float>>, r: RectF): Path {
        val path = Path()
        unit.forEachIndexed { i, (x, y) ->
            val px = r.left + x * r.width()
            val py = r.top + y * r.height()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    fun path(shape: TextShape, r: RectF): Path = when (shape) {
        TextShape.Rect -> Path().apply { addRect(r, Path.Direction.CW) }
        TextShape.Circle -> Path().apply {
            val d = min(r.width(), r.height())
            addCircle(r.centerX(), r.centerY(), d / 2f, Path.Direction.CW)
        }
        TextShape.Oval -> Path().apply { addOval(r, Path.Direction.CW) }
        TextShape.Heart -> {
            val pts = ArrayList<Pair<Float, Float>>()
            val n = 160
            for (i in 0 until n) {
                val t = 2.0 * PI * i / n
                val x = 16.0 * sin(t).let { it * it * it }
                val y = -(13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t))
                pts.add(x.toFloat() to y.toFloat())
            }
            fitUniform(pts, r)
        }
        TextShape.Star -> {
            val pts = ArrayList<Pair<Float, Float>>()
            for (i in 0 until 10) {
                val ang = -PI / 2 + i * PI / 5
                val rad = if (i % 2 == 0) 1.0 else 0.55
                pts.add((rad * cos(ang)).toFloat() to (rad * sin(ang)).toFloat())
            }
            fitUniform(pts, r)
        }
        TextShape.Diamond -> stretch(listOf(0.5f to 0f, 1f to 0.5f, 0.5f to 1f, 0f to 0.5f), r)
        TextShape.Hexagon -> stretch(
            listOf(0.25f to 0f, 0.75f to 0f, 1f to 0.5f, 0.75f to 1f, 0.25f to 1f, 0f to 0.5f), r,
        )
        TextShape.Bubble -> Path().apply {
            val rad = min(r.width(), r.height()) * 0.18f
            addRoundRect(r, rad, rad, Path.Direction.CW)
        }
        TextShape.Triangle -> stretch(listOf(0.5f to 0f, 1f to 1f, 0f to 1f), r)
        TextShape.Octagon -> stretch(
            listOf(0.3f to 0f, 0.7f to 0f, 1f to 0.3f, 1f to 0.7f, 0.7f to 1f, 0.3f to 1f, 0f to 0.7f, 0f to 0.3f), r,
        )
        TextShape.Cross -> stretch(
            listOf(
                0.33f to 0f, 0.67f to 0f, 0.67f to 0.33f, 1f to 0.33f, 1f to 0.67f, 0.67f to 0.67f,
                0.67f to 1f, 0.33f to 1f, 0.33f to 0.67f, 0f to 0.67f, 0f to 0.33f, 0.33f to 0.33f,
            ), r,
        )
        TextShape.Shield -> stretch(
            listOf(0f to 0f, 1f to 0f, 1f to 0.5f, 0.92f to 0.72f, 0.5f to 1f, 0.08f to 0.72f, 0f to 0.5f), r,
        )
        TextShape.Drop -> {
            val pts = ArrayList<Pair<Float, Float>>()
            val n = 120
            for (i in 0 until n) {
                val t = 2.0 * PI * i / n
                val x = 0.5 + 0.5 * kotlin.math.sin(t) * kotlin.math.sin(t / 2) / 0.65
                val y = 0.5 - 0.5 * kotlin.math.cos(t)
                pts += x.toFloat() to y.toFloat()
            }
            stretch(pts, r)
        }
        TextShape.Cloud -> Path().apply {
            fun circ(cx: Float, cy: Float, rad: Float) = addCircle(r.left + cx * r.width(), r.top + cy * r.height(), rad * min(r.width(), r.height()), Path.Direction.CW)
            circ(0.27f, 0.62f, 0.22f); circ(0.5f, 0.4f, 0.28f); circ(0.76f, 0.6f, 0.24f); circ(0.5f, 0.64f, 0.26f)
            addRect(r.left + 0.2f * r.width(), r.top + 0.6f * r.height(), r.left + 0.82f * r.width(), r.top + 0.88f * r.height(), Path.Direction.CW)
        }
        TextShape.Arch -> Path().apply {
            val d = r.width()
            val topArc = RectF(r.left, r.top, r.right, r.top + d)
            moveTo(r.left, r.bottom)
            lineTo(r.left, r.top + d / 2f)
            arcTo(topArc, 180f, 180f, false)
            lineTo(r.right, r.bottom)
            close()
        }
    }
}

/**
 * Per-row horizontal extent of a shape, used to wrap text inside it.
 * Built once per shape and size from a small raster of the outline.
 */
class ShapeMask private constructor(
    private val left: Float,
    private val top: Float,
    private val unitsPerPx: Float,
    private val rowLeft: FloatArray,
    private val rowRight: FloatArray,
    private val pad: Float,
) {
    private fun rowOf(y: Float): Int = ((y - top) / unitsPerPx).toInt().coerceIn(0, rowLeft.size - 1)

    /** The widest horizontal stretch available for a line spanning [yTop]..[yBottom], or null if none. */
    fun span(yTop: Float, yBottom: Float): Pair<Float, Float>? {
        if (yBottom < top || yTop > top + rowLeft.size * unitsPerPx) return null
        val r0 = rowOf(yTop)
        val r1 = rowOf(yBottom)
        var l = -Float.MAX_VALUE
        var r = Float.MAX_VALUE
        val steps = 6
        for (i in 0..steps) {
            val row = r0 + ((r1 - r0) * i) / steps
            val rl = rowLeft[row]
            val rr = rowRight[row]
            if (rr <= rl) return null
            l = max(l, rl)
            r = min(r, rr)
        }
        l += pad
        r -= pad
        return if (r - l > 2f) l to r else null
    }

    companion object {
        private const val PX_PER_UNIT = 0.5f
        private val cache = LinkedHashMap<String, ShapeMask>()

        @Synchronized
        fun get(shape: TextShape, content: RectF): ShapeMask {
            val key = "${shape.name}|${content.left}|${content.top}|${content.width()}|${content.height()}"
            cache[key]?.let { return it }
            val m = build(shape, content)
            if (cache.size > 24) cache.remove(cache.keys.first())
            cache[key] = m
            return m
        }

        private fun build(shape: TextShape, content: RectF): ShapeMask {
            val w = max(4, (content.width() * PX_PER_UNIT).toInt())
            val h = max(4, (content.height() * PX_PER_UNIT).toInt())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val local = RectF(0f, 0f, w.toFloat(), h.toFloat())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawPath(ShapePaths.path(shape, local), paint)

            val unitsPerPx = 1f / PX_PER_UNIT
            val rowLeft = FloatArray(h)
            val rowRight = FloatArray(h)
            val buf = IntArray(w)
            val mergeGap = max(2, (w * 0.08f).toInt())
            for (row in 0 until h) {
                bmp.getPixels(buf, 0, w, 0, row, w, 1)
                var bestStart = -1
                var bestEnd = -1
                var runStart = -1
                var runEnd = -1
                for (x in 0 until w) {
                    val on = (buf[x] ushr 24) > 127
                    if (on) {
                        if (runStart < 0) runStart = x
                        else if (x - runEnd - 1 > mergeGap) {
                            if (runEnd - runStart > bestEnd - bestStart) { bestStart = runStart; bestEnd = runEnd }
                            runStart = x
                        }
                        runEnd = x
                    }
                }
                if (runStart >= 0 && runEnd - runStart > bestEnd - bestStart) { bestStart = runStart; bestEnd = runEnd }
                if (bestStart >= 0) {
                    rowLeft[row] = content.left + bestStart * unitsPerPx
                    rowRight[row] = content.left + (bestEnd + 1) * unitsPerPx
                } else {
                    rowLeft[row] = 0f
                    rowRight[row] = 0f
                }
            }
            bmp.recycle()
            return ShapeMask(content.left, content.top, unitsPerPx, rowLeft, rowRight, content.width() * 0.012f)
        }
    }
}
