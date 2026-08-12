package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PlaybackRequest

internal fun kidsContentKey(type: ContentType, id: Int): String = "${type.name}:$id"

internal fun verifiedKidsContentKeys(snapshot: VerifiedKidsCatalogSnapshot): Set<String> =
    ContentType.entries
        .asSequence()
        .flatMap { type -> snapshot.catalog(type).items.asSequence() }
        .map { item -> kidsContentKey(item.type, item.id) }
        .toCollection(linkedSetOf())

internal fun isAllowedKidsItem(
    allowedKeys: Set<String>,
    item: ContentItem,
): Boolean = kidsContentKey(item.type, item.id) in allowedKeys

internal fun isAllowedKidsHistoryEntry(
    allowedKeys: Set<String>,
    entry: HistoryEntry,
): Boolean = when (entry.streamKind.trim().lowercase()) {
    "live" ->
        entry.key == kidsContentKey(ContentType.LIVE, entry.streamId) &&
            entry.key in allowedKeys
    "movie" ->
        entry.key == kidsContentKey(ContentType.MOVIE, entry.streamId) &&
            entry.key in allowedKeys
    "series" -> entry.parentContentId
        ?.let { parentId -> kidsContentKey(ContentType.SERIES, parentId) in allowedKeys }
        ?: false
    else -> false
}

internal fun isAllowedKidsPlaybackRequest(
    allowedKeys: Set<String>,
    request: PlaybackRequest,
): Boolean = when (request.streamKind.trim().lowercase()) {
    "live" -> kidsContentKey(ContentType.LIVE, request.streamId) in allowedKeys
    "movie" -> kidsContentKey(ContentType.MOVIE, request.streamId) in allowedKeys
    "series" -> request.parentContentId
        ?.let { parentId -> kidsContentKey(ContentType.SERIES, parentId) in allowedKeys }
        ?: false
    else -> false
}

/**
 * Account-scoped, fail-closed allow-list for Kids content.
 *
 * The verified server snapshot remains the source of truth. This store exists so profile-owned
 * library surfaces (My List, history and Continue Watching) can enforce the same verified scope
 * even when they are reconstructed outside the Kids composable tree or after a process restart.
 */
class KidsContentFilterStore(context: Context) {
    private val accountScope = AccountScopeStore(context.applicationContext)
    private val preferences: SharedPreferences
        get() = accountScope.preferences(PREFERENCES_NAME)

    @Synchronized
    fun replace(snapshot: VerifiedKidsCatalogSnapshot): Boolean {
        val allowed = verifiedKidsContentKeys(snapshot)
        val committed = preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putBoolean(KEY_SNAPSHOT_VERIFIED, true)
            .putStringSet(KEY_ALLOWED_CONTENT, allowed)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .commit()
        if (!committed) {
            // Never keep a stale allow-list if the new verified scope could not be persisted.
            preferences.edit().clear().commit()
        }
        return committed
    }

    fun allowedKeys(): Set<String> {
        if (!preferences.getBoolean(KEY_SNAPSHOT_VERIFIED, false)) return emptySet()
        return preferences.getStringSet(KEY_ALLOWED_CONTENT, emptySet())
            .orEmpty()
            .toSet()
    }

    fun isAllowedKey(key: String): Boolean = key in allowedKeys()

    fun isAllowed(item: ContentItem): Boolean = isAllowedKidsItem(allowedKeys(), item)

    fun isAllowed(entry: HistoryEntry): Boolean = isAllowedKidsHistoryEntry(allowedKeys(), entry)

    fun isAllowed(request: PlaybackRequest): Boolean =
        isAllowedKidsPlaybackRequest(allowedKeys(), request)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val PREFERENCES_NAME = "hulk_kids_content_filter_v1"
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_SNAPSHOT_VERIFIED = "snapshot_verified"
        private const val KEY_ALLOWED_CONTENT = "allowed_content_keys"
        private const val KEY_UPDATED_AT = "updated_at_epoch_ms"
    }
}
