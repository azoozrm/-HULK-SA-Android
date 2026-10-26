package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal const val VOD_SEEK_FEEDBACK_LIFETIME_MS = 1_000L

/** One accepted VOD seek feedback event. The id keeps identical consecutive labels distinct. */
internal data class VodSeekFeedbackEvent(
    val id: Long,
    val text: String,
)

/**
 * Request-owned VOD seek feedback state. Every accepted seek posts a fresh event identity, so a
 * repeated label still restarts its full lifetime and an obsolete expiry cannot clear a newer one.
 */
internal class VodSeekFeedbackOwner {
    var event by mutableStateOf<VodSeekFeedbackEvent?>(null)
        private set

    private var nextId = 0L

    fun post(text: String) {
        nextId += 1L
        event = VodSeekFeedbackEvent(id = nextId, text = text)
    }

    fun expire(event: VodSeekFeedbackEvent) {
        if (this.event == event) {
            this.event = null
        }
    }
}

/**
 * Single expiry owner for the latest accepted seek event. Keying the effect on event identity
 * restarts the 1000 ms lifetime for identical text as well.
 */
@Composable
internal fun VodSeekFeedbackExpiry(owner: VodSeekFeedbackOwner) {
    // The owner identity is part of the key so a replaced request cannot reuse an old effect
    // even when the fresh request's first event happens to have the same local sequence id/text.
    LaunchedEffect(owner, owner.event) {
        val event = owner.event ?: return@LaunchedEffect
        delay(VOD_SEEK_FEEDBACK_LIFETIME_MS)
        owner.expire(event)
    }
}

/** Preserves the existing seek label text, position, styling and nonfocusable presentation. */
@Composable
internal fun VodSeekFeedbackLabel(
    owner: VodSeekFeedbackOwner,
    modifier: Modifier = Modifier,
) {
    owner.event?.let { feedback ->
        Text(
            feedback.text,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = .78f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}
