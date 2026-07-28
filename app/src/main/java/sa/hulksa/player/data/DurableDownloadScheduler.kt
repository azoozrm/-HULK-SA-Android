package sa.hulksa.player.data

import android.content.Context
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
    val uniqueWorkName: String,
    val initialDelayMs: Long,
    val networkRequirement: DurableDownloadNetworkRequirement,
)

internal fun durableDownloadWorkPlan(
    downloadId: Long,
    wifiOnly: Boolean,
    scheduledAtEpochMs: Long,
    nowEpochMs: Long = System.currentTimeMillis(),
): DurableDownloadWorkPlan {
    require(downloadId > 0L) { "downloadId must be positive" }
    return DurableDownloadWorkPlan(
        downloadId = downloadId,
        uniqueWorkName = "$UNIQUE_WORK_PREFIX$downloadId",
        initialDelayMs = (scheduledAtEpochMs - nowEpochMs).coerceAtLeast(0L),
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
        wifiOnly: Boolean,
        scheduledAtEpochMs: Long,
        title: String? = null,
    ) {
        val plan = durableDownloadWorkPlan(
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

    fun cancel(downloadId: Long) {
        workManager.cancelUniqueWork("$UNIQUE_WORK_PREFIX$downloadId")
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
        .putLong(KEY_DOWNLOAD_ID, downloadId)
        .apply {
            title?.trim()?.takeIf(String::isNotEmpty)?.let { putString(KEY_DOWNLOAD_TITLE, it) }
        }
        .build()
    return OneTimeWorkRequestBuilder<DownloadCoordinatorWorker>()
        .setInputData(input)
        .setConstraints(constraints)
        .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
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
        setForeground(
            DurableDownloadForeground(applicationContext).createInfo(
                downloadId = downloadId,
                title = inputData.getString(KEY_DOWNLOAD_TITLE),
            ),
        )
        return when (DownloadExecutionEntryPoint(applicationContext).execute(downloadId)) {
            DurableDownloadExecutionResult.COMPLETED,
            DurableDownloadExecutionResult.TERMINAL,
            -> Result.success()

            DurableDownloadExecutionResult.RETRY -> Result.retry()
        }
    }
}

internal const val KEY_DOWNLOAD_ID = "download_id"
internal const val KEY_DOWNLOAD_TITLE = "download_title"
internal const val DURABLE_DOWNLOAD_TAG = "hulk_durable_download"
private const val UNIQUE_WORK_PREFIX = "hulk_durable_download_"
