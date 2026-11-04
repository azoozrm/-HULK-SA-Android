package sa.hulksa.player.ui.screens

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import sa.hulksa.player.data.*

class LoginConnectionCopyPolicyTest {
    private val cases = listOf(
        Triple(LoginFailureCategory.SERVICE_REJECTED, "الخدمة رفضت الاتصال", LoginErrorTarget.NONE),
        Triple(LoginFailureCategory.INVALID_CODE, "الكود غير صحيح", LoginErrorTarget.ACCESS_CODE),
        Triple(LoginFailureCategory.DISABLED_CODE, "الكود غير مفعل", LoginErrorTarget.ACCESS_CODE),
        Triple(LoginFailureCategory.INVALID_CREDENTIALS, "بيانات الدخول غير صحيحة", LoginErrorTarget.USERNAME),
        Triple(LoginFailureCategory.EXPIRED_SUBSCRIPTION, "انتهت صلاحية اشتراكك", LoginErrorTarget.NONE),
        Triple(LoginFailureCategory.INACTIVE_SUBSCRIPTION, "الاشتراك غير فعال", LoginErrorTarget.NONE),
        Triple(LoginFailureCategory.CONNECTION_UNCERTAIN, "تعذر الاتصال بالخدمة", LoginErrorTarget.NONE),
        Triple(LoginFailureCategory.MALFORMED, "تعذر تسجيل الدخول", LoginErrorTarget.NONE),
        Triple(LoginFailureCategory.UNKNOWN, "تعذر تسجيل الدخول", LoginErrorTarget.NONE),
        Triple(LoginFailureCategory.REQUIRED_ACCESS_CODE, "ادخل كود الدخول", LoginErrorTarget.ACCESS_CODE),
        Triple(LoginFailureCategory.REQUIRED_USERNAME, "ادخل اسم المستخدم", LoginErrorTarget.USERNAME),
        Triple(LoginFailureCategory.REQUIRED_PASSWORD, "ادخل كلمة المرور", LoginErrorTarget.PASSWORD),
        Triple(LoginFailureCategory.LINK_UNAVAILABLE, "تعذر فتح الرابط", LoginErrorTarget.NONE),
    )

    @Test fun semanticCausesKeepExactCopyAndFieldFocusWhenConnectivityChanges() {
        for ((category, text, target) in cases) for (network in LoginConnectivityEvidence.entries) {
            val result = loginErrorPresentation(LoginFailure(category, LoginFailureStage.PROVIDER), network)
            assertEquals("$category/$network", text, result.displayText)
            assertEquals(target, result.target)
            assertFalse(result.confirmedOffline)
        }
    }

    @Test fun actualTransportAtBothStagesUsesOfflineOnlyWithAuthoritativeOfflineEvidence() {
        for (stage in listOf(LoginFailureStage.RESOLVER, LoginFailureStage.PROVIDER)) {
            for (network in LoginConnectivityEvidence.entries) {
                val result = loginErrorPresentation(LoginFailure(LoginFailureCategory.TRANSPORT, stage), network)
                assertEquals(if (network == LoginConnectivityEvidence.OFFLINE) "لا يوجد اتصال بالانترنت" else "تعذر الاتصال بالخدمة", result.displayText)
                assertEquals(network == LoginConnectivityEvidence.OFFLINE, result.confirmedOffline)
                assertEquals(LoginErrorTarget.NONE, result.target)
            }
        }
    }

    @Test fun missingOrUnvalidatedPlatformConnectivityIsUnknownInsteadOfOffline() {
        assertEquals(LoginConnectivityEvidence.UNKNOWN, validatedConnectivityEvidence(null, false, false, false))
        assertEquals(LoginConnectivityEvidence.UNKNOWN, validatedConnectivityEvidence(true, false, false, false))
        assertEquals(LoginConnectivityEvidence.UNKNOWN, validatedConnectivityEvidence(true, true, true, false))
        assertEquals(LoginConnectivityEvidence.OFFLINE, validatedConnectivityEvidence(false, false, false, false))
        assertEquals(LoginConnectivityEvidence.ONLINE, validatedConnectivityEvidence(true, true, true, true))
    }

    @Test fun requiredFieldsHaveIndependentLogicalTargetsWithoutParsingArabic() {
        val inputs = listOf(
            Triple("", "", "") to LoginFailureCategory.REQUIRED_ACCESS_CODE,
            Triple("Valid-Code", " ", "") to LoginFailureCategory.REQUIRED_USERNAME,
            Triple("Valid-Code", "customer", "") to LoginFailureCategory.REQUIRED_PASSWORD,
        )
        for ((input, category) in inputs) {
            val failure = loginValidationFailure(input.first, input.second, input.third)!!
            assertEquals(category, failure.category)
            assertEquals(LoginFailureStage.VALIDATION, failure.stage)
            assertNotEquals(LoginErrorTarget.NONE, loginErrorPresentation(failure, LoginConnectivityEvidence.UNKNOWN).target)
        }
        assertNull(loginValidationFailure(" Exact_Code! ", " customer ", " password "))
    }

    @Test fun websiteAndAuthenticationFailuresHaveOneExplicitOwner() {
        val auth = loginErrorPresentation(PortalException.InvalidAccessCode.loginFailure, LoginConnectivityEvidence.ONLINE)
        assertEquals(auth, loginNoticePresentation(auth, websiteFailed = true))
        val website = loginNoticePresentation(LoginErrorPresentation(null), websiteFailed = true)
        assertEquals("تعذر فتح الرابط", website.displayText)
        assertEquals(LoginNoticeOwner.WEBSITE, website.owner)
        assertNull(loginNoticePresentation(LoginErrorPresentation(null), websiteFailed = false).displayText)
    }

    @Test fun localStorageOrUnknownExceptionNeverShowsPrivateMessageOrClaimsOffline() {
        for (error in listOf(IOException("synthetic-private-value"), IllegalStateException("synthetic-private-value"))) {
            val result = loginErrorPresentation(loginFailureFor(error), LoginConnectivityEvidence.OFFLINE)
            assertEquals("تعذر تسجيل الدخول", result.displayText)
            assertFalse(result.confirmedOffline)
        }
        assertThrows(CancellationException::class.java) { loginFailureFor(CancellationException("cancelled")) }
    }

    @Test fun sharedExceptionsRetainTheirNonLoginCopyWhileLoginUsesCuratedCopy() {
        assertEquals("تعذر الاتصال بخدمة HULK ، حاول مرة اخرى", PortalException.ServiceUnavailable.message)
        assertEquals("تعذر الاتصال بالخدمة", loginErrorPresentation(PortalException.ServiceUnavailable.loginFailure, LoginConnectivityEvidence.OFFLINE).displayText)
        assertNull(loginErrorPresentation(null, LoginConnectivityEvidence.OFFLINE).displayText)
    }
}
