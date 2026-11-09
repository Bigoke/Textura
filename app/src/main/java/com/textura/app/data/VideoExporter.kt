package com.textura.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.textura.app.model.CanvasShape
import com.textura.app.model.Look
import com.textura.app.render.Renderer
import com.textura.app.render.Watermark
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Records the text being written as an MP4 (with optional sound) or an animated GIF. */
object VideoExporter {
    enum class Result { Ok, Failed, NoSound }

    private class Sample(val data: ByteArray, val pts: Long, val flags: Int)
    private class EncodedAudio(val format: MediaFormat, val samples: List<Sample>)

    fun dims(shape: CanvasShape, longEdge: Int, multiple: Int): Pair<Int, Int> {
        val (dw, dh) = Renderer.designSize(shape)
        val scale = longEdge / max(dw, dh)
        val w = max(multiple, ((dw * scale) / multiple).roundToInt() * multiple)
        val h = max(multiple, ((dh * scale) / multiple).roundToInt() * multiple)
        return w to h
    }

    fun export(
        context: Context,
        look: Look,
        text: String,
        shape: CanvasShape,
        opts: VideoOptions,
        signature: Watermark?,
        onProgress: (Int, Int) -> Unit,
        cancelled: () -> Boolean,
    ): Result = if (opts.gif) exportGif(context, look, text, shape, opts, signature, onProgress, cancelled)
    else exportMp4(context, look, text, shape, opts, signature, onProgress, cancelled)

    // ------------------------------------------------------------------------------------------ GIF

    private fun exportGif(
        context: Context, look: Look, text: String, shape: CanvasShape, opts: VideoOptions, signature: Watermark?,
        onProgress: (Int, Int) -> Unit, cancelled: () -> Boolean,
    ): Result {
        val (w, h) = dims(shape, opts.gifEdge, 2)
        val frames = VideoFrames(context, look, text, shape, opts, w, h, signature)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Textura_" + System.currentTimeMillis() + ".gif")
            put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Textura")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return Result.Failed
        var ok = false
        try {
            val fps = opts.gifFps.coerceIn(5, 25)
            val total = max(1, ceil(frames.duration * fps).toInt())
            val px = IntArray(w * h)

            // palette from a spread of moments
            val sample = ArrayList<Int>()
            for (k in 0..9) {
                if (cancelled()) return Result.Failed
                val b = frames.frameAt(frames.duration * k / 9f)
                b.getPixels(px, 0, w, 0, 0, w, h)
                var i = (k * 3) % 7
                while (i < px.size) { sample.add(px[i] and 0xFFFFFF); i += 7 }
            }
            val quant = Quantizer(sample.toIntArray())

            resolver.openOutputStream(uri)?.buffered()?.use { os ->
                val gif = GifWriter(os, w, h, quant.palette)
                val idx = ByteArray(w * h)
                for (f in 0 until total) {
                    if (cancelled()) return Result.Failed
                    onProgress(f, total)
                    val b = frames.frameAt(min(frames.duration, f / fps.toFloat()))
                    b.getPixels(px, 0, w, 0, 0, w, h)
                    quant.map(px, w, h, idx, dither = true)
                    val delay = ((f + 1) * 100f / fps).roundToInt() - (f * 100f / fps).roundToInt()
                    gif.frame(idx, delay)
                }
                gif.finish()
                ok = true
            }
        } catch (e: Exception) {
            ok = false
        } catch (e: OutOfMemoryError) {
            ok = false
        } finally {
            frames.release()
            if (ok) resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            else runCatching { resolver.delete(uri, null, null) }
        }
        return if (ok) Result.Ok else Result.Failed
    }

    // ------------------------------------------------------------------------------------------ MP4

    private fun exportMp4(
        context: Context, look: Look, text: String, shape: CanvasShape, opts: VideoOptions, signature: Watermark?,
        onProgress: (Int, Int) -> Unit, cancelled: () -> Boolean,
    ): Result {
        val (w, h) = dims(shape, min(opts.longEdge, 1440), 16)
        val fps = opts.fps.coerceIn(10, 30)
        val frames = VideoFrames(context, look, text, shape, opts, w, h, signature)
        val total = max(1, ceil(frames.duration * fps).toInt())

        // soundtrack first, so a failure there still gives a silent video
        var soundFailed = false
        var audio: EncodedAudio? = null
        val wantsSound = opts.fx != FxSound.Off || opts.music != MusicChoice.None
        if (wantsSound) {
            audio = runCatching {
                val pcm = SoundBuilder.build(text, frames.mode, opts, frames.writeStart, frames.writeDur, frames.duration)
                pcm?.let { encodeAudio(it) }
            }.getOrNull()
            if (audio == null) soundFailed = true
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Textura_" + System.currentTimeMillis() + ".mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Textura")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return Result.Failed
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var ok = false
        try {
            val pfd = resolver.openFileDescriptor(uri, "w") ?: return Result.Failed
            pfd.use {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_BIT_RATE, (w * h * fps / 6).coerceIn(1_500_000, 14_000_000))
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                codec = c
                c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                c.start()
                val m = MediaMuxer(it.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = m
                var vTrack = -1
                val info = MediaCodec.BufferInfo()

                fun drain(untilEnd: Boolean) {
                    while (true) {
                        val idx = c.dequeueOutputBuffer(info, if (untilEnd) 10_000L else 0L)
                        when {
                            idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEnd) return
                            idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                vTrack = m.addTrack(c.outputFormat)
                                val a = audio
                                val aTrack = if (a != null) m.addTrack(a.format) else -1
                                m.start()
                                muxerStarted = true
                                if (a != null) {
                                    val bi = MediaCodec.BufferInfo()
                                    for (s in a.samples) {
                                        bi.set(0, s.data.size, s.pts, s.flags)
                                        m.writeSampleData(aTrack, ByteBuffer.wrap(s.data), bi)
                                    }
                                }
                            }
                            idx >= 0 -> {
                                val buf = c.getOutputBuffer(idx)
                                if (buf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && muxerStarted) {
                                    buf.position(info.offset)
                                    buf.limit(info.offset + info.size)
                                    m.writeSampleData(vTrack, buf, info)
                                }
                                c.releaseOutputBuffer(idx, false)
                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                            }
                        }
                    }
                }

                val pixels = IntArray(w * h)
                for (f in 0 until total) {
                    if (cancelled()) return Result.Failed
                    onProgress(f, total)
                    val bmp = frames.frameAt(min(frames.duration, f / fps.toFloat()))
                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)

                    var inIdx = c.dequeueInputBuffer(10_000L)
                    while (inIdx < 0) {
                        drain(false)
                        if (cancelled()) return Result.Failed
                        inIdx = c.dequeueInputBuffer(10_000L)
                    }
                    val image = c.getInputImage(inIdx) ?: return Result.Failed
                    fillYuv(image, pixels, w, h)
                    c.queueInputBuffer(inIdx, 0, w * h * 3 / 2, f * 1_000_000L / fps, 0)
                    drain(false)
                }
                var eos = c.dequeueInputBuffer(10_000L)
                while (eos < 0) { drain(false); eos = c.dequeueInputBuffer(10_000L) }
                c.queueInputBuffer(eos, 0, 0, total * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
                ok = muxerStarted
            }
        } catch (e: Exception) {
            ok = false
        } catch (e: OutOfMemoryError) {
            ok = false
        } finally {
            frames.release()
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            if (ok) resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            else runCatching { resolver.delete(uri, null, null) }
        }
        return if (!ok) Result.Failed else if (soundFailed) Result.NoSound else Result.Ok
    }

    // ------------------------------------------------------------------------------------------ audio

    private fun encodeAudio(pcm: ShortArray): EncodedAudio? {
        val rate = SoundBuilder.RATE
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val samples = ArrayList<Sample>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var pos = 0
        var inDone = false
        var outDone = false
        var guard = 0
        try {
            while (!outDone && guard++ < 400000) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(5000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        val n = min(pcm.size - pos, buf.capacity() / 2)
                        val pts = pos * 1_000_000L / rate
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            for (k in 0 until n) buf.putShort(pcm[pos + k])
                            codec.queueInputBuffer(i, 0, n * 2, pts, 0)
                            pos += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outFormat = codec.outputFormat
                } else if (o >= 0) {
                    val b = codec.getOutputBuffer(o)!!
                    if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val bytes = ByteArray(info.size)
                        b.position(info.offset); b.limit(info.offset + info.size)
                        b.get(bytes)
                        samples.add(Sample(bytes, info.presentationTimeUs, 0))
                    }
                    codec.releaseOutputBuffer(o, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        val f = outFormat ?: return null
        return if (samples.isEmpty()) null else EncodedAudio(f, samples)
    }

    private fun fillYuv(image: android.media.Image, px: IntArray, w: Int, h: Int) {
        val yP = image.planes[0]
        val uP = image.planes[1]
        val vP = image.planes[2]
        val yb = yP.buffer
        val ub = uP.buffer
        val vb = vP.buffer
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val p = px[row + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val yy = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yb.put(y * yP.rowStride + x * yP.pixelStride, yy.coerceIn(0, 255).toByte())
                if ((y and 1) == 0 && (x and 1) == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val ci = (y / 2) * uP.rowStride + (x / 2) * uP.pixelStride
                    val cj = (y / 2) * vP.rowStride + (x / 2) * vP.pixelStride
                    ub.put(ci, u.coerceIn(0, 255).toByte())
                    vb.put(cj, v.coerceIn(0, 255).toByte())
                }
            }
        }
    }
}
