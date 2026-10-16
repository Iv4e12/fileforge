package app.fileforge.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import app.fileforge.model.FileType

class SyntaxColors(val key: Color, val string: Color, val number: Color, val tag: Color, val comment: Color)

/** Regex based colouring for JSON / XML / HTML. Offsets are unchanged, so editing is unaffected. */
class SyntaxTransformation(type: FileType, colors: SyntaxColors) : VisualTransformation {
    // Later rules win where ranges overlap. \x22 is a double quote.
    private val rules: List<Pair<Regex, Color>> = when (type) {
        FileType.JSON -> listOf(
            Regex("""-?\b\d+(\.\d+)?([eE][+-]?\d+)?\b""") to colors.number,
            Regex("""\b(true|false|null)\b""") to colors.tag,
            Regex("""\x22(?:\\.|[^\x22\\\n])*\x22""") to colors.string,
            Regex("""\x22(?:\\.|[^\x22\\\n])*\x22(?=\s*:)""") to colors.key,
        )
        else -> listOf(
            Regex("""</?[\w:.-]+|/?>""") to colors.tag,
            Regex("""\s[\w:.-]+(?==)""") to colors.key,
            Regex("""(?<==)\s*(\x22[^\x22]*\x22|'[^']*')""") to colors.string,
            Regex("""<!--[\s\S]*?-->""") to colors.comment,
        )
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val out = buildAnnotatedString {
            append(s)
            for ((regex, color) in rules) {
                for (m in regex.findAll(s)) {
                    if (m.range.isEmpty()) continue
                    addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
                }
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}
