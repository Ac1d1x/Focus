package fan.x0.focus.data.assistant

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fan.x0.focus.data.roleplay.RoleplayStatus
import fan.x0.focus.data.roleplay.RoleplayStore
import fan.x0.focus.data.roleplay.RoleplayTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.koin.mp.KoinPlatform
import java.util.concurrent.TimeUnit

class RoleplayTaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        return try {
            KoinPlatform.getKoin().get<RoleplayScheduler>().fire(id)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            runCatching {
                KoinPlatform.getKoin().get<RoleplayStore>().setTaskStatus(id, "pending", "retry")
            }
            Result.retry()
        }
    }

    companion object {
        const val KEY_ID = "roleplay_task_id"
    }
}

@Single
class RoleplayScheduler(
    @Provided private val context: Context,
    private val store: RoleplayStore,
    private val planner: RoleplayProactivePlanner,
    private val turns: ChatTurnRunner,
    private val profiles: AssistantProfileStore,
    private val chats: ChatStore,
    private val settings: fan.x0.focus.data.roleplay.RoleplaySettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            try {
                store.resetFiring()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
            while (true) {
                try {
                    scan()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                }
                delay(SCAN_MS)
            }
        }
    }

    suspend fun fire(id: String): Boolean {
        val task = store.task(id) ?: return false
        if (task.status != "pending") return false
        val conversation = chats.conversation(task.conversationId)
        if (conversation == null) {
            store.setTaskStatus(task.id, "cancelled", "conversation is gone")
            return false
        }
        val state = store.state(task.conversationId)
        val now = System.currentTimeMillis()
        val config = settings.effectiveOnce(task.assistantId)
        if (!config.proactive && !task.urgent) {
            store.setTaskStatus(task.id, "skipped", "proactive messages are off")
            return false
        }
        if (!task.urgent) {
            val quietUntil = planner.quietUntil(config, now)
            if (quietUntil != null) {
                store.setTaskStatus(task.id, "pending", "quiet hours", fireAt = quietUntil)
                scheduleWork(task.copy(fireAt = quietUntil))
                return false
            }
            if (state.effectiveStatus(now) == RoleplayStatus.Dnd) {
                val next = now + 30 * 60_000L
                store.setTaskStatus(task.id, "pending", "do not disturb", fireAt = next)
                scheduleWork(task.copy(fireAt = next))
                return false
            }
            if (state.energy < 15) {
                val next = now + 30 * 60_000L
                store.setTaskStatus(task.id, "pending", "low energy", fireAt = next)
                scheduleWork(task.copy(fireAt = next))
                return false
            }
            if (state.consecutiveProactive >= config.maxConsecutiveProactive && task.type in OPTIONAL) {
                store.setTaskStatus(task.id, "skipped", "too many proactive messages")
                return false
            }
            if (task.condition.contains("user_silent", ignoreCase = true) && state.lastUserAt > task.createdAt) {
                store.setTaskStatus(task.id, "skipped", "the user replied")
                return false
            }
        }
        if (!store.claimTask(task.id)) return false
        val options = TurnOptions(
            providerId = conversation.providerId,
            model = conversation.model,
            reasoning = fan.x0.focus.data.assistant.ReasoningLevel.fromName(conversation.reasoning),
            autoTitle = false,
            proactive = task.prompt,
        )
        val result = turns.startAndAwait(
            conversationId = task.conversationId,
            text = "",
            options = options,
            appendUser = false,
            timeoutMs = 20 * 60_000L,
        )
        val answer = result?.answer.orEmpty().trim()
        return when {
            result == null -> {
                store.setTaskStatus(task.id, "failed", "timeout", failures = task.failures + 1)
                false
            }
            result.error != null -> {
                store.setTaskStatus(task.id, "failed", result.error.type.name, failures = task.failures + 1)
                false
            }
            answer.isBlank() -> {
                store.setTaskStatus(task.id, "skipped", "the character stayed silent")
                true
            }
            else -> {
                store.setTaskStatus(task.id, "done", "sent")
                true
            }
        }
    }

    private suspend fun scan() {
        val now = System.currentTimeMillis()
        profiles.profilesOnce()
            .filter { it.kind == AssistantKind.Roleplay }
            .forEach { profile ->
                val config = settings.effectiveOnce(profile.id)
                chats.conversationIds(profile.id).forEach { conversationId ->
                    try {
                        planner.maintenance(conversationId, profile.id, config)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                    }
                }
            }
        store.openTasks().forEach { task ->
            if (task.fireAt > now) {
                scheduleWork(task)
            } else {
                fireSafe(task.id)
            }
        }
        store.trimTasks(now)
    }

    private suspend fun fireSafe(id: String) {
        try {
            fire(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            store.setTaskStatus(id, "pending", "retry")
        }
    }

    private fun scheduleWork(task: RoleplayTask) {
        val delay = (task.fireAt - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<RoleplayTaskWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(RoleplayTaskWorker.KEY_ID to task.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "roleplay-task-${task.id}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val SCAN_MS = 30_000L
        val OPTIONAL = setOf("greeting", "checkin", "followup", "icebreak")
    }
}
