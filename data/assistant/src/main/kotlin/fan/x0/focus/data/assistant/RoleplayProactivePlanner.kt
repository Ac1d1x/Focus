package fan.x0.focus.data.assistant

import fan.x0.focus.data.roleplay.RoleplayConfig
import fan.x0.focus.data.roleplay.RoleplayStage
import fan.x0.focus.data.roleplay.RoleplayState
import fan.x0.focus.data.roleplay.RoleplayStore
import java.time.LocalTime
import kotlin.random.Random
import org.koin.core.annotation.Single

@Single
class RoleplayProactivePlanner(
    private val store: RoleplayStore,
    private val chats: ChatStore,
) {
    suspend fun onUserMessage(conversationId: String) {
        store.cancelOptionalTasks(conversationId)
    }

    suspend fun afterReply(
        conversationId: String,
        assistantId: String,
        proactive: Boolean,
        config: RoleplayConfig,
    ) {
        if (!config.proactive || assistantId.isBlank()) return
        val now = System.currentTimeMillis()
        val state = store.tick(conversationId, now)
        if (state.consecutiveProactive >= config.maxConsecutiveProactive) return
        val random = Random(now.toInt())
        if (proactive) {
            if (random.nextFloat() < 0.25f) {
                schedule(
                    conversationId = conversationId,
                    assistantId = assistantId,
                    delayMs = random.nextLong(5, 45) * 60_000L,
                    prompt = "You spoke first a moment ago. If the user has not answered, do not send another message. Call stay_silent when nothing new is worth saying.",
                    type = "followup",
                    tag = "followup:$conversationId",
                    condition = "user_silent",
                )
            }
            return
        }
        val will = proactiveWill(state, config, now)
        if (random.nextFloat() < config.followUpChance * will) {
            schedule(
                conversationId = conversationId,
                assistantId = assistantId,
                delayMs = random.nextLong(2, 30) * 60_000L,
                prompt = "Pick up the last topic naturally. Do not recap the whole conversation. One or two short bubbles.",
                type = "followup",
                tag = "followup:$conversationId",
                condition = "user_silent",
            )
        }
        maintenance(conversationId, assistantId, config)
    }

    suspend fun maintenance(
        conversationId: String,
        assistantId: String,
        config: RoleplayConfig,
    ) {
        if (!config.proactive || assistantId.isBlank()) return
        val now = System.currentTimeMillis()
        val state = store.tick(conversationId, now)
        val random = Random(now.toInt())
        val will = proactiveWill(state, config, now)
        val silence = if (state.lastUserAt == 0L) Long.MAX_VALUE else now - state.lastUserAt
        val hasHistory = state.lastUserAt > 0 || state.aiMsgs > 1
        val day = java.time.LocalDate.now().toString()
        val mainId = chats.ensureMainConversation(assistantId).id
        val isMain = conversationId == mainId
        if (isMain && hasHistory && silence > config.checkinSilenceMinutes * 60_000L && random.nextFloat() < will) {
            schedule(
                conversationId = conversationId,
                assistantId = assistantId,
                delayMs = random.nextLong(1, 10) * 60_000L,
                prompt = "The user has been quiet for a while. Ask where they went or how they are doing, in your own tone. Do not nag.",
                type = "checkin",
                tag = "checkin:$conversationId:$day",
                condition = "user_silent",
                once = true,
            )
        }
        if (isMain && hasHistory && silence > config.icebreakDays * 86_400_000L && random.nextFloat() < 0.5f) {
            schedule(
                conversationId = conversationId,
                assistantId = assistantId,
                delayMs = random.nextLong(2, 20) * 60_000L,
                prompt = "It has been a long time since you talked. Open the conversation gently, without pretending no time passed.",
                type = "icebreak",
                tag = "icebreak:$conversationId:$day",
                condition = "user_silent",
                once = true,
            )
        }
        store.dueMemories(assistantId, now).forEach { memory ->
            val target = chats.ensureMainConversation(assistantId).id
            schedule(
                conversationId = target,
                assistantId = assistantId,
                delayMs = 60_000L,
                prompt = "A promise or todo is due: ${memory.content}. Remind the user naturally in one short message, then call complete_todo if it is settled.",
                type = "reminder",
                tag = "reminder:${memory.id}",
                urgent = true,
                once = true,
            )
            store.markReminded(memory.id)
        }
        val hour = LocalTime.now().hour
        val morningHour = (config.morningMinute / 60).coerceIn(0, 23)
        val eveningHour = (config.eveningMinute / 60).coerceIn(0, 23)
        if (isMain && hasHistory && silence > 30 * 60_000L && config.morningGreeting && hour == morningHour) {
            scheduleGreeting(conversationId, assistantId, "morning", "Say good morning in your own way. Keep it light.")
        }
        if (isMain && hasHistory && silence > 30 * 60_000L && config.eveningGreeting && hour == eveningHour) {
            scheduleGreeting(conversationId, assistantId, "evening", "Say good night in your own way. Keep it short.")
        }
    }

    fun quietUntil(config: RoleplayConfig, now: Long): Long? {
        if (!config.quietEnabled) return null
        val time = LocalTime.now()
        val minute = time.hour * 60 + time.minute
        val start = config.quietStartMinute
        val end = config.quietEndMinute
        if (start == end) return null
        val inside = if (start < end) minute in start until end else minute >= start || minute < end
        if (!inside) return null
        val minutes = if (minute < end) end - minute else 24 * 60 - minute + end
        return now + minutes * 60_000L
    }

    private suspend fun scheduleGreeting(
        conversationId: String,
        assistantId: String,
        slot: String,
        prompt: String,
    ) {
        val day = java.time.LocalDate.now().toString()
        val tag = "greeting:$assistantId:$day:$slot"
        val target = chats.ensureMainConversation(assistantId).id
        schedule(
            conversationId = target,
            assistantId = assistantId,
            delayMs = 60_000L,
            prompt = prompt,
            type = "greeting",
            tag = tag,
            once = true,
        )
    }

    private suspend fun schedule(
        conversationId: String,
        assistantId: String,
        delayMs: Long,
        prompt: String,
        type: String,
        tag: String,
        condition: String = "always",
        once: Boolean = false,
        urgent: Boolean = false,
    ) {
        if (once) {
            if (store.hasTaskTag(tag)) return
        } else if (store.hasOpenTag(tag)) {
            return
        }
        store.scheduleTask(
            conversationId = conversationId,
            assistantId = assistantId,
            fireAt = System.currentTimeMillis() + delayMs,
            prompt = prompt,
            type = type,
            condition = condition,
            urgent = urgent,
            tag = tag,
        )
    }

    private fun proactiveWill(state: RoleplayState, config: RoleplayConfig, now: Long): Float {
        var value = when (state.stage) {
            RoleplayStage.Stranger -> 0.25f
            RoleplayStage.Acquaintance -> 0.6f
            RoleplayStage.Close -> 1f
        }
        value *= 0.4f + state.energy.toFloat() / 100f * 0.6f
        value *= 0.6f + state.affection.toFloat() / 100f * 0.4f
        val effective = state.effectiveStatus(now)
        if (effective == fan.x0.focus.data.roleplay.RoleplayStatus.Away) value *= 0.15f
        if (effective == fan.x0.focus.data.roleplay.RoleplayStatus.Dnd) value = 0f
        return value.coerceIn(0f, 1f)
    }
}
