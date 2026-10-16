package app.fileforge

import app.fileforge.parse.Csv
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test fun simpleRows() =
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), Csv.parse("a,b,c\n1,2,3\n"))

    @Test fun quotedCommaAndEscapedQuote() =
        assertEquals(listOf(listOf("Smith, John", "say \"hi\"", "x")), Csv.parse("\"Smith, John\",\"say \"\"hi\"\"\",x\n"))

    @Test fun embeddedNewlineInQuotedField() =
        assertEquals(listOf(listOf("line1\nline2", "z")), Csv.parse("\"line1\nline2\",z\n"))

    @Test fun crlfLineEndings() =
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), Csv.parse("a,b\r\nc,d\r\n"))

    @Test fun emptyFieldsAndNoTrailingNewline() {
        assertEquals(listOf(listOf("a", "", "c")), Csv.parse("a,,c"))
        assertEquals(listOf(listOf("a", "b")), Csv.parse("a,b"))
    }

    @Test fun emptyInput() = assertEquals(emptyList<List<String>>(), Csv.parse(""))

    @Test fun detectsSemicolonAndTab() {
        assertEquals(';', Csv.detectDelimiter("a;b;c\n1;2;3"))
        assertEquals('\t', Csv.detectDelimiter("a\tb\tc"))
        assertEquals(',', Csv.detectDelimiter("single"))
    }

    @Test fun serializeQuotesWhenNeeded() =
        assertEquals("\"a,b\",\"c\"\"d\",plain\n", Csv.serialize(listOf(listOf("a,b", "c\"d", "plain"))))

    @Test fun roundTripPreservesTrickyContent() {
        val rows = listOf(
            listOf("name", "note"),
            listOf("Zoë", "multi\nline, with \"quotes\""),
            listOf("", "日本語"),
        )
        assertEquals(rows, Csv.parse(Csv.serialize(rows)))
        val semi = listOf(listOf("a;b", "c"))
        assertEquals(semi, Csv.parse(Csv.serialize(semi, ';'), ';'))
    }
}
