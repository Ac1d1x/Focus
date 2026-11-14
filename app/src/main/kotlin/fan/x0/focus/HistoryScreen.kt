package fan.x0.focus

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerAvailable
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerOpener
import fan.x0.focus.core.designsystem.kit.ExConfirm
import fan.x0.focus.core.designsystem.kit.ExEmpty
import fan.x0.focus.core.designsystem.kit.ExMenu
import fan.x0.focus.core.designsystem.kit.ExPage
import fan.x0.focus.core.designsystem.kit.ExPill
import fan.x0.focus.core.designsystem.kit.ExRow
import fan.x0.focus.core.designsystem.kit.ExSearch
import fan.x0.focus.core.designsystem.kit.ExSegmented
import fan.x0.focus.core.designsystem.kit.Ic
import fan.x0.focus.core.designsystem.kit.MenuItem
import fan.x0.focus.core.designsystem.kit.group
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
    val chatLabel = stringResource(R.string.history_tab_active)
    val archivedLabel = stringResource(R.string.history_tab_archived)
    val promote = stringResource(R.string.history_promote)
    val archive = stringResource(R.string.history_archive)
    val delete = stringResource(R.string.history_delete)
    val current = stringResource(R.string.history_current)
    val historyTitle = stringResource(R.string.history_title)
    val searchHint = stringResource(R.string.history_search_hint)
    val summary = stringResource(R.string.history_summary, conversations.size, archived.size)
    val opener = LocalFocusDrawerOpener.current
    val available = LocalFocusDrawerAvailable.current

    ExPage(
        title = historyTitle,
        subtitle = summary,
        onMenu = if (available) opener else null,
    ) {
        item {
            ExSearch(
                value = query,
                onChange = { query = it },
                placeholder = searchHint,
            )
        }
        item {
            ExSegmented(
                options = listOf(false, true),
                selected = archivedTab,
                onSelect = { archivedTab = it },
            ) {
                if (it) archivedLabel else chatLabel
            }
        }
        if (filtered.isEmpty()) {
            item {
                ExEmpty(
                    icon = if (archivedTab) Ic.Archive else Ic.History,
                    title = if (query.isBlank()) {
                        if (archivedTab) {
                            stringResource(R.string.history_archived_empty)
                        } else {
                            stringResource(R.string.history_empty)
                        }
                    } else {
                        stringResource(R.string.history_no_results)
                    },
                    text = if (query.isBlank()) {
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
        }
        groups.forEach { (bucket, rows) ->
            group(labels[bucket]) {
                rows.forEach { conversation ->
                    val main = conversation.kind == ConversationKind.Main
                    ExRow(
                        title = conversation.title.ifBlank { stringResource(R.string.drawer_untitled) },
                        subtitle = listOf(
                            DateUtils.getRelativeTimeSpanString(
                                conversation.updatedAt,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE,
                            ).toString(),
                            conversation.model,
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        icon = if (main) Ic.Star else Ic.Chat,
                        onClick = {
                            if (archivedTab) {
                                onOpenArchived(conversation.id)
                            } else {
                                onOpen(conversation.id)
                            }
                        },
                        trailing = {
                            if (main) {
                                ExPill(text = current, icon = Ic.Star)
                            } else {
                                ExMenu(
                                    listOfNotNull(
                                        if (!archivedTab) {
                                            MenuItem(promote, Ic.Star) { onPromote(conversation.id) }
                                        } else {
                                            null
                                        },
                                        if (!archivedTab) {
                                            MenuItem(archive, Ic.Archive) { onArchive(conversation.id) }
                                        } else {
                                            null
                                        },
                                        MenuItem(delete, Ic.Delete, danger = true) {
                                            deleteTarget = conversation
                                        },
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        val title = target.title.ifBlank { stringResource(R.string.drawer_untitled) }
        ExConfirm(
            title = stringResource(R.string.history_delete_title),
            text = stringResource(R.string.history_delete_body, title),
            confirm = stringResource(R.string.history_delete_confirm),
            danger = true,
            onConfirm = {
                onDelete(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
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
