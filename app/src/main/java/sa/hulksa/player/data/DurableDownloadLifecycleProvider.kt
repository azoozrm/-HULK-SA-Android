package sa.hulksa.player.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper

internal class DurableDownloadLifecycleProvider : ContentProvider() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var store: DurableDownloadPreferenceStore? = null
    private var bridge: DurableDownloadLifecycleBridge? = null
    private var knownDownloadIds: Set<Long> = emptySet()

    private val reconcileRunnable = Runnable(::reconcile)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (
            key == DurableDownloadPreferenceStore.KEY_DOWNLOADS ||
            key == DurableDownloadPreferenceStore.KEY_WIFI_ONLY
        ) {
            requestReconciliation()
        }
    }

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        store = DurableDownloadPreferenceStore(appContext)
        store?.register(preferenceListener)
        mainHandler.post {
            bridge = DurableDownloadLifecycleBridge(appContext)
            reconcile()
        }
        return true
    }

    private fun requestReconciliation() {
        mainHandler.removeCallbacks(reconcileRunnable)
        mainHandler.post(reconcileRunnable)
    }

    private fun reconcile() {
        val currentStore = store ?: return
        val currentBridge = bridge ?: return
        val snapshot = currentStore.snapshot()
        val currentIds = snapshot.records.mapTo(mutableSetOf()) { it.downloadId }

        (knownDownloadIds - currentIds).forEach(currentBridge::cancel)
        snapshot.records.forEach { record ->
            when (durableDownloadLifecycleAction(record.status)) {
                DurableDownloadLifecycleAction.ENQUEUE -> currentBridge.enqueue(
                    downloadId = record.downloadId,
                    title = record.title,
                    wifiOnly = snapshot.wifiOnly,
                    scheduledAtEpochMs = record.scheduledAtEpochMs,
                )
                DurableDownloadLifecycleAction.CANCEL -> currentBridge.cancel(record.downloadId)
            }
        }
        knownDownloadIds = currentIds
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
