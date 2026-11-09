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
    private val texts: List<String>,
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
    val holdDur = opts.hold.coerceAtLeast(0f)
    val outroDur = if (opts.outro) opts.outroSec else 0f
    private val fadeOut = if (opts.loop) 0.5f else 0f
    private val pageGap = 0.4f

    class Seg(val text: String, val start: Float, val dur: Float)

    /** Where each page is written on the timeline (used for the sound). */
    val segments: List<Seg>
    val duration: Float

    init {
        val segs = ArrayList<Seg>()
        var t = introDur
        for ((i, tx) in texts.withIndex()) {
            val wd = if (opts.seconds > 0f) opts.seconds else autoWrite(tx, mode)
            segs.add(Seg(tx, t, wd))
            t += wd + holdDur
            if (i < texts.size - 1) t += pageGap
        }
        segments = segs
        duration = t + outroDur + fadeOut
    }

    private val cached = ArrayList<Bitmap>()
    private var temp: Bitmap? = null
    private var work: Bitmap? = null

    /** One page: its prepared scene and its first and last pictures, rebuilt on demand and freed when passed. */
    private inner class Page(val text: String) {
        private var scene: Renderer.Scene? = null
        private var blankB: Bitmap? = null
        private var finalB: Bitmap? = null

        fun scene(): Renderer.Scene = scene ?: Renderer.Scene(context, look, text, shape, width, height, signature).also { scene = it }
        fun blank(): Bitmap = blankB ?: own(scene().frame(0f, mode, false)).also { blankB = it }
        fun final(): Bitmap = finalB ?: own(scene().frame(1f, mode, false)).also { finalB = it }

        fun free() {
            if (keep) return
            for (b in listOf(blankB, finalB)) if (b != null) { cached.remove(b); if (b !== temp) runCatching { b.recycle() } }
            blankB = null; finalB = null
            runCatching { scene?.release() }
            scene = null
        }
    }

    private val pages = texts.map { Page(it) }
    private val introCard: Bitmap by lazy { own(card(opts.introText.ifBlank { "Title" })) }
    private val outroCard: Bitmap by lazy { own(card(opts.outroText.ifBlank { signature?.text ?: "" }.ifBlank { "The end" })) }

    private fun card(t: String): Bitmap =
        Renderer.render(context, look, t, shape, width, false, 1f, signature, width to height, mode, false)

    private fun own(b: Bitmap): Bitmap { cached.add(b); return b }

    /** The picture at time [t] seconds. Valid until the next call (or [release]). */
    fun frameAt(t: Float): Bitmap {
        val out = build(t.coerceIn(0f, duration))
        if (!keep && temp != null && temp !== out && temp !in cached && temp !== work) temp?.recycle()
        temp = if (out in cached || out === work) null else out
        return out
    }

    private fun build(t: Float): Bitmap {
        var x = t
        if (introDur > 0f) {
            if (x < introDur) {
                val edge = min(0.35f, introDur * 0.3f)
                val a = min(1f, min(x / edge, (introDur - x) / edge)).coerceIn(0f, 1f)
                return if (a >= 0.99f) introCard else mix(pages[0].blank(), introCard, a)
            }
            x -= introDur
        }
        for ((i, pg) in pages.withIndex()) {
            val wd = segments[i].dur
            if (x < wd) {
                if (!keep) for (j in 0 until i) pages[j].free()
                val f = (x / wd).coerceIn(0f, 1f)
                if (f <= 0f && !opts.pen) return pg.blank()
                if (keep) return pg.scene().frame(f, mode, opts.pen)
                val w = work ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { work = it }
                return pg.scene().frame(f, mode, opts.pen, w)
            }
            x -= wd
            if (x < holdDur) return pg.final()
            x -= holdDur
            if (i < pages.size - 1) {
                if (x < pageGap) return mix(pg.final(), pages[i + 1].blank(), x / pageGap)
                x -= pageGap
            }
        }
        val last = pages.last()
        if (outroDur > 0f) {
            if (x < outroDur) {
                val a = min(1f, x / min(0.5f, outroDur * 0.4f)).coerceIn(0f, 1f)
                return if (a >= 0.99f) outroCard else mix(last.final(), outroCard, a)
            }
            x -= outroDur
        }
        if (fadeOut > 0f) {
            val end = if (outroDur > 0f) outroCard else last.final()
            val a = (1f - x / fadeOut).coerceIn(0f, 1f)
            return if (a >= 0.99f) end else mix(pages[0].blank(), end, a)
        }
        return if (outroDur > 0f) outroCard else last.final()
    }

    private fun mix(base: Bitmap, top: Bitmap, a: Float): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val p = Paint().apply { alpha = (a * 255).toInt().coerceIn(0, 255) }
        Canvas(out).drawBitmap(top, 0f, 0f, p)
        return out
    }

    fun release() {
        if (!keep) {
            temp?.takeIf { it !in cached && it !== work }?.recycle()
            work?.recycle()
            pages.forEach { it.free() }
            cached.toList().forEach { runCatching { it.recycle() } }
        }
        work = null
        temp = null
        cached.clear()
    }

    companion object {
        /** Natural writing time when the user leaves it on Auto. */
        fun autoWrite(text: String, mode: RevealMode): Float {
            val n = Markup.strip(text).length.toFloat()
            return when (mode) {
                RevealMode.Handwriting -> (n / 12f).coerceIn(3.5f, 18f)
                RevealMode.Typewriter -> (n / 18f).coerceIn(3f, 14f)
                else -> (n / 24f).coerceIn(2.5f, 8f)
            }
        }

        fun resolveMode(look: Look, style: VideoStyle): RevealMode = when (style) {
            VideoStyle.Typewriter -> RevealMode.Typewriter
            VideoStyle.Handwriting -> RevealMode.Handwriting
            VideoStyle.Words -> RevealMode.Words
            VideoStyle.Lines -> RevealMode.Lines
            VideoStyle.Sweep -> RevealMode.Sweep
            VideoStyle.LineWipe -> RevealMode.LineWipe
            VideoStyle.Pop -> RevealMode.Pop
            VideoStyle.Scatter -> RevealMode.Scatter
            VideoStyle.Highlight -> RevealMode.Highlight
            VideoStyle.Fade -> RevealMode.Fade
            VideoStyle.Auto -> when {
                look.handwritten -> RevealMode.Handwriting
                Fonts.byId(look.fontId).group == FontGroup.Typed -> RevealMode.Typewriter
                Fonts.byId(look.fontId).group == FontGroup.Handwritten || Fonts.byId(look.fontId).group == FontGroup.Script -> RevealMode.Handwriting
                else -> RevealMode.Words
            }
        }
    }
}
