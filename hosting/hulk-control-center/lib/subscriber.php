<?php

declare(strict_types=1);

require_once __DIR__ . '/presence.php';
require_once __DIR__ . '/subscriber-identity.php';
require_once __DIR__ . '/subscriber-notes.php';
require_once __DIR__ . '/subscriber-result.php';
require_once __DIR__ . '/subscriber-session.php';

function cc_subscriber_resolve_online_subscription(PDO $db, array $input, array $config): array
{
    $resellerId = filter_var($input['reseller_id'] ?? null, FILTER_VALIDATE_INT);
    if ($resellerId === false || $resellerId < 1) {
        throw new InvalidArgumentException('الاشتراك المحدد غير صالح.');
    }
    $hostHash = strtoupper(trim((string) ($input['host_hash'] ?? '')));
    $userHash = strtoupper(trim((string) ($input['user_hash'] ?? '')));
    if (
        $hostHash === ''
        || $userHash === ''
        || strlen($hostHash) > 4096
        || strlen($userHash) > 510
        || strlen($hostHash) % 2 !== 0
        || strlen($userHash) % 2 !== 0
        || preg_match('/^[A-F0-9]+$/D', $hostHash) !== 1
        || preg_match('/^[A-F0-9]+$/D', $userHash) !== 1
    ) {
        throw new InvalidArgumentException('الاشتراك المحدد غير صالح.');
    }
    $ttlSeconds = (int) ($config['online_ttl_seconds'] ?? 0);
    if ($ttlSeconds < 1) {
        throw new RuntimeException('Presence online TTL is invalid.');
    }
    $cutoff = cc_presence_datetime(cc_presence_now()->modify('-' . $ttlSeconds . ' seconds'));
    $statement = $db->prepare(
        'SELECT s.id, s.reseller_id, s.host_snapshot, s.iptv_username, s.iptv_password, '
        . 's.access_code_snapshot, s.last_seen_at '
        . 'FROM cc_app_sessions s '
        . 'WHERE s.reseller_id = :reseller_id AND s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff '
        . 'AND UPPER(HEX(s.host_snapshot)) = :host_hash '
        . 'AND UPPER(HEX(s.iptv_username)) = :user_hash '
        . 'ORDER BY s.last_seen_at DESC, s.id DESC LIMIT 1'
    );
    $statement->execute([
        'reseller_id' => (int) $resellerId,
        'online_cutoff' => $cutoff,
        'host_hash' => $hostHash,
        'user_hash' => $userHash,
    ]);
    $subscription = $statement->fetch();
    if (!is_array($subscription)) {
        throw new InvalidArgumentException('الاشتراك غير متاح حاليًا. أعد تحميل الصفحة ثم حاول مرة أخرى.');
    }
    return $subscription;
}

function cc_subscriber_handle_post(string $module, array $admin): string
{
    if ($module !== 'live-users') {
        throw new InvalidArgumentException('هذه الوحدة لا تقبل هذا الإجراء.');
    }
    $action = cc_post_action();
    $controlDb = cc_db('control');
    $config = cc_presence_config();

    if ($action === 'check_subscriber') {
        require_once __DIR__ . '/subscriber-check.php';
        $subscription = cc_subscriber_resolve_online_subscription($controlDb, $_POST, $config);
        $result = cc_subscriber_check_provider(
            (string) $subscription['host_snapshot'],
            (string) $subscription['iptv_username'],
            (string) $subscription['iptv_password']
        );
        $result['checked_at'] = cc_presence_datetime(cc_presence_now());
        cc_subscriber_check_store_result($subscription, $result);
        return 'تم تنفيذ فحص المشترك.';
    }

    if ($action === 'save_subscriber_note') {
        $subscription = cc_subscriber_resolve_online_subscription($controlDb, $_POST, $config);
        $note = is_string($_POST['note'] ?? null) ? trim($_POST['note']) : '';
        $controlDb->beginTransaction();
        try {
            $saved = cc_subscriber_note_save($controlDb, $subscription, $note);
            ops_audit($controlDb, (int) $admin['id'], 'SUBSCRIBER_NOTE_SAVED', [
                'reseller_id' => (int) $subscription['reseller_id'],
                'note_length' => cc_subscriber_text_length((string) $saved['note']),
            ]);
            $controlDb->commit();
        } catch (Throwable $exception) {
            if ($controlDb->inTransaction()) {
                $controlDb->rollBack();
            }
            cc_subscriber_note_store_draft($subscription, $note);
            throw $exception;
        }
        return 'تم حفظ ملاحظة المشترك.';
    }

    throw new InvalidArgumentException('الإجراء غير معروف.');
}

function cc_subscriber_attach_session_state(array $pageData): array
{
    $pageData = cc_subscriber_check_attach_results($pageData);
    return cc_subscriber_note_attach_drafts($pageData);
}
