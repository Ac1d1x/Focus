package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCard
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupValue
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusSectionHeader
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.FocusTonalButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.switchItem
import fan.x0.focus.core.designsystem.kit.ExConfirm
import fan.x0.focus.core.designsystem.kit.ExField
import fan.x0.focus.core.designsystem.kit.ExSearch
import fan.x0.focus.core.designsystem.kit.ExSheet
import fan.x0.focus.feature.assistant.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun SettingsDebugPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var chip by remember { mutableIntStateOf(0) }
    var switchOn by remember { mutableStateOf(true) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_debug_title),
        onBack = onBack,
    ) {
        item(key = "debug_buttons") {
            DebugSection(index = 0, title = stringResource(R.string.settings_debug_buttons), first = true) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusPrimaryButton(text = "Primary", onClick = {}, icon = FocusIcons.Check)
                    FocusTonalButton(text = "Tonal", onClick = {})
                    FocusOutlinedButton(text = "Outlined", onClick = {})
                    FocusTextButton(text = "Text", onClick = {})
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FocusPrimaryButton(
                            text = stringResource(R.string.settings_debug_loading),
                            onClick = {},
                            loading = true,
                        )
                        FocusPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
                    }
                    FocusLoadingBlob(size = 28.dp)
                }
            }
        }
        item(key = "debug_chips") {
            DebugSection(index = 1, title = stringResource(R.string.settings_debug_chips)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Alpha", "Beta", "Gamma").forEachIndexed { index, label ->
                        FocusChip(
                            label = label,
                            selected = chip == index,
                            onClick = { chip = index },
                        )
                    }
                }
            }
        }
        item(key = "debug_status") {
            DebugSection(index = 2, title = stringResource(R.string.settings_debug_status)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusStatusPill("Neutral", StatusTone.Neutral)
                    FocusStatusPill("Active", StatusTone.Active)
                    FocusStatusPill("Success", StatusTone.Success)
                    FocusStatusPill("Warning", StatusTone.Warning)
                    FocusStatusPill("Danger", StatusTone.Danger)
                }
            }
        }
        item(key = "debug_fields") {
            DebugSection(index = 3, title = stringResource(R.string.settings_debug_fields)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExField(
                        value = field,
                        onChange = { field = it },
                        label = stringResource(R.string.settings_debug_field_label),
                        placeholder = stringResource(R.string.settings_debug_field_hint),
                    )
                    ExSearch(
                        value = search,
                        onChange = { search = it },
                        placeholder = stringResource(R.string.settings_debug_search),
                    )
                }
            }
        }
        item(key = "debug_cards") {
            DebugSection(index = 4, title = stringResource(R.string.settings_debug_cards)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusCard {
                        Text(
                            text = stringResource(R.string.settings_debug_card_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_debug_card_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FocusCardGroup {
                        item(
                            headlineContent = { Text(stringResource(R.string.settings_debug_switch)) },
                            supportingContent = { Text(stringResource(R.string.settings_debug_card_body)) },
                            trailingContent = { FocusCardGroupValue(if (switchOn) "On" else "Off") },
                            onClick = { switchOn = !switchOn },
                        )
                        switchItem(
                            checked = switchOn,
                            onCheckedChange = { switchOn = it },
                            headlineContent = { Text(stringResource(R.string.settings_debug_switch)) },
                        )
                    }
                }
            }
        }
        item(key = "debug_overlays") {
            DebugSection(index = 5, title = stringResource(R.string.settings_debug_overlays)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusTonalButton(
                        text = stringResource(R.string.settings_debug_sheet),
                        onClick = { sheetOpen = true },
                        modifier = Modifier.weight(1f),
                    )
                    FocusTonalButton(
                        text = stringResource(R.string.settings_debug_dialog),
                        onClick = { dialogOpen = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item(key = "debug_crash") {
            DebugSection(
                index = 6,
                title = stringResource(R.string.settings_debug_crash),
                subtitle = stringResource(R.string.settings_debug_crash_hint),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusOutlinedButton(
                        text = stringResource(R.string.settings_debug_crash_main),
                        onClick = { throw IllegalStateException("Focus debug crash: main thread") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FocusOutlinedButton(
                        text = stringResource(R.string.settings_debug_crash_background),
                        onClick = {
                            Thread {
                                throw IllegalStateException("Focus debug crash: background thread")
                            }.start()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FocusOutlinedButton(
                        text = stringResource(R.string.settings_debug_crash_coroutine),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                throw IllegalStateException("Focus debug crash: coroutine")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        ExSheet(
            onDismiss = { sheetOpen = false },
            title = stringResource(R.string.settings_debug_sheet_title),
        ) {
            Text(
                text = stringResource(R.string.settings_debug_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            FocusPrimaryButton(
                text = stringResource(R.string.settings_debug_close),
                onClick = { sheetOpen = false },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (dialogOpen) {
        ExConfirm(
            title = stringResource(R.string.settings_debug_dialog_title),
            text = stringResource(R.string.settings_debug_dialog_body),
            confirm = stringResource(R.string.settings_debug_close),
            onConfirm = { dialogOpen = false },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun DebugSection(
    index: Int,
    title: String,
    subtitle: String? = null,
    first: Boolean = false,
    content: @Composable () -> Unit,
) {
    FocusReveal(index = index) {
        Column {
            FocusSectionHeader(
                title = title,
                subtitle = subtitle,
                modifier = if (first) Modifier else Modifier.padding(top = 20.dp),
            )
            Column(modifier = Modifier.padding(top = 10.dp)) {
                content()
            }
        }
    }
}
