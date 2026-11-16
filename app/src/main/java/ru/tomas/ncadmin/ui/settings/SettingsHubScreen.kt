package ru.tomas.ncadmin.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tomas.ncadmin.data.AppContainer
import ru.tomas.ncadmin.data.repo.CompanionStatus
import ru.tomas.ncadmin.ui.common.BackButton
import ru.tomas.ncadmin.ui.common.ncFactory
import ru.tomas.ncadmin.ui.common.userMessage

data class HubUiState(
    val status: CompanionStatus? = null,
    val checking: Boolean = true,
    val error: String? = null,
)

class SettingsHubViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(HubUiState())

    init { check() }

    fun check() {
        viewModelScope.launch {
            state.update { it.copy(checking = true, error = null) }
            try {
                val st = container.requireSession().companion.status()
                state.update { it.copy(status = st, checking = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state.update { it.copy(checking = false, error = e.userMessage()) }
            }
        }
    }

    fun confirmNow() {
        viewModelScope.launch {
            runCatching { container.requireSession().companion.confirm() }
            check()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit,
    onOpenSystem: () -> Unit,
    onOpenApps: () -> Unit,
    vm: SettingsHubViewModel = viewModel(factory = ncFactory { c, _ -> SettingsHubViewModel(c) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Параметры сервера") }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { CompanionCard(state, onRecheck = vm::check, onConfirm = vm::confirmNow) }
            items(SettingsCatalog.sections, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(s.description, maxLines = 2) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                    modifier = Modifier.clickable { onOpenSection(s.id) },
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Приложения") },
                    supportingContent = { Text("Включение и выключение приложений Nextcloud") },
                    leadingContent = { Icon(Icons.Default.Apps, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                    modifier = Modifier.clickable(onClick = onOpenApps),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("config.php") },
                    supportingContent = { Text("Почтовый сервер, логирование, доверенные домены и другие системные параметры (нужен ncadmin_api)") },
                    leadingContent = { Icon(Icons.Default.Code, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                    modifier = Modifier.clickable(onClick = onOpenSystem),
                )
                HorizontalDivider()
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CompanionCard(state: HubUiState, onRecheck: () -> Unit, onConfirm: () -> Unit) {
    val st = state.status
    val ok = st?.installed == true
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.checking -> "Проверяю companion-приложение…"
                        state.error != null -> "Не удалось проверить: ${state.error}"
                        ok -> "ncadmin_api ${st?.version} установлен"
                        else -> "ncadmin_api не установлен"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (ok) {
                    if (st?.confirmed == true) "Сессия подтверждена — запись настроек и управление пользователями доступны."
                    else "Подтверждение будет запрошено автоматически при первой записи."
                } else {
                    "Без него доступно только чтение: Nextcloud требует подтверждения пароля для любых изменений, " +
                        "а app-пароль его дать не может. Установка: server-app/README.md в репозитории."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row {
                TextButton(onClick = onRecheck) { Text("Проверить") }
                if (ok && st?.confirmed != true) TextButton(onClick = onConfirm) { Text("Подтвердить сейчас") }
            }
        }
    }
}
