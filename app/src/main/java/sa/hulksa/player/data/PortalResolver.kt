package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.model.PortalConfig
import java.util.concurrent.TimeUnit

class PortalResolver(context: Context) {
    private val preferences = context.getSharedPreferences("hulk_portal_config", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(): PortalConfig {
        resolveRemote()?.let { remote ->
            preferences.edit().putString(KEY_LAST_PORTAL, remote).apply()
            return PortalConfig(remote, PortalConfig.Source.REMOTE)
        }

        preferences.getString(KEY_LAST_PORTAL, null)
            ?.normalizePortal()
            ?.let { return PortalConfig(it, PortalConfig.Source.REMOTE) }

        BuildConfig.PORTAL_URL.normalizePortal()
            ?.let { return PortalConfig(it, PortalConfig.Source.COMPILED) }

        throw PortalException.ConfigurationMissing
    }

    private suspend fun resolveRemote(): String? = withContext(Dispatchers.IO) {
        val configUrl = BuildConfig.CONFIG_URL.trim()
        val parsed = configUrl.toHttpUrlOrNull() ?: return@withContext null
        if (!parsed.isHttps) return@withContext null

        runCatching {
            val request = Request.Builder()
                .url(parsed)
                .header("Accept", "application/json")
                .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                JSONObject(body).optString("portal_url").normalizePortal()
            }
        }.getOrNull()
    }

    private fun String?.normalizePortal(): String? {
        val value = this?.trim()?.trimEnd('/').orEmpty()
        val parsed = value.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        return parsed.toString().trimEnd('/')
    }

    private companion object {
        const val KEY_LAST_PORTAL = "last_portal"
    }
}

sealed class PortalException(message: String) : Exception(message) {
    data object ConfigurationMissing : PortalException("تعذر تحميل اعدادات الخدمة. تواصل مع الدعم.")
}
