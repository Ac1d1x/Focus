package fan.x0.focus.data.assistant

import org.koin.core.annotation.Single

@Single
class AssistantManager(
    private val profiles: AssistantProfileStore,
    private val workspace: WorkspaceStore,
    private val prompts: PromptSettings,
    private val chats: ChatStore,
    private val sideChats: SideChatManager,
    private val turns: ChatTurnRunner,
    private val switcher: ChatSwitcher,
) {
    suspend fun create(name: String, role: String, emoji: String): AssistantProfile {
        val profile = profiles.create(name, role, emoji)
        workspace.seed(profile.id, profile.name, profile.role)
        val main = chats.ensureMainConversation(profile.id)
        switcher.reload(main.id)
        return profile
    }

    suspend fun update(profile: AssistantProfile) {
        profiles.update(profile)
        workspace.updateAssistantName(profile.id, profile.name)
    }

    suspend fun delete(profile: AssistantProfile): Boolean {
        val all = profiles.profilesOnce()
        if (all.size <= 1) return false
        chats.conversationIds(profile.id).forEach { id ->
            sideChats.stop(id)
            turns.stop(id)
        }
        profiles.delete(profile.id)
        workspace.delete(profile.id)
        prompts.clear(profile.id)
        chats.deleteAssistantData(profile.id)
        val main = chats.ensureMainConversation()
        switcher.switchQuietly(main.id)
        return true
    }
}
