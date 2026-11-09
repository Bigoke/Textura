package com.textura.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds the centre line of a letter (as pen strokes in writing order) by thinning its filled shape.
 * This lets the pen follow every line of every letter, whatever font is used.
 * Coordinates are in "glyph units": a font size of 100, origin at the baseline start of the letter.
 */
object GlyphStrokes {
    class Skel(
        /** Each stroke is x0,y0,x1,y1,... in glyph units. */
        val strokes: List<FloatArray>,
        val lengths: FloatArray,
        val total: Float,
        /** Estimated pen thickness in glyph units. */
        val stem: Float,
    ) {
        /** The strokes drawn up to [dist] units of pen travel, as a path. */
        fun pathUpTo(dist: Float): Path {
            val path = Path()
            var left = dist
            for ((si, s) in strokes.withIndex()) {
                if (left <= 0f) break
                val len = lengths[si]
                if (s.size < 4) {
                    if (s.size >= 2) { path.moveTo(s[0], s[1]); path.lineTo(s[0] + 0.01f, s[1]) }
                    left -= len
                    continue
                }
                path.moveTo(s[0], s[1])
                var remaining = left
                var i = 2
                while (i < s.size) {
                    val dx = s[i] - s[i - 2]
                    val dy = s[i + 1] - s[i - 1]
                    val seg = hypot(dx, dy)
                    if (seg >= remaining) {
                        val f = if (seg == 0f) 0f else remaining / seg
                        path.lineTo(s[i - 2] + dx * f, s[i - 1] + dy * f)
                        remaining = 0f
                        break
                    }
                    path.lineTo(s[i], s[i + 1])
                    remaining -= seg
                    i += 2
                }
                left -= len
            }
            return path
        }

        /** Where the pen tip is after [dist] units of travel. */
        fun pointAt(dist: Float): Pair<Float, Float> {
            var left = dist
            var last = if (strokes.isNotEmpty() && strokes[0].size >= 2) strokes[0][0] to strokes[0][1] else 0f to 0f
            for ((si, s) in strokes.withIndex()) {
                if (s.size < 2) continue
                last = s[0] to s[1]
                if (left <= 0f) return last
                val len = lengths[si]
                if (left >= len) {
                    last = s[s.size - 2] to s[s.size - 1]
                    left -= len
                    continue
                }
                var i = 2
                while (i < s.size) {
                    val dx = s[i] - s[i - 2]
                    val dy = s[i + 1] - s[i - 1]
                    val seg = hypot(dx, dy)
                    if (seg >= left) {
                        val f = if (seg == 0f) 0f else left / seg
                        return (s[i - 2] + dx * f) to (s[i - 1] + dy * f)
                    }
                    left -= seg
                    i += 2
                }
            }
            return last
        }
    }

    private val cache = HashMap<String, Skel?>()
    private val NONE = Skel(emptyList(), FloatArray(0), 0f, 0f)

    @Synchronized
    fun get(tf: Typeface, key: String, ch: String): Skel? {
        val k = "$key|$ch"
        if (cache.containsKey(k)) return cache[k]
        val s = runCatching { compute(tf, ch) }.getOrNull()
        cache[k] = s
        if (cache.size > 6000) cache.clear()
        return s
    }

    private const val F = 1.6f // raster scale relative to glyph units

    private fun compute(tf: Typeface, ch: String): Skel? {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = 100f }
        val outline = Path()
        paint.getTextPath(ch, 0, ch.length, 0f, 0f, outline)
        val b = RectF()
        outline.computeBounds(b, true)
        if (b.isEmpty && b.width() <= 0f && b.height() <= 0f) return null
        val pad = 3
        val gw = (b.width() * F).toInt() + pad * 2 + 2
        val gh = (b.height() * F).toInt() + pad * 2 + 2
        if (gw < 4 || gh < 4 || gw > 700 || gh > 700) return null
        val bmp = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(pad - b.left * F, pad - b.top * F)
        canvas.scale(F, F)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawPath(outline, fill)
        val px = IntArray(gw * gh)
        bmp.getPixels(px, 0, gw, 0, 0, gw, gh)
        bmp.recycle()
        val g = BooleanArray(gw * gh) { (px[it] ushr 24) > 127 }
        if (g.none { it }) return null

        // thickness: average distance to the edge along the centre line, measured before thinning
        val dist = distance(g, gw, gh)
        thin(g, gw, gh)

        var count = 0
        var distSum = 0f
        for (i in g.indices) if (g[i]) { count++; distSum += dist[i] }
        if (count == 0) return null
        val stem = ((2f * distSum / count) + 1f) / F

        val strokes = trace(g, gw, gh)
        if (strokes.isEmpty()) return null

        val outStrokes = ArrayList<FloatArray>()
        val lens = ArrayList<Float>()
        for (st in strokes) {
            // thin out the pixel chain and convert to glyph units
            val keep = ArrayList<Int>()
            for (i in st.indices) if (i == 0 || i == st.size - 1 || i % 2 == 0) keep.add(st[i])
            val arr = FloatArray(keep.size * 2)
            for ((j, idx) in keep.withIndex()) {
                val x = idx % gw
                val y = idx / gw
                arr[j * 2] = (x + 0.5f - pad) / F + b.left
                arr[j * 2 + 1] = (y + 0.5f - pad) / F + b.top
            }
            var len = 0f
            var i = 2
            while (i < arr.size) { len += hypot(arr[i] - arr[i - 2], arr[i + 1] - arr[i - 1]); i += 2 }
            outStrokes.add(arr)
            lens.add(max(len, 0.5f))
        }
        return Skel(outStrokes, lens.toFloatArray(), lens.sum(), stem)
    }

    private fun distance(g: BooleanArray, w: Int, h: Int): FloatArray {
        val inf = 1e9f
        val d = FloatArray(w * h) { if (g[it]) inf else 0f }
        val s2 = 1.4142f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (d[i] == 0f) continue
            var v = d[i]
            if (x > 0) v = min(v, d[i - 1] + 1f)
            if (y > 0) {
                v = min(v, d[i - w] + 1f)
                if (x > 0) v = min(v, d[i - w - 1] + s2)
                if (x < w - 1) v = min(v, d[i - w + 1] + s2)
            }
            d[i] = v
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (d[i] == 0f) continue
            var v = d[i]
            if (x < w - 1) v = min(v, d[i + 1] + 1f)
            if (y < h - 1) {
                v = min(v, d[i + w] + 1f)
                if (x < w - 1) v = min(v, d[i + w + 1] + s2)
                if (x > 0) v = min(v, d[i + w - 1] + s2)
            }
            d[i] = v
        }
        return d
    }

    /** Zhang-Suen thinning. */
    private fun thin(g: BooleanArray, w: Int, h: Int) {
        fun p(x: Int, y: Int) = if (x in 0 until w && y in 0 until h && g[y * w + x]) 1 else 0
        val remove = ArrayList<Int>()
        var changed = true
        var guard = 0
        while (changed && guard++ < 80) {
            changed = false
            for (step in 0..1) {
                remove.clear()
                for (y in 1 until h - 1) for (x in 1 until w - 1) {
                    if (!g[y * w + x]) continue
                    val p2 = p(x, y - 1); val p3 = p(x + 1, y - 1); val p4 = p(x + 1, y); val p5 = p(x + 1, y + 1)
                    val p6 = p(x, y + 1); val p7 = p(x - 1, y + 1); val p8 = p(x - 1, y); val p9 = p(x - 1, y - 1)
                    val n = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
                    if (n < 2 || n > 6) continue
                    var a = 0
                    if (p2 == 0 && p3 == 1) a++
                    if (p3 == 0 && p4 == 1) a++
                    if (p4 == 0 && p5 == 1) a++
                    if (p5 == 0 && p6 == 1) a++
                    if (p6 == 0 && p7 == 1) a++
                    if (p7 == 0 && p8 == 1) a++
                    if (p8 == 0 && p9 == 1) a++
                    if (p9 == 0 && p2 == 1) a++
                    if (a != 1) continue
                    val ok = if (step == 0) (p2 * p4 * p6 == 0 && p4 * p6 * p8 == 0) else (p2 * p4 * p8 == 0 && p2 * p6 * p8 == 0)
                    if (ok) remove.add(y * w + x)
                }
                if (remove.isNotEmpty()) { changed = true; for (i in remove) g[i] = false }
            }
        }
    }

    private val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    /** Walks the thin line into strokes, in the order a person would write them (top-left first, straight on at crossings). */
    private fun trace(g: BooleanArray, w: Int, h: Int): List<IntArray> {
        val visited = BooleanArray(w * h)
        fun on(x: Int, y: Int) = x in 0 until w && y in 0 until h && g[y * w + x]
        fun degree(x: Int, y: Int): Int {
            var n = 0
            for (k in 0 until 8) if (on(x + DX[k], y + DY[k])) n++
            return n
        }
        var remaining = g.count { it }
        val strokes = ArrayList<IntArray>()
        var lastX = 0
        var lastY = 0
        var first = true

        while (remaining > 0) {
            // choose a start
            var start = -1
            var startViaVisited = -1
            var best = Float.MAX_VALUE
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (!g[i] || visited[i]) continue
                if (degree(x, y) == 1) {
                    val score = if (first) x * 1f + y * 0.6f else hypot((x - lastX).toFloat(), (y - lastY).toFloat()) + x * 0.05f
                    if (score < best) { best = score; start = i }
                }
            }
            if (start < 0) {
                // branches off a junction: start from an unvisited pixel touching a visited one
                best = Float.MAX_VALUE
                for (y in 0 until h) for (x in 0 until w) {
                    val i = y * w + x
                    if (!g[i] || visited[i]) continue
                    var touch = -1
                    for (k in 0 until 8) {
                        val nx = x + DX[k]; val ny = y + DY[k]
                        if (on(nx, ny) && visited[ny * w + nx]) { touch = ny * w + nx; break }
                    }
                    if (touch >= 0) {
                        val score = hypot((x - lastX).toFloat(), (y - lastY).toFloat())
                        if (score < best) { best = score; start = i; startViaVisited = touch }
                    }
                }
            }
            if (start < 0) {
                // closed loop or isolated dot: begin at the top-most, left-most pixel
                loop@ for (y in 0 until h) for (x in 0 until w) {
                    val i = y * w + x
                    if (g[i] && !visited[i]) { start = i; break@loop }
                }
            }
            if (start < 0) break

            val chain = ArrayList<Int>()
            if (startViaVisited >= 0) chain.add(startViaVisited)
            var cur = start
            visited[cur] = true; remaining--
            chain.add(cur)
            var dirX = 0f
            var dirY = 0f
            while (true) {
                val cx = cur % w
                val cy = cur / w
                var bestK = -1
                var bestDot = -2f
                for (k in 0 until 8) {
                    val nx = cx + DX[k]; val ny = cy + DY[k]
                    if (!on(nx, ny) || visited[ny * w + nx]) continue
                    val l = if (DX[k] != 0 && DY[k] != 0) 1.4142f else 1f
                    val dot = if (dirX == 0f && dirY == 0f) (if (DX[k] != 0 && DY[k] != 0) 0f else 0.1f) else (DX[k] * dirX + DY[k] * dirY) / l
                    // prefer orthogonal neighbours when equal, so the chain stays connected
                    val score = dot - (if (l > 1f) 0.001f else 0f)
                    if (score > bestDot) { bestDot = score; bestK = k }
                }
                if (bestK < 0) break
                val nx = cx + DX[bestK]; val ny = cy + DY[bestK]
                val l = sqrt((DX[bestK] * DX[bestK] + DY[bestK] * DY[bestK]).toFloat())
                dirX = 0.6f * dirX + 0.4f * DX[bestK] / l
                dirY = 0.6f * dirY + 0.4f * DY[bestK] / l
                cur = ny * w + nx
                visited[cur] = true; remaining--
                chain.add(cur)
            }
            lastX = cur % w; lastY = cur / w
            first = false
            // tiny side-spurs left by the thinning are not pen strokes: skip them so the pen doesn't jump
            if (startViaVisited >= 0 && chain.size < 7) continue
            strokes.add(chain.toIntArray())
        }
        return strokes
    }
}
