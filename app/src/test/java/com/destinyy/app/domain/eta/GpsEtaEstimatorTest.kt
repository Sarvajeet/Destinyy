package com.destinyy.app.domain.eta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsEtaEstimatorTest {
    @Test
    fun calculatesEtaFromMeasuredGpsGroundSpeed() {
        val estimator = GpsEtaEstimator()
        estimator.addSpeedSample(10.0, mode = "CAB_CAR")

        // 1,000 m straight-line x 1.35 road-detour factor at 10 m/s = 2 minutes.
        assertEquals(2, estimator.estimateMinutes(1_000.0, "CAB_CAR"))
    }

    @Test
    fun smoothsNoisyGpsSpeedSamples() {
        val estimator = GpsEtaEstimator(smoothingFactor = 0.25)
        estimator.addSpeedSample(10.0, mode = "CAB_CAR")

        assertEquals(12.5, estimator.addSpeedSample(20.0, mode = "CAB_CAR")!!, 0.001)
    }

    @Test
    fun rejectsSpeedWithPoorGpsSpeedAccuracy() {
        val estimator = GpsEtaEstimator()
        estimator.addSpeedSample(10.0, mode = "CAB_CAR")

        assertEquals(
            10.0,
            estimator.addSpeedSample(
                speedMetersPerSecond = 25.0,
                speedAccuracyMetersPerSecond = 8.0,
                mode = "CAB_CAR"
            )!!,
            0.001
        )
    }

    @Test
    fun returnsNoEtaWhenGpsShowsStationaryMovement() {
        val estimator = GpsEtaEstimator()
        estimator.addSpeedSample(0.0, mode = "TRAIN_METRO")

        assertNull(estimator.estimateMinutes(2_000.0, "TRAIN_METRO"))
    }

    @Test
    fun rejectsImpossibleWalkingSpeed() {
        val estimator = GpsEtaEstimator()

        assertNull(estimator.addSpeedSample(9.0, mode = "WALKING"))
        assertNull(estimator.estimateMinutes(2_000.0, "WALKING"))
    }

    @Test
    fun resetClearsMovementSpeedBetweenStops() {
        val estimator = GpsEtaEstimator()
        estimator.addSpeedSample(8.0, mode = "BUS")
        estimator.reset()

        assertNull(estimator.currentSpeedMetersPerSecond)
        assertNull(estimator.estimateMinutes(2_000.0, "BUS"))
    }
}
