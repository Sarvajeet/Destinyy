package com.destinyy.app.domain.model

enum class JourneyMode { TRAIN_METRO, CAB_CAR, BUS, WALKING }

data class Journey(
    val destinationName: String,
    val latitude: Double,
    val longitude: Double,
    val mode: JourneyMode,
    val alertMinutes: Set<Int> = setOf(10, 5, 2)
)
