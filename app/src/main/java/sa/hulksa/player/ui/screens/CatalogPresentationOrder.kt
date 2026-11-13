package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.ContentType

/**
 * Stable mixed-content presentation order: every Movie/Series item keeps its relative order
 * before every Live channel, and channels keep theirs. Single-type lists are returned unchanged.
 *
 * This is presentation-only. It never rewrites favorite/history storage or search rank; callers
 * derive their visible keys and indices from the returned list.
 */
internal fun <T> orderMixedContentVodFirst(
    items: List<T>,
    contentType: (T) -> ContentType,
): List<T> {
    if (items.size < 2) return items
    val vod = ArrayList<T>(items.size)
    val live = ArrayList<T>()
    var sawLive = false
    var sawVod = false
    items.forEach { item ->
        if (contentType(item) == ContentType.LIVE) {
            sawLive = true
            live += item
        } else {
            sawVod = true
            vod += item
        }
    }
    return if (!sawLive || !sawVod) items else vod + live
}
