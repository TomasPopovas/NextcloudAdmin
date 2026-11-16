package ru.tomas.ncadmin

import android.app.Application
import ru.tomas.ncadmin.data.AppContainer

class NcAdminApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
