package ru.tomas.ncadmin.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.tomas.ncadmin.R
import ru.tomas.ncadmin.data.api.NcUser
import ru.tomas.ncadmin.ui.common.BackButton
import ru.tomas.ncadmin.ui.common.EmptyBox
import ru.tomas.ncadmin.ui.common.ErrorBox
import ru.tomas.ncadmin.ui.common.LoadingBox
import ru.tomas.ncadmin.ui.common.ncFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onCreateUser: () -> Unit,
    vm: UsersViewModel = viewModel(factory = ncFactory { c, _ -> UsersViewModel(c) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.users_title)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = vm::reload) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateUser) { Icon(Icons.Default.Add, stringResource(R.string.users_new)) }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text(stringResource(R.string.users_search_hint)) },
                singleLine = true,
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, null) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.filter == UserFilter.ALL, onClick = { vm.onFilterChange(UserFilter.ALL) }, label = { Text(stringResource(R.string.users_filter_all)) })
                FilterChip(selected = state.filter == UserFilter.ENABLED, onClick = { vm.onFilterChange(UserFilter.ENABLED) }, label = { Text(stringResource(R.string.users_filter_enabled)) })
                FilterChip(selected = state.filter == UserFilter.DISABLED, onClick = { vm.onFilterChange(UserFilter.DISABLED) }, label = { Text(stringResource(R.string.users_filter_disabled)) })
            }

            when {
                state.loading -> LoadingBox()
                state.error != null && state.users.isEmpty() -> ErrorBox(state.error!!, onRetry = vm::reload)
                state.visible.isEmpty() -> EmptyBox()
                else -> LazyColumn {
                    items(state.visible, key = { it.id }) { user ->
                        UserRow(user, onClick = { onOpenUser(user.id) })
                        HorizontalDivider()
                    }
                    if (!state.endReached) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (state.loadingMore) {
                                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
                                } else {
                                    TextButton(onClick = vm::loadMore) { Text(stringResource(R.string.users_load_more)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: NcUser, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(user.displayNameOrId) },
        supportingContent = {
            Text(buildString {
                append(user.id)
                user.email?.let { append(" · ").append(it) }
            }, maxLines = 1)
        },
        leadingContent = {
            Icon(
                if (user.enabled) Icons.Default.Person else Icons.Default.Block,
                null,
                tint = if (user.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            if (!user.enabled) {
                AssistChip(onClick = onClick, label = { Text(stringResource(R.string.users_disabled_badge)) })
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
