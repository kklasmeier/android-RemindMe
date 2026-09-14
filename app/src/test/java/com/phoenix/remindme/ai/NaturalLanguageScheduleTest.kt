package com.phoenix.remindme.ai

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import com.phoenix.remindme.data.BaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NaturalLanguageScheduleTest {
    private val zone = ZoneId.of("America/New_York")
    private val sundayEvening = Instant.parse("2026-09-13T23:56:00Z")

    @Test
    fun tomorrowAtSixThirtyAmUsesExplicitLocalTime() {
        val result = NaturalLanguageSchedule.parse(
            "remind me to take a shower tomorrow at 6:30am",
            sundayEvening,
            zone,
        )!!

        assertEquals(
            ZonedDateTime.of(2026, 9, 14, 6, 30, 0, 0, zone).toInstant().toEpochMilli(),
            result.triggerTime,
        )
        assertNull(result.recurrenceRule)
    }

    @Test
    fun everyWeekdayUsesNextWeekdayAndWeekdayRule() {
        val result = NaturalLanguageSchedule.parse(
            "remind me to take my daughter to school every weekday at 7:30am",
            sundayEvening,
            zone,
        )!!

        assertEquals(
            ZonedDateTime.of(2026, 9, 14, 7, 30, 0, 0, zone).toInstant().toEpochMilli(),
            result.triggerTime,
        )
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", result.recurrenceRule)
    }

    @Test
    fun invalidTimeIsNotOverridden() {
        assertNull(NaturalLanguageSchedule.parse("tomorrow at 19:99pm", sundayEvening, zone))
    }

    @Test
    fun twelveAmIsMidnight() {
        val result = NaturalLanguageSchedule.parse("tomorrow at 12am", sundayEvening, zone)!!
        val parsed = Instant.ofEpochMilli(result.triggerTime).atZone(zone)
        assertEquals(0, parsed.hour)
        assertFalse(parsed.toInstant().isBefore(sundayEvening))
    }

    @Test
    fun namedWeekdayUsesThatDay() {
        val result = NaturalLanguageSchedule.parse("take bins out every Tuesday at 7am", sundayEvening, zone)!!
        val parsed = Instant.ofEpochMilli(result.triggerTime).atZone(zone)
        assertEquals(2026, parsed.year)
        assertEquals(9, parsed.monthValue)
        assertEquals(15, parsed.dayOfMonth)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", result.recurrenceRule)
    }

    @Test
    fun multipleNamedWeekdaysBecomeOneRule() {
        val result = NaturalLanguageSchedule.parse(
            "exercise every Saturday and Sunday at 8am",
            sundayEvening,
            zone,
        )!!
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", result.recurrenceRule)
    }

    @Test
    fun monthlyOrdinalDateIsDeterministic() {
        val result = NaturalLanguageSchedule.parse(
            "pay rent on the 5th of every month at 18:30",
            sundayEvening,
            zone,
        )!!
        val parsed = Instant.ofEpochMilli(result.triggerTime).atZone(zone)
        assertEquals(10, parsed.monthValue)
        assertEquals(5, parsed.dayOfMonth)
        assertEquals(18, parsed.hour)
        assertEquals(30, parsed.minute)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=5", result.recurrenceRule)
    }

    @Test
    fun noonAndMidnightAreUnderstood() {
        val noon = NaturalLanguageSchedule.parse("tomorrow at noon", sundayEvening, zone)!!
        val midnight = NaturalLanguageSchedule.parse("tomorrow at midnight", sundayEvening, zone)!!
        assertEquals(12, Instant.ofEpochMilli(noon.triggerTime).atZone(zone).hour)
        assertEquals(0, Instant.ofEpochMilli(midnight.triggerTime).atZone(zone).hour)
    }

    @Test
    fun firstMondayOfEveryMonthIsUnderstood() {
        val result = NaturalLanguageSchedule.parse(
            "send the report the first Monday of every month at 9am",
            sundayEvening,
            zone,
        )!!
        val parsed = Instant.ofEpochMilli(result.triggerTime).atZone(zone)
        assertEquals(10, parsed.monthValue)
        assertEquals(5, parsed.dayOfMonth)
        assertEquals("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=1", result.recurrenceRule)
    }

    @Test
    fun everyOtherWeekIsUnderstood() {
        val result = NaturalLanguageSchedule.parse("water plants every other week at 8am", sundayEvening, zone)!!
        assertEquals("FREQ=WEEKLY;INTERVAL=2", result.recurrenceRule)
    }

    @Test
    fun localScheduleOverridesConflictingAiSchedule() {
        val aiDraft = draft(
            triggerTime = ZonedDateTime.of(2026, 9, 14, 19, 56, 0, 0, zone).toInstant().toEpochMilli(),
            isRecurring = false,
            recurrenceRule = null,
        )
        val local = ParsedSchedule(
            triggerTime = ZonedDateTime.of(2026, 9, 14, 6, 30, 0, 0, zone).toInstant().toEpochMilli(),
            recurrenceRule = "FREQ=DAILY",
        )

        val merged = mergeAuthoritativeSchedule(aiDraft, local)

        assertEquals(local.triggerTime, merged.triggerTime)
        assertEquals(true, merged.isRecurring)
        assertEquals("FREQ=DAILY", merged.recurrenceRule)
        assertEquals("Take a shower", merged.title)
    }

    @Test
    fun aiScheduleIsPreservedWhenNoLocalRuleMatched() {
        val aiDraft = draft(triggerTime = 123456L, isRecurring = true, recurrenceRule = "FREQ=WEEKLY")
        assertEquals(aiDraft, mergeAuthoritativeSchedule(aiDraft, null))
    }

    private fun draft(
        triggerTime: Long?,
        isRecurring: Boolean,
        recurrenceRule: String?,
    ) = ReminderDraft(
        title = "Take a shower",
        baseType = BaseType.TIME,
        triggerTime = triggerTime,
        locationName = null,
        locationOffsetMinutes = 0,
        isRecurring = isRecurring,
        recurrenceRule = recurrenceRule,
        alertFullScreen = true,
        alertSound = true,
        alertHeadsUp = true,
        alertLockScreen = true,
    )
}
