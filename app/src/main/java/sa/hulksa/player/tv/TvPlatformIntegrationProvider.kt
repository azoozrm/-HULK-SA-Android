package sa.hulksa.player.tv

internal class TvPlatformIntegrationProvider<T>(
    initializer: () -> T,
) {
    private val instance = lazy(LazyThreadSafetyMode.SYNCHRONIZED, initializer)

    fun get(): T = instance.value

    fun getIfInitialized(): T? =
        if (instance.isInitialized()) instance.value else null
}
