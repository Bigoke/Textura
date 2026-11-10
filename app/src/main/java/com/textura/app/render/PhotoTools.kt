package com.textura.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/** Looks at a background photo to help keep the text readable and to suggest matching colours. */
object PhotoTools {
    class Analysis(val brightness: Float, val busyness: Float, val palette: List<Int>)

    fun analyse(path: String): Analysis? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 200) sample *= 2
        val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val bmp = Bitmap.createScaledBitmap(raw, 48, 48, true)
        val n = 48 * 48
        val px = IntArray(n)
        bmp.getPixels(px, 0, 48, 0, 0, 48, 48)
        var sum = 0.0
        val lum = FloatArray(n)
        val buckets = HashMap<Int, IntArray>() // key -> [count, r, g, b]
        for (i in 0 until n) {
            val r = Color.red(px[i]); val g = Color.green(px[i]); val b = Color.blue(px[i])
            val l = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            lum[i] = l; sum += l
            val key = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
            val e = buckets.getOrPut(key) { IntArray(4) }
            e[0]++; e[1] += r; e[2] += g; e[3] += b
        }
        val mean = (sum / n).toFloat()
        var v = 0.0
        for (l in lum) v += (l - mean) * (l - mean)
        val sd = sqrt(v / n).toFloat()

        val ranked = buckets.values.sortedByDescending { it[0] }
        val colors = ArrayList<Int>()
        for (e in ranked) {
            val c = Color.rgb(e[1] / e[0], e[2] / e[0], e[3] / e[0])
            val far = colors.all { o ->
                abs(Color.red(o) - Color.red(c)) + abs(Color.green(o) - Color.green(c)) + abs(Color.blue(o) - Color.blue(c)) > 110
            }
            if (far) colors += c
            if (colors.size == 5) break
        }
        bmp.recycle(); if (raw !== bmp) raw.recycle()
        Analysis(mean, sd, colors)
    }.getOrNull()
}
