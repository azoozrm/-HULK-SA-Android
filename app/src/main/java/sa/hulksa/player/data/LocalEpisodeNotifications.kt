package sa.hulksa.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.ProfileKind

data class EpisodeNotificationSubscription(
    val accountId: String,
    val profileId: String,
    val seriesId: Int,
    val seriesName: String,
    val posterUrl: String?,
    val categoryId: String,
    val enabled: Boolean,
    val knownEpisodeKeys: Set<String>,
    val lastCheckedAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class LocalEpisodeNotification(
    val id: String,
    val accountId: String,
    val profileId: String,
    val seriesId: Int,
    val episodeStableKey: String,
    val episodeId: Int?,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val seriesName: String,
    val posterUrl: String?,
    val categoryId: String,
    val createdAtEpochMs: Long,
    val read: Boolean,
    val popupShown: Boolean,
    val batchId: String,
)

data class ProfileEpisodeNotificationSettings(
    val profileId: String,
    val enabled: Boolean = true,
    val baselineRefreshRequired: Boolean = false,
)

data class EpisodeNotificationSnapshot(
    val subscriptions: List<EpisodeNotificationSubscription>,
    val notifications: List<LocalEpisodeNotification>,
    val settings: ProfileEpisodeNotificationSettings,
) {
    val unreadCount: Int
        get() = notifications.count { !it.read }

    val subscribedSeriesIds: Set<Int>
        get() = subscriptions.asSequence()
            .filter(EpisodeNotificationSubscription::enabled)
            .map(EpisodeNotificationSubscription::seriesId)
            .toSet()
}

data class EpisodeNotificationPopup(
    val profileId: String,
    val eventIds: List<String>,
    val notifications: List<LocalEpisodeNotification>,
    val summary: Boolean,
) {
    val episodeCount: Int
        get() = notifications.size

    val seriesName: String?
        get() = notifications.firstOrNull()?.seriesName

    val posterUrl: String?
        get() = notifications.firstOrNull()?.posterUrl
}

enum class EpisodeNotificationStoreResult {
    SUCCESS,
    MISSING_ACCOUNT,
    NOT_FOUND,
    INVALID_EPISODES,
    STORAGE_FAILURE,
}

data class EpisodeScanEvaluation(
    val result: EpisodeNotificationStoreResult,
    val updatedSubscription: EpisodeNotificationSubscription,
    val newNotifications: List<LocalEpisodeNotification>,
)

data class EpisodeScanCommitResult(
    val result: EpisodeNotificationStoreResult,
    val notifications: List<LocalEpisodeNotification> = emptyList(),
)

internal fun buildEpisodeNotificationSubscription(
    accountId: String,
    profileId: String,
    series: ContentItem,
    episodes: List<Episode>,
    nowEpochMs: Long,
    existing: EpisodeNotificationSubscription? = null,
): EpisodeNotificationSubscription? {
    val keys = reliableEpisodeKeys(episodes)
    if (
        accountId.isBlank() ||
        profileId.isBlank() ||
        series.id <= 0 ||
        series.name.isBlank() ||
        keys.isEmpty()
    ) return null
    return EpisodeNotificationSubscription(
        accountId = accountId,
        profileId = profileId,
        seriesId = series.id,
        seriesName = series.name,
        posterUrl = series.posterUrl,
        categoryId = series.categoryId,
        enabled = true,
        knownEpisodeKeys = existing?.knownEpisodeKeys.orEmpty() + keys,
        lastCheckedAtEpochMs = nowEpochMs,
        createdAtEpochMs = existing?.createdAtEpochMs ?: nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
    )
}

fun stableEpisodeKey(episode: Episode): String? = when {
    episode.id > 0 -> "id:${episode.id}"
    episode.season > 0 && episode.episodeNumber > 0 ->
        "season:${episode.season}:episode:${episode.episodeNumber}"
    else -> null
}

fun reliableEpisodeKeys(episodes: List<Episode>): Set<String> = episodes
    .mapNotNull(::stableEpisodeKey)
    .toCollection(linkedSetOf())

internal fun canUseSeriesEpisodeNotifications(
    profileKind: ProfileKind,
    seriesId: Int,
    verifiedKidsContentKeys: Set<String>,
): Boolean = seriesId > 0 && (
    profileKind != ProfileKind.KIDS ||
        kidsContentKey(ContentType.SERIES, seriesId) in verifiedKidsContentKeys
    )

internal fun nextProfileEpisodeNotificationSettings(
    current: ProfileEpisodeNotificationSettings,
    enabled: Boolean,
): ProfileEpisodeNotificationSettings = current.copy(
    enabled = enabled,
    baselineRefreshRequired = enabled && !current.enabled,
)

internal fun canRecordEpisodeNotificationScan(
    settings: ProfileEpisodeNotificationSettings,
    subscription: EpisodeNotificationSubscription,
): Boolean = settings.enabled && !settings.baselineRefreshRequired && subscription.enabled

internal fun resolveLocalNotificationSeriesTarget(
    profileKind: ProfileKind,
    notification: LocalEpisodeNotification,
    generalSeries: List<ContentItem>,
): ContentItem {
    val storedTarget = ContentItem(
        id = notification.seriesId,
        name = notification.seriesName,
        categoryId = notification.categoryId,
        type = ContentType.SERIES,
        posterUrl = notification.posterUrl,
        rating = null,
        year = null,
        containerExtension = null,
    )
    return if (profileKind == ProfileKind.KIDS) {
        storedTarget
    } else {
        generalSeries.firstOrNull { it.id == notification.seriesId } ?: storedTarget
    }
}

fun localEpisodeNotificationEventKey(
    accountId: String,
    profileId: String,
    seriesId: Int,
    episodeStableKey: String,
): String = "$accountId|$profileId|$seriesId|$episodeStableKey"

fun evaluateEpisodeScan(
    subscription: EpisodeNotificationSubscription,
    episodes: List<Episode>,
    detectedAtEpochMs: Long,
    batchId: String,
): EpisodeScanEvaluation {
    if (!subscription.enabled) {
        return EpisodeScanEvaluation(
            result = EpisodeNotificationStoreResult.NOT_FOUND,
            updatedSubscription = subscription,
            newNotifications = emptyList(),
        )
    }
    val keyedEpisodes = episodes.mapNotNull { episode ->
        stableEpisodeKey(episode)?.let { key -> key to episode }
    }.distinctBy { it.first }
    val responseIsUnreliable =
        (episodes.isNotEmpty() && keyedEpisodes.isEmpty()) ||
            (episodes.isEmpty() && subscription.knownEpisodeKeys.isNotEmpty())
    if (responseIsUnreliable) {
        return EpisodeScanEvaluation(
            result = EpisodeNotificationStoreResult.INVALID_EPISODES,
            updatedSubscription = subscription,
            newNotifications = emptyList(),
        )
    }

    val newEpisodes = keyedEpisodes.filterNot { (key, _) -> key in subscription.knownEpisodeKeys }
    val notifications = newEpisodes.mapNotNull { (key, episode) ->
        if (episode.season <= 0 || episode.episodeNumber <= 0) return@mapNotNull null
        LocalEpisodeNotification(
            id = localEpisodeNotificationEventKey(
                accountId = subscription.accountId,
                profileId = subscription.profileId,
                seriesId = subscription.seriesId,
                episodeStableKey = key,
            ),
            accountId = subscription.accountId,
            profileId = subscription.profileId,
            seriesId = subscription.seriesId,
            episodeStableKey = key,
            episodeId = episode.id.takeIf { it > 0 },
            seasonNumber = episode.season,
            episodeNumber = episode.episodeNumber,
            seriesName = subscription.seriesName,
            posterUrl = episode.posterUrl ?: subscription.posterUrl,
            categoryId = subscription.categoryId,
            createdAtEpochMs = detectedAtEpochMs,
            read = false,
            popupShown = false,
            batchId = batchId,
        )
    }
    val currentKeys = keyedEpisodes.mapTo(linkedSetOf()) { it.first }
    return EpisodeScanEvaluation(
        result = EpisodeNotificationStoreResult.SUCCESS,
        updatedSubscription = subscription.copy(
            knownEpisodeKeys = subscription.knownEpisodeKeys + currentKeys,
            lastCheckedAtEpochMs = detectedAtEpochMs,
            updatedAtEpochMs = detectedAtEpochMs,
        ),
        newNotifications = notifications,
    )
}

fun trimEpisodeNotificationHistory(
    notifications: List<LocalEpisodeNotification>,
    limit: Int = LocalEpisodeNotificationStore.MAX_NOTIFICATIONS_PER_PROFILE,
): List<LocalEpisodeNotification> {
    if (limit <= 0) return emptyList()
    val retained = notifications
        .distinctBy(LocalEpisodeNotification::id)
        .sortedByDescending(LocalEpisodeNotification::createdAtEpochMs)
        .toMutableList()
    while (retained.size > limit) {
        val oldestReadIndex = retained.indexOfLast(LocalEpisodeNotification::read)
        retained.removeAt(if (oldestReadIndex >= 0) oldestReadIndex else retained.lastIndex)
    }
    return retained
}

internal fun markEpisodeNotificationRead(
    notifications: List<LocalEpisodeNotification>,
    notificationId: String,
): List<LocalEpisodeNotification> = notifications.map { notification ->
    if (notification.id == notificationId) notification.copy(read = true) else notification
}

internal fun markAllEpisodeNotificationsRead(
    notifications: List<LocalEpisodeNotification>,
): List<LocalEpisodeNotification> = notifications.map { it.copy(read = true) }

internal fun deleteEpisodeNotification(
    notifications: List<LocalEpisodeNotification>,
    notificationId: String,
): List<LocalEpisodeNotification> = notifications.filterNot { it.id == notificationId }

internal fun markEpisodeNotificationPopupsShown(
    notifications: List<LocalEpisodeNotification>,
    notificationIds: Collection<String>,
): List<LocalEpisodeNotification> {
    val ids = notificationIds.toSet()
    return notifications.map { notification ->
        if (notification.id in ids) notification.copy(popupShown = true) else notification
    }
}

fun buildEpisodeNotificationPopups(
    notifications: List<LocalEpisodeNotification>,
    maxSequentialAlerts: Int = 3,
    summaryEpisodeThreshold: Int = 5,
): List<EpisodeNotificationPopup> {
    val pending = notifications
        .asSequence()
        .filter { !it.popupShown && !it.read }
        .sortedBy(LocalEpisodeNotification::createdAtEpochMs)
        .toList()
    if (pending.isEmpty()) return emptyList()

    val grouped = pending.groupBy { "${it.batchId}|${it.seriesId}" }.values.toList()
    if (
        grouped.size > maxSequentialAlerts ||
        (grouped.size > 1 && pending.size >= summaryEpisodeThreshold)
    ) {
        return listOf(
            EpisodeNotificationPopup(
                profileId = pending.first().profileId,
                eventIds = pending.map(LocalEpisodeNotification::id),
                notifications = pending,
                summary = true,
            ),
        )
    }
    return grouped.map { events ->
        EpisodeNotificationPopup(
            profileId = events.first().profileId,
            eventIds = events.map(LocalEpisodeNotification::id),
            notifications = events.sortedWith(
                compareBy(LocalEpisodeNotification::seasonNumber, LocalEpisodeNotification::episodeNumber),
            ),
            summary = false,
        )
    }
}

class LocalEpisodeNotificationStore(context: Context) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)

    fun activeAccountId(): String? = accountScope.activeAccountId()

    @Synchronized
    fun snapshot(profileId: String): EpisodeNotificationSnapshot {
        val accountId = accountScope.activeAccountId()
        if (accountId == null) {
            return EpisodeNotificationSnapshot(
                subscriptions = emptyList(),
                notifications = emptyList(),
                settings = ProfileEpisodeNotificationSettings(profileId),
            )
        }
        val state = readState(accountId)
        return EpisodeNotificationSnapshot(
            subscriptions = state.subscriptions
                .filter { it.profileId == profileId }
                .sortedBy(EpisodeNotificationSubscription::seriesName),
            notifications = state.notifications
                .filter { it.profileId == profileId }
                .sortedByDescending(LocalEpisodeNotification::createdAtEpochMs),
            settings = state.settings[profileId] ?: ProfileEpisodeNotificationSettings(profileId),
        )
    }

    @Synchronized
    fun enableSubscription(
        profileId: String,
        series: ContentItem,
        episodes: List<Episode>,
        nowEpochMs: Long = System.currentTimeMillis(),
        expectedAccountId: String? = null,
    ): EpisodeNotificationStoreResult {
        val accountId = resolveActiveAccount(expectedAccountId)
            ?: return EpisodeNotificationStoreResult.MISSING_ACCOUNT
        val state = readState(accountId)
        val existing = state.subscriptions.firstOrNull {
            it.profileId == profileId && it.seriesId == series.id
        }
        val subscription = buildEpisodeNotificationSubscription(
            accountId = accountId,
            profileId = profileId,
            series = series,
            episodes = episodes,
            nowEpochMs = nowEpochMs,
            existing = existing,
        ) ?: return EpisodeNotificationStoreResult.INVALID_EPISODES
        val updated = state.copy(
            subscriptions = state.subscriptions.filterNot {
                it.profileId == profileId && it.seriesId == series.id
            } + subscription,
        )
        return if (writeState(accountId, updated)) {
            EpisodeNotificationStoreResult.SUCCESS
        } else {
            EpisodeNotificationStoreResult.STORAGE_FAILURE
        }
    }

    @Synchronized
    fun disableSubscription(
        profileId: String,
        seriesId: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
        expectedAccountId: String? = null,
    ): EpisodeNotificationStoreResult {
        val accountId = resolveActiveAccount(expectedAccountId)
            ?: return EpisodeNotificationStoreResult.MISSING_ACCOUNT
        val state = readState(accountId)
        val existing = state.subscriptions.firstOrNull {
            it.profileId == profileId && it.seriesId == seriesId && it.enabled
        } ?: return EpisodeNotificationStoreResult.NOT_FOUND
        val updatedSubscription = existing.copy(enabled = false, updatedAtEpochMs = nowEpochMs)
        val updated = state.copy(
            subscriptions = state.subscriptions.map {
                if (it.profileId == profileId && it.seriesId == seriesId) updatedSubscription else it
            },
        )
        return if (writeState(accountId, updated)) {
            EpisodeNotificationStoreResult.SUCCESS
        } else {
            EpisodeNotificationStoreResult.STORAGE_FAILURE
        }
    }

    @Synchronized
    fun recordSuccessfulScan(
        profileId: String,
        seriesId: Int,
        episodes: List<Episode>,
        detectedAtEpochMs: Long,
        batchId: String,
        expectedAccountId: String? = null,
    ): EpisodeScanCommitResult {
        val accountId = resolveActiveAccount(expectedAccountId)
            ?: return EpisodeScanCommitResult(EpisodeNotificationStoreResult.MISSING_ACCOUNT)
        val state = readState(accountId)
        val settings = state.settings[profileId] ?: ProfileEpisodeNotificationSettings(profileId)
        val subscription = state.subscriptions.firstOrNull {
            it.profileId == profileId && it.seriesId == seriesId && it.enabled
        } ?: return EpisodeScanCommitResult(EpisodeNotificationStoreResult.NOT_FOUND)
        if (!canRecordEpisodeNotificationScan(settings, subscription)) {
            return EpisodeScanCommitResult(EpisodeNotificationStoreResult.NOT_FOUND)
        }
        val evaluation = evaluateEpisodeScan(subscription, episodes, detectedAtEpochMs, batchId)
        if (evaluation.result != EpisodeNotificationStoreResult.SUCCESS) {
            return EpisodeScanCommitResult(evaluation.result)
        }

        val existingIds = state.notifications.mapTo(hashSetOf(), LocalEpisodeNotification::id)
        val uniqueNew = evaluation.newNotifications.filterNot { it.id in existingIds }
        val updatedNotifications = trimPerProfile(
            notifications = state.notifications + uniqueNew,
            profileId = profileId,
        )
        val updated = state.copy(
            subscriptions = state.subscriptions.map {
                if (it.profileId == profileId && it.seriesId == seriesId) {
                    evaluation.updatedSubscription
                } else {
                    it
                }
            },
            notifications = updatedNotifications,
        )
        return if (writeState(accountId, updated)) {
            EpisodeScanCommitResult(EpisodeNotificationStoreResult.SUCCESS, uniqueNew)
        } else {
            EpisodeScanCommitResult(EpisodeNotificationStoreResult.STORAGE_FAILURE)
        }
    }

    @Synchronized
    fun replaceBaseline(
        profileId: String,
        seriesId: Int,
        episodes: List<Episode>,
        checkedAtEpochMs: Long,
        expectedAccountId: String? = null,
    ): EpisodeNotificationStoreResult {
        val accountId = resolveActiveAccount(expectedAccountId)
            ?: return EpisodeNotificationStoreResult.MISSING_ACCOUNT
        val keys = reliableEpisodeKeys(episodes)
        if (keys.isEmpty()) {
            return EpisodeNotificationStoreResult.INVALID_EPISODES
        }
        val state = readState(accountId)
        val subscription = state.subscriptions.firstOrNull {
            it.profileId == profileId && it.seriesId == seriesId && it.enabled
        } ?: return EpisodeNotificationStoreResult.NOT_FOUND
        val updatedSubscription = subscription.copy(
            knownEpisodeKeys = subscription.knownEpisodeKeys + keys,
            lastCheckedAtEpochMs = checkedAtEpochMs,
            updatedAtEpochMs = checkedAtEpochMs,
        )
        val updated = state.copy(
            subscriptions = state.subscriptions.map {
                if (it.profileId == profileId && it.seriesId == seriesId) updatedSubscription else it
            },
        )
        return if (writeState(accountId, updated)) {
            EpisodeNotificationStoreResult.SUCCESS
        } else {
            EpisodeNotificationStoreResult.STORAGE_FAILURE
        }
    }

    @Synchronized
    fun setMasterEnabled(
        profileId: String,
        enabled: Boolean,
        expectedAccountId: String? = null,
    ): EpisodeNotificationStoreResult {
        val accountId = resolveActiveAccount(expectedAccountId)
            ?: return EpisodeNotificationStoreResult.MISSING_ACCOUNT
        val state = readState(accountId)
        val current = state.settings[profileId] ?: ProfileEpisodeNotificationSettings(profileId)
        val updatedSetting = nextProfileEpisodeNotificationSettings(current, enabled)
        val updated = state.copy(settings = state.settings + (profileId to updatedSetting))
        return if (writeState(accountId, updated)) {
            EpisodeNotificationStoreResult.SUCCESS
        } else {
            EpisodeNotificationStoreResult.STORAGE_FAILURE
        }
    }

    @Synchronized
    fun completeMasterBaselineRefresh(
        profileId: String,
        expectedAccountId: String? = null,
    ): EpisodeNotificationStoreResult {
        val accountId = resolveActiveAccount(expectedAccountId)
            ?: return EpisodeNotificationStoreResult.MISSING_ACCOUNT
        val state = readState(accountId)
        val current = state.settings[profileId] ?: ProfileEpisodeNotificationSettings(profileId)
        val updated = state.copy(
            settings = state.settings + (profileId to current.copy(baselineRefreshRequired = false)),
        )
        return if (writeState(accountId, updated)) {
            EpisodeNotificationStoreResult.SUCCESS
        } else {
            EpisodeNotificationStoreResult.STORAGE_FAILURE
        }
    }

    @Synchronized
    fun markRead(
        profileId: String,
        notificationId: String,
        expectedAccountId: String? = null,
    ): Boolean =
        mutateProfileNotifications(profileId, expectedAccountId) { events ->
            markEpisodeNotificationRead(events, notificationId)
        }

    @Synchronized
    fun markAllRead(profileId: String, expectedAccountId: String? = null): Boolean =
        mutateProfileNotifications(profileId, expectedAccountId, ::markAllEpisodeNotificationsRead)

    @Synchronized
    fun markPopupShown(
        profileId: String,
        notificationIds: Collection<String>,
        expectedAccountId: String? = null,
    ): Boolean {
        return mutateProfileNotifications(profileId, expectedAccountId) { events ->
            markEpisodeNotificationPopupsShown(events, notificationIds)
        }
    }

    @Synchronized
    fun deleteNotification(
        profileId: String,
        notificationId: String,
        expectedAccountId: String? = null,
    ): Boolean =
        mutateProfileNotifications(profileId, expectedAccountId) { events ->
            deleteEpisodeNotification(events, notificationId)
        }

    @Synchronized
    fun clearNotifications(profileId: String, expectedAccountId: String? = null): Boolean =
        mutateProfileNotifications(profileId, expectedAccountId) { emptyList() }

    @Synchronized
    fun removeProfile(profileId: String, expectedAccountId: String? = null): Boolean {
        val accountId = resolveActiveAccount(expectedAccountId) ?: return false
        val state = readState(accountId)
        return writeState(
            accountId,
            state.copy(
                subscriptions = state.subscriptions.filterNot { it.profileId == profileId },
                notifications = state.notifications.filterNot { it.profileId == profileId },
                settings = state.settings - profileId,
            ),
        )
    }

    private fun mutateProfileNotifications(
        profileId: String,
        expectedAccountId: String?,
        transform: (List<LocalEpisodeNotification>) -> List<LocalEpisodeNotification>,
    ): Boolean {
        val accountId = resolveActiveAccount(expectedAccountId) ?: return false
        val state = readState(accountId)
        val currentProfileEvents = state.notifications.filter { it.profileId == profileId }
        val updated = state.copy(
            notifications = state.notifications.filterNot { it.profileId == profileId } +
                transform(currentProfileEvents),
        )
        return writeState(accountId, updated)
    }

    private fun trimPerProfile(
        notifications: List<LocalEpisodeNotification>,
        profileId: String,
    ): List<LocalEpisodeNotification> =
        notifications.filterNot { it.profileId == profileId } +
            trimEpisodeNotificationHistory(notifications.filter { it.profileId == profileId })

    private fun readState(accountId: String): PersistedState = runCatching {
        val raw = accountPreferences(accountId).getString(KEY_STATE, null) ?: return PersistedState()
        val root = JSONObject(raw)
        val subscriptions = root.optJSONArray("subscriptions").objects().mapNotNull { item ->
            val itemAccount = item.optString("accountId").takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (itemAccount != accountId) return@mapNotNull null
            val profileId = item.optString("profileId").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val seriesId = item.optInt("seriesId").takeIf { it > 0 } ?: return@mapNotNull null
            EpisodeNotificationSubscription(
                accountId = itemAccount,
                profileId = profileId,
                seriesId = seriesId,
                seriesName = item.optString("seriesName").ifBlank { "مسلسل" },
                posterUrl = item.optNullableString("posterUrl"),
                categoryId = item.optString("categoryId"),
                enabled = item.optBoolean("enabled", false),
                knownEpisodeKeys = item.optJSONArray("knownEpisodeKeys").strings().toSet(),
                lastCheckedAtEpochMs = item.optLong("lastCheckedAtEpochMs", 0L),
                createdAtEpochMs = item.optLong("createdAtEpochMs", 0L),
                updatedAtEpochMs = item.optLong("updatedAtEpochMs", 0L),
            )
        }
        val notifications = root.optJSONArray("notifications").objects().mapNotNull { item ->
            val itemAccount = item.optString("accountId").takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (itemAccount != accountId) return@mapNotNull null
            val profileId = item.optString("profileId").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val seriesId = item.optInt("seriesId").takeIf { it > 0 } ?: return@mapNotNull null
            val stableKey = item.optString("episodeStableKey").takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            LocalEpisodeNotification(
                id = item.optString("id").takeIf(String::isNotBlank)
                    ?: localEpisodeNotificationEventKey(itemAccount, profileId, seriesId, stableKey),
                accountId = itemAccount,
                profileId = profileId,
                seriesId = seriesId,
                episodeStableKey = stableKey,
                episodeId = item.optInt("episodeId").takeIf { item.has("episodeId") && it > 0 },
                seasonNumber = item.optInt("seasonNumber", 0),
                episodeNumber = item.optInt("episodeNumber", 0),
                seriesName = item.optString("seriesName").ifBlank { "مسلسل" },
                posterUrl = item.optNullableString("posterUrl"),
                categoryId = item.optString("categoryId"),
                createdAtEpochMs = item.optLong("createdAtEpochMs", 0L),
                read = item.optBoolean("read", false),
                popupShown = item.optBoolean("popupShown", false),
                batchId = item.optString("batchId").ifBlank { "legacy" },
            )
        }
        val settings = root.optJSONArray("settings").objects().mapNotNull { item ->
            val profileId = item.optString("profileId").takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            profileId to ProfileEpisodeNotificationSettings(
                profileId = profileId,
                enabled = item.optBoolean("enabled", true),
                baselineRefreshRequired = item.optBoolean("baselineRefreshRequired", false),
            )
        }.toMap()
        PersistedState(subscriptions, notifications, settings)
    }.getOrDefault(PersistedState())

    private fun writeState(accountId: String, state: PersistedState): Boolean {
        val root = JSONObject()
            .put("schemaVersion", CURRENT_SCHEMA_VERSION)
            .put("subscriptions", JSONArray().apply {
                state.subscriptions.forEach { subscription ->
                    put(JSONObject()
                        .put("accountId", subscription.accountId)
                        .put("profileId", subscription.profileId)
                        .put("seriesId", subscription.seriesId)
                        .put("seriesName", subscription.seriesName)
                        .put("posterUrl", subscription.posterUrl ?: JSONObject.NULL)
                        .put("categoryId", subscription.categoryId)
                        .put("enabled", subscription.enabled)
                        .put("knownEpisodeKeys", JSONArray(subscription.knownEpisodeKeys.toList()))
                        .put("lastCheckedAtEpochMs", subscription.lastCheckedAtEpochMs)
                        .put("createdAtEpochMs", subscription.createdAtEpochMs)
                        .put("updatedAtEpochMs", subscription.updatedAtEpochMs))
                }
            })
            .put("notifications", JSONArray().apply {
                state.notifications.forEach { notification ->
                    put(JSONObject()
                        .put("id", notification.id)
                        .put("accountId", notification.accountId)
                        .put("profileId", notification.profileId)
                        .put("seriesId", notification.seriesId)
                        .put("episodeStableKey", notification.episodeStableKey)
                        .put("episodeId", notification.episodeId ?: JSONObject.NULL)
                        .put("seasonNumber", notification.seasonNumber)
                        .put("episodeNumber", notification.episodeNumber)
                        .put("seriesName", notification.seriesName)
                        .put("posterUrl", notification.posterUrl ?: JSONObject.NULL)
                        .put("categoryId", notification.categoryId)
                        .put("createdAtEpochMs", notification.createdAtEpochMs)
                        .put("read", notification.read)
                        .put("popupShown", notification.popupShown)
                        .put("batchId", notification.batchId))
                }
            })
            .put("settings", JSONArray().apply {
                state.settings.values.forEach { setting ->
                    put(JSONObject()
                        .put("profileId", setting.profileId)
                        .put("enabled", setting.enabled)
                        .put("baselineRefreshRequired", setting.baselineRefreshRequired))
                }
            })
        return accountPreferences(accountId).edit().putString(KEY_STATE, root.toString()).commit()
    }

    private fun accountPreferences(accountId: String) = appContext.getSharedPreferences(
        accountScopedPreferencesName(PREFERENCES_NAME, accountId),
        Context.MODE_PRIVATE,
    )

    private fun resolveActiveAccount(expectedAccountId: String?): String? {
        val activeAccountId = accountScope.activeAccountId() ?: return null
        return activeAccountId.takeIf { expectedAccountId == null || it == expectedAccountId }
    }

    private data class PersistedState(
        val subscriptions: List<EpisodeNotificationSubscription> = emptyList(),
        val notifications: List<LocalEpisodeNotification> = emptyList(),
        val settings: Map<String, ProfileEpisodeNotificationSettings> = emptyMap(),
    )

    companion object {
        const val MAX_NOTIFICATIONS_PER_PROFILE = 200
        private const val CURRENT_SCHEMA_VERSION = 1
        private const val PREFERENCES_NAME = "hulk_local_episode_notifications_v1"
        private const val KEY_STATE = "state"
    }
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeUnless { it.isBlank() || it.equals("null", true) }
}
