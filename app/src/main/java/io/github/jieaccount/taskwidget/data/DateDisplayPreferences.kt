package io.github.jieaccount.taskwidget.data

import android.content.Context
import android.content.SharedPreferences

/** 全App和组件共用的本机显示偏好；导入任务备份时沿用当前选择。 */
object DateDisplayPreferences {
    private const val FILE = "display_preferences_v1"
    private const val MODE = "date_display_mode"

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(context: Context): DateDisplayMode = DateDisplayMode.fromPreference(preferences(context).getString(MODE, null))

    @Synchronized
    fun toggle(context: Context): DateDisplayMode {
        val next = read(context).toggled()
        preferences(context).edit().putString(MODE, next.name).apply()
        return next
    }

    /** 立即回传当前值，后续变化通知App；返回的回调用于页面离开时解除订阅。 */
    fun observe(context: Context, onChanged: (DateDisplayMode) -> Unit): () -> Unit {
        val prefs = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MODE) onChanged(read(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onChanged(read(context))
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
