package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/*
 * Native LaTeX math typesetting. A formula is parsed into a small node tree, laid out into boxes
 * (width, ascent, descent) and painted on a Canvas. No WebView and no extra library, so formulas
 * scroll and stream as cheaply as text.
 */

private const val ORD = 0
private const val REL = 1
private const val BIG = 2
private const val LIM = 3
private const val FUN = 4
private const val INT = 5
private const val AXIS = 0.28f

internal sealed interface MNode
internal data class MSym(val text: String, val italic: Boolean = false, val bold: Boolean = false, val kind: Int = ORD) : MNode
internal data class MRow(val items: List<MNode>) : MNode
internal data class MSpace(val em: Float) : MNode
internal data class MFrac(val num: MNode, val den: MNode, val bar: Boolean = true) : MNode
internal data class MScript(val base: MNode, val sub: MNode?, val sup: MNode?) : MNode
internal data class MSqrt(val body: MNode, val index: MNode?) : MNode
internal data class MFence(val open: String, val close: String, val body: MNode) : MNode
internal data class MAccent(val body: MNode, val mark: String) : MNode

/** mode 0 centres cells, 1 aligns pairs around the relation, 2 left-aligns (cases). */
internal data class MGrid(val rows: List<List<MNode>>, val open: String, val close: String, val mode: Int) : MNode

private fun table(src: String): Map<String, String> = src.trim().split(Regex("\\s+")).chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }

private val GREEK = table(
    "alpha α beta β gamma γ delta δ epsilon ϵ varepsilon ε zeta ζ eta η theta θ vartheta ϑ iota ι kappa κ lambda λ mu μ nu ν xi ξ pi π varpi ϖ " +
        "rho ρ varrho ϱ sigma σ varsigma ς tau τ upsilon υ phi ϕ varphi φ chi χ psi ψ omega ω Gamma Γ Delta Δ Theta Θ Lambda Λ Xi Ξ Pi Π Sigma Σ " +
        "Upsilon Υ Phi Φ Psi Ψ Omega Ω",
)
private val RELS = table(
    "times × div ÷ cdot ⋅ pm ± mp ∓ leq ≤ le ≤ geq ≥ ge ≥ neq ≠ ne ≠ approx ≈ equiv ≡ sim ∼ simeq ≃ cong ≅ propto ∝ ll ≪ gg ≫ in ∈ notin ∉ ni ∋ " +
        "subset ⊂ supset ⊃ subseteq ⊆ supseteq ⊇ cup ∪ cap ∩ setminus ∖ land ∧ wedge ∧ lor ∨ vee ∨ oplus ⊕ otimes ⊗ odot ⊙ circ ∘ bullet ∙ star ⋆ ast ∗ " +
        "to → rightarrow → leftarrow ← gets ← leftrightarrow ↔ Rightarrow ⇒ Leftarrow ⇐ Leftrightarrow ⇔ implies ⟹ iff ⟺ mapsto ↦ longrightarrow ⟶ " +
        "longleftarrow ⟵ uparrow ↑ downarrow ↓ perp ⊥ parallel ∥ mid ∣ vdash ⊢ models ⊨ prec ≺ succ ≻ triangleq ≜ coloneqq ≔ leqslant ⩽ geqslant ⩾",
)
private val ORDS = table(
    "infty ∞ partial ∂ nabla ∇ forall ∀ exists ∃ nexists ∄ emptyset ∅ varnothing ∅ neg ¬ lnot ¬ angle ∠ triangle △ hbar ℏ ell ℓ Re ℜ Im ℑ aleph ℵ " +
        "wp ℘ prime ′ dots … ldots … cdots ⋯ vdots ⋮ ddots ⋱ therefore ∴ because ∵ degree ° square □ Box □ checkmark ✓ dagger † langle ⟨ rangle ⟩ " +
        "lfloor ⌊ rfloor ⌋ lceil ⌈ rceil ⌉ lbrace { rbrace } vert | Vert ‖ top ⊤ bot ⊥ percent %",
)
private val BIGS = table("sum ∑ prod ∏ coprod ∐ bigcup ⋃ bigcap ⋂ bigoplus ⨁ bigotimes ⨂ bigvee ⋁ bigwedge ⋀")
private val INTS = table("int ∫ iint ∬ iiint ∭ oint ∮")
private val LIMS = setOf("lim", "max", "min", "sup", "inf", "limsup", "liminf", "argmax", "argmin")
private val FUNS = setOf(
    "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "ln", "log", "lg", "exp",
    "det", "dim", "ker", "deg", "arg", "gcd", "hom", "Pr", "mod", "bmod", "tr", "rank", "sgn",
)
private val DELIMS = mapOf(
    "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈", "rceil" to "⌉", "lbrace" to "{", "rbrace" to "}",
    "{" to "{", "}" to "}", "|" to "‖", "vert" to "|", "lvert" to "|", "rvert" to "|", "Vert" to "‖", "lVert" to "‖", "rVert" to "‖",
)
private val BLACKBOARD = mapOf('R' to "ℝ", 'N' to "ℕ", 'Z' to "ℤ", 'Q' to "ℚ", 'C' to "ℂ", 'P' to "ℙ", 'E' to "𝔼", 'H' to "ℍ", 'F' to "𝔽")
private val NAMED = Regex("\\\\([A-Za-z]+)")

private fun plain(text: String): String = NAMED.replace(text) { m ->
    val name = m.groupValues[1]
    GREEK[name] ?: RELS[name] ?: ORDS[name] ?: name
}

internal class MathParser(private val s: String) {
    private var i = 0
    private var guard = 0

    fun parse(): MNode = row(emptySet())

    private fun peekCmd(): String {
        if (i >= s.length || s[i] != '\\') return ""
        var j = i + 1
        if (j < s.length && s[j].isLetter()) {
            while (j < s.length && s[j].isLetter()) j++
            return s.substring(i, j)
        }
        return s.substring(i, minOf(j + 1, s.length))
    }

    private fun atEnd(ends: Set<String>): Boolean {
        if (i >= s.length) return true
        return when (s[i]) {
            '}' -> "}" in ends
            '&' -> "&" in ends
            ']' -> "]" in ends
            '\\' -> peekCmd() in ends
            else -> false
        }
    }

    private fun skipSpaces() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun row(ends: Set<String>): MNode {
        val items = ArrayList<MNode>()
        while (!atEnd(ends) && guard++ < 20_000) {
            val c = s[i]
            if (c == '^' || c == '_' || c == '\'') {
                i++
                val operand = if (c == '\'') MSym("′") else arg()
                val prev = if (items.isEmpty()) MSym("") else items.removeAt(items.lastIndex)
                val script = prev as? MScript
                val base = script?.base ?: prev
                items += if (c == '_') {
                    MScript(base, operand, script?.sup)
                } else {
                    val old = script?.sup
                    MScript(base, script?.sub, if (old != null) MRow(listOf(old, operand)) else operand)
                }
                continue
            }
            atom()?.let { items += it }
        }
        return if (items.size == 1) items[0] else MRow(items)
    }

    private fun arg(): MNode {
        skipSpaces()
        if (i >= s.length) return MSym("")
        if (s[i] == '{') return group()
        return atom() ?: MSym("")
    }

    private fun group(): MNode {
        i++
        val inner = row(setOf("}"))
        if (i < s.length) i++
        return inner
    }

    private fun braceText(): String {
        skipSpaces()
        if (i >= s.length || s[i] != '{') return ""
        var depth = 0
        val start = i + 1
        while (i < s.length) {
            if (s[i] == '{') depth++ else if (s[i] == '}') {
                depth--
                if (depth == 0) break
            }
            i++
        }
        val out = s.substring(start, minOf(i, s.length))
        if (i < s.length) i++
        return out
    }

    private fun delim(): String {
        skipSpaces()
        if (i >= s.length) return ""
        if (s[i] == '\\') {
            val cmd = peekCmd()
            i += cmd.length
            return DELIMS[cmd.drop(1)] ?: ""
        }
        val c = s[i++]
        return if (c == '.') "" else c.toString()
    }

    private fun atom(): MNode? {
        val c = s[i]
        if (c.isWhitespace() || c == '}' || c == '&') {
            i++
            return null
        }
        if (c == '{') return group()
        if (c == '\\') return command()
        if (c.isDigit()) {
            val start = i
            while (i < s.length && (s[i].isDigit() || (s[i] == '.' && i + 1 < s.length && s[i + 1].isDigit()))) i++
            return MSym(s.substring(start, i))
        }
        i++
        if (c.isLetter()) return MSym(c.toString(), italic = c.code < 128)
        return when (c) {
            '+', '=', '<', '>' -> MSym(c.toString(), kind = REL)
            '-' -> MSym("−", kind = REL)
            '*' -> MSym("∗", kind = REL)
            '~' -> MSpace(0.3f)
            else -> MSym(c.toString())
        }
    }

    private fun command(): MNode? {
        val cmd = peekCmd()
        i += cmd.length
        val name = cmd.drop(1)
        if (name.isEmpty()) return null
        if (!name[0].isLetter()) {
            return when (name) {
                ",", ":", ">" -> MSpace(0.2f)
                ";", " " -> MSpace(0.3f)
                "!" -> MSpace(-0.1f)
                "\\" -> MSpace(0.6f)
                "|" -> MSym("‖")
                else -> MSym(name)
            }
        }
        return when (name) {
            "frac", "dfrac", "tfrac", "cfrac" -> MFrac(arg(), arg())
            "binom", "dbinom", "tbinom" -> MFence("(", ")", MFrac(arg(), arg(), bar = false))
            "sqrt" -> {
                skipSpaces()
                var index: MNode? = null
                if (i < s.length && s[i] == '[') {
                    i++
                    index = row(setOf("]"))
                    if (i < s.length) i++
                }
                MSqrt(arg(), index)
            }
            "left" -> {
                val open = delim()
                val body = row(setOf("\\right"))
                if (peekCmd() == "\\right") i += 6
                MFence(open, delim(), body)
            }
            "right" -> {
                delim()
                null
            }
            "begin" -> environment(braceText())
            "end" -> {
                braceText()
                null
            }
            "text", "textrm", "textnormal", "mbox", "mathrm", "operatorname", "mathsf", "texttt", "mathtt", "textsf" -> MSym(plain(braceText()))
            "textbf", "mathbf", "boldsymbol", "bm", "pmb" -> MSym(plain(braceText()), bold = true)
            "textit", "mathit", "mathcal", "mathscr", "mathfrak" -> MSym(plain(braceText()), italic = true)
            "mathbb" -> MSym(braceText().map { BLACKBOARD[it] ?: it.toString() }.joinToString(""))
            "hat", "widehat", "bar", "overline", "underline", "vec", "overrightarrow", "tilde", "widetilde", "dot", "ddot", "boxed" -> MAccent(arg(), name)
            "quad" -> MSpace(1f)
            "qquad" -> MSpace(2f)
            "limits", "nolimits", "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle", "big", "Big", "bigg", "Bigg", "bigl", "bigr",
            "Bigl", "Bigr", "biggl", "biggr", "Biggl", "Biggr", "middle", "nonumber", "notag", "hline", "centering",
            -> null
            "label", "tag", "color", "phantom", "vspace", "hspace" -> {
                braceText()
                null
            }
            else -> symbol(name)
        }
    }

    private fun symbol(name: String): MNode {
        GREEK[name]?.let { return MSym(it, italic = name[0].isLowerCase()) }
        RELS[name]?.let { return MSym(it, kind = REL) }
        ORDS[name]?.let { return MSym(it) }
        BIGS[name]?.let { return MSym(it, kind = BIG) }
        INTS[name]?.let { return MSym(it, kind = INT) }
        if (name in LIMS) return MSym(name, kind = LIM)
        if (name in FUNS) return MSym(name, kind = FUN)
        return MSym(name)
    }

    private fun environment(env: String): MNode {
        val base = env.trimEnd('*')
        if (base == "array" || base == "tabular") braceText()
        val rows = ArrayList<ArrayList<MNode>>()
        rows += ArrayList<MNode>()
        while (i < s.length && guard++ < 20_000) {
            rows.last() += row(setOf("&", "\\\\", "\\end"))
            if (i >= s.length) break
            if (s[i] == '&') {
                i++
                continue
            }
            val cmd = peekCmd()
            if (cmd == "\\\\") {
                i += 2
                rows += ArrayList<MNode>()
                continue
            }
            if (cmd == "\\end") {
                i += 4
                braceText()
                break
            }
            i++
        }
        if (rows.size > 1 && rows.last().all { it is MRow && it.items.isEmpty() }) rows.removeAt(rows.lastIndex)
        return when (base) {
            "pmatrix" -> MGrid(rows, "(", ")", 0)
            "bmatrix" -> MGrid(rows, "[", "]", 0)
            "Bmatrix" -> MGrid(rows, "{", "}", 0)
            "vmatrix" -> MGrid(rows, "|", "|", 0)
            "Vmatrix" -> MGrid(rows, "‖", "‖", 0)
            "cases" -> MGrid(rows, "{", "", 2)
            "matrix", "array", "smallmatrix", "tabular" -> MGrid(rows, "", "", 0)
            else -> MGrid(rows, "", "", 1)
        }
    }
}

/** A laid-out fragment. [paint] draws it with its baseline at y and left edge at x. */
internal class MBox(val w: Float, val asc: Float, val desc: Float, private val draw: DrawScope.(Float, Float) -> Unit) {
    val h: Float get() = asc + desc
    fun paint(scope: DrawScope, x: Float, y: Float) = scope.draw(x, y)
}

internal class MathLayout(private val tm: TextMeasurer, private val density: Density, private val color: Color) {

    private fun empty(width: Float = 0f): MBox = MBox(width, 0f, 0f) { _, _ -> }

    private fun glyph(text: String, size: Float, italic: Boolean = false, bold: Boolean = false): MBox {
        if (text.isEmpty()) return MBox(0f, size * 0.7f, 0f) { _, _ -> }
        val style = TextStyle(
            color = color,
            fontSize = with(density) { size.toSp() },
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Serif,
        )
        val result = tm.measure(text = AnnotatedString(text), style = style, softWrap = false, maxLines = 1)
        val base = result.firstBaseline
        // Font line boxes are taller than the ink. Trimming keeps fractions and scripts tight.
        return MBox(result.size.width.toFloat(), minOf(base, size * 0.78f), minOf(result.size.height - base, size * 0.24f)) { x, y ->
            drawText(result, topLeft = Offset(x, y - base))
        }
    }

    fun lay(n: MNode, size: Float, display: Boolean): MBox = when (n) {
        is MSym -> sym(n, size, display)
        is MSpace -> empty(n.em * size)
        is MRow -> rowOf(n.items, size, display)
        is MFrac -> frac(n, size, display)
        is MScript -> script(n, size, display)
        is MSqrt -> root(n, size, display)
        is MFence -> fence(n.open, n.close, lay(n.body, size, display), size)
        is MAccent -> accent(n, size, display)
        is MGrid -> grid(n, size, display)
    }

    private fun sym(n: MSym, size: Float, display: Boolean): MBox {
        if (n.kind != BIG && n.kind != INT) return glyph(n.text, size, n.italic, n.bold)
        val g = glyph(n.text, size * (if (display) 1.7f else 1.25f))
        val shift = (g.asc - g.desc) / 2 - size * AXIS
        return MBox(g.w, g.asc - shift, g.desc + shift) { x, y -> g.paint(this, x, y + shift) }
    }

    private fun rowOf(items: List<MNode>, size: Float, display: Boolean): MBox {
        val boxes = items.map { lay(it, size, display) }
        val xs = FloatArray(boxes.size)
        var x = 0f
        items.forEachIndexed { k, node ->
            val kind = ((node as? MSym) ?: ((node as? MScript)?.base as? MSym))?.kind ?: ORD
            val pad = when {
                kind == ORD || k == 0 -> 0f
                kind == REL -> size * 0.22f
                else -> size * 0.12f
            }
            x += pad
            xs[k] = x
            x += boxes[k].w + when {
                kind == ORD -> 0f
                kind == REL -> pad
                else -> size * 0.14f
            }
        }
        val asc = boxes.maxOfOrNull { it.asc } ?: (size * 0.7f)
        val desc = boxes.maxOfOrNull { it.desc } ?: 0f
        return MBox(x, asc, desc) { ox, y -> boxes.forEachIndexed { k, b -> b.paint(this, ox + xs[k], y) } }
    }

    private fun frac(n: MFrac, size: Float, display: Boolean): MBox {
        val inner = if (display) size * 0.92f else size * 0.76f
        val a = lay(n.num, inner, false)
        val b = lay(n.den, inner, false)
        val pad = size * 0.12f
        val w = maxOf(a.w, b.w) + pad * 2
        val axis = size * AXIS
        val gap = size * 0.14f
        val th = maxOf(1f, size * 0.05f)
        return MBox(w, axis + gap + a.h, gap + b.h - axis) { x, y ->
            a.paint(this, x + (w - a.w) / 2, y - axis - gap - a.desc)
            if (n.bar) drawLine(color, Offset(x + pad / 2, y - axis), Offset(x + w - pad / 2, y - axis), th)
            b.paint(this, x + (w - b.w) / 2, y - axis + gap + b.asc)
        }
    }

    private fun script(n: MScript, size: Float, display: Boolean): MBox {
        val kind = (n.base as? MSym)?.kind ?: ORD
        val base = lay(n.base, size, display)
        val small = size * 0.7f
        val sup = n.sup?.let { lay(it, small, false) }
        val sub = n.sub?.let { lay(it, small, false) }
        if (display && (kind == BIG || kind == LIM)) {
            val w = maxOf(base.w, sup?.w ?: 0f, sub?.w ?: 0f)
            val g = size * 0.1f
            return MBox(w, base.asc + (sup?.let { it.h + g } ?: 0f), base.desc + (sub?.let { it.h + g } ?: 0f)) { x, y ->
                base.paint(this, x + (w - base.w) / 2, y)
                sup?.let { it.paint(this, x + (w - it.w) / 2, y - base.asc - g - it.desc) }
                sub?.let { it.paint(this, x + (w - it.w) / 2, y + base.desc + g + it.asc) }
            }
        }
        val up = maxOf(base.asc - small * 0.38f, size * 0.36f)
        val down = maxOf(base.desc + small * 0.2f, size * 0.2f)
        val sx = base.w + size * 0.03f
        val asc = maxOf(base.asc, sup?.let { up + it.asc } ?: 0f)
        val desc = maxOf(base.desc, sub?.let { down + it.desc } ?: 0f)
        return MBox(sx + maxOf(sup?.w ?: 0f, sub?.w ?: 0f), asc, desc) { x, y ->
            base.paint(this, x, y)
            sup?.paint(this, x + sx, y - up)
            sub?.paint(this, x + sx, y + down)
        }
    }

    private fun root(n: MSqrt, size: Float, display: Boolean): MBox {
        val b = lay(n.body, size, display)
        val gap = size * 0.12f
        val hook = size * 0.55f
        val th = maxOf(1f, size * 0.05f)
        val index = n.index?.let { lay(it, size * 0.55f, false) }
        val left = maxOf(hook, (index?.w ?: 0f) + hook * 0.55f)
        val asc = b.asc + gap + th
        return MBox(left + b.w + size * 0.08f, asc, b.desc) { x, y ->
            val x0 = x + left - hook
            val top = y - asc + th / 2
            val path = Path().apply {
                moveTo(x0, y - b.asc * 0.35f)
                lineTo(x0 + hook * 0.25f, y - b.asc * 0.45f)
                lineTo(x0 + hook * 0.55f, y + b.desc - th)
                lineTo(x0 + hook * 0.9f, top)
                lineTo(x + left + b.w + size * 0.06f, top)
            }
            drawPath(path, color, style = Stroke(th, cap = StrokeCap.Round, join = StrokeJoin.Round))
            index?.let { it.paint(this, x0 + hook * 0.2f - it.w, y - b.asc * 0.55f) }
            b.paint(this, x + left, y)
        }
    }

    private fun fence(open: String, close: String, body: MBox, size: Float): MBox {
        fun delim(ch: String): MBox? {
            if (ch.isEmpty()) return null
            val g = glyph(ch, size)
            if (body.h <= g.h * 1.2f) return g
            val sy = body.h / g.h
            return MBox(g.w, body.asc, body.desc) { x, y ->
                val cy = y + (body.desc - body.asc) / 2
                withTransform({ scale(1f, sy, Offset(x, cy)) }) { g.paint(this, x, cy + (g.asc - g.desc) / 2) }
            }
        }
        val l = delim(open)
        val r = delim(close)
        if (l == null && r == null) return body
        val lw = l?.w ?: 0f
        val gap = size * 0.06f
        val asc = maxOf(body.asc, l?.asc ?: 0f, r?.asc ?: 0f)
        val desc = maxOf(body.desc, l?.desc ?: 0f, r?.desc ?: 0f)
        return MBox(lw + body.w + (r?.w ?: 0f) + gap * 2, asc, desc) { x, y ->
            l?.paint(this, x, y)
            body.paint(this, x + lw + gap, y)
            r?.paint(this, x + lw + body.w + gap * 2, y)
        }
    }

    private fun accent(n: MAccent, size: Float, display: Boolean): MBox {
        val b = lay(n.body, size, display)
        val th = maxOf(1f, size * 0.05f)
        val g = size * 0.1f
        val stroke = Stroke(th, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (n.mark == "underline") {
            return MBox(b.w, b.asc, b.desc + g + th) { x, y ->
                b.paint(this, x, y)
                drawLine(color, Offset(x, y + b.desc + g), Offset(x + b.w, y + b.desc + g), th)
            }
        }
        if (n.mark == "boxed") {
            val p = size * 0.25f
            return MBox(b.w + p * 2, b.asc + p, b.desc + p) { x, y ->
                b.paint(this, x + p, y)
                drawRect(color, Offset(x + th, y - b.asc - p + th), Size(b.w + p * 2 - th * 2, b.h + p * 2 - th * 2), style = Stroke(th))
            }
        }
        return MBox(b.w, b.asc + size * 0.26f, b.desc) { x, y ->
            b.paint(this, x, y)
            val top = y - b.asc - g
            val cx = x + b.w / 2
            val hw = minOf(b.w / 2, size * 0.28f)
            when (n.mark) {
                "bar", "overline" -> drawLine(color, Offset(x, top), Offset(x + b.w, top), th)
                "hat", "widehat" -> drawPath(Path().apply { moveTo(cx - hw, top); lineTo(cx, top - size * 0.13f); lineTo(cx + hw, top) }, color, style = stroke)
                "vec", "overrightarrow" -> {
                    val wide = n.mark != "vec"
                    val from = if (wide) x else cx - hw
                    val to = if (wide) x + b.w else cx + hw
                    drawLine(color, Offset(from, top), Offset(to, top), th)
                    val head = Path().apply { moveTo(to - size * 0.12f, top - size * 0.08f); lineTo(to, top); lineTo(to - size * 0.12f, top + size * 0.08f) }
                    drawPath(head, color, style = stroke)
                }
                "tilde", "widetilde" -> {
                    val wave = Path().apply {
                        moveTo(cx - hw, top)
                        cubicTo(cx - hw / 2, top - size * 0.14f, cx + hw / 2, top + size * 0.1f, cx + hw, top - size * 0.04f)
                    }
                    drawPath(wave, color, style = stroke)
                }
                "dot" -> drawCircle(color, th * 1.3f, Offset(cx, top - th))
                "ddot" -> {
                    drawCircle(color, th * 1.3f, Offset(cx - size * 0.1f, top - th))
                    drawCircle(color, th * 1.3f, Offset(cx + size * 0.1f, top - th))
                }
            }
        }
    }

    private fun grid(n: MGrid, size: Float, display: Boolean): MBox {
        val cells = n.rows.map { row -> row.map { lay(it, size, display && n.mode == 1) } }
        val cols = cells.maxOfOrNull { it.size } ?: 0
        if (cols == 0) return empty()
        val cw = FloatArray(cols) { c -> cells.maxOf { it.getOrNull(c)?.w ?: 0f } }
        val ra = cells.map { row -> maxOf(row.maxOfOrNull { it.asc } ?: 0f, size * 0.7f) }
        val rd = cells.map { row -> maxOf(row.maxOfOrNull { it.desc } ?: 0f, size * 0.25f) }
        val cg = if (n.mode == 1) size * 0.25f else size * 0.9f
        val rg = size * 0.35f
        val w = cw.sum() + cg * (cols - 1)
        val h = ra.sum() + rd.sum() + rg * (cells.size - 1)
        val axis = size * AXIS
        val body = MBox(w, h / 2 + axis, h / 2 - axis) { x, y ->
            var top = y - axis - h / 2
            cells.forEachIndexed { ri, row ->
                var cx = x
                row.forEachIndexed { ci, b ->
                    val off = when (n.mode) {
                        1 -> if (ci % 2 == 0) cw[ci] - b.w else 0f
                        2 -> 0f
                        else -> (cw[ci] - b.w) / 2
                    }
                    b.paint(this, cx + off, top + ra[ri])
                    cx += cw[ci] + cg
                }
                top += ra[ri] + rd[ri] + rg
            }
        }
        return fence(n.open, n.close, body, size)
    }
}

@Composable
internal fun rememberMathBox(latex: String, display: Boolean, color: Color, fontSize: TextUnit): MBox? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(latex, display, color, fontSize, density) {
        runCatching {
            val px = with(density) { fontSize.toPx() } * (if (display) 1.12f else 1f)
            MathLayout(measurer, density, color).lay(MathParser(latex).parse(), px, display)
        }.getOrNull()
    }
}

/** Renders one LaTeX formula. Falls back to the raw source in monospace if it cannot be laid out. */
@Composable
fun MathView(
    latex: String,
    modifier: Modifier = Modifier,
    display: Boolean = true,
    color: Color = LocalContentColor.current,
    fontSize: TextUnit = LocalTextStyle.current.fontSize,
) {
    val size = if (fontSize.isSpecified) fontSize else 16.sp
    val box = rememberMathBox(latex.trim(), display, color, size)
    if (box == null || box.w <= 0f) {
        Text(latex, modifier, color = color, fontFamily = FontFamily.Monospace)
        return
    }
    val density = LocalDensity.current
    val canvas = Modifier.size(with(density) { box.w.toDp() }, with(density) { box.h.toDp() })
    if (display) {
        Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Canvas(canvas) { box.paint(this, 0f, box.asc) }
        }
    } else {
        Canvas(modifier.then(canvas)) { box.paint(this, 0f, box.asc) }
    }
}
