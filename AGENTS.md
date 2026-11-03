# task-widget 项目协作入口

为个人使用开发以可调整大小的桌面组件为核心的安卓任务记录软件，先完成华为 P60 上可日常使用的原型。目标、完成标志和正文导航见 [README](README.md)，个人协作偏好沿用用户级规则。

## 必须遵守的项目约束

- 以[首版需求](docs/requirements.md)作为产品规则依据；技术正文中的建议和待验证项保留各自状态。
- 首版功能边界按需求第 1 节执行，后续方向集中在[扩展清单](docs/future-directions.md)。
- R、E、T 分别标识需求、扩展方向和跨会话任务；接手与重组沿用已有编号。
- 当前事实、执行任务及待取得的信息集中维护在 [docs/progress.md](docs/progress.md)；实质行动与依据记入 [docs/session-log.md](docs/session-log.md)。

## 按完整任务读取

按本次实际行动组合表内路径，先完整读取适用的项目约束，再展开相关正文；维护结果、证据和接续入口属于任务收尾的一部分。

| 行动 | 阅读路径 |
|---|---|
| 接手或继续 | 本文件 → [状态与任务](docs/progress.md) → [近期行动](docs/session-log.md) → 对应正文 |
| 布局和交互 | [需求](docs/requirements.md)第 1–8 节 → [组件约束](docs/technical-direction.md)第 3 节 |
| 工程、组件或数据实现 | [需求](docs/requirements.md)的范围、相关规则与第 9 节验收 → [技术方向](docs/technical-direction.md) → 实际代码与配置（创建后） |
| 修改需求或整理交接 | 对应负责正文及结果依据 → [状态与任务](docs/progress.md) → [行动记录](docs/session-log.md)及受影响入口 |
| 调整公开范围或发布材料 | [公开维护说明](docs/publication.md) → 当前文件、Git历史及Release附件 → [状态与任务](docs/progress.md) |

## 文档职责与已采用的协作机制

| 职责 | 本项目入口 |
|---|---|
| 产品规则与验收 | [需求](docs/requirements.md) |
| 实现方向与验证条件 | [技术方向](docs/technical-direction.md)；具体版本与参数由实际配置承载 |
| 当前状态、唯一任务清单、待取得的信息 | [进度文档](docs/progress.md) |
| 固定会话身份、行动及结果追溯 | [会话行动记录](docs/session-log.md) |
| 后续候选方向 | [扩展清单](docs/future-directions.md) |

新会话先登记不含私人标识的短代号，再沿用任务编号。实际并行时明确文件归属，同一时刻一个文件由一个执行者写入。个人环境、私有备份及设备原始证据保存在忽略目录，公开文档保留技术结论与必要条件。

