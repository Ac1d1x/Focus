package fan.x0.focus.core.network.openai

import kotlinx.serialization.json.JsonObject

data class ServerToolSource(
    val title: String,
    val url: String,
    val snippet: String = "",
    val siteName: String? = null,
    val siteIcon: String? = null,
    val publishedAt: String? = null,
    val imageUrl: String? = null,
)

data class ServerToolPage(
    val url: String,
    val title: String? = null,
    val publishedAt: String? = null,
)

data class ServerToolCall(
    val id: String,
    val name: String,
    val kind: String = "",
    val action: JsonObject? = null,
    val status: String? = null,
    val sources: List<ServerToolSource> = emptyList(),
    val page: ServerToolPage? = null,
    val error: String? = null,
    val signature: String? = null,
)

data class ServerToolStep(
    val index: Int,
    val reasoning: String = "",
    val calls: List<ServerToolCall> = emptyList(),
)

internal fun JsonObject.toServerToolCall(): ServerToolCall = ServerToolCall(
    id = wireString("id").orEmpty(),
    name = wireString("type") ?: wireString("name").orEmpty(),
    kind = wireString("kind").orEmpty(),
    action = wireObject("action"),
    status = wireString("status"),
    sources = wireArray("sources")
        ?.mapNotNull { (it as? JsonObject)?.toServerToolSource() }
        .orEmpty(),
    page = wireObject("page")?.toServerToolPage(),
    error = wireObject("error")?.wireString("code") ?: wireString("error"),
    signature = wireString("signature"),
)

internal fun JsonObject.toServerToolSource(): ServerToolSource = ServerToolSource(
    title = wireString("title").orEmpty(),
    url = wireString("url").orEmpty(),
    snippet = wireString("snippet").orEmpty(),
    siteName = wireString("site_name"),
    siteIcon = wireString("site_icon"),
    publishedAt = wireString("published_at"),
    imageUrl = wireString("image_url"),
)

internal fun JsonObject.toServerToolPage(): ServerToolPage = ServerToolPage(
    url = wireString("url").orEmpty(),
    title = wireString("title"),
    publishedAt = wireString("published_at"),
)

internal fun JsonObject.toServerToolStep(): ServerToolStep = ServerToolStep(
    index = wireInt("index") ?: 0,
    reasoning = wireText("reasoning_content").orEmpty(),
    calls = wireArray("calls")
        ?.mapNotNull { (it as? JsonObject)?.toServerToolCall() }
        .orEmpty(),
)
