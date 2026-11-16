package ru.tomas.ncadmin.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException

/**
 * Ошибка OCS-уровня: HTTP-код либо ocs.meta.statuscode вне успешного диапазона.
 */
class OcsException(
    val httpCode: Int,
    val ocsStatus: Int,
    message: String,
) : IOException(message) {
    val isAuthError: Boolean get() = httpCode == 401 || ocsStatus == 997
    val isForbidden: Boolean get() = httpCode == 403 || ocsStatus == 403 || ocsStatus == 998
    val isNotFound: Boolean get() = httpCode == 404 || ocsStatus == 404 || ocsStatus == 101
}

/**
 * Параметр формы: обычное значение либо массив (groups[]=a&groups[]=b).
 */
sealed class FormValue {
    data class Single(val value: String) : FormValue()
    data class Multi(val values: List<String>) : FormValue()
}

fun String.single(): FormValue = FormValue.Single(this)
fun List<String>.multi(): FormValue = FormValue.Multi(this)

/**
 * Минимальный клиент OCS API поверх OkHttp. Работает с /ocs/v2.php (JSON, HTTP-коды
 * совпадают с ocs.meta.statuscode). Возвращает ocs.data как JsonElement.
 *
 * Nextcloud 34 и 35 используют одинаковый Provisioning API, отдельных веток не требуется.
 */
class OcsClient(
    private val http: OkHttpClient,
    val baseUrl: String,
    private val loginName: String?,
    private val appPassword: String?,
) {
    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement =
        request("GET", path, query, null)

    suspend fun post(path: String, form: Map<String, FormValue> = emptyMap()): JsonElement =
        request("POST", path, emptyMap(), form)

    suspend fun put(path: String, form: Map<String, FormValue> = emptyMap()): JsonElement =
        request("PUT", path, emptyMap(), form)

    suspend fun delete(path: String, form: Map<String, FormValue> = emptyMap()): JsonElement =
        request("DELETE", path, emptyMap(), form)

    private suspend fun request(
        method: String,
        path: String,
        query: Map<String, String>,
        form: Map<String, FormValue>?,
    ): JsonElement = withContext(Dispatchers.IO) {
        val urlBuilder = (baseUrl.trimEnd('/') + path).toHttpUrlOrNull()?.newBuilder()
            ?: throw IOException("Некорректный URL: $baseUrl$path")
        urlBuilder.addQueryParameter("format", "json")
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        val body: RequestBody? = when {
            method == "GET" -> null
            else -> {
                val fb = FormBody.Builder()
                form?.forEach { (key, value) ->
                    when (value) {
                        is FormValue.Single -> fb.add(key, value.value)
                        is FormValue.Multi -> value.values.forEach { fb.add("$key[]", it) }
                    }
                }
                fb.build()
            }
        }

        val builder = Request.Builder()
            .url(urlBuilder.build())
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .method(method, body)

        if (loginName != null && appPassword != null) {
            builder.header("Authorization", Credentials.basic(loginName, appPassword, Charsets.UTF_8))
        }

        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val root: JsonObject? = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val ocs = root?.get("ocs")?.let { it as? JsonObject }
            val meta = ocs?.get("meta")?.let { it as? JsonObject }
            val ocsStatus = meta?.get("statuscode")?.jsonPrimitive?.intOrNull ?: -1
            val ocsMessage = meta?.get("message")?.jsonPrimitive?.contentOrNull

            val ok = resp.isSuccessful && (ocsStatus == 100 || ocsStatus in 200..299 || ocsStatus == -1)
            if (!ok) {
                val msg = ocsMessage?.takeIf { it.isNotBlank() && it != "OK" }
                    ?: when (resp.code) {
                        401 -> "Требуется авторизация (неверный app-пароль?)"
                        403 -> "Недостаточно прав"
                        404 -> "Не найдено"
                        else -> "HTTP ${resp.code}"
                    }
                throw OcsException(resp.code, ocsStatus, msg)
            }
            ocs?.get("data") ?: JsonNull
        }
    }
}
