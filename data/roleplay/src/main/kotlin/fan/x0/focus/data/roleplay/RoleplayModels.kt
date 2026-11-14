package fan.x0.focus.data.roleplay

import kotlinx.serialization.Serializable

@Serializable
data class RoleplayCard(
    val greeting: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val prompt: String = "",
    val examples: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val providerId: String = "",
    val model: String = "",
    val worldBookIds: List<String> = emptyList(),
    val importedFrom: String = "",
    val extraJson: String = "",
) {
    val configured: Boolean
        get() = greeting.isNotBlank() || prompt.isNotBlank() || examples.isNotBlank()
}

@Serializable
data class RoleplayUserPersona(
    val name: String = "",
    val description: String = "",
    val avatar: String = "",
)

enum class RoleplayStatus(val wire: String) {
    Online("online"),
    Away("away"),
    Dnd("dnd"),
    ReadNoReply("read_no_reply"),
    ;

    companion object {
        fun fromWire(value: String?): RoleplayStatus =
            entries.firstOrNull { it.wire == value } ?: Online
    }
}

enum class RoleplayStage {
    Stranger,
    Acquaintance,
    Close,
    ;

    companion object {
        fun fromWire(value: String?): RoleplayStage =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Stranger
    }
}

enum class RoleplayPunctuation {
    Normal,
    Loose,
    Minimal,
}

@Serializable
data class RoleplayConfig(
    val immersive: Boolean = true,
    val splitBubbles: Boolean = true,
    val breakTags: Boolean = true,
    val readDelayMs: Int = 1_500,
    val bubbleGapMs: Int = 600,
    val paceScale: Float = 1f,
    val jitter: Float = 0.35f,
    val typing: Boolean = true,
    val stripMarkdown: Boolean = true,
    val punctuation: String = RoleplayPunctuation.Normal.name,
    val typoChance: Float = 0f,
    val recallEnabled: Boolean = true,
    val recallPerHour: Int = 4,
    val recallWindowSec: Int = 180,
    val proactive: Boolean = true,
    val maxConsecutiveProactive: Int = 3,
    val quietEnabled: Boolean = true,
    val quietStartMinute: Int = 23 * 60,
    val quietEndMinute: Int = 8 * 60,
    val urgentAllowed: Boolean = false,
    val morningGreeting: Boolean = true,
    val eveningGreeting: Boolean = true,
    val morningMinute: Int = 8 * 60 + 30,
    val eveningMinute: Int = 22 * 60,
    val icebreakDays: Int = 3,
    val checkinSilenceMinutes: Int = 90,
    val followUpChance: Float = 0.35f,
    val affectionEnabled: Boolean = true,
    val memoryEnabled: Boolean = true,
    val memoryLimit: Int = 12,
    val memoryHalfLifeDays: Int = 30,
    val worldBookEnabled: Boolean = true,
    val worldBookBudgetChars: Int = 4_000,
    val stickerEnabled: Boolean = true,
    val stickerFrequency: Float = 0.35f,
    val stickerOnly: Boolean = true,
    val allowAiSaveSticker: Boolean = false,
    val toolsEnabled: Boolean = false,
    val toolCategories: Set<String> = emptySet(),
    val showToolTrace: Boolean = false,
    val showTime: Boolean = true,
    val readReceipts: Boolean = false,
    val showStatusInChat: Boolean = true,
    val proactiveScene: String = "recent",
) {
    val punctuationStyle: RoleplayPunctuation
        get() = RoleplayPunctuation.entries.firstOrNull { it.name == punctuation } ?: RoleplayPunctuation.Normal
}

@Serializable
data class RoleplayOverride(
    val config: RoleplayConfig? = null,
    val providerId: String = "",
    val model: String = "",
    val worldBookIds: List<String>? = null,
)

@Serializable
data class RoleplayBubble(
    val text: String,
    val delayMs: Int = 0,
)

@Serializable
data class RoleplayStateView(
    val conversationId: String = "",
    val status: RoleplayStatus = RoleplayStatus.Online,
    val mood: Int = 60,
    val affection: Int = 30,
    val energy: Int = 80,
    val stage: RoleplayStage = RoleplayStage.Stranger,
    val lifeTitle: String = "",
    val typing: Boolean = false,
)

data class RoleplayPromptInput(
    val assistantName: String,
    val userName: String,
    val userDescription: String,
    val role: String,
    val card: RoleplayCard,
    val config: RoleplayConfig,
    val state: RoleplayStateView,
    val currentTime: String,
    val language: String,
    val memory: List<String> = emptyList(),
    val worldBefore: String = "",
    val worldAfter: String = "",
    val worldBottom: String = "",
    val reminders: List<String> = emptyList(),
    val stickers: String = "",
    val proactive: String = "",
    val toolsEnabled: Boolean = false,
)

data class RoleplayProactiveTask(
    val id: String,
    val conversationId: String,
    val assistantId: String,
    val type: String,
    val prompt: String,
    val fireAt: Long,
)
