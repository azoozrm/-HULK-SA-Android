package sa.hulksa.player.compatibilityv2

import android.app.Activity
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.MainActivity
import sa.hulksa.player.TvMainActivity
import sa.hulksa.player.ui.adaptive.restoreOrientationRequest

@RunWith(AndroidJUnit4::class)
class CompatibilityV2InstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private data class FakeAccessibilityNode(
        val id: String,
        val clickable: Boolean,
        val parent: FakeAccessibilityNode? = null,
    )

    private class FakeStaleObjectException : RuntimeException()

    private fun isTelevision(): Boolean {
        val mode = (targetContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType
        return mode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun explicitLauncherIntent(): Intent {
        val activityClass: Class<out Activity> =
            if (isTelevision()) TvMainActivity::class.java else MainActivity::class.java
        val launcherCategory =
            if (isTelevision()) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER

        return Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(targetContext.packageName, activityClass.name)
            addCategory(launcherCategory)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private fun launchMainPackage(): Boolean {
        targetContext.startActivity(explicitLauncherIntent())
        return device.wait(Until.hasObject(By.pkg(targetContext.packageName).depth(0)), 15_000L)
    }

    private fun launchScenario(): ActivityScenario<Activity> =
        ActivityScenario.launch(explicitLauncherIntent())

    private fun waitForDisplayOrientation(landscape: Boolean, timeoutMs: Long = 10_000L): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val matches = if (landscape) {
                device.displayWidth > device.displayHeight
            } else {
                device.displayHeight > device.displayWidth
            }
            if (matches) return true
            SystemClock.sleep(100L)
        }
        return false
    }

    private fun <Node> nearestClickableOwner(
        actionNode: Node,
        parentOf: (Node) -> Node?,
        isClickable: (Node) -> Boolean,
        maxAncestorDepth: Int = 6,
    ): Node? {
        var current: Node? = actionNode
        repeat(maxAncestorDepth + 1) {
            val candidate = current ?: return null
            if (isClickable(candidate)) return candidate
            current = parentOf(candidate)
        }
        return null
    }

    private fun <Node> dismissOptionalUpdateWithinDeadline(
        timeoutMs: Long,
        nowMs: () -> Long,
        isOverlayPresent: () -> Boolean,
        resolveLaterAction: () -> Node?,
        parentOf: (Node) -> Node?,
        isClickable: (Node) -> Boolean,
        click: (Node) -> Unit,
        waitForOverlayGone: (Long) -> Boolean,
        isStaleObject: (Throwable) -> Boolean,
    ): Boolean {
        if (!isOverlayPresent()) return true

        val deadline = nowMs() + timeoutMs
        while (nowMs() < deadline) {
            if (!isOverlayPresent()) return true
            try {
                val actionNode = resolveLaterAction()
                val clickableOwner = actionNode?.let { node ->
                    nearestClickableOwner(
                        actionNode = node,
                        parentOf = parentOf,
                        isClickable = isClickable,
                    )
                }
                if (clickableOwner != null) {
                    click(clickableOwner)
                }
            } catch (error: Throwable) {
                if (!isStaleObject(error)) throw error
                // Re-resolve the action from the current tree on the next bounded probe.
            }

            val remaining = deadline - nowMs()
            if (remaining > 0L && waitForOverlayGone(minOf(remaining, 500L))) {
                return true
            }
        }
        return !isOverlayPresent()
    }

    private fun dismissOptionalUpdateIfPresent(timeoutMs: Long = 5_000L): Boolean {
        val titleSelector = By.text("يتوفر تحديث جديد")
        val laterSelector = By.text("لاحقًا")
        return dismissOptionalUpdateWithinDeadline(
            timeoutMs = timeoutMs,
            nowMs = { SystemClock.uptimeMillis() },
            isOverlayPresent = { device.hasObject(titleSelector) },
            resolveLaterAction = { device.findObject(laterSelector) },
            parentOf = { node -> node.parent },
            isClickable = { node -> node.isClickable },
            click = { node -> node.click() },
            waitForOverlayGone = { waitMs -> device.wait(Until.gone(titleSelector), waitMs) },
            isStaleObject = { error -> error is StaleObjectException },
        )
    }

    @Test
    fun optionalUpdateDismissalUsesClickableAncestorWhenTextNodeIsNotClickable() {
        val clickableOwner = FakeAccessibilityNode(id = "later-owner", clickable = true)
        val laterText = FakeAccessibilityNode(
            id = "later-text",
            clickable = false,
            parent = clickableOwner,
        )
        var overlayPresent = true
        var nowMs = 0L
        var clickedNode: FakeAccessibilityNode? = null

        val dismissed = dismissOptionalUpdateWithinDeadline(
            timeoutMs = 1_000L,
            nowMs = { nowMs },
            isOverlayPresent = { overlayPresent },
            resolveLaterAction = { laterText },
            parentOf = { node -> node.parent },
            isClickable = { node -> node.clickable },
            click = { node ->
                clickedNode = node
                overlayPresent = false
            },
            waitForOverlayGone = { waitMs ->
                nowMs += waitMs
                !overlayPresent
            },
            isStaleObject = { false },
        )

        assertTrue("Optional update should dismiss through the clickable action owner", dismissed)
        assertEquals("later-owner", clickedNode?.id)
    }

    @Test
    fun optionalUpdateDismissalReResolvesAfterStaleActionNode() {
        val clickableOwner = FakeAccessibilityNode(id = "later-owner", clickable = true)
        val staleLaterText = FakeAccessibilityNode(
            id = "stale-later-text",
            clickable = false,
            parent = clickableOwner,
        )
        val currentLaterText = FakeAccessibilityNode(
            id = "current-later-text",
            clickable = false,
            parent = clickableOwner,
        )
        var overlayPresent = true
        var nowMs = 0L
        var resolveCount = 0
        var clickedNode: FakeAccessibilityNode? = null

        val dismissed = dismissOptionalUpdateWithinDeadline(
            timeoutMs = 1_500L,
            nowMs = { nowMs },
            isOverlayPresent = { overlayPresent },
            resolveLaterAction = {
                resolveCount += 1
                if (resolveCount == 1) staleLaterText else currentLaterText
            },
            parentOf = { node ->
                if (node === staleLaterText) throw FakeStaleObjectException()
                node.parent
            },
            isClickable = { node -> node.clickable },
            click = { node ->
                clickedNode = node
                overlayPresent = false
            },
            waitForOverlayGone = { waitMs ->
                nowMs += waitMs
                !overlayPresent
            },
            isStaleObject = { error -> error is FakeStaleObjectException },
        )

        assertTrue("Optional update should dismiss after resolving the current action node", dismissed)
        assertEquals(2, resolveCount)
        assertEquals("later-owner", clickedNode?.id)
    }

    @Test
    fun optionalUpdateDismissalWithoutOverlayHasNoSideEffects() {
        var resolveCount = 0
        var clickCount = 0
        var waitCount = 0

        val dismissed = dismissOptionalUpdateWithinDeadline<FakeAccessibilityNode>(
            timeoutMs = 1_000L,
            nowMs = { 0L },
            isOverlayPresent = { false },
            resolveLaterAction = {
                resolveCount += 1
                null
            },
            parentOf = { node -> node.parent },
            isClickable = { node -> node.clickable },
            click = { clickCount += 1 },
            waitForOverlayGone = {
                waitCount += 1
                false
            },
            isStaleObject = { false },
        )

        assertTrue("Missing optional update overlay should already satisfy dismissal", dismissed)
        assertEquals(0, resolveCount)
        assertEquals(0, clickCount)
        assertEquals(0, waitCount)
    }

    @Test
    fun optionalUpdateDismissalFailsBoundedWhenNoClickableOwnerExists() {
        val nonClickableContainer = FakeAccessibilityNode(id = "container", clickable = false)
        val laterText = FakeAccessibilityNode(
            id = "later-text",
            clickable = false,
            parent = nonClickableContainer,
        )
        var nowMs = 0L
        var clickCount = 0

        val dismissed = dismissOptionalUpdateWithinDeadline(
            timeoutMs = 1_000L,
            nowMs = { nowMs },
            isOverlayPresent = { true },
            resolveLaterAction = { laterText },
            parentOf = { node -> node.parent },
            isClickable = { node -> node.clickable },
            click = { clickCount += 1 },
            waitForOverlayGone = { waitMs ->
                nowMs += waitMs
                false
            },
            isStaleObject = { false },
        )

        assertFalse("Overlay without a proven clickable action owner must fail", dismissed)
        assertEquals(0, clickCount)
        assertEquals(1_000L, nowMs)
    }

    private fun clickResolved(selector: BySelector, timeoutMs: Long = 6_000L): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (!dismissOptionalUpdateIfPresent()) return false
            try {
                val node = device.findObject(selector)
                if (node != null) {
                    node.click()
                    instrumentation.waitForIdleSync()
                    if (!device.hasObject(By.text("يتوفر تحديث جديد"))) return true
                }
            } catch (_: StaleObjectException) {
                // Resolve the selector again rather than retaining a node across a window transition.
            }
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining > 0L) {
                device.wait(Until.hasObject(selector), minOf(remaining, 500L))
            }
        }
        return false
    }

    private fun scrollLoginPageOnce(): Boolean {
        return try {
            val appWindow = device.findObject(By.pkg(targetContext.packageName).depth(0)) ?: return false
            val page = appWindow.findObject(By.scrollable(true)) ?: return false
            page.scroll(Direction.DOWN, 1f)
            instrumentation.waitForIdleSync()
            true
        } catch (_: StaleObjectException) {
            false
        }
    }

    private fun clickLoginFieldResolved(selector: BySelector, timeoutMs: Long = 6_000L): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var didScroll = false
        while (SystemClock.uptimeMillis() < deadline) {
            if (!dismissOptionalUpdateIfPresent()) return false
            try {
                val node = device.findObject(selector)
                if (node != null) {
                    node.click()
                    instrumentation.waitForIdleSync()
                    if (!device.hasObject(By.text("يتوفر تحديث جديد"))) return true
                } else if (!didScroll) {
                    didScroll = true
                    scrollLoginPageOnce()
                }
            } catch (_: StaleObjectException) {
                // Resolve from the current accessibility tree on the next bounded probe.
            }
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining > 0L) {
                device.wait(Until.hasObject(selector), minOf(remaining, 500L))
            }
        }
        return false
    }

    private fun resolvedVisibleBounds(selector: BySelector, timeoutMs: Long = 6_000L): Rect? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (!dismissOptionalUpdateIfPresent()) return null
            try {
                device.findObject(selector)?.let { return Rect(it.visibleBounds) }
            } catch (_: StaleObjectException) {
                // Resolve from the current accessibility tree on the next bounded probe.
            }
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining > 0L) {
                device.wait(Until.hasObject(selector), minOf(remaining, 500L))
            }
        }
        return null
    }

    private fun imeWindowIsActuallyVisible(): Boolean {
        val dump = device.executeShellCommand("dumpsys window windows")
        val block = StringBuilder()
        var inImeWindow = false
        for (line in dump.lineSequence()) {
            val startsWindow = line.startsWith("  Window #") && line.contains(" Window{")
            if (!inImeWindow && startsWindow && line.contains(" InputMethod}:")) {
                inImeWindow = true
            } else if (inImeWindow && startsWindow) {
                break
            }
            if (inImeWindow) block.appendLine(line)
        }
        if (block.isEmpty()) return false
        if (!block.contains("mViewVisibility=0x0")) return false
        return block.contains("mHasSurface=true") ||
            block.contains("isOnScreen=true") ||
            block.contains("isVisible=true")
    }

    private fun waitForTelevisionImeHiddenSettled(
        timeoutMs: Long = 30_000L,
        stableHiddenMs: Long = 4_000L,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var hiddenSince = -1L
        while (SystemClock.uptimeMillis() < deadline) {
            val remaining = deadline - SystemClock.uptimeMillis()
            if (!dismissOptionalUpdateIfPresent(minOf(1_000L, remaining))) {
                hiddenSince = -1L
                continue
            }
            val loginExposed = device.hasObject(By.text("كود الدخول"))
            val now = SystemClock.uptimeMillis()
            if (!loginExposed || imeWindowIsActuallyVisible()) {
                hiddenSince = -1L
            } else {
                if (hiddenSince < 0L) hiddenSince = now
                if (now - hiddenSince >= stableHiddenMs) return true
            }
            val sampleRemaining = deadline - SystemClock.uptimeMillis()
            if (sampleRemaining > 0L) {
                // Match the runtime collector's bounded sampling cadence; success depends on the hidden streak.
                SystemClock.sleep(minOf(500L, sampleRemaining))
            }
        }
        return false
    }

    private fun visibleApplicationBoundsSnapshot(timeoutMs: Long = 5_000L): List<Rect>? {
        val selector = By.pkg(targetContext.packageName)
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                val nodes = device.findObjects(selector)
                if (nodes.isNotEmpty()) {
                    return nodes.take(100).map { node -> Rect(node.visibleBounds) }
                }
            } catch (_: StaleObjectException) {
                // Discard the partial snapshot and resolve the current accessibility tree again.
            }
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining > 0L) {
                device.wait(Until.hasObject(selector), minOf(remaining, 500L))
            }
        }
        return null
    }

    @Test
    fun phoneLauncherStartsRealApplicationAndSurvivesRecreation() {
        assumeFalse("Phone lifecycle test is not applicable to television UI mode", isTelevision())
        launchScenario().use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity is MainActivity)
                assertFalse(activity.isFinishing)
                assertTrue(activity.window.decorView.isShown)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity is MainActivity)
                assertFalse(activity.isFinishing)
                assertTrue(activity.window.decorView.isShown)
            }
        }
    }

    @Test
    fun phonePortraitLoginFieldsAcceptTypingWithoutCrash() {
        assumeFalse("Portrait login typing test is not applicable to television UI mode", isTelevision())
        assumeTrue("Portrait login typing test must start in portrait", device.displayHeight > device.displayWidth)

        launchScenario().use { scenario ->
            assertTrue(
                "Application package did not become visible",
                device.wait(Until.hasObject(By.pkg(targetContext.packageName).depth(0)), 15_000L),
            )
            assertTrue(
                "Access-code field was not exposed",
                clickLoginFieldResolved(By.text("كود الدخول")),
            )
            device.executeShellCommand("input text HULK-ABCD-EFGH-JKMN-PQRS")
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                assertFalse("Application finished after access-code input", activity.isFinishing)
                assertTrue("Application window disappeared after access-code input", activity.window.decorView.isShown)
            }
            assertTrue(
                "Application package left the foreground after access-code input",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )

            assertTrue(
                "Username field was not exposed",
                clickLoginFieldResolved(By.text("اسم المستخدم")),
            )
            device.executeShellCommand("input text portraituser")
            instrumentation.waitForIdleSync()

            assertTrue(
                "Password field was not exposed",
                clickLoginFieldResolved(By.text("كلمة المرور")),
            )
            device.executeShellCommand("input text portraitpass")
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                assertFalse("Application finished after password input", activity.isFinishing)
                assertTrue("Application window disappeared after password input", activity.window.decorView.isShown)
            }
            assertTrue(
                "Application package left the foreground after password input",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )
            assertFalse(
                "Android crash recovery dialog appeared after portrait login typing",
                device.hasObject(By.textContains("مسح ذاكرة التخزين المؤقت")),
            )
            assertTrue(
                "Optional update overlay could not be dismissed for login qualification",
                dismissOptionalUpdateIfPresent(),
            )

            val output = File(targetContext.getExternalFilesDir(null), "compatibility-v2").apply { mkdirs() }
            assertTrue(
                "Portrait login evidence screenshot failed",
                device.takeScreenshot(File(output, "portrait-login-ime-stable.png")),
            )
            device.dumpWindowHierarchy(File(output, "portrait-login-ime-stable.xml"))

            var loginBounds: Rect? = null
            var subscribeBounds: Rect? = null
            repeat(6) {
                loginBounds = resolvedVisibleBounds(By.text("دخول الى HULK"), 500L)
                subscribeBounds = resolvedVisibleBounds(By.text("اشتراك جديد"), 500L)
                val loginVisible = loginBounds?.height()?.let { it > 0 } == true
                val subscribeVisible = subscribeBounds?.height()?.let { it > 0 } == true
                if (!loginVisible || !subscribeVisible) {
                    device.swipe(
                        device.displayWidth / 2,
                        device.displayHeight * 44 / 100,
                        device.displayWidth / 2,
                        device.displayHeight * 14 / 100,
                        30,
                    )
                    instrumentation.waitForIdleSync()
                }
            }
            assertNotNull("Login action was not exposed while the IME was active", loginBounds)
            assertNotNull("Subscribe action was not exposed while the IME was active", subscribeBounds)
            assertTrue(
                "Login action remained outside the visible resized window",
                loginBounds?.height()?.let { it > 0 } == true,
            )
            assertTrue(
                "Subscribe action remained outside the visible resized window",
                subscribeBounds?.height()?.let { it > 0 } == true,
            )
            assertTrue(
                "Portrait login action reachability screenshot failed",
                device.takeScreenshot(File(output, "portrait-login-ime-actions-reachable.png")),
            )
            device.dumpWindowHierarchy(File(output, "portrait-login-ime-actions-reachable.xml"))

            scenario.onActivity { activity ->
                val focusedToken = activity.currentFocus?.windowToken ?: activity.window.decorView.windowToken
                activity.currentFocus?.clearFocus()
                val inputMethodManager =
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(focusedToken, 0)
            }
            instrumentation.waitForIdleSync()

            fun readImeVisibility(): Boolean {
                var visible = true
                scenario.onActivity { activity ->
                    visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }

            var imeVisible = readImeVisibility()
            var imeProbe = 0
            while (imeVisible && imeProbe < 20) {
                SystemClock.sleep(250L)
                imeVisible = readImeVisibility()
                imeProbe += 1
            }

            if (imeVisible) {
                device.pressBack()
                instrumentation.waitForIdleSync()
                imeProbe = 0
                while (imeVisible && imeProbe < 20) {
                    SystemClock.sleep(250L)
                    imeVisible = readImeVisibility()
                    imeProbe += 1
                }
            }

            assertFalse("Portrait keyboard remained visible after dismissal", imeVisible)
            assertTrue(
                "Application package left the foreground while dismissing the portrait keyboard",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )
            scenario.onActivity { activity ->
                assertFalse("Application finished while dismissing the portrait keyboard", activity.isFinishing)
                assertTrue("Application window disappeared while dismissing the portrait keyboard", activity.window.decorView.isShown)
            }
        }
    }

    @Test
    fun loginFieldsRemainReachableAcrossScrollableLayouts() {
        assertTrue("Application package did not become visible", launchMainPackage())
        assertTrue("Access-code field was not reachable", clickLoginFieldResolved(By.text("كود الدخول")))
        assertTrue("Username field was not reachable", clickLoginFieldResolved(By.text("اسم المستخدم")))
        assertTrue("Password field was not reachable", clickLoginFieldResolved(By.text("كلمة المرور")))
    }

    @Test
    fun phonePortraitOrientationRestoresAfterLandscapePlayback() {
        assumeFalse("Phone orientation restore is not applicable to television UI mode", isTelevision())
        assumeTrue(
            "Orientation restore contract is limited to phones",
            targetContext.resources.configuration.smallestScreenWidthDp < 600,
        )
        assumeTrue("Orientation restore test must start in portrait", device.displayHeight > device.displayWidth)

        launchScenario().use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            assertTrue(
                "Phone did not enter landscape playback orientation",
                waitForDisplayOrientation(landscape = true),
            )

            scenario.onActivity { activity ->
                activity.requestedOrientation =
                    restoreOrientationRequest(Configuration.ORIENTATION_PORTRAIT)
            }
            assertTrue(
                "Phone did not restore portrait orientation after playback",
                waitForDisplayOrientation(landscape = false),
            )
        }
    }

    @Test
    fun explicitManifestComponentStartsInstalledDebugPackage() {
        assertTrue("Application package did not become visible", launchMainPackage())
    }

    @Test
    fun shortLandscapePhoneCanScrollToPrimaryLoginActions() {
        assumeFalse("Short landscape phone test is not applicable to television UI mode", isTelevision())
        assumeTrue(
            "Short landscape phone test requires a landscape display no taller than 1200 px",
            device.displayWidth > device.displayHeight && device.displayHeight <= 1200,
        )
        assertTrue("Application package did not become visible", launchMainPackage())

        val loginSelector = By.textContains("دخول")
        val subscribeSelector = By.text("اشتراك جديد")
        var loginBounds = resolvedVisibleBounds(loginSelector, 500L)
        var subscribeBounds = resolvedVisibleBounds(subscribeSelector, 500L)

        repeat(8) {
            val loginVisible = loginBounds?.height()?.let { it > 0 } == true
            val subscribeVisible = subscribeBounds?.height()?.let { it > 0 } == true
            if (!loginVisible || !subscribeVisible) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 4 / 5,
                    device.displayWidth / 2,
                    device.displayHeight / 5,
                    30,
                )
                instrumentation.waitForIdleSync()
                loginBounds = resolvedVisibleBounds(loginSelector, 500L)
                subscribeBounds = resolvedVisibleBounds(subscribeSelector, 500L)
            }
        }

        assertNotNull("Primary login action is not reachable after scrolling", loginBounds)
        assertNotNull("Subscribe or renew action is not reachable after scrolling", subscribeBounds)

        val display = Rect(0, 0, device.displayWidth, device.displayHeight)
        assertTrue("Primary login action is outside the display", Rect.intersects(display, requireNotNull(loginBounds)))
        assertTrue("Subscribe or renew action is outside the display", Rect.intersects(display, requireNotNull(subscribeBounds)))
    }

    @Suppress("DEPRECATION")
    @Test
    fun phoneWindowUsesTransparentEdgeToEdgeSystemBars() {
        assumeFalse("Phone window contract is not applicable to television UI mode", isTelevision())
        launchScenario().use { scenario ->
            assertTrue(
                "Application package did not become visible",
                device.wait(Until.hasObject(By.pkg(targetContext.packageName).depth(0)), 15_000L),
            )
            instrumentation.waitForIdleSync()
            SystemClock.sleep(900L)
            instrumentation.waitForIdleSync()

            var safeContentBounds = Rect()
            scenario.onActivity { activity ->
                val insets = requireNotNull(
                    ViewCompat.getRootWindowInsets(activity.window.decorView),
                ) { "Root window insets were not available" }
                assertTrue(
                    "Phone status bar must remain visible outside playback",
                    insets.isVisible(WindowInsetsCompat.Type.statusBars()),
                )
                assertTrue(
                    "Phone navigation controls must remain accessible outside playback",
                    insets.isVisible(WindowInsetsCompat.Type.navigationBars()),
                )
                assertEquals(
                    "Phone navigation bar must be transparent so app background reaches the display edge",
                    Color.TRANSPARENT,
                    activity.window.navigationBarColor,
                )
                val safeInsets = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
                safeContentBounds = Rect(
                    safeInsets.left,
                    safeInsets.top,
                    activity.window.decorView.width - safeInsets.right,
                    activity.window.decorView.height - safeInsets.bottom,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    assertTrue(
                        "Window must draw behind short-edge display cutouts",
                        activity.window.attributes.layoutInDisplayCutoutMode ==
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES ||
                            activity.window.attributes.layoutInDisplayCutoutMode ==
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                    )
                }
                assertTrue("Navigation safe content width is invalid", safeContentBounds.width() > 0)
                assertTrue("Navigation safe content height is invalid", safeContentBounds.height() > 0)
            }

            val probeBounds =
                resolvedVisibleBounds(By.textContains("دخول"), 1_500L)
                    ?: resolvedVisibleBounds(By.textContains("كلمة المرور"), 5_000L)
            assertNotNull(
                "No visible login control was exposed for safe-area verification",
                probeBounds,
            )
            val visibleProbeBounds = requireNotNull(probeBounds)
            assertTrue(
                "Visible login control overlaps system navigation controls: control=$visibleProbeBounds safe=$safeContentBounds",
                visibleProbeBounds.left >= safeContentBounds.left &&
                    visibleProbeBounds.top >= safeContentBounds.top &&
                    visibleProbeBounds.right <= safeContentBounds.right &&
                    visibleProbeBounds.bottom <= safeContentBounds.bottom,
            )
            assertFalse(
                "Normal phone pages must not trigger Android's immersive-mode education overlay",
                device.hasObject(By.res("android", "immersive_cling_title")),
            )
        }
    }

    @Test
    fun televisionLoginStartsWithImeHidden() {
        assumeTrue("Initial IME visibility contract requires television UI mode", isTelevision())
        launchScenario().use { scenario ->
            assertTrue(
                "Application package did not become visible",
                device.wait(Until.hasObject(By.pkg(targetContext.packageName).depth(0)), 15_000L),
            )
            scenario.onActivity { activity -> assertTrue(activity is TvMainActivity) }
            assertTrue(
                "TV login did not settle with the software keyboard hidden for four consecutive seconds",
                waitForTelevisionImeHiddenSettled(),
            )
        }
    }

    @Test
    fun televisionActivityAcceptsRapidDirectionalInputAndRetainsVisibleFocus() {
        assumeTrue("D-pad ownership test requires television UI mode", isTelevision())
        launchScenario().use { scenario ->
            assertNotNull(
                "TV reseller access-code field was not exposed",
                resolvedVisibleBounds(By.text("كود الدخول")),
            )
            repeat(12) { index ->
                val keyCode = when (index % 4) {
                    0 -> KeyEvent.KEYCODE_DPAD_RIGHT
                    1 -> KeyEvent.KEYCODE_DPAD_DOWN
                    2 -> KeyEvent.KEYCODE_DPAD_LEFT
                    else -> KeyEvent.KEYCODE_DPAD_UP
                }
                instrumentation.sendKeyDownUpSync(keyCode)
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            scenario.onActivity { activity ->
                assertTrue(activity is TvMainActivity)
                assertFalse(activity.isFinishing)
                assertTrue(activity.window.decorView.isShown)
            }
            val focusedBounds = resolvedVisibleBounds(By.focused(true), 5_000L)
            assertNotNull("No visible focused accessibility node after D-pad input", focusedBounds)
            val visibleFocusedBounds = requireNotNull(focusedBounds)
            assertTrue(
                "Focused node is outside the display",
                visibleFocusedBounds.width() > 0 && visibleFocusedBounds.height() > 0,
            )
        }
    }

    @Test
    fun visibleApplicationNodesHaveNonZeroBoundsInsideDisplay() {
        assertTrue("Application package did not become visible", launchMainPackage())

        val display = Rect(0, 0, device.displayWidth, device.displayHeight)
        val bounds = visibleApplicationBoundsSnapshot()
        assertNotNull("A stable accessibility snapshot was not available", bounds)
        val visibleBounds = requireNotNull(bounds)
        assertTrue("No visible accessibility nodes were exposed by the application", visibleBounds.isNotEmpty())
        visibleBounds.forEach { nodeBounds ->
            assertTrue(
                "Node has zero-sized visible bounds: $nodeBounds",
                nodeBounds.width() > 0 && nodeBounds.height() > 0,
            )
            assertTrue("Node is outside the display: $nodeBounds", Rect.intersects(display, nodeBounds))
        }
    }

    @Test
    fun capturesFullWindowScreenshotAndHierarchyWithoutCropping() {
        assertTrue("Application package did not become visible", launchMainPackage())

        val output = File(targetContext.getExternalFilesDir(null), "compatibility-v2").apply { mkdirs() }
        val screenshot = File(output, "instrumentation-full-window.png")
        val hierarchy = File(output, "instrumentation-window.xml")
        assertTrue("Full-window screenshot failed", device.takeScreenshot(screenshot))
        device.dumpWindowHierarchy(hierarchy)
        assertTrue("Screenshot evidence is missing", screenshot.isFile && screenshot.length() > 0L)
        assertTrue("Hierarchy evidence is missing", hierarchy.isFile && hierarchy.length() > 0L)
    }
}
