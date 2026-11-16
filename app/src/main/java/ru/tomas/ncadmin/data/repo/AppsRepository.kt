package ru.tomas.ncadmin.data.repo

import kotlinx.serialization.json.JsonObject
import ru.tomas.ncadmin.data.api.OcsClient
import ru.tomas.ncadmin.data.api.obj
import ru.tomas.ncadmin.data.api.str
import ru.tomas.ncadmin.data.api.strOr
import ru.tomas.ncadmin.data.api.stringList

data class NcApp(
    val id: String,
    val name: String,
    val version: String,
    val summary: String,
    val enabled: Boolean,
) {
    val isCore: Boolean get() = id in PROTECTED

    companion object {
        /** Приложения, выключение которых ломает сервер или сам клиент. */
        val PROTECTED = setOf(
            "provisioning_api", "settings", "files", "dav", "workflowengine", "oauth2",
            "twofactor_backupcodes", "cloud_federation_api", "federatedfilesharing", "lookup_server_connector",
            "theming", "viewer", "ncadmin_api",
        )
    }
}

/**
 * Provisioning API: приложения.
 * GET    /ocs/v2.php/cloud/apps?filter=enabled|disabled → data.apps [ids]
 * GET    /ocs/v2.php/cloud/apps/{id}                    → data {id,name,version,summary,…}
 * POST   /ocs/v2.php/cloud/apps/{id}                    enable
 * DELETE /ocs/v2.php/cloud/apps/{id}                    disable
 */
class AppsRepository(private val ocs: OcsClient) {

    private val base = "/ocs/v2.php/cloud/apps"

    suspend fun ids(enabled: Boolean): List<String> =
        ocs.get(base, mapOf("filter" to if (enabled) "enabled" else "disabled")).obj()?.stringList("apps") ?: emptyList()

    suspend fun info(id: String, enabled: Boolean): NcApp {
        val d: JsonObject? = runCatching { ocs.get("$base/$id").obj() }.getOrNull()
        return NcApp(
            id = id,
            name = d?.str("name")?.ifBlank { null } ?: id,
            version = d?.strOr("version") ?: "",
            summary = d?.str("summary")?.ifBlank { null } ?: d?.str("description")?.lineSequence()?.firstOrNull().orEmpty(),
            enabled = enabled,
        )
    }

    suspend fun enable(id: String) { ocs.post("$base/$id") }
    suspend fun disable(id: String) { ocs.delete("$base/$id") }
}
