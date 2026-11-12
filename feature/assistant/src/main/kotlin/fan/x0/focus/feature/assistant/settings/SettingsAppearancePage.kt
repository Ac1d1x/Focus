package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.data.assistant.AssistantReplyStyle
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

@Composable
internal fun SettingsAppearancePage(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_appearance),
        onBack = onBack,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_page_appearance_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "language") {
            FocusReveal(index = 1) {
                FocusCardGroup(title = stringResource(R.string.settings_language_title)) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.settings_language_item)) },
                        supportingContent = { Text(stringResource(R.string.settings_language_body)) },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = FocusIcons.Language,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        },
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FocusChip(
                                label = stringResource(R.string.assistant_language_system),
                                selected = state.language == null,
                                onClick = { onAction(SettingsAction.LanguageChanged(null)) },
                            )
                            FocusChip(
                                label = stringResource(R.string.assistant_language_zh),
                                selected = state.language == "zh",
                                onClick = { onAction(SettingsAction.LanguageChanged("zh")) },
                            )
                            FocusChip(
                                label = stringResource(R.string.assistant_language_en),
                                selected = state.language == "en",
                                onClick = { onAction(SettingsAction.LanguageChanged("en")) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "reply_style") {
            FocusReveal(index = 2) {
                FocusCardGroup(title = stringResource(R.string.settings_reply_style_title)) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.settings_reply_style_item)) },
                        supportingContent = { Text(stringResource(R.string.settings_reply_style_body)) },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = FocusIcons.Chat,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistantReplyStyle.entries.forEach { style ->
                                FocusChip(
                                    label = stringResource(style.labelRes()),
                                    selected = state.replyStyle == style,
                                    onClick = { onAction(SettingsAction.ReplyStyleChanged(style)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun AssistantReplyStyle.labelRes(): Int = when (this) {
    AssistantReplyStyle.Flat -> R.string.settings_reply_style_flat
    AssistantReplyStyle.Bubble -> R.string.settings_reply_style_bubble
    AssistantReplyStyle.Card -> R.string.settings_reply_style_card
}
