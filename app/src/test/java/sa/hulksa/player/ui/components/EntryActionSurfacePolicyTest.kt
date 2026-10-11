package sa.hulksa.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryActionSurfacePolicyTest {
    @Test
    fun loadingPrimaryKeepsGoldSurfaceInsteadOfDimmedDisabled() {
        assertEquals(
            EntryActionSurface.PRIMARY,
            entryActionSurface(
                primary = true,
                outlined = false,
                danger = false,
                enabled = false,
                loading = true,
                focused = false,
            ),
        )
    }

    @Test
    fun genuinelyDisabledNonLoadingPrimaryStaysDimmed() {
        assertEquals(
            EntryActionSurface.DISABLED,
            entryActionSurface(
                primary = true,
                outlined = false,
                danger = false,
                enabled = false,
                loading = false,
                focused = false,
            ),
        )
    }

    @Test
    fun loadingDoesNotChangeEnabledSecondaryRoles() {
        assertEquals(
            EntryActionSurface.OUTLINED,
            entryActionSurface(
                primary = false,
                outlined = true,
                danger = false,
                enabled = true,
                loading = true,
                focused = false,
            ),
        )
        assertEquals(
            EntryActionSurface.IDLE,
            entryActionSurface(
                primary = false,
                outlined = false,
                danger = false,
                enabled = true,
                loading = true,
                focused = false,
            ),
        )
    }
}
