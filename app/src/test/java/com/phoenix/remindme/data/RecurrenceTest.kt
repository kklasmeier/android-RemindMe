package com.phoenix.remindme.data

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun dailyPreservesLocalTimeAcrossDaylightSaving() {
        val start = ZonedDateTime.of(2026, 3, 7, 9, 30, 0, 0, zone)
        val next = Recurrence.next("FREQ=DAILY", start.toInstant().toEpochMilli(), zone)
        assertEquals(9, instant(next).hour)
        assertEquals(30, instant(next).minute)
    }

    @Test
    fun weekdaysSkipsWeekend() {
        val friday = ZonedDateTime.of(2026, 9, 11, 8, 0, 0, 0, zone)
        val next = Recurrence.next(
            "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
            friday.toInstant().toEpochMilli(),
            zone,
        )
        assertEquals(DayOfWeek.MONDAY, instant(next).dayOfWeek)
    }

    @Test
    fun monthlyAdvancesOneMonth() {
        val start = ZonedDateTime.of(2026, 9, 13, 12, 0, 0, 0, zone)
        val next = instant(Recurrence.next("FREQ=MONTHLY", start.toInstant().toEpochMilli(), zone))
        assertEquals(10, next.monthValue)
        assertEquals(13, next.dayOfMonth)
    }

    @Test
    fun namedWeekdaysAdvanceToNextSelectedDay() {
        val sunday = ZonedDateTime.of(2026, 9, 13, 8, 0, 0, 0, zone)
        val next = instant(Recurrence.next(
            "FREQ=WEEKLY;BYDAY=TU",
            sunday.toInstant().toEpochMilli(),
            zone,
        ))
        assertEquals(DayOfWeek.TUESDAY, next.dayOfWeek)
        assertEquals("Every Tuesday", Recurrence.label("FREQ=WEEKLY;BYDAY=TU"))
    }

    @Test
    fun monthlyByDateHonorsRequestedDate() {
        val start = ZonedDateTime.of(2026, 9, 5, 18, 30, 0, 0, zone)
        val next = instant(Recurrence.next(
            "FREQ=MONTHLY;BYMONTHDAY=5",
            start.toInstant().toEpochMilli(),
            zone,
        ))
        assertEquals(10, next.monthValue)
        assertEquals(5, next.dayOfMonth)
        assertEquals("Monthly on the 5th", Recurrence.label("FREQ=MONTHLY;BYMONTHDAY=5"))
    }

    @Test
    fun firstMondayOfMonthAdvancesCorrectly() {
        val start = ZonedDateTime.of(2026, 9, 7, 9, 0, 0, 0, zone)
        val next = instant(Recurrence.next(
            "FREQ=MONTHLY;BYDAY=MO;BYSETPOS=1",
            start.toInstant().toEpochMilli(),
            zone,
        ))
        assertEquals(10, next.monthValue)
        assertEquals(5, next.dayOfMonth)
    }

    private fun instant(value: Long?) =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(requireNotNull(value)), zone)
}
