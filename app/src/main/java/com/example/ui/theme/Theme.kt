package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkStudioColorScheme = darkColorScheme(
    primary = Gold400,
    onPrimary = Obsidian950,
    primaryContainer = GoldTintDark,
    onPrimaryContainer = Gold300,
    secondary = PurpleAccent400,
    onSecondary = PureWhite,
    secondaryContainer = PurpleTintDark,
    onSecondaryContainer = PurpleAccent300,
    tertiary = EmeraldSuccessDark,
    onTertiary = Obsidian950,
    tertiaryContainer = EmeraldTintDark,
    onTertiaryContainer = EmeraldSuccessDark,
    background = Obsidian950,
    onBackground = Slate200,
    surface = PurpleCardSurface,
    onSurface = Slate200,
    surfaceVariant = PurpleCardElevated,
    onSurfaceVariant = Slate400,
    outline = PurpleBorder,
    outlineVariant = PurpleBorder,
    error = CrimsonErrorDark,
    onError = Obsidian950,
    errorContainer = CrimsonTintDark,
    onErrorContainer = CrimsonErrorDark,
)

val MiniRecapShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun MiniRecapTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkStudioColorScheme,
        typography = MiniRecapTypography,
        shapes = MiniRecapShapes,
        content = content,
    )
}
