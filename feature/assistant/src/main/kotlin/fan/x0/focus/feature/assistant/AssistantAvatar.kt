package fan.x0.focus.feature.assistant

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.data.assistant.AssistantAvatarStore
import fan.x0.focus.data.assistant.AssistantProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun AssistantAvatar(
    assistant: AssistantProfile,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val store: AssistantAvatarStore = koinInject()
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, assistant.avatar) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                store.path(assistant.avatar)?.let { BitmapFactory.decodeFile(it) }
            }.getOrNull()
        }
    }
    val image = bitmap
    when {
        image != null -> Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
        assistant.emoji.isNotBlank() -> Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = assistant.emoji,
                style = if (size > 40.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            )
        }
        else -> {
            val hash = assistant.id.hashCode()
            FocusAvatar(
                style = AvatarStyle(preset = hash, palette = hash ushr 8),
                size = size,
                modifier = modifier,
            )
        }
    }
}
