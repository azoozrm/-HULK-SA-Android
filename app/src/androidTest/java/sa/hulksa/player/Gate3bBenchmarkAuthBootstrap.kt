package sa.hulksa.player

import android.app.Application
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.ProfilePreferencesStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.Credentials

/**
 * Gate 3B protected authentication bootstrap (benchmark variant only).
 *
 * Authenticates the `sa.hulksa.player.benchmark` target with the protected production-e2e
 * account through the existing repository contract, persists the session
 * (`remember = true`), resolves the primary profile and enables Direct Entry so the
 * subsequent warm start lands on the authenticated HOME screen required by
 * `sa.hulksa.player.macrobenchmark.TvFirstEntryNavigation`.
 *
 * Credentials arrive as a single shell-safe base64 JSON instrumentation argument so the
 * `adb shell am instrument -e` transport cannot mangle values. The session exists only in
 * the app's private storage on the ephemeral runner and is destroyed with the runner.
 * Credentials are never written to evidence or logs.
 */
@RunWith(AndroidJUnit4::class)
class Gate3bBenchmarkAuthBootstrap {
    private val arguments = InstrumentationRegistry.getArguments()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun authenticateBenchmarkTargetForDirectEntry() = runBlocking {
        assumeTrue(
            "Gate 3B auth bootstrap is opt-in only",
            arguments.getString(ARG_ENABLED) == "true",
        )

        val repository = HulkRepository(app)
        repository.login(credentials(), remember = true)

        assertNotNull(
            "authenticated session metadata was not persisted",
            repository.activeAccountSession(),
        )

        val profileStore = ProfileStore(app)
        val profiles = profileStore.profiles()
        assertTrue("no profile was resolved after authentication", profiles.isNotEmpty())
        val primary = profiles.firstOrNull { it.isPrimary } ?: profiles.first()
        assertTrue("could not activate the primary profile", profileStore.setActiveProfile(primary.id))

        val routing = ProfilePreferencesStore(app).setRouting(
            directEntryEnabled = true,
            defaultProfileId = primary.id,
        )
        assertTrue("Direct Entry was not persisted", routing.directEntryEnabled)
    }

    private fun credentials(): Credentials {
        val blob = arguments.getString(ARG_CREDENTIALS).orEmpty()
        assertTrue(
            "Required protected instrumentation argument is missing: $ARG_CREDENTIALS",
            blob.isNotBlank(),
        )
        val json = JSONObject(String(Base64.decode(blob, Base64.NO_WRAP), Charsets.UTF_8))
        return Credentials(
            accessCode = json.getString("accessCode"),
            username = json.getString("username"),
            password = json.getString("password"),
        )
    }

    private companion object {
        const val ARG_ENABLED = "hulkGate3bAuth"
        const val ARG_CREDENTIALS = "hulkGate3bCreds"
    }
}
