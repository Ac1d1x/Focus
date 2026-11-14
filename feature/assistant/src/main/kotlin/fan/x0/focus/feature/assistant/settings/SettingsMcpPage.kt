package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.designsystem.kit.BtnKind
import fan.x0.focus.core.designsystem.kit.ExButton
import fan.x0.focus.core.designsystem.kit.ExCard
import fan.x0.focus.core.designsystem.kit.ExEmpty
import fan.x0.focus.core.designsystem.kit.ExFab
import fan.x0.focus.core.designsystem.kit.ExField
import fan.x0.focus.core.designsystem.kit.ExGroup
import fan.x0.focus.core.designsystem.kit.ExIconButton
import fan.x0.focus.core.designsystem.kit.ExPage
import fan.x0.focus.core.designsystem.kit.ExPill
import fan.x0.focus.core.designsystem.kit.ExRow
import fan.x0.focus.core.designsystem.kit.ExSegmented
import fan.x0.focus.core.designsystem.kit.ExSheet
import fan.x0.focus.core.designsystem.kit.ExSwitchRow
import fan.x0.focus.core.designsystem.kit.Ic
import fan.x0.focus.core.designsystem.kit.group
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.McpServerConfig
import fan.x0.focus.data.assistant.McpTestReport
import fan.x0.focus.data.assistant.McpTransport
import fan.x0.focus.feature.assistant.McpAction
import fan.x0.focus.feature.assistant.McpDraft
import fan.x0.focus.feature.assistant.McpEffect
import fan.x0.focus.feature.assistant.McpSettingsViewModel
import fan.x0.focus.feature.assistant.McpUiState
import fan.x0.focus.feature.assistant.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsMcpRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: McpSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved = stringResource(R.string.settings_mcp_saved)
    val deleted = stringResource(R.string.settings_mcp_deleted)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            McpEffect.Saved -> snackbar.showSnackbar(saved)
            McpEffect.Deleted -> snackbar.showSnackbar(deleted)
        }
    }
    SettingsMcpPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsMcpPage(
    state: McpUiState,
    onAction: (McpAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    ExPage(
        title = stringResource(R.string.settings_mcp_title),
        subtitle = stringResource(R.string.settings_mcp_guide),
        onBack = onBack,
        snackbar = snackbar,
        fab = {
            ExFab(
                text = stringResource(R.string.settings_mcp_add),
                icon = Ic.Add,
                onClick = { onAction(McpAction.Add) },
            )
        },
    ) {
        if (state.servers.isEmpty()) {
            item {
                ExEmpty(
                    icon = Ic.Hub,
                    title = stringResource(R.string.settings_mcp_no_servers),
                    text = stringResource(R.string.settings_mcp_guide),
                )
            }
        }
        if (state.servers.isNotEmpty()) {
            group {
                state.servers.forEach { server ->
                    McpServerRow(
                        server = server,
                        onOpen = { onAction(McpAction.Edit(server.id)) },
                        onEnabled = { onAction(McpAction.SetEnabled(server.id, it)) },
                    )
                }
            }
        }
    }
    state.draft?.let { draft ->
        McpEditorSheet(
            draft = draft,
            state = state,
            onAction = onAction,
        )
    }
}

@Composable
private fun McpServerRow(
    server: McpServerConfig,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    val subtitle = if (server.transport == McpTransport.Stdio) {
        listOf(server.command, server.args.joinToString(" "))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    } else {
        server.url
    }
    ExRow(
        title = server.name,
        subtitle = subtitle,
        icon = Ic.Hub,
        onClick = onOpen,
        trailing = {
            Switch(checked = server.enabled, onCheckedChange = onEnabled)
        },
    )
}

@Composable
private fun McpEditorSheet(
    draft: McpDraft,
    state: McpUiState,
    onAction: (McpAction) -> Unit,
) {
    val existing = state.servers.any { it.id == draft.id }
    ExSheet(
        onDismiss = { onAction(McpAction.Close) },
        title = stringResource(if (existing) R.string.settings_mcp_edit else R.string.settings_mcp_add),
        full = true,
    ) {
        ExField(
            value = draft.name,
            onChange = { onAction(McpAction.NameChanged(it)) },
            label = stringResource(R.string.settings_mcp_name),
        )
        val http = stringResource(R.string.settings_mcp_transport_http)
        val sse = stringResource(R.string.settings_mcp_transport_sse)
        val stdio = stringResource(R.string.settings_mcp_transport_stdio)
        ExSegmented(
            options = McpTransport.entries,
            selected = draft.transport,
            onSelect = { onAction(McpAction.TransportChanged(it)) },
        ) {
            when (it) {
                McpTransport.StreamableHttp -> http
                McpTransport.Sse -> sse
                McpTransport.Stdio -> stdio
            }
        }
        if (draft.transport == McpTransport.Stdio) {
            ExField(
                value = draft.command,
                onChange = { onAction(McpAction.CommandChanged(it)) },
                label = stringResource(R.string.settings_mcp_command),
                mono = true,
            )
            ExField(
                value = draft.args,
                onChange = { onAction(McpAction.ArgsChanged(it)) },
                label = stringResource(R.string.settings_mcp_args),
                singleLine = false,
                mono = true,
            )
            ExField(
                value = draft.env,
                onChange = { onAction(McpAction.EnvChanged(it)) },
                label = stringResource(R.string.settings_mcp_env),
                singleLine = false,
                mono = true,
            )
        } else {
            ExField(
                value = draft.url,
                onChange = { onAction(McpAction.UrlChanged(it)) },
                label = stringResource(R.string.settings_mcp_url),
                mono = true,
            )
            draft.headers.forEachIndexed { index, header ->
                ExCard(shape = MaterialTheme.shapes.large) {
                    ExField(
                        value = header.name,
                        onChange = { onAction(McpAction.HeaderNameChanged(index, it)) },
                        label = stringResource(R.string.settings_mcp_header_name),
                        mono = true,
                    )
                    ExField(
                        value = header.value,
                        onChange = { onAction(McpAction.HeaderValueChanged(index, it)) },
                        label = stringResource(R.string.settings_mcp_header_value),
                        secret = header.secret,
                        mono = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_mcp_header_secret),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = header.secret,
                            onCheckedChange = {
                                onAction(McpAction.HeaderSecretChanged(index, it))
                            },
                        )
                        ExIconButton(
                            icon = Ic.Delete,
                            label = stringResource(R.string.settings_mcp_remove_header),
                            onClick = { onAction(McpAction.RemoveHeader(index)) },
                        )
                    }
                }
            }
            ExButton(
                text = stringResource(R.string.settings_mcp_add_header),
                onClick = { onAction(McpAction.AddHeader) },
                kind = BtnKind.Text,
                icon = Ic.Add,
            )
        }
        ExSwitchRow(
            title = stringResource(R.string.settings_mcp_enabled),
            checked = draft.enabled,
            onChange = { onAction(McpAction.EnabledChanged(it)) },
        )
        state.error?.let { error ->
            Text(
                text = error.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.report?.let { report ->
            McpTestReportCard(report = report)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExButton(
                text = stringResource(
                    if (state.testing) R.string.settings_mcp_testing else R.string.settings_mcp_test,
                ),
                onClick = { onAction(McpAction.Test) },
                modifier = Modifier.weight(1f),
                kind = BtnKind.Tonal,
                icon = Ic.Bolt,
                enabled = !state.testing,
                loading = state.testing,
            )
            ExButton(
                text = stringResource(R.string.settings_mcp_save),
                onClick = { onAction(McpAction.Save) },
                modifier = Modifier.weight(1f),
                icon = Ic.Check,
            )
        }
        if (existing) {
            ExButton(
                text = stringResource(R.string.settings_mcp_delete),
                onClick = { onAction(McpAction.Delete) },
                kind = BtnKind.Text,
                danger = true,
                icon = Ic.Delete,
            )
        }
    }
}

@Composable
private fun McpTestReportCard(report: McpTestReport) {
    ExGroup {
        ExCard {
            ExPill(
                text = stringResource(
                    R.string.settings_mcp_status_ok,
                    report.info.name,
                    report.info.version,
                ),
                icon = Ic.Check,
            )
            Text(
                text = stringResource(R.string.settings_mcp_tools_count, report.tools.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.tools.take(8).forEach { tool ->
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
