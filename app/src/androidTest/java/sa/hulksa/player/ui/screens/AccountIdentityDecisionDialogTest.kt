package sa.hulksa.player.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.AccountDecisionOption
import sa.hulksa.player.ui.theme.HulkTheme

class AccountIdentityDecisionDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun singleCandidateOffersSameDifferentAndCancelWithSafeInitialFocus() {
        var selected: String? = null
        var different = false
        var cancelled = false
        setDialog(
            options = listOf(AccountDecisionOption(key = "0", label = "first.example.test")),
            onSelectExisting = { selected = it },
            onDifferent = { different = true },
            onCancel = { cancelled = true },
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("${ACCOUNT_DECISION_CANDIDATE_TAG_PREFIX}0").assertIsDisplayed()
        composeRule.onNodeWithTag(ACCOUNT_DECISION_DIFFERENT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ACCOUNT_DECISION_CANCEL_TAG).assertIsDisplayed().assertIsFocused()
        assertEquals(null, selected)
        assertFalse(different)
        assertFalse(cancelled)
    }

    @Test
    fun dpadTraversalIsDeterministicBetweenSameDifferentAndCancel() {
        setDialog(options = listOf(AccountDecisionOption("0", "first.example.test")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ACCOUNT_DECISION_CANCEL_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag(ACCOUNT_DECISION_DIFFERENT_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("${ACCOUNT_DECISION_CANDIDATE_TAG_PREFIX}0")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(ACCOUNT_DECISION_DIFFERENT_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(ACCOUNT_DECISION_CANCEL_TAG).assertIsFocused()
    }

    @Test
    fun selectingACandidateDeliversItsStableKey() {
        var selected: String? = null
        setDialog(
            options = listOf(
                AccountDecisionOption("0", "first.example.test"),
                AccountDecisionOption("1", "second.example.test:8080"),
            ),
            onSelectExisting = { selected = it },
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("${ACCOUNT_DECISION_CANDIDATE_TAG_PREFIX}1").performClick()

        assertEquals("1", selected)
    }

    @Test
    fun multipleCandidatesRenderEveryPrivacySafeLabel() {
        val labels = listOf("first.example.test", "second.example.test:8080")
        setDialog(
            options = labels.mapIndexed { index, label ->
                AccountDecisionOption(index.toString(), label)
            },
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("${ACCOUNT_DECISION_CANDIDATE_TAG_PREFIX}0").assertIsDisplayed()
        composeRule.onNodeWithTag("${ACCOUNT_DECISION_CANDIDATE_TAG_PREFIX}1").assertIsDisplayed()
        composeRule.onNodeWithText(labels[0], substring = true).assertExists()
        composeRule.onNodeWithText(labels[1], substring = true).assertExists()
    }

    @Test
    fun differentAndCancelInvokeTheirOwnActions() {
        var different = false
        var cancelled = false
        setDialog(
            options = listOf(AccountDecisionOption("0", "first.example.test")),
            onDifferent = { different = true },
            onCancel = { cancelled = true },
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ACCOUNT_DECISION_DIFFERENT_TAG).performClick()
        composeRule.onNodeWithTag(ACCOUNT_DECISION_CANCEL_TAG).performClick()

        assertTrue(different)
        assertTrue(cancelled)
    }

    @Test
    fun systemBackDismissesTheDecisionAsCancel() {
        var cancelled = false
        setDialog(
            options = listOf(AccountDecisionOption("0", "first.example.test")),
            onCancel = { cancelled = true },
        )
        composeRule.waitForIdle()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitForIdle()

        assertTrue(cancelled)
    }

    private fun setDialog(
        options: List<AccountDecisionOption>,
        onSelectExisting: (String) -> Unit = {},
        onDifferent: () -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        composeRule.setContent {
            HulkTheme {
                AccountIdentityDecisionDialog(
                    username = "subscriber",
                    options = options,
                    onSelectExisting = onSelectExisting,
                    onDifferentSubscription = onDifferent,
                    onCancel = onCancel,
                )
            }
        }
    }
}
