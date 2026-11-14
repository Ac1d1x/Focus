package fan.x0.focus.core.designsystem.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import fan.x0.focus.core.designsystem.components.FocusHaptics
import fan.x0.focus.core.designsystem.components.LocalFocusHaptics

// One accessor for the baseline scale and the emphasized roles used across the app.
val LocalFocusTypography = staticCompositionLocalOf { FocusTypography() }
val LocalFocusMotion = staticCompositionLocalOf { FocusMotionDefaults }

fun focusColorScheme(context: Context, dark: Boolean): ColorScheme {
    FocusLook.init(context)
    return resolveScheme(context, dark)
}

@Composable
fun FocusTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    remember(context) { FocusLook.init(context) }
    val cornerScale = FocusLook.cornerScale
    val dark = darkTheme ?: when (FocusLook.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = remember(context, dark, FocusLook.dynamicColor, FocusLook.seed, FocusLook.amoled) {
        resolveScheme(context, dark)
    }
    val shapes = remember(cornerScale) { focusShapes(cornerScale) }
    val typography = FocusTypography()
    val hapticFeedback = LocalHapticFeedback.current
    val haptics = remember(hapticFeedback) { FocusHaptics(hapticFeedback) }
    CompositionLocalProvider(
        LocalFocusTypography provides typography,
        LocalFocusMotion provides FocusMotionDefaults,
        LocalFocusHaptics provides haptics,
        LocalFocusCornerScale provides cornerScale,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography.baseline,
            shapes = shapes,
            content = content,
        )
    }
}
