package ru.tomas.ncadmin.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Утилиты для «мягкого» разбора ответов Nextcloud: PHP отдаёт пустой объект как [],
 * числа иногда как строки, булевы — как 0/1. Поэтому не используем строгие data-классы.
 */

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonElement?.prim(): JsonPrimitive? = this as? JsonPrimitive

fun JsonObject.str(key: String): String? = this[key].prim()?.contentOrNull?.takeIf { this[key] !is JsonNull }
fun JsonObject.strOr(key: String, default: String = ""): String = str(key) ?: default

fun JsonObject.int(key: String): Int? = this[key].prim()?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
fun JsonObject.long(key: String): Long? = this[key].prim()?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
fun JsonObject.double(key: String): Double? = this[key].prim()?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

fun JsonObject.bool(key: String): Boolean? = this[key].prim()?.let { p ->
    p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: p.contentOrNull?.let {
        when (it.lowercase()) { "true", "1", "yes" -> true; "false", "0", "no", "" -> false; else -> null }
    }
}

fun JsonObject.stringList(key: String): List<String> =
    this[key].arr()?.mapNotNull { it.prim()?.contentOrNull } ?: emptyList()

/** Ключи объекта либо элементы массива — для ответов вида {"users": {id: {...}}} или {"users": []}. */
fun JsonElement?.objectValues(): List<JsonObject> = when (this) {
    is JsonObject -> values.mapNotNull { it as? JsonObject }
    is JsonArray -> mapNotNull { it as? JsonObject }
    else -> emptyList()
}
