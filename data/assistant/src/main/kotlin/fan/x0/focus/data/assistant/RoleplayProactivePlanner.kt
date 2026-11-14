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
        val silence = if (state.lastUserAt == 0L) Long.MAX_VALUE else now - state.lastUserAt
        if (silence > config.checkinSilenceMinutes * 60_000L && random.nextFloat() < will) {
            schedule(
                conversationId = conversationId,
                assistantId = assistantId,
                delayMs = random.nextLong(1, 10) * 60_000L,
                prompt = "The user has been quiet for a while. Ask where they went or how they are doing, in your own tone. Do not nag.",
                type = "checkin",
                tag = "checkin:$conversationId",
                condition = "user_silent",
            )
        }
        if (silence > config.icebreakDays * 86_400_000L && random.nextFloat() < 0.5f) {
            schedule(
                conversationId = conversationId,
                assistantId = assistantId,
                delayMs = random.nextLong(2, 20) * 60_000L,
                prompt = "It has been a long time since you talked. Open the conversation gently, without pretending no time passed.",
                type = "icebreak",
                tag = "icebreak:$conversationId",
                condition = "user_silent",
            )
        }
        val hour = LocalTime.now().hour
        if (config.morningGreeting && hour in 7..9) {
            scheduleGreeting(conversationId, assistantId, "morning", "Say good morning in your own way. Keep it light.")
        }
        if (config.eveningGreeting && hour in 21..23) {
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
        val tag = "greeting:$day:$slot:$conversationId"
        if (store.hasOpenTag(tag)) return
        schedule(
            conversationId = conversationId,
            assistantId = assistantId,
            delayMs = 60_000L,
            prompt = prompt,
            type = "greeting",
            tag = tag,
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
    ) {
        if (store.hasOpenTag(tag)) return
        store.scheduleTask(
            conversationId = conversationId,
            assistantId = assistantId,
            fireAt = System.currentTimeMillis() + delayMs,
            prompt = prompt,
            type = type,
            condition = condition,
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
