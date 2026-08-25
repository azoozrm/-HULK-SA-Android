package sa.hulksa.player

import sa.hulksa.player.model.ContentType

/**
 * Owns the logical lifetime of details requests. A new request or explicit
 * invalidation makes every older completion stale, even when the underlying
 * network call cannot stop immediately.
 */
internal class DetailsRequestGate {
    data class Key(
        val type: ContentType,
        val contentId: Int,
        val profileId: String,
    )

    data class Token(
        val generation: Long,
        val key: Key,
    )

    private var generation: Long = 0L
    private var active: Token? = null

    @Synchronized
    fun begin(key: Key): Token {
        generation += 1L
        return Token(generation = generation, key = key).also { active = it }
    }

    @Synchronized
    fun isCurrent(token: Token): Boolean = active == token

    @Synchronized
    fun invalidate() {
        generation += 1L
        active = null
    }
}
