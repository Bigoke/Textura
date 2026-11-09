package com.textura.app.data

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Minimal GIF89a writer: one global palette, frames stored as changed rectangles, identical frames merged. */
class GifWriter(private val out: OutputStream, private val w: Int, private val h: Int, palette: IntArray) {
    private var prev: ByteArray? = null
    private var pending: ByteArray? = null // encoded image block of the frame waiting for its delay
    private var pendingDelay = 0

    init {
        val b = ByteArrayOutputStream()
        b.write("GIF89a".toByteArray())
        b.write(le(w)); b.write(le(h))
        b.write(0xF7); b.write(0); b.write(0)
        for (i in 0 until 256) {
            val c = palette.getOrElse(i) { 0 }
            b.write((c shr 16) and 0xFF); b.write((c shr 8) and 0xFF); b.write(c and 0xFF)
        }
        // loop forever
        b.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        b.write("NETSCAPE2.0".toByteArray())
        b.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
        out.write(b.toByteArray())
    }

    private fun le(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    /** [idx] is w*h palette indices; [delayCs] is how long this frame stays, in 1/100 s. */
    fun frame(idx: ByteArray, delayCs: Int) {
        val p = prev
        var x0 = 0; var y0 = 0; var x1 = w - 1; var y1 = h - 1
        if (p != null) {
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if (idx[row + x] != p[row + x]) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < 0) { pendingDelay += delayCs; return } // identical to the last frame
            x0 = minX; y0 = minY; x1 = maxX; y1 = maxY
        }
        flush()
        val rw = x1 - x0 + 1
        val rh = y1 - y0 + 1
        val sub = ByteArray(rw * rh)
        for (y in 0 until rh) System.arraycopy(idx, (y0 + y) * w + x0, sub, y * rw, rw)
        val blk = ByteArrayOutputStream()
        blk.write(0x2C)
        blk.write(le(x0)); blk.write(le(y0)); blk.write(le(rw)); blk.write(le(rh))
        blk.write(0)
        blk.write(8)
        val data = lzw(sub)
        var pos = 0
        while (pos < data.size) {
            val n = minOf(255, data.size - pos)
            blk.write(n); blk.write(data, pos, n); pos += n
        }
        blk.write(0)
        pending = blk.toByteArray()
        pendingDelay = delayCs
        prev = idx.copyOf()
    }

    private fun flush() {
        val blk = pending ?: return
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04))
        out.write(le(pendingDelay.coerceAtLeast(2)))
        out.write(byteArrayOf(0x00, 0x00))
        out.write(blk)
        pending = null
    }

    fun finish() {
        flush()
        out.write(0x3B)
        out.flush()
    }

    // ---- LZW, same scheme as the classic GIF encoders (clear = 256, end = 257, 9..12 bit codes)
    private fun lzw(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        var bitBuf = 0
        var bitCnt = 0
        var nBits = 9
        var maxCode = 511
        var free = 258
        var clearFlg = false
        val dict = HashMap<Int, Int>(8192)

        fun output(code: Int) {
            bitBuf = bitBuf or (code shl bitCnt)
            bitCnt += nBits
            while (bitCnt >= 8) { bos.write(bitBuf and 0xFF); bitBuf = bitBuf ushr 8; bitCnt -= 8 }
            if (free > maxCode || clearFlg) {
                if (clearFlg) { nBits = 9; maxCode = 511; clearFlg = false }
                else { nBits++; maxCode = if (nBits == 12) 4096 else (1 shl nBits) - 1 }
            }
        }
        fun clearBlock() { dict.clear(); free = 258; clearFlg = true; output(256) }

        output(256)
        var ent = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val c = data[i].toInt() and 0xFF
            val key = (ent shl 8) or c
            val found = dict[key]
            if (found != null) { ent = found; continue }
            output(ent)
            ent = c
            if (free < 4096) { dict[key] = free++ } else clearBlock()
        }
        output(ent)
        output(257)
        if (bitCnt > 0) bos.write(bitBuf and 0xFF)
        return bos.toByteArray()
    }
}

/** Picks a 256-colour palette (median cut) and maps pixels to it with a small ordered dither. */
class Quantizer(samples: IntArray) {
    val palette: IntArray = medianCut(samples)
    private val lut = IntArray(32768) { -1 }

    private fun medianCut(src: IntArray): IntArray {
        class Box(val px: IntArray)
        val boxes = ArrayList<Box>()
        boxes.add(Box(src.copyOf()))
        fun range(b: Box, shift: Int): Int {
            var lo = 255; var hi = 0
            for (c in b.px) { val v = (c shr shift) and 0xFF; if (v < lo) lo = v; if (v > hi) hi = v }
            return hi - lo
        }
        while (boxes.size < 256) {
            var best = -1; var bestRange = 0; var bestShift = 0
            for ((i, b) in boxes.withIndex()) {
                if (b.px.size < 2) continue
                for (sh in intArrayOf(16, 8, 0)) {
                    val r = range(b, sh)
                    if (r > bestRange) { bestRange = r; best = i; bestShift = sh }
                }
            }
            if (best < 0 || bestRange == 0) break
            val b = boxes.removeAt(best)
            val sorted = b.px.sortedBy { (it shr bestShift) and 0xFF }.toIntArray()
            val mid = sorted.size / 2
            boxes.add(Box(sorted.copyOfRange(0, mid)))
            boxes.add(Box(sorted.copyOfRange(mid, sorted.size)))
        }
        val pal = IntArray(256)
        for ((i, b) in boxes.withIndex()) {
            var r = 0L; var g = 0L; var bl = 0L
            for (c in b.px) { r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF }
            val n = maxOf(1, b.px.size)
            pal[i] = (((r / n).toInt()) shl 16) or (((g / n).toInt()) shl 8) or (bl / n).toInt()
        }
        return pal
    }

    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun map(pixels: IntArray, w: Int, h: Int, out: ByteArray, dither: Boolean) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                var r = (p shr 16) and 0xFF
                var g = (p shr 8) and 0xFF
                var b = p and 0xFF
                if (dither) {
                    val o = (bayer[(y and 3) * 4 + (x and 3)] - 8) * 2
                    r = (r + o).coerceIn(0, 255); g = (g + o).coerceIn(0, 255); b = (b + o).coerceIn(0, 255)
                }
                val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                var idx = lut[key]
                if (idx < 0) {
                    val rr = (r and 0xF8) + 4; val gg = (g and 0xF8) + 4; val bb = (b and 0xF8) + 4
                    var bestD = Int.MAX_VALUE
                    idx = 0
                    for (i in palette.indices) {
                        val c = palette[i]
                        val dr = ((c shr 16) and 0xFF) - rr; val dg = ((c shr 8) and 0xFF) - gg; val db = (c and 0xFF) - bb
                        val d = dr * dr + dg * dg + db * db
                        if (d < bestD) { bestD = d; idx = i }
                    }
                    lut[key] = idx
                }
                out[y * w + x] = idx.toByte()
            }
        }
    }
}
