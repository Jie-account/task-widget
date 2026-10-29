# task-widget

供本人使用的安卓任务记录软件，以桌面小组件为主要入口，默认采用已认可的 4×4 尺寸，支持拖动边框调整大小；先实现可日常使用的简易原型，再逐步迭代。项目名称暂用工作文件夹名。

**首版完成标志：** 在用户的华为 P60 上完成[首版需求第 9 节](docs/requirements.md)的验收，交付可日常使用的安装包与简明使用说明。

当前候选版本 **0.2.2-local** 已构建，统一黑色页面、菜单返回与原入口确认弹窗；P60 暂留 `0.2.1-local` 完成省电隔夜观察，明早回读后再安装新版检查。长内容、新安装及重启结果见 [T-006 验收](docs/verification/t006-p60.md)。日常操作见[简明使用说明](docs/user-guide.md)，新版本机留存包位于 `dist/task-widget-0.2.2-local/`。

## 从这里接续


1. 阅读根目录 [AGENTS.md](AGENTS.md)，定位本项目规则与阅读路径。
2. 查看[当前状态与唯一任务清单](docs/progress.md)，确定本次任务。
3. 阅读近期[会话行动记录](docs/session-log.md)，登记身份后展开相关正文。

可复制的新会话接续说明见[当前状态文档第 5 节](docs/progress.md)。

## 版本管理

GitHub 私有仓库：[Jie-account/task-widget](https://github.com/Jie-account/task-widget)，主分支为 `main`。

```bash
git clone git@github.com:Jie-account/task-widget.git
```

## 按问题查找

| 要了解什么 | 正文入口 |
|---|---|
| 用户已确认哪些行为、怎样验收 | [首版需求](docs/requirements.md) |
| 桌面组件与 App 怎样布局、怎样操作 | [线框设计与交互说明](docs/wireframes.md)；[交互线框](docs/design/wireframes.html) |
| 原生安卓、桌面组件、跨品牌兼容与设备条件 | [技术方向](docs/technical-direction.md) |
| 怎样构建、定位代码与连机使用 | [工程构建与使用](docs/development.md)；[T-005 实机验证](docs/verification/t005-p60.md) |
| 怎样安装和日常操作、当前验收结果 | [简明使用说明](docs/user-guide.md)；[T-006 验收](docs/verification/t006-p60.md) |
| 当前做到哪里、下一项任务与未定条件 | [当前状态与唯一任务清单](docs/progress.md) |
| 谁做了什么、结果在哪里 | [会话行动记录](docs/session-log.md) |
| 暂缓与候选功能 | [后续扩展方向](docs/future-directions.md) |

