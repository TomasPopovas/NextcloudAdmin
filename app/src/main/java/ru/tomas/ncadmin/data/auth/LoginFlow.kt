package ru.tomas.ncadmin.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.tomas.ncadmin.data.api.obj
import ru.tomas.ncadmin.data.api.strOr
import java.io.IOException

/**
 * Nextcloud Login Flow v2:
 * https://docs.nextcloud.com/server/stable/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2
 *
 * 1. POST {server}/index.php/login/v2  → { poll: { token, endpoint }, login: url }
 * 2. Открываем url в браузере, пользователь логинится (в т.ч. с 2FA) и подтверждает устройство.
 * 3. Опрашиваем POST endpoint с token= ; 404 — ещё не готово; 200 — { server, loginName, appPassword }.
 */
class LoginFlow(private val http: OkHttpClient) {

    data class Started(val loginUrl: String, val pollEndpoint: String, val token: String)
    data class Result(val server: String, val loginName: String, val appPassword: String)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun start(serverUrl: String): Started = withContext(Dispatchers.IO) {
        val url = serverUrl.trimEnd('/') + "/index.php/login/v2"
        val req = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Сервер не ответил на login/v2 (HTTP ${resp.code})")
            val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val poll = root["poll"].obj() ?: throw IOException("Неверный ответ login/v2")
            Started(
                loginUrl = root.strOr("login"),
                pollEndpoint = poll.strOr("endpoint"),
                token = poll.strOr("token"),
            )
        }
    }

    /** Один опрос. Возвращает null, пока пользователь не завершил вход. */
    suspend fun pollOnce(started: Started): Result? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(started.pollEndpoint)
            .post(FormBody.Builder().add("token", started.token).build())
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            when {
                resp.code == 404 -> null
                !resp.isSuccessful -> throw IOException("Ошибка опроса login/v2 (HTTP ${resp.code})")
                else -> {
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    Result(
                        server = root.strOr("server"),
                        loginName = root.strOr("loginName"),
                        appPassword = root.strOr("appPassword"),
                    )
                }
            }
        }
    }

    /** Опрос до результата либо до отмены корутины. */
    suspend fun poll(started: Started, intervalMs: Long = 2000, timeoutMs: Long = 10 * 60 * 1000): Result {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            pollOnce(started)?.let { return it }
            delay(intervalMs)
        }
        throw IOException("Время ожидания входа истекло")
    }
}
