package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.security.containsCredentialBearingIptvMaterial
import sa.hulksa.player.security.persistableExternalUrlOrNull

internal data class UserLibrarySnapshot(
    val favorites: Set<String>,
    val history: List<HistoryEntry>,
)

enum class FavoriteMutationStatus {
    APPLIED_ADDED,
    APPLIED_REMOVED,
    UNCHANGED,
    KIDS_REJECTED,
    OWNER_CHANGED,
    PERSISTENCE_FAILED,
    THROTTLED,
}

/**
 * One authoritative favorite-toggle result. `favorites` is the safe owner-scoped set that may be
 * published; owner changes deliberately expose no other profile's data.
 */
data class FavoriteMutationOutcome(
    val status: FavoriteMutationStatus,
    val key: String,
    val favorites: Set<String> = emptySet(),
)

/** Per-item remote duplicate protection without rejecting a legitimate different action. */
internal class FavoriteMutationGate(
    private val throttleMs: Long = FAVORITE_TOGGLE_THROTTLE_MS,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    internal class Ticket internal constructor(val key: String)

    private val lastAcceptedAtMs = mutableMapOf<String, Long>()
    private val inFlight = mutableSetOf<String>()

    @Synchronized
    fun admit(key: String): Ticket? {
        if (key in inFlight) return null
        val now = nowMs()
        val previous = lastAcceptedAtMs[key]
        if (previous != null && now - previous < throttleMs) return null
        lastAcceptedAtMs[key] = now
        inFlight += key
        return Ticket(key)
    }

    @Synchronized
    fun finish(ticket: Ticket) {
        inFlight -= ticket.key
    }
}

internal const val FAVORITE_TOGGLE_THROTTLE_MS = 700L

internal suspend fun <T> runUserLibraryStartupOffMain(operation: () -> T): T =
    withContext(Dispatchers.IO) { operation() }

internal suspend fun <T> runUserLibraryProgressPersistenceOffMain(operation: suspend () -> T): T =
    withContext(Dispatchers.IO) { operation() }

internal suspend fun <T> runUserLibraryHistoryPersistenceOffMain(operation: suspend () -> T): T =
    withContext(Dispatchers.IO) { operation() }

internal suspend fun <T> runFavoriteMutationOffMain(operation: () -> T): T =
    withContext(Dispatchers.IO) { operation() }

internal fun userLibraryProgressWriteAllowed(
    attemptCurrent: Boolean,
    sameAuthenticatedSession: Boolean,
    expectedAccountId: String,
    activeAccountId: String?,
    expectedProfileId: String,
    activeProfileId: String,
): Boolean =
    attemptCurrent &&
        sameAuthenticatedSession &&
        expectedAccountId == activeAccountId &&
        expectedProfileId == activeProfileId

internal fun historyPublicationAllowed(
    attemptCurrent: Boolean,
    sessionCurrent: Boolean,
    expectedAccountId: String,
    activeAccountId: String?,
    expectedProfileId: String,
    activeProfileId: String,
): Boolean =
    attemptCurrent &&
        sessionCurrent &&
        expectedAccountId == activeAccountId &&
        expectedProfileId == activeProfileId

internal fun resumePositionForHistory(entries: List<HistoryEntry>, key: String): Long {
    val entry = entries.firstOrNull { it.key == key } ?: return 0L
    return entry.positionMs.takeIf {
        entry.durationMs <= 0L || entry.positionMs.toDouble() / entry.durationMs < COMPLETED_RATIO
    }?.coerceAtLeast(0L) ?: 0L
}

class UserLibrary internal constructor(
    private val accountScope: AccountScopeStore,
    private val profileStore: ProfileStore,
    private val kidsContentFilterStore: KidsContentFilterStore,
) {
    constructor(context: Context) : this(
        AccountScopeStore(context.applicationContext),
        ProfileStore(context.applicationContext),
        KidsContentFilterStore(context.applicationContext),
    )

    private val preferences: SharedPreferences
        get() = accountScope.preferences(PREFERENCES_NAME)
    private val historyMutationGate = UserLibraryHistoryMutationGate()
    private val historyPersistenceLock = Any()
    private val historyFinalWriteLock = Any()
    private val favoritesMutationLock = Any()
    private val trackPreferenceIntents = TrackPreferenceIntentStore()

    /**
     * Publishes the latest explicit intent synchronously, before any disk work. The returned
     * snapshot is immediately usable by every playback entry path through [trackPreferenceIntent].
     */
    internal fun publishTrackPreferenceIntent(
        expectedAccountId: String,
        expectedProfileId: String,
        request: PlaybackRequest,
        audioPreference: String? = null,
        subtitlePreference: String? = null,
    ): TrackPreferenceIntent? {
        if (request.isLive) return null
        if (expectedAccountId.isBlank() || expectedProfileId.isBlank()) return null
        if (accountScope.activeAccountId() != expectedAccountId) return null
        if (profileStore.activeProfileId() != expectedProfileId) return null
        if (audioPreference == null && subtitlePreference == null) return null
        return trackPreferenceIntents.publish(
            accountId = expectedAccountId,
            profileId = expectedProfileId,
            historyKey = request.historyKey,
            audioPreference = audioPreference,
            subtitlePreference = subtitlePreference,
        )
    }

    internal fun trackPreferenceIntent(
        accountId: String,
        profileId: String,
        historyKey: String,
    ): TrackPreferenceIntent? = trackPreferenceIntents.snapshot(accountId, profileId, historyKey)

    internal fun clearTrackPreferenceIntents(
        accountId: String,
        profileId: String,
        historyKey: String? = null,
    ) {
        trackPreferenceIntents.clear(accountId, profileId, historyKey)
    }

    internal fun beginProgressMutation(): UserLibraryHistoryMutationGate.ProgressAttempt =
        historyMutationGate.beginProgress()

    internal fun currentHistoryAttempt(): UserLibraryHistoryMutationGate.HistoryAttempt =
        historyMutationGate.currentHistoryAttempt()

    internal fun beginHistoryMutation(): UserLibraryHistoryMutationGate.HistoryAttempt =
        historyMutationGate.beginHistoryMutation()

    internal fun isCurrentHistoryMutation(
        attempt: UserLibraryHistoryMutationGate.HistoryAttempt,
    ): Boolean = historyMutationGate.isCurrentHistory(attempt)

    internal fun isCurrentProgressMutation(
        attempt: UserLibraryHistoryMutationGate.ProgressAttempt,
    ): Boolean = historyMutationGate.isCurrent(attempt)

    internal fun invalidateProgressMutations() {
        historyMutationGate.invalidateProgress()
    }

    fun favorites(): Set<String> = favorites(
        preferences = preferences,
        profile = profileStore.activeProfile(),
    )

    private fun favorites(
        preferences: SharedPreferences,
        profile: UserProfile,
    ): Set<String> {
        val stored = preferences
            .getStringSet(profileKey(profile.id, KEY_FAVORITES), emptySet())
            .orEmpty()
            .toSet()
        if (profile.kind != ProfileKind.KIDS) return stored
        val allowed = kidsContentFilterStore.allowedKeys()
        return stored.filterTo(linkedSetOf()) { it in allowed }
    }

    fun toggle(item: ContentItem): Set<String> {
        if (isActiveKidsProfile() && !kidsContentFilterStore.isAllowed(item)) return favorites()
        val key = keyFor(item)
        val updated = favorites().toMutableSet().apply {
            if (!add(key)) remove(key)
        }
        preferences.edit().putStringSet(activeKey(KEY_FAVORITES), updated).apply()
        return updated
    }

    fun replaceFavorites(favorites: Set<String>) {
        val safeFavorites = if (isActiveKidsProfile()) {
            val allowed = kidsContentFilterStore.allowedKeys()
            favorites.filterTo(linkedSetOf()) { it in allowed }
        } else {
            favorites.toSet()
        }
        preferences.edit().putStringSet(activeKey(KEY_FAVORITES), safeFavorites).apply()
    }

    /**
     * Owner-bound favorite toggle used by the production ViewModel. The persisted value and the
     * returned set are derived here, after Kids filtering, so callers never promote a requested
     * set over the authoritative safe result.
     */
    internal fun toggleFavoriteForOwner(
        expectedOwner: AuthenticatedSessionOwner,
        expectedAccountId: String,
        expectedProfileId: String,
        item: ContentItem,
    ): FavoriteMutationOutcome {
        val key = keyFor(item)
        synchronized(favoritesMutationLock) {
            if (!favoriteOwnerMatches(expectedOwner, expectedAccountId, expectedProfileId)) {
                return FavoriteMutationOutcome(FavoriteMutationStatus.OWNER_CHANGED, key)
            }
            val profile = profileStore.activeProfile()
            if (profile.id != expectedProfileId) {
                return FavoriteMutationOutcome(FavoriteMutationStatus.OWNER_CHANGED, key)
            }
            val accountPreferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
            val current = favorites(accountPreferences, profile)
            if (profile.kind == ProfileKind.KIDS && !kidsContentFilterStore.isAllowed(item)) {
                return if (favoriteOwnerMatches(expectedOwner, expectedAccountId, expectedProfileId)) {
                    FavoriteMutationOutcome(
                        status = FavoriteMutationStatus.KIDS_REJECTED,
                        key = key,
                        favorites = current,
                    )
                } else {
                    FavoriteMutationOutcome(FavoriteMutationStatus.OWNER_CHANGED, key)
                }
            }
            val target = if (key in current) {
                current - key
            } else {
                current + key
            }
            if (target == current) {
                return FavoriteMutationOutcome(
                    status = FavoriteMutationStatus.UNCHANGED,
                    key = key,
                    favorites = current,
                )
            }
            if (!favoriteOwnerMatches(expectedOwner, expectedAccountId, expectedProfileId)) {
                return FavoriteMutationOutcome(FavoriteMutationStatus.OWNER_CHANGED, key)
            }
            val storageKey = profileKey(profile.id, KEY_FAVORITES)
            val persisted = accountPreferences.edit()
                .putStringSet(storageKey, target)
                .commit()
            if (!persisted) {
                // Do not publish a false applied heart when the durable write failed.
                accountPreferences.edit().putStringSet(storageKey, current).commit()
                return FavoriteMutationOutcome(
                    status = FavoriteMutationStatus.PERSISTENCE_FAILED,
                    key = key,
                    favorites = current,
                )
            }
            return FavoriteMutationOutcome(
                status = if (key in current) {
                    FavoriteMutationStatus.APPLIED_REMOVED
                } else {
                    FavoriteMutationStatus.APPLIED_ADDED
                },
                key = key,
                favorites = target,
            )
        }
    }

    fun isFavorite(item: ContentItem, favorites: Set<String>): Boolean = keyFor(item) in favorites

    fun keyFor(item: ContentItem): String = "${item.type.name}:${item.id}"

    fun history(): List<HistoryEntry> = history(
        preferences = preferences,
        profile = profileStore.activeProfile(),
    )

    private fun history(
        preferences: SharedPreferences,
        profile: UserProfile,
        canRepair: () -> Boolean = { false },
    ): List<HistoryEntry> = readHistoryForProfile(preferences, profile, canRepair).entries

    private fun readHistoryForProfile(
        preferences: SharedPreferences,
        profile: UserProfile,
        canRepair: () -> Boolean = { true },
    ): HistoryDocumentRead {
        if (profile.id == ProfileStore.INTEGRITY_BLOCKED_PROFILE_ID) {
            return HistoryDocumentRead(
                entries = emptyList(),
                state = HistoryDocumentState.UNREADABLE,
                safeRawDocument = null,
                requiresPrivacyRewrite = false,
            )
        }
        val historyKey = profileKey(profile.id, KEY_HISTORY)
        val read = readHistoryDocument(preferences, historyKey)
        if (read.requiresPrivacyRewrite && read.safeRawDocument != null && canRepair()) {
            preferences.edit().putString(historyKey, read.safeRawDocument).commit()
        }
        if (profile.kind != ProfileKind.KIDS) return read
        val allowed = kidsContentFilterStore.allowedKeys()
        return read.copy(
            entries = read.entries.filter { entry -> isAllowedKidsHistoryEntry(allowed, entry) },
        )
    }

    /**
     * Runs the account-scoped legacy migration and initial library decode away
     * from the ViewModel constructor. The fixed account/profile owner prevents
     * a late startup result from being reused after a scope switch.
     */
    internal suspend fun initializeForProfile(
        expectedAccountId: String,
        profile: UserProfile,
    ): UserLibrarySnapshot? = runUserLibraryStartupOffMain {
        if (!matchesOwner(expectedAccountId, profile.id)) return@runUserLibraryStartupOffMain null
        // A same-profile reload must not discard an explicit audio/subtitle intent whose durable
        // write is still queued. Intents for any other account/profile are cleared while the
        // caller is still the active owner. Durable history remains the persisted source.
        trackPreferenceIntents.retainOnlyIf(expectedAccountId, profile.id) {
            matchesOwner(expectedAccountId, profile.id)
        }
        if (!matchesOwner(expectedAccountId, profile.id)) return@runUserLibraryStartupOffMain null
        val scopedPreferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
        migrateLegacyLibraryIfNeeded(scopedPreferences)
        if (!matchesOwner(expectedAccountId, profile.id)) return@runUserLibraryStartupOffMain null
        val pending = trackPreferenceIntents.snapshotFor(expectedAccountId, profile.id)
        val snapshot = UserLibrarySnapshot(
            favorites = favorites(scopedPreferences, profile),
            history = applyTrackPreferenceIntents(
                entries = history(scopedPreferences, profile) { true },
                pending = pending,
            ),
        )
        snapshot.takeIf { matchesOwner(expectedAccountId, profile.id) }
    }

    /**
     * Reconciles a live explicit choice over a reloaded durable snapshot. Null intent fields mean
     * "no choice for this setting", so the durable value is retained; an explicit OFF is the
     * non-null string "off" and wins. Entries are not added or reordered here: the durable history
     * remains the structural owner while a queued preference write is still in flight.
     */
    private fun applyTrackPreferenceIntents(
        entries: List<HistoryEntry>,
        pending: Map<String, TrackPreferenceIntent>,
    ): List<HistoryEntry> {
        if (pending.isEmpty()) return entries
        return entries.map { entry ->
            val intent = pending[entry.key] ?: return@map entry
            entry.copy(
                savedAudioPreference = intent.audioPreference ?: entry.savedAudioPreference,
                savedSubtitlePreference = intent.subtitlePreference
                    ?: entry.savedSubtitlePreference,
            )
        }
    }

    internal suspend fun recordStartForOwner(
        expectedOwner: AuthenticatedSessionOwner,
        expectedAccountId: String,
        expectedProfileId: String,
        request: PlaybackRequest,
        historyAttempt: UserLibraryHistoryMutationGate.HistoryAttempt,
    ): List<HistoryEntry>? = runUserLibraryHistoryPersistenceOffMain {
        val operationContext = currentCoroutineContext()
        fun canWrite(): Boolean =
            operationContext.isActive &&
                expectedOwner.accountId == expectedAccountId &&
                AuthenticatedSessionRegistry.isCurrent(expectedOwner) &&
                accountScope.activeAccountId() == expectedAccountId &&
                profileStore.activeProfileId() == expectedProfileId &&
                historyMutationGate.isCurrentHistory(historyAttempt)

        if (!canWrite()) return@runUserLibraryHistoryPersistenceOffMain null
        synchronized(historyPersistenceLock) {
            if (!canWrite()) return@synchronized null
            val profile = profileStore.activeProfile()
            if (profile.id != expectedProfileId) return@synchronized null
            val scopedPreferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
            val read = readHistoryForProfile(scopedPreferences, profile, ::canWrite)
            if (profile.kind == ProfileKind.KIDS && !kidsContentFilterStore.isAllowed(request)) {
                return@synchronized read.entries
            }
            val previous = read.entries.firstOrNull { it.key == request.historyKey }
            val intent = trackPreferenceIntents.snapshot(
                expectedAccountId,
                expectedProfileId,
                request.historyKey,
            )
            val entry = HistoryEntry(
                key = request.historyKey,
                title = request.title,
                posterUrl = persistableExternalUrlOrNull(request.posterUrl),
                streamKind = request.streamKind,
                streamId = request.streamId,
                extension = request.extension,
                isLive = request.isLive,
                positionMs = previous?.positionMs ?: request.resumePositionMs,
                durationMs = previous?.durationMs ?: 0L,
                updatedAtEpochMs = System.currentTimeMillis(),
                seriesTitle = request.seriesTitle ?: previous?.seriesTitle,
                season = request.season ?: previous?.season,
                episodeNumber = request.episodeNumber ?: previous?.episodeNumber,
                episodeTitle = request.episodeTitle ?: previous?.episodeTitle,
                parentContentId = request.parentContentId ?: previous?.parentContentId,
                originalLanguage = request.originalLanguage ?: previous?.originalLanguage,
                // A live explicit intent is newer than disk and the immutable playback request;
                // progress and a fresh record-start must never roll an explicit choice back.
                savedAudioPreference = intent?.audioPreference
                    ?: previous?.savedAudioPreference
                    ?: request.savedAudioPreference,
                savedSubtitlePreference = intent?.subtitlePreference
                    ?: previous?.savedSubtitlePreference
                    ?: request.savedSubtitlePreference,
            )
            val normalized = normalizeHistory(
                entries = listOf(entry) + read.entries.filterNot { it.key == entry.key },
                profile = profile,
            )
            if (!canWrite()) return@synchronized null
            if (!writeHistoryDocument(
                    preferences = scopedPreferences,
                    historyKey = profileKey(profile.id, KEY_HISTORY),
                    entries = normalized,
                    read = read,
                    canWrite = ::canWrite,
                    finalWriteLock = historyFinalWriteLock,
                )
            ) {
                null
            } else {
                normalized
            }
        }
    }

    /**
     * Persists an explicit audio/subtitle choice into this content's existing profile-scoped history
     * entry as soon as the customer commits it. It never creates a competing persistence owner.
     * A missing entry is created from the current request; an existing entry keeps its position,
     * duration, original language and the other saved preference.
     */
    internal suspend fun updateTrackPreferenceForOwner(
        expectedOwner: AuthenticatedSessionOwner,
        expectedAccountId: String,
        expectedProfileId: String,
        request: PlaybackRequest,
        audioPreference: String? = null,
        subtitlePreference: String? = null,
        audioRevision: Long? = null,
        subtitleRevision: Long? = null,
        historyAttempt: UserLibraryHistoryMutationGate.HistoryAttempt,
    ): List<HistoryEntry>? = runUserLibraryHistoryPersistenceOffMain {
        if (audioPreference == null && subtitlePreference == null) return@runUserLibraryHistoryPersistenceOffMain null
        val operationContext = currentCoroutineContext()
        fun canWrite(): Boolean =
            operationContext.isActive &&
                expectedOwner.accountId == expectedAccountId &&
                AuthenticatedSessionRegistry.isCurrent(expectedOwner) &&
                accountScope.activeAccountId() == expectedAccountId &&
                profileStore.activeProfileId() == expectedProfileId &&
                historyMutationGate.isCurrentHistory(historyAttempt)

        if (!canWrite() || request.isLive) return@runUserLibraryHistoryPersistenceOffMain null
        synchronized(historyPersistenceLock) {
            if (!canWrite()) return@synchronized null
            val profile = profileStore.activeProfile()
            if (profile.id != expectedProfileId) return@synchronized null
            val scopedPreferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
            val read = readHistoryForProfile(scopedPreferences, profile, ::canWrite)
            if (profile.kind == ProfileKind.KIDS && !kidsContentFilterStore.isAllowed(request)) {
                return@synchronized read.entries
            }
            val audioCurrent = audioPreference == null || audioRevision == null ||
                trackPreferenceIntents.isCurrent(
                    expectedAccountId,
                    expectedProfileId,
                    request.historyKey,
                    TrackPreferenceSetting.AUDIO,
                    audioRevision,
                )
            val subtitleCurrent = subtitlePreference == null || subtitleRevision == null ||
                trackPreferenceIntents.isCurrent(
                    expectedAccountId,
                    expectedProfileId,
                    request.historyKey,
                    TrackPreferenceSetting.SUBTITLE,
                    subtitleRevision,
                )
            // An older queued write must never overwrite a newer explicit intent. At least one
            // setting in this write must still be current; a stale setting is never applied.
            val anyCurrent = (audioPreference != null && audioCurrent) ||
                (subtitlePreference != null && subtitleCurrent)
            if (!anyCurrent) return@synchronized null
            val previous = read.entries.firstOrNull { it.key == request.historyKey }
            val intent = trackPreferenceIntents.snapshot(
                expectedAccountId,
                expectedProfileId,
                request.historyKey,
            )
            val resolvedAudio = intent?.audioPreference
                ?: audioPreference.takeIf { audioCurrent }
                ?: previous?.savedAudioPreference
                ?: request.savedAudioPreference
            val resolvedSubtitle = intent?.subtitlePreference
                ?: subtitlePreference.takeIf { subtitleCurrent }
                ?: previous?.savedSubtitlePreference
                ?: request.savedSubtitlePreference
            val entry = previous?.copy(
                savedAudioPreference = resolvedAudio,
                savedSubtitlePreference = resolvedSubtitle,
            ) ?: HistoryEntry(
                key = request.historyKey,
                title = request.title,
                posterUrl = persistableExternalUrlOrNull(request.posterUrl),
                streamKind = request.streamKind,
                streamId = request.streamId,
                extension = request.extension,
                isLive = request.isLive,
                positionMs = request.resumePositionMs.coerceAtLeast(0L),
                durationMs = 0L,
                updatedAtEpochMs = System.currentTimeMillis(),
                seriesTitle = request.seriesTitle,
                season = request.season,
                episodeNumber = request.episodeNumber,
                episodeTitle = request.episodeTitle,
                parentContentId = request.parentContentId,
                originalLanguage = request.originalLanguage,
                savedAudioPreference = resolvedAudio,
                savedSubtitlePreference = resolvedSubtitle,
            )
            val normalized = normalizeHistory(
                entries = listOf(entry) + read.entries.filterNot { it.key == entry.key },
                profile = profile,
            )
            if (!canWrite()) return@synchronized null
            if (!writeHistoryDocument(
                    preferences = scopedPreferences,
                    historyKey = profileKey(profile.id, KEY_HISTORY),
                    entries = normalized,
                    read = read,
                    canWrite = ::canWrite,
                    finalWriteLock = historyFinalWriteLock,
                )
            ) {
                null
            } else {
                normalized
            }
        }
    }

    /**
     * Persists one Player progress update away from the caller thread. The caller supplies its
     * monotonic attempt and this method verifies the authenticated account/profile owner again
     * immediately before each repair or write, so a cancelled or replaced owner cannot mutate
     * its old library after the switch.
     */
    internal suspend fun updateProgressForOwner(
        expectedOwner: AuthenticatedSessionOwner,
        expectedProfileId: String,
        request: PlaybackRequest,
        positionMs: Long,
        durationMs: Long,
        historyAttempt: UserLibraryHistoryMutationGate.ProgressAttempt,
        isCurrentAttempt: () -> Boolean,
    ): List<HistoryEntry>? = runUserLibraryProgressPersistenceOffMain {
        val operationContext = currentCoroutineContext()
        operationContext.ensureActive()
        fun canWrite(): Boolean = operationContext.isActive && userLibraryProgressWriteAllowed(
            attemptCurrent = isCurrentAttempt() && historyMutationGate.isCurrent(historyAttempt),
            sameAuthenticatedSession = AuthenticatedSessionRegistry.isCurrent(expectedOwner),
            expectedAccountId = expectedOwner.accountId,
            activeAccountId = accountScope.activeAccountId(),
            expectedProfileId = expectedProfileId,
            activeProfileId = profileStore.activeProfileId(),
        )

        if (!canWrite()) return@runUserLibraryProgressPersistenceOffMain null
        val result = synchronized(historyPersistenceLock) {
            if (!canWrite()) return@synchronized null
            val profile = profileStore.activeProfile()
            if (profile.id != expectedProfileId) return@synchronized null
            val scopedPreferences = accountScope.preferences(PREFERENCES_NAME, expectedOwner.accountId)
            if (!canWrite()) return@synchronized null
            val currentHistoryRead = readHistoryForProfile(
                preferences = scopedPreferences,
                profile = profile,
                canRepair = ::canWrite,
            )
            val currentHistory = currentHistoryRead.entries
            if (!canWrite()) return@synchronized null
            if (profile.kind == ProfileKind.KIDS && !kidsContentFilterStore.isAllowed(request)) {
                return@synchronized currentHistory
            }
            val previous = currentHistory.firstOrNull { it.key == request.historyKey }
            val intent = trackPreferenceIntents.snapshot(
                expectedOwner.accountId,
                expectedProfileId,
                request.historyKey,
            )
            val entry = HistoryEntry(
                key = request.historyKey,
                title = request.title,
                posterUrl = persistableExternalUrlOrNull(request.posterUrl),
                streamKind = request.streamKind,
                streamId = request.streamId,
                extension = request.extension,
                isLive = request.isLive,
                positionMs = if (request.isLive) 0L else positionMs.coerceAtLeast(0L),
                durationMs = if (request.isLive) 0L else durationMs.coerceAtLeast(0L),
                updatedAtEpochMs = System.currentTimeMillis(),
                seriesTitle = request.seriesTitle ?: previous?.seriesTitle,
                season = request.season ?: previous?.season,
                episodeNumber = request.episodeNumber ?: previous?.episodeNumber,
                episodeTitle = request.episodeTitle ?: previous?.episodeTitle,
                parentContentId = request.parentContentId ?: previous?.parentContentId,
                originalLanguage = request.originalLanguage ?: previous?.originalLanguage,
                // A live explicit intent is newer than disk and the immutable playback request;
                // progress and a fresh record-start must never roll an explicit choice back.
                savedAudioPreference = intent?.audioPreference
                    ?: previous?.savedAudioPreference
                    ?: request.savedAudioPreference,
                savedSubtitlePreference = intent?.subtitlePreference
                    ?: previous?.savedSubtitlePreference
                    ?: request.savedSubtitlePreference,
            )
            val normalized = normalizeHistory(
                entries = listOf(entry) + currentHistory.filterNot { it.key == entry.key },
                profile = profile,
            )
            if (!canWrite()) return@synchronized null
            historyMutationGate.writeIfCurrent(historyAttempt) {
                if (!canWrite()) return@writeIfCurrent null
                if (!writeHistoryDocument(
                        preferences = scopedPreferences,
                        historyKey = profileKey(profile.id, KEY_HISTORY),
                        entries = normalized,
                        read = currentHistoryRead,
                        canWrite = ::canWrite,
                        finalWriteLock = historyFinalWriteLock,
                    )
                ) {
                    null
                } else {
                    normalized
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (!canWrite()) null else result
    }

    internal suspend fun removeHistoryForOwner(
        expectedOwner: AuthenticatedSessionOwner,
        expectedAccountId: String,
        expectedProfileId: String,
        key: String,
        historyAttempt: UserLibraryHistoryMutationGate.HistoryAttempt,
    ): List<HistoryEntry>? = runUserLibraryHistoryPersistenceOffMain {
        val operationContext = currentCoroutineContext()
        fun canWrite(): Boolean =
            operationContext.isActive &&
                expectedOwner.accountId == expectedAccountId &&
                AuthenticatedSessionRegistry.isCurrent(expectedOwner) &&
                accountScope.activeAccountId() == expectedAccountId &&
                profileStore.activeProfileId() == expectedProfileId &&
                historyMutationGate.isCurrentHistory(historyAttempt)

        if (!canWrite()) return@runUserLibraryHistoryPersistenceOffMain null
        synchronized(historyPersistenceLock) {
            if (!canWrite()) return@synchronized null
            val profile = profileStore.activeProfile()
            if (profile.id != expectedProfileId) return@synchronized null
            val scopedPreferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
            val read = readHistoryForProfile(scopedPreferences, profile, ::canWrite)
            if (read.entries.none { it.key == key }) return@synchronized read.entries
            val normalized = normalizeHistory(read.entries.filterNot { it.key == key }, profile)
            if (!writeHistoryDocument(
                    preferences = scopedPreferences,
                    historyKey = profileKey(profile.id, KEY_HISTORY),
                    entries = normalized,
                    read = read,
                    canWrite = ::canWrite,
                    finalWriteLock = historyFinalWriteLock,
                )
            ) {
                null
            } else {
                trackPreferenceIntents.clear(expectedAccountId, expectedProfileId, key)
                normalized
            }
        }
    }

    fun resumePosition(key: String): Long {
        return resumePositionForHistory(history(), key)
    }

    fun clearHistory(): List<HistoryEntry> {
        historyMutationGate.beginHistoryMutation()
        val historyKey = activeKey(KEY_HISTORY)
        synchronized(historyFinalWriteLock) {
            preferences.edit().remove(historyKey).apply()
        }
        trackPreferenceIntents.clearAll()
        return emptyList()
    }

    private fun normalizeHistory(
        entries: List<HistoryEntry>,
        profile: UserProfile,
    ): List<HistoryEntry> {
        val scopedEntries = if (profile.kind == ProfileKind.KIDS) {
            val allowed = kidsContentFilterStore.allowedKeys()
            entries.filter { entry -> isAllowedKidsHistoryEntry(allowed, entry) }
        } else {
            entries
        }
        val normalized = scopedEntries
            .map { entry -> entry.copy(posterUrl = persistableExternalUrlOrNull(entry.posterUrl)) }
            .distinctBy(HistoryEntry::key)
            .sortedByDescending(HistoryEntry::updatedAtEpochMs)
            .take(MAX_HISTORY)
        return normalized
    }

    private fun isActiveKidsProfile(): Boolean =
        profileStore.activeProfile().kind == ProfileKind.KIDS

    private fun activeKey(baseKey: String): String =
        profileKey(profileStore.activeProfileId(), baseKey)

    private fun profileKey(profileId: String, baseKey: String): String =
        "profile:$profileId:$baseKey"

    private fun matchesOwner(expectedAccountId: String, expectedProfileId: String): Boolean =
        accountScope.activeAccountId() == expectedAccountId &&
            profileStore.activeProfileId() == expectedProfileId

    private fun favoriteOwnerMatches(
        expectedOwner: AuthenticatedSessionOwner,
        expectedAccountId: String,
        expectedProfileId: String,
    ): Boolean =
        expectedAccountId.isNotBlank() &&
            expectedProfileId.isNotBlank() &&
            expectedAccountId == expectedOwner.accountId &&
            AuthenticatedSessionRegistry.isCurrent(expectedOwner) &&
            accountScope.activeAccountId() == expectedAccountId &&
            profileStore.activeProfileId() == expectedProfileId

    private fun migrateLegacyLibraryIfNeeded(preferences: SharedPreferences) {
        if (hasUnsafeLegacyMigrationMarker(preferences, USER_LIBRARY_PREFERENCES_BASE)) return
        val primaryProfileId = ProfileStore.PRIMARY_PROFILE_ID
        val scopedFavoritesKey = profileKey(primaryProfileId, KEY_FAVORITES)
        val scopedHistoryKey = profileKey(primaryProfileId, KEY_HISTORY)
        val plan = profileLibraryMigrationPlan(
            migrationAlreadyComplete = preferences.getBoolean(KEY_PROFILE_SCOPE_MIGRATION_V1, false),
            scopedFavoritesExist = preferences.contains(scopedFavoritesKey),
            legacyFavoritesExist = preferences.contains(KEY_FAVORITES),
            scopedHistoryExists = preferences.contains(scopedHistoryKey),
            legacyHistoryExists = preferences.contains(KEY_HISTORY),
        )
        if (!plan.markMigrationComplete) return

        val editor = preferences.edit()
        if (plan.copyLegacyFavorites) {
            editor.putStringSet(
                scopedFavoritesKey,
                preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet(),
            )
        }
        if (preferences.contains(KEY_HISTORY)) {
            preferences.getString(KEY_HISTORY, null)?.let { rawHistory ->
                val safeToCopy = isStructurallyValidHistoryDocument(rawHistory) &&
                    !containsCredentialBearingIptvMaterial(rawHistory)
                if (safeToCopy) {
                    val safeHistory = sanitizeHistoryDocumentJson(rawHistory)
                    if (plan.copyLegacyHistory) editor.putString(scopedHistoryKey, safeHistory)
                    if (safeHistory != rawHistory) editor.putString(KEY_HISTORY, safeHistory)
                } else {
                    // Keep the raw legacy source untouched, but prevent this scoped
                    // store from treating the abandoned history as fresh/writable.
                    editor.putBoolean(
                        unsafeLegacyMigrationMarkerKey(USER_LIBRARY_PREFERENCES_BASE),
                        true,
                    )
                }
            }
        }

        // Keep non-sensitive legacy keys intact as rollback safety.
        editor.putBoolean(KEY_PROFILE_SCOPE_MIGRATION_V1, true).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "hulk_user_library"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HISTORY = "history"
        const val KEY_PROFILE_SCOPE_MIGRATION_V1 = "profile_scope_migration_v1"
        const val MAX_HISTORY = 100
    }
}

private const val COMPLETED_RATIO = .92
