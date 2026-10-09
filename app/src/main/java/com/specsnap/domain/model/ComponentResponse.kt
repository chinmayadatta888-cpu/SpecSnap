package com.specsnap.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ComponentResponse(
    val partNumber: String,
    val manufacturer: String,
    val category: String,
    val description: String,
    val pinouts: List<PinoutEntry>,
    val electricalRatings: ElectricalRatings,
    val safetyAlerts: List<String> = emptyList(),
)

@Serializable
data class PinoutEntry(
    val pinNumber: Int,
    val pinName: String,
    val function: String,
)

@Serializable
data class ElectricalRatings(
    val minVoltage: String,
    val maxVoltage: String,
    val maxCurrent: String,
    val operatingTempRange: String,
)
