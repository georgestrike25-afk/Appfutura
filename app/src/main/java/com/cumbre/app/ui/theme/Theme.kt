package com.cumbre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cumbre.app.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = CumbreColors.VerdeBosque,
    onPrimary = CumbreColors.Blanco,
    primaryContainer = Color(0xFFD3E6DA),
    onPrimaryContainer = CumbreColors.VerdeMontana,
    secondary = CumbreColors.VerdeMontana,
    onSecondary = CumbreColors.Blanco,
    secondaryContainer = Color(0xFFDCEAE1),
    onSecondaryContainer = CumbreColors.VerdeMontana,
    tertiary = CumbreColors.Naranja,
    onTertiary = CumbreColors.GrisOscuro,
    background = CumbreColors.GrisClaro,
    onBackground = CumbreColors.GrisOscuro,
    surface = CumbreColors.Blanco,
    onSurface = CumbreColors.GrisOscuro,
    surfaceVariant = Color(0xFFEDF2EF),
    onSurfaceVariant = Color(0xFF55615D),
    surfaceContainerLowest = CumbreColors.Blanco,
    surfaceContainerLow = CumbreColors.GrisClaro,
    surfaceContainer = Color(0xFFEFF3F1),
    surfaceContainerHigh = Color(0xFFE8EEEB),
    outline = Color(0xFFC9D6CF),
    outlineVariant = Color(0xFFE3E8E5),
    error = CumbreColors.Error,
    errorContainer = CumbreColors.ErrorContainer,
    onErrorContainer = CumbreColors.OnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FCF97),
    onPrimary = Color(0xFF00391F),
    primaryContainer = CumbreColors.DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFCFE8DA),
    secondary = CumbreColors.VerdeSuave,
    onSecondary = CumbreColors.VerdeMontana,
    secondaryContainer = CumbreColors.DarkPrimaryContainer,
    onSecondaryContainer = Color(0xFFCFE8DA),
    tertiary = CumbreColors.Naranja,
    onTertiary = CumbreColors.GrisOscuro,
    background = CumbreColors.DarkBackground,
    onBackground = CumbreColors.DarkOnSurface,
    surface = CumbreColors.DarkSurface,
    onSurface = CumbreColors.DarkOnSurface,
    surfaceVariant = CumbreColors.DarkSurfaceHigh,
    onSurfaceVariant = CumbreColors.DarkOnSurfaceVariant,
    surfaceContainerLowest = CumbreColors.DarkBackground,
    surfaceContainerLow = CumbreColors.DarkSurface,
    surfaceContainer = CumbreColors.DarkSurface,
    surfaceContainerHigh = CumbreColors.DarkSurfaceHigh,
    outline = CumbreColors.DarkOutline,
    outlineVariant = Color(0xFF26312E),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1D1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Colores semánticos y de cartografía que no forman parte del esquema Material. */
@Immutable
data class CumbreExtraColors(
    val topBar: Color,
    val onTopBar: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val track: Color,
    val trackHalo: Color,
    val position: Color,
    val recorded: Color,
    val mapControl: Color,
    val onMapControl: Color,
)

private val LightExtra = CumbreExtraColors(
    topBar = CumbreColors.VerdeMontana,
    onTopBar = CumbreColors.Blanco,
    warning = CumbreColors.Warning,
    warningContainer = CumbreColors.WarningContainer,
    onWarningContainer = CumbreColors.OnWarningContainer,
    success = CumbreColors.Success,
    successContainer = CumbreColors.SuccessContainer,
    onSuccessContainer = CumbreColors.OnSuccessContainer,
    track = CumbreColors.Track,
    trackHalo = CumbreColors.TrackHaloLight,
    position = CumbreColors.Naranja,
    recorded = CumbreColors.Recorded,
    mapControl = CumbreColors.Blanco,
    onMapControl = CumbreColors.VerdeMontana,
)

private val DarkExtra = CumbreExtraColors(
    topBar = Color(0xFF0B2E2A),
    onTopBar = CumbreColors.DarkOnSurface,
    warning = Color(0xFFFFB951),
    warningContainer = Color(0xFF3D2A00),
    onWarningContainer = Color(0xFFFFDDB0),
    success = Color(0xFF6FCF97),
    successContainer = Color(0xFF173A2C),
    onSuccessContainer = Color(0xFFBEEBD1),
    track = Color(0xFFFF4FA3),
    trackHalo = CumbreColors.TrackHaloDark,
    position = CumbreColors.Naranja,
    recorded = Color(0xFF7FB8FF),
    mapControl = CumbreColors.DarkSurfaceHigh,
    onMapControl = Color(0xFFE2EEE8),
)

val LocalCumbreColors = staticCompositionLocalOf { LightExtra }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

val CumbreShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Espaciado coherente en toda la app. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Altura mínima de controles táctiles durante la navegación. */
    val touchTarget = 52.dp
}

@Composable
fun CumbreTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalCumbreColors provides if (dark) DarkExtra else LightExtra,
        LocalIsDarkTheme provides dark,
    ) {
        MaterialTheme(colorScheme = colors, typography = CumbreTypography, shapes = CumbreShapes, content = content)
    }
}

object CumbreTheme {
    val extra: CumbreExtraColors
        @Composable get() = LocalCumbreColors.current
    val isDark: Boolean
        @Composable get() = LocalIsDarkTheme.current
}
