package fan.x0.focus

import android.text.format.DateUtils
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusTabContent
import fan.x0.focus.core.designsystem.components.FocusTabSwitcher
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.data.assistant.Conversation

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
    var section by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    val active = remember(conversations, query) {
        if (query.isBlank()) {
            conversations
        } else {
            conversations.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
    val archivedFiltered = remember(archived, query) {
        if (query.isBlank()) {
            archived
        } else {
            archived.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FocusCompactTopBar(title = stringResource(R.string.history_title))
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
                selectedIndex = section,
                onSelect = { section = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FocusTabContent(selectedIndex = section) { tab ->
                if (tab == 0) {
                    ConversationList(
                        conversations = active,
                        archived = false,
                        emptyIcon = if (query.isBlank()) FocusIcons.History else FocusIcons.Search,
                        emptyTitle = if (query.isBlank()) {
                            stringResource(R.string.history_empty)
                        } else {
                            stringResource(R.string.history_no_results)
                        },
                        emptyBody = if (query.isBlank()) {
                            stringResource(R.string.history_empty_body)
                        } else {
                            stringResource(R.string.history_no_results_body)
                        },
                        onOpen = { onOpen(it.id) },
                        onArchive = { onArchive(it.id) },
                        onDelete = { deleteTarget = it },
                        onPromote = { onPromote(it.id) },
                    )
                } else {
                    ConversationList(
                        conversations = archivedFiltered,
                        archived = true,
                        emptyIcon = if (query.isBlank()) FocusIcons.Archive else FocusIcons.Search,
                        emptyTitle = if (query.isBlank()) {
                            stringResource(R.string.history_archived_empty)
                        } else {
                            stringResource(R.string.history_no_results)
                        },
                        emptyBody = if (query.isBlank()) {
                            stringResource(R.string.history_archived_empty_body)
                        } else {
                            stringResource(R.string.history_no_results_body)
                        },
                        onOpen = { onOpenArchived(it.id) },
                        onArchive = {},
                        onDelete = { deleteTarget = it },
                        onPromote = {},
                    )
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

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    archived: Boolean,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptyBody: String,
    onOpen: (Conversation) -> Unit,
    onArchive: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    onPromote: (Conversation) -> Unit,
) {
    if (conversations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            FocusEmptyState(
                icon = emptyIcon,
                title = emptyTitle,
                body = emptyBody,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(conversations, key = { it.id }) { conversation ->
            ConversationCard(
                conversation = conversation,
                archived = archived,
                onOpen = { onOpen(conversation) },
                onArchive = { onArchive(conversation) },
                onDelete = { onDelete(conversation) },
                onPromote = { onPromote(conversation) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    archived: Boolean,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onPromote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = hapticTap(onOpen),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = conversation.title.ifBlank { stringResource(R.string.drawer_untitled) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        conversation.updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                if (archived) {
                    IconButton(onClick = hapticTap(onDelete)) {
                        Icon(
                            imageVector = FocusIcons.Delete,
                            contentDescription = stringResource(R.string.history_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Row {
                        IconButton(onClick = hapticTap(onPromote)) {
                            Icon(
                                imageVector = FocusIcons.Assistant,
                                contentDescription = stringResource(R.string.history_promote),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = hapticTap(onArchive)) {
                            Icon(
                                imageVector = FocusIcons.Archive,
                                contentDescription = stringResource(R.string.history_archive),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
