package ru.tomas.ncadmin.data

import android.content.Context
import okhttp3.OkHttpClient
import ru.tomas.ncadmin.data.api.HttpFactory
import ru.tomas.ncadmin.data.api.OcsClient
import ru.tomas.ncadmin.data.auth.Account
import ru.tomas.ncadmin.data.auth.AccountStore
import ru.tomas.ncadmin.data.auth.LoginFlow
import ru.tomas.ncadmin.data.repo.GroupsRepository
import ru.tomas.ncadmin.data.repo.ServerRepository
import ru.tomas.ncadmin.data.repo.UsersRepository

/** Набор репозиториев, привязанный к одному аккаунту (серверу). */
class Session(val account: Account, http: OkHttpClient) {
    val ocs = OcsClient(http, account.serverUrl, account.loginName, account.appPassword)
    val server = ServerRepository(ocs)
    val users = UsersRepository(ocs)
    val groups = GroupsRepository(ocs)
}

/** Ручной DI: один экземпляр на процесс, хранится в Application. */
class AppContainer(context: Context) {
    val http: OkHttpClient = HttpFactory.create()
    val accounts = AccountStore(context.applicationContext)
    val loginFlow = LoginFlow(http)

    @Volatile
    private var cached: Session? = null

    /** Сессия текущего аккаунта; пересоздаётся при смене аккаунта. */
    val session: Session?
        get() {
            val acc = accounts.current ?: return null.also { cached = null }
            val c = cached
            if (c != null && c.account == acc) return c
            return Session(acc, http).also { cached = it }
        }

    fun requireSession(): Session = session ?: error("Нет активного аккаунта")

    /** Временная сессия для проверки учётных данных до сохранения аккаунта. */
    fun probeSession(account: Account): Session = Session(account, http)
}
