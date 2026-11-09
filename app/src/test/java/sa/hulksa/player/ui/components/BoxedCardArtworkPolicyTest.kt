package sa.hulksa.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/**
 * Behavioral candidate/failure lifecycle for the shared boxed Movie card. The catalog poster is
 * preferred while usable; current-owner provider presentation artwork is the fallback; a newly
 * supplied real candidate invalidates obsolete failure state; all real candidates failing keeps
 * the honest fallback.
 */
class BoxedCardArtworkPolicyTest {
    private val poster = "https://cdn.test/poster.jpg"
    private val metadataArtwork = "https://cdn.test/backdrop.jpg"

    @Test
    fun `working catalog poster stays preferred over metadata artwork`() {
        val candidates = boxedCardArtworkCandidates(poster, metadataArtwork)

        assertEquals(listOf(poster, metadataArtwork), candidates)
        assertEquals(poster, activeBoxedCardArtwork(candidates, emptySet()))
    }

    @Test
    fun `missing catalog poster uses the metadata artwork`() {
        val candidates = boxedCardArtworkCandidates(null, metadataArtwork)

        assertEquals(listOf(metadataArtwork), candidates)
        assertEquals(metadataArtwork, activeBoxedCardArtwork(candidates, emptySet()))
    }

    @Test
    fun `failed catalog poster falls through to the metadata artwork`() {
        val candidates = boxedCardArtworkCandidates(poster, metadataArtwork)

        assertEquals(
            metadataArtwork,
            activeBoxedCardArtwork(candidates, setOf(poster)),
        )
    }

    @Test
    fun `metadata arriving after poster failure invalidates the obsolete failure state`() {
        val posterOnly = boxedCardArtworkCandidates(poster, null)
        val posterFailed = setOf(poster)

        assertNull(activeBoxedCardArtwork(posterOnly, posterFailed))

        val enriched = boxedCardArtworkCandidates(poster, metadataArtwork)
        val retained = retainedBoxedCardArtworkFailures(enriched, posterFailed)

        assertEquals(setOf(poster), retained)
        assertEquals(metadataArtwork, activeBoxedCardArtwork(enriched, retained))
    }

    @Test
    fun `a replacement poster invalidates the previous poster failure`() {
        val oldCandidates = boxedCardArtworkCandidates(poster, metadataArtwork)
        val failed = setOf(poster)

        val replacement = "https://cdn.test/new-poster.jpg"
        val newCandidates = boxedCardArtworkCandidates(replacement, metadataArtwork)
        val retained = retainedBoxedCardArtworkFailures(newCandidates, failed)

        assertEquals(emptySet<String>(), retained)
        assertEquals(replacement, activeBoxedCardArtwork(newCandidates, retained))
    }

    @Test
    fun `all real candidates failing keeps the honest fallback`() {
        val candidates = boxedCardArtworkCandidates(poster, metadataArtwork)
        val failed = setOf(poster, metadataArtwork)

        assertNull(activeBoxedCardArtwork(candidates, failed))
    }

    @Test
    fun `blank and duplicate candidates are never tried twice`() {
        assertEquals(emptyList<String>(), boxedCardArtworkCandidates("  ", null))
        assertEquals(
            listOf(poster),
            boxedCardArtworkCandidates(" $poster ", poster),
        )
    }

    @Test
    fun `late metadata from another owner or generation is rejected`() {
        val session = AuthenticatedSession(
            portal = PortalConfig("https://portal.test", PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("CODE", "user", "pass"),
            account = AccountInfo("user", "Active", null, 1, 1, false),
        )
        val owner = AuthenticatedSessionOwner(
            accountId = "account-a",
            providerId = "provider-a",
            sessionId = "session-a",
            session = session,
        )
        val replacementOwner = owner.copy(sessionId = "session-b")

        assertTrue(movieCardMetadataResultIsCurrent(2L, 2L, owner, owner))
        assertFalse(
            "an old item/owner generation is not the current card request",
            movieCardMetadataResultIsCurrent(2L, 3L, owner, owner),
        )
        assertFalse(
            "a late result from a replaced session is rejected",
            movieCardMetadataResultIsCurrent(2L, 2L, owner, replacementOwner),
        )
        assertFalse(
            "a result without a requested owner is rejected",
            movieCardMetadataResultIsCurrent(2L, 2L, null, owner),
        )
    }
}
