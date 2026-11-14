package fan.x0.focus.data.roleplay

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private val Context.roleplaySettingsDataStore by preferencesDataStore(name = "roleplay_settings")

@Single
class RoleplaySettings(@Provided private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val overridesSerializer = MapSerializer(String.serializer(), RoleplayOverride.serializer())

    private object Keys {
        val global = stringPreferencesKey("global")
        val overrides = stringPreferencesKey("overrides")
        val userPersona = stringPreferencesKey("user_persona")
    }

    val global: Flow<RoleplayConfig> = context.roleplaySettingsDataStore.data.map { prefs ->
        prefs[Keys.global]
            ?.let { runCatching { json.decodeFromString(RoleplayConfig.serializer(), it) }.getOrNull() }
            ?: RoleplayConfig()
    }

    val overrides: Flow<Map<String, RoleplayOverride>> = context.roleplaySettingsDataStore.data.map { prefs ->
        prefs[Keys.overrides]
            ?.let { runCatching { json.decodeFromString(overridesSerializer, it) }.getOrNull() }
            .orEmpty()
    }

    val userPersona: Flow<RoleplayUserPersona> = context.roleplaySettingsDataStore.data.map { prefs ->
        prefs[Keys.userPersona]
            ?.let { runCatching { json.decodeFromString(RoleplayUserPersona.serializer(), it) }.getOrNull() }
            ?: RoleplayUserPersona()
    }

    fun effective(assistantId: String): Flow<RoleplayConfig> = context.roleplaySettingsDataStore.data.map { prefs ->
        val base = readGlobal(prefs)
        val override = readOverrides(prefs)[assistantId]
        override?.config ?: base
    }

    suspend fun globalOnce(): RoleplayConfig = global.first()

    suspend fun effectiveOnce(assistantId: String): RoleplayConfig = effective(assistantId).first()

    suspend fun userPersonaOnce(): RoleplayUserPersona = userPersona.first()

    suspend fun setGlobal(config: RoleplayConfig) {
        context.roleplaySettingsDataStore.edit { prefs ->
            prefs[Keys.global] = json.encodeToString(RoleplayConfig.serializer(), config)
        }
    }

    suspend fun setOverride(assistantId: String, override: RoleplayOverride) {
        if (assistantId.isBlank()) return
        context.roleplaySettingsDataStore.edit { prefs ->
            val next = readOverrides(prefs) + (assistantId to override)
            prefs[Keys.overrides] = json.encodeToString(overridesSerializer, next)
        }
    }

    suspend fun clearOverride(assistantId: String) {
        if (assistantId.isBlank()) return
        context.roleplaySettingsDataStore.edit { prefs ->
            val next = readOverrides(prefs) - assistantId
            prefs[Keys.overrides] = json.encodeToString(overridesSerializer, next)
        }
    }

    suspend fun setUserPersona(persona: RoleplayUserPersona) {
        context.roleplaySettingsDataStore.edit { prefs ->
            prefs[Keys.userPersona] = json.encodeToString(RoleplayUserPersona.serializer(), persona)
        }
    }

    private fun readGlobal(prefs: androidx.datastore.preferences.core.Preferences): RoleplayConfig =
        prefs[Keys.global]
            ?.let { runCatching { json.decodeFromString(RoleplayConfig.serializer(), it) }.getOrNull() }
            ?: RoleplayConfig()

    private fun readOverrides(prefs: androidx.datastore.preferences.core.Preferences): Map<String, RoleplayOverride> =
        prefs[Keys.overrides]
            ?.let { runCatching { json.decodeFromString(overridesSerializer, it) }.getOrNull() }
            .orEmpty()
}
