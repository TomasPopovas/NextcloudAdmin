package ru.tomas.ncadmin.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tomas.ncadmin.R
import ru.tomas.ncadmin.data.AppContainer
import ru.tomas.ncadmin.data.api.NcGroup
import ru.tomas.ncadmin.ui.common.BackButton
import ru.tomas.ncadmin.ui.common.ncFactory
import ru.tomas.ncadmin.ui.common.userMessage

data class CreateUserState(
    val userId: String = "",
    val password: String = "",
    val displayName: String = "",
    val email: String = "",
    val quotaMode: String = "default", // default | none | custom
    val quotaCustom: String = "10 GB",
    val groups: Set<String> = emptySet(),
    val allGroups: List<NcGroup> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val createdId: String? = null,
) {
    val valid: Boolean
        get() = userId.isNotBlank() && (password.isEmpty() || password.length >= 8) && (password.isNotEmpty() || email.contains("@"))
}

class CreateUserViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(CreateUserState())

    init {
        viewModelScope.launch {
            runCatching { container.requireSession().groups.list() }
                .onSuccess { g -> state.update { it.copy(allGroups = g) } }
        }
    }

    fun update(block: CreateUserState.() -> CreateUserState) = state.update { it.block().copy(error = null) }

    fun toggleGroup(id: String) = update { copy(groups = if (id in groups) groups - id else groups + id) }

    fun create() {
        val s = state.value
        if (!s.valid) return
        viewModelScope.launch {
            state.update { it.copy(busy = true, error = null) }
            try {
                val quota = when (s.quotaMode) { "default" -> null; "none" -> "none"; else -> s.quotaCustom.trim() }
                val id = container.requireSession().users.create(
                    userId = s.userId.trim(),
                    password = s.password.ifEmpty { null },
                    displayName = s.displayName.trim().ifEmpty { null },
                    email = s.email.trim().ifEmpty { null },
                    groups = s.groups.toList(),
                    subadmin = emptyList(),
                    quota = quota,
                    language = null,
                )
                state.update { it.copy(busy = false, createdId = id) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateUserScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    vm: CreateUserViewModel = viewModel(factory = ncFactory { c, _ -> CreateUserViewModel(c) }),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(s.createdId) { s.createdId?.let(onCreated) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.users_new)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = s.userId, onValueChange = { v -> vm.update { copy(userId = v) } },
                label = { Text(stringResource(R.string.user_login)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            OutlinedTextField(
                value = s.displayName, onValueChange = { v -> vm.update { copy(displayName = v) } },
                label = { Text(stringResource(R.string.user_display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.email, onValueChange = { v -> vm.update { copy(email = v) } },
                label = { Text(stringResource(R.string.user_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                value = s.password, onValueChange = { v -> vm.update { copy(password = v) } },
                label = { Text(stringResource(R.string.user_password)) },
                supportingText = { Text(stringResource(R.string.user_password_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    androidx.compose.material3.TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Скрыть" else "Показать") }
                },
            )

            Text(stringResource(R.string.user_quota), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.quotaMode == "default", onClick = { vm.update { copy(quotaMode = "default") } }, label = { Text(stringResource(R.string.user_quota_default)) })
                FilterChip(selected = s.quotaMode == "none", onClick = { vm.update { copy(quotaMode = "none") } }, label = { Text(stringResource(R.string.user_quota_unlimited)) })
                FilterChip(selected = s.quotaMode == "custom", onClick = { vm.update { copy(quotaMode = "custom") } }, label = { Text("Своя") })
            }
            if (s.quotaMode == "custom") {
                OutlinedTextField(value = s.quotaCustom, onValueChange = { v -> vm.update { copy(quotaCustom = v) } }, singleLine = true, label = { Text("10 GB / 500 MB / 1 TB") }, modifier = Modifier.fillMaxWidth())
            }

            Text(stringResource(R.string.user_groups), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.allGroups.forEach { g ->
                    FilterChip(selected = g.id in s.groups, onClick = { vm.toggleGroup(g.id) }, label = { Text(g.displayName) })
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::create, enabled = s.valid && !s.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_create))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
