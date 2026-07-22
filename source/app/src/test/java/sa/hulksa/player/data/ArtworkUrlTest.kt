package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkUrlTest {
    private val portal = "http://example.test:8080"

    @Test
    fun keepsAbsoluteArtworkUrls() {
        assertEquals(
            "https://cdn.example.test/channel.png",
            normalizeArtworkUrl("https://cdn.example.test/channel.png", portal),
        )
    }

    @Test
    fun resolvesProtocolRelativeArtworkUrls() {
        assertEquals(
            "http://cdn.example.test/channel.png",
            normalizeArtworkUrl("//cdn.example.test/channel.png", portal),
        )
    }

    @Test
    fun resolvesRootAndPathRelativeArtworkUrls() {
        assertEquals(
            "http://example.test:8080/images/channel.png",
            normalizeArtworkUrl("/images/channel.png", portal),
        )
        assertEquals(
            "http://example.test:8080/images/channel.png",
            normalizeArtworkUrl("images/channel.png", portal),
        )
    }

    @Test
    fun ignoresMissingArtworkValues() {
        assertNull(normalizeArtworkUrl(null, portal))
        assertNull(normalizeArtworkUrl("  ", portal))
        assertNull(normalizeArtworkUrl("null", portal))
    }
}
