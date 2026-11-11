package sa.hulksa.player.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.ContentType

class ProviderRequestTraceTest {
    private val lines = mutableListOf<String>()

    @Before
    fun setUp() {
        ProviderRequestTrace.resetForTest()
        lines.clear()
        ProviderRequestTrace.sink = { lines += it }
    }

    @After
    fun tearDown() {
        ProviderRequestTrace.sink = null
        ProviderRequestTrace.resetForTest()
    }

    @Test
    fun `queued and terminal stay balanced and a terminal is reported once`() {
        val attempt = ProviderRequestTrace.queued(ProviderSurface.HERO, ContentType.MOVIE, "get_vod_info")
        ProviderRequestTrace.callIssued(attempt)
        ProviderRequestTrace.terminal(attempt, ProviderOutcome.SUCCESS, 200, shape = ProviderShape.OBJECT_START)
        ProviderRequestTrace.terminal(attempt, ProviderOutcome.HTTP, 500)

        assertTrue(ProviderRequestTrace.idle())
        assertEquals(3, lines.count { it.contains("ev=") })
        assertEquals(1, lines.count { it.contains("ev=terminal") })
        assertTrue(lines.first { it.contains("ev=terminal") }.contains("outcome=SUCCESS"))
    }

    @Test
    fun `attempts carry the bound owner and the current stage`() {
        ProviderRequestTrace.bindSession()
        ProviderRequestTrace.enterStage(ProviderTrigger.RETRY)
        val attempt = ProviderRequestTrace.queued(ProviderSurface.SCAN, ContentType.SERIES, "get_series_info")

        assertEquals("o1", attempt.ownerTag)
        assertEquals(ProviderTrigger.RETRY, attempt.trigger)
        assertTrue(lines.any { it.contains("ev=session") && it.contains("owner=o1") })
        assertTrue(lines.any { it.contains("ev=stage") && it.contains("stage=RETRY") })

        ProviderRequestTrace.bindSession()
        val next = ProviderRequestTrace.queued(ProviderSurface.SCAN, ContentType.SERIES, "get_series_info")
        assertEquals("o2", next.ownerTag)
        assertEquals(ProviderTrigger.STARTUP, next.trigger)
    }

    @Test
    fun `cache decisions are recorded without a provider attempt`() {
        ProviderRequestTrace.cache(ProviderSurface.SERIES_CARD, ContentType.SERIES, ProviderCacheDecision.COMPLETE)
        ProviderRequestTrace.cache(
            ProviderSurface.SERIES_CARD, ContentType.SERIES, ProviderCacheDecision.COOLDOWN_OR_SHARED,
        )

        assertEquals(2, lines.count { it.contains("ev=cache") })
        assertEquals(0, lines.count { it.contains("ev=queued") })
        assertTrue(ProviderRequestTrace.idle())
    }

    @Test
    fun `event history is capped once and counters still settle after truncation`() {
        repeat(600) {
            ProviderRequestTrace.cache(
                ProviderSurface.CATALOG, ContentType.MOVIE, ProviderCacheDecision.COMPLETE,
            )
        }

        assertEquals(512, lines.count { it.contains("ev=cache") })
        assertEquals(1, lines.count { it.contains("ev=truncated") })
        assertEquals(513, lines.size)

        val attempt = ProviderRequestTrace.queued(ProviderSurface.CATALOG, ContentType.MOVIE, "get_vod_streams")
        ProviderRequestTrace.callIssued(attempt)
        ProviderRequestTrace.terminal(attempt, ProviderOutcome.CANCELED)
        assertTrue(ProviderRequestTrace.idle())
        assertEquals(513, lines.size)
    }

    @Test
    fun `a throwing log sink cannot affect the caller`() {
        ProviderRequestTrace.sink = { throw IllegalStateException("log backend failure") }

        val attempt = ProviderRequestTrace.queued(ProviderSurface.AUTH, null, "authenticate")
        ProviderRequestTrace.callIssued(attempt)
        ProviderRequestTrace.terminal(attempt, ProviderOutcome.NETWORK)

        assertTrue(ProviderRequestTrace.idle())
    }

    @Test
    fun `shape helper keeps marker-free JSON distinct from HTML`() {
        assertEquals(ProviderShape.ARRAY_START, providerResponseShape("""[{"category_name":"Cloudflare"}]"""))
        assertEquals(ProviderShape.OBJECT_START, providerResponseShape("""{"info":{}}"""))
        assertEquals(ProviderShape.HTML_START, providerResponseShape("  <!DOCTYPE html><html></html>"))
        assertEquals(ProviderShape.HTML_START, providerResponseShape("<html><body>blocked</body></html>"))
        assertEquals(ProviderShape.OTHER, providerResponseShape("cloudflare challenge"))
        assertEquals(ProviderShape.OTHER, providerResponseShape("Access denied"))
        assertEquals(ProviderShape.UNAVAILABLE, providerResponseShape(null))
        assertEquals(ProviderShape.UNAVAILABLE, providerResponseShape("   "))
    }

    @Test
    fun `marker classifier is substring based inside JSON and shape is the discriminator`() {
        assertEquals("html-doctype", providerChallengeCategory("<!doctype html><html></html>"))
        assertEquals("html", providerChallengeCategory("<html><body>blocked</body></html>"))
        assertEquals("cloudflare", providerChallengeCategory("Attention: cloudflare challenge"))
        assertEquals("blocked-page", providerChallengeCategory("Sorry, you have been blocked"))
        // The existing classifier matches the marker inside a JSON array too; that is exactly why
        // status+category alone cannot distinguish a real page from JSON content, and why the
        // diagnostic records a coarse prefix shape as well.
        val json = """[{"category_name":"Cloudflare"}]"""
        assertEquals("cloudflare", providerChallengeCategory(json))
        assertEquals(ProviderShape.ARRAY_START, providerResponseShape(json))
        assertEquals(ProviderShape.HTML_START, providerResponseShape("<!doctype html><html></html>"))
    }
}
