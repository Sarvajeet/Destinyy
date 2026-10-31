package com.destinyy.app.domain.model

enum class JourneyMode {
    TRAIN_METRO, CAB_CAR, BUS, WALKING
}

/** One planned leg in the user's ordered, multi-stop journey. */
data class JourneyLegDraft(
    val destinationName: String = "",
    val mode: String = "TRAIN_METRO",
    val alertMinutes: Set<Int> = setOf(10, 5, 2)
)

/** A stop after its destination has been resolved to device coordinates. */
data class JourneyStop(
    val destinationName: String,
    val latitude: Double,
    val longitude: Double,
    val mode: JourneyMode,
    val alertMinutes: Set<Int> = setOf(10, 5, 2)
)

data class Journey(val stops: List<JourneyStop>) {
    init {
        require(stops.isNotEmpty()) { "A journey must contain at least one stop." }
    }
}
