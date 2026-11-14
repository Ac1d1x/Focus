package fan.x0.focus.data.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.error.AppError
import org.koin.mp.KoinPlatform

@Stable
class ReadAloudHandle(
    val playingKey: String?,
    val loading: Boolean,
    val autoRead: Boolean,
    val error: AppError?,
    val speak: (String, String) -> Unit,
    val stop: () -> Unit,
    val clearError: () -> Unit,
)

@Composable
fun rememberReadAloud(): ReadAloudHandle {
    val controller = remember {
        runCatching { KoinPlatform.getKoin().get<SpeechSynthesisController>() }.getOrNull()
    }
    if (controller == null) return remember { noOpReadAloud() }
    val state by controller.state.collectAsStateWithLifecycle()
    val config by controller.ttsConfig.collectAsStateWithLifecycle()
    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }
    return remember(controller, state, config) {
        ReadAloudHandle(
            playingKey = state.playingKey,
            loading = state.loading,
            autoRead = config.autoRead,
            error = state.error,
            speak = controller::speak,
            stop = controller::stop,
            clearError = controller::clearError,
        )
    }
}

private fun noOpReadAloud(): ReadAloudHandle = ReadAloudHandle(
    playingKey = null,
    loading = false,
    autoRead = false,
    error = null,
    speak = { _, _ -> },
    stop = {},
    clearError = {},
)
