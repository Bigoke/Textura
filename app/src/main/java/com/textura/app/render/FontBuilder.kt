package com.textura.app.render

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Builds a real TrueType font from hand-drawn strokes, so the drawn handwriting works everywhere a font does
 * (fit-to-shape, 3D, emboss, foil...). Pure Kotlin with no Android dependencies.
 *
 * Strokes are in "pad" coordinates: x and y in 0..1, y pointing down, with the writing guides at
 * [GUIDE_ASCENDER], [GUIDE_XHEIGHT], [GUIDE_BASELINE] and [GUIDE_DESCENDER].
 */
object FontBuilder {
    const val GUIDE_ASCENDER = 0.12f
    const val GUIDE_XHEIGHT = 0.40f
    const val GUIDE_BASELINE = 0.70f
    const val GUIDE_DESCENDER = 0.92f

    private const val UPM = 1000
    private const val PAD_TO_UNITS = 1200f
    private const val SIDE_BEARING = 70
    private const val SPACE_ADVANCE = 300
    private const val MIN_ADVANCE = 260

    class Glyph(val code: Int, val contours: List<List<Pair<Int, Int>>>, val advance: Int, val lsb: Int) {
        val xMin = contours.minOfOrNull { c -> c.minOf { it.first } } ?: 0
        val yMin = contours.minOfOrNull { c -> c.minOf { it.second } } ?: 0
        val xMax = contours.maxOfOrNull { c -> c.maxOf { it.first } } ?: 0
        val yMax = contours.maxOfOrNull { c -> c.maxOf { it.second } } ?: 0
        val pointCount get() = contours.sumOf { it.size }
    }

    /** [thickness] is the pen width as a fraction of the pad (e.g. 0.05). */
    fun build(
        family: String,
        strokes: Map<Char, List<List<Pair<Float, Float>>>>,
        thickness: Float,
    ): ByteArray {
        val pen = (thickness * PAD_TO_UNITS).coerceIn(30f, 160f)
        val glyphs = ArrayList<Glyph>()
        for ((ch, list) in strokes.entries.sortedBy { it.key.code }) {
            if (ch == ' ' || list.isEmpty()) continue
            makeGlyph(ch.code, list, pen)?.let { glyphs.add(it) }
        }
        glyphs.add(Glyph(' '.code, emptyList(), SPACE_ADVANCE, 0))
        glyphs.sortBy { it.code }
        return writeFont(family, glyphs)
    }

    // ---------------------------------------------------------------------------------------------
    // Strokes to outlines
    // ---------------------------------------------------------------------------------------------

    private fun makeGlyph(code: Int, strokes: List<List<Pair<Float, Float>>>, pen: Float): Glyph? {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        for (s in strokes) for ((x, _) in s) {
            minX = min(minX, x); maxX = max(maxX, x)
        }
        if (minX > maxX) return null

        // pad coordinates to font units, left edge at the side bearing
        fun toUnits(p: Pair<Float, Float>): Pair<Float, Float> =
            ((p.first - minX) * PAD_TO_UNITS + SIDE_BEARING + pen / 2f) to ((GUIDE_BASELINE - p.second) * PAD_TO_UNITS)

        val contours = ArrayList<List<Pair<Int, Int>>>()
        for (stroke in strokes) {
            val pts = simplify(stroke.map(::toUnits), 7f)
            if (pts.isEmpty()) continue
            if (pts.size == 1) {
                contours.add(circle(pts[0], pen / 2f))
                continue
            }
            for (i in 0 until pts.size - 1) {
                val quad = segment(pts[i], pts[i + 1], pen / 2f)
                if (quad != null) contours.add(quad)
            }
            for (p in pts) contours.add(circle(p, pen / 2f))
        }
        if (contours.isEmpty()) return null
        val width = ((maxX - minX) * PAD_TO_UNITS + pen + 2 * SIDE_BEARING).roundToInt()
        return Glyph(code, contours, max(MIN_ADVANCE, width), 0).let {
            // left side bearing equals xMin so the font metrics are consistent
            Glyph(code, contours, it.advance, it.xMin)
        }
    }

    private fun simplify(pts: List<Pair<Float, Float>>, minDist: Float): List<Pair<Float, Float>> {
        if (pts.isEmpty()) return pts
        val out = ArrayList<Pair<Float, Float>>()
        out.add(pts.first())
        for (i in 1 until pts.size) {
            val last = out.last()
            if (hypot(pts[i].first - last.first, pts[i].second - last.second) >= minDist) out.add(pts[i])
        }
        if (out.size == 1 && pts.size > 1) {
            val l = pts.last()
            if (hypot(l.first - out[0].first, l.second - out[0].second) > 1f) out.add(l)
        }
        return out
    }

    /** Clockwise (TrueType outer direction) rectangle around the segment a to b. */
    private fun segment(a: Pair<Float, Float>, b: Pair<Float, Float>, r: Float): List<Pair<Int, Int>>? {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len = hypot(dx, dy)
        if (len < 0.5f) return null
        val nx = -dy / len * r
        val ny = dx / len * r
        val poly = listOf(
            (a.first + nx) to (a.second + ny),
            (b.first + nx) to (b.second + ny),
            (b.first - nx) to (b.second - ny),
            (a.first - nx) to (a.second - ny),
        )
        return clockwise(poly.map { it.first.roundToInt() to it.second.roundToInt() })
    }

    private fun circle(c: Pair<Float, Float>, r: Float): List<Pair<Int, Int>> {
        val n = 12
        val poly = (0 until n).map { i ->
            val a = 2.0 * PI * i / n
            (c.first + r * cos(a)).roundToInt() to (c.second + r * sin(a)).roundToInt()
        }
        return clockwise(poly)
    }

    /** Orders points clockwise for a y-up coordinate system. */
    private fun clockwise(poly: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        var area = 0L
        for (i in poly.indices) {
            val (x1, y1) = poly[i]
            val (x2, y2) = poly[(i + 1) % poly.size]
            area += x1.toLong() * y2 - x2.toLong() * y1
        }
        return if (area > 0) poly.reversed() else poly // positive area = counter-clockwise in y-up, so reverse it
    }

    // ---------------------------------------------------------------------------------------------
    // TrueType file writing
    // ---------------------------------------------------------------------------------------------

    private class Table(val tag: String, val data: ByteArray)

    private fun bytes(block: DataOutputStream.() -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { it.block() }
        return bos.toByteArray()
    }

    private fun DataOutputStream.u16(v: Int) = writeShort(v and 0xFFFF)
    private fun DataOutputStream.i16(v: Int) = writeShort(v)
    private fun DataOutputStream.u32(v: Long) = writeInt((v and 0xFFFFFFFFL).toInt())

    private fun glyphBytes(g: Glyph?): ByteArray {
        if (g == null || g.contours.isEmpty()) return ByteArray(0)
        val raw = bytes {
            i16(g.contours.size)
            i16(g.xMin); i16(g.yMin); i16(g.xMax); i16(g.yMax)
            var end = -1
            for (c in g.contours) { end += c.size; u16(end) }
            u16(0) // no instructions
            // flags: all on-curve, coordinates stored as 16-bit deltas
            repeat(g.pointCount) { writeByte(0x01) }
            var px = 0
            for (c in g.contours) for ((x, _) in c) { i16(x - px); px = x }
            var py = 0
            for (c in g.contours) for ((_, y) in c) { i16(y - py); py = y }
        }
        val pad = (4 - raw.size % 4) % 4
        return raw + ByteArray(pad)
    }

    private fun notdef(): Glyph {
        val outer = clockwise(listOf(50 to 0, 450 to 0, 450 to 700, 50 to 700))
        val inner = clockwise(listOf(100 to 50, 400 to 50, 400 to 650, 100 to 650)).reversed()
        return Glyph(0, listOf(outer, inner), 500, 50)
    }

    private fun writeFont(family: String, userGlyphs: List<Glyph>): ByteArray {
        val all = ArrayList<Glyph>()
        all.add(notdef())
        all.addAll(userGlyphs)
        val n = all.size

        // glyf and loca
        val glyfBos = ByteArrayOutputStream()
        val offsets = IntArray(n + 1)
        for (i in 0 until n) {
            offsets[i] = glyfBos.size()
            glyfBos.write(glyphBytes(all[i]))
        }
        offsets[n] = glyfBos.size()
        val glyf = glyfBos.toByteArray()
        val loca = bytes { for (o in offsets) u32(o.toLong()) }

        val xMin = all.minOf { it.xMin }
        val yMin = all.minOf { it.yMin }
        val xMax = all.maxOf { it.xMax }
        val yMax = all.maxOf { it.yMax }
        val maxAdvance = all.maxOf { it.advance }
        val maxPoints = all.maxOf { it.pointCount }
        val maxContours = all.maxOf { it.contours.size }

        val head = bytes {
            u32(0x00010000); u32(0x00010000)
            u32(0) // checkSumAdjustment, patched later
            u32(0x5F0F3CF5)
            u16(0x0003)
            u16(UPM)
            u32(0); u32(0); u32(0); u32(0) // created, modified
            i16(xMin); i16(yMin); i16(xMax); i16(yMax)
            u16(0) // macStyle
            u16(8) // lowestRecPPEM
            i16(2) // fontDirectionHint
            i16(1) // indexToLocFormat: 32-bit offsets
            i16(0) // glyphDataFormat
        }
        val hhea = bytes {
            u32(0x00010000)
            i16(900); i16(-300); i16(0)
            u16(maxAdvance)
            i16(all.minOf { it.lsb })
            i16(all.minOf { it.advance - it.lsb - (it.xMax - it.xMin) })
            i16(xMax)
            i16(1); i16(0); i16(0)
            repeat(4) { i16(0) }
            i16(0)
            u16(n)
        }
        val maxp = bytes {
            u32(0x00010000)
            u16(n)
            u16(maxPoints); u16(maxContours)
            u16(0); u16(0)
            u16(2); u16(0); u16(0); u16(0); u16(0); u16(0); u16(0); u16(0); u16(0)
        }
        val hmtx = bytes { for (g in all) { u16(g.advance); i16(g.lsb) } }

        val os2 = bytes {
            u16(3)               // version
            i16(500)             // xAvgCharWidth
            u16(400)             // usWeightClass
            u16(5)               // usWidthClass
            u16(0)               // fsType
            i16(650); i16(600); i16(0); i16(75)       // subscript size/offset
            i16(650); i16(600); i16(0); i16(350)      // superscript
            i16(50); i16(300)                          // strikeout
            i16(0)                                     // sFamilyClass
            repeat(10) { writeByte(0) }                // panose
            u32(0x00000001); u32(0); u32(0); u32(0)    // unicode ranges
            writeBytes("TXTR")                         // vendor
            u16(0x0040)                                // fsSelection: regular
            u16(all.drop(1).firstOrNull { it.code > 0 }?.code ?: 0x20)   // first char
            u16(all.last().code.coerceAtMost(0xFFFF))  // last char
            i16(800); i16(-250); i16(0)                // typo metrics
            u16(900); u16(300)                         // win ascent/descent
            u32(1); u32(0)                             // code page ranges
            i16(500); i16(700)                         // x-height, cap height
            u16(0); u16(0x20); u16(0x20)               // default, break char, max context
        }

        // cmap format 4
        val coded = userGlyphs.map { it.code to all.indexOf(it) }.filter { it.first <= 0xFFFF }.sortedBy { it.first }
        val segCount = coded.size + 1
        val pow = Integer.highestOneBit(segCount)
        val cmap = bytes {
            u16(0); u16(1)
            u16(3); u16(1); u32(12)
            u16(4)
            u16(16 + segCount * 8)
            u16(0)
            u16(segCount * 2)
            u16(pow * 2)
            u16(Integer.numberOfTrailingZeros(pow))
            u16(segCount * 2 - pow * 2)
            for ((code, _) in coded) u16(code)
            u16(0xFFFF)
            u16(0)
            for ((code, _) in coded) u16(code)
            u16(0xFFFF)
            for ((code, gi) in coded) u16(gi - code)
            u16(1)
            repeat(segCount) { u16(0) }
        }

        val nameStrings = listOf(
            1 to family, 2 to "Regular", 3 to "$family Regular", 4 to family, 5 to "Version 1.0", 6 to family.replace(" ", "") + "-Regular",
        )
        val nameData = bytes {
            val strings = nameStrings.map { it.second.toByteArray(Charsets.UTF_16BE) }
            u16(0); u16(nameStrings.size); u16(6 + nameStrings.size * 12)
            var off = 0
            for ((i, ns) in nameStrings.withIndex()) {
                u16(3); u16(1); u16(0x409); u16(ns.first); u16(strings[i].size); u16(off)
                off += strings[i].size
            }
            for (s in strings) write(s)
        }
        val post = bytes {
            u32(0x00030000); u32(0)
            i16(-100); i16(50)
            u32(0); u32(0); u32(0); u32(0); u32(0)
        }

        val tables = listOf(
            Table("OS/2", os2), Table("cmap", cmap), Table("glyf", glyf), Table("head", head), Table("hhea", hhea),
            Table("hmtx", hmtx), Table("loca", loca), Table("maxp", maxp), Table("name", nameData), Table("post", post),
        )
        return assemble(tables)
    }

    private fun checksum(data: ByteArray): Long {
        var sum = 0L
        var i = 0
        while (i < data.size) {
            var w = 0L
            for (k in 0 until 4) {
                val b = if (i + k < data.size) data[i + k].toLong() and 0xFF else 0L
                w = (w shl 8) or b
            }
            sum = (sum + w) and 0xFFFFFFFFL
            i += 4
        }
        return sum
    }

    private fun assemble(tables: List<Table>): ByteArray {
        val num = tables.size
        val pow = Integer.highestOneBit(num)
        val dirSize = 12 + 16 * num
        var offset = dirSize
        val offsets = IntArray(num)
        for ((i, t) in tables.withIndex()) {
            offsets[i] = offset
            offset += (t.data.size + 3) / 4 * 4
        }
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        dos.writeInt(0x00010000)
        dos.writeShort(num)
        dos.writeShort(pow * 16)
        dos.writeShort(Integer.numberOfTrailingZeros(pow))
        dos.writeShort(num * 16 - pow * 16)
        for ((i, t) in tables.withIndex()) {
            dos.writeBytes(t.tag.padEnd(4))
            dos.writeInt(checksum(t.data).toInt())
            dos.writeInt(offsets[i])
            dos.writeInt(t.data.size)
        }
        for (t in tables) {
            dos.write(t.data)
            repeat((4 - t.data.size % 4) % 4) { dos.writeByte(0) }
        }
        dos.flush()
        val font = out.toByteArray()
        // patch head.checkSumAdjustment
        val headIdx = tables.indexOfFirst { it.tag == "head" }
        val adj = (0xB1B0AFBAL - checksum(font)) and 0xFFFFFFFFL
        val pos = offsets[headIdx] + 8
        font[pos] = (adj shr 24).toByte()
        font[pos + 1] = (adj shr 16).toByte()
        font[pos + 2] = (adj shr 8).toByte()
        font[pos + 3] = adj.toByte()
        return font
    }

    @Suppress("unused")
    private fun unusedGuard(v: Int) = abs(v)
}
