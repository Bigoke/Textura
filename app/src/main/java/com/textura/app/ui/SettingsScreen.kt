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
private val SigStyles = listOf(0 to "Elegant", 1 to "Script", 2 to "Badge", 3 to "Plain")
private val SigColors = listOf(0 to "White", 1 to "Black", 2 to "Gold")

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

        Group("Theme", startOpen = true) {
        ChipRow(ThemeMode.entries, s.theme, { it.name }) { t -> vm.updateSettings { it.copy(theme = t) } }

        }
        Group("Default canvas size") {
        ShapePicker(s.defaultShape) { shape ->
            vm.updateSettings { it.copy(defaultShape = shape) }
            vm.updateShape(shape)
        }

        }
        Group("Default export size (longest side)") {
        val selectedRes = SettingResolutions.firstOrNull { it.first == s.defaultLongEdge } ?: SettingResolutions[1]
        ChipRow(SettingResolutions, selectedRes, { it.second }) { r -> vm.updateSettings { it.copy(defaultLongEdge = r.first) } }

        }
        Group("Default format") {
        ChipRow(ImageFormat.entries, s.defaultFormat, { it.label }) { f -> vm.updateSettings { it.copy(defaultFormat = f) } }

        if (s.defaultFormat == ImageFormat.JPG) {
            LabeledSlider(
                "JPG quality", s.jpgQuality.toFloat(), 60f..100f,
                { v -> vm.updateSettings { it.copy(jpgQuality = v.toInt()) } }, "${s.jpgQuality}",
            )
        }

        }
        Group("Signature on exports") {
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
        ChipRow(SigStyles, SigStyles.first { it.first == s.signatureStyle }, { it.second }) { p ->
            vm.updateSettings { it.copy(signatureStyle = p.first) }
        }
        ChipRow(SigColors, SigColors.first { it.first == s.signatureColor }, { it.second }) { p ->
            vm.updateSettings { it.copy(signatureColor = p.first) }
        }

        }
        Group("Translation quality") {
        Text(
            "The built-in translator works offline. For much better Afrikaans and English, paste a Google Cloud Translation API key; it is only used when you translate and stays on this phone.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = s.googleKey,
            onValueChange = { v -> vm.updateSettings { it.copy(googleKey = v.trim()) } },
            singleLine = true,
            label = { Text("Google Translation API key (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        }
        Group("Feel") {
        SwitchRow("Haptic tick when swiping", s.haptics) { on -> vm.updateSettings { it.copy(haptics = on) } }
        SwitchRow("Smart order (best looks for your text first)", s.smartOrder) { on -> vm.updateSettings { it.copy(smartOrder = on) } }

        }
        Group("Spelling & grammar") {
        Text(
            "Checks Afrikaans and English. Spelling and the basic grammar rules run on your phone. Words you add while checking are kept in your own dictionary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("My dictionary: ${vm.userWords.size} words", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { vm.clearUserWords() }, enabled = vm.userWords.isNotEmpty()) { Text("Clear") }
        }

        }
        Group("Backup") {
        Text(
            "Saves your templates, favorites and settings to a file. Choose Google Drive in the file picker to keep it in the cloud. Background photos are not included.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { backupLauncher.launch("textura-backup.json") }) { Text("Back up") }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Restore") }
        }

        }
        Group("Widget") {
        Text(
            "Long-press your home screen, choose Widgets, and add Textura for a quote of the day in one of your favorite looks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        }
        Group("About") {
        Text(
            "Textura 1.3\nEverything is created on your device. Fonts are bundled open-source families (SIL Open Font License / Apache 2.0) from Google Fonts. Afrikaans spelling uses the af_ZA dictionary (Translate.org.za, LGPL 2.1); English uses SCOWL-based word lists. Translation uses Google ML Kit; its language data downloads once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        }
    }
}
