package ru.tomas.ncadmin.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.tomas.ncadmin.R
import ru.tomas.ncadmin.ui.common.BackButton
import ru.tomas.ncadmin.ui.common.ErrorBox
import ru.tomas.ncadmin.ui.common.LoadingBox
import ru.tomas.ncadmin.ui.common.ncFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(
    sectionId: String,
    onBack: () -> Unit,
) {
    val section = remember(sectionId) { SettingsCatalog.byId(sectionId) }
    if (section == null) { ErrorBox("Неизвестный раздел: $sectionId"); return }
    val vm: SectionViewModel = viewModel(key = "section:$sectionId", factory = ncFactory { c, _ -> SectionViewModel(c, section) })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<SettingSpec?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                navigationIcon = { BackButton(onBack) },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) } },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error != null -> ErrorBox(state.error!!, onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> Column(Modifier.padding(padding)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn {
                    item {
                        Text(section.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                    items(section.specs, key = { it.id }) { spec ->
                        val value = state.values[spec.id] ?: spec.default
                        SettingRow(
                            spec = spec,
                            value = value,
                            enabled = !state.busy && !spec.readOnly,
                            onToggle = { vm.setBool(spec, it) },
                            onEdit = { editing = spec },
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    editing?.let { spec ->
        val value = state.values[spec.id] ?: spec.default
        when (spec.type) {
            SettingType.GROUPS -> GroupsPickerDialog(
                title = spec.title,
                allGroups = state.groups,
                selected = SectionViewModel.parseGroups(value),
                onConfirm = { vm.setGroups(spec, it); editing = null },
                onDismiss = { editing = null },
            )
            SettingType.CHOICE -> ChoiceDialog(
                title = spec.title, choices = spec.choices, current = value,
                onPick = { vm.set(spec, it); editing = null },
                onDismiss = { editing = null },
            )
            else -> ValueEditDialog(
                spec = spec, initial = value,
                onConfirm = { vm.set(spec, it); editing = null },
                onReset = { vm.reset(spec); editing = null },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun SettingRow(
    spec: SettingSpec,
    value: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val subtitle = when (spec.type) {
        SettingType.GROUPS -> SectionViewModel.parseGroups(value).let { if (it.isEmpty()) "—" else it.joinToString(", ") }
        SettingType.CHOICE -> spec.choices.firstOrNull { it.first == value }?.second ?: value
        SettingType.YES_NO, SettingType.BOOL, SettingType.ONE_ZERO -> null
        else -> value.ifBlank { "—" }
    }
    ListItem(
        headlineContent = { Text(spec.title) },
        supportingContent = {
            Column {
                subtitle?.let { Text(it, maxLines = 3) }
                Text(spec.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        trailingContent = {
            if (spec.isSwitch) {
                Switch(checked = spec.decodeBool(value), onCheckedChange = onToggle, enabled = enabled)
            }
        },
        modifier = if (spec.isSwitch || !enabled) Modifier else Modifier.clickable(onClick = onEdit),
    )
}

@Composable
private fun ValueEditDialog(
    spec: SettingSpec,
    initial: String,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(spec.id) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = {
            Column {
                spec.description?.let { Text(it, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = spec.type != SettingType.MULTILINE,
                    minLines = if (spec.type == SettingType.MULTILINE) 4 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = if (spec.type == SettingType.INT) KeyboardType.Number else KeyboardType.Text),
                    label = { Text(spec.key) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text("По умолчанию: ${spec.default.ifBlank { "(пусто)" }}", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = spec.type != SettingType.INT || text.trim().toIntOrNull() != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("Сбросить") }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<Pair<String, String>>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        supportingContent = { Text(value) },
                        trailingContent = { if (value == current) Text("✓") },
                        modifier = Modifier.clickable { onPick(value) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun GroupsPickerDialog(
    title: String,
    allGroups: List<String>,
    selected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember { mutableStateOf(selected.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                if (allGroups.isEmpty()) Text(stringResource(R.string.label_empty))
                allGroups.forEach { g ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            chosen.value = if (g in chosen.value) chosen.value - g else chosen.value + g
                        },
                    ) {
                        Checkbox(checked = g in chosen.value, onCheckedChange = null)
                        Text(g, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(chosen.value.toList()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
