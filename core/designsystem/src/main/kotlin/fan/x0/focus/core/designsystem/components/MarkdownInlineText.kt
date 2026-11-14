package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

internal data class InlineCitation(
    val number: Int,
    val url: String,
)

internal data class MarkdownInline(
    val text: AnnotatedString,
    val codeSpans: List<IntRange> = emptyList(),
    val formulas: List<String> = emptyList(),
    val citations: List<InlineCitation> = emptyList(),
)

@Composable
internal fun MarkdownInlineText(
    content: MarkdownInline,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    var codeRects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    val density = LocalDensity.current
    val inline = HashMap<String, InlineTextContent>()
    content.formulas.forEachIndexed { index, latex ->
        val fontSize = if (style.fontSize.isSpecified) style.fontSize else 16.sp
        val box = rememberMathBox(latex, false, color, fontSize)
        val widthPx = if (box != null && box.w > 0f) {
            box.w
        } else {
            with(density) { fontSize.toPx() } * latex.length.coerceIn(1, 16) * 0.58f
        }
        val heightPx = if (box != null && box.h > 0f) {
            box.h
        } else {
            with(density) { fontSize.toPx() } * 1.2f
        }
        val placeholder = Placeholder(
            width = with(density) { widthPx.toSp() },
            height = with(density) { heightPx.toSp() },
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        )
        inline["math$index"] = InlineTextContent(placeholder) {
            if (box != null && box.w > 0f) {
                Canvas(Modifier.fillMaxSize()) { box.paint(this, 0f, box.asc) }
            } else {
                Text(
                    text = latex,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize),
                    maxLines = 1,
                )
            }
        }
    }
    val uriHandler = LocalUriHandler.current
    content.citations.forEachIndexed { index, citation ->
        inline["cite$index"] = InlineTextContent(
            Placeholder(
                width = 18.sp,
                height = 14.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
            ),
        ) {
            CitationBadge(
                number = citation.number,
                onClick = { runCatching { uriHandler.openUri(citation.url) } },
            )
        }
    }
    val background = color.copy(alpha = InlineCodeBackgroundAlpha)
    Text(
        text = content.text,
        style = style,
        color = color,
        textAlign = textAlign,
        inlineContent = inline,
        modifier = modifier.drawBehind {
            if (codeRects.isEmpty()) return@drawBehind
            val corner = CornerRadius(InlineCodeCornerRadius.toPx())
            val horizontalPadding = InlineCodeHorizontalPadding.toPx()
            codeRects.forEach { rect ->
                drawRoundRect(
                    color = background,
                    topLeft = Offset(rect.left - horizontalPadding, rect.top),
                    size = Size(rect.width + horizontalPadding * 2, rect.height),
                    cornerRadius = corner,
                )
            }
        },
        onTextLayout = { layout ->
            codeRects = layout.codeRects(content.codeSpans)
        },
    )
}

@Composable
private fun CitationBadge(number: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = TextStyle(
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun TextLayoutResult.codeRects(spans: List<IntRange>): List<Rect> {
    if (spans.isEmpty()) return emptyList()
    val textLength = layoutInput.text.length
    val rects = mutableListOf<Rect>()
    spans.forEach { span ->
        val start = span.first.coerceIn(0, textLength)
        val end = (span.last + 1).coerceIn(start, textLength)
        if (start >= end) return@forEach
        val lastLine = getLineForOffset(end - 1)
        for (line in getLineForOffset(start)..lastLine) {
            val lineStart = getLineStart(line)
            val lineEnd = getLineEnd(line, visibleEnd = true)
            val from = maxOf(start, lineStart)
            val to = minOf(end, lineEnd)
            if (from >= to) continue
            val left = if (from == lineStart) {
                getLineLeft(line)
            } else {
                getHorizontalPosition(from, usePrimaryDirection = true)
            }
            val right = getHorizontalPosition(to, usePrimaryDirection = true)
            rects += Rect(
                left = minOf(left, right),
                top = getLineTop(line),
                right = maxOf(left, right),
                bottom = getLineBottom(line),
            )
        }
    }
    return rects
}

private val InlineCodeCornerRadius = 6.dp
private val InlineCodeHorizontalPadding = 4.dp
private const val InlineCodeBackgroundAlpha = 0.08f
