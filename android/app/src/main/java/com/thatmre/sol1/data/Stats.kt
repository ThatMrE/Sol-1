package com.thatmre.sol1.data

import java.time.LocalDate

val MILESTONES = listOf(1, 3, 7, 14, 21, 30, 60, 90, 100, 180, 200, 365, 500, 730, 1000, 1825, 3650)

data class Milestone(val next: Int, val prev: Int, val toGo: Int, val progress: Float)

fun milestone(days: Int): Milestone {
    val next = MILESTONES.firstOrNull { it > days } ?: ((days / 365 + 1) * 365)
    val prev = MILESTONES.lastOrNull { it <= days } ?: 0
    val progress = if (next == prev) 1f else (days - prev).toFloat() / (next - prev)
    return Milestone(next, prev, next - days, progress.coerceIn(0f, 1f))
}

data class Stats(
    val kind: TrackerType,
    /** Days elapsed (COUNT) or current streak length (HABIT). */
    val days: Int,
    val hours: Int,
    val mins: Int,
    val secs: Int,
    val longest: Int,
    val total: Int,
    val resets: Int,
    val doneToday: Boolean,
    /** Last 28 days, oldest first, true when checked in (HABIT). */
    val grid: List<Boolean>,
    val milestone: Milestone,
    val start: Long,
) {
    val showHours: Boolean get() = kind == TrackerType.COUNT && days == 0
    val number: Int get() = if (showHours) hours else days
    val unit: String
        get() = when {
            showHours -> if (hours == 1) "hour" else "hours"
            kind == TrackerType.HABIT -> "day streak"
            else -> if (days == 1) "day" else "days"
        }
}

private const val DAY_MS = 86_400_000L

fun dayKey(d: LocalDate): String = d.toString()

fun Tracker.stats(now: Long = System.currentTimeMillis()): Stats = when (type) {
    TrackerType.COUNT -> countStats(now)
    TrackerType.HABIT -> habitStats()
}

private fun Tracker.countStats(now: Long): Stats {
    val ms = (now - start).coerceAtLeast(0L)
    val days = (ms / DAY_MS).toInt()
    val rem = ms - days * DAY_MS
    val hours = (rem / 3_600_000L).toInt()
    val mins = ((rem % 3_600_000L) / 60_000L).toInt()
    val secs = ((rem % 60_000L) / 1000L).toInt()
    val longest = maxOf(days, history.maxOfOrNull { it.days } ?: 0)
    val total = days + history.sumOf { it.days }
    return Stats(type, days, hours, mins, secs, longest, total, history.size, false, emptyList(), milestone(days), start)
}

private fun Tracker.habitStats(): Stats {
    val set = checkins.toSet()
    val today = LocalDate.now()
    val doneToday = set.contains(dayKey(today))
    var d = if (doneToday) today else today.minusDays(1)
    var streak = 0
    while (set.contains(dayKey(d))) {
        streak++
        d = d.minusDays(1)
    }
    var longest = 0
    var run = 0
    var prev: LocalDate? = null
    for (k in set.sorted()) {
        val date = runCatching { LocalDate.parse(k) }.getOrNull() ?: continue
        run = if (prev != null && prev.plusDays(1) == date) run + 1 else 1
        longest = maxOf(longest, run)
        prev = date
    }
    val grid = (0 until 28).map { i -> set.contains(dayKey(today.minusDays((27 - i).toLong()))) }
    return Stats(type, streak, 0, 0, 0, maxOf(longest, streak), set.size, 0, doneToday, grid, milestone(streak), start)
}

fun plural(n: Int, word: String): String = "$n $word${if (n == 1) "" else "s"}"
