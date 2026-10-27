package com.ivy.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

// the phone app's colors
val Ivy = Color(0xFF6B4DFF)
val Green = Color(0xFF14CC9E)
val Red = Color(0xFFFF4060)
val Orange = Color(0xFFF29F30)
val Gray = Color(0xFF939199)

private val IvyLight = Color(0xFFD5CCFF)
private val IvyDark = Color(0xFF352680)
private val White = Color(0xFFFAFAFA)
private val MediumWhite = Color(0xFFEFEEF0)
private val Black = Color(0xFF111114)
private val MediumBlack = Color(0xFF2B2C2D)
private val DarkSurface = Color(0xFF1C1C20)

// the phone app's fonts: Raleway for titles, Open Sans for text and numbers
private val Raleway = FontFamily(
    Font("font/raleway_semibold.ttf", FontWeight.SemiBold),
    Font("font/raleway_bold.ttf", FontWeight.Bold),
    Font("font/raleway_extrabold.ttf", FontWeight.ExtraBold),
    Font("font/raleway_black.ttf", FontWeight.Black),
)

private val OpenSans = FontFamily(
    Font("font/opensans_regular.ttf", FontWeight.Normal),
    Font("font/opensans_semibold.ttf", FontWeight.SemiBold),
    Font("font/opensans_bold.ttf", FontWeight.Bold),
)

/** Amounts: Open Sans, bold */
val NumberStyle = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Bold)

private val Typo = Typography(
    headlineLarge = TextStyle(fontFamily = Raleway, fontWeight = FontWeight.Black, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = Raleway, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Raleway, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Raleway, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Raleway, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = OpenSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

private val LightColors = lightColorScheme(
    primary = Ivy,
    onPrimary = White,
    primaryContainer = IvyLight,
    onPrimaryContainer = IvyDark,
    background = White,
    onBackground = Black,
    surface = Color.White,
    onSurface = Black,
    surfaceVariant = MediumWhite,
    onSurfaceVariant = Gray,
    outline = MediumWhite,
)

private val DarkColors = darkColorScheme(
    primary = Ivy,
    onPrimary = White,
    primaryContainer = IvyDark,
    onPrimaryContainer = IvyLight,
    background = Black,
    onBackground = White,
    surface = DarkSurface,
    onSurface = White,
    surfaceVariant = MediumBlack,
    onSurfaceVariant = Gray,
    outline = MediumBlack,
)

/** Light or dark, following Windows. */
@Composable
fun IvyTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typo,
        content = content,
    )
}
