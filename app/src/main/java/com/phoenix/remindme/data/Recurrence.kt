package com.phoenix.remindme.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.ZonedDateTime

object Recurrence {
    val presets = linkedMapOf(
        "Daily" to "FREQ=DAILY",
        "Weekdays" to "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
        "Weekends" to "FREQ=WEEKLY;BYDAY=SA,SU",
        "Weekly" to "FREQ=WEEKLY",
        "Monthly" to "FREQ=MONTHLY",
        "Yearly" to "FREQ=YEARLY",
    )

    fun label(rule: String?): String {
        if (rule.isNullOrBlank()) return "Does not repeat"
        presets.entries.firstOrNull { it.value == rule }?.let { return it.key }
        val values = values(rule)
        val dayNames = values["BYDAY"]?.split(',')?.mapNotNull(::shortDayName).orEmpty()
        val monthDay = values["BYMONTHDAY"]?.toIntOrNull()
        val position = values["BYSETPOS"]?.toIntOrNull()
        return when {
            values["FREQ"] == "WEEKLY" && values["INTERVAL"] == "2" && dayNames.isEmpty() ->
                "Every other week"
            values["FREQ"] == "WEEKLY" && dayNames.size == 1 -> "Every ${dayNames.single()}"
            values["FREQ"] == "WEEKLY" && dayNames.isNotEmpty() ->
                "Every ${dayNames.dropLast(1).joinToString(", ")} and ${dayNames.last()}"
            values["FREQ"] == "MONTHLY" && monthDay != null ->
                "Monthly on the ${ordinal(monthDay)}"
            values["FREQ"] == "MONTHLY" && dayNames.size == 1 && position != null ->
                "${positionName(position)} ${dayNames.single()} of every month"
            else -> "Custom repeat"
        }
    }

    fun next(rule: String?, fromMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val ruleValues = rule?.let(::values) ?: return null
        val freq = ruleValues["FREQ"] ?: return null
        val interval = ruleValues["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1) ?: 1L
        var time = Instant.ofEpochMilli(fromMillis).atZone(zone)
        val next = when (freq) {
            "DAILY" -> time.plusDays(interval)
            "WEEKLY" -> {
                val days = ruleValues["BYDAY"].orEmpty()
                    .split(',').mapNotNull { runCatching { DayOfWeek.valueOf(dayName(it)) }.getOrNull() }
                if (days.isEmpty()) time.plusWeeks(interval) else {
                    do { time = time.plusDays(1) } while (time.dayOfWeek !in days)
                    time
                }
            }
            "MONTHLY" -> {
                val targetMonth = YearMonth.from(time).plusMonths(interval)
                val monthDay = ruleValues["BYMONTHDAY"]?.toIntOrNull()
                val byDay = ruleValues["BYDAY"]?.let { short ->
                    runCatching { DayOfWeek.valueOf(dayName(short.substringBefore(','))) }.getOrNull()
                }
                val position = ruleValues["BYSETPOS"]?.toIntOrNull()
                val date = when {
                    monthDay != null -> targetMonth.atDay(monthDay.coerceIn(1, targetMonth.lengthOfMonth()))
                    byDay != null && position != null -> nthWeekday(targetMonth, byDay, position)
                    else -> targetMonth.atDay(time.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth()))
                }
                date.atTime(time.toLocalTime()).atZone(zone)
            }
            "YEARLY" -> time.plusYears(interval)
            else -> return null
        }
        return next.toInstant().toEpochMilli()
    }

    private fun values(rule: String): Map<String, String> =
        rule.split(';').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0].uppercase() to parts[1].uppercase() else null
        }.toMap()

    private fun nthWeekday(month: YearMonth, day: DayOfWeek, position: Int): java.time.LocalDate {
        if (position == -1) {
            var candidate = month.atEndOfMonth()
            while (candidate.dayOfWeek != day) candidate = candidate.minusDays(1)
            return candidate
        }
        var candidate = month.atDay(1)
        while (candidate.dayOfWeek != day) candidate = candidate.plusDays(1)
        return candidate.plusWeeks((position.coerceIn(1, 4) - 1).toLong())
    }

    private fun shortDayName(value: String): String? = mapOf(
        "MO" to "Monday", "TU" to "Tuesday", "WE" to "Wednesday", "TH" to "Thursday",
        "FR" to "Friday", "SA" to "Saturday", "SU" to "Sunday",
    )[value]

    private fun ordinal(day: Int): String {
        val suffix = if (day % 100 in 11..13) "th" else when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }

    private fun positionName(position: Int): String = when (position) {
        1 -> "First"
        2 -> "Second"
        3 -> "Third"
        4 -> "Fourth"
        -1 -> "Last"
        else -> "Custom"
    }

    private fun dayName(value: String) = mapOf(
        "MO" to "MONDAY", "TU" to "TUESDAY", "WE" to "WEDNESDAY", "TH" to "THURSDAY",
        "FR" to "FRIDAY", "SA" to "SATURDAY", "SU" to "SUNDAY",
    )[value] ?: value
}
