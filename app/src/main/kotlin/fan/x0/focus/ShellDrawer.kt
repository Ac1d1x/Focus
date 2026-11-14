package fan.x0.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.app.ShellState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.hapticSelect
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.data.assistant.AssistantKind
import fan.x0.focus.data.assistant.AssistantProfile
import fan.x0.focus.data.assistant.Conversation
import fan.x0.focus.feature.assistant.AssistantAvatar
import fan.x0.focus.feature.assistant.NewAssistantDialog
import fan.x0.focus.plugin.PluginPageRef
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellDrawer(
    state: ShellState,
    current: FocusRoute,
    currentConversationId: String,
    pluginPages: List<PluginPageRef>,
    controlledOnly: Boolean = false,
    onNavigate: (FocusRoute) -> Unit,
    onOpenMain: () -> Unit,
    onNewSideChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onSelectAssistant: (String) -> Unit,
    onCreateAssistant: (AssistantKind, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAssistants by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val assistant = state.activeAssistant ?: AssistantProfile(
        id = state.activeAssistantId,
        name = AssistantProfile.DEFAULT_NAME,
    )
    val sessions = remember(state.sides, state.conversations, state.main?.id) {
        (state.sides + state.conversations)
            .filter { it.id != state.main?.id }
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
    }
    val today = LocalDate.now()
    val groups = remember(sessions, today) {
        sessions.groupBy { bucketOf(it.updatedAt, today) }.toSortedMap()
    }
    val labels = listOf(
        stringResource(R.string.drawer_today),
        stringResource(R.string.drawer_yesterday),
        stringResource(R.string.drawer_previous_7_days),
        stringResource(R.string.drawer_older),
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (state.main != null) {
                item(key = "shell:pinned") { SectionLabel(stringResource(R.string.drawer_pinned)) }
                item(key = "shell:main") {
                    ConversationRow(
                        conversation = state.main,
                        selected = current == FocusRoute.Chat && currentConversationId == state.main.id,
                        onClick = onOpenMain,
                    )
                }
            }
            groups.forEach { (bucket, list) ->
                item(key = "shell:date_$bucket") { SectionLabel(labels[bucket]) }
                items(list, key = { "conversation:${it.id}" }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        selected = conversation.id == currentConversationId,
                        onClick = { onOpenConversation(conversation.id) },
                    )
                }
            }
            if (sessions.isEmpty() && state.main == null) {
                item(key = "shell:empty") { EmptyRow(stringResource(R.string.drawer_no_conversations)) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AssistantPicker(
            assistant = assistant,
            onSwitch = { showAssistants = true },
            onEdit = { onNavigate(FocusRoute.Assistant) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconOnlyAction(
                icon = FocusIcons.History,
                label = stringResource(R.string.drawer_history),
                selected = current == FocusRoute.History,
                onClick = { onNavigate(FocusRoute.History) },
            )
            IconOnlyAction(
                icon = FocusIcons.Add,
                label = stringResource(R.string.drawer_new_chat),
                selected = false,
                onClick = onNewSideChat,
            )
            if (!controlledOnly) {
                IconOnlyAction(
                    icon = FocusIcons.Assistant,
                    label = stringResource(R.string.drawer_context_short),
                    selected = current == FocusRoute.Assistant,
                    onClick = { onNavigate(FocusRoute.Assistant) },
                )
                IconOnlyAction(
                    icon = FocusIcons.MoreHoriz,
                    label = stringResource(R.string.drawer_more),
                    selected = false,
                    onClick = { showMore = true },
                )
            }
            Spacer(Modifier.weight(1f))
            IconOnlyAction(
                icon = FocusIcons.Settings,
                label = stringResource(R.string.settings_title),
                selected = current == FocusRoute.Settings,
                onClick = { onNavigate(FocusRoute.Settings) },
            )
        }
    }
    if (showAssistants) {
        AssistantSwitcherSheet(
            assistants = state.assistants,
            activeId = state.activeAssistantId,
            onDismiss = { showAssistants = false },
            onSelect = {
                showAssistants = false
                onSelectAssistant(it)
            },
            onCreate = {
                showAssistants = false
                creating = true
            },
        )
    }
    if (showMore) {
        MoreSheet(
            controlledOnly = controlledOnly,
            pluginPages = pluginPages,
            current = current,
            onDismiss = { showMore = false },
            onNavigate = {
                showMore = false
                onNavigate(it)
            },
        )
    }
    if (creating) {
        NewAssistantDialog(
            onDismiss = { creating = false },
            onSave = { kind, name, role, emoji ->
                onCreateAssistant(kind, name, role, emoji)
                creating = false
            },
        )
    }
}

private fun bucketOf(timestamp: Long, today: LocalDate): Int {
    val date = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return when {
        date == today -> 0
        date == today.minusDays(1) -> 1
        date.isAfter(today.minusDays(8)) -> 2
        else -> 3
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = hapticSelect(onClick),
        shape = FocusShapeTokens.pill,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = conversation.title.ifBlank { stringResource(R.string.drawer_untitled) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (conversation.taskStatus.active) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
            }
        }
    }
}

@Composable
private fun AssistantPicker(
    assistant: AssistantProfile,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(FocusIcons.Assistant, contentDescription = null, modifier = Modifier.size(20.dp)) },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assistant.name.ifBlank { AssistantProfile.DEFAULT_NAME },
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = assistant.role.ifBlank { stringResource(R.string.drawer_assistant_default_role) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = stringResource(R.string.drawer_manage_assistant),
                            onClick = hapticTap(onEdit),
                        ),
                ) {
                    AssistantAvatar(assistant = assistant, size = 30.dp)
                }
            }
        },
        selected = false,
        onClick = hapticSelect(onSwitch),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun IconOnlyAction(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        FilledIconButton(onClick = hapticTap(onClick)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
    } else {
        FilledTonalIconButton(onClick = hapticTap(onClick)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantSwitcherSheet(
    assistants: List<AssistantProfile>,
    activeId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.drawer_assistants),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilledTonalIconButton(onClick = hapticTap(onCreate)) {
                        Icon(FocusIcons.Add, contentDescription = stringResource(R.string.drawer_new_assistant))
                    }
                }
                Text(
                    text = stringResource(R.string.drawer_assistants_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(assistants, key = { it.id }) { assistant ->
                    val selected = assistant.id == activeId
                    Card(
                        onClick = hapticSelect { onSelect(assistant.id) },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AssistantAvatar(assistant = assistant, size = 40.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = assistant.name,
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = assistant.role.ifBlank {
                                        stringResource(R.string.drawer_assistant_default_role)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (selected) {
                                Icon(FocusIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    controlledOnly: Boolean,
    pluginPages: List<PluginPageRef>,
    current: FocusRoute,
    onDismiss: () -> Unit,
    onNavigate: (FocusRoute) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.drawer_more),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.drawer_more_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!controlledOnly) {
                MoreSection(title = stringResource(R.string.drawer_workspace_section)) {
                    MoreTile(
                        icon = FocusIcons.Today,
                        label = stringResource(R.string.tab_today),
                        selected = current == FocusRoute.Today,
                        modifier = Modifier.fillMaxWidth(),
                    ) { onNavigate(FocusRoute.Today) }
                    MoreTile(
                        icon = FocusIcons.Groups,
                        label = stringResource(R.string.tab_groups),
                        selected = current == FocusRoute.Crew,
                        modifier = Modifier.fillMaxWidth(),
                    ) { onNavigate(FocusRoute.Crew) }
                    MoreTile(
                        icon = FocusIcons.Goals,
                        label = stringResource(R.string.tab_goals),
                        selected = current == FocusRoute.Goals,
                        modifier = Modifier.fillMaxWidth(),
                    ) { onNavigate(FocusRoute.Goals) }
                    MoreTile(
                        icon = FocusIcons.Library,
                        label = stringResource(R.string.tab_library),
                        selected = current == FocusRoute.Library,
                        modifier = Modifier.fillMaxWidth(),
                    ) { onNavigate(FocusRoute.Library) }
                }
            }
            if (!controlledOnly && pluginPages.isNotEmpty()) {
                MoreSection(title = stringResource(R.string.plugins_drawer_section)) {
                    pluginPages.forEach { page ->
                        MoreTile(
                            icon = FocusIcons.Tool,
                            label = page.title,
                            selected = current is FocusRoute.PluginPage &&
                                current.pluginId == page.pluginId &&
                                current.pageId == page.pageId,
                            modifier = Modifier.fillMaxWidth(),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            onNavigate(FocusRoute.PluginPage(page.pluginId, page.pageId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun MoreTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit,
) {
    Surface(
        onClick = hapticTap(onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.secondary else container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onSecondary else content,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
