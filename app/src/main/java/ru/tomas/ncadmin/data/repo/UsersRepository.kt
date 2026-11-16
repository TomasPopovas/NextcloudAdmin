package ru.tomas.ncadmin.data.repo

import ru.tomas.ncadmin.data.api.NcUser
import ru.tomas.ncadmin.data.api.OcsClient
import ru.tomas.ncadmin.data.api.multi
import ru.tomas.ncadmin.data.api.obj
import ru.tomas.ncadmin.data.api.objectValues
import ru.tomas.ncadmin.data.api.single
import ru.tomas.ncadmin.data.api.stringList

/**
 * OCS User Provisioning API (NC 34/35):
 * https://docs.nextcloud.com/server/stable/admin_manual/configuration_user/instruction_set_for_users.html
 */
class UsersRepository(private val ocs: OcsClient) {

    private val base = "/ocs/v2.php/cloud/users"

    suspend fun list(search: String = "", limit: Int = 50, offset: Int = 0): List<NcUser> {
        val q = buildMap {
            if (search.isNotBlank()) put("search", search)
            put("limit", limit.toString())
            put("offset", offset.toString())
        }
        val data = ocs.get("$base/details", q).obj()
        return data?.get("users").objectValues().map { NcUser.parse(it) }
    }

    suspend fun listIds(search: String = "", limit: Int = 50, offset: Int = 0): List<String> {
        val q = buildMap {
            if (search.isNotBlank()) put("search", search)
            put("limit", limit.toString())
            put("offset", offset.toString())
        }
        return ocs.get(base, q).obj()?.stringList("users") ?: emptyList()
    }

    suspend fun get(userId: String): NcUser {
        val data = ocs.get("$base/${enc(userId)}").obj() ?: error("Пользователь не найден")
        return NcUser.parse(data)
    }

    suspend fun create(
        userId: String,
        password: String?,
        displayName: String?,
        email: String?,
        groups: List<String>,
        subadmin: List<String>,
        quota: String?,
        language: String?,
    ): String {
        val form = buildMap {
            put("userid", userId.single())
            if (!password.isNullOrEmpty()) put("password", password.single())
            if (!displayName.isNullOrBlank()) put("displayName", displayName.single())
            if (!email.isNullOrBlank()) put("email", email.single())
            if (groups.isNotEmpty()) put("groups", groups.multi())
            if (subadmin.isNotEmpty()) put("subadmin", subadmin.multi())
            if (!quota.isNullOrBlank()) put("quota", quota.single())
            if (!language.isNullOrBlank()) put("language", language.single())
        }
        val data = ocs.post(base, form).obj()
        return data?.get("id")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: userId
    }

    /** PUT users/{id} с key/value: email, displayname, quota, password, language, phone, address, website… */
    suspend fun setField(userId: String, key: String, value: String) {
        ocs.put("$base/${enc(userId)}", mapOf("key" to key.single(), "value" to value.single()))
    }

    suspend fun delete(userId: String) {
        ocs.delete("$base/${enc(userId)}")
    }

    suspend fun enable(userId: String) {
        ocs.put("$base/${enc(userId)}/enable")
    }

    suspend fun disable(userId: String) {
        ocs.put("$base/${enc(userId)}/disable")
    }

    suspend fun groupsOf(userId: String): List<String> =
        ocs.get("$base/${enc(userId)}/groups").obj()?.stringList("groups") ?: emptyList()

    suspend fun addToGroup(userId: String, groupId: String) {
        ocs.post("$base/${enc(userId)}/groups", mapOf("groupid" to groupId.single()))
    }

    suspend fun removeFromGroup(userId: String, groupId: String) {
        ocs.delete("$base/${enc(userId)}/groups", mapOf("groupid" to groupId.single()))
    }

    suspend fun subadminGroupsOf(userId: String): List<String> {
        // Ответ — массив идентификаторов групп прямо в data
        val data = ocs.get("$base/${enc(userId)}/subadmins")
        return (data as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
    }

    suspend fun addSubadmin(userId: String, groupId: String) {
        ocs.post("$base/${enc(userId)}/subadmins", mapOf("groupid" to groupId.single()))
    }

    suspend fun removeSubadmin(userId: String, groupId: String) {
        ocs.delete("$base/${enc(userId)}/subadmins", mapOf("groupid" to groupId.single()))
    }

    suspend fun sendWelcomeMail(userId: String) {
        ocs.post("$base/${enc(userId)}/welcome")
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
