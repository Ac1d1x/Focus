package fan.x0.focus.feature.assistant.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.kit.ExField
import fan.x0.focus.core.designsystem.kit.ExGroup
import fan.x0.focus.core.designsystem.kit.ExPage
import fan.x0.focus.core.designsystem.kit.ExRow
import fan.x0.focus.core.designsystem.kit.ExSwitchRow
import fan.x0.focus.core.designsystem.kit.Ic
import fan.x0.focus.core.designsystem.kit.group
import fan.x0.focus.feature.assistant.DeepSeekSettingsAction
import fan.x0.focus.feature.assistant.DeepSeekSettingsUiState
import fan.x0.focus.feature.assistant.DeepSeekSettingsViewModel
import fan.x0.focus.feature.assistant.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsDeepSeekRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: DeepSeekSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsDeepSeekPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsDeepSeekPage(
    state: DeepSeekSettingsUiState,
    onAction: (DeepSeekSettingsAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val providersTitle = stringResource(R.string.settings_deepseek_providers)
    val capabilitiesTitle = stringResource(R.string.settings_deepseek_capabilities)
    val metaTitle = stringResource(R.string.settings_deepseek_meta)
    ExPage(
        title = stringResource(R.string.settings_deepseek_title),
        subtitle = stringResource(R.string.settings_deepseek_body),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item {
            ExGroup {
                ExSwitchRow(
                    title = stringResource(R.string.settings_deepseek_enabled),
                    checked = state.config.enabled,
                    onChange = { onAction(DeepSeekSettingsAction.SetEnabled(it)) },
                    icon = Ic.Robot,
                )
            }
        }
        if (state.providers.isEmpty()) {
            item {
                ExGroup {
                    ExRow(
                        title = stringResource(R.string.settings_deepseek_no_provider),
                        icon = Ic.Robot,
                    )
                }
            }
        } else {
            group(providersTitle) {
                state.providers.forEach { provider ->
                    val ready = provider.model.startsWith("deepseek-flash")
                    ExRow(
                        title = provider.name,
                        subtitle = if (ready) {
                            stringResource(R.string.settings_deepseek_model_ready, provider.model)
                        } else {
                            stringResource(R.string.settings_deepseek_model_note)
                        },
                        icon = Ic.Robot,
                    )
                }
            }
        }
        group(capabilitiesTitle) {
            ExSwitchRow(
                title = stringResource(R.string.settings_deepseek_web),
                subtitle = stringResource(R.string.settings_deepseek_web_body),
                checked = state.config.webSearch,
                enabled = state.config.enabled,
                onChange = { onAction(DeepSeekSettingsAction.SetWebSearch(it)) },
                icon = Ic.Search,
            )
            ExSwitchRow(
                title = stringResource(R.string.settings_deepseek_read),
                subtitle = stringResource(R.string.settings_deepseek_read_body),
                checked = state.config.readLinks,
                enabled = state.config.enabled,
                onChange = { onAction(DeepSeekSettingsAction.SetReadLinks(it)) },
                icon = Ic.Link,
            )
            ExSwitchRow(
                title = stringResource(R.string.settings_deepseek_auto_title),
                subtitle = stringResource(R.string.settings_deepseek_auto_title_body),
                checked = state.config.title,
                enabled = state.config.enabled,
                onChange = { onAction(DeepSeekSettingsAction.SetAutoTitle(it)) },
                icon = Ic.Chat,
            )
            ExSwitchRow(
                title = stringResource(R.string.settings_deepseek_user_meta),
                subtitle = stringResource(R.string.settings_deepseek_user_meta_body),
                checked = state.config.userMeta,
                enabled = state.config.enabled,
                onChange = { onAction(DeepSeekSettingsAction.SetUserMeta(it)) },
                icon = Ic.Person,
            )
        }
        if (state.config.userMeta) {
            group(metaTitle) {
                ExRow(
                    title = stringResource(R.string.settings_deepseek_location),
                    icon = Ic.Pin,
                    below = {
                        ExField(
                            value = state.config.location,
                            onChange = { onAction(DeepSeekSettingsAction.SetLocation(it)) },
                            label = stringResource(R.string.settings_deepseek_location),
                            placeholder = stringResource(R.string.settings_deepseek_location_hint),
                            enabled = state.config.enabled,
                        )
                    },
                )
            }
        }
    }
}
