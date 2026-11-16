package ru.tomas.ncadmin.data.repo

import ru.tomas.ncadmin.data.api.NcGroup
import ru.tomas.ncadmin.data.api.NcUser
import ru.tomas.ncadmin.data.api.OcsClient
import ru.tomas.ncadmin.data.api.obj
import ru.tomas.ncadmin.data.api.objectValues
import ru.tomas.ncadmin.data.api.single
import ru.tomas.ncadmin.data.api.stringList

/**
 * OCS Group Provisioning API (NC 34/35):
 * https://docs.nextcloud.com/server/stable/admin_manual/configuration_user/instruction_set_for_groups.html
 */
class GroupsRepository(private val ocs: OcsClient) {

    private val base = "/ocs/v2.php/cloud/groups"

    suspend fun list(search: String = "", limit: Int = 200, offset: Int = 0): List<NcGroup> {
        val q = buildMap {
            if (search.isNotBlank()) put("search", search)
            put("limit", limit.toString())
            put("offset", offset.toString())
        }
        val data = ocs.get("$base/details", q).obj()
        return data?.get("groups").objectValues().map { NcGroup.parse(it) }
    }

    suspend fun listIds(search: String = ""): List<String> {
        val q = if (search.isBlank()) emptyMap() else mapOf("search" to search)
        return ocs.get(base, q).obj()?.stringList("groups") ?: emptyList()
    }

    suspend fun create(groupId: String, displayName: String?) {
        val form = buildMap {
            put("groupid", groupId.single())
            if (!displayName.isNullOrBlank()) put("displayname", displayName.single())
        }
        ocs.post(base, form)
    }

    suspend fun rename(groupId: String, displayName: String) {
        ocs.put("$base/${enc(groupId)}", mapOf("key" to "displayname".single(), "value" to displayName.single()))
    }

    suspend fun delete(groupId: String) {
        ocs.delete("$base/${enc(groupId)}")
    }

    suspend fun memberIds(groupId: String): List<String> =
        ocs.get("$base/${enc(groupId)}/users").obj()?.stringList("users") ?: emptyList()

    suspend fun members(groupId: String, search: String = "", limit: Int = 100, offset: Int = 0): List<NcUser> {
        val q = buildMap {
            if (search.isNotBlank()) put("search", search)
            put("limit", limit.toString())
            put("offset", offset.toString())
        }
        val data = ocs.get("$base/${enc(groupId)}/users/details", q).obj()
        return data?.get("users").objectValues().map { NcUser.parse(it) }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
