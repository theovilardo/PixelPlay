package com.theveloper.pixelplay.data.model

data class MusicFolder(
    val path: String,
    val name: String,
    val songs: List<Song>,
    val subFolders: List<MusicFolder>
)