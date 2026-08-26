package sa.hulksa.player

/**
 * Process-local proof that the current account/session was established by an explicit Login submit.
 * Nothing in this state is persisted, timestamp-based, or derived from remembered credentials.
 */
internal data class ManualAuthSessionOwner(
    val accountId: String,
    val sessionId: String,
)

internal class ManualParentAuthProofTracker {
    private var manualLoginPreflight: Boolean = false
    private var manualAuthenticationInFlight: Boolean = false
    private var currentOwner: ManualAuthSessionOwner? = null
    private var proofOwner: ManualAuthSessionOwner? = null

    @Synchronized
    fun noteManualLoginPreflight() {
        manualLoginPreflight = true
        // Starting new authentication ownership invalidates any older proof immediately.
        proofOwner = null
    }

    @Synchronized
    fun beginAuthenticationAttempt() {
        manualAuthenticationInFlight = manualLoginPreflight
        manualLoginPreflight = false
        // A new authentication attempt must never inherit proof from an older session.
        proofOwner = null
    }

    @Synchronized
    fun completeAuthenticationSuccess(accountId: String, sessionId: String) {
        val owner = ownerOrNull(accountId, sessionId)
        currentOwner = owner
        proofOwner = if (manualAuthenticationInFlight) owner else null
        manualAuthenticationInFlight = false
        manualLoginPreflight = false
    }

    @Synchronized
    fun completeAuthenticationFailure() {
        manualAuthenticationInFlight = false
        manualLoginPreflight = false
        proofOwner = null
    }

    @Synchronized
    fun onSessionReplacement(accountId: String, sessionId: String) {
        val owner = ownerOrNull(accountId, sessionId)
        currentOwner = owner
        if (proofOwner != owner) proofOwner = null
    }

    @Synchronized
    fun endAuthenticationAttempt() {
        manualAuthenticationInFlight = false
        manualLoginPreflight = false
    }

    @Synchronized
    fun invalidateAll() {
        manualLoginPreflight = false
        manualAuthenticationInFlight = false
        currentOwner = null
        proofOwner = null
    }

    @Synchronized
    fun hasValidProof(): Boolean = proofOwner != null && proofOwner == currentOwner

    @Synchronized
    fun hasValidProofFor(accountId: String, sessionId: String): Boolean =
        proofOwner == ownerOrNull(accountId, sessionId) && proofOwner == currentOwner

    @Synchronized
    fun consumeValidProof(): Boolean {
        if (!hasValidProof()) return false
        proofOwner = null
        return true
    }

    private fun ownerOrNull(accountId: String, sessionId: String): ManualAuthSessionOwner? {
        val normalizedAccountId = accountId.trim().takeIf(String::isNotEmpty) ?: return null
        val normalizedSessionId = sessionId.trim().takeIf(String::isNotEmpty) ?: return null
        return ManualAuthSessionOwner(normalizedAccountId, normalizedSessionId)
    }
}

/**
 * Single process-memory registry shared by the login gate, repository session commit and Kids policy.
 * Process death therefore destroys the proof by construction.
 */
internal object ManualParentAuthProofRegistry {
    private val tracker = ManualParentAuthProofTracker()

    fun noteManualLoginPreflight() = tracker.noteManualLoginPreflight()
    fun beginAuthenticationAttempt() = tracker.beginAuthenticationAttempt()
    fun completeAuthenticationSuccess(accountId: String, sessionId: String) =
        tracker.completeAuthenticationSuccess(accountId, sessionId)
    fun completeAuthenticationFailure() = tracker.completeAuthenticationFailure()
    fun onSessionReplacement(accountId: String, sessionId: String) =
        tracker.onSessionReplacement(accountId, sessionId)
    fun endAuthenticationAttempt() = tracker.endAuthenticationAttempt()
    fun invalidateAll() = tracker.invalidateAll()
    fun hasValidProof(): Boolean = tracker.hasValidProof()
    fun consumeValidProof(): Boolean = tracker.consumeValidProof()
}
