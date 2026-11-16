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
import ru.tomas.ncadmin.ui.common.userMessage

data class GroupsUiState(
    val query: String = "",
    val groups: List<NcGroup> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val visible: List<NcGroup>
        get() = if (query.isBlank()) groups
        else groups.filter { it.id.contains(query, true) || it.displayName.contains(query, true) }
}

class GroupsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val groups get() = container.requireSession().groups

    init { reload() }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val list = groups.list().sortedWith(compareBy({ !it.isProtected }, { it.displayName.lowercase() }))
                _state.update { it.copy(loading = false, groups = list) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun create(id: String, displayName: String?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                groups.create(id.trim(), displayName?.trim()?.ifEmpty { null })
                _messages.send("Группа создана")
                reload()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _messages.send(e.userMessage())
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
