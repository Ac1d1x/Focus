package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

data class PromptConfig(
    val main: String = "",
    val side: String = "",
)

private val Context.promptDataStore by preferencesDataStore(name = "prompt_settings")

@Single
class PromptSettings(@Provided private val context: Context) {
    private object Keys {
        val main = stringPreferencesKey("main_prompt")
        val side = stringPreferencesKey("side_prompt")
    }

    fun config(assistantId: String): Flow<PromptConfig> = context.promptDataStore.data.map { prefs ->
        PromptConfig(
            main = prefs[mainKey(assistantId)].orEmpty(),
            side = prefs[sideKey(assistantId)].orEmpty(),
        )
    }

    suspend fun configOnce(assistantId: String): PromptConfig = config(assistantId).first()

    suspend fun setMain(assistantId: String, value: String) {
        context.promptDataStore.edit { prefs -> prefs[mainKey(assistantId)] = value.take(MAX_PROMPT_CHARS) }
    }

    suspend fun setSide(assistantId: String, value: String) {
        context.promptDataStore.edit { prefs -> prefs[sideKey(assistantId)] = value.take(MAX_PROMPT_CHARS) }
    }

    suspend fun clear(assistantId: String) {
        context.promptDataStore.edit { prefs ->
            prefs.remove(mainKey(assistantId))
            prefs.remove(sideKey(assistantId))
        }
    }

    private fun mainKey(assistantId: String) =
        if (assistantId == AssistantProfile.DEFAULT_ID) Keys.main else stringPreferencesKey("main_prompt:$assistantId")

    private fun sideKey(assistantId: String) =
        if (assistantId == AssistantProfile.DEFAULT_ID) Keys.side else stringPreferencesKey("side_prompt:$assistantId")

    companion object {
        const val MAX_PROMPT_CHARS = 8_000
    }
}
