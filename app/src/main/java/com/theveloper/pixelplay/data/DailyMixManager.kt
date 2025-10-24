package com.theveloper.pixelplay.data

import android.content.Context
import com.google.gson.Gson
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyMixManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val gson = Gson()
    private val scoresFile = File(context.filesDir, "song_scores.json")

    data class SongEngagementStats(
        val playCount: Int = 0,
        val totalPlayDurationMs: Long = 0L,
        val lastPlayedTimestamp: Long = 0L
    )

    private fun readEngagements(): MutableMap<String, SongEngagementStats> {
        if (!scoresFile.exists()) {
            return mutableMapOf()
        }

        val jsonText = scoresFile.readText()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, SongEngagementStats>>() {}.type
            gson.fromJson<MutableMap<String, SongEngagementStats>>(jsonText, type) ?: mutableMapOf()
        } catch (statsException: Exception) {
            try {
                val legacyType = object : com.google.gson.reflect.TypeToken<MutableMap<String, Int>>() {}.type
                val legacyMap: MutableMap<String, Int> = gson.fromJson(jsonText, legacyType) ?: mutableMapOf()
                legacyMap.mapValuesTo(mutableMapOf()) { (_, playCount) ->
                    SongEngagementStats(playCount = playCount)
                }
            } catch (ignored: Exception) {
                mutableMapOf()
            }
        }
    }

    private fun saveEngagements(engagements: Map<String, SongEngagementStats>) {
        scoresFile.writeText(gson.toJson(engagements))
    }

    fun recordPlay(
        songId: String,
        songDurationMs: Long = 0L,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val engagements = readEngagements()
        val currentStats = engagements[songId] ?: SongEngagementStats()
        val updatedStats = currentStats.copy(
            playCount = currentStats.playCount + 1,
            totalPlayDurationMs = currentStats.totalPlayDurationMs + songDurationMs.coerceAtLeast(0L),
            lastPlayedTimestamp = timestamp
        )
        engagements[songId] = updatedStats
        saveEngagements(engagements)
    }

    fun incrementScore(songId: String) {
        recordPlay(songId)
    }

    fun getScore(songId: String): Int {
        return readEngagements()[songId]?.playCount ?: 0
    }

    private fun computeRankedSongs(
        allSongs: List<Song>,
        favoriteSongIds: Set<String>,
        random: java.util.Random
    ): List<RankedSong> {
        if (allSongs.isEmpty()) return emptyList()

        val engagements = readEngagements()
        val songById = allSongs.associateBy { it.id }
        val now = System.currentTimeMillis()

        val artistAffinity = mutableMapOf<Long, Double>()
        val genreAffinity = mutableMapOf<String, Double>()

        engagements.forEach { (songId, stats) ->
            val song = songById[songId] ?: return@forEach
            val weight = stats.playCount.toDouble() + (stats.totalPlayDurationMs / 60000.0)
            if (weight <= 0) return@forEach
            artistAffinity.merge(song.artistId, weight, Double::plus)
            song.genre?.lowercase()?.let { genreAffinity.merge(it, weight, Double::plus) }
        }

        val favoriteArtistWeights = mutableMapOf<Long, Int>()
        favoriteSongIds.forEach { id ->
            val song = songById[id] ?: return@forEach
            favoriteArtistWeights.merge(song.artistId, 1, Int::plus)
        }

        val maxPlayCount = engagements.values.maxOfOrNull { it.playCount }?.takeIf { it > 0 } ?: 1
        val maxDuration = engagements.values.maxOfOrNull { it.totalPlayDurationMs }?.takeIf { it > 0L } ?: 1L
        val maxArtistAffinity = artistAffinity.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val maxGenreAffinity = genreAffinity.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val maxFavoriteArtist = favoriteArtistWeights.values.maxOrNull()?.takeIf { it > 0 } ?: 1

        return allSongs.map { song ->
            val stats = engagements[song.id]
            val playCountScore = (stats?.playCount?.toDouble() ?: 0.0) / maxPlayCount
            val durationScore = (stats?.totalPlayDurationMs?.toDouble() ?: 0.0) / maxDuration
            val affinityScore = (playCountScore * 0.7 + durationScore * 0.3).coerceIn(0.0, 1.0)

            val genreKey = song.genre?.lowercase()
            val artistPreference = artistAffinity[song.artistId]?.div(maxArtistAffinity) ?: 0.0
            val genrePreference = genreKey?.let { (genreAffinity[it] ?: 0.0) / maxGenreAffinity } ?: 0.0
            val favoriteArtistPreference = favoriteArtistWeights[song.artistId]?.toDouble()?.div(maxFavoriteArtist) ?: 0.0
            val preferenceScore = listOf(artistPreference, genrePreference, favoriteArtistPreference).maxOrNull() ?: 0.0

            val recencyScore = computeRecencyScore(stats?.lastPlayedTimestamp, now)
            val noveltyScore = computeNoveltyScore(song.dateAdded, now)
            val favoriteScore = if (favoriteSongIds.contains(song.id)) 1.0 else 0.0
            val baselineScore = if (stats == null) 0.1 else 0.0
            val noise = random.nextDouble() * 0.03

            val finalScore = (affinityScore * 0.4) +
                (preferenceScore * 0.2) +
                (recencyScore * 0.2) +
                (favoriteScore * 0.15) +
                (noveltyScore * 0.05) +
                baselineScore +
                noise

            val discoveryScore = ((1.0 - affinityScore).coerceIn(0.0, 1.0) * 0.6) +
                (noveltyScore * 0.25) +
                (preferenceScore * 0.15)

            RankedSong(
                song = song,
                finalScore = finalScore,
                discoveryScore = discoveryScore,
                affinityScore = affinityScore,
                recencyScore = recencyScore,
                noveltyScore = noveltyScore,
                favoriteScore = favoriteScore
            )
        }
            .sortedWith(compareByDescending<RankedSong> { it.finalScore }.thenBy { it.song.id })
    }

    fun generateDailyMix(
        allSongs: List<Song>,
        favoriteSongIds: Set<String> = emptySet(),
        limit: Int = 30
    ): List<Song> {
        if (allSongs.isEmpty()) {
            return emptyList()
        }

        val calendar = Calendar.getInstance()
        val seed = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        val random = java.util.Random(seed.toLong())

        val rankedSongs = computeRankedSongs(allSongs, favoriteSongIds, random)
        if (rankedSongs.isEmpty()) {
            return allSongs.shuffled(random).take(limit.coerceAtMost(allSongs.size))
        }

        val selected = pickWithDiversity(rankedSongs, favoriteSongIds, limit)
        if (selected.size >= limit || selected.size == rankedSongs.size) {
            return selected
        }

        val remaining = allSongs
            .filterNot { song -> selected.any { it.id == song.id } }
            .shuffled(random)

        val combined = (selected + remaining).distinctBy { it.id }
        return combined.take(limit.coerceAtMost(combined.size))
    }

    fun generateYourMix(
        allSongs: List<Song>,
        favoriteSongIds: Set<String> = emptySet(),
        limit: Int = 60
    ): List<Song> {
        if (allSongs.isEmpty()) {
            return emptyList()
        }

        val calendar = Calendar.getInstance()
        val seed = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR) + 17
        val random = java.util.Random(seed.toLong())
        val rankedSongs = computeRankedSongs(allSongs, favoriteSongIds, random)

        if (rankedSongs.isEmpty()) {
            return allSongs.shuffled(random).take(limit.coerceAtMost(allSongs.size))
        }

        val favoriteSectionSize = (limit * 0.3).toInt().coerceAtLeast(5).coerceAtMost(limit)
        val coreSectionSize = (limit * 0.45).toInt().coerceAtLeast(10).coerceAtMost(limit)
        val discoverySectionSize = (limit - favoriteSectionSize - coreSectionSize).coerceAtLeast(0)

        val favoriteSection = pickWithDiversity(
            rankedSongs.filter { favoriteSongIds.contains(it.song.id) },
            favoriteSongIds,
            favoriteSectionSize
        )

        val alreadySelectedIds = favoriteSection.map { it.id }.toMutableSet()

        val coreSection = pickWithDiversity(
            rankedSongs.filterNot { alreadySelectedIds.contains(it.song.id) },
            favoriteSongIds,
            coreSectionSize
        )

        alreadySelectedIds.addAll(coreSection.map { it.id })

        val discoveryCandidates = rankedSongs
            .filterNot { alreadySelectedIds.contains(it.song.id) }
            .sortedWith(compareByDescending<RankedSong> { it.discoveryScore }.thenBy { it.song.id })

        val discoverySection = pickWithDiversity(discoveryCandidates, favoriteSongIds, discoverySectionSize)

        val orderedResult = LinkedHashSet<Song>()
        orderedResult.addAll(favoriteSection)
        orderedResult.addAll(coreSection)
        orderedResult.addAll(discoverySection)

        if (orderedResult.size < limit) {
            val filler = allSongs
                .filterNot { orderedResult.any { selected -> selected.id == it.id } }
                .shuffled(random)
            for (song in filler) {
                orderedResult.add(song)
                if (orderedResult.size >= limit) break
            }
        }

        return orderedResult.take(limit.coerceAtMost(orderedResult.size))
    }

    private fun pickWithDiversity(
        rankedSongs: List<RankedSong>,
        favoriteSongIds: Set<String>,
        limit: Int
    ): List<Song> {
        if (limit <= 0 || rankedSongs.isEmpty()) return emptyList()

        val selected = mutableListOf<Song>()
        val artistCounts = mutableMapOf<Long, Int>()

        for (candidate in rankedSongs) {
            if (selected.size >= limit) break
            val artistId = candidate.song.artistId
            val maxPerArtist = if (favoriteSongIds.contains(candidate.song.id)) 2 else 1
            val currentCount = artistCounts.getOrDefault(artistId, 0)
            if (currentCount >= maxPerArtist) continue

            selected += candidate.song
            artistCounts[artistId] = currentCount + 1
        }

        if (selected.size < limit) {
            for (candidate in rankedSongs) {
                if (selected.size >= limit) break
                if (selected.any { it.id == candidate.song.id }) continue
                selected += candidate.song
            }
        }

        return selected.take(limit)
    }

    private fun computeRecencyScore(lastPlayedTimestamp: Long?, now: Long): Double {
        if (lastPlayedTimestamp == null || lastPlayedTimestamp <= 0L) return 0.6
        val daysSinceLastPlay = ((now - lastPlayedTimestamp).coerceAtLeast(0L) / TimeUnit.DAYS.toMillis(1)).toDouble()
        return when {
            daysSinceLastPlay < 1 -> 0.2
            daysSinceLastPlay < 3 -> 0.5
            daysSinceLastPlay < 7 -> 0.7
            daysSinceLastPlay < 14 -> 0.85
            else -> 1.0
        }
    }

    private fun computeNoveltyScore(dateAdded: Long, now: Long): Double {
        if (dateAdded <= 0L) return 0.0
        val daysSinceAdded = ((now - dateAdded).coerceAtLeast(0L) / TimeUnit.DAYS.toMillis(1)).toDouble()
        return (1.0 - (daysSinceAdded / 60.0)).coerceIn(0.0, 1.0)
    }

    private data class RankedSong(
        val song: Song,
        val finalScore: Double,
        val discoveryScore: Double,
        val affinityScore: Double,
        val recencyScore: Double,
        val noveltyScore: Double,
        val favoriteScore: Double
    )
}
