package app.fileforge

import app.fileforge.model.FileType
import org.junit.Assert.assertEquals
import org.junit.Test

class FileTypeTest {
    @Test fun detectsByExtensionCaseInsensitively() {
        assertEquals(FileType.TEXT, FileType.detect("notes.TXT", null))
        assertEquals(FileType.JSON, FileType.detect("Data.json", "application/octet-stream"))
        assertEquals(FileType.HTML, FileType.detect("page.htm", null))
        assertEquals(FileType.PDF, FileType.detect("report.pdf", "application/octet-stream"))
        assertEquals(FileType.MARKDOWN, FileType.detect("README.md", null))
    }

    @Test fun fallsBackToMimeType() {
        assertEquals(FileType.TEXT, FileType.detect("noextension", "text/plain; charset=utf-8"))
        assertEquals(FileType.CSV, FileType.detect("export", "text/csv"))
        assertEquals(FileType.XML, FileType.detect("feed", "application/xml"))
    }

    @Test fun unknownFormatsAreUnsupportedNotCrashes() {
        assertEquals(FileType.UNSUPPORTED, FileType.detect("letter.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(FileType.UNSUPPORTED, FileType.detect("blob.bin", "application/octet-stream"))
        assertEquals(FileType.UNSUPPORTED, FileType.detect(null, null))
    }
}
