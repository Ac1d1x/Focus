package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AssistantManager
import fan.x0.focus.data.assistant.AssistantProfile
import fan.x0.focus.data.assistant.AssistantProfileStore
import fan.x0.focus.data.assistant.PromptSettings
import fan.x0.focus.data.assistant.WorkspaceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AssistantViewModel(
    private val workspace: WorkspaceStore,
    private val prompts: PromptSettings,
    private val profiles: AssistantProfileStore,
    private val manager: AssistantManager,
) : BaseViewModel<AssistantAction, AssistantUiState, AssistantEffect>(AssistantUiState()) {

    private var assistantId = AssistantProfile.DEFAULT_ID

    init {
        viewModelScope.launch {
            workspace.revision.collect { load() }
        }
        viewModelScope.launch {
            profiles.activeId.collect { id ->
                assistantId = id
                load()
            }
        }
        viewModelScope.launch {
            profiles.activeId.collectLatest { id ->
                prompts.config(id).collect { config ->
                    updateState { copy(mainPrompt = config.main, sidePrompt = config.side) }
                }
            }
        }
    }

    private suspend fun load() {
        try {
            val documents = workspace.readAll(assistantId)
            val assistantName = workspace.assistantName(assistantId)
            val all = profiles.profiles.first()
            val profile = all.firstOrNull { it.id == assistantId }
            updateState {
                copy(
                    loading = false,
                    assistantId = assistantId,
                    assistantName = assistantName,
                    assistantRole = profile?.role.orEmpty(),
                    assistantEmoji = profile?.emoji.orEmpty(),
                    assistantAvatar = profile?.avatar.orEmpty(),
                    canDelete = all.size > 1,
                    documents = documents.map { WorkspaceDocumentUi(it.file, it.content) },
                    openDocument = openDocument?.let { open ->
                        documents.firstOrNull { it.file == open.file }
                            ?.let { WorkspaceDocumentUi(it.file, it.content) }
                    },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            updateState { copy(loading = false) }
            emitError(e.toAppError())
        }
    }

    override fun onAction(action: AssistantAction) {
        when (action) {
            is AssistantAction.OpenDocument -> updateState {
                copy(openDocument = documents.firstOrNull { it.file == action.file })
            }
            AssistantAction.CloseDocument -> updateState { copy(openDocument = null) }
            is AssistantAction.OpenPrompt -> updateState {
                copy(
                    promptEditor = PromptEditorUi(
                        target = action.target,
                        value = when (action.target) {
                            PromptTarget.Main -> mainPrompt
                            PromptTarget.Side -> sidePrompt
                        },
                    ),
                )
            }
            is AssistantAction.PromptChanged -> updateState {
                copy(
                    promptEditor = promptEditor?.copy(
                        value = action.value.take(PromptSettings.MAX_PROMPT_CHARS),
                    ),
                )
            }
            AssistantAction.ClosePrompt -> updateState { copy(promptEditor = null) }
            AssistantAction.SavePrompt -> savePrompt()
            is AssistantAction.SaveProfile -> saveProfile(action)
            AssistantAction.DeleteProfile -> deleteProfile()
        }
    }

    private fun saveProfile(action: AssistantAction.SaveProfile) {
        viewModelScope.launch {
            val profile = profiles.profiles.first().firstOrNull { it.id == assistantId } ?: return@launch
            manager.update(
                profile.copy(
                    name = action.name,
                    role = action.role,
                    emoji = action.emoji,
                    avatar = action.avatar,
                ),
            )
            updateState {
                copy(
                    assistantName = action.name.trim(),
                    assistantRole = action.role.trim(),
                    assistantEmoji = action.emoji.trim(),
                    assistantAvatar = action.avatar,
                )
            }
            sendEffect(AssistantEffect.ProfileSaved)
        }
    }

    private fun deleteProfile() {
        viewModelScope.launch {
            val profile = profiles.profiles.first().firstOrNull { it.id == assistantId } ?: return@launch
            if (manager.delete(profile)) {
                sendEffect(AssistantEffect.ProfileDeleted)
            }
        }
    }

    private fun savePrompt() {
        val editor = currentState.promptEditor ?: return
        if (currentState.promptSaving) return
        updateState { copy(promptSaving = true) }
        launchGuarded(
            onError = { updateState { copy(promptSaving = false) } },
        ) {
            val value = editor.value.trim()
            when (editor.target) {
                PromptTarget.Main -> prompts.setMain(assistantId, value)
                PromptTarget.Side -> prompts.setSide(assistantId, value)
            }
            updateState { copy(promptSaving = false, promptEditor = null) }
            sendEffect(AssistantEffect.PromptSaved)
        }
    }
}
