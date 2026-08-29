package sa.hulksa.player.tv

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TvPlatformIntegrationProviderTest {
    @Test
    fun `login startup probe does not initialize tv platform integration`() {
        val creations = AtomicInteger(0)
        val provider = TvPlatformIntegrationProvider {
            creations.incrementAndGet()
            Any()
        }

        assertNull(provider.getIfInitialized())
        assertEquals(0, creations.get())
    }

    @Test
    fun `first platform operation creates one stable instance`() {
        val creations = AtomicInteger(0)
        val provider = TvPlatformIntegrationProvider {
            creations.incrementAndGet()
            Any()
        }

        val first = provider.get()
        val second = provider.get()

        assertSame(first, second)
        assertSame(first, provider.getIfInitialized())
        assertEquals(1, creations.get())
    }
}
