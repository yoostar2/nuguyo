package com.nuguyo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandNavy = Color(0xFF1B3A5C)
private val BrandAmber = Color(0xFFFFC53D)

private val LightScheme = lightColorScheme(
    primary = BrandNavy,
    secondary = Color(0xFF4A6E8F),
    tertiary = BrandAmber,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FC4E8),
    secondary = Color(0xFFB0C8DD),
    tertiary = BrandAmber,
)

@Composable
fun NuguyoTheme(
    // One UI 의 사용자 색상을 따라가되, 원하면 브랜드 색으로 고정할 수 있게 열어 둔다.
    useDynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
