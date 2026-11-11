package sa.hulksa.player.data

import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import sa.hulksa.player.model.ContentType

/** Surface that issued an authenticated provider request. */
enum class ProviderSurface {
    CATALOG,
    HERO,
    MOVIE_CARD,
    SERIES_CARD,
    SCAN,
    DETAILS,
    AUTH,
    OTHER,
}

/** Stage in which the request was created. */
enum class ProviderTrigger {
    STARTUP,
    RETRY,
    REFRESH,
    OTHER,
}

/** Terminal outcome of one provider attempt. */
enum class ProviderOutcome {
    SUCCESS,
    BLOCKED,
    HTTP,
    NETWORK,
    CANCELED,
    GUARD,
    OTHER,
}

/** Coarse, already-available response prefix shape. Never a claim that the whole body is JSON. */
enum class ProviderShape {
    ARRAY_START,
    OBJECT_START,
    HTML_START,
    OTHER,
    UNAVAILABLE,
}

/** Cache decision taken before any network attempt. */
enum class ProviderCacheDecision {
    COMPLETE,
    COOLDOWN_OR_SHARED,
    FALLBACK,
}

/**
 * One diagnostic attempt. It carries only closed-registry or numeric fields: no URL, query,
 * header, cookie, credential, account or provider body. A terminal may be reported exactly once.
 */
internal class ProviderAttempt(
    val id: Int,
    val batchId: Int,
    val surface: ProviderSurface,
    val type: ContentType?,
    val action: String,
    val trigger: ProviderTrigger,
    val ownerTag: String,
    val startedNs: Long,
) {
    internal val terminalReported = AtomicBoolean(false)
    internal val callReported = AtomicBoolean(false)
}

/**
 * Bounded, sanitized provider request chronology for the R22 startup diagnosis.
 *
 * Records app-enqueued work, the actual client call issuance and exactly one terminal result per
 * attempt, plus cache decisions. There is no socket-level observer in the existing clients, so the
 * "call" event means the app issued the HTTP call; it is never labelled as a proven TCP/TLS start.
 * Emission is capped at [MAX_EVENTS] per process; counters keep accounting after truncation so a
 * lost line can never leak an outstanding count.
 */
internal object ProviderRequestTrace {
    private const val MAX_EVENTS = 512
    private const val TAG = "HulkTrace"

    /** Test hook; production uses Android logging with a safe fallback. */
    internal var sink: ((String) -> Unit)? = null

    private val lock = Any()
    private var ownerSeq = 0
    private var batchSeq = 0
    private var attemptSeq = 0
    private var eventCount = 0
    private var dropped = 0
    private var stage = ProviderTrigger.STARTUP
    private var ownerTag = "o0"
    private val pending = IntArray(ProviderSurface.values().size)
    private val calls = IntArray(ProviderSurface.values().size)
    private val originNs = System.nanoTime()

    internal fun bindSession() {
        synchronized(lock) {
            ownerSeq += 1
            ownerTag = "o$ownerSeq"
            stage = ProviderTrigger.STARTUP
        }
        emit { base("ev=session") + " owner=$ownerTag stage=STARTUP" }
    }

    internal fun enterStage(trigger: ProviderTrigger) {
        synchronized(lock) { stage = trigger }
        emit { base("ev=stage") + " stage=$trigger" }
    }

    internal fun queued(
        surface: ProviderSurface,
        type: ContentType?,
        action: String,
    ): ProviderAttempt = synchronized(lock) {
        attemptSeq += 1
        val attempt = ProviderAttempt(
            id = attemptSeq,
            batchId = batchSeq,
            surface = surface,
            type = type,
            action = action,
            trigger = stage,
            ownerTag = ownerTag,
            startedNs = System.nanoTime(),
        )
        pending[surface.ordinal] += 1
        attempt
    }.also { attempt ->
        emit { attemptLine("queued", attempt) + " pending=${counts(pending)} calls=${counts(calls)}" }
    }

    internal fun beginBatch(): Int = synchronized(lock) {
        batchSeq += 1
        batchSeq
    }

    internal fun callIssued(attempt: ProviderAttempt) {
        if (!attempt.callReported.compareAndSet(false, true)) return
        synchronized(lock) { calls[attempt.surface.ordinal] += 1 }
        emit {
            attemptLine("call", attempt) +
                " pending=${counts(pending)} calls=${counts(calls)}"
        }
    }

    internal fun terminal(
        attempt: ProviderAttempt,
        outcome: ProviderOutcome,
        status: Int? = null,
        category: String? = null,
        shape: ProviderShape = ProviderShape.UNAVAILABLE,
    ) {
        if (!attempt.terminalReported.compareAndSet(false, true)) return
        synchronized(lock) {
            pending[attempt.surface.ordinal] =
                (pending[attempt.surface.ordinal] - 1).coerceAtLeast(0)
            if (attempt.callReported.get()) {
                calls[attempt.surface.ordinal] = (calls[attempt.surface.ordinal] - 1).coerceAtLeast(0)
            }
        }
        val durationMs = (System.nanoTime() - attempt.startedNs) / 1_000_000
        emit {
            attemptLine("terminal", attempt) +
                " outcome=$outcome" +
                " code=${status ?: "none"}" +
                " shape=$shape" +
                " category=${category ?: "none"}" +
                " durMs=$durationMs" +
                " pending=${counts(pending)} calls=${counts(calls)}"
        }
    }

    /** Reports a cancellation terminal only when the attempt has not already settled. */
    internal fun terminalIfOpen(attempt: ProviderAttempt?, outcome: ProviderOutcome) {
        if (attempt != null && !attempt.terminalReported.get()) {
            terminal(attempt, outcome)
        }
    }

    internal fun cache(
        surface: ProviderSurface,
        type: ContentType?,
        decision: ProviderCacheDecision,
    ) {
        emit {
            base("ev=cache") +
                " surface=$surface type=${type?.name ?: "none"} decision=$decision" +
                " owner=$ownerTag stage=$stage"
        }
    }

    internal fun outstanding(): String = synchronized(lock) {
        "pending=${counts(pending)} calls=${counts(calls)}"
    }

    internal fun idle(): Boolean = synchronized(lock) {
        pending.all { it == 0 } && calls.all { it == 0 }
    }

    internal fun resetForTest() {
        synchronized(lock) {
            ownerSeq = 0
            batchSeq = 0
            attemptSeq = 0
            eventCount = 0
            dropped = 0
            stage = ProviderTrigger.STARTUP
            ownerTag = "o0"
            for (index in pending.indices) {
                pending[index] = 0
                calls[index] = 0
            }
        }
    }

    private fun base(event: String): String {
        val offset = (System.nanoTime() - originNs) / 1_000_000
        return "v=1 $event off=$offset"
    }

    private fun attemptLine(event: String, attempt: ProviderAttempt): String =
        base("ev=$event") +
            " id=${attempt.id}" +
            " batch=${attempt.batchId}" +
            " surface=${attempt.surface}" +
            " type=${attempt.type?.name ?: "none"}" +
            " action=${attempt.action}" +
            " trigger=${attempt.trigger}" +
            " owner=${attempt.ownerTag}"

    private fun counts(values: IntArray): String = listOf(
        "cat" to ProviderSurface.CATALOG,
        "hero" to ProviderSurface.HERO,
        "mcard" to ProviderSurface.MOVIE_CARD,
        "scard" to ProviderSurface.SERIES_CARD,
        "scan" to ProviderSurface.SCAN,
        "det" to ProviderSurface.DETAILS,
        "auth" to ProviderSurface.AUTH,
        "other" to ProviderSurface.OTHER,
    ).joinToString(",") { (label, surface) -> "$label:${values[surface.ordinal]}" }

    private fun emit(line: () -> String) {
        val text = synchronized(lock) {
            if (eventCount < MAX_EVENTS) {
                eventCount += 1
                line()
            } else {
                if (dropped == 0) {
                    eventCount += 1
                    dropped = 1
                    base("ev=truncated") + " cap=$MAX_EVENTS dropped=1"
                } else {
                    dropped += 1
                    null
                }
            }
        } ?: return
        write(text)
    }

    private fun write(line: String) {
        try {
            val custom = sink
            if (custom != null) custom(line) else Log.i(TAG, line)
        } catch (_: RuntimeException) {
            // Logging must never change an app result.
        } catch (_: LinkageError) {
            // Missing Android logging backend on the JVM test runtime.
        }
    }
}

/** Coarse shape from already-read bounded prefix text. Never retains or prints the text itself. */
internal fun providerResponseShape(prefix: String?): ProviderShape {
    val trimmed = prefix?.trimStart() ?: return ProviderShape.UNAVAILABLE
    if (trimmed.isEmpty()) return ProviderShape.UNAVAILABLE
    return when {
        trimmed.startsWith("[") -> ProviderShape.ARRAY_START
        trimmed.startsWith("{") -> ProviderShape.OBJECT_START
        trimmed.startsWith("<!doctype html", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true) -> ProviderShape.HTML_START
        else -> ProviderShape.OTHER
    }
}

/**
 * The same four sanitized challenge markers the product classifier uses, exposed for the
 * diagnostic record only. Classification decisions stay in their existing single owner.
 */
internal fun providerChallengeCategory(body: String): String? {
    val prefix = body.trimStart().take(512).lowercase()
    return when {
        prefix.startsWith("<!doctype html") -> "html-doctype"
        prefix.startsWith("<html") -> "html"
        "cloudflare" in prefix -> "cloudflare"
        "sorry, you have been blocked" in prefix -> "blocked-page"
        else -> null
    }
}

/** Reports one terminal result; null attempts and repeated reports are ignored. */
internal fun reportProviderTerminal(
    attempt: ProviderAttempt?,
    outcome: ProviderOutcome,
    status: Int? = null,
    category: String? = null,
    shape: ProviderShape = ProviderShape.UNAVAILABLE,
) {
    if (attempt == null) return
    ProviderRequestTrace.terminal(attempt, outcome, status, category, shape)
}

/** Maps an already-thrown provider exception to its sanitized terminal outcome. */
internal fun reportProviderException(attempt: ProviderAttempt?, error: Throwable) {
    if (attempt == null) return
    when (error) {
        is XtreamException.ServiceBlocked -> reportProviderTerminal(
            attempt, ProviderOutcome.BLOCKED, error.statusCode, error.category,
        )
        is XtreamException.Http -> reportProviderTerminal(
            attempt, ProviderOutcome.HTTP, error.statusCode,
        )
        is IOException -> reportProviderTerminal(attempt, ProviderOutcome.NETWORK)
        is kotlinx.coroutines.CancellationException ->
            reportProviderTerminal(attempt, ProviderOutcome.CANCELED)
        is XtreamJsonGuardException -> reportProviderTerminal(attempt, ProviderOutcome.GUARD)
        else -> reportProviderTerminal(attempt, ProviderOutcome.OTHER)
    }
}
