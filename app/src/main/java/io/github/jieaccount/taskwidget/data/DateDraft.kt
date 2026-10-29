package io.github.jieaccount.taskwidget.data

import java.time.LocalDate
import java.time.LocalTime

data class DateChoice(val date: LocalDate, val time: LocalTime?)

/** 日期候选独立于任务草稿；确认时校验，不自动调整无效月日或推断年份。 */
data class DateDraft(val year: String, val month: String, val day: String,
                     val hasTime: Boolean = false, val hour: String = "", val minute: String = "") {
    fun parse(): DateChoice {
        require(year.matches(Regex("[0-9]{1,4}")) && year.toInt() in 1..9999) { "请填写 1–9999 之间的年份。" }
        require(month.matches(Regex("[0-9]{1,2}")) && day.matches(Regex("[0-9]{1,2}"))) { "请填写有效的月和日。" }
        val date = runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
        require(date != null) { "年月日组合无效，请检查闰年、月份和天数。" }
        val time = if (!hasTime) null else {
            require(hour.matches(Regex("[0-9]{1,2}")) && minute.matches(Regex("[0-9]{1,2}"))) { "请完整填写时和分。" }
            val parsed = runCatching { LocalTime.of(hour.toInt(), minute.toInt()) }.getOrNull()
            require(parsed != null) { "请填写有效的 24 小时时分（00:00–23:59）。" }
            parsed
        }
        return DateChoice(date, time)
    }

    fun step(field: String, delta: Int): DateDraft {
        require(delta == -1 || delta == 1)
        fun shifted(value: String, range: IntRange): String {
            val current = value.toIntOrNull() ?: return value
            if (current !in range || current + delta !in range) return value
            return (current + delta).toString()
        }
        return when (field) {
            "year" -> copy(year = shifted(year, 1..9999))
            "month" -> copy(month = shifted(month, 1..12))
            "day" -> copy(day = shifted(day, 1..31))
            else -> error("未知日期字段")
        }
    }

    companion object {
        fun from(date: LocalDate, time: LocalTime?) = DateDraft(date.year.toString(), date.monthValue.toString(), date.dayOfMonth.toString(),
            time != null, time?.hour?.toString()?.padStart(2, '0') ?: "", time?.minute?.toString()?.padStart(2, '0') ?: "")
    }
}
