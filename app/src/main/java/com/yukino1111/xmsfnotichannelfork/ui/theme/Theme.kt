package com.yukino1111.xmsfnotichannelfork.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FallbackLightColors = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9DF2DC),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8DF),
    onSecondaryContainer = Color(0xFF07201A),
    tertiary = Color(0xFF426278),
    tertiaryContainer = Color(0xFFC7E7FF),
    surface = Color(0xFFF5FBF7),
    surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE3E9E5),
)

private val FallbackDarkColors = darkColorScheme(
    primary = Color(0xFF81D5C0),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFF9DF2DC),
    secondary = Color(0xFFB1CCC3),
    onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF334B44),
    onSecondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFFA9CBE3),
    tertiaryContainer = Color(0xFF2A4A5F),
    surface = Color(0xFF0F1513),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotiChannelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = runCatching {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrElse {
        if (darkTheme) FallbackDarkColors else FallbackLightColors
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
