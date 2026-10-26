package sa.hulksa.player.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

/**
 * Exercises the production VOD seek feedback owner, expiry effect and label with controlled time.
 * Both Movie and Series requests use the same PlayerScreen owner; the request key is covered by
 * replacing the owner, which is what `remember(request)` does on episode/request replacement.
 */
class VodSeekFeedbackEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun advance(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    private fun post(owner: VodSeekFeedbackOwner, text: String) {
        composeRule.runOnUiThread { owner.post(text) }
        composeRule.waitForIdle()
    }

    @Test
    fun repeatedForwardBackwardAndAlternatingSeeksKeepFullLifetimeFromLatest() {
        var owner by mutableStateOf(VodSeekFeedbackOwner())
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VodSeekFeedbackExpiry(owner)
            VodSeekFeedbackLabel(owner, Modifier.testTag("feedback"))
        }

        post(owner, "+10 ث"); advance(250)
        post(owner, "+10 ث"); advance(250)
        post(owner, "+10 ث"); advance(250)
        post(owner, "-10 ث"); advance(250)
        post(owner, "-10 ث"); advance(250)
        post(owner, "+10 ث"); advance(100)
        post(owner, "-10 ث"); advance(100)
        post(owner, "+10 ث")

        // 900 ms after the latest accepted seek the label is still in its full lifetime.
        advance(900)
        composeRule.onNodeWithTag("feedback").assertTextEquals("+10 ث")

        advance(200)
        composeRule.onNodeWithTag("feedback").assertDoesNotExist()
    }

    @Test
    fun identicalSeekNearPreviousExpiryGetsFreshFullLifetime() {
        var owner by mutableStateOf(VodSeekFeedbackOwner())
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VodSeekFeedbackExpiry(owner)
            VodSeekFeedbackLabel(owner, Modifier.testTag("feedback"))
        }

        post(owner, "+10 ث")
        advance(900)
        // Same text at the previous event's expiry boundary; the old timeout must not clear it.
        post(owner, "+10 ث")
        advance(900)
        composeRule.onNodeWithTag("feedback").assertTextEquals("+10 ث")

        advance(200)
        composeRule.onNodeWithTag("feedback").assertDoesNotExist()
    }

    @Test
    fun newerDifferentSeekCannotBeClearedByTheObsoleteTimeout() {
        var owner by mutableStateOf(VodSeekFeedbackOwner())
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VodSeekFeedbackExpiry(owner)
            VodSeekFeedbackLabel(owner, Modifier.testTag("feedback"))
        }

        post(owner, "+10 ث")
        advance(900)
        post(owner, "-10 ث")
        advance(900)
        composeRule.onNodeWithTag("feedback").assertTextEquals("-10 ث")

        advance(200)
        composeRule.onNodeWithTag("feedback").assertDoesNotExist()
    }

    @Test
    fun controlsVisibilityAndPlaybackDoNotOwnFeedbackExpiry() {
        var owner by mutableStateOf(VodSeekFeedbackOwner())
        var controlsVisible by mutableStateOf(true)
        var playing by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VodSeekFeedbackExpiry(owner)
            VodSeekFeedbackLabel(owner, Modifier.testTag("feedback"))
            if (controlsVisible) {
                Text("controls", Modifier.testTag("controls"))
            }
            Text(if (playing) "playing" else "paused", Modifier.testTag("playback"))
        }

        post(owner, "+10 ث")
        advance(200)
        composeRule.runOnUiThread { controlsVisible = false }
        composeRule.waitForIdle()

        // The label is independent of chrome visibility and playing state.
        composeRule.onNodeWithTag("feedback").assertExists()
        advance(200)
        composeRule.runOnUiThread { playing = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feedback").assertExists()

        advance(400)
        composeRule.onNodeWithTag("feedback").assertExists()

        advance(300)
        composeRule.onNodeWithTag("feedback").assertDoesNotExist()

        composeRule.runOnUiThread {
            controlsVisible = true
            playing = true
        }
        composeRule.waitForIdle()

        // Revealing chrome after expiry cannot resurrect the feedback.
        composeRule.onNodeWithTag("feedback").assertDoesNotExist()
    }

    @Test
    fun requestReplacementDropsPendingFeedbackAndItsTimeout() {
        var owner by mutableStateOf(VodSeekFeedbackOwner())
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VodSeekFeedbackExpiry(owner)
            VodSeekFeedbackLabel(owner, Modifier.testTag("feedback"))
        }

        post(owner, "+10 ث")
        advance(400)

        val replacement = VodSeekFeedbackOwner()
        // Replace the request-owned owner and accept the same first event in the same frame,
        // matching `remember(request) { VodSeekFeedbackOwner() }` on a Movie/episode change.
        composeRule.runOnUiThread {
            owner = replacement
            replacement.post("+10 ث")
        }
        composeRule.waitForIdle()

        // The fresh owner has its own local id 1 and identical text, but owns a fresh lifetime.
        advance(900)
        composeRule.onNodeWithTag("feedback").assertTextEquals("+10 ث")

        advance(200)
        composeRule.onNodeWithTag("feedback").assertDoesNotExist()
    }

    @Test
    fun playerDisposalCannotResurrectPendingFeedback() {
        var owner by mutableStateOf(VodSeekFeedbackOwner())
        var shown by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            if (shown) {
                VodSeekFeedbackExpiry(owner)
                VodSeekFeedbackLabel(owner, Modifier.testTag("feedback"))
            }
        }

        post(owner, "+10 ث")
        advance(200)

        composeRule.runOnUiThread { shown = false }
        composeRule.waitForIdle()
        advance(2_000)

        composeRule.runOnUiThread {
            owner = VodSeekFeedbackOwner()
            shown = true
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("feedback").assertDoesNotExist()
    }

    @Test
    fun positionSeekTextUsesTheSameFeedbackLifetime() {
        var owner by mutableStateOf(VodSeekFeedbackOwner())
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VodSeekFeedbackExpiry(owner)
            VodSeekFeedbackLabel(owner, Modifier.testTag("feedback"))
        }

        post(owner, "انتقال الى 01:23")
        advance(900)
        composeRule.onNodeWithTag("feedback").assertTextEquals("انتقال الى 01:23")

        advance(200)
        composeRule.onNodeWithTag("feedback").assertDoesNotExist()
    }
}
