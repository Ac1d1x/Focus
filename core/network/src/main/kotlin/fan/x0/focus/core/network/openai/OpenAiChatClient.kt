package fan.x0.focus.core.network.openai

import fan.x0.focus.core.network.NetworkExceptionMapper
import fan.x0.focus.core.network.ProviderRequestConfig
import fan.x0.focus.core.network.applyProvider
import io.ktor.client.HttpClient
import fan.x0.focus.core.network.allowLongStream
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class ModelEndpoint(val baseUrl: String, val model: String) {
    val rootUrl: String
        get() {
            var value = baseUrl.trim().trimEnd('/')
            listOf("/chat/completions", "/responses", "/messages").forEach { suffix ->
                if (value.endsWith(suffix)) value = value.removeSuffix(suffix).trimEnd('/')
            }
            return value
        }

    val chatUrl: String get() = "$rootUrl/chat/completions"

    val responsesUrl: String get() = "$rootUrl/responses"

    val messagesUrl: String get() = "$rootUrl/messages"

    val isUsable: Boolean
        get() = model.isNotBlank() && (baseUrl.startsWith("https://") || baseUrl.startsWith("http://"))
}

class ModelProtocolException(message: String) : Exception(message)

class OpenAiChatClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) {
    suspend fun complete(
        endpoint: ModelEndpoint,
        config: ProviderRequestConfig,
        request: ChatCompletionRequest,
    ): ChatCompletionResponse {
        val response = try {
            http.post(endpoint.chatUrl) {
                applyProvider(config, ChatProtocol.ChatCompletions)
                header(HttpHeaders.Accept, "application/json")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ChatCompletionRequest.serializer(), request))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw NetworkExceptionMapper.mapOrNull(e) ?: e
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw NetworkExceptionMapper.http(
                response.status.value,
                body,
                response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
            )
        }
        return runCatching { json.decodeFromString(ChatCompletionResponse.serializer(), body) }
            .getOrElse { throw ModelProtocolException(it.message ?: "invalid response body") }
    }

    fun stream(
        endpoint: ModelEndpoint,
        config: ProviderRequestConfig,
        request: ChatCompletionRequest,
    ): Flow<ChatStreamEvent> = flow {
        val response = try {
            http.post(endpoint.chatUrl) {
                allowLongStream()
                applyProvider(config, ChatProtocol.ChatCompletions)
                header(HttpHeaders.Accept, "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ChatCompletionRequest.serializer(), request))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw NetworkExceptionMapper.mapOrNull(e) ?: e
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw NetworkExceptionMapper.http(
                response.status.value,
                body,
                response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
            )
        }
        val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
        if (contentType.contains("application/json")) {
            val body = response.bodyAsText()
            val chunk = runCatching { json.decodeFromString(ChatCompletionResponse.serializer(), body) }
                .getOrElse { throw ModelProtocolException(it.message ?: "invalid response body") }
            chunk.choices.forEach { choice ->
                choice.message?.let { message ->
                    (message.reasoningContent?.takeIf { it.isNotBlank() }
                        ?: message.reasoning?.takeIf { it.isNotBlank() })
                        ?.let { emit(ChatStreamEvent.Reasoning(it)) }
                    message.content?.takeIf { it.isNotBlank() }?.let { emit(ChatStreamEvent.Text(it)) }
                    message.serverToolCalls?.let { raw ->
                        emit(ChatStreamEvent.ServerToolCalls(raw))
                        emitServerSteps(raw)
                    }
                    message.annotations?.let { emit(ChatStreamEvent.Annotations(it)) }
                    message.title?.takeIf { it.isNotBlank() }?.let { emit(ChatStreamEvent.Title(it)) }
                    message.refusal?.takeIf { it.isNotBlank() }?.let { emit(ChatStreamEvent.Refused(it)) }
                }
                choice.message?.toolCalls?.forEachIndexed { index, call ->
                    emit(
                        ChatStreamEvent.ToolDelta(
                            index = index,
                            id = call.id,
                            name = call.function?.name,
                            arguments = call.function?.arguments,
                        ),
                    )
                }
                if (choice.finishReason == "content_filter") {
                    emit(ChatStreamEvent.Refused(null))
                }
                emit(ChatStreamEvent.Finished(choice.finishReason))
            }
            return@flow
        }
        val channel = response.bodyAsChannel()
        val decoder = SseDecoder()
        val serverCalls = mutableMapOf<String, ServerToolCall>()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            val payload = decoder.onLine(line) ?: continue
            if (payload == "[DONE]") break
            for (event in decodeChunk(payload, serverCalls)) emit(event)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ChatStreamEvent>.emitServerSteps(raw: JsonArray) {
        raw.forEach { element ->
            val step = (element as? JsonObject)?.toServerToolStep() ?: return@forEach
            step.calls.forEach { call ->
                emit(ChatStreamEvent.ServerToolStarted(step.index, call))
                emit(ChatStreamEvent.ServerToolFinished(step.index, call))
            }
        }
    }

    private fun decodeChunk(
        payload: String,
        serverCalls: MutableMap<String, ServerToolCall>,
    ): List<ChatStreamEvent> {
        val chunk = runCatching { json.decodeFromString(ChatCompletionResponse.serializer(), payload) }
            .getOrElse {
                val error = NetworkExceptionMapper.decodeError(payload)
                if (error != null) {
                    throw ModelProtocolException(error.message ?: error.title ?: "stream error")
                }
                throw ModelProtocolException(it.message ?: "invalid stream chunk")
            }
        val events = mutableListOf<ChatStreamEvent>()
        chunk.usage?.let {
            events += ChatStreamEvent.Usage(
                prompt = it.promptTokens,
                completion = it.completionTokens,
                total = it.totalTokens,
                cached = it.promptDetails?.cachedTokens ?: 0,
            )
        }
        chunk.choices.forEach { choice: ChoiceDto ->
            choice.serverToolEvent?.let { event ->
                val step = event.wireInt("step") ?: 0
                val call = event.wireObject("call")?.toServerToolCall()
                when (event.wireString("type")) {
                    "call_started" -> if (call != null) {
                        serverCalls[call.id] = call
                        events += ChatStreamEvent.ServerToolStarted(step, call)
                    }
                    "call_completed" -> if (call != null) {
                        val started = serverCalls[call.id]
                        events += ChatStreamEvent.ServerToolFinished(
                            step,
                            call.copy(
                                name = call.name.ifBlank { started?.name.orEmpty() },
                                kind = call.kind.ifBlank { started?.kind.orEmpty() },
                                action = call.action ?: started?.action,
                            ),
                        )
                    }
                }
            }
            choice.delta?.let { delta ->
                (delta.reasoningContent?.takeIf { it.isNotBlank() } ?: delta.reasoning?.takeIf { it.isNotBlank() })
                    ?.let { events += ChatStreamEvent.Reasoning(it) }
                delta.content?.takeIf { it.isNotEmpty() }?.let { events += ChatStreamEvent.Text(it) }
                delta.serverToolCalls?.let { events += ChatStreamEvent.ServerToolCalls(it) }
                delta.annotations?.let { events += ChatStreamEvent.Annotations(it) }
                delta.title?.takeIf { it.isNotBlank() }?.let { events += ChatStreamEvent.Title(it) }
                delta.refusal?.takeIf { it.isNotBlank() }?.let { events += ChatStreamEvent.Refused(it) }
            }
            choice.delta?.toolCalls?.forEach { call: ToolCallDeltaDto ->
                events += ChatStreamEvent.ToolDelta(
                    index = call.index,
                    id = call.id,
                    name = call.function?.name,
                    arguments = call.function?.arguments,
                )
            }
            choice.finishReason?.let {
                if (it == "content_filter") events += ChatStreamEvent.Refused(null)
                events += ChatStreamEvent.Finished(it)
            }
        }
        return events
    }
}
