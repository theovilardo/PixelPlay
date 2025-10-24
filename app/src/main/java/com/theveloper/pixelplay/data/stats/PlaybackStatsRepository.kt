package com.theveloper.pixelplay.data.stats

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max

@Singleton
class PlaybackStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val gson = Gson()
    private val historyFile = File(context.filesDir, "playback_history.json")
    private val fileLock = Any()
    private val eventsType = object : TypeToken<MutableList<PlaybackEvent>>() {}.type

    private val sessionGapThresholdMs = TimeUnit.MINUTES.toMillis(30)

    data class PlaybackEvent(
        val songId: String,
        val timestamp: Long,
        val durationMs: Long
    )

    data class SongPlaybackSummary(
        val songId: String,
        val title: String,
        val artist: String,
        val albumArtUri: String?,
        val totalDurationMs: Long,
        val playCount: Int
    )

    data class ArtistPlaybackSummary(
        val artist: String,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueSongs: Int
    )

    data class AlbumPlaybackSummary(
        val album: String,
        val albumArtUri: String?,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueSongs: Int
    )

    data class TimelineEntry(
        val label: String,
        val totalDurationMs: Long,
        val playCount: Int
    )

    data class PlaybackStatsSummary(
        val range: StatsTimeRange,
        val startTimestamp: Long?,
        val endTimestamp: Long,
        val totalDurationMs: Long,
        val totalPlayCount: Int,
        val uniqueSongs: Int,
        val averageDailyDurationMs: Long,
        val topSongs: List<SongPlaybackSummary>,
        val timeline: List<TimelineEntry>,
        val topArtists: List<ArtistPlaybackSummary>,
        val topAlbums: List<AlbumPlaybackSummary>,
        val activeDays: Int,
        val longestStreakDays: Int,
        val totalSessions: Int,
        val averageSessionDurationMs: Long,
        val longestSessionDurationMs: Long,
        val averageSessionsPerDay: Double,
        val peakTimeline: TimelineEntry?,
        val peakDayLabel: String?,
        val peakDayDurationMs: Long
    )

    fun recordPlayback(
        songId: String,
        durationMs: Long,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (songId.isBlank()) return
        val sanitizedEvent = PlaybackEvent(
            songId = songId,
            timestamp = timestamp.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L)
        )
        synchronized(fileLock) {
            val events = readEventsLocked()
            events += sanitizedEvent
            val cutoff = sanitizedEvent.timestamp - MAX_HISTORY_AGE_MS
            val pruned = if (cutoff > 0) {
                events.filterTo(mutableListOf()) { it.timestamp >= cutoff }
            } else {
                events
            }
            writeEventsLocked(pruned)
        }
    }

    fun loadSummary(
        range: StatsTimeRange,
        songs: List<Song>,
        nowMillis: Long = System.currentTimeMillis()
    ): PlaybackStatsSummary {
        val zoneId = ZoneId.systemDefault()
        val allEvents = readEvents()
        val (startBound, endBound) = range.resolveBounds(allEvents, nowMillis, zoneId)
        val filteredEvents = allEvents.filter { event ->
            val ts = event.timestamp
            val afterStart = startBound?.let { ts >= it } ?: true
            afterStart && ts <= endBound
        }

        val effectiveStart = startBound
            ?: filteredEvents.minOfOrNull { it.timestamp }
            ?: allEvents.minOfOrNull { it.timestamp }
        val effectiveEnd = filteredEvents.maxOfOrNull { it.timestamp } ?: endBound

        val totalDuration = filteredEvents.sumOf { it.durationMs }
        val totalPlays = filteredEvents.size
        val uniqueSongs = filteredEvents.map { it.songId }.toSet().size

        val songMap = songs.associateBy { it.id }
        val topSongs = filteredEvents
            .groupBy { it.songId }
            .map { (songId, eventsForSong) ->
                val song = songMap[songId]
                SongPlaybackSummary(
                    songId = songId,
                    title = song?.title ?: "Unknown Track",
                    artist = song?.artist ?: "Unknown Artist",
                    albumArtUri = song?.albumArtUriString,
                    totalDurationMs = eventsForSong.sumOf { it.durationMs },
                    playCount = eventsForSong.size
                )
            }
            .sortedWith(
                compareByDescending<SongPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
            .take(5)

        var daySpan = 1L
        val averageDailyDuration = if (effectiveStart != null) {
            val startInstant = Instant.ofEpochMilli(effectiveStart)
            val endInstant = Instant.ofEpochMilli(effectiveEnd)
            val startDate = startInstant.atZone(zoneId).toLocalDate()
            val endDate = endInstant.atZone(zoneId).toLocalDate()
            daySpan = max(1L, java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1)
            if (daySpan > 0) totalDuration / daySpan else totalDuration
        } else {
            totalDuration
        }

        val eventsByDay = filteredEvents.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate()
        }
        val activeDays = eventsByDay.size
        val sortedDays = eventsByDay.keys.sorted()
        var longestStreak = 0
        var currentStreak = 0
        var lastDay: java.time.LocalDate? = null
        sortedDays.forEach { day ->
            if (lastDay == null || day == lastDay?.plusDays(1)) {
                currentStreak += 1
            } else {
                currentStreak = 1
            }
            if (currentStreak > longestStreak) {
                longestStreak = currentStreak
            }
            lastDay = day
        }

        val sessions = computeListeningSessions(filteredEvents)
        val totalSessions = sessions.size
        val totalSessionDuration = sessions.sumOf { it.totalDuration }
        val averageSessionDuration = if (totalSessions > 0) totalSessionDuration / totalSessions else 0L
        val longestSessionDuration = sessions.maxOfOrNull { it.totalDuration } ?: 0L
        val averageSessionsPerDay = if (daySpan > 0) totalSessions.toDouble() / daySpan else 0.0

        val timelineBuckets = createTimelineBuckets(
            range = range,
            zoneId = zoneId,
            now = Instant.ofEpochMilli(endBound),
            events = filteredEvents,
            fallbackStart = effectiveStart ?: endBound
        )
        val timelineEntries = accumulateTimelineEntries(timelineBuckets, filteredEvents)

        val topArtists = filteredEvents
            .groupBy { event ->
                songMap[event.songId]?.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            }
            .map { (artist, eventsForArtist) ->
                val uniqueSongCount = eventsForArtist.map { it.songId }.toSet().size
                ArtistPlaybackSummary(
                    artist = artist,
                    totalDurationMs = eventsForArtist.sumOf { it.durationMs },
                    playCount = eventsForArtist.size,
                    uniqueSongs = uniqueSongCount
                )
            }
            .sortedWith(
                compareByDescending<ArtistPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
            .take(5)

        val topAlbums = filteredEvents
            .groupBy { event ->
                val song = songMap[event.songId]
                song?.album?.takeIf { it.isNotBlank() } ?: "Unknown Album"
            }
            .map { (album, eventsForAlbum) ->
                val uniqueSongCount = eventsForAlbum.map { it.songId }.toSet().size
                val firstSong = eventsForAlbum
                    .asSequence()
                    .mapNotNull { songMap[it.songId] }
                    .firstOrNull()
                AlbumPlaybackSummary(
                    album = album,
                    albumArtUri = firstSong?.albumArtUriString,
                    totalDurationMs = eventsForAlbum.sumOf { it.durationMs },
                    playCount = eventsForAlbum.size,
                    uniqueSongs = uniqueSongCount
                )
            }
            .sortedWith(
                compareByDescending<AlbumPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
            .take(5)

        val peakTimeline = timelineEntries
            .filter { it.totalDurationMs > 0L }
            .maxByOrNull { it.totalDurationMs }

        val durationsByDayOfWeek = filteredEvents.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zoneId).dayOfWeek
        }
        val peakDay = durationsByDayOfWeek.maxByOrNull { entry ->
            entry.value.sumOf { it.durationMs }
        }
        val peakDayLabel = peakDay?.key?.getDisplayName(TextStyle.FULL, Locale.US)
        val peakDayDuration = peakDay?.value?.sumOf { it.durationMs } ?: 0L

        return PlaybackStatsSummary(
            range = range,
            startTimestamp = startBound,
            endTimestamp = endBound,
            totalDurationMs = totalDuration,
            totalPlayCount = totalPlays,
            uniqueSongs = uniqueSongs,
            averageDailyDurationMs = averageDailyDuration,
            topSongs = topSongs,
            timeline = timelineEntries,
            topArtists = topArtists,
            topAlbums = topAlbums,
            activeDays = activeDays,
            longestStreakDays = longestStreak,
            totalSessions = totalSessions,
            averageSessionDurationMs = averageSessionDuration,
            longestSessionDurationMs = longestSessionDuration,
            averageSessionsPerDay = averageSessionsPerDay,
            peakTimeline = peakTimeline,
            peakDayLabel = peakDayLabel,
            peakDayDurationMs = peakDayDuration
        )
    }

    private fun readEvents(): List<PlaybackEvent> = synchronized(fileLock) { readEventsLocked() }

    private fun readEventsLocked(): MutableList<PlaybackEvent> {
        if (!historyFile.exists()) {
            return mutableListOf()
        }
        val raw = runCatching { historyFile.readText() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return mutableListOf()

        return runCatching {
            val element = gson.fromJson(raw, JsonElement::class.java)
            parseElement(element)
        }.getOrElse { mutableListOf() }
    }

    private fun parseElement(element: JsonElement?): MutableList<PlaybackEvent> {
        if (element == null || element.isJsonNull) return mutableListOf()
        if (element.isJsonArray) {
            val parsed: MutableList<PlaybackEvent> = gson.fromJson(element, eventsType)
            return parsed.mapTo(mutableListOf()) { sanitizeEvent(it) }
        }
        return mutableListOf()
    }

    private fun sanitizeEvent(event: PlaybackEvent): PlaybackEvent {
        return event.copy(
            songId = event.songId,
            timestamp = event.timestamp.coerceAtLeast(0L),
            durationMs = event.durationMs.coerceAtLeast(0L)
        )
    }

    private data class ListeningSessionAggregate(
        var start: Long,
        var end: Long,
        var totalDuration: Long,
        var playCount: Int
    )

    private fun computeListeningSessions(events: List<PlaybackEvent>): List<ListeningSessionAggregate> {
        if (events.isEmpty()) return emptyList()
        val sorted = events.sortedBy { it.timestamp }
        val sessions = mutableListOf<ListeningSessionAggregate>()

        var current = ListeningSessionAggregate(
            start = sorted.first().timestamp,
            end = sorted.first().timestamp + sorted.first().durationMs,
            totalDuration = sorted.first().durationMs,
            playCount = 1
        )

        for (index in 1 until sorted.size) {
            val event = sorted[index]
            val eventEnd = event.timestamp + event.durationMs
            val gap = event.timestamp - current.end
            if (gap <= sessionGapThresholdMs) {
                current.end = max(current.end, eventEnd)
                current.totalDuration += event.durationMs
                current.playCount += 1
            } else {
                sessions += current
                current = ListeningSessionAggregate(
                    start = event.timestamp,
                    end = eventEnd,
                    totalDuration = event.durationMs,
                    playCount = 1
                )
            }
        }

        sessions += current
        return sessions
    }

    private fun writeEventsLocked(events: MutableList<PlaybackEvent>) {
        val sanitized = events.map { sanitizeEvent(it) }
        runCatching {
            historyFile.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            historyFile.writeText(gson.toJson(sanitized))
        }
    }

    private fun accumulateTimelineEntries(
        buckets: List<TimelineBucket>,
        events: List<PlaybackEvent>
    ): List<TimelineEntry> {
        if (buckets.isEmpty()) return emptyList()
        val durationByBucket = LongArray(buckets.size)
        val playCountByBucket = IntArray(buckets.size)
        events.forEach { event ->
            val index = buckets.indexOfFirst { bucket ->
                val isAfterStart = event.timestamp >= bucket.startMillis
                val isBeforeEnd = if (bucket.inclusiveEnd) {
                    event.timestamp <= bucket.endMillis
                } else {
                    event.timestamp < bucket.endMillis
                }
                isAfterStart && isBeforeEnd
            }
            if (index >= 0) {
                durationByBucket[index] += event.durationMs
                playCountByBucket[index] += 1
            }
        }
        return buckets.mapIndexed { index, bucket ->
            TimelineEntry(
                label = bucket.label,
                totalDurationMs = durationByBucket[index],
                playCount = playCountByBucket[index]
            )
        }
    }

    private fun createTimelineBuckets(
        range: StatsTimeRange,
        zoneId: ZoneId,
        now: Instant,
        events: List<PlaybackEvent>,
        fallbackStart: Long
    ): List<TimelineBucket> {
        return when (range) {
            StatsTimeRange.DAY -> createDayBuckets(zoneId, now)
            StatsTimeRange.WEEK -> createWeekBuckets(zoneId, now)
            StatsTimeRange.MONTH -> createMonthBuckets(zoneId, now)
            StatsTimeRange.YEAR -> createYearBuckets(zoneId, now)
            StatsTimeRange.ALL -> createAllTimeBuckets(zoneId, events, fallbackStart, now)
        }
    }

    private fun createDayBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
        val dayStart = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
        val formatter = DateTimeFormatter.ofPattern("ha", Locale.US)
        return (0 until 6).map { index ->
            val bucketStart = dayStart.plus(Duration.ofHours((index * 4).toLong()))
            val bucketEnd = bucketStart.plus(Duration.ofHours(4))
            val label = formatter.format(bucketStart.atZone(zoneId)).lowercase(Locale.US)
            TimelineBucket(
                label = label,
                startMillis = bucketStart.toEpochMilli(),
                endMillis = bucketEnd.toEpochMilli(),
                inclusiveEnd = false
            )
        }
    }

    private fun createWeekBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
        val startOfWeek = now.atZone(zoneId)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0 until 7).map { index ->
            val day = startOfWeek.plusDays(index.toLong())
            val start = day.atStartOfDay(zoneId).toInstant()
            val end = day.plusDays(1).atStartOfDay(zoneId).toInstant()
            TimelineBucket(
                label = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US),
                startMillis = start.toEpochMilli(),
                endMillis = end.toEpochMilli(),
                inclusiveEnd = false
            )
        }
    }

    private fun createMonthBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
        val yearMonth = YearMonth.from(now.atZone(zoneId))
        val daysInMonth = yearMonth.lengthOfMonth()
        val bucketCount = 5
        val daysPerBucket = ceil(daysInMonth / bucketCount.toDouble()).toInt().coerceAtLeast(1)
        return (0 until bucketCount).map { index ->
            val startDay = index * daysPerBucket + 1
            if (startDay > daysInMonth) {
                TimelineBucket(
                    label = "Week ${index + 1}",
                    startMillis = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    endMillis = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    inclusiveEnd = false
                )
            } else {
                val endDay = minOf(startDay + daysPerBucket - 1, daysInMonth)
                val start = yearMonth.atDay(startDay).atStartOfDay(zoneId).toInstant()
                val end = yearMonth.atDay(endDay).plusDays(1).atStartOfDay(zoneId).toInstant()
                TimelineBucket(
                    label = "Week ${index + 1}",
                    startMillis = start.toEpochMilli(),
                    endMillis = end.toEpochMilli(),
                    inclusiveEnd = false
                )
            }
        }
    }

    private fun createYearBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
        val year = Year.from(now.atZone(zoneId))
        return (1..12).map { monthIndex ->
            val start = year.atMonth(monthIndex).atDay(1).atStartOfDay(zoneId).toInstant()
            val end = year.atMonth(monthIndex).atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant()
            TimelineBucket(
                label = year.atMonth(monthIndex).month.getDisplayName(TextStyle.SHORT, Locale.US),
                startMillis = start.toEpochMilli(),
                endMillis = end.toEpochMilli(),
                inclusiveEnd = false
            )
        }
    }

    private fun createAllTimeBuckets(
        zoneId: ZoneId,
        events: List<PlaybackEvent>,
        fallbackStart: Long,
        now: Instant
    ): List<TimelineBucket> {
        val allEvents = if (events.isEmpty()) listOf(PlaybackEvent("", fallbackStart, 0)) else events
        val minTimestamp = allEvents.minOfOrNull { it.timestamp } ?: fallbackStart
        val maxTimestamp = allEvents.maxOfOrNull { it.timestamp } ?: now.toEpochMilli()
        val startYear = Instant.ofEpochMilli(minTimestamp).atZone(zoneId).year
        val endYear = Instant.ofEpochMilli(maxTimestamp).atZone(zoneId).year
        if (startYear > endYear) return emptyList()
        return (startYear..endYear).map { yearValue ->
            val year = Year.of(yearValue)
            val start = year.atDay(1).atStartOfDay(zoneId).toInstant()
            val end = year.plusYears(1).atDay(1).atStartOfDay(zoneId).toInstant()
            TimelineBucket(
                label = yearValue.toString(),
                startMillis = start.toEpochMilli(),
                endMillis = end.toEpochMilli(),
                inclusiveEnd = false
            )
        }
    }

    private fun StatsTimeRange.resolveBounds(
        events: List<PlaybackEvent>,
        nowMillis: Long,
        zoneId: ZoneId
    ): Pair<Long?, Long> {
        val nowInstant = Instant.ofEpochMilli(nowMillis)
        return when (this) {
            StatsTimeRange.DAY -> {
                val start = nowInstant.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
                start to nowMillis
            }
            StatsTimeRange.WEEK -> {
                val start = nowInstant.atZone(zoneId)
                    .toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                start to nowMillis
            }
            StatsTimeRange.MONTH -> {
                val start = YearMonth.from(nowInstant.atZone(zoneId))
                    .atDay(1)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                start to nowMillis
            }
            StatsTimeRange.YEAR -> {
                val start = nowInstant.atZone(zoneId)
                    .toLocalDate()
                    .withDayOfYear(1)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                start to nowMillis
            }
            StatsTimeRange.ALL -> {
                val start = events.minOfOrNull { it.timestamp }
                start to nowMillis
            }
        }
    }

    private data class TimelineBucket(
        val label: String,
        val startMillis: Long,
        val endMillis: Long,
        val inclusiveEnd: Boolean
    )

    companion object {
        private val MAX_HISTORY_AGE_MS = TimeUnit.DAYS.toMillis(730) // Keep roughly two years of history
    }
}

enum class StatsTimeRange(val displayName: String) {
    DAY("Today"),
    WEEK("This Week"),
    MONTH("This Month"),
    YEAR("This Year"),
    ALL("All Time")
}
