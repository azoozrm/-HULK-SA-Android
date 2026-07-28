package sa.hulksa.player

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    fun applicationContextIsAvailable() {
        assertNotNull(context.applicationContext)
    }
}