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

## Дорожная карта

- **Этап 2 — Параметры сервера** через `GET/POST/DELETE /ocs/v2.php/apps/provisioning_api/api/v1/config/apps/{app}/{key}`:
  общий доступ (`core/shareapi_*`), фоновые задания (`core/backgroundjobs_mode`),
  почтовый сервер (`core/mail_*`), политики паролей (`password_policy/*`), Theming (`theming/*`),
  список/включение приложений (`cloud/apps`).
- **Этап 3** — дашборд `serverinfo` (нагрузка, место, активные пользователи), просмотр логов,
  тёмная тема, EN-локализация.
- **Этап 4 (опционально)** — companion-приложение на стороне Nextcloud для настроек из
  `config.php` и `occ` (trusted_domains, maintenance mode, уровень логирования).
