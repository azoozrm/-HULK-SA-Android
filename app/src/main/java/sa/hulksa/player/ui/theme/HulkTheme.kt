package sa.hulksa.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import sa.hulksa.player.R

@Immutable
data class HulkColors(
    val gold: Color = Color(0xFFE6C352),
    val goldBright: Color = Color(0xFFFFF0A8),
    val goldDeep: Color = Color(0xFF9A7A23),
    val background: Color = Color(0xFF030402),
    val surface: Color = Color(0xFF111108),
    val surfaceRaised: Color = Color(0xFF1B1A0E),
    val text: Color = Color(0xFFFFF9EB),
    val textMuted: Color = Color(0xFFB8B3A4),
    val line: Color = Color(0x47E6C352),
    val danger: Color = Color(0xFFFF746C),
)

val LocalHulkColors = staticCompositionLocalOf { HulkColors() }

@Composable
fun HulkTheme(content: @Composable () -> Unit) {
    val colors = HulkColors()
    val plex = FontFamily(
        Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
        Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
    )
    val baseTypography = Typography()
    val typography = Typography(
        displayLarge = baseTypography.displayLarge.withFamily(plex),
        displayMedium = baseTypography.displayMedium.withFamily(plex),
        displaySmall = baseTypography.displaySmall.withFamily(plex),
        headlineLarge = baseTypography.headlineLarge.withFamily(plex),
        headlineMedium = baseTypography.headlineMedium.withFamily(plex),
        headlineSmall = baseTypography.headlineSmall.withFamily(plex),
        titleLarge = baseTypography.titleLarge.withFamily(plex),
        titleMedium = baseTypography.titleMedium.withFamily(plex),
        titleSmall = baseTypography.titleSmall.withFamily(plex),
        bodyLarge = baseTypography.bodyLarge.withFamily(plex),
        bodyMedium = baseTypography.bodyMedium.withFamily(plex),
        bodySmall = baseTypography.bodySmall.withFamily(plex),
        labelLarge = baseTypography.labelLarge.withFamily(plex),
        labelMedium = baseTypography.labelMedium.withFamily(plex),
        labelSmall = baseTypography.labelSmall.withFamily(plex),
    )
    CompositionLocalProvider(
        LocalHulkColors provides colors,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = colors.gold,
                onPrimary = Color(0xFF100E04),
                background = colors.background,
                surface = colors.surface,
                onBackground = colors.text,
                onSurface = colors.text,
                error = colors.danger,
            ),
            typography = typography,
        ) {
            ProvideTextStyle(
                value = typography.bodyLarge.copy(color = colors.text),
                content = content,
            )
        }
    }
}

private fun TextStyle.withFamily(fontFamily: FontFamily): TextStyle = copy(fontFamily = fontFamily)
