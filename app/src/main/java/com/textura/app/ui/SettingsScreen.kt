package com.textura.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.textura.app.data.ImageFormat
import com.textura.app.data.ThemeMode
import com.textura.app.model.CanvasShape

private val SettingResolutions = listOf(1080 to "HD 1080", 2048 to "2K 2048", 3840 to "4K 3840")
private val SigPositions = listOf(0 to "Left", 1 to "Centre", 2 to "Right")

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.backupJson().toByteArray()) } != null
            }.getOrDefault(false)
            Toast.makeText(ctx, if (ok) "Backup saved" else "Could not save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val ok = json != null && vm.restoreBackup(json)
            Toast.makeText(ctx, if (ok) "Backup restored" else "That file is not a Textura backup", Toast.LENGTH_SHORT).show()
        }
    }

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

        SectionTitle("Default canvas size")
        ShapePicker(s.defaultShape) { shape ->
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

        SectionTitle("Signature on exports")
        SwitchRow("Add my signature by default", s.signatureOn) { on -> vm.updateSettings { it.copy(signatureOn = on) } }
        OutlinedTextField(
            value = s.signature,
            onValueChange = { v -> vm.updateSettings { it.copy(signature = v) } },
            singleLine = true,
            label = { Text("Signature text, e.g. @yourname") },
            modifier = Modifier.fillMaxWidth(),
        )
        ChipRow(SigPositions, SigPositions.first { it.first == s.signaturePos }, { it.second }) { p ->
            vm.updateSettings { it.copy(signaturePos = p.first) }
        }

        SectionTitle("Feel")
        SwitchRow("Haptic tick when swiping", s.haptics) { on -> vm.updateSettings { it.copy(haptics = on) } }
        SwitchRow("Smart order (best looks for your text first)", s.smartOrder) { on -> vm.updateSettings { it.copy(smartOrder = on) } }

        SectionTitle("Backup")
        Text(
            "Saves your templates, favorites and settings to a file. Choose Google Drive in the file picker to keep it in the cloud. Background photos are not included.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { backupLauncher.launch("textura-backup.json") }) { Text("Back up") }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Restore") }
        }

        SectionTitle("Widget")
        Text(
            "Long-press your home screen, choose Widgets, and add Textura for a quote of the day in one of your favorite looks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("About")
        Text(
            "Textura 1.2\nEverything is created on your device. Fonts are bundled open-source families (SIL Open Font License / Apache 2.0) from Google Fonts, so the app works fully offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
