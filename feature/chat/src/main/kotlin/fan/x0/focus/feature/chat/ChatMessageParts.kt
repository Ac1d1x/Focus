package fan.x0.focus.feature.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusChatBubble
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusChatBubbleStyle
import fan.x0.focus.core.designsystem.components.FocusSelectionBar
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.data.assistant.AssistantAvatarStore
import fan.x0.focus.data.assistant.AssistantReplyStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
internal fun ChatMessageBubble(
    entry: ChatEntry,
    fromUser: Boolean,
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    onCopy: (String) -> Unit,
    streaming: Boolean = false,
    showAssistantHeader: Boolean = false,
) {
    val text = when (entry) {
        is ChatEntry.User -> entry.text
        is ChatEntry.Assistant -> entry.text
        else -> return
    }
    val quote = when (entry) {
        is ChatEntry.User -> entry.replyTo
        is ChatEntry.Assistant -> entry.replyTo
    }
    val reactions = when (entry) {
        is ChatEntry.User -> entry.reactions
        is ChatEntry.Assistant -> entry.reactions
    }
    val key = entry.key
    val selecting = state.selectionKey == key
    val canMenu = entry.messageId != null
    val header: (@Composable () -> Unit)? = if (quote != null || showAssistantHeader) {
        {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showAssistantHeader) {
                    AssistantMessageHeader(
                        name = state.assistantName.ifBlank { stringResource(R.string.chat_title) },
                        emoji = state.assistantEmoji,
                        avatar = state.assistantAvatar,
                    )
                }
                quote?.let {
                    MessageQuote(
                        quote = it,
                        onClick = { onAction(ChatAction.JumpToMessage("m${it.id}")) },
                    )
                }
            }
        }
    } else {
        null
    }
    FocusChatBubble(
        text = text,
        fromUser = fromUser,
        style = state.replyStyle.toBubbleStyle(),
        streaming = streaming,
        highlighted = state.highlightKey == key,
        selectable = selecting,
        reactions = reactions,
        onLongPress = if (canMenu) {
            { onAction(ChatAction.OpenMessageMenu(key)) }
        } else {
            null
        },
        header = header,
        menu = {
            val menu = state.menu
            if (menu?.key == key) {
                MessageActionPopup(menu = menu, onAction = onAction, onCopy = onCopy)
            }
        },
    )
}

@Composable
private fun AssistantMessageHeader(
    name: String,
    emoji: String,
    avatar: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistantHeaderAvatar(name = name, emoji = emoji, avatar = avatar)
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssistantHeaderAvatar(name: String, emoji: String, avatar: String) {
    val store: AssistantAvatarStore = koinInject()
    val bitmap by produceState<Bitmap?>(initialValue = null, avatar) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                store.path(avatar)?.let { BitmapFactory.decodeFile(it) }
            }.getOrNull()
        }
    }
    val image = bitmap
    when {
        image != null -> Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        )
        emoji.isNotBlank() -> Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.labelMedium)
        }
        else -> {
            val hash = name.hashCode()
            FocusAvatar(
                style = AvatarStyle(preset = hash, palette = hash ushr 8),
                size = 28.dp,
            )
        }
    }
}

@Composable
private fun MessageQuote(
    quote: QuotedMessageUi,
    onClick: () -> Unit,
) {
    val content = LocalContentColor.current
    val author = quote.author.ifBlank {
        if (quote.fromUser) {
            stringResource(R.string.chat_quote_you)
        } else {
            stringResource(R.string.chat_title)
        }
    }
    Surface(
        onClick = hapticTap(onClick),
        enabled = !quote.deleted,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = content.copy(alpha = 0.12f),
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(content.copy(alpha = 0.7f)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (quote.deleted) {
                        stringResource(R.string.chat_quote_deleted)
                    } else {
                        quote.text
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SelectionModeBar(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSelectionBar(
        text = stringResource(R.string.chat_selection_mode),
        doneText = stringResource(R.string.chat_selection_done),
        onDone = onDone,
        modifier = modifier,
    )
}

private fun AssistantReplyStyle.toBubbleStyle(): FocusChatBubbleStyle = when (this) {
    AssistantReplyStyle.Flat -> FocusChatBubbleStyle.Flat
    AssistantReplyStyle.Bubble -> FocusChatBubbleStyle.Bubble
    AssistantReplyStyle.Card -> FocusChatBubbleStyle.Card
}
