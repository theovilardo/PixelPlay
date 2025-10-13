package com.theveloper.pixelplay.data.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class MusicFolder(
    val path: String,
    val name: String,
    val songs: ImmutableList<Song> = persistentListOf(),
    val subFolders: ImmutableList<MusicFolder> = persistentListOf()
) {
    val totalSongCount: Int by lazy {
        songs.size + subFolders.sumOf { it.totalSongCount }
    }
}