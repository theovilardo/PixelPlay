package com.theveloper.pixelplay.data.model

import androidx.room.Embedded

/**
 * Defines the available transition modes between songs.
 */
enum class TransitionMode {
    /** No transition, the next song starts after the previous one ends. */
    NONE,
    /** The current song fades out completely before the next song fades in. */
    FADE_IN_OUT,
    /** The current song fades out while the next song fades in, overlapping them. */
    OVERLAP,
    /** A smooth, S-shaped curve is used for fading in and out during the overlap. */
    SMOOTH
}

/**
 * Defines the shape of the volume curve for transitions.
 */
enum class Curve {
    /** Linear volume change. */
    LINEAR,
    /** Exponential (fast start, slow end) volume change. */
    EXP,
    /** Logarithmic (slow start, fast end) volume change. */
    LOG,
    /** S-shaped (sigmoid) curve for a very smooth transition. */
    S_CURVE
}

/**
 * Indicates where a transition configuration came from so callers can resolve
 * priority (playlist-specific rules should override global defaults).
 */
enum class TransitionSource {
    /** Fallback to the global default transition settings. */
    GLOBAL_DEFAULT,

    /** A playlist-wide default rule. */
    PLAYLIST_DEFAULT,

    /** A rule targeted to the specific track combination. */
    PLAYLIST_SPECIFIC,
}

/**
 * Holds the settings for a specific transition.
 *
 * @param mode The type of transition to apply.
 * @param durationMs The duration of the transition in milliseconds.
 * @param curveIn The curve to use for the incoming track's volume.
 * @param curveOut The curve to use for the outgoing track's volume.
 */
data class TransitionSettings(
    val mode: TransitionMode = TransitionMode.OVERLAP,
    val durationMs: Int = 6000,
    val curveIn: Curve = Curve.S_CURVE,
    val curveOut: Curve = Curve.S_CURVE,
)

/**
 * Wraps transition settings with the source they were resolved from.
 */
data class TransitionResolution(
    val settings: TransitionSettings,
    val source: TransitionSource,
)

/**
 * Represents a rule for a transition within a specific context.
 *
 * @param playlistId The ID of the playlist this rule applies to.
 * @param fromTrackId The ID of the track to transition from. If null, this is a default rule for the playlist.
 * @param toTrackId The ID of the track to transition to. If null, this is a default rule for the playlist.
 * @param settings The transition settings to apply for this rule.
 */
data class TransitionRule(
    val id: Long = 0,
    val playlistId: String,
    val fromTrackId: String? = null,
    val toTrackId: String? = null,
    @Embedded val settings: TransitionSettings,
)
