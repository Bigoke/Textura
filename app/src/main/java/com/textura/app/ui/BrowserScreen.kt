package com.textura.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility as FadeVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.textura.app.model.Category
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

@Composable
fun BrowserScreen(vm: AppViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pager = rememberPagerState(initialPage = vm.browserIndex.coerceIn(0, max(0, vm.looks.size - 1))) { vm.looks.size }
    var grid by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf<ExportMode?>(null) }

    val looks = vm.looks
    val current = looks.getOrNull(pager.currentPage) ?: looks.first()
    val accent by animateColorAsState(accentOf(current, MaterialTheme.colorScheme.primary), label = "accent")

    LaunchedEffect(vm.browserIndex) {
        if (pager.currentPage != vm.browserIndex && vm.browserIndex in 0 until pager.pageCount) {
            pager.scrollToPage(vm.browserIndex)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.drop(1).collect {
            vm.browserIndex = it
            if (vm.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- top bar: back, look name, grid toggle
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Crossfade(
                targetState = current.name,
                modifier = Modifier.weight(1f),
                label = "lookName",
            ) { name ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (current.id == AppViewModel.WORKING_ID) "Your edit" else name,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        current.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val fav0 = vm.isFavorite(current)
            val pulse = remember { Animatable(1f) }
            LaunchedEffect(fav0) {
                if (fav0) { pulse.animateTo(1.4f, tween(110)); pulse.animateTo(1f, spring(0.35f, 380f)) }
            }
            IconButton(onClick = {
                val wasWorking = current.id == AppViewModel.WORKING_ID
                vm.toggleFavorite(current)
                if (wasWorking) Toast.makeText(ctx, "Saved to My templates", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    if (fav0) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite",
                    tint = if (fav0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
                )
            }
            OverflowMenu(
                buildList {
                    add(MenuAction(if (grid) "Back to swiping" else "Show all looks", if (grid) Icons.Rounded.ViewCarousel else Icons.Rounded.GridView) { grid = !grid })
                    Category.entries.forEach { cat ->
                        add(MenuAction("Jump to ${cat.label}") {
                            val idx = looks.indexOfFirst { it.category == cat && it.id != AppViewModel.WORKING_ID }
                            if (idx >= 0) {
                                grid = false
                                scope.launch { pager.animateScrollToPage(idx) }
                            }
                        })
                    }
                },
            )
        }

        // ---- the pages (and the grid laid over them)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { grid = !grid }) },
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 36.dp),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1,
            ) { page ->
                val look = vm.looks.getOrNull(page)
                if (look != null) {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp)
                            .graphicsLayer {
                                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
                                val s = 1f - 0.08f * offset.coerceIn(0f, 1f)
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.45f * offset.coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val ratio = vm.shape.ratio
                        val w = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
                        LookPreview(
                            look = look,
                            text = vm.text,
                            shape = vm.shape,
                            longEdge = 900,
                            modifier = Modifier
                                .width(w)
                                .shadow(14.dp, RoundedCornerShape(14.dp)),
                        )
                    }
                }
            }

            FadeVisibility(
                visible = grid,
                enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f),
                exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.97f),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(looks.size) { i ->
                        val look = looks[i]
                        val selected = i == pager.currentPage
                        Column(
                            Modifier.clickable {
                                grid = false
                                scope.launch { pager.scrollToPage(i) }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LookPreview(
                                look = look,
                                text = vm.text,
                                shape = vm.shape,
                                longEdge = 360,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        if (selected) 3.dp else 0.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(14.dp),
                                    ),
                            )
                            Text(
                                if (look.id == AppViewModel.WORKING_ID) "Your edit" else look.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // ---- dots and hint
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PagerDots(count = looks.size, current = pager.currentPage, accent = accent)
            Text(
                "Swipe to change look · double-tap for grid",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // ---- action bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionItem(Icons.Rounded.Edit, "Edit") {
                vm.startEditing(current)
                onEdit()
            }
            ActionItem(Icons.Rounded.SaveAlt, "Save") { exportMode = ExportMode.Save }
            ActionItem(Icons.Rounded.Share, "Share") { exportMode = ExportMode.Share }
        }
    }

    exportMode?.let { mode -> ExportSheet(vm, current, mode) { exportMode = null } }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Column(
        Modifier
            .pressScale()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, accent: Color) {
    val window = 7
    val start = (current - window / 2).coerceIn(0, max(0, count - window))
    val end = min(count, start + window)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in start until end) {
            val d = abs(i - current)
            val size = when (d) {
                0 -> 9.dp
                1 -> 7.dp
                else -> 5.dp
            }
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (d == 0) accent else MaterialTheme.colorScheme.outline),
            )
        }
    }
}
