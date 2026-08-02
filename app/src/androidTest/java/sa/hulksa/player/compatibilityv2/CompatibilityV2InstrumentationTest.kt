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
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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
        val subscribeSelector = By.textContains("اشترك")
        var loginVisible = device.hasObject(loginSelector)
        var subscribeVisible = device.hasObject(subscribeSelector)

        repeat(8) {
            if (!loginVisible || !subscribeVisible) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 4 / 5,
                    device.displayWidth / 2,
                    device.displayHeight / 5,
                    30,
                )
                instrumentation.waitForIdleSync()
                SystemClock.sleep(250L)
                loginVisible = device.hasObject(loginSelector)
                subscribeVisible = device.hasObject(subscribeSelector)
            }
        }

        assertTrue("Primary login action is not reachable after scrolling", loginVisible)
        assertTrue("Subscribe or renew action is not reachable after scrolling", subscribeVisible)

        val display = Rect(0, 0, device.displayWidth, device.displayHeight)
        val loginBounds = device.findObject(loginSelector).visibleBounds
        val subscribeBounds = device.findObject(subscribeSelector).visibleBounds
        assertTrue("Primary login action is outside the display", Rect.intersects(display, loginBounds))
        assertTrue("Subscribe or renew action is outside the display", Rect.intersects(display, subscribeBounds))
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
                val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                safeContentBounds = Rect(
                    navigationInsets.left,
                    0,
                    activity.window.decorView.width - navigationInsets.right,
                    activity.window.decorView.height - navigationInsets.bottom,
                )
                assertTrue("Navigation safe content width is invalid", safeContentBounds.width() > 0)
                assertTrue("Navigation safe content height is invalid", safeContentBounds.height() > 0)
            }

            val visibleSafeAreaProbe =
                device.wait(Until.findObject(By.textContains("دخول")), 1_500L)
                    ?: device.wait(Until.findObject(By.textContains("كلمة المرور")), 5_000L)
            assertNotNull(
                "No visible login control was exposed for safe-area verification",
                visibleSafeAreaProbe,
            )
            val probeBounds = requireNotNull(visibleSafeAreaProbe).visibleBounds
            assertTrue(
                "Visible login control overlaps system navigation controls: control=$probeBounds safe=$safeContentBounds",
                probeBounds.left >= safeContentBounds.left &&
                    probeBounds.top >= safeContentBounds.top &&
                    probeBounds.right <= safeContentBounds.right &&
                    probeBounds.bottom <= safeContentBounds.bottom,
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
            instrumentation.waitForIdleSync()
            SystemClock.sleep(2_500L)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity is TvMainActivity)
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                assertFalse(
                    "TV login opened the software keyboard after the window settled",
                    insets?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                )
            }
        }
    }

    @Test
    fun televisionActivityAcceptsRapidDirectionalInputAndRetainsVisibleFocus() {
        assumeTrue("D-pad ownership test requires television UI mode", isTelevision())
        launchScenario().use { scenario ->
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
            val focused = device.wait(Until.findObject(By.focused(true)), 5_000L)
            assertNotNull("No visible focused accessibility node after D-pad input", focused)
            assertTrue(
                "Focused node is outside the display",
                focused.visibleBounds.width() > 0 && focused.visibleBounds.height() > 0,
            )
        }
    }

    @Test
    fun visibleApplicationNodesHaveNonZeroBoundsInsideDisplay() {
        assertTrue("Application package did not become visible", launchMainPackage())

        val display = Rect(0, 0, device.displayWidth, device.displayHeight)
        val nodes = device.findObjects(By.pkg(targetContext.packageName))
        assertTrue("No accessibility nodes were exposed by the application", nodes.isNotEmpty())
        nodes.take(100).forEach { node ->
            val bounds = node.visibleBounds
            assertTrue("Node has zero-sized visible bounds: $node", bounds.width() > 0 && bounds.height() > 0)
            assertTrue("Node is outside the display: $bounds", Rect.intersects(display, bounds))
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
