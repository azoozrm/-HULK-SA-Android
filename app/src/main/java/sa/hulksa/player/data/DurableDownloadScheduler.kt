package sa.hulksa.player.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal enum class DurableDownloadNetworkRequirement {
    CONNECTED,
    UNMETERED,
}

internal data class DurableDownloadWorkPlan(
    val accountId: String,
    val downloadId: Long,
    val uniqueWorkName: String,
    val initialDelayMs: Long,
    val backoffDelayMs: Long,
    val networkRequirement: DurableDownloadNetworkRequirement,
)

internal fun durableDownloadWorkPlan(
    accountId: String,
    downloadId: Long,
    wifiOnly: Boolean,
    scheduledAtEpochMs: Long,
    nowEpochMs: Long = System.currentTimeMillis(),
): DurableDownloadWorkPlan {
    validateDownloadAccountId(accountId)
    require(downloadId > 0L) { "downloadId must be positive" }
    val normalizedAccountId = accountId.trim()
    return DurableDownloadWorkPlan(
        accountId = normalizedAccountId,
        downloadId = downloadId,
        uniqueWorkName = durableDownloadUniqueWorkName(normalizedAccountId, downloadId),
        initialDelayMs = (scheduledAtEpochMs - nowEpochMs).coerceAtLeast(0L),
        backoffDelayMs = DURABLE_DOWNLOAD_BACKOFF_MS,
        networkRequirement = if (wifiOnly) {
            DurableDownloadNetworkRequirement.UNMETERED
        } else {
            DurableDownloadNetworkRequirement.CONNECTED
        },
    )
}

internal fun validateDownloadAccountId(accountId: String) {
    require(accountId.isNotBlank()) { "accountId must not be blank" }
}

internal fun durableDownloadUniqueWorkName(accountId: String, downloadId: Long): String {
    validateDownloadAccountId(accountId)
    require(downloadId > 0L) { "downloadId must be positive" }
    return "$UNIQUE_WORK_PREFIX${downloadAccountStorageKey(accountId)}_$downloadId"
}

internal fun durableDownloadAccountTag(accountId: String): String {
    validateDownloadAccountId(accountId)
    return "$DURABLE_DOWNLOAD_ACCOUNT_TAG_PREFIX${downloadAccountStorageKey(accountId)}"
}

internal enum class DownloadWorkerSessionGate {
    ALLOW,
    RETRY,
    TERMINAL,
}

internal fun downloadWorkerSessionGate(
    workerAccountId: String,
    activeAccountId: String?,
    metadata: AccountSessionMetadata?,
    hasAuthenticatedSession: Boolean,
    authenticatedSessionMatches: Boolean,
): DownloadWorkerSessionGate {
    val normalizedWorkerAccountId = workerAccountId.trim().takeIf(String::isNotEmpty)
        ?: return DownloadWorkerSessionGate.TERMINAL
    if (activeAccountId?.trim() != normalizedWorkerAccountId) {
        return DownloadWorkerSessionGate.TERMINAL
    }
    if (metadata == null) return DownloadWorkerSessionGate.RETRY
    if (
        metadata.isExpired() ||
        metadata.accountId.trim() != normalizedWorkerAccountId
    ) {
        return DownloadWorkerSessionGate.TERMINAL
    }
    if (!hasAuthenticatedSession) return DownloadWorkerSessionGate.RETRY
    return if (authenticatedSessionMatches) {
        DownloadWorkerSessionGate.ALLOW
    } else {
        DownloadWorkerSessionGate.TERMINAL
    }
}

internal fun shouldRestoreDownloadWorkerSession(
    workerAccountId: String,
    activeAccountId: String?,
    metadata: AccountSessionMetadata?,
    hasAuthenticatedSession: Boolean,
): Boolean {
    if (hasAuthenticatedSession) return false
    val normalizedWorkerAccountId = workerAccountId.trim().takeIf(String::isNotEmpty) ?: return false
    if (activeAccountId?.trim() != normalizedWorkerAccountId) return false
    if (metadata == null || metadata.isExpired()) return false
    return metadata.accountId.trim() == normalizedWorkerAccountId
}

internal class DurableDownloadScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) {
    fun enqueue(
        accountId: String,
        downloadId: Long,
        wifiOnly: Boolean,
        scheduledAtEpochMs: Long,
        title: String? = null,
    ) {
        val plan = durableDownloadWorkPlan(
            accountId = accountId,
            downloadId = downloadId,
            wifiOnly = wifiOnly,
            scheduledAtEpochMs = scheduledAtEpochMs,
        )
        workManager.enqueueUniqueWork(
            plan.uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            plan.toWorkRequest(title),
        )
    }

    fun cancel(accountId: String, downloadId: Long) {
        workManager.cancelUniqueWork(durableDownloadUniqueWorkName(accountId, downloadId))
    }

    fun cancelAccount(accountId: String) {
        workManager.cancelAllWorkByTag(durableDownloadAccountTag(accountId))
    }
}

private fun DurableDownloadWorkPlan.toWorkRequest(title: String?): OneTimeWorkRequest {
    val requiredNetworkType = when (networkRequirement) {
        DurableDownloadNetworkRequirement.CONNECTED -> NetworkType.CONNECTED
        DurableDownloadNetworkRequirement.UNMETERED -> NetworkType.UNMETERED
    }
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(requiredNetworkType)
        .setRequiresStorageNotLow(true)
        .build()
    val input = Data.Builder()
        .putString(KEY_DOWNLOAD_ACCOUNT_ID, accountId)
        .putLong(KEY_DOWNLOAD_ID, downloadId)
        .apply {
            title?.trim()?.takeIf(String::isNotEmpty)?.let { putString(KEY_DOWNLOAD_TITLE, it) }
        }
        .build()
    return OneTimeWorkRequestBuilder<DownloadCoordinatorWorker>()
        .setInputData(input)
        .setConstraints(constraints)
        .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            backoffDelayMs,
            TimeUnit.MILLISECONDS,
        )
        .addTag(DURABLE_DOWNLOAD_TAG)
        .addTag(durableDownloadAccountTag(accountId))
        .build()
}

internal class DownloadCoordinatorWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString(KEY_DOWNLOAD_ACCOUNT_ID)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return Result.failure()
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return Result.failure()

        val accountScopeStore = AccountScopeStore(applicationContext)
        val accountSessionStore = AccountSessionStore(applicationContext)
        val activeAccountId = accountScopeStore.activeAccountId()
        val metadata = accountSessionStore.metadata()
        var session = AuthenticatedSessionRegistry.current()
        if (
            shouldRestoreDownloadWorkerSession(
                workerAccountId = accountId,
                activeAccountId = activeAccountId,
                metadata = metadata,
                hasAuthenticatedSession = session != null,
            )
        ) {
            // Reconstruct stale RUNNING state before authentication. If credentials were not
            // persisted, the record remains safely queued instead of presenting phantom progress.
            DownloadRepositoryProcessOwner.get(applicationContext, accountId).downloads()
            session = HulkRepository(applicationContext).currentAuthenticatedSession()
        }

        when (
            downloadWorkerSessionGate(
                workerAccountId = accountId,
                activeAccountId = activeAccountId,
                metadata = accountSessionStore.metadata(),
                hasAuthenticatedSession = session != null,
                authenticatedSessionMatches =
                    authenticatedDownloadAccountId(session, accountSessionStore.metadata()) == accountId,
            )
        ) {
            DownloadWorkerSessionGate.TERMINAL -> return Result.success()
            DownloadWorkerSessionGate.RETRY -> return Result.retry()
            DownloadWorkerSessionGate.ALLOW -> Unit
        }
        setForeground(
            DurableDownloadForeground(applicationContext).createInfo(
                downloadId = downloadId,
                title = inputData.getString(KEY_DOWNLOAD_TITLE),
            ),
        )
        return when (DownloadExecutionEntryPoint(applicationContext).execute(accountId, downloadId)) {
            DurableDownloadExecutionResult.COMPLETED,
            DurableDownloadExecutionResult.TERMINAL,
            -> Result.success()

            DurableDownloadExecutionResult.RETRY -> Result.retry()
        }
    }
}

internal const val KEY_DOWNLOAD_ID = "download_id"
internal const val KEY_DOWNLOAD_ACCOUNT_ID = "download_account_id"
internal const val KEY_DOWNLOAD_TITLE = "download_title"
internal const val DURABLE_DOWNLOAD_TAG = "hulk_durable_download"
private const val DURABLE_DOWNLOAD_ACCOUNT_TAG_PREFIX = "hulk_durable_download_account_"
private const val UNIQUE_WORK_PREFIX = "hulk_durable_download_"
private const val DURABLE_DOWNLOAD_BACKOFF_MS = 30_000L
