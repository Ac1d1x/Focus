package fan.x0.focus

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fan.x0.focus.app.ShellState
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.hapticSelect
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.data.assistant.AssistantProfile
import fan.x0.focus.data.assistant.Conversation
import fan.x0.focus.feature.assistant.NewAssistantDialog
import fan.x0.focus.plugin.PluginPageRef

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
    onCreateAssistant: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DrawerHeader(
            assistant = state.activeAssistant,
            onOpenSettings = { onNavigate(FocusRoute.Settings) },
        )
        Spacer(Modifier.height(12.dp))
        DrawerSectionLabel(stringResource(R.string.drawer_assistants))
        state.assistants.forEach { assistant ->
            AssistantRow(
                assistant = assistant,
                selected = assistant.id == state.activeAssistantId,
                onClick = { onSelectAssistant(assistant.id) },
            )
        }
        NewAssistantRow(onClick = { creating = true })
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(6.dp))
        DrawerSectionLabel(stringResource(R.string.drawer_sessions))
        DrawerEntry(
            FocusIcons.Assistant,
            stringResource(R.string.drawer_main_session),
            current == FocusRoute.Chat && currentConversationId == state.main?.id,
            onOpenMain,
        )
        if (!controlledOnly) {
            DrawerEntry(
                FocusIcons.Add,
                stringResource(R.string.drawer_new_side_chat),
                selected = false,
                onClick = onNewSideChat,
            )
            state.recent.forEach { conversation ->
                ConversationRow(
                    conversation = conversation,
                    selected = conversation.id == currentConversationId,
                    onClick = { onOpenConversation(conversation.id) },
                )
            }
            DrawerEntry(
                icon = FocusIcons.History,
                label = stringResource(R.string.drawer_history),
                selected = current == FocusRoute.History,
                onClick = { onNavigate(FocusRoute.History) },
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(6.dp))
            DrawerEntry(FocusIcons.Today, stringResource(R.string.tab_today), current == FocusRoute.Today) {
                onNavigate(FocusRoute.Today)
            }
            DrawerEntry(FocusIcons.Groups, stringResource(R.string.tab_groups), current == FocusRoute.Crew) {
                onNavigate(FocusRoute.Crew)
            }
            DrawerEntry(FocusIcons.Goals, stringResource(R.string.tab_goals), current == FocusRoute.Goals) {
                onNavigate(FocusRoute.Goals)
            }
            DrawerEntry(FocusIcons.Library, stringResource(R.string.tab_library), current == FocusRoute.Library) {
                onNavigate(FocusRoute.Library)
            }
            DrawerEntry(FocusIcons.Terminal, stringResource(R.string.tab_bridge), current == FocusRoute.Bridge) {
                onNavigate(FocusRoute.Bridge)
            }
        }
        DrawerEntry(FocusIcons.Device, stringResource(R.string.device_title), current == FocusRoute.Device) {
            onNavigate(FocusRoute.Device)
        }
        if (!controlledOnly) {
            DrawerEntry(FocusIcons.Assistant, stringResource(R.string.drawer_assistant_context), current == FocusRoute.Assistant) {
                onNavigate(FocusRoute.Assistant)
            }
        }
        if (!controlledOnly && pluginPages.isNotEmpty()) {
            DrawerSectionLabel(stringResource(R.string.plugins_drawer_section))
            pluginPages.forEach { page ->
                DrawerEntry(
                    icon = FocusIcons.Tool,
                    label = page.title,
                    selected = current is FocusRoute.PluginPage &&
                        current.pluginId == page.pluginId &&
                        current.pageId == page.pageId,
                ) {
                    onNavigate(FocusRoute.PluginPage(page.pluginId, page.pageId))
                }
            }
        }
        DrawerEntry(FocusIcons.Settings, stringResource(R.string.settings_title), current == FocusRoute.Settings) {
            onNavigate(FocusRoute.Settings)
        }
    }
    if (creating) {
        NewAssistantDialog(
            onDismiss = { creating = false },
            onSave = { name, role, emoji ->
                onCreateAssistant(name, role, emoji)
                creating = false
            },
        )
    }
}

@Composable
private fun DrawerHeader(assistant: AssistantProfile?, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AssistantAvatar(
            assistant = assistant ?: AssistantProfile(AssistantProfile.DEFAULT_ID, AssistantProfile.DEFAULT_NAME),
            size = 52.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = assistant?.name?.ifBlank { AssistantProfile.DEFAULT_NAME } ?: AssistantProfile.DEFAULT_NAME,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = assistant?.role?.ifBlank { null } ?: stringResource(R.string.drawer_assistant_default_role),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = hapticTap(onOpenSettings)) {
            Icon(
                imageVector = FocusIcons.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssistantRow(
    assistant: AssistantProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = hapticSelect(onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AssistantAvatar(assistant = assistant, size = 32.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = assistant.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = assistant.role.ifBlank { stringResource(R.string.drawer_assistant_default_role) },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        scheme.onSecondaryContainer.copy(alpha = 0.72f)
                    } else {
                        scheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NewAssistantRow(onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.drawer_new_assistant)) },
        selected = false,
        onClick = hapticTap(onClick),
        icon = {
            Icon(
                imageVector = FocusIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun AssistantAvatar(assistant: AssistantProfile, size: Dp) {
    if (assistant.emoji.isNotBlank()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = assistant.emoji,
                style = if (size > 40.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            )
        }
    } else {
        val hash = assistant.id.hashCode()
        FocusAvatar(
            style = AvatarStyle(preset = hash, palette = hash ushr 8),
            size = size,
        )
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = conversation.title.ifBlank { stringResource(R.string.drawer_untitled) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
            }
        },
        selected = selected,
        onClick = hapticSelect(onClick),
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun DrawerEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                style = if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
            )
        },
        selected = selected,
        onClick = hapticSelect(onClick),
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}
