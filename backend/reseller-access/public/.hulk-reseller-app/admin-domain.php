<?php

declare(strict_types=1);

/**
 * Authoritative reseller-owner mutations shared by the legacy owner panel and
 * HULK SA Control Center. The caller owns authentication, CSRF and auditing.
 */

function hulk_admin_reseller_id(mixed $value): int
{
    $resellerId = filter_var($value, FILTER_VALIDATE_INT);
    if (!is_int($resellerId) || $resellerId < 1) {
        throw new InvalidArgumentException('معرّف الموزع غير صالح.');
    }
    return $resellerId;
}

function hulk_admin_create_reseller(PDO $db, array $input): array
{
    $resellerName = trim(is_string($input['reseller_name'] ?? null) ? $input['reseller_name'] : '');
    $password = is_string($input['password'] ?? null) ? $input['password'] : '';
    $rawHost = trim(is_string($input['host'] ?? null) ? $input['host'] : '');
    $rawCode = trim(is_string($input['access_code'] ?? null) ? $input['access_code'] : '');
    $nameKey = hulk_normalize_reseller_name($resellerName);

    if ($nameKey === '' || strlen($resellerName) > 100 || strlen($password) < 10 || strlen($password) > 256) {
        throw new InvalidArgumentException('أدخل اسم موزع وكلمة مرور من 10 إلى 256 خانة.');
    }

    $host = '';
    if ($rawHost !== '') {
        $host = hulk_normalize_host($rawHost) ?? '';
        if ($host === '') {
            throw new InvalidArgumentException('رابط الهوست غير صالح.');
        }
    }

    $customCode = $rawCode !== '';
    $normalizedCode = $customCode ? hulk_normalize_access_code($rawCode) : null;
    if ($customCode && $normalizedCode === null) {
        throw new InvalidArgumentException('صيغة كود الدخول غير صالحة.');
    }

    $existing = $db->prepare('SELECT reseller_id FROM resellers WHERE reseller_name_key = :name_key LIMIT 1');
    $existing->execute(['name_key' => $nameKey]);
    if ($existing->fetchColumn() !== false) {
        throw new InvalidArgumentException('اسم الموزع مستخدم مسبقًا.');
    }

    $maximumAttempts = $customCode ? 1 : 5;
    for ($attempt = 0; $attempt < $maximumAttempts; $attempt++) {
        $accessCode = $customCode ? (string) $normalizedCode : hulk_generate_access_code();
        try {
            $statement = $db->prepare(
                'INSERT INTO resellers '
                . '(reseller_name, reseller_name_key, password_hash, host, access_code, access_code_hash, status) '
                . 'VALUES (:name, :name_key, :password_hash, :host, :access_code, :access_code_hash, :status)'
            );
            $statement->execute([
                'name' => $resellerName,
                'name_key' => $nameKey,
                'password_hash' => password_hash($password, PASSWORD_DEFAULT),
                'host' => $host,
                'access_code' => $accessCode,
                'access_code_hash' => hulk_access_code_hash($accessCode),
                'status' => HULK_ACTIVE_STATUS,
            ]);
            return ['reseller_id' => (int) $db->lastInsertId(), 'access_code' => $accessCode];
        } catch (PDOException $exception) {
            if ((string) $exception->getCode() !== '23000' || $customCode || $attempt === $maximumAttempts - 1) {
                throw $exception;
            }
        }
    }

    throw new RuntimeException('تعذر إنشاء كود دخول فريد.');
}

function hulk_admin_set_status(PDO $db, mixed $resellerId, mixed $status): void
{
    $id = hulk_admin_reseller_id($resellerId);
    $normalized = is_string($status) ? $status : '';
    if (!in_array($normalized, ['active', 'inactive'], true)) {
        throw new InvalidArgumentException('حالة الموزع غير صالحة.');
    }
    $statement = $db->prepare('UPDATE resellers SET status = :status WHERE reseller_id = :id');
    $statement->execute(['status' => $normalized, 'id' => $id]);
}

function hulk_admin_update_host(PDO $db, mixed $resellerId, mixed $value): string
{
    $id = hulk_admin_reseller_id($resellerId);
    $rawHost = trim(is_string($value) ? $value : '');
    $host = '';
    if ($rawHost !== '') {
        $host = hulk_normalize_host($rawHost) ?? '';
        if ($host === '') {
            throw new InvalidArgumentException('رابط الهوست غير صالح.');
        }
    }
    $statement = $db->prepare('UPDATE resellers SET host = :host WHERE reseller_id = :id');
    $statement->execute(['host' => $host, 'id' => $id]);
    return $host;
}

function hulk_admin_set_code(PDO $db, mixed $resellerId, mixed $value): string
{
    $id = hulk_admin_reseller_id($resellerId);
    $code = hulk_normalize_access_code(is_string($value) ? $value : '');
    if ($code === null) {
        throw new InvalidArgumentException('صيغة كود الدخول غير صالحة.');
    }
    hulk_admin_write_code($db, $id, $code);
    return $code;
}

function hulk_admin_rotate_code(PDO $db, mixed $resellerId): string
{
    $id = hulk_admin_reseller_id($resellerId);
    for ($attempt = 0; $attempt < 5; $attempt++) {
        $code = hulk_generate_access_code();
        try {
            hulk_admin_write_code($db, $id, $code);
            return $code;
        } catch (PDOException $exception) {
            if ((string) $exception->getCode() !== '23000' || $attempt === 4) {
                throw $exception;
            }
        }
    }
    throw new RuntimeException('تعذر إنشاء كود دخول فريد.');
}

function hulk_admin_write_code(PDO $db, int $resellerId, string $accessCode): void
{
    $statement = $db->prepare(
        'UPDATE resellers SET access_code = :access_code, access_code_hash = :access_code_hash '
        . 'WHERE reseller_id = :id'
    );
    $statement->execute([
        'access_code' => $accessCode,
        'access_code_hash' => hulk_access_code_hash($accessCode),
        'id' => $resellerId,
    ]);
}

function hulk_admin_reset_password(PDO $db, mixed $resellerId, mixed $password, mixed $confirmation): void
{
    $id = hulk_admin_reseller_id($resellerId);
    $value = is_string($password) ? $password : '';
    $confirmed = is_string($confirmation) ? $confirmation : '';
    if (strlen($value) < 10 || strlen($value) > 256) {
        throw new InvalidArgumentException('أدخل كلمة مرور من 10 إلى 256 خانة.');
    }
    if (!hash_equals($value, $confirmed)) {
        throw new InvalidArgumentException('كلمتا المرور غير متطابقتين.');
    }
    $statement = $db->prepare('UPDATE resellers SET password_hash = :password_hash WHERE reseller_id = :id');
    $statement->execute(['password_hash' => password_hash($value, PASSWORD_DEFAULT), 'id' => $id]);
}
