package ru.tomas.ncadmin.ui.settings

/**
 * Каталог настроек «Параметры сервера», которые хранятся в appconfig и доступны через
 * Provisioning API. Ключи взяты из apps/settings/lib/Settings/Admin (PHP) и
 * core/AppInfo/ConfigLexicon (Nextcloud 34/35).
 *
 * Типы значений:
 *  - YES_NO      — строка "yes"/"no" (старые ключи)
 *  - BOOL        — типизированный bool из ConfigLexicon, пишем "true"/"false"
 *  - ONE_ZERO    — строка "1"/"0"
 *  - INT, TEXT, MULTILINE
 *  - GROUPS      — JSON-массив идентификаторов групп
 *  - CHOICE      — одно из значений choices
 */
enum class SettingType { YES_NO, BOOL, ONE_ZERO, INT, TEXT, MULTILINE, GROUPS, CHOICE }

data class SettingSpec(
    val app: String,
    val key: String,
    val title: String,
    val type: SettingType,
    val default: String = "",
    val description: String? = null,
    val choices: List<Pair<String, String>> = emptyList(),
    val readOnly: Boolean = false,
) {
    val id: String get() = "$app/$key"
    val isSwitch: Boolean get() = type == SettingType.YES_NO || type == SettingType.BOOL || type == SettingType.ONE_ZERO

    /** Преобразование булева значения в строку для записи. */
    fun encodeBool(v: Boolean): String = when (type) {
        SettingType.YES_NO -> if (v) "yes" else "no"
        SettingType.BOOL -> if (v) "true" else "false"
        SettingType.ONE_ZERO -> if (v) "1" else "0"
        else -> v.toString()
    }

    /** Разбор булева значения из любого представления. */
    fun decodeBool(raw: String?): Boolean {
        val s = (raw ?: default).trim().lowercase()
        return s == "yes" || s == "true" || s == "1" || s == "on"
    }
}

data class SettingsSection(
    val id: String,
    val title: String,
    val description: String,
    val specs: List<SettingSpec>,
    /** Для theming: после записи нужно увеличить cachebuster. */
    val bumpCacheBuster: Boolean = false,
)

object SettingsCatalog {

    val sharing = SettingsSection(
        id = "sharing",
        title = "Общий доступ",
        description = "Политики общего доступа: ссылки, пароли, сроки действия, перечисление пользователей",
        specs = listOf(
            SettingSpec("core", "shareapi_enabled", "Разрешить приложениям использовать API общего доступа", SettingType.YES_NO, "yes"),
            SettingSpec("core", "shareapi_allow_links", "Разрешить общий доступ по ссылке", SettingType.YES_NO, "yes"),
            SettingSpec("core", "shareapi_allow_links_exclude_groups", "Группы, которым запрещён общий доступ по ссылке", SettingType.GROUPS, "[]"),
            SettingSpec("core", "shareapi_allow_public_upload", "Разрешить загрузку в публичные папки", SettingType.YES_NO, "yes"),
            SettingSpec("core", "shareapi_enforce_links_password", "Требовать пароль для публичных ссылок", SettingType.YES_NO, "no"),
            SettingSpec("core", "shareapi_enforce_links_password_excluded_groups", "Группы, освобождённые от обязательного пароля", SettingType.GROUPS, "[]"),
            SettingSpec("core", "shareapi_enable_link_password_by_default", "Включать пароль для ссылок по умолчанию", SettingType.BOOL, "false"),
            SettingSpec("core", "shareapi_allow_federation_on_public_shares", "Разрешить добавлять публичные ссылки как федеративные", SettingType.BOOL, "true"),
            SettingSpec("core", "shareapi_default_expire_date", "Срок действия публичных ссылок по умолчанию", SettingType.BOOL, "false"),
            SettingSpec("core", "shareapi_expire_after_n_days", "Срок действия публичных ссылок, дней", SettingType.INT, "7"),
            SettingSpec("core", "shareapi_enforce_expire_date", "Принудительный срок для публичных ссылок", SettingType.BOOL, "false"),
            SettingSpec("core", "shareapi_default_internal_expire_date", "Срок действия внутренних ресурсов по умолчанию", SettingType.YES_NO, "no"),
            SettingSpec("core", "shareapi_internal_expire_after_n_days", "Срок внутренних ресурсов, дней", SettingType.INT, "7"),
            SettingSpec("core", "shareapi_enforce_internal_expire_date", "Принудительный срок для внутренних ресурсов", SettingType.YES_NO, "no"),
            SettingSpec("core", "shareapi_default_remote_expire_date", "Срок действия федеративных ресурсов по умолчанию", SettingType.YES_NO, "no"),
            SettingSpec("core", "shareapi_remote_expire_after_n_days", "Срок федеративных ресурсов, дней", SettingType.INT, "7"),
            SettingSpec("core", "shareapi_enforce_remote_expire_date", "Принудительный срок для федеративных ресурсов", SettingType.YES_NO, "no"),
            SettingSpec("core", "shareapi_allow_resharing", "Разрешить повторное предоставление доступа", SettingType.YES_NO, "yes"),
            SettingSpec("core", "shareapi_allow_group_sharing", "Разрешить общий доступ для групп", SettingType.YES_NO, "yes"),
            SettingSpec("core", "shareapi_only_share_with_group_members", "Делиться только с участниками своих групп", SettingType.YES_NO, "no"),
            SettingSpec("core", "shareapi_only_share_with_group_members_exclude_group_list", "Группы-исключения для предыдущего правила", SettingType.GROUPS, "[]"),
            SettingSpec("core", "shareapi_exclude_groups", "Запретить общий доступ некоторым группам", SettingType.YES_NO, "no"),
            SettingSpec("core", "shareapi_exclude_groups_list", "Группы, которым запрещён общий доступ", SettingType.GROUPS, "[]"),
            SettingSpec("core", "shareapi_allow_share_dialog_user_enumeration", "Разрешить автодополнение имён в диалоге доступа", SettingType.YES_NO, "yes"),
            SettingSpec("core", "shareapi_restrict_user_enumeration_to_group", "Автодополнение только внутри своих групп", SettingType.YES_NO, "no"),
            SettingSpec("core", "shareapi_restrict_user_enumeration_to_phone", "Автодополнение только по совпадению телефона", SettingType.YES_NO, "no"),
            SettingSpec("core", "shareapi_restrict_user_enumeration_full_match", "Показывать точные совпадения при ограничениях", SettingType.YES_NO, "yes"),
            SettingSpec("core", "shareapi_default_permissions", "Права по умолчанию (битовая маска: 1 чтение, 2 изменение, 4 создание, 8 удаление, 16 доступ)", SettingType.INT, "31"),
            SettingSpec("core", "shareapi_public_link_disclaimertext", "Текст отказа от ответственности для публичных ссылок", SettingType.MULTILINE, ""),
        ),
    )

    val federation = SettingsSection(
        id = "federation",
        title = "Федеративный доступ",
        description = "Обмен файлами с другими серверами Nextcloud и глобальный каталог",
        specs = listOf(
            SettingSpec("files_sharing", "outgoing_server2server_share_enabled", "Разрешить отправлять ресурсы на другие серверы", SettingType.YES_NO, "yes"),
            SettingSpec("files_sharing", "incoming_server2server_share_enabled", "Разрешить принимать ресурсы с других серверов", SettingType.YES_NO, "yes"),
            SettingSpec("files_sharing", "outgoing_server2server_group_share_enabled", "Разрешить отправлять групповые ресурсы", SettingType.YES_NO, "no"),
            SettingSpec("files_sharing", "incoming_server2server_group_share_enabled", "Разрешить принимать групповые ресурсы", SettingType.YES_NO, "no"),
            SettingSpec("files_sharing", "lookupServerEnabled", "Искать пользователей через глобальный каталог", SettingType.YES_NO, "yes"),
            SettingSpec("files_sharing", "lookupServerUploadEnabled", "Публиковать данные пользователей в глобальном каталоге", SettingType.YES_NO, "yes"),
        ),
    )

    val security = SettingsSection(
        id = "security",
        title = "Безопасность",
        description = "Политика паролей и принудительная двухфакторная аутентификация",
        specs = listOf(
            SettingSpec("password_policy", "minLength", "Минимальная длина пароля", SettingType.INT, "10"),
            SettingSpec("password_policy", "enforceNonCommonPassword", "Запретить распространённые пароли", SettingType.ONE_ZERO, "1"),
            SettingSpec("password_policy", "enforceUpperLowerCase", "Требовать буквы верхнего и нижнего регистра", SettingType.ONE_ZERO, "0"),
            SettingSpec("password_policy", "enforceNumericCharacters", "Требовать цифры", SettingType.ONE_ZERO, "0"),
            SettingSpec("password_policy", "enforceSpecialCharacters", "Требовать спецсимволы", SettingType.ONE_ZERO, "0"),
            SettingSpec("password_policy", "enforceHaveIBeenPwned", "Проверять по базе haveibeenpwned.com", SettingType.ONE_ZERO, "1"),
            SettingSpec("password_policy", "expiration", "Срок действия пароля, дней (0 — без срока)", SettingType.INT, "0"),
            SettingSpec("password_policy", "maximumLoginAttempts", "Блокировать после N неудачных входов (0 — выкл.)", SettingType.INT, "0"),
            SettingSpec("password_policy", "historySize", "Запрещать повторное использование последних N паролей", SettingType.INT, "0"),
            SettingSpec("core", "twofactor_enforced", "Принудительная двухфакторная аутентификация", SettingType.BOOL, "false"),
            SettingSpec("core", "twofactor_enforced_groups", "Только для групп (пусто — для всех)", SettingType.GROUPS, "[]"),
            SettingSpec("core", "twofactor_enforced_excluded_groups", "Группы-исключения", SettingType.GROUPS, "[]"),
        ),
    )

    val basic = SettingsSection(
        id = "basic",
        title = "Основные параметры",
        description = "Квота по умолчанию, фоновые задания, профили",
        specs = listOf(
            SettingSpec("files", "default_quota", "Квота по умолчанию (например 10 GB, none — без ограничений)", SettingType.TEXT, "none"),
            SettingSpec("files", "allow_unlimited_quota", "Разрешать неограниченную квоту", SettingType.ONE_ZERO, "1"),
            SettingSpec("core", "backgroundjobs_mode", "Режим фоновых заданий", SettingType.CHOICE, "ajax",
                choices = listOf("ajax" to "AJAX", "webcron" to "Webcron", "cron" to "Cron (рекомендуется)")),
            SettingSpec("core", "lastcron", "Последний запуск cron (unix time)", SettingType.TEXT, "", readOnly = true),
            SettingSpec("settings", "profile_enabled_by_default", "Профили пользователей включены по умолчанию", SettingType.ONE_ZERO, "1"),
            SettingSpec("core", "lostpassword_link", "Ссылка «Забыли пароль?» (пусто — стандартная, disabled — скрыть)", SettingType.TEXT, ""),
        ),
    )

    val theming = SettingsSection(
        id = "theming",
        title = "Оформление",
        description = "Название, слоган, цвета и ссылки инстанса (приложение Theming)",
        bumpCacheBuster = true,
        specs = listOf(
            SettingSpec("theming", "name", "Название", SettingType.TEXT, "Nextcloud"),
            SettingSpec("theming", "slogan", "Слоган", SettingType.TEXT, ""),
            SettingSpec("theming", "url", "Ссылка с логотипа", SettingType.TEXT, "https://nextcloud.com"),
            SettingSpec("theming", "primary_color", "Основной цвет (#RRGGBB)", SettingType.TEXT, "#0082c9"),
            SettingSpec("theming", "background_color", "Цвет фона (#RRGGBB)", SettingType.TEXT, ""),
            SettingSpec("theming", "imprintUrl", "Ссылка на реквизиты", SettingType.TEXT, ""),
            SettingSpec("theming", "privacyUrl", "Ссылка на политику конфиденциальности", SettingType.TEXT, ""),
            SettingSpec("theming", "disable-user-theming", "Запретить пользователям менять тему", SettingType.YES_NO, "no"),
        ),
    )

    val sections: List<SettingsSection> = listOf(basic, sharing, federation, security, theming)

    fun byId(id: String): SettingsSection? = sections.firstOrNull { it.id == id }
}
