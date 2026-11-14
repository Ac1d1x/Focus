package fan.x0.focus.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fan.x0.focus.data.assistant.AssistantKind
import fan.x0.focus.data.assistant.AssistantManager
import fan.x0.focus.data.assistant.AssistantProfile
import fan.x0.focus.data.assistant.AssistantProfileStore
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatSwitcher
import fan.x0.focus.data.assistant.Conversation
import fan.x0.focus.data.assistant.SideChatManager
import fan.x0.focus.data.assistant.sideChatRank
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class ShellState(
    val loading: Boolean = true,
    val assistants: List<AssistantProfile> = emptyList(),
    val activeAssistantId: String = AssistantProfile.DEFAULT_ID,
    val activeAssistant: AssistantProfile? = null,
    val main: Conversation? = null,
    val conversations: List<Conversation> = emptyList(),
    val sides: List<Conversation> = emptyList(),
    val archived: List<Conversation> = emptyList(),
) {
    val recent: List<Conversation> get() = sides.take(6)
}

@KoinViewModel
class ShellViewModel(
    private val chats: ChatStore,
    private val switcher: ChatSwitcher,
    private val sideChats: SideChatManager,
    private val profiles: AssistantProfileStore,
    private val manager: AssistantManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ShellState())
    val state: StateFlow<ShellState> = _state.asStateFlow()

    private val _created = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val created: SharedFlow<String> = _created.asSharedFlow()

    init {
        refresh()
        viewModelScope.launch {
            chats.changes.collect { load() }
        }
        viewModelScope.launch {
            combine(profiles.profiles, profiles.activeId) { assistants, activeId ->
                assistants to activeId
            }.collect { (assistants, activeId) ->
                load(assistants, activeId)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun openConversation(id: String) {
        switcher.open(id)
    }

    fun openArchivedConversation(id: String) {
        switcher.open(id)
        viewModelScope.launch {
            chats.restore(id)
            load()
        }
    }

    fun archiveConversation(id: String) {
        viewModelScope.launch {
            chats.archive(id)
            if (switcher.current.value == id) switcher.open(chats.ensureMainConversation().id)
            load()
        }
    }

    fun promoteConversation(id: String) {
        viewModelScope.launch {
            if (chats.promoteToMain(id)) {
                switcher.reload(id)
                load()
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chats.deleteConversation(id)
            load()
        }
    }

    fun newChat() {
        switcher.newChat()
    }

    fun newSideChat() {
        viewModelScope.launch {
            val conversation = sideChats.createEmpty()
            switcher.open(conversation.id)
            load()
        }
    }

    fun openMain() {
        viewModelScope.launch {
            switcher.open(chats.ensureMainConversation().id)
        }
    }

    fun selectAssistant(id: String) {
        viewModelScope.launch {
            profiles.setActive(id)
            val profile = profiles.profileOnce(id)
            if (profile != null) manager.ensureGreeting(profile)
            val main = chats.ensureMainConversation(id)
            switcher.open(main.id)
            load()
        }
    }

    fun createAssistant(kind: AssistantKind, name: String, role: String, emoji: String) {
        viewModelScope.launch {
            val profile = manager.create(name, role, emoji, kind)
            load()
            _created.tryEmit(profile.id)
        }
    }

    private suspend fun load(
        assistants: List<AssistantProfile>? = null,
        activeId: String? = null,
    ) {
        val list = assistants ?: profiles.profilesOnce()
        val id = activeId ?: profiles.activeOnce()
        val active = list.firstOrNull { it.id == id } ?: list.firstOrNull() ?: AssistantProfile(
            id = AssistantProfile.DEFAULT_ID,
            name = AssistantProfile.DEFAULT_NAME,
        )
        val main = chats.ensureMainConversation(active.id)
        val conversations = chats.conversations(assistantId = active.id)
            .filter { it.id != ChatStore.ONBOARDING_CONVERSATION_ID && !it.archived }
        val sides = chats.listedConversations(active.id)
            .filter { it.id != ChatStore.ONBOARDING_CONVERSATION_ID && it.title.isNotBlank() }
            .sortedWith(compareBy({ it.taskStatus.sideChatRank() }, { -it.updatedAt }))
        val archived = chats.conversations(archived = true, assistantId = active.id)
            .filter { it.id != ChatStore.ONBOARDING_CONVERSATION_ID && it.archived }
        _state.value = ShellState(
            loading = false,
            assistants = list,
            activeAssistantId = active.id,
            activeAssistant = active,
            main = main,
            conversations = conversations,
            sides = sides,
            archived = archived,
        )
    }

}
