package sa.hulksa.player.data

/**
 * Monotonic request ownership for the optional EPG load.
 *
 * A result is publishable only while its exact token is current and its account/provider scope
 * still matches the authenticated owner. Logout, a new account, or a changed portal/source
 * invalidates the token, so an older in-flight XMLTV result can never paint the new session's Live
 * surface.
 */
internal class EpgLoadGate {
    internal class Token internal constructor(
        val id: Long,
        val scope: EpgScope,
    )

    private var nextId = 0L
    private var currentToken: Token? = null

    @Synchronized
    fun begin(scope: EpgScope): Token {
        nextId += 1L
        return Token(nextId, scope).also { currentToken = it }
    }

    @Synchronized
    fun isCurrent(token: Token, activeScope: EpgScope?): Boolean =
        currentToken === token && activeScope == token.scope

    @Synchronized
    fun invalidate() {
        currentToken = null
    }
}
