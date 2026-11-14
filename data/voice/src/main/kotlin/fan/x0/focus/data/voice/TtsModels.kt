package fan.x0.focus.data.voice

import fan.x0.focus.core.error.AppError

enum class TtsEngine {
    System,
    DeepSeek,
}

enum class TtsVoice(val id: String) {
    Stella("stella"),
    Tide("tide"),
    ;

    companion object {
        fun fromId(value: String?): TtsVoice =
            entries.firstOrNull { it.id == value } ?: Stella
    }
}

data class TtsConfig(
    val engine: TtsEngine = TtsEngine.System,
    val providerId: String = "",
    val voice: TtsVoice = TtsVoice.Stella,
    val autoRead: Boolean = false,
)

data class ReadAloudState(
    val playingKey: String? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
)
