package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import fan.x0.focus.core.designsystem.kit.ExEmpty
import fan.x0.focus.core.designsystem.kit.ExField
import fan.x0.focus.core.designsystem.kit.ExGroup
import fan.x0.focus.core.designsystem.kit.ExPage
import fan.x0.focus.core.designsystem.kit.ExPill
import fan.x0.focus.core.designsystem.kit.ExRow
import fan.x0.focus.core.designsystem.kit.ExSheet
import fan.x0.focus.core.designsystem.kit.Ic
import fan.x0.focus.core.designsystem.kit.group
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.WebSearchProvider
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.WebSearchAction
import fan.x0.focus.feature.assistant.WebSearchEffect
import fan.x0.focus.feature.assistant.WebSearchProviderStatus
import fan.x0.focus.feature.assistant.WebSearchSettingsViewModel
import fan.x0.focus.feature.assistant.WebSearchUiState
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsWebSearchRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: WebSearchSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved = stringResource(R.string.settings_web_saved)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            WebSearchEffect.Saved -> snackbar.showSnackbar(saved)
        }
    }
    SettingsWebSearchPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsWebSearchPage(
    state: WebSearchUiState,
    onAction: (WebSearchAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val default = stringResource(R.string.settings_web_default)
    ExPage(
        title = stringResource(R.string.settings_web_title),
        subtitle = stringResource(R.string.settings_web_body),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        if (state.providers.isEmpty()) {
            item {
                ExEmpty(
                    icon = Ic.Search,
                    title = stringResource(R.string.settings_web_title),
                    text = stringResource(R.string.settings_web_body),
                )
            }
        }
        group {
            state.providers.forEach { status ->
                val isDefault = status.provider == state.defaultProvider
                val configured = if (status.provider.requiresKey) status.configured else true
                val subtitle = when {
                    !status.provider.requiresKey -> stringResource(R.string.settings_web_no_key)
                    status.configured -> stringResource(R.string.settings_web_configured)
                    else -> stringResource(R.string.settings_web_not_configured)
                }
                ExRow(
                    title = status.provider.name,
                    subtitle = subtitle,
                    icon = Ic.Search,
                    tint = if (configured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    onClick = { onAction(WebSearchAction.Open(status.provider)) },
                    onLongClick = { onAction(WebSearchAction.SetDefault(status.provider)) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isDefault) {
                                ExPill(text = default, icon = Ic.Check)
                            }
                            Switch(
                                checked = status.enabled,
                                onCheckedChange = { onAction(WebSearchAction.SetEnabled(status.provider, it)) },
                            )
                        }
                    },
                )
            }
        }
    }
    state.editing?.let { provider ->
        val isDefault = provider == state.defaultProvider
        ExSheet(
            onDismiss = { onAction(WebSearchAction.Close) },
            title = provider.name,
            full = true,
        ) {
            if (provider.requiresKey) {
                ExField(
                    value = state.keyInput,
                    onChange = { onAction(WebSearchAction.KeyChanged(it)) },
                    label = stringResource(R.string.settings_web_key),
                    secret = true,
                    mono = true,
                    supporting = if (state.keySaved) stringResource(R.string.settings_key_saved) else null,
                )
            } else {
                ExPill(
                    text = stringResource(R.string.settings_web_no_key),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            state.error?.let { error ->
                Text(
                    text = error.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExButton(
                    text = stringResource(R.string.settings_web_test),
                    onClick = { onAction(WebSearchAction.Test) },
                    modifier = Modifier.weight(1f),
                    kind = BtnKind.Tonal,
                    icon = Ic.Bolt,
                    enabled = !state.testing,
                    loading = state.testing,
                )
                ExButton(
                    text = stringResource(R.string.settings_web_save),
                    onClick = { onAction(WebSearchAction.Save) },
                    modifier = Modifier.weight(1f),
                    icon = Ic.Check,
                    enabled = !state.testing,
                )
            }
            ExButton(
                text = stringResource(R.string.settings_web_set_default),
                onClick = { onAction(WebSearchAction.SetDefault(provider)) },
                kind = BtnKind.Text,
                icon = Ic.Star,
                enabled = !isDefault,
            )
            if (state.results.isNotEmpty()) {
                ExGroup(title = stringResource(R.string.settings_web_results)) {
                    state.results.take(5).forEach { result ->
                        ExRow(
                            title = result.title,
                            subtitle = result.snippet.ifBlank { result.url },
                            icon = Ic.Search,
                        )
                    }
                }
            }
        }
    }
}
