package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginKeyboardFocusTransitionControllerTest {
    @Test
    fun passwordToRememberThenShowPasswordThenLogin_hidesKeyboardOnlyOnce() {
        val controller = LoginKeyboardFocusTransitionController()
        var hideCount = 0

        controller.onTextInputFocused()
        controller.onNonTextFocused { hideCount += 1 }
        controller.onNonTextFocused { hideCount += 1 }
        controller.onNonTextFocused { hideCount += 1 }

        assertEquals(1, hideCount)
    }

    @Test
    fun returningToTextFieldThenLeavingAgain_allowsAnotherKeyboardHide() {
        val controller = LoginKeyboardFocusTransitionController()
        var hideCount = 0

        controller.onTextInputFocused()
        controller.onNonTextFocused { hideCount += 1 }
        controller.onNonTextFocused { hideCount += 1 }

        controller.onTextInputFocused()
        controller.onNonTextFocused { hideCount += 1 }
        controller.onNonTextFocused { hideCount += 1 }

        assertEquals(2, hideCount)
    }
}
