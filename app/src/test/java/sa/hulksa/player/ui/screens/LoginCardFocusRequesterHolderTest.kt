package sa.hulksa.player.ui.screens

import androidx.compose.ui.focus.FocusRequester
import org.junit.Assert.assertSame
import org.junit.Test

class LoginCardFocusRequesterHolderTest {
    @Test
    fun subscriptionReturnTarget_isTheExactLatestCardRequester() {
        val submitRequester = FocusRequester()
        val rememberRequester = FocusRequester()
        val showPasswordRequester = FocusRequester()
        val holder = LoginCardFocusRequesterHolder(submitRequester)

        assertSame(submitRequester, holder.current())

        holder.update(rememberRequester)
        assertSame(rememberRequester, holder.current())

        holder.update(showPasswordRequester)
        assertSame(showPasswordRequester, holder.current())
    }
}
