package sa.hulksa.player.compatibilityv2

import android.app.Activity
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityV2InstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun isTelevision(): Boolean {
        val mode = (targetContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType
        return mode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun launcherCategory(): String =
        if (isTelevision()) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER

    private fun resolvedLauncherIntent(): Intent {
        val queryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(launcherCategory())
            setPackage(targetContext.packageName)
        }
        val resolved = requireNotNull(targetContext.packageManager.resolveActivity(queryIntent, 0)) {
            "No ${launcherCategory()} activity resolves for ${targetContext.packageName}"
        }
        val activityInfo = requireNotNull(resolved.activityInfo) {
            "Resolved launcher has no ActivityInfo for ${targetContext.packageName}"
        }
        return Intent(queryIntent).apply {
            component = ComponentName(activityInfo.packageName, activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private fun launchMainPackage(): Boolean {
        targetContext.startActivity(resolvedLauncherIntent())
        return device.wait(Until.hasObject(By.pkg(targetContext.packageName).depth(0)), 15_000L)
    }

    private fun launchScenario(): ActivityScenario<Activity> =
        ActivityScenario.launch(resolvedLauncherIntent())

    @Test
    fun phoneLauncherStartsRealApplicationAndSurvivesRecreation() {
        assumeFalse("Phone lifecycle test is not applicable to television UI mode", isTelevision())
        launchScenario().use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(activity.window.decorView.isShown)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(activity.window.decorView.isShown)
            }
        }
    }

    @Test
    fun explicitLauncherIntentResolvesToInstalledDebugPackage() {
        assertTrue("Application package did not become visible", launchMainPackage())
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
                assertFalse(activity.isFinishing)
                assertTrue(activity.window.decorView.isShown)
            }
            val focused = device.wait(Until.findObject(By.focused(true)), 5_000L)
            assertNotNull("No visible focused accessibility node after D-pad input", focused)
            assertTrue("Focused node is outside the display", focused.visibleBounds.width() > 0 && focused.visibleBounds.height() > 0)
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
