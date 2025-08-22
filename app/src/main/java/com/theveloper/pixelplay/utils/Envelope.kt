package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Curve
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Calculates a volume multiplier based on the progress of a transition and a given curve.
 *
 * This function maps a linear progress value (0.0 to 1.0) to a non-linear value
 * according to the selected curve, which can be used to shape volume fades.
 *
 * @param progress A Float from 0.0 (start) to 1.0 (end) representing the transition's progress.
 * @param curve The volume curve to apply.
 * @return A Float from 0.0 to 1.0 representing the calculated volume multiplier.
 */
fun envelope(progress: Float, curve: Curve): Float {
    // Ensure progress is clamped between 0.0 and 1.0 to avoid unexpected math results
    val clampedProgress = progress.coerceIn(0f, 1f)

    return when (curve) {
        Curve.LINEAR -> clampedProgress
        Curve.S_CURVE -> ((1 - cos(PI * clampedProgress)) / 2f).toFloat()
        Curve.LOG -> sqrt(clampedProgress)
        Curve.EXP -> clampedProgress * clampedProgress
    }
}
