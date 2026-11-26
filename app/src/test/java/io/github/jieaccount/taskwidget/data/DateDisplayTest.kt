package io.github.jieaccount.taskwidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DateDisplayTest {
    private val today = LocalDate.of(2026, 11, 26)

    @Test fun calendarModeKeepsFullDateRegardlessOfToday() {
        assertEquals("2026-11-29", DateDisplay.label(today.plusDays(3), today, DateDisplayMode.CALENDAR))
        assertEquals("0001-01-01", DateDisplay.label(LocalDate.of(1, 1, 1), today, DateDisplayMode.CALENDAR))
    }

    @Test fun relativeModeDistinguishesFutureTodayAndPast() {
        assertEquals("3天后", DateDisplay.label(today.plusDays(3), today, DateDisplayMode.RELATIVE))
        assertEquals("今天", DateDisplay.label(today, today, DateDisplayMode.RELATIVE))
        assertEquals("2天前", DateDisplay.label(today.minusDays(2), today, DateDisplayMode.RELATIVE))
    }

    @Test fun relativeDatesCrossMonthsYearsAndLeapDays() {
        assertEquals("2天后", DateDisplay.label(LocalDate.of(2027, 1, 2), LocalDate.of(2026, 12, 31), DateDisplayMode.RELATIVE))
        assertEquals("2天后", DateDisplay.label(LocalDate.of(2028, 3, 1), LocalDate.of(2028, 2, 28), DateDisplayMode.RELATIVE))
        assertEquals("1天后", DateDisplay.label(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 28), DateDisplayMode.RELATIVE))
    }

    @Test fun nextCalendarDateIsOneDayAwayEvenWhenOnlyMinutesRemain() {
        val now = today.atTime(23, 59)
        val due = today.plusDays(1).atTime(0, 1)
        assertEquals("1天后", DateDisplay.label(due.toLocalDate(), now.toLocalDate(), DateDisplayMode.RELATIVE))
    }

    @Test fun supportedYearExtremesRemainReadableInBothDirections() {
        val first = LocalDate.of(1, 1, 1); val last = LocalDate.of(9999, 12, 31)
        assertEquals("3652058天后", DateDisplay.label(last, first, DateDisplayMode.RELATIVE))
        assertEquals("3652058天前", DateDisplay.label(first, last, DateDisplayMode.RELATIVE))
    }

    @Test fun preferenceDefaultsAndSavedModesSupportTwoWayToggle() {
        assertEquals(DateDisplayMode.CALENDAR, DateDisplayMode.fromPreference(null))
        assertEquals(DateDisplayMode.CALENDAR, DateDisplayMode.fromPreference("unknown"))
        val relative = DateDisplayMode.CALENDAR.toggled()
        assertEquals(DateDisplayMode.RELATIVE, DateDisplayMode.fromPreference(relative.name))
        assertEquals(DateDisplayMode.CALENDAR, DateDisplayMode.fromPreference(relative.name).toggled())
    }

    @Test fun todayLabelKeepsOriginalTimeBasedOverdueMeaning() {
        val now = LocalDateTime.of(today, LocalTime.of(10, 0))
        val task = TaskItem(1, 1, "示例", date = today, time = LocalTime.of(9, 30), createdOrder = 1)
        assertEquals("今天", DateDisplay.label(task.date!!, now.toLocalDate(), DateDisplayMode.RELATIVE))
        assertTrue(TaskRules.overdue(task, now))
        assertFalse(TaskRules.overdue(task.copy(time = LocalTime.of(15, 0)), now))
        assertFalse(TaskRules.overdue(task.copy(time = null), now))
    }
}
