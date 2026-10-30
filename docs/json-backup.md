# JSON导入与导出

App顶部“…”→“导出 JSON”选择保存位置，导出全部表、任务与排序。“导入 JSON”选择文件后，先检查格式并显示表／任务数量，点红字“确认”整体替换当前业务数据；“取消”保留当前数据。导入采用用户已确认的整体恢复方式。

## 文件包含什么

文件为UTF-8 JSON，顶层包含`format: "task-widget"`、`formatVersion: 1`、`exportedAt`（UTC时间）和`data`。`data`沿用私有快照v2的业务字段：

| 字段 | 含义 |
|---|---|
| version / sequence | 数据版本2、后续创建所需序号 |
| tables | 按显示顺序保存的表，含稳定id与name |
| tasks | 标题、描述、表归属、年月日、时分、完成状态、创建／完成顺序、人工排序值 |
| selections | App最近选择的表／子表／标签页；App入口标识为-1 |

任务的date、time以字符串保存，空字符串代表没有该精度；`00:00`仍作为明确时分保留。无日期任务按manual值升序，已完成按completed倒序；导出恢复保留这些值。文件的tasks数组顺序不代替各视图排序规则。

设备桌面组件实例不导出。导入后保留本机实例，选择的表不存在时回退到第一张表，并修正标签页范围。App选择可从备份恢复。

## 校验与提交

备份标识、备份版本、数据版本、表／任务标识、表归属、必填标题、日期精度和序号在显示确认前完成校验。未知版本或无效文件提示导入失败，原数据保留；确认后的写入沿用AtomicFile完整快照事务。

取消系统文件选择、取消覆盖确认均不导入。备份包含全部任务内容，可由用户保存到系统文件选择器提供的位置。

代码：[BackupCodec](../app/src/main/java/io/github/jieaccount/taskwidget/data/BackupCodec.kt)、[仓储恢复](../app/src/main/java/io/github/jieaccount/taskwidget/data/TaskRepository.kt)、[App文件入口](../app/src/main/java/io/github/jieaccount/taskwidget/ui/MainActivity.kt)。验证结果见[T-008](verification/t008-p60.md)。
