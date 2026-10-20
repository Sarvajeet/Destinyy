package com.destinyy.app.domain.eta

import kotlin.math.ceil

/**
 * Distance-only fallback. This is not route ETA and must not be presented as precise.
 * Distances are straight-line meters; the multiplier approximates route detours.
 */
object EtaEstimator {
    fun minutesFromStraightLineMeters(distanceMeters: Double, mode: String): Int {
        if (!distanceMeters.isFinite() || distanceMeters < 0.0) return 1
        val averageKmh = when (mode) {
            "CAB_CAR" -> 24.0
            "BUS" -> 16.0
            "WALKING" -> 4.0
            else -> 32.0
        }
        val routeMultiplier = when (mode) {
            "CAB_CAR" -> 1.35
            "BUS" -> 1.45
            "WALKING" -> 1.25
            else -> 1.55
        }
        return ceil(distanceMeters / 1000.0 * routeMultiplier / averageKmh * 60.0)
            .toInt()
            .coerceAtLeast(1)
    }
}
