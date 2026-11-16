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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import ru.tomas.ncadmin.R
import ru.tomas.ncadmin.ui.common.BackButton
import ru.tomas.ncadmin.ui.common.EmptyBox
import ru.tomas.ncadmin.ui.common.ErrorBox
import ru.tomas.ncadmin.ui.common.LoadingBox
import ru.tomas.ncadmin.ui.common.ncFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    vm: GroupsViewModel = viewModel(factory = ncFactory { c, _ -> GroupsViewModel(c) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                navigationIcon = { BackButton(onBack) },
                actions = { IconButton(onClick = vm::reload) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, stringResource(R.string.groups_new)) }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text(stringResource(R.string.action_search)) },
                singleLine = true,
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error!!, onRetry = vm::reload)
                state.visible.isEmpty() -> EmptyBox()
                else -> LazyColumn {
                    items(state.visible, key = { it.id }) { g ->
                        ListItem(
                            headlineContent = { Text(g.displayName) },
                            supportingContent = {
                                Text(buildString {
                                    if (g.displayName != g.id) append(g.id).append(" · ")
                                    append(stringResource(R.string.group_members, g.userCount))
                                    if (g.disabledCount > 0) append(" (заблок.: ${g.disabledCount})")
                                })
                            },
                            leadingContent = {
                                Icon(if (g.isProtected) Icons.Default.Shield else Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable { onOpenGroup(g.id) },
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showCreate) {
        CreateGroupDialog(
            onCreate = { id, name -> vm.create(id, name); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun CreateGroupDialog(onCreate: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_new)) },
        text = {
            Column {
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text(stringResource(R.string.group_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.group_display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(id, name.ifBlank { null }) }, enabled = id.isNotBlank()) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
