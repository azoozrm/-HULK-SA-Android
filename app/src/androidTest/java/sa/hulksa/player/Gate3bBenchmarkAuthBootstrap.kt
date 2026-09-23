package sa.hulksa.player

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
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
 * The session exists only in the app's private storage on the ephemeral runner and is
 * destroyed with the runner. Credentials are read from protected instrumentation arguments
 * and are never written to evidence or logs.
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
        repository.login(
            Credentials(
                accessCode = requireSecret(ARG_ACCESS_CODE),
                username = requireSecret(ARG_USERNAME),
                password = requireSecret(ARG_PASSWORD),
            ),
            remember = true,
        )

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

    private fun requireSecret(name: String): String {
        val value = arguments.getString(name).orEmpty()
        assertTrue("Required protected instrumentation argument is missing: $name", value.isNotBlank())
        return value
    }

    private companion object {
        const val ARG_ENABLED = "hulkGate3bAuth"
        const val ARG_ACCESS_CODE = "hulkE2eAccessCode"
        const val ARG_USERNAME = "hulkE2eUsername"
        const val ARG_PASSWORD = "hulkE2ePassword"
    }
}
