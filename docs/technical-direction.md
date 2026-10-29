# 技术方向与验证条件

- 基线日期：2026-10-28
- 用户已认可标准 Android 原生路线与分阶段兼容思路。
- 阅读依据：[首版需求](requirements.md)；执行安排见[唯一任务清单](progress.md)。
- 本文记录采用的方向、候选选择和验证条件；实际版本及参数由后续工程配置承载。

## 1. 已认可的方向

| 方面 | 方向 |
|---|---|
| 语言与平台 | Kotlin 原生安卓，优先使用 AndroidX / Jetpack |
| App 界面 | T-004 原型采用 Jetpack Compose |
| 组件 | T-004 原型采用标准 App Widget + RemoteViews + ListView/RemoteViewsService；P60 关键宿主行为已验证 |
| 数据 | 应用私有存储；T-004 演示数据使用版本化 JSON，T-005 再确定 Room 与正式数据迁移 |
| 职责划分 | 共用任务归类、排序、日期、完成恢复规则；App 界面、组件显示与更新分别组织 |
| 扩展基础 | 稳定标识、日期精度、创建/完成/人工排序信息、数据版本与迁移机制 |
| 跨品牌 | 同一套核心实现，依据实测增加小范围兼容处理 |

本地任务记录以设备自身能力完成。华为、小米或 Google 账号及服务均为未来可按功能引入的可选依赖。

## 2. 当前已知环境

| 项目 | 信息与取得方式 |
|---|---|
| 手机 | P60，ADB 实测型号 LNA-AL00 |
| 系统 | HarmonyOS 4.2.0；构建 LNA-AL00 4.2.0.225(C00E183R2P7)；兼容层 Android 12 / API 31 |
| 桌面 | 华为自带桌面 14.0.1.441（versionCode 140001441） |
| 屏幕 | 实测 1220 × 2700 像素；density 520dpi（3.25），fontScale 1.0 |
| Android 兼容 API 级别 | 已由 ADB 实测 API 31，HarmonyOS 版本与兼容 API 分别记录 |
| 工具链、SDK、JDK、ADB、设备连接 | JDK 17 与项目内 SDK/Gradle 已就绪，P60 已授权 ADB 并安装运行原型；构建入口见[开发文档](development.md) |

## 3. 安卓组件约束

T-003 已形成[线框与交互方案](wireframes.md)及[浏览器验证记录](design/review.md)。D01–D13 为设计默认值，供 T-004 验证可执行布局；本文各技术候选与待实测项保持原状态。

T-004 将固定标签放在集合列表外，使用 RemoteViewsService 提供纵向 ListView，行内独立点击通过 PendingIntent 模板与 fill-in Intent 进入 App。此方案对应可伸缩布局与四类行内目标，沿用 API 26 起的标准路径；P60 关键交互已通过[实机验证](verification/t004-p60.md)。Glance 同样基于 RemoteViews；本轮选择依据是控件和点击的直接控制，未执行 Glance 性能对比。原文见[Android 集合组件](https://developer.android.com/develop/ui/views/appwidgets/collections)。

原型最低 API 暂定 26，以直接使用 java.time；P60 实测 API 31。工具版本、compile/target 与依赖参数以[App 构建配置](../app/build.gradle.kts)、[根插件配置](../build.gradle.kts)和[Wrapper](../gradle/wrapper/gradle-wrapper.properties)为准。实际环境与结果见 [T-004 构建记录](verification/t004-build.md)。

### 3.1 添加与页面承载

组件由手机桌面承载，随 App 安装。用户可从桌面组件列表添加；App 可在桌面支持时发起添加请求，通常由用户确认。

组件控件与交互受平台限制。添加、详情、菜单和确认可由轻量 App 页面承载，需验证保存返回原入口。排序入口携带当前表标识，打开对应排序页。

有日期和已完成菜单内容为空时仍保留按钮；空态表现由线框阶段形成方案。

### 3.2 默认尺寸与边框调整

用户在 2026-10-29 实测后认可默认 4×4 组件，并要求保留拖动边框调整大小。R64 已同步到需求 v0.8，实际位置、伸缩范围和网格吸附由桌面决定。

首版提供同一种可伸缩布局，配置默认 4×4，保留 `resizeMode="horizontal|vertical"`，最小区域暂为 250×300dp。使用逻辑布局单位和可缩放字体，保留足够点击区域；任务列表纵向滚动，顶部固定，表标签通过左右翻页展示。

### 3.3 刷新与时间边界

用户修改数据后主动请求更新组件。日期跨天和超过指定时刻时，需要调度刷新并验证过期着色。

省电策略、后台限制和桌面缓存可能延迟刷新，需在目标真机测量实际表现；目前未承诺后台精确到秒。常驻前台服务或精确闹钟权限由后续实际需求决定。

未设置时分与设置 00:00 分别表达。T-004 原型按设备当前本地时间判断，时区变化后重新计算；正式调度与实际刷新精度仍需验证。

### 3.4 系统与生命周期

采用标准用户点击入口打开 App 页面，验证新旧系统的跳转及返回行为。区分普通进程结束、组件重载与手机重启，确保持久数据能恢复。

首版围绕一个组件设计和验收，底层正确处理系统组件标识，便于后续扩展。桌面重复添加能力按系统机制处理。

## 4. 跨系统兼容边界

1. 支持 Android APK 和标准 App Widget 的设备沿用当前技术路线，优先共用业务和数据实现。
2. 厂商桌面的网格、边距、圆角、组件尺寸上限和更新行为分别验证。
3. 最低 API、target SDK 和依赖版本在工程任务中依据目标设备与接口要求确定。新接口按 API 版本分支处理。
4. 数据存储使用应用自身区域；升级沿用稳定应用标识和签名，并处理数据迁移。
5. 不提供 Android APK 兼容环境的原生鸿蒙设备，需要另行评估鸿蒙应用与服务卡片。
6. 小米等其他品牌的兼容意向已确认，具体型号和系统版本尚未指定；当前验证范围见下表。

## 5. 分阶段验证

| 环境 | 条件与覆盖 |
|---|---|
| 当前华为设备 | P60 / HarmonyOS 4.2.0 / 自带桌面：安装、组件入口、默认 4×4 与边框调整、菜单与确认、排序跳转、更新、进程重建与重启 |
| 标准 Android 模拟器 | 选定的最低与较新 API：系统接口、字体、长文本、组件点击；厂商桌面仍需真机测试 |
| 后续小米设备 | 明确型号与系统版本：最大区域、网格边距、滚动、跳转、省电下刷新、跨天着色 |
| 其他品牌或桌面 | 根据实际使用需求纳入 |

2026-10-29 先完成可构建原型，随后按用户安排连接 P60 安装与实测；本轮未运行模拟器。构建结果与宿主验证分别记录，工程入口见[开发文档](development.md)。每次实测记录设备、API、构建版本、操作步骤、实际结果和相关证据。

## 6. 后续技术核查原文入口

| 需核查的问题 | 官方入口 |
|---|---|
| 标准组件的接口、布局和交互 | [App widgets overview](https://developer.android.com/develop/ui/views/appwidgets/overview) |
| Glance 的组件实现能力 | [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance) |
| Room 本地数据与迁移 | [Room](https://developer.android.com/training/data-storage/room) |
| 当前构建版本的兼容组合 | [AGP 9.0 发布说明](https://developer.android.com/build/releases/agp-9-0-0-release-notes) |
| Windows 下 Gradle 8 系列缓存目录移动失败 | [Gradle 官方问题 #31438](https://github.com/gradle/gradle/issues/31438)，修复里程碑为 9.1 |

T-004 已核对集合组件文档、AGP 兼容表及上述 Gradle 问题；其余候选随相应工程任务展开。
