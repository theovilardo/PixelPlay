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

/**
 * Escape sequence for delimiters in artist names.
 * Use double backslash (\\) before a delimiter to prevent splitting at that position.
 * Example: "AC\\\\DC" with delimiter "/" won't split, but "Artist1/Artist2" will.
 */
private const val ESCAPE_SEQUENCE = "\\\\"

/**
 * Placeholder used internally during parsing to preserve escaped delimiters.
 */
private const val ESCAPE_PLACEHOLDER = "\u0000ESCAPED\u0000"

/**
 * Splits an artist string by the given delimiters, respecting escaped delimiters.
 * 
 * @param delimiters List of delimiter strings to split by (e.g., ["/", ";", ","])
 * @return List of individual artist names, trimmed and with escaped delimiters restored.
 *         Returns a single-element list with the original string if no splitting occurs.
 *
 * Examples:
 * - "Artist1/Artist2".splitArtistsByDelimiters(listOf("/")) -> ["Artist1", "Artist2"]
 * - "AC\\DC".splitArtistsByDelimiters(listOf("/")) -> ["AC/DC"] (escaped)
 * - "A/B;C".splitArtistsByDelimiters(listOf("/", ";")) -> ["A", "B", "C"]
 * - "  Artist  ".splitArtistsByDelimiters(listOf("/")) -> ["Artist"] (trimmed)
 */
fun String.splitArtistsByDelimiters(delimiters: List<String>): List<String> {
    if (delimiters.isEmpty() || this.isBlank()) {
        return listOf(this.trim()).filter { it.isNotEmpty() }
    }

    // Sort delimiters by length descending to handle longer delimiters first
    val sortedDelimiters = delimiters.sortedByDescending { it.length }

    // Track escaped delimiter positions
    var working = this

    // Replace escaped delimiters with placeholders
    // For each delimiter, find occurrences of \\ + delimiter and replace with placeholder
    val escapedMappings = mutableMapOf<String, String>()
    sortedDelimiters.forEachIndexed { index, delimiter ->
        val escapedDelimiter = ESCAPE_SEQUENCE + delimiter
        val placeholder = "${ESCAPE_PLACEHOLDER}${index}${ESCAPE_PLACEHOLDER}"
        escapedMappings[placeholder] = delimiter
        working = working.replace(escapedDelimiter, placeholder)
    }

    // Build regex pattern from delimiters (escape special regex chars)
    val pattern = sortedDelimiters.joinToString("|") { Regex.escape(it) }
    val regex = Regex(pattern)

    // Split by delimiters
    val parts = working.split(regex)

    // Restore escaped delimiters and trim each part
    return parts
        .map { part ->
            var restored = part
            escapedMappings.forEach { (placeholder, delimiter) ->
                restored = restored.replace(placeholder, delimiter)
            }
            restored.trim()
        }
        .filter { it.isNotEmpty() }
        .distinct() // Remove duplicates
        .ifEmpty { if (this.trim().isNotEmpty()) listOf(this.trim()) else emptyList() } // Fallback to original if non-empty, else empty list
}

/**
 * Joins a list of artist names into a display string.
 * 
 * @param separator The separator to use between artist names (default: ", ")
 * @return A formatted string with all artist names joined.
 */
fun List<String>.joinArtistsForDisplay(separator: String = ", "): String {
    return this.joinToString(separator)
}
