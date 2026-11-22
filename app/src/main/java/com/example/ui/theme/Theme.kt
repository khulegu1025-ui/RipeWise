package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = DeepSage,
    onPrimary = Color.White,
    primaryContainer = SageWash,
    onPrimaryContainer = ForestInk,
    secondary = GoldenMango,
    onSecondary = Color.White,
    secondaryContainer = LemonCream,
    onSecondaryContainer = MangoInk,
    tertiary = TerracottaBerry,
    onTertiary = Color.White,
    tertiaryContainer = PeachBlush,
    onTertiaryContainer = BerryInk,
    background = OatCream,
    onBackground = ForestInk,
    surface = PureCream,
    onSurface = ForestInk,
    surfaceVariant = WarmLinen,
    onSurfaceVariant = MutedOlive,
    surfaceContainerLowest = PureCream,
    surfaceContainerLow = SoftSageSurface,
    surfaceContainer = WarmLinen,
    outline = SoftOutline,
    outlineVariant = Color(0xFFE7E2D7)
)

private val DarkColorScheme = darkColorScheme(
    primary = LightSagePrimary,
    onPrimary = DeepForestBg,
    primaryContainer = Color(0xFF224D37),
    onPrimaryContainer = SoftCreamText,
    secondary = GoldenMango,
    onSecondary = Color(0xFF2E1E00),
    secondaryContainer = Color(0xFF473411),
    onSecondaryContainer = LemonCream,
    tertiary = Color(0xFFE68A76),
    onTertiary = Color(0xFF421209),
    tertiaryContainer = Color(0xFF5C2418),
    onTertiaryContainer = PeachBlush,
    background = DeepForestBg,
    onBackground = SoftCreamText,
    surface = DarkMossSurface,
    onSurface = SoftCreamText,
    surfaceVariant = DarkCardSurface,
    onSurfaceVariant = MutedSageText,
    surfaceContainerLowest = DeepForestBg,
    surfaceContainerLow = DarkMossSurface,
    surfaceContainer = DarkCardSurface,
    outline = Color(0xFF394A3F),
    outlineVariant = Color(0xFF2A382F)
)

val RipeWiseShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun RipeWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RipeWiseTypography,
        shapes = RipeWiseShapes,
        content = content
    )
}
