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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import ru.tomas.ncadmin.R
import ru.tomas.ncadmin.data.AppContainer
import ru.tomas.ncadmin.data.repo.SystemValue
import ru.tomas.ncadmin.ui.common.BackButton
import ru.tomas.ncadmin.ui.common.ErrorBox
import ru.tomas.ncadmin.ui.common.LoadingBox
import ru.tomas.ncadmin.ui.common.ncFactory
import ru.tomas.ncadmin.ui.common.userMessage

/** Группировка ключей config.php для экрана. */
private val SYSTEM_GROUPS: List<Pair<String, (String) -> Boolean>> = listOf(
    "Почтовый сервер" to { k -> k.startsWith("mail_") },
    "Безопасность и сессии" to { k ->
        k.startsWith("auth.") || k.startsWith("ratelimit.") || k in setOf("session_lifetime", "remember_login_cookie_lifetime", "token_auth_enforced")
    },
    "Файлы" to { k -> k.contains("retention") || k.startsWith("preview") || k == "enable_previews" || k == "filelocking.enabled" || k.contains("directory") },
    "Общие" to { _: String -> true },
)

private val KEY_TITLES = mapOf(
    "mail_smtpmode" to "Режим отправки (smtp / sendmail / qmail)",
    "mail_sendmailmode" to "Режим sendmail (smtp / pipe)",
    "mail_smtphost" to "SMTP-сервер",
    "mail_smtpport" to "SMTP-порт",
    "mail_smtpsecure" to "Шифрование (пусто / ssl / tls)",
    "mail_smtpauth" to "Требуется аутентификация",
    "mail_smtpname" to "Имя пользователя SMTP",
    "mail_smtppassword" to "Пароль SMTP",
    "mail_from_address" to "Адрес отправителя (часть до @)",
    "mail_domain" to "Домен отправителя",
    "mail_smtpdebug" to "Отладка SMTP в лог",
    "mail_smtptimeout" to "Таймаут SMTP, сек",
    "maintenance" to "Режим обслуживания (только чтение)",
    "maintenance_window_start" to "Начало окна обслуживания (час UTC)",
    "loglevel" to "Уровень логирования (0 debug … 4 fatal)",
    "log_rotate_size" to "Ротация лога, байт (0 — выкл.)",
    "trusted_domains" to "Доверенные домены",
    "overwrite.cli.url" to "URL для cron и ссылок в письмах",
    "default_language" to "Язык по умолчанию",
    "default_locale" to "Локаль по умолчанию",
    "default_phone_region" to "Регион телефонов (ISO 3166-1)",
    "defaultapp" to "Приложение по умолчанию",
    "skeletondirectory" to "Каталог-шаблон новых пользователей",
    "templatedirectory" to "Каталог шаблонов документов",
    "knowledgebaseenabled" to "Показывать базу знаний",
    "allow_user_to_change_display_name" to "Разрешить менять отображаемое имя",
    "lost_password_link" to "Ссылка восстановления пароля",
    "has_internet_connection" to "Есть доступ в интернет",
    "updatechecker" to "Проверять обновления",
    "updater.release.channel" to "Канал обновлений",
    "upgrade.disable-web" to "Запретить обновление через веб",
    "auth.bruteforce.protection.enabled" to "Защита от перебора паролей",
    "ratelimit.protection.enabled" to "Ограничение частоты запросов",
    "session_lifetime" to "Время жизни сессии, сек",
    "remember_login_cookie_lifetime" to "Время жизни «Запомнить меня», сек",
    "token_auth_enforced" to "Только app-пароли для клиентов",
    "auth.webauthn.enabled" to "Вход по WebAuthn",
    "trashbin_retention_obligation" to "Хранение корзины (auto, D, auto/D, D/auto)",
    "versions_retention_obligation" to "Хранение версий",
    "filelocking.enabled" to "Блокировка файлов",
    "enable_previews" to "Генерировать превью",
    "preview_max_x" to "Макс. ширина превью",
    "preview_max_y" to "Макс. высота превью",
)

data class SystemUiState(
    val values: List<SystemValue> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val companionMissing: Boolean = false,
)

class SystemConfigViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(SystemUiState())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private val companion get() = container.requireSession().companion

    init { load() }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            try {
                val st = companion.status()
                if (!st.installed) {
                    state.update { it.copy(loading = false, companionMissing = true) }
                    return@launch
                }
                val values = companion.systemConfig()
                state.update { it.copy(loading = false, values = values, companionMissing = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    private fun act(message: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            state.update { it.copy(busy = true) }
            try {
                block()
                message?.let { _messages.send(it) }
                state.update { it.copy(values = companion.systemConfig()) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _messages.send(e.userMessage())
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    fun set(key: String, value: String) = act("Сохранено: $key") { companion.setSystem(key, value) }
    fun reset(key: String) = act("Сброшено: $key") { companion.deleteSystem(key) }
    fun testMail() = act(null) {
        val to = companion.testMail()
        _messages.send("Тестовое письмо отправлено на $to")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemConfigScreen(
    onBack: () -> Unit,
    vm: SystemConfigViewModel = viewModel(factory = ncFactory { c, _ -> SystemConfigViewModel(c) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<SystemValue?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("config.php") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = vm::testMail, enabled = !state.busy && !state.companionMissing) {
                        Icon(Icons.Default.Email, "Тестовое письмо")
                    }
                    IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.companionMissing -> ErrorBox(
                "Параметры config.php доступны только через companion-приложение ncadmin_api. Установите его на сервере (server-app/README.md в репозитории).",
                onRetry = vm::load, modifier = Modifier.padding(padding),
            )
            state.error != null -> ErrorBox(state.error!!, onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> Column(Modifier.padding(padding)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                val remaining = state.values.toMutableList()
                val grouped = SYSTEM_GROUPS.map { (title, pred) ->
                    val part = remaining.filter { pred(it.key) }
                    remaining.removeAll(part)
                    title to part
                }.filter { it.second.isNotEmpty() }
                LazyColumn {
                    grouped.forEach { (title, items) ->
                        item(key = "h:$title") {
                            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                        }
                        items(items, key = { it.key }) { sv ->
                            SystemRow(sv, enabled = !state.busy && sv.key != "maintenance",
                                onToggle = { vm.set(sv.key, it.toString()) }, onEdit = { editing = sv })
                            HorizontalDivider()
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    editing?.let { sv ->
        var text by remember(sv.key) { mutableStateOf(displayValue(sv, forEdit = true)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(KEY_TITLES[sv.key] ?: sv.key) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, label = { Text(sv.key) },
                        singleLine = sv.type != "array", minLines = if (sv.type == "array") 3 else 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (sv.type) {
                            "array" -> "JSON-массив, например [\"cloud.example.ru\", \"10.0.0.5\"]"
                            "int" -> "Целое число"
                            "secret" -> "Текущее значение скрыто; введите новое"
                            else -> "Строка"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (!sv.isSet) Text("Ключ сейчас не задан — действует значение по умолчанию", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.set(sv.key, text.trim()); editing = null },
                    enabled = sv.type != "int" || text.trim().toLongOrNull() != null) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                Row {
                    if (sv.isSet) TextButton(onClick = { vm.reset(sv.key); editing = null }) { Text("Удалить ключ") }
                    TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
}

private fun displayValue(sv: SystemValue, forEdit: Boolean = false): String = when (val v = sv.value) {
    null, JsonNull -> if (forEdit) (if (sv.type == "array") "[]" else "") else "—"
    is JsonPrimitive -> if (sv.type == "secret" && forEdit) "" else (v.contentOrNull ?: v.toString())
    is JsonArray -> if (forEdit) v.toString() else v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ").ifBlank { "[]" }
    else -> v.toString()
}

@Composable
private fun SystemRow(sv: SystemValue, enabled: Boolean, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val isBool = sv.type == "bool"
    ListItem(
        headlineContent = { Text(KEY_TITLES[sv.key] ?: sv.key) },
        supportingContent = {
            Column {
                if (!isBool) Text(displayValue(sv) + if (!sv.isSet) "  (по умолчанию)" else "", maxLines = 3)
                Text(sv.key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        trailingContent = {
            if (isBool) {
                val checked = (sv.value as? JsonPrimitive)?.contentOrNull?.let { it == "true" || it == "1" } ?: false
                Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
            }
        },
        modifier = if (isBool || !enabled) Modifier else Modifier.clickable(onClick = onEdit),
    )
}
