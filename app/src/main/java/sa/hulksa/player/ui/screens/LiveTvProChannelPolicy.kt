package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.ContentItem

/**
 * v1.6 Live TV Pro channel-navigation policy.
 *
 * Keeps zapping deterministic and category-aware while also providing the data needed for the
 * familiar "last channel" action. UI wiring stays separate from this pure policy so TV remote
 * focus and playback behavior can be qualified independently.
 */
internal fun liveTvProChannelSequence(
    channels: List<ContentItem>,
    currentStreamId: Int,
): List<ContentItem> {
    if (channels.isEmpty()) return emptyList()
    val current = channels.firstOrNull { it.id == currentStreamId } ?: return channels
    val sameCategory = channels.filter { it.categoryId == current.categoryId }
    return sameCategory.takeIf { it.size > 1 } ?: channels
}

internal fun liveTvProRelativeChannel(
    channels: List<ContentItem>,
    currentStreamId: Int,
    delta: Int,
): ContentItem? {
    val sequence = liveTvProChannelSequence(channels, currentStreamId)
    if (sequence.isEmpty()) return null
    val currentIndex = sequence.indexOfFirst { it.id == currentStreamId }.takeIf { it >= 0 } ?: 0
    val targetIndex = (((currentIndex + delta) % sequence.size) + sequence.size) % sequence.size
    return sequence[targetIndex]
}

internal fun liveTvProUpdateRecentChannelIds(
    existingIds: List<Int>,
    currentStreamId: Int,
    limit: Int = 60,
): List<Int> {
    if (limit <= 0) return emptyList()
    return (listOf(currentStreamId) + existingIds.filterNot { it == currentStreamId })
        .distinct()
        .take(limit)
}

internal fun liveTvProLastChannel(
    channels: List<ContentItem>,
    recentIds: List<Int>,
    currentStreamId: Int,
): ContentItem? {
    if (channels.isEmpty()) return null
    val availableById = channels.associateBy(ContentItem::id)
    return recentIds
        .asSequence()
        .filterNot { it == currentStreamId }
        .mapNotNull(availableById::get)
        .firstOrNull()
}
