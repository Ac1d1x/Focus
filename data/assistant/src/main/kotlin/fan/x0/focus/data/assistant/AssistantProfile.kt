package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.util.UUID

@Serializable
data class AssistantProfile(
    val id: String,
    val name: String,
    val emoji: String = "",
    val role: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_NAME = "Focus"
    }
}

private val Context.assistantProfileDataStore by preferencesDataStore(name = "assistant_profiles")

@Single
class AssistantProfileStore(@Provided private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(AssistantProfile.serializer())

    private object Keys {
        val profiles = stringPreferencesKey("profiles_json")
        val active = stringPreferencesKey("active_id")
    }

    val profiles: Flow<List<AssistantProfile>> = context.assistantProfileDataStore.data.map(::read).flowOn(Dispatchers.IO)

    val activeId: Flow<String> = context.assistantProfileDataStore.data.map { prefs ->
        val list = read(prefs)
        prefs[Keys.active]
            ?.takeIf { id -> list.any { it.id == id } }
            ?: list.firstOrNull()?.id
            ?: AssistantProfile.DEFAULT_ID
    }.flowOn(Dispatchers.IO)

    suspend fun profilesOnce(): List<AssistantProfile> {
        ensureSeed()
        return profiles.first()
    }

    suspend fun activeOnce(): String {
        ensureSeed()
        return activeId.first()
    }

    suspend fun activeProfileOnce(): AssistantProfile {
        ensureSeed()
        val active = activeId.first()
        return profiles.first().firstOrNull { it.id == active } ?: defaultProfile
    }

    suspend fun ensureSeed() {
        context.assistantProfileDataStore.edit { prefs ->
            if (prefs[Keys.profiles].isNullOrBlank()) {
                prefs[Keys.profiles] = json.encodeToString(serializer, listOf(defaultProfile))
            }
            val list = read(prefs)
            val active = prefs[Keys.active]
            if (active.isNullOrBlank() || list.none { it.id == active }) {
                prefs[Keys.active] = list.firstOrNull()?.id ?: AssistantProfile.DEFAULT_ID
            }
        }
    }

    suspend fun create(name: String, role: String, emoji: String): AssistantProfile {
        ensureSeed()
        val now = System.currentTimeMillis()
        val profile = AssistantProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { AssistantProfile.DEFAULT_NAME },
            emoji = emoji.trim().take(8),
            role = role.trim().take(MAX_ROLE_CHARS),
            createdAt = now,
            updatedAt = now,
        )
        context.assistantProfileDataStore.edit { prefs ->
            val current = read(prefs)
            prefs[Keys.profiles] = json.encodeToString(serializer, current + profile)
            prefs[Keys.active] = profile.id
        }
        return profile
    }

    suspend fun update(profile: AssistantProfile) {
        val next = profile.copy(
            name = profile.name.trim().ifBlank { AssistantProfile.DEFAULT_NAME },
            emoji = profile.emoji.trim().take(8),
            role = profile.role.trim().take(MAX_ROLE_CHARS),
            updatedAt = System.currentTimeMillis(),
        )
        context.assistantProfileDataStore.edit { prefs ->
            val current = read(prefs)
            prefs[Keys.profiles] = json.encodeToString(serializer, current.map { if (it.id == next.id) next else it })
        }
    }

    suspend fun rename(id: String, name: String) {
        val value = name.trim().take(MAX_NAME_CHARS)
        if (value.isBlank()) return
        context.assistantProfileDataStore.edit { prefs ->
            val current = read(prefs)
            prefs[Keys.profiles] = json.encodeToString(
                serializer,
                current.map {
                    if (it.id == id) it.copy(name = value, updatedAt = System.currentTimeMillis()) else it
                },
            )
        }
    }

    suspend fun delete(id: String): Boolean {
        ensureSeed()
        var removed = false
        context.assistantProfileDataStore.edit { prefs ->
            val current = read(prefs)
            if (current.size <= 1 || current.none { it.id == id }) return@edit
            val next = current.filterNot { it.id == id }
            prefs[Keys.profiles] = json.encodeToString(serializer, next)
            if (prefs[Keys.active] == id) prefs[Keys.active] = next.first().id
            removed = true
        }
        return removed
    }

    suspend fun setActive(id: String) {
        if (id.isBlank()) return
        ensureSeed()
        context.assistantProfileDataStore.edit { prefs ->
            if (read(prefs).any { it.id == id }) prefs[Keys.active] = id
        }
    }

    private fun read(prefs: Preferences): List<AssistantProfile> {
        val stored = prefs[Keys.profiles]
            ?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }
            ?.filter { it.id.isNotBlank() }
            .orEmpty()
        return stored.ifEmpty { listOf(defaultProfile) }
    }

    private val defaultProfile: AssistantProfile by lazy {
        val persona = runCatching {
            File(File(File(context.filesDir, "assistant"), "workspace"), "PERSONA.md")
        }.getOrNull()
        val text = persona?.takeIf { it.exists() }?.readText().orEmpty()
        AssistantProfile(
            id = AssistantProfile.DEFAULT_ID,
            name = fieldOf(text, "Name") ?: AssistantProfile.DEFAULT_NAME,
            role = fieldOf(text, "Role").orEmpty(),
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun fieldOf(content: String, label: String): String? =
        Regex("(?im)^\\s*[-*]?\\s*${Regex.escape(label)}\\s*:\\s*(.+)$")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val MAX_NAME_CHARS = 40
        const val MAX_ROLE_CHARS = 120
    }
}
