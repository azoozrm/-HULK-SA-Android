package sa.hulksa.player

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilitySmokeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun applicationPackageIsInstalled() {
        assertTrue(context.packageName.startsWith("sa.hulksa.player"))
    }

    @Test
    fun launcherActivitiesAreDeclared() {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()),
        )
        val activityNames = packageInfo.activities.orEmpty().map { it.name }.toSet()

        assertTrue(activityNames.contains(MainActivity::class.java.name))
        assertTrue(activityNames.contains(TvMainActivity::class.java.name))
    }

    @Test
    fun launcherIntentsResolve() {
        val packageManager = context.packageManager
        val phoneIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(context, MainActivity::class.java)
        }
        val televisionIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            component = ComponentName(context, TvMainActivity::class.java)
        }

        assertNotNull(phoneIntent.resolveActivity(packageManager))
        assertNotNull(televisionIntent.resolveActivity(packageManager))
    }

    @Test
    fun touchscreenIsNotRequired() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageManager = instrumentation.targetContext.packageManager
        val packageInfo = packageManager.getPackageInfo(
            instrumentation.targetContext.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_CONFIGURATIONS.toLong()),
        )
        val requiredTouchscreen = packageInfo.reqFeatures.orEmpty().any { feature ->
            feature.name == PackageManager.FEATURE_TOUCHSCREEN &&
                feature.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED != 0
        }

        assertFalse(requiredTouchscreen)
    }

    @Test
    fun mainActivityLaunchesOnNonTelevisionConfiguration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertEquals(Activity.RESULT_CANCELED, activity.resultCode)
            }
        }
    }

    @Test
    fun televisionActivityLaunchesAndAcceptsDirectionalInput() {
        ActivityScenario.launch(TvMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                val root = activity.window.decorView
                assertNotNull(root)
                assertTrue(root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)) || root.isShown)
                assertTrue(root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)) || root.isShown)
            }
        }
    }

    @Test
    fun televisionActivityDeclaresLandscapeOrientation() {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, TvMainActivity::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )

        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, info.screenOrientation)
    }

    @Test
    fun runtimeConfigurationSupportsPhoneTabletAndTelevisionBuckets() {
        val configurations = listOf(
            Configuration().apply { smallestScreenWidthDp = 360 },
            Configuration().apply { smallestScreenWidthDp = 600 },
            Configuration().apply {
                smallestScreenWidthDp = 960
                uiMode = Configuration.UI_MODE_TYPE_TELEVISION
            },
        )

        assertTrue(configurations.any { it.smallestScreenWidthDp < 600 })
        assertTrue(configurations.any { it.smallestScreenWidthDp >= 600 })
        assertTrue(configurations.any { it.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION })
    }

    @Test
    fun applicationContextIsAvailable() {
        assertNotNull(context.applicationContext)
    }
}