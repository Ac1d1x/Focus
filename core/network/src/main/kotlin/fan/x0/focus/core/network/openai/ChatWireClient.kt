package fan.x0.focus.core.network.openai

import fan.x0.focus.core.network.ProviderRequestConfig
import kotlinx.coroutines.flow.Flow

class ChatWireClient(
    private val chat: OpenAiChatClient,
    private val responses: OpenAiResponsesClient,
    private val anthropic: AnthropicChatClient,
) {
    fun stream(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        config: ProviderRequestConfig,
        request: ChatCompletionRequest,
    ): Flow<ChatStreamEvent> {
        val target = if (protocol == ChatProtocol.AnthropicMessages) endpoint.anthropicEndpoint() else endpoint
        return when (protocol) {
            ChatProtocol.ChatCompletions -> chat.stream(target, config, request)
            ChatProtocol.Responses -> responses.stream(target, config, request)
            ChatProtocol.AnthropicMessages -> anthropic.stream(target, config, request)
        }
    }

    suspend fun complete(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        config: ProviderRequestConfig,
        request: ChatCompletionRequest,
    ): ChatReply {
        val target = if (protocol == ChatProtocol.AnthropicMessages) endpoint.anthropicEndpoint() else endpoint
        return when (protocol) {
            ChatProtocol.ChatCompletions -> chat.complete(target, config, request).toReply()
            ChatProtocol.Responses -> responses.complete(target, config, request)
            ChatProtocol.AnthropicMessages -> anthropic.complete(target, config, request)
        }
    }
}

private fun ModelEndpoint.anthropicEndpoint(): ModelEndpoint {
    val host = runCatching { java.net.URI(rootUrl).host }.getOrNull().orEmpty().lowercase()
    if (host != "deepseek.com" && !host.endsWith(".deepseek.com")) return this
    if (rootUrl.endsWith("/anthropic/v1")) return this
    return copy(baseUrl = "https://api.deepseek.com/anthropic/v1")
}

private fun ChatCompletionResponse.toReply(): ChatReply {
    val message = choices.firstOrNull()?.message
    val calls = message?.toolCalls?.mapIndexedNotNull { index, call ->
        val name = call.function?.name ?: return@mapIndexedNotNull null
        CompletedToolCall(
            id = call.id ?: "call_$index",
            name = name,
            arguments = call.function?.arguments ?: "{}",
        )
    }.orEmpty()
    return ChatReply(
        text = message?.content.orEmpty(),
        model = model,
        toolCalls = calls,
        serverToolCalls = message?.serverToolCalls,
        annotations = message?.annotations,
        title = message?.title,
        refusal = message?.refusal,
    )
}
