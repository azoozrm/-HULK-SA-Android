<?php

declare(strict_types=1);

require_once __DIR__ . '/presence.php';
require_once __DIR__ . '/subscriber-identity.php';

const CC_SUBSCRIBER_NOTE_MAX_LENGTH = 2000;
const CC_SUBSCRIBER_NOTE_DRAFT_MAX_LENGTH = 10000;
const CC_SUBSCRIBER_NOTE_MAX_STORED_DRAFTS = 20;

function cc_subscriber_text_length(string $value): int
{
    return function_exists('mb_strlen') ? mb_strlen($value, 'UTF-8') : strlen($value);
}

function cc_subscriber_notes_attach(PDO $db, array $groups): array
{
    foreach ($groups as &$group) {
        $group['note'] = '';
        $group['note_updated_at'] = null;
    }
    unset($group);
    if ($groups === []) {
        return ['groups' => $groups, 'notes_available' => true];
    }

    $conditions = [];
    $parameters = [];
    foreach ($groups as $index => $group) {
        $suffix = (string) $index;
        $parameters['note_reseller_' . $suffix] = (int) ($group['reseller_id'] ?? 0);
        $parameters['note_host_' . $suffix] = cc_subscriber_note_host_key((string) ($group['host_snapshot'] ?? ''));
        $parameters['note_user_' . $suffix] = cc_subscriber_note_username_key((string) ($group['iptv_username'] ?? ''));
        $conditions[] = '(reseller_id = :note_reseller_' . $suffix
            . ' AND host_key = :note_host_' . $suffix
            . ' AND username_key = :note_user_' . $suffix . ')';
    }

    try {
        $statement = $db->prepare(
            'SELECT reseller_id, host_key, username_key, note_text, updated_at '
            . 'FROM cc_subscriber_notes WHERE ' . implode(' OR ', $conditions)
            . ' ORDER BY updated_at DESC LIMIT 25'
        );
        $statement->execute($parameters);
        $notes = [];
        while (($row = $statement->fetch()) !== false) {
            if (!is_array($row)) {
                continue;
            }
            $notes[(int) $row['reseller_id'] . '|' . (string) $row['host_key'] . '|' . (string) $row['username_key']] = [
                'note' => (string) $row['note_text'],
                'updated_at' => (string) $row['updated_at'],
            ];
        }
        foreach ($groups as &$group) {
            $key = (int) ($group['reseller_id'] ?? 0) . '|'
                . cc_subscriber_note_host_key((string) ($group['host_snapshot'] ?? '')) . '|'
                . cc_subscriber_note_username_key((string) ($group['iptv_username'] ?? ''));
            if (isset($notes[$key])) {
                $group['note'] = $notes[$key]['note'];
                $group['note_updated_at'] = $notes[$key]['updated_at'];
            }
        }
        unset($group);
        return ['groups' => $groups, 'notes_available' => true];
    } catch (PDOException $exception) {
        error_log('HULK Control Center subscriber notes unavailable.');
        return ['groups' => $groups, 'notes_available' => false];
    }
}

function cc_subscriber_note_save(PDO $db, array $subscription, string $noteText): array
{
    $noteText = trim($noteText);
    $length = cc_subscriber_text_length($noteText);
    if ($length < 1) {
        throw new InvalidArgumentException('اكتب نص الملاحظة أولًا.');
    }
    if ($length > CC_SUBSCRIBER_NOTE_MAX_LENGTH) {
        throw new InvalidArgumentException('الملاحظة أطول من الحد المسموح.');
    }
    $resellerId = (int) ($subscription['reseller_id'] ?? 0);
    $hostKey = cc_subscriber_note_host_key((string) ($subscription['host_snapshot'] ?? ''));
    $userKey = cc_subscriber_note_username_key((string) ($subscription['iptv_username'] ?? ''));
    $now = cc_presence_datetime(cc_presence_now());
    $driver = strtolower((string) $db->getAttribute(PDO::ATTR_DRIVER_NAME));

    $params = [
        'reseller_id' => $resellerId,
        'host_key' => $hostKey,
        'username_key' => $userKey,
        'note_text' => $noteText,
        'created_at' => $now,
        'updated_at' => $now,
    ];
    if ($driver === 'mysql') {
        $statement = $db->prepare(
            'INSERT INTO cc_subscriber_notes '
            . '(reseller_id, host_key, username_key, note_text, created_at, updated_at) '
            . 'VALUES (:reseller_id, :host_key, :username_key, :note_text, :created_at, :updated_at) '
            . 'ON DUPLICATE KEY UPDATE note_text = VALUES(note_text), updated_at = VALUES(updated_at)'
        );
        $statement->execute($params);
    } elseif ($driver === 'sqlite') {
        $statement = $db->prepare(
            'INSERT INTO cc_subscriber_notes '
            . '(reseller_id, host_key, username_key, note_text, created_at, updated_at) '
            . 'VALUES (:reseller_id, :host_key, :username_key, :note_text, :created_at, :updated_at) '
            . 'ON CONFLICT(reseller_id, host_key, username_key) '
            . 'DO UPDATE SET note_text = excluded.note_text, updated_at = excluded.updated_at'
        );
        $statement->execute($params);
    } else {
        $update = $db->prepare(
            'UPDATE cc_subscriber_notes SET note_text = :note_text, updated_at = :updated_at '
            . 'WHERE reseller_id = :reseller_id AND host_key = :host_key AND username_key = :username_key'
        );
        $update->execute([
            'note_text' => $noteText,
            'updated_at' => $now,
            'reseller_id' => $resellerId,
            'host_key' => $hostKey,
            'username_key' => $userKey,
        ]);
        if ($update->rowCount() < 1) {
            $insert = $db->prepare(
                'INSERT INTO cc_subscriber_notes '
                . '(reseller_id, host_key, username_key, note_text, created_at, updated_at) '
                . 'VALUES (:reseller_id, :host_key, :username_key, :note_text, :created_at, :updated_at)'
            );
            $insert->execute($params);
        }
    }
    return ['note' => $noteText, 'updated_at' => $now];
}

function cc_subscriber_note_store_draft(array $subscription, string $noteText): void
{
    cc_start_admin_session();
    $key = cc_subscriber_identity_key(
        (int) ($subscription['reseller_id'] ?? 0),
        (string) ($subscription['host_snapshot'] ?? ''),
        (string) ($subscription['iptv_username'] ?? '')
    );
    if (!isset($_SESSION['cc_subscriber_note_drafts']) || !is_array($_SESSION['cc_subscriber_note_drafts'])) {
        $_SESSION['cc_subscriber_note_drafts'] = [];
    }
    $length = cc_subscriber_text_length($noteText);
    if ($length > CC_SUBSCRIBER_NOTE_DRAFT_MAX_LENGTH) {
        $noteText = function_exists('mb_substr')
            ? mb_substr($noteText, 0, CC_SUBSCRIBER_NOTE_DRAFT_MAX_LENGTH, 'UTF-8')
            : substr($noteText, 0, CC_SUBSCRIBER_NOTE_DRAFT_MAX_LENGTH);
    }
    $_SESSION['cc_subscriber_note_drafts'][$key] = $noteText;
    while (count($_SESSION['cc_subscriber_note_drafts']) > CC_SUBSCRIBER_NOTE_MAX_STORED_DRAFTS) {
        array_shift($_SESSION['cc_subscriber_note_drafts']);
    }
}

function cc_subscriber_note_attach_drafts(array $pageData): array
{
    if (empty($pageData['groups']) || !is_array($pageData['groups'])) {
        return $pageData;
    }
    $drafts = $_SESSION['cc_subscriber_note_drafts'] ?? [];
    if (!is_array($drafts) || $drafts === []) {
        return $pageData;
    }
    foreach ($pageData['groups'] as &$group) {
        $key = cc_subscriber_identity_key(
            (int) ($group['reseller_id'] ?? 0),
            (string) ($group['host_snapshot'] ?? ''),
            (string) ($group['iptv_username'] ?? '')
        );
        if (isset($drafts[$key]) && is_string($drafts[$key])) {
            $group['note_draft'] = $drafts[$key];
            unset($drafts[$key]);
        }
    }
    unset($group);
    $_SESSION['cc_subscriber_note_drafts'] = $drafts;
    return $pageData;
}
