package ru.tomas.ncadmin.data.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Cookie-хранилище в памяти на один аккаунт. Нужно, чтобы серверная сессия Nextcloud
 * (и выставленная companion-приложением отметка подтверждения пароля) сохранялась
 * между запросами. На диск не пишется: при перезапуске приложения сессия создаётся заново.
 */
class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.forEach { c ->
            val key = "${c.domain}|${c.path}|${c.name}"
            if (c.expiresAt < now) store.remove(key) else store[key] = c
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.values.removeAll { it.expiresAt < now }
        return store.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = store.clear()
}
