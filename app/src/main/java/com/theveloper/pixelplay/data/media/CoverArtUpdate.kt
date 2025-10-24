package com.theveloper.pixelplay.data.media

/**
 * Represents a cover art update requested by the user. The [bytes] contain the
 * cropped image data ready to be embedded in the audio file while [mimeType]
 * identifies the encoded format (for example, "image/jpeg").
 */
data class CoverArtUpdate(
    val bytes: ByteArray,
    val mimeType: String,
)
