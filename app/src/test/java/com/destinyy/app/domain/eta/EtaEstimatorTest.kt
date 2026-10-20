package com.destinyy.app.domain.eta

import org.junit.Assert.assertEquals
import org.junit.Test

class EtaEstimatorTest {
    @Test fun zeroDistanceStillReturnsMinimumOneMinute() {
        assertEquals(1, EtaEstimator.minutesFromStraightLineMeters(0.0, "WALKING"))
    }

    @Test fun walkingEstimateIsSlowerThanCarForSameDistance() {
        val distance = 2_000.0
        val walking = EtaEstimator.minutesFromStraightLineMeters(distance, "WALKING")
        val car = EtaEstimator.minutesFromStraightLineMeters(distance, "CAB_CAR")
        assert(walking > car)
    }

    @Test fun invalidDistanceFallsBackToOneMinute() {
        assertEquals(1, EtaEstimator.minutesFromStraightLineMeters(Double.NaN, "BUS"))
        assertEquals(1, EtaEstimator.minutesFromStraightLineMeters(-100.0, "BUS"))
    }

    @Test fun unknownModeUsesDefaultEstimate() {
        assertEquals(
            EtaEstimator.minutesFromStraightLineMeters(5_000.0, "TRAIN_METRO"),
            EtaEstimator.minutesFromStraightLineMeters(5_000.0, "unknown")
        )
    }
}
