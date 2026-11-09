package com.textura.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.textura.app.data.FxSound
import com.textura.app.data.MusicChoice
import com.textura.app.data.VideoExporter
import com.textura.app.data.VideoFrames
import com.textura.app.data.VideoStyle
import com.textura.app.model.Look
import com.textura.app.render.Markup
import com.textura.app.render.Watermark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

private val WriteTimes = listOf(0f to "Auto", 4f to "4 s", 8f to "8 s", 12f to "12 s", 20f to "20 s")
private val VideoSizes = listOf(720 to "720", 1080 to "1080", 1280 to "1280", 1440 to "1440")
private val VideoFps = listOf(24 to "24 fps", 30 to "30 fps")
private val GifSizes = listOf(360 to "360", 480 to "480", 540 to "540", 720 to "720")
private val GifFps = listOf(10 to "10 fps", 12 to "12 fps", 15 to "15 fps")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSheet(vm: AppViewModel, look: Look, signature: Watermark?, onClose: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val o = vm.videoOptions
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = vm.importMusic(uri)
            Toast.makeText(ctx, if (ok) "Sound added" else "Could not read that file", Toast.LENGTH_SHORT).show()
        }
    }

    // live preview of everything that changes the picture (not the sound or the file format)
    val previewKey = o.copy(
        gif = false, fps = 24, longEdge = 1280, gifFps = 12, gifEdge = 480,
        fx = FxSound.Auto, fxVolume = 0.8f, music = MusicChoice.None, musicVolume = 0.5f, musicPath = null,
    )
    LaunchedEffect(look, vm.text, vm.shape, previewKey) {
        val (pw, ph) = VideoExporter.dims(vm.shape, 300, 2)
        val frames = VideoFrames(ctx, look, vm.text, vm.shape, previewKey, pw, ph, null, keep = true)
        val fps = 8
        val n = ceil(frames.duration * fps).toInt().coerceAtLeast(1)
        val cache = HashMap<Int, ImageBitmap>()
        var i = 0
        while (true) {
            val img = cache.getOrPut(i) {
                withContext(Dispatchers.Default) { frames.frameAt(i / fps.toFloat()).asImageBitmap() }
            }
            preview = img
            delay(1000L / fps)
            i = (i + 1) % n
        }
    }

    val write = if (o.seconds > 0f) o.seconds else (Markup.strip(vm.text).length / 22f).coerceIn(3f, 9f)
    val total = (if (o.intro) o.introSec else 0f) + write + o.hold + (if (o.outro) o.outroSec else 0f) + (if (o.loop) 0.5f else 0f)
    val (dw, dh) = VideoExporter.dims(vm.shape, if (o.gif) o.gifEdge else minOf(o.longEdge, 1440), if (o.gif) 2 else 16)

    fun create() {
        if (busy) return
        busy = true
        progress = 0 to 1
        job = scope.launch {
            val r = vm.exportAnimation(look, o, signature) { d, t -> progress = d to t }
            busy = false
            progress = null
            when (r) {
                VideoExporter.Result.Ok -> {
                    Toast.makeText(ctx, if (o.gif) "GIF saved to Pictures/Textura" else "Video saved to Movies/Textura", Toast.LENGTH_LONG).show()
                    onDone()
                }
                VideoExporter.Result.NoSound -> {
                    Toast.makeText(ctx, "Saved without sound: the sound could not be created on this phone.", Toast.LENGTH_LONG).show()
                    onDone()
                }
                VideoExporter.Result.Failed ->
                    Toast.makeText(ctx, "Could not create it. Try a smaller size.", Toast.LENGTH_LONG).show()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onClose() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Video & GIF", style = MaterialTheme.typography.titleLarge)
            Text(
                "$dw × $dh px · about ${"%.1f".format(total)} s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val img = preview
                if (img != null) {
                    Image(
                        bitmap = img, contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(260.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    Box(Modifier.height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                }
            }

            SectionTitle("Format")
            ChipRow(listOf(false, true), o.gif, { if (it) "GIF (no sound)" else "MP4 video" }) { g -> vm.updateVideo { it.copy(gif = g) } }

            SectionTitle("Animation")
            ChipRow(VideoStyle.entries, o.style, { it.label }) { s -> vm.updateVideo { it.copy(style = s) } }
            Spacer(Modifier.height(6.dp))
            ChipRow(WriteTimes, WriteTimes.firstOrNull { it.first == o.seconds } ?: WriteTimes[0], { "Writing: " + it.second }) { t ->
                vm.updateVideo { it.copy(seconds = t.first) }
            }
            SwitchRow("Show pen or cursor", o.pen) { on -> vm.updateVideo { it.copy(pen = on) } }
            LabeledSlider("Hold the finished text", o.hold, 0f..5f, { v -> vm.updateVideo { it.copy(hold = v) } }, "${"%.1f".format(o.hold)} s")
            SwitchRow("Loop (fade out and restart)", o.loop) { on -> vm.updateVideo { it.copy(loop = on) } }

            SectionTitle("Intro and outro")
            SwitchRow("Intro title card", o.intro) { on -> vm.updateVideo { it.copy(intro = on) } }
            if (o.intro) {
                OutlinedTextField(
                    value = o.introText, onValueChange = { v -> vm.updateVideo { it.copy(introText = v.take(60)) } },
                    singleLine = true, label = { Text("Intro text, e.g. Day 12") }, modifier = Modifier.fillMaxWidth(),
                )
                LabeledSlider("Intro length", o.introSec, 1f..4f, { v -> vm.updateVideo { it.copy(introSec = v) } }, "${"%.1f".format(o.introSec)} s")
            }
            SwitchRow("Outro closing card", o.outro) { on -> vm.updateVideo { it.copy(outro = on) } }
            if (o.outro) {
                OutlinedTextField(
                    value = o.outroText, onValueChange = { v -> vm.updateVideo { it.copy(outroText = v.take(60)) } },
                    singleLine = true, label = { Text("Outro text (empty uses your signature)") }, modifier = Modifier.fillMaxWidth(),
                )
                LabeledSlider("Outro length", o.outroSec, 1f..4f, { v -> vm.updateVideo { it.copy(outroSec = v) } }, "${"%.1f".format(o.outroSec)} s")
            }

            SectionTitle("Quality")
            if (o.gif) {
                ChipRow(GifSizes, GifSizes.firstOrNull { it.first == o.gifEdge } ?: GifSizes[1], { it.second + " px" }) { s -> vm.updateVideo { it.copy(gifEdge = s.first) } }
                Spacer(Modifier.height(6.dp))
                ChipRow(GifFps, GifFps.firstOrNull { it.first == o.gifFps } ?: GifFps[1], { it.second }) { s -> vm.updateVideo { it.copy(gifFps = s.first) } }
                Text(
                    "GIFs get large quickly. Smaller sizes and fewer frames per second keep the file easy to share. Photo backgrounds look grainier in a GIF.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                ChipRow(VideoSizes, VideoSizes.firstOrNull { it.first == o.longEdge } ?: VideoSizes[2], { it.second + " px" }) { s -> vm.updateVideo { it.copy(longEdge = s.first) } }
                Spacer(Modifier.height(6.dp))
                ChipRow(VideoFps, VideoFps.firstOrNull { it.first == o.fps } ?: VideoFps[0], { it.second }) { s -> vm.updateVideo { it.copy(fps = s.first) } }
            }

            if (!o.gif) {
                SectionTitle("Sound")
                ChipRow(FxSound.entries, o.fx, { it.label }) { f -> vm.updateVideo { it.copy(fx = f) } }
                if (o.fx != FxSound.Off) {
                    LabeledSlider("Effects volume", o.fxVolume, 0f..1f, { v -> vm.updateVideo { it.copy(fxVolume = v) } }, "${(o.fxVolume * 100).toInt()}%")
                }
                Spacer(Modifier.height(6.dp))
                ChipRow(MusicChoice.entries, o.music, { it.label }) { m -> vm.updateVideo { it.copy(music = m) } }
                if (o.music == MusicChoice.Imported) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { musicPicker.launch(arrayOf("audio/*")) }) { Text(if (o.musicPath == null) "Choose a sound file" else "Choose another file") }
                        Spacer(Modifier.width(10.dp))
                        Text(if (o.musicPath == null) "MP3, WAV, M4A…" else "File added", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (o.music != MusicChoice.None) {
                    LabeledSlider("Music volume", o.musicVolume, 0f..1f, { v -> vm.updateVideo { it.copy(musicVolume = v) } }, "${(o.musicVolume * 100).toInt()}%")
                }
            }

            Spacer(Modifier.height(16.dp))
            if (busy) {
                val p = progress
                Text("Creating ${p?.first ?: 0} / ${p?.second ?: 1}", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { if (p == null || p.second == 0) 0f else p.first.toFloat() / p.second },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                TextButton(onClick = { job?.cancel(); busy = false; progress = null }) { Text("Stop") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onClose) { Text("Close") }
                    Button(onClick = { create() }, modifier = Modifier.weight(1f)) { Text(if (o.gif) "Create GIF" else "Create video") }
                }
            }
        }
    }
}
