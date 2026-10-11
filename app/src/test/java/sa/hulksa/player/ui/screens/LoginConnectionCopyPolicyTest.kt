package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.PortalException

class LoginConnectionCopyPolicyTest {
    private val serviceMessage = PortalException.ServiceUnavailable.message.orEmpty()

    @Test
    fun confirmedOfflineTransportFailureShowsOfflineCopy() {
        val presentation = loginErrorPresentation(
            errorMessage = serviceMessage,
            serviceUnavailableMessage = serviceMessage,
            confirmedOffline = true,
        )
        assertEquals(
            "لا يوجد اتصال بالانترنت ، تحقق من اتصالك وحاول مرة اخرى",
            presentation.displayText,
        )
        assertTrue(presentation.confirmedOffline)
    }

    @Test
    fun serviceFailureWithInternetShowsTheExactServiceRetryCopy() {
        val presentation = loginErrorPresentation(
            errorMessage = serviceMessage,
            serviceUnavailableMessage = serviceMessage,
            confirmedOffline = false,
        )
        assertEquals("تعذر الاتصال بخدمة HULK ، حاول مرة اخرى", presentation.displayText)
        assertFalse(presentation.confirmedOffline)
    }

    @Test
    fun validationAndCredentialErrorsNeverBecomeOfflineWording() {
        val validation = "ادخل كود دخول صحيح"
        val presentation = loginErrorPresentation(
            errorMessage = validation,
            serviceUnavailableMessage = serviceMessage,
            confirmedOffline = true,
        )
        assertEquals(validation, presentation.displayText)
        assertFalse(presentation.confirmedOffline)
    }

    @Test
    fun missingErrorHasNoNotice() {
        assertNull(
            loginErrorPresentation(
                errorMessage = null,
                serviceUnavailableMessage = serviceMessage,
                confirmedOffline = true,
            ).displayText,
        )
    }

    @Test
    fun requiredConnectionCopyHasNoPeriodsIncludingAfterHulk() {
        assertEquals("تعذر الاتصال بخدمة HULK ، حاول مرة اخرى", serviceMessage)
        assertFalse(serviceMessage.contains("HULK."))
        assertFalse(serviceMessage.endsWith("."))
        assertFalse(LOGIN_OFFLINE_MESSAGE.endsWith("."))
        assertFalse(LOGIN_OFFLINE_MESSAGE.endsWith("\u2026"))
    }
}
