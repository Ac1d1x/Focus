package fan.x0.focus.feature.assistant

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.AssistantKind
import fan.x0.focus.data.assistant.AssistantProfile
import fan.x0.focus.data.assistant.ToolPack
import fan.x0.focus.data.assistant.WorkspaceFile
import fan.x0.focus.data.roleplay.RoleplayStateView

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

data class RoleplayBookUi(
    val id: String,
    val name: String,
    val kind: String,
    val enabled: Boolean,
    val entryCount: Int,
)

data class RoleplayMemoryUi(
    val id: String,
    val type: String,
    val content: String,
    val dueAt: Long,
    val done: Boolean,
)

data class AssistantUiState(
    val loading: Boolean = true,
    val profile: AssistantProfile? = null,
    val assistantId: String = AssistantProfile.DEFAULT_ID,
    val assistantName: String = "",
    val assistantRole: String = "",
    val assistantEmoji: String = "",
    val assistantAvatar: String = "",
    val assistantKind: AssistantKind = AssistantKind.Assistant,
    val roleplayGreeting: String = "",
    val roleplayPrompt: String = "",
    val roleplayExamples: String = "",
    val roleplayBooks: List<RoleplayBookUi> = emptyList(),
    val roleplayMemories: List<RoleplayMemoryUi> = emptyList(),
    val roleplayState: RoleplayStateView? = null,
    val canDelete: Boolean = false,
    val documents: List<WorkspaceDocumentUi> = emptyList(),
    val openDocument: WorkspaceDocumentUi? = null,
    val mainPrompt: String = "",
    val sidePrompt: String = "",
    val promptEditor: PromptEditorUi? = null,
    val promptSaving: Boolean = false,
    val toolPacks: Set<ToolPack> = ToolPack.Default,
    val toolsOpen: Boolean = false,
) : UiState

sealed interface AssistantAction : UiAction {
    data class OpenDocument(val file: WorkspaceFile) : AssistantAction
    data object CloseDocument : AssistantAction
    data class OpenPrompt(val target: PromptTarget) : AssistantAction
    data class PromptChanged(val value: String) : AssistantAction
    data object ClosePrompt : AssistantAction
    data object SavePrompt : AssistantAction
    data class SaveProfile(val name: String, val role: String, val emoji: String, val avatar: String) : AssistantAction
    data class SaveRoleplayCard(val greeting: String, val prompt: String, val examples: String) : AssistantAction
    data class ImportRoleplayCard(val json: String) : AssistantAction
    data class ImportWorldBook(val json: String) : AssistantAction
    data class RemoveWorldBook(val id: String) : AssistantAction
    data object ExportRoleplayCard : AssistantAction
    data class DeleteRoleplayMemory(val id: String) : AssistantAction
    data class CompleteRoleplayMemory(val id: String) : AssistantAction
    data object OpenTools : AssistantAction
    data object CloseTools : AssistantAction
    data class ToggleToolPack(val pack: ToolPack) : AssistantAction
    data object DeleteProfile : AssistantAction
}

sealed interface AssistantEffect : UiEffect {
    data object PromptSaved : AssistantEffect
    data object ProfileSaved : AssistantEffect
    data object ProfileDeleted : AssistantEffect
    data class RoleplayExported(val json: String, val name: String) : AssistantEffect
}
