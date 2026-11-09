package com.textura.app.render

import kotlin.math.abs

/** Shared timing model, so the picture and the sound follow exactly the same rhythm. */
object Timing {
    /** How long a character takes to write by hand, relative to an average letter. */
    fun charWeight(c: Char): Float = when (c) {
        'i', 'l', 'j', 't', 'f', 'r', '.', ',', '\'', ':', ';', '!', '|' -> 0.6f
        'm', 'w', 'M', 'W', '@', '&' -> 1.5f
        else -> if (c.isUpperCase()) 1.25f else 1f
    }

    fun wordWeight(s: String): Float = s.sumOf { charWeight(it).toDouble() }.toFloat().coerceAtLeast(0.5f) + 0.55f

    /** Extra time spent moving the pen to the start of a new line. */
    const val LINE_TRAVEL = 1.8f

    /**
     * Typing schedule: for every non-space character, the moment (0..1 of the writing time) it appears.
     * Has a human rhythm: small random variation, short pauses at spaces, longer at punctuation and new lines.
     */
    fun typeSchedule(plain: String): FloatArray {
        val times = ArrayList<Float>()
        var t = 0f
        var pause = 0f
        var seed = 12345
        var prevPunct = false
        for (ch in plain) {
            if (ch.isWhitespace()) {
                pause += if (ch == '\n') 3.2f else 0.7f
                continue
            }
            seed = seed * 1103515245 + 12345
            val r = ((seed ushr 16) and 0x7FFF) / 32767f
            val interval = 0.65f + r * 0.85f
            t += interval + pause + (if (prevPunct) 1.1f else 0f)
            pause = 0f
            prevPunct = ch in ".,!?;:"
            times.add(t)
        }
        if (times.isEmpty()) return FloatArray(0)
        val end = times.last()
        return FloatArray(times.size) { times[it] / end }
    }

    /** How many of [schedule]'s characters have appeared by progress [f]. */
    fun typedCount(schedule: FloatArray, f: Float): Int {
        var lo = 0
        var hi = schedule.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (schedule[mid] <= f) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun easeOutCubic(t: Float): Float { val u = 1f - t.coerceIn(0f, 1f); return 1f - u * u * u }

    fun easeOutBack(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = x - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    @Suppress("unused")
    fun near(a: Float, b: Float) = abs(a - b) < 1e-4f
}
