package ru.tomas.ncadmin.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tomas.ncadmin.data.AppContainer
import ru.tomas.ncadmin.data.api.NcGroup
import ru.tomas.ncadmin.data.api.NcUser
import ru.tomas.ncadmin.ui.common.userMessage

data class UserDetailUiState(
    val user: NcUser? = null,
    val allGroups: List<NcGroup> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

sealed interface UserDetailEvent {
    data class Message(val text: String) : UserDetailEvent
    data object Deleted : UserDetailEvent
}

class UserDetailViewModel(private val container: AppContainer, private val userId: String) : ViewModel() {

    private val _state = MutableStateFlow(UserDetailUiState())
    val state: StateFlow<UserDetailUiState> = _state.asStateFlow()

    private val _events = Channel<UserDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val users get() = container.requireSession().users
    private val groups get() = container.requireSession().groups

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val user = users.get(userId)
                val all = runCatching { groups.list() }.getOrDefault(emptyList())
                _state.update { it.copy(user = user, allGroups = all, loading = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    /** Общий обёртчик для действий: busy → действие → перезагрузка карточки → сообщение. */
    private fun action(successMessage: String? = null, reloadAfter: Boolean = true, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
                if (reloadAfter) {
                    val user = users.get(userId)
                    _state.update { it.copy(user = user) }
                }
                successMessage?.let { _events.send(UserDetailEvent.Message(it)) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _events.send(UserDetailEvent.Message(e.userMessage()))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun setField(key: String, value: String, message: String = "Сохранено") = action(message) { users.setField(userId, key, value) }

    /** quota: "default", "none" либо строка вида "10 GB" / "1073741824" */
    fun setQuota(value: String) = setField("quota", value)

    fun setPassword(password: String) = action("Пароль изменён", reloadAfter = false) { users.setField(userId, "password", password) }

    fun setEnabled(enabled: Boolean) = action(if (enabled) "Пользователь разблокирован" else "Пользователь заблокирован") {
        if (enabled) users.enable(userId) else users.disable(userId)
    }

    fun delete() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                users.delete(userId)
                _events.send(UserDetailEvent.Deleted)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _events.send(UserDetailEvent.Message(e.userMessage()))
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun sendWelcome() = action("Письмо отправлено", reloadAfter = false) { users.sendWelcomeMail(userId) }

    fun addToGroup(groupId: String) = action("Добавлен в $groupId") { users.addToGroup(userId, groupId) }
    fun removeFromGroup(groupId: String) = action("Удалён из $groupId") { users.removeFromGroup(userId, groupId) }

    fun addSubadmin(groupId: String) = action("Назначен администратором $groupId") { users.addSubadmin(userId, groupId) }
    fun removeSubadmin(groupId: String) = action("Снят с администрирования $groupId") { users.removeSubadmin(userId, groupId) }
}
