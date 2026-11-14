package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.ProviderAuth
import fan.x0.focus.core.network.ProviderHeader
import fan.x0.focus.core.network.ProviderRequestConfig
import fan.x0.focus.core.network.openai.ChatProtocol
import java.net.URI
import kotlinx.serialization.Serializable

const val LEGACY_PROVIDER_ID = "default"

@Serializable
data class ProviderModel(
    val id: String,
    val name: String = "",
)

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String = "",
    val models: List<ProviderModel> = emptyList(),
    val modelsConfigured: Boolean = false,
    val protocol: ChatProtocol = ChatProtocol.ChatCompletions,
    val requiresKey: Boolean = true,
    val userAgent: String = "",
    val headers: List<ProviderHeader> = emptyList(),
    val auth: ProviderAuth = ProviderAuth(),
    val icon: String = "",
)

val ProviderConfig.modelOptions: List<ProviderModel>
    get() {
        val saved = models
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        if (model.isBlank() || saved.any { it.id == model }) return saved
        return listOf(ProviderModel(model)) + saved
    }

fun ProviderConfig.requestConfig(apiKey: String): ProviderRequestConfig = ProviderRequestConfig(
    apiKey = apiKey,
    auth = auth,
    userAgent = userAgent,
    headers = headers,
)

val ProviderConfig.isDeepSeek: Boolean
    get() {
        if (id == "deepseek") return true
        val host = runCatching { URI(baseUrl.trim()).host }.getOrNull().orEmpty().lowercase()
        return host == "deepseek.com" || host.endsWith(".deepseek.com")
    }

fun ProviderConfig.deepSeekToolUrl(tool: String): String {
    val raw = baseUrl.trim().trimEnd('/')
    if (raw.isBlank()) return ""
    val host = runCatching { URI(raw).host }.getOrNull().orEmpty().lowercase()
    val name = tool.trimStart('/')
    if (host == "deepseek.com" || host.endsWith(".deepseek.com")) {
        return "https://api.deepseek.com/v1/tools/$name"
    }
    val root = when {
        raw.endsWith("/anthropic/v1") -> raw.removeSuffix("/anthropic/v1") + "/v1"
        raw.endsWith("/v1") -> raw
        else -> "$raw/v1"
    }
    return "$root/tools/$name"
}

fun ProviderConfig.deepSeekFilesUrl(): String {
    val raw = baseUrl.trim().trimEnd('/')
    if (raw.isBlank()) return ""
    val host = runCatching { URI(raw).host }.getOrNull().orEmpty().lowercase()
    if (host == "deepseek.com" || host.endsWith(".deepseek.com")) {
        return "https://api.deepseek.com/files"
    }
    val root = when {
        raw.endsWith("/anthropic/v1") -> raw.removeSuffix("/anthropic/v1")
        raw.endsWith("/v1") -> raw.removeSuffix("/v1")
        else -> raw
    }
    return "$root/files"
}

object ProviderCatalog {
    val presets = listOf(
        ProviderConfig(id = "openai", name = "OpenAI", baseUrl = "https://api.openai.com/v1"),
        ProviderConfig(id = "openrouter", name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1"),
        ProviderConfig(id = "deepseek", name = "DeepSeek", baseUrl = "https://api.deepseek.com/v1"),
        ProviderConfig(
            id = "anthropic",
            name = "Anthropic",
            baseUrl = "https://api.anthropic.com/v1",
            protocol = ChatProtocol.AnthropicMessages,
        ),
        ProviderConfig(
            id = PolicyDefaults.JUDGE_PROVIDER_ID,
            name = "TypeSafe",
            baseUrl = "https://api.typesafe.ai/v1",
            model = PolicyDefaults.JEV_MODEL,
        ),
    )

    fun isPreset(id: String): Boolean = presets.any { it.id == id }
}
