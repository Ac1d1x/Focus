package fan.x0.focus.data.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.network.allowLongStream
import fan.x0.focus.core.network.applyProvider
import fan.x0.focus.core.network.openai.ChatProtocol
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

class AttachmentException(message: String) : Exception(message)

@Single
class AttachmentLoader(
    @Provided private val context: Context,
    private val assistant: AssistantSettings,
    private val secrets: SecretStore,
    private val deepSeek: DeepSeekSettings,
    private val http: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(uriValue: String, providerId: String = ""): ChatAttachment? = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(uriValue) }.getOrNull() ?: return@withContext null
        val name = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("image/")) {
            val compressed = readImage(uri) ?: return@withContext null
            ChatAttachment(
                id = UUID.randomUUID().toString(),
                kind = AttachmentKind.Image,
                name = name,
                mime = compressed.first,
                base64 = compressed.second,
            )
        } else {
            val text = readText(uri)
            if (text != null) {
                ChatAttachment(
                    id = UUID.randomUUID().toString(),
                    kind = AttachmentKind.Text,
                    name = name,
                    mime = mime.ifBlank { "text/plain" },
                    text = text,
                )
            } else {
                uploadDocument(uri, name, mime, providerId)
            }
        }
    }

    private suspend fun uploadDocument(
        uri: Uri,
        name: String,
        mime: String,
        providerId: String,
    ): ChatAttachment? {
        val assistantConfig = assistant.configOnce()
        val provider = assistantConfig.providers.firstOrNull { it.id == providerId }
            ?: assistantConfig.activeProvider
            ?: return null
        if (!provider.isDeepSeek) return null
        if (!provider.model.startsWith("deepseek-flash")) {
            throw AttachmentException("DeepSeek file parsing requires the deepseek-flash model.")
        }
        val config = deepSeek.configOnce()
        if (!config.enabled || !config.files) return null
        val purpose = purposeFor(name, mime, config) ?: return null
        val url = provider.deepSeekFilesUrl()
        if (url.isBlank()) throw AttachmentException("The provider has no files endpoint.")
        val apiKey = secrets.apiKey(provider.id).orEmpty()
        if (provider.requiresKey && apiKey.isBlank()) {
            throw AttachmentException("The provider has no API key.")
        }
        val bytes = readBytes(uri) ?: throw AttachmentException("Cannot read the file.")
        if (bytes.size > MAX_UPLOAD_BYTES) {
            throw AttachmentException("The file is too large.")
        }
        val resolvedMime = mime.ifBlank { mimeFor(name) }
        val response = http.submitFormWithBinaryData(
            url = url,
            formData = formData {
                append("purpose", purpose)
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, resolvedMime)
                        append(
                            HttpHeaders.ContentDisposition,
                            "form-data; name=\"file\"; filename=\"${safeName(name)}\"",
                        )
                    },
                )
            },
        ) {
            allowLongStream()
            applyProvider(provider.requestConfig(apiKey), ChatProtocol.ChatCompletions)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val appError = NetworkException.Http(
                statusCode = response.status.value,
                error = null,
                bodySnippet = body,
            ).toAppError()
            throw AttachmentException(appError.serverMessage ?: "File upload failed.")
        }
        val id = runCatching {
            ((json.parseToJsonElement(body) as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
        if (id.isNullOrBlank()) throw AttachmentException("The file parser did not return an id.")
        return ChatAttachment(
            id = UUID.randomUUID().toString(),
            kind = AttachmentKind.Document,
            name = name,
            mime = resolvedMime,
            fileId = id,
            providerId = provider.id,
        )
    }

    private fun purposeFor(name: String, mime: String, config: DeepSeekConfig): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        val office = ext in OFFICE_EXTENSIONS ||
            mime == "application/pdf" ||
            mime.startsWith("application/msword") ||
            mime.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml") ||
            mime.startsWith("application/vnd.ms-powerpoint") ||
            mime.startsWith("application/vnd.openxmlformats-officedocument.presentationml")
        if (office) {
            return if (config.richLayout) "rich_layout_recognition" else "text_recognition"
        }
        val data = ext in DATA_EXTENSIONS ||
            mime.startsWith("text/") ||
            mime in DATA_MIME_TYPES
        return if (data) "user_data" else null
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "epub" -> "application/epub+zip"
        "mobi" -> "application/x-mobipocket-ebook"
        else -> "application/octet-stream"
    }

    private fun safeName(name: String): String =
        name.replace("\"", "").ifBlank { "file" }

    private fun readBytes(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    private fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(MAX_TEXT_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read <= 0) break
                total += read
            }
            if (total > MAX_TEXT_BYTES) return@use null
            val bytes = buffer.copyOf(total)
            if (!isProbablyText(bytes)) return@use null
            String(bytes, Charsets.UTF_8)
        }
    }.getOrNull()

    private fun isProbablyText(bytes: ByteArray): Boolean {
        val sample = bytes.take(512)
        return sample.none { it == 0.toByte() }
    }

    fun imageFromBytes(bytes: ByteArray, name: String): ChatAttachment? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val oriented = applyOrientation(decoded, orientationOf(bytes))
        val encoded = encodeJpeg(oriented)
        if (oriented !== decoded) decoded.recycle()
        oriented.recycle()
        return ChatAttachment(
            id = UUID.randomUUID().toString(),
            kind = AttachmentKind.Image,
            name = name,
            mime = encoded.first,
            base64 = encoded.second,
        )
    }

    internal fun encodeJpeg(bitmap: Bitmap): Pair<String, String> {
        val scaled = scale(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== bitmap) scaled.recycle()
        return "image/jpeg" to Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun readImage(uri: Uri): Pair<String, String>? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@runCatching null
        val oriented = applyOrientation(decoded, orientationOf(uri))
        val encoded = encodeJpeg(oriented)
        if (oriented !== decoded) decoded.recycle()
        oriented.recycle()
        encoded
    }.getOrNull()

    internal fun displaySize(bytes: ByteArray): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 0 to 0
        return if (isQuarterTurn(orientationOf(bytes))) {
            bounds.outHeight to bounds.outWidth
        } else {
            bounds.outWidth to bounds.outHeight
        }
    }

    private fun orientationOf(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun orientationOf(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    private fun isQuarterTurn(orientation: Int): Boolean = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE,
        -> true
        else -> false
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun sampleSize(width: Int, height: Int, target: Int = MAX_EDGE): Int {
        var sample = 1
        while (width / sample > target * 2 && height / sample > target * 2) {
            sample *= 2
        }
        return sample
    }

    private fun scale(bitmap: Bitmap): Bitmap = scaleTo(bitmap, MAX_EDGE)

    private fun scaleTo(bitmap: Bitmap, target: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= target) return bitmap
        val ratio = target.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    companion object {
        private const val MAX_EDGE = 1024
        private const val JPEG_QUALITY = 80
        private const val MAX_TEXT_BYTES = 512 * 1024
        private const val MAX_UPLOAD_BYTES = 32 * 1024 * 1024
        private val OFFICE_EXTENSIONS = setOf("pdf", "doc", "docx", "ppt", "pptx")
        private val DATA_EXTENSIONS = setOf(
            "xls", "xlsx", "epub", "mobi",
            "txt", "md", "csv", "json", "xml", "yaml", "yml", "html", "css",
            "js", "ts", "kt", "java", "py", "rs", "c", "cpp", "h", "sh",
        )
        private val DATA_MIME_TYPES = setOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/epub+zip",
            "application/x-mobipocket-ebook",
        )
    }
}
