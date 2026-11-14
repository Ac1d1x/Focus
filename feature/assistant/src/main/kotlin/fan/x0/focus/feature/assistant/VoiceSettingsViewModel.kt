package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.isDeepSeek
import fan.x0.focus.data.voice.LocalModelState
import fan.x0.focus.data.voice.LocalVoiceModelRepository
import fan.x0.focus.data.voice.TtsConfig
import fan.x0.focus.data.voice.TtsEngine
import fan.x0.focus.data.voice.TtsVoice
import fan.x0.focus.data.voice.VoiceConfig
import fan.x0.focus.data.voice.VoiceEngine
import fan.x0.focus.data.voice.VoiceInputController
import fan.x0.focus.data.voice.VoiceSettingsStore
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class VoiceProviderOption(
    val id: String,
    val name: String,
    val deepSeek: Boolean,
)

data class VoiceSettingsUiState(
    val loading: Boolean = true,
    val config: VoiceConfig = VoiceConfig(),
    val tts: TtsConfig = TtsConfig(),
    val activeProviderId: String = "",
    val providers: List<VoiceProviderOption> = emptyList(),
    val model: LocalModelState = LocalModelState.Missing,
) : UiState

sealed interface VoiceSettingsAction : UiAction {
    data class SetEngine(val engine: VoiceEngine) : VoiceSettingsAction
    data class SetCloudProvider(val providerId: String) : VoiceSettingsAction
    data class SetCloudModel(val model: String) : VoiceSettingsAction
    data class SetAutoStop(val enabled: Boolean) : VoiceSettingsAction
    data class SetSilenceSeconds(val seconds: Int) : VoiceSettingsAction
    data class SetTtsEngine(val engine: TtsEngine) : VoiceSettingsAction
    data class SetTtsProvider(val providerId: String) : VoiceSettingsAction
    data class SetTtsVoice(val voice: TtsVoice) : VoiceSettingsAction
    data class SetTtsAutoRead(val enabled: Boolean) : VoiceSettingsAction
    data object DownloadModel : VoiceSettingsAction
    data object DeleteModel : VoiceSettingsAction
    data object RefreshModel : VoiceSettingsAction
}

sealed interface VoiceSettingsEffect : UiEffect

@KoinViewModel
class VoiceSettingsViewModel(
    private val settings: VoiceSettingsStore,
    private val assistant: AssistantSettings,
    private val models: LocalVoiceModelRepository,
    private val voice: VoiceInputController,
) : BaseViewModel<VoiceSettingsAction, VoiceSettingsUiState, VoiceSettingsEffect>(
    VoiceSettingsUiState(),
) {
    init {
        viewModelScope.launch {
            settings.config.collect { config ->
                updateState { copy(config = config, loading = false) }
            }
        }
        viewModelScope.launch {
            settings.ttsConfig.collect { config ->
                updateState { copy(tts = config) }
            }
        }
        viewModelScope.launch {
            assistant.config.collect { config ->
                updateState {
                    copy(
                        activeProviderId = config.activeProviderId,
                        providers = config.providers.map { provider ->
                            VoiceProviderOption(
                                id = provider.id,
                                name = provider.name.ifBlank { provider.id },
                                deepSeek = provider.isDeepSeek,
                            )
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            models.state.collect { state ->
                updateState { copy(model = state) }
            }
        }
    }

    override fun onAction(action: VoiceSettingsAction) {
        when (action) {
            is VoiceSettingsAction.SetEngine -> viewModelScope.launch {
                settings.setEngine(action.engine)
            }
            is VoiceSettingsAction.SetCloudProvider -> viewModelScope.launch {
                settings.setCloudProvider(action.providerId)
            }
            is VoiceSettingsAction.SetCloudModel -> viewModelScope.launch {
                settings.setCloudModel(action.model)
            }
            is VoiceSettingsAction.SetAutoStop -> viewModelScope.launch {
                settings.setAutoStop(action.enabled)
            }
            is VoiceSettingsAction.SetSilenceSeconds -> viewModelScope.launch {
                settings.setSilenceSeconds(action.seconds)
            }
            is VoiceSettingsAction.SetTtsEngine -> viewModelScope.launch {
                settings.setTtsEngine(action.engine)
                if (action.engine == TtsEngine.DeepSeek && currentState.tts.providerId.isBlank()) {
                    val fallback = currentState.providers.firstOrNull { it.deepSeek }?.id
                    if (!fallback.isNullOrBlank()) settings.setTtsProvider(fallback)
                }
            }
            is VoiceSettingsAction.SetTtsProvider -> viewModelScope.launch {
                settings.setTtsProvider(action.providerId)
            }
            is VoiceSettingsAction.SetTtsVoice -> viewModelScope.launch {
                settings.setTtsVoice(action.voice)
            }
            is VoiceSettingsAction.SetTtsAutoRead -> viewModelScope.launch {
                settings.setTtsAutoRead(action.enabled)
            }
            VoiceSettingsAction.DownloadModel -> models.download()
            VoiceSettingsAction.DeleteModel -> {
                models.delete()
                voice.releaseLocalModel()
            }
            VoiceSettingsAction.RefreshModel -> models.refresh()
        }
    }
}
