package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.LocalFocusCornerScale
import kotlinx.coroutines.flow.first

enum class FocusSignalTone { Info, Success, Warning, Error }

internal data class FocusSignalData(
    val id: Long,
    val message: String,
    val tone: FocusSignalTone,
    val icon: ImageVector?,
    val actionLabel: String?,
    val action: (() -> Unit)?,
    val durationMs: Long,
    val onFinished: (() -> Unit)?,
)

internal class FocusSignalEntry(val data: FocusSignalData) {
    val visible = MutableTransitionState(false)
    var dismissed = false
        private set

    fun dismiss() {
        dismissed = true
        visible.targetState = false
    }
}

class FocusSignalState {
    private val entries = mutableStateListOf<FocusSignalEntry>()
    internal val signals: List<FocusSignalEntry> get() = entries

    private var counter = 0L

    fun show(
        message: String,
        tone: FocusSignalTone = FocusSignalTone.Info,
        icon: ImageVector? = null,
        actionLabel: String? = null,
        action: (() -> Unit)? = null,
        durationMs: Long = 2_800L,
        onFinished: (() -> Unit)? = null,
    ) {
        if (message.isBlank()) return
        entries.add(
            0,
            FocusSignalEntry(
                FocusSignalData(
                    id = ++counter,
                    message = message,
                    tone = tone,
                    icon = icon,
                    actionLabel = actionLabel.takeIf { action != null && !it.isNullOrBlank() },
                    action = action,
                    durationMs = durationMs.coerceAtLeast(1_200L),
                    onFinished = onFinished,
                ),
            ),
        )
        while (entries.size > MaxSignals) {
            entries.removeAt(entries.lastIndex)
        }
    }

    fun dismiss() {
        entries.forEach { it.dismiss() }
    }

    internal fun dismiss(id: Long) {
        entries.firstOrNull { it.data.id == id }?.dismiss()
    }

    internal fun remove(id: Long) {
        entries.removeAll { it.data.id == id }
    }
}

private const val MaxSignals = 4

val LocalFocusSignal = staticCompositionLocalOf { FocusSignalState() }

@Composable
fun rememberFocusSignalState(): FocusSignalState = remember { FocusSignalState() }

@Composable
fun FocusSignalProvider(content: @Composable () -> Unit) {
    val state = rememberFocusSignalState()
    CompositionLocalProvider(LocalFocusSignal provides state) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            FocusSignalHost(state = state, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
fun FocusSnackbarHost(hostState: SnackbarHostState) {
    val signal = LocalFocusSignal.current
    val data = hostState.currentSnackbarData
    LaunchedEffect(data) {
        val snackbar = data ?: return@LaunchedEffect
        signal.show(
            message = snackbar.visuals.message,
            tone = FocusSignalTone.Info,
            actionLabel = snackbar.visuals.actionLabel,
            action = { snackbar.performAction() },
            durationMs = when (snackbar.visuals.duration) {
                SnackbarDuration.Short -> 3_000L
                SnackbarDuration.Long -> 5_500L
                SnackbarDuration.Indefinite -> 6_000L
            },
            onFinished = { snackbar.dismiss() },
        )
    }
}

@Composable
fun FocusSignalScaffold(content: @Composable () -> Unit) {
    val state = rememberFocusSignalState()
    CompositionLocalProvider(LocalFocusSignal provides state) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
            FocusSignalHost(
                state = state,
                modifier = Modifier.align(Alignment.TopCenter),
                statusBarPadding = false,
            )
        }
    }
}

@Composable
fun FocusSignalHost(
    state: FocusSignalState,
    modifier: Modifier = Modifier,
    statusBarPadding: Boolean = true,
) {
    val signals = state.signals
    val motion = MaterialTheme.motionScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            signals.forEach { entry ->
                key(entry.data.id) {
                    FocusSignalItem(
                        entry = entry,
                        state = state,
                        motion = motion,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusSignalItem(
    entry: FocusSignalEntry,
    state: FocusSignalState,
    motion: MotionScheme,
) {
    LaunchedEffect(entry) {
        if (!entry.dismissed) entry.visible.targetState = true
        snapshotFlow {
            entry.visible.isIdle && !entry.visible.currentState && !entry.visible.targetState
        }.first { it }
        state.remove(entry.data.id)
    }
    AnimatedVisibility(
        visibleState = entry.visible,
        enter = slideInVertically(motion.defaultSpatialSpec()) { -it } +
            fadeIn(motion.defaultEffectsSpec()) +
            scaleIn(
                animationSpec = motion.defaultSpatialSpec(),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.5f, 0f),
            ),
        exit = slideOutVertically(motion.fastSpatialSpec()) { -it } +
            fadeOut(motion.fastEffectsSpec()) +
            scaleOut(
                animationSpec = motion.fastSpatialSpec(),
                targetScale = 0.92f,
                transformOrigin = TransformOrigin(0.5f, 0f),
            ),
    ) {
        FocusSignalCard(
            data = entry.data,
            onDismiss = { state.dismiss(entry.data.id) },
        )
    }
}

@Composable
private fun FocusSignalCard(data: FocusSignalData, onDismiss: () -> Unit) {
    val colors = signalColors(data.tone)
    val scale = LocalFocusCornerScale.current
    val shape = RoundedCornerShape(24.dp * scale)
    val progress = remember(data.id) { Animatable(1f) }
    DisposableEffect(data.id) {
        onDispose { data.onFinished?.invoke() }
    }
    LaunchedEffect(data.id) {
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(data.durationMs.toInt(), easing = LinearEasing),
        )
        onDismiss()
    }
    val icon = data.icon ?: defaultSignalIcon(data.tone)
    Surface(
        onClick = onDismiss,
        shape = shape,
        color = colors.container,
        contentColor = colors.content,
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = data.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.content,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (data.action != null && data.actionLabel != null) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            data.action.invoke()
                        },
                    ) {
                        Text(
                            text = data.actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accent,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(colors.accent.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .background(colors.accent),
                )
            }
        }
    }
}

private data class SignalColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val onAccent: Color,
)

@Composable
private fun signalColors(tone: FocusSignalTone): SignalColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        FocusSignalTone.Info -> SignalColors(
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer,
            accent = scheme.secondary,
            onAccent = scheme.onSecondary,
        )
        FocusSignalTone.Success -> SignalColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
        )
        FocusSignalTone.Warning -> SignalColors(
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            accent = scheme.tertiary,
            onAccent = scheme.onTertiary,
        )
        FocusSignalTone.Error -> SignalColors(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            accent = scheme.error,
            onAccent = scheme.onError,
        )
    }
}

private fun defaultSignalIcon(tone: FocusSignalTone): ImageVector = when (tone) {
    FocusSignalTone.Info -> FocusIcons.Info
    FocusSignalTone.Success -> FocusIcons.Check
    FocusSignalTone.Warning, FocusSignalTone.Error -> FocusIcons.Warning
}
