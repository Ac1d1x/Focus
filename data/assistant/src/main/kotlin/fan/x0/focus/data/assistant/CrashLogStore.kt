package fan.x0.focus.data.assistant

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashLogEntry(
    val name: String,
    val time: Long,
    val size: Long,
    val preview: String,
)

@Single
class CrashLogStore(@Provided private val context: Context) {
    private val folder: File
        get() = File(context.filesDir, FOLDER).apply { mkdirs() }

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    suspend fun list(): List<CrashLogEntry> = withContext(Dispatchers.IO) {
        folder.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(PREFIX) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val text = runCatching { file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(8_192)
                    val read = input.read(buffer)
                    if (read > 0) String(buffer, 0, read) else ""
                } }.getOrDefault("")
                CrashLogEntry(
                    name = file.name,
                    time = file.lastModified(),
                    size = file.length(),
                    preview = text.lineSequence()
                        .firstOrNull { it.startsWith("exception=") }
                        ?.removePrefix("exception=")
                        ?.take(180)
                        ?: text.lineSequence().firstOrNull().orEmpty(),
                )
            }
    }

    suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        runCatching { fileFor(name).readText().take(MAX_READ_CHARS) }.getOrNull()
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        runCatching { fileFor(name).delete() }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        folder.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("unknown")
        val text = buildString {
            appendLine("time=${dateFormat.format(Date(now))}")
            appendLine("thread=${thread.name}")
            appendLine("version=$version")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("exception=${throwable.javaClass.name}: ${throwable.message.orEmpty()}")
            appendLine()
            appendLine(Log.getStackTraceString(throwable))
        }
        folder.mkdirs()
        val target = File(folder, "$PREFIX$now$SUFFIX")
        target.writeText(text)
        folder.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(PREFIX) }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_LOGS)
            .forEach { it.delete() }
    }

    private fun fileFor(name: String): File {
        require(name.startsWith(PREFIX) && name.endsWith(SUFFIX) && '/' !in name && '\\' !in name)
        return File(folder, name)
    }

    companion object {
        private const val FOLDER = "crash_logs"
        private const val PREFIX = "crash-"
        private const val SUFFIX = ".log"
        private const val MAX_LOGS = 20
        private const val MAX_READ_CHARS = 256_000
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}
