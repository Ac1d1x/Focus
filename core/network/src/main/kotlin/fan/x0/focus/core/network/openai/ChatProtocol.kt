package fan.x0.focus.core.network.openai

import kotlinx.serialization.json.JsonArray

enum class ChatProtocol {
    ChatCompletions,
    Responses,
    AnthropicMessages,
}

data class ChatReply(
    val text: String,
    val model: String?,
    val toolCalls: List<CompletedToolCall> = emptyList(),
    val serverToolCalls: JsonArray? = null,
    val annotations: JsonArray? = null,
    val title: String? = null,
    val refusal: String? = null,
)
