# 08 · 可视化方案

## 1. 设计目标

在现有"依赖图 + 性能曲线"基础上，建成**面向隔离 bug 全流程分析的可视化层**：

1. **KV 依赖图**（现有能力的增强）：反例环、已知边 vs 推导边、展开推导链；
2. **SQL/表级视图**（新增）：把行级访问历史渲染为"表状态随交错演化"的直观视图——谓词/phantom 类 bug 的刚需；
3. **时间线视图**（新增）：操作时间区间、事务生命周期、阶段（采集/验证）色带；
4. **交互式调试**（新增）：IsoBugView 式 hook + 渐进披露 + 用户反馈回路；
5. **多格式导出**：PNG / SVG / TikZ / DOT / JSON。

## 2. 视图体系

### 2.1 事务依赖图（TDG）— 现有增强

- **保留**：G6 渲染、节点（事务 + ops）、边类型着色（WR/SO/WW/RW…）、`in_cycle` 高亮、Expand 展开推导链（现有 IsoVista/PolySI 解释算法的产品化）；
- **增强**：
  - 边区分**已知边 / 推导边 / 被剪枝假设**（现有用颜色区分，补图例与悬停解释）；
  - 反例环一键聚焦（`crop-to-cycle`，05）+ 上下文还原；
  - SQL 语义边（谓词读依赖、phanton 边）渲染（依赖 06 的谓词 checker 反例格式）；
  - 布局算法可切换（dagre/force + 手工拖动），布局可保存/分享。

### 2.2 SQL/表级视图（新增）

针对 SQL bug（谓词异常、更新丢失、写偏斜）：

- **表状态电影**：按操作序逐步播放每张表的行状态（哪些行被读/写/删/插），当前步高亮冲突行；
- **谓词命中视图**：对谓词读操作，展示"谓词 + 返回行集 + 被检查行集"，phantom read 时两次相同谓词的行集差异高亮（R13 Fig.1 场景的内置演示）；
- **事务 SQL 面板**：事务的参数化 SQL 原文 + 执行结果（行数/值），与 TDG 节点联动；
- **异常标注层**：把 Adya 异常模式（lost-update/write-skew/phantom/fractured-read…）作为可切换的标注层，命中的模式高亮。

### 2.3 时间线视图（新增）

- **操作区间图**：每会话一条泳道，操作按 [startTs, endTs] 绘制，重叠即潜在并发；冲突对连线；
- **事务生命周期**：begin→commit/abort 的状态条，重试/回滚可见（连接 04 的重试记录）；
- **运行阶段带**：现有 `RuntimeStageRecorder` 产出的阶段时间线（采集 → 各 checker 验证 → 归约 → 分析）作为全局进度上下文；
- **在线模式**：在线检查（AION 式，P3）时时间线实时滚动。

### 2.4 交互式调试（新增，IsoBugView 路线 R19）

- **渐进披露**：先给高层概览（环 + 参与事务 + 异常类型），按需下钻单条边/单个操作；
- **Hook 机制**：后端分析管线的预处理（依赖图过滤）/后处理（环的选择）开放为用户可应用的 hook 函数；
  - 内置 hook 库：按异常类型过滤、按会话分组、只看冲突对、隐藏推导边……；
  - 自定义 hook：平台提供脚本接口（Python/JS），热加载；
- **反馈回路**：用户对环/边的确认/否决写回，驱动分析器重新解释（IsoBugView 的核心经验：bug 分析需要用户领域知识）。

### 2.5 反例解释增强（现有 AnomalyInterpreter 的产品化）

- 每个反例附带**自然语言解释卡**："Txn(id429) 读到了 Txn(id400) 写的 k378，而该写依赖 Txn(id234) 的写与 Txn(id429) 的读，构成 RW 环……"（模板化生成，中英文）；
- 推导链的**分步展开动画**（现有 Expand 的渐进式版本）。

## 3. 数据契约（可视化服务输入）

```json
{
  "graph": { "nodes": [...], "edges": [...] },      // 现有 /view 格式扩展（谓词边、剪枝标记）
  "tables": [ { "name": "t", "rows": [...], "timeline": [...] } ],  // SQL 视图
  "intervals": [ { "session": 0, "ops": [ {"id":1,"start":..,"end":..,"kind":"read","key":..} ] } ],
  "stages": [ { "stage": "PolySI Verification", "begin": .., "end": .. } ],
  "explain": { "cycle": [...], "steps": [...] }
}
```

由 Visualization 服务（后端 FastAPI 扩展）从 IR + checker 输出 + trace 组装；前端按视图类型消费。

## 4. 导出与分享

| 格式 | 用途 |
|---|---|
| PNG/SVG | 论文/报告插图 |
| TikZ | LaTeX 论文矢量图 |
| DOT | Graphviz 再加工/其他工具 |
| JSON | 完整场景（图 + 表 + 区间 + 解释），可再次导入平台 |
| HTML（自包含） | 交互式场景分享（静态资源内联） |

## 5. 与现状衔接

| 现有组件 | 处置 |
|---|---|
| `frontend/src/components/BugView.vue`（G6） | 保留为核心；扩展 SQL 视图/时间线/hook 面板 |
| `backend/main.py /view/{id}`（pydot 解析） | 扩展为 Visualization 服务（组装 graph/tables/intervals/explain） |
| `util/AnomalyInterpreter` | 增加 SQL/谓词反例解释与自然语言模板 |
| `RunView.vue`（ECharts） | 保留；阶段带与时间线并入（09/10 的图表规范复用） |

## 6. 验收要点

1. R13 Fig.1 的 phantom read 历史在 SQL 视图下"两次谓词读行集差异"一眼可辨（用户测试）；
2. IsoBugView 式 hook 可在不改代码的情况下过滤出目标环；
3. 全部视图支持导出且导出的 DOT/JSON 可被平台重新导入还原；
4. 大历史（≥10 万操作）下图渲染 <3s（虚拟化/分层渲染）。
