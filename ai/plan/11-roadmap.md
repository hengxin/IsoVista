# 11 · 实施路线图

## 1. 分期总览

| 期 | 主题 | 目标产出 | 对应文档 |
|---|---|---|---|
| **P0** | 平台化地基 | 统一 trace/历史 IR、Checker 插件 API、benchmark/实验配置 schema、分析数据规范 | 02/04/05/06/09/10 |
| **P1** | 覆盖面 | SQL Predicates 模型（插桩+谓词 checker）、benchmark 首批、executor 扩 8 库、实验编排与自动报告 | 02/03/04/06/10 |
| **P2** | 深度工具 | DBStorm/MT/Adaptive 生成器、bug reducer、history synthesizer、可视化增强、checker 全家桶 | 03/05/06/07/08 |
| **P3** | 生态与前沿 | 在线检查、白盒加速通道、公开 bug 库、社区 benchmark 贡献流程 | 06/07/09 |

原则：**每期结束平台都可交付使用**（P0 后可跑通现有功能的新骨架；P1 后覆盖 SQL；P2 后工具链完整；P3 后生态开放）。

## 2. P0：平台化地基（预计 6–8 周）

| 任务 | 产出 | 验收 |
|---|---|---|
| P0.1 统一 trace 格式与 Logger 独立化（04 §5） | `Trace` JSON schema + TraceLoader + 采集开销基线 | 现有 4 库跑通；开销报告 |
| P0.2 历史 IR 与 Synthesizer 骨架（05 §2-3） | `AbstractHistory` + IRBuilder + 格式往返回归 | 现有 checker 往返输出不变 |
| P0.3 Checker 插件 API（06 §2） | `CheckerPlugin` + 参考模板 + 6 家族迁移 | 迁移后 verdict 逐例一致 |
| P0.4 Benchmark manifest schema 与目录（02 §3） | `benchmarks/` 目录 + rw-register/list-append 入库 + CI 冒烟 | 默认参数一键跑通 |
| P0.5 实验配置 schema 与 Orchestrator 雏形（10 §2-3） | YAML schema + 单维 sweep + artifact 结构 | 单 YAML 驱动一次扫描实验 |
| P0.6 Profile JSON 规范（09 §2） | 结构化阶段 + 机器画像 | 现有 6 家族输出符合规范 |

**里程碑 M0**：新 checker 按模板 ≤200 行接入；一个 YAML 完成"生成→采集→多 checker→出图"闭环。

## 3. P1：覆盖面（预计 8–10 周）

| 任务 | 产出 | 验收 |
|---|---|---|
| P1.1 SQL Executor + 辅助列插桩（04 §4） | JdbcExecutor 的 SQL 化 + 插桩重写 + 行级 trace | MySQL/PG/TiDB/CockroachDB 行级记录抽测一致 |
| P1.2 SQL 历史 IR + 谓词反例格式（05/06） | SQL IR + phantom 边 + IsoRel 集成（子进程） | R13 Fig.1 场景正确判定 |
| P1.3 Benchmark 首批（02 §4-5） | TPC-C、smallbank、YCSB、RUBiS、Twitter、anomaly-suite | 每 benchmark 默认参数跑通 + 参考结果 |
| P1.4 Executor 扩库（04 §2） | Oracle/SQL Server/CockroachDB/TiDB/YugabyteDB | 覆盖矩阵 P1 全绿 |
| P1.5 Elle 全模型 + Cobra 集成（06 §3） | elle-cli 多模型 + Cobra 子进程 | SER 图检查家族到位 |
| P1.6 编排器完善 + 自动报告（10） | 多维 sweep + repeats + 断点续跑 + 报告模板 | 标准评测一键复现（论文三场景量级） |

**里程碑 M1**：SQL Predicates 端到端可用；≥8 数据库；≥9 checker 家族；对比报告自动生成。

## 4. P2：深度工具（预计 8–12 周）

| 任务 | 产出 | 验收 |
|---|---|---|
| P2.1 DBStorm-Gen（03 §4） | DataMirror + GraphSpaceExplorer + AnomalyInjector | 访问率 α=1、分布拟合 β 报告、33-bug 级有效性（自测集） |
| P2.2 MT-Gen（03 §5） | MTShapeLibrary + MT 生成 + 快速验证通道 | 端到端时间显著优于 GT（复现 R22 量级） |
| P2.3 Adaptive-Gen（03 §6） | 反馈闭环 + 覆盖率报告 | 固定预算覆盖率 ≥ 随机基线 |
| P2.4 Bug reducer（07） | OrderInferencer + CaseReducer + BugDeduplicator + bug 库 | 归约 ≥90% 操作数下降；复现率 ≥94% |
| P2.5 History synthesizer（05 §4） | SpecDiffSynthesizer + AllowedValueSynthesizer + MockExecutor | 复现 R17/R16 两个已知结论 |
| P2.6 可视化增强（08） | SQL 视图 + 时间线 + hook 机制 + 自然语言解释 | 验收点全过（含 10 万操作渲染 <3s） |
| P2.7 Checker 全家桶（06 §3） | Leopard/AWDIT/Vbox/Boomslang 后端 | ≥15 家族；交叉验证会诊可用 |

**里程碑 M2**：工具链完整闭环（benchmark→生成→执行→检查→归约→可视化→对比报告）。

## 5. P3：生态与前沿（预计 持续）

| 任务 | 产出 | 验收 |
|---|---|---|
| P3.1 在线检查（06 §3 / R18/R23） | AION 式在线 checker + RushMon 式异常计数 | 12K TPS 级吞吐、<5% 数据库影响 |
| P3.2 白盒加速通道（R13/R23） | version certificate / timestamp 接口 + Emme/CHRONOS 集成 | 白盒 vs 黑盒对照报告 |
| P3.3 公开 bug 库与数据集（07 §7） | bug 库导出 + 数据集发布（对标 R11） | 社区可引用 |
| P3.4 社区贡献流程（02 §7） | benchmark PR 模板 + CI 冒烟矩阵 + 文档站 | 第三方 benchmark 合入 |
| P3.5 教学/演示包（08） | Isolde 式区分历史 + 内置演示（phantom/write-skew…） | 一堂课可演示的用例集 |

**里程碑 M3**：平台对外开放贡献；在线/白盒能力作为差异化亮点。

## 6. 依赖关系与关键路径

```
P0.1 trace ──┬─▶ P0.2 IR ──▶ P0.3 checker 插件 ──▶ P1.2 SQL IR/谓词 ──▶ P2.7 全家桶
             ├─▶ P1.1 SQL executor ──▶ P1.2
             └─▶ P2.4 reducer（依赖 P1.1 重放）
P0.4 benchmark schema ──▶ P1.3 首批 benchmark ──▶ P2.1/P2.2 生成器
P0.5 编排器 ──▶ P1.6 自动报告 ──▶ P2.4/P2.5（pipeline 阶段扩展）
P0.6 profile 规范 ──▶ 全部性能验收
```

关键路径：**trace → IR → checker 插件 → SQL 通道**（P0.1→P0.2→P0.3→P1.1→P1.2），建议优先投入。

## 7. 人力与技能需求（估算）

| 角色 | 投入 | 主要职责 |
|---|---|---|
| 平台架构/后端（Java） | 1.5–2 FTE | IR、插件框架、executor、编排器 |
| 算法（checker 方向） | 1–1.5 FTE | checker 迁移/集成、交叉验证、白盒通道 |
| 前端/可视化 | 1 FTE | SQL 视图、时间线、hook、报告页 |
| 数据/分析 | 0.5 FTE | 分析管道、图表规范、报告模板 |
| 测试/CI | 0.5 FTE | 冒烟矩阵、回归保护、复现包验证 |

## 8. 风险与权衡

| 风险 | 影响 | 缓解 |
|---|---|---|
| SQL 全语义 SMT 检查是开放问题（R25 明确指出） | 谓词检查只能覆盖部分观测/插桩路线 | 明确平台边界（05/06 已声明）；插桩 + partial-observation + IsoRel/Vbox 路线分步落地 |
| 子进程 checker 的资产合规（license） | Cobra/Leopard 等集成法务风险 | 资产清单评审；优先 Apache/BSD/MIT 组件；R25 的"模块复现"（271–386 行/家族）作为兜底 |
| 多数据库环境搭建成本 | CI 时间长 | 容器化 DB 矩阵 + 冒烟子集；云库凭据隔离 |
| apple-to-apple 被"调参"污染 | 对比失信 | 协议强制（09 §4）：预算统一、重复取中位、机器画像留痕 |
| 归约/去重的误杀（把相关操作删掉） | 漏报 | 依赖感知归约 + 验证必经重放；保留归约报告供审计 |
| 范围蔓延（做成通用 DB 测试平台） | 交付延迟 | 坚守非目标（01 §6）：只做隔离测试 |

## 9. 与现有代码的衔接清单（汇总）

| 现有组件 | 计划动作 | 期 |
|---|---|---|
| `history/` 包 | 扩展 `AbstractHistory` IR + Trace/加载器归一 | P0 |
| `checker/Checker` + 6 家族 | `CheckerPlugin` 迁移 + Boomslang 式分层 | P0–P2 |
| `collector/` | 泛化 `Executor` + SQL 插桩 | P0–P1 |
| `generator/` | GT  benchmark 化 + 三类新生成器 | P0–P2 |
| `util/Profiler` 等 | 结构化 profile + 机器画像 | P0 |
| `backend/main.py` | 演进为 Orchestrator + Visualization 服务 | P0–P2 |
| `frontend/` | 实验视图、SQL 视图、时间线、报告页 | P1–P2 |
| `config.properties` / `workload.variable` | 实验 YAML 编译/泛化 | P0–P1 |
| `resource/`（elle-cli、Viper submodule） | 子进程 checker 资产；新增 Cobra 等 | P1–P2 |
| `docs/how-to-*` | 扩充为平台扩展指南（benchmark/executor/checker/reducer） | P0–P3 |

## 10. 结语

本计划以 **IR 统一、插件化、协议化对比** 为三根支柱：IR 打通 benchmark→生成→执行→检查→归约→可视化的数据流；插件化把"集成一个新东西"的成本压到最低；协议化对比保证平台产出的性能结论可信、可复现。按 P0→P3 推进，IsoVista 将从单体检查工具演进为隔离级别黑盒测试的基础设施。
