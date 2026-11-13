package fan.x0.focus.data.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File

enum class WorkspaceFile(val fileName: String) {
    Soul("SOUL.md"),
    Persona("PERSONA.md"),
    User("USER.md"),
    Agent("AGENT.md"),
    Memory("MEMORY.md"),
    ;

    companion object {
        fun fromKey(key: String): WorkspaceFile? {
            val value = key.trim()
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                    it.fileName.equals(value, ignoreCase = true) ||
                    it.fileName.removeSuffix(".md").equals(value, ignoreCase = true)
            }
        }

        fun keys(): String = entries.joinToString(", ") { it.name.lowercase() }
    }
}

data class WorkspaceDocument(
    val file: WorkspaceFile,
    val content: String,
)

@Single
class WorkspaceStore(
    @Provided private val context: Context,
    private val profiles: AssistantProfileStore,
) {
    private val revisionCount = MutableStateFlow(0)
    private val seedLock = Mutex()

    val revision: StateFlow<Int> = revisionCount.asStateFlow()

    suspend fun seed(assistantId: String, name: String, role: String) {
        withContext(Dispatchers.IO) {
            seedLock.withLock {
                val dir = directory(assistantId)
                if (!dir.exists()) dir.mkdirs()
                WorkspaceFile.entries.forEach { file ->
                    val target = dir.resolve(file.fileName)
                    if (!target.exists()) {
                        target.writeText(defaultContent(file, name, role))
                    }
                }
            }
        }
        revisionCount.update { it + 1 }
    }

    suspend fun delete(assistantId: String) {
        withContext(Dispatchers.IO) {
            directory(assistantId).deleteRecursively()
            if (assistantId != AssistantProfile.DEFAULT_ID) {
                directory(assistantId).parentFile?.deleteRecursively()
            }
        }
    }

    suspend fun readAll(assistantId: String): List<WorkspaceDocument> = withContext(Dispatchers.IO) {
        ensureSeeded(assistantId)
        WorkspaceFile.entries.map { file ->
            WorkspaceDocument(file, fileFor(assistantId, file).readText().take(MAX_FILE_CHARS))
        }
    }

    suspend fun read(assistantId: String, file: WorkspaceFile): String = withContext(Dispatchers.IO) {
        ensureSeeded(assistantId)
        fileFor(assistantId, file).readText().take(MAX_FILE_CHARS)
    }

    suspend fun write(assistantId: String, file: WorkspaceFile, content: String) {
        withContext(Dispatchers.IO) {
            ensureSeeded(assistantId)
            writeFile(assistantId, file, content.take(MAX_FILE_CHARS))
        }
        revisionCount.update { it + 1 }
    }

    suspend fun append(assistantId: String, file: WorkspaceFile, block: String) {
        val text = block.trim()
        if (text.isBlank()) return
        withContext(Dispatchers.IO) {
            ensureSeeded(assistantId)
            val current = fileFor(assistantId, file).readText()
            val next = buildString {
                append(current.trimEnd())
                if (isNotEmpty()) append("\n\n")
                append(text)
                append('\n')
            }
            writeFile(assistantId, file, next.take(MAX_FILE_CHARS))
        }
        revisionCount.update { it + 1 }
    }

    suspend fun appendMemory(assistantId: String, fact: String) {
        val text = fact.trim()
        if (text.isBlank()) return
        append(assistantId, WorkspaceFile.Memory, "- $text")
    }

    suspend fun updateUserProfile(
        assistantId: String,
        name: String? = null,
        tone: String? = null,
        focus: String? = null,
        language: String? = null,
    ) {
        updateFields(
            assistantId = assistantId,
            file = WorkspaceFile.User,
            values = listOf(
                "Name" to name,
                "Focus" to focus,
                "Tone" to tone,
                "Language" to language,
            ),
        )
    }

    suspend fun updateAssistantName(assistantId: String, name: String) {
        val value = name.trim().take(MAX_NAME_CHARS)
        if (value.isBlank()) return
        updateFields(assistantId, WorkspaceFile.Persona, listOf("Name" to value))
        profiles.rename(assistantId, value)
    }

    suspend fun userName(assistantId: String): String? = field(assistantId, WorkspaceFile.User, "Name")

    suspend fun assistantName(assistantId: String): String {
        val fromFile = field(assistantId, WorkspaceFile.Persona, "Name")
        if (!fromFile.isNullOrBlank()) return fromFile
        return profiles.profiles.first().firstOrNull { it.id == assistantId }?.name
            ?: DEFAULT_ASSISTANT_NAME
    }

    suspend fun userLanguage(assistantId: String): String? = field(assistantId, WorkspaceFile.User, "Language")

    suspend fun field(assistantId: String, file: WorkspaceFile, label: String): String? = withContext(Dispatchers.IO) {
        ensureSeeded(assistantId)
        fieldOf(fileFor(assistantId, file).readText(), label)
    }

    private suspend fun updateFields(
        assistantId: String,
        file: WorkspaceFile,
        values: List<Pair<String, String?>>,
    ) {
        val present = values.filter { !it.second.isNullOrBlank() }
        if (present.isEmpty()) return
        withContext(Dispatchers.IO) {
            ensureSeeded(assistantId)
            var content = fileFor(assistantId, file).readText()
            if (content.isBlank()) content = "# ${file.fileName.removeSuffix(".md")}\n"
            present.forEach { (label, value) ->
                val line = "- $label: ${value!!.trim()}"
                val pattern = Regex("(?im)^\\s*[-*]?\\s*${Regex.escape(label)}\\s*:.*$")
                content = if (pattern.containsMatchIn(content)) {
                    pattern.replace(content, line)
                } else {
                    content.trimEnd() + "\n" + line + "\n"
                }
            }
            writeFile(assistantId, file, content.take(MAX_FILE_CHARS))
        }
        revisionCount.update { it + 1 }
    }

    private suspend fun ensureSeeded(assistantId: String) {
        seedLock.withLock {
            val dir = directory(assistantId)
            if (!dir.exists()) dir.mkdirs()
            val profile = profiles.profiles.first().firstOrNull { it.id == assistantId }
            WorkspaceFile.entries.forEach { file ->
                val target = dir.resolve(file.fileName)
                if (!target.exists()) {
                    target.writeText(
                        defaultContent(
                            file = file,
                            name = profile?.name ?: DEFAULT_ASSISTANT_NAME,
                            role = profile?.role.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    private fun writeFile(assistantId: String, file: WorkspaceFile, content: String) {
        val dir = directory(assistantId)
        if (!dir.exists()) dir.mkdirs()
        val target = fileFor(assistantId, file)
        val temp = dir.resolve("${file.fileName}.tmp")
        temp.writeText(content)
        if (!temp.renameTo(target)) {
            target.writeText(content)
            temp.delete()
        }
    }

    private fun directory(assistantId: String): File {
        val root = File(context.filesDir, "assistant")
        return if (assistantId == AssistantProfile.DEFAULT_ID) {
            File(root, "workspace")
        } else {
            File(File(root, "assistants"), assistantId).resolve("workspace")
        }
    }

    private fun fileFor(assistantId: String, file: WorkspaceFile): File =
        directory(assistantId).resolve(file.fileName)

    private fun defaultContent(file: WorkspaceFile, name: String, role: String): String = when (file) {
        WorkspaceFile.Soul ->
            "# Soul\n\n- Direct, calm, and specific.\n- Short answers unless the user needs detail.\n"
        WorkspaceFile.Persona ->
            buildString {
                appendLine("# Persona")
                appendLine()
                appendLine("- Name: $name")
                if (role.isNotBlank()) appendLine("- Role: $role")
                appendLine("- $name is a personal assistant on the user's phone.")
                appendLine("- Helpful first, warm second.")
            }
        WorkspaceFile.User ->
            "# User\n\n"
        WorkspaceFile.Agent ->
            "# Agent\n\n- Nothing learned yet.\n"
        WorkspaceFile.Memory ->
            "# Memory\n\n"
    }

    private fun fieldOf(content: String, label: String): String? =
        Regex("(?im)^\\s*[-*]?\\s*${Regex.escape(label)}\\s*:\\s*(.+)$")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val MAX_FILE_CHARS = 48_000
        const val PROMPT_LIMIT = 8_000
        const val DEFAULT_ASSISTANT_NAME = "Focus"
        private const val MAX_NAME_CHARS = 40
    }
}
