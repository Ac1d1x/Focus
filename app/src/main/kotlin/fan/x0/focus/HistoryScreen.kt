package fan.x0.focus

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusTabSwitcher
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.data.assistant.Conversation
import fan.x0.focus.data.assistant.ConversationKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryRoute(
    conversations: List<Conversation>,
    archived: List<Conversation>,
    onOpen: (String) -> Unit,
    onOpenArchived: (String) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPromote: (String) -> Unit,
) {
    var archivedTab by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    val source = if (archivedTab) archived else conversations
    val filtered = remember(source, query) {
        if (query.isBlank()) {
            source
        } else {
            source.filter {
                it.title.contains(query, ignoreCase = true) || it.model.contains(query, ignoreCase = true)
            }
        }.sortedByDescending { it.updatedAt }
    }
    val today = LocalDate.now()
    val groups = remember(filtered, today) {
        filtered.groupBy { historyBucket(it.updatedAt, today) }.toSortedMap()
    }
    val labels = listOf(
        stringResource(R.string.drawer_today),
        stringResource(R.string.drawer_yesterday),
        stringResource(R.string.drawer_previous_7_days),
        stringResource(R.string.history_previous_30_days),
        stringResource(R.string.drawer_older),
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FocusCompactTopBar(
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.history_summary, conversations.size, archived.size),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
            )
            FocusTabSwitcher(
                labels = listOf(
                    stringResource(R.string.history_tab_active),
                    stringResource(R.string.history_tab_archived),
                ),
                selectedIndex = if (archivedTab) 1 else 0,
                onSelect = { archivedTab = it == 1 },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FocusEmptyState(
                        icon = if (query.isBlank()) {
                            if (archivedTab) FocusIcons.Archive else FocusIcons.History
                        } else {
                            FocusIcons.Search
                        },
                        title = if (query.isBlank()) {
                            if (archivedTab) {
                                stringResource(R.string.history_archived_empty)
                            } else {
                                stringResource(R.string.history_empty)
                            }
                        } else {
                            stringResource(R.string.history_no_results)
                        },
                        body = if (query.isBlank()) {
                            if (archivedTab) {
                                stringResource(R.string.history_archived_empty_body)
                            } else {
                                stringResource(R.string.history_empty_body)
                            }
                        } else {
                            stringResource(R.string.history_no_results_body)
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    groups.forEach { (bucket, rows) ->
                        item(key = "history_$bucket") {
                            Text(
                                text = labels[bucket],
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                            )
                        }
                        items(rows, key = { it.id }) { conversation ->
                            HistoryRow(
                                conversation = conversation,
                                archived = archivedTab,
                                onClick = {
                                    if (archivedTab) {
                                        onOpenArchived(conversation.id)
                                    } else {
                                        onOpen(conversation.id)
                                    }
                                },
                                onArchive = { onArchive(conversation.id) },
                                onDelete = { deleteTarget = conversation },
                                onPromote = { onPromote(conversation.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        val title = target.title.ifBlank { stringResource(R.string.drawer_untitled) }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_body, title)) },
            confirmButton = {
                TextButton(
                    onClick = hapticTap {
                        onDelete(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.history_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = hapticTap { deleteTarget = null }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }
}

private fun historyBucket(timestamp: Long, today: LocalDate): Int {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return when {
        date == today -> 0
        date == today.minusDays(1) -> 1
        date.isAfter(today.minusDays(8)) -> 2
        date.isAfter(today.minusDays(31)) -> 3
        else -> 4
    }
}

@Composable
private fun HistoryRow(
    conversation: Conversation,
    archived: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onPromote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val main = conversation.kind == ConversationKind.Main
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FocusShapeTokens.pill)
            .clickable(onClick = hapticTap(onClick))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (main) FocusIcons.Check else FocusIcons.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title.ifBlank { stringResource(R.string.drawer_untitled) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    DateUtils.getRelativeTimeSpanString(
                        conversation.updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                    conversation.model,
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (main) {
            Text(
                text = stringResource(R.string.history_current),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (!main) {
            Box {
                IconButton(onClick = hapticTap { menu = true }) {
                    Icon(
                        imageVector = FocusIcons.MoreHoriz,
                        contentDescription = stringResource(R.string.history_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!archived) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_promote)) },
                            onClick = {
                                menu = false
                                onPromote()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_archive)) },
                            onClick = {
                                menu = false
                                onArchive()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.history_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.history_search_hint)) },
        leadingIcon = {
            Icon(FocusIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        trailingIcon = if (query.isBlank()) {
            null
        } else {
            {
                IconButton(onClick = hapticTap { onQueryChange("") }) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.history_clear_search),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = FocusShapeTokens.input,
        modifier = modifier.fillMaxWidth(),
    )
}
