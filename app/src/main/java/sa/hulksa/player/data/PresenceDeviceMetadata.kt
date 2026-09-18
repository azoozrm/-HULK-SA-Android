package sa.hulksa.player.data

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import sa.hulksa.player.BuildConfig

internal fun Application.currentPresenceDeviceMetadata(isTelevision: Boolean): PresenceDeviceMetadata {
    val platformClass = when {
        isTelevision -> PresencePlatformClass.TV
        resources.configuration.smallestScreenWidthDp >= 600 -> PresencePlatformClass.TABLET
        resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK in setOf(
            Configuration.UI_MODE_TYPE_NORMAL,
            Configuration.UI_MODE_TYPE_UNDEFINED,
        ) -> PresencePlatformClass.PHONE
        else -> PresencePlatformClass.OTHER
    }
    return PresenceDeviceMetadata(
        platformClass = platformClass,
        manufacturer = Build.MANUFACTURER.safePresenceValue("unknown", 100),
        model = Build.MODEL.safePresenceValue("unknown", 100),
        androidRelease = Build.VERSION.RELEASE.safePresenceValue("unknown", 32),
        sdkInt = Build.VERSION.SDK_INT,
    )
}

internal fun currentPresenceAppMetadata(): PresenceAppMetadata = PresenceAppMetadata(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
)

private fun String?.safePresenceValue(fallback: String, maxLength: Int): String =
    this?.trim()?.takeIf(String::isNotEmpty)?.take(maxLength) ?: fallback
