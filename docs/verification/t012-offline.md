# T-012 日期显示与取消日期：离线结果

- 执行：S-002，2026-11-26。
- 状态：离线开发与针对性检查完成；本轮未连接手机、安装或发布。
- 开发版本：0.3.2-local／code10；已发布版本仍为v0.3.1。
- 规则：[需求v0.15](../requirements.md)，R19／R37／R48–R50／R73／R78；[交互说明v0.8](../wireframes.md)。

后续已完成正式0.3.2／code11连机验证与数据恢复，见[实机结果](t012-p60.md)；本页保留离线阶段的构建与条件。

## 1. 实现结果

点击任意橙色日期，在年月日与相对自然日之间统一切换，所有表的App和组件列表共用本机偏好；详情及日期预览继续显示真实日期及时分。相对模式显示“3天后”“今天”“2天前”，过期颜色仍由原日期／时分判断。

App订阅显示偏好变化；组件集合的日期点击通过既有透明流程入口在创建阶段切换并退出，不绘制确认页。桌面缓存的旧`cancel_date`点击也转为显示切换，避免继续触发原业务操作。

详情“修改日期”右侧新增“取消日期”，只清空草稿的年月日及时分。与其他字段一起保存；未保存返回仍可继续编辑或放弃。业务快照和JSON格式保持兼容，显示偏好独立保存，导入备份时沿用本机模式。

相对模式在最近过期边界之外增加下一个本地午夜候选，取较早者；全为过期任务时仍每天刷新天数。年月日模式沿用原调度，无日期或仅已完成任务不额外安排相对天数闹钟。

## 2. 自动验证

| 范围 | 项数 | 结果 |
|---|---:|---|
| DateDisplayTest | 7 | 年月日、未来／今天／过去、跨月跨年及闰日、自然日口径、年份边界、偏好默认／双向模式、当天时分过期含义通过 |
| RelativeDateRefreshTest | 6 | 未来／过期任务每日更新、最近时分优先、无关集合不调度、夏令时自然日边界、午夜后继续安排通过 |
| DateDraftSaveTest | 4 | 日期清空与其他字段共同保存、无日期置顶、写入失败保留、已完成元数据及无效标题拒绝通过 |
| DateAndRefreshTest原有回归 | 11 | 日期输入、精度及原过期调度通过 |
| TaskRepositoryTest选定回归 | 2 | 编辑保留稳定字段和完成状态、已完成任务清空日期后仍归已完成通过 |

共30项，0失败／0错误；本轮未重复运行其他不相关专项。Android偏好持久化、监听通知和界面手势尚未实测。

`assembleDebug`、`assembleRelease`及`lintRelease`在离线依赖模式下成功；lint为0错误／28警告，包含新增偏好写入的KTX风格建议。报告位于`app/build/test-results/testDebugUnitTest/`及`app/build/reports/lint-results-release.html`。

复现入口（先将`JAVA_HOME`设为本机JDK17目录）：

```powershell
.\tools\build.ps1 -JavaHome $env:JAVA_HOME -Tasks @(
    'testDebugUnitTest',
    '--tests', '*DateDisplayTest',
    '--tests', '*RelativeDateRefreshTest',
    '--tests', '*DateDraftSaveTest',
    '--tests', '*DateAndRefreshTest',
    '--tests', '*TaskRepositoryTest.editCannotChangeStableMetadataOrCompletedStatus',
    '--tests', '*TaskRepositoryTest.completedDateCanBeRemovedWithoutLeavingCompletedView',
    'assembleDebug', 'assembleRelease', 'lintRelease', '--offline'
)
```

## 3. 本机候选包

| 项目 | 值 |
|---|---|
| 留存路径 | `dist/task-widget-0.3.2-local/task-widget-0.3.2-local.apk`，附SHA256SUMS.txt |
| 版本 | 0.3.2-local／code10，release变体，非debuggable |
| 大小 | 6,944,990字节 |
| APK SHA256 | `3aa9c0ed97dc41e2bfe18de469d367cead4b4c80bb3ec1ed56f3f4e8d5c92711` |
| 签名证书SHA256 | `41fa9c0bb358a98cd2a73915000607865532cbe641a57b930a6aee0fba54341d` |

APK签名核对通过，沿用原安装证书。debug构建另留在`app/build/outputs/apk/debug/`。候选尚未安装和验收，不作为新的正式Release。

## 4. 连机接续

用户方便后再连接手机，先导出当时最新JSON并留存组件选择，后续检查及恢复以该轮新基线为准。

1. 核对覆盖升级和数据保留，在App／组件、不同表之间验证双向切换、记忆选择及实时同步。
2. 确认日期点击直接更新显示，兼容宿主旧缓存；表、视图和任务数据保持，详情始终显示完整精度。
3. 验证取消日期与其他草稿共存、保存前保留原记录、返回继续／放弃、保存后的归类及组件更新。
4. 观察新相对天数的实际跨日更新；后台仍采用系统非精确调度。本轮纯时区规则测试不替代E17系统设置专项。
5. 检查导出的日期仍为真实值及JSON导入兼容，清理独立测试记录，恢复当轮原数据和选择，再按用户安排交付。

此前用电和自动过期验收保持完成；E15–E17继续暂缓。
