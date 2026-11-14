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
import fan.x0.focus.core.designsystem.kit.Ic
import fan.x0.focus.core.designsystem.kit.group
import fan.x0.focus.data.voice.LocalModelState
import fan.x0.focus.data.voice.VoiceEngine
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.VoiceSettingsAction
import fan.x0.focus.feature.assistant.VoiceSettingsUiState
import fan.x0.focus.feature.assistant.VoiceSettingsViewModel
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
                        selected = state.config.cloudProviderId,
                        onSelect = { onAction(VoiceSettingsAction.SetCloudProvider(it)) },
                        icon = Ic.Cloud,
                    ) { labels[it].orEmpty() }
                }
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
