package ru.tomas.ncadmin.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.tomas.ncadmin.NcAdminApp
import ru.tomas.ncadmin.data.AppContainer

/** Фабрика ViewModel с доступом к AppContainer без Hilt. */
inline fun <reified VM : ViewModel> ncFactory(crossinline create: (AppContainer, CreationExtras) -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NcAdminApp
            create(app.container, this)
        }
    }

/** Человекочитаемое сообщение об ошибке. */
fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "Ошибка"
