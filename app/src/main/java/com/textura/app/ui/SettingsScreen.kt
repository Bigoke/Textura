package com.textura.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.textura.app.data.ImageFormat
import com.textura.app.data.ThemeMode
import com.textura.app.model.CanvasShape

private val SettingResolutions = listOf(1080 to "HD 1080", 2048 to "2K 2048", 3840 to "4K 3840")

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionTitle("Theme")
        ChipRow(ThemeMode.entries, s.theme, { it.name }) { t -> vm.updateSettings { it.copy(theme = t) } }

        SectionTitle("Default shape")
        ChipRow(CanvasShape.entries, s.defaultShape, { it.label }) { shape ->
            vm.updateSettings { it.copy(defaultShape = shape) }
            vm.updateShape(shape)
        }

        SectionTitle("Default export size (longest side)")
        val selectedRes = SettingResolutions.firstOrNull { it.first == s.defaultLongEdge } ?: SettingResolutions[1]
        ChipRow(SettingResolutions, selectedRes, { it.second }) { r -> vm.updateSettings { it.copy(defaultLongEdge = r.first) } }

        SectionTitle("Default format")
        ChipRow(ImageFormat.entries, s.defaultFormat, { it.label }) { f -> vm.updateSettings { it.copy(defaultFormat = f) } }

        if (s.defaultFormat == ImageFormat.JPG) {
            LabeledSlider(
                "JPG quality", s.jpgQuality.toFloat(), 60f..100f,
                { v -> vm.updateSettings { it.copy(jpgQuality = v.toInt()) } }, "${s.jpgQuality}",
            )
        }

        SectionTitle("Feel")
        SwitchRow("Haptic tick when swiping", s.haptics) { on -> vm.updateSettings { it.copy(haptics = on) } }
        SwitchRow("Smart order (best looks for your text first)", s.smartOrder) { on -> vm.updateSettings { it.copy(smartOrder = on) } }

        SectionTitle("About")
        Text(
            "Textura 1.0\nEverything is created on your device. Fonts are bundled open-source families (SIL Open Font License / Apache 2.0) from Google Fonts, so the app works fully offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
