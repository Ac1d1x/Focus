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

data class RoleplaySticker(
    val id: String,
    val kind: String = "emoji",
    val value: String,
    val name: String = "",
    val tags: List<String> = emptyList(),
    val emotion: String = "",
    val category: String = "General",
    val favorite: Boolean = false,
    val uses: Int = 0,
    val lastUsed: Long = 0,
    val source: String = "user",
    val createdAt: Long = 0,
)

enum class RoleplayBookKind {
    General,
    Facts,
    Rules,
    Style,
    Relations,
    Scenes,
    ;

    companion object {
        fun fromWire(value: String?): RoleplayBookKind =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: General
    }
}

enum class RoleplayEntryPosition {
    BeforeChar,
    AfterChar,
    BottomNote,
    AtDepth,
    ;

    companion object {
        fun fromWire(value: String?): RoleplayEntryPosition =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BeforeChar
    }
}

enum class RoleplayKeyLogic {
    AnySecondary,
    NotAnySecondary,
    AllSecondary,
    NotAllSecondary,
    ;

    companion object {
        fun fromWire(value: String?): RoleplayKeyLogic =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AnySecondary
    }
}

data class RoleplayWorldBook(
    val id: String,
    val name: String,
    val kind: RoleplayBookKind = RoleplayBookKind.General,
    val enabled: Boolean = true,
    val budgetChars: Int = 4_000,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class RoleplayWorldEntry(
    val id: String,
    val bookId: String,
    val comment: String = "",
    val content: String = "",
    val enabled: Boolean = true,
    val keys: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val logic: RoleplayKeyLogic = RoleplayKeyLogic.AnySecondary,
    val probability: Double = 1.0,
    val constant: Boolean = false,
    val position: RoleplayEntryPosition = RoleplayEntryPosition.BeforeChar,
    val depth: Int = 4,
    val role: String = "system",
    val group: String = "",
    val groupWeight: Int = 100,
    val priority: Int = 0,
    val aiEditable: Boolean = false,
)

data class RoleplayWorldContext(
    val beforeChar: String = "",
    val afterChar: String = "",
    val bottomNote: String = "",
    val atDepth: List<Pair<Int, String>> = emptyList(),
)

@Serializable
data class RoleplayLifeEntry(
    val id: String,
    val title: String,
    val start: Long,
    val end: Long,
    val kind: String = "busy",
    val by: String = "ai",
    val announced: Boolean = false,
)

enum class RoleplayMemoryType {
    Preference,
    Event,
    Promise,
    Todo,
    Conflict,
    ;

    companion object {
        fun fromWire(value: String?): RoleplayMemoryType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Event
    }
}

data class RoleplayMemory(
    val id: String,
    val assistantId: String,
    val type: RoleplayMemoryType,
    val content: String,
    val weight: Double = 1.0,
    val createdAt: Long = 0,
    val lastAccess: Long = 0,
    val forgotten: Boolean = false,
    val dueAt: Long = 0,
    val done: Boolean = false,
    val reminded: Boolean = false,
    val decayedAt: Long = 0,
) {
    val sticky: Boolean
        get() = (type == RoleplayMemoryType.Promise || type == RoleplayMemoryType.Todo) && !done
}

data class RoleplayState(
    val conversationId: String,
    val status: RoleplayStatus = RoleplayStatus.Online,
    val mood: Double = 60.0,
    val affection: Double = 30.0,
    val energy: Double = 80.0,
    val stage: RoleplayStage = RoleplayStage.Stranger,
    val userMsgs: Int = 0,
    val aiMsgs: Int = 0,
    val interactionMin: Int = 0,
    val firstAt: Long = 0,
    val lastUserAt: Long = 0,
    val lastAiAt: Long = 0,
    val lastTickAt: Long = 0,
    val consecutiveProactive: Int = 0,
    val turn: Int = 0,
    val recallStamps: List<Long> = emptyList(),
    val life: List<RoleplayLifeEntry> = emptyList(),
    val feelingLog: List<String> = emptyList(),
) {
    fun activeLife(now: Long): RoleplayLifeEntry? =
        life.firstOrNull { now >= it.start && now < it.end }

    fun effectiveStatus(now: Long): RoleplayStatus {
        if (status == RoleplayStatus.Dnd) return RoleplayStatus.Dnd
        val entry = activeLife(now)
        if (entry != null) {
            return if (entry.kind == "rest") RoleplayStatus.Dnd else RoleplayStatus.Away
        }
        return status
    }

    fun view(now: Long = System.currentTimeMillis()): RoleplayStateView =
        RoleplayStateView(
            conversationId = conversationId,
            status = effectiveStatus(now),
            mood = mood.toInt().coerceIn(0, 100),
            affection = affection.toInt().coerceIn(0, 100),
            energy = energy.toInt().coerceIn(0, 100),
            stage = stage,
            lifeTitle = activeLife(now)?.title.orEmpty(),
        )
}

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

data class RoleplayTask(
    val id: String,
    val conversationId: String,
    val assistantId: String,
    val fireAt: Long,
    val prompt: String,
    val type: String = "custom",
    val condition: String = "always",
    val status: String = "pending",
    val urgent: Boolean = false,
    val note: String = "",
    val tag: String = "",
    val createdAt: Long = 0,
    val firedAt: Long = 0,
    val failures: Int = 0,
) {
    val open: Boolean
        get() = status == "pending" || status == "suspended"
}
