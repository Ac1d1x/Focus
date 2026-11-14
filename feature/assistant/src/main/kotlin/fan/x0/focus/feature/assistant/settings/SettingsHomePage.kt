package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.feature.assistant.R

private data class SettingsEntry(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private data class SettingsSection(
    val key: String,
    val title: String?,
    val entries: List<SettingsEntry>,
)

@Composable
internal fun SettingsHomePage(
    onOpen: (SettingsDestination) -> Unit,
    onOpenBridge: (() -> Unit)?,
    onOpenDevice: () -> Unit,
) {
    val accountTitle = stringResource(R.string.settings_page_account)
    val accountBody = stringResource(R.string.settings_page_account_body)
    val modelsTitle = stringResource(R.string.settings_page_models)
    val modelsBody = stringResource(R.string.settings_page_models_body)
    val chatTitle = stringResource(R.string.settings_page_chat_agent)
    val chatBody = stringResource(R.string.settings_page_chat_agent_body)
    val detailTitle = stringResource(R.string.settings_page_chat_detail)
    val detailBody = stringResource(R.string.settings_page_chat_detail_body)
    val toolsTitle = stringResource(R.string.settings_page_tools)
    val toolsBody = stringResource(R.string.settings_page_tools_body)
    val automationTitle = stringResource(R.string.settings_page_automation)
    val automationBody = stringResource(R.string.settings_page_automation_body)
    val pluginsTitle = stringResource(R.string.settings_page_plugins)
    val pluginsBody = stringResource(R.string.settings_page_plugins_body)
    val bridgeTitle = stringResource(R.string.settings_page_bridge)
    val bridgeBody = stringResource(R.string.settings_page_bridge_body)
    val deviceTitle = stringResource(R.string.settings_page_device)
    val deviceBody = stringResource(R.string.settings_page_device_body)
    val imTitle = stringResource(R.string.settings_page_im)
    val imBody = stringResource(R.string.settings_page_im_body)
    val voiceTitle = stringResource(R.string.settings_page_voice)
    val voiceBody = stringResource(R.string.settings_page_voice_body)
    val overlayTitle = stringResource(R.string.settings_page_overlay)
    val overlayBody = stringResource(R.string.settings_page_overlay_body)
    val appearanceTitle = stringResource(R.string.settings_page_appearance)
    val appearanceBody = stringResource(R.string.settings_page_appearance_body)
    val notificationsTitle = stringResource(R.string.settings_page_notifications)
    val notificationsBody = stringResource(R.string.settings_page_notifications_body)
    val dataTitle = stringResource(R.string.settings_page_data)
    val dataBody = stringResource(R.string.settings_page_data_body)
    val crashTitle = stringResource(R.string.settings_page_crash_logs)
    val crashBody = stringResource(R.string.settings_page_crash_logs_body)
    val aboutTitle = stringResource(R.string.settings_page_about)
    val aboutBody = stringResource(R.string.settings_page_about_body)

    val sections = listOf(
        SettingsSection(
            key = "assistant",
            title = stringResource(R.string.settings_section_assistant),
            entries = listOf(
                SettingsEntry(accountTitle, accountBody, FocusIcons.Provider) {
                    onOpen(SettingsDestination.Account)
                },
                SettingsEntry(modelsTitle, modelsBody, FocusIcons.Groups) {
                    onOpen(SettingsDestination.Models)
                },
                SettingsEntry(chatTitle, chatBody, FocusIcons.Bolt) {
                    onOpen(SettingsDestination.ChatAgent)
                },
                SettingsEntry(detailTitle, detailBody, FocusIcons.Chat) {
                    onOpen(SettingsDestination.ChatDetail)
                },
            ),
        ),
        SettingsSection(
            key = "capabilities",
            title = stringResource(R.string.settings_section_capabilities),
            entries = listOfNotNull(
                SettingsEntry(toolsTitle, toolsBody, FocusIcons.Tool) {
                    onOpen(SettingsDestination.Tools)
                },
                SettingsEntry(automationTitle, automationBody, FocusIcons.Bolt) {
                    onOpen(SettingsDestination.Automation)
                },
                SettingsEntry(pluginsTitle, pluginsBody, FocusIcons.Tool) {
                    onOpen(SettingsDestination.Plugins)
                },
                onOpenBridge?.let { open ->
                    SettingsEntry(bridgeTitle, bridgeBody, FocusIcons.Terminal, open)
                },
                SettingsEntry(deviceTitle, deviceBody, FocusIcons.Device, onOpenDevice),
            ),
        ),
        SettingsSection(
            key = "connections",
            title = stringResource(R.string.settings_section_connections),
            entries = listOf(
                SettingsEntry(imTitle, imBody, FocusIcons.Link) {
                    onOpen(SettingsDestination.Im)
                },
                SettingsEntry(voiceTitle, voiceBody, FocusIcons.Chat) {
                    onOpen(SettingsDestination.Voice)
                },
                SettingsEntry(overlayTitle, overlayBody, FocusIcons.Assistant) {
                    onOpen(SettingsDestination.Overlay)
                },
            ),
        ),
        SettingsSection(
            key = "app",
            title = stringResource(R.string.settings_section_app),
            entries = listOf(
                SettingsEntry(appearanceTitle, appearanceBody, FocusIcons.Language) {
                    onOpen(SettingsDestination.Appearance)
                },
                SettingsEntry(notificationsTitle, notificationsBody, FocusIcons.Notification) {
                    onOpen(SettingsDestination.Notifications)
                },
                SettingsEntry(dataTitle, dataBody, FocusIcons.Folder) {
                    onOpen(SettingsDestination.Data)
                },
                SettingsEntry(crashTitle, crashBody, FocusIcons.Warning) {
                    onOpen(SettingsDestination.CrashLogs)
                },
            ),
        ),
        SettingsSection(
            key = "about",
            title = null,
            entries = listOf(
                SettingsEntry(aboutTitle, aboutBody, FocusIcons.Info) {
                    onOpen(SettingsDestination.About)
                },
            ),
        ),
    )

    var query by rememberSaveable { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        sections
    } else {
        sections.mapNotNull { section ->
            if (section.title?.contains(query, ignoreCase = true) == true) {
                section
            } else {
                val entries = section.entries.filter {
                    it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
                }
                if (entries.isEmpty()) null else section.copy(entries = entries)
            }
        }
    }

    SettingsPageScaffold(title = stringResource(R.string.settings_home_title)) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                leadingIcon = {
                    Icon(FocusIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = if (query.isBlank()) {
                    null
                } else {
                    {
                        IconButton(onClick = hapticTap { query = "" }) {
                            Icon(
                                imageVector = FocusIcons.Close,
                                contentDescription = stringResource(R.string.settings_search_clear),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = FocusShapeTokens.input,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.settings_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                )
            }
        }
        filtered.forEachIndexed { index, section ->
            item(key = "section_${section.key}") {
                FocusReveal(index = index) {
                    FocusCardGroup(title = section.title) {
                        section.entries.forEach { entry ->
                            pageItem(
                                title = entry.title,
                                body = entry.body,
                                icon = entry.icon,
                                onClick = entry.onClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun FocusCardGroupScope.pageItem(
    title: String,
    body: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    item(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        leadingContent = {
            FocusCardGroupIcon(icon = icon)
        },
        trailingContent = {
            Icon(
                imageVector = FocusIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}
