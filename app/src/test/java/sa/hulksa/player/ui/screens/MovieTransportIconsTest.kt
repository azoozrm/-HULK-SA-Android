package sa.hulksa.player.ui.screens

import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieTransportIconsTest {

    @Test
    fun movieTransportIconsContainParsedDistinctVectorGeometry() {
        val rewind = MovieRewind10Icon
        val forward = MovieForward10Icon

        listOf(rewind, forward).forEach { icon ->
            assertEquals(960f, icon.viewportWidth, 0f)
            assertEquals(960f, icon.viewportHeight, 0f)
            val path = icon.root[0] as VectorPath
            assertTrue(path.pathData.isNotEmpty())
        }

        // The two physical directions must stay distinct vectors, not one mirrored copy.
        val rewindPath = (rewind.root[0] as VectorPath).pathData
        val forwardPath = (forward.root[0] as VectorPath).pathData
        assertNotEquals(rewindPath, forwardPath)
    }
}
