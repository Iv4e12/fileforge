package app.fileforge.io

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Describes how a file was encoded so it can be written back the same way. */
data class TextEncoding(val charset: Charset, val bom: Boolean, val crlf: Boolean)

data class DecodedText(val text: String, val encoding: TextEncoding)

/** [reencoded] is true when the original charset could not represent the text and UTF-8 was used instead. */
class EncodedText(val bytes: ByteArray, val encoding: TextEncoding, val reencoded: Boolean)

object TextCodec {
    private val WIN1252: Charset = Charset.forName("windows-1252")
    private val LATIN1: Charset = Charsets.ISO_8859_1

    fun decode(bytes: ByteArray): DecodedText {
        val (bomCharset, offset) = when {
            startsWith(bytes, 0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            startsWith(bytes, 0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            startsWith(bytes, 0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> null to 0
        }
        val used: Charset
        val raw: String
        if (bomCharset != null) {
            used = bomCharset
            raw = String(bytes, offset, bytes.size - offset, bomCharset)
        } else {
            val strict = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val utf8 = try {
                strict.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (e: CharacterCodingException) {
                null
            }
            if (utf8 != null) {
                used = Charsets.UTF_8
                raw = utf8
            } else {
                // Not valid UTF-8: fall back to a single-byte charset. Latin-1 maps every byte
                // one-to-one, so it is used when windows-1252 would lose undefined bytes.
                val hasUndefined = bytes.any {
                    val v = it.toInt() and 0xFF
                    v == 0x81 || v == 0x8D || v == 0x8F || v == 0x90 || v == 0x9D
                }
                used = if (hasUndefined) LATIN1 else WIN1252
                raw = String(bytes, used)
            }
        }
        val crlf = raw.contains("\r\n")
        val text = if (raw.indexOf('\r') >= 0) raw.replace("\r\n", "\n").replace('\r', '\n') else raw
        return DecodedText(text, TextEncoding(used, bomCharset != null, crlf))
    }

    fun encode(text: String, enc: TextEncoding): EncodedText {
        val body = if (enc.crlf) text.replace("\n", "\r\n") else text
        var effective = enc
        var reencoded = false
        if (!isUnicode(enc.charset) && !enc.charset.newEncoder().canEncode(body)) {
            effective = TextEncoding(Charsets.UTF_8, false, enc.crlf)
            reencoded = true
        }
        val payload = body.toByteArray(effective.charset)
        val bom = if (effective.bom) bomFor(effective.charset) else ByteArray(0)
        return EncodedText(bom + payload, effective, reencoded)
    }

    private fun isUnicode(cs: Charset) =
        cs == Charsets.UTF_8 || cs == Charsets.UTF_16LE || cs == Charsets.UTF_16BE

    private fun bomFor(cs: Charset): ByteArray = when (cs) {
        Charsets.UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        Charsets.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        Charsets.UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        else -> ByteArray(0)
    }

    private fun startsWith(b: ByteArray, vararg prefix: Int): Boolean =
        b.size >= prefix.size && prefix.indices.all { (b[it].toInt() and 0xFF) == prefix[it] }
}
