package ru.tomas.ncadmin.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.tomas.ncadmin.R
import ru.tomas.ncadmin.data.api.NcUser
import ru.tomas.ncadmin.ui.common.BackButton
import ru.tomas.ncadmin.ui.common.ConfirmDialog
import ru.tomas.ncadmin.ui.common.ErrorBox
import ru.tomas.ncadmin.ui.common.LoadingBox
import ru.tomas.ncadmin.ui.common.formatBytes
import ru.tomas.ncadmin.ui.common.formatDateTime
import ru.tomas.ncadmin.ui.common.ncFactory

private enum class Dialog { NONE, DELETE, PASSWORD, QUOTA, ADD_GROUP, ADD_SUBADMIN, EDIT_FIELD }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserDetailScreen(
    userId: String,
    onBack: () -> Unit,
    vm: UserDetailViewModel = viewModel(key = "user:$userId", factory = ncFactory { c, _ -> UserDetailViewModel(c, userId) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    var editKey by remember { mutableStateOf("") }
    var editLabel by remember { mutableStateOf("") }
    var editInitial by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is UserDetailEvent.Message -> snackbar.showSnackbar(ev.text)
                UserDetailEvent.Deleted -> onBack()
            }
        }
    }

    fun openEdit(key: String, label: String, initial: String) {
        editKey = key; editLabel = label; editInitial = initial; dialog = Dialog.EDIT_FIELD
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.user?.displayNameOrId ?: userId, maxLines = 1) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    val user = state.user
                    if (user != null) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (user.enabled) R.string.user_disable else R.string.user_enable)) },
                                leadingIcon = { Icon(if (user.enabled) Icons.Default.Block else Icons.Default.CheckCircle, null) },
                                onClick = { menuOpen = false; vm.setEnabled(!user.enabled) },
                            )
                            if (user.canSetPassword) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.user_set_password)) },
                                    leadingIcon = { Icon(Icons.Default.Key, null) },
                                    onClick = { menuOpen = false; dialog = Dialog.PASSWORD },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.user_send_welcome)) },
                                leadingIcon = { Icon(Icons.Default.Email, null) },
                                onClick = { menuOpen = false; vm.sendWelcome() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.user_delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; dialog = Dialog.DELETE },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val user = state.user
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            user == null -> ErrorBox(state.error ?: "—", onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                if (!user.enabled) {
                    AssistChip(
                        onClick = { vm.setEnabled(true) },
                        label = { Text(stringResource(R.string.users_disabled_badge)) },
                        leadingIcon = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
                    )
                }

                // --- Профиль ---
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        InfoRow(stringResource(R.string.user_login), user.id)
                        EditableRow(stringResource(R.string.user_display_name), user.displayName, enabled = user.canSetDisplayName) {
                            openEdit("displayname", it, user.displayName)
                        }
                        EditableRow(stringResource(R.string.user_email), user.email ?: "") { openEdit("email", it, user.email ?: "") }
                        EditableRow(stringResource(R.string.user_phone), user.phone ?: "") { openEdit("phone", it, user.phone ?: "") }
                        EditableRow(stringResource(R.string.user_address), user.address ?: "") { openEdit("address", it, user.address ?: "") }
                        EditableRow(stringResource(R.string.user_website), user.website ?: "") { openEdit("website", it, user.website ?: "") }
                        InfoRow(stringResource(R.string.user_language), listOfNotNull(user.language, user.locale).joinToString(" / ").ifBlank { "—" })
                        InfoRow(stringResource(R.string.user_backend), user.backend ?: "—")
                        InfoRow(
                            stringResource(R.string.user_last_login),
                            if (user.lastLoginMillis > 0) formatDateTime(user.lastLoginMillis) else stringResource(R.string.user_last_login_never),
                        )
                    }
                }

                // --- Хранилище / квота ---
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.user_storage), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { dialog = Dialog.QUOTA }) {
                                Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.user_quota))
                            }
                        }
                        val q = user.quota
                        val usedStr = formatBytes(q.used)
                        if (q.isUnlimited || q.quota == null && (q.total == null || q.total <= 0)) {
                            Text(stringResource(R.string.user_storage_used_unlimited, usedStr))
                            Text(stringResource(R.string.user_quota) + ": " + stringResource(if (q.isUnlimited) R.string.user_quota_unlimited else R.string.user_quota_default),
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            val total = q.quota?.takeIf { it > 0 } ?: q.total ?: 0
                            val pct = (q.relative ?: (if (total > 0) q.used * 100.0 / total else 0.0)).toInt().coerceIn(0, 100)
                            Text(stringResource(R.string.user_storage_used, usedStr, formatBytes(total), pct))
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                // --- Группы ---
                ChipSection(
                    title = stringResource(R.string.user_groups),
                    items = user.groups,
                    onAdd = { dialog = Dialog.ADD_GROUP },
                    onRemove = { vm.removeFromGroup(it) },
                    removeConfirmText = { stringResource(R.string.user_remove_from_group, it) },
                )

                // --- Субадмин ---
                ChipSection(
                    title = stringResource(R.string.user_subadmin),
                    items = user.subadmin,
                    onAdd = { dialog = Dialog.ADD_SUBADMIN },
                    onRemove = { vm.removeSubadmin(it) },
                    removeConfirmText = { "Снять администрирование группы $it?" },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val user = state.user ?: return
    when (dialog) {
        Dialog.NONE -> Unit
        Dialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.user_delete),
            text = stringResource(R.string.user_delete_confirm, user.id),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = vm::delete,
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.PASSWORD -> TextInputDialog(
            title = stringResource(R.string.user_set_password),
            label = stringResource(R.string.user_new_password),
            initial = "",
            password = true,
            onConfirm = { vm.setPassword(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.EDIT_FIELD -> TextInputDialog(
            title = editLabel,
            label = editLabel,
            initial = editInitial,
            onConfirm = { vm.setField(editKey, it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.QUOTA -> QuotaDialog(
            current = user,
            onConfirm = { vm.setQuota(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.ADD_GROUP -> PickGroupDialog(
            title = stringResource(R.string.user_add_to_group),
            options = state.allGroups.map { it.id }.filterNot { it in user.groups },
            onPick = { vm.addToGroup(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.ADD_SUBADMIN -> PickGroupDialog(
            title = stringResource(R.string.user_make_subadmin),
            options = state.allGroups.map { it.id }.filterNot { it in user.subadmin || it == "admin" },
            onPick = { vm.addSubadmin(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EditableRow(label: String, value: String, enabled: Boolean = true, onEdit: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
        }
        if (enabled) IconButton(onClick = { onEdit(label) }) { Icon(Icons.Default.Edit, null) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(
    title: String,
    items: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    removeConfirmText: @Composable (String) -> String,
) {
    var pendingRemove by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.action_add)) }
            }
            if (items.isEmpty()) {
                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { g ->
                        InputChip(
                            selected = false,
                            onClick = { pendingRemove = g },
                            label = { Text(g) },
                            trailingIcon = { Icon(Icons.Default.Close, null) },
                        )
                    }
                }
            }
        }
    }
    pendingRemove?.let { g ->
        ConfirmDialog(
            title = title,
            text = removeConfirmText(g),
            onConfirm = { onRemove(g) },
            onDismiss = { pendingRemove = null },
        )
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    password: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = !password || value.length >= 8) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun QuotaDialog(current: NcUser, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(if (current.quota.isUnlimited) "none" else if (current.quota.quota == null) "default" else "custom") }
    var custom by remember { mutableStateOf(current.quota.quota?.takeIf { it > 0 }?.let { formatBytes(it) } ?: "10 GB") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.user_quota)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "default", onClick = { mode = "default" }, label = { Text(stringResource(R.string.user_quota_default)) })
                FilterChip(selected = mode == "none", onClick = { mode = "none" }, label = { Text(stringResource(R.string.user_quota_unlimited)) })
                FilterChip(selected = mode == "custom", onClick = { mode = "custom" }, label = { Text(stringResource(R.string.user_quota_custom)) })
                if (mode == "custom") {
                    OutlinedTextField(value = custom, onValueChange = { custom = it }, singleLine = true, label = { Text("10 GB / 500 MB / 1 TB") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (mode == "custom") custom.trim() else mode) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun PickGroupDialog(title: String, options: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val shown = options.filter { it.contains(filter, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true, placeholder = { Text(stringResource(R.string.action_search)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (shown.isEmpty()) Text(stringResource(R.string.label_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(280.dp)) {
                    items(shown.size) { i ->
                        val g = shown[i]
                        OutlinedButton(onClick = { onPick(g) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(g) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
