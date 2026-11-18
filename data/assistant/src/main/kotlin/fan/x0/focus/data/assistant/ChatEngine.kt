package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.network.ProviderRequestConfig
import fan.x0.focus.core.network.openai.ChatCompletionRequest
import fan.x0.focus.core.network.openai.ChatMessageDto
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ChatStreamEvent
import fan.x0.focus.core.network.openai.ChatWireClient
import fan.x0.focus.core.network.openai.CompletedMessage
import fan.x0.focus.core.network.openai.CompletedToolCall
import fan.x0.focus.core.network.openai.FunctionCallDto
import fan.x0.focus.core.network.openai.MessageContent
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.ModelProtocolException
import fan.x0.focus.core.network.openai.ServerToolCall
import fan.x0.focus.core.network.openai.StreamAccumulator
import fan.x0.focus.core.network.openai.StreamOptionsDto
import fan.x0.focus.core.network.openai.ThinkingDto
import fan.x0.focus.core.network.openai.TitleDto
import fan.x0.focus.core.network.openai.ToolCallDto
import fan.x0.focus.core.network.openai.ToolDto
import fan.x0.focus.data.roleplay.RoleplayBubble
import fan.x0.focus.data.roleplay.RoleplayBubbleParser
import fan.x0.focus.data.roleplay.RoleplayCard
import fan.x0.focus.data.roleplay.RoleplayConfig
import fan.x0.focus.data.roleplay.RoleplayPromptBuilder
import fan.x0.focus.data.roleplay.RoleplayPromptInput
import fan.x0.focus.data.roleplay.RoleplaySettings
import fan.x0.focus.data.roleplay.RoleplayStore
import fan.x0.focus.data.roleplay.RoleplayWorldStore
import fan.x0.focus.data.roleplay.RoleplayStateView
import fan.x0.focus.data.roleplay.applyPunctuation
import fan.x0.focus.data.roleplay.cleanBubble
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

enum class ChatMode { Chat, Onboarding }

data class TurnOptions(
    val providerId: String = "",
    val model: String = "",
    val reasoning: ReasoningLevel = ReasoningLevel.Auto,
    val attachments: List<ChatAttachment> = emptyList(),
    val agentMode: AgentMode = AgentMode.Build,
    val replyToId: Long? = null,
    val mentionedSkills: List<String> = emptyList(),
    val silentReply: Boolean = false,
    val autoTitle: Boolean = true,
    val turnId: String = "",
    val proactive: String = "",
)

sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class ReasoningDelta(val text: String) : ChatEvent
    data class ReasoningTimed(val durationMs: Long) : ChatEvent
    data class UserMessageAppended(val turns: Int) : ChatEvent
    data class ToolStarted(
        val label: String,
        val id: String = "",
        val tool: String = "",
        val arguments: String = "",
    ) : ChatEvent
    data class ToolFinished(
        val label: String,
        val ok: Boolean,
        val summary: String,
        val tool: String? = null,
        val id: String = "",
        val output: String? = null,
        val durationMs: Long = 0,
        val diffs: List<FileDiff> = emptyList(),
        val media: List<ChatAttachment> = emptyList(),
    ) : ChatEvent
    data class WorkingTextChanged(val text: String) : ChatEvent
    data class ApprovalRequested(val request: ApprovalRequest) : ChatEvent
    data class ApprovalResolved(val request: ApprovalRequest, val approved: Boolean) : ChatEvent
    data class AskRequested(val request: AskRequest) : ChatEvent
    data class AskResolved(val request: AskRequest, val reply: AskReply) : ChatEvent
    data class TrustRequested(val request: WorkspaceTrustRequest) : ChatEvent
    data class TrustResolved(val request: WorkspaceTrustRequest, val trusted: Boolean) : ChatEvent
    data class PlanProposed(val plan: ProposedPlan, val request: ApprovalRequest) : ChatEvent
    data class PlanResolved(val plan: ProposedPlan, val approved: Boolean) : ChatEvent
    data class TodoUpdated(val todos: List<ConversationTodo>) : ChatEvent
    data class StatsUpdated(val stats: MessageStats) : ChatEvent
    data class Citations(val citations: List<ChatCitation>) : ChatEvent
    data class TitleGenerated(val title: String) : ChatEvent
    data class Refused(val message: String?) : ChatEvent
    data class CompactionStarted(val automatic: Boolean) : ChatEvent
    data class CompactionDelta(val text: String) : ChatEvent
    data class CompactionFinished(
        val automatic: Boolean,
        val ok: Boolean,
        val tokensBefore: Int = 0,
        val tokensAfter: Int = 0,
    ) : ChatEvent
    data class RetryScheduled(val attempt: Int, val max: Int, val reason: String) : ChatEvent
    data class RoleplayBubbleShown(
        val messageId: Long,
        val text: String,
        val delayMs: Int,
        val kind: String = "text",
        val meta: String = "",
    ) : ChatEvent
    data class RoleplayRecalled(val messageId: Long) : ChatEvent
    data object RetryReset : ChatEvent
    data object Completed : ChatEvent
    data class Failed(val error: AppError) : ChatEvent
}

@Single
class ChatEngine(
    private val client: ChatWireClient,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val chats: ChatStore,
    private val prompt: PromptBuilder,
    private val tools: ToolExecutor,
    private val approvals: ApprovalStore,
    private val agent: AgentSettings,
    private val compactor: ChatCompactor,
    private val workspaceTrust: WorkspaceTrustService,
    private val deepSeekSettings: DeepSeekSettings,
    private val locale: LocaleController,
    private val profiles: AssistantProfileStore,
    private val roleplaySettings: RoleplaySettings,
    private val roleplayPrompt: RoleplayPromptBuilder,
    private val roleplayStore: RoleplayStore,
    private val roleplayWorld: RoleplayWorldStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val waivers = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val askReplies = ConcurrentHashMap<String, CompletableDeferred<AskReply>>()
    private val trustDecisions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun decide(approvalId: String, approved: Boolean) {
        waivers.computeIfAbsent(approvalId) { CompletableDeferred() }.complete(approved)
    }

    fun decideTrust(id: String, trusted: Boolean) {
        trustDecisions.computeIfAbsent(id) { CompletableDeferred() }.complete(trusted)
    }

    fun reply(askId: String, answers: List<AskAnswer>, skipped: Boolean) {
        askReplies.computeIfAbsent(askId) { CompletableDeferred() }
            .complete(AskReply(id = askId, answers = answers, skipped = skipped))
    }

    fun send(
        conversationId: String,
        text: String,
        mode: ChatMode = ChatMode.Chat,
        options: TurnOptions = TurnOptions(),
        appendUser: Boolean = true,
    ): Flow<ChatEvent> = flow {
        val info = chats.conversation(conversationId)
        val profile = info?.let { profiles.profileOnce(it.assistantId) }
        if (mode == ChatMode.Chat && profile?.kind == AssistantKind.Roleplay) {
            emitAll(roleplaySend(conversationId, text, options, appendUser, profile))
        } else {
            emitAll(assistantSend(conversationId, text, mode, options, appendUser))
        }
    }

    private fun assistantSend(
        conversationId: String,
        text: String,
        mode: ChatMode,
        options: TurnOptions,
        appendUser: Boolean,
    ): Flow<ChatEvent> = flow {
        val config = settings.configOnce()
        val provider = config.providers.firstOrNull { it.id == options.providerId } ?: config.activeProvider
        val providerId = provider?.id ?: config.activeProviderId.orEmpty()
        val baseUrl = provider?.baseUrl ?: config.baseUrl.orEmpty()
        val protocol = provider?.protocol ?: ChatProtocol.ChatCompletions
        val model = options.model.ifBlank { provider?.model.orEmpty() }
        val endpoint = ModelEndpoint(baseUrl, model)
        if (!endpoint.isUsable) {
            emit(ChatEvent.Failed(AppError(AppErrorType.Generic, serverMessage = "Set the model connection first.")))
            return@flow
        }
        val apiKey = secrets.apiKey(providerId).orEmpty()
        if (apiKey.isBlank() && provider?.requiresKey != false) {
            emit(ChatEvent.Failed(AppError(AppErrorType.Unauthorized, serverMessage = "Set the API key first.")))
            return@flow
        }
        val requestConfig = provider?.requestConfig(apiKey) ?: ProviderRequestConfig(apiKey = apiKey)
        val profilePacks = chats.conversation(conversationId)?.assistantId
            ?.let { profiles.profileOnce(it)?.toolPacks }
            ?: ToolPack.Default

        val startedAt = System.currentTimeMillis()
        var agentMode = options.agentMode
        var totalStats = MessageStats(
            providerId = providerId,
            providerName = provider?.name ?: providerId,
            model = model,
        )
        var stepCount = 0
        var completesOnboarding = false
        var pendingMedia: List<ChatAttachment> = emptyList()
        var compactionTried = false
        val agentConfig = agent.configOnce()
        val retry = agentConfig.retry
        val deepSeekConfig = if (provider?.isDeepSeek == true) deepSeekSettings.configOnce() else DeepSeekConfig()
        val deepSeekBase = provider?.isDeepSeek == true &&
            model.startsWith("deepseek-flash") &&
            deepSeekConfig.enabled && mode == ChatMode.Chat
        val deepSeekChat = deepSeekBase && protocol == ChatProtocol.ChatCompletions
        val deepSeekMessages = deepSeekBase && protocol == ChatProtocol.AnthropicMessages
        val deepSeekEnabled = deepSeekChat || deepSeekMessages
        val wire = when {
            deepSeekChat -> WIRE_DEEPSEEK_CHAT
            deepSeekMessages -> WIRE_DEEPSEEK_MESSAGES
            else -> ""
        }
        var userTurns = 0

        try {
            if (appendUser && (text.isNotBlank() || options.attachments.isNotEmpty())) {
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.User,
                    content = text,
                    attachments = options.attachments,
                    replyToId = options.replyToId,
                )
                if (mode == ChatMode.Chat) {
                    emit(ChatEvent.UserMessageAppended(chats.userTurnCount(conversationId)))
                }
            }
            userTurns = chats.userTurnCount(conversationId)
            val maxRounds = agentConfig.roundLimit
            repeat(maxRounds) { roundIndex ->
                val round = roundIndex + 1
                if (!compactionTried && mode == ChatMode.Chat &&
                    compactor.shouldCompact(conversationId, providerId, model)
                ) {
                    compactionTried = true
                    emit(ChatEvent.CompactionStarted(automatic = true))
                    val compacted = compactor.compact(conversationId, providerId, model) { delta ->
                        emit(ChatEvent.CompactionDelta(delta))
                    }
                    emit(
                        ChatEvent.CompactionFinished(
                            automatic = true,
                            ok = compacted != null,
                            tokensBefore = compacted?.tokensBefore ?: 0,
                            tokensAfter = compacted?.tokensAfter ?: 0,
                        ),
                    )
                }
                val context = chats.context(conversationId)
                val info = chats.conversation(conversationId)
                val phoneMode = tools.phoneMode(conversationId)
                val specs = when (mode) {
                    ChatMode.Chat -> tools.specsFor(
                        agentMode,
                        main = info?.kind == ConversationKind.Main,
                        packs = profilePacks,
                        phoneMode = phoneMode,
                    )
                    ChatMode.Onboarding -> ToolCatalog.onboarding()
                }.let { list ->
                    if (completesOnboarding) list.filterNot { it.name == "complete_onboarding" } else list
                }
                val system = when (mode) {
                    ChatMode.Chat -> prompt.chatPrompt(
                        assistantId = info?.assistantId ?: AssistantProfile.DEFAULT_ID,
                        title = info?.title.orEmpty(),
                        mode = agentMode,
                        mentionedSkills = options.mentionedSkills,
                        main = info?.kind == ConversationKind.Main,
                        side = info?.kind == ConversationKind.Side,
                        packs = if (phoneMode) ToolPack.Phone else profilePacks,
                        phoneMode = phoneMode,
                    )
                    ChatMode.Onboarding -> prompt.onboardingPrompt(
                        assistantId = AssistantProfile.DEFAULT_ID,
                        turn = context.messages.count { it.role == ChatRole.User },
                    )
                }.let { base ->
                    if (deepSeekEnabled) {
                        base + "\n- Prefer deepseek_web_search and deepseek_read_url for current facts and links. " +
                            "They run on the server and return source citations."
                    } else {
                        base
                    }
                }.withSummary(context.summary)
                val messages = buildList {
                    add(ChatMessageDto(role = "system", content = system))
                    addAll(context.messages.toDtos(deepSeekEnabled, providerId, wire))
                    val media = pendingMedia.mapNotNull { it.dataUrl }
                    pendingMedia = emptyList()
                    if (media.isNotEmpty()) {
                        add(
                            ChatMessageDto(
                                role = "user",
                                content = MessageContent.of("Images returned by the tool calls above.", media),
                            ),
                        )
                    }
                    if (context.messages.isEmpty() && context.summary.isBlank()) {
                        add(ChatMessageDto(role = "user", content = "Begin the setup conversation now."))
                    }
                }.let { base ->
                    if (deepSeekEnabled && deepSeekConfig.userMeta && base.lastOrNull()?.role == "user") {
                        base.toMutableList().apply {
                            add(
                                lastIndex,
                                ChatMessageDto(
                                    role = "latest_reminder",
                                    content = latestReminder(deepSeekConfig.location),
                                ),
                            )
                        }
                    } else {
                        base
                    }
                }
                val toolDtos = specs.map { it.toDto() }.toMutableList().apply {
                    if (deepSeekEnabled) {
                        if (deepSeekConfig.webSearch) add(ToolDto.server("deepseek_web_search"))
                        if (deepSeekConfig.readLinks) add(ToolDto.server("deepseek_read_url"))
                    }
                }
                val request = ChatCompletionRequest(
                    model = endpoint.model,
                    messages = messages,
                    stream = true,
                    streamOptions = StreamOptionsDto(includeUsage = true),
                    tools = toolDtos.takeIf { it.isNotEmpty() },
                    toolChoice = "auto",
                    temperature = if (deepSeekEnabled) null else 0.4,
                    maxTokens = if (deepSeekEnabled) 8192 else null,
                    reasoningEffort = if (deepSeekEnabled) null else options.reasoning.wire,
                    thinking = if (deepSeekEnabled && options.reasoning.isEnabled) ThinkingDto() else null,
                    title = if (
                        deepSeekEnabled && deepSeekConfig.title && userTurns == 1 && round == 1
                    ) {
                        TitleDto()
                    } else {
                        null
                    },
                )
                val roundResult = streamRound(
                    endpoint = endpoint,
                    protocol = protocol,
                    config = requestConfig,
                    request = request,
                    retry = retry,
                    round = round,
                    step = stepCount,
                    totalStats = totalStats,
                    startedAt = startedAt,
                    emitDelta = { text -> emit(ChatEvent.Delta(text)) },
                    emitReasoningDelta = { text -> emit(ChatEvent.ReasoningDelta(text)) },
                    emitStats = { stats -> emit(ChatEvent.StatsUpdated(stats)) },
                    emitRetry = { attempt, max, reason -> emit(ChatEvent.RetryScheduled(attempt, max, reason)) },
                    emitReset = { emit(ChatEvent.RetryReset) },
                    emitEvent = { emit(it) },
                )
                if (roundResult.reasoningMs > 0) {
                    emit(ChatEvent.ReasoningTimed(roundResult.reasoningMs))
                }
                val message = roundResult.message
                message.title?.takeIf { it.isNotBlank() }?.let { emit(ChatEvent.TitleGenerated(it)) }
                if (roundResult.refusal) {
                    emit(ChatEvent.Refused(roundResult.refusalMessage))
                    return@flow
                }
                val citations = parseCitations(message.text, message.annotations)
                if (citations.isNotEmpty()) emit(ChatEvent.Citations(citations))
                totalStats = roundResult.stats.copy(
                    round = round,
                    step = stepCount,
                    durationMs = System.currentTimeMillis() - startedAt,
                )
                emit(ChatEvent.StatsUpdated(totalStats))
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.Assistant,
                    content = message.text,
                    reasoning = message.reasoning,
                    toolCalls = message.toolCalls.map {
                        ToolCallRecord(it.id, it.name, tools.redactArguments(it.name, it.arguments))
                    },
                    stats = totalStats,
                    elapsedMs = roundResult.reasoningMs,
                    serverBlocks = message.serverToolCalls?.toString(),
                    citations = citations,
                    wire = wire,
                )
                if (message.toolCalls.isEmpty()) {
                    emit(ChatEvent.Completed)
                    if (completesOnboarding) finishOnboarding(conversationId)
                    return@flow
                }

                message.toolCalls.forEach { call ->
                    if (call.name == ToolCatalog.STAY_SILENT) return@forEach
                    emit(
                        ChatEvent.ToolStarted(
                            label = tools.label(call.name),
                            id = call.id,
                            tool = call.name,
                            arguments = tools.redactArguments(call.name, call.arguments).take(500),
                        ),
                    )
                }
                val results = executeTools(message.toolCalls, conversationId, agentMode, providerId, model, options)
                if (results.any { it.silent }) {
                    emit(ChatEvent.Completed)
                    return@flow
                }
                pendingMedia = results.flatMap { it.media }
                for ((index, call) in message.toolCalls.withIndex()) {
                    var result = results[index]
                    val trust = result.trust
                    if (trust != null) {
                        if (workspaceTrust.state(trust.target) == WorkspaceTrustState.Unknown) {
                            emit(ChatEvent.TrustRequested(trust))
                            val trusted = awaitTrust(trust.id)
                            emit(ChatEvent.TrustResolved(trust, trusted))
                            workspaceTrust.decide(trust, trusted)
                        }
                        result = executeTool(call, conversationId, agentMode, providerId, model, options)
                        pendingMedia += result.media
                    }
                    stepCount++
                    totalStats = totalStats.copy(step = stepCount, durationMs = System.currentTimeMillis() - startedAt)
                    emit(ChatEvent.StatsUpdated(totalStats))
                    val label = tools.label(call.name)
                    result.workingText?.let { emit(ChatEvent.WorkingTextChanged(it)) }
                    result.todos?.let { emit(ChatEvent.TodoUpdated(it)) }
                    if (mode == ChatMode.Onboarding && result.completesOnboarding) {
                        completesOnboarding = true
                    }
                    val ask = result.ask
                    if (ask != null) {
                        emit(ChatEvent.AskRequested(ask))
                        val reply = awaitReply(ask.id)
                        emit(ChatEvent.AskResolved(ask, reply))
                        chats.append(
                            conversationId = conversationId,
                            role = ChatRole.Tool,
                            content = ask.answerContent(reply),
                            toolCallId = call.id,
                            name = call.name,
                            label = ask.questionLabels(),
                            elapsedMs = result.durationMs,
                        )
                        continue
                    }
                    val plan = result.plan
                    if (plan != null && agentMode == AgentMode.Plan) {
                        val request = result.approval ?: approvals.create(plan.title, plan.summary, emptyList())
                        chats.setPlan(conversationId, plan, PlanStatus.Pending, request.id)
                        emit(ChatEvent.PlanProposed(plan, request))
                        val approved = awaitDecision(request.id)
                        chats.setPlan(
                            conversationId,
                            plan,
                            if (approved) PlanStatus.Approved else PlanStatus.Rejected,
                            request.id,
                        )
                        emit(ChatEvent.PlanResolved(plan, approved))
                        if (approved) {
                            agentMode = AgentMode.Build
                            chats.setMode(conversationId, AgentMode.Build)
                        }
                        chats.append(
                            conversationId = conversationId,
                            role = ChatRole.Tool,
                            content = if (approved) {
                                "The user approved the plan. Continue in build mode and execute it."
                            } else {
                                "The user wants changes. Stay in plan mode and revise the plan."
                            },
                            toolCallId = call.id,
                            name = call.name,
                            label = plan.title,
                            elapsedMs = result.durationMs,
                        )
                        emit(
                            ChatEvent.ToolFinished(
                                label = label,
                                ok = true,
                                summary = plan.title,
                                tool = call.name,
                                id = call.id,
                                durationMs = result.durationMs,
                            ),
                        )
                        continue
                    }
                    val approval = result.approval
                    if (approval == null) {
                        if (call.name == ToolCatalog.SET_WORKING_TEXT) {
                            chats.append(
                                conversationId = conversationId,
                                role = ChatRole.Tool,
                                content = result.content,
                                toolCallId = call.id,
                                name = call.name,
                                label = label,
                                elapsedMs = result.durationMs,
                            )
                            continue
                        }
                        emit(
                            ChatEvent.ToolFinished(
                                label = label,
                                ok = result.ok,
                                summary = result.summary,
                                tool = call.name,
                                id = call.id,
                                output = result.content,
                                durationMs = result.durationMs,
                                diffs = result.diffs,
                                media = result.previews,
                            ),
                        )
                        chats.append(
                            conversationId = conversationId,
                            role = ChatRole.Tool,
                            content = result.content,
                            toolCallId = call.id,
                            name = call.name,
                            attachments = result.previews,
                            label = label,
                            elapsedMs = result.durationMs,
                            diffs = result.diffs.takeIf { it.isNotEmpty() }?.let { listOf(it) }.orEmpty(),
                        )
                        continue
                    }

                    emit(ChatEvent.ApprovalRequested(approval))
                    val approved = awaitDecision(approval.id)
                    emit(ChatEvent.ApprovalResolved(approval, approved))
                    val lines = mutableListOf<String>()
                    val stepDiffs = mutableListOf<List<FileDiff>>()
                    if (approved) {
                        approval.steps.forEach { step ->
                            emit(ChatEvent.ToolStarted(step.label, tool = step.tool, arguments = step.arguments.take(500)))
                            val stepStartedAt = System.currentTimeMillis()
                            var outcome = tools.runStep(step, conversationId, agentMode)
                            outcome.trust?.let { trust ->
                                if (workspaceTrust.state(trust.target) == WorkspaceTrustState.Unknown) {
                                    emit(ChatEvent.TrustRequested(trust))
                                    val trusted = awaitTrust(trust.id)
                                    emit(ChatEvent.TrustResolved(trust, trusted))
                                    workspaceTrust.decide(trust, trusted)
                                }
                                outcome = tools.runStep(step, conversationId, agentMode)
                            }
                            stepDiffs += outcome.diffs
                            emit(
                                ChatEvent.ToolFinished(
                                    label = step.label,
                                    ok = outcome.ok,
                                    summary = outcome.summary,
                                    tool = step.tool,
                                    output = outcome.content,
                                    durationMs = System.currentTimeMillis() - stepStartedAt,
                                    diffs = outcome.diffs,
                                ),
                            )
                            lines += outcome.describe()
                        }
                        approvals.decide(approval.id, ApprovalStatus.Approved, lines.joinToString("\n").take(500))
                    } else {
                        lines += "The user rejected this action."
                        approvals.decide(approval.id, ApprovalStatus.Rejected, null)
                    }
                    chats.append(
                        conversationId = conversationId,
                        role = ChatRole.Tool,
                        content = lines.joinToString("\n"),
                        toolCallId = call.id,
                        name = call.name,
                        label = approval.steps.map { it.label }
                            .filter { it.isNotBlank() }
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString("\n"),
                        elapsedMs = result.durationMs,
                        diffs = stepDiffs.takeIf { list -> list.any { it.isNotEmpty() } }.orEmpty(),
                    )
                    emit(
                        ChatEvent.ToolFinished(
                            label = label,
                            ok = approved,
                            summary = if (approved) "Approved" else "Rejected",
                            tool = call.name,
                            id = call.id,
                            durationMs = result.durationMs,
                        ),
                    )
                }
            }
            emit(ChatEvent.Completed)
            if (completesOnboarding) finishOnboarding(conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(ChatEvent.Failed(mapError(e)))
        }
    }

    fun compact(
        conversationId: String,
        options: TurnOptions = TurnOptions(),
    ): Flow<ChatEvent> = flow {
        emit(ChatEvent.CompactionStarted(automatic = false))
        val result = try {
            compactor.compact(conversationId, options.providerId, options.model) { delta ->
                emit(ChatEvent.CompactionDelta(delta))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        emit(
            ChatEvent.CompactionFinished(
                automatic = false,
                ok = result != null,
                tokensBefore = result?.tokensBefore ?: 0,
                tokensAfter = result?.tokensAfter ?: 0,
            ),
        )
        emit(ChatEvent.Completed)
    }

    private fun roleplaySend(
        conversationId: String,
        text: String,
        options: TurnOptions,
        appendUser: Boolean,
        profile: AssistantProfile,
    ): Flow<ChatEvent> = flow {
        val config = settings.configOnce()
        val provider = config.providers.firstOrNull { it.id == options.providerId } ?: config.activeProvider
        val providerId = provider?.id ?: config.activeProviderId.orEmpty()
        val baseUrl = provider?.baseUrl ?: config.baseUrl.orEmpty()
        val protocol = provider?.protocol ?: ChatProtocol.ChatCompletions
        val model = options.model.ifBlank { provider?.model.orEmpty() }
        val endpoint = ModelEndpoint(baseUrl, model)
        if (!endpoint.isUsable) {
            emit(ChatEvent.Failed(AppError(AppErrorType.Generic, serverMessage = "Set the model connection first.")))
            return@flow
        }
        val apiKey = secrets.apiKey(providerId).orEmpty()
        if (apiKey.isBlank() && provider?.requiresKey != false) {
            emit(ChatEvent.Failed(AppError(AppErrorType.Unauthorized, serverMessage = "Set the API key first.")))
            return@flow
        }
        val requestConfig = provider?.requestConfig(apiKey) ?: ProviderRequestConfig(apiKey = apiKey)
        val roleplayConfig = roleplaySettings.effectiveOnce(profile.id)
        val userPersona = roleplaySettings.userPersonaOnce()
        val turnId = options.turnId.ifBlank { System.currentTimeMillis().toString() }
        val startedAt = System.currentTimeMillis()
        val retry = agent.configOnce().retry
        try {
            if (appendUser && (text.isNotBlank() || options.attachments.isNotEmpty())) {
                chats.append(
                    conversationId = conversationId,
                    role = ChatRole.User,
                    content = text,
                    attachments = options.attachments,
                    replyToId = options.replyToId,
                )
                emit(ChatEvent.UserMessageAppended(chats.userTurnCount(conversationId)))
            }
            if (compactor.shouldCompact(conversationId, providerId, model)) {
                emit(ChatEvent.CompactionStarted(automatic = true))
                val compacted = compactor.compact(conversationId, providerId, model) { delta ->
                    emit(ChatEvent.CompactionDelta(delta))
                }
                emit(
                    ChatEvent.CompactionFinished(
                        automatic = true,
                        ok = compacted != null,
                        tokensBefore = compacted?.tokensBefore ?: 0,
                        tokensAfter = compacted?.tokensAfter ?: 0,
                    ),
                )
            }
            val context = chats.context(conversationId)
            val state = roleplayStore.tick(conversationId)
            if (roleplayConfig.memoryEnabled) {
                roleplayStore.decay(profile.id, roleplayConfig.memoryHalfLifeDays)
            }
            val memories = if (roleplayConfig.memoryEnabled) {
                roleplayStore.forContext(profile.id, text, roleplayConfig.memoryLimit)
            } else {
                emptyList()
            }
            val world = if (roleplayConfig.worldBookEnabled) {
                val books = roleplayWorld.books(profile.roleplay?.worldBookIds.orEmpty())
                val recent = chats.messages(conversationId, 40).mapNotNull { message ->
                    message.content.takeIf { it.isNotBlank() }
                }
                roleplayWorld.context(
                    books = books,
                    recentMessages = recent,
                    budgetChars = roleplayConfig.worldBookBudgetChars,
                )
            } else {
                fan.x0.focus.data.roleplay.RoleplayWorldContext()
            }
            val stickers = if (roleplayConfig.stickerEnabled) {
                roleplayStore.stickers().take(30).joinToString("\n") { sticker ->
                    val label = sticker.name.ifBlank { sticker.emotion }.ifBlank { sticker.value }
                    "- ${sticker.id.takeLast(6)} [${sticker.emotion.ifBlank { "-" }}] $label ${sticker.value}"
                }
            } else {
                ""
            }
            val system = roleplayPrompt.build(
                RoleplayPromptInput(
                    assistantName = profile.name,
                    userName = userPersona.name.ifBlank { "the user" },
                    userDescription = userPersona.description,
                    role = profile.role,
                    card = profile.roleplay ?: RoleplayCard(),
                    config = roleplayConfig,
                    state = state.view(),
                    currentTime = ToolTime.full(ToolTime.now()),
                    language = locale.current() ?: "zh",
                    memory = memories.map { memory ->
                        val due = if (memory.dueAt > 0) " due ${ToolTime.full(memory.dueAt)}" else ""
                        "${memory.id.takeLast(6)} [${memory.type.name.lowercase()}] ${memory.content}$due"
                    },
                    reminders = roleplayStore.openTasks(conversationId).map { task ->
                        "${task.id.takeLast(6)} at ${ToolTime.full(task.fireAt)} [${task.type}] ${task.prompt.take(100)}"
                    },
                    worldBefore = world.beforeChar,
                    worldAfter = world.afterChar,
                    worldBottom = world.bottomNote,
                    stickers = stickers,
                    proactive = options.proactive,
                    toolsEnabled = true,
                ),
            ).withSummary(context.summary)
            val agentConfig = agent.configOnce()
            val specs = tools.roleplaySpecs().filterNot {
                it.name == RoleplayToolCatalog.SEND_STICKER && !roleplayConfig.stickerEnabled
            }
            var totalStats = MessageStats(
                providerId = providerId,
                providerName = provider?.name ?: providerId,
                model = model,
            )
            var round = 0
            while (round < agentConfig.roundLimit) {
                round++
                val roundContext = chats.context(conversationId)
                val request = buildRequest(
                    endpoint = endpoint,
                    system = system,
                    context = roundContext,
                    pendingMedia = emptyList(),
                    providerId = providerId,
                    wire = "",
                    deepSeekEnabled = false,
                    deepSeekConfig = DeepSeekConfig(),
                    options = options,
                    specs = specs,
                    userTurns = chats.userTurnCount(conversationId),
                    round = round,
                    roleplay = true,
                )
                val bubbles = mutableListOf<RoleplayBubble>()
                val split = roleplayConfig.immersive && roleplayConfig.splitBubbles
                val parser = RoleplayBubbleParser(
                    enabled = split,
                    defaultDelayMs = 600,
                    autoSplitChars = if (split) 72 else 0,
                )
                val roundResult = streamRound(
                    endpoint = endpoint,
                    protocol = protocol,
                    config = requestConfig,
                    request = request,
                    retry = retry,
                    round = round,
                    step = round - 1,
                    totalStats = totalStats,
                    startedAt = startedAt,
                    emitDelta = { delta -> bubbles += parser.push(delta) },
                    emitReasoningDelta = { delta ->
                        if (roleplayConfig.showToolTrace) emit(ChatEvent.ReasoningDelta(delta))
                    },
                    emitStats = { stats -> emit(ChatEvent.StatsUpdated(stats)) },
                    emitRetry = { attempt, max, reason -> emit(ChatEvent.RetryScheduled(attempt, max, reason)) },
                    emitReset = { emit(ChatEvent.RetryReset) },
                    emitEvent = { emit(it) },
                )
                if (roundResult.reasoningMs > 0 && roleplayConfig.showToolTrace) {
                    emit(ChatEvent.ReasoningTimed(roundResult.reasoningMs))
                }
                if (roundResult.refusal) {
                    emit(ChatEvent.Refused(roundResult.refusalMessage))
                    return@flow
                }
                bubbles += parser.finish()
                val message = roundResult.message
                val citations = parseCitations(message.text, message.annotations)
                totalStats = roundResult.stats.copy(
                    round = round,
                    step = round - 1,
                    durationMs = System.currentTimeMillis() - startedAt,
                )
                emit(ChatEvent.StatsUpdated(totalStats))
                persistRoleplayRound(
                    conversationId = conversationId,
                    bubbles = bubbles,
                    calls = message.toolCalls.map {
                        ToolCallRecord(it.id, it.name, tools.redactArguments(it.name, it.arguments))
                    },
                    message = message,
                    totalStats = totalStats,
                    reasoningMs = roundResult.reasoningMs,
                    citations = citations,
                    wire = "",
                    turnId = turnId,
                    proactive = options.proactive.isNotBlank(),
                    config = roleplayConfig,
                    showReasoning = roleplayConfig.showToolTrace,
                ).forEach { (id, bubble) ->
                    emit(ChatEvent.RoleplayBubbleShown(id, bubble.text, bubble.delayMs))
                }
                if (message.toolCalls.isEmpty()) {
                    emit(ChatEvent.Completed)
                    return@flow
                }
                for (call in message.toolCalls) {
                    if (call.name == ToolCatalog.STAY_SILENT) {
                        emit(ChatEvent.Completed)
                        return@flow
                    }
                    val label = tools.label(call.name)
                    if (roleplayConfig.showToolTrace) {
                        emit(
                            ChatEvent.ToolStarted(
                                label = label,
                                id = call.id,
                                tool = call.name,
                                arguments = tools.redactArguments(call.name, call.arguments).take(500),
                            ),
                        )
                    }
                    val result = tools.execute(call, conversationId, AgentMode.Build)
                    result.workingText?.let { emit(ChatEvent.WorkingTextChanged(it)) }
                    val ask = result.ask
                    if (ask != null) {
                        emit(ChatEvent.AskRequested(ask))
                        val reply = awaitReply(ask.id)
                        emit(ChatEvent.AskResolved(ask, reply))
                        chats.append(
                            conversationId = conversationId,
                            role = ChatRole.Tool,
                            content = ask.answerContent(reply),
                            toolCallId = call.id,
                            name = call.name,
                            label = ask.questionLabels(),
                            elapsedMs = result.durationMs,
                        )
                        continue
                    }
                    result.outgoing.forEach { outgoing ->
                        val id = chats.append(
                            conversationId = conversationId,
                            role = ChatRole.Assistant,
                            content = outgoing.text,
                            kind = outgoing.kind,
                            meta = outgoing.meta,
                            turnId = turnId,
                            proactive = options.proactive.isNotBlank(),
                        )
                        emit(
                            ChatEvent.RoleplayBubbleShown(
                                messageId = id,
                                text = outgoing.text,
                                delayMs = 0,
                                kind = outgoing.kind,
                                meta = outgoing.meta,
                            ),
                        )
                    }
                    result.recalledMessageId?.let { emit(ChatEvent.RoleplayRecalled(it)) }
                    if (roleplayConfig.showToolTrace) {
                        emit(
                            ChatEvent.ToolFinished(
                                label = label,
                                ok = result.ok,
                                summary = result.summary,
                                tool = call.name,
                                id = call.id,
                                output = result.content,
                                durationMs = result.durationMs,
                                diffs = result.diffs,
                                media = result.previews,
                            ),
                        )
                    }
                    chats.append(
                        conversationId = conversationId,
                        role = ChatRole.Tool,
                        content = result.content,
                        toolCallId = call.id,
                        name = call.name,
                        attachments = result.previews,
                        label = label,
                        elapsedMs = result.durationMs,
                        diffs = result.diffs.takeIf { it.isNotEmpty() }?.let { listOf(it) }.orEmpty(),
                    )
                    if (result.silent) {
                        emit(ChatEvent.Completed)
                        return@flow
                    }
                }
            }
            emit(ChatEvent.Completed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(ChatEvent.Failed(e.toAppError()))
        }
    }

    private suspend fun buildRequest(
        endpoint: ModelEndpoint,
        system: String,
        context: ChatContext,
        pendingMedia: List<ChatAttachment>,
        providerId: String,
        wire: String,
        deepSeekEnabled: Boolean,
        deepSeekConfig: DeepSeekConfig,
        options: TurnOptions,
        specs: List<ToolSpec>,
        userTurns: Int,
        round: Int,
        roleplay: Boolean,
    ): ChatCompletionRequest {
        val messages = buildList {
            add(ChatMessageDto(role = "system", content = system))
            addAll(context.messages.toDtos(deepSeekEnabled, providerId, wire))
            val media = pendingMedia.mapNotNull { it.dataUrl }
            if (media.isNotEmpty()) {
                add(
                    ChatMessageDto(
                        role = "user",
                        content = MessageContent.of("Images returned by the tool calls above.", media),
                    ),
                )
            }
            if (context.messages.isEmpty() && context.summary.isBlank()) {
                add(ChatMessageDto(role = "user", content = "Begin the setup conversation now."))
            }
        }.let { base ->
            if (deepSeekEnabled && deepSeekConfig.userMeta && base.lastOrNull()?.role == "user") {
                base.toMutableList().apply {
                    add(
                        lastIndex,
                        ChatMessageDto(
                            role = "latest_reminder",
                            content = latestReminder(deepSeekConfig.location),
                        ),
                    )
                }
            } else {
                base
            }
        }
        val toolDtos = specs.map { it.toDto() }.toMutableList().apply {
            if (deepSeekEnabled) {
                if (deepSeekConfig.webSearch) add(ToolDto.server("deepseek_web_search"))
                if (deepSeekConfig.readLinks) add(ToolDto.server("deepseek_read_url"))
            }
        }
        return ChatCompletionRequest(
            model = endpoint.model,
            messages = messages,
            stream = true,
            streamOptions = StreamOptionsDto(includeUsage = true),
            tools = toolDtos.takeIf { it.isNotEmpty() },
            toolChoice = "auto",
            temperature = when {
                deepSeekEnabled -> null
                roleplay -> 1.0
                else -> 0.4
            },
            maxTokens = if (deepSeekEnabled) 8192 else null,
            reasoningEffort = if (deepSeekEnabled) null else options.reasoning.wire,
            thinking = if (deepSeekEnabled && options.reasoning.isEnabled) ThinkingDto() else null,
            title = if (!roleplay && deepSeekEnabled && deepSeekConfig.title && userTurns == 1 && round == 1) {
                TitleDto()
            } else {
                null
            },
        )
    }

    private suspend fun persistRoleplayRound(
        conversationId: String,
        bubbles: List<RoleplayBubble>,
        calls: List<ToolCallRecord>,
        message: CompletedMessage,
        totalStats: MessageStats,
        reasoningMs: Long,
        citations: List<ChatCitation>,
        wire: String,
        turnId: String,
        proactive: Boolean,
        config: RoleplayConfig,
        showReasoning: Boolean,
    ): List<Pair<Long, RoleplayBubble>> {
        val cleaned = bubbles.mapNotNull { bubble ->
            val text = applyPunctuation(
                cleanBubble(bubble.text, config.stripMarkdown),
                config.punctuationStyle,
            )
            if (text.isBlank()) null else RoleplayBubble(text, bubble.delayMs)
        }
        if (cleaned.isEmpty()) {
            if (calls.isEmpty()) return emptyList()
            chats.append(
                conversationId = conversationId,
                role = ChatRole.Assistant,
                content = "",
                reasoning = if (showReasoning) message.reasoning else "",
                toolCalls = calls,
                stats = totalStats,
                elapsedMs = reasoningMs,
                serverBlocks = message.serverToolCalls?.toString(),
                citations = citations,
                wire = wire,
                turnId = turnId,
                proactive = proactive,
            )
            return emptyList()
        }
        return cleaned.mapIndexed { index, bubble ->
            val last = index == cleaned.lastIndex
            val id = chats.append(
                conversationId = conversationId,
                role = ChatRole.Assistant,
                content = bubble.text,
                reasoning = if (index == 0 && showReasoning) message.reasoning else "",
                toolCalls = if (last) calls else emptyList(),
                stats = if (last) totalStats else null,
                elapsedMs = if (index == 0) reasoningMs else 0,
                serverBlocks = if (last) message.serverToolCalls?.toString() else null,
                citations = if (last) citations else emptyList(),
                wire = wire,
                turnId = turnId,
                proactive = proactive,
            )
            id to bubble
        }
    }

    private suspend fun executeTools(
        calls: List<CompletedToolCall>,
        conversationId: String,
        mode: AgentMode,
        providerId: String,
        model: String,
        options: TurnOptions,
    ): List<ToolResult> = coroutineScope {
        val semaphore = Semaphore(TOOL_CONCURRENCY)
        val readCalls = calls.filter { call ->
            tools.find(call.name)?.kind == ToolKind.Read
        }
        val deferred = readCalls.associate { call ->
            call.id to async {
                semaphore.withPermit {
                    executeTool(call, conversationId, mode, providerId, model, options)
                }
            }
        }
        calls.map { call ->
            if (call.id in deferred) {
                deferred.getValue(call.id).await()
            } else {
                executeTool(call, conversationId, mode, providerId, model, options)
            }
        }
    }

    private suspend fun executeTool(
        call: CompletedToolCall,
        conversationId: String,
        mode: AgentMode,
        providerId: String,
        model: String,
        options: TurnOptions,
    ): ToolResult {
        val startedAt = System.currentTimeMillis()
        val result = tools.execute(call, conversationId, mode)
        return result.copy(durationMs = System.currentTimeMillis() - startedAt)
    }

    private suspend fun streamRound(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        config: ProviderRequestConfig,
        request: ChatCompletionRequest,
        retry: RetryConfig,
        round: Int,
        step: Int,
        totalStats: MessageStats,
        startedAt: Long,
        emitDelta: suspend (String) -> Unit,
        emitReasoningDelta: suspend (String) -> Unit,
        emitStats: suspend (MessageStats) -> Unit,
        emitRetry: suspend (Int, Int, String) -> Unit,
        emitReset: suspend () -> Unit,
        emitEvent: suspend (ChatEvent) -> Unit,
    ): RoundResult {
        var attempt = 0
        while (true) {
            var latest = totalStats.copy(round = round, step = step)
            val accumulator = StreamAccumulator()
            var visible = false
            var reasoningStartedAt = 0L
            var reasoningEndedAt = 0L
            var roundStats = MessageStats(
                providerId = totalStats.providerId,
                providerName = totalStats.providerName,
                model = totalStats.model,
                round = round,
                step = step,
            )
            try {
                client.stream(endpoint, protocol, config, request).collect { event ->
                    if (event is ChatStreamEvent.Text && event.delta.isNotBlank()) visible = true
                    accumulator.apply(event)
                    when (event) {
                        is ChatStreamEvent.Text -> {
                            if (reasoningStartedAt > 0 && reasoningEndedAt == 0L) {
                                reasoningEndedAt = System.currentTimeMillis()
                            }
                            emitDelta(event.delta)
                        }
                        is ChatStreamEvent.Reasoning -> {
                            if (reasoningStartedAt == 0L) {
                                reasoningStartedAt = System.currentTimeMillis()
                            }
                            emitReasoningDelta(event.delta)
                        }
                        is ChatStreamEvent.ServerToolStarted -> {
                            visible = true
                            if (reasoningStartedAt > 0 && reasoningEndedAt == 0L) {
                                reasoningEndedAt = System.currentTimeMillis()
                            }
                            emitEvent(
                                ChatEvent.ToolStarted(
                                    label = serverToolLabel(event.call),
                                    id = event.call.id,
                                    tool = event.call.name,
                                    arguments = serverToolArguments(event.call),
                                ),
                            )
                        }
                        is ChatStreamEvent.ServerToolFinished -> {
                            visible = true
                            emitEvent(
                                ChatEvent.ToolFinished(
                                    label = serverToolLabel(event.call),
                                    ok = event.call.error == null && event.call.status != "failed",
                                    summary = serverToolSummary(event.call),
                                    tool = event.call.name,
                                    id = event.call.id,
                                    output = serverToolOutput(event.call),
                                ),
                            )
                        }
                        is ChatStreamEvent.Usage -> {
                            roundStats = roundStats.copy(
                                inputTokens = event.prompt,
                                outputTokens = event.completion,
                                cachedTokens = event.cached,
                                totalTokens = event.total,
                                durationMs = System.currentTimeMillis() - startedAt,
                                lastContextTokens = event.prompt,
                            )
                            latest = totalStats.merge(roundStats).copy(
                                round = round,
                                step = step,
                                durationMs = System.currentTimeMillis() - startedAt,
                            )
                            emitStats(latest)
                        }
                        else -> Unit
                    }
                }
                val message = accumulator.result()
                if (message.text.isBlank() && message.toolCalls.isEmpty() && message.reasoning.isBlank() &&
                    message.refusal == null && message.finishReason != "content_filter"
                ) {
                    throw ModelProtocolException("stream ended before a reply")
                }
                val reasoningMs = when {
                    reasoningStartedAt == 0L -> 0L
                    reasoningEndedAt > 0 -> reasoningEndedAt - reasoningStartedAt
                    else -> System.currentTimeMillis() - reasoningStartedAt
                }
                return RoundResult(
                    message = message,
                    stats = latest,
                    reasoningMs = reasoningMs,
                    refusal = message.refusal != null || message.finishReason == "content_filter",
                    refusalMessage = message.refusal,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (visible || attempt >= retry.maxRetries || !isRetryable(e)) throw e
                attempt++
                emitReset()
                emitRetry(attempt, retry.maxRetries, retryReason(e))
                delay(retry.delayFor(attempt, retryAfter(e)))
            }
        }
    }

    private fun isRetryable(e: Throwable): Boolean = RetryPolicy.isRetryable(e)

    private fun retryAfter(e: Throwable): Long? = RetryPolicy.retryAfter(e)

    private fun retryReason(e: Throwable): String = RetryPolicy.reason(e)

    private suspend fun finishOnboarding(conversationId: String) {
        withContext(NonCancellable) {
            chats.clear(conversationId)
            settings.completeOnboarding()
        }
    }

    private suspend fun awaitDecision(approvalId: String): Boolean {
        val deferred = waivers.computeIfAbsent(approvalId) { CompletableDeferred() }
        val decision = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() }
        waivers.remove(approvalId)
        return decision ?: false
    }

    private suspend fun awaitReply(askId: String): AskReply {
        val deferred = askReplies.computeIfAbsent(askId) { CompletableDeferred() }
        val reply = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() }
        askReplies.remove(askId)
        return reply ?: AskReply(id = askId, skipped = true)
    }

    private suspend fun awaitTrust(id: String): Boolean {
        val deferred = trustDecisions.computeIfAbsent(id) { CompletableDeferred() }
        val decision = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() }
        trustDecisions.remove(id)
        return decision ?: false
    }

    private fun String.withSummary(summary: String): String {
        if (summary.isBlank()) return this
        return buildString {
            append(this@withSummary)
            appendLine()
            appendLine("Summary of the earlier conversation:")
            append(summary)
        }
    }

    private suspend fun List<ChatMessage>.toDtos(
        deepSeekEnabled: Boolean,
        providerId: String,
        wire: String,
    ): List<ChatMessageDto> {
        val quotes = chats.messagesByIds(mapNotNull { it.replyToId }.toSet())
        return withoutDanglingToolCalls().mapNotNull { message ->
            when (message.role) {
                ChatRole.User -> ChatMessageDto(
                    role = "user",
                    content = message.toUserContent(quotes[message.replyToId], deepSeekEnabled, providerId),
                )
                ChatRole.Assistant -> {
                    val raw = message.serverBlocks
                        ?.takeIf { wire.isNotBlank() && message.wire == wire }
                        ?.let { raw ->
                            runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
                        }
                    ChatMessageDto(
                        role = "assistant",
                        content = MessageContent.Text(message.content),
                        reasoningContent = message.reasoning.ifBlank { null },
                        toolCalls = message.toolCalls.map { call ->
                            ToolCallDto(
                                id = call.id,
                                function = FunctionCallDto(
                                    name = call.name,
                                    arguments = tools.redactArguments(call.name, call.arguments),
                                ),
                            )
                        }.ifEmpty { null },
                        serverToolCalls = raw.takeIf { wire == WIRE_DEEPSEEK_CHAT },
                        protocolBlocks = raw.takeIf { wire == WIRE_DEEPSEEK_MESSAGES },
                    )
                }
                ChatRole.Tool -> ChatMessageDto(
                    role = "tool",
                    content = message.content.orEmpty(),
                    toolCallId = message.toolCallId,
                    name = message.name,
                )
                ChatRole.System -> ChatMessageDto(
                    role = "user",
                    content = "[app notice] ${message.content}",
                )
            }
        }
    }

    private fun List<ChatMessage>.withoutDanglingToolCalls(): List<ChatMessage> {
        val consumed = BooleanArray(size)
        val kept = mutableListOf<ChatMessage>()
        for (index in indices) {
            if (consumed[index]) continue
            val message = this[index]
            if (message.role == ChatRole.Tool) continue
            if (message.role != ChatRole.Assistant || message.toolCalls.isEmpty()) {
                kept += message
                continue
            }
            val ids = message.toolCalls.mapTo(mutableSetOf()) { it.id }
            val answered = mutableSetOf<String>()
            val results = mutableListOf<ChatMessage>()
            for (next in index + 1 until size) {
                val candidate = this[next]
                if (candidate.role == ChatRole.Assistant) break
                val callId = candidate.toolCallId
                if (candidate.role == ChatRole.Tool && callId != null && callId in ids && answered.add(callId)) {
                    consumed[next] = true
                    results += candidate
                }
            }
            if (answered.containsAll(ids)) {
                kept += message
                kept += results
            }
        }
        return kept
    }

    private fun ChatMessage.toUserContent(
        quoted: ChatMessage?,
        deepSeekEnabled: Boolean,
        providerId: String,
    ): MessageContent {
        val images = attachments.filter { it.kind == AttachmentKind.Image }.mapNotNull { it.dataUrl }
        val files = if (deepSeekEnabled) {
            attachments
                .filter { it.kind == AttachmentKind.Document }
                .filter { it.providerId.isNullOrBlank() || it.providerId == providerId }
                .mapNotNull { it.fileId?.takeIf { id -> id.isNotBlank() } }
        } else {
            emptyList()
        }
        val text = buildString {
            if (quoted != null) {
                val author = if (quoted.role == ChatRole.User) "the user" else "you"
                append("Replying to ").append(author).append(": ")
                append(quoted.content.replace(Regex("\\s+"), " ").trim().take(600))
                append("\n\n")
            }
            append(content)
            attachments.filter { it.kind == AttachmentKind.Text }.forEach { attach ->
                val body = attach.text ?: return@forEach
                if (body.isBlank()) return@forEach
                if (isNotEmpty()) append("\n\n")
                append("<<file: ").append(attach.name).append(">>\n")
                append(body)
                append("\n<<end file>>")
            }
        }
        return MessageContent.of(text.trim(), images, files)
    }

    private fun latestReminder(location: String): String {
        val language = locale.current()
            ?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
            ?: Locale.getDefault()
        val date = LocalDate.now()
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, language)
        return listOf(date.toString(), weekday, location.trim(), "App", language.getDisplayLanguage(language))
            .joinToString(",")
    }

    private fun parseCitations(text: String, annotations: JsonArray?): List<ChatCitation> {
        if (annotations == null) return emptyList()
        val numbers = LinkedHashMap<String, Int>()
        val result = mutableListOf<ChatCitation>()
        annotations.forEach { element ->
            val citation = (element as? JsonObject)?.get("url_citation") as? JsonObject ?: return@forEach
            val url = (citation["url"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@forEach
            val end = (citation["end_index"] as? JsonPrimitive)?.intOrNull ?: return@forEach
            result += ChatCitation(
                index = numbers.getOrPut(url) { numbers.size + 1 },
                url = url,
                title = (citation["title"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                siteName = (citation["site_name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                anchor = charIndex(text, end),
            )
        }
        return result
    }

    private fun charIndex(text: String, codePointIndex: Int): Int {
        if (codePointIndex <= 0) return 0
        var codePoints = 0
        var index = 0
        while (index < text.length && codePoints < codePointIndex) {
            val high = text[index].isHighSurrogate()
            val low = index + 1 < text.length && text[index + 1].isLowSurrogate()
            index += if (high && low) 2 else 1
            codePoints++
        }
        return index
    }

    private fun serverToolLabel(call: ServerToolCall): String = when (call.action?.actionString("action")) {
        "search" -> "Web search"
        "open_page" -> "Open page"
        "find_in_page" -> "Find in page"
        "read_link" -> "Read link"
        else -> ToolLabels.of(call.name)
    }

    private fun serverToolArguments(call: ServerToolCall): String {
        val action = call.action ?: return call.name
        return when (action.actionString("action")) {
            "search" -> (action["queries"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.joinToString(" / ")
                .orEmpty()
            "open_page", "read_link" -> action.actionString("url").orEmpty()
            "find_in_page" -> listOfNotNull(
                action.actionString("url"),
                action.actionString("pattern"),
            ).joinToString(" ")
            else -> call.name
        }
    }

    private fun serverToolSummary(call: ServerToolCall): String {
        call.error?.let { return it }
        if (call.sources.isNotEmpty()) return "${call.sources.size} sources"
        call.page?.let { page -> return page.title ?: page.url }
        return if (call.status == "completed") {
            when (call.action?.actionString("action")) {
                "search" -> "Search completed"
                "read_link" -> "Page loaded"
                "open_page" -> "Page opened"
                "find_in_page" -> "Search completed"
                else -> "Done"
            }
        } else {
            ""
        }
    }

    private fun serverToolOutput(call: ServerToolCall): String = buildString {
        call.sources.take(6).forEach { source ->
            appendLine(listOfNotNull(source.title, source.url).joinToString(" "))
        }
        call.page?.let { page ->
            appendLine(listOfNotNull(page.title, page.url).joinToString(" "))
        }
    }.trim()

    private fun JsonObject?.actionString(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun mapError(e: Throwable): AppError = when (e) {
        is NetworkException -> e.toAppError()
        is ModelProtocolException -> AppError(AppErrorType.Generic, serverMessage = e.message)
        else -> AppError(AppErrorType.Generic, serverMessage = e.message ?: e.javaClass.simpleName)
    }

    companion object {
        private const val APPROVAL_TIMEOUT_MS = 15 * 60 * 1000L
        private const val TOOL_CONCURRENCY = 4
        private const val WIRE_DEEPSEEK_CHAT = "deepseek.chat"
        private const val WIRE_DEEPSEEK_MESSAGES = "deepseek.messages"
    }
}

private data class RoundResult(
    val message: CompletedMessage,
    val stats: MessageStats,
    val reasoningMs: Long = 0,
    val refusal: Boolean = false,
    val refusalMessage: String? = null,
)
