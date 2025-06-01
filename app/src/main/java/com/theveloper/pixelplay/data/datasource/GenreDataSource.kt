package com.theveloper.pixelplay.data.datasource

import com.theveloper.pixelplay.data.model.Genre

object GenreDataSource {
    val staticGenres = listOf(
        Genre(id = "pop", name = "Pop"),
        Genre(id = "rock", name = "Rock"),
        Genre(id = "hip_hop", name = "Hip Hop"),
        Genre(id = "electronic", name = "Electronic"),
        Genre(id = "classical", name = "Classical"),
        Genre(id = "jazz", name = "Jazz"),
        Genre(id = "reggae", name = "Reggae"),
        Genre(id = "blues", name = "Blues"),
        Genre(id = "metal", name = "Metal"),
        Genre(id = "folk_acoustic", name = "Folk & Acoustic"),
        Genre(id = "rnb_soul", name = "R&B / Soul"),
        Genre(id = "latin", name = "Latin")
        // Add more genres as desired
        // Optionally, include image URLs if you have them, e.g.,
        // Genre(id = "pop", name = "Pop", imageUrl = "https://example.com/images/pop.jpg")
    )
}
