package ru.tomas.ncadmin.ui.groups

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

data class GroupDetailUiState(
    val group: NcGroup? = null,
    val members: List<NcUser> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    /** Кандидаты для добавления (результат поиска по пользователям) */
    val candidates: List<NcUser> = emptyList(),
    val candidatesLoading: Boolean = false,
)

sealed interface GroupDetailEvent {
    data class Message(val text: String) : GroupDetailEvent
    data object Deleted : GroupDetailEvent
}

class GroupDetailViewModel(private val container: AppContainer, private val groupId: String) : ViewModel() {

    private val _state = MutableStateFlow(GroupDetailUiState())
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    private val _events = Channel<GroupDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val groups get() = container.requireSession().groups
    private val users get() = container.requireSession().users

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val group = groups.list(search = groupId).firstOrNull { it.id == groupId }
                    ?: NcGroup(groupId, groupId, 0, 0, canAdd = true, canRemove = true)
                val members = groups.members(groupId, limit = 500)
                _state.update { it.copy(group = group, members = members.sortedBy { u -> u.displayNameOrId.lowercase() }, loading = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    private fun action(message: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
                message?.let { _events.send(GroupDetailEvent.Message(it)) }
                load()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _events.send(GroupDetailEvent.Message(e.userMessage()))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun rename(displayName: String) = action("Группа переименована") { groups.rename(groupId, displayName.trim()) }

    fun removeMember(userId: String) = action("Пользователь убран из группы") { users.removeFromGroup(userId, groupId) }

    fun addMember(userId: String) = action("Пользователь добавлен") { users.addToGroup(userId, groupId) }

    fun delete() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                groups.delete(groupId)
                _events.send(GroupDetailEvent.Deleted)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _events.send(GroupDetailEvent.Message(e.userMessage()))
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun searchCandidates(query: String) {
        viewModelScope.launch {
            _state.update { it.copy(candidatesLoading = true) }
            val memberIds = _state.value.members.map { it.id }.toSet()
            val found = runCatching { users.list(search = query, limit = 30) }.getOrDefault(emptyList())
            _state.update { it.copy(candidates = found.filterNot { u -> u.id in memberIds }, candidatesLoading = false) }
        }
    }
}
