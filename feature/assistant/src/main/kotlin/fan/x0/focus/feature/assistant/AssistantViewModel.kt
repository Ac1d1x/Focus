package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AssistantManager
import fan.x0.focus.data.assistant.AssistantProfile
import fan.x0.focus.data.assistant.AssistantProfileStore
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.PromptSettings
import fan.x0.focus.data.assistant.WorkspaceStore
import fan.x0.focus.data.roleplay.RoleplayCard
import fan.x0.focus.data.roleplay.RoleplayStore
import fan.x0.focus.data.roleplay.RoleplayWorldStore
import fan.x0.focus.data.roleplay.SillyTavernCodec
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
    private val roleplayWorld: RoleplayWorldStore,
    private val roleplayStore: RoleplayStore,
    private val chats: ChatStore,
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
            chats.changes.collect { load() }
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
            val books = if (profile?.kind == fan.x0.focus.data.assistant.AssistantKind.Roleplay) {
                roleplayWorld.books(profile.roleplay?.worldBookIds.orEmpty()).map { book ->
                    RoleplayBookUi(
                        id = book.id,
                        name = book.name,
                        kind = book.kind.name,
                        enabled = book.enabled,
                        entryCount = roleplayWorld.countEntries(book.id),
                    )
                }
            } else {
                emptyList()
            }
            val roleplayState = if (profile?.kind == fan.x0.focus.data.assistant.AssistantKind.Roleplay) {
                val main = chats.ensureMainConversation(profile.id)
                roleplayStore.state(main.id).view()
            } else {
                null
            }
            val memories = if (profile?.kind == fan.x0.focus.data.assistant.AssistantKind.Roleplay) {
                roleplayStore.memories(profile.id).take(20).map { memory ->
                    RoleplayMemoryUi(
                        id = memory.id,
                        type = memory.type.name.lowercase(),
                        content = memory.content,
                        dueAt = memory.dueAt,
                        done = memory.done,
                    )
                }
            } else {
                emptyList()
            }
            updateState {
                copy(
                    loading = false,
                    profile = profile,
                    assistantId = assistantId,
                    assistantName = assistantName,
                    assistantRole = profile?.role.orEmpty(),
                    assistantEmoji = profile?.emoji.orEmpty(),
                    assistantAvatar = profile?.avatar.orEmpty(),
                    assistantKind = profile?.kind ?: fan.x0.focus.data.assistant.AssistantKind.Assistant,
                    roleplayGreeting = profile?.roleplay?.greeting.orEmpty(),
                    roleplayPrompt = profile?.roleplay?.prompt.orEmpty(),
                    roleplayExamples = profile?.roleplay?.examples.orEmpty(),
                    roleplayBooks = books,
                    roleplayMemories = memories,
                    roleplayState = roleplayState,
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
            is AssistantAction.SaveRoleplayCard -> saveRoleplayCard(action)
            is AssistantAction.ImportRoleplayCard -> importRoleplayCard(action.json)
            is AssistantAction.ImportWorldBook -> importWorldBook(action.json)
            is AssistantAction.RemoveWorldBook -> removeWorldBook(action.id)
            AssistantAction.ExportRoleplayCard -> exportRoleplayCard()
            is AssistantAction.DeleteRoleplayMemory -> deleteMemory(action.id)
            is AssistantAction.CompleteRoleplayMemory -> completeMemory(action.id)
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
            load()
            sendEffect(AssistantEffect.ProfileSaved)
        }
    }

    private fun saveRoleplayCard(action: AssistantAction.SaveRoleplayCard) {
        viewModelScope.launch {
            val profile = profiles.profiles.first().firstOrNull { it.id == assistantId } ?: return@launch
            val card = (profile.roleplay ?: RoleplayCard()).copy(
                greeting = action.greeting.trim(),
                prompt = action.prompt.trim(),
                examples = action.examples.trim(),
            )
            manager.update(profile.copy(roleplay = card))
            load()
            sendEffect(AssistantEffect.ProfileSaved)
        }
    }

    private fun importRoleplayCard(raw: String) {
        viewModelScope.launch {
            val imported = SillyTavernCodec.importCharacter(raw)
            if (imported == null) {
                emitError(fan.x0.focus.core.error.AppError(fan.x0.focus.core.error.AppErrorType.Generic, serverMessage = "Could not read the character card."))
                return@launch
            }
            val profile = profiles.profiles.first().firstOrNull { it.id == assistantId } ?: return@launch
            val book = imported.book
            if (book != null) {
                roleplayWorld.saveBook(book)
                imported.entries.forEach { roleplayWorld.saveEntry(it) }
            }
            val card = imported.card.copy(
                worldBookIds = listOfNotNull(book?.id).ifEmpty { imported.card.worldBookIds },
            )
            manager.update(
                profile.copy(
                    name = imported.name.take(40),
                    role = imported.role.take(120),
                    roleplay = card,
                ),
            )
            load()
            sendEffect(AssistantEffect.ProfileSaved)
        }
    }

    private fun importWorldBook(raw: String) {
        viewModelScope.launch {
            val imported = SillyTavernCodec.importWorldBook(raw)
            if (imported == null) {
                emitError(fan.x0.focus.core.error.AppError(fan.x0.focus.core.error.AppErrorType.Generic, serverMessage = "Could not read the world book."))
                return@launch
            }
            val profile = profiles.profiles.first().firstOrNull { it.id == assistantId } ?: return@launch
            roleplayWorld.saveBook(imported.book)
            imported.entries.forEach { roleplayWorld.saveEntry(it) }
            val card = (profile.roleplay ?: RoleplayCard()).copy(
                worldBookIds = (profile.roleplay?.worldBookIds.orEmpty() + imported.book.id).distinct(),
            )
            manager.update(profile.copy(roleplay = card))
            load()
            sendEffect(AssistantEffect.ProfileSaved)
        }
    }

    private fun removeWorldBook(id: String) {
        viewModelScope.launch {
            val profile = profiles.profiles.first().firstOrNull { it.id == assistantId } ?: return@launch
            val card = (profile.roleplay ?: RoleplayCard()).copy(
                worldBookIds = profile.roleplay?.worldBookIds.orEmpty().filterNot { it == id },
            )
            manager.update(profile.copy(roleplay = card))
            load()
        }
    }

    private fun deleteMemory(id: String) {
        viewModelScope.launch {
            roleplayStore.deleteMemory(id)
            load()
        }
    }

    private fun completeMemory(id: String) {
        viewModelScope.launch {
            roleplayStore.completeMemory(id)
            load()
        }
    }

    private fun exportRoleplayCard() {
        viewModelScope.launch {
            val profile = profiles.profiles.first().firstOrNull { it.id == assistantId } ?: return@launch
            val card = profile.roleplay ?: RoleplayCard()
            val book = card.worldBookIds.firstOrNull()?.let { roleplayWorld.book(it) }
            val entries = book?.let { roleplayWorld.entries(it.id) }.orEmpty()
            val json = SillyTavernCodec.exportCharacter(profile.name, profile.role, card, book, entries)
            sendEffect(AssistantEffect.RoleplayExported(json, profile.name))
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
