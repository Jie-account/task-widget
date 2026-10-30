package io.github.jieaccount.taskwidget.data

import org.json.JSONObject
import java.time.Instant

/** 可移植的JSON备份：保存全部业务和App选择，组件实例由导入设备自身管理。 */
object BackupCodec {
    fun encode(state: TaskSnapshot): String = JSONObject()
        .put("format", "task-widget")
        .put("formatVersion", 1)
        .put("exportedAt", Instant.now().toString())
        .put("data", JSONObject(SnapshotCodec.encode(state.copy(
            selections = state.selections.filterKeys { it == TaskRepository.APP_ENTRANCE }))))
        .toString(2)

    /** 完整解析和校验后才返回候选数据；解析过程不写入仓储。 */
    fun decode(raw: String): TaskSnapshot {
        val root = JSONObject(raw.removePrefix("\uFEFF"))
        require(root.getString("format") == "task-widget") { "请选择任务组件导出的JSON文件。" }
        require(root.getInt("formatVersion") == 1) { "备份版本不兼容。" }
        return SnapshotCodec.decode(root.getJSONObject("data").toString())
    }
}
