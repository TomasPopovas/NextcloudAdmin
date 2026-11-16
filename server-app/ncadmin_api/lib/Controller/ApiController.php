<?php

declare(strict_types=1);

namespace OCA\NcAdminApi\Controller;

use OCA\NcAdminApi\AppInfo\Application;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\DataResponse;
use OCP\AppFramework\OCSController;
use OCP\AppFramework\Utility\ITimeFactory;
use OCP\IAppConfig;
use OCP\IConfig;
use OCP\IRequest;
use OCP\ISession;
use OCP\IUserSession;
use OCP\Mail\IMailer;

/**
 * Admin-only OCS API for the NC Admin Android client.
 *
 * None of the methods carry #[NoAdminRequired], so the AppFramework security
 * middleware rejects every non-admin caller before the controller runs.
 * CSRF is covered by the standard "OCS-APIRequest: true" header rule.
 */
class ApiController extends OCSController {

    /** Lifetime of a password confirmation in core (30 min + 15 s grace). */
    private const CONFIRM_TTL = 30 * 60;

    /**
     * Allow-listed config.php keys exposed to the client.
     * type: bool | int | string | array | secret (string, masked on read)
     */
    private const SYSTEM_KEYS = [
        // General
        'maintenance'                        => 'bool',
        'maintenance_window_start'           => 'int',
        'loglevel'                           => 'int',
        'log_rotate_size'                    => 'int',
        'trusted_domains'                    => 'array',
        'overwrite.cli.url'                  => 'string',
        'default_language'                   => 'string',
        'default_locale'                     => 'string',
        'default_phone_region'               => 'string',
        'defaultapp'                         => 'string',
        'skeletondirectory'                  => 'string',
        'templatedirectory'                  => 'string',
        'knowledgebaseenabled'               => 'bool',
        'allow_user_to_change_display_name'  => 'bool',
        'lost_password_link'                 => 'string',
        'has_internet_connection'            => 'bool',
        'updatechecker'                      => 'bool',
        'updater.release.channel'            => 'string',
        'upgrade.disable-web'                => 'bool',
        // Security
        'auth.bruteforce.protection.enabled' => 'bool',
        'ratelimit.protection.enabled'       => 'bool',
        'session_lifetime'                   => 'int',
        'remember_login_cookie_lifetime'     => 'int',
        'token_auth_enforced'                => 'bool',
        'auth.webauthn.enabled'              => 'bool',
        // Files
        'trashbin_retention_obligation'      => 'string',
        'versions_retention_obligation'      => 'string',
        'filelocking.enabled'                => 'bool',
        'enable_previews'                    => 'bool',
        'preview_max_x'                      => 'int',
        'preview_max_y'                      => 'int',
        // Mail
        'mail_smtpmode'                      => 'string',
        'mail_sendmailmode'                  => 'string',
        'mail_smtphost'                      => 'string',
        'mail_smtpport'                      => 'int',
        'mail_smtpsecure'                    => 'string',
        'mail_smtpauth'                      => 'bool',
        'mail_smtpname'                      => 'string',
        'mail_smtppassword'                  => 'secret',
        'mail_from_address'                  => 'string',
        'mail_domain'                        => 'string',
        'mail_smtpdebug'                     => 'bool',
        'mail_smtptimeout'                   => 'int',
    ];

    public function __construct(
        string $appName,
        IRequest $request,
        private ISession $session,
        private IConfig $config,
        private IAppConfig $appConfig,
        private ITimeFactory $timeFactory,
        private IUserSession $userSession,
        private IMailer $mailer,
    ) {
        parent::__construct($appName, $request);
    }

    /**
     * GET /api/v1/status — companion version and confirmation state.
     */
    public function status(): DataResponse {
        $last = (int)$this->session->get('last-password-confirm');
        $now = $this->timeFactory->getTime();
        return new DataResponse([
            'app'            => Application::APP_ID,
            'version'        => Application::VERSION,
            'nextcloud'      => $this->config->getSystemValueString('version', ''),
            'user'           => $this->userSession->getUser()?->getUID(),
            'confirmed'      => $last > 0 && ($now - $last) < self::CONFIRM_TTL,
            'confirmedUntil' => $last > 0 ? $last + self::CONFIRM_TTL : 0,
            'systemKeys'     => array_keys(self::SYSTEM_KEYS),
        ]);
    }

    /**
     * POST /api/v1/confirm — mark the current session as password-confirmed.
     *
     * Core's PasswordConfirmationMiddleware only looks at the
     * `last-password-confirm` session value; an app-password login never sets it.
     * The caller is already an authenticated admin (enforced by the middleware),
     * so granting the confirmation to this very session is equivalent to what a
     * browser admin gets after typing the password again.
     */
    public function confirm(): DataResponse {
        $now = $this->timeFactory->getTime();
        $this->session->set('last-password-confirm', $now);
        return new DataResponse([
            'confirmed'      => true,
            'confirmedUntil' => $now + self::CONFIRM_TTL,
        ]);
    }

    /**
     * GET /api/v1/config/apps/{app} — every app config value of one app in a single call.
     */
    public function getAppConfig(string $app): DataResponse {
        if (!preg_match('/^[a-z0-9_]+$/', $app)) {
            return new DataResponse(['message' => 'Invalid app id'], Http::STATUS_BAD_REQUEST);
        }
        $values = $this->appConfig->getAllValues($app);
        return new DataResponse(['app' => $app, 'values' => $values]);
    }

    /**
     * GET /api/v1/system — allow-listed config.php values with their types.
     */
    public function getSystemConfig(): DataResponse {
        $out = [];
        foreach (self::SYSTEM_KEYS as $key => $type) {
            $raw = $this->config->getSystemValue($key, null);
            $out[$key] = [
                'type'  => $type,
                'set'   => $raw !== null,
                'value' => $type === 'secret' ? ($raw ? '********' : '') : $raw,
            ];
        }
        return new DataResponse(['keys' => $out]);
    }

    /**
     * PUT /api/v1/system/{key} — set one allow-listed config.php value.
     * The value is always sent as a string and converted according to the key type;
     * arrays are JSON-encoded strings.
     */
    public function setSystemConfig(string $key, string $value): DataResponse {
        if (!isset(self::SYSTEM_KEYS[$key])) {
            return new DataResponse(['message' => 'Key is not allowed'], Http::STATUS_FORBIDDEN);
        }
        $type = self::SYSTEM_KEYS[$key];
        try {
            $typed = $this->convert($type, $value);
        } catch (\InvalidArgumentException $e) {
            return new DataResponse(['message' => $e->getMessage()], Http::STATUS_BAD_REQUEST);
        }
        $this->config->setSystemValue($key, $typed);
        return new DataResponse(['key' => $key, 'value' => $type === 'secret' ? '********' : $typed]);
    }

    /**
     * DELETE /api/v1/system/{key} — remove the key so the built-in default applies again.
     */
    public function deleteSystemConfig(string $key): DataResponse {
        if (!isset(self::SYSTEM_KEYS[$key])) {
            return new DataResponse(['message' => 'Key is not allowed'], Http::STATUS_FORBIDDEN);
        }
        $this->config->deleteSystemValue($key);
        return new DataResponse(['key' => $key, 'deleted' => true]);
    }

    /**
     * POST /api/v1/mail/test — send a test message to the calling admin's e-mail address.
     */
    public function testMail(): DataResponse {
        $user = $this->userSession->getUser();
        $email = $user?->getEMailAddress();
        if ($user === null || empty($email)) {
            return new DataResponse(['message' => 'Set an e-mail address for your account first'], Http::STATUS_BAD_REQUEST);
        }
        if (!$this->mailer->validateMailAddress($email)) {
            return new DataResponse(['message' => 'Invalid e-mail address on the admin account'], Http::STATUS_BAD_REQUEST);
        }
        try {
            $template = $this->mailer->createEMailTemplate('ncadmin_api.TestEmail', []);
            $template->setSubject('Nextcloud test e-mail (NC Admin)');
            $template->addHeader();
            $template->addHeading('Test e-mail');
            $template->addBodyText('If you received this e-mail, the mail server settings of your Nextcloud instance are correct.');
            $template->addFooter();

            $message = $this->mailer->createMessage();
            $message->setTo([$email => $user->getDisplayName()]);
            $message->useTemplate($template);
            $errors = $this->mailer->send($message);
            if (!empty($errors)) {
                return new DataResponse(['message' => 'Mailer reported failures for: ' . implode(', ', $errors)], Http::STATUS_INTERNAL_SERVER_ERROR);
            }
        } catch (\Throwable $e) {
            return new DataResponse(['message' => $e->getMessage()], Http::STATUS_INTERNAL_SERVER_ERROR);
        }
        return new DataResponse(['sent' => true, 'to' => $email]);
    }

    /** @return bool|int|string|array */
    private function convert(string $type, string $value): mixed {
        return match ($type) {
            'bool' => in_array(strtolower(trim($value)), ['1', 'true', 'yes', 'on'], true),
            'int' => (function () use ($value) {
                if (!is_numeric($value)) {
                    throw new \InvalidArgumentException('Integer expected');
                }
                return (int)$value;
            })(),
            'array' => (function () use ($value) {
                $decoded = json_decode($value, true);
                if (!is_array($decoded)) {
                    throw new \InvalidArgumentException('JSON array expected');
                }
                return $decoded;
            })(),
            default => $value,
        };
    }
}
