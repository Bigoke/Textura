package com.textura.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Records the text being typed out as an H.264 MP4 saved to Movies/Textura. */
object VideoExporter {
    private const val FPS = 20
    private const val HOLD_SECONDS = 2

    fun export(
        context: Context,
        look: Look,
        text: String,
        shape: CanvasShape,
        longEdge: Int,
        signature: Watermark?,
        onProgress: (Int, Int) -> Unit,
        cancelled: () -> Boolean,
    ): Boolean {
        val (dw, dh) = Renderer.designSize(shape)
        val scale = min(longEdge, 1440) / max(dw, dh)
        val w = max(16, ((dw * scale) / 16f).roundToInt() * 16)
        val h = max(16, ((dh * scale) / 16f).roundToInt() * 16)

        val seconds = (text.length / 22f).coerceIn(3f, 9f)
        val typingFrames = (seconds * FPS).toInt()
        val total = typingFrames + HOLD_SECONDS * FPS

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Textura_" + System.currentTimeMillis() + ".mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Textura")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var ok = false
        try {
            val pfd = resolver.openFileDescriptor(uri, "w") ?: return false
            pfd.use {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_BIT_RATE, (w * h * 4).coerceAtMost(12_000_000))
                    setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                codec = c
                c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                c.start()
                val m = MediaMuxer(it.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = m
                var track = -1
                val info = MediaCodec.BufferInfo()

                fun drain(untilEnd: Boolean) {
                    while (true) {
                        val idx = c.dequeueOutputBuffer(info, if (untilEnd) 10_000L else 0L)
                        when {
                            idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEnd) return
                            idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                track = m.addTrack(c.outputFormat)
                                m.start()
                                muxerStarted = true
                            }
                            idx >= 0 -> {
                                val buf = c.getOutputBuffer(idx)
                                if (buf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && muxerStarted) {
                                    buf.position(info.offset)
                                    buf.limit(info.offset + info.size)
                                    m.writeSampleData(track, buf, info)
                                }
                                c.releaseOutputBuffer(idx, false)
                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                            }
                        }
                    }
                }

                val pixels = IntArray(w * h)
                for (f in 0 until total) {
                    if (cancelled()) return false
                    onProgress(f, total)
                    val reveal = if (f >= typingFrames) 1f else f.toFloat() / typingFrames
                    val bmp = Renderer.render(context, look, text, shape, w, false, reveal, signature, w to h)
                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                    bmp.recycle()

                    var inIdx = c.dequeueInputBuffer(10_000L)
                    while (inIdx < 0) {
                        drain(false)
                        if (cancelled()) return false
                        inIdx = c.dequeueInputBuffer(10_000L)
                    }
                    val image = c.getInputImage(inIdx) ?: return false
                    fillYuv(image, pixels, w, h)
                    c.queueInputBuffer(inIdx, 0, w * h * 3 / 2, f * 1_000_000L / FPS, 0)
                    drain(false)
                }
                var eos = c.dequeueInputBuffer(10_000L)
                while (eos < 0) { drain(false); eos = c.dequeueInputBuffer(10_000L) }
                c.queueInputBuffer(eos, 0, 0, total * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
                ok = muxerStarted
            }
        } catch (e: Exception) {
            ok = false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            if (ok) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            } else {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        return ok
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
