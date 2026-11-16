package ru.tomas.ncadmin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import ru.tomas.ncadmin.data.AppContainer
import ru.tomas.ncadmin.ui.common.userMessage

data class SectionUiState(
    val section: SettingsSection,
    /** Текущее строковое представление значения по id настройки ("app/key"). */
    val values: Map<String, String> = emptyMap(),
    val groups: List<String> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

class SectionViewModel(private val container: AppContainer, section: SettingsSection) : ViewModel() {

    private val _state = MutableStateFlow(SectionUiState(section))
    val state: StateFlow<SectionUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val session get() = container.requireSession()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val section = _state.value.section
                val result = mutableMapOf<String, String>()
                section.specs.groupBy { it.app }.forEach { (app, specs) ->
                    val raw = session.appConfig.values(app, specs.map { it.key })
                    specs.forEach { spec -> result[spec.id] = normalize(raw[spec.key], spec) }
                }
                val groups = if (section.specs.any { it.type == SettingType.GROUPS })
                    runCatching { session.groups.list().map { it.id } }.getOrDefault(emptyList())
                else emptyList()
                _state.update { it.copy(values = result, groups = groups, loading = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun set(spec: SettingSpec, value: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                session.appConfig.set(spec.app, spec.key, value)
                if (_state.value.section.bumpCacheBuster) bumpCacheBuster()
                _state.update { it.copy(values = it.values + (spec.id to value)) }
                _messages.send("Сохранено: ${spec.title}")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _messages.send(e.userMessage())
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun setBool(spec: SettingSpec, v: Boolean) = set(spec, spec.encodeBool(v))

    fun setGroups(spec: SettingSpec, groups: List<String>) =
        set(spec, JsonArray(groups.map { JsonPrimitive(it) }).toString())

    /** Удалить ключ — вернуться к значению по умолчанию. */
    fun reset(spec: SettingSpec) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                session.appConfig.delete(spec.app, spec.key)
                _state.update { it.copy(values = it.values + (spec.id to spec.default)) }
                _messages.send("Сброшено: ${spec.title}")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _messages.send(e.userMessage())
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun bumpCacheBuster() {
        val cur = runCatching { session.appConfig.value("theming", "cachebuster") }.getOrNull()
        val n = (cur as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        runCatching { session.appConfig.set("theming", "cachebuster", (n + 1).toString()) }
    }

    /** Приводим JSON-значение к строке с учётом типа настройки. */
    private fun normalize(raw: JsonElement?, spec: SettingSpec): String = when (raw) {
        null, JsonNull -> spec.default
        is JsonPrimitive -> {
            val s = raw.contentOrNull ?: spec.default
            if (spec.type == SettingType.GROUPS && !s.trimStart().startsWith("[")) "[]" else s
        }
        is JsonArray -> raw.toString()
        else -> raw.toString()
    }

    companion object {
        fun parseGroups(raw: String): List<String> = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw).let { el ->
                (el as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }
}
