package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.DeepSeekConfig
import fan.x0.focus.data.assistant.DeepSeekSettings
import fan.x0.focus.data.assistant.isDeepSeek
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class DeepSeekProviderOption(
    val id: String,
    val name: String,
    val model: String,
)

data class DeepSeekSettingsUiState(
    val config: DeepSeekConfig = DeepSeekConfig(),
    val providers: List<DeepSeekProviderOption> = emptyList(),
) : UiState

sealed interface DeepSeekSettingsAction : UiAction {
    data class SetEnabled(val value: Boolean) : DeepSeekSettingsAction
    data class SetWebSearch(val value: Boolean) : DeepSeekSettingsAction
    data class SetReadLinks(val value: Boolean) : DeepSeekSettingsAction
    data class SetAutoTitle(val value: Boolean) : DeepSeekSettingsAction
    data class SetUserMeta(val value: Boolean) : DeepSeekSettingsAction
    data class SetLocation(val value: String) : DeepSeekSettingsAction
}

sealed interface DeepSeekSettingsEffect : UiEffect

@KoinViewModel
class DeepSeekSettingsViewModel(
    private val settings: DeepSeekSettings,
    private val assistant: AssistantSettings,
) : BaseViewModel<DeepSeekSettingsAction, DeepSeekSettingsUiState, DeepSeekSettingsEffect>(
    DeepSeekSettingsUiState(),
) {
    init {
        viewModelScope.launch {
            settings.config.collect { config ->
                updateState { copy(config = config) }
            }
        }
        viewModelScope.launch {
            assistant.config.collect { config ->
                updateState {
                    copy(
                        providers = config.providers
                            .filter { it.isDeepSeek }
                            .map { provider ->
                                DeepSeekProviderOption(
                                    id = provider.id,
                                    name = provider.name.ifBlank { provider.id },
                                    model = provider.model,
                                )
                            },
                    )
                }
            }
        }
    }

    override fun onAction(action: DeepSeekSettingsAction) {
        when (action) {
            is DeepSeekSettingsAction.SetEnabled -> update { it.copy(enabled = action.value) }
            is DeepSeekSettingsAction.SetWebSearch -> update { it.copy(webSearch = action.value) }
            is DeepSeekSettingsAction.SetReadLinks -> update { it.copy(readLinks = action.value) }
            is DeepSeekSettingsAction.SetAutoTitle -> update { it.copy(title = action.value) }
            is DeepSeekSettingsAction.SetUserMeta -> update { it.copy(userMeta = action.value) }
            is DeepSeekSettingsAction.SetLocation -> update { it.copy(location = action.value) }
        }
    }

    private fun update(transform: (DeepSeekConfig) -> DeepSeekConfig) {
        val next = transform(currentState.config)
        updateState { copy(config = next) }
        viewModelScope.launch { settings.set(next) }
    }
}
