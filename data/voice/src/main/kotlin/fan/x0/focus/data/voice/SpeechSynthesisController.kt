package fan.x0.focus.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.error.AppErrorType
import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.network.allowLongStream
import fan.x0.focus.core.network.applyProvider
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.ProviderConfig
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.assistant.deepSeekToolUrl
import fan.x0.focus.data.assistant.isDeepSeek
import fan.x0.focus.data.assistant.requestConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class SpeechSynthesisController(
    @Provided private val context: Context,
    private val settings: VoiceSettingsStore,
    private val assistant: AssistantSettings,
    private val secrets: SecretStore,
    private val http: HttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            scope.launch { stop() }
        }
    }

    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    val ttsConfig: StateFlow<TtsConfig> = settings.ttsConfig.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = TtsConfig(),
    )

    private var job: Job? = null
    private var session = 0
    private var systemEngine: TextToSpeech? = null
    private var pendingUtterance: CompletableDeferred<Result<Unit>>? = null
    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            pendingUtterance?.complete(Result.success(Unit))
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            pendingUtterance?.complete(Result.failure(IllegalStateException("speech engine failed")))
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            pendingUtterance?.complete(Result.failure(IllegalStateException("speech engine failed: $errorCode")))
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            pendingUtterance?.complete(Result.success(Unit))
        }
    }

    fun speak(key: String, text: String) {
        val body = text.trim()
        if (body.isBlank()) return
        stop()
        val current = ++session
        _state.value = ReadAloudState(playingKey = key, loading = true)
        job = scope.launch {
            try {
                val config = settings.ttsConfigOnce()
                val limited = body.take(MAX_CHARS)
                when (config.engine) {
                    TtsEngine.System -> speakSystem(current, key, limited)
                    TtsEngine.DeepSeek -> speakDeepSeek(current, key, config, limited)
                }
                if (session == current) {
                    _state.value = ReadAloudState()
                    abandonFocus()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (session == current) {
                    _state.value = ReadAloudState(error = e.toReadAloudError())
                    abandonFocus()
                }
            }
        }
    }

    fun stop() {
        session++
        job?.cancel()
        job = null
        pendingUtterance?.cancel()
        pendingUtterance = null
        runCatching { systemEngine?.stop() }
        releaseTrack(audioTrack)
        abandonFocus()
        _state.value = ReadAloudState()
    }

    fun clearError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    private suspend fun speakSystem(current: Int, key: String, text: String) {
        if (session != current) return
        val engine = ensureEngine()
        if (session != current) return
        requestFocus()
        val done = CompletableDeferred<Result<Unit>>()
        pendingUtterance = done
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "focus-${UUID.randomUUID()}")
        if (result != TextToSpeech.SUCCESS) {
            pendingUtterance = null
            throw ReadAloudFailure(error(R.string.voice_error_tts_engine))
        }
        if (session == current) _state.value = ReadAloudState(playingKey = key)
        done.await().getOrThrow()
        pendingUtterance = null
    }

    private suspend fun speakDeepSeek(
        current: Int,
        key: String,
        config: TtsConfig,
        text: String,
    ) {
        val provider = resolveProvider(config)
        val apiKey = secrets.apiKey(provider.id).orEmpty()
        if (provider.requiresKey && apiKey.isBlank()) {
            throw ReadAloudFailure(error(R.string.voice_error_tts_key))
        }
        val url = provider.deepSeekToolUrl("tts")
        if (url.isBlank()) throw ReadAloudFailure(error(R.string.voice_error_tts_provider))
        val body = buildJsonObject {
            put("text", text)
            put("voice_id", config.voice.id)
            put("format", "pcm")
            put("filter_markdown", true)
        }.toString()
        val response = withContext(Dispatchers.IO) {
            http.post(url) {
                allowLongStream()
                applyProvider(provider.requestConfig(apiKey), ChatProtocol.ChatCompletions)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw ReadAloudFailure(
                NetworkException.Http(
                    statusCode = response.status.value,
                    error = null,
                    bodySnippet = errorBody,
                ).toAppError(),
            )
        }
        if (session != current) return
        withContext(Dispatchers.IO) {
            requestFocus()
            val track = createTrack()
            audioTrack = track
            if (session != current) {
                releaseTrack(track)
                return@withContext
            }
            track.play()
            if (session == current) _state.value = ReadAloudState(playingKey = key)
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(PLAY_BUFFER_BYTES)
            var totalBytes = 0L
            try {
                while (isActive) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    var offset = 0
                    while (offset < read && isActive) {
                        val written = track.write(buffer, offset, read - offset)
                        if (written < 0) throw ReadAloudFailure(error(R.string.voice_error_tts_audio))
                        offset += written
                    }
                    totalBytes += read
                }
                val frames = totalBytes / 2
                while (isActive && track.playbackHeadPosition.toLong() < frames) {
                    delay(PLAYBACK_POLL_MS)
                }
            } finally {
                releaseTrack(track)
            }
        }
    }

    private suspend fun ensureEngine(): TextToSpeech {
        systemEngine?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            val created = TextToSpeech(context.applicationContext) { status ->
                if (continuation.isActive) {
                    if (status == TextToSpeech.SUCCESS && systemEngine != null) {
                        systemEngine?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        continuation.resume(systemEngine!!)
                    } else {
                        systemEngine = null
                        continuation.resumeWithException(ReadAloudFailure(error(R.string.voice_error_tts_engine)))
                    }
                }
            }
            created.setOnUtteranceProgressListener(utteranceListener)
            systemEngine = created
        }
    }

    private fun createTrack(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(
            TTS_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) throw ReadAloudFailure(error(R.string.voice_error_tts_audio))
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(TTS_SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(min * 2, TRACK_BUFFER_BYTES))
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw ReadAloudFailure(error(R.string.voice_error_tts_audio))
        }
        return track
    }

    private suspend fun resolveProvider(config: TtsConfig): ProviderConfig {
        val assistantConfig = assistant.configOnce()
        val provider = assistantConfig.providers.firstOrNull { it.id == config.providerId && it.isDeepSeek }
            ?: assistantConfig.providers.firstOrNull { it.isDeepSeek }
            ?: throw ReadAloudFailure(error(R.string.voice_error_tts_provider))
        if (provider.baseUrl.isBlank()) throw ReadAloudFailure(error(R.string.voice_error_tts_provider))
        return provider
    }

    private fun requestFocus() {
        val manager = audioManager ?: return
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        val granted = runCatching { manager.requestAudioFocus(request) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest = null
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { audioManager?.abandonAudioFocusRequest(request) }
    }

    private fun releaseTrack(track: AudioTrack?) {
        if (track == null) return
        runCatching { track.stop() }
        runCatching { track.release() }
        if (audioTrack === track) audioTrack = null
    }

    private fun error(resId: Int): AppError = AppError(
        type = AppErrorType.Generic,
        serverMessage = context.getString(resId),
    )

    private fun Throwable.toReadAloudError(): AppError = when (this) {
        is ReadAloudFailure -> this.error
        else -> toAppError()
    }

    private class ReadAloudFailure(val error: AppError) : Exception()

    private companion object {
        const val MAX_CHARS = 10_000
        const val TTS_SAMPLE_RATE = 24_000
        const val PLAY_BUFFER_BYTES = 16 * 1024
        const val TRACK_BUFFER_BYTES = 64 * 1024
        const val PLAYBACK_POLL_MS = 30L
    }
}
