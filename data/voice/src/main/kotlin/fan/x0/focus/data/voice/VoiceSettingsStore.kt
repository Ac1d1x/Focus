package fan.x0.focus.data.voice

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private val Context.voiceDataStore by preferencesDataStore(name = "voice_settings")

@Single
class VoiceSettingsStore(@Provided private val context: Context) {
    private object Keys {
        val engine = stringPreferencesKey("engine")
        val cloudProviderId = stringPreferencesKey("cloud_provider_id")
        val cloudModel = stringPreferencesKey("cloud_model")
        val ttsEngine = stringPreferencesKey("tts_engine")
        val ttsProviderId = stringPreferencesKey("tts_provider_id")
        val ttsVoice = stringPreferencesKey("tts_voice")
        val ttsAutoRead = booleanPreferencesKey("tts_auto_read")
    }

    val config: Flow<VoiceConfig> = context.voiceDataStore.data.map { prefs ->
        VoiceConfig(
            engine = prefs[Keys.engine]
                ?.let { runCatching { VoiceEngine.valueOf(it) }.getOrNull() }
                ?: VoiceEngine.Local,
            cloudProviderId = prefs[Keys.cloudProviderId].orEmpty(),
            cloudModel = prefs[Keys.cloudModel]?.ifBlank { DEFAULT_CLOUD_MODEL } ?: DEFAULT_CLOUD_MODEL,
        )
    }

    val ttsConfig: Flow<TtsConfig> = context.voiceDataStore.data.map { prefs ->
        TtsConfig(
            engine = prefs[Keys.ttsEngine]
                ?.let { runCatching { TtsEngine.valueOf(it) }.getOrNull() }
                ?: TtsEngine.System,
            providerId = prefs[Keys.ttsProviderId].orEmpty(),
            voice = TtsVoice.fromId(prefs[Keys.ttsVoice]),
            autoRead = prefs[Keys.ttsAutoRead] ?: false,
        )
    }

    suspend fun configOnce(): VoiceConfig = config.first()

    suspend fun ttsConfigOnce(): TtsConfig = ttsConfig.first()

    suspend fun setEngine(engine: VoiceEngine) {
        context.voiceDataStore.edit { it[Keys.engine] = engine.name }
    }

    suspend fun setCloudProvider(providerId: String) {
        context.voiceDataStore.edit { it[Keys.cloudProviderId] = providerId }
    }

    suspend fun setCloudModel(model: String) {
        context.voiceDataStore.edit { it[Keys.cloudModel] = model.trim() }
    }

    suspend fun setTtsEngine(engine: TtsEngine) {
        context.voiceDataStore.edit { it[Keys.ttsEngine] = engine.name }
    }

    suspend fun setTtsProvider(providerId: String) {
        context.voiceDataStore.edit { it[Keys.ttsProviderId] = providerId }
    }

    suspend fun setTtsVoice(voice: TtsVoice) {
        context.voiceDataStore.edit { it[Keys.ttsVoice] = voice.id }
    }

    suspend fun setTtsAutoRead(enabled: Boolean) {
        context.voiceDataStore.edit { it[Keys.ttsAutoRead] = enabled }
    }

    companion object {
        const val DEFAULT_CLOUD_MODEL = "whisper-1"
    }
}
