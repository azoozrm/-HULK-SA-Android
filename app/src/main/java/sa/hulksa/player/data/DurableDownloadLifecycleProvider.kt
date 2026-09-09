package sa.hulksa.player.data

import android.content.ContentProvider
import android.content.Context
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sa.hulksa.player.model.OfflineStatus

internal enum class DurableDownloadStartupMaintenanceResult {
    READY,
    CREDENTIAL_SCRUB_FAILED,
    LEGACY_OWNER_CAPTURE_FAILED,
}

internal suspend fun runDurableDownloadStartupMaintenance(
    scrubPersistedCredentials: () -> Boolean,
    captureLegacyOwner: () -> Boolean,
): DurableDownloadStartupMaintenanceResult = withContext(Dispatchers.IO) {
    val scrubbed = try {
        scrubPersistedCredentials()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }
    if (!scrubbed) return@withContext DurableDownloadStartupMaintenanceResult.CREDENTIAL_SCRUB_FAILED

    val ownerCaptured = try {
        captureLegacyOwner()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }
    if (ownerCaptured) {
        DurableDownloadStartupMaintenanceResult.READY
    } else {
        DurableDownloadStartupMaintenanceResult.LEGACY_OWNER_CAPTURE_FAILED
    }
}

// Two exponential retries bound total backoff to 1.5 seconds without a tight storage loop.
internal val DURABLE_DOWNLOAD_STARTUP_RETRY_BACKOFF_MS = listOf(500L, 1_000L)

internal suspend fun runDurableDownloadStartupMaintenanceWithRetry(
    retryBackoffMs: List<Long> = DURABLE_DOWNLOAD_STARTUP_RETRY_BACKOFF_MS,
    maintenance: suspend () -> DurableDownloadStartupMaintenanceResult,
    waitBeforeRetry: suspend (Long) -> Unit = { delay(it) },
    onReady: () -> Unit,
): DurableDownloadStartupMaintenanceResult {
    var result = maintenance()
    retryBackoffMs.forEach { backoffMs ->
        if (result == DurableDownloadStartupMaintenanceResult.READY) {
            onReady()
            return result
        }
        waitBeforeRetry(backoffMs)
        result = maintenance()
    }
    if (result == DurableDownloadStartupMaintenanceResult.READY) {
        onReady()
    }
    return result
}

internal class DurableDownloadLifecycleProvider : ContentProvider() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var store: DurableDownloadPreferenceStore? = null
    private var bridge: DurableDownloadLifecycleBridge? = null
    private var accountScopeStore: AccountScopeStore? = null
    private var startupMaintenanceJob: Job? = null
    private var lifecycleInitialized = false
    private var boundAccountId: String? = null
    private var connectivityManager: ConnectivityManager? = null
    private var knownSchedulingStates: Map<Long, DurableDownloadSchedulingState> = emptyMap()
    private var forceWaitingNetworkReenqueue = false

    private val reconcileRunnable = Runnable(::reconcile)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (
            key == DurableDownloadPreferenceStore.KEY_DOWNLOADS ||
            key == DurableDownloadPreferenceStore.KEY_WIFI_ONLY
        ) {
            requestReconciliation()
        }
    }
    private val accountScopeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mainHandler.post {
            if (lifecycleInitialized) {
                requestReconciliation()
            } else {
                startStartupMaintenance()
            }
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            requestNetworkRecoveryIfUsable(network)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (hasUsableInternet(capabilities)) {
                requestNetworkRecovery()
            }
        }
    }

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        accountScopeStore = AccountScopeStore(appContext).also { accountScope ->
            accountScope.registerActiveAccountListener(accountScopeListener)
        }
        startStartupMaintenance()
        return true
    }

    private fun startStartupMaintenance() {
        if (lifecycleInitialized || startupMaintenanceJob?.isActive == true) return
        val appContext = context?.applicationContext ?: return
        startupMaintenanceJob = startupScope.launch {
            runDurableDownloadStartupMaintenanceWithRetry(
                maintenance = {
                    runDurableDownloadStartupMaintenance(
                        scrubPersistedCredentials = { scrubPersistedDownloadCredentialUrls(appContext) },
                        captureLegacyOwner = { DownloadRepositoryProcessOwner.captureLegacyOwner(appContext) },
                    )
                },
                onReady = {
                    initializeLifecycle(appContext)
                },
            )
        }
    }

    private fun initializeLifecycle(appContext: Context) {
        if (lifecycleInitialized) return
        lifecycleInitialized = true
        connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
        runCatching {
            connectivityManager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback,
            )
        }
        bridge = DurableDownloadLifecycleBridge(appContext)
        reconcile()
    }

    private fun requestNetworkRecoveryIfUsable(network: Network) {
        val capabilities = connectivityManager?.getNetworkCapabilities(network) ?: return
        if (hasUsableInternet(capabilities)) {
            requestNetworkRecovery()
        }
    }

    private fun requestNetworkRecovery() {
        mainHandler.post {
            forceWaitingNetworkReenqueue = true
            requestReconciliation()
        }
    }

    private fun hasUsableInternet(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun requestReconciliation() {
        mainHandler.removeCallbacks(reconcileRunnable)
        mainHandler.post(reconcileRunnable)
    }

    private fun reconcile() {
        rebindAccountStore()
        val currentStore = store ?: return
        val currentBridge = bridge ?: return
        val accountId = boundAccountId ?: return
        val forceNetworkRecovery = forceWaitingNetworkReenqueue
        forceWaitingNetworkReenqueue = false
        val snapshot = currentStore.snapshot()
        val recordsById = snapshot.records.associateBy { it.downloadId }
        val currentStates = recordsById.mapValues { (_, record) ->
            durableDownloadSchedulingState(record, snapshot.wifiOnly)
        }

        (knownSchedulingStates.keys - currentStates.keys).forEach { downloadId ->
            currentBridge.cancel(accountId, downloadId)
        }
        recordsById.forEach { (downloadId, record) ->
            val currentState = currentStates.getValue(downloadId)
            val previousState = knownSchedulingStates[downloadId]
            val forceWaitingNetwork =
                forceNetworkRecovery &&
                    record.status == OfflineStatus.WAITING_NETWORK &&
                    currentState.action == DurableDownloadLifecycleAction.ENQUEUE
            if (
                !forceWaitingNetwork &&
                !shouldApplyDurableDownloadSchedulingState(
                    previous = previousState,
                    current = currentState,
                    currentStatus = record.status,
                )
            ) {
                return@forEach
            }
            when (currentState.action) {
                DurableDownloadLifecycleAction.ENQUEUE -> currentBridge.enqueue(
                    accountId = accountId,
                    downloadId = downloadId,
                    title = record.title,
                    wifiOnly = currentState.wifiOnly,
                    scheduledAtEpochMs = currentState.scheduledAtEpochMs,
                )
                DurableDownloadLifecycleAction.CANCEL -> currentBridge.cancel(accountId, downloadId)
            }
        }
        knownSchedulingStates = currentStates
    }

    private fun rebindAccountStore() {
        val nextAccountId = accountScopeStore?.activeAccountId()
        if (nextAccountId == boundAccountId) return

        store?.unregister(preferenceListener)
        val previousAccountId = boundAccountId
        val currentBridge = bridge
        if (previousAccountId != null && currentBridge != null) {
            knownSchedulingStates.keys.forEach { downloadId ->
                currentBridge.cancel(previousAccountId, downloadId)
            }
            currentBridge.cancelAccount(previousAccountId)
        }
        knownSchedulingStates = emptyMap()
        boundAccountId = nextAccountId
        store = nextAccountId?.let { accountId ->
            DurableDownloadPreferenceStore(requireNotNull(context).applicationContext, accountId)
                .also { it.register(preferenceListener) }
        }
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
