package fan.x0.focus.data.roleplay

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

@Single
class RoleplayWorldStore(private val database: RoleplayDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val stringsSerializer = ListSerializer(String.serializer())

    suspend fun books(): List<RoleplayWorldBook> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $BOOK_COLUMNS FROM roleplay_world_books ORDER BY updated_at DESC",
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toBook()) }
        }
    }

    suspend fun books(ids: List<String>): List<RoleplayWorldBook> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        database.readableDatabase.rawQuery(
            "SELECT $BOOK_COLUMNS FROM roleplay_world_books WHERE id IN ($placeholders)",
            ids.toTypedArray(),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toBook()) }
        }
    }

    suspend fun book(id: String): RoleplayWorldBook? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $BOOK_COLUMNS FROM roleplay_world_books WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toBook() else null }
    }

    suspend fun saveBook(book: RoleplayWorldBook): RoleplayWorldBook = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val next = book.copy(updatedAt = now, createdAt = if (book.createdAt == 0L) now else book.createdAt)
        val values = ContentValues().apply {
            put("id", next.id)
            put("name", next.name)
            put("kind", next.kind.name)
            put("enabled", if (next.enabled) 1 else 0)
            put("budget_chars", next.budgetChars)
            put("created_at", next.createdAt)
            put("updated_at", next.updatedAt)
        }
        database.writableDatabase.insertWithOnConflict(
            "roleplay_world_books",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        next
    }

    suspend fun deleteBook(id: String): Boolean = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("roleplay_world_entries", "book_id = ?", arrayOf(id))
        database.writableDatabase.delete("roleplay_world_books", "id = ?", arrayOf(id)) > 0
    }

    suspend fun entries(bookId: String): List<RoleplayWorldEntry> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $ENTRY_COLUMNS FROM roleplay_world_entries WHERE book_id = ? ORDER BY priority DESC",
            arrayOf(bookId),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toEntry()) }
        }
    }

    suspend fun saveEntry(entry: RoleplayWorldEntry): RoleplayWorldEntry = withContext(Dispatchers.IO) {
        val next = if (entry.id.isBlank()) entry.copy(id = "entry_${UUID.randomUUID()}") else entry
        val values = ContentValues().apply {
            put("id", next.id)
            put("book_id", next.bookId)
            put("comment", next.comment)
            put("content", next.content)
            put("enabled", if (next.enabled) 1 else 0)
            put("keys_json", json.encodeToString(stringsSerializer, next.keys))
            put("secondary_keys_json", json.encodeToString(stringsSerializer, next.secondaryKeys))
            put("logic", next.logic.name)
            put("probability", next.probability)
            put("constant", if (next.constant) 1 else 0)
            put("position", next.position.name)
            put("depth", next.depth)
            put("role", next.role)
            put("group_name", next.group)
            put("group_weight", next.groupWeight)
            put("priority", next.priority)
            put("ai_editable", if (next.aiEditable) 1 else 0)
        }
        database.writableDatabase.insertWithOnConflict(
            "roleplay_world_entries",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        next
    }

    suspend fun deleteEntry(id: String): Boolean = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("roleplay_world_entries", "id = ?", arrayOf(id)) > 0
    }

    suspend fun countEntries(bookId: String): Int = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM roleplay_world_entries WHERE book_id = ?",
            arrayOf(bookId),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    suspend fun context(
        books: List<RoleplayWorldBook>,
        recentMessages: List<String>,
        budgetChars: Int = 4_000,
        random: Random = Random.Default,
    ): RoleplayWorldContext = withContext(Dispatchers.IO) {
        val text = recentMessages.joinToString("\n")
        val hits = mutableListOf<Pair<RoleplayWorldBook, RoleplayWorldEntry>>()
        books.filter { it.enabled }.forEach { book ->
            val candidates = entries(book.id)
                .filter { it.enabled && it.content.isNotBlank() }
                .filter { entry -> entry.constant || fires(entry, text) }
                .filter { entry -> entry.constant || entry.probability >= 1.0 || random.nextDouble() < entry.probability }
            hits += pickGroups(candidates).map { book to it }
        }
        val kept = mutableListOf<Pair<RoleplayWorldBook, RoleplayWorldEntry>>()
        var spent = 0
        hits.sortedByDescending { it.second.priority }.forEach { hit ->
            val cost = hit.second.content.length + hit.second.comment.length + 8
            if (hit.second.constant || spent + cost <= budgetChars) {
                kept += hit
                spent += cost
            }
        }
        val before = kept.filter { it.second.position == RoleplayEntryPosition.BeforeChar }
            .joinToString("\n\n") { block(it.first, it.second) }
        val after = kept.filter { it.second.position == RoleplayEntryPosition.AfterChar }
            .joinToString("\n\n") { block(it.first, it.second) }
        val bottom = kept.filter { it.second.position == RoleplayEntryPosition.BottomNote || it.second.position == RoleplayEntryPosition.AtDepth }
            .joinToString("\n\n") { block(it.first, it.second) }
        RoleplayWorldContext(
            beforeChar = before,
            afterChar = after,
            bottomNote = bottom,
        )
    }

    private fun fires(entry: RoleplayWorldEntry, text: String): Boolean {
        val primary = entry.keys.filter { it.isNotBlank() }
        if (primary.isEmpty()) return false
        if (primary.none { text.contains(it, ignoreCase = true) }) return false
        val secondary = entry.secondaryKeys.filter { it.isNotBlank() }
        if (secondary.isEmpty()) return true
        val any = secondary.any { text.contains(it, ignoreCase = true) }
        val all = secondary.all { text.contains(it, ignoreCase = true) }
        return when (entry.logic) {
            RoleplayKeyLogic.AnySecondary -> any
            RoleplayKeyLogic.NotAnySecondary -> !any
            RoleplayKeyLogic.AllSecondary -> all
            RoleplayKeyLogic.NotAllSecondary -> !all
        }
    }

    private fun pickGroups(entries: List<RoleplayWorldEntry>): List<RoleplayWorldEntry> {
        val grouped = entries.filter { it.group.isNotBlank() }
        if (grouped.size <= 1) return entries
        val winners = grouped.groupBy { it.group }.mapValues { (_, list) ->
            list.maxWithOrNull(compareBy<RoleplayWorldEntry> { it.groupWeight }.thenBy { it.priority })
        }
        val keep = winners.values.filterNotNull().toSet()
        return entries.filter { it.group.isBlank() || keep.contains(it) }
    }

    private fun block(book: RoleplayWorldBook, entry: RoleplayWorldEntry): String = buildString {
        append("## ")
        append(book.name)
        if (entry.comment.isNotBlank()) {
            append(" / ")
            append(entry.comment)
        }
        append('\n')
        append(entry.content.trim())
    }

    private fun Cursor.toBook(): RoleplayWorldBook = RoleplayWorldBook(
        id = getString(0),
        name = getString(1),
        kind = RoleplayBookKind.fromWire(getString(2)),
        enabled = getInt(3) != 0,
        budgetChars = getInt(4),
        createdAt = getLong(5),
        updatedAt = getLong(6),
    )

    private fun Cursor.toEntry(): RoleplayWorldEntry = RoleplayWorldEntry(
        id = getString(0),
        bookId = getString(1),
        comment = getString(2).orEmpty(),
        content = getString(3).orEmpty(),
        enabled = getInt(4) != 0,
        keys = runCatching { json.decodeFromString(stringsSerializer, getString(5).orEmpty()) }.getOrDefault(emptyList()),
        secondaryKeys = runCatching { json.decodeFromString(stringsSerializer, getString(6).orEmpty()) }.getOrDefault(emptyList()),
        logic = RoleplayKeyLogic.fromWire(getString(7)),
        probability = getDouble(8),
        constant = getInt(9) != 0,
        position = RoleplayEntryPosition.fromWire(getString(10)),
        depth = getInt(11),
        role = getString(12).orEmpty(),
        group = getString(13).orEmpty(),
        groupWeight = getInt(14),
        priority = getInt(15),
        aiEditable = getInt(16) != 0,
    )

    private companion object {
        const val BOOK_COLUMNS = "id, name, kind, enabled, budget_chars, created_at, updated_at"
        const val ENTRY_COLUMNS =
            "id, book_id, comment, content, enabled, keys_json, secondary_keys_json, logic, " +
                "probability, constant, position, depth, role, group_name, group_weight, priority, ai_editable"
    }
}
