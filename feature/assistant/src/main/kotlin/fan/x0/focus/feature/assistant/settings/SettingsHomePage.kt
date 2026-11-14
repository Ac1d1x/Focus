package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.feature.assistant.R

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

    SettingsPageScaffold(title = stringResource(R.string.settings_home_title)) {
        item(key = "intro") {
            FocusReveal {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_home_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "assistant") {
            FocusReveal(index = 1) {
                FocusCardGroup(title = stringResource(R.string.settings_section_assistant)) {
                    pageItem(
                        title = accountTitle,
                        body = accountBody,
                        icon = FocusIcons.Provider,
                        onClick = { onOpen(SettingsDestination.Account) },
                    )
                    pageItem(
                        title = modelsTitle,
                        body = modelsBody,
                        icon = FocusIcons.Groups,
                        onClick = { onOpen(SettingsDestination.Models) },
                    )
                    pageItem(
                        title = chatTitle,
                        body = chatBody,
                        icon = FocusIcons.Bolt,
                        onClick = { onOpen(SettingsDestination.ChatAgent) },
                    )
                    pageItem(
                        title = detailTitle,
                        body = detailBody,
                        icon = FocusIcons.Chat,
                        onClick = { onOpen(SettingsDestination.ChatDetail) },
                    )
                }
            }
        }
        item(key = "capabilities") {
            FocusReveal(index = 2) {
                FocusCardGroup(title = stringResource(R.string.settings_section_capabilities)) {
                    pageItem(
                        title = toolsTitle,
                        body = toolsBody,
                        icon = FocusIcons.Tool,
                        onClick = { onOpen(SettingsDestination.Tools) },
                    )
                    pageItem(
                        title = automationTitle,
                        body = automationBody,
                        icon = FocusIcons.Bolt,
                        onClick = { onOpen(SettingsDestination.Automation) },
                    )
                    pageItem(
                        title = pluginsTitle,
                        body = pluginsBody,
                        icon = FocusIcons.Tool,
                        onClick = { onOpen(SettingsDestination.Plugins) },
                    )
                    if (onOpenBridge != null) {
                        pageItem(
                            title = bridgeTitle,
                            body = bridgeBody,
                            icon = FocusIcons.Terminal,
                            onClick = onOpenBridge,
                        )
                    }
                    pageItem(
                        title = deviceTitle,
                        body = deviceBody,
                        icon = FocusIcons.Device,
                        onClick = onOpenDevice,
                    )
                }
            }
        }
        item(key = "connections") {
            FocusReveal(index = 3) {
                FocusCardGroup(title = stringResource(R.string.settings_section_connections)) {
                    pageItem(
                        title = imTitle,
                        body = imBody,
                        icon = FocusIcons.Link,
                        onClick = { onOpen(SettingsDestination.Im) },
                    )
                    pageItem(
                        title = voiceTitle,
                        body = voiceBody,
                        icon = FocusIcons.Chat,
                        onClick = { onOpen(SettingsDestination.Voice) },
                    )
                    pageItem(
                        title = overlayTitle,
                        body = overlayBody,
                        icon = FocusIcons.Assistant,
                        onClick = { onOpen(SettingsDestination.Overlay) },
                    )
                }
            }
        }
        item(key = "app") {
            FocusReveal(index = 4) {
                FocusCardGroup(title = stringResource(R.string.settings_section_app)) {
                    pageItem(
                        title = appearanceTitle,
                        body = appearanceBody,
                        icon = FocusIcons.Language,
                        onClick = { onOpen(SettingsDestination.Appearance) },
                    )
                    pageItem(
                        title = notificationsTitle,
                        body = notificationsBody,
                        icon = FocusIcons.Notification,
                        onClick = { onOpen(SettingsDestination.Notifications) },
                    )
                    pageItem(
                        title = dataTitle,
                        body = dataBody,
                        icon = FocusIcons.Folder,
                        onClick = { onOpen(SettingsDestination.Data) },
                    )
                    pageItem(
                        title = crashTitle,
                        body = crashBody,
                        icon = FocusIcons.Warning,
                        onClick = { onOpen(SettingsDestination.CrashLogs) },
                    )
                }
            }
        }
        item(key = "about") {
            FocusReveal(index = 5) {
                FocusCardGroup {
                    pageItem(
                        title = aboutTitle,
                        body = aboutBody,
                        icon = FocusIcons.Info,
                        onClick = { onOpen(SettingsDestination.About) },
                    )
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
