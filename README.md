# NC Admin — Android-клиент администратора Nextcloud

Приложение для администрирования Nextcloud 34 / 35 с телефона: пользователи, группы,
а далее — параметры сервера. Kotlin + Jetpack Compose, без Hilt, минимум зависимостей.

## Этап 1 (этот архив)

- Вход через **Login Flow v2** (браузер → app-password; работает с 2FA и SSO). Основной пароль
  приложение не видит и не хранит.
- Несколько серверов; app-пароли в `EncryptedSharedPreferences` (ключ в Android Keystore).
- При входе проверяется версия сервера (`cloud/capabilities`, допускаются major 34 и 35)
  и членство в группе `admin`.
- **Пользователи**: список с поиском и постраничной подгрузкой, фильтр активные/заблокированные,
  карточка (профиль, квота и занятое место, группы, субадмин-группы, последний вход, бэкенд),
  редактирование displayname/email/телефона/адреса/сайта, квота (по умолчанию / без ограничений /
  своя), смена пароля, блокировка/разблокировка, удаление, приветственное письмо,
  добавление/удаление из групп, назначение/снятие администратора групп, создание пользователя.
- **Группы**: список с числом участников, создание, переименование, удаление, состав группы,
  добавление/удаление участников (поиск по пользователям).

## Сборка

1. Android Studio Ladybug (2024.2) или новее, JDK 17.
2. Открыть папку проекта. Gradle wrapper скачает Gradle 8.11.1 (в архиве нет `gradle-wrapper.jar` —
   Android Studio предложит его сгенерировать, либо выполните `gradle wrapper` при установленном Gradle).
3. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

minSdk 26, targetSdk 35. Для http:// без TLS нужно разрешить cleartext в манифесте
(`android:usesCleartextTraffic="true"`) — по умолчанию выключено.

## Структура

```
app/src/main/java/ru/tomas/ncadmin/
├── NcAdminApp.kt, MainActivity.kt
├── data/
│   ├── AppContainer.kt          — ручной DI, Session на аккаунт
│   ├── api/OcsClient.kt         — OCS v2 поверх OkHttp, разбор ocs/meta/data
│   ├── api/JsonExt.kt, Models.kt — мягкий разбор JSON (PHP: [] vs {}), NcUser/NcGroup/NcQuota
│   ├── auth/LoginFlow.kt        — Login Flow v2
│   ├── auth/AccountStore.kt     — зашифрованное хранилище аккаунтов
│   └── repo/                    — UsersRepository, GroupsRepository, ServerRepository
└── ui/
    ├── nav/NavGraph.kt
    ├── login/, home/, users/, groups/, common/
```

## Используемые API

| Действие | Запрос |
|---|---|
| Версия сервера | `GET /ocs/v2.php/cloud/capabilities` |
| Текущий пользователь | `GET /ocs/v2.php/cloud/user` |
| Список пользователей | `GET /ocs/v2.php/cloud/users/details?search&limit&offset` |
| Создать | `POST /ocs/v2.php/cloud/users` |
| Изменить поле | `PUT /ocs/v2.php/cloud/users/{id}` (`key`, `value`) |
| Вкл/выкл | `PUT …/users/{id}/enable` / `disable` |
| Удалить | `DELETE …/users/{id}` |
| Группы пользователя | `POST`/`DELETE …/users/{id}/groups` (`groupid`) |
| Субадмин | `POST`/`DELETE …/users/{id}/subadmins` |
| Welcome-письмо | `POST …/users/{id}/welcome` |
| Группы | `GET /ocs/v2.php/cloud/groups/details`, `POST /groups`, `PUT /groups/{id}`, `DELETE /groups/{id}` |
| Состав группы | `GET …/groups/{id}/users/details` |

Nextcloud 34 и 35 используют один и тот же Provisioning API; в 35.0.3 исправлена серверная
фильтрация по `enabled` — пока фильтр делается на клиенте по загруженной странице.

## Этап 2 — Параметры сервера (ветка `stage-2-settings`)

**Важно: нужно companion-приложение `server-app/ncadmin_api`** — установка описана в
`server-app/README.md`. Причина: все операции записи Provisioning API (в том числе создание
и блокировка пользователей из этапа 1) требуют подтверждения пароля, которое app-пароль дать
не может. Companion ставит отметку подтверждения для сессии; клиент держит cookie сессии
и повторяет запрос автоматически. Без companion приложение работает в режиме чтения.

- Разделы appconfig (чтение через Provisioning API, запись — после авто-подтверждения):
  Основные параметры, Общий доступ, Федеративный доступ, Безопасность (политика паролей, 2FA),
  Оформление (с инкрементом `theming/cachebuster`).
- Приложения: список включённых/выключенных, переключение (системные защищены).
- `config.php` через companion: почтовый сервер + тестовое письмо, логирование, доверенные
  домены, сессии, превью, хранение корзины/версий. Белый список ключей — в `ApiController::SYSTEM_KEYS`.

Каталог настроек — `ui/settings/SettingsCatalog.kt`: добавить ключ = добавить одну строку.

## Дорожная карта

- **Этап 3** — дашборд `serverinfo` (нагрузка, место, активные пользователи), просмотр логов,
  EN-локализация, UI-полировка.
- **Этап 4** — расширение companion: `occ`-операции (maintenance mode с автоснятием, repair,
  files:scan), LDAP-конфиг через OCS user_ldap API, групповые папки.
