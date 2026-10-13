package sa.hulksa.player.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.theme.HulkTheme

/**
 * Isolated in-memory workflow fixture for the real ProfilePinProtectionScreen. It uses
 * non-persistent callbacks only and never touches an owner credential store.
 */
class PinConfirmationWorkflowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun fixtureProfile() = UserProfile(
        id = "r04-workflow-fixture",
        displayName = "اختبار",
        kind = ProfileKind.STANDARD,
        avatarKey = "ember",
        createdAtEpochMs = 0L,
        isPrimary = false,
    )

    private fun setFixture(
        onSetPin: suspend (String) -> Boolean,
        onClose: () -> Unit,
    ) {
        composeRule.setContent {
            HulkTheme {
                ProfilePinProtectionScreen(
                    profile = fixtureProfile(),
                    isTv = true,
                    isProtected = false,
                    onVerify = { true },
                    onSetPin = onSetPin,
                    onClearPin = { true },
                    onClose = onClose,
                )
            }
        }
    }

    private fun tapKey(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    private fun enterDigits(digits: List<String>) {
        digits.forEach { tapKey(it) }
    }

    /** Real on-screen D-pad path: focus the key and press the remote OK/center key. */
    private fun dpadEnter(digits: List<String>) {
        digits.forEach { digit ->
            composeRule.onNodeWithText(digit).requestFocus()
            composeRule.onNodeWithText(digit).performKeyInput { pressKey(Key.DirectionCenter) }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun firstFourDigitsAdvanceOnceAndConfirmationOwnsKeypadFocus() {
        val stored = mutableListOf<String>()
        setFixture(onSetPin = { pin -> stored += pin; true }, onClose = {})

        enterDigits(listOf("1", "2", "3", "4"))
        composeRule.onNodeWithText("تاكيد رمز PIN").assertExists()
        // The confirmation stage must own the keypad focus for D-pad input.
        composeRule.onNodeWithText("1").assertIsFocused()
        assertTrue(stored.isEmpty())
    }

    @Test
    fun confirmationAcceptsAllFourDigitsOnceAndSaves() {
        val stored = mutableListOf<String>()
        var closed = false
        setFixture(onSetPin = { pin -> stored += pin; true }, onClose = { closed = true })

        enterDigits(listOf("9", "8", "7", "6"))
        composeRule.onNodeWithText("تاكيد رمز PIN").assertExists()
        enterDigits(listOf("9", "8", "7", "6"))
        composeRule.waitForIdle()

        assertEquals(listOf("9876"), stored)
        assertTrue(closed)
    }

    @Test
    fun confirmationAcceptsSupportedNumericRemoteDigits() {
        val stored = mutableListOf<String>()
        setFixture(onSetPin = { pin -> stored += pin; true }, onClose = {})

        enterDigits(listOf("1", "2", "3", "4"))
        composeRule.onNodeWithText("تاكيد رمز PIN").assertExists()

        composeRule.onNodeWithText("1").requestFocus()
        composeRule.onNodeWithText("1").performKeyInput { pressKey(Key.One) }
        composeRule.onNodeWithText("1").performKeyInput { pressKey(Key.Two) }
        composeRule.onNodeWithText("1").performKeyInput { pressKey(Key.Three) }
        composeRule.onNodeWithText("1").performKeyInput { pressKey(Key.Four) }
        composeRule.waitForIdle()

        assertEquals(listOf("1234"), stored)
    }

    @Test
    fun mismatchKeepsConfirmationEditableForRetry() {
        val stored = mutableListOf<String>()
        setFixture(onSetPin = { pin -> stored += pin; true }, onClose = {})

        enterDigits(listOf("1", "2", "3", "4"))
        composeRule.onNodeWithText("تاكيد رمز PIN").assertExists()
        enterDigits(listOf("4", "3", "2", "1"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("الرمزان غير متطابقين ، اعد التاكيد").assertExists()
        assertTrue(stored.isEmpty())

        enterDigits(listOf("1", "2", "3", "4"))
        composeRule.waitForIdle()
        assertEquals(listOf("1234"), stored)
    }

    @Test
    fun backspaceRemovesExactlyOneDigitInConfirmation() {
        val stored = mutableListOf<String>()
        setFixture(onSetPin = { pin -> stored += pin; true }, onClose = {})

        enterDigits(listOf("1", "2", "3", "4"))
        composeRule.onNodeWithText("تاكيد رمز PIN").assertExists()
        enterDigits(listOf("1", "2", "9"))
        tapKey("⌫")
        enterDigits(listOf("3", "4"))
        composeRule.waitForIdle()

        // A no-op backspace would complete the mismatching "1293" and store nothing.
        assertEquals(listOf("1234"), stored)
    }

    @Test
    fun cancelNeverSavesAndClosesExactlyOnce() {
        val stored = mutableListOf<String>()
        var closes = 0
        setFixture(onSetPin = { pin -> stored += pin; true }, onClose = { closes += 1 })

        enterDigits(listOf("1", "2", "3", "4"))
        composeRule.onNodeWithText("تاكيد رمز PIN").assertExists()
        composeRule.onNodeWithText("رجوع").performClick()
        composeRule.waitForIdle()

        assertTrue(stored.isEmpty())
        assertEquals(1, closes)
    }

    @Test
    fun onScreenDpadOkEntersBothStagesAndSavesOnce() {
        val stored = mutableListOf<String>()
        var closes = 0
        setFixture(onSetPin = { pin -> stored += pin; true }, onClose = { closes += 1 })

        dpadEnter(listOf("1", "3", "5", "7"))
        composeRule.onNodeWithText("تاكيد رمز PIN").assertExists()
        dpadEnter(listOf("1", "3", "5", "7"))
        composeRule.waitForIdle()

        assertEquals(listOf("1357"), stored)
        assertEquals(1, closes)
    }

    @Test
    fun bottomKeypadRowReachesTheVisibleBackAction() {
        val stored = mutableListOf<String>()
        setFixture(onSetPin = { pin -> stored += pin; true }, onClose = {})

        composeRule.onNodeWithText("0").requestFocus()
        composeRule.onNodeWithText("0").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("رجوع").assertIsFocused()

        composeRule.onNodeWithText("⌫").requestFocus()
        composeRule.onNodeWithText("⌫").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("رجوع").assertIsFocused()
        assertTrue(stored.isEmpty())
    }
    @Test
    fun directPanelStageChangeCompletesBothStages() {
        val completed = mutableListOf<String>()
        val stage = androidx.compose.runtime.mutableIntStateOf(0)
        composeRule.setContent {
            HulkTheme {
                PinPanel(
                    stateKey = "fixture",
                    isTv = true,
                    shortLandscape = false,
                    compactHeight = false,
                    tvCompact = true,
                    splitTv = true,
                    title = if (stage.intValue == 0) "الرمز الاول" else "الرمز الثاني",
                    subtitle = "",
                    errorMessage = null,
                    resetToken = stage.intValue,
                    inputEnabled = true,
                    onComplete = { pin ->
                        completed += pin
                        stage.intValue += 1
                    },
                    onCancel = {},
                )
            }
        }
        enterDigits(listOf("1", "2", "3", "4"))
        composeRule.onNodeWithText("الرمز الثاني").assertExists()
        enterDigits(listOf("5", "6", "7", "8"))
        composeRule.waitForIdle()
        assertEquals(listOf("1234", "5678"), completed)
    }

    @Test
    fun singleStageUnlockAcceptsFourDigitsForComparison() {
        val verified = mutableListOf<String>()
        composeRule.setContent {
            HulkTheme {
                ProfilePinUnlockScreen(
                    profile = fixtureProfile(),
                    isTv = true,
                    onVerify = { pin -> verified += pin; true },
                    onUnlocked = {},
                    onCancel = {},
                )
            }
        }
        enterDigits(listOf("1", "2", "3", "4"))
        composeRule.waitForIdle()
        assertEquals(listOf("1234"), verified)
    }

}