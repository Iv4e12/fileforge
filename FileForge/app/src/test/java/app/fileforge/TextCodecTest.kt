package app.fileforge

import app.fileforge.io.TextCodec
import app.fileforge.io.TextEncoding
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCodecTest {
    @Test fun utf8UnicodeRoundTrip() {
        val original = "héllo ✓ 日本語 😀\nsecond line\n"
        val bytes = original.toByteArray(Charsets.UTF_8)
        val d = TextCodec.decode(bytes)
        assertEquals(original, d.text)
        assertFalse(d.encoding.bom)
        assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding).bytes)
    }

    @Test fun utf8BomIsPreserved() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "abc".toByteArray()
        val d = TextCodec.decode(bytes)
        assertEquals("abc", d.text)
        assertTrue(d.encoding.bom)
        assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding).bytes)
    }

    @Test fun crlfIsNormalisedForEditingAndRestoredOnSave() {
        val bytes = "a\r\nb\r\n".toByteArray()
        val d = TextCodec.decode(bytes)
        assertEquals("a\nb\n", d.text)
        assertTrue(d.encoding.crlf)
        assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding).bytes)
    }

    @Test fun lfFilesStayLf() {
        val bytes = "a\nb\n".toByteArray()
        val d = TextCodec.decode(bytes)
        assertFalse(d.encoding.crlf)
        assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding).bytes)
    }

    @Test fun legacySingleByteFileRoundTrips() {
        val bytes = byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte()) // "café" in windows-1252, invalid UTF-8
        val d = TextCodec.decode(bytes)
        assertEquals("café", d.text)
        assertEquals("windows-1252", d.encoding.charset.name())
        assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding).bytes)
    }

    @Test fun unencodableCharactersFallBackToUtf8InsteadOfLosingData() {
        val enc = TextEncoding(java.nio.charset.Charset.forName("windows-1252"), false, false)
        val result = TextCodec.encode("café ✓", enc)
        assertTrue(result.reencoded)
        assertEquals("café ✓", String(result.bytes, Charsets.UTF_8))
    }

    @Test fun utf16WithBomRoundTrips() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi ✓".toByteArray(Charsets.UTF_16LE)
        val d = TextCodec.decode(bytes)
        assertEquals("hi ✓", d.text)
        assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding).bytes)
    }

    @Test fun emptyFile() {
        val d = TextCodec.decode(ByteArray(0))
        assertEquals("", d.text)
        assertEquals(0, TextCodec.encode(d.text, d.encoding).bytes.size)
    }
}
