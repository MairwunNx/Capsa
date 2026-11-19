package com.mairwunnx.capsa.samples

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

private val Purple = Color(0xFF7F52FF)
private val LightPurple = Color(0xFFCBB8FF)

/** Material 3 in light and dark, with type a size larger than usual: the videos of the demo play on small screens. */
@Composable
fun SamplesTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = LightPurple) else lightColorScheme(primary = Purple)
    MaterialTheme(colorScheme = colors, typography = Typography().scaled(1.15f), content = content)
}

private fun Typography.scaled(factor: Float) = Typography(
    displayLarge = displayLarge.scaled(factor),
    displayMedium = displayMedium.scaled(factor),
    displaySmall = displaySmall.scaled(factor),
    headlineLarge = headlineLarge.scaled(factor),
    headlineMedium = headlineMedium.scaled(factor),
    headlineSmall = headlineSmall.scaled(factor),
    titleLarge = titleLarge.scaled(factor),
    titleMedium = titleMedium.scaled(factor),
    titleSmall = titleSmall.scaled(factor),
    bodyLarge = bodyLarge.scaled(factor),
    bodyMedium = bodyMedium.scaled(factor),
    bodySmall = bodySmall.scaled(factor),
    labelLarge = labelLarge.scaled(factor),
    labelMedium = labelMedium.scaled(factor),
    labelSmall = labelSmall.scaled(factor),
)

private fun TextStyle.scaled(factor: Float) = copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)
