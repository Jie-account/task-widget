package io.github.jieaccount.taskwidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RelativeDateRefreshTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 11, 26, 9, 0, 0, 0, zone)
    private fun task(date: LocalDate?, time: LocalTime? = null, done: Boolean = false) =
        TaskItem(1, 1, "示例", date = date, time = time, done = done, createdOrder = 1)

    @Test fun futureRelativeLabelsRefreshBeforeTheTaskEventuallyExpires() {
        val items = listOf(task(now.toLocalDate().plusDays(10)))
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant(), RefreshRules.nextBoundary(items, now, true))
        assertEquals(now.toLocalDate().plusDays(11).atStartOfDay(zone).toInstant(), RefreshRules.nextBoundary(items, now))
    }

    @Test fun pastDatesStillRefreshDailyOnlyInRelativeMode() {
        val items = listOf(task(now.toLocalDate().minusDays(2)))
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant(), RefreshRules.nextBoundary(items, now, true))
        assertNull(RefreshRules.nextBoundary(items, now, false))
    }

    @Test fun upcomingTimedOverdueBoundaryWinsAndThenFallsBackToMidnight() {
        val items = listOf(task(now.toLocalDate(), LocalTime.of(9, 30)))
        assertEquals(now.withMinute(30).toInstant().plusMillis(1), RefreshRules.nextBoundary(items, now, true))
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant(), RefreshRules.nextBoundary(items, now.withHour(10), true))
    }

    @Test fun undatedOrCompletedOnlyListsNeedNoRelativeDateAlarm() {
        assertNull(RefreshRules.nextBoundary(listOf(task(null), task(now.toLocalDate(), done = true)), now, true))
        assertNull(RefreshRules.nextBoundary(emptyList(), now, true))
    }

    @Test fun dailyRefreshRespectsDaylightSavingCalendarBoundary() {
        val localZone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 3, 8).atStartOfDay(localZone)
        val next = RefreshRules.nextBoundary(listOf(task(start.toLocalDate().minusDays(1))), start, true)!!
        assertEquals(Duration.ofHours(23), Duration.between(start.toInstant(), next))
    }

    @Test fun midnightContinuesWithNextDayAndChangesRelativeLabel() {
        val date = now.toLocalDate().plusDays(3); val items = listOf(task(date))
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        assertEquals("3天后", DateDisplay.label(date, now.toLocalDate(), DateDisplayMode.RELATIVE))
        assertEquals("2天后", DateDisplay.label(date, midnight.toLocalDate(), DateDisplayMode.RELATIVE))
        assertEquals(midnight.plusDays(1).toInstant(), RefreshRules.nextBoundary(items, midnight, true))
    }
}
