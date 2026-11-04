<?php

declare(strict_types=1);

/** Retired entry points fail explicitly; hosts/codes have one writable authority */
function hulk_admin_reseller_id(mixed $value): int { return hulk_host_id($value); }
function hulk_admin_create_reseller(PDO $db, array $input): array { throw new RuntimeException('استخدم اضافة هوست في لوحة التحكم'); }
function hulk_admin_set_status(PDO $db, mixed $id, mixed $status): void { throw new RuntimeException('استخدم اعدادات الهوست في لوحة التحكم'); }
function hulk_admin_update_host(PDO $db, mixed $id, mixed $host): string { throw new RuntimeException('استخدم تعديل الهوست في لوحة التحكم'); }
function hulk_admin_set_code(PDO $db, mixed $id, mixed $code): string { throw new RuntimeException('استخدم ادارة اكواد الهوست في لوحة التحكم'); }
function hulk_admin_rotate_code(PDO $db, mixed $id): string { throw new RuntimeException('استخدم ادارة اكواد الهوست في لوحة التحكم'); }
function hulk_admin_write_code(PDO $db, int $id, string $code): void { throw new RuntimeException('استخدم ادارة اكواد الهوست في لوحة التحكم'); }
function hulk_admin_reset_password(PDO $db, mixed $id, mixed $password, mixed $confirmation): void { throw new RuntimeException('ادارة حسابات الموزعين متوقفة'); }
