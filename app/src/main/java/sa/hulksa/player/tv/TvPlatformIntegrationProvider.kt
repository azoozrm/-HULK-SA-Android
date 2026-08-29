package sa.hulksa.player.tv

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TvPlatformIntegrationProvider<T>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    initializer: () -> T,
) {
    private val instance = lazy(LazyThreadSafetyMode.SYNCHRONIZED, initializer)

    suspend fun <R> withInstance(block: suspend (T) -> R): R =
        withContext(dispatcher) {
            block(instance.value)
        }

    fun getIfInitialized(): T? =
        if (instance.isInitialized()) instance.value else null
}
