package sa.hulksa.player.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.then
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.data.LoginFailure
import sa.hulksa.player.data.LoginFailureCategory
import sa.hulksa.player.data.LoginFailureStage
import sa.hulksa.player.PairingUiState
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.rejectLoginValidation
import sa.hulksa.player.finishLoginFailure
import sa.hulksa.player.data.loginValidationFailure
import sa.hulksa.player.data.PortalException
import sa.hulksa.player.data.XtreamException
import sa.hulksa.player.ui.theme.HulkTheme

/**
 * Real Compose geometry on an isolated disposable Debug target only. No screenshots, real
 * credentials, authentication or account-store writes. Root runs this class directly without
 * Orchestrator/clearPackageData; never against either persistent engineering or primary package.
 */
class LoginNoticeGeometryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private data class Viewport(val width: Int, val height: Int, val tv: Boolean, val fontScale: Float)

    private val viewport = mutableStateOf(Viewport(960, 540, true, 1f))
    private val error = mutableStateOf<String?>(null)

    @Test
    fun freshTvLargeFontWithConsistentConfigurationKeepsCredentialFocusAndSingleLine() {
        viewport.value = Viewport(960, 540, true, 1.3f)
        setScreen()
        composeRule.waitForIdle()
        val baseline = actionBounds()
        for (id in listOf("OFFLINE", "REFUSED", "INVALID_CODE", "EXPIRED", "CREDENTIALS")) {
            composeRule.runOnIdle { error.value = id }
            composeRule.waitForIdle()
            if (id == "CREDENTIALS") composeRule.onNodeWithContentDescription("اسم المستخدم").assertIsFocused()
            val layouts = mutableListOf<TextLayoutResult>()
            val message = messages.getValue(id)
            composeRule.onNodeWithText(message, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals(1, layout.lineCount)
            assertTrue(!layout.didOverflowWidth && !layout.didOverflowHeight)
            assertEquals(message.length, layout.getLineEnd(0, visibleEnd = true))
            assertTrue(sameBounds(baseline, actionBounds()))
            Log.i(LOG_TAG, "consistentConfig/960x540/font1.3/$id fullLine=true stableActions=true")
            composeRule.runOnIdle { error.value = null }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun directUsernameFocusWithoutAnyNoticeAtLargeFont() {
        viewport.value = Viewport(960, 540, true, 1.3f)
        setScreen()
        composeRule.waitForIdle()
            Log.i(LOG_TAG, "directUsername/noNotice/font1.3/beforeFocus")
            composeRule.onNodeWithContentDescription("اسم المستخدم")
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("اسم المستخدم").assertIsFocused()
            Log.i(LOG_TAG, "directUsername/noNotice/font1.3/afterFocus")
    }

    @Test
    fun replacingAndClearingNoticesPreservesAbsoluteActionBoundsOnRoomyPhone() {
        viewport.value = Viewport(360, 800, false, 1f)
        setScreen()
        for (scale in listOf(1f, 1.3f)) {
            composeRule.runOnIdle { viewport.value = Viewport(360, 800, false, scale); error.value = null }
            composeRule.waitForIdle()
            val baseline = absoluteActionBounds()
            for (id in listOf("REFUSED", "EXPIRED", "UNCLASSIFIED", null)) {
                composeRule.runOnIdle { error.value = id }
                composeRule.waitForIdle()
                assertTrue("absolute actions changed at font=$scale/$id", sameBounds(baseline, absoluteActionBounds()))
                if (id != null) {
                    val text = messages.getValue(id)
                    val layouts = mutableListOf<TextLayoutResult>()
                    composeRule.onNodeWithText(text, useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    Log.i(LOG_TAG, "absoluteFit/360x800/font=$scale/$id lines=${layout.lineCount}" +
                        " size=${layout.size} overflowWidth=${layout.didOverflowWidth} overflowHeight=${layout.didOverflowHeight}")
                    assertEquals(1, layout.lineCount)
                    assertTrue("$scale/$id layout=${layout.size}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}",
                        !layout.didOverflowWidth && !layout.didOverflowHeight)
                    assertEquals(text.length, layout.getLineEnd(0, visibleEnd = true))
                }
                val outer = composeRule.onNodeWithTag("login-test-viewport").getUnclippedBoundsInRoot()
                for (caption in actionCaptions) {
                    action(caption).assertIsDisplayed()
                    val inner = action(caption).getUnclippedBoundsInRoot()
                    assertTrue("$scale/$id/$caption clipped", inner.left >= outer.left && inner.top >= outer.top &&
                        inner.right <= outer.right && inner.bottom <= outer.bottom)
                }
                Log.i(LOG_TAG, "absolute/360x800/font=$scale/$id stableActions=true bounds=$baseline")
            }
        }
    }

    @Test
    fun submittingTheSameMissingUsernameAgainRefocusesTheField() {
        val state = mutableStateOf(HulkUiState(isStarting = false))
        setScreen(
            failure = { state.value.loginFailure },
            validationAttempt = { state.value.loginValidationAttempt },
            onLogin = { code, username, password, _ ->
                state.value = state.value.rejectLoginValidation(loginValidationFailure(code, username, password)!!)
            },
        )
        composeRule.onNodeWithContentDescription("كود الدخول").performTextInput("Synthetic-Code")
        repeat(2) { index ->
            action("تسجيل الدخول").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            action("تسجيل الدخول").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("اسم المستخدم").assertIsFocused()
            assertEquals((index + 1).toLong(), state.value.loginValidationAttempt)
            composeRule.onNodeWithText("ادخل اسم المستخدم").assertIsDisplayed()
        }
    }

    @Test
    fun viewModelFailureProjectionReachesRealLoginCopyAndFocusWithoutPrivateMessages() {
        val state = mutableStateOf(HulkUiState(isStarting = false, favorites = setOf("synthetic-retained")))
        setScreen(failure = { state.value.loginFailure }, connectivity = { LoginConnectivityEvidence.OFFLINE })
        val cases = listOf(
            Triple(PortalException.Transport(IOException("synthetic-private")), "لا يوجد اتصال بالانترنت", null),
            Triple(XtreamException.Network(IOException("synthetic-private")), "لا يوجد اتصال بالانترنت", null),
            Triple(PortalException.InvalidAccessCode, "الكود غير صحيح", "كود الدخول"),
            Triple(PortalException.ResellerInactive, "الكود غير مفعل", "كود الدخول"),
            Triple(XtreamException.InvalidCredentials, "بيانات الدخول غير صحيحة", "اسم المستخدم"),
            Triple(XtreamException.SubscriptionExpired, "انتهت صلاحية اشتراكك", null),
            Triple(XtreamException.SubscriptionInactive, "الاشتراك غير فعال", null),
            Triple(XtreamException.InvalidResponse, "تعذر تسجيل الدخول", null),
            Triple(XtreamException.Http(503), "الخدمة رفضت الاتصال", null),
            Triple(XtreamException.Http(408), "تعذر الاتصال بالخدمة", null),
        )
        for ((cause, text, field) in cases) {
            composeRule.runOnIdle { state.value = state.value.finishLoginFailure(cause) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(text).assertIsDisplayed()
            field?.let { composeRule.onNodeWithContentDescription(it).assertIsFocused() }
            assertEquals(setOf("synthetic-retained"), state.value.favorites)
            assertTrue(!state.value.isLoading && !state.value.isStarting)
            assertEquals(0, composeRule.onAllNodes(hasText("synthetic-private", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().size)
        }
    }

    @Test
    fun curatedNoticesStaySingleLineAndKeepAllActionsStableAcrossViewportsAndFontScales() {
        setScreen()
        val failures = mutableListOf<String>()
        val viewports = listOf(
            Viewport(960, 540, true, 1f), Viewport(960, 540, true, 1.3f),
            Viewport(360, 800, false, 1f), Viewport(360, 800, false, 1.3f),
            Viewport(360, 520, false, 1f), Viewport(360, 520, false, 1.3f),
            Viewport(320, 520, false, 1f), Viewport(320, 520, false, 1.3f),
            // A constrained usable viewport models the space remaining above an IME; it does
            // not claim actual phone keyboard/window-inset interaction on a TV test surface.
            Viewport(360, 320, false, 1f),
        )
        for (size in viewports) {
            composeRule.runOnIdle { viewport.value = size; error.value = null }
            composeRule.waitForIdle()
            val baseline = actionBounds()
            for ((id, message) in messages) {
                composeRule.runOnIdle { error.value = id }
                composeRule.waitForIdle()
                val focusedField = when (id) {
                    "INVALID_CODE", "DISABLED_CODE", "REQUIRED_CODE" -> "كود الدخول"
                    "CREDENTIALS", "REQUIRED_USERNAME" -> "اسم المستخدم"
                    "REQUIRED_PASSWORD" -> "كلمة المرور"
                    else -> null
                }
                focusedField?.let { composeRule.onNodeWithContentDescription(it).assertIsFocused() }
                val layouts = mutableListOf<TextLayoutResult>()
                composeRule.onNodeWithText(message, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                val fullLine = layout.lineCount == 1 && !layout.didOverflowWidth &&
                    !layout.didOverflowHeight && layout.getLineEnd(0, visibleEnd = true) == message.length
                val current = actionBounds()
                val stable = sameBounds(baseline, current)
                val label = "${size.width}x${size.height}/tv=${size.tv}/font=${size.fontScale}/$id"
                Log.i(LOG_TAG, "$label lines=${layout.lineCount} textPx=${layout.size.width}x${layout.size.height}" +
                    " overflowWidth=${layout.didOverflowWidth} overflowHeight=${layout.didOverflowHeight}" +
                    " fullLine=$fullLine stableActions=$stable baseline=$baseline actual=$current")
                if (!fullLine) failures += "$label: text does not fit completely on one line"
                if (!stable) failures += "$label: action bounds changed when notice appeared/replaced"
                composeRule.runOnIdle { error.value = null }
                composeRule.waitForIdle()
                if (!sameBounds(baseline, actionBounds())) failures += "$label: action bounds changed on clear"
            }
            for (caption in actionCaptions) {
                action(caption).performScrollTo().assertIsDisplayed()
                val outer = composeRule.onNodeWithTag("login-test-viewport").getUnclippedBoundsInRoot()
                val inner = action(caption).getUnclippedBoundsInRoot()
                assertTrue("$size/$caption clipped after scroll", inner.left >= outer.left &&
                    inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun simultaneousWebsiteAndLoginFailuresUseOneStatusWithoutMovingActions() {
        viewport.value = Viewport(360, 800, false, 1f)
        setScreen()
        composeRule.waitForIdle()
        val baseline = actionBounds()
        action("اشتراك جديد").performClick()
        composeRule.waitForIdle()
        val afterWebsite = actionBounds()
        composeRule.onNodeWithText(LOGIN_WEBSITE_FAILURE_MESSAGE).assertIsDisplayed()
        composeRule.runOnIdle { error.value = "INVALID_CODE" }
        composeRule.waitForIdle()
        val afterBoth = actionBounds()
        assertEquals("A current authentication failure owns the one Login status", 0,
            composeRule.onAllNodes(hasText(LOGIN_WEBSITE_FAILURE_MESSAGE), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue("Website failure displaced actions: $baseline -> $afterWebsite", sameBounds(baseline, afterWebsite))
        assertTrue("Simultaneous failures displaced actions: $baseline -> $afterBoth", sameBounds(baseline, afterBoth))
    }

    @Test
    fun noticeHasPoliteLiveRegionOnlyWhenPresentAndDpadKeepsAllActionsReachable() {
        setScreen()
        composeRule.waitForIdle()
        val liveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        assertEquals(0, composeRule.onAllNodes(liveRegion, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.runOnIdle { error.value = "REFUSED" }
        composeRule.waitForIdle()
        val regions = composeRule.onAllNodes(liveRegion, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, regions.size)
        assertEquals(LiveRegionMode.Polite, regions.single().config[SemanticsProperties.LiveRegion])
        action("تسجيل الدخول").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        action("تسجيل الدخول").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        action("اشتراك جديد").assertIsFocused().assertIsDisplayed().performKeyInput { pressKey(Key.DirectionDown) }
        action("الدخول السريع").assertIsFocused().assertIsDisplayed().performKeyInput { pressKey(Key.DirectionUp) }
        action("اشتراك جديد").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        action("تسجيل الدخول").assertIsFocused().assertIsDisplayed()
        composeRule.runOnIdle { error.value = null }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodes(liveRegion, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    private fun setScreen(
        failure: () -> LoginFailure? = { error.value?.let(::failureFor) },
        validationAttempt: () -> Long = { 0 },
        connectivity: () -> LoginConnectivityEvidence = {
            if (error.value == "OFFLINE") LoginConnectivityEvidence.OFFLINE else LoginConnectivityEvidence.ONLINE
        },
        onLogin: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    ) {
        composeRule.setContent {
            val size = viewport.value
            // Override the actual Configuration, resource context, font resolver and density
            // together; a Density-only override leaves BasicTextField/platform owners mismatched.
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(size.width.dp, size.height.dp)) then
                    DeviceConfigurationOverride.FontScale(size.fontScale),
            ) {
            CompositionLocalProvider(
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) { throw IllegalStateException("Synthetic browser unavailable") }
                },
            ) {
                Box(Modifier.fillMaxSize().testTag("login-test-viewport")) {
                    // Each viewport models a separate configured screen, not a live density
                    // mutation under a retained focused text-input session. Error states below
                    // still reuse this exact same screen throughout each configuration.
                    key(size) {
                    HulkTheme {
                        LoginScreen(
                            isTv = size.tv,
                            pairingOffered = true,
                            pairing = PairingUiState(),
                            isStarting = false,
                            isLoading = false,
                            failure = failure(),
                            loginValidationAttempt = validationAttempt(),
                            connectivity = connectivity(),
                            onLogin = onLogin,
                            onStartPairing = {},
                            onRetryPairing = {},
                            onRegeneratePairing = {},
                            onCancelPairing = {},
                        )
                    }
                    }
                }
            }
            }
        }
    }

    private fun action(caption: String) = composeRule.onNode(hasText(caption) and hasClickAction())

    private fun actionBounds(): Map<String, List<Float>> {
        // Field-relative geometry rejects notice growth while allowing the existing legitimate
        // focus/IME scroll to translate the whole form on a short usable viewport.
        val field = composeRule.onNodeWithContentDescription("كود الدخول").getUnclippedBoundsInRoot()
        return actionCaptions.associateWith { caption ->
            val bounds = action(caption).getUnclippedBoundsInRoot()
            listOf((bounds.left - field.left).value, (bounds.top - field.top).value,
                (bounds.right - field.left).value, (bounds.bottom - field.top).value)
        }
    }

    private fun absoluteActionBounds(): Map<String, List<Float>> = actionCaptions.associateWith { caption ->
        val bounds = action(caption).getUnclippedBoundsInRoot()
        listOf(bounds.left.value, bounds.top.value, bounds.right.value, bounds.bottom.value)
    }

    private fun sameBounds(a: Map<String, List<Float>>, b: Map<String, List<Float>>): Boolean =
        a.keys == b.keys && a.all { (key, values) -> values.zip(b.getValue(key)).all { (x, y) -> abs(x - y) < .5f } }

    private fun failureFor(id: String): LoginFailure {
        val category = when (id) {
            "OFFLINE", "TRANSPORT" -> LoginFailureCategory.TRANSPORT
            "REFUSED" -> LoginFailureCategory.SERVICE_REJECTED
            "INVALID_CODE" -> LoginFailureCategory.INVALID_CODE
            "EXPIRED" -> LoginFailureCategory.EXPIRED_SUBSCRIPTION
            "CREDENTIALS" -> LoginFailureCategory.INVALID_CREDENTIALS
            "INACTIVE" -> LoginFailureCategory.INACTIVE_SUBSCRIPTION
            "DISABLED_CODE" -> LoginFailureCategory.DISABLED_CODE
            "REQUIRED_CODE" -> LoginFailureCategory.REQUIRED_ACCESS_CODE
            "REQUIRED_USERNAME" -> LoginFailureCategory.REQUIRED_USERNAME
            "REQUIRED_PASSWORD" -> LoginFailureCategory.REQUIRED_PASSWORD
            "LINK_UNAVAILABLE" -> LoginFailureCategory.LINK_UNAVAILABLE
            else -> LoginFailureCategory.UNKNOWN
        }
        val stage = when {
            id.startsWith("REQUIRED") || id == "LINK_UNAVAILABLE" -> LoginFailureStage.VALIDATION
            id == "INVALID_CODE" || id == "DISABLED_CODE" -> LoginFailureStage.RESOLVER
            else -> LoginFailureStage.PROVIDER
        }
        return LoginFailure(category, stage)
    }

    private companion object {
        const val LOG_TAG = "R11LoginGeometry"
        val actionCaptions = listOf("تسجيل الدخول", "اشتراك جديد", "الدخول السريع")
        val messages = linkedMapOf(
            "OFFLINE" to "لا يوجد اتصال بالانترنت",
            "REFUSED" to "الخدمة رفضت الاتصال",
            "INVALID_CODE" to "الكود غير صحيح",
            "EXPIRED" to "انتهت صلاحية اشتراكك",
            "CREDENTIALS" to "بيانات الدخول غير صحيحة",
            "INACTIVE" to "الاشتراك غير فعال",
            "DISABLED_CODE" to "الكود غير مفعل",
            "TRANSPORT" to "تعذر الاتصال بالخدمة",
            "UNCLASSIFIED" to "تعذر تسجيل الدخول",
            "REQUIRED_CODE" to "ادخل كود الدخول",
            "REQUIRED_USERNAME" to "ادخل اسم المستخدم",
            "REQUIRED_PASSWORD" to "ادخل كلمة المرور",
            "LINK_UNAVAILABLE" to "تعذر فتح الرابط",
        )
    }
}
