package com.theveloper.pixelplay.data

import android.content.Context
import com.google.gson.Gson
import com.theveloper.pixelplay.data.PlaybackStatsOverview
import com.theveloper.pixelplay.data.PlaybackTrendEntry
import com.theveloper.pixelplay.data.SongPlaybackSummary
import com.theveloper.pixelplay.data.StatsTimeframe
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Singleton
class DailyMixManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val gson = Gson()
    private val scoresFile = File(context.filesDir, "song_scores.json")
    private val playbackHistoryFile = File(context.filesDir, "playback_history.json")

    data class SongEngagementStats(
        val playCount: Int = 0,
        val totalPlayDurationMs: Long = 0L,
        val lastPlayedTimestamp: Long = 0L
    )

    data class PlaybackEvent(
        val songId: String,
        val durationMs: Long,
        val timestamp: Long
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
        recordPlaybackEvent(songId, songDurationMs, timestamp)
    }

    fun incrementScore(songId: String) {
        recordPlay(songId)
    }

    private fun readPlaybackHistory(): MutableList<PlaybackEvent> {
        if (!playbackHistoryFile.exists()) {
            return mutableListOf()
        }

        val jsonText = playbackHistoryFile.readText()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<MutableList<PlaybackEvent>>() {}.type
            gson.fromJson<MutableList<PlaybackEvent>>(jsonText, type) ?: mutableListOf()
        } catch (ignored: Exception) {
            mutableListOf()
        }
    }

    private fun savePlaybackHistory(events: List<PlaybackEvent>) {
        playbackHistoryFile.writeText(gson.toJson(events))
    }

    private fun recordPlaybackEvent(songId: String, songDurationMs: Long, timestamp: Long) {
        val sanitizedDuration = songDurationMs.coerceAtLeast(TimeUnit.SECONDS.toMillis(5))
        val events = readPlaybackHistory()
        events.add(
            PlaybackEvent(
                songId = songId,
                durationMs = sanitizedDuration,
                timestamp = timestamp
            )
        )

        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(550)
        val pruned = events.filter { it.timestamp >= cutoff }
        savePlaybackHistory(pruned)
    }

    fun getPlaybackStats(timeframe: StatsTimeframe, allSongs: List<Song>): PlaybackStatsOverview {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zoneId)

        val buckets = createBuckets(timeframe, now)
        val songById = allSongs.associateBy { it.id }
        val events = readPlaybackHistory()

        val filteredEvents = when (timeframe) {
            StatsTimeframe.ALL -> events
            else -> {
                val startMillis = buckets.minOfOrNull { it.startEpochMillis } ?: 0L
                events.filter { it.timestamp >= startMillis }
            }
        }

        val totalDuration = filteredEvents.sumOf { it.durationMs }
        val totalPlays = filteredEvents.size

        val chartDurations = buckets.map { bucket ->
            val bucketDuration = filteredEvents
                .asSequence()
                .filter { it.timestamp in bucket.startEpochMillis..bucket.endEpochMillis }
                .sumOf { it.durationMs }
            PlaybackTrendEntry(
                label = bucket.label,
                durationMs = bucketDuration,
                startEpochMillis = bucket.startEpochMillis,
                endEpochMillis = bucket.endEpochMillis,
                isCurrentPeriod = bucket.isCurrent,
                isPeak = false
            )
        }

        val maxChartDuration = chartDurations.maxOfOrNull { it.durationMs } ?: 0L
        val enrichedChart = chartDurations.map {
            if (maxChartDuration == 0L) {
                it
            } else {
                it.copy(isPeak = it.durationMs == maxChartDuration)
            }
        }

        val topSongs = filteredEvents
            .groupBy { it.songId }
            .mapNotNull { (songId, plays) ->
                val song = songById[songId] ?: return@mapNotNull null
                SongPlaybackSummary(
                    song = song,
                    playCount = plays.size,
                    totalDurationMs = plays.sumOf { it.durationMs }
                )
            }
            .sortedWith(
                compareByDescending<SongPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
            .take(5)

        val averagePerBucket = if (enrichedChart.isEmpty()) 0L else totalDuration / max(enrichedChart.size, 1)

        return PlaybackStatsOverview(
            timeframe = timeframe,
            totalDurationMs = totalDuration,
            totalPlayCount = totalPlays,
            averageDurationPerBucketMs = averagePerBucket,
            chartEntries = enrichedChart,
            topSongs = topSongs
        )
    }

    private data class TimeBucket(
        val label: String,
        val startEpochMillis: Long,
        val endEpochMillis: Long,
        val isCurrent: Boolean
    )

    private fun createBuckets(timeframe: StatsTimeframe, now: ZonedDateTime): List<TimeBucket> {
        val locale = Locale.getDefault()
        val zoneId = now.zone
        return when (timeframe) {
            StatsTimeframe.DAY -> {
                val startOfDay = now.toLocalDate().atStartOfDay(zoneId)
                (0 until 6).map { blockIndex ->
                    val startHour = blockIndex * 4
                    val endHour = startHour + 4
                    val start = startOfDay.plusHours(startHour.toLong())
                    val end = startOfDay.plusHours(endHour.toLong()).minusNanos(1)
                    TimeBucket(
                        label = String.format(locale, "%02d-%02dh", startHour, endHour),
                        startEpochMillis = start.toInstant().toEpochMilli(),
                        endEpochMillis = end.toInstant().toEpochMilli(),
                        isCurrent = now.isAfter(start) && now.isBefore(end.plusNanos(1))
                    )
                }
            }

            StatsTimeframe.WEEK -> {
                val startOfWeek = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(zoneId)
                (0 until 7).map { offset ->
                    val start = startOfWeek.plusDays(offset.toLong())
                    val end = start.plusDays(1).minusNanos(1)
                    val label = start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(3)
                    TimeBucket(
                        label = label,
                        startEpochMillis = start.toInstant().toEpochMilli(),
                        endEpochMillis = end.toInstant().toEpochMilli(),
                        isCurrent = now.isAfter(start) && now.isBefore(end.plusNanos(1))
                    )
                }
            }

            StatsTimeframe.MONTH -> {
                val startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId)
                val endOfMonth = startOfMonth.plusMonths(1).minusNanos(1)
                val totalDays = now.toLocalDate().lengthOfMonth()
                val bucketSize = max(1, totalDays / 4)
                var currentStart = startOfMonth
                val buckets = mutableListOf<TimeBucket>()
                var index = 1
                while (!currentStart.isAfter(endOfMonth)) {
                    val tentativeEnd = currentStart.plusDays(bucketSize.toLong()).minusNanos(1)
                    val endDate = if (tentativeEnd.isAfter(endOfMonth)) endOfMonth else tentativeEnd
                    val label = String.format(locale, "Sem %d", index)
                    val isCurrent = now.isAfter(currentStart) && now.isBefore(endDate.plusNanos(1))
                    buckets += TimeBucket(
                        label = label,
                        startEpochMillis = currentStart.toInstant().toEpochMilli(),
                        endEpochMillis = endDate.toInstant().toEpochMilli(),
                        isCurrent = isCurrent
                    )
                    currentStart = endDate.plusNanos(1)
                    index++
                }
                buckets
            }

            StatsTimeframe.YEAR -> {
                val startOfYear = now.toLocalDate().withDayOfYear(1).atStartOfDay(zoneId)
                (0 until 12).map { monthIndex ->
                    val monthStart = startOfYear.plusMonths(monthIndex.toLong())
                    val monthEnd = monthStart.plusMonths(1).minusNanos(1)
                    val label = monthStart.month.getDisplayName(TextStyle.SHORT, locale)
                    TimeBucket(
                        label = label,
                        startEpochMillis = monthStart.toInstant().toEpochMilli(),
                        endEpochMillis = monthEnd.toInstant().toEpochMilli(),
                        isCurrent = now.isAfter(monthStart) && now.isBefore(monthEnd.plusNanos(1))
                    )
                }
            }

            StatsTimeframe.ALL -> {
                val startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId)
                (11 downTo 0).map { monthsAgo ->
                    val monthStart = startOfMonth.minusMonths(monthsAgo.toLong())
                    val monthEnd = monthStart.plusMonths(1).minusNanos(1)
                    val label = monthStart.month.getDisplayName(TextStyle.SHORT, locale)
                    TimeBucket(
                        label = label,
                        startEpochMillis = monthStart.toInstant().toEpochMilli(),
                        endEpochMillis = monthEnd.toInstant().toEpochMilli(),
                        isCurrent = now.isAfter(monthStart) && now.isBefore(monthEnd.plusNanos(1))
                    )
                }
            }
        }
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
