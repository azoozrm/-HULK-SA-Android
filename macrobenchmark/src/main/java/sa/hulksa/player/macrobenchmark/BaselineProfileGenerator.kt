package sa.hulksa.player.macrobenchmark

import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineProfileRule.collect(TargetApp.PACKAGE) {
        startActivityAndWait { intent ->
            intent.component = TargetApp.tvMainComponent()
        }
        device.waitForIdle()
        repeat(3) {
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            device.waitForIdle()
        }
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP)
        device.waitForIdle()
    }
}
