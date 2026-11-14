package fan.x0.focus.data.roleplay

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

private val TAG_PREFIX = Regex(
    "^<(?:i(?:-(?:b(?:r(?:[0-9_\\s/ms-]{0,24})?)?)?)?)?$",
    RegexOption.IGNORE_CASE,
)
private val TAG_FULL = Regex(
    "^<i-br(?:[_\\s-]*ms)?(?:[_\\s-]*(\\d{1,9}))?\\s*/?>$",
    RegexOption.IGNORE_CASE,
)
private val TAG_JUNK = Regex(
    "<i-br(?:[_\\s-]*ms)?(?:[_\\s-]*\\d{1,9})?\\s*/?>|<i-br[_\\s/-]*\\d{1,12}\\s*/?>",
    RegexOption.IGNORE_CASE,
)
private val FENCE_LINE = Regex("^\\s*```")
private val STOP_SENTENCE = Regex("[。！？!?…]+|[.!?](?=\\s|$)")
private val STOP_CLAUSE = Regex("[，、,;；：:]+")
private val STOP_SPACE = Regex("\\s")

class RoleplayBubbleParser(
    private val enabled: Boolean = true,
    private val defaultDelayMs: Int = 600,
    private val maxDelayMs: Int = 8_000,
    private val autoSplitChars: Int = 72,
) {
    private val text = StringBuilder()
    private var tag = ""
    private var ticks = 0
    private var fence = false
    private var inline = false
    private var newlineRun = 0
    private var carryDelay = 0
    private var aborted = false

    val inCode: Boolean get() = fence || inline

    fun push(delta: String): List<RoleplayBubble> {
        if (aborted || delta.isEmpty()) return emptyList()
        if (!enabled) {
            text.append(delta)
            return emptyList()
        }
        val out = mutableListOf<RoleplayBubble>()
        delta.forEach { char -> feed(char, out) }
        autoSplit(out)
        return out
    }

    fun finish(): List<RoleplayBubble> {
        if (aborted) return emptyList()
        resolveTicks()
        tag = ""
        val body = trimSegment(text.toString())
        text.clear()
        if (body.isEmpty()) return emptyList()
        val bubble = RoleplayBubble(body, carryDelay)
        carryDelay = 0
        return listOf(bubble)
    }

    fun release(): List<RoleplayBubble> {
        if (aborted) return emptyList()
        resolveTicks()
        tag = ""
        aborted = true
        val body = trimSegment(text.toString())
        text.clear()
        val delay = carryDelay
        carryDelay = 0
        return if (body.isEmpty()) emptyList() else listOf(RoleplayBubble(body, delay))
    }

    fun abort(): String {
        val dropped = text.toString()
        aborted = true
        text.clear()
        tag = ""
        ticks = 0
        fence = false
        inline = false
        return dropped
    }

    private fun feed(char: Char, out: MutableList<RoleplayBubble>) {
        if (tag.isNotEmpty()) {
            val next = tag + char
            if (char == '>') {
                val match = TAG_FULL.matchEntire(next)
                tag = ""
                if (match != null) {
                    val raw = match.groupValues[1]
                    cut(raw.toIntOrNull() ?: defaultDelayMs, out)
                } else {
                    text.append(next)
                }
                return
            }
            if (TAG_PREFIX.matches(next)) {
                tag = next
                return
            }
            text.append(tag)
            tag = ""
        }

        if (char == '`') {
            ticks++
            text.append(char)
            return
        }
        resolveTicks()
        if (char == '\n' && !fence) inline = false

        if (enabled && !inCode && char == '\n') {
            if (newlineRun > 0) {
                newlineRun = 0
                cut(defaultDelayMs, out)
                return
            }
            newlineRun = 1
            if (trimSegment(text.toString()).length >= MIN_BREAK) {
                cut(defaultDelayMs, out)
                return
            }
            text.append(char)
            return
        }
        if (char != ' ' && char != '\t' && char != '\r') newlineRun = 0

        if (char == '<' && enabled && !inCode) {
            tag = "<"
            return
        }
        text.append(char)
    }

    private fun resolveTicks() {
        if (ticks == 0) return
        if (ticks >= 3) {
            fence = !fence
            inline = false
        } else if (ticks == 1 && !fence) {
            inline = !inline
        }
        ticks = 0
    }

    private fun cut(delayMs: Int, out: MutableList<RoleplayBubble>) {
        val body = trimSegment(text.toString())
        text.clear()
        val delay = delayMs.coerceIn(0, maxDelayMs)
        if (body.isEmpty()) {
            carryDelay = (carryDelay + delay).coerceAtMost(maxDelayMs)
            return
        }
        val carried = carryDelay
        carryDelay = 0
        out += RoleplayBubble(body, if (delay > 0) delay else carried)
    }

    private fun cutAt(index: Int, delayMs: Int, out: MutableList<RoleplayBubble>) {
        val full = text.toString()
        if (index <= 0 || index >= full.length) return
        val body = trimSegment(full.substring(0, index))
        text.clear()
        text.append(full.substring(index))
        if (body.isEmpty()) return
        out += RoleplayBubble(body, delayMs.coerceIn(0, maxDelayMs))
    }

    private fun autoSplit(out: MutableList<RoleplayBubble>) {
        if (!enabled || autoSplitChars <= 0 || tag.isNotEmpty() || inCode) return
        while (true) {
            val index = softCut()
            if (index <= 0) return
            cutAt(index, defaultDelayMs, out)
        }
    }

    private fun softCut(): Int {
        val body = text.toString()
        val from = autoSplitChars
        if (body.length < from) return 0
        for (regex in listOf(STOP_SENTENCE, STOP_CLAUSE, STOP_SPACE)) {
            for (match in regex.findAll(body)) {
                if (match.range.last + 1 > from && match.range.last + 1 < body.length) {
                    return match.range.last + 1
                }
            }
        }
        return if (body.length >= from * 2) from else 0
    }

    private companion object {
        const val MIN_BREAK = 6
    }
}

private fun trimSegment(text: String): String = text.replace(Regex("^\\s+|\\s+$"), "")

fun cleanBubble(text: String, stripMarkdown: Boolean = true): String {
    val withoutTags = text.replace(TAG_JUNK, "")
        .replace(Regex("[<\\[]m:[^\\n<>\\[\\]]{0,40}[>\\]]?[ \\t]?"), "")
        .replace(Regex("^[ \\t]*m:\\w{1,16}[ \\t]+\\d{1,2}:\\d{2}[ \\t]*", RegexOption.MULTILINE), "")
    val lines = (if (stripMarkdown) stripMarkdown(withoutTags) else withoutTags).split('\n')
    val out = mutableListOf<String>()
    var fenced = false
    lines.forEach { line ->
        if (FENCE_LINE.containsMatchIn(line)) {
            fenced = !fenced
            out += line.trim()
        } else {
            out += if (fenced) line else line.replace(Regex("[ \\t]{2,}"), " ").trimEnd()
        }
    }
    return out.joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim()
}

fun stripMarkdown(text: String): String {
    val out = mutableListOf<String>()
    var fenced = false
    text.split('\n').forEach { line ->
        if (FENCE_LINE.containsMatchIn(line)) {
            fenced = !fenced
            out += line
            return@forEach
        }
        if (fenced) {
            out += line
            return@forEach
        }
        out += line
            .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "\$1")
            .replace(Regex("^\\s{0,3}#{1,6}\\s+"), "")
            .replace(Regex("^\\s{0,3}>\\s?"), "")
            .replace(Regex("^\\s{0,3}([-*+]|\\d+\\.)\\s+"), "")
            .replace(Regex("(\\*\\*|__)(.*?)\\1"), "\$2")
            .replace(Regex("(\\*|_)(.*?)\\1"), "\$2")
            .replace(Regex("~~(.*?)~~"), "\$1")
            .replace("|", " ")
            .replace(Regex("`([^`\\n]*)`"), "\$1")
    }
    return out.joinToString("\n").trim()
}

fun expandRoleplayMacros(text: String, charName: String, userName: String): String =
    text
        .replace(Regex("\\{\\{(char|persona|ai)\\}\\}", RegexOption.IGNORE_CASE), charName)
        .replace(Regex("\\{\\{(user|me)\\}\\}", RegexOption.IGNORE_CASE), userName)

fun formatRoleplayExamples(raw: String): String =
    raw.split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val match = Regex("^(.+?)\\s*[:：]\\s*(.*)$").matchEntire(line) ?: return@map line
            val who = match.groupValues[1].trim()
            val body = match.groupValues[2]
            when {
                Regex("^(me|user|用户)$", RegexOption.IGNORE_CASE).matches(who) -> "{{user}}: $body"
                Regex("^(you|assistant|ai|char|助手)$", RegexOption.IGNORE_CASE).matches(who) -> "{{char}}: $body"
                else -> line
            }
        }
        .joinToString("\n")

fun typingMillis(text: String, random: Random = Random.Default, spread: Float = 0.2f): Int {
    var weight = 0.0
    var index = 0
    while (index < text.length) {
        val code = Character.codePointAt(text, index)
        weight += if (isWide(code)) 1.0 else 0.5
        index += Character.charCount(code)
    }
    if (weight <= 0) return 0
    val factor = 1f - spread + random.nextFloat() * spread * 2f
    return min(12_000, max(500, (weight / 8.0 * factor * 1000).toInt()))
}

fun bubbleDelay(text: String, random: Random = Random.Default, scale: Float = 1f, spread: Float = 0.25f): Int {
    val normalized = scale.coerceAtLeast(0.1f)
    val base = min(1_500, 250 + text.length * 22)
    val factor = 1f - spread + random.nextFloat() * spread * 2f
    val value = base * factor * normalized
    return min((1_500 * normalized).toInt(), max((180 * normalized).toInt(), value.toInt()))
}

fun jitterMillis(base: Int, random: Random, jitter: Float = 0.35f): Int {
    if (base <= 0) return 0
    val factor = 1f - jitter + random.nextFloat() * jitter * 2f
    return max(1, (base * factor).toInt())
}

fun applyPunctuation(text: String, style: RoleplayPunctuation): String {
    if (text.isEmpty()) return text
    var fenced = false
    text.split('\n').forEach { line ->
        if (FENCE_LINE.containsMatchIn(line)) fenced = !fenced
    }
    if (fenced) return text
    val cleaned = when (style) {
        RoleplayPunctuation.Normal -> return text
        RoleplayPunctuation.Loose -> text.replace(Regex("[。.]+$"), "").replace("。", " ")
        RoleplayPunctuation.Minimal -> text.replace(Regex("[。.，,！!]+$"), "").replace("，", " ")
    }.trim()
    return cleaned.ifEmpty { text }
}

fun memoryRelevanceScore(
    content: String,
    query: String,
    weight: Double,
    lastAccess: Long,
    now: Long,
    sticky: Boolean,
): Double {
    val queryTokens = memoryTokens(query)
    val contentTokens = memoryTokens(content)
    val hits = queryTokens.intersect(contentTokens).size
    val relevance = if (queryTokens.isEmpty()) 0.0 else hits.toDouble() / max(3, queryTokens.size) * 3
    val days = max(0.0, (now - lastAccess).toDouble() / 86_400_000.0)
    val recency = 0.5.pow(days / 14.0)
    val stickyBoost = if (sticky) 1.2 else 0.0
    return weight * (0.5 + recency * 0.5) + relevance + stickyBoost
}

fun memoryTokens(text: String): Set<String> {
    val out = mutableSetOf<String>()
    val lower = text.lowercase()
    Regex("[a-z0-9]+").findAll(lower).forEach { match ->
        if (match.value.length > 1) out += match.value
    }
    val cjk = lower.replace(Regex("[^\\u4e00-\\u9fff\\u3040-\\u30ff]"), "")
    var index = 0
    while (index + 1 < cjk.length) {
        out += cjk.substring(index, index + 2)
        index++
    }
    return out
}

private fun isWide(code: Int): Boolean =
    (code in 0x3000..0x9FFF) || (code in 0xF900..0xFAFF) || (code in 0xFF00..0xFFEF)
