package fan.x0.focus.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.hapticSelect
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.data.assistant.AssistantKind

@Composable
fun NewAssistantDialog(
    onDismiss: () -> Unit,
    onSave: (AssistantKind, String, String, String) -> Unit,
) {
    var kind by remember { mutableStateOf(AssistantKind.Assistant) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.assistant_profile_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.assistant_type_body),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AssistantKind.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == kind,
                            onClick = hapticSelect { kind = option },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AssistantKind.entries.size,
                            ),
                            label = {
                                Text(
                                    stringResource(
                                        if (option == AssistantKind.Roleplay) {
                                            R.string.assistant_type_roleplay
                                        } else {
                                            R.string.assistant_type_assistant
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = {
                        Text(
                            stringResource(
                                if (kind == AssistantKind.Roleplay) {
                                    R.string.assistant_roleplay_name
                                } else {
                                    R.string.assistant_profile_name
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapeTokens.input,
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it.take(120) },
                    label = {
                        Text(
                            stringResource(
                                if (kind == AssistantKind.Roleplay) {
                                    R.string.assistant_roleplay_role
                                } else {
                                    R.string.assistant_profile_role
                                },
                            ),
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                if (kind == AssistantKind.Roleplay) {
                                    R.string.assistant_roleplay_role_hint
                                } else {
                                    R.string.assistant_profile_role_hint
                                },
                            ),
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapeTokens.input,
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(8) },
                    label = { Text(stringResource(R.string.assistant_profile_emoji)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapeTokens.input,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = hapticTap { onSave(kind, name.trim(), role.trim(), emoji.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(
                    stringResource(
                        if (kind == AssistantKind.Roleplay) {
                            R.string.assistant_roleplay_create
                        } else {
                            R.string.assistant_profile_create
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = hapticTap(onDismiss)) {
                Text(stringResource(R.string.assistant_profile_cancel))
            }
        },
    )
}
