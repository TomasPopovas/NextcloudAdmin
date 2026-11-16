package ru.tomas.ncadmin.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.tomas.ncadmin.NcAdminApp
import ru.tomas.ncadmin.ui.groups.GroupDetailScreen
import ru.tomas.ncadmin.ui.groups.GroupsScreen
import ru.tomas.ncadmin.ui.home.HomeScreen
import ru.tomas.ncadmin.ui.login.LoginScreen
import ru.tomas.ncadmin.ui.settings.AppsScreen
import ru.tomas.ncadmin.ui.settings.SectionScreen
import ru.tomas.ncadmin.ui.settings.SettingsHubScreen
import ru.tomas.ncadmin.ui.settings.SystemConfigScreen
import ru.tomas.ncadmin.ui.users.CreateUserScreen
import ru.tomas.ncadmin.ui.users.UserDetailScreen
import ru.tomas.ncadmin.ui.users.UsersScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val USERS = "users"
    const val USER_NEW = "users/new"
    const val USER_DETAIL = "users/detail/{userId}"
    const val GROUPS = "groups"
    const val GROUP_DETAIL = "groups/detail/{groupId}"
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings/section/{sectionId}"
    const val SETTINGS_SYSTEM = "settings/system"
    const val SETTINGS_APPS = "settings/apps"

    fun userDetail(id: String) = "users/detail/${Uri.encode(id)}"
    fun groupDetail(id: String) = "groups/detail/${Uri.encode(id)}"
    fun section(id: String) = "settings/section/$id"
}

@Composable
fun NcAdminNavHost() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val container = remember { (context.applicationContext as NcAdminApp).container }
    val start = if (container.accounts.current != null) Routes.HOME else Routes.LOGIN

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onUsers = { nav.navigate(Routes.USERS) },
                onGroups = { nav.navigate(Routes.GROUPS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onSwitchAccount = {
                    nav.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } }
                },
            )
        }
        composable(Routes.USERS) {
            UsersScreen(
                onBack = { nav.popBackStack() },
                onOpenUser = { nav.navigate(Routes.userDetail(it)) },
                onCreateUser = { nav.navigate(Routes.USER_NEW) },
            )
        }
        composable(Routes.USER_NEW) {
            CreateUserScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate(Routes.userDetail(id)) { popUpTo(Routes.USERS) } },
            )
        }
        composable(Routes.USER_DETAIL, arguments = listOf(navArgument("userId") { type = NavType.StringType })) { entry ->
            UserDetailScreen(userId = entry.arguments?.getString("userId").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.GROUPS) {
            GroupsScreen(onBack = { nav.popBackStack() }, onOpenGroup = { nav.navigate(Routes.groupDetail(it)) })
        }
        composable(Routes.GROUP_DETAIL, arguments = listOf(navArgument("groupId") { type = NavType.StringType })) { entry ->
            GroupDetailScreen(
                groupId = entry.arguments?.getString("groupId").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenUser = { nav.navigate(Routes.userDetail(it)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsHubScreen(
                onBack = { nav.popBackStack() },
                onOpenSection = { nav.navigate(Routes.section(it)) },
                onOpenSystem = { nav.navigate(Routes.SETTINGS_SYSTEM) },
                onOpenApps = { nav.navigate(Routes.SETTINGS_APPS) },
            )
        }
        composable(Routes.SETTINGS_SECTION, arguments = listOf(navArgument("sectionId") { type = NavType.StringType })) { entry ->
            SectionScreen(sectionId = entry.arguments?.getString("sectionId").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS_SYSTEM) { SystemConfigScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS_APPS) { AppsScreen(onBack = { nav.popBackStack() }) }
    }
}
