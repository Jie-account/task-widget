package io.github.jieaccount.taskwidget.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 列表显示方式独立于任务保存的真实日期和精度。 */
enum class DateDisplayMode {
    CALENDAR, RELATIVE;

    fun toggled(): DateDisplayMode = when (this) {
        CALENDAR -> RELATIVE
        RELATIVE -> CALENDAR
    }

    val toggleDescription: String
        get() = when (this) {
            CALENDAR -> "统一切换为相对天数"
            RELATIVE -> "统一切换为年月日"
        }

    companion object {
        /** 新安装和未知偏好使用年月日，兼容原有显示。 */
        fun fromPreference(value: String?): DateDisplayMode = entries.firstOrNull { it.name == value } ?: CALENDAR
    }
}

object DateDisplay {
    /** 按当地日历日期求差，跨午夜算一天，时分仍交给原过期规则解释。 */
    fun label(date: LocalDate, today: LocalDate, mode: DateDisplayMode): String {
        if (mode == DateDisplayMode.CALENDAR) return date.toString()
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days > 0 -> "${days}天后"
            days < 0 -> "${-days}天前"
            else -> "今天"
        }
    }
}
