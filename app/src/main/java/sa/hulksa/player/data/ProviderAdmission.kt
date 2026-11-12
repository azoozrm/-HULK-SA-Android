package sa.hulksa.player.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.Response
import sa.hulksa.player.model.AuthenticatedSession

/**
 * Provider/account admission identity for server-directed cooldowns.
 *
 * The provider is the canonical resolved portal base and the account is the existing
 * provider-scoped account identity. A username alone, a catalog type or a surface is never the
 * key, so another provider or account is never blocked by this provider's limit.
 */
internal data class ProviderAdmissionKey(
    val provider: String,
    val account: String,
)

internal fun providerAdmissionKey(session: AuthenticatedSession): ProviderAdmissionKey {
    val provider = session.portal.baseUrl.trim().trimEnd('/')
    return ProviderAdmissionKey(
        provider = provider,
        account = stableAccountId(provider, session.credentials.username),
    )
}

/** Process-only expectation attached to one provider call so the 429 interceptor can scope it. */
internal class ProviderAdmissionContext(
    val session: AuthenticatedSession,
    val owner: AuthenticatedSessionOwner?,
)

/**
 * Admission binding captured when a provider call is built. A session that is not the current
 * registry owner is stale: the call must not dispatch and its response may not modify any state.
 */
internal data class ProviderAdmissionBinding(
    val owner: AuthenticatedSessionOwner?,
    val stale: Boolean,
)

internal fun providerAdmissionBinding(session: AuthenticatedSession): ProviderAdmissionBinding {
    val owner = AuthenticatedSessionRegistry.currentOwner()
    return ProviderAdmissionBinding(
        owner = owner?.takeIf { it.session === session },
        stale = owner != null && owner.session !== session,
    )
}

/** True only for an actual HTTP 429 surfaced by the provider clients. */
internal fun Throwable.isProviderRateLimited(): Boolean = when (this) {
    is XtreamException.ServiceBlocked -> statusCode == 429
    is XtreamException.Http -> statusCode == 429
    else -> false
}

/**
 * Parses only the standard `Retry-After` forms. Invalid, missing or out-of-range values return
 * null so no automatic delay is invented; the observed 10 seconds and Cloudflare are not special.
 */
internal object ProviderRetryAfter {
    const val MAX_SECONDS = 86_400L
    private const val MAX_MILLIS = MAX_SECONDS * 1_000L

    fun delayMillis(header: String?, responseAtEpochMs: Long): Long? {
        val value = header?.trim().orEmpty()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { seconds ->
            if (seconds < 0L || seconds > MAX_SECONDS) return null
            return seconds * 1_000L
        }
        val dateMs = parseHttpDate(value) ?: return null
        val delta = dateMs - responseAtEpochMs
        return when {
            delta <= 0L -> 0L
            delta > MAX_MILLIS -> null
            else -> delta
        }
    }

    /** API-23-safe RFC 1123 parsing; invalid or partially consumed values are rejected. */
    private fun parseHttpDate(value: String): Long? {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        format.timeZone = TimeZone.getTimeZone("GMT")
        format.isLenient = false
        val position = ParsePosition(0)
        val parsed = runCatching { format.parse(value, position) }.getOrNull()
        return if (parsed == null || position.index != value.length) null else parsed.time
    }
}

/**
 * Shared production admission for authenticated provider API requests.
 *
 * A valid `Retry-After` on a 429 records a monotonic deadline for the resolved provider/account.
 * Concurrent responses may extend that deadline and never shorten it. Every provider client asks
 * for admission immediately before dispatch; a blocked request suspends cancellably and rechecks
 * its owner, so an obsolete account can never dispatch or keep waiting for a newer owner.
 *
 * After a cooldown, admissions are released with a small spacing instead of all at once. Without a
 * recorded 429 the state is absent and admission is a no-op, preserving ordinary behavior.
 */
class ProviderAdmission internal constructor(
    private val nowNs: () -> Long = System::nanoTime,
    private val nowWallMs: () -> Long = System::currentTimeMillis,
    private val sleepNs: suspend (Long) -> Unit = { nanos ->
        delay((nanos + 999_999L) / 1_000_000L)
    },
    private val ownerIsCurrent: (AuthenticatedSessionOwner) -> Boolean = {
        AuthenticatedSessionRegistry.isCurrent(it)
    },
) {
    private class State {
        var deadlineNs: Long = Long.MIN_VALUE
        var nextGrantNs: Long = Long.MIN_VALUE
        var waiters: Int = 0
        var touchedNs: Long = 0L
    }

    private val lock = Any()
    private val states = LinkedHashMap<ProviderAdmissionKey, State>()

    /** Test-only observation of the monotonic admission instant; never consulted in production. */
    internal var onAdmissionGranted: ((Long) -> Unit)? = null

    /**
     * Records one actual 429. Returns true only when a valid server delay was accepted. A stale
     * owner's response never modifies the newer owner's deadline.
     */
    internal fun onRateLimited(
        session: AuthenticatedSession,
        owner: AuthenticatedSessionOwner?,
        retryAfter: String?,
        responseAtEpochMs: Long,
    ): Boolean {
        if (owner != null && !ownerIsCurrent(owner)) return false
        val delayMs = ProviderRetryAfter.delayMillis(retryAfter, responseAtEpochMs) ?: return false
        val now = nowNs()
        val deadline = saturatingAdd(now, delayMs * 1_000_000L)
        val key = providerAdmissionKey(session)
        synchronized(lock) {
            val state = stateLocked(key, now)
            state.deadlineNs = maxOf(state.deadlineNs, deadline)
            state.nextGrantNs = maxOf(state.nextGrantNs, state.deadlineNs)
        }
        return true
    }

    /** Current server deadline for this provider/account, or null when no valid 429 was seen. */
    fun cooldownDeadlineNs(session: AuthenticatedSession): Long? = synchronized(lock) {
        val deadline = states[providerAdmissionKey(session)]?.deadlineNs ?: return null
        if (deadline == Long.MIN_VALUE) null else deadline
    }

    /** Suspends until this provider/account may dispatch. A no-op without an active cooldown. */
    internal suspend fun awaitAdmission(
        session: AuthenticatedSession,
        owner: AuthenticatedSessionOwner?,
    ) {
        ensureOwner(owner)
        val key = providerAdmissionKey(session)
        val startedNs = nowNs()
        synchronized(lock) {
            val state = states[key] ?: return
            state.waiters += 1
            state.touchedNs = startedNs
        }
        try {
            while (true) {
                ensureOwner(owner)
                val now = nowNs()
                var grantedAtNs: Long? = null
                val waitNanos: Long
                synchronized(lock) {
                    pruneLocked(now)
                    val state = states[key] ?: return
                    val start = maxOf(state.deadlineNs, state.nextGrantNs)
                    if (now >= start) {
                        state.nextGrantNs = saturatingAdd(now, RELEASE_SPACING_NS)
                        state.touchedNs = now
                        grantedAtNs = now
                        waitNanos = 0L
                    } else {
                        waitNanos = minOf(start - now, WAIT_CHUNK_NS)
                    }
                }
                grantedAtNs?.let { grantedAt ->
                    onAdmissionGranted?.invoke(grantedAt)
                    return
                }
                sleepNs(maxOf(waitNanos, 1_000_000L))
            }
        } finally {
            synchronized(lock) {
                states[key]?.let { state ->
                    state.waiters = (state.waiters - 1).coerceAtLeast(0)
                }
            }
        }
    }

    /**
     * Waits for at least [notBeforeNs] and any later extension. Returns after the deadline even if
     * the state was pruned, so a scheduled bounded recovery is never stranded by cleanup.
     */
    internal suspend fun awaitCooldown(
        session: AuthenticatedSession,
        owner: AuthenticatedSessionOwner?,
        notBeforeNs: Long,
    ) {
        ensureOwner(owner)
        val key = providerAdmissionKey(session)
        synchronized(lock) {
            states[key]?.let { it.waiters += 1 }
        }
        try {
            while (true) {
                ensureOwner(owner)
                val now = nowNs()
                val deadline = synchronized(lock) {
                    maxOf(notBeforeNs, states[key]?.deadlineNs ?: Long.MIN_VALUE)
                }
                if (now >= deadline) return
                sleepNs(deadline - now)
            }
        } finally {
            synchronized(lock) {
                states[key]?.let { state ->
                    state.waiters = (state.waiters - 1).coerceAtLeast(0)
                }
            }
        }
    }

    private fun ensureOwner(owner: AuthenticatedSessionOwner?) {
        if (owner != null && !ownerIsCurrent(owner)) {
            throw CancellationException("Provider admission owner replaced")
        }
    }

    private fun stateLocked(key: ProviderAdmissionKey, now: Long): State {
        states[key]?.let {
            it.touchedNs = now
            return it
        }
        if (states.size >= MAX_STATES) {
            val victim = states.entries.firstOrNull { it.value.waiters == 0 }
                ?: states.entries.first()
            states.remove(victim.key)
        }
        return State().also {
            it.touchedNs = now
            states[key] = it
        }
    }

    private fun pruneLocked(now: Long) {
        val iterator = states.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val state = entry.value
            if (state.waiters > 0) continue
            if (state.deadlineNs == Long.MIN_VALUE) {
                iterator.remove()
                continue
            }
            val idleAfter = saturatingAdd(state.nextGrantNs, RELEASE_SPACING_NS)
            if (now >= state.deadlineNs && now >= idleAfter) iterator.remove()
        }
    }

    private fun saturatingAdd(base: Long, delta: Long): Long {
        if (delta <= 0L) return base
        return if (base > Long.MAX_VALUE - delta) Long.MAX_VALUE else base + delta
    }

    internal companion object {
        const val RELEASE_SPACING_NS = 250_000_000L
        const val WAIT_CHUNK_NS = 250_000_000L
        const val MAX_STATES = 16

        val shared: ProviderAdmission by lazy { ProviderAdmission() }
    }
}

/**
 * Per-client interceptor. It only reads response headers, so the deadline is recorded before any
 * body parsing and independently of coroutine cancellation or diagnostics availability.
 */
internal class ProviderAdmissionInterceptor(
    private val admission: ProviderAdmission = ProviderAdmission.shared,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val context = request.tag(ProviderAdmissionContext::class.java)
        val response = chain.proceed(request)
        if (context != null && response.code == 429) {
            admission.onRateLimited(
                session = context.session,
                owner = context.owner,
                retryAfter = response.header("Retry-After"),
                responseAtEpochMs = response.receivedResponseAtMillis,
            )
        }
        return response
    }
}

/** Current owner token only when it still owns [session]; tests may have no registry owner. */
internal fun providerAdmissionOwner(
    session: AuthenticatedSession,
): AuthenticatedSessionOwner? = providerAdmissionBinding(session).owner
