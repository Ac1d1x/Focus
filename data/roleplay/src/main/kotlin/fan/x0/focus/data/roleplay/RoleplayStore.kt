package fan.x0.focus.data.roleplay

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Single
class RoleplayStore(private val database: RoleplayDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lifeSerializer = ListSerializer(RoleplayLifeEntry.serializer())
    private val longsSerializer = ListSerializer(Long.serializer())
    private val stringsSerializer = ListSerializer(String.serializer())

    suspend fun state(conversationId: String): RoleplayState = withContext(Dispatchers.IO) {
        if (conversationId.isBlank()) return@withContext RoleplayState(conversationId = "")
        queryState(conversationId) ?: RoleplayState(conversationId = conversationId).also { saveState(it) }
    }

    suspend fun saveState(state: RoleplayState) = withContext(Dispatchers.IO) {
        if (state.conversationId.isBlank()) return@withContext
        val values = ContentValues().apply {
            put("conversation_id", state.conversationId)
            put("status", state.status.wire)
            put("mood", state.mood)
            put("affection", state.affection)
            put("energy", state.energy)
            put("stage", state.stage.name)
            put("user_msgs", state.userMsgs)
            put("ai_msgs", state.aiMsgs)
            put("interaction_min", state.interactionMin)
            put("first_at", state.firstAt)
            put("last_user_at", state.lastUserAt)
            put("last_ai_at", state.lastAiAt)
            put("last_tick_at", state.lastTickAt)
            put("consecutive_proactive", state.consecutiveProactive)
            put("turn", state.turn)
            put("recall_stamps", json.encodeToString(longsSerializer, state.recallStamps))
            put("life", json.encodeToString(lifeSerializer, state.life))
            put("feeling_log", json.encodeToString(stringsSerializer, state.feelingLog))
        }
        database.writableDatabase.insertWithOnConflict(
            "roleplay_states",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    suspend fun noteUser(conversationId: String, text: String, now: Long = System.currentTimeMillis()): RoleplayState =
        withContext(Dispatchers.IO) {
            val current = state(conversationId)
            val firstAt = if (current.firstAt == 0L) now else current.firstAt
            val interaction = if (current.lastUserAt > 0 && now - current.lastUserAt < 10 * 60_000) {
                current.interactionMin + max(1, ((now - current.lastUserAt) / 60_000).toInt())
            } else {
                current.interactionMin
            }
            var mood = current.mood
            var affection = current.affection
            val log = current.feelingLog.toMutableList()
            when {
                INSULT.containsMatchIn(text) -> {
                    mood = (mood - 12).coerceIn(0.0, 100.0)
                    affection = (affection - 6).coerceIn(0.0, 100.0)
                    log += stamp(now, "mood -12 affection -6 insulted")
                }
                PRAISE.containsMatchIn(text) -> {
                    mood = (mood + 6).coerceIn(0.0, 100.0)
                    affection = (affection + 3).coerceIn(0.0, 100.0)
                    log += stamp(now, "mood +6 affection +3 praised")
                }
                else -> affection = (affection + 0.15).coerceIn(0.0, 100.0)
            }
            val next = current.copy(
                status = if (current.status == RoleplayStatus.ReadNoReply) RoleplayStatus.Online else current.status,
                mood = mood,
                affection = affection,
                energy = (current.energy - 0.4).coerceIn(0.0, 100.0),
                userMsgs = current.userMsgs + 1,
                interactionMin = interaction,
                firstAt = firstAt,
                lastUserAt = now,
                consecutiveProactive = 0,
                feelingLog = trimLog(log),
            ).evaluateStage(now, log)
            saveState(next)
            next
        }

    suspend fun noteAi(
        conversationId: String,
        proactive: Boolean,
        now: Long = System.currentTimeMillis(),
    ): RoleplayState = withContext(Dispatchers.IO) {
        val current = state(conversationId)
        val next = current.copy(
            aiMsgs = current.aiMsgs + 1,
            lastAiAt = now,
            consecutiveProactive = if (proactive) current.consecutiveProactive + 1 else 0,
            energy = (current.energy - 0.15).coerceIn(0.0, 100.0),
        )
        saveState(next)
        next
    }

    suspend fun tick(conversationId: String, now: Long = System.currentTimeMillis()): RoleplayState =
        withContext(Dispatchers.IO) {
            val current = state(conversationId)
            val from = if (current.lastTickAt == 0L) now else current.lastTickAt
            val hours = (now - from).toDouble() / 3_600_000.0
            var energy = current.energy
            var mood = current.mood
            var affection = current.affection
            if (hours > 0) {
                val recovery = if (current.activeLife(now) != null) 0.0 else 6.0
                energy = (energy + hours * recovery).coerceIn(0.0, 100.0)
                mood += (55 - mood) * min(1.0, hours * 0.08)
                if (current.lastUserAt > 0 && now - current.lastUserAt > 3 * 86_400_000L) {
                    affection = (affection - hours * 0.05).coerceIn(0.0, 100.0)
                }
            }
            val next = current.copy(
                energy = energy,
                mood = mood.coerceIn(0.0, 100.0),
                affection = affection,
                lastTickAt = now,
            ).evaluateStage(now, current.feelingLog)
            saveState(next)
            next
        }

    suspend fun adjust(
        conversationId: String,
        mood: Double,
        affection: Double,
        energy: Double,
        reason: String,
        now: Long = System.currentTimeMillis(),
    ): RoleplayState = withContext(Dispatchers.IO) {
        val current = state(conversationId)
        val log = current.feelingLog.toMutableList()
        if (mood != 0.0 || affection != 0.0 || energy != 0.0) {
            log += stamp(now, "mood $mood affection $affection energy $energy $reason".trim())
        }
        val next = current.copy(
            mood = (current.mood + mood).coerceIn(0.0, 100.0),
            affection = (current.affection + affection).coerceIn(0.0, 100.0),
            energy = (current.energy + energy).coerceIn(0.0, 100.0),
            feelingLog = trimLog(log),
        ).evaluateStage(now, log)
        saveState(next)
        next
    }

    suspend fun setStatus(conversationId: String, status: RoleplayStatus): RoleplayState =
        withContext(Dispatchers.IO) {
            val current = state(conversationId)
            val next = current.copy(status = status)
            saveState(next)
            next
        }

    suspend fun setLife(conversationId: String, life: List<RoleplayLifeEntry>): RoleplayState =
        withContext(Dispatchers.IO) {
            val current = state(conversationId)
            val next = current.copy(life = life)
            saveState(next)
            next
        }

    suspend fun addRecall(conversationId: String, now: Long = System.currentTimeMillis()): RoleplayState =
        withContext(Dispatchers.IO) {
            val current = state(conversationId)
            val stamps = (current.recallStamps + now).filter { now - it <= 3_600_000L }
            val next = current.copy(recallStamps = stamps)
            saveState(next)
            next
        }

    fun recallsInLastHour(state: RoleplayState, now: Long = System.currentTimeMillis()): Int =
        state.recallStamps.count { now - it <= 3_600_000L }

    suspend fun memories(assistantId: String, includeForgotten: Boolean = false): List<RoleplayMemory> =
        withContext(Dispatchers.IO) {
            val where = if (includeForgotten) {
                "assistant_id = ?"
            } else {
                "assistant_id = ? AND forgotten = 0"
            }
            database.readableDatabase.rawQuery(
                "SELECT $MEMORY_COLUMNS FROM roleplay_memories WHERE $where ORDER BY created_at DESC",
                arrayOf(assistantId),
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.toMemory()) }
            }
        }

    suspend fun writeMemory(
        assistantId: String,
        type: RoleplayMemoryType,
        content: String,
        dueAt: Long = 0,
        weight: Double = 1.0,
        now: Long = System.currentTimeMillis(),
    ): RoleplayMemory = withContext(Dispatchers.IO) {
        val text = content.trim()
        if (text.isEmpty()) return@withContext RoleplayMemory(
            id = "",
            assistantId = assistantId,
            type = type,
            content = "",
        )
        val existing = memories(assistantId).firstOrNull {
            it.type == type && it.content == text && !it.done
        }
        if (existing != null) {
            val next = existing.copy(
                weight = min(5.0, existing.weight + 0.5),
                lastAccess = now,
                forgotten = false,
                dueAt = if (dueAt > 0) dueAt else existing.dueAt,
            )
            saveMemory(next)
            return@withContext next
        }
        val memory = RoleplayMemory(
            id = "mem_${UUID.randomUUID()}",
            assistantId = assistantId,
            type = type,
            content = text,
            weight = weight.coerceIn(0.1, 5.0),
            createdAt = now,
            lastAccess = now,
            dueAt = dueAt,
            decayedAt = now,
        )
        saveMemory(memory)
        memory
    }

    suspend fun readMemories(
        assistantId: String,
        query: String,
        type: RoleplayMemoryType? = null,
        limit: Int = 10,
        includeForgotten: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): List<RoleplayMemory> = withContext(Dispatchers.IO) {
        val tokens = memoryTokens(query)
        val list = memories(assistantId, includeForgotten)
            .filter { type == null || it.type == type }
            .filter { memory ->
                tokens.isEmpty() || memoryTokens(memory.content).intersect(tokens).isNotEmpty() ||
                    memory.content.contains(query, ignoreCase = true)
            }
            .sortedByDescending { memoryRelevanceScore(it.content, query, it.weight, it.lastAccess, now, it.sticky) }
            .take(limit)
        list.forEach { memory ->
            saveMemory(
                memory.copy(
                    lastAccess = now,
                    weight = min(5.0, memory.weight + 0.1),
                ),
            )
        }
        list
    }

    suspend fun forContext(
        assistantId: String,
        userText: String,
        limit: Int = 12,
        now: Long = System.currentTimeMillis(),
    ): List<RoleplayMemory> = withContext(Dispatchers.IO) {
        val all = memories(assistantId)
        val sticky = all.filter { it.sticky && !it.forgotten }
        val rest = all.filter { !it.sticky && !it.forgotten && !it.done }
            .sortedByDescending { memoryRelevanceScore(it.content, userText, it.weight, it.lastAccess, now, false) }
        (sticky + rest.take(max(0, limit - sticky.size))).distinctBy { it.id }
    }

    suspend fun completeMemory(id: String): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("done", 1)
            put("due_at", 0)
        }
        database.writableDatabase.update("roleplay_memories", values, "id = ?", arrayOf(id)) > 0
    }

    suspend fun deleteMemory(id: String): Boolean = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("roleplay_memories", "id = ?", arrayOf(id)) > 0
    }

    suspend fun decay(
        assistantId: String,
        halfLifeDays: Int,
        now: Long = System.currentTimeMillis(),
    ): Int = withContext(Dispatchers.IO) {
        val days = halfLifeDays.coerceAtLeast(1).toDouble()
        var forgotten = 0
        memories(assistantId).forEach { memory ->
            if (memory.sticky || memory.forgotten) return@forEach
            val from = max(memory.decayedAt, memory.lastAccess)
            val elapsed = max(0.0, (now - from).toDouble() / 86_400_000.0)
            val weight = memory.weight * 0.5.pow(elapsed / days)
            val next = memory.copy(
                weight = weight,
                forgotten = weight < 0.15,
                decayedAt = now,
            )
            if (next.forgotten && !memory.forgotten) forgotten++
            saveMemory(next)
        }
        forgotten
    }

    suspend fun dueMemories(assistantId: String, now: Long = System.currentTimeMillis()): List<RoleplayMemory> =
        withContext(Dispatchers.IO) {
            memories(assistantId).filter { it.sticky && it.dueAt in 1..now && !it.reminded }
        }

    suspend fun clearAssistant(assistantId: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("roleplay_memories", "assistant_id = ?", arrayOf(assistantId))
    }

    suspend fun clearConversation(conversationId: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("roleplay_states", "conversation_id = ?", arrayOf(conversationId))
        database.writableDatabase.delete("roleplay_tasks", "conversation_id = ?", arrayOf(conversationId))
    }

    suspend fun scheduleTask(
        conversationId: String,
        assistantId: String,
        fireAt: Long,
        prompt: String,
        type: String = "custom",
        condition: String = "always",
        urgent: Boolean = false,
        tag: String = "",
        now: Long = System.currentTimeMillis(),
    ): RoleplayTask = withContext(Dispatchers.IO) {
        val task = RoleplayTask(
            id = "task_${UUID.randomUUID()}",
            conversationId = conversationId,
            assistantId = assistantId,
            fireAt = fireAt.coerceAtLeast(now),
            prompt = prompt.trim(),
            type = type,
            condition = condition.trim().ifBlank { "always" },
            urgent = urgent,
            tag = tag.trim(),
            createdAt = now,
        )
        saveTask(task)
        task
    }

    suspend fun task(id: String): RoleplayTask? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $TASK_COLUMNS FROM roleplay_tasks WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toTask() else null }
    }

    suspend fun openTasks(conversationId: String? = null): List<RoleplayTask> = withContext(Dispatchers.IO) {
        val where = if (conversationId == null) {
            "status IN ('pending', 'suspended')"
        } else {
            "status IN ('pending', 'suspended') AND conversation_id = ?"
        }
        database.readableDatabase.rawQuery(
            "SELECT $TASK_COLUMNS FROM roleplay_tasks WHERE $where ORDER BY fire_at ASC",
            conversationId?.let { arrayOf(it) },
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toTask()) }
        }
    }

    suspend fun dueTasks(now: Long = System.currentTimeMillis()): List<RoleplayTask> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $TASK_COLUMNS FROM roleplay_tasks WHERE status = 'pending' AND fire_at <= ? ORDER BY fire_at ASC",
            arrayOf(now.toString()),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toTask()) }
        }
    }

    suspend fun hasOpenTag(tag: String): Boolean = withContext(Dispatchers.IO) {
        if (tag.isBlank()) return@withContext false
        database.readableDatabase.rawQuery(
            "SELECT 1 FROM roleplay_tasks WHERE tag = ? AND status IN ('pending', 'suspended') LIMIT 1",
            arrayOf(tag),
        ).use { it.moveToFirst() }
    }

    suspend fun setTaskStatus(
        id: String,
        status: String,
        note: String = "",
        fireAt: Long? = null,
        failures: Int? = null,
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", status)
            put("note", note)
            if (fireAt != null) put("fire_at", fireAt)
            if (failures != null) put("failures", failures)
            if (status == "done" || status == "skipped" || status == "failed") {
                put("fired_at", System.currentTimeMillis())
            }
        }
        database.writableDatabase.update("roleplay_tasks", values, "id = ?", arrayOf(id))
    }

    suspend fun updateTask(
        id: String,
        fireAt: Long? = null,
        prompt: String? = null,
        condition: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", "pending")
            put("note", "modified")
            if (fireAt != null) put("fire_at", fireAt)
            if (!prompt.isNullOrBlank()) put("prompt", prompt.trim())
            if (!condition.isNullOrBlank()) put("condition", condition.trim())
        }
        database.writableDatabase.update("roleplay_tasks", values, "id = ?", arrayOf(id)) > 0
    }

    suspend fun cancelTask(id: String): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", "cancelled")
            put("note", "cancelled")
        }
        database.writableDatabase.update("roleplay_tasks", values, "id = ?", arrayOf(id)) > 0
    }

    suspend fun cancelOptionalTasks(conversationId: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", "cancelled")
            put("note", "user replied")
        }
        database.writableDatabase.update(
            "roleplay_tasks",
            values,
            "conversation_id = ? AND status = 'pending' AND type IN ('checkin', 'followup', 'icebreak')",
            arrayOf(conversationId),
        )
    }

    suspend fun resetFiring() = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", "pending")
            put("note", "resumed")
        }
        database.writableDatabase.update("roleplay_tasks", values, "status = 'firing'", null)
    }

    suspend fun trimTasks(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete(
            "roleplay_tasks",
            "status IN ('done', 'skipped', 'cancelled', 'failed') AND fired_at > 0 AND fired_at < ?",
            arrayOf((now - 7 * 86_400_000L).toString()),
        )
    }

    private fun saveTask(task: RoleplayTask) {
        val values = ContentValues().apply {
            put("id", task.id)
            put("conversation_id", task.conversationId)
            put("assistant_id", task.assistantId)
            put("fire_at", task.fireAt)
            put("prompt", task.prompt)
            put("type", task.type)
            put("condition", task.condition)
            put("status", task.status)
            put("urgent", if (task.urgent) 1 else 0)
            put("note", task.note)
            put("tag", task.tag)
            put("created_at", task.createdAt)
            put("fired_at", task.firedAt)
            put("failures", task.failures)
        }
        database.writableDatabase.insertWithOnConflict(
            "roleplay_tasks",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun Cursor.toTask(): RoleplayTask = RoleplayTask(
        id = getString(0),
        conversationId = getString(1),
        assistantId = getString(2),
        fireAt = getLong(3),
        prompt = getString(4).orEmpty(),
        type = getString(5).orEmpty(),
        condition = getString(6).orEmpty(),
        status = getString(7).orEmpty(),
        urgent = getInt(8) != 0,
        note = getString(9).orEmpty(),
        tag = getString(10).orEmpty(),
        createdAt = getLong(11),
        firedAt = getLong(12),
        failures = getInt(13),
    )

    suspend fun stickers(): List<RoleplaySticker> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $STICKER_COLUMNS FROM roleplay_stickers ORDER BY favorite DESC, uses DESC, created_at DESC",
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toSticker()) }
        }
    }

    suspend fun addSticker(
        kind: String,
        value: String,
        name: String = "",
        tags: List<String> = emptyList(),
        emotion: String = "",
        category: String = "General",
        source: String = "user",
    ): RoleplaySticker = withContext(Dispatchers.IO) {
        val existing = stickers().firstOrNull { it.value == value }
        if (existing != null) {
            val next = existing.copy(
                name = name.ifBlank { existing.name },
                emotion = emotion.ifBlank { existing.emotion },
                tags = (existing.tags + tags).distinct(),
            )
            saveSticker(next)
            return@withContext next
        }
        val sticker = RoleplaySticker(
            id = "stk_${UUID.randomUUID()}",
            kind = kind,
            value = value,
            name = name,
            tags = tags.distinct(),
            emotion = emotion,
            category = category,
            source = source,
            createdAt = System.currentTimeMillis(),
        )
        saveSticker(sticker)
        sticker
    }

    suspend fun deleteSticker(id: String): Boolean = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("roleplay_stickers", "id = ?", arrayOf(id)) > 0
    }

    suspend fun pickSticker(id: String, emotion: String): RoleplaySticker? = withContext(Dispatchers.IO) {
        val all = stickers()
        all.firstOrNull { it.id == id || it.id.takeLast(6) == id.takeLast(6) }
            ?: all.filter {
                emotion.isBlank() ||
                    it.emotion.contains(emotion, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(emotion, ignoreCase = true) } ||
                    it.name.contains(emotion, ignoreCase = true)
            }.randomOrNull()
    }

    suspend fun markStickerUsed(id: String) = withContext(Dispatchers.IO) {
        val sticker = stickers().firstOrNull { it.id == id } ?: return@withContext
        saveSticker(
            sticker.copy(
                uses = sticker.uses + 1,
                lastUsed = System.currentTimeMillis(),
            ),
        )
    }

    private fun saveSticker(sticker: RoleplaySticker) {
        val values = ContentValues().apply {
            put("id", sticker.id)
            put("kind", sticker.kind)
            put("value", sticker.value)
            put("name", sticker.name)
            put("tags_json", json.encodeToString(stringsSerializer, sticker.tags))
            put("emotion", sticker.emotion)
            put("category", sticker.category)
            put("favorite", if (sticker.favorite) 1 else 0)
            put("uses", sticker.uses)
            put("last_used", sticker.lastUsed)
            put("source", sticker.source)
            put("created_at", sticker.createdAt)
        }
        database.writableDatabase.insertWithOnConflict(
            "roleplay_stickers",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun Cursor.toSticker(): RoleplaySticker = RoleplaySticker(
        id = getString(0),
        kind = getString(1).orEmpty(),
        value = getString(2).orEmpty(),
        name = getString(3).orEmpty(),
        tags = runCatching { json.decodeFromString(stringsSerializer, getString(4).orEmpty()) }.getOrDefault(emptyList()),
        emotion = getString(5).orEmpty(),
        category = getString(6).orEmpty(),
        favorite = getInt(7) != 0,
        uses = getInt(8),
        lastUsed = getLong(9),
        source = getString(10).orEmpty(),
        createdAt = getLong(11),
    )

    private fun queryState(conversationId: String): RoleplayState? =
        database.readableDatabase.rawQuery(
            "SELECT $STATE_COLUMNS FROM roleplay_states WHERE conversation_id = ? LIMIT 1",
            arrayOf(conversationId),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toState() else null
        }

    private fun Cursor.toState(): RoleplayState = RoleplayState(
        conversationId = getString(0),
        status = RoleplayStatus.fromWire(getString(1)),
        mood = getDouble(2),
        affection = getDouble(3),
        energy = getDouble(4),
        stage = RoleplayStage.fromWire(getString(5)),
        userMsgs = getInt(6),
        aiMsgs = getInt(7),
        interactionMin = getInt(8),
        firstAt = getLong(9),
        lastUserAt = getLong(10),
        lastAiAt = getLong(11),
        lastTickAt = getLong(12),
        consecutiveProactive = getInt(13),
        turn = getInt(14),
        recallStamps = runCatching {
            json.decodeFromString(longsSerializer, getString(15).orEmpty())
        }.getOrDefault(emptyList()),
        life = runCatching {
            json.decodeFromString(lifeSerializer, getString(16).orEmpty())
        }.getOrDefault(emptyList()),
        feelingLog = runCatching {
            json.decodeFromString(stringsSerializer, getString(17).orEmpty())
        }.getOrDefault(emptyList()),
    )

    private fun saveMemory(memory: RoleplayMemory) {
        val values = ContentValues().apply {
            put("id", memory.id)
            put("assistant_id", memory.assistantId)
            put("type", memory.type.name)
            put("content", memory.content)
            put("weight", memory.weight)
            put("created_at", memory.createdAt)
            put("last_access", memory.lastAccess)
            put("forgotten", if (memory.forgotten) 1 else 0)
            put("due_at", memory.dueAt)
            put("done", if (memory.done) 1 else 0)
            put("reminded", if (memory.reminded) 1 else 0)
            put("decayed_at", memory.decayedAt)
        }
        database.writableDatabase.insertWithOnConflict(
            "roleplay_memories",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun Cursor.toMemory(): RoleplayMemory = RoleplayMemory(
        id = getString(0),
        assistantId = getString(1),
        type = RoleplayMemoryType.fromWire(getString(2)),
        content = getString(3),
        weight = getDouble(4),
        createdAt = getLong(5),
        lastAccess = getLong(6),
        forgotten = getInt(7) != 0,
        dueAt = getLong(8),
        done = getInt(9) != 0,
        reminded = getInt(10) != 0,
        decayedAt = getLong(11),
    )

    private fun RoleplayState.evaluateStage(now: Long, log: List<String>): RoleplayState {
        val silentDays = if (lastUserAt == 0L) 0.0 else (now - lastUserAt).toDouble() / 86_400_000.0
        var next = stage
        when (stage) {
            RoleplayStage.Stranger -> {
                if (userMsgs >= 20 && affection >= 35) next = RoleplayStage.Acquaintance
            }
            RoleplayStage.Acquaintance -> {
                if (userMsgs >= 100 && interactionMin >= 240 && affection >= 70) {
                    next = RoleplayStage.Close
                } else if (affection < 20 || silentDays >= 30) {
                    next = RoleplayStage.Stranger
                }
            }
            RoleplayStage.Close -> {
                if (affection < 50 || silentDays >= 14) next = RoleplayStage.Acquaintance
            }
        }
        return if (next == stage) {
            copy(feelingLog = trimLog(log))
        } else {
            copy(
                stage = next,
                feelingLog = trimLog(log + stamp(now, "stage ${stage.name} -> ${next.name}")),
            )
        }
    }

    private fun stamp(now: Long, text: String): String {
        val minutes = (now / 60_000) % (24 * 60)
        val hour = (minutes / 60).toString().padStart(2, '0')
        val minute = (minutes % 60).toString().padStart(2, '0')
        return "$hour:$minute $text"
    }

    private fun trimLog(log: List<String>): List<String> = log.takeLast(40)

    private companion object {
        private val PRAISE = Regex(
            "(谢谢|感谢|太棒|真棒|厉害|喜欢你|爱你|可爱|聪明|好样的|辛苦了|thank|awesome|great job|love you|you.re (the best|great|cute))",
            RegexOption.IGNORE_CASE,
        )
        private val INSULT = Regex(
            "(傻[逼比x]|撒比|沙比|蠢|滚|闭嘴|废物|垃圾|烦死|讨厌你|智障|stupid|idiot|shut up|hate you|useless)",
            RegexOption.IGNORE_CASE,
        )
        private const val STATE_COLUMNS =
            "conversation_id, status, mood, affection, energy, stage, user_msgs, ai_msgs, " +
                "interaction_min, first_at, last_user_at, last_ai_at, last_tick_at, " +
                "consecutive_proactive, turn, recall_stamps, life, feeling_log"
        private const val MEMORY_COLUMNS =
            "id, assistant_id, type, content, weight, created_at, last_access, forgotten, " +
                "due_at, done, reminded, decayed_at"
        private const val TASK_COLUMNS =
            "id, conversation_id, assistant_id, fire_at, prompt, type, condition, status, " +
                "urgent, note, tag, created_at, fired_at, failures"
        private const val STICKER_COLUMNS =
            "id, kind, value, name, tags_json, emotion, category, favorite, uses, last_used, source, created_at"
    }
}
