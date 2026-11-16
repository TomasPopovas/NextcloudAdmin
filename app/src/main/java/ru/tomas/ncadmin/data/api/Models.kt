package ru.tomas.ncadmin.data.api

import kotlinx.serialization.json.JsonObject

data class NcQuota(
    val free: Long?,
    val used: Long,
    val total: Long?,
    val relative: Double?,
    /** Сырое значение quota: байты, -3 = без ограничений, null = не задано/по умолчанию */
    val quota: Long?,
) {
    val isUnlimited: Boolean get() = quota != null && quota < 0

    companion object {
        fun parse(o: JsonObject?): NcQuota {
            if (o == null) return NcQuota(null, 0, null, null, null)
            val quotaRaw = o["quota"].prim()
            val quota: Long? = quotaRaw?.let { p ->
                p.content.toLongOrNull() ?: when (p.content.lowercase()) {
                    "none" -> -3L
                    "default" -> null
                    else -> null
                }
            }
            return NcQuota(
                free = o.long("free"),
                used = o.long("used") ?: 0,
                total = o.long("total"),
                relative = o.double("relative"),
                quota = quota,
            )
        }
    }
}

data class NcUser(
    val id: String,
    val displayName: String,
    val email: String?,
    val enabled: Boolean,
    val groups: List<String>,
    val subadmin: List<String>,
    val quota: NcQuota,
    val language: String?,
    val locale: String?,
    val backend: String?,
    val lastLoginMillis: Long,
    val storageLocation: String?,
    val phone: String?,
    val address: String?,
    val website: String?,
    val canSetDisplayName: Boolean,
    val canSetPassword: Boolean,
) {
    val displayNameOrId: String get() = displayName.ifBlank { id }

    companion object {
        fun parse(o: JsonObject): NcUser {
            val caps = o["backendCapabilities"].obj()
            return NcUser(
                id = o.strOr("id"),
                displayName = o.str("displayname") ?: o.str("display-name") ?: "",
                email = o.str("email")?.takeIf { it.isNotBlank() },
                enabled = o.bool("enabled") ?: true,
                groups = o.stringList("groups"),
                subadmin = o.stringList("subadmin"),
                quota = NcQuota.parse(o["quota"].obj()),
                language = o.str("language"),
                locale = o.str("locale"),
                backend = o.str("backend"),
                lastLoginMillis = o.long("lastLogin") ?: 0L,
                storageLocation = o.str("storageLocation"),
                phone = o.str("phone")?.takeIf { it.isNotBlank() },
                address = o.str("address")?.takeIf { it.isNotBlank() },
                website = o.str("website")?.takeIf { it.isNotBlank() },
                canSetDisplayName = caps?.bool("setDisplayName") ?: true,
                canSetPassword = caps?.bool("setPassword") ?: true,
            )
        }
    }
}

data class NcGroup(
    val id: String,
    val displayName: String,
    val userCount: Int,
    val disabledCount: Int,
    val canAdd: Boolean,
    val canRemove: Boolean,
) {
    val isProtected: Boolean get() = id == "admin"

    companion object {
        fun parse(o: JsonObject): NcGroup = NcGroup(
            id = o.strOr("id"),
            displayName = o.str("displayname")?.ifBlank { null } ?: o.strOr("id"),
            userCount = o.int("usercount") ?: 0,
            disabledCount = o.int("disabled") ?: 0,
            canAdd = o.bool("canAdd") ?: true,
            canRemove = o.bool("canRemove") ?: true,
        )
    }
}

data class ServerInfo(
    val versionString: String,
    val major: Int,
    val editionName: String,
) {
    val isSupported: Boolean get() = major in SUPPORTED_MAJORS

    companion object {
        val SUPPORTED_MAJORS = setOf(34, 35)
    }
}
