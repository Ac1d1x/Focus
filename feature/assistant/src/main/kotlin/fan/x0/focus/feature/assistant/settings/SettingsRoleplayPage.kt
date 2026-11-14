package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.switchItem
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.roleplay.RoleplayUserPersona
import fan.x0.focus.feature.assistant.AssistantAvatar
import fan.x0.focus.feature.assistant.R
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsRoleplayRoute(
    onBack: () -> Unit,
    onOpenAssistant: (String) -> Unit,
    viewModel: RoleplaySettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HandleAppErrors(viewModel.errors)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is RoleplaySettingsEffect.OpenAssistant -> onOpenAssistant(effect.id)
        }
    }
    SettingsRoleplayPage(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
internal fun SettingsRoleplayPage(
    state: RoleplaySettingsUiState,
    onAction: (RoleplaySettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_roleplay),
        onBack = onBack,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_page_roleplay_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "assistants") {
            FocusReveal(index = 1) {
                FocusCardGroup(title = stringResource(R.string.settings_roleplay_assistants)) {
                    if (state.assistants.isEmpty()) {
                        item(
                            headlineContent = { Text(stringResource(R.string.settings_roleplay_empty)) },
                            supportingContent = { Text(stringResource(R.string.settings_roleplay_empty_body)) },
                            leadingContent = {
                                FocusCardGroupIcon(
                                    icon = FocusIcons.Person,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            },
                        )
                    } else {
                        state.assistants.forEach { assistant ->
                            item(
                                headlineContent = { Text(assistant.name) },
                                supportingContent = {
                                    Text(
                                        assistant.role.ifBlank {
                                            stringResource(R.string.settings_roleplay_assistant_body)
                                        },
                                    )
                                },
                                leadingContent = { AssistantAvatar(assistant = assistant, size = 36.dp) },
                                trailingContent = {
                                    Text(
                                        text = stringResource(R.string.settings_roleplay_open),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    onAction(RoleplaySettingsAction.OpenAssistant(assistant.id))
                                },
                            )
                        }
                    }
                }
            }
        }
        item(key = "chat") {
            FocusReveal(index = 2) {
                FocusCardGroup(title = stringResource(R.string.settings_roleplay_chat)) {
                    switchItem(
                        checked = state.config.immersive,
                        onCheckedChange = {
                            onAction(RoleplaySettingsAction.UpdateConfig(state.config.copy(immersive = it)))
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_immersive)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_immersive_body)) },
                    )
                    switchItem(
                        checked = state.config.splitBubbles,
                        onCheckedChange = {
                            onAction(RoleplaySettingsAction.UpdateConfig(state.config.copy(splitBubbles = it)))
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_split)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_split_body)) },
                    )
                    switchItem(
                        checked = state.config.typing,
                        onCheckedChange = {
                            onAction(RoleplaySettingsAction.UpdateConfig(state.config.copy(typing = it)))
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_typing)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_typing_body)) },
                    )
                    switchItem(
                        checked = state.config.stripMarkdown,
                        onCheckedChange = {
                            onAction(RoleplaySettingsAction.UpdateConfig(state.config.copy(stripMarkdown = it)))
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_markdown)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_markdown_body)) },
                    )
                    switchItem(
                        checked = state.config.showToolTrace,
                        onCheckedChange = {
                            onAction(RoleplaySettingsAction.UpdateConfig(state.config.copy(showToolTrace = it)))
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_trace)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_trace_body)) },
                    )
                    switchItem(
                        checked = state.config.typoChance > 0f,
                        onCheckedChange = {
                            onAction(
                                RoleplaySettingsAction.UpdateConfig(
                                    state.config.copy(typoChance = if (it) 0.05f else 0f),
                                ),
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_typo)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_typo_body)) },
                    )
                    sliderItem(
                        title = R.string.settings_roleplay_read_delay,
                        value = state.config.readDelayMs.toFloat(),
                        range = 300f..4_000f,
                        steps = 36,
                        label = "${state.config.readDelayMs} ms",
                        onChange = {
                            onAction(
                                RoleplaySettingsAction.UpdateConfig(
                                    state.config.copy(readDelayMs = it.roundToInt()),
                                ),
                            )
                        },
                    )
                    sliderItem(
                        title = R.string.settings_roleplay_gap,
                        value = state.config.bubbleGapMs.toFloat(),
                        range = 100f..2_000f,
                        steps = 18,
                        label = "${state.config.bubbleGapMs} ms",
                        onChange = {
                            onAction(
                                RoleplaySettingsAction.UpdateConfig(
                                    state.config.copy(bubbleGapMs = it.roundToInt()),
                                ),
                            )
                        },
                    )
                    sliderItem(
                        title = R.string.settings_roleplay_pace,
                        value = state.config.paceScale,
                        range = 0.5f..1.8f,
                        steps = 12,
                        label = "${(state.config.paceScale * 100).roundToInt()}%",
                        onChange = {
                            onAction(
                                RoleplaySettingsAction.UpdateConfig(
                                    state.config.copy(paceScale = it),
                                ),
                            )
                        },
                    )
                }
            }
        }
        item(key = "proactive") {
            FocusReveal(index = 3) {
                FocusCardGroup(title = stringResource(R.string.settings_roleplay_proactive)) {
                    switchItem(
                        checked = state.config.proactive,
                        onCheckedChange = {
                            onAction(RoleplaySettingsAction.UpdateConfig(state.config.copy(proactive = it)))
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_proactive_on)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_proactive_body)) },
                    )
                    switchItem(
                        checked = state.config.quietEnabled,
                        onCheckedChange = {
                            onAction(RoleplaySettingsAction.UpdateConfig(state.config.copy(quietEnabled = it)))
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_quiet)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_quiet_body)) },
                    )
                    switchItem(
                        checked = state.config.morningGreeting,
                        onCheckedChange = {
                            onAction(RoleplaySettingsAction.UpdateConfig(state.config.copy(morningGreeting = it)))
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_morning)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_morning_body)) },
                    )
                    switchItem(
                        checked = state.config.eveningGreeting,
                        onCheckedChange = {
                            onAction(RoleplaySettingsAction.UpdateConfig(state.config.copy(eveningGreeting = it)))
                        },
                        headlineContent = { Text(stringResource(R.string.settings_roleplay_evening)) },
                        supportingContent = { Text(stringResource(R.string.settings_roleplay_evening_body)) },
                    )
                }
            }
        }
        item(key = "user") {
            FocusReveal(index = 4) {
                UserPersonaCard(state = state, onAction = onAction)
            }
        }
        item(key = "stickers") {
            FocusReveal(index = 5) {
                StickerCard(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun StickerCard(
    state: RoleplaySettingsUiState,
    onAction: (RoleplaySettingsAction) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var emotion by remember { mutableStateOf("") }
    FocusCardGroup(title = stringResource(R.string.settings_roleplay_stickers)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_roleplay_sticker_add)) },
            supportingContent = { Text(stringResource(R.string.settings_roleplay_sticker_hint)) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.take(8) },
                        label = { Text(stringResource(R.string.settings_roleplay_sticker_emoji)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = FocusShapeTokens.input,
                    )
                    OutlinedTextField(
                        value = emotion,
                        onValueChange = { emotion = it.take(20) },
                        label = { Text(stringResource(R.string.settings_roleplay_sticker_emotion)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = FocusShapeTokens.input,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(R.string.settings_roleplay_sticker_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapeTokens.input,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FocusPrimaryButton(
                        text = stringResource(R.string.settings_roleplay_sticker_save),
                        onClick = {
                            if (value.isNotBlank()) {
                                onAction(RoleplaySettingsAction.AddSticker(value.trim(), name.trim(), emotion.trim()))
                                value = ""
                                name = ""
                                emotion = ""
                            }
                        },
                        enabled = value.isNotBlank(),
                    )
                }
            }
        }
        state.stickers.forEach { sticker ->
            item(
                headlineContent = {
                    Text(
                        text = listOf(sticker.value, sticker.name.ifBlank { sticker.emotion })
                            .filter { it.isNotBlank() }
                            .joinToString("  "),
                    )
                },
                supportingContent = {
                    Text(sticker.emotion.ifBlank { sticker.category })
                },
                trailingContent = {
                    TextButton(
                        onClick = { onAction(RoleplaySettingsAction.DeleteSticker(sticker.id)) },
                    ) {
                        Text(
                            text = stringResource(R.string.settings_roleplay_sticker_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun UserPersonaCard(
    state: RoleplaySettingsUiState,
    onAction: (RoleplaySettingsAction) -> Unit,
) {
    var name by remember(state.userPersona) { mutableStateOf(state.userPersona.name) }
    var description by remember(state.userPersona) { mutableStateOf(state.userPersona.description) }
    LaunchedEffect(state.userPersona) {
        name = state.userPersona.name
        description = state.userPersona.description
    }
    FocusCardGroup(title = stringResource(R.string.settings_roleplay_me)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_roleplay_me_body)) },
            supportingContent = { Text(stringResource(R.string.settings_roleplay_me_hint)) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.settings_roleplay_me_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapeTokens.input,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(4000) },
                    label = { Text(stringResource(R.string.settings_roleplay_me_description)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapeTokens.input,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FocusPrimaryButton(
                        text = stringResource(R.string.settings_roleplay_me_save),
                        onClick = {
                            onAction(
                                RoleplaySettingsAction.SaveUserPersona(
                                    RoleplayUserPersona(
                                        name = name.trim(),
                                        description = description.trim(),
                                        avatar = state.userPersona.avatar,
                                    ),
                                ),
                            )
                        },
                        enabled = name.trim() != state.userPersona.name ||
                            description.trim() != state.userPersona.description,
                    )
                }
            }
        }
    }
}

private fun FocusCardGroupScope.sliderItem(
    @androidx.annotation.StringRes title: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String,
    onChange: (Float) -> Unit,
) {
    formItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(label) },
    ) {
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
