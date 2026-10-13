package sa.hulksa.player.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.data.GrowthDestination
import sa.hulksa.player.data.OperationsGrowthLinkConfig
import sa.hulksa.player.ui.theme.HulkTheme

/** Isolated fixture for the real GrowthQrDialog focus contract; no persistence or external action. */
class GrowthQrBackFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun fixtureLink() = OperationsGrowthLinkConfig(
        enabled = true,
        title = "تجديد الاشتراك",
        url = "https://hulksa.com/",
        displayText = "hulksa.com",
    )

    @Test
    fun wideDialogFocusesTheVisibleBackAndOkDismissesOnce() {
        var dismissals = 0
        composeRule.setContent {
            HulkTheme {
                GrowthQrDialog(
                    destination = GrowthDestination.RENEWAL,
                    link = fixtureLink(),
                    onDismiss = { dismissals += 1 },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("رجوع").assertIsFocused()
        composeRule.onNodeWithText("رجوع").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertTrue(dismissals == 1)
    }
}
