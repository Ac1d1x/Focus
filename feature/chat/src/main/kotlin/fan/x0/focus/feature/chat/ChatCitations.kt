package fan.x0.focus.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import java.net.URI

internal fun withCitationMarkers(text: String, citations: List<CitationUi>): String {
    if (text.isBlank() || citations.isEmpty()) return text
    val numbers = LinkedHashMap<String, Int>()
    citations.forEach { citation ->
        if (citation.url.isNotBlank() && citation.url !in numbers) {
            numbers[citation.url] = numbers.size + 1
        }
    }
    if (numbers.isEmpty()) return text
    val groups = citations
        .filter { it.url.isNotBlank() }
        .groupBy { it.anchor.coerceIn(0, text.length) }
        .toSortedMap(compareByDescending { it })
    val builder = StringBuilder(text)
    groups.forEach { (anchor, group) ->
        val markers = group
            .distinctBy { it.url }
            .mapNotNull { citation ->
                val number = numbers[citation.url] ?: return@mapNotNull null
                "[$number](${citation.url})"
            }
            .joinToString("")
        if (markers.isNotEmpty()) builder.insert(anchor, markers)
    }
    return builder.toString()
}

@Composable
internal fun ChatCitationCapsule(
    citations: List<CitationUi>,
    modifier: Modifier = Modifier,
) {
    val unique = remember(citations) { uniqueCitations(citations) }
    if (unique.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = hapticTap { open = true },
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = FocusIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.chat_sources_count, unique.size),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
    if (open) {
        ChatCitationSheet(
            citations = unique,
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatCitationSheet(
    citations: List<CitationUi>,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.chat_sources),
                    style = LocalFocusTypography.current.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.chat_sources_count, citations.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(citations, key = { it.url }) { citation ->
                    CitationRow(
                        citation = citation,
                        onClick = {
                            runCatching { uriHandler.openUri(citation.url) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CitationRow(
    citation: CitationUi,
    onClick: () -> Unit,
) {
    Surface(
        onClick = hapticTap(onClick),
        modifier = Modifier.fillMaxWidth(),
        shape = FocusShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = citation.index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = citation.title.ifBlank { citation.url },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = citation.siteName.ifBlank { hostOf(citation.url) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = FocusIcons.Chevron,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun uniqueCitations(citations: List<CitationUi>): List<CitationUi> = citations
    .filter { it.url.isNotBlank() }
    .distinctBy { it.url }
    .sortedBy { it.index }

private fun hostOf(url: String): String =
    runCatching { URI(url).host }.getOrNull().orEmpty()
