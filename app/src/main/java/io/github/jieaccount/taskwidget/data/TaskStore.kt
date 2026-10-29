package io.github.jieaccount.taskwidget.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/** 保持应用标识与旧偏好文件，升级时一次性迁移；正式数据使用私有原子文件。 */
object TaskStore {
    private var instance: TaskRepository? = null
    @Synchronized
    private fun repository(context: Context): TaskRepository = instance ?: TaskRepository(AndroidStorage(context.applicationContext)).also { instance = it }

    fun load(context: Context) = repository(context).load()
    fun saveTask(context: Context, task: TaskItem) = repository(context).saveTask(task)
    fun act(context: Context, id: Long, action: String) = repository(context).act(id, action)
    fun reorder(context: Context, tableId: Long, ids: List<Long>) = repository(context).reorder(tableId, ids)
    fun selection(context: Context, entranceId: Int) = repository(context).selection(entranceId)
    fun select(context: Context, entranceId: Int, selection: WidgetSelection) = repository(context).select(entranceId, selection)
    fun forgetWidget(context: Context, id: Int) = repository(context).forgetWidget(id)
    fun saveTable(context: Context, id: Long, name: String) = repository(context).saveTable(id, name)
    fun moveTable(context: Context, id: Long, delta: Int) = repository(context).moveTable(id, delta)
    fun deleteTable(context: Context, id: Long, count: Int) = repository(context).deleteTable(id, count)
}

private class AndroidStorage(private val context: Context) : SnapshotStorage {
    private val file = AtomicFile(File(context.filesDir, "tasks-v2.json"))
    override fun read(): String? = try { file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() } }
    catch (error: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error else null
    }

    override fun write(value: String) {
        val stream = file.startWrite()
        try { stream.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }

    override fun legacy(): LegacyData? {
        val prefs = context.getSharedPreferences("capability_prototype_v1", Context.MODE_PRIVATE)
        val raw = prefs.getString("snapshot", null) ?: return null
        val selections = prefs.all.keys.mapNotNull { key ->
            Regex("widget\\.(\\d+)\\.table").matchEntire(key)?.groupValues?.get(1)?.toInt()?.let { id ->
                id to WidgetSelection(prefs.getLong(key, 1),
                    TaskView.entries.firstOrNull { it.name == prefs.getString("widget.$id.view", "") } ?: TaskView.DATED,
                    prefs.getInt("widget.$id.page", 0))
            }
        }.toMap()
        return LegacyData(raw, selections)
    }
}
