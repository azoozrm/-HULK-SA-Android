package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class VodSeekFeedbackOwnerTest {
    @Test
    fun identicalTextGetsFreshIdentityAndObsoleteExpiryCannotClearIt() {
        val owner = VodSeekFeedbackOwner()
        owner.post("+10 ث")
        val first = requireNotNull(owner.event)
        owner.post("+10 ث")
        val second = requireNotNull(owner.event)

        assertNotEquals(first.id, second.id)
        assertEquals(first.text, second.text)

        owner.expire(first)
        assertSame(second, owner.event)

        owner.expire(second)
        assertNull(owner.event)
    }
}
