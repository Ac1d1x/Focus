package fan.x0.focus.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import fan.x0.focus.core.designsystem.components.FocusAmbientBackground
import fan.x0.focus.core.designsystem.components.FocusAmbientWorking
import fan.x0.focus.core.designsystem.components.FocusBubbleEnter
import fan.x0.focus.core.designsystem.components.FocusChatBubble
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusChoiceButtons
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusComposer
import fan.x0.focus.core.designsystem.components.FocusComposerPill
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReasoningCard
import fan.x0.focus.core.designsystem.components.FocusSnackbarHost
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.FocusTraceCard
import fan.x0.focus.core.designsystem.components.FocusVoiceUi
import fan.x0.focus.core.designsystem.kit.BtnKind
import fan.x0.focus.core.designsystem.kit.ExButton
import fan.x0.focus.core.designsystem.kit.ExField
import fan.x0.focus.core.designsystem.kit.ExIconButton
import fan.x0.focus.core.designsystem.kit.ExPill
import fan.x0.focus.core.designsystem.kit.ExSegmented
import fan.x0.focus.core.designsystem.kit.ExSheet
import fan.x0.focus.core.designsystem.kit.ExSwitchRow
import fan.x0.focus.core.designsystem.kit.Ic
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.modelpicker.ModelPickerSheet
import fan.x0.focus.core.modelpicker.rememberModelPickerGroup
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.data.assistant.ProviderCatalog
import fan.x0.focus.data.assistant.ProviderModel
import fan.x0.focus.data.voice.rememberVoiceInput
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingRoute(
    sessionKey: String,
    viewModel: OnboardingViewModel = koinViewModel(key = sessionKey),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors)
    val saved = stringResource(R.string.onboarding_provider_saved)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            OnboardingEffect.ProviderSaved -> snackbar.showSnackbar(saved)
        }
    }
    OnboardingScreen(state = state, onAction = viewModel::onAction, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val hazeState = rememberHazeState()
    var overlayHeight by remember { mutableStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { overlayHeight.toDp() } + 12.dp
    FocusAmbientBackground {
        FocusAmbientWorking(active = state.sending)
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { FocusSnackbarHost(snackbar) },
            topBar = {
                FocusCompactTopBar(
                    title = state.assistantName.ifBlank { stringResource(R.string.onboarding_title) },
                    subtitle = stringResource(
                        if (state.providerReady) {
                            R.string.onboarding_ready_subtitle
                        } else {
                            R.string.onboarding_subtitle
                        },
                    ),
                    onMenu = null,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                if (state.loading) {
                    FocusLoadingBlock()
                } else if (!state.providerReady) {
                    ProviderGate(onAction = onAction)
                } else {
                    Conversation(
                        state = state,
                        onAction = onAction,
                        bottomPadding = bottomPadding,
                        hazeState = hazeState,
                    )
                }
                if (state.providerReady) {
                    ComposerArea(
                        state = state,
                        onAction = onAction,
                        hazeState = hazeState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { overlayHeight = it.height },
                    )
                }
            }
        }
        if (state.providerSheetOpen) {
            ProviderSheet(state = state, onAction = onAction)
        }
        if (state.modelPickerOpen) {
            val models = (listOfNotNull(state.model.takeIf { it.isNotBlank() }) + state.models)
                .distinct()
                .map { ProviderModel(it) }
            val group = rememberModelPickerGroup(
                providerId = "onboarding",
                providerName = state.providerName.ifBlank {
                    stringResource(R.string.onboarding_provider_title)
                },
                models = models,
            )
            ModelPickerSheet(
                title = stringResource(R.string.onboarding_model_picker_title),
                searchHint = stringResource(R.string.onboarding_model_search_hint),
                emptyText = stringResource(
                    if (state.fetchingModels) {
                        R.string.onboarding_models_loading
                    } else {
                        R.string.onboarding_models_empty
                    },
                ),
                groups = listOf(group),
                query = state.modelQuery,
                onQueryChange = { onAction(OnboardingAction.ModelQueryChanged(it)) },
                onSelect = { _, modelId -> onAction(OnboardingAction.SelectModel(modelId)) },
                onDismiss = { onAction(OnboardingAction.CloseModelPicker) },
                selectedProviderId = "onboarding",
                selectedModelId = state.model,
                loading = state.fetchingModels,
                onRefresh = { onAction(OnboardingAction.FetchModels) },
            )
        }
    }
}

@Composable
private fun Conversation(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    bottomPadding: Dp,
    hazeState: HazeState,
) {
    val listState = rememberLazyListState()
    val lastText = (state.entries.lastOrNull() as? OnboardingEntry.Assistant)?.text.orEmpty()
    LaunchedEffect(state.entries.size, lastText.length, state.sending) {
        if (state.entries.isNotEmpty()) {
            listState.requestScrollToItem(state.entries.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = bottomPadding + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            items = state.entries,
            key = { _, it -> it.key },
            contentType = { _, it ->
                when (it) {
                    is OnboardingEntry.User -> "user"
                    is OnboardingEntry.Assistant -> "assistant"
                    is OnboardingEntry.Trace -> "trace"
                    is OnboardingEntry.Notice -> "notice"
                }
            },
        ) { _, entry ->
            when (entry) {
                is OnboardingEntry.User -> FocusBubbleEnter(play = true, fromUser = true) {
                    FocusChatBubble(text = entry.text, fromUser = true)
                }
                is OnboardingEntry.Assistant -> FocusBubbleEnter(
                    play = entry.live,
                    fromUser = false,
                ) {
                    AssistantTurn(entry = entry)
                }
                is OnboardingEntry.Trace -> FocusTraceCard(steps = entry.steps)
                is OnboardingEntry.Notice -> OnboardingNotice(text = entry.text)
            }
        }
    }
}

@Composable
private fun AssistantTurn(entry: OnboardingEntry.Assistant) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entry.reasoning.isNotBlank()) {
            FocusReasoningCard(text = entry.reasoning, running = entry.live)
        }
        if (entry.text.isNotBlank() || entry.live) {
            FocusChatBubble(
                text = entry.text,
                fromUser = false,
                streaming = entry.live,
            )
        }
    }
}

@Composable
private fun OnboardingNotice(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProviderGate(onAction: (OnboardingAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FocusIcons.Assistant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp),
            )
        }
        Text(
            text = stringResource(R.string.onboarding_gate_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_gate_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        FocusCardGroup(modifier = Modifier.padding(top = 24.dp)) {
            item(
                headlineContent = { Text(stringResource(R.string.onboarding_welcome_local_title)) },
                supportingContent = { Text(stringResource(R.string.onboarding_welcome_local_body)) },
                leadingContent = { FocusCardGroupIcon(icon = FocusIcons.Lock) },
            )
            item(
                headlineContent = { Text(stringResource(R.string.onboarding_welcome_more_title)) },
                supportingContent = { Text(stringResource(R.string.onboarding_welcome_more_body)) },
                leadingContent = { FocusCardGroupIcon(icon = FocusIcons.Bolt) },
            )
            item(
                headlineContent = { Text(stringResource(R.string.onboarding_welcome_style_title)) },
                supportingContent = { Text(stringResource(R.string.onboarding_welcome_style_body)) },
                leadingContent = { FocusCardGroupIcon(icon = FocusIcons.Tune) },
            )
        }
        FocusPrimaryButton(
            text = stringResource(R.string.onboarding_connect_model),
            onClick = { onAction(OnboardingAction.OpenProviderSheet) },
            icon = FocusIcons.Tune,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(56.dp),
        )
        FocusTextButton(
            text = stringResource(R.string.onboarding_controlled_only),
            onClick = { onAction(OnboardingAction.UseAsControlled) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ComposerArea(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val name = state.assistantName.ifBlank { stringResource(R.string.onboarding_title) }
    val voice = rememberVoiceInput(
        input = state.input,
        onInput = { onAction(OnboardingAction.InputChanged(it)) },
    )
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.permissionGranted() else voice.permissionDenied()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        FocusComposer(
            input = state.input,
            onInputChange = { onAction(OnboardingAction.InputChanged(it)) },
            placeholder = stringResource(R.string.onboarding_input_hint, name),
            canSend = state.canSend,
            sending = state.sending,
            onSend = { onAction(OnboardingAction.Send) },
            onStop = { onAction(OnboardingAction.Stop) },
            hazeState = hazeState,
            voice = FocusVoiceUi(
                active = voice.listening,
                busy = voice.busy,
                status = voice.status,
                error = voice.error?.message(),
                level = voice.level,
            ),
            onVoiceStart = {
                if (voice.canRecord()) {
                    voice.start()
                } else {
                    microphone.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onVoiceStop = { voice.stop() },
            onVoiceCancel = { voice.cancel() },
            pills = {
                FocusComposerPill(
                    label = stringResource(R.string.onboarding_model_settings),
                    icon = FocusIcons.Tune,
                    compact = true,
                    onClick = { onAction(OnboardingAction.OpenProviderSheet) },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProviderSheet(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    ExSheet(
        onDismiss = { onAction(OnboardingAction.CloseProviderSheet) },
        title = stringResource(R.string.onboarding_provider_title),
        full = true,
    ) {
        Text(
            text = stringResource(R.string.onboarding_provider_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PresetRow(state = state, onAction = onAction)
        ExField(
            value = state.providerName,
            onChange = { onAction(OnboardingAction.ProviderNameChanged(it)) },
            label = stringResource(R.string.onboarding_provider_name),
        )
        ExField(
            value = state.baseUrl,
            onChange = { onAction(OnboardingAction.BaseUrlChanged(it)) },
            label = stringResource(R.string.onboarding_base_url),
            placeholder = "https://api.example.com/v1",
            keyboard = KeyboardType.Uri,
            mono = true,
        )
        val chat = stringResource(R.string.onboarding_protocol_chat)
        val responses = stringResource(R.string.onboarding_protocol_responses)
        val anthropic = stringResource(R.string.onboarding_protocol_anthropic)
        ExSegmented(
            options = ChatProtocol.entries,
            selected = state.protocol,
            onSelect = { onAction(OnboardingAction.ProtocolChanged(it)) },
        ) {
            when (it) {
                ChatProtocol.ChatCompletions -> chat
                ChatProtocol.Responses -> responses
                ChatProtocol.AnthropicMessages -> anthropic
            }
        }
        if (!state.keyOptional) {
            ExField(
                value = state.apiKey,
                onChange = { onAction(OnboardingAction.ApiKeyChanged(it)) },
                label = stringResource(R.string.onboarding_api_key),
                secret = true,
                mono = true,
            )
        }
        ExSwitchRow(
            title = stringResource(R.string.onboarding_key_optional),
            checked = state.keyOptional,
            onChange = { onAction(OnboardingAction.ToggleKeyOptional) },
        )
        ExField(
            value = state.model,
            onChange = { onAction(OnboardingAction.ModelChanged(it)) },
            label = stringResource(R.string.onboarding_model),
            mono = true,
            trailing = {
                if (state.fetchingModels) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                } else {
                    ExIconButton(
                        icon = Ic.Search,
                        label = stringResource(R.string.onboarding_model_picker_title),
                        onClick = { onAction(OnboardingAction.OpenModelPicker) },
                    )
                }
            },
        )
        state.providerFailure?.let { failure ->
            ProviderFailureCard(failure = failure)
        }
        if (state.connectionOk) {
            ExPill(
                text = stringResource(R.string.onboarding_connection_ok),
                icon = Ic.Check,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExButton(
                text = stringResource(
                    if (state.testing) R.string.onboarding_testing else R.string.onboarding_test,
                ),
                onClick = { onAction(OnboardingAction.TestProvider) },
                modifier = Modifier.weight(1f),
                kind = BtnKind.Tonal,
                icon = Ic.Bolt,
                enabled = state.canTestProvider,
                loading = state.testing,
            )
            ExButton(
                text = stringResource(
                    if (state.saving) R.string.onboarding_saving else R.string.onboarding_save,
                ),
                onClick = { onAction(OnboardingAction.SaveProvider) },
                modifier = Modifier.weight(1f),
                icon = Ic.Check,
                enabled = state.canSaveProvider,
                loading = state.saving,
            )
        }
        Text(
            text = stringResource(R.string.onboarding_privacy),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExButton(
            text = stringResource(R.string.onboarding_controlled_only),
            onClick = { onAction(OnboardingAction.UseAsControlled) },
            modifier = Modifier.fillMaxWidth(),
            kind = BtnKind.Text,
        )
    }
}

@Composable
private fun PresetRow(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.onboarding_preset_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProviderCatalog.presets.forEach { preset ->
                FocusChip(
                    label = preset.name,
                    selected = state.baseUrl.trim().trimEnd('/') == preset.baseUrl,
                    onClick = { onAction(OnboardingAction.ApplyPreset(preset.id)) },
                )
            }
        }
    }
}

@Composable
private fun ProtocolChips(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.onboarding_protocol),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FocusChoiceButtons(
            options = ChatProtocol.entries,
            selected = state.protocol,
            onSelect = { onAction(OnboardingAction.ProtocolChanged(it)) },
            label = { stringResource(it.labelRes()) },
        )
    }
}

private fun ChatProtocol.labelRes(): Int = when (this) {
    ChatProtocol.ChatCompletions -> R.string.onboarding_protocol_chat
    ChatProtocol.Responses -> R.string.onboarding_protocol_responses
    ChatProtocol.AnthropicMessages -> R.string.onboarding_protocol_anthropic
}

@Composable
private fun ApiKeyField(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = { onAction(OnboardingAction.ApiKeyChanged(it)) },
        label = { Text(stringResource(R.string.onboarding_api_key)) },
        singleLine = true,
        visualTransformation = if (state.apiKeyVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = hapticTap { onAction(OnboardingAction.ToggleApiKeyVisibility) }) {
                Icon(
                    imageVector = if (state.apiKeyVisible) {
                        FocusIcons.VisibilityOff
                    } else {
                        FocusIcons.Visibility
                    },
                    contentDescription = stringResource(
                        if (state.apiKeyVisible) {
                            R.string.onboarding_key_hide
                        } else {
                            R.string.onboarding_key_show
                        },
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = FocusShapeTokens.input,
    )
}

@Composable
private fun ModelField(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OutlinedTextField(
        value = state.model,
        onValueChange = { onAction(OnboardingAction.ModelChanged(it)) },
        label = { Text(stringResource(R.string.onboarding_model)) },
        singleLine = true,
        trailingIcon = {
            if (state.fetchingModels) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    FocusLoadingBlob(size = 18.dp)
                }
            } else {
                IconButton(onClick = hapticTap { onAction(OnboardingAction.OpenModelPicker) }) {
                    Icon(
                        imageVector = FocusIcons.Search,
                        contentDescription = stringResource(R.string.onboarding_model_picker_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = FocusShapeTokens.input,
    )
}

@Composable
private fun ProviderFailureCard(failure: ProviderFailure) {
    val text = when (failure) {
        is ProviderFailure.Validation -> stringResource(
            when (failure.field) {
                ProviderFailure.Field.BaseUrl -> R.string.onboarding_err_base_url
                ProviderFailure.Field.Model -> R.string.onboarding_err_model
                ProviderFailure.Field.ApiKey -> R.string.onboarding_err_key
            },
        )
        is ProviderFailure.Network -> failure.error.message()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
