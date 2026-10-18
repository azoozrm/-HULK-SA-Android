package sa.hulksa.player.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.PairingUiState
import sa.hulksa.player.ui.theme.HulkTheme

/**
 * Isolated fixture for the login credential lifetime contract. The password is
 * intentionally non-saveable and must not return after recreation, while the
 * access code remains saveable and prefilled. Synthetic values only; no
 * persistence, network or owner state is touched.
 */
class LoginCredentialLifetimeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun passwordDoesNotRestoreAcrossRecreationWhileAccessCodeIsRetained() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            HulkTheme {
                LoginScreen(
                    isTv = false,
                    pairingOffered = false,
                    pairing = PairingUiState(),
                    pairingReturnFocusSuspended = false,
                    isStarting = false,
                    isLoading = false,
                    errorMessage = null,
                    onLogin = { _, _, _, _ -> },
                    onStartPairing = {},
                    onRetryPairing = {},
                    onRegeneratePairing = {},
                    onCancelPairing = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("كود الدخول")
            .performTextInput("111222333")
        composeRule.onNodeWithText("اظهار كلمة المرور").performClick()
        composeRule.onNodeWithContentDescription("كلمة المرور")
            .performTextInput("Synthetic-Pass-123")
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("كلمة المرور")
            .assertTextContains("Synthetic-Pass-123")
        composeRule.onNodeWithText("كلمة المرور").assertDoesNotExist()

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("كود الدخول")
            .assertTextContains("111222333")
        composeRule.onNodeWithText("Synthetic-Pass-123").assertDoesNotExist()
        composeRule.onNodeWithText("كلمة المرور").assertIsDisplayed()
    }
}
