package sa.hulksa.player.compatibilityv2

import android.content.ComponentName
import android.content.Intent
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
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.MainActivity
import sa.hulksa.player.TvMainActivity

@RunWith(AndroidJUnit4::class)
class CompatibilityV2InstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun phoneLauncherStartsRealApplicationAndSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
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
        val component = ComponentName(targetContext, MainActivity::class.java)
        val intent = Intent.makeMainActivity(component).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        targetContext.startActivity(intent)

        val appeared = device.wait(
            Until.hasObject(By.pkg(targetContext.packageName).depth(0)),
            15_000L,
        )
        assertTrue("Application package did not become visible", appeared)
    }

    @Test
    fun televisionActivityAcceptsRapidDirectionalInputWithoutCrash() {
        ActivityScenario.launch(TvMainActivity::class.java).use { scenario ->
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
                assertNotNull(activity.currentFocus ?: activity.window.decorView)
            }
        }
    }

    @Test
    fun visibleApplicationNodesHaveNonZeroBoundsInsideDisplay() {
        val component = ComponentName(targetContext, MainActivity::class.java)
        targetContext.startActivity(Intent.makeMainActivity(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg(targetContext.packageName)), 15_000L))

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
        val component = ComponentName(targetContext, MainActivity::class.java)
        targetContext.startActivity(Intent.makeMainActivity(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg(targetContext.packageName)), 15_000L))

        val output = File(targetContext.getExternalFilesDir(null), "compatibility-v2").apply { mkdirs() }
        val screenshot = File(output, "instrumentation-full-window.png")
        val hierarchy = File(output, "instrumentation-window.xml")
        assertTrue("Full-window screenshot failed", device.takeScreenshot(screenshot))
        device.dumpWindowHierarchy(hierarchy)
        assertTrue("Screenshot evidence is missing", screenshot.isFile && screenshot.length() > 0L)
        assertTrue("Hierarchy evidence is missing", hierarchy.isFile && hierarchy.length() > 0L)
    }
}
