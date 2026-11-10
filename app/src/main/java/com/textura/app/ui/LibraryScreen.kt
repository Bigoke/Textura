package com.textura.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.textura.app.data.HistoryItem
import com.textura.app.model.Look
import java.text.DateFormat
import java.util.Date

private val LibraryTabs = listOf("Favorites", "My templates", "Projects", "History")

@Composable
fun LibraryScreen(vm: AppViewModel, onOpenLook: (Look) -> Unit, onOpenHistory: (HistoryItem) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
        )
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            LibraryTabs.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
        }
        Crossfade(targetState = tab, animationSpec = tween(220), label = "libTab") { t ->
        when (t) {
            0 -> LooksGrid(
                vm, vm.favoriteLooks(), "Tap the heart on any look to keep it here.",
                onOpenLook, onDelete = null,
            )
            1 -> LooksGrid(
                vm, vm.custom, "Edit a look, then choose Save as my template.",
                onOpenLook, onDelete = { vm.deleteTemplate(it.id) },
            )
            2 -> HistoryGrid(vm, onOpenHistory, projects = true)
            else -> HistoryGrid(vm, onOpenHistory, projects = false)
        }
        }
    }
}

@Composable
private fun LooksGrid(
    vm: AppViewModel,
    looks: List<Look>,
    emptyText: String,
    onOpen: (Look) -> Unit,
    onDelete: ((Look) -> Unit)?,
) {
    if (looks.isEmpty()) {
        EmptyHint(emptyText)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(looks, key = { it.id }) { look ->
            Column(Modifier.clickable { onOpen(look) }) {
                Box {
                    LookPreview(look, vm.text, vm.shape, 480, Modifier.fillMaxWidth())
                    if (onDelete != null) {
                        IconButton(
                            onClick = { onDelete(look) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000)),
                        ) {
                            Icon(Icons.Rounded.Close, "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Text(look.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                Text(look.category.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HistoryGrid(vm: AppViewModel, onOpen: (HistoryItem) -> Unit, projects: Boolean) {
    val shown = vm.history.filter { it.saved == projects }
    if (shown.isEmpty()) {
        EmptyHint(if (projects) "In the editor, tap Save project to keep a design here and continue it later." else "Designs you export or share appear here.")
        return
    }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shown, key = { it.id }) { item ->
            Column {
                Box {
                    HistoryThumb(vm, item, 220.dp, Modifier.fillMaxWidth()) { onOpen(item) }
                    IconButton(
                        onClick = { vm.deleteHistory(item) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000)),
                    ) {
                        Icon(Icons.Rounded.Close, "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                if (item.name.isNotBlank()) Text(item.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                Text(
                    fmt.format(Date(item.time)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
