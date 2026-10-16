package app.fileforge.model

enum class FileType(val label: String, val mime: String) {
    TEXT("Text", "text/plain"),
    MARKDOWN("Markdown", "text/markdown"),
    HTML("HTML", "text/html"),
    CSV("CSV", "text/csv"),
    JSON("JSON", "application/json"),
    XML("XML", "text/xml"),
    PDF("PDF", "application/pdf"),
    UNSUPPORTED("Unsupported", "application/octet-stream");

    /** True for formats whose content is loaded as text. */
    val isText: Boolean get() = this != PDF && this != UNSUPPORTED

    companion object {
        private val byExtension = mapOf(
            "txt" to TEXT, "text" to TEXT, "log" to TEXT,
            "md" to MARKDOWN, "markdown" to MARKDOWN,
            "html" to HTML, "htm" to HTML,
            "csv" to CSV, "json" to JSON, "xml" to XML, "pdf" to PDF,
        )

        /** Detects the type from the file extension first, then from the MIME type. */
        fun detect(name: String?, mime: String?): FileType {
            val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
            byExtension[ext]?.let { return it }
            val m = mime?.lowercase()?.substringBefore(';')?.trim().orEmpty()
            return when (m) {
                "application/pdf" -> PDF
                "text/markdown" -> MARKDOWN
                "text/html" -> HTML
                "text/csv", "text/comma-separated-values" -> CSV
                "application/json" -> JSON
                "text/xml", "application/xml" -> XML
                "text/plain" -> TEXT
                else -> UNSUPPORTED
            }
        }
    }
}
