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
    private val avatars: AssistantAvatarStore,
) {
    suspend fun create(
        name: String,
        role: String,
        emoji: String,
        kind: AssistantKind = AssistantKind.Assistant,
        roleplay: fan.x0.focus.data.roleplay.RoleplayCard? = null,
    ): AssistantProfile {
        val profile = profiles.create(name, role, emoji, kind, roleplay)
        workspace.seed(profile.id, profile.name, profile.role)
        val main = chats.ensureMainConversation(profile.id)
        ensureGreeting(profile)
        switcher.reload(main.id)
        return profile
    }

    suspend fun ensureGreeting(profile: AssistantProfile) {
        if (profile.kind != AssistantKind.Roleplay) return
        val greeting = profile.roleplay?.greeting?.trim().orEmpty()
        if (greeting.isBlank()) return
        val main = chats.ensureMainConversation(profile.id)
        if (chats.messages(main.id).isNotEmpty()) return
        chats.append(
            conversationId = main.id,
            role = ChatRole.Assistant,
            content = greeting,
            label = "greeting",
            kind = "text",
        )
    }

    suspend fun update(profile: AssistantProfile) {
        val previous = profiles.profilesOnce().firstOrNull { it.id == profile.id }
        profiles.update(profile)
        if (previous != null && previous.avatar != profile.avatar && previous.avatar.isNotBlank()) {
            avatars.delete(previous.avatar)
        }
        workspace.updateAssistantName(profile.id, profile.name)
        ensureGreeting(profile)
    }

    suspend fun delete(profile: AssistantProfile): Boolean {
        val all = profiles.profilesOnce()
        if (all.size <= 1) return false
        chats.conversationIds(profile.id).forEach { id ->
            sideChats.stop(id)
            turns.stop(id)
        }
        profiles.delete(profile.id)
        if (profile.avatar.isNotBlank()) avatars.delete(profile.avatar)
        workspace.delete(profile.id)
        prompts.clear(profile.id)
        chats.deleteAssistantData(profile.id)
        val main = chats.ensureMainConversation()
        switcher.switchQuietly(main.id)
        return true
    }
}
