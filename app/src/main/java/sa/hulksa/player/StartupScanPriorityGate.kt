package sa.hulksa.player

import sa.hulksa.player.model.ContentType

/**
 * R23 startup ordering gate.
 *
 * Holds automatic notification scan admission until the initial current-owner catalog
 * revalidation round has settled for every required type. Settlement is terminal-outcome based
 * (network success, failure, or a valid no-fetch/fresh decision) and is reported by the catalog
 * owner's callbacks; an empty loading set or completed cache restore is deliberately not a
 * settlement signal.
 *
 * The gate owns no coroutine, permit or network state: it only answers whether automatic scan
 * admission is allowed and which types are still pending. Owner identity is compared by reference
 * so a stale session/profile callback can never release a newer owner's gate.
 */
internal class StartupScanPriorityGate {
    private val lock = Any()
    private var owner: Any? = null
    private var pending: MutableSet<ContentType> = mutableSetOf()
    private var held = false

    /** Starts a round for [owner]. Returns true when automatic scans must wait. */
    fun begin(owner: Any, types: Set<ContentType>): Boolean = synchronized(lock) {
        this.owner = owner
        pending = types.toMutableSet()
        held = pending.isNotEmpty()
        held
    }

    /** True only for the exact bound owner while its round is still pending. */
    fun isHeld(owner: Any?): Boolean = synchronized(lock) {
        held && owner != null && this.owner === owner && pending.isNotEmpty()
    }

    /**
     * Marks one required type settled. Returns true exactly once, when this settlement completed
     * the round and the held automatic work may be released.
     */
    fun settle(owner: Any?, type: ContentType): Boolean = synchronized(lock) {
        if (!held || owner == null || this.owner !== owner) return false
        if (!pending.remove(type)) return false
        if (pending.isEmpty()) {
            held = false
            true
        } else {
            false
        }
    }

    /** Pending types for the exact owner; empty for any stale/mismatched owner. */
    fun pendingTypes(owner: Any?): Set<ContentType> = synchronized(lock) {
        if (!held || owner == null || this.owner !== owner) emptySet() else pending.toSet()
    }

    /** Drops the round without releasing anything; used on logout/session/profile replacement. */
    fun invalidate(owner: Any? = null) = synchronized(lock) {
        if (owner == null || this.owner === owner) {
            held = false
            pending = mutableSetOf()
            this.owner = null
        }
    }

    fun isHeldForAnyOwner(): Boolean = synchronized(lock) { held }
}

/**
 * Automatic triggers that may be held behind the initial catalog round. Explicit manual actions
 * keep their existing policy and are never gated.
 */
internal val NotificationScanTrigger.isAutomaticStartupScan: Boolean
    get() = when (this) {
        NotificationScanTrigger.APP_START,
        NotificationScanTrigger.APP_RESUME,
        NotificationScanTrigger.PROFILE_SWITCH,
        NotificationScanTrigger.LIBRARY_REFRESH,
        -> true

        NotificationScanTrigger.MANUAL_REFRESH,
        NotificationScanTrigger.NOTIFICATION_CENTER,
        NotificationScanTrigger.MASTER_REENABLE,
        -> false
    }
