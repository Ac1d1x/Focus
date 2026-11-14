package fan.x0.focus.data.roleplay

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class SillyTavernImport(
    val name: String,
    val role: String,
    val card: RoleplayCard,
    val book: RoleplayWorldBook? = null,
    val entries: List<RoleplayWorldEntry> = emptyList(),
)

data class SillyTavernWorldImport(
    val book: RoleplayWorldBook,
    val entries: List<RoleplayWorldEntry>,
)

object SillyTavernCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun importCharacter(raw: String): SillyTavernImport? {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val data = (root["data"] as? JsonObject) ?: root
        val name = data.string("name")?.trim().orEmpty().ifBlank { "Imported character" }
        val description = data.string("description").orEmpty()
        val personality = data.string("personality").orEmpty()
        val scenario = data.string("scenario").orEmpty()
        val prompt = listOf(description, personality, scenario)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val greeting = data.string("first_mes").orEmpty()
        val alternates = data.stringList("alternate_greetings")
        val examples = data.string("mes_example").orEmpty()
        val systemPrompt = data.string("system_prompt").orEmpty()
        val postHistory = data.string("post_history_instructions").orEmpty()
        val notes = data.string("creator_notes").orEmpty()
        val tags = data.stringList("tags")
        val firstLine = description.lineSequence().firstOrNull()?.trim().orEmpty()
        val (book, entries) = importCharacterBook(data["character_book"] as? JsonObject)
        return SillyTavernImport(
            name = name,
            role = firstLine.take(120),
            card = RoleplayCard(
                greeting = greeting,
                alternateGreetings = alternates,
                prompt = prompt,
                examples = examples,
                systemPrompt = systemPrompt,
                postHistoryInstructions = postHistory,
                notes = notes,
                tags = tags,
                worldBookIds = listOfNotNull(book?.id),
                importedFrom = "sillytavern",
            ),
            book = book,
            entries = entries,
        )
    }

    fun exportCharacter(
        name: String,
        role: String,
        card: RoleplayCard,
        book: RoleplayWorldBook? = null,
        entries: List<RoleplayWorldEntry> = emptyList(),
    ): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("spec", "chara_card_v2")
            put("spec_version", "2.0")
            putJsonObject("data") {
                put("name", name)
                put("description", card.prompt)
                put("personality", role)
                put("scenario", "")
                put("first_mes", card.greeting)
                putJsonArray("alternate_greetings") {
                    card.alternateGreetings.forEach { add(it) }
                }
                put("mes_example", card.examples)
                put("creator_notes", card.notes)
                put("system_prompt", card.systemPrompt)
                put("post_history_instructions", card.postHistoryInstructions)
                putJsonArray("tags") { card.tags.forEach { add(it) } }
                put("creator", "Focus")
                put("character_version", "1.0")
                putJsonObject("extensions") {}
                if (book != null) {
                    putJsonObject("character_book") {
                        put("name", book.name)
                        putJsonArray("entries") {
                            entries.forEachIndexed { index, entry ->
                                add(exportEntry(entry, index))
                            }
                        }
                        putJsonObject("extensions") {}
                    }
                }
            }
        },
    )

    fun importWorldBook(raw: String): SillyTavernWorldImport? {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val name = root.string("name")?.trim().orEmpty().ifBlank { "Imported world" }
        val book = RoleplayWorldBook(
            id = "book_${UUID.randomUUID()}",
            name = name,
            kind = RoleplayBookKind.General,
        )
        val entries = mutableListOf<RoleplayWorldEntry>()
        val rawEntries = root["entries"]
        when (rawEntries) {
            is JsonArray -> rawEntries.forEachIndexed { index, element ->
                (element as? JsonObject)?.let { entries += importEntry(it, book.id, index) }
            }
            is JsonObject -> rawEntries.values.forEachIndexed { index, element ->
                (element as? JsonObject)?.let { entries += importEntry(it, book.id, index) }
            }
            else -> Unit
        }
        return SillyTavernWorldImport(book, entries)
    }

    fun exportWorldBook(book: RoleplayWorldBook, entries: List<RoleplayWorldEntry>): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("name", book.name)
            putJsonObject("entries") {
                entries.forEachIndexed { index, entry ->
                    put(index.toString(), exportEntry(entry, index))
                }
            }
        },
    )

    private fun importCharacterBook(raw: JsonObject?): Pair<RoleplayWorldBook?, List<RoleplayWorldEntry>> {
        if (raw == null) return null to emptyList()
        val book = RoleplayWorldBook(
            id = "book_${UUID.randomUUID()}",
            name = raw.string("name")?.trim().orEmpty().ifBlank { "Character book" },
            kind = RoleplayBookKind.General,
        )
        val entries = (raw["entries"] as? JsonArray).orEmpty().mapIndexedNotNull { index, element ->
            (element as? JsonObject)?.let { importEntry(it, book.id, index) }
        }
        return book to entries
    }

    private fun importEntry(raw: JsonObject, bookId: String, index: Int): RoleplayWorldEntry {
        val extensions = raw["extensions"] as? JsonObject ?: JsonObject(emptyMap())
        val keys = raw.stringList("keys").ifEmpty { raw.stringList("key") }
        val secondary = raw.stringList("secondary_keys").ifEmpty { raw.stringList("keysecondary") }
        val selective = raw.boolean("selective")
        val logic = when (raw.int("selectiveLogic") ?: raw.int("selective_logic")) {
            1 -> RoleplayKeyLogic.NotAnySecondary
            2 -> RoleplayKeyLogic.AllSecondary
            3 -> RoleplayKeyLogic.NotAllSecondary
            else -> if (selective == false) RoleplayKeyLogic.AnySecondary else RoleplayKeyLogic.AnySecondary
        }
        val position = raw.string("position") ?: extensions.string("position")
        val mappedPosition = when (position?.lowercase()) {
            "after_char", "1", "after" -> RoleplayEntryPosition.AfterChar
            "2", "top", "top_note" -> RoleplayEntryPosition.BeforeChar
            "bottom", "3", "bottom_note" -> RoleplayEntryPosition.BottomNote
            "at_depth", "4", "depth" -> RoleplayEntryPosition.AtDepth
            else -> RoleplayEntryPosition.BeforeChar
        }
        val rawProbability = extensions.double("probability") ?: raw.double("probability") ?: 1.0
        val probability = if (rawProbability > 1.0) rawProbability / 100.0 else rawProbability
        return RoleplayWorldEntry(
            id = "entry_${UUID.randomUUID()}",
            bookId = bookId,
            comment = raw.string("comment").orEmpty(),
            content = raw.string("content").orEmpty(),
            enabled = raw.boolean("disable") != true && raw.boolean("enabled") != false,
            keys = keys,
            secondaryKeys = secondary,
            logic = logic,
            probability = probability.coerceIn(0.0, 1.0),
            constant = raw.boolean("constant") == true,
            position = mappedPosition,
            depth = raw.int("depth") ?: 4,
            role = raw.string("role") ?: extensions.string("role") ?: "system",
            group = extensions.string("group").orEmpty(),
            groupWeight = extensions.int("group_weight") ?: 100,
            priority = raw.int("insertion_order") ?: raw.int("order") ?: index,
            aiEditable = extensions.boolean("ai_editable") == true,
        )
    }

    private fun exportEntry(entry: RoleplayWorldEntry, index: Int): JsonObject = buildJsonObject {
        putJsonArray("keys") { entry.keys.forEach { add(it) } }
        put("content", entry.content)
        put("enabled", entry.enabled)
        put("insertion_order", entry.priority.takeIf { it != 0 } ?: index)
        put("comment", entry.comment)
        put("constant", entry.constant)
        put("selective", entry.secondaryKeys.isNotEmpty())
        put(
            "selectiveLogic",
            when (entry.logic) {
                RoleplayKeyLogic.AnySecondary -> 0
                RoleplayKeyLogic.NotAnySecondary -> 1
                RoleplayKeyLogic.AllSecondary -> 2
                RoleplayKeyLogic.NotAllSecondary -> 3
            },
        )
        putJsonArray("secondary_keys") { entry.secondaryKeys.forEach { add(it) } }
        put(
            "position",
            when (entry.position) {
                RoleplayEntryPosition.BeforeChar -> "before_char"
                RoleplayEntryPosition.AfterChar -> "after_char"
                RoleplayEntryPosition.BottomNote -> "bottom"
                RoleplayEntryPosition.AtDepth -> "at_depth"
            },
        )
        put("depth", entry.depth)
        put("role", entry.role)
        putJsonObject("extensions") {
            put("position", entry.position.name)
            put("probability", entry.probability)
            put("group", entry.group)
            put("group_weight", entry.groupWeight)
            put("ai_editable", entry.aiEditable)
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNullValue()

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullValue() }.orEmpty()

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNullValue()?.toIntOrNull() }

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNullValue()?.toDoubleOrNull() }

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonPrimitive.contentOrNullValue(): String? =
        if (this is JsonPrimitive) content else null
}
