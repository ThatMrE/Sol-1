package com.thatmre.sol1.data

import kotlinx.serialization.Serializable

@Serializable
enum class TrackerType { COUNT, HABIT }

/** One logged reset of a COUNT tracker: the streak that ended and how long it lasted. */
@Serializable
data class Reset(val start: Long, val end: Long, val days: Int)

@Serializable
data class Tracker(
    val id: String,
    val name: String,
    val type: TrackerType,
    /** Epoch millis the count started from (COUNT trackers). */
    val start: Long = 0L,
    val history: List<Reset> = emptyList(),
    /** ISO local dates ("2026-09-13") with a check-in (HABIT trackers). */
    val checkins: List<String> = emptyList(),
)
