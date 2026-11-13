package fan.x0.focus.feature.assistant

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.AssistantProfile
import fan.x0.focus.data.assistant.WorkspaceFile

data class WorkspaceDocumentUi(
    val file: WorkspaceFile,
    val content: String,
) {
    val fileName: String get() = file.fileName
}

enum class PromptTarget {
    Main,
    Side,
}

data class PromptEditorUi(
    val target: PromptTarget,
    val value: String,
)

data class AssistantUiState(
    val loading: Boolean = true,
    val assistantId: String = AssistantProfile.DEFAULT_ID,
    val assistantName: String = "",
    val assistantRole: String = "",
    val assistantEmoji: String = "",
    val assistantAvatar: String = "",
    val canDelete: Boolean = false,
    val documents: List<WorkspaceDocumentUi> = emptyList(),
    val openDocument: WorkspaceDocumentUi? = null,
    val mainPrompt: String = "",
    val sidePrompt: String = "",
    val promptEditor: PromptEditorUi? = null,
    val promptSaving: Boolean = false,
) : UiState

sealed interface AssistantAction : UiAction {
    data class OpenDocument(val file: WorkspaceFile) : AssistantAction
    data object CloseDocument : AssistantAction
    data class OpenPrompt(val target: PromptTarget) : AssistantAction
    data class PromptChanged(val value: String) : AssistantAction
    data object ClosePrompt : AssistantAction
    data object SavePrompt : AssistantAction
    data class SaveProfile(val name: String, val role: String, val emoji: String, val avatar: String) : AssistantAction
    data object DeleteProfile : AssistantAction
}

sealed interface AssistantEffect : UiEffect {
    data object PromptSaved : AssistantEffect
    data object ProfileSaved : AssistantEffect
    data object ProfileDeleted : AssistantEffect
}
