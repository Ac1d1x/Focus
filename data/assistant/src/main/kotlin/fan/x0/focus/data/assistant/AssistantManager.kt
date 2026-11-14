package fan.x0.focus.data.assistant

import fan.x0.focus.data.roleplay.RoleplaySettings
import fan.x0.focus.data.roleplay.RoleplayStore
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
    private val roleplayStore: RoleplayStore,
    private val roleplaySettings: RoleplaySettings,
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

    suspend fun ensureGreeting(profile: AssistantProfile, conversationId: String? = null) {
        if (profile.kind != AssistantKind.Roleplay) return
        val greeting = profile.roleplay?.greeting?.trim().orEmpty()
        if (greeting.isBlank()) return
        val target = if (conversationId != null) {
            chats.conversation(conversationId) ?: return
        } else {
            chats.ensureMainConversation(profile.id)
        }
        if (chats.messages(target.id).isNotEmpty()) return
        chats.append(
            conversationId = target.id,
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
        if (profile.kind == AssistantKind.Roleplay) {
            val main = chats.ensureMainConversation(profile.id)
            if (main.title != profile.name) chats.rename(main.id, profile.name)
        }
        ensureGreeting(profile)
    }

    suspend fun delete(profile: AssistantProfile): Boolean {
        val all = profiles.profilesOnce()
        if (all.size <= 1) return false
        chats.conversationIds(profile.id).forEach { id ->
            sideChats.stop(id)
            turns.stop(id)
            roleplayStore.clearConversation(id)
        }
        roleplayStore.clearAssistant(profile.id)
        roleplaySettings.clearOverride(profile.id)
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
