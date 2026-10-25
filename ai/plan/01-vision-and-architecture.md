# 01 · 愿景与总体架构

## 1. 愿景

**IsoVista 要成为隔离级别（Isolation Levels）黑盒测试的统一平台**，覆盖全流程与工具链：

```
┌──────────┐   ┌──────────────┐   ┌────────────┐   ┌──────────────┐
│ Benchmarks│ → │ Workload Gen │ → │  Executor  │ → │    Logger    │
│ (KV/SQL)  │   │ (GT/DBStorm/ │   │ (12+ DBMS) │   │ (统一 trace) │
└──────────┘   │  MT/Adaptive)│   └────────────┘   └──────┬───────┘
               └──────────────┘                          │
                                                         ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌────────────┐
│ Visualization│ ← │  Bug Reducer │ ← │   Checkers   │ ← │  History   │
│ (图/SQL/时间)│   │ (归约+去重)  │   │ (10+ 家族)   │   │ Synthesizer│
└──────────────┘   └──────────────┘   └──────────────┘   └────────────┘
        │                                     │
        ▼                                     ▼
┌──────────────┐   ┌──────────────────────────────────────┐
│ Perf. Analysis│ → │  Experiment Automation + Auto-Report │
│ (分阶段/扩展性)│   │  (参数扫描/多图示/可复现)            │
└──────────────┘   └──────────────────────────────────────┘
```

设计原则：

1. **黑盒优先，白盒可选**：默认不改 DBMS、不依赖内部信息（Cobra/Viper/PolySI 路线）；对愿意暴露少量信息的数据库，提供可选的 version certificate / timestamp 白盒加速通道（Emme/Enne/CHRONOS 路线），两者结果可互相对照。
2. **统一数据契约**：benchmark、generator、executor、logger、synthesizer、checker 之间只通过**统一 trace/历史 IR** 交互（见 05），任何环节可独立替换。
3. **插件化**：benchmark / executor / checker / reducer 均为插件，按 NAME 注册（沿用现有 Reflections 机制），第三方工具以子进程或 JNI 方式接入。
4. **可复现**：一切实验由声明式配置 + 随机种子驱动，产出物自包含（配置、历史、日志、原始数据、图表、报告）。
5. **公平比较**：平台自身不偏向任何 checker；对比实验由统一协议强制执行（同输入、同预算、同环境，见 09）。

## 2. 目标用户与典型场景

| 用户 | 场景 | 平台价值 |
|---|---|---|
| DBMS 研发/测试团队 | 发布前验证隔离实现；bug 归约后提交工单 | Pisco 式归约产出可提交的最小复现；多 checker 交叉验证降低漏报 |
| Checker 研究者 | 验证新算法；与 PolySI/Viper/Elle 等同台竞技 | 统一 API + apple-to-apple 协议 + 自动作图，省去搭采集/执行环境 |
| 数据库用户/审计方 | 审计云数据库是否兑现隔离承诺 | 黑盒执行 + 多 checker 会诊；白盒接口可选加速 |
| 应用开发者 | 验证应用在弱隔离下的健壮性 | MonkeyDB 式合成历史/mock storage 驱动应用测试 |
| 教学 | 隔离级别语义与异常模式演示 | Isolde 式区分历史 + 可视化，直观展示 RC/RR/SI/SER 差异 |

## 3. 核心标准落地映射

| 标准 | 落地机制 | 负责文档 |
|---|---|---|
| C1 快速验证新算法 | Checker 插件 API（verify/report/profile 三方法）+ 参考模板 + 开发容器 | 06 |
| C2 apple-to-apple | 统一对比协议：同历史文件、同 timeout、同机器画像、分阶段分解对齐 | 09 |
| C3 benchmark 全面权威 | Benchmark 目录（manifest 描述数据模型/参数/权威来源）；KV 与 SQL Predicates 双轨 | 02 |
| C4 数据库覆盖 | Executor 插件矩阵 + 能力声明（是否支持谓词/范围扫描/快照读取） | 04 |
| C5 自动化脚本 | 实验配置 schema（YAML）+ CLI + 参数扫描 + 复现清单 | 10 |
| C6 自动分析作图 | 分析管道：CSV/JSON → 图表规范 → 多图示 + Markdown/HTML 报告 | 10 |

## 4. 目标总体架构（逻辑视图）

### 4.1 组件与职责

| 层 | 组件 | 职责 | 现有对应 |
|---|---|---|---|
| 接入层 | Web GUI / CLI / Python SDK | 配置实验、提交、监控、下载 | 现有 Configuration.vue + Main.java |
| 编排层 | Experiment Orchestrator | 解析实验配置；调度 benchmark→gen→exec→check→reduce→analyze 流水线；任务队列与断点续跑 | 现有 main.py run_queue |
| 数据层 | Trace Store / History IR | 统一 trace 与历史的存储、格式转换（text/elle/JSON）、版本管理 | 现有 history/ 包 |
| 执行层 | Executor 插件群 | 在真实 DBMS 上执行负载并采集 | 现有 collector/ 包 |
| 算法层 | Generator / Checker / Reducer 插件群 | 负载生成、隔离验证、bug 归约 | 现有 generator/ checker/ 包 |
| 分析层 | Profiler / Analyzer / Reporter | 分阶段计时、内存/CPU 采样、统计检验、自动作图、报告生成 | 现有 util/Profiler 等 |
| 展示层 | Visualization 服务 | 依赖图、SQL 视图、时间线、性能图表 | 现有 G6/ECharts 页面 |

### 4.2 关键数据流（目标态）

1. 实验配置声明 `benchmark + db + checker 列表 + 参数扫描 + 预算`；
2. Orchestrator 实例化 Benchmark → Generator 产出负载 → Executor 在目标 DBMS 执行，Logger 落统一 trace；
3. Synthesizer 将 trace 归一为历史 IR（含行级/谓词访问信息），按需做历史变换；
4. 各 Checker 并行/串行验证同一历史，输出 verdict + counterexample（DOT/JSON）+ 分阶段 profile；
5. Reducer 对每个 bug 做确定性复现 → 最小化 → 去重，产出 bug 记录（含标签）；
6. Analyzer 汇总全部运行数据，Reporter 自动生成图表与报告；
7. Visualization 提供交互式下钻（图/SQL/时间线联动）。

### 4.3 与现有代码的衔接（不改基调，做扩展）

- **保留**：`Main.java` 的 Reflections 插件注册、`Checker` 接口、`History/Session/Transaction/Operation` 模型、`config.properties` 配置风格、FastAPI 任务队列、G6/ECharts 前端。
- **扩展**：
  - `history/` 增加 `Trace`（带时间区间与访问行集）与 IR 转换层（05）；
  - `checker/` 抽出 `CheckerPlugin` 统一骨架与上下文对象（06）；
  - `collector/` 泛化为 `Executor` 接口（能力声明 + trace 输出）（04）；
  - `util/` 增加 Analyzer/Reporter（10）；
  - 新增 `benchmark/` 顶层包与 benchmark 目录（02）；
  - 新增 `experiment/` 编排层与实验配置 schema（10）。

## 5. 现状差距分析（GAP）

> 现状结论来自对代码库的通读（详见 `ai/meta/project-overview.md`）；目标结论来自 refs/ 文献调研。

| # | 维度 | 现状 | 目标 | 差距 | 优先级 |
|---|---|---|---|---|---|
| G1 | 数据模型 | 仅 read-write register（text）+ list-append（elle） | KV + SQL Predicates（行级/范围/谓词读、insert/delete/update） | 谓词异常（phantom read）无法表达；RR vs SER 无法区分（R13/R14/R15） | P1 |
| G2 | Benchmark | 无 benchmark 抽象，负载参数内嵌在生成器 | 统一 benchmark 套件与目录 | 无权威负载、无可比性基线 | P1 |
| G3 | Executor | 4 库（MySQL/PG/MariaDB/H2） | ≥12 库 | 云 NewSQL/NoSQL 缺失 | P1 |
| G4 | Logger | Collector 顺带回填读值，无独立 trace | 统一 trace（时间区间+行集+SQL 文本） | 无法支撑 SQL 检查与在线检查 | P0 |
| G5 | Checker 生态 | 6 家族（C4/PolySI/SMT_SI/SMT_SER/Viper/ELLE_TCC） | +Cobra/Elle 主线/Leopard/AWDIT/VeriStrong 完整版/Boomslang 插件/Emme 等 | 缺 SER 图检查代表、缺最优弱隔离测试器、缺统一 IR | P2 |
| G6 | Synthesizer | 仅 Loader/Serializer | 区分历史合成 + 允许值枚举 + 历史变换 | 无法做规格比对与应用测试 | P2 |
| G7 | Bug reducer | 无 | 复现+归约+去重 | 原始 case 过大无法提交工单（R11） | P2 |
| G8 | 可视化 | 依赖图 + 曲线 | +SQL/表视图、时间线、交互 hook | SQL bug 无法直观呈现（R19） | P2 |
| G9 | 自动化 | workload.variable 单维批处理 | YAML 实验配置 + 多维扫描 + 复现清单 | 实验管理靠手工 | P1 |
| G10 | 自动分析 | 手写 CSV + 前端图表 | 分析管道 + 多图示 + 报告 | 出图出报告靠手工 | P1 |
| G11 | 在线检查 | 无 | 持续历史在线验证（R18/R23） | 无法长时监控 | P3 |
| G12 | 白盒接口 | 无 | version certificate / timestamp 接口（R13/R23） | 黑盒性能上限无法突破 | P3 |

## 6. 非目标与边界

- **不做**：数据库功能/性能测试（SQLancer、TPC 基准性能对比不属于本平台；benchmark 只取"隔离测试有效"的部分）。
- **不做**：形式化验证工具链本身（平台集成而非重新实现定理证明）。
- **不承诺**：SQL 全语义的 SMT 检查（R25 指出这是开放问题；平台以"插桩 + 行级依赖 + 部分历史（partial-observation）"路线落地，见 05/06）。
