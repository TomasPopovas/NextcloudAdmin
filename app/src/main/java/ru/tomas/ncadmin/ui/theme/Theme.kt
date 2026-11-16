package ru.tomas.ncadmin.ui.theme

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

private val NcBlue = Color(0xFF0082C9)
private val NcBlueDark = Color(0xFF00639B)
private val NcBlueLight = Color(0xFF6CB4EE)

private val LightColors = lightColorScheme(
    primary = NcBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E9F7),
    secondary = NcBlueDark,
    tertiary = Color(0xFF2E7D32),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = NcBlueLight,
    onPrimary = Color(0xFF00344F),
    primaryContainer = NcBlueDark,
    secondary = NcBlueLight,
    tertiary = Color(0xFF81C784),
    error = Color(0xFFF2B8B5),
)

@Composable
fun NcAdminTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
