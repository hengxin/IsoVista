# 10 · 实验自动化与数据分析方案

## 1. 设计目标

1. **声明式实验配置**：一次实验（含参数扫描、多数据库、多 checker、归约、分析）由单个 YAML 描述，可版本化、可 diff、可复现；
2. **自动化运行**：CLI + 编排器驱动全流程，支持断点续跑、失败重试、资源限额；
3. **自动数据分析**：运行结束即产出结构化数据 + 多种图示 + Markdown/HTML 报告；
4. **可复现性**：每个实验产出自包含 artifact（配置、seed、历史、profile、图表、报告、环境清单）。

## 2. 实验配置 Schema

```yaml
# experiment.yaml
apiVersion: isovista/v1
kind: Experiment
metadata:
  name: si-checker-bakeoff
  description: "PolySI vs Viper vs Cobra on TPC-C, session sweep"
spec:
  benchmark: { name: tpcc, version: "1.0.0", params: {...} }   # 或 workload spec（见 03）
  databases:
    - { type: MYSQL, url: "jdbc:mysql://...", isolation: TRANSACTION_REPEATABLE_READ }
    - { type: COCKROACHDB, url: "jdbc:postgresql://...", isolation: TRANSACTION_SERIALIZATION }
  generator: { kind: dbstorm, seed: 42, coordination: true }
  checkers:
    - { name: PolySI, budget: { timeout_ms: 600000, max_mem_mb: 8192 } }
    - { name: Viper,  budget: { timeout_ms: 600000, max_mem_mb: 8192 } }
  sweep:                       # 参数扫描（笛卡尔积；单维即一维扫描）
    - param: sessions
      values: [5, 10, 20, 50, 100]
    - param: read_ratio
      values: [0.3, 0.5, 0.7]
  repeats: 3                   # 每配置重复次数（取中位数）
  pipeline:                    # 全流程开关
    stages: [collect, check, reduce, analyze, report]
  analysis:
    charts: [time-curve, stage-stack, scaling, verdict-matrix, memory-curve]
    report: { format: [markdown, html], lang: zh }
  resources: { parallel_runs: 4, cpu_limit: 8, mem_limit_gb: 64 }
```

约束：配置必须声明 `seed`；任何随机组件（生成、DBMS 侧抖动除外）由 seed 决定；配置与结果一起存入 artifact。

## 3. 运行编排（Orchestrator）

```
Experiment YAML ─▶ 校验（schema + benchmark/executor/checker 兼容矩阵）
                     │
                     ▼
             运行计划 DAG：collect(run) → check(run × checker) → reduce(bug) → analyze → report
                     │
                     ▼
             执行器：任务队列（现有 run_queue 演进）+ 资源限额 + 失败重试 + 断点续跑
                     │
                     ▼
             Artifact Store：result/exp_<id>/{config.yaml, runs/, bugs/, analysis/, report/}
```

- **任务粒度**：一次 (配置点 × 数据库 × 历史数) 为一个 run；一个 run 内多 checker 串行/并行（资源允许）；
- **断点续跑**：已完成 run 的产物哈希命中即跳过（内容寻址）；
- **失败策略**：网络/DB 连接类错误自动重试（指数退避）；checker timeout/oom 记为结果而非失败；
- **复用现有**：`backend/main.py` 的队列 + 前端 RunList/RunView 演进为编排器的 Web 面；`Main.java` 的 `runOneShot` 逻辑重构为编排器的 Java 侧执行器。

## 4. 自动数据分析与作图

### 4.1 分析管道

```
Profile JSON + trace + verdict 集合
   ─▶ 聚合器（按 checker/维度分组，统计：中位数/P95/上限）
   ─▶ 图表规范生成器（每图一个 JSON spec）
   ─▶ 渲染（ECharts 服务端渲染 PNG/SVG + 前端交互）
   ─▶ 报告组装（Markdown/HTML，图 + 表 + 结论模板）
```

### 4.2 标准图示库（多种展示方式）

| 图 | 类型 | 展示内容 |
|---|---|---|
| `time-curve` | 折线（对数轴） | 验证时间 vs 规模（多 checker 叠加，误差带） |
| `stage-stack` | 堆叠柱状/面积 | 分阶段时间分解（每 checker 一组） |
| `memory-curve` | 折线/面积 | 内存随时间/规模 |
| `scaling` | 双对数散点 + 拟合 | 扩展性指数（线性/二次/超线性判定） |
| `verdict-matrix` | 热力表 | checker × 负载 的 verdict（accept/reject/timeout + 分歧高亮） |
| `bug-summary` | 表格 + 条形 | bug 计数（按 DB/级别/异常类型/版本） |
| `coverage` | 柱状 + 饼 | 异常模式覆盖率（Adaptive-Gen 场景） |
| `reduction` | 对比条 | 归约前后操作数/事务数 |
| `cpu-runtime` | 双轴折线 | 运行时 CPU/内存（现有 runtime_info 的产品化） |
| `stage-timeline` | 甘特 | 阶段时间线（现有 runtime_stage 的产品化） |

### 4.3 报告模板

- **实验报告**（Markdown/HTML）：配置摘要 → 环境画像 → 主结果表 → 标准图示集 → verdict 分歧分析 → bug 清单（链接到 bug 库）→ 复现指引（artifact 哈希）；
- **对比报告**：突出 apple-to-apple 协议版本、可处理规模上限、分阶段差异归因；
- **日报/周报**：定时实验（可配置 automation）自动生成趋势（bug 数、性能回归）。

## 5. CLI 与 SDK

```bash
# 运行实验
isovista run experiment.yaml --out result/exp_001
# 只跑分析（复用已有 artifact）
isovista analyze result/exp_001 --charts all --report md,html
# 导出可复现包（配置 + 数据 + 报告 + 环境）
isovista export result/exp_001 --self-contained
# 列出现存实验/对比
isovista ls experiments
```

Python SDK（与 FastAPI 后端同源）：`isovista.Client().run(...)` / `.analyze(...)`，便于脚本化与 CI 集成。

## 6. 复现性清单（每个 artifact 必含）

| 项 | 内容 |
|---|---|
| `config.yaml` | 实验配置原文 |
| `env.json` | 平台版本、容器镜像 ID、DBMS 版本、JVM/求解器版本、机器画像 |
| `seeds.json` | 全部随机种子 |
| `runs/` | 每 run 的 trace/历史（或其内容哈希 + 存储位置）、profile JSON、verdict |
| `bugs/` | raw + minimal case（若进入归约） |
| `analysis/` | 聚合数据（CSV/JSON）+ 图表 spec |
| `report/` | 最终报告 |
| `checksums.txt` | 全部文件 sha256 |

## 7. 与现状衔接

| 现有组件 | 处置 |
|---|---|
| `config.properties` | 保留为单次运行配置；实验配置编译为一次或多次 properties 运行（兼容旧路径） |
| `Main.java` 的 `workload.variable` 批处理 | 泛化为通用 sweep（多维笛卡尔积 + repeats） |
| `backend/main.py` 队列 | 演进为 Orchestrator 的任务层；`/run` API 保留（提交单配置） |
| `frontend` RunList/RunView/BugList/BugView | 增加实验视图（一次实验 = 一组 run 的聚合展示）与报告页 |
| 现有 CSV 产物 | 保留并扩展 JSON profile（09 §2） |

## 8. 验收要点

1. 单个 YAML 驱动"TPC-C × 2 库 × 3 checker × 5 点扫描 × 3 重复"全流程无需人工介入；
2. 同一 artifact 二次分析产出逐字节一致的图表数据；
3. 复现包在干净容器内可完整重跑（除真实云 DB 凭据外）；
4. 报告含全部标准图示且与协议版本标注一致（09 §4）。
