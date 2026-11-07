package com.airmousepro.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.airmousepro.appSettings
import com.airmousepro.data.Accents
import com.airmousepro.data.AppSettings

@Composable
fun AirMouseTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val s = ctx.appSettings
    val dark = when (s.themeMode) {
        1, 2 -> true
        3 -> false
        else -> isSystemInDarkTheme()
    }
    val amoled = s.themeMode == 2 || (s.themeMode == 0 && dark)
    val accent = Color(Accents.getOrElse(s.accent) { Accents[0] }.second)

    val scheme = when {
        s.accent == -1 && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = if (amoled) Color(0xFF000000) else Color(0xFF0E0E12),
            surface = if (amoled) Color(0xFF111114) else Color(0xFF18181D),
            surfaceVariant = if (amoled) Color(0xFF1E1E23) else Color(0xFF24242B),
            onBackground = Color(0xFFF5F5F7),
            onSurface = Color(0xFFF5F5F7),
            outline = Color(0xFF3A3A42),
        )
        else -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = Color(0xFFF4F4F8),
            surface = Color.White,
            surfaceVariant = Color(0xFFE8E8EE),
            onBackground = Color(0xFF111114),
            onSurface = Color(0xFF111114),
            outline = Color(0xFFC9C9D2),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

/** Haptic ticks respecting the strength setting: 0 off, 1 soft, 2 crisp. */
object Haptics {
    fun tap(view: View, s: AppSettings) {
        when (s.haptics) {
            1 -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            2 -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
            )
        }
    }

    /** A lighter tick for detents on the dial and for drag start. */
    fun tick(view: View, s: AppSettings) {
        if (s.haptics == 0) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
