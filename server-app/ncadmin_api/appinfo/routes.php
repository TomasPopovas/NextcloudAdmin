<?php

declare(strict_types=1);

/**
 * OCS routes: /ocs/v2.php/apps/ncadmin_api/api/v1/...
 * All handlers live in ApiController and require an admin user.
 */
return [
    'ocs' => [
        ['name' => 'api#status',             'url' => '/api/v1/status',            'verb' => 'GET'],
        ['name' => 'api#confirm',            'url' => '/api/v1/confirm',           'verb' => 'POST'],
        ['name' => 'api#getAppConfig',       'url' => '/api/v1/config/apps/{app}', 'verb' => 'GET'],
        ['name' => 'api#getSystemConfig',    'url' => '/api/v1/system',            'verb' => 'GET'],
        ['name' => 'api#setSystemConfig',    'url' => '/api/v1/system/{key}',      'verb' => 'PUT'],
        ['name' => 'api#deleteSystemConfig', 'url' => '/api/v1/system/{key}',      'verb' => 'DELETE'],
        ['name' => 'api#testMail',           'url' => '/api/v1/mail/test',         'verb' => 'POST'],
    ],
];
