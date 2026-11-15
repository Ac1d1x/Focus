package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import kotlinx.coroutines.flow.first
import kotlin.math.min

enum class FocusChatBubbleStyle { Flat, Bubble, Card }

private class ChatBubbleShape(
    private val fromUser: Boolean,
    private val tail: Boolean,
    private val topNear: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = with(density) {
            val pad = 2.dp.toPx()
            val inset = 8.dp.toPx()
            val w = size.width
            val h = size.height
            val radius = 17.dp.toPx()
            val rad = min(radius, (h - pad) / 2f)
            val near = min(6.dp.toPx(), radius)
            val small = 6.dp.toPx()
            val rt = min(if (topNear) near else rad, (h - pad) / 2f)
            val path = Path()
            if (tail) {
                path.moveTo(w - 2.6f.dp.toPx(), h - pad)
                path.lineTo(pad + rad, h - pad)
                path.arcTo(
                    Rect(pad, h - pad - rad * 2f, pad + rad * 2f, h - pad),
                    90f,
                    90f,
                    false,
                )
                path.lineTo(pad, pad + rad)
                path.arcTo(
                    Rect(pad, pad, pad + rad * 2f, pad + rad * 2f),
                    180f,
                    90f,
                    false,
                )
                path.lineTo(w - inset - rt, pad)
                path.arcTo(
                    Rect(w - inset - rt * 2f, pad, w - inset, pad + rt * 2f),
                    270f,
                    90f,
                    false,
                )
                path.lineTo(w - inset, h - pad - small - 3.dp.toPx())
                path.arcTo(
                    Rect(
                        w - inset,
                        h - pad - small * 2f - 9.dp.toPx(),
                        w - 7.dp.toPx() + small * 2f,
                        h - pad - 1.dp.toPx(),
                    ),
                    180f,
                    -83f,
                    false,
                )
                path.close()
            } else {
                path.addRoundRect(
                    RoundRect(
                        left = pad,
                        top = pad,
                        right = w - inset,
                        bottom = h - pad,
                        topLeftCornerRadius = CornerRadius(rad),
                        topRightCornerRadius = CornerRadius(rt),
                        bottomRightCornerRadius = CornerRadius(min(near, (h - pad) / 2f)),
                        bottomLeftCornerRadius = CornerRadius(rad),
                    ),
                )
            }
            path
        }
        if (!fromUser) {
            path.transform(
                Matrix().apply {
                    translate(size.width, 0f)
                    scale(-1f, 1f)
                },
            )
        }
        return Outline.Generic(path)
    }
}

@Composable
fun FocusChatBubble(
    text: String,
    fromUser: Boolean,
    modifier: Modifier = Modifier,
    style: FocusChatBubbleStyle = FocusChatBubbleStyle.Bubble,
    streaming: Boolean = false,
    highlighted: Boolean = false,
    selectable: Boolean = false,
    reactions: List<String> = emptyList(),
    onLongPress: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
    tail: Boolean = true,
    topNear: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val totalMaxWidth = maxWidth * if (fromUser) 0.82f else 0.9f
        val bubbleMaxWidth = totalMaxWidth.coerceAtLeast(64.dp)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box {
                if (fromUser) {
                    UserBubble(
                        text = text,
                        maxWidth = bubbleMaxWidth,
                        highlighted = highlighted,
                        selectable = selectable,
                        reactions = reactions,
                        onLongPress = onLongPress,
                        header = header,
                    )
                } else {
                    AssistantBubble(
                        text = text,
                        style = style,
                        maxWidth = bubbleMaxWidth,
                        tail = tail,
                        topNear = topNear,
                        streaming = streaming,
                        highlighted = highlighted,
                        selectable = selectable,
                        reactions = reactions,
                        onLongPress = onLongPress,
                        header = header,
                    )
                }
                menu?.invoke()
            }
        }
    }
}

@Composable
private fun UserBubble(
    text: String,
    maxWidth: Dp,
    highlighted: Boolean,
    selectable: Boolean,
    reactions: List<String>,
    onLongPress: (() -> Unit)?,
    header: (@Composable () -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalFocusMotion.current
    val shape = ChatBubbleShape(fromUser = true, tail = true, topNear = false)
    val container by animateColorAsState(
        targetValue = if (highlighted) scheme.tertiaryContainer else scheme.primary,
        animationSpec = motion.effectsDefault(),
        label = "userBubbleContainer",
    )
    val content by animateColorAsState(
        targetValue = if (highlighted) scheme.onTertiaryContainer else scheme.onPrimary,
        animationSpec = motion.effectsDefault(),
        label = "userBubbleContent",
    )
    Surface(
        modifier = Modifier
            .widthIn(min = 64.dp, max = maxWidth)
            .animateContentSize(motion.spatialDefault())
            .bubbleGestures(
                shape = shape,
                contentColor = content,
                selectable = selectable,
                onLongPress = onLongPress,
            ),
        shape = shape,
        color = container,
        contentColor = content,
        border = if (selectable) BorderStroke(1.dp, content) else null,
        tonalElevation = 0.dp,
    ) {
        Column {
            Column(
                modifier = Modifier.padding(start = 11.dp, end = 17.dp, top = 9.dp, bottom = 8.dp),
            ) {
                header?.let {
                    it()
                    Spacer(Modifier.height(6.dp))
                }
                if (selectable) {
                    SelectionContainer {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            BubbleReactions(
                reactions = reactions,
                fromUser = true,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(start = 6.dp, end = 17.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun AssistantBubble(
    text: String,
    style: FocusChatBubbleStyle,
    maxWidth: Dp,
    tail: Boolean,
    topNear: Boolean,
    streaming: Boolean,
    highlighted: Boolean,
    selectable: Boolean,
    reactions: List<String>,
    onLongPress: (() -> Unit)?,
    header: (@Composable () -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalFocusMotion.current
    val shape = when (style) {
        FocusChatBubbleStyle.Flat -> RectangleShape
        FocusChatBubbleStyle.Bubble -> ChatBubbleShape(fromUser = false, tail = tail, topNear = topNear)
        FocusChatBubbleStyle.Card -> ChatBubbleShape(fromUser = false, tail = tail, topNear = topNear)
    }
    val container by animateColorAsState(
        targetValue = when {
            style == FocusChatBubbleStyle.Flat -> Color.Transparent
            highlighted -> scheme.secondaryContainer
            streaming -> scheme.surfaceContainer
            else -> scheme.surfaceContainerLow
        },
        animationSpec = motion.effectsDefault(),
        label = "bubbleContainer",
    )
    val border = when {
        selectable -> BorderStroke(1.dp, scheme.primary)
        style == FocusChatBubbleStyle.Card -> BorderStroke(1.dp, scheme.outlineVariant)
        else -> null
    }
    val horizontalStart = if (style == FocusChatBubbleStyle.Flat) 0.dp else 17.dp
    val horizontalEnd = if (style == FocusChatBubbleStyle.Flat) 0.dp else 11.dp
    val vertical = if (style == FocusChatBubbleStyle.Flat) 4.dp else 9.dp
    val textModifier = if (style == FocusChatBubbleStyle.Flat && highlighted) {
        Modifier.border(2.dp, scheme.primary, MaterialTheme.shapes.medium).padding(6.dp)
    } else {
        Modifier
    }
    val body: @Composable () -> Unit = {
        Column {
            Column(
                modifier = Modifier.padding(
                    start = horizontalStart,
                    end = horizontalEnd,
                    top = vertical,
                    bottom = if (style == FocusChatBubbleStyle.Flat) 4.dp else 8.dp,
                ),
            ) {
                header?.let {
                    it()
                    Spacer(Modifier.height(6.dp))
                }
                Crossfade(
                    targetState = text.isBlank() && streaming,
                    animationSpec = motion.effectsDefault(),
                    label = "bubbleBody",
                ) { waiting ->
                    when {
                        waiting -> FocusAvatar(
                            style = AvatarStyle(preset = 0, palette = 0),
                            size = 32.dp,
                            state = AvatarState.Orbit,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        selectable -> SelectionContainer {
                            FocusMarkdown(text = text, modifier = textModifier, color = scheme.onSurface, streaming = streaming)
                        }
                        else -> FocusMarkdown(text = text, modifier = textModifier, color = scheme.onSurface, streaming = streaming)
                    }
                }
            }
            BubbleReactions(
                reactions = reactions,
                fromUser = false,
                modifier = Modifier.padding(start = 17.dp, end = 6.dp, bottom = 6.dp),
            )
        }
    }
    when (style) {
        FocusChatBubbleStyle.Flat -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(motion.spatialDefault())
                .bubbleGestures(
                    shape = shape,
                    contentColor = scheme.onSurface,
                    selectable = selectable,
                    onLongPress = onLongPress,
                ),
        ) { body() }
        FocusChatBubbleStyle.Bubble -> AssistantBubbleSurface(
            body = body,
            shape = shape,
            container = container,
            border = border,
            selectable = selectable,
            onLongPress = onLongPress,
            maxWidth = maxWidth,
        )
        FocusChatBubbleStyle.Card -> AssistantBubbleSurface(
            body = body,
            shape = shape,
            container = container,
            border = border,
            selectable = selectable,
            onLongPress = onLongPress,
            maxWidth = maxWidth,
        )
    }
}

@Composable
private fun AssistantBubbleSurface(
    body: @Composable () -> Unit,
    shape: Shape,
    container: Color,
    border: BorderStroke?,
    selectable: Boolean,
    onLongPress: (() -> Unit)?,
    maxWidth: Dp,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .widthIn(min = 64.dp, max = maxWidth)
            .animateContentSize(motion.spatialDefault())
            .bubbleGestures(
                shape = shape,
                contentColor = scheme.onSurface,
                selectable = selectable,
                onLongPress = onLongPress,
            ),
        shape = shape,
        color = container,
        contentColor = scheme.onSurface,
        border = border,
    ) { body() }
}

@Composable
private fun BubbleReactions(
    reactions: List<String>,
    fromUser: Boolean,
    modifier: Modifier = Modifier,
) {
    var retained by remember { mutableStateOf(reactions) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { initialized = true }
    LaunchedEffect(reactions) {
        retained = (retained + reactions).distinct()
    }
    if (retained.isEmpty()) return
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            retained.forEach { reaction ->
                key(reaction) {
                    BubbleReaction(
                        reaction = reaction,
                        count = reactions.count { it == reaction }.coerceAtLeast(1),
                        visible = reaction in reactions,
                        animateEnter = initialized,
                        fromUser = fromUser,
                        onExited = { retained = retained - reaction },
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleReaction(
    reaction: String,
    count: Int,
    visible: Boolean,
    animateEnter: Boolean,
    fromUser: Boolean,
    onExited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val state = remember { MutableTransitionState(if (animateEnter) false else visible) }
    SideEffect { state.targetState = visible }
    LaunchedEffect(Unit) {
        snapshotFlow { !state.currentState && !state.targetState }.first { it }
        onExited()
    }
    val origin = TransformOrigin(if (fromUser) 1f else 0f, 1f)
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(motion.effectsFast()) +
            scaleIn(
                animationSpec = motion.spatialDefault(),
                initialScale = 0.6f,
                transformOrigin = origin,
            ),
        exit = fadeOut(motion.effectsFast()) +
            scaleOut(
                animationSpec = motion.spatialFast(),
                targetScale = 0.6f,
                transformOrigin = origin,
            ),
    ) {
        FilterChip(
            selected = true,
            onClick = {},
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = reaction,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            },
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$reaction, $count" },
            shape = MaterialTheme.shapes.extraLarge,
            colors = if (fromUser) {
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = scheme.onPrimary.copy(alpha = 0.16f),
                    selectedLabelColor = scheme.onPrimary,
                )
            } else {
                FilterChipDefaults.filterChipColors()
            },
        )
    }
}

@Composable
private fun Modifier.bubbleGestures(
    shape: Shape,
    contentColor: Color,
    selectable: Boolean,
    onLongPress: (() -> Unit)?,
): Modifier {
    if (selectable || onLongPress == null) return this
    val interaction = remember { MutableInteractionSource() }
    return this
        .clip(shape)
        .combinedClickable(
            interactionSource = interaction,
            indication = ripple(color = contentColor),
            role = Role.Button,
            onClick = {},
            onLongClick = hapticLongPress(onLongPress),
        )
}

@Composable
fun FocusLabeledField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
