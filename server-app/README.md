# ncadmin_api — companion-приложение для Nextcloud

Нужно, потому что все операции записи в Provisioning API (создание/блокировка пользователей,
изменение appconfig и т.д.) помечены `#[PasswordConfirmationRequired]`, а вход по app-паролю
никогда не ставит отметку подтверждения пароля в сессии. Приложение добавляет OCS-эндпоинт
`confirm`, который ставит эту отметку для текущей (уже аутентифицированной, админской) сессии;
дальше мобильный клиент пользуется стандартным API, удерживая cookie сессии.

Дополнительно: массовое чтение appconfig, белый список ключей `config.php`, тест почты.

## Установка

```bash
# на сервере Nextcloud
cd /var/www/nextcloud/apps            # или apps-extra / custom_apps
cp -r /path/to/repo/server-app/ncadmin_api .
chown -R www-data:www-data ncadmin_api
sudo -u www-data php occ app:enable ncadmin_api
```

Проверка:

```bash
curl -u admin:APP-PASSWORD -H 'OCS-APIRequest: true' \
  'https://cloud.example.ru/ocs/v2.php/apps/ncadmin_api/api/v1/status?format=json'
```

Обновление: заменить папку и выполнить `occ app:update ncadmin_api` (либо `app:disable` / `app:enable`).

## Эндпоинты (`/ocs/v2.php/apps/ncadmin_api/api/v1/`)

| Метод | Путь | Назначение |
|---|---|---|
| GET | `status` | версия, состояние подтверждения, список разрешённых ключей config.php |
| POST | `confirm` | поставить `last-password-confirm` для текущей сессии (30 мин) |
| GET | `config/apps/{app}` | все значения appconfig приложения одним запросом |
| GET | `system` | разрешённые ключи config.php с типами (секреты маскируются) |
| PUT | `system/{key}` | записать значение (`value` как строка; массивы — JSON) |
| DELETE | `system/{key}` | удалить ключ (вернуть значение по умолчанию) |
| POST | `mail/test` | отправить тестовое письмо на e-mail текущего администратора |

Все методы требуют члена группы `admin` (атрибут `NoAdminRequired` намеренно не используется).

## Безопасность

- `confirm` не ослабляет аутентификацию: вызвать его может только уже вошедший администратор
  с действующим app-паролем. Он лишь уравнивает app-пароль с браузерной сессией,
  в которой админ ввёл пароль повторно.
- Белый список `config.php` не содержит ключей БД, секретов инстанса, путей данных и т.п.
- Ключ `maintenance` отдаётся на чтение; включать режим обслуживания с телефона не стоит —
  после этого OCS API перестанет отвечать, и снять его можно будет только через `occ`.
