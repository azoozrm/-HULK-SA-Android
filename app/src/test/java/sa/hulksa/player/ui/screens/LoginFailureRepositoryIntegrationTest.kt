package sa.hulksa.player.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.WifiOff
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.ManualParentAuthProofRegistry
import sa.hulksa.player.finishLoginFailure
import sa.hulksa.player.finishExpiredOwnerFailure
import sa.hulksa.player.finishInvalidTvDeepLink
import sa.hulksa.player.rejectLoginValidation
import sa.hulksa.player.data.*

/** Real repository, encrypted synthetic vault and account stores through actual Login state/presentation. */
class LoginFailureRepositoryIntegrationTest {
    @After fun releaseProcessOwnership() {
        AuthenticationAttemptRegistry.invalidate()
        AuthenticatedSessionRegistry.clear()
        ManualParentAuthProofRegistry.invalidateAll()
    }

    @Test fun repeatedRequiredFieldRejectionPublishesAnotherFocusOccurrenceWithoutChangingOwner() {
        val existing = HulkUiState(screen = HulkScreen.LOGIN, favorites = setOf("synthetic-retained"))
        val failure = loginValidationFailure("Synthetic-Code", "", "")!!
        val first = existing.rejectLoginValidation(failure)
        val second = first.rejectLoginValidation(failure)
        assertEquals(1L, first.loginValidationAttempt)
        assertEquals(2L, second.loginValidationAttempt)
        assertNotEquals(first, second)
        assertEquals(first.loginFailure, second.loginFailure)
        assertEquals(existing.favorites, second.favorites)
        val notice = loginErrorPresentation(second.loginFailure, LoginConnectivityEvidence.OFFLINE)
        assertEquals("ادخل اسم المستخدم", notice.displayText)
        assertEquals(LoginErrorTarget.USERNAME, notice.target)
        assertFalse(notice.confirmedOffline)
    }

    @Test fun expiredCredentialCleanupFailureReachesLoginAsGenericLocalFailureAndPreservesData() {
        val existing = HulkUiState(screen = HulkScreen.LOGIN, isStarting = true, isLoading = true,
            favorites = setOf("synthetic-retained"))
        val updated = existing.finishExpiredOwnerFailure("synthetic private persistence detail")
        val notice = loginErrorPresentation(updated.loginFailure, LoginConnectivityEvidence.OFFLINE)
        assertEquals("تعذر تسجيل الدخول", notice.displayText)
        assertEquals(LoginErrorTarget.NONE, notice.target)
        assertFalse(notice.confirmedOffline)
        assertFalse(updated.isStarting)
        assertFalse(updated.isLoading)
        assertEquals(existing.screen, updated.screen)
        assertEquals(existing.favorites, updated.favorites)
    }

    @Test fun invalidTvLinkKeepsExistingAuthenticationPriorityAndMainFeedback() {
        val existing = HulkUiState(screen = HulkScreen.LOGIN, favorites = setOf("synthetic-retained"))
        val updated = existing.finishInvalidTvDeepLink()
        val notice = loginErrorPresentation(updated.loginFailure, LoginConnectivityEvidence.OFFLINE)
        assertEquals("تعذر فتح الرابط", notice.displayText)
        assertEquals(LoginNoticeOwner.WEBSITE, notice.owner)
        assertEquals(LoginErrorTarget.NONE, notice.target)
        assertFalse(notice.confirmedOffline)
        val auth = existing.copy(loginFailure = PortalException.InvalidAccessCode.loginFailure)
        assertEquals(auth.loginFailure, auth.finishInvalidTvDeepLink().loginFailure)
        assertEquals(LoginErrorTarget.ACCESS_CODE,
            loginErrorPresentation(auth.finishInvalidTvDeepLink().loginFailure, LoginConnectivityEvidence.UNKNOWN).target)
        val main = existing.copy(screen = HulkScreen.MAIN).finishInvalidTvDeepLink()
        assertEquals(HulkScreen.MAIN, main.screen)
        assertEquals("تعذر فتح هذا الرابط", main.errorMessage)
        assertEquals(existing.favorites, main.favorites)
        assertNull(main.loginFailure)
    }

    @Test fun repositoryFailureStageCategoryCopyIconAndFocusReachLoginWithoutDestroyingOwnerData() = runBlocking {
        val errors = listOf(
            PortalException.Transport(IOException("synthetic")),
            XtreamException.Network(IOException("synthetic")),
            PortalException.InvalidAccessCode, PortalException.ResellerInactive,
            PortalException.ServiceRejected(503), XtreamException.ServiceBlocked(403, "synthetic"),
            XtreamException.InvalidCredentials, XtreamException.SubscriptionExpired,
            XtreamException.SubscriptionInactive, XtreamException.InvalidResponse,
            XtreamException.Http(408),
        )
        for (error in errors) for (network in LoginConnectivityEvidence.entries) {
            val fixture = AutomaticRestorationFixture()
            val owner = fixture.seedExistingOwner()
            fixture.vault.save(fixture.credentials())
            if (error is PortalException) fixture.resolverFailure = error
            else fixture.authenticate = { _, _ -> throw error }
            val observed = runCatching { fixture.repository.beginAuthentication(fixture.credentials(), true) }.exceptionOrNull()!!
            assertSame(error, observed)
            val state = HulkUiState(isStarting = true, isLoading = true).finishLoginFailure(observed)
            val expected = loginFailureFor(error)
            assertEquals(expected, state.loginFailure)
            assertFalse(state.isLoading)
            assertFalse(state.isStarting)
            val result = loginErrorPresentation(state.loginFailure, network)
            val offline = expected.category == LoginFailureCategory.TRANSPORT && network == LoginConnectivityEvidence.OFFLINE
            assertEquals(offline, result.confirmedOffline)
            assertEquals(if (offline) Icons.Rounded.WifiOff else Icons.Outlined.ErrorOutline, moviesErrorIcon(result.confirmedOffline))
            when (expected.category) {
                LoginFailureCategory.INVALID_CODE -> { assertEquals("الكود غير صحيح", result.displayText); assertEquals(LoginErrorTarget.ACCESS_CODE, result.target) }
                LoginFailureCategory.DISABLED_CODE -> { assertEquals("الكود غير مفعل", result.displayText); assertEquals(LoginErrorTarget.ACCESS_CODE, result.target) }
                LoginFailureCategory.INVALID_CREDENTIALS -> { assertEquals("بيانات الدخول غير صحيحة", result.displayText); assertEquals(LoginErrorTarget.USERNAME, result.target) }
                LoginFailureCategory.EXPIRED_SUBSCRIPTION -> assertEquals("انتهت صلاحية اشتراكك", result.displayText)
                LoginFailureCategory.INACTIVE_SUBSCRIPTION -> assertEquals("الاشتراك غير فعال", result.displayText)
                LoginFailureCategory.SERVICE_REJECTED -> assertEquals("الخدمة رفضت الاتصال", result.displayText)
                LoginFailureCategory.MALFORMED -> assertEquals("تعذر تسجيل الدخول", result.displayText)
                else -> assertEquals(if (offline) "لا يوجد اتصال بالانترنت" else "تعذر الاتصال بالخدمة", result.displayText)
            }
            fixture.assertOwnedData(owner)
            assertEquals(fixture.credentials(), fixture.vault.load())
            assertFalse(AuthenticationAttemptRegistry.hasActiveAttempt())
        }
    }
}
