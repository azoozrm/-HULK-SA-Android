package sa.hulksa.player.data

import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import java.io.File
import java.net.URI
import java.security.MessageDigest

internal const val DOWNLOAD_SCHEMA_VERSION = 2
internal const val DOWNLOAD_MIGRATION_STATE_COMPLETE = "complete"

data class DownloadOwner(
    val accountId: String,
    val profileId: String,
) {
    fun normalizedOrNull(): DownloadOwner? {
        val account = accountId.normalizedOwnershipComponent() ?: return null
        val profile = profileId.normalizedOwnershipComponent() ?: return null
        return DownloadOwner(account, profile)
    }
}

internal data class DownloadOwnershipKey(
    val accountId: String,
    val profileId: String,
    val historyKey: String,
)

internal fun OfflineDownload.owner(): DownloadOwner = DownloadOwner(accountId, profileId)

internal fun normalizedDownloadAccountId(accountId: String?): String? =
    accountId.normalizedOwnershipComponent()

internal fun normalizedDownloadHistoryKey(historyKey: String?): String? =
    historyKey.normalizedHistoryKey()

internal fun OfflineDownload.ownershipKey(): DownloadOwnershipKey = DownloadOwnershipKey(
    accountId = accountId,
    profileId = profileId,
    historyKey = historyKey,
)

internal fun OfflineDownload.isOwnedBy(owner: DownloadOwner): Boolean {
    val normalized = owner.normalizedOrNull() ?: return false
    return accountId == normalized.accountId && profileId == normalized.profileId
}

internal fun visibleDownloads(
    records: List<OfflineDownload>,
    owner: DownloadOwner?,
): List<OfflineDownload> {
    val normalized = owner?.normalizedOrNull() ?: return emptyList()
    return records.filter { it.isOwnedBy(normalized) }
}

internal fun suspendDownloadForAccountLogout(
    item: OfflineDownload,
    accountId: String,
): OfflineDownload {
    val normalizedAccountId = accountId.normalizedOwnershipComponent() ?: return item
    if (item.accountId != normalizedAccountId) return item
    val wasTransferActive = item.status in OWNER_SUSPENDABLE_STATUSES
    return item.copy(
        sourceCandidates = emptyList(),
        status = if (wasTransferActive) OfflineStatus.PAUSED else item.status,
        bytesPerSecond = 0L,
        etaSeconds = if (item.status == OfflineStatus.COMPLETED) item.etaSeconds else -1L,
        errorMessage = if (wasTransferActive || item.resumeOnOwnerAuthentication) {
            OWNER_AUTHENTICATION_REQUIRED_MESSAGE
        } else {
            item.errorMessage
        },
        resumeOnOwnerAuthentication = wasTransferActive || item.resumeOnOwnerAuthentication,
    )
}

internal fun suspendInactiveAccountDownloads(
    records: List<OfflineDownload>,
    activeAccountId: String,
): List<OfflineDownload> {
    val normalizedActive = normalizedDownloadAccountId(activeAccountId) ?: return records
    return records.map { item ->
        if (item.accountId == normalizedActive) {
            item
        } else {
            suspendDownloadForAccountLogout(item, item.accountId)
        }
    }
}

internal fun prepareDownloadForAuthenticatedOwner(
    item: OfflineDownload,
    owner: DownloadOwner,
    refreshedSources: List<String>,
    nowEpochMs: Long,
): OfflineDownload? {
    val normalizedOwner = owner.normalizedOrNull() ?: return null
    if (!item.isOwnedBy(normalizedOwner)) return null
    if (item.status == OfflineStatus.COMPLETED) {
        return item.copy(sourceCandidates = emptyList(), resumeOnOwnerAuthentication = false)
    }
    val sources = refreshedSources
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    if (sources.isEmpty()) return null
    val resumedStatus = when {
        !item.resumeOnOwnerAuthentication -> item.status
        item.scheduledAtEpochMs > nowEpochMs -> OfflineStatus.WAITING_SCHEDULE
        else -> OfflineStatus.QUEUED
    }
    return item.copy(
        sourceCandidates = sources,
        status = resumedStatus,
        bytesPerSecond = 0L,
        etaSeconds = -1L,
        errorMessage = if (item.resumeOnOwnerAuthentication) null else item.errorMessage,
        resumeOnOwnerAuthentication = false,
    )
}

internal fun recoverDownloadAfterProcessDeath(
    item: OfflineDownload,
    nowEpochMs: Long,
): OfflineDownload {
    if (item.status == OfflineStatus.COMPLETED) return item.copy(sourceCandidates = emptyList())
    if (item.status !in OWNER_SUSPENDABLE_STATUSES) {
        return item.copy(sourceCandidates = emptyList(), bytesPerSecond = 0L)
    }
    return item.copy(
        sourceCandidates = emptyList(),
        status = if (
            item.status == OfflineStatus.WAITING_SCHEDULE &&
            item.scheduledAtEpochMs > nowEpochMs
        ) {
            OfflineStatus.WAITING_SCHEDULE
        } else {
            OfflineStatus.QUEUED
        },
        bytesPerSecond = 0L,
        etaSeconds = -1L,
        errorMessage = "سيتم التحقق من الحساب المالك ثم استئناف التحميل من اخر نقطة.",
    )
}

internal data class ProfileDownloadDeletion(
    val retained: List<OfflineDownload>,
    val removed: List<OfflineDownload>,
)

internal fun partitionDownloadsForProfileDeletion(
    records: List<OfflineDownload>,
    owner: DownloadOwner,
): ProfileDownloadDeletion {
    val normalized = owner.normalizedOrNull()
        ?: return ProfileDownloadDeletion(retained = records, removed = emptyList())
    val removed = records.filter { it.isOwnedBy(normalized) }
    return ProfileDownloadDeletion(
        retained = records.filterNot { it.isOwnedBy(normalized) },
        removed = removed,
    )
}

internal fun normalizeOwnedDownloadQueues(
    records: List<OfflineDownload>,
    owners: Set<DownloadOwner>? = null,
): List<OfflineDownload> {
    val queuePositions = records
        .asSequence()
        .filter { item -> owners == null || item.owner() in owners }
        .toList()
        .groupBy { it.owner() }
        .values
        .flatMap { ownedRecords ->
            ownedRecords.sortedWith(
                compareByDescending<OfflineDownload> { it.priority }
                    .thenBy { it.queuePosition }
                    .thenBy { it.createdAtEpochMs },
            ).mapIndexed { index, item -> item.downloadId to index }
        }
        .toMap()
    return records.map { item ->
        queuePositions[item.downloadId]
            ?.let { queuePosition -> item.copy(queuePosition = queuePosition) }
            ?: item
    }
}

internal enum class DownloadWorkerOwnershipDecision {
    ALLOW,
    RECORD_OWNER_MISMATCH,
    SESSION_OWNER_MISMATCH,
}

internal fun decideDownloadWorkerOwnership(
    record: OfflineDownload?,
    requestedOwner: DownloadOwner?,
    activeAccountId: String?,
): DownloadWorkerOwnershipDecision {
    val owner = requestedOwner?.normalizedOrNull()
        ?: return DownloadWorkerOwnershipDecision.RECORD_OWNER_MISMATCH
    if (record == null || !record.isOwnedBy(owner)) {
        return DownloadWorkerOwnershipDecision.RECORD_OWNER_MISMATCH
    }
    val active = activeAccountId.normalizedOwnershipComponent()
    return if (active == owner.accountId) {
        DownloadWorkerOwnershipDecision.ALLOW
    } else {
        DownloadWorkerOwnershipDecision.SESSION_OWNER_MISMATCH
    }
}

internal enum class DownloadQuarantineReason {
    UNKNOWN_LEGACY_OWNER,
    MALFORMED_LEGACY_RECORD,
    MALFORMED_SCHEMA,
    UNSUPPORTED_SCHEMA,
    INCOMPLETE_MIGRATION,
    MISSING_EXPLICIT_OWNER,
    MALFORMED_RECORD,
    DUPLICATE_DOWNLOAD_ID,
    DUPLICATE_OWNERSHIP_KEY,
}

internal data class QuarantinedDownload(
    val quarantineId: String,
    val reason: DownloadQuarantineReason,
    val legacyDownloadId: Long?,
    val historyKey: String?,
    val fileName: String?,
    val storagePath: String?,
    val localUri: String?,
    val quarantinedAtEpochMs: Long,
)

internal data class DownloadSchemaSnapshot(
    val records: List<OfflineDownload>,
    val quarantined: List<QuarantinedDownload>,
    val requiresRewrite: Boolean,
    val rewriteAllowed: Boolean = true,
)

/**
 * Versioned, fail-closed persistence codec for downloads.
 *
 * V1 was an unscoped JSON array and cannot prove an account owner. It is therefore
 * converted to non-runnable quarantine metadata. V2 persists the complete
 * (accountId, profileId, historyKey) owner key and a secret-free source descriptor.
 * Authenticated source URLs are deliberately runtime-only.
 */
internal object DownloadSchemaCodec {
    fun decode(
        raw: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DownloadSchemaSnapshot {
        if (raw.isNullOrBlank()) {
            return DownloadSchemaSnapshot(emptyList(), emptyList(), requiresRewrite = true)
        }
        return when (raw.trimStart().firstOrNull()) {
            '[' -> decodeLegacy(raw, nowEpochMs)
            '{' -> decodeVersioned(raw, nowEpochMs)
            else -> malformedSchema(nowEpochMs)
        }
    }

    fun encode(
        records: List<OfflineDownload>,
        quarantined: List<QuarantinedDownload>,
    ): String = JSONObject()
        .put("schemaVersion", DOWNLOAD_SCHEMA_VERSION)
        .put("migrationState", DOWNLOAD_MIGRATION_STATE_COMPLETE)
        .put("records", JSONArray().apply { records.forEach { put(encodeRecord(it)) } })
        .put("quarantine", JSONArray().apply { quarantined.forEach { put(encodeQuarantine(it)) } })
        .toString()

    private fun decodeLegacy(raw: String, nowEpochMs: Long): DownloadSchemaSnapshot {
        val array = runCatching { JSONArray(raw) }.getOrElse {
            return malformedSchema(nowEpochMs, DownloadQuarantineReason.MALFORMED_LEGACY_RECORD)
        }
        val quarantine = buildList {
            for (index in 0 until array.length()) {
                val data = array.optJSONObject(index)
                add(
                    quarantineFrom(
                        data = data,
                        reason = if (data == null) {
                            DownloadQuarantineReason.MALFORMED_LEGACY_RECORD
                        } else {
                            DownloadQuarantineReason.UNKNOWN_LEGACY_OWNER
                        },
                        index = index,
                        nowEpochMs = nowEpochMs,
                    ),
                )
            }
        }
        return DownloadSchemaSnapshot(
            records = emptyList(),
            quarantined = quarantine,
            requiresRewrite = true,
        )
    }

    private fun decodeVersioned(raw: String, nowEpochMs: Long): DownloadSchemaSnapshot {
        val root = runCatching { JSONObject(raw) }.getOrElse { return malformedSchema(nowEpochMs) }
        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion != DOWNLOAD_SCHEMA_VERSION) {
            return malformedSchema(
                nowEpochMs,
                DownloadQuarantineReason.UNSUPPORTED_SCHEMA,
                rewriteAllowed = false,
            )
        }
        if (root.optString("migrationState") != DOWNLOAD_MIGRATION_STATE_COMPLETE) {
            return malformedSchema(
                nowEpochMs,
                DownloadQuarantineReason.INCOMPLETE_MIGRATION,
                rewriteAllowed = false,
            )
        }

        val quarantined = decodeQuarantine(root.optJSONArray("quarantine"), nowEpochMs).toMutableList()
        val recordsArray = root.optJSONArray("records")
            ?: return DownloadSchemaSnapshot(
                records = emptyList(),
                quarantined = quarantined + rootQuarantine(
                    reason = DownloadQuarantineReason.MALFORMED_SCHEMA,
                    nowEpochMs = nowEpochMs,
                ),
                requiresRewrite = true,
                rewriteAllowed = false,
            )
        val candidates = mutableListOf<DecodedRecordCandidate>()
        var requiresRewrite = false
        for (index in 0 until recordsArray.length()) {
            val data = recordsArray.optJSONObject(index)
            val record = data?.let(::decodeRecord)
            if (record == null) {
                quarantined += quarantineFrom(
                    data = data,
                    reason = if (data?.optString("accountId").isNullOrBlank() ||
                        data?.optString("profileId").isNullOrBlank()
                    ) {
                        DownloadQuarantineReason.MISSING_EXPLICIT_OWNER
                    } else {
                        DownloadQuarantineReason.MALFORMED_RECORD
                    },
                    index = index,
                    nowEpochMs = nowEpochMs,
                )
                requiresRewrite = true
                continue
            }
            val candidateData = data ?: continue
            candidates += DecodedRecordCandidate(index, candidateData, record)
        }
        val downloadIdCounts = candidates.groupingBy { it.record.downloadId }.eachCount()
        val ownershipKeyCounts = candidates.groupingBy { it.record.ownershipKey() }.eachCount()
        val records = mutableListOf<OfflineDownload>()
        candidates.forEach { candidate ->
            val duplicateReason = when {
                downloadIdCounts.getValue(candidate.record.downloadId) > 1 ->
                    DownloadQuarantineReason.DUPLICATE_DOWNLOAD_ID
                ownershipKeyCounts.getValue(candidate.record.ownershipKey()) > 1 ->
                    DownloadQuarantineReason.DUPLICATE_OWNERSHIP_KEY
                else -> null
            }
            if (duplicateReason != null) {
                quarantined += quarantineFrom(
                    candidate.data,
                    duplicateReason,
                    candidate.index,
                    nowEpochMs,
                )
                requiresRewrite = true
            } else {
                records += candidate.record
            }
        }
        return DownloadSchemaSnapshot(records, quarantined, requiresRewrite)
    }

    private data class DecodedRecordCandidate(
        val index: Int,
        val data: JSONObject,
        val record: OfflineDownload,
    )

    private fun decodeRecord(data: JSONObject): OfflineDownload? {
        if (data.optInt("recordVersion", -1) != DOWNLOAD_SCHEMA_VERSION) return null
        val accountId = data.optString("accountId").normalizedOwnershipComponent() ?: return null
        val profileId = data.optString("profileId").normalizedOwnershipComponent() ?: return null
        val historyKey = data.optString("historyKey").normalizedHistoryKey() ?: return null
        val downloadId = data.optLong("downloadId", -1L).takeIf { it > 0L } ?: return null
        val descriptor = data.optJSONObject("sourceDescriptor") ?: return null
        val streamKind = descriptor.optString("streamKind")
            .trim()
            .takeIf { it in DOWNLOADABLE_STREAM_KINDS }
            ?: return null
        val streamId = descriptor.optInt("streamId", -1).takeIf { it > 0 } ?: return null
        val extension = descriptor.optString("extension").safeDownloadExtension()
        val status = runCatching {
            OfflineStatus.valueOf(data.optString("status", OfflineStatus.PAUSED.name))
        }.getOrDefault(OfflineStatus.PAUSED)
        return OfflineDownload(
            downloadId = downloadId,
            accountId = accountId,
            profileId = profileId,
            historyKey = historyKey,
            title = data.optString("title").trim().takeIf(String::isNotBlank) ?: "تحميل غير معروف",
            posterUrl = data.optNullableString("posterUrl"),
            streamKind = streamKind,
            streamId = streamId,
            extension = extension,
            seriesTitle = data.optNullableString("seriesTitle"),
            season = data.optNullableInt("season"),
            episodeNumber = data.optNullableInt("episodeNumber"),
            sourceCandidates = emptyList(),
            fileName = data.optNullableString("fileName"),
            storagePath = data.optNullableString("storagePath"),
            storageLabel = data.optString("storageLabel", "التخزين الداخلي"),
            supportsRange = data.optNullableBoolean("supportsRange"),
            status = status,
            bytesDownloaded = data.optLong("bytesDownloaded", 0L).coerceAtLeast(0L),
            totalBytes = data.optLong("totalBytes", -1L),
            bytesPerSecond = 0L,
            etaSeconds = data.optLong("etaSeconds", -1L),
            localUri = data.optNullableString("localUri"),
            errorMessage = null,
            retryCount = data.optInt("retryCount", 0).coerceAtLeast(0),
            integrityVerified = data.optBoolean("integrityVerified", false),
            priority = data.optInt("priority", 0),
            queuePosition = data.optInt("queuePosition", 0).coerceAtLeast(0),
            scheduledAtEpochMs = data.optLong("scheduledAtEpochMs", 0L).coerceAtLeast(0L),
            resumeOnOwnerAuthentication = data.optBoolean("resumeOnOwnerAuthentication", false),
            createdAtEpochMs = data.optLong("createdAtEpochMs", 0L)
                .takeIf { it > 0L }
                ?: System.currentTimeMillis(),
        )
    }

    private fun encodeRecord(item: OfflineDownload): JSONObject = JSONObject()
        .put("recordVersion", DOWNLOAD_SCHEMA_VERSION)
        .put("downloadId", item.downloadId)
        .put("accountId", item.accountId)
        .put("profileId", item.profileId)
        .put("historyKey", item.historyKey)
        .put("title", item.title)
        .put("posterUrl", item.posterUrl ?: JSONObject.NULL)
        .put(
            "sourceDescriptor",
            JSONObject()
                .put("streamKind", item.streamKind)
                .put("streamId", item.streamId)
                .put("extension", item.extension),
        )
        .put("seriesTitle", item.seriesTitle ?: JSONObject.NULL)
        .put("season", item.season ?: JSONObject.NULL)
        .put("episodeNumber", item.episodeNumber ?: JSONObject.NULL)
        .put("fileName", item.fileName ?: JSONObject.NULL)
        .put("storagePath", item.storagePath ?: JSONObject.NULL)
        .put("storageLabel", item.storageLabel)
        .put("supportsRange", item.supportsRange ?: JSONObject.NULL)
        .put("status", item.status.name)
        .put("bytesDownloaded", item.bytesDownloaded)
        .put("totalBytes", item.totalBytes)
        .put("etaSeconds", item.etaSeconds)
        .put("localUri", item.localUri ?: JSONObject.NULL)
        .put("retryCount", item.retryCount)
        .put("integrityVerified", item.integrityVerified)
        .put("priority", item.priority)
        .put("queuePosition", item.queuePosition)
        .put("scheduledAtEpochMs", item.scheduledAtEpochMs)
        .put("resumeOnOwnerAuthentication", item.resumeOnOwnerAuthentication)
        .put("createdAtEpochMs", item.createdAtEpochMs)

    private fun decodeQuarantine(
        array: JSONArray?,
        nowEpochMs: Long,
    ): List<QuarantinedDownload> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val data = array.optJSONObject(index) ?: continue
                val reason = runCatching {
                    DownloadQuarantineReason.valueOf(data.optString("reason"))
                }.getOrDefault(DownloadQuarantineReason.MALFORMED_RECORD)
                add(
                    QuarantinedDownload(
                        quarantineId = data.optString("quarantineId")
                            .trim()
                            .takeIf(String::isNotBlank)
                            ?: quarantineId(reason, null, null, null, index),
                        reason = reason,
                        legacyDownloadId = data.optLong("legacyDownloadId", -1L).takeIf { it > 0L },
                        historyKey = data.optNullableString("historyKey"),
                        fileName = data.optNullableString("fileName"),
                        storagePath = data.optNullableString("storagePath"),
                        localUri = data.optNullableString("localUri"),
                        quarantinedAtEpochMs = data.optLong("quarantinedAtEpochMs", nowEpochMs)
                            .takeIf { it > 0L }
                            ?: nowEpochMs,
                    ),
                )
            }
        }.distinctBy(QuarantinedDownload::quarantineId)
    }

    private fun encodeQuarantine(item: QuarantinedDownload): JSONObject = JSONObject()
        .put("quarantineId", item.quarantineId)
        .put("reason", item.reason.name)
        .put("legacyDownloadId", item.legacyDownloadId ?: JSONObject.NULL)
        .put("historyKey", item.historyKey ?: JSONObject.NULL)
        .put("fileName", item.fileName ?: JSONObject.NULL)
        .put("storagePath", item.storagePath ?: JSONObject.NULL)
        .put("localUri", item.localUri ?: JSONObject.NULL)
        .put("quarantinedAtEpochMs", item.quarantinedAtEpochMs)

    private fun quarantineFrom(
        data: JSONObject?,
        reason: DownloadQuarantineReason,
        index: Int,
        nowEpochMs: Long,
    ): QuarantinedDownload {
        val downloadId = data?.optLong("downloadId", -1L)?.takeIf { it > 0L }
        val historyKey = data?.optNullableString("historyKey")
        val fileName = data?.optNullableString("fileName")
        val storagePath = data?.optNullableString("storagePath")
        val localUri = data?.optNullableString("localUri")
        return QuarantinedDownload(
            quarantineId = quarantineId(reason, downloadId, historyKey, fileName, index),
            reason = reason,
            legacyDownloadId = downloadId,
            historyKey = historyKey,
            fileName = fileName,
            storagePath = storagePath,
            localUri = localUri,
            quarantinedAtEpochMs = nowEpochMs,
        )
    }

    private fun malformedSchema(
        nowEpochMs: Long,
        reason: DownloadQuarantineReason = DownloadQuarantineReason.MALFORMED_SCHEMA,
        rewriteAllowed: Boolean = false,
    ): DownloadSchemaSnapshot = DownloadSchemaSnapshot(
        records = emptyList(),
        quarantined = listOf(rootQuarantine(reason, nowEpochMs)),
        requiresRewrite = true,
        rewriteAllowed = rewriteAllowed,
    )

    private fun rootQuarantine(
        reason: DownloadQuarantineReason,
        nowEpochMs: Long,
    ): QuarantinedDownload = QuarantinedDownload(
        quarantineId = quarantineId(reason, null, null, null, 0),
        reason = reason,
        legacyDownloadId = null,
        historyKey = null,
        fileName = null,
        storagePath = null,
        localUri = null,
        quarantinedAtEpochMs = nowEpochMs,
    )
}

internal fun downloadOwnerStorageKey(owner: DownloadOwner): String {
    val normalized = requireNotNull(owner.normalizedOrNull()) { "A valid download owner is required" }
    return sha256Hex("${normalized.accountId}\u0000${normalized.profileId}").take(32)
}

internal fun downloadHistoryFileKey(historyKey: String): String {
    val normalized = requireNotNull(historyKey.normalizedHistoryKey()) { "A valid history key is required" }
    return sha256Hex(normalized).take(16)
}

internal fun resolveOwnedDownloadFile(
    directory: File,
    fileName: String?,
    localUri: String?,
): File? {
    val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return null
    val normalizedFileName = fileName?.trim()?.takeIf(String::isNotBlank) ?: return null
    val candidate = runCatching { File(canonicalDirectory, normalizedFileName).canonicalFile }
        .getOrNull()
        ?: return null
    if (candidate.parentFile != canonicalDirectory) return null
    if (!localUri.isNullOrBlank()) {
        val uriFile = runCatching {
            URI(localUri).takeIf { it.scheme.equals("file", ignoreCase = true) }
                ?.let(::File)
                ?.canonicalFile
        }.getOrNull() ?: return null
        if (uriFile != candidate) return null
    }
    return candidate
}

private fun quarantineId(
    reason: DownloadQuarantineReason,
    downloadId: Long?,
    historyKey: String?,
    fileName: String?,
    index: Int,
): String = sha256Hex(
    listOf(reason.name, downloadId?.toString().orEmpty(), historyKey.orEmpty(), fileName.orEmpty(), index.toString())
        .joinToString("\u0000"),
).take(32)

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String?.normalizedOwnershipComponent(): String? = this
    ?.trim()
    ?.takeIf { value ->
        value.isNotEmpty() && value.length <= 256 && value.none { character -> character.isISOControl() }
    }

private fun String?.normalizedHistoryKey(): String? = this
    ?.trim()
    ?.takeIf { value ->
        value.isNotEmpty() && value.length <= 512 && value.none { character -> character.isISOControl() }
    }

private fun String?.safeDownloadExtension(): String = this
    .orEmpty()
    .trim()
    .lowercase()
    .filter { character -> character.isLetterOrDigit() }
    .take(8)
    .ifBlank { "mp4" }

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).trim().takeIf(String::isNotBlank)

private fun JSONObject.optNullableInt(name: String): Int? = if (isNull(name)) null else optInt(name)

private fun JSONObject.optNullableBoolean(name: String): Boolean? = if (isNull(name)) null else optBoolean(name)

internal const val OWNER_AUTHENTICATION_REQUIRED_MESSAGE =
    "تم ايقاف التحميل لحماية ملكية الحساب. سيستأنف بعد تسجيل دخول المالك."

private val OWNER_SUSPENDABLE_STATUSES = setOf(
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
)

private val DOWNLOADABLE_STREAM_KINDS = setOf("movie", "series")
