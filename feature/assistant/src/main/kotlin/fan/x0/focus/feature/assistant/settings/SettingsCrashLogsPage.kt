package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.feature.assistant.CrashLogOpenUi
import fan.x0.focus.feature.assistant.CrashLogsAction
import fan.x0.focus.feature.assistant.CrashLogsUiState
import fan.x0.focus.feature.assistant.CrashLogsViewModel
import fan.x0.focus.feature.assistant.R
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SettingsCrashLogsRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: CrashLogsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HandleAppErrors(viewModel.errors, snackbar)
    SettingsCrashLogsPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsCrashLogsPage(
    state: CrashLogsUiState,
    onAction: (CrashLogsAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val dates = remember(state.logs) {
        state.logs.associate { log ->
            log.name to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.time))
        }
    }
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_crash_logs),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            Text(
                text = stringResource(R.string.settings_page_crash_logs_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (state.logs.isEmpty()) {
            item(key = "empty") {
                FocusEmptyState(
                    icon = FocusIcons.Warning,
                    title = stringResource(R.string.crash_logs_empty_title),
                    body = stringResource(R.string.crash_logs_empty_body),
                )
            }
        } else {
            item(key = "logs") {
                FocusCardGroup(title = stringResource(R.string.crash_logs_title)) {
                    state.logs.forEach { log ->
                        item(
                            headlineContent = { Text(dates[log.name].orEmpty()) },
                            supportingContent = {
                                Text(
                                    text = log.preview.ifBlank {
                                        stringResource(R.string.crash_logs_unknown)
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = {
                                FocusCardGroupIcon(
                                    icon = FocusIcons.Warning,
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = hapticTap { onAction(CrashLogsAction.Delete(log.name)) }) {
                                    Icon(
                                        imageVector = FocusIcons.Delete,
                                        contentDescription = stringResource(R.string.crash_logs_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = { onAction(CrashLogsAction.Open(log.name)) },
                        )
                    }
                }
            }
            item(key = "clear") {
                FocusTextButton(
                    text = stringResource(R.string.crash_logs_clear),
                    onClick = { onAction(CrashLogsAction.Clear) },
                )
            }
        }
    }
    state.open?.let { open ->
        CrashLogSheet(
            open = open,
            onClose = { onAction(CrashLogsAction.Close) },
            snackbar = snackbar,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashLogSheet(
    open: CrashLogOpenUi,
    onClose: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.crash_logs_copied)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = open.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            SelectionContainer {
                Text(
                    text = open.content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
            FocusTextButton(
                text = stringResource(R.string.crash_logs_copy),
                onClick = {
                    clipboard.setText(AnnotatedString(open.content))
                    scope.launch { snackbar.showSnackbar(copied) }
                },
            )
        }
    }
}
