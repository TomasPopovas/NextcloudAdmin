package ru.tomas.ncadmin.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tomas.ncadmin.data.AppContainer
import ru.tomas.ncadmin.data.api.ServerInfo
import ru.tomas.ncadmin.data.auth.Account
import ru.tomas.ncadmin.ui.common.userMessage

sealed interface LoginPhase {
    data object Idle : LoginPhase
    data object Starting : LoginPhase
    data class WaitingBrowser(val loginUrl: String) : LoginPhase
    data object Verifying : LoginPhase
    data class Error(val message: String) : LoginPhase
}

sealed interface LoginEvent {
    data class OpenUrl(val url: String) : LoginEvent
    data object LoggedIn : LoginEvent
}

data class LoginUiState(
    val serverUrl: String = "",
    val phase: LoginPhase = LoginPhase.Idle,
    val accounts: List<Account> = emptyList(),
    val currentId: String? = null,
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(accounts = container.accounts.accounts.value, currentId = container.accounts.currentId.value))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loginJob: Job? = null

    init {
        viewModelScope.launch { container.accounts.accounts.collect { list -> _state.update { it.copy(accounts = list) } } }
        viewModelScope.launch { container.accounts.currentId.collect { id -> _state.update { it.copy(currentId = id) } } }
    }

    fun onServerUrlChange(v: String) = _state.update { it.copy(serverUrl = v, phase = LoginPhase.Idle) }

    fun startLogin() {
        val url = normalizeUrl(_state.value.serverUrl) ?: run {
            _state.update { it.copy(phase = LoginPhase.Error("Некорректный адрес сервера")) }
            return
        }
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            try {
                _state.update { it.copy(phase = LoginPhase.Starting) }
                val started = container.loginFlow.start(url)
                _state.update { it.copy(phase = LoginPhase.WaitingBrowser(started.loginUrl)) }
                _events.send(LoginEvent.OpenUrl(started.loginUrl))

                val result = container.loginFlow.poll(started)
                _state.update { it.copy(phase = LoginPhase.Verifying) }

                val serverUrl = result.server.ifBlank { url }.trimEnd('/')
                val candidate = Account(
                    id = Account.makeId(serverUrl, result.loginName),
                    serverUrl = serverUrl,
                    loginName = result.loginName,
                    appPassword = result.appPassword,
                )
                val probe = container.probeSession(candidate)
                val info: ServerInfo = probe.server.serverInfo()
                if (!info.isSupported) {
                    _state.update { it.copy(phase = LoginPhase.Error("Nextcloud ${info.versionString} не поддерживается (нужна 34 или 35)")) }
                    return@launch
                }
                val me = probe.server.currentUser()
                if ("admin" !in me.groups) {
                    _state.update { it.copy(phase = LoginPhase.Error("Учётная запись ${me.id} не входит в группу admin")) }
                    return@launch
                }
                container.accounts.upsert(candidate.copy(displayName = me.displayNameOrId, serverVersion = info.versionString))
                _state.update { it.copy(phase = LoginPhase.Idle, serverUrl = "") }
                _events.send(LoginEvent.LoggedIn)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = LoginPhase.Error(e.userMessage())) }
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        _state.update { it.copy(phase = LoginPhase.Idle) }
    }

    fun selectAccount(id: String) {
        container.accounts.setCurrent(id)
        viewModelScope.launch { _events.send(LoginEvent.LoggedIn) }
    }

    fun removeAccount(id: String) = container.accounts.remove(id)

    private fun normalizeUrl(input: String): String? {
        var s = input.trim().trimEnd('/')
        if (s.isEmpty()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return runCatching { java.net.URI(s) }.getOrNull()?.takeIf { !it.host.isNullOrBlank() }?.let { s }
    }
}
