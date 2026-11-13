package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ChatCompletionRequest
import fan.x0.focus.core.network.openai.ChatMessageDto
import fan.x0.focus.core.network.openai.ChatWireClient
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.data.sources.CalendarRepository
import fan.x0.focus.data.sources.FeedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.util.Locale

data class ChatSuggestion(val text: String)

@Single
class ChatSuggestionGenerator(
    private val client: ChatWireClient,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val chats: ChatStore,
    private val calendar: CalendarRepository,
    private val feeds: FeedRepository,
    private val goals: GoalStore,
    private val workspace: WorkspaceStore,
    private val locale: LocaleController,
    private val models: ModelResolver,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun generate(providerId: String, model: String, assistantId: String): List<ChatSuggestion> {
        val config = settings.configOnce()
        val fallback = config.providers.firstOrNull { it.id == providerId }
            ?: config.activeProvider
            ?: return emptyList()
        val resolved = models.resolve(AgentRole.Suggestion)
        val provider = resolved?.provider ?: fallback
        val endpoint = ModelEndpoint(
            baseUrl = provider.baseUrl,
            model = resolved?.model ?: model.ifBlank { provider.model },
        )
        if (!endpoint.isUsable) return emptyList()
        val apiKey = secrets.apiKey(provider.id).orEmpty()
        if (apiKey.isBlank()) return emptyList()
        val requestConfig = provider.requestConfig(apiKey)

        val request = ChatCompletionRequest(
            model = endpoint.model,
            messages = listOf(
                ChatMessageDto(role = "system", content = SYSTEM_PROMPT),
                ChatMessageDto(role = "user", content = buildContext(assistantId)),
            ),
            stream = false,
        )
        val reply = client.complete(endpoint, provider.protocol, requestConfig, request)
        return parse(reply.text)
    }

    private suspend fun buildContext(assistantId: String): String {
        val name = workspace.userName(assistantId)
        val focus = workspace.field(assistantId, WorkspaceFile.User, "Focus")
        val activeGoals = fallback({ goals.active(assistantId) }, emptyList())
        val titles = fallback(
            {
                chats.conversations(assistantId = assistantId)
                    .map { it.title.trim() }
                    .filter { it.isNotBlank() }
                    .take(20)
            },
            emptyList(),
        )
        val events = if (calendar.canRead()) {
            fallback(
                { calendar.eventsBetween(ToolTime.now(), ToolTime.daysFromNow(3), 12) },
                emptyList(),
            )
        } else {
            emptyList()
        }
        val articles = fallback({ feeds.listArticles(limit = 30) }, emptyList())
            .let { list -> list.filterNot { it.read }.ifEmpty { list } }
            .take(12)
        val language = workspace.userLanguage(assistantId)?.takeIf { it.isNotBlank() }
            ?: locale.current()?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().language

        return buildString {
            appendLine("Preferred language: $language")
            appendLine("Current time: ${ToolTime.full(ToolTime.now())}")
            name?.takeIf { it.isNotBlank() }?.let { appendLine("Name: $it") }
            focus?.takeIf { it.isNotBlank() }?.let { appendLine("Focus: $it") }
            if (activeGoals.isNotEmpty()) {
                appendLine("Active goals:")
                activeGoals.take(8).forEach { goal ->
                    val detail = goal.detail?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    appendLine("- ${goal.title}$detail (${goal.progress}%)")
                }
            }
            if (events.isNotEmpty()) {
                appendLine("Calendar:")
                events.forEach { event ->
                    appendLine("- ${ToolTime.full(event.start)} ${event.title}")
                }
            }
            if (articles.isNotEmpty()) {
                appendLine("Recent articles:")
                articles.forEach { article ->
                    appendLine("- [${article.feedTitle}] ${article.title}")
                }
            }
            if (titles.isNotEmpty()) {
                appendLine("Previous conversation titles:")
                titles.forEach { appendLine("- $it") }
            }
            appendLine("Suggest 3 things the user might ask next.")
        }
    }

    private fun parse(raw: String): List<ChatSuggestion> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val payload = raw.substring(start, end + 1)
        val items = runCatching {
            json.decodeFromString(ListSerializer(SuggestionDto.serializer()), payload)
        }.getOrNull().orEmpty()
        return items.mapNotNull { item ->
            val text = item.text.trim().take(MAX_TEXT_LENGTH)
            if (text.isBlank()) return@mapNotNull null
            ChatSuggestion(text)
        }.distinctBy { it.text }.take(3)
            .takeIf { it.size == 3 }
            .orEmpty()
    }

    private suspend fun <T> fallback(block: suspend () -> T, default: T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        default
    }

    @Serializable
    private data class SuggestionDto(
        val text: String = "",
    )

    companion object {
        private const val MAX_TEXT_LENGTH = 240
        private const val SYSTEM_PROMPT =
            "You suggest what the user might ask their personal assistant next. " +
                "Use only the context provided. Never invent calendar events, articles, goals, or past conversations. " +
                "Reply with a JSON array of exactly 3 objects. Each object has " +
                "\"text\", a short natural first-person question the user can send as is. " +
                "Write the questions in the user's preferred language. " +
                "Make the three suggestions distinct and specific to the context. " +
                "Return only the JSON array, no markdown."
    }
}
