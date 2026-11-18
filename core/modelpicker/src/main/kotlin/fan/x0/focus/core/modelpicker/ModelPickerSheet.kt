package fan.x0.focus.core.modelpicker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.AutoModelIcon
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.components.rememberProviderImage
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.data.assistant.ProviderModel
import fan.x0.focus.data.assistant.ProviderModelGroup
import kotlinx.coroutines.launch
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens

data class ModelPickerModel(
    val id: String,
    val label: String,
    val imageInput: Boolean = false,
    val imageOutput: Boolean = false,
    val tools: Boolean = false,
    val reasoning: Boolean = false,
)

data class ModelPickerGroup(
    val id: String,
    val title: String,
    val icon: String = "",
    val models: List<ModelPickerModel> = emptyList(),
)

@Composable
fun rememberModelPickerGroups(groups: List<ProviderModelGroup>): List<ModelPickerGroup> {
    return remember(groups) {
        groups.map { group ->
            ModelPickerGroup(
                id = group.providerId,
                title = group.providerName,
                icon = group.icon,
                models = group.models.map { model ->
                    val meta = model.meta
                    ModelPickerModel(
                        id = model.id,
                        label = model.name.ifBlank { model.id },
                        imageInput = meta.supportsImage == true,
                        imageOutput = meta.supportsImageOutput == true,
                        tools = meta.supportsTools == true,
                        reasoning = meta.supportsReasoning == true,
                    )
                },
            )
        }
    }
}

@Composable
fun rememberModelPickerGroup(
    providerId: String,
    providerName: String,
    icon: String = "",
    models: List<ProviderModel>,
): ModelPickerGroup {
    return remember(providerId, providerName, icon, models) {
        ModelPickerGroup(
            id = providerId,
            title = providerName,
            icon = icon,
            models = models.map { model ->
                ModelPickerModel(id = model.id, label = model.name.ifBlank { model.id })
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelPickerSheet(
    title: String,
    searchHint: String,
    emptyText: String,
    groups: List<ModelPickerGroup>,
    onQueryChange: (String) -> Unit,
    onSelect: (providerId: String, modelId: String) -> Unit,
    onDismiss: () -> Unit,
    query: String = "",
    selectedProviderId: String? = null,
    selectedModelId: String? = null,
    loading: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    val keyword = query.trim()

    val visibleGroups = remember(groups, keyword, collapsed) {
        groups.mapNotNull { group ->
            val models = if (keyword.isBlank()) {
                group.models
            } else {
                group.models.filter {
                    it.label.contains(keyword, ignoreCase = true) ||
                        it.id.contains(keyword, ignoreCase = true)
                }
            }
            if (models.isEmpty()) null else group to models
        }
    }

    val positions = remember(visibleGroups, keyword, collapsed) {
        var index = 0
        visibleGroups.associate { (group, models) ->
            val position = index
            index += 1
            if (keyword.isNotBlank() || group.id !in collapsed) index += models.size
            group.id to position
        }
    }

    LaunchedEffect(keyword) {
        if (keyword.isNotBlank()) collapsed = emptySet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .imePadding()
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = LocalFocusTypography.current.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.model_picker_count, groups.sumOf { it.models.size }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    loading -> FocusLoadingBlob(size = 20.dp)
                    onRefresh != null -> FilledTonalIconButton(onClick = hapticTap(onRefresh)) {
                        Icon(
                            imageVector = FocusIcons.Refresh,
                            contentDescription = stringResource(R.string.model_picker_refresh),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalIconButton(onClick = hapticTap(onDismiss)) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.model_picker_close),
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(searchHint) },
                leadingIcon = {
                    Icon(
                        imageVector = FocusIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = FocusShapeTokens.input,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            if (visibleGroups.isEmpty()) {
                if (loading) {
                    FocusLoadingBlob(
                        size = 40.dp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 32.dp),
                    )
                } else {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
                return@Column
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleGroups.map { it.first }, key = { it.id }) { group ->
                    AssistChip(
                        onClick = {
                            val position = positions[group.id] ?: return@AssistChip
                            scope.launch { listState.animateScrollToItem(position) }
                        },
                        label = { Text(group.title, maxLines = 1) },
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleGroups.forEach { (group, models) ->
                    stickyHeader(key = "provider:${group.id}", contentType = "provider") {
                        ProviderHeader(
                            group = group,
                            expanded = keyword.isNotBlank() || group.id !in collapsed,
                            onToggle = {
                                if (keyword.isBlank()) {
                                    collapsed = if (group.id in collapsed) {
                                        collapsed - group.id
                                    } else {
                                        collapsed + group.id
                                    }
                                }
                            },
                        )
                    }
                    if (keyword.isNotBlank() || group.id !in collapsed) {
                        items(models, key = { "${group.id}:${it.id}" }, contentType = { "model" }) { model ->
                            ModelRow(
                                model = model,
                                selected = group.id == selectedProviderId && model.id == selectedModelId,
                                onClick = { onSelect(group.id, model.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderHeader(
    group: ModelPickerGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "modelProviderChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = hapticTap(onToggle))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = FocusIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(16.dp)
                .rotate(rotation),
        )
        ProviderAvatar(icon = group.icon, size = 22.dp)
        Text(
            text = group.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.model_picker_model_count, group.models.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelRow(
    model: ModelPickerModel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = hapticTap(onClick),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModelAvatar(model = model, selected = selected)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.label,
                        style = LocalFocusTypography.current.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = FocusIcons.Check,
                            contentDescription = stringResource(R.string.model_picker_selected),
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ModalityTag(model = model)
                    if (model.tools) {
                        AbilityTag(
                            icon = FocusIcons.Tool,
                            description = stringResource(R.string.model_picker_tools),
                            container = scheme.tertiaryContainer,
                            content = scheme.onTertiaryContainer,
                        )
                    }
                    if (model.reasoning) {
                        AbilityTag(
                            icon = FocusIcons.Idea,
                            description = stringResource(R.string.model_picker_reasoning),
                            container = scheme.primaryContainer,
                            content = scheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalityTag(model: ModelPickerModel) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = FocusIcons.Text,
                contentDescription = stringResource(R.string.model_picker_text),
                modifier = Modifier.size(14.dp),
            )
            if (model.imageInput) {
                Icon(
                    imageVector = FocusIcons.Photo,
                    contentDescription = stringResource(R.string.model_picker_vision),
                    modifier = Modifier.size(14.dp),
                )
            }
            Icon(
                imageVector = FocusIcons.Chevron,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Icon(
                imageVector = FocusIcons.Text,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            if (model.imageOutput) {
                Icon(
                    imageVector = FocusIcons.Photo,
                    contentDescription = stringResource(R.string.model_picker_image_output),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AbilityTag(
    icon: ImageVector,
    description: String,
    container: Color,
    content: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ModelAvatar(
    model: ModelPickerModel,
    selected: Boolean,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            AutoModelIcon(
                name = model.id,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ProviderAvatar(
    icon: String,
    size: androidx.compose.ui.unit.Dp,
) {
    val image = rememberProviderImage(icon)
    if (image != null) {
        Surface(
            modifier = Modifier.size(size),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(size * 0.12f),
            )
        }
        return
    }
    FocusIconBadge(
        icon = FocusIcons.Assistant,
        size = size,
        container = MaterialTheme.colorScheme.secondaryContainer,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
