package fan.x0.focus.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
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
internal fun ChatCitationCard(
    citations: List<CitationUi>,
    modifier: Modifier = Modifier,
) {
    val unique = remember(citations) {
        citations
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
            .sortedBy { it.index }
    }
    if (unique.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FocusShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_sources),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            unique.forEach { citation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = hapticTap {
                            runCatching { uriHandler.openUri(citation.url) }
                        })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = citation.index.toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = citation.title.ifBlank { citation.url },
                            style = MaterialTheme.typography.bodySmall,
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
                }
            }
        }
    }
}

private fun hostOf(url: String): String =
    runCatching { URI(url).host }.getOrNull().orEmpty()
