package com.theveloper.pixelplay.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.nio.charset.Charset
import java.text.Normalizer

private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

fun Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}

/**
 * Attempts to fix incorrectly encoded metadata strings that frequently appear when
 * tags are saved using Windows-1252/ISO-8859-1 but are later read as UTF-8. This results
 * in characters such as "Ã", "â" or replacement symbols appearing instead of expected
 * punctuation. The function re-encodes the text when those patterns are detected and
 * removes stray control characters while keeping the original text when no adjustment
 * is necessary.
 */
fun String?.normalizeMetadataText(): String? {
    if (this == null) return null
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return trimmed

    val suspiciousPatterns = listOf("Ã", "â", "�", "ð", "Ÿ")
    val needsFix = suspiciousPatterns.any { trimmed.contains(it) }

    val reencoded = if (needsFix) {
        runCatching {
            String(trimmed.toByteArray(WINDOWS_1252), Charsets.UTF_8).trim()
        }.getOrNull()
    } else null

    val candidate = reencoded?.takeIf { it.isNotEmpty() } ?: trimmed

    val cleaned = candidate.replace("\u0000", "")

    return Normalizer.normalize(cleaned, Normalizer.Form.NFC)
}

fun String?.normalizeMetadataTextOrEmpty(): String {
    return normalizeMetadataText() ?: ""
}
