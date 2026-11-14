package fan.x0.focus.feature.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLargeTopBar
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusSnackbarHost
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.LocalFocusSettingsOpener
import fan.x0.focus.core.designsystem.kit.BtnKind
import fan.x0.focus.core.designsystem.kit.ExButton
import fan.x0.focus.core.designsystem.kit.ExField
import fan.x0.focus.core.designsystem.kit.ExSheet
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.AssistantAvatarStore
import fan.x0.focus.data.assistant.AssistantProfile
import fan.x0.focus.data.assistant.WorkspaceFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AssistantRoute(viewModel: AssistantViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val promptSaved = stringResource(R.string.assistant_prompts_saved)
    val profileSaved = stringResource(R.string.assistant_profile_saved)
    val profileDeleted = stringResource(R.string.assistant_profile_deleted)
    HandleAppErrors(viewModel.errors)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            AssistantEffect.PromptSaved -> snackbar.showSnackbar(promptSaved)
            AssistantEffect.ProfileSaved -> snackbar.showSnackbar(profileSaved)
            AssistantEffect.ProfileDeleted -> snackbar.showSnackbar(profileDeleted)
        }
    }
    AssistantScreen(state = state, onAction = viewModel::onAction, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onAction: (AssistantAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val openSettings = LocalFocusSettingsOpener.current
    var deletingProfile by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { FocusSnackbarHost(snackbar) },
        topBar = {
            FocusLargeTopBar(
                title = stringResource(R.string.assistant_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = hapticTap(openSettings)) {
                        Icon(
                            FocusIcons.Settings,
                            contentDescription = stringResource(R.string.assistant_settings_row),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = inner.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                end = inner.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero") {
                FocusReveal {
                    AssistantHero(state)
                }
            }
            item(key = "profile") {
                FocusReveal(index = 1) {
                    ProfileGroup(
                        state = state,
                        onSave = { name, role, emoji, avatar ->
                            onAction(AssistantAction.SaveProfile(name, role, emoji, avatar))
                        },
                        onDelete = { deletingProfile = true },
                    )
                }
            }
            item(key = "workspace") {
                FocusReveal(index = 2) {
                    WorkspaceGroup(state = state, onAction = onAction)
                }
            }
            item(key = "prompts") {
                FocusReveal(index = 3) {
                    PromptGroup(state = state, onAction = onAction)
                }
            }
            item(key = "more") {
                FocusReveal(index = 4) {
                    FocusCardGroup(title = stringResource(R.string.assistant_more)) {
                        item(
                            headlineContent = { Text(stringResource(R.string.assistant_settings_row)) },
                            supportingContent = { Text(stringResource(R.string.assistant_settings_row_body)) },
                            leadingContent = {
                                FocusCardGroupIcon(
                                    icon = FocusIcons.Settings,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    FocusIcons.Chevron,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = openSettings,
                        )
                    }
                }
            }
        }
    }
    if (deletingProfile) {
        AlertDialog(
            onDismissRequest = { deletingProfile = false },
            title = { Text(stringResource(R.string.assistant_profile_delete_title)) },
            text = { Text(stringResource(R.string.assistant_profile_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = hapticTap {
                        onAction(AssistantAction.DeleteProfile)
                        deletingProfile = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.assistant_profile_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = hapticTap { deletingProfile = false }) {
                    Text(stringResource(R.string.assistant_profile_cancel))
                }
            },
        )
    }
    state.openDocument?.let { document ->
        DocumentSheet(
            document = document,
            onClose = { onAction(AssistantAction.CloseDocument) },
        )
    }
    state.promptEditor?.let { editor ->
        PromptEditorSheet(
            editor = editor,
            saving = state.promptSaving,
            onValueChange = { onAction(AssistantAction.PromptChanged(it)) },
            onClose = { onAction(AssistantAction.ClosePrompt) },
            onSave = { onAction(AssistantAction.SavePrompt) },
        )
    }
}

@Composable
private fun ProfileGroup(
    state: AssistantUiState,
    onSave: (String, String, String, String) -> Unit,
    onDelete: () -> Unit,
) {
    val avatarStore: AssistantAvatarStore = koinInject()
    val scope = rememberCoroutineScope()
    var name by remember(state.assistantId) { mutableStateOf(state.assistantName) }
    var role by remember(state.assistantId) { mutableStateOf(state.assistantRole) }
    var emoji by remember(state.assistantId) { mutableStateOf(state.assistantEmoji) }
    var avatar by remember(state.assistantId) { mutableStateOf(state.assistantAvatar) }
    var dirty by remember(state.assistantId) { mutableStateOf(false) }
    var avatarError by remember(state.assistantId) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val imported = avatarStore.import(uri.toString(), state.assistantId)
                    if (imported.isNotBlank()) {
                        avatar = imported
                        avatarError = false
                        dirty = name.isBlank()
                        if (name.isNotBlank()) {
                            onSave(name.trim(), role.trim(), emoji.trim(), imported)
                        }
                    } else {
                        avatarError = true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    avatarError = true
                }
            }
        }
    }
    LaunchedEffect(state.assistantId) {
        dirty = false
        name = state.assistantName
        role = state.assistantRole
        emoji = state.assistantEmoji
        avatar = state.assistantAvatar
        avatarError = false
    }
    val changed = name != state.assistantName || role != state.assistantRole ||
        emoji != state.assistantEmoji || avatar != state.assistantAvatar
    LaunchedEffect(state.assistantName, state.assistantRole, state.assistantEmoji, state.assistantAvatar) {
        if (!dirty) {
            name = state.assistantName
            role = state.assistantRole
            emoji = state.assistantEmoji
            avatar = state.assistantAvatar
        }
    }
    FocusCardGroup(title = stringResource(R.string.assistant_profile_section)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.assistant_profile_form_title)) },
            supportingContent = { Text(stringResource(R.string.assistant_profile_form_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Person,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AssistantAvatar(
                        assistant = AssistantProfile(
                            id = state.assistantId,
                            name = name,
                            emoji = emoji,
                            role = role,
                            avatar = avatar,
                        ),
                        size = 64.dp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FocusTextButton(
                            text = stringResource(R.string.assistant_profile_avatar_pick),
                            onClick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                        if (avatar.isNotBlank()) {
                            TextButton(
                                onClick = hapticTap {
                                    avatar = ""
                                    avatarError = false
                                    dirty = name.isBlank()
                                    if (name.isNotBlank()) {
                                        onSave(name.trim(), role.trim(), emoji.trim(), "")
                                    }
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.assistant_profile_avatar_remove),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (avatarError) {
                            Text(
                                text = stringResource(R.string.assistant_profile_avatar_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(40)
                        dirty = true
                    },
                    label = { Text(stringResource(R.string.assistant_profile_name_item)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapeTokens.input,
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = {
                        role = it.take(120)
                        dirty = true
                    },
                    label = { Text(stringResource(R.string.assistant_profile_role_item)) },
                    placeholder = { Text(stringResource(R.string.assistant_profile_role_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapeTokens.input,
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = {
                        emoji = it.take(8)
                        dirty = true
                    },
                    label = { Text(stringResource(R.string.assistant_profile_emoji_item)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapeTokens.input,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.canDelete) {
                        TextButton(onClick = hapticTap(onDelete)) {
                            Text(
                                text = stringResource(R.string.assistant_profile_delete_item),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FocusPrimaryButton(
                        text = stringResource(R.string.assistant_profile_save),
                        onClick = {
                            val nextName = name.trim()
                            val nextRole = role.trim()
                            val nextEmoji = emoji.trim()
                            name = nextName
                            role = nextRole
                            emoji = nextEmoji
                            dirty = false
                            onSave(nextName, nextRole, nextEmoji, avatar)
                        },
                        enabled = name.isNotBlank() && changed,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceGroup(state: AssistantUiState, onAction: (AssistantAction) -> Unit) {
    val entries = state.documents.map { it.file to it.file.meta() }
    FocusCardGroup(title = stringResource(R.string.assistant_workspace_title)) {
        entries.forEach { (file, meta) ->
            item(
                headlineContent = { Text(meta.title, modifier = Modifier.fillMaxWidth()) },
                supportingContent = { Text(meta.body) },
                leadingContent = {
                    FocusCardGroupIcon(
                        icon = meta.icon,
                        containerColor = meta.container(),
                        contentColor = meta.content(),
                    )
                },
                trailingContent = {
                    Icon(
                        FocusIcons.Chevron,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { onAction(AssistantAction.OpenDocument(file)) },
            )
        }
        if (state.documents.isEmpty()) {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_workspace_empty)) },
                supportingContent = { Text(stringResource(R.string.assistant_workspace_body)) },
            )
        }
    }
}

@Composable
private fun PromptGroup(state: AssistantUiState, onAction: (AssistantAction) -> Unit) {
    val title = stringResource(R.string.assistant_prompts_title)
    val main = stringResource(R.string.assistant_prompts_main)
    val side = stringResource(R.string.assistant_prompts_side)
    val empty = stringResource(R.string.assistant_prompts_empty)
    FocusCardGroup(title = title) {
        promptItem(
            target = PromptTarget.Main,
            label = main,
            empty = empty,
            value = state.mainPrompt,
            icon = FocusIcons.Chat,
            onAction = onAction,
        )
        promptItem(
            target = PromptTarget.Side,
            label = side,
            empty = empty,
            value = state.sidePrompt,
            icon = FocusIcons.Groups,
            onAction = onAction,
        )
    }
}

private fun FocusCardGroupScope.promptItem(
    target: PromptTarget,
    label: String,
    empty: String,
    value: String,
    icon: ImageVector,
    onAction: (AssistantAction) -> Unit,
) {
    val preview = value.lineSequence().firstOrNull { it.isNotBlank() }?.take(60)
    item(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(preview ?: empty)
        },
        leadingContent = {
            FocusCardGroupIcon(icon = icon)
        },
        trailingContent = {
            Icon(
                FocusIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = { onAction(AssistantAction.OpenPrompt(target)) },
    )
}

@Composable
private fun PromptEditorSheet(
    editor: PromptEditorUi,
    saving: Boolean,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    val title = stringResource(
        if (editor.target == PromptTarget.Main) R.string.assistant_prompts_main else R.string.assistant_prompts_side,
    )
    val body = stringResource(
        if (editor.target == PromptTarget.Main) {
            R.string.assistant_prompts_main_body
        } else {
            R.string.assistant_prompts_side_body
        },
    )
    val hint = stringResource(
        if (editor.target == PromptTarget.Main) {
            R.string.assistant_prompts_main_hint
        } else {
            R.string.assistant_prompts_side_hint
        },
    )
    ExSheet(onDismiss = onClose, title = title, full = true) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExField(
            value = editor.value,
            onChange = onValueChange,
            label = hint,
            singleLine = false,
            minLines = 8,
            maxLines = 20,
            supporting = stringResource(R.string.assistant_prompts_empty),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.weight(1f))
            ExButton(
                text = stringResource(R.string.assistant_prompts_cancel),
                onClick = onClose,
                kind = BtnKind.Text,
            )
            ExButton(
                text = stringResource(R.string.assistant_prompts_save),
                onClick = onSave,
                loading = saving,
                enabled = !saving,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentSheet(document: WorkspaceDocumentUi, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.file.fileName,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.assistant_workspace_readonly),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = hapticTap(onClose)) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.assistant_workspace_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = document.content.ifBlank {
                        stringResource(R.string.assistant_workspace_empty)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AssistantHero(state: AssistantUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AssistantAvatar(
            assistant = state.profile ?: AssistantProfile(
                id = state.assistantId,
                name = state.assistantName,
                emoji = state.assistantEmoji,
                role = state.assistantRole,
                avatar = state.assistantAvatar,
            ),
            size = 88.dp,
        )
        Text(
            text = state.assistantName.ifBlank { stringResource(R.string.assistant_hero_default) },
            style = LocalFocusTypography.current.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = state.assistantRole.ifBlank { stringResource(R.string.assistant_hero_hint) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class FileMeta(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val tone: Int,
)

@Composable
private fun WorkspaceFile.meta(): FileMeta = when (this) {
    WorkspaceFile.Soul -> FileMeta(
        title = "SOUL.md",
        body = stringResource(R.string.assistant_file_soul),
        icon = FocusIcons.Assistant,
        tone = 0,
    )
    WorkspaceFile.Persona -> FileMeta(
        title = "PERSONA.md",
        body = stringResource(R.string.assistant_file_persona),
        icon = FocusIcons.Person,
        tone = 1,
    )
    WorkspaceFile.User -> FileMeta(
        title = "USER.md",
        body = stringResource(R.string.assistant_file_user),
        icon = FocusIcons.Chat,
        tone = 2,
    )
    WorkspaceFile.Agent -> FileMeta(
        title = "AGENT.md",
        body = stringResource(R.string.assistant_file_agent),
        icon = FocusIcons.Idea,
        tone = 0,
    )
    WorkspaceFile.Memory -> FileMeta(
        title = "MEMORY.md",
        body = stringResource(R.string.assistant_file_memory),
        icon = FocusIcons.Library,
        tone = 1,
    )
}

@Composable
private fun FileMeta.container(): Color = when (tone) {
    0 -> MaterialTheme.colorScheme.primaryContainer
    1 -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun FileMeta.content(): Color = when (tone) {
    0 -> MaterialTheme.colorScheme.onPrimaryContainer
    1 -> MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.onTertiaryContainer
}
