package com.calories.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.calories.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6B4E), onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0D2), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6357), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9D8), onSecondaryContainer = Color(0xFF0A1F15),
    tertiary = Color(0xFF3D6373), tertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFFF7FAF6), onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF7FAF6), onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDCE5DD), onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F5F0),
    surfaceContainer = Color(0xFFEBF0EA), surfaceContainerHigh = Color(0xFFE5EAE4), surfaceContainerHighest = Color(0xFFE0E4DF),
    outline = Color(0xFF707973), outlineVariant = Color(0xFFC0C9C1),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD7B1), onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005237), onPrimaryContainer = Color(0xFFA9F4CC),
    secondary = Color(0xFFB4CCBC), onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B3F), onSecondaryContainer = Color(0xFFCFE9D8),
    tertiary = Color(0xFFA5CCDF), tertiaryContainer = Color(0xFF244C5B),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF181D1A),
    surfaceContainer = Color(0xFF1C211E), surfaceContainerHigh = Color(0xFF262B28), surfaceContainerHighest = Color(0xFF313633),
    outline = Color(0xFF8A938C), outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
)

/** Cores fixas por nutriente (mesmas em barras, gráficos e legendas; sempre acompanhadas de rótulo). */
@Immutable
data class NutrientColors(val protein: Color, val carbohydrate: Color, val fat: Color, val fiber: Color, val over: Color)

private val LightNutrientColors = NutrientColors(Color(0xFFC2185B), Color(0xFFB26A00), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFB3261E))
private val DarkNutrientColors = NutrientColors(Color(0xFFF48FB1), Color(0xFFFFB74D), Color(0xFF90CAF9), Color(0xFFA5D6A7), Color(0xFFF2B8B5))

val LocalNutrientColors = staticCompositionLocalOf { LightNutrientColors }

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Números alinhados em colunas (algarismos tabulares). */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun CaloriesTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalNutrientColors provides if (dark) DarkNutrientColors else LightNutrientColors) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}

@Suppress("unused")
private val sampleSize = 14.sp
