package fan.x0.focus.data.assistant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object RoleplayToolCatalog {
    const val ADJUST_FEELING = "adjust_feeling"
    const val SET_STATUS = "set_status"
    const val WRITE_MEMORY = "write_memory"
    const val READ_MEMORY = "read_memory"
    const val COMPLETE_TODO = "complete_todo"
    const val SCHEDULE_MESSAGE = "schedule_message"
    const val CANCEL_SCHEDULED = "cancel_scheduled"
    const val MODIFY_SCHEDULED = "modify_scheduled"
    const val LIST_SCHEDULED = "list_scheduled"
    const val SEND_STICKER = "send_sticker"
    const val SEND_TYPO = "send_typo"
    const val RECALL_MESSAGE = "recall_message"

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            name = ADJUST_FEELING,
            description = "Change your own mood, affection or energy by a delta between -30 and 30, for example after praise, an insult, a long day, or a tender moment.",
            kind = ToolKind.Write,
            parameters = schema {
                put("mood", number("Mood delta, -30 to 30"))
                put("affection", number("Affection delta, -30 to 30"))
                put("energy", number("Energy delta, -30 to 30"))
                put("reason", string("Short reason for the change"))
            },
        ),
        ToolSpec(
            name = SET_STATUS,
            description = "Set your presence shown in the chat. Use read_no_reply when you read the user's message and deliberately do not answer.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("status")) {
                put("status", string("online, away, dnd, or read_no_reply"))
            },
        ),
        ToolSpec(
            name = WRITE_MEMORY,
            description = "Store something about the user or your shared life. Type is preference, event, promise, todo, or conflict. Promises and todos may carry a due time.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("content")) {
                put("type", string("preference, event, promise, todo, or conflict. Default event."))
                put("content", string("The fact to remember, one short sentence"))
                put("due_at", string("Optional ISO time for a promise or todo"))
            },
        ),
        ToolSpec(
            name = READ_MEMORY,
            description = "Search your long term memory.",
            kind = ToolKind.Read,
            parameters = schema {
                put("query", string("Words to look for"))
                put("type", string("Optional preference, event, promise, todo, or conflict"))
                put("limit", number("Maximum entries, default 10"))
            },
        ),
        ToolSpec(
            name = COMPLETE_TODO,
            description = "Mark a todo or promise as finished.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id")) {
                put("id", string("Memory id from the prompt or read_memory"))
            },
        ),
        ToolSpec(
            name = SCHEDULE_MESSAGE,
            description = "Schedule a message for later. The prompt is a note to yourself, not the final text. Use it for check-ins, reminders and follow-ups.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("prompt")) {
                put("prompt", string("What you want to say or do when it fires"))
                put("delay_ms", number("Delay in milliseconds. Use this or at."))
                put("at", string("ISO time. Use this or delay_ms."))
                put("type", string("greeting, checkin, followup, reminder, icebreak, or custom"))
                put("condition", string("always or user_silent"))
                put("urgent", boolean("May fire during quiet hours"))
            },
        ),
        ToolSpec(
            name = CANCEL_SCHEDULED,
            description = "Cancel a scheduled message.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id")) {
                put("id", string("Task id from list_scheduled"))
            },
        ),
        ToolSpec(
            name = MODIFY_SCHEDULED,
            description = "Change the time or prompt of a scheduled message. This also keeps a parked task alive.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id")) {
                put("id", string("Task id from list_scheduled"))
                put("delay_ms", number("New delay in milliseconds"))
                put("at", string("New ISO time"))
                put("prompt", string("New note"))
                put("condition", string("always or user_silent"))
            },
        ),
        ToolSpec(
            name = LIST_SCHEDULED,
            description = "List the messages you have scheduled.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = SEND_STICKER,
            description = "Send a sticker from the library. Give an id, or an emotion and one is chosen. Only send one that genuinely fits this exact moment.",
            kind = ToolKind.Write,
            parameters = schema {
                put("id", string("Sticker id, optional"))
                put("emotion", string("One emotion word, optional"))
            },
        ),
        ToolSpec(
            name = SEND_TYPO,
            description = "Send a message that contains a deliberate typo, as its own bubble. Afterwards call recall_message with a keyword and then send the corrected text. Use this rarely.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("text")) {
                put("text", string("The text including the wrong character"))
            },
        ),
        ToolSpec(
            name = RECALL_MESSAGE,
            description = "Recall one of your delivered messages from the last few minutes. The keyword is matched fuzzily. Use delay_ms to wait before recalling.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("keyword")) {
                put("keyword", string("A word or phrase from the message to recall"))
                put("delay_ms", number("Wait this long before recalling, optional"))
            },
        ),
    )

    private val byName = specs.associateBy { it.name }

    fun find(name: String): ToolSpec? = byName[name]

    fun chatSpecs(): List<ToolSpec> = specs + listOfNotNull(
        ToolCatalog.find("ask"),
        ToolCatalog.find("react_to_message"),
        ToolCatalog.find(ToolCatalog.STAY_SILENT),
    )

    private fun schema(required: List<String> = emptyList(), props: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties", props)
            if (required.isNotEmpty()) {
                putJsonArray("required") { required.forEach { add(it) } }
            }
            put("additionalProperties", false)
        }

    private fun string(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun number(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun boolean(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }
}
