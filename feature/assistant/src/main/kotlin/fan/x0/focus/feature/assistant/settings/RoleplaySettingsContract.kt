package fan.x0.focus.feature.assistant.settings

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.AssistantProfile
import fan.x0.focus.data.roleplay.RoleplayConfig
import fan.x0.focus.data.roleplay.RoleplayUserPersona

data class RoleplaySettingsUiState(
    val loading: Boolean = true,
    val assistants: List<AssistantProfile> = emptyList(),
    val config: RoleplayConfig = RoleplayConfig(),
    val userPersona: RoleplayUserPersona = RoleplayUserPersona(),
) : UiState

sealed interface RoleplaySettingsAction : UiAction {
    data class UpdateConfig(val config: RoleplayConfig) : RoleplaySettingsAction
    data class SaveUserPersona(val persona: RoleplayUserPersona) : RoleplaySettingsAction
    data class OpenAssistant(val id: String) : RoleplaySettingsAction
}

sealed interface RoleplaySettingsEffect : UiEffect {
    data class OpenAssistant(val id: String) : RoleplaySettingsEffect
}
