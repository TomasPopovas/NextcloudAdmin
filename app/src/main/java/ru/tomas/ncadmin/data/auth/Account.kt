package ru.tomas.ncadmin.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    /** Уникальный ключ: serverUrl + loginName */
    val id: String,
    val serverUrl: String,
    val loginName: String,
    val appPassword: String,
    val displayName: String = "",
    val serverVersion: String = "",
) {
    val host: String get() = serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')

    companion object {
        fun makeId(serverUrl: String, loginName: String) =
            serverUrl.trimEnd('/').lowercase() + "|" + loginName.lowercase()
    }
}
