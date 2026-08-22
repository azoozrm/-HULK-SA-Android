package sa.hulksa.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class LocalNotificationKind {
    NEW_EPISODE,
    SYSTEM_MESSAGE,
}

data class LocalSystemNotification(
    val id: String,
    val messageId: String,
    val title: String,
    val message: String,
    val severity: OperationsAnnouncementSeverity,
    val createdAtEpochMs: Long,
    val read: Boolean,
)

sealed interface LocalNotificationItem {
    val id: String
    val createdAtEpochMs: Long
    val read: Boolean
    val kind: LocalNotificationKind

    data class Episode(val notification: LocalEpisodeNotification) : LocalNotificationItem {
        override val id: String = notification.id
        override val createdAtEpochMs: Long = notification.createdAtEpochMs
        override val read: Boolean = notification.read
        override val kind: LocalNotificationKind = LocalNotificationKind.NEW_EPISODE
    }

    data class System(val notification: LocalSystemNotification) : LocalNotificationItem {
        override val id: String = notification.id
        override val createdAtEpochMs: Long = notification.createdAtEpochMs
        override val read: Boolean = notification.read
        override val kind: LocalNotificationKind = LocalNotificationKind.SYSTEM_MESSAGE
    }
}

fun mergeNotificationCenterItems(
    episodeNotifications: List<LocalEpisodeNotification>,
    systemNotifications: List<LocalSystemNotification>,
    limit: Int = 200,
): List<LocalNotificationItem> = buildList {
    addAll(episodeNotifications.map(LocalNotificationItem::Episode))
    addAll(systemNotifications.map(LocalNotificationItem::System))
}.distinctBy(LocalNotificationItem::id)
    .sortedByDescending(LocalNotificationItem::createdAtEpochMs)
    .take(limit.coerceAtLeast(0))

class OperationsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    fun cachedConfig(): CachedOperationsConfig? = synchronized(lock) {
        val rawJson = preferences.getString(KEY_CONFIG_JSON, null) ?: return@synchronized null
        val fetchedAt = preferences.getLong(KEY_CONFIG_FETCHED_AT, 0L)
        if (fetchedAt <= 0L) return@synchronized null
        parseOperationsConfig(rawJson)?.let { parsed ->
            CachedOperationsConfig(parsed, rawJson, fetchedAt)
        }
    }

    fun saveConfig(rawJson: String, fetchedAtEpochMs: Long): Boolean = synchronized(lock) {
        if (fetchedAtEpochMs <= 0L || parseOperationsConfig(rawJson) == null) {
            return@synchronized false
        }
        preferences.edit()
            .putString(KEY_CONFIG_JSON, rawJson)
            .putLong(KEY_CONFIG_FETCHED_AT, fetchedAtEpochMs)
            .commit()
    }

    fun acknowledgedMessageIds(): Set<String> = synchronized(lock) {
        readStringSet(KEY_ACKNOWLEDGED_MESSAGES)
    }

    fun acknowledgeMessage(messageId: String): Boolean = synchronized(lock) {
        if (messageId.isBlank()) return@synchronized false
        val updated = readStringSet(KEY_ACKNOWLEDGED_MESSAGES) + messageId
        writeStringSet(KEY_ACKNOWLEDGED_MESSAGES, updated)
    }

    fun systemNotifications(): List<LocalSystemNotification> = synchronized(lock) {
        readSystemNotifications()
    }

    fun recordImportantAnnouncements(
        announcements: List<OperationsAnnouncement>,
        generatedAtEpochSeconds: Long,
    ): Boolean = synchronized(lock) {
        val existing = readSystemNotifications().associateByTo(linkedMapOf(), LocalSystemNotification::messageId)
        val recordedMessageIds = readStringSet(KEY_RECORDED_SYSTEM_MESSAGES).toMutableSet()
        announcements.asSequence()
            .filter(OperationsAnnouncement::enabled)
            .filter { it.severity == OperationsAnnouncementSeverity.IMPORTANT }
            .forEach { announcement ->
                if (announcement.id !in recordedMessageIds) {
                    existing[announcement.id] = LocalSystemNotification(
                        id = "operations:${announcement.id}",
                        messageId = announcement.id,
                        title = announcement.title,
                        message = announcement.message.take(MAX_SYSTEM_MESSAGE_LENGTH),
                        severity = announcement.severity,
                        createdAtEpochMs = generatedAtEpochSeconds.coerceAtLeast(0L) * 1_000L,
                        read = false,
                    )
                    recordedMessageIds += announcement.id
                }
            }
        val notificationsSaved = writeSystemNotifications(
            existing.values
                .sortedByDescending(LocalSystemNotification::createdAtEpochMs)
                .take(MAX_SYSTEM_NOTIFICATIONS),
        )
        notificationsSaved && writeStringSet(KEY_RECORDED_SYSTEM_MESSAGES, recordedMessageIds)
    }

    fun markSystemNotificationRead(notificationId: String): Boolean = synchronized(lock) {
        val current = readSystemNotifications()
        if (current.none { it.id == notificationId }) return@synchronized false
        writeSystemNotifications(
            current.map { item ->
                if (item.id == notificationId) item.copy(read = true) else item
            },
        )
    }

    fun deleteSystemNotification(notificationId: String): Boolean = synchronized(lock) {
        val current = readSystemNotifications()
        val updated = current.filterNot { it.id == notificationId }
        if (updated.size == current.size) return@synchronized false
        writeSystemNotifications(updated)
    }

    fun markAllSystemNotificationsRead(): Boolean = synchronized(lock) {
        writeSystemNotifications(readSystemNotifications().map { it.copy(read = true) })
    }

    fun clearSystemNotifications(): Boolean = synchronized(lock) {
        preferences.edit().remove(KEY_SYSTEM_NOTIFICATIONS).commit()
    }

    private fun readStringSet(key: String): Set<String> {
        val encoded = preferences.getString(key, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(encoded)
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun writeStringSet(key: String, values: Set<String>): Boolean {
        val encoded = JSONArray().apply { values.sorted().forEach(::put) }.toString()
        return preferences.edit().putString(key, encoded).commit()
    }

    private fun readSystemNotifications(): List<LocalSystemNotification> {
        val encoded = preferences.getString(KEY_SYSTEM_NOTIFICATIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    val id = value.optString("id").trim()
                    val messageId = value.optString("messageId").trim()
                    val title = value.optString("title").trim()
                    val message = value.optString("message").trim()
                    val severity = runCatching {
                        OperationsAnnouncementSeverity.valueOf(value.optString("severity"))
                    }.getOrNull()
                    if (
                        id.isBlank() ||
                        messageId.isBlank() ||
                        title.isBlank() ||
                        message.isBlank() ||
                        severity == null
                    ) continue
                    add(
                        LocalSystemNotification(
                            id = id,
                            messageId = messageId,
                            title = title,
                            message = message,
                            severity = severity,
                            createdAtEpochMs = value.optLong("createdAtEpochMs", 0L).coerceAtLeast(0L),
                            read = value.optBoolean("read", false),
                        ),
                    )
                }
            }.distinctBy(LocalSystemNotification::id)
                .sortedByDescending(LocalSystemNotification::createdAtEpochMs)
                .take(MAX_SYSTEM_NOTIFICATIONS)
        }.getOrDefault(emptyList())
    }

    private fun writeSystemNotifications(values: List<LocalSystemNotification>): Boolean {
        val encoded = JSONArray().apply {
            values.forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .put("messageId", item.messageId)
                        .put("title", item.title)
                        .put("message", item.message)
                        .put("severity", item.severity.name)
                        .put("createdAtEpochMs", item.createdAtEpochMs)
                        .put("read", item.read),
                )
            }
        }.toString()
        return preferences.edit().putString(KEY_SYSTEM_NOTIFICATIONS, encoded).commit()
    }

    companion object {
        private const val PREFERENCES_NAME = "hulk_operations_v1"
        private const val KEY_CONFIG_JSON = "config_json"
        private const val KEY_CONFIG_FETCHED_AT = "config_fetched_at"
        private const val KEY_ACKNOWLEDGED_MESSAGES = "acknowledged_messages"
        private const val KEY_SYSTEM_NOTIFICATIONS = "system_notifications"
        private const val KEY_RECORDED_SYSTEM_MESSAGES = "recorded_system_messages"
        private const val MAX_SYSTEM_NOTIFICATIONS = 100
        private const val MAX_SYSTEM_MESSAGE_LENGTH = 2_000
    }
}
