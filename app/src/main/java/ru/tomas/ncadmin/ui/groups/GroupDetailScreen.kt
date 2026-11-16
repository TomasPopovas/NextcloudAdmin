package ru.tomas.ncadmin.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.snapshotFlow
import ru.tomas.ncadmin.R
import ru.tomas.ncadmin.ui.common.BackButton
import ru.tomas.ncadmin.ui.common.ConfirmDialog
import ru.tomas.ncadmin.ui.common.EmptyBox
import ru.tomas.ncadmin.ui.common.ErrorBox
import ru.tomas.ncadmin.ui.common.LoadingBox
import ru.tomas.ncadmin.ui.common.ncFactory
import ru.tomas.ncadmin.ui.users.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    vm: GroupDetailViewModel = viewModel(key = "group:$groupId", factory = ncFactory { c, _ -> GroupDetailViewModel(c, groupId) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is GroupDetailEvent.Message -> snackbar.showSnackbar(ev.text)
                GroupDetailEvent.Deleted -> onBack()
            }
        }
    }

    val group = state.group

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(group?.displayName ?: groupId, maxLines = 1)
                        if (group != null && group.displayName != group.id) Text(group.id, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (group != null) {
                        IconButton(onClick = { showRename = true }) { Icon(Icons.Default.Edit, stringResource(R.string.group_rename)) }
                        if (!group.isProtected) {
                            IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (group?.canAdd != false) {
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.PersonAdd, stringResource(R.string.group_add_member)) }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error != null -> ErrorBox(state.error!!, onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> Column(Modifier.padding(padding)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.group_members_title) + " (${state.members.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp),
                )
                if (state.members.isEmpty()) {
                    EmptyBox()
                } else {
                    LazyColumn {
                        items(state.members, key = { it.id }) { u ->
                            ListItem(
                                headlineContent = { Text(u.displayNameOrId) },
                                supportingContent = { Text(u.id + (u.email?.let { " · $it" } ?: "")) },
                                leadingContent = {
                                    Icon(if (u.enabled) Icons.Default.Person else Icons.Default.Block, null,
                                        tint = if (u.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                },
                                trailingContent = {
                                    if (group?.canRemove != false) {
                                        IconButton(onClick = { memberToRemove = u.id }) { Icon(Icons.Default.RemoveCircleOutline, null) }
                                    }
                                },
                                modifier = Modifier.clickable { onOpenUser(u.id) },
                            )
                            HorizontalDivider()
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (showRename && group != null) {
        TextInputDialog(
            title = stringResource(R.string.group_rename),
            label = stringResource(R.string.group_display_name),
            initial = group.displayName,
            onConfirm = { vm.rename(it); showRename = false },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete && group != null) {
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            text = stringResource(R.string.group_delete_confirm, group.displayName),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = vm::delete,
            onDismiss = { showDelete = false },
        )
    }
    memberToRemove?.let { uid ->
        ConfirmDialog(
            title = stringResource(R.string.group_members_title),
            text = stringResource(R.string.group_remove_member, uid),
            onConfirm = { vm.removeMember(uid) },
            onDismiss = { memberToRemove = null },
        )
    }
    if (showAdd) {
        var query by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            snapshotFlow { query }.debounce(300).collect { vm.searchCandidates(it) }
        }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.group_add_member)) },
            text = {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.users_search_hint)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    if (state.candidatesLoading) CircularProgressIndicator(Modifier.padding(8.dp))
                    LazyColumn(Modifier.height(300.dp)) {
                        items(state.candidates, key = { it.id }) { u ->
                            ListItem(
                                headlineContent = { Text(u.displayNameOrId) },
                                supportingContent = { Text(u.id) },
                                modifier = Modifier.clickable { vm.addMember(u.id); showAdd = false },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
