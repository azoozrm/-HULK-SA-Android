package sa.hulksa.player.data

import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.PlaybackRequest

/**
 * A second Live source resolved from the exact authenticated account that owns the current
 * playback session. Candidate URLs stay inside this object and are never logged.
 */
internal data class ArabicCommentarySource(
    val owner: AuthenticatedSessionOwner,
    val request: PlaybackRequest,
    val activeConnections: Int,
    val maxConnections: Int,
)

/**
 * Resolves the commentary channel through the existing Xtream playback contract. This performs no
 * network request and introduces no new credential path: it reuses the process-only authenticated
 * session that the current player already belongs to.
 */
internal fun resolveArabicCommentarySource(channel: ContentItem): ArabicCommentarySource? {
    if (channel.type != ContentType.LIVE) return null
    val owner = AuthenticatedSessionRegistry.currentOwner() ?: return null
    val session = owner.session
    val request = runCatching {
        XtreamClient().playback(session, channel)
    }.getOrNull() ?: return null
    if (!request.isLive || request.candidates.isEmpty()) return null
    return ArabicCommentarySource(
        owner = owner,
        request = request,
        activeConnections = session.account.activeConnections,
        maxConnections = session.account.maxConnections,
    )
}
