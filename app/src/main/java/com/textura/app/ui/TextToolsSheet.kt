package com.textura.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.textura.app.lang.Checker
import com.textura.app.lang.Issue
import com.textura.app.lang.IssueKind
import com.textura.app.lang.Lang
import com.textura.app.lang.Translator
import com.textura.app.render.Markup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What to open the text tools with. [tab] 0 = translate, 1 = spelling and grammar. */
data class ToolsRequest(val text: String, val fromScan: Boolean, val tab: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToolsSheet(vm: AppViewModel, request: ToolsRequest, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var working by remember { mutableStateOf(request.text) }
    var tab by remember { mutableStateOf(request.tab) }

    // ---- translate
    var detected by remember { mutableStateOf(Lang.English) }
    var from by remember { mutableStateOf<Lang?>(null) }
    var to by remember { mutableStateOf<Lang?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // ---- check
    var forced by remember { mutableStateOf<Lang?>(null) }
    var deep by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf<Checker.Result?>(null) }
    var checking by remember { mutableStateOf(false) }

    val plainWorking = Markup.strip(working)
    LaunchedEffect(working) {
        delay(250)
        detected = Checker.detect(ctx, working)
    }
    val source = from ?: detected
    val target = to ?: if (source == Lang.English) Lang.Afrikaans else Lang.English

    LaunchedEffect(working, forced, deep, vm.userWords, vm.ignoredIssues, tab) {
        if (tab != 1) return@LaunchedEffect
        delay(450)
        checking = true
        checked = Checker.check(ctx, working, forced, vm.userWords, vm.ignoredIssues, deep)
        checking = false
    }

    fun applyFix(issue: Issue, replacement: String) {
        if (issue.end > working.length || working.substring(issue.start, issue.end) != issue.word) return
        working = working.replaceRange(issue.start, issue.end, replacement)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(if (request.fromScan) "Text found in your photo" else "Translate & check", style = MaterialTheme.typography.titleLarge)
            if (request.fromScan) {
                Text(
                    "Fix anything that was misread, translate it, or check the spelling, then use it in Textura.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = working,
                onValueChange = { working = it; result = null },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                label = { Text("Text") },
            )
            Spacer(Modifier.height(8.dp))
            ChipRow(listOf(0, 1), tab, { if (it == 0) "Translate" else "Spelling & grammar" }) { tab = it }

            if (tab == 0) {
                SectionTitle("Translate")
                Text(
                    "Detected: ${detected.label}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ChipRow(listOf<Lang?>(null) + Lang.entries, from, { it?.label?.let { l -> "From: $l" } ?: "From: Auto" }) { from = it; result = null }
                Spacer(Modifier.height(6.dp))
                ChipRow(Lang.entries, target, { "To: ${it.label}" }) { to = it; result = null }
                if (source == target) {
                    Text(
                        "The text is already ${source.label}. Pick the other language to translate to.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(status ?: "Working…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Button(
                        onClick = {
                            busy = true; error = null; result = null
                            scope.launch {
                                val r = Translator.translate(plainWorking, source, target) { status = it }
                                busy = false
                                when (r) {
                                    is Translator.Outcome.Ok -> result = r.text
                                    Translator.Outcome.NeedsInternet ->
                                        error = "The ${source.label} and ${target.label} language data has to be downloaded once (about 30 MB each). Connect to the internet and try again. After that, translating works offline."
                                    is Translator.Outcome.Failed -> error = "Could not translate: ${r.reason}"
                                }
                            }
                        },
                        enabled = plainWorking.isNotBlank() && source != target,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Translate to ${target.label}") }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                result?.let { res ->
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Column(Modifier.padding(16.dp)) {
                            Text(target.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer { Text(res, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { working = res; result = null }, modifier = Modifier.weight(1f)) { Text("Use translation") }
                        OutlinedButton(onClick = { working = working.trimEnd() + "\n\n" + res; result = null }, modifier = Modifier.weight(1f)) { Text("Add below") }
                    }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(res))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy translation") }
                }
            } else {
                SectionTitle("Spelling & grammar")
                ChipRow(listOf<Lang?>(null) + Lang.entries, forced, { it?.label ?: "Auto-detect" }) { forced = it }
                SwitchRow("Deeper English grammar check (online)", deep) { deep = it }
                Text(
                    if (deep) "The text is sent to the LanguageTool service to check grammar. Spelling still stays on your phone."
                    else "Everything is checked on your phone. English also has an optional deeper grammar check that needs the internet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                val res = checked
                if (checking || res == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Checking…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val spell = res.issues.count { it.kind == IssueKind.Spelling }
                    val gram = res.issues.count { it.kind == IssueKind.Grammar }
                    Text(
                        if (res.issues.isEmpty()) "No problems found in ${res.lang.label}." else "${res.lang.label}: $spell spelling, $gram grammar",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (res.deepFailed) {
                        Text("The online check could not be reached, so only on-device checks ran.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (res.lang == Lang.Afrikaans) {
                        Text(
                            "Afrikaans: spelling uses a full word list. Grammar checks cover the common slips only.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val fixable = res.issues.filter { it.kind == IssueKind.Spelling && it.suggestions.isNotEmpty() }
                    if (fixable.size > 1) {
                        TextButton(onClick = {
                            var t = working
                            for (i in fixable.sortedByDescending { it.start }) {
                                if (i.end <= t.length && t.substring(i.start, i.end) == i.word) t = t.replaceRange(i.start, i.end, i.suggestions.first())
                            }
                            working = t
                        }) { Text("Fix all spelling with first suggestions") }
                    }
                    Spacer(Modifier.height(6.dp))
                    for (issue in res.issues.take(100)) {
                        IssueCard(
                            issue,
                            onApply = { applyFix(issue, it) },
                            onIgnore = { vm.ignoreIssue(issue.key) },
                            onAddWord = { vm.addUserWord(issue.word); Toast.makeText(ctx, "Added \"${issue.word}\" to your dictionary", Toast.LENGTH_SHORT).show() },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (res.issues.size > 100) Text("…and ${res.issues.size - 100} more", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { vm.updateText(working.trim()); onDismiss() },
                    enabled = working.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(if (request.fromScan) "Use this text" else "Apply to my text") }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: Issue, onApply: (String) -> Unit, onIgnore: () -> Unit, onAddWord: () -> Unit) {
    val spelling = issue.kind == IssueKind.Spelling
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (spelling) "Spelling" else "Grammar",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (spelling) Color(0xFFD32F2F) else Color(0xFF1976D2),
                )
                Spacer(Modifier.width(8.dp))
                Text("“${issue.word.trim()}”", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            }
            Text(issue.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (issue.suggestions.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (s in issue.suggestions) AssistChip(onClick = { onApply(s) }, label = { Text(s.ifEmpty { "(remove)" }) })
                }
            }
            Row {
                TextButton(onClick = onIgnore) { Text("Ignore") }
                if (spelling) TextButton(onClick = onAddWord) { Text("Add to my dictionary") }
            }
        }
    }
}
