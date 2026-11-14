package fan.x0.focus.data.roleplay

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class RoleplayDatabase(@Provided context: Context) : SQLiteOpenHelper(context, "roleplay.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE roleplay_states(
                conversation_id TEXT PRIMARY KEY,
                status TEXT NOT NULL DEFAULT 'online',
                mood REAL NOT NULL DEFAULT 60,
                affection REAL NOT NULL DEFAULT 30,
                energy REAL NOT NULL DEFAULT 80,
                stage TEXT NOT NULL DEFAULT 'Stranger',
                user_msgs INTEGER NOT NULL DEFAULT 0,
                ai_msgs INTEGER NOT NULL DEFAULT 0,
                interaction_min INTEGER NOT NULL DEFAULT 0,
                first_at INTEGER NOT NULL DEFAULT 0,
                last_user_at INTEGER NOT NULL DEFAULT 0,
                last_ai_at INTEGER NOT NULL DEFAULT 0,
                last_tick_at INTEGER NOT NULL DEFAULT 0,
                consecutive_proactive INTEGER NOT NULL DEFAULT 0,
                turn INTEGER NOT NULL DEFAULT 0,
                recall_stamps TEXT NOT NULL DEFAULT '[]',
                life TEXT NOT NULL DEFAULT '[]',
                feeling_log TEXT NOT NULL DEFAULT '[]'
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE roleplay_memories(
                id TEXT PRIMARY KEY,
                assistant_id TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'Event',
                content TEXT NOT NULL,
                weight REAL NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                last_access INTEGER NOT NULL,
                forgotten INTEGER NOT NULL DEFAULT 0,
                due_at INTEGER NOT NULL DEFAULT 0,
                done INTEGER NOT NULL DEFAULT 0,
                reminded INTEGER NOT NULL DEFAULT 0,
                decayed_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_roleplay_memories_assistant ON roleplay_memories(assistant_id, forgotten, done)")
        db.execSQL(
            """
            CREATE TABLE roleplay_world_books(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                kind TEXT NOT NULL DEFAULT 'general',
                enabled INTEGER NOT NULL DEFAULT 1,
                budget_chars INTEGER NOT NULL DEFAULT 4000,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE roleplay_world_entries(
                id TEXT PRIMARY KEY,
                book_id TEXT NOT NULL,
                comment TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL DEFAULT 1,
                keys_json TEXT NOT NULL DEFAULT '[]',
                secondary_keys_json TEXT NOT NULL DEFAULT '[]',
                logic TEXT NOT NULL DEFAULT 'anySecondary',
                probability REAL NOT NULL DEFAULT 1,
                constant INTEGER NOT NULL DEFAULT 0,
                position TEXT NOT NULL DEFAULT 'beforeChar',
                depth INTEGER NOT NULL DEFAULT 4,
                role TEXT NOT NULL DEFAULT 'system',
                group_name TEXT NOT NULL DEFAULT '',
                group_weight INTEGER NOT NULL DEFAULT 100,
                priority INTEGER NOT NULL DEFAULT 0,
                ai_editable INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_roleplay_world_entries_book ON roleplay_world_entries(book_id)")
        db.execSQL(
            """
            CREATE TABLE roleplay_stickers(
                id TEXT PRIMARY KEY,
                kind TEXT NOT NULL DEFAULT 'emoji',
                value TEXT NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                tags_json TEXT NOT NULL DEFAULT '[]',
                emotion TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT 'General',
                favorite INTEGER NOT NULL DEFAULT 0,
                uses INTEGER NOT NULL DEFAULT 0,
                last_used INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL DEFAULT 'user',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE roleplay_tasks(
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                assistant_id TEXT NOT NULL,
                fire_at INTEGER NOT NULL,
                prompt TEXT NOT NULL DEFAULT '',
                type TEXT NOT NULL DEFAULT 'custom',
                condition TEXT NOT NULL DEFAULT 'always',
                status TEXT NOT NULL DEFAULT 'pending',
                urgent INTEGER NOT NULL DEFAULT 0,
                note TEXT NOT NULL DEFAULT '',
                tag TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                fired_at INTEGER NOT NULL DEFAULT 0,
                failures INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_roleplay_tasks_due ON roleplay_tasks(status, fire_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
