package fan.x0.focus.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.data.assistant.AssistantProfile

@Composable
fun AssistantProfileDialog(
    profile: AssistantProfile?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val editing = profile != null
    var name by remember(profile) { mutableStateOf(profile?.name.orEmpty()) }
    var role by remember(profile) { mutableStateOf(profile?.role.orEmpty()) }
    var emoji by remember(profile) { mutableStateOf(profile?.emoji.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editing) R.string.assistant_profile_edit else R.string.assistant_profile_new,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.assistant_profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it.take(120) },
                    label = { Text(stringResource(R.string.assistant_profile_role)) },
                    placeholder = { Text(stringResource(R.string.assistant_profile_role_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(8) },
                    label = { Text(stringResource(R.string.assistant_profile_emoji)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = hapticTap { onSave(name.trim(), role.trim(), emoji.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(
                    stringResource(
                        if (editing) R.string.assistant_profile_save else R.string.assistant_profile_create,
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
