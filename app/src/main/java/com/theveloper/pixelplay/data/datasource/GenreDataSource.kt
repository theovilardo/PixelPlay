package com.theveloper.pixelplay.data.datasource

import com.theveloper.pixelplay.data.model.Genre

object GenreDataSource {
    val staticGenres = listOf(
        Genre(id = "pop", name = "Pop", colorHex = "#FF4081"), // Vibrant Pink
        Genre(id = "rock", name = "Rock", colorHex = "#D32F2F"), // Strong Red
        Genre(id = "hip_hop", name = "Hip Hop", colorHex = "#7B1FA2"), // Deep Purple
        Genre(id = "electronic", name = "Electronic", colorHex = "#00BCD4"), // Cyan
        Genre(id = "classical", name = "Classical", colorHex = "#C8E6C9"), // Soft Green
        Genre(id = "jazz", name = "Jazz", colorHex = "#FFC107"), // Amber
        Genre(id = "reggae", name = "Reggae", colorHex = "#4CAF50"), // Green
        Genre(id = "blues", name = "Blues", colorHex = "#1976D2"), // Blue
        Genre(id = "metal", name = "Metal", colorHex = "#424242"), // Dark Grey
        Genre(id = "folk_acoustic", name = "Folk & Acoustic", colorHex = "#A1887F"), // Lighter Brown
        Genre(id = "rnb_soul", name = "R&B / Soul", colorHex = "#E91E63"), // Pink
        Genre(id = "latin", name = "Latin", colorHex = "#FF9800") // Orange
        // Add more genres as desired
        // Optionally, include image URLs if you have them, e.g.,
        // Genre(id = "pop", name = "Pop", imageUrl = "https://example.com/images/pop.jpg")
    )
}
