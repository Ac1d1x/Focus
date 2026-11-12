package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

enum class AssistantReplyStyle { Flat, Bubble, Card }

private val Context.chatAppearanceDataStore by preferencesDataStore(name = "chat_appearance_settings")

@Single
class ChatAppearanceSettings(@Provided private val context: Context) {
    private val replyStyleKey = stringPreferencesKey("assistant_reply_style")

    val replyStyle: Flow<AssistantReplyStyle> = context.chatAppearanceDataStore.data.map { prefs ->
        prefs[replyStyleKey]
            ?.let { raw -> AssistantReplyStyle.entries.firstOrNull { it.name == raw } }
            ?: AssistantReplyStyle.Bubble
    }

    suspend fun setReplyStyle(style: AssistantReplyStyle) {
        context.chatAppearanceDataStore.edit { prefs ->
            prefs[replyStyleKey] = style.name
        }
    }
}
