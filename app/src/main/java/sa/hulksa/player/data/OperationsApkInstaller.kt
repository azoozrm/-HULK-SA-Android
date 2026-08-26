package sa.hulksa.player.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import sa.hulksa.player.BuildConfig

sealed interface OperationsInstallResult {
    data object InstallerOpened : OperationsInstallResult
    data object UnknownSourcesBlocked : OperationsInstallResult
    data class Failure(val message: String) : OperationsInstallResult
}

class OperationsApkInstaller(
    context: Context,
    private val client: OkHttpClient = defaultDownloadHttpClient(),
) {
    private val applicationContext = context.applicationContext

    suspend fun downloadAndOpen(
        update: OperationsUpdateConfig,
        onProgress: (Int?) -> Unit,
    ): OperationsInstallResult = withContext(Dispatchers.IO) {
        if (!isTrustedApkUrl(update.apkUrl)) {
            return@withContext OperationsInstallResult.Failure("رابط التحديث الرسمي غير صالح.")
        }
        val expectedSha = update.apkSha256?.lowercase().orEmpty()
        if (!expectedSha.matches(Regex("[a-f0-9]{64}"))) {
            return@withContext OperationsInstallResult.Failure("بيانات التحقق من التحديث غير صالحة.")
        }
        if (!canInstallUnknownPackages()) {
            return@withContext OperationsInstallResult.UnknownSourcesBlocked
        }

        val directory = File(applicationContext.cacheDir, UPDATE_CACHE_DIRECTORY)
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) {
            return@withContext OperationsInstallResult.Failure("تعذر تجهيز مساحة تنزيل التحديث.")
        }
        val partialFile = File(directory, "hulk-sa-${update.latestVersionCode}.apk.partial")
        val finalFile = File(directory, "hulk-sa-${update.latestVersionCode}.apk")
        partialFile.delete()
        finalFile.delete()

        val request = Request.Builder()
            .url(requireNotNull(update.apkUrl))
            .get()
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} Update")
            .build()

        val downloadResult = runCatching {
            client.executeCancellable(request) { response ->
                if (!response.isSuccessful) {
                    return@executeCancellable OperationsInstallResult.Failure("تعذر تنزيل التحديث من الخادم الرسمي.")
                }
                if (!isTrustedApkUrl(response.request.url.toString())) {
                    return@executeCancellable OperationsInstallResult.Failure("تم رفض إعادة توجيه غير موثوقة للتحديث.")
                }
                val body = response.body
                    ?: return@executeCancellable OperationsInstallResult.Failure("ملف التحديث فارغ.")
                val totalBytes = body.contentLength()
                if (totalBytes > MAX_APK_BYTES) {
                    return@executeCancellable OperationsInstallResult.Failure("حجم ملف التحديث غير مسموح.")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var writtenBytes = 0L
                body.byteStream().use { input ->
                    partialFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            writtenBytes += read
                            if (writtenBytes > MAX_APK_BYTES) {
                                throw IllegalStateException("APK too large")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            onProgress(
                                if (totalBytes > 0L) {
                                    ((writtenBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                if (writtenBytes <= 0L || (totalBytes >= 0L && writtenBytes != totalBytes)) {
                    partialFile.delete()
                    return@executeCancellable OperationsInstallResult.Failure("لم يكتمل تنزيل ملف التحديث.")
                }

                val actualSha = digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
                val matches = MessageDigest.isEqual(
                    actualSha.toByteArray(Charsets.US_ASCII),
                    expectedSha.toByteArray(Charsets.US_ASCII),
                )
                if (!matches) {
                    partialFile.delete()
                    return@executeCancellable OperationsInstallResult.Failure("تعذر التحقق من سلامة التحديث")
                }
                if (!partialFile.renameTo(finalFile)) {
                    partialFile.delete()
                    return@executeCancellable OperationsInstallResult.Failure("تعذر تجهيز ملف التحديث للتثبيت.")
                }
                currentCoroutineContext().ensureActive()
                onProgress(100)
                launchInstaller(finalFile)
            }
        }.getOrElse { error ->
            partialFile.delete()
            finalFile.delete()
            if (error is CancellationException) throw error
            OperationsInstallResult.Failure("تعذر تنزيل التحديث. تحقق من الاتصال وحاول مرة أخرى.")
        }
        downloadResult
    }

    fun openUnknownSourcesSettings(): Boolean {
        val packageUri = Uri.parse("package:${applicationContext.packageName}")
        val securitySettings = Intent(Settings.ACTION_SECURITY_SETTINGS)
        val applicationDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        val allApplications = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return openFirstAvailableSettings(
                listOf(securitySettings, applicationDetails, allApplications),
            )
        }

        val packageUnknownSources = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
        val candidates = if (isTelevisionDevice()) {
            // Several Android/Google TV settings apps resolve the package-specific action
            // but immediately finish it. Open the TV security surface first, where the
            // Unknown sources / Install unknown apps switch is actually exposed.
            listOf(
                securitySettings,
                packageUnknownSources,
                applicationDetails,
                allApplications,
                Intent(Settings.ACTION_SETTINGS),
            )
        } else {
            listOf(
                packageUnknownSources,
                applicationDetails,
                securitySettings,
                allApplications,
                Intent(Settings.ACTION_SETTINGS),
            )
        }
        return openFirstAvailableSettings(candidates)
    }

    private fun openFirstAvailableSettings(candidates: List<Intent>): Boolean {
        val packageManager = applicationContext.packageManager
        for (candidate in candidates) {
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (candidate.resolveActivity(packageManager) == null) continue
            val opened = runCatching {
                applicationContext.startActivity(candidate)
                true
            }.getOrDefault(false)
            if (opened) return true
        }
        return false
    }

    private fun isTelevisionDevice(): Boolean {
        val packageManager = applicationContext.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    private fun canInstallUnknownPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            applicationContext.packageManager.canRequestPackageInstalls()

    private fun isTrustedApkUrl(value: String?): Boolean {
        val apk = runCatching { URI(value?.trim().orEmpty()) }.getOrNull() ?: return false
        val operations = runCatching { URI(BuildConfig.OPERATIONS_CONFIG_URL) }.getOrNull() ?: return false
        return apk.scheme.equals("https", ignoreCase = true) &&
            apk.host.equals(operations.host, ignoreCase = true) &&
            (apk.port == -1 || apk.port == 443) &&
            apk.userInfo == null &&
            apk.query == null &&
            apk.fragment == null &&
            apk.path?.startsWith("/hulk-operations/releases/") == true &&
            apk.path?.lowercase()?.endsWith(".apk") == true
    }

    private fun launchInstaller(apk: File): OperationsInstallResult = runCatching {
        val uri = FileProvider.getUriForFile(
            applicationContext,
            "${BuildConfig.APPLICATION_ID}.operations-file-provider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        applicationContext.startActivity(intent)
        OperationsInstallResult.InstallerOpened
    }.getOrElse {
        apk.delete()
        OperationsInstallResult.Failure("تعذر فتح مثبت Android على هذا الجهاز.")
    }

    private companion object {
        const val UPDATE_CACHE_DIRECTORY = "operations-releases"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val MAX_APK_BYTES = 600L * 1_024L * 1_024L

        fun defaultDownloadHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }
}
