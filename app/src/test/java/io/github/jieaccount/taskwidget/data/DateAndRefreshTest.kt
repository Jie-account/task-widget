package io.github.jieaccount.taskwidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DateAndRefreshTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun task(date: LocalDate?, time: LocalTime? = null, done: Boolean = false) =
        TaskItem(1, 1, "测试", date = date, time = time, done = done, createdOrder = 1)

    @Test fun validPastDateKeepsExplicitYear() {
        assertEquals(LocalDate.of(2020, 1, 1), DateDraft("2020", "1", "1").parse().date)
    }
    @Test fun leapDayRequiresLeapYearAndDoesNotRollIntoMarch() {
        assertEquals(LocalDate.of(2024, 2, 29), DateDraft("2024", "2", "29").parse().date)
        assertThrows(IllegalArgumentException::class.java) { DateDraft("2025", "2", "29").parse() }
        assertThrows(IllegalArgumentException::class.java) { DateDraft("2024", "2", "31").parse() }
    }
    @Test fun yearZeroAndOutOfRangeAndNonNumericInputsAreRejected() {
        for (year in listOf("0", "10000", "-1", "abc", "")) {
            assertThrows(IllegalArgumentException::class.java) { DateDraft(year, "1", "1").parse() }
        }
    }
    @Test fun dateOnlyAndExplicitMidnightKeepDifferentPrecision() {
        assertNull(DateDraft("2026", "10", "29").parse().time)
        assertEquals(LocalTime.MIDNIGHT, DateDraft("2026", "10", "29", true, "00", "00").parse().time)
    }
    @Test fun enabledTimeRequiresBothPartsAnd24HourRange() {
        for ((hour, minute) in listOf("9" to "", "" to "30", "24" to "00", "23" to "60")) {
            assertThrows(IllegalArgumentException::class.java) { DateDraft("2026", "10", "29", true, hour, minute).parse() }
        }
    }
    @Test fun steppingStopsAtFieldBoundaryAndRetainsInvalidCombinationForCorrection() {
        val december = DateDraft("2026", "12", "31")
        assertEquals(december, december.step("month", 1))
        assertEquals("2026", december.step("month", -1).year)
        assertEquals("31", december.step("month", -1).day)
        assertThrows(IllegalArgumentException::class.java) { december.step("month", -1).parse() }
        assertEquals("1", DateDraft("1", "1", "1").step("year", -1).year)
    }
    @Test fun nextBoundaryChoosesTimedTaskStrictlyAfterDueBeforeDayOnly() {
        val day = LocalDate.of(2026, 10, 29); val now = day.atTime(9, 0).atZone(zone)
        val list = listOf(task(day), task(day, LocalTime.of(9, 30)))
        assertEquals(day.atTime(9, 30).atZone(zone).toInstant().plusMillis(1), RefreshRules.nextBoundary(list, now))
    }
    @Test fun exactDueMinuteStillSchedulesStrictBoundaryAndExpiredTasksAreSkipped() {
        val now = ZonedDateTime.of(2026, 10, 29, 9, 30, 0, 0, zone)
        val task = task(now.toLocalDate(), now.toLocalTime())
        assertEquals(now.toInstant().plusMillis(1), RefreshRules.nextBoundary(listOf(task), now))
        assertNull(RefreshRules.nextBoundary(listOf(task), now.plusSeconds(1)))
    }
    @Test fun dateOnlySchedulesNewYearMidnight() {
        val now = ZonedDateTime.of(2026, 12, 31, 12, 0, 0, 0, zone)
        assertEquals(LocalDate.of(2027, 1, 1).atStartOfDay(zone).toInstant(), RefreshRules.nextBoundary(listOf(task(now.toLocalDate())), now))
    }
    @Test fun completedUndatedAndExpiredTasksDoNotCreateFutureAlarm() {
        val now = ZonedDateTime.of(2026, 10, 29, 12, 0, 0, 0, zone)
        assertNull(RefreshRules.nextBoundary(listOf(task(null), task(now.toLocalDate(), done = true), task(now.toLocalDate().minusDays(1))), now))
    }
    @Test fun nextMidnightUsesZoneRulesRatherThanAdding24Hours() {
        val zone = ZoneId.of("America/New_York"); val day = LocalDate.of(2026, 3, 8)
        val now = day.atStartOfDay(zone)
        val next = RefreshRules.nextBoundary(listOf(task(day)), now)!!
        assertEquals(Duration.ofHours(23), Duration.between(now.toInstant(), next))
    }
}
