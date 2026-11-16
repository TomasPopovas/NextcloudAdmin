package ru.tomas.ncadmin.data.repo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ru.tomas.ncadmin.data.api.OcsClient
import ru.tomas.ncadmin.data.api.OcsException
import ru.tomas.ncadmin.data.api.bool
import ru.tomas.ncadmin.data.api.long
import ru.tomas.ncadmin.data.api.obj
import ru.tomas.ncadmin.data.api.single
import ru.tomas.ncadmin.data.api.str
import ru.tomas.ncadmin.data.api.strOr
import ru.tomas.ncadmin.data.api.stringList

data class CompanionStatus(
    val installed: Boolean,
    val version: String = "",
    val confirmed: Boolean = false,
    val confirmedUntilSec: Long = 0,
    val systemKeys: List<String> = emptyList(),
)

/** Одно значение config.php из белого списка companion-приложения. */
data class SystemValue(
    val key: String,
    val type: String,      // bool | int | string | array | secret
    val isSet: Boolean,
    val value: JsonElement?,
)

/**
 * Клиент companion-приложения ncadmin_api (см. server-app/).
 */
class CompanionRepository(private val ocs: OcsClient) {

    private val base = "/ocs/v2.php/apps/ncadmin_api/api/v1"

    @Volatile
    var lastStatus: CompanionStatus? = null
        private set

    suspend fun status(): CompanionStatus {
        val st = try {
            val d = ocs.get("$base/status").obj() ?: return CompanionStatus(installed = false)
            CompanionStatus(
                installed = true,
                version = d.strOr("version"),
                confirmed = d.bool("confirmed") ?: false,
                confirmedUntilSec = d.long("confirmedUntil") ?: 0,
                systemKeys = d.stringList("systemKeys"),
            )
        } catch (e: OcsException) {
            // 404 / 998 — приложение не установлено или выключено
            if (e.isNotFound || e.ocsStatus == 998) CompanionStatus(installed = false) else throw e
        }
        lastStatus = st
        return st
    }

    /** Ставит отметку подтверждения пароля для текущей сессии. true — успех. */
    suspend fun confirm(): Boolean {
        val d = try {
            ocs.post("$base/confirm").obj()
        } catch (e: OcsException) {
            if (e.isNotFound || e.ocsStatus == 998) return false
            throw e
        }
        val ok = d?.bool("confirmed") ?: false
        if (ok) lastStatus = (lastStatus ?: CompanionStatus(true)).copy(
            installed = true, confirmed = true, confirmedUntilSec = d?.long("confirmedUntil") ?: 0,
        )
        return ok
    }

    /** Все значения appconfig одного приложения. */
    suspend fun appConfigAll(app: String): Map<String, JsonElement> {
        val d = ocs.get("$base/config/apps/$app").obj()
        val values = d?.get("values") as? JsonObject ?: return emptyMap()
        return values.toMap()
    }

    suspend fun systemConfig(): List<SystemValue> {
        val keys = ocs.get("$base/system").obj()?.get("keys") as? JsonObject ?: return emptyList()
        return keys.entries.map { (k, v) ->
            val o = v as? JsonObject
            SystemValue(
                key = k,
                type = o?.str("type") ?: "string",
                isSet = o?.bool("set") ?: false,
                value = o?.get("value"),
            )
        }
    }

    suspend fun setSystem(key: String, value: String) {
        ocs.put("$base/system/$key", mapOf("value" to value.single()))
    }

    suspend fun deleteSystem(key: String) {
        ocs.delete("$base/system/$key")
    }

    /** Отправить тестовое письмо; возвращает адрес получателя. */
    suspend fun testMail(): String =
        ocs.post("$base/mail/test").obj()?.strOr("to") ?: ""
}
