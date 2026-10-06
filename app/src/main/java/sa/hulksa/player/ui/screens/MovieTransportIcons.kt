package sa.hulksa.player.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Movie-local ten-second transport glyphs.
 *
 * The approved Movies board uses one clean circular arrow with a single upright readable 10; the
 * installed classic Rounded Material glyphs were rejected in shape. These two vectors hold the
 * Material Symbols Rounded replay_10 / forward_10 geometry (Apache-2.0), whose numeral is drawn
 * once, centered and upright while the arrow is physically counterclockwise (rewind) and clockwise
 * (forward). The symbol 0..-960 viewport is translated into the positive 0..960 viewport. Keeping
 * them Movie-local leaves the shared Live/Series players untouched.
 */
private const val MOVIE_TRANSPORT_VIEWPORT = 960f

private const val MOVIE_REWIND_10_PATH =
    "M360 460 h-30 q-13 0 -21.5 -8.5 T300 430 q0 -13 8.5 -21.5 T330 400 h60 q13 0 21.5 8.5 T420 430 v180 q0 13 -8.5 21.5 T390 640 q-13 0 -21.5 -8.5 T360 610 v-150 Z m140 180 q-17 0 -28.5 -11.5 T460 600 v-160 q0 -17 11.5 -28.5 T500 400 h80 q17 0 28.5 11.5 T620 440 v160 q0 17 -11.5 28.5 T580 640 h-80 Z m20 -60 h40 v-120 h-40 v120 Z M339.5 851.5 q-65.5 -28.5 -114 -77 t-77 -114 Q120 595 120 520 q0 -17 11.5 -28.5 T160 480 q17 0 28.5 11.5 T200 520 q0 117 81.5 198.5 T480 800 q117 0 198.5 -81.5 T760 520 q0 -117 -81.5 -198.5 T480 240 h-6 l34 34 q12 12 11.5 28 T508 330 q-12 12 -28.5 12.5 T451 331 L348 228 q-12 -12 -12 -28 t12 -28 l103 -103 q12 -12 28.5 -11.5 T508 70 q11 12 11.5 28 T508 126 l-34 34 h6 q75 0 140.5 28.5 t114 77 q48.5 48.5 77 114 T840 520 q0 75 -28.5 140.5 t-77 114 q-48.5 48.5 -114 77 T480 880 q-75 0 -140.5 -28.5 Z"

private const val MOVIE_FORWARD_10_PATH =
    "M339.5 851.5 q-65.5 -28.5 -114 -77 t-77 -114 Q120 595 120 520 t28.5 -140.5 q28.5 -65.5 77 -114 t114 -77 Q405 160 480 160 h6 l-34 -34 q-12 -12 -11.5 -28 t11.5 -28 q12 -12 28.5 -12.5 T509 69 l103 103 q12 12 12 28 t-12 28 L509 331 q-12 12 -28.5 11.5 T452 330 q-11 -12 -11.5 -28 t11.5 -28 l34 -34 h-6 q-117 0 -198.5 81.5 T200 520 q0 117 81.5 198.5 T480 800 q117 0 198.5 -81.5 T760 520 q0 -17 11.5 -28.5 T800 480 q17 0 28.5 11.5 T840 520 q0 75 -28.5 140.5 t-77 114 q-48.5 48.5 -114 77 T480 880 q-75 0 -140.5 -28.5 Z M360 460 h-30 q-13 0 -21.5 -8.5 T300 430 q0 -13 8.5 -21.5 T330 400 h60 q13 0 21.5 8.5 T420 430 v180 q0 13 -8.5 21.5 T390 640 q-13 0 -21.5 -8.5 T360 610 v-150 Z m140 180 q-17 0 -28.5 -11.5 T460 600 v-160 q0 -17 11.5 -28.5 T500 400 h80 q17 0 28.5 11.5 T620 440 v160 q0 17 -11.5 28.5 T580 640 h-80 Z m20 -60 h40 v-120 h-40 v120 Z"

internal val MovieRewind10Icon: ImageVector by lazy {
    movieTransportIcon(name = "MovieRewind10", pathData = MOVIE_REWIND_10_PATH)
}

internal val MovieForward10Icon: ImageVector by lazy {
    movieTransportIcon(name = "MovieForward10", pathData = MOVIE_FORWARD_10_PATH)
}

private fun movieTransportIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = MOVIE_TRANSPORT_VIEWPORT,
        viewportHeight = MOVIE_TRANSPORT_VIEWPORT,
    ).apply {
        addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        )
    }.build()
