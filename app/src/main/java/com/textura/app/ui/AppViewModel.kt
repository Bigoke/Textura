package com.textura.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.textura.app.data.Exporter
import com.textura.app.data.HistoryItem
import com.textura.app.data.ImageFormat
import com.textura.app.data.Settings
import com.textura.app.data.Store
import com.textura.app.model.CanvasShape
import com.textura.app.model.Align
import com.textura.app.model.AppFont
import com.textura.app.model.FontGroup
import com.textura.app.model.Fonts
import com.textura.app.render.Watermark
import com.textura.app.model.Catalogue
import com.textura.app.model.Category
import com.textura.app.model.Depth
import com.textura.app.model.TextShape
import com.textura.app.render.Markup
import com.textura.app.model.Look
import com.textura.app.render.Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

object Samples {
    val all = listOf(
        "Write it down. Make it beautiful.\nShare it with someone who needs to read it today.",
        "Some days you carry the whole sky.\nOther days the sky carries you.\nEither way, keep walking.",
        "Dear future me,\nI hope you finally learned to rest without guilt, to say no without explaining, and to keep the small joys close.\nYours, today.",
        "Good design is invisible until it is missing. Make the words fill the page, let the shape decide the rhythm, and choose the one look that feels like you.",
        "[huge]Dear[/huge] [c=#E8604C]future[/c] [i]me[/i],\nbe [b]kind[/b] to yourself today.\n[small]Select words and use the Format buttons to style them.[/small]",
    )
}

enum class ExportResult { Saved, Shared, Failed, NoApp }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val WORKING_ID = "working"
        private const val HISTORY_MAX = 40
    }

    private val store = Store(app)

    /** Bumped when fonts are added or removed so the font lists refresh. */
    var fontsVersion by mutableIntStateOf(0)

    init {
        Fonts.user = store.loadUserFonts().map { AppFont(it.id, it.label, FontGroup.Mine, it.path) }
    }

    var settings by mutableStateOf(store.loadSettings()); private set
    var text by mutableStateOf(store.loadText() ?: Samples.all.first()); private set
    var shape by mutableStateOf(settings.defaultShape); private set
    var favorites by mutableStateOf(store.loadFavorites()); private set
    var custom by mutableStateOf(store.loadCustom()); private set
    var history by mutableStateOf(store.loadHistory()); private set

    /** The look the user is editing / has edited; shown as the first page of the browser. */
    var working by mutableStateOf<Look?>(null); private set
    var browserIndex by mutableIntStateOf(0)

    private var orderKey: String? = null
    private var orderCache: List<Look> = Catalogue.all

    /** Looks sorted so the ones that suit the current text come first (when smart order is on). */
    private fun orderedCatalogue(): List<Look> {
        if (!settings.smartOrder) return Catalogue.all
        val key = text.hashCode().toString()
        if (key != orderKey) {
            orderKey = key
            orderCache = Catalogue.all.sortedByDescending { suitability(it) }
        }
        return orderCache
    }

    private fun suitability(l: Look): Int {
        val len = Markup.strip(text).length
        val lines = text.count { it == '\n' } + 1
        var s = 0
        val short = len < 90
        val long = len > 420
        when (l.category) {
            Category.Art, Category.ThreeD, Category.Shapes -> s += if (short) 3 else if (len < 220) 1 else -2
            Category.Typed -> s += if (long) 3 else if (len > 220) 1 else 0
            Category.Paper -> if (len in 120..500) s += 2
            Category.Handwritten -> if (len < 350) s += 2
        }
        if (long && l.fontId in setOf("anton", "bungee", "monoton", "abril_fatface", "unifraktur", "bebas_neue")) s -= 3
        if (l.textShape != TextShape.Rect) s += if (len > 300) -4 else if (len < 200) 1 else 0
        if (l.depth == Depth.Extrude && len > 200) s -= 2
        if (lines >= 4 && l.align == Align.Center) s += 1
        return s
    }

    val looks: List<Look> get() = listOfNotNull(working) + orderedCatalogue() + custom

    // ---------------------------------------------------------------- text & shape
    fun updateText(value: String) {
        text = value
        store.saveText(value)
    }

    fun randomSample() {
        val others = Samples.all.filter { it != text }
        updateText(others.random())
    }

    fun updateShape(value: CanvasShape) {
        shape = value
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        settings = transform(settings)
        store.saveSettings(settings)
    }

    // ---------------------------------------------------------------- editing with undo / redo
    var editing by mutableStateOf<Look?>(null); private set
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    private var editBase: Look? = null
    private val undoStack = ArrayList<Look>()
    private val redoStack = ArrayList<Look>()
    private var lastPush = 0L

    fun startEditing(look: Look) {
        editBase = if (look.id == WORKING_ID) (editBase ?: look) else look
        editing = look.copy(id = WORKING_ID)
        undoStack.clear()
        redoStack.clear()
        lastPush = 0L
        refreshFlags()
    }

    fun edit(transform: (Look) -> Look) {
        val current = editing ?: return
        val next = transform(current).copy(id = WORKING_ID)
        if (next == current) return
        val now = SystemClock.elapsedRealtime()
        // changes arriving in a quick burst (a slider drag) collapse into one undo step
        if (undoStack.isEmpty() || now - lastPush > 700L) {
            undoStack.add(current)
            if (undoStack.size > 60) undoStack.removeAt(0)
        }
        lastPush = now
        redoStack.clear()
        editing = next
        refreshFlags()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        editing?.let { redoStack.add(it) }
        editing = undoStack.removeAt(undoStack.size - 1)
        lastPush = 0L
        refreshFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        editing?.let { undoStack.add(it) }
        editing = redoStack.removeAt(redoStack.size - 1)
        lastPush = 0L
        refreshFlags()
    }

    fun resetToTemplate() {
        val base = editBase ?: return
        edit { base }
    }

    /** Commits the edit so it appears as the first page of the browser. */
    fun finishEditing() {
        editing?.let {
            working = it
            browserIndex = 0
        }
    }

    private fun refreshFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun importPhoto(uri: Uri): String? = store.importPhoto(uri)

    // ---------------------------------------------------------------- favourites & templates
    fun isFavorite(look: Look) = look.id in favorites

    fun toggleFavorite(look: Look) {
        if (look.id == WORKING_ID) {
            saveAsTemplate(look, "My look ${custom.size + 1}")
            return
        }
        favorites = if (look.id in favorites) favorites - look.id else favorites + look.id
        store.saveFavorites(favorites)
    }

    fun favoriteLooks(): List<Look> = (Catalogue.all + custom).filter { it.id in favorites }

    fun saveAsTemplate(look: Look, name: String) {
        val t = look.copy(id = "custom_" + UUID.randomUUID(), name = name.trim().ifEmpty { "My look" })
        custom = custom + t
        store.saveCustom(custom)
    }

    fun deleteTemplate(id: String) {
        custom = custom.filter { it.id != id }
        favorites = favorites - id
        store.saveCustom(custom)
        store.saveFavorites(favorites)
    }

    /** Jump the browser to a given look. */
    fun openLook(look: Look) {
        val idx = looks.indexOfFirst { it.id == look.id }
        browserIndex = if (idx >= 0) idx else 0
    }

    // ---------------------------------------------------------------- history
    fun thumb(id: String): Bitmap? = store.loadThumb(id)

    fun restore(item: HistoryItem) {
        updateText(item.text)
        shape = item.shape
        working = item.look.copy(id = WORKING_ID)
        browserIndex = 0
    }

    fun deleteHistory(item: HistoryItem) {
        store.deleteThumb(item.id)
        history = history.filter { it.id != item.id }
        store.saveHistory(history)
    }

    private fun makeThumb(bmp: Bitmap): Bitmap {
        val f = 480f / max(bmp.width, bmp.height)
        return Bitmap.createScaledBitmap(bmp, max(2, (bmp.width * f).roundToInt()), max(2, (bmp.height * f).roundToInt()), true)
    }

    private fun record(look: Look, thumb: Bitmap) {
        val id = UUID.randomUUID().toString()
        store.saveThumb(id, thumb)
        val item = HistoryItem(id, System.currentTimeMillis(), text, shape, look.copy(id = look.id))
        val next = listOf(item) + history
        next.drop(HISTORY_MAX).forEach { store.deleteThumb(it.id) }
        history = next.take(HISTORY_MAX)
        store.saveHistory(history)
    }

    // ---------------------------------------------------------------- export
    /** Splits [raw] into [n] pages at word boundaries, keeping formatting tags balanced on every page. */
    fun splitPages(raw: String, n: Int): List<String> {
        if (n <= 1) return listOf(raw)
        val tokens = Regex("\\S+|\\s+").findAll(raw).map { it.value }.toList()
        val total = tokens.sumOf { Markup.strip(it).length }.coerceAtLeast(1)
        val target = total.toFloat() / n
        val pages = ArrayList<String>()
        val cur = StringBuilder()
        val open = ArrayList<Pair<String, String>>() // tag name to full opening tag
        var acc = 0
        for (tok in tokens) {
            val isWord = !tok[0].isWhitespace()
            if (isWord && pages.size < n - 1 && acc >= target * (pages.size + 1) && cur.isNotBlank()) {
                for (o in open.asReversed()) cur.append("[/").append(o.first).append("]")
                pages.add(cur.toString().trim())
                cur.setLength(0)
                for (o in open) cur.append(o.second)
            }
            cur.append(tok)
            acc += Markup.strip(tok).length
            for (m in Markup.TAG.findAll(tok)) {
                val name = m.groupValues[2]
                if (m.groupValues[1] == "/") {
                    val idx = open.indexOfLast { it.first == name }
                    if (idx >= 0) open.removeAt(idx)
                } else open.add(name to m.value)
            }
        }
        pages.add(cur.toString().trim())
        return pages.filter { it.isNotBlank() }.ifEmpty { listOf(raw) }
    }

    suspend fun export(
        look: Look,
        share: Boolean,
        longEdge: Int,
        format: ImageFormat,
        transparent: Boolean,
        pages: Int = 1,
        signature: Watermark? = null,
        targetPackage: String? = null,
    ): ExportResult {
        val ctx = getApplication<Application>()
        val s = settings
        val pageTexts = splitPages(text, pages)
        val currentShape = shape
        val png = transparent && format == ImageFormat.PNG
        return withContext(Dispatchers.Default) {
            val uris = ArrayList<android.net.Uri>()
            var thumb: Bitmap? = null
            if (share) Exporter.clearShareCache(ctx)
            for ((i, pageText) in pageTexts.withIndex()) {
                val bmp = try {
                    Renderer.render(ctx, look, pageText, currentShape, longEdge, png, 1f, signature)
                } catch (e: OutOfMemoryError) {
                    return@withContext ExportResult.Failed
                }
                if (i == 0) thumb = makeThumb(bmp)
                if (share) {
                    val uri = Exporter.writeToShareCache(ctx, bmp, format, s.jpgQuality, i)
                    if (uri != null) uris.add(uri)
                } else {
                    val saved = Exporter.saveToGallery(
                        ctx, bmp, format, s.jpgQuality,
                        subfolder = if (pageTexts.size > 1) "Carousel" else null,
                        suffix = if (pageTexts.size > 1) "_p${i + 1}" else "",
                    )
                    if (saved == null) { bmp.recycle(); return@withContext ExportResult.Failed }
                }
                bmp.recycle()
            }
            val ok = if (share) withContext(Dispatchers.Main) { Exporter.shareUris(ctx, uris, format, targetPackage) } else true
            if (ok) thumb?.let { t -> withContext(Dispatchers.Main) { record(look, t) } }
            when {
                !ok && targetPackage != null -> ExportResult.NoApp
                !ok -> ExportResult.Failed
                share -> ExportResult.Shared
                else -> ExportResult.Saved
            }
        }
    }

    /** Saves the current text in each of [looks] to Pictures/Textura/All looks. Returns how many were saved. */
    suspend fun exportBatch(
        looks: List<Look>,
        longEdge: Int,
        format: ImageFormat,
        signature: Watermark?,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int {
        val ctx = getApplication<Application>()
        val s = settings
        val t = text
        val sh = shape
        return withContext(Dispatchers.Default) {
            var saved = 0
            for ((i, look) in looks.withIndex()) {
                ensureActive()
                onProgress(i, looks.size)
                val bmp = try {
                    Renderer.render(ctx, look, t, sh, longEdge, false, 1f, signature)
                } catch (e: OutOfMemoryError) {
                    break
                }
                val uri = Exporter.saveToGallery(ctx, bmp, format, s.jpgQuality, subfolder = "All looks", suffix = "_" + look.id.take(24))
                bmp.recycle()
                if (uri != null) saved++
            }
            onProgress(looks.size, looks.size)
            saved
        }
    }

    // ---------------------------------------------------------------- fonts, backup
    fun importFont(uri: android.net.Uri): Boolean {
        val f = store.importFont(uri) ?: return false
        val list = store.loadUserFonts() + f
        store.saveUserFonts(list)
        Fonts.user = list.map { AppFont(it.id, it.label, FontGroup.Mine, it.path) }
        fontsVersion++
        return true
    }

    var videoOptions by mutableStateOf(com.textura.app.data.VideoOptions())

    fun updateVideo(t: (com.textura.app.data.VideoOptions) -> com.textura.app.data.VideoOptions) {
        videoOptions = t(videoOptions)
    }

    /** Copies a picked audio file into app storage so it stays readable. */
    fun importMusic(uri: android.net.Uri): Boolean {
        val ctx = getApplication<Application>()
        return runCatching {
            val dir = java.io.File(ctx.filesDir, "music").apply { mkdirs() }
            val f = java.io.File(dir, "import.audio")
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: return false
            videoOptions = videoOptions.copy(musicPath = f.absolutePath, music = com.textura.app.data.MusicChoice.Imported)
            true
        }.getOrDefault(false)
    }

    suspend fun exportAnimation(
        look: Look,
        opts: com.textura.app.data.VideoOptions,
        signature: Watermark?,
        onProgress: (Int, Int) -> Unit,
    ): com.textura.app.data.VideoExporter.Result {
        val ctx = getApplication<Application>()
        val pageTexts = splitPages(text, opts.pages.coerceIn(1, 10))
        val sh = shape
        return withContext(Dispatchers.Default) {
            runCatching {
                if (opts.separate && pageTexts.size > 1) {
                    var worst = com.textura.app.data.VideoExporter.Result.Ok
                    for ((i, pt) in pageTexts.withIndex()) {
                        val r = com.textura.app.data.VideoExporter.export(
                            ctx, look, listOf(pt), sh, opts, signature,
                            { d, t -> onProgress(i * 1000 + (1000L * d / maxOf(1, t)).toInt(), pageTexts.size * 1000) },
                            { !isActive },
                        )
                        if (r == com.textura.app.data.VideoExporter.Result.Failed) return@runCatching r
                        if (r == com.textura.app.data.VideoExporter.Result.NoSound) worst = r
                    }
                    worst
                } else {
                    com.textura.app.data.VideoExporter.export(ctx, look, pageTexts, sh, opts, signature, onProgress, { !isActive })
                }
            }.getOrDefault(com.textura.app.data.VideoExporter.Result.Failed)
        }
    }

    fun loadHand() = store.loadHand()

    /** Builds the handwriting font from drawn strokes and selects it. */
    fun buildHandwriting(glyphs: Map<String, List<List<List<Float>>>>, thickness: Float): Boolean {
        val strokes = glyphs.entries.filter { it.key.length == 1 }.associate { e ->
            e.key[0] to e.value.map { st -> st.filter { it.size >= 2 }.map { it[0] to it[1] } }
        }
        if (strokes.isEmpty()) return false
        store.saveHand(com.textura.app.data.HandData(thickness, glyphs))
        val bytes = runCatching { com.textura.app.render.FontBuilder.build("My handwriting", strokes, thickness) }.getOrNull() ?: return false
        val id = "user_myhand"
        Fonts.forget(id)
        val f = store.saveGeneratedFont(id, "My handwriting", bytes) ?: return false
        val list = store.loadUserFonts().filter { it.id != id } + f
        store.saveUserFonts(list)
        Fonts.user = list.map { AppFont(it.id, it.label, FontGroup.Mine, it.path) }
        fontsVersion++
        edit { it.copy(fontId = id, handwritten = true, imperfection = maxOf(it.imperfection, 0.4f)) }
        return true
    }

    fun deleteFont(id: String) {
        val list = store.loadUserFonts()
        list.firstOrNull { it.id == id }?.let { runCatching { java.io.File(it.path).delete() } }
        val rest = list.filter { it.id != id }
        store.saveUserFonts(rest)
        Fonts.forget(id)
        Fonts.user = rest.map { AppFont(it.id, it.label, FontGroup.Mine, it.path) }
        fontsVersion++
    }

    fun backupJson(): String = store.backupJson(custom, favorites, settings, text)

    fun restoreBackup(json: String): Boolean {
        val b = store.parseBackup(json) ?: return false
        custom = custom.filter { c -> b.custom.none { it.id == c.id } } + b.custom
        favorites = favorites + b.favorites
        settings = b.settings
        store.saveCustom(custom)
        store.saveFavorites(favorites)
        store.saveSettings(settings)
        if (b.text.isNotBlank()) updateText(b.text)
        shape = settings.defaultShape
        return true
    }

    /** Adds text found in a photo to the text box. */
    fun useScannedText(found: String) {
        if (found.isNotBlank()) updateText(found.trim())
    }
}
