package fan.x0.focus.feature.assistant.settings

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AssistantKind
import fan.x0.focus.data.assistant.AssistantProfileStore
import fan.x0.focus.data.roleplay.RoleplaySettings
import fan.x0.focus.data.roleplay.RoleplayStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class RoleplaySettingsViewModel(
    private val profiles: AssistantProfileStore,
    private val settings: RoleplaySettings,
    private val roleplayStore: RoleplayStore,
) : BaseViewModel<RoleplaySettingsAction, RoleplaySettingsUiState, RoleplaySettingsEffect>(
    RoleplaySettingsUiState(),
) {
    init {
        viewModelScope.launch {
            combine(
                profiles.profiles,
                settings.global,
                settings.userPersona,
            ) { list, config, persona ->
                Triple(list.filter { it.kind == AssistantKind.Roleplay }, config, persona)
            }.collect { (assistants, config, persona) ->
                updateState {
                    copy(
                        loading = false,
                        assistants = assistants,
                        config = config,
                        userPersona = persona,
                    )
                }
            }
        }
        viewModelScope.launch { refreshStickers() }
    }

    override fun onAction(action: RoleplaySettingsAction) {
        when (action) {
            is RoleplaySettingsAction.UpdateConfig -> {
                updateState { copy(config = action.config) }
                viewModelScope.launch { settings.setGlobal(action.config) }
            }
            is RoleplaySettingsAction.SaveUserPersona -> {
                updateState { copy(userPersona = action.persona) }
                viewModelScope.launch { settings.setUserPersona(action.persona) }
            }
            is RoleplaySettingsAction.OpenAssistant -> sendEffect(
                RoleplaySettingsEffect.OpenAssistant(action.id),
            )
            is RoleplaySettingsAction.AddSticker -> viewModelScope.launch {
                roleplayStore.addSticker(
                    kind = "emoji",
                    value = action.value,
                    name = action.name,
                    emotion = action.emotion,
                )
                refreshStickers()
            }
            is RoleplaySettingsAction.DeleteSticker -> viewModelScope.launch {
                roleplayStore.deleteSticker(action.id)
                refreshStickers()
            }
        }
    }

    private suspend fun refreshStickers() {
        val list = roleplayStore.stickers()
        updateState { copy(stickers = list) }
    }
}
