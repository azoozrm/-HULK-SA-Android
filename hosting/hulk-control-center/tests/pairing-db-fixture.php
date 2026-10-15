<?php

declare(strict_types=1);

/**
 * Local behavioral test fixture for the pairing backend.
 *
 * The lab PHP build has no PDO extension and there is no local MySQL/MariaDB
 * server, so this file provides  a narrow in-memory PDO test double covering
 * exactly the statement shapes used by pairing.php/pairing-http.php. It is test
 * tooling only: it does NOT qualify MySQL locking, isolation or concurrency
 * (those remain BLOCKED and must run on a real disposable database).
 *
 * Loaded via auto_prepend_file for the repository suites; its
 * PDO::getAvailableDrivers() returns [] so existing suites self-skip their own
 * SQLite-backed checks exactly as designed.
 */

if (!function_exists('ctype_xdigit')) {
    function ctype_xdigit(string $text): bool
    {
        return $text !== '' && preg_match('/^[0-9A-Fa-f]+$/', $text) === 1;
    }
}
if (!function_exists('ctype_digit')) {
    function ctype_digit(string $text): bool
    {
        return $text !== '' && preg_match('/^[0-9]+$/', $text) === 1;
    }
}

if (class_exists('PDO', false)) {
    // Native PDO is present (e.g. CI): never shadow it. Every mock declaration
    // below is conditional so nothing is early-bound at compile time; the default
    // suite stays fully native.
    return;
}

if (!class_exists('PDO', false)) {
    define('CC_PAIRING_TEST_FIXTURE_ACTIVE', true);

final class CcPairingTestStore
{
    public static ?CcPairingTestStore $instance = null;

    /** @var array<int, array<string, mixed>> */
    public array $requests = [];
    /** @var array<int, array<string, mixed>> */
    public array $resellers = [];
    /** @var array<string, array<string, mixed>> */
    public array $rateLimits = [];
    public bool $insertFailure = false;
    public bool $deleteFailure = false;
    public int $nextPairingId = 1;
    public int $lastInsertId = 0;

    public static function instance(): self
    {
        return self::$instance ??= new self();
    }

    public function reset(): void
    {
        $this->requests = [];
        $this->resellers = [];
        $this->rateLimits = [];
        $this->insertFailure = false;
        $this->deleteFailure = false;
        $this->nextPairingId = 1;
    }

    public function findPairingId(int $pairingId): ?int
    {
        foreach ($this->requests as $index => $row) {
            if ((int) $row['pairing_id'] === $pairingId) {
                return $index;
            }
        }
        return null;
    }

    public function findRequestId(string $requestId): ?int
    {
        foreach ($this->requests as $index => $row) {
            if ((string) $row['request_id'] === $requestId) {
                return $index;
            }
        }
        return null;
    }
}

class PDOException extends RuntimeException
{
    /** @var array<int|string, mixed> */
    public array $errorInfo = [];

    public function __construct(string $message = '', int|string $code = 0, array $errorInfo = [])
    {
        parent::__construct($message, is_int($code) ? $code : 0);
        $this->errorInfo = $errorInfo;
    }
}

class PDOStatement
{
}

class PDO
{
    public const ATTR_ERRMODE = 3;
    public const ATTR_TIMEOUT = 2;
    public const ATTR_SERVER_VERSION = 4;
    public const ATTR_PERSISTENT = 12;
    public const ATTR_DRIVER_NAME = 16;
    public const ATTR_DEFAULT_FETCH_MODE = 19;
    public const ATTR_EMULATE_PREPARES = 20;
    public const ERRMODE_EXCEPTION = 2;
    public const FETCH_ASSOC = 2;
    public const FETCH_COLUMN = 7;

    private CcPairingTestStore $store;
    private int $transactionDepth = 0;

    public function __construct(string $dsn = '', ?string $username = null, ?string $password = null, ?array $options = null)
    {
        $this->store = CcPairingTestStore::instance();
    }

    public static function getAvailableDrivers(): array
    {
        return [];
    }

    public function store(): CcPairingTestStore
    {
        return $this->store;
    }

    public function setAttribute(int $attribute, mixed $value): bool
    {
        return true;
    }

    public function getAttribute(int $attribute): mixed
    {
        return match ($attribute) {
            self::ATTR_DRIVER_NAME => 'mysql',
            self::ATTR_SERVER_VERSION => '10.11.14-MariaDB-fixture',
            default => null,
        };
    }

    public function prepare(string $query, array $options = []): PDOStatement|false
    {
        return new CcPairingTestStatement($this->store, $query);
    }

    public function query(string $query): PDOStatement|false
    {
        $statement = new CcPairingTestStatement($this->store, $query);
        $statement->execute([]);
        return $statement;
    }

    public function exec(string $statement): int
    {
        return 0;
    }

    public function beginTransaction(): bool
    {
        $this->transactionDepth++;
        return true;
    }

    public function commit(): bool
    {
        $this->transactionDepth = max(0, $this->transactionDepth - 1);
        return true;
    }

    public function rollBack(): bool
    {
        $this->transactionDepth = max(0, $this->transactionDepth - 1);
        return true;
    }

    public function inTransaction(): bool
    {
        return $this->transactionDepth > 0;
    }
}

final class CcPairingTestStatement extends PDOStatement
{
    private CcPairingTestStore $store;
    private string $sql;
    /** @var array<string|int, mixed> */
    private array $params = [];
    /** @var array<int, array<string, mixed>> */
    private array $rows = [];
    private int $affected = 0;

    public function __construct(CcPairingTestStore $store, string $sql)
    {
        $this->store = $store;
        $this->sql = (string) preg_replace('/\s+/', ' ', trim($sql));
    }

    public function execute(array $params = []): bool
    {
        $this->params = $params;
        $this->rows = [];
        $this->affected = 0;
        $sql = (string) preg_replace('/ FOR UPDATE$/', '', $this->sql);

        if (str_starts_with($sql, 'SET SESSION') || str_starts_with($sql, 'SELECT GET_LOCK') || str_starts_with($sql, 'SELECT RELEASE_LOCK')) {
            $this->rows = [['locked' => 1]];
            return true;
        }
        if (preg_match('/^SELECT VERSION\(\)$/', $sql) === 1) {
            $this->rows = [['version' => '10.11.14-MariaDB-fixture']];
            return true;
        }
        if (str_starts_with($sql, 'DELETE FROM cc_api_rate_limits WHERE scope = :scope AND window_started_at <= :cutoff')) {
            $scope = (string) ($this->params['scope'] ?? '');
            $cutoff = (string) ($this->params['cutoff'] ?? '');
            $limit = 200;
            if (preg_match('/LIMIT (\d+)$/', $sql, $limitMatch) === 1) {
                $limit = (int) $limitMatch[1];
            }
            $deleted = 0;
            foreach ($this->store->rateLimits as $key => $row) {
                if ($deleted >= $limit) {
                    break;
                }
                if ((string) $row['scope'] === $scope && (string) $row['window_started_at'] <= $cutoff) {
                    unset($this->store->rateLimits[$key]);
                    $deleted++;
                }
            }
            $this->affected = $deleted;
            return true;
        }

        if (preg_match('/^SELECT COUNT\(\*\) FROM cc_pairing_requests$/', $sql) === 1) {
            $this->rows = [['count' => count($this->store->requests)]];
            return true;
        }
        if (preg_match('/^SELECT SUM\(state IN \(\'PENDING\', \'APPROVED\'\)\) AS handoff_count, COUNT\(\*\) AS total_count FROM cc_pairing_requests$/', $sql) === 1) {
            $handoff = 0;
            foreach ($this->store->requests as $row) {
                if (in_array((string) $row['state'], ['PENDING', 'APPROVED'], true)) {
                    $handoff++;
                }
            }
            $this->rows = [['handoff_count' => $handoff, 'total_count' => count($this->store->requests)]];
            return true;
        }
        if (preg_match("/^SELECT COUNT\(\*\) FROM cc_pairing_requests WHERE state IN \('PENDING', 'APPROVED'\)$/", $sql) === 1) {
            $this->rows = [['count' => $this->countNonterminal()]];
            return true;
        }
        if (preg_match('/^SELECT lock_id FROM cc_pairing_admission WHERE lock_id = 1$/', $sql) === 1) {
            $this->rows = [['lock_id' => 1]];
            return true;
        }

        if (str_starts_with($sql, 'SELECT * FROM cc_pairing_requests ')) {
            return $this->selectRequests($sql);
        }
        if (str_starts_with($sql, 'SELECT pairing_id FROM cc_pairing_requests ')) {
            return $this->selectPairingIds($sql);
        }
        if (str_starts_with($sql, 'INSERT INTO cc_pairing_requests ')) {
            return $this->insertRequest($sql);
        }
        if (str_starts_with($sql, 'UPDATE cc_pairing_requests SET ')) {
            return $this->updateRequests($sql);
        }
        if (str_starts_with($sql, 'DELETE FROM cc_pairing_requests ')) {
            return $this->deleteRequests($sql);
        }

        if (str_starts_with($sql, 'SELECT reseller_id, access_code, access_code_hash, host, status FROM resellers ')) {
            $hash = (string) ($this->params['access_code_hash'] ?? '');
            foreach ($this->store->resellers as $row) {
                if ((string) $row['access_code_hash'] === $hash) {
                    $this->rows = [$row];
                    break;
                }
            }
            return true;
        }
        if (str_starts_with($sql, 'SELECT access_code, access_code_hash, host, status FROM resellers ')) {
            $resellerId = (int) ($this->params['reseller_id'] ?? 0);
            foreach ($this->store->resellers as $row) {
                if ((int) $row['reseller_id'] === $resellerId) {
                    $this->rows = [$row];
                    break;
                }
            }
            return true;
        }

        if (str_starts_with($sql, 'SELECT window_started_at FROM cc_api_rate_limits ')) {
            $key = (string) ($this->params['scope'] ?? '') . "\x00" . (string) ($this->params['client_hash'] ?? '');
            if (isset($this->store->rateLimits[$key])) {
                $this->rows = [['window_started_at' => $this->store->rateLimits[$key]['window_started_at']]];
            }
            return true;
        }
        if (str_starts_with($sql, 'SELECT window_started_at, attempts FROM cc_api_rate_limits ')) {
            $key = (string) ($this->params['scope'] ?? '') . "\x00" . (string) ($this->params['client_hash'] ?? '');
            if (isset($this->store->rateLimits[$key])) {
                $this->rows = [$this->store->rateLimits[$key]];
            }
            return true;
        }
        if (preg_match('/^SELECT LAST_INSERT_ID\(\)$/', $sql) === 1) {
            $this->rows = [['last_insert_id' => $this->store->lastInsertId]];
            return true;
        }
        if (str_starts_with($sql, 'INSERT INTO cc_api_rate_limits ') && str_contains($sql, 'ON DUPLICATE KEY UPDATE')) {
            $scope = (string) ($this->params['scope'] ?? '');
            $hash = (string) ($this->params['client_hash'] ?? '');
            $windowNow = (string) ($this->params['window_now'] ?? '');
            $cutoff = (string) ($this->params['cutoff_a'] ?? '');
            $key = $scope . "\x00" . $hash;
            if (isset($this->store->rateLimits[$key])) {
                $row = $this->store->rateLimits[$key];
                $expired = (string) $row['window_started_at'] <= $cutoff;
                $attempts = $expired ? 1 : (int) $row['attempts'] + 1;
                $this->store->rateLimits[$key]['attempts'] = $attempts;
                if ($expired) {
                    $this->store->rateLimits[$key]['window_started_at'] = $windowNow;
                }
                $this->store->lastInsertId = $attempts;
                $this->affected = 2;
                return true;
            }
            $this->store->rateLimits[$key] = [
                'scope' => $scope,
                'client_hash' => $hash,
                'window_started_at' => $windowNow,
                'attempts' => 1,
            ];
            $this->store->lastInsertId = 1;
            $this->affected = 1;
            return true;
        }
        if (str_starts_with($sql, 'INSERT INTO cc_api_rate_limits ')) {
            $key = (string) ($this->params['scope'] ?? '') . "\x00" . (string) ($this->params['client_hash'] ?? '');
            $this->store->rateLimits[$key] = [
                'scope' => (string) ($this->params['scope'] ?? ''),
                'client_hash' => (string) ($this->params['client_hash'] ?? ''),
                'window_started_at' => (string) ($this->params['window_started_at'] ?? ''),
                'attempts' => 1,
            ];
            $this->affected = 1;
            return true;
        }
        if (str_starts_with($sql, 'UPDATE cc_api_rate_limits SET ')) {
            $key = (string) ($this->params['scope'] ?? '') . "\x00" . (string) ($this->params['client_hash'] ?? '');
            if (isset($this->store->rateLimits[$key])) {
                $this->store->rateLimits[$key]['window_started_at'] = (string) ($this->params['window_started_at'] ?? '');
                $this->store->rateLimits[$key]['attempts'] = (int) ($this->params['attempts'] ?? 0);
                $this->affected = 1;
            }
            return true;
        }

        throw new PDOException('Unhandled fixture statement: ' . $sql);
    }

    public function fetch(): mixed
    {
        return $this->rows === [] ? false : array_shift($this->rows);
    }

    public function fetchAll(int $mode = 0): array
    {
        $rows = $this->rows;
        $this->rows = [];
        if ($mode === PDO::FETCH_COLUMN) {
            return array_map(static fn (array $row) => array_values($row)[0], $rows);
        }
        return $rows;
    }

    public function fetchColumn(): mixed
    {
        $row = $this->fetch();
        if (!is_array($row)) {
            return false;
        }
        return array_values($row)[0] ?? false;
    }

    public function rowCount(): int
    {
        return $this->affected;
    }

    private function countNonterminal(): int
    {
        $count = 0;
        foreach ($this->store->requests as $row) {
            if (in_array((string) $row['state'], ['PENDING', 'APPROVED'], true)) {
                $count++;
            }
        }
        return $count;
    }

    private function selectRequests(string $sql): bool
    {
        $row = null;
        if (str_contains($sql, 'WHERE installation_id = :installation_id AND client_request_id = :client_request_id')) {
            foreach ($this->store->requests as $candidate) {
                if (
                    (string) $candidate['installation_id'] === (string) ($this->params['installation_id'] ?? '')
                    && (string) $candidate['client_request_id'] === (string) ($this->params['client_request_id'] ?? '')
                ) {
                    $row = $candidate;
                    break;
                }
            }
        } elseif (str_contains($sql, 'WHERE request_id = :request_id')) {
            $index = $this->store->findRequestId((string) ($this->params['request_id'] ?? ''));
            $row = $index === null ? null : $this->store->requests[$index];
        } elseif (str_contains($sql, 'WHERE user_code = :user_code')) {
            foreach ($this->store->requests as $candidate) {
                if ((string) $candidate['user_code'] === (string) ($this->params['user_code'] ?? '')) {
                    $row = $candidate;
                    break;
                }
            }
        } else {
            throw new PDOException('Unhandled request select: ' . $sql);
        }
        $this->rows = $row === null ? [] : [$row];
        return true;
    }

    private function selectPairingIds(string $sql): bool
    {
        $now = (string) ($this->params['now'] ?? '');
        $cutoff = (string) ($this->params['cutoff'] ?? '');
        $matches = [];
        foreach ($this->store->requests as $row) {
            $state = (string) $row['state'];
            if ($state === 'PENDING' || $state === 'APPROVED') {
                if ($now !== '' && str_contains($sql, 'expires_at <= :now') && (string) $row['expires_at'] > $now) {
                    continue;
                }
            } elseif (str_contains($sql, 'terminal_at IS NOT NULL AND terminal_at <= :cutoff')) {
                if (empty($row['terminal_at']) || (string) $row['terminal_at'] > $cutoff) {
                    continue;
                }
            } else {
                continue;
            }
            if (str_contains($sql, 'AND expires_at <= :now') && (string) $row['expires_at'] > $now) {
                continue;
            }
            $matches[] = $row;
        }
        usort($matches, static fn (array $a, array $b) => (int) $a['pairing_id'] <=> (int) $b['pairing_id']);
        $limit = 0;
        if (preg_match('/LIMIT (\d+)$/', $sql, $m) === 1) {
            $limit = (int) $m[1];
        }
        $ids = array_map(static fn (array $row) => ['pairing_id' => (int) $row['pairing_id']], $matches);
        if ($limit > 0) {
            $ids = array_slice($ids, 0, $limit);
        }
        $this->rows = $ids;
        return true;
    }

    private function insertRequest(string $sql): bool
    {
        if ($this->store->insertFailure) {
            throw new PDOException('Duplicate entry', 23000, [23000, 1062]);
        }
        if (preg_match('/^INSERT INTO cc_pairing_requests \(([^)]+)\) VALUES \(([^)]+)\)$/', $sql, $matches) !== 1) {
            throw new PDOException('Unhandled insert: ' . $sql);
        }
        $columns = array_map('trim', explode(',', $matches[1]));
        $values = array_map('trim', explode(',', $matches[2]));
        if (count($columns) !== count($values)) {
            throw new PDOException('Insert arity mismatch.');
        }
        $row = [];
        foreach ($columns as $index => $column) {
            $value = $values[$index];
            if (str_starts_with($value, ':')) {
                $row[$column] = $this->params[substr($value, 1)] ?? null;
            } elseif (str_starts_with($value, "'") && str_ends_with($value, "'")) {
                $row[$column] = substr($value, 1, -1);
            } else {
                $row[$column] = $value;
            }
        }
        foreach (['envelope_version', 'envelope_key_id', 'envelope_blob', 'envelope_set_at', 'reseller_id', 'host_fingerprint', 'approved_at', 'acked_at', 'terminal_at', 'terminal_reason', 'last_delivered_at'] as $nullable) {
            $row[$nullable] ??= null;
        }
        $row['delivery_count'] ??= 0;
        $row['pairing_id'] = $this->store->nextPairingId++;
        $this->store->requests[] = $row;
        $this->affected = 1;
        return true;
    }

    private function updateRequests(string $sql): bool
    {
        if (preg_match('/^UPDATE cc_pairing_requests SET (.+) WHERE (.+)$/', $sql, $matches) !== 1) {
            throw new PDOException('Unhandled update: ' . $sql);
        }
        $setPairs = $this->parseSetPairs($matches[1]);
        $where = $matches[2];

        $targets = [];
        if (preg_match('/pairing_id IN \(([?,\s]+)\)/', $where, $idMatches) === 1) {
            $placeholders = substr_count($idMatches[1], '?');
            $ids = array_slice(array_values($this->params), 1, $placeholders);
            if ($this->store->deleteFailure) {
                throw new PDOException('Fixture forced failure');
            }
            foreach ($ids as $id) {
                $index = $this->store->findPairingId((int) $id);
                if ($index !== null && in_array((string) $this->store->requests[$index]['state'], ['PENDING', 'APPROVED'], true)) {
                    $targets[] = $index;
                }
            }
            $this->applySet($targets, $setPairs, ['terminal_at' => $this->params[0] ?? null]);
            foreach ($targets as $index) {
                $this->store->requests[$index]['state'] = str_contains($sql, 'DISABLED_PURGE') ? 'CANCELLED' : 'EXPIRED';
                $this->store->requests[$index]['terminal_reason'] = str_contains($sql, 'DISABLED_PURGE') ? 'DISABLED_PURGE' : 'EXPIRED';
            }
            $this->affected = count($targets);
            return true;
        }

        foreach ($this->store->requests as $index => $row) {
            $state = (string) $row['state'];
            if (str_contains($where, 'pairing_id = :pairing_id') && (int) $row['pairing_id'] !== (int) ($this->params['pairing_id'] ?? 0)) {
                continue;
            }
            if (str_contains($where, 'request_id = :request_id') && (string) $row['request_id'] !== (string) ($this->params['request_id'] ?? '')) {
                continue;
            }
            if (str_contains($where, "state = 'PENDING'") && $state !== 'PENDING') {
                continue;
            }
            if (str_contains($where, "state = 'APPROVED'") && $state !== 'APPROVED') {
                continue;
            }
            if (str_contains($where, "state IN ('PENDING', 'APPROVED')") && !in_array($state, ['PENDING', 'APPROVED'], true)) {
                continue;
            }
            $targets[] = $index;
        }
        $this->applySet($targets, $setPairs, $this->params);
        $this->affected = count($targets);
        return true;
    }

    /**
     * @param array<int, string> $setPairs
     * @return array<int, string>
     */
    private function parseSetPairs(string $setSql): array
    {
        return array_map('trim', explode(',', $setSql));
    }

    /**
     * @param array<int, int> $targets
     * @param array<int, string> $setPairs
     * @param array<string|int, mixed> $params
     */
    private function applySet(array $targets, array $setPairs, array $params): void
    {
        foreach ($targets as $index) {
            foreach ($setPairs as $pair) {
                if (preg_match('/^([a-z_]+) = NULL$/', $pair, $m) === 1) {
                    $this->store->requests[$index][$m[1]] = null;
                } elseif (preg_match('/^([a-z_]+) = :([a-z_]+)$/', $pair, $m) === 1) {
                    $this->store->requests[$index][$m[1]] = $params[$m[2]] ?? null;
                } elseif (preg_match('/^([a-z_]+) = ([a-z_]+) \+ 1$/', $pair, $m) === 1) {
                    $this->store->requests[$index][$m[1]] = (int) $this->store->requests[$index][$m[1]] + 1;
                } elseif (preg_match("/^([a-z_]+) = '([A-Z_]+)'$/", $pair, $m) === 1) {
                    $this->store->requests[$index][$m[1]] = $m[2];
                } elseif (preg_match('/^([a-z_]+) = \?$/', $pair, $m) === 1) {
                    $this->store->requests[$index][$m[1]] = $params[0] ?? null;
                } else {
                    throw new PDOException('Unhandled SET pair: ' . $pair);
                }
            }
        }
    }

    private function deleteRequests(string $sql): bool
    {
        if ($this->store->deleteFailure) {
            throw new PDOException('Fixture forced failure');
        }
        if (preg_match('/pairing_id IN \(([?,\s]+)\)/', $sql, $matches) === 1) {
            $placeholders = substr_count($matches[1], '?');
            $ids = array_slice(array_values($this->params), 0, $placeholders);
            $before = count($this->store->requests);
            $this->store->requests = array_values(array_filter(
                $this->store->requests,
                static fn (array $row) => !in_array((int) $row['pairing_id'], array_map('intval', $ids), true)
            ));
            $this->affected = $before - count($this->store->requests);
            return true;
        }
        throw new PDOException('Unhandled delete: ' . $sql);
    }
}

}
