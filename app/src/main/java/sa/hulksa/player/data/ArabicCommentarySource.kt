package sa.hulksa.player.data

import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.PlaybackRequest

/**
 * Resolves the commentary channel through the existing Xtream playback contract. This performs no
 * network request. A retired playback owner cannot resolve a stream with a different account.
 */
internal fun resolveArabicCommentarySource(
    owner: AuthenticatedSessionOwner,
    channel: ContentItem,
): PlaybackRequest? {
    if (channel.type != ContentType.LIVE) return null
    return AuthenticatedSessionRegistry.withCurrentOwner(owner) {
        runCatching { XtreamClient().playback(owner.session, channel) }
            .getOrNull()
            ?.takeIf { it.isLive && it.candidates.isNotEmpty() }
    }
}
