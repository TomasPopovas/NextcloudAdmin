package ru.tomas.ncadmin.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.tomas.ncadmin.R
import ru.tomas.ncadmin.data.AppContainer
import ru.tomas.ncadmin.data.repo.NcApp
import ru.tomas.ncadmin.ui.common.BackButton
import ru.tomas.ncadmin.ui.common.ConfirmDialog
import ru.tomas.ncadmin.ui.common.ErrorBox
import ru.tomas.ncadmin.ui.common.LoadingBox
import ru.tomas.ncadmin.ui.common.ncFactory
import ru.tomas.ncadmin.ui.common.userMessage

enum class AppsFilter { ALL, ENABLED, DISABLED }

data class AppsUiState(
    val apps: List<NcApp> = emptyList(),
    val filter: AppsFilter = AppsFilter.ALL,
    val query: String = "",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val visible: List<NcApp>
        get() = apps.filter {
            when (filter) { AppsFilter.ALL -> true; AppsFilter.ENABLED -> it.enabled; AppsFilter.DISABLED -> !it.enabled }
        }.filter { query.isBlank() || it.id.contains(query, true) || it.name.contains(query, true) }
}

class AppsViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(AppsUiState())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private val apps get() = container.requireSession().apps

    init { load() }

    fun setFilter(f: AppsFilter) = state.update { it.copy(filter = f) }
    fun setQuery(q: String) = state.update { it.copy(query = q) }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            try {
                val enabled = apps.ids(true)
                val disabled = apps.ids(false)
                // Показываем список сразу по идентификаторам, детали подтягиваем параллельно
                val skeleton = enabled.map { NcApp(it, it, "", "", true) } + disabled.map { NcApp(it, it, "", "", false) }
                state.update { it.copy(loading = false, apps = skeleton.sortedBy { a -> a.id }) }
                val sem = Semaphore(6)
                val detailed = coroutineScope {
                    skeleton.map { a -> async { sem.withPermit { apps.info(a.id, a.enabled) } } }.map { it.await() }
                }
                state.update { it.copy(apps = detailed.sortedBy { a -> a.name.lowercase() }) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun toggle(app: NcApp, enable: Boolean) {
        viewModelScope.launch {
            state.update { it.copy(busy = true) }
            try {
                if (enable) apps.enable(app.id) else apps.disable(app.id)
                state.update { s -> s.copy(apps = s.apps.map { if (it.id == app.id) it.copy(enabled = enable) else it }) }
                _messages.send(if (enable) "Включено: ${app.name}" else "Выключено: ${app.name}")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _messages.send(e.userMessage())
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onBack: () -> Unit,
    vm: AppsViewModel = viewModel(factory = ncFactory { c, _ -> AppsViewModel(c) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDisable by remember { mutableStateOf<NcApp?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Приложения") },
                navigationIcon = { BackButton(onBack) },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query, onValueChange = vm::setQuery, singleLine = true,
                placeholder = { Text(stringResource(R.string.action_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.filter == AppsFilter.ALL, onClick = { vm.setFilter(AppsFilter.ALL) }, label = { Text("Все") })
                FilterChip(selected = state.filter == AppsFilter.ENABLED, onClick = { vm.setFilter(AppsFilter.ENABLED) }, label = { Text("Включённые") })
                FilterChip(selected = state.filter == AppsFilter.DISABLED, onClick = { vm.setFilter(AppsFilter.DISABLED) }, label = { Text("Выключенные") })
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error!!, onRetry = vm::load)
                else -> LazyColumn {
                    items(state.visible, key = { it.id }) { app ->
                        ListItem(
                            headlineContent = { Text(app.name) },
                            supportingContent = {
                                Column {
                                    if (app.summary.isNotBlank()) Text(app.summary, maxLines = 2)
                                    Text(listOf(app.id, app.version).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            },
                            trailingContent = {
                                Switch(
                                    checked = app.enabled,
                                    enabled = !state.busy && !app.isCore,
                                    onCheckedChange = { on -> if (on) vm.toggle(app, true) else pendingDisable = app },
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    pendingDisable?.let { app ->
        ConfirmDialog(
            title = "Выключить приложение",
            text = "Выключить ${app.name} (${app.id})? Данные приложения не удаляются.",
            destructive = true,
            onConfirm = { vm.toggle(app, false) },
            onDismiss = { pendingDisable = null },
        )
    }
}
