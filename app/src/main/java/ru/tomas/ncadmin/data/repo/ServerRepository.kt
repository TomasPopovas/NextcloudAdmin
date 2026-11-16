package ru.tomas.ncadmin.data.repo

import ru.tomas.ncadmin.data.api.NcUser
import ru.tomas.ncadmin.data.api.OcsClient
import ru.tomas.ncadmin.data.api.ServerInfo
import ru.tomas.ncadmin.data.api.int
import ru.tomas.ncadmin.data.api.obj
import ru.tomas.ncadmin.data.api.strOr

class ServerRepository(private val ocs: OcsClient) {

    /** GET /ocs/v2.php/cloud/capabilities — версия сервера. */
    suspend fun serverInfo(): ServerInfo {
        val data = ocs.get("/ocs/v2.php/cloud/capabilities").obj()
        val version = data?.get("version").obj()
        return ServerInfo(
            versionString = version?.strOr("string") ?: "?",
            major = version?.int("major") ?: 0,
            editionName = version?.strOr("edition") ?: "",
        )
    }

    /** GET /ocs/v2.php/cloud/user — текущий пользователь (для проверки членства в admin). */
    suspend fun currentUser(): NcUser {
        val data = ocs.get("/ocs/v2.php/cloud/user").obj() ?: error("Пустой ответ cloud/user")
        return NcUser.parse(data)
    }
}
