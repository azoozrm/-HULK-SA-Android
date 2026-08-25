package sa.hulksa.player.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePinOperationGuardTest {
    @Test
    fun duplicateSubmissionIsRejectedUntilCurrentOperationFinishes() {
        val guard = ProfilePinOperationGuard()
        val first = requireNotNull(guard.begin())

        assertNull(guard.begin())
        assertTrue(guard.isCurrent(first))

        guard.finish(first)

        requireNotNull(guard.begin())
    }

    @Test
    fun cancelledTokenCannotBecomeCurrentAfterNewOperationStarts() {
        val guard = ProfilePinOperationGuard()
        val first = requireNotNull(guard.begin())

        guard.cancel()
        val second = requireNotNull(guard.begin())

        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))

        guard.finish(first)
        assertTrue(guard.isCurrent(second))
    }
}
