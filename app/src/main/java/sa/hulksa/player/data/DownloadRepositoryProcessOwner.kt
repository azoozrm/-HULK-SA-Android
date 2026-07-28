package sa.hulksa.player.data

import android.app.Application
import android.content.Context

/**
 * Process-local owner for the stateful repository. WorkManager runs in the app
 * process by default, so sharing this instance prevents parallel OkHttp writers
 * from touching the same partial download file.
 */
internal object DownloadRepositoryProcessOwner {
    @Volatile
    private var instance: DownloadRepository? = null

    fun get(context: Context): DownloadRepository {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: DownloadRepository(context.applicationContext).also { created ->
                instance = created
            }
        }
    }
}

/**
 * More-specific overload used by AndroidViewModel callers so the UI and
 * WorkManager worker share one repository and one in-process transport engine.
 */
@Suppress("FunctionName")
internal fun DownloadRepository(application: Application): DownloadRepository =
    DownloadRepositoryProcessOwner.get(application)
