package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.prefs.AppThemeStyle

@Immutable
data class LiquidGlassColors(
  val isDark: Boolean,
  val glassSurface: Color,
  val glassSurfaceElevated: Color,
  val glassBorderTop: Color,
  val glassBorderBottom: Color,
  val shadowColor: Color,
  val heroStart: Color,
  val heroEnd: Color,
  val tealAccent: Color,
  val primaryAccent: Color,
  val softBlueBg: Color,
  val divider: Color,
)

val LocalLiquidGlassColors =
  staticCompositionLocalOf {
    LiquidGlassColors(
      isDark = false,
      glassSurface = SurfaceWhite.copy(alpha = 0.82f),
      glassSurfaceElevated = SurfaceWhite.copy(alpha = 0.94f),
      glassBorderTop = Color.White.copy(alpha = 0.95f),
      glassBorderBottom = BorderLight.copy(alpha = 0.75f),
      shadowColor = DeepNavy.copy(alpha = 0.08f),
      heroStart = DeepNavy,
      heroEnd = RoyalBlue,
      tealAccent = PremiumTeal,
      primaryAccent = PrimaryBlue,
      softBlueBg = LightBlue,
      divider = BorderLight,
    )
  }

@Composable
fun MunakasatTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  themeStyle: AppThemeStyle = AppThemeStyle.LIQUID_ROYAL,
  content: @Composable () -> Unit,
) {
  val primary =
    when (themeStyle) {
      AppThemeStyle.LIQUID_ROYAL -> PrimaryBlue
      AppThemeStyle.DEEP_NAVY -> if (darkTheme) RoyalBlue else DeepNavy
      AppThemeStyle.OCEAN_TEAL -> PremiumTeal
    }

  val secondary =
    when (themeStyle) {
      AppThemeStyle.LIQUID_ROYAL -> PremiumTeal
      AppThemeStyle.DEEP_NAVY -> RoyalBlue
      AppThemeStyle.OCEAN_TEAL -> RoyalBlue
    }

  val colorScheme =
    if (darkTheme) {
      darkColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = SurfaceElevatedDark,
        onPrimaryContainer = LightBlue,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF153642),
        onSecondaryContainer = CyanSoftBg,
        tertiary = InfoBlue,
        background = AppBackgroundDark,
        onBackground = MainTextDark,
        surface = SurfaceDark,
        onSurface = MainTextDark,
        surfaceVariant = SurfaceElevatedDark,
        onSurfaceVariant = SecondaryTextDark,
        outline = BorderDark,
        outlineVariant = BorderDark.copy(alpha = 0.6f),
        error = DangerRed,
        onError = Color.White,
      )
    } else {
      lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = LightBlue,
        onPrimaryContainer = DeepNavy,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = CyanSoftBg,
        onSecondaryContainer = DeepNavy,
        tertiary = InfoBlue,
        background = AppBackgroundLight,
        onBackground = MainTextLight,
        surface = SurfaceWhite,
        onSurface = MainTextLight,
        surfaceVariant = LightBlue.copy(alpha = 0.55f),
        onSurfaceVariant = SecondaryTextLight,
        outline = BorderLight,
        outlineVariant = BorderLight,
        error = DangerRed,
        onError = Color.White,
      )
    }

  val heroStart =
    when (themeStyle) {
      AppThemeStyle.LIQUID_ROYAL -> RoyalBlue
      AppThemeStyle.DEEP_NAVY -> DeepNavy
      AppThemeStyle.OCEAN_TEAL -> Color(0xFF0D9488)
    }
  val heroEnd =
    when (themeStyle) {
      AppThemeStyle.LIQUID_ROYAL -> PremiumTeal
      AppThemeStyle.DEEP_NAVY -> RoyalBlue
      AppThemeStyle.OCEAN_TEAL -> PrimaryBlue
    }

  val glassColors =
    if (darkTheme) {
      LiquidGlassColors(
        isDark = true,
        glassSurface = SurfaceDark.copy(alpha = 0.82f),
        glassSurfaceElevated = SurfaceElevatedDark.copy(alpha = 0.90f),
        glassBorderTop = Color.White.copy(alpha = 0.16f),
        glassBorderBottom = BorderDark.copy(alpha = 0.65f),
        shadowColor = Color.Black.copy(alpha = 0.35f),
        heroStart = heroStart,
        heroEnd = heroEnd,
        tealAccent = PremiumTeal,
        primaryAccent = primary,
        softBlueBg = SurfaceElevatedDark,
        divider = BorderDark,
      )
    } else {
      LiquidGlassColors(
        isDark = false,
        glassSurface = SurfaceWhite.copy(alpha = 0.80f),
        glassSurfaceElevated = SurfaceWhite.copy(alpha = 0.94f),
        glassBorderTop = Color.White.copy(alpha = 0.98f),
        glassBorderBottom = BorderLight.copy(alpha = 0.80f),
        shadowColor = DeepNavy.copy(alpha = 0.08f),
        heroStart = heroStart,
        heroEnd = heroEnd,
        tealAccent = PremiumTeal,
        primaryAccent = primary,
        softBlueBg = LightBlue,
        divider = BorderLight,
      )
    }

  CompositionLocalProvider(
    LocalLayoutDirection provides LayoutDirection.Rtl,
    LocalLiquidGlassColors provides glassColors,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = AppShapes,
      content = content,
    )
  }
}
