package sa.hulksa.player.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.graphics.vector.ImageVector
import sa.hulksa.player.data.FavoriteMutationOutcome
import sa.hulksa.player.data.FavoriteMutationStatus

internal enum class FavoriteFeedbackGlyph {
    ADDED,
    REMOVED,
    ERROR,
}

internal fun favoriteFeedbackIcon(glyph: FavoriteFeedbackGlyph): ImageVector = when (glyph) {
    FavoriteFeedbackGlyph.ADDED -> Icons.Rounded.Favorite
    FavoriteFeedbackGlyph.REMOVED -> Icons.Outlined.FavoriteBorder
    FavoriteFeedbackGlyph.ERROR -> Icons.Outlined.ErrorOutline
}

internal data class FavoriteFeedback(
    val message: String,
    val glyph: FavoriteFeedbackGlyph,
)

/**
 * The only success copy is derived from a real applied favorite outcome. Throttled, duplicate,
 * unchanged, Kids-rejected and stale-owner results stay silent; a durable failure is reported
 * truthfully through the same scoped notice host.
 */
internal fun favoriteMutationFeedback(
    outcome: FavoriteMutationOutcome,
    title: String,
): FavoriteFeedback? = when (outcome.status) {
    FavoriteMutationStatus.APPLIED_ADDED -> FavoriteFeedback(
        message = "تمت اضافة $title الى المفضلة",
        glyph = FavoriteFeedbackGlyph.ADDED,
    )
    FavoriteMutationStatus.APPLIED_REMOVED -> FavoriteFeedback(
        message = "تمت ازالة $title من المفضلة",
        glyph = FavoriteFeedbackGlyph.REMOVED,
    )
    FavoriteMutationStatus.PERSISTENCE_FAILED -> FavoriteFeedback(
        message = "تعذر تحديث المفضلة",
        glyph = FavoriteFeedbackGlyph.ERROR,
    )
    FavoriteMutationStatus.UNCHANGED,
    FavoriteMutationStatus.KIDS_REJECTED,
    FavoriteMutationStatus.OWNER_CHANGED,
    FavoriteMutationStatus.THROTTLED,
    -> null
}
