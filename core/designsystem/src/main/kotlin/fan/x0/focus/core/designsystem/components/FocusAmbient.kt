package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import fan.x0.focus.core.designsystem.theme.FocusLook

internal class FocusAmbientActivity {
    private var count = 0
    internal var working by mutableStateOf(false)
        private set
    internal val progress = mutableFloatStateOf(0f)

    internal fun acquire() {
        count += 1
        working = true
    }

    internal fun release() {
        count = (count - 1).coerceAtLeast(0)
        working = count > 0
    }
}

internal val LocalFocusAmbientActivity = staticCompositionLocalOf { FocusAmbientActivity() }

@Composable
fun FocusAmbientWorking(active: Boolean) {
    val activity = LocalFocusAmbientActivity.current
    DisposableEffect(activity, active) {
        if (active) activity.acquire()
        onDispose { if (active) activity.release() }
    }
}

@Composable
fun FocusAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val activity = remember { FocusAmbientActivity() }
    if (FocusLook.ambientBackground) FocusAmbientClock(activity)
    CompositionLocalProvider(LocalFocusAmbientActivity provides activity) {
        Box(modifier = modifier.fillMaxSize()) {
            if (FocusLook.ambientBackground) {
                FocusAmbientBackdrop(activity)
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            content()
        }
    }
}

@Composable
fun FocusAmbientSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val activity = LocalFocusAmbientActivity.current
    Box(modifier = modifier.fillMaxSize()) {
        if (FocusLook.ambientBackground) {
            FocusAmbientBackdrop(activity)
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        content()
    }
}
