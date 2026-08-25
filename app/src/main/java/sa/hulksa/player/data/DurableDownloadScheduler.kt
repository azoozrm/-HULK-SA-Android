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
    val downloadId: Long,
    val owner: DownloadOwner,
    val uniqueWorkName: String,
    val initialDelayMs: Long,
    val backoffDelayMs: Long,
    val networkRequirement: DurableDownloadNetworkRequirement,
)

internal fun durableDownloadWorkPlan(
    downloadId: Long,
    owner: DownloadOwner,
    wifiOnly: Boolean,
    scheduledAtEpochMs: Long,
    nowEpochMs: Long = System.currentTimeMillis(),
): DurableDownloadWorkPlan {
    require(downloadId > 0L) { "downloadId must be positive" }
    val normalizedOwner = requireNotNull(owner.normalizedOrNull()) { "A valid download owner is required" }
    return DurableDownloadWorkPlan(
        downloadId = downloadId,
        owner = normalizedOwner,
        uniqueWorkName = "$UNIQUE_WORK_PREFIX${downloadOwnerStorageKey(normalizedOwner)}_$downloadId",
        initialDelayMs = (scheduledAtEpochMs - nowEpochMs).coerceAtLeast(0L),
        backoffDelayMs = DURABLE_DOWNLOAD_BACKOFF_MS,
        networkRequirement = if (wifiOnly) {
            DurableDownloadNetworkRequirement.UNMETERED
        } else {
            DurableDownloadNetworkRequirement.CONNECTED
        },
    )
}

internal class DurableDownloadScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) {
    fun enqueue(
        downloadId: Long,
        owner: DownloadOwner,
        wifiOnly: Boolean,
        scheduledAtEpochMs: Long,
    ) {
        val plan = durableDownloadWorkPlan(
            downloadId = downloadId,
            owner = owner,
            wifiOnly = wifiOnly,
            scheduledAtEpochMs = scheduledAtEpochMs,
        )
        workManager.enqueueUniqueWork(
            plan.uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            plan.toWorkRequest(),
        )
    }

    fun cancel(downloadId: Long, owner: DownloadOwner) {
        val plan = durableDownloadWorkPlan(
            downloadId = downloadId,
            owner = owner,
            wifiOnly = false,
            scheduledAtEpochMs = 0L,
        )
        workManager.cancelUniqueWork(plan.uniqueWorkName)
    }
}

private fun DurableDownloadWorkPlan.toWorkRequest(): OneTimeWorkRequest {
    val requiredNetworkType = when (networkRequirement) {
        DurableDownloadNetworkRequirement.CONNECTED -> NetworkType.CONNECTED
        DurableDownloadNetworkRequirement.UNMETERED -> NetworkType.UNMETERED
    }
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(requiredNetworkType)
        .setRequiresStorageNotLow(true)
        .build()
    val input = Data.Builder()
        .putLong(KEY_DOWNLOAD_ID, downloadId)
        .putString(KEY_DOWNLOAD_ACCOUNT_ID, owner.accountId)
        .putString(KEY_DOWNLOAD_PROFILE_ID, owner.profileId)
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
        .build()
}

internal class DownloadCoordinatorWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return Result.failure()
        val owner = DownloadOwner(
            accountId = inputData.getString(KEY_DOWNLOAD_ACCOUNT_ID).orEmpty(),
            profileId = inputData.getString(KEY_DOWNLOAD_PROFILE_ID).orEmpty(),
        ).normalizedOrNull() ?: return Result.failure()
        setForeground(
            DurableDownloadForeground(applicationContext).createInfo(
                downloadId = downloadId,
                owner = owner,
            ),
        )
        return when (DownloadExecutionEntryPoint(applicationContext).execute(downloadId, owner)) {
            DurableDownloadExecutionResult.COMPLETED,
            DurableDownloadExecutionResult.TERMINAL,
            -> Result.success()

            DurableDownloadExecutionResult.RETRY -> Result.retry()
        }
    }
}

internal const val KEY_DOWNLOAD_ID = "download_id"
internal const val KEY_DOWNLOAD_ACCOUNT_ID = "download_account_id"
internal const val KEY_DOWNLOAD_PROFILE_ID = "download_profile_id"
internal const val DURABLE_DOWNLOAD_TAG = "hulk_durable_download"
private const val UNIQUE_WORK_PREFIX = "hulk_durable_download_v2_"
private const val DURABLE_DOWNLOAD_BACKOFF_MS = 30_000L
