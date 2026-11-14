package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Serializable
data class DeepSeekConfig(
    val enabled: Boolean = true,
    val webSearch: Boolean = true,
    val readLinks: Boolean = true,
    val title: Boolean = true,
    val userMeta: Boolean = true,
    val location: String = "",
    val files: Boolean = true,
    val richLayout: Boolean = false,
)

private val Context.deepSeekDataStore by preferencesDataStore(name = "deepseek_settings")

@Single
class DeepSeekSettings(@Provided private val context: Context) {
    private val key = stringPreferencesKey("config_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val config: Flow<DeepSeekConfig> = context.deepSeekDataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString(DeepSeekConfig.serializer(), raw) }.getOrNull()
        } ?: DeepSeekConfig()
    }

    suspend fun configOnce(): DeepSeekConfig = config.first()

    suspend fun set(config: DeepSeekConfig) {
        context.deepSeekDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(DeepSeekConfig.serializer(), config)
        }
    }
}
