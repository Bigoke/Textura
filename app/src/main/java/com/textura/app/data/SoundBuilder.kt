package com.textura.app.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.textura.app.render.Markup
import com.textura.app.render.RevealMode
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/** Builds the soundtrack (mono, 16-bit, 44.1 kHz): synthesized writing sounds plus optional music. */
object SoundBuilder {
    const val RATE = 44100

    fun build(
        text: String,
        mode: RevealMode,
        opts: VideoOptions,
        writeStart: Float,
        writeDur: Float,
        total: Float,
    ): ShortArray? {
        val fxKind = when (opts.fx) {
            FxSound.Off -> null
            FxSound.Keys -> FxSound.Keys
            FxSound.Pen -> FxSound.Pen
            FxSound.Paper -> FxSound.Paper
            FxSound.Auto -> when (mode) {
                RevealMode.Typewriter -> FxSound.Keys
                RevealMode.Handwriting -> FxSound.Pen
                else -> FxSound.Paper
            }
        }
        val wantMusic = opts.music != MusicChoice.None
        if (fxKind == null && !wantMusic) return null

        val n = (total * RATE).toInt() + RATE / 2
        val mix = FloatArray(n)
        val rnd = Random(7)

        if (fxKind != null) {
            val fx = FloatArray(n)
            val plain = Markup.strip(text)
            when (fxKind) {
                FxSound.Keys -> keys(fx, plain, writeStart, writeDur, rnd)
                FxSound.Pen -> pen(fx, plain, writeStart, writeDur, rnd, mode)
                else -> rustle(fx, plain, writeStart, writeDur, rnd, mode)
            }
            val g = opts.fxVolume.coerceIn(0f, 1f)
            for (i in 0 until n) mix[i] += fx[i] * g
        }

        if (wantMusic) {
            val music: FloatArray? = when (opts.music) {
                MusicChoice.Ambient -> ambient(n)
                MusicChoice.Plucks -> plucks(n, rnd)
                MusicChoice.Imported -> opts.musicPath?.let { decode(it, n) }
                MusicChoice.None -> null
            }
            if (music != null) {
                val g = opts.musicVolume.coerceIn(0f, 1f)
                val fadeIn = (0.6f * RATE).toInt()
                val fadeOut = (min(2f, total * 0.4f) * RATE).toInt().coerceAtLeast(1)
                val end = (total * RATE).toInt().coerceAtMost(n)
                for (i in 0 until end) {
                    var e = 1f
                    if (i < fadeIn) e = i / fadeIn.toFloat()
                    if (i > end - fadeOut) e = min(e, (end - i) / fadeOut.toFloat())
                    mix[i] += music[i] * g * e
                }
            }
        }

        val out = ShortArray(n)
        var peak = 0f
        for (v in mix) peak = max(peak, abs(v))
        if (peak < 1e-4f) return null
        for (i in 0 until n) out[i] = (tanh(mix[i].toDouble() * 1.1) * 30000).toInt().toShort()
        return out
    }

    // ------------------------------------------------------------------------------------------ fx

    private fun add(buf: FloatArray, at: Int, sample: FloatArray, gain: Float) {
        for (i in sample.indices) {
            val k = at + i
            if (k < 0 || k >= buf.size) break
            buf[k] += sample[i] * gain
        }
    }

    private fun click(rnd: Random, thump: Float, pitch: Float): FloatArray {
        val len = (0.045f * RATE).toInt()
        val s = FloatArray(len)
        var prev = 0f
        for (i in s.indices) {
            val t = i / RATE.toFloat()
            val noise = rnd.nextFloat() * 2f - 1f
            val hp = noise - prev * 0.9f
            prev = noise
            val tick = hp * exp(-t * 380f) * 0.55f
            val body = sin(2.0 * PI * pitch * t).toFloat() * exp(-t * 90f) * thump
            s[i] = tick + body
        }
        return s
    }

    private fun bell(): FloatArray {
        val len = (0.55f * RATE).toInt()
        return FloatArray(len) { i ->
            val t = i / RATE.toFloat()
            ((sin(2.0 * PI * 2350 * t) + 0.4 * sin(2.0 * PI * 3520 * t)) * exp(-t * 7.0) * 0.25).toFloat()
        }
    }

    private fun keys(buf: FloatArray, text: String, start: Float, dur: Float, rnd: Random) {
        val chars = text.count { !it.isWhitespace() }.coerceAtLeast(1)
        var seen = 0
        val bellSample = bell()
        for (ch in text) {
            if (ch == '\n') {
                add(buf, ((start + dur * seen / chars) * RATE).toInt(), bellSample, 0.5f)
                continue
            }
            if (ch.isWhitespace()) continue
            val t = start + dur * seen / chars
            val c = click(rnd, 0.5f + rnd.nextFloat() * 0.25f, 110f + rnd.nextFloat() * 50f)
            add(buf, (t * RATE).toInt(), c, 0.55f + rnd.nextFloat() * 0.3f)
            seen++
        }
    }

    private fun pen(buf: FloatArray, text: String, start: Float, dur: Float, rnd: Random, mode: RevealMode) {
        val words = Regex("\\S+").findAll(text).map { it.value.length }.toList()
        if (words.isEmpty()) return
        val weights = words.map { it + 0.5f }
        val sum = weights.sum()
        var t = start
        var prev = 0f
        var lp = 0f
        for ((wi, wl) in words.withIndex()) {
            val d = dur * weights[wi] / sum
            val a = (t * RATE).toInt()
            val b = ((t + d * 0.88f) * RATE).toInt()
            val wob = 5f + rnd.nextFloat() * 4f
            val ph = rnd.nextFloat() * 6f
            for (i in a until min(b, buf.size)) {
                val u = (i - a) / max(1, b - a).toFloat()
                val hump = sin(PI * u).toFloat().pow(0.6f)
                val stroke = 0.45f + 0.55f * abs(sin(2.0 * PI * wob * (i - a) / RATE + ph)).toFloat()
                val noise = rnd.nextFloat() * 2f - 1f
                lp += (noise - lp) * 0.55f
                val hp = lp - prev * 0.8f
                prev = lp
                buf[i] += hp * hump * stroke * 0.30f * min(1f, 0.5f + wl / 6f)
            }
            t += d
        }
    }

    private fun rustle(buf: FloatArray, text: String, start: Float, dur: Float, rnd: Random, mode: RevealMode) {
        if (mode == RevealMode.Sweep) {
            var lp = 0f
            val a = (start * RATE).toInt()
            val b = ((start + dur) * RATE).toInt()
            for (i in a until min(b, buf.size)) {
                val u = (i - a) / max(1, b - a).toFloat()
                lp += ((rnd.nextFloat() * 2f - 1f) - lp) * 0.08f
                buf[i] += lp * sin(PI * u).toFloat() * 0.5f
            }
            return
        }
        val units = if (mode == RevealMode.Lines) text.lines().filter { it.isNotBlank() }.size
        else Regex("\\S+").findAll(text).count()
        if (units == 0) return
        for (k in 0 until units) {
            val t = start + dur * k / units
            val len = (0.09f * RATE).toInt()
            var lp = 0f
            val s = FloatArray(len) { i ->
                val u = i / len.toFloat()
                lp += ((rnd.nextFloat() * 2f - 1f) - lp) * 0.2f
                lp * sin(PI * u).toFloat() * 0.6f
            }
            add(buf, (t * RATE).toInt(), s, 0.8f)
        }
    }

    // ------------------------------------------------------------------------------------------ music

    private fun hz(semitonesFromA4: Int) = 440.0 * 2.0.pow(semitonesFromA4 / 12.0)

    private fun ambient(n: Int): FloatArray {
        val out = FloatArray(n)
        // Am - F - C - G, a few seconds each
        val chords = listOf(
            intArrayOf(-24, -17, -12, -9),
            intArrayOf(-28, -21, -16, -12),
            intArrayOf(-21, -14, -9, -5),
            intArrayOf(-26, -19, -14, -10),
        )
        val len = (5.5f * RATE).toInt()
        var pos = 0
        var idx = 0
        while (pos < n) {
            val ch = chords[idx % chords.size]
            for (semi in ch) {
                val f = hz(semi)
                for (detune in doubleArrayOf(0.997, 1.003)) {
                    for (i in 0 until len + RATE) {
                        val k = pos + i
                        if (k >= n) break
                        val t = i / RATE.toDouble()
                        val env = (min(1.0, t / 1.3) * exp(-max(0.0, t - 4.5) * 1.1)).toFloat()
                        out[k] += (sin(2.0 * PI * f * detune * t) * 0.5 + sin(4.0 * PI * f * detune * t) * 0.12).toFloat() * env * 0.05f
                    }
                }
            }
            pos += len
            idx++
        }
        return reverb(out)
    }

    private fun plucks(n: Int, rnd: Random): FloatArray {
        val out = FloatArray(n)
        val scale = intArrayOf(-21, -19, -17, -14, -12, -9, -7, -5, -2, 0, 3)
        var t = 0.2f
        while (t * RATE < n) {
            val f = hz(scale[rnd.nextInt(scale.size)])
            val a = (t * RATE).toInt()
            val len = (1.6f * RATE).toInt()
            for (i in 0 until len) {
                val k = a + i
                if (k >= n) break
                val tt = i / RATE.toDouble()
                val v = (sin(2.0 * PI * f * tt) + 0.35 * sin(4.0 * PI * f * tt) + 0.12 * sin(6.0 * PI * f * tt)) * exp(-tt * 3.2)
                out[k] += (v * 0.12).toFloat()
            }
            t += if (rnd.nextFloat() < 0.3f) 0.25f else 0.5f
        }
        return reverb(out)
    }

    private fun reverb(src: FloatArray): FloatArray {
        val out = src.copyOf()
        val delays = intArrayOf((0.113f * RATE).toInt(), (0.197f * RATE).toInt(), (0.283f * RATE).toInt())
        val gains = floatArrayOf(0.35f, 0.25f, 0.18f)
        for ((d, g) in delays.zip(gains.toList())) {
            for (i in d until out.size) out[i] += src[i - d] * g
        }
        return out
    }

    // ------------------------------------------------------------------------------------------ import

    /** Decodes any audio file Android supports to mono 44.1 kHz floats, looped or cut to [n] samples. */
    private fun decode(path: String, n: Int): FloatArray? = runCatching {
        val ex = MediaExtractor()
        ex.setDataSource(File(path).absolutePath)
        var track = -1
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
        }
        if (track < 0) { ex.release(); return null }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val pcm = ArrayList<FloatArray>()
        var pcmSamples = 0
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var outDone = false
        val needed = (n.toDouble() / RATE * rate).toInt() + rate
        var guard = 0
        while (!outDone && pcmSamples < needed && guard++ < 200000) {
            if (!inDone) {
                val i = codec.dequeueInputBuffer(5000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!
                    val sz = ex.readSampleData(buf, 0)
                    if (sz < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inDone = true
                    } else {
                        codec.queueInputBuffer(i, 0, sz, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 5000)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val of = codec.outputFormat
                rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else if (o >= 0) {
                val b = codec.getOutputBuffer(o)!!
                b.position(info.offset); b.limit(info.offset + info.size)
                val sb = b.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val frames = sb.remaining() / max(1, channels)
                val mono = FloatArray(frames)
                for (f in 0 until frames) {
                    var acc = 0f
                    for (c in 0 until channels) acc += sb.get() / 32768f
                    mono[f] = acc / channels
                }
                pcm.add(mono); pcmSamples += frames
                codec.releaseOutputBuffer(o, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
            }
        }
        codec.stop(); codec.release(); ex.release()
        if (pcmSamples == 0) return null
        val all = FloatArray(pcmSamples)
        var p = 0
        for (c in pcm) { System.arraycopy(c, 0, all, p, c.size); p += c.size }
        val out = FloatArray(n)
        val ratio = rate.toDouble() / RATE
        for (i in 0 until n) {
            val src = i * ratio
            val i0 = src.toInt() % pcmSamples // loops if the file is shorter than the video
            val i1 = (i0 + 1) % pcmSamples
            val fr = (src - src.toInt()).toFloat()
            out[i] = all[i0] * (1 - fr) + all[i1] * fr
        }
        out
    }.getOrNull()
}
