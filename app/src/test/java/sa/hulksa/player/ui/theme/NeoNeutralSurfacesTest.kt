package sa.hulksa.player.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class NeoNeutralSurfacesTest {
    @Test
    fun `v5 neutral roles keep the owner-approved opaque values`() {
        assertEquals(Color(0xFF151719), NeoNeutralSurfaces.card)
        assertEquals(Color(0xFF17191C), NeoNeutralSurfaces.modal)
        assertEquals(Color(0xFF202225), NeoNeutralSurfaces.control)
        assertEquals(Color(0xFF383A3D), NeoNeutralSurfaces.stroke)
    }

    @Test
    fun `legacy warm defaults remain unchanged`() {
        val colors = HulkColors()
        assertEquals(Color(0xFF111108), colors.surface)
        assertEquals(Color(0xFF1B1A0E), colors.surfaceRaised)
        assertEquals(Color(0x47E6C352), colors.line)
        assertEquals(Color(0xFFE6C352), colors.gold)
        assertEquals(Color(0xFFFFF0A8), colors.goldBright)
    }
}
