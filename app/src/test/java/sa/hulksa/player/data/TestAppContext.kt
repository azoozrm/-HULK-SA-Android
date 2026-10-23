package sa.hulksa.player.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File

/**
 * Minimal JVM Context for touching production owners without Robolectric.
 * Only the platform surface used by the persistence tests is implemented.
 */
@Suppress("DEPRECATION")
internal class TestAppContext(
    private val preferences: MutableMap<String, FakeSharedPreferences> = mutableMapOf(),
    private val externalDir: File? = null,
) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        preferences.getOrPut(name.orEmpty()) { FakeSharedPreferences() }

    override fun getSystemService(name: String): Any? = null

    override fun getSystemServiceName(serviceClass: Class<*>): String? = null

    override fun getExternalFilesDirs(type: String?): Array<File>? =
        externalDir?.let { arrayOf(it) }
}
