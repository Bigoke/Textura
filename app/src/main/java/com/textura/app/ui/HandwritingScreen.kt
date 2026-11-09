package com.textura.app.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.textura.app.render.FontBuilder

private const val CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,!?'\"-:;()&@#"

@Composable
fun HandwritingScreen(vm: AppViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val glyphs = remember {
        mutableStateMapOf<Char, List<List<Pair<Float, Float>>>>().also { m ->
            vm.loadHand().glyphs.forEach { (k, v) ->
                if (k.length == 1) m[k[0]] = v.map { st -> st.filter { it.size >= 2 }.map { it[0] to it[1] } }
            }
        }
    }
    var thickness by remember { mutableFloatStateOf(vm.loadHand().thickness) }
    var current by remember { mutableIntStateOf(0) }
    val live = remember { mutableStateListOf<Offset>() }
    var tick by remember { mutableIntStateOf(0) }
    val ch = CHARS[current]
    val done = glyphs.keys.count { glyphs[it]?.isNotEmpty() == true }
    val ink = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        Text("My handwriting", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Write each letter inside the guides, sitting on the bold line. Do as many as you like; letters you skip use a plain fallback. $done of ${CHARS.length} done.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            items(CHARS.length) { i ->
                val c = CHARS[i]
                val has = glyphs[c]?.isNotEmpty() == true
                Box(
                    Modifier
                        .size(40.dp)
                        .background(if (i == current) accent else if (has) accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(10.dp))
                        .border(1.dp, if (i == current) accent else guide, RoundedCornerShape(10.dp))
                        .clickable { current = i; live.clear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(c.toString(), color = if (i == current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(1.dp, guide, RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(current) {
                        detectDragGestures(
                            onDragStart = { live.clear(); live.add(it) },
                            onDrag = { change, _ -> change.consume(); live.add(change.position) },
                            onDragEnd = { finishStroke(live, size.width.toFloat(), size.height.toFloat(), glyphs, ch); live.clear(); tick++ },
                            onDragCancel = { live.clear() },
                        )
                    },
            ) {
                val w = size.width
                val h = size.height
                listOf(FontBuilder.GUIDE_ASCENDER, FontBuilder.GUIDE_XHEIGHT, FontBuilder.GUIDE_DESCENDER).forEach {
                    drawLine(guide, Offset(0f, it * h), Offset(w, it * h), strokeWidth = 2f)
                }
                drawLine(accent.copy(alpha = 0.7f), Offset(0f, FontBuilder.GUIDE_BASELINE * h), Offset(w, FontBuilder.GUIDE_BASELINE * h), strokeWidth = 5f)
                val pen = thickness * w
                for (st in glyphs[ch].orEmpty()) drawStroke(st.map { Offset(it.first * w, it.second * h) }, ink, pen)
                drawStroke(live.toList(), ink, pen)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val l = glyphs[ch].orEmpty()
                if (l.isNotEmpty()) glyphs[ch] = l.dropLast(1)
            }, modifier = Modifier.weight(1f)) { Text("Undo") }
            OutlinedButton(onClick = { glyphs.remove(ch) }, modifier = Modifier.weight(1f)) { Text("Clear") }
            Button(onClick = { current = (current + 1) % CHARS.length; live.clear() }, modifier = Modifier.weight(1f)) { Text("Next") }
        }

        LabeledSlider("Pen thickness", thickness, 0.02f..0.10f, { thickness = it }, "${(thickness * 100).toInt()}")

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDone) { Text("Cancel") }
            Button(
                onClick = {
                    val data = glyphs.filter { it.value.isNotEmpty() }
                        .mapKeys { it.key.toString() }
                        .mapValues { e -> e.value.map { st -> st.map { listOf(it.first, it.second) } } }
                    val ok = vm.buildHandwriting(data, thickness)
                    Toast.makeText(ctx, if (ok) "Font created. It is selected as your font." else "Draw at least one letter first.", Toast.LENGTH_LONG).show()
                    if (ok) onDone()
                },
                enabled = done > 0,
                modifier = Modifier.weight(1f),
            ) { Text("Create my font") }
        }
    }
}

private fun finishStroke(
    pts: List<Offset>, w: Float, h: Float,
    glyphs: MutableMap<Char, List<List<Pair<Float, Float>>>>, ch: Char,
) {
    if (pts.isEmpty() || w <= 0f || h <= 0f) return
    val st = pts.map { (it.x / w).coerceIn(0f, 1f) to (it.y / h).coerceIn(0f, 1f) }
    glyphs[ch] = glyphs[ch].orEmpty() + listOf(st)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(pts: List<Offset>, color: Color, pen: Float) {
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        drawCircle(color, pen / 2f, pts[0])
        return
    }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }
    drawPath(path, color, style = Stroke(width = pen, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
