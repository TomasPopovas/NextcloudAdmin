package ru.tomas.ncadmin.ui.login

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.tomas.ncadmin.R
import ru.tomas.ncadmin.data.auth.Account
import ru.tomas.ncadmin.ui.common.ConfirmDialog
import ru.tomas.ncadmin.ui.common.ncFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    vm: LoginViewModel = viewModel(factory = ncFactory { c, _ -> LoginViewModel(c) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var accountToRemove by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is LoginEvent.OpenUrl -> {
                    val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
                    intent.launchUrl(context, Uri.parse(ev.url))
                }
                LoginEvent.LoggedIn -> onLoggedIn()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.login_title)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = vm::onServerUrlChange,
                    label = { Text(stringResource(R.string.login_server_label)) },
                    placeholder = { Text(stringResource(R.string.login_server_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = state.phase is LoginPhase.Idle || state.phase is LoginPhase.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                when (val phase = state.phase) {
                    LoginPhase.Idle, is LoginPhase.Error -> {
                        if (phase is LoginPhase.Error) {
                            Text(phase.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Button(onClick = vm::startLogin, modifier = Modifier.fillMaxWidth(), enabled = state.serverUrl.isNotBlank()) {
                            Text(stringResource(R.string.login_button))
                        }
                    }
                    LoginPhase.Starting, LoginPhase.Verifying, is LoginPhase.WaitingBrowser -> {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (phase is LoginPhase.Verifying) "Проверяю доступ…" else stringResource(R.string.login_waiting),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (phase is LoginPhase.WaitingBrowser) {
                                    TextButton(onClick = {
                                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(phase.loginUrl))
                                    }) { Text("Открыть браузер ещё раз") }
                                }
                                TextButton(onClick = vm::cancelLogin) { Text(stringResource(R.string.login_cancel)) }
                            }
                        }
                    }
                }
            }
            if (state.accounts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.login_accounts_title), style = MaterialTheme.typography.titleMedium)
                }
                items(state.accounts, key = { it.id }) { acc ->
                    ListItem(
                        headlineContent = { Text(acc.host) },
                        supportingContent = {
                            Text(buildString {
                                append(acc.loginName)
                                if (acc.serverVersion.isNotBlank()) append(" · NC ").append(acc.serverVersion)
                            })
                        },
                        leadingContent = { Icon(Icons.Default.Cloud, null, Modifier.size(28.dp)) },
                        trailingContent = {
                            IconButton(onClick = { accountToRemove = acc }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.login_remove_account))
                            }
                        },
                        modifier = Modifier.clickable { vm.selectAccount(acc.id) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    accountToRemove?.let { acc ->
        ConfirmDialog(
            title = stringResource(R.string.login_remove_account),
            text = stringResource(R.string.login_remove_confirm, acc.host),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.removeAccount(acc.id) },
            onDismiss = { accountToRemove = null },
        )
    }
}
