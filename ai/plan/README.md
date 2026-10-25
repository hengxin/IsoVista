# IsoVista 统一黑盒隔离测试平台 — 开发与扩展计划

> 状态：**规划中（仅方案，不实现）** · 生成日期：2026-10-25
> 依据：IsoVista 现有代码库通读 + `refs/` 27 篇权威文献调研（VLDB/OSDI/EuroSys/SIGMOD/ICDE/ISSTA/CAV/PLDI/CIDR/TOCS 2020–2026）

## 1. 目标定位（一句话）

把 IsoVista 从"SI/SER 检查器 + 简单 RW-register 负载采集的 Web 工具"演进为**隔离级别黑盒测试的统一平台（unified black-box isolation testing platform）**：科研人员在该平台上可以一键完成 **benchmark 选择 → 负载生成 → 多数据库执行与记录 → 历史合成 → 多 checker 并行验证 → bug 归约去重 → 可视化分析 → 性能对比 → 自动作图和出报告** 的全流程，并与平台已集成的算法/工具做 **apple-to-apple 比较**。

## 2. 核心标准（验收总纲）

| # | 标准 | 可检验指标 |
|---|---|---|
| C1 | **快速验证新算法/新工具** | 新 checker 按统一 API 接入后 ≤200 行胶水代码即可跑通全流程；提供参考实现模板 |
| C2 | **apple-to-apple 比较** | 同一历史、同一机器、同时间/内存预算下，任意两个 checker 的对比一键生成（含分阶段分解） |
| C3 | **benchmark 全面且权威** | 同时覆盖 key-value 数据模型与 SQL Predicates 模型；集成 TPC 系、YCSB、RUBiS、Twitter、smallbank 等公认 benchmark 及文献测试集 |
| C4 | **数据库覆盖主流** | 至少 12 类数据库（见 04 文档矩阵），含云 NewSQL 与 NoSQL |
| C5 | **自动化运行脚本** | 参数化实验配置（YAML/CLI）驱动批量运行，支持参数扫描与复现 |
| C6 | **自动数据分析与作图** | 运行结束即产出多种图示（时序曲线、阶段堆叠、散点、通过率表等）与汇总报告 |

## 3. 文档索引

| 文档 | 内容 |
|---|---|
| [01 愿景与总体架构](01-vision-and-architecture.md) | 愿景、目标用户、核心标准落地、目标架构、与现状的 GAP 分析 |
| [02 Benchmark 套件](02-benchmark-suite.md) | 统一参数化 benchmark：KV 模型 + SQL Predicates 模型、选型与目录规范 |
| [03 Workload Generator](03-workload-generator.md) | 通用 / DBStorm 式 / Mini-Transaction / 自适应四类生成器 |
| [04 Executor 与 Logger](04-executor-and-logger.md) | 数据库全覆盖矩阵、统一 trace 格式、SQL 插桩 |
| [05 History Synthesizer](05-history-synthesizer.md) | 历史中间表示、隔离级别区分合成、允许值枚举、历史变换 |
| [06 History Checkers](06-history-checkers.md) | Checker 统一框架、集成清单、交叉验证协议 |
| [07 Bug Reducer](07-bug-reducer.md) | 确定性复现、依赖感知归约、去重 |
| [08 可视化](08-visualization.md) | 依赖图、SQL 视图、时间线、交互 hook、导出 |
| [09 性能分析](09-performance-analysis.md) | 分阶段 profiling、扩展性协议、对比协议 |
| [10 实验自动化与数据分析](10-experiment-automation.md) | 参数扫描、批量脚本、复现、自动作图 |
| [11 实施路线图](11-roadmap.md) | P0–P3 分期、里程碑、风险 |

## 4. 模块总览（与现状对照）

| 流程环节 | 现状（代码事实） | 目标形态 | 计划文档 |
|---|---|---|---|
| Benchmarks | 无独立 benchmark 概念，仅内置 RW-register 生成参数 | 统一参数化 benchmark 套件（KV + SQL Predicates） | 02 |
| Workload generator | `GeneralGenerator`（uniform/zipf/hotspot）；`TpccGenerator` 空壳 | 四类生成器 + 统一参数空间 + seed 复现 | 03 |
| Executor | MySQL / PostgreSQL / MariaDB / H2（JDBC），失败重试 1000 次 | ≥12 类数据库，含 CockroachDB/TiDB/Yugabyte/OceanBase/Dgraph/MongoDB 等 | 04 |
| Logger | Collector 回填读值，无独立 trace 层 | 统一 trace（时间区间 + 行级访问记录），兼容 Jepsen/Elle 格式 | 04 |
| History synthesizer | Loader/Serializer（text/elle） | + 合成（Isolde 式区分历史、MonkeyDB 式允许值）+ 变换 | 05 |
| History checkers | C4 / PolySI / SMT_SI / SMT_SER / Viper / ELLE_TCC（6 家族） | 统一框架 + 10+ 家族 + 交叉验证 | 06 |
| Bug reducer | 无 | Pisco 式归约 + 去重，接入 bug 库 | 07 |
| Visualization | G6 依赖图 + ECharts 曲线 | + SQL/表级视图、时间线、交互 hook、多格式导出 | 08 |
| Performance analysis | Profiler + RuntimeInfo/StageRecorder | + 分阶段分解、扩展性协议、对比协议 | 09 |
| 实验自动化/分析 | workload.variable 批量化 + CSV | YAML 实验配置 + 参数扫描 + 自动作图 + 报告 | 10 |

## 5. 路线图摘要

- **P0（平台化地基）**：统一 trace/历史 IR、Checker 插件 API、benchmark 目录 schema、实验配置 schema。
- **P1（覆盖面）**：SQL Predicates 模型（插桩 + 谓词 checker）、benchmark 套件首批（TPC-C、YCSB、smallbank、RUBiS、Twitter）、executor 扩到 8+ 数据库。
- **P2（深度工具）**：DBStorm 式生成器、MTC/MT 生成器、bug reducer、history synthesizer、可视化增强。
- **P3（生态）**：10+ checker 全家桶、在线检查（RushMon/AION 式）、白盒接口（version certificate recovery）、自动出报告与公开 bug 库。

详见 [11 实施路线图](11-roadmap.md)。

## 6. 参考文献索引（refs/ 27 篇）

| 编号 | 文献 | 在本计划中的用途 |
|---|---|---|
| R1 | IsoVista (PVLDB 2024) | 现状基线与架构基线 |
| R2 | Elle (VLDB 2020) | list-append 历史、依赖图检查基线 |
| R3 | PolySI (PVLDB 2023) | SI 图检查基线 |
| R4 | Viper (EuroSys 2023) | SI 检查、range query 支持、benchmark 用法 |
| R5 | Cobra (OSDI 2020) | SER 检查、事务分段、持续验证 |
| R6 | VeriStrong (PVLDB 2025/2026) | 强隔离 SMT、重复写值处理 |
| R7 | Plume (OOPSLA 2024) | 弱隔离检查、向量/树时钟 |
| R8 | Boomslang (arXiv 2026) | checker 统一框架（前/后端 + IR + superpositions） |
| R9 | AWDIT (PLDI 2025) | 弱隔离最优复杂度测试器 |
| R10 | DBStorm (ISSTA 2024) | 有效负载生成、数据状态镜像、异常注入 |
| R11 | Pisco (PVLDB 2026) | bug 归约与去重 |
| R12 | Leopard (ICDE 2023 / CIDR 2024) | 黑盒通用验证、四机制抽象、在线验证 |
| R13 | Bringing Order to Practical Validation (TOCS 2026) | 谓词操作、version certificate recovery、Emme/Enne |
| R14 | IsoRel (ISSTA 2025) | 关系模型 SQL 插桩检查（辅助列） |
| R15 | Mixed Isolation for SQL (CAV 2025) | SQL 事务形式语义、混合隔离级别 |
| R16 | MonkeyDB (PACMPL 2021) | mock storage、允许值枚举合成 |
| R17 | Isolde (arXiv) | 隔离级别区分历史合成（CEGIS） |
| R18 | RushMon (SIGMOD 2018) | 实时异常监控 |
| R19 | IsoBugView (PVLDB 2022) | 交互式 bug 调试、hook 机制 |
| R20 | Troc (ICSE 2023) | 事务 oracle 构造（语句级视图） |
| R21 | Biswas & Enea (OOPSLA 2019) | 复杂度理论、公理框架 |
| R22 | MTC/Mini-Transactions (ICDE 2025) | 紧凑事务负载生成 |
| R23 | CHRONOS/AION (ICDE 2025) | 时间戳白盒检查、在线检查 |
| R24 | Vbox (ISSTA 2026) | 黑盒 SER 验证 + 谓词支持 |
| R25 | Making Checking Practical/Boomslang (arXiv 2026) | 任意操作类型、模块化 checker |
| R26 | IsoFuzz (SPLASH Companion 2025) | 灰盒 fuzz、插桩调度 |
| R27 | Online Timestamp-based Checking (ICDE 2025) | 在线 SI/SER 检查 |

> 注：文献要点均提炼自 `refs/` PDF 原文提取（Elle 一篇为扫描版未能机读，依据其公开集成形式描述）；引用细节以原论文为准。
