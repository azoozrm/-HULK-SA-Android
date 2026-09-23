package sa.hulksa.player.macrobenchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deterministic regression coverage for the Gate 3A.5 destination proof.
 *
 * Pure-logic assertions (no device/UI state). Guards against the earlier over-broad LIVE
 * proof: the live-hero status "على الهواء الان" also appears on HOME and must not prove LIVE,
 * while a HOME screen with a LIVE hero must still prove HOME.
 */
@RunWith(AndroidJUnit4::class)
class TvFirstEntryNavigationTest {
    @Test
    fun homeWithLiveHeroStillProvesHome() {
        val present = setOf("شاهد الان", "على الهواء الان")
        assertTrue(TvFirstEntryNavigation.isHomeDestination(present))
        assertFalse(TvFirstEntryNavigation.isLiveDestination(present))
    }

    @Test
    fun homeDoesNotFalselyProveLive() {
        val present = setOf("مختار لك", "توصيات ومحتوى جديد", "على الهواء الان")
        assertFalse(TvFirstEntryNavigation.isLiveDestination(present))
        assertTrue(TvFirstEntryNavigation.isHomeDestination(present))
    }

    @Test
    fun realLiveScreenProvesLive() {
        val withPlay = setOf("القنوات", "تشغيل القناة")
        val withPlaceholder = setOf("القنوات", "اختر قناة")
        assertTrue(TvFirstEntryNavigation.isLiveDestination(withPlay))
        assertTrue(TvFirstEntryNavigation.isLiveDestination(withPlaceholder))
        assertFalse(TvFirstEntryNavigation.isHomeDestination(withPlay))
        assertFalse(TvFirstEntryNavigation.isHomeDestination(withPlaceholder))
    }

    @Test
    fun ambiguousLiveStatusAloneNeverProvesLive() {
        assertFalse(TvFirstEntryNavigation.isLiveDestination(setOf("على الهواء الان")))
    }

    @Test
    fun liveListHeaderAloneNeverProvesLive() {
        assertFalse(TvFirstEntryNavigation.isLiveDestination(setOf("القنوات")))
    }

    @Test
    fun emptySemanticsProveNothing() {
        assertFalse(TvFirstEntryNavigation.isLiveDestination(emptySet()))
        assertFalse(TvFirstEntryNavigation.isHomeDestination(emptySet()))
    }

    @Test
    fun proofUniverseContainsEveryProbedSemantic() {
        val universe = TvFirstEntryNavigation.PROOF_UNIVERSE.toSet()
        assertTrue(TvFirstEntryNavigation.HOME_SEMANTICS.all { it in universe })
        assertTrue(TvFirstEntryNavigation.LIVE_REQUIRED_SEMANTIC in universe)
        assertTrue(TvFirstEntryNavigation.LIVE_ACTIONS.all { it in universe })
    }
}
