package sa.hulksa.player.data

internal enum class TrackPreferenceSetting { AUDIO, SUBTITLE }

/**
 * The latest explicit audio/subtitle intent for one account/profile/content, published
 * synchronously before any disk IO. It is a live intent overlay owned by [UserLibrary], not a
 * second history authority: durable history remains the only persisted record.
 */
internal data class TrackPreferenceIntent(
    val audioPreference: String?,
    val subtitlePreference: String?,
    val audioRevision: Long,
    val subtitleRevision: Long,
)

internal class TrackPreferenceIntentStore {
    private data class Key(
        val accountId: String,
        val profileId: String,
        val historyKey: String,
    )

    private data class Entry(
        val audioPreference: String?,
        val subtitlePreference: String?,
        val audioRevision: Long,
        val subtitleRevision: Long,
    ) {
        fun toIntent(): TrackPreferenceIntent = TrackPreferenceIntent(
            audioPreference = audioPreference,
            subtitlePreference = subtitlePreference,
            audioRevision = audioRevision,
            subtitleRevision = subtitleRevision,
        )
    }

    private val lock = Any()
    private var nextRevision = 0L
    private val entries = mutableMapOf<Key, Entry>()

    fun publish(
        accountId: String,
        profileId: String,
        historyKey: String,
        audioPreference: String? = null,
        subtitlePreference: String? = null,
    ): TrackPreferenceIntent = synchronized(lock) {
        val key = Key(accountId, profileId, historyKey)
        val current = entries[key] ?: Entry(
            audioPreference = null,
            subtitlePreference = null,
            audioRevision = 0L,
            subtitleRevision = 0L,
        )
        val revision = ++nextRevision
        val updated = current.copy(
            audioPreference = audioPreference ?: current.audioPreference,
            subtitlePreference = subtitlePreference ?: current.subtitlePreference,
            audioRevision = if (audioPreference != null) revision else current.audioRevision,
            subtitleRevision = if (subtitlePreference != null) {
                revision
            } else {
                current.subtitleRevision
            },
        )
        entries[key] = updated
        updated.toIntent()
    }

    fun snapshot(
        accountId: String,
        profileId: String,
        historyKey: String,
    ): TrackPreferenceIntent? = synchronized(lock) {
        entries[Key(accountId, profileId, historyKey)]?.toIntent()
    }

    /**
     * Every live explicit intent for one account/profile. A same-profile library reload reconciles
     * these overlays into the durable snapshot so a queued write cannot be lost when the store is
     * re-read. Other account/profile keys are removed only while the caller still owns the active
     * scope; the check runs under this store lock so a newer scope cannot publish into a purge.
     */
    fun snapshotFor(
        accountId: String,
        profileId: String,
    ): Map<String, TrackPreferenceIntent> = synchronized(lock) {
        entries.entries
            .asSequence()
            .filter { (key, _) -> key.accountId == accountId && key.profileId == profileId }
            .associate { (key, entry) -> key.historyKey to entry.toIntent() }
    }

    fun retainOnlyIf(
        accountId: String,
        profileId: String,
        canRetain: () -> Boolean,
    ) = synchronized(lock) {
        if (!canRetain()) return@synchronized
        entries.keys.removeAll { key ->
            key.accountId != accountId || key.profileId != profileId
        }
    }

    fun isCurrent(
        accountId: String,
        profileId: String,
        historyKey: String,
        setting: TrackPreferenceSetting,
        revision: Long,
    ): Boolean = synchronized(lock) {
        val entry = entries[Key(accountId, profileId, historyKey)] ?: return false
        when (setting) {
            TrackPreferenceSetting.AUDIO -> entry.audioRevision == revision
            TrackPreferenceSetting.SUBTITLE -> entry.subtitleRevision == revision
        }
    }

    fun clear(
        accountId: String,
        profileId: String,
        historyKey: String? = null,
    ) = synchronized(lock) {
        if (historyKey != null) {
            entries.remove(Key(accountId, profileId, historyKey))
        } else {
            entries.keys.removeAll { key ->
                key.accountId == accountId && key.profileId == profileId
            }
        }
    }

    fun clearAll() = synchronized(lock) {
        entries.clear()
    }
}
