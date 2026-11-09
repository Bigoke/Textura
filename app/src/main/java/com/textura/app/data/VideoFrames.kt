package com.textura.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.textura.app.model.CanvasShape
import com.textura.app.model.FontGroup
import com.textura.app.model.Fonts
import com.textura.app.model.Look
import com.textura.app.render.Markup
import com.textura.app.render.RevealMode
import com.textura.app.render.Renderer
import com.textura.app.render.Watermark
import kotlin.math.max
import kotlin.math.min

/**
 * The timeline of an animated export (intro, writing, hold, outro, loop fade) and a way to get the picture at any
 * moment. Shared by MP4, GIF and the live preview.
 */
class VideoFrames(
    private val context: Context,
    private val look: Look,
    private val text: String,
    private val shape: CanvasShape,
    private val opts: VideoOptions,
    val width: Int,
    val height: Int,
    private val signature: Watermark?,
    /** When true, returned bitmaps are never recycled (needed when they are shown on screen). */
    private val keep: Boolean = false,
) {
    val mode: RevealMode = resolveMode(look, opts.style)
    val introDur = if (opts.intro) opts.introSec else 0f
    val writeDur = if (opts.seconds > 0f) opts.seconds else (Markup.strip(text).length / 22f).coerceIn(3f, 9f)
    val holdDur = opts.hold.coerceAtLeast(0f)
    val outroDur = if (opts.outro) opts.outroSec else 0f
    private val fadeOut = if (opts.loop) 0.5f else 0f
    val duration = introDur + writeDur + holdDur + outroDur + fadeOut
    val writeStart = introDur

    private val size = width to height
    private val cached = ArrayList<Bitmap>()
    private var temp: Bitmap? = null

    private val blank: Bitmap by lazy { own(render("", 0f)) }
    private val finalBmp: Bitmap by lazy { own(render(text, 1f)) }
    private val introCard: Bitmap by lazy { own(render(cardText(opts.introText, "Title"), 1f)) }
    private val outroCard: Bitmap by lazy {
        own(render(cardText(opts.outroText.ifBlank { signature?.text ?: "" }, "The end"), 1f))
    }

    private fun cardText(t: String, fallback: String) = t.ifBlank { fallback }

    private fun own(b: Bitmap): Bitmap { cached.add(b); return b }

    private fun render(t: String, reveal: Float): Bitmap =
        Renderer.render(
            context, look, t, shape, width, false, reveal,
            signature, size,
            mode, opts.pen && reveal < 1f,
        )

    /** The picture at time [t] seconds. Valid until the next call (or [release]). */
    fun frameAt(t: Float): Bitmap {
        val out = build(t.coerceIn(0f, duration))
        if (!keep && temp != null && temp !== out && temp !in cached) temp?.recycle()
        temp = if (out in cached) null else out
        return out
    }

    private fun build(t: Float): Bitmap {
        var x = t
        if (introDur > 0f) {
            if (x < introDur) {
                val edge = min(0.35f, introDur * 0.3f)
                val a = min(1f, min(x / edge, (introDur - x) / edge)).coerceIn(0f, 1f)
                return if (a >= 0.99f) introCard else mix(blank, introCard, a)
            }
            x -= introDur
        }
        if (x < writeDur) {
            val f = (x / writeDur).coerceIn(0f, 1f)
            return if (f <= 0f && !opts.pen) blank else render(text, f)
        }
        x -= writeDur
        if (x < holdDur) return finalBmp
        x -= holdDur
        if (outroDur > 0f) {
            if (x < outroDur) {
                val a = min(1f, x / min(0.5f, outroDur * 0.4f)).coerceIn(0f, 1f)
                return if (a >= 0.99f) outroCard else mix(finalBmp, outroCard, a)
            }
            x -= outroDur
        }
        if (fadeOut > 0f) {
            val last = if (outroDur > 0f) outroCard else finalBmp
            val a = (1f - x / fadeOut).coerceIn(0f, 1f)
            return if (a >= 0.99f) last else mix(blank, last, a)
        }
        return if (outroDur > 0f) outroCard else finalBmp
    }

    private fun mix(base: Bitmap, top: Bitmap, a: Float): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val p = Paint().apply { alpha = (a * 255).toInt().coerceIn(0, 255) }
        Canvas(out).drawBitmap(top, 0f, 0f, p)
        return out
    }

    fun release() {
        if (!keep) {
            temp?.takeIf { it !in cached }?.recycle()
            cached.forEach { runCatching { it.recycle() } }
        }
        temp = null
        cached.clear()
    }

    companion object {
        fun resolveMode(look: Look, style: VideoStyle): RevealMode = when (style) {
            VideoStyle.Typewriter -> RevealMode.Typewriter
            VideoStyle.Handwriting -> RevealMode.Handwriting
            VideoStyle.Words -> RevealMode.Words
            VideoStyle.Lines -> RevealMode.Lines
            VideoStyle.Sweep -> RevealMode.Sweep
            VideoStyle.Auto -> when {
                look.handwritten -> RevealMode.Handwriting
                Fonts.byId(look.fontId).group == FontGroup.Typed -> RevealMode.Typewriter
                Fonts.byId(look.fontId).group == FontGroup.Handwritten || Fonts.byId(look.fontId).group == FontGroup.Script -> RevealMode.Handwriting
                else -> RevealMode.Words
            }
        }
    }
}
