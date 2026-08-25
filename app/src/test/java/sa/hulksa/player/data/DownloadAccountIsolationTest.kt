package sa.hulksa.player.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import java.nio.file.Files

class DownloadAccountIsolationTest {
    @Test
    fun `account A primary logout then account B primary sees no A records`() {
        val aRecord = download(ownerA, OfflineStatus.QUEUED)
        val loggedOut = suspendDownloadForAccountLogout(aRecord, ownerA.accountId)

        assertTrue(visibleDownloads(listOf(loggedOut), ownerB).isEmpty())
        assertEquals(
            DownloadWorkerOwnershipDecision.SESSION_OWNER_MISMATCH,
            decideDownloadWorkerOwnership(loggedOut, ownerA, ownerB.accountId),
        )
        assertEquals(
            DownloadWorkerOwnershipDecision.RECORD_OWNER_MISMATCH,
            decideDownloadWorkerOwnership(loggedOut, ownerB, ownerB.accountId),
        )
        assertTrue(loggedOut.sourceCandidates.isEmpty())
    }

    @Test
    fun `account switch without logout still suspends the inactive account source`() {
        val activeA = download(ownerA, OfflineStatus.DOWNLOADING)
        val activeB = download(ownerB, OfflineStatus.PAUSED, downloadId = 2L, historyKey = "movie:2")
        val switchedRecords = suspendInactiveAccountDownloads(listOf(activeA, activeB), ownerB.accountId)
        val switched = switchedRecords.first { it.isOwnedBy(ownerA) }

        assertTrue(visibleDownloads(listOf(switched), ownerB).isEmpty())
        assertEquals(OfflineStatus.PAUSED, switched.status)
        assertTrue(switched.sourceCandidates.isEmpty())
        assertEquals(activeB, switchedRecords.first { it.isOwnedBy(ownerB) })
        assertEquals(
            DownloadWorkerOwnershipDecision.SESSION_OWNER_MISMATCH,
            decideDownloadWorkerOwnership(switched, ownerA, ownerB.accountId),
        )
    }

    @Test
    fun `profiles in the same account have exact isolation`() {
        val profile1 = DownloadOwner(ownerA.accountId, "profile-1")
        val profile2 = DownloadOwner(ownerA.accountId, "profile-2")
        val records = listOf(
            download(profile1, OfflineStatus.COMPLETED, historyKey = "movie:1"),
            download(profile2, OfflineStatus.COMPLETED, historyKey = "movie:2", downloadId = 2L),
        )

        assertEquals(listOf("movie:1"), visibleDownloads(records, profile1).map { it.historyKey })
        assertEquals(listOf("movie:2"), visibleDownloads(records, profile2).map { it.historyKey })
    }

    @Test
    fun `logout while queued suspends transport and requires owner authentication`() {
        assertLogoutSuspends(OfflineStatus.QUEUED)
    }

    @Test
    fun `logout while checking suspends transport and requires owner authentication`() {
        assertLogoutSuspends(OfflineStatus.CHECKING)
    }

    @Test
    fun `logout while downloading suspends transport and requires owner authentication`() {
        assertLogoutSuspends(OfflineStatus.DOWNLOADING)
    }

    @Test
    fun `logout while manually paused does not create automatic resume`() {
        val paused = suspendDownloadForAccountLogout(
            download(ownerA, OfflineStatus.PAUSED),
            ownerA.accountId,
        )

        assertEquals(OfflineStatus.PAUSED, paused.status)
        assertFalse(paused.resumeOnOwnerAuthentication)
        assertTrue(paused.sourceCandidates.isEmpty())
    }

    @Test
    fun `logout after completion keeps ownership but removes authenticated source`() {
        val completed = suspendDownloadForAccountLogout(
            download(ownerA, OfflineStatus.COMPLETED),
            ownerA.accountId,
        )

        assertEquals(OfflineStatus.COMPLETED, completed.status)
        assertFalse(completed.resumeOnOwnerAuthentication)
        assertTrue(completed.sourceCandidates.isEmpty())
        assertTrue(visibleDownloads(listOf(completed), ownerB).isEmpty())
    }

    @Test
    fun `process death before migration quarantines unowned v1 record`() {
        val legacy = JSONArray().put(
            JSONObject()
                .put("downloadId", 7L)
                .put("historyKey", "movie:7")
                .put("title", "Legacy")
                .put("streamKind", "movie")
                .put("streamId", 7)
                .put("extension", "mp4")
                .put("sourceCandidates", JSONArray().put(legacyAuthenticatedUrl)),
        ).toString()

        val restored = DownloadSchemaCodec.decode(legacy, nowEpochMs = 100L)

        assertTrue(restored.records.isEmpty())
        assertEquals(1, restored.quarantined.size)
        assertEquals(DownloadQuarantineReason.UNKNOWN_LEGACY_OWNER, restored.quarantined.single().reason)
        assertTrue(restored.requiresRewrite)
    }

    @Test
    fun `process death after migration restores explicit owner without persisted source`() {
        val beforeDeath = download(ownerA, OfflineStatus.DOWNLOADING)
        val persisted = DownloadSchemaCodec.encode(listOf(beforeDeath), emptyList())

        val afterDeath = DownloadSchemaCodec.decode(persisted, nowEpochMs = 200L)

        assertEquals(1, afterDeath.records.size)
        assertEquals(ownerA, afterDeath.records.single().owner())
        assertEquals(beforeDeath.historyKey, afterDeath.records.single().historyKey)
        assertTrue(afterDeath.records.single().sourceCandidates.isEmpty())
        assertFalse(afterDeath.requiresRewrite)
        assertEquals(
            OfflineStatus.QUEUED,
            recoverDownloadAfterProcessDeath(afterDeath.records.single(), nowEpochMs = 200L).status,
        )
    }

    @Test
    fun `reboot restoration only allows persisted worker for its authenticated account`() {
        val record = DownloadSchemaCodec.decode(
            DownloadSchemaCodec.encode(
                listOf(download(ownerA, OfflineStatus.QUEUED)),
                emptyList(),
            ),
        ).records.single()

        assertEquals(
            DownloadWorkerOwnershipDecision.ALLOW,
            decideDownloadWorkerOwnership(record, ownerA, ownerA.accountId),
        )
        assertEquals(
            DownloadWorkerOwnershipDecision.SESSION_OWNER_MISMATCH,
            decideDownloadWorkerOwnership(record, ownerA, ownerB.accountId),
        )
        val prepared = prepareDownloadForAuthenticatedOwner(
            item = record,
            owner = ownerA,
            refreshedSources = listOf("https://runtime.invalid/new-source"),
            nowEpochMs = 1_000L,
        )
        assertEquals(listOf("https://runtime.invalid/new-source"), prepared?.sourceCandidates)
        val plan = durableDownloadWorkPlan(
            downloadId = record.downloadId,
            owner = ownerA,
            wifiOnly = false,
            scheduledAtEpochMs = 0L,
            nowEpochMs = 1_000L,
        )
        assertEquals(ownerA, plan.owner)
        assertTrue(plan.uniqueWorkName.contains(downloadOwnerStorageKey(ownerA)))
    }

    @Test
    fun `malformed legacy and malformed v2 data fail closed without throwing`() {
        val malformedLegacy = DownloadSchemaCodec.decode("[{broken", nowEpochMs = 1L)
        val malformedV2 = DownloadSchemaCodec.decode(
            JSONObject()
                .put("schemaVersion", DOWNLOAD_SCHEMA_VERSION)
                .put("migrationState", DOWNLOAD_MIGRATION_STATE_COMPLETE)
                .put("records", JSONArray().put(JSONObject().put("downloadId", 1L)))
                .put("quarantine", JSONArray())
                .toString(),
            nowEpochMs = 2L,
        )

        assertTrue(malformedLegacy.records.isEmpty())
        assertTrue(malformedLegacy.quarantined.isNotEmpty())
        assertFalse(malformedLegacy.rewriteAllowed)
        assertTrue(malformedV2.records.isEmpty())
        assertEquals(DownloadQuarantineReason.MISSING_EXPLICIT_OWNER, malformedV2.quarantined.single().reason)
    }

    @Test
    fun `unsupported or incomplete schema is not overwritten`() {
        val unsupported = DownloadSchemaCodec.decode(
            JSONObject()
                .put("schemaVersion", DOWNLOAD_SCHEMA_VERSION + 1)
                .put("migrationState", DOWNLOAD_MIGRATION_STATE_COMPLETE)
                .put("records", JSONArray())
                .toString(),
            nowEpochMs = 4L,
        )
        val incomplete = DownloadSchemaCodec.decode(
            JSONObject()
                .put("schemaVersion", DOWNLOAD_SCHEMA_VERSION)
                .put("migrationState", "in_progress")
                .put("records", JSONArray())
                .toString(),
            nowEpochMs = 5L,
        )

        assertTrue(unsupported.records.isEmpty())
        assertFalse(unsupported.rewriteAllowed)
        assertEquals(DownloadQuarantineReason.UNSUPPORTED_SCHEMA, unsupported.quarantined.single().reason)
        assertTrue(incomplete.records.isEmpty())
        assertFalse(incomplete.rewriteAllowed)
        assertEquals(DownloadQuarantineReason.INCOMPLETE_MIGRATION, incomplete.quarantined.single().reason)
    }

    @Test
    fun `missing ownership key components are each quarantined`() {
        val validRecord = JSONObject(
            DownloadSchemaCodec.encode(
                listOf(download(ownerA, OfflineStatus.PAUSED)),
                emptyList(),
            ),
        ).getJSONArray("records").getJSONObject(0)

        listOf("accountId", "profileId", "historyKey").forEach { missingField ->
            val incompleteRecord = JSONObject(validRecord.toString()).apply { remove(missingField) }
            val root = JSONObject()
                .put("schemaVersion", DOWNLOAD_SCHEMA_VERSION)
                .put("migrationState", DOWNLOAD_MIGRATION_STATE_COMPLETE)
                .put("records", JSONArray().put(incompleteRecord))
                .put("quarantine", JSONArray())

            val decoded = DownloadSchemaCodec.decode(root.toString(), nowEpochMs = 3L)

            assertTrue(decoded.records.isEmpty())
            assertTrue(decoded.quarantined.isNotEmpty())
        }
    }

    @Test
    fun `unknown legacy owner is quarantined and cannot become active`() {
        val legacy = JSONArray().put(
            JSONObject()
                .put("downloadId", 9L)
                .put("historyKey", "series:9")
                .put("fileName", "episode.mp4")
                .put("storagePath", "/private/legacy")
                .put("localUri", "file:///private/legacy/episode.mp4"),
        ).toString()

        val migrated = DownloadSchemaCodec.decode(legacy, nowEpochMs = 9L)
        val persistedQuarantine = DownloadSchemaCodec.encode(migrated.records, migrated.quarantined)

        assertTrue(parseDurableDownloadRecords(persistedQuarantine).isEmpty())
        assertEquals("episode.mp4", migrated.quarantined.single().fileName)
    }

    @Test
    fun `credential rotation replaces runtime source only for the exact owner`() {
        val old = download(ownerA, OfflineStatus.PAUSED)
        val rotated = prepareDownloadForAuthenticatedOwner(
            item = old,
            owner = ownerA,
            refreshedSources = listOf("https://runtime.invalid/new-credential-source"),
            nowEpochMs = 50L,
        )

        assertEquals(listOf("https://runtime.invalid/new-credential-source"), rotated?.sourceCandidates)
        assertFalse(rotated.orEmptySources().contains(legacyAuthenticatedUrl))
        assertNull(
            prepareDownloadForAuthenticatedOwner(
                item = old,
                owner = ownerB,
                refreshedSources = listOf("https://runtime.invalid/wrong-account"),
                nowEpochMs = 50L,
            ),
        )
    }

    @Test
    fun `deleting same profile id in account B cannot delete account A files or records`() {
        val sharedProfileId = "profile-shared-id"
        val aOwner = DownloadOwner("account-a", sharedProfileId)
        val bOwner = DownloadOwner("account-b", sharedProfileId)
        val a = download(aOwner, OfflineStatus.COMPLETED, downloadId = 11L, historyKey = "movie:11")
        val b = download(bOwner, OfflineStatus.COMPLETED, downloadId = 12L, historyKey = "movie:12")

        val deletion = partitionDownloadsForProfileDeletion(listOf(a, b), bOwner)

        assertEquals(listOf(a), deletion.retained)
        assertEquals(listOf(b), deletion.removed)
    }

    @Test
    fun `physical file resolution cannot cross owner directories`() {
        val root = Files.createTempDirectory("hulk-download-owner-test").toFile()
        try {
            val accountADirectory = root.resolve(downloadOwnerStorageKey(ownerA)).apply { mkdirs() }
            val accountBDirectory = root.resolve(downloadOwnerStorageKey(ownerB)).apply { mkdirs() }
            val accountAFile = accountADirectory.resolve("movie.mp4").apply { createNewFile() }

            assertEquals(
                accountAFile.canonicalFile,
                resolveOwnedDownloadFile(accountADirectory, accountAFile.name, accountAFile.toURI().toString()),
            )
            assertNull(
                resolveOwnedDownloadFile(accountBDirectory, accountAFile.name, accountAFile.toURI().toString()),
            )
            assertNull(
                resolveOwnedDownloadFile(accountBDirectory, "../${accountADirectory.name}/movie.mp4", null),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `queue normalization cannot let account B reorder account A`() {
        val aFirst = download(ownerA, OfflineStatus.QUEUED, downloadId = 31L, historyKey = "movie:31")
            .copy(queuePosition = 8)
        val aSecond = download(ownerA, OfflineStatus.QUEUED, downloadId = 32L, historyKey = "movie:32")
            .copy(queuePosition = 9)
        val b = download(ownerB, OfflineStatus.QUEUED, downloadId = 33L, historyKey = "movie:33")
            .copy(queuePosition = 0, priority = 1)

        val normalizedWithB = normalizeOwnedDownloadQueues(listOf(aFirst, aSecond, b))
        val normalizedWithoutB = normalizeOwnedDownloadQueues(listOf(aFirst, aSecond))

        assertEquals(
            normalizedWithoutB.map { it.downloadId to it.queuePosition },
            normalizedWithB.filter { it.isOwnedBy(ownerA) }.map { it.downloadId to it.queuePosition },
        )
    }

    @Test
    fun `persisted migration and quarantine never contain authenticated source credentials`() {
        val owned = download(ownerA, OfflineStatus.QUEUED)
        val encodedOwned = DownloadSchemaCodec.encode(listOf(owned), emptyList())
        val legacy = JSONArray().put(
            JSONObject()
                .put("downloadId", 1L)
                .put("historyKey", "movie:1")
                .put("sourceCandidates", JSONArray().put(legacyAuthenticatedUrl)),
        ).toString()
        val migrated = DownloadSchemaCodec.decode(legacy, nowEpochMs = 1L)
        val encodedQuarantine = DownloadSchemaCodec.encode(migrated.records, migrated.quarantined)

        listOf(encodedOwned, encodedQuarantine).forEach { persisted ->
            assertFalse(persisted.contains("credential-marker-a"))
            assertFalse(persisted.contains("credential-marker-b"))
            assertFalse(persisted.contains(legacyAuthenticatedUrl))
            assertFalse(persisted.contains("sourceCandidates"))
        }
    }

    @Test
    fun `duplicate owner key is quarantined instead of creating ambiguous ownership`() {
        val first = download(ownerA, OfflineStatus.PAUSED, downloadId = 21L)
        val duplicate = first.copy(downloadId = 22L)
        val root = JSONObject(DownloadSchemaCodec.encode(listOf(first, duplicate), emptyList()))

        val decoded = DownloadSchemaCodec.decode(root.toString(), nowEpochMs = 22L)

        assertTrue(decoded.records.isEmpty())
        assertEquals(2, decoded.quarantined.size)
        assertTrue(decoded.quarantined.all { it.reason == DownloadQuarantineReason.DUPLICATE_OWNERSHIP_KEY })
    }

    @Test
    fun `duplicate download id across accounts quarantines every conflicting record`() {
        val accountA = download(ownerA, OfflineStatus.PAUSED, downloadId = 23L, historyKey = "movie:23")
        val accountB = download(ownerB, OfflineStatus.PAUSED, downloadId = 23L, historyKey = "movie:24")

        val decoded = DownloadSchemaCodec.decode(
            DownloadSchemaCodec.encode(listOf(accountA, accountB), emptyList()),
            nowEpochMs = 23L,
        )

        assertTrue(decoded.records.isEmpty())
        assertEquals(2, decoded.quarantined.size)
        assertTrue(decoded.quarantined.all { it.reason == DownloadQuarantineReason.DUPLICATE_DOWNLOAD_ID })
    }

    private fun assertLogoutSuspends(status: OfflineStatus) {
        val suspended = suspendDownloadForAccountLogout(download(ownerA, status), ownerA.accountId)
        assertEquals(OfflineStatus.PAUSED, suspended.status)
        assertTrue(suspended.resumeOnOwnerAuthentication)
        assertTrue(suspended.sourceCandidates.isEmpty())
        assertEquals(OWNER_AUTHENTICATION_REQUIRED_MESSAGE, suspended.errorMessage)
    }

    private fun download(
        owner: DownloadOwner,
        status: OfflineStatus,
        historyKey: String = "movie:1",
        downloadId: Long = 1L,
    ): OfflineDownload = OfflineDownload(
        downloadId = downloadId,
        accountId = owner.accountId,
        profileId = owner.profileId,
        historyKey = historyKey,
        title = "Movie",
        posterUrl = null,
        streamKind = "movie",
        streamId = downloadId.toInt().coerceAtLeast(1),
        extension = "mp4",
        sourceCandidates = listOf(legacyAuthenticatedUrl),
        fileName = "movie.mp4",
        storagePath = "/private/${downloadOwnerStorageKey(owner)}",
        localUri = "file:///private/${downloadOwnerStorageKey(owner)}/movie.mp4",
        status = status,
    )

    private fun OfflineDownload?.orEmptySources(): List<String> = this?.sourceCandidates.orEmpty()

    private val ownerA = DownloadOwner("account-a", "primary")
    private val ownerB = DownloadOwner("account-b", "primary")
    private val legacyAuthenticatedUrl =
        "http://iptv.invalid/movie/credential-marker-a/credential-marker-b/1.mp4"
}
