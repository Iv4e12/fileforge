package app.fileforge.parse

/** Minimal RFC 4180 style CSV reader/writer (quoted fields, escaped quotes, embedded newlines). */
object Csv {
    fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val best = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
        return if (firstLine.contains(best)) best else ','
    }

    fun parse(text: String, delimiter: Char = ','): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when {
                    c == '"' && field.isEmpty() -> inQuotes = true
                    c == delimiter -> {
                        row.add(field.toString())
                        field.setLength(0)
                    }
                    c == '\n' -> {
                        row.add(field.toString())
                        field.setLength(0)
                        rows.add(row)
                        row = ArrayList()
                    }
                    c == '\r' -> Unit
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    fun serialize(rows: List<List<String>>, delimiter: Char = ','): String {
        val sb = StringBuilder()
        for (r in rows) {
            r.forEachIndexed { idx, f ->
                if (idx > 0) sb.append(delimiter)
                sb.append(escape(f, delimiter))
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun escape(f: String, d: Char): String {
        val needsQuotes = f.any { it == d || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + f.replace("\"", "\"\"") + "\"" else f
    }
}
