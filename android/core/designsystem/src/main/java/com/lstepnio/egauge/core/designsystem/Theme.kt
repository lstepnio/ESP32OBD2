package com.lstepnio.egauge.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class SemanticColors(val warning: Color, val critical: Color, val success: Color)
val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(EGaugeTokens.Dark.warning, EGaugeTokens.Dark.critical, EGaugeTokens.Dark.success)
}

private fun graphiteDark() = with(EGaugeTokens.Dark) {
    darkColorScheme(primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer, secondary = primary, secondaryContainer = surfaceRaised,
        onSecondaryContainer = onSurface, background = background, onBackground = onSurface,
        surface = surface, onSurface = onSurface, surfaceVariant = surfaceRaised,
        surfaceContainer = surface, surfaceContainerHigh = surfaceRaised, surfaceContainerLow = background,
        onSurfaceVariant = muted, outline = outline, outlineVariant = outline, error = critical)
}
private fun graphiteLight() = with(EGaugeTokens.Light) {
    lightColorScheme(primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer, secondary = primary, secondaryContainer = surfaceRaised,
        onSecondaryContainer = onSurface, background = background, onBackground = onSurface,
        surface = surface, onSurface = onSurface, surfaceVariant = surfaceRaised,
        surfaceContainer = surface, surfaceContainerHigh = surfaceRaised, surfaceContainerLow = background,
        onSurfaceVariant = muted, outline = outline, outlineVariant = outline, error = critical)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EGaugeTheme(dark: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false,
                content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) graphiteDark() else graphiteLight()
    val semantic = if (dark) with(EGaugeTokens.Dark) { SemanticColors(warning, critical, success) }
        else with(EGaugeTokens.Light) { SemanticColors(warning, critical, success) }
    val typography = Typography(
        headlineLarge = TextStyle(fontSize = EGaugeTokens.Type.title.sp, lineHeight = 38.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
        headlineSmall = TextStyle(fontSize = EGaugeTokens.Type.headline.sp, lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = EGaugeTokens.Type.body.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = EGaugeTokens.Type.label.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    )
    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialExpressiveTheme(colorScheme = colors, typography = typography,
            shapes = Shapes(medium = RoundedCornerShape(EGaugeTokens.Radius.control.dp),
                large = RoundedCornerShape(EGaugeTokens.Radius.card.dp),
                extraLarge = RoundedCornerShape(EGaugeTokens.Radius.hero.dp)), content = content)
    }
}
