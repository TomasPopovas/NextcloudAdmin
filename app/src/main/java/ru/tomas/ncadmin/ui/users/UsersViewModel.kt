package ru.tomas.ncadmin.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tomas.ncadmin.data.AppContainer
import ru.tomas.ncadmin.data.api.NcUser
import ru.tomas.ncadmin.ui.common.userMessage

enum class UserFilter { ALL, ENABLED, DISABLED }

data class UsersUiState(
    val query: String = "",
    val filter: UserFilter = UserFilter.ALL,
    val users: List<NcUser> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
) {
    val visible: List<NcUser>
        get() = when (filter) {
            UserFilter.ALL -> users
            UserFilter.ENABLED -> users.filter { it.enabled }
            UserFilter.DISABLED -> users.filter { !it.enabled }
        }
}

@OptIn(FlowPreview::class)
class UsersViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(UsersUiState())
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")
    private var loadJob: Job? = null

    private val pageSize = 50

    init {
        viewModelScope.launch {
            query.debounce(350).distinctUntilChanged().collect { reload() }
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        query.value = q
    }

    fun onFilterChange(f: UserFilter) = _state.update { it.copy(filter = f) }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, users = emptyList(), endReached = false) }
            try {
                val page = container.requireSession().users.list(search = _state.value.query, limit = pageSize, offset = 0)
                _state.update { it.copy(loading = false, users = page, endReached = page.size < pageSize) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = container.requireSession().users.list(search = s.query, limit = pageSize, offset = s.users.size)
                _state.update { it.copy(loadingMore = false, users = it.users + page, endReached = page.size < pageSize) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loadingMore = false, error = e.userMessage()) }
            }
        }
    }
}
