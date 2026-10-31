package com.destinyy.app.domain.eta

import kotlin.math.ceil

/**
 * Estimates remaining time from the device's measured GPS ground speed.
 *
 * Remaining distance is a straight-line GPS-to-destination distance adjusted
 * by a mode-specific detour factor. This is GPS-motion-based, not a map-route
 * or live-traffic ETA.
 */
class GpsEtaEstimator(
    private val smoothingFactor: Double = 0.25
) {
    var currentSpeedMetersPerSecond: Double? = null
        private set

    init {
        require(smoothingFactor in 0.0..1.0)
    }

    fun addSpeedSample(
        speedMetersPerSecond: Double,
        speedAccuracyMetersPerSecond: Double? = null,
        mode: String
    ): Double? {
        if (!speedMetersPerSecond.isFinite() || speedMetersPerSecond < 0.0) {
            return currentSpeedMetersPerSecond
        }
        if (speedAccuracyMetersPerSecond != null &&
            (!speedAccuracyMetersPerSecond.isFinite() || speedAccuracyMetersPerSecond > 3.0)
        ) {
            return currentSpeedMetersPerSecond
        }

        val maxSpeed = when (mode) {
            "WALKING" -> 3.5
            "BUS" -> 35.0
            "TRAIN_METRO" -> 50.0
            else -> 45.0
        }
        if (speedMetersPerSecond > maxSpeed) return currentSpeedMetersPerSecond

        val previous = currentSpeedMetersPerSecond
        currentSpeedMetersPerSecond = if (previous == null) {
            speedMetersPerSecond
        } else {
            previous + smoothingFactor * (speedMetersPerSecond - previous)
        }
        return currentSpeedMetersPerSecond
    }

    fun estimateMinutes(remainingStraightLineMeters: Double, mode: String): Int? {
        if (!remainingStraightLineMeters.isFinite() || remainingStraightLineMeters < 0.0) return null
        if (remainingStraightLineMeters <= 1.0) return 1

        val speed = currentSpeedMetersPerSecond ?: return null
        val minimumUsefulSpeed = if (mode == "WALKING") 0.45 else 0.8
        if (speed < minimumUsefulSpeed) return null

        val routeDetourFactor = when (mode) {
            "CAB_CAR" -> 1.35
            "BUS" -> 1.45
            "WALKING" -> 1.25
            else -> 1.55
        }
        return ceil(
            remainingStraightLineMeters * routeDetourFactor / speed / 60.0
        ).toInt().coerceAtLeast(1)
    }

    fun reset() {
        currentSpeedMetersPerSecond = null
    }
}
