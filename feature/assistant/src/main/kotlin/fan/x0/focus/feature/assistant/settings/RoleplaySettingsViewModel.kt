package fan.x0.focus.feature.assistant.settings

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AssistantKind
import fan.x0.focus.data.assistant.AssistantProfileStore
import fan.x0.focus.data.roleplay.RoleplayOverride
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
                settings.overrides,
            ) { list, config, persona, overrides ->
                OverrideBundle(list.filter { it.kind == AssistantKind.Roleplay }, config, persona, overrides)
            }.collect { bundle ->
                updateState {
                    copy(
                        loading = false,
                        assistants = bundle.assistants,
                        config = bundle.config,
                        userPersona = bundle.persona,
                        overrides = bundle.overrides,
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
            is RoleplaySettingsAction.SetOverride -> viewModelScope.launch {
                if (action.override == null) {
                    settings.clearOverride(action.assistantId)
                } else {
                    settings.setOverride(action.assistantId, action.override)
                }
            }
        }
    }

    private suspend fun refreshStickers() {
        val list = roleplayStore.stickers()
        updateState { copy(stickers = list) }
    }

    private data class OverrideBundle(
        val assistants: List<fan.x0.focus.data.assistant.AssistantProfile>,
        val config: fan.x0.focus.data.roleplay.RoleplayConfig,
        val persona: fan.x0.focus.data.roleplay.RoleplayUserPersona,
        val overrides: Map<String, RoleplayOverride>,
    )
}
