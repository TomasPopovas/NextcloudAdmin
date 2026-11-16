package ru.tomas.ncadmin.data.repo

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import ru.tomas.ncadmin.data.api.OcsClient
import ru.tomas.ncadmin.data.api.obj
import ru.tomas.ncadmin.data.api.single
import ru.tomas.ncadmin.data.api.stringList

/**
 * Provisioning API: app config.
 * GET  /ocs/v2.php/apps/provisioning_api/api/v1/config/apps/{app}        → data.data = [keys]
 * GET  /ocs/v2.php/apps/provisioning_api/api/v1/config/apps/{app}/{key}  → data.data = value
 * POST /ocs/v2.php/apps/provisioning_api/api/v1/config/apps/{app}/{key}  (value)
 * DELETE …/{app}/{key}
 *
 * Чтение доступно с app-паролем; запись требует подтверждения пароля — OcsClient
 * получает его через companion-приложение автоматически.
 */
class AppConfigRepository(private val ocs: OcsClient, private val companion: CompanionRepository) {

    private val base = "/ocs/v2.php/apps/provisioning_api/api/v1/config/apps"

    suspend fun keys(app: String): List<String> =
        ocs.get("$base/$app").obj()?.stringList("data") ?: emptyList()

    suspend fun value(app: String, key: String): JsonElement =
        ocs.get("$base/$app/${enc(key)}").obj()?.get("data") ?: JsonNull

    /**
     * Значения нескольких ключей. Если установлен companion — один запрос,
     * иначе параллельные запросы по ключу.
     */
    suspend fun values(app: String, keys: Collection<String>): Map<String, JsonElement> {
        val st = companion.lastStatus ?: runCatching { companion.status() }.getOrNull()
        if (st?.installed == true) {
            val all = runCatching { companion.appConfigAll(app) }.getOrNull()
            if (all != null) return keys.associateWith { all[it] ?: JsonNull }
        }
        return coroutineScope {
            keys.map { k -> k to async { runCatching { value(app, k) }.getOrDefault(JsonNull) } }
                .associate { (k, d) -> k to d.await() }
        }
    }

    suspend fun set(app: String, key: String, value: String) {
        ocs.post("$base/$app/${enc(key)}", mapOf("value" to value.single()))
    }

    suspend fun delete(app: String, key: String) {
        ocs.delete("$base/$app/${enc(key)}")
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
