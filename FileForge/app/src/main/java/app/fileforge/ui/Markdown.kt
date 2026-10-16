package app.fileforge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private sealed class Block {
    data class Heading(val level: Int, val text: String) : Block()
    data class Bullet(val marker: String, val text: String) : Block()
    data class Quote(val text: String) : Block()
    data class Code(val text: String) : Block()
    data class Para(val text: String) : Block()
    object Rule : Block()
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val NUMBERED = Regex("""^\d+[.)] .*""")
private val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|\[(.+?)]\((.+?)\)""")

private fun parseBlocks(src: String): List<Block> {
    val out = ArrayList<Block>()
    val para = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    fun flush() {
        if (para.isNotEmpty()) {
            out.add(Block.Para(para.toString().trim()))
            para.setLength(0)
        }
    }
    for (line in src.lineSequence()) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                out.add(Block.Code(code.toString().trimEnd('\n')))
                code.setLength(0)
                inCode = false
            } else {
                flush()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.append(line).append('\n')
            continue
        }
        val t = line.trim()
        val heading = HEADING.matchEntire(t)
        when {
            t.isEmpty() -> flush()
            heading != null -> {
                flush()
                out.add(Block.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
            }
            t == "---" || t == "***" || t == "___" -> { flush(); out.add(Block.Rule) }
            t.startsWith("> ") -> { flush(); out.add(Block.Quote(t.drop(2))) }
            t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> {
                flush(); out.add(Block.Bullet("•", t.drop(2)))
            }
            NUMBERED.matches(t) -> {
                flush(); out.add(Block.Bullet(t.substringBefore(' '), t.substringAfter(' ')))
            }
            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(t)
            }
        }
    }
    flush()
    if (inCode && code.isNotEmpty()) out.add(Block.Code(code.toString().trimEnd('\n')))
    return out
}

private fun inline(s: String, codeBg: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE.findAll(s)) {
        append(s.substring(last, m.range.first))
        when {
            m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groups[2] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[2]) }
            m.groups[3] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                append(m.groupValues[3])
            }
            else -> withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(m.groupValues[4])
            }
        }
        last = m.range.last + 1
    }
    append(s.substring(last))
}

/** A basic preview: headings, paragraphs, lists, quotes, code blocks, rules, bold/italic/code/link text. */
@Composable
fun MarkdownPreview(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(blocks) { b ->
            when (b) {
                is Block.Heading -> Text(
                    inline(b.text, scheme.surfaceVariant, scheme.primary),
                    style = when (b.level) {
                        1 -> type.headlineMedium
                        2 -> type.headlineSmall
                        3 -> type.titleLarge
                        else -> type.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                )
                is Block.Para -> Text(inline(b.text, scheme.surfaceVariant, scheme.primary), style = type.bodyLarge)
                is Block.Bullet -> Row(Modifier.padding(start = 8.dp)) {
                    Text(b.marker, style = type.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(inline(b.text, scheme.surfaceVariant, scheme.primary), style = type.bodyLarge)
                }
                is Block.Quote -> Surface(color = scheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        inline(b.text, scheme.surface, scheme.primary),
                        style = type.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                is Block.Code -> Surface(color = scheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        b.text,
                        fontFamily = FontFamily.Monospace,
                        style = type.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Block.Rule -> HorizontalDivider()
            }
        }
    }
}
