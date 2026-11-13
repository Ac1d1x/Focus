package fan.x0.focus.feature.chat

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.data.assistant.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SideChatSheet(
    panel: SidePanelUi,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onStop: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPromote: (String) -> Unit,
) {
    if (!panel.open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SideChatHeader(panel = panel, onRefresh = onRefresh)
            when {
                panel.loading && panel.items.isEmpty() -> FocusLoadingBlock(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                panel.items.isEmpty() -> FocusEmptyState(
                    icon = FocusIcons.Groups,
                    title = stringResource(R.string.chat_side_panel_empty_title),
                    body = stringResource(R.string.chat_side_panel_empty),
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                else -> FocusCardGroup(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    panel.items.forEach { row ->
                        item(
                            headlineContent = {
                                Text(
                                    text = row.title.ifBlank {
                                        stringResource(R.string.chat_side_panel_untitled)
                                    },
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                val detail = listOf(row.summary, relativeTime(row.updatedAt))
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                if (detail.isNotBlank()) {
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            leadingContent = {
                                val color = when {
                                    row.status.active -> MaterialTheme.colorScheme.tertiary
                                    row.status == TaskStatus.Failed || row.status == TaskStatus.Interrupted ->
                                        MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                FocusCardGroupIcon(
                                    icon = if (row.unseen) FocusIcons.Notification else FocusIcons.Groups,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = color,
                                )
                            },
                            trailingContent = {
                                SideChatActions(
                                    row = row,
                                    onStop = { onStop(row.id) },
                                    onRetry = { onRetry(row.id) },
                                    onPromote = { onPromote(row.id) },
                                )
                            },
                            onClick = { onOpen(row.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SideChatHeader(
    panel: SidePanelUi,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.chat_side_panel_title),
                style = LocalFocusTypography.current.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.chat_side_panel_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        FilledTonalIconButton(onClick = hapticTap(onRefresh), enabled = !panel.loading) {
            Icon(
                imageVector = FocusIcons.Refresh,
                contentDescription = stringResource(R.string.chat_side_panel_refresh),
            )
        }
    }
}

@Composable
private fun SideChatActions(
    row: SideChatRowUi,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onPromote: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (row.status != TaskStatus.None) {
            FocusStatusPill(
                text = statusLabel(row.status),
                tone = statusTone(row.status),
            )
        }
        Box {
            IconButton(
                onClick = hapticTap { menu = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = FocusIcons.More,
                    contentDescription = stringResource(R.string.chat_side_panel_actions),
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (row.status.active) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_side_panel_stop)) },
                        onClick = {
                            menu = false
                            onStop()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_side_result_retry)) },
                        onClick = {
                            menu = false
                            onRetry()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_promote_main)) },
                    onClick = {
                        menu = false
                        onPromote()
                    },
                )
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String =
    DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

private fun statusTone(status: TaskStatus) = when (status) {
    TaskStatus.Queued, TaskStatus.Running -> StatusTone.Active
    TaskStatus.Waiting -> StatusTone.Warning
    TaskStatus.Done -> StatusTone.Success
    TaskStatus.Failed, TaskStatus.Interrupted -> StatusTone.Danger
    TaskStatus.NoResult, TaskStatus.None -> StatusTone.Neutral
}

@Composable
private fun statusLabel(status: TaskStatus) = stringResource(
    when (status) {
        TaskStatus.Queued -> R.string.chat_side_status_queued
        TaskStatus.Running -> R.string.chat_side_status_running
        TaskStatus.Waiting -> R.string.chat_side_status_waiting
        TaskStatus.Done -> R.string.chat_side_status_done
        TaskStatus.Failed -> R.string.chat_side_status_failed
        TaskStatus.Interrupted -> R.string.chat_side_status_interrupted
        TaskStatus.NoResult -> R.string.chat_side_status_no_result
        TaskStatus.None -> R.string.chat_side_status_none
    },
)
