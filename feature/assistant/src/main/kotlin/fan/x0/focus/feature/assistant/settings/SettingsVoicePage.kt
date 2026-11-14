package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.designsystem.kit.BtnKind
import fan.x0.focus.core.designsystem.kit.ExButton
import fan.x0.focus.core.designsystem.kit.ExChoiceRow
import fan.x0.focus.core.designsystem.kit.ExField
import fan.x0.focus.core.designsystem.kit.ExGroup
import fan.x0.focus.core.designsystem.kit.ExPage
import fan.x0.focus.core.designsystem.kit.ExRow
import fan.x0.focus.core.designsystem.kit.ExSwitchRow
import fan.x0.focus.core.designsystem.kit.Ic
import fan.x0.focus.core.designsystem.kit.group
import fan.x0.focus.data.voice.LocalModelState
import fan.x0.focus.data.voice.SpeechSynthesisController
import fan.x0.focus.data.voice.TtsEngine
import fan.x0.focus.data.voice.TtsVoice
import fan.x0.focus.data.voice.VoiceEngine
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.VoiceSettingsAction
import fan.x0.focus.feature.assistant.VoiceSettingsUiState
import fan.x0.focus.feature.assistant.VoiceSettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsVoiceRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: VoiceSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsVoicePage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsVoicePage(
    state: VoiceSettingsUiState,
    onAction: (VoiceSettingsAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val local = stringResource(R.string.settings_voice_engine_local)
    val cloud = stringResource(R.string.settings_voice_engine_cloud)
    val localTitle = stringResource(R.string.settings_voice_local)
    val cloudTitle = stringResource(R.string.settings_voice_cloud)
    val recordTitle = stringResource(R.string.settings_voice_record)
    val autoStopTitle = stringResource(R.string.settings_voice_auto_stop)
    val autoStopBody = stringResource(R.string.settings_voice_auto_stop_body)
    val autoStopTime = stringResource(R.string.settings_voice_auto_stop_time)
    val silenceOptions = listOf(2, 3, 4, 5, 8, 10)
    val silenceLabels = silenceOptions.associateWith {
        stringResource(R.string.settings_voice_auto_stop_seconds, it)
    }
    val silenceSelected = state.config.silenceSeconds.takeIf { it in silenceOptions } ?: 3
    val ttsTitle = stringResource(R.string.settings_voice_tts)
    val systemTts = stringResource(R.string.settings_voice_tts_system)
    val deepSeekTts = stringResource(R.string.settings_voice_tts_deepseek)
    val stella = stringResource(R.string.settings_voice_tts_voice_stella)
    val tide = stringResource(R.string.settings_voice_tts_voice_tide)
    val tts: SpeechSynthesisController = koinInject()
    val sample = stringResource(R.string.settings_voice_tts_sample)
    val cloudProviderId = state.config.cloudProviderId.ifBlank { state.activeProviderId }
    val deepSeekAsr = state.providers.firstOrNull { it.id == cloudProviderId }?.deepSeek == true
    DisposableEffect(tts) {
        onDispose { tts.stop() }
    }
    ExPage(
        title = stringResource(R.string.settings_voice_title),
        subtitle = stringResource(R.string.settings_voice_body),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item {
            ExGroup {
                ExChoiceRow(
                    title = stringResource(R.string.settings_voice_engine),
                    options = VoiceEngine.entries,
                    selected = state.config.engine,
                    onSelect = { onAction(VoiceSettingsAction.SetEngine(it)) },
                    icon = Ic.Mic,
                ) {
                    when (it) {
                        VoiceEngine.Local -> local
                        VoiceEngine.Cloud -> cloud
                    }
                }
            }
        }
        if (state.config.engine == VoiceEngine.Local) {
            group(localTitle) {
                VoiceLocalRow(
                    state = state.model,
                    onDownload = { onAction(VoiceSettingsAction.DownloadModel) },
                    onDelete = { onAction(VoiceSettingsAction.DeleteModel) },
                    onRefresh = { onAction(VoiceSettingsAction.RefreshModel) },
                )
            }
        }
        if (state.config.engine == VoiceEngine.Cloud) {
            group(cloudTitle) {
                if (state.providers.isEmpty()) {
                    ExRow(
                        title = stringResource(R.string.settings_voice_cloud_no_provider),
                        icon = Ic.Mic,
                    )
                } else {
                    val providers = state.providers
                    val labels = providers.associate { it.id to it.name }
                    ExChoiceRow(
                        title = stringResource(R.string.settings_voice_cloud_provider),
                        options = providers.map { it.id },
                        selected = cloudProviderId,
                        onSelect = { onAction(VoiceSettingsAction.SetCloudProvider(it)) },
                        icon = Ic.Cloud,
                    ) { labels[it].orEmpty() }
                }
                if (deepSeekAsr) {
                    ExRow(
                        title = stringResource(R.string.settings_voice_cloud_deepseek),
                        subtitle = stringResource(R.string.settings_voice_cloud_deepseek_note),
                        icon = Ic.Cloud,
                    )
                } else {
                    ExRow(
                        title = stringResource(R.string.settings_voice_cloud_model),
                        icon = Ic.Wave,
                        below = {
                            ExField(
                                value = state.config.cloudModel,
                                onChange = { onAction(VoiceSettingsAction.SetCloudModel(it)) },
                                label = stringResource(R.string.settings_voice_cloud_model),
                                mono = true,
                            )
                            Text(
                                text = stringResource(R.string.settings_voice_cloud_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        },
                    )
                }
            }
        }
        group(recordTitle) {
            ExSwitchRow(
                title = autoStopTitle,
                subtitle = autoStopBody,
                checked = state.config.autoStop,
                onChange = { onAction(VoiceSettingsAction.SetAutoStop(it)) },
                icon = Ic.Timer,
            )
            if (state.config.autoStop) {
                ExChoiceRow(
                    title = autoStopTime,
                    options = silenceOptions,
                    selected = silenceSelected,
                    onSelect = { onAction(VoiceSettingsAction.SetSilenceSeconds(it)) },
                    icon = Ic.Timer,
                ) { silenceLabels[it].orEmpty() }
            }
        }
        group(ttsTitle) {
            ExChoiceRow(
                title = stringResource(R.string.settings_voice_tts_engine),
                options = TtsEngine.entries,
                selected = state.tts.engine,
                onSelect = { onAction(VoiceSettingsAction.SetTtsEngine(it)) },
                icon = Ic.Volume,
            ) {
                when (it) {
                    TtsEngine.System -> systemTts
                    TtsEngine.DeepSeek -> deepSeekTts
                }
            }
            if (state.tts.engine == TtsEngine.DeepSeek) {
                val deepSeekProviders = state.providers.filter { it.deepSeek }
                if (deepSeekProviders.isEmpty()) {
                    ExRow(
                        title = stringResource(R.string.settings_voice_tts_no_provider),
                        icon = Ic.Volume,
                    )
                } else {
                    val ids = deepSeekProviders.map { it.id }
                    val labels = deepSeekProviders.associate { it.id to it.name }
                    val selected = state.tts.providerId.takeIf { it in ids } ?: ids.first()
                    ExChoiceRow(
                        title = stringResource(R.string.settings_voice_tts_provider),
                        options = ids,
                        selected = selected,
                        onSelect = { onAction(VoiceSettingsAction.SetTtsProvider(it)) },
                        icon = Ic.Cloud,
                    ) { labels[it].orEmpty() }
                    ExChoiceRow(
                        title = stringResource(R.string.settings_voice_tts_voice),
                        options = TtsVoice.entries,
                        selected = state.tts.voice,
                        onSelect = { onAction(VoiceSettingsAction.SetTtsVoice(it)) },
                        icon = Ic.Volume,
                    ) {
                        when (it) {
                            TtsVoice.Stella -> stella
                            TtsVoice.Tide -> tide
                        }
                    }
                    ExRow(
                        title = stringResource(R.string.settings_voice_tts_try),
                        icon = Ic.Play,
                        onClick = { tts.speak("tts-test", sample) },
                    )
                }
            }
            ExSwitchRow(
                title = stringResource(R.string.settings_voice_tts_auto),
                checked = state.tts.autoRead,
                onChange = { onAction(VoiceSettingsAction.SetTtsAutoRead(it)) },
                icon = Ic.Volume,
            )
            ExRow(
                title = stringResource(R.string.settings_voice_tts_note),
                icon = Ic.Info,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VoiceLocalRow(
    state: LocalModelState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
) {
    when (state) {
        LocalModelState.Missing -> {
            ExRow(
                title = stringResource(R.string.settings_voice_local_model),
                subtitle = stringResource(R.string.settings_voice_local_size),
                icon = Ic.Download,
                onClick = onDownload,
                chevron = true,
            )
        }
        is LocalModelState.Downloading -> {
            val progress = if (state.total > 0) {
                state.downloaded.toFloat() / state.total
            } else {
                0f
            }
            ExRow(
                title = stringResource(R.string.settings_voice_local_model),
                subtitle = stringResource(
                    R.string.settings_voice_local_progress,
                    (progress * 100).toInt(),
                ),
                icon = Ic.Download,
                below = {
                    LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExButton(
                        text = stringResource(R.string.settings_voice_local_cancel),
                        onClick = onDelete,
                        kind = BtnKind.Text,
                        icon = Ic.Close,
                    )
                },
            )
        }
        LocalModelState.Extracting -> {
            ExRow(
                title = stringResource(R.string.settings_voice_local_extracting),
                icon = Ic.Download,
                trailing = { LoadingIndicator(modifier = Modifier.size(24.dp)) },
            )
        }
        LocalModelState.Ready -> {
            ExRow(
                title = stringResource(R.string.settings_voice_local_ready),
                subtitle = stringResource(R.string.settings_voice_local_size),
                icon = Ic.Check,
                trailing = {
                    ExButton(
                        text = stringResource(R.string.settings_voice_local_delete),
                        onClick = onDelete,
                        kind = BtnKind.Text,
                        danger = true,
                    )
                },
            )
        }
        is LocalModelState.Failed -> {
            ExRow(
                title = state.error.message(),
                icon = Ic.Error,
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    onRefresh()
                    onDownload()
                },
                chevron = true,
            )
        }
    }
}
