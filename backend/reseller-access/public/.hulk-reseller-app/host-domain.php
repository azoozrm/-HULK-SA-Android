<?php

declare(strict_types=1);

/** Host IDs deliberately preserve legacy numeric association IDs in control history */
function hulk_host_relation(): string
{
    return '(SELECT host_id AS reseller_id, name AS reseller_name, host, status, created_at, updated_at FROM hosts)';
}

function hulk_host_code_relation(): string
{
    return "(SELECT h.host_id AS reseller_id, h.name AS reseller_name, h.host, c.code_id, c.access_code, c.access_code_hash, "
        . "CASE WHEN h.status = 'active' AND c.status = 'active' THEN 'active' ELSE 'inactive' END AS status, "
        . 'c.status AS code_status, h.status AS host_status FROM hosts h JOIN host_access_codes c ON c.host_id = h.host_id)';
}

function hulk_host_id(mixed $value): int
{
    $id = filter_var($value, FILTER_VALIDATE_INT);
    if (!is_int($id) || $id < 1) {
        throw new InvalidArgumentException('السجل المحدد غير صالح');
    }
    return $id;
}

function hulk_host_status(mixed $value): string
{
    if (!is_string($value) || !in_array($value, ['active', 'inactive'], true)) {
        throw new InvalidArgumentException('اختر حالة صالحة');
    }
    return $value;
}

function hulk_host_input(PDO $db, array $input, ?array $current = null): array
{
    $name = trim(is_string($input['name'] ?? null) ? $input['name'] : '');
    $host = hulk_normalize_host(is_string($input['host'] ?? null) ? $input['host'] : '');
    if ($name === '' || mb_strlen($name, 'UTF-8') > 100) {
        throw new InvalidArgumentException('ادخل اسما واضحا للهوست حتى 100 خانة');
    }
    if ($host === null) {
        throw new InvalidArgumentException('ادخل رابط هوست صالحا من دون بيانات دخول');
    }
    $status = hulk_host_status($input['status'] ?? 'active');
    // Existing duplicate URLs remain separate historical identities; no new duplicates
    $statement = $db->prepare('SELECT host_id, host FROM hosts WHERE host_id <> :id');
    $statement->execute(['id' => (int) ($current['host_id'] ?? 0)]);
    foreach ($statement->fetchAll() as $row) {
        if (hulk_normalize_host((string) $row['host']) === $host
            && ($current === null || hulk_normalize_host((string) $current['host']) !== $host)) {
            throw new InvalidArgumentException('هذا الهوست موجود بالفعل، افتحه لتعديل حالته او انشاء كود اخر');
        }
    }
    return ['name' => $name, 'host' => $host, 'status' => $status];
}

function hulk_host_create(PDO $db, array $input): int
{
    $values = hulk_host_input($db, $input);
    $statement = $db->prepare('INSERT INTO hosts (name, host, host_key, status) VALUES (:name, :host, :host_key, :status)');
    $statement->execute($values + ['host_key' => hash('sha256', $values['host'])]);
    return (int) $db->lastInsertId();
}

function hulk_host_update(PDO $db, mixed $hostId, array $input): void
{
    $id = hulk_host_id($hostId);
    $lookup = $db->prepare('SELECT host_id, host, host_key FROM hosts WHERE host_id = :id');
    $lookup->execute(['id' => $id]);
    $current = $lookup->fetch();
    if (!is_array($current)) {
        throw new InvalidArgumentException('الهوست غير موجود');
    }
    $values = hulk_host_input($db, $input, $current);
    // Preserve the nullable key on an unchanged imported duplicate URL
    $key = hulk_normalize_host((string) $current['host']) === $values['host']
        ? $current['host_key'] : hash('sha256', $values['host']);
    $statement = $db->prepare('UPDATE hosts SET name = :name, host = :host, host_key = :host_key, status = :status WHERE host_id = :id');
    $statement->execute($values + ['host_key' => $key, 'id' => $id]);
}

function hulk_host_validate_code_alias(PDO $db, string $code, int $exceptId = 0): void
{
    $canonical=hulk_legacy_canonical_code($code);
    if($canonical===null)return;
    $query=$db->prepare('SELECT code_id, access_code FROM host_access_codes WHERE code_id <> :id');
    $query->execute(['id'=>$exceptId]);
    foreach($query->fetchAll() as $existing) {
        if(hulk_legacy_canonical_code((string)$existing['access_code'])===$canonical) {
            throw new InvalidArgumentException('الكود او احدى صيغه مستخدم مسبقا');
        }
    }
}

function hulk_host_create_code(PDO $db, mixed $hostId, mixed $rawCode = ''): array
{
    $id = hulk_host_id($hostId);
    $lookup = $db->prepare('SELECT host FROM hosts WHERE host_id = :id');
    $lookup->execute(['id' => $id]);
    $host = $lookup->fetchColumn();
    if (!is_string($host) || hulk_normalize_host($host) === null) {
        throw new InvalidArgumentException('صحح رابط الهوست قبل انشاء كود');
    }
    $raw = is_string($rawCode) ? trim($rawCode) : '';
    $custom = $raw !== '';
    $code = $custom ? hulk_normalize_access_code($raw) : null;
    if ($custom && $code === null) {
        throw new InvalidArgumentException('صيغة الكود غير صالحة');
    }
    if ($custom) hulk_host_validate_code_alias($db, (string)$code);
    for ($attempt = 0; $attempt < ($custom ? 1 : 5); $attempt++) {
        $value = $custom ? (string) $code : hulk_generate_access_code();
        try {
            $statement = $db->prepare("INSERT INTO host_access_codes (host_id, access_code, access_code_hash, alias_key, status) VALUES (:host_id, :code, :hash, :alias_key, 'active')");
            $statement->execute(['host_id' => $id, 'code' => $value, 'hash' => hulk_access_code_hash($value), 'alias_key'=>hulk_legacy_canonical_code($value)===null?null:hash('sha256',(string)hulk_legacy_canonical_code($value))]);
            return ['code_id' => (int) $db->lastInsertId(), 'access_code' => $value];
        } catch (PDOException $exception) {
            if ((string) $exception->getCode() !== '23000' || $custom || $attempt === 4) {
                throw $exception;
            }
        }
    }
    throw new RuntimeException('تعذر انشاء كود فريد');
}

function hulk_host_require_code(PDO $db, mixed $codeId): int
{
    $id = hulk_host_id($codeId);
    $lookup = $db->prepare('SELECT code_id FROM host_access_codes WHERE code_id = :id');
    $lookup->execute(['id' => $id]);
    if ($lookup->fetchColumn() === false) {
        throw new InvalidArgumentException('الكود غير موجود');
    }
    return $id;
}

function hulk_host_set_code_status(PDO $db, mixed $codeId, mixed $status): void
{
    $id = hulk_host_require_code($db, $codeId);
    $value = hulk_host_status($status);
    $statement = $db->prepare('UPDATE host_access_codes SET status = :status WHERE code_id = :id');
    $statement->execute(['status' => $value, 'id' => $id]);
}

function hulk_host_set_code(PDO $db, mixed $codeId, mixed $rawCode): string
{
    $id = hulk_host_require_code($db, $codeId);
    $code = hulk_normalize_access_code(is_string($rawCode) ? $rawCode : '');
    if ($code === null) {
        throw new InvalidArgumentException('صيغة الكود غير صالحة');
    }
    hulk_host_validate_code_alias($db, $code, $id);
    $statement = $db->prepare('UPDATE host_access_codes SET access_code = :code, access_code_hash = :hash, alias_key = :alias_key WHERE code_id = :id');
    $statement->execute(['code' => $code, 'hash' => hulk_access_code_hash($code), 'id' => $id, 'alias_key'=>hulk_legacy_canonical_code($code)===null?null:hash('sha256',(string)hulk_legacy_canonical_code($code))]);
    return $code;
}

function hulk_host_rotate_code(PDO $db, mixed $codeId): string
{
    for ($attempt = 0; $attempt < 5; $attempt++) {
        try {
            return hulk_host_set_code($db, $codeId, hulk_generate_access_code());
        } catch (PDOException $exception) {
            if ((string) $exception->getCode() !== '23000' || $attempt === 4) {
                throw $exception;
            }
        }
    }
    throw new RuntimeException('تعذر انشاء كود فريد');
}

function hulk_host_code_lookup(PDO $db, string $canonicalCode): ?array
{
    $statement = $db->prepare('SELECT * FROM ' . hulk_host_code_relation() . ' host_codes WHERE access_code_hash = :hash LIMIT 1');
    $statement->execute(['hash' => hulk_access_code_hash($canonicalCode)]);
    $row = $statement->fetch();
    if (is_array($row)) {
        return $row;
    }
    $legacy = hulk_legacy_canonical_code($canonicalCode);
    if ($legacy !== null && $legacy !== $canonicalCode) {
        $statement->execute(['hash' => hulk_access_code_hash($legacy)]);
        $row = $statement->fetch();
    }
    return is_array($row) ? $row : null;
}

/** Explicit offline migration only: callers install DDL and quiesce legacy writers first */
function hulk_hosts_migrate(PDO $db): bool
{
    if ($db->inTransaction()) {
        throw new RuntimeException('Migration requires its own transaction');
    }
    $db->beginTransaction();
    try {
        $key = '2026-11-01-direct-hosts-v1';
        $marker = $db->prepare('SELECT migration_key FROM host_schema_migrations WHERE migration_key = :key');
        $marker->execute(['key' => $key]);
        if ($marker->fetchColumn() !== false) {
            $db->commit();
            return false;
        }
        if ((int) $db->query('SELECT COUNT(*) FROM hosts')->fetchColumn() !== 0
            || (int) $db->query('SELECT COUNT(*) FROM host_access_codes')->fetchColumn() !== 0) {
            throw new RuntimeException('Host migration targets must be empty');
        }
        $db->exec('INSERT INTO hosts (host_id, name, host, status, created_at, updated_at) SELECT reseller_id, reseller_name, host, status, created_at, updated_at FROM resellers');
        $db->exec("INSERT INTO host_access_codes (host_id, access_code, access_code_hash, status, created_at, updated_at) SELECT reseller_id, access_code, access_code_hash, 'active', created_at, updated_at FROM resellers");
        $record = $db->prepare('INSERT INTO host_schema_migrations (migration_key) VALUES (:key)');
        $record->execute(['key' => $key]);
        $db->commit();
        return true;
    } catch (Throwable $exception) {
        if ($db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
}
