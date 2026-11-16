package ru.tomas.ncadmin.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Хранилище аккаунтов (несколько серверов). App-пароли лежат в EncryptedSharedPreferences
 * (AES-256-GCM, ключ в Android Keystore).
 */
class AccountStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nc_admin_accounts",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(Account.serializer())

    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _currentId = MutableStateFlow(prefs.getString(KEY_CURRENT, null))
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    val current: Account? get() = _accounts.value.firstOrNull { it.id == _currentId.value }

    fun upsert(account: Account) {
        val list = _accounts.value.filterNot { it.id == account.id } + account
        persist(list)
        setCurrent(account.id)
    }

    fun remove(id: String) {
        persist(_accounts.value.filterNot { it.id == id })
        if (_currentId.value == id) setCurrent(_accounts.value.firstOrNull()?.id)
    }

    fun setCurrent(id: String?) {
        prefs.edit().putString(KEY_CURRENT, id).apply()
        _currentId.value = id
    }

    private fun load(): List<Account> =
        runCatching { json.decodeFromString(listSerializer, prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persist(list: List<Account>) {
        prefs.edit().putString(KEY_ACCOUNTS, json.encodeToString(listSerializer, list)).apply()
        _accounts.value = list
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_CURRENT = "current"
    }
}
