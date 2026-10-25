# 02 · Benchmark 套件方案（统一参数化、可定制）

## 1. 设计目标

1. **双数据模型覆盖**：
   - **Key-Value 模型**（读写寄存器 / list-append）：兼容现有全部 checker（C4/PolySI/Viper/Elle/Cobra…）；
   - **SQL Predicates 模型**（表、行、谓词读、范围扫描、insert/delete/update）：支撑谓词异常（phantom read）检测与 RR/SER 区分（R13、R14、R15）。
2. **权威性**：每个 benchmark 有明确出处（TPC、学术界基准、Jepsen/Elle 测试集、生产 bug 复现集），而非平台自造。
3. **统一参数化**：所有 benchmark 共享同一套参数 schema（规模、并发、分布、读写比、数据量、seed…），可被任意 generator/executor/checker 消费。
4. **可定制**：用户可基于模板定义新 benchmark（声明式 manifest），无需改平台代码。
5. **可比性**：benchmark 与"负载实例"分离——同一 benchmark 可生成多组负载（不同参数/seed），保证跨工具对比时输入一致。

## 2. 总体结构

```
benchmarks/
├── kv/                          # Key-Value 模型 benchmark
│   ├── rw-register/             # 读写寄存器（IsoVista 现载模型的 benchmark 化）
│   ├── list-append/             # list-append（Elle/Jepsen 语义）
│   ├── ycsb/                    # YCSB 六种负载（A-F）
│   ├── smallbank/               # smallbank（Bailis 经典弱隔离测试）
│   ├── twitter/                 # Twitter 负载（R4/R6 使用）
│   ├── rubis/                   # RUBiS（R4 使用）
│   └── blindw-rw/               # Viper 微基准 BlindW-RW（R4）
├── sql/                         # SQL Predicates 模型 benchmark
│   ├── tpcc/                    # TPC-C（new-order/payment/stock-level…）
│   ├── tpch-readonly/           # 只读分析型（隔离测试中的快照语义）
│   ├── seats/                   # SEATS（R15 BenchBase 应用之一）
│   ├── epinions/                # 社交类应用负载
│   ├── auctionmark/             # 拍卖类（更新冲突密集）
│   └── anomaly-suite/           # 异常模式定向测试集（write-skew/lost-update/phantom…）
└── regression/                  # 已知 bug 复现集（PostgreSQL SER 历史 bug、TiDB #42487 等）
    ├── pg-ser-rw/
    ├── tidb-rr-42487/
    └── dgraph-si/
```

每个 benchmark 是一个目录，包含：

| 文件 | 内容 |
|---|---|
| `manifest.yaml` | 元数据：数据模型、参数 schema、权威来源、适配的 isolation levels、已知覆盖的异常模式 |
| `schema.sql` / `schema.kv` | 数据模式（表结构/键空间） |
| `templates.*` | 事务模板（参数化操作序列；SQL 用参数化语句，KV 用 r/w 序列） |
| `data-gen.yaml` | 初始数据生成规则（规模、分布、外键完整性） |
| `defaults.yaml` | 默认参数（可直接跑通） |
| `README.md` | 出处、用法、与文献的对应关系 |

## 3. Benchmark Manifest Schema（统一描述）

```yaml
name: tpcc                       # 唯一标识（小写）
version: 1.0.0
model: sql                       # kv | sql
source: "TPC-C v5.11 (tpc.org); BenchBase implementation"
isolation_levels: [SER, SI, RR, RC]
anomalies_covered: [lost-update, write-skew, phantom, ...]   # 该 benchmark 设计意图覆盖
params:                          # 参数 schema（全部可被实验配置覆盖）
  warehouses: {type: int, default: 4, min: 1, max: 1000}
  sessions:    {type: int, default: 20}
  txns_per_session: {type: int, default: 100}
  ops_per_txn: {type: int, default: 5}
  read_ratio:  {type: float, default: 0.5}
  key_space:   {type: int, default: 1000}
  distribution: {type: enum, default: uniform, values: [uniform, zipf, hotspot]}
  seed:        {type: int, default: 0}
compatible_checkers: [C4, PolySI, SMT_SI, ...]   # 语义兼容声明（用于自动对比分组）
notes: "..."
```

平台约定：

- `model: kv` 的 benchmark 产出 `History<Key,Val>`（读写寄存器或 list-append），走现有 text/elle 序列化；
- `model: sql` 的 benchmark 产出**行级访问 trace**（表、主键、谓词、读集/写集），走 04 的 SQL trace 通道；
- 两类模型共享 `params` 命名，保证"同一实验配置可切换模型"。

## 4. Key-Value Benchmark 选型与规格

| Benchmark | 出处/用途 | 关键参数 | 覆盖异常 |
|---|---|---|---|
| rw-register | IsoVista 现载模型的规范化（R1） | sessions/txn/ops/read-ratio/key/distribution | 全谱系（取决于 checker） |
| list-append | Elle/Jepsen（R2） | append 比例、键数 | 版本序推断相关 |
| smallbank | Bailis et al.（弱隔离测试经典） | accounts、hot 比例 | write-skew、lost-update、read-skew |
| ycsb (A–F) | YCSB（Cooper et al.） | recordcount、ops、readproportion、zipfian θ | 倾斜访问下的冲突密集度 |
| twitter | RUBiS/Twitter 负载（R4、R6 采用） | 用户/推文规模、fan-out | 重复写值场景（R6 强调） |
| rubis | RUBiS（R4 采用） | 商品/竞拍数 | 更新冲突 |
| blindw-rw | Viper 微基准（R4） | 固定小事务形状 | 定向压力测试 |

实现要点：

- **smallbank/twitter/rubis/ycsb** 的事务模板以"操作序列 DSL"描述（如 `r(k1); w(k1, f(v)); r(k2)`），由 Generator 实例化；
- **重复写值支持**：VeriStrong（R6）指出现实负载普遍存在重复写值，benchmark 必须可配置"允许重复写值"开关（现有 IsoVista 生成器天然支持，需在 manifest 显式声明）；
- **确定性**：每个负载实例 = (benchmark, params, seed)，哈希即实例 ID，保证跨工具复现。

## 5. SQL Predicates Benchmark 选型与规格

| Benchmark | 出处/用途 | 表结构要点 | 覆盖异常 |
|---|---|---|---|
| tpcc | TPC-C（R15 用 BenchBase TPC-C 模型评估） | warehouse/district/customer/orders/order-line/stock 六表，外键密集 | lost-update、write-skew、phantom |
| anomaly-suite | Adya/Cerone 异常模式定向集（R13/R15） | 2–3 张小表，专为每种异常设计 | 每种异常一个最小场景（含谓词读双次扫描触发 phantom） |
| seats | SEATS（R15 BenchBase） | 航班/预订/客户 | 谓词更新、库存扣减 |
| epinions | 社交信任网络（R15） | 用户/信任关系 | 计数类写偏斜 |
| auctionmark | 拍卖（BenchBase） | 商品/竞拍/出价 | 高冲突更新 |
| tpch-readonly | TPC-H 只读部分 | 大表扫描、范围谓词 | 快照一致性（RR/SI 语义） |

实现要点：

- **谓词读必须一等公民**：`SELECT ... WHERE pred` 记录"谓词 + 返回行集 + 被检查行集（partial-observation，R15）"；
- **双次相同谓词读**是 phantom read 的最小触发器（R13 Fig.1），anomaly-suite 必须内置；
- **insert/delete 动态键空间**：R15 指出 SQL insert/delete 改变位置集合，trace 必须记录"被检查但不返回的行"；
- 表结构遵循 R10 的 pk/fk/nk 分类与数据分布参数化（初始记录数、fk/nk 分布函数）。

## 6. 与 Generator / Executor / Checker 的接口

```
Benchmark(manifest+templates) ──实例化──▶ WorkloadSpec(params, seed)
WorkloadSpec ──Generator──▶ TransactionSet（KV: History；SQL: 参数化事务计划）
TransactionSet ──Executor──▶ Trace（含时间区间、行级访问、SQL 文本）
Trace ──Synthesizer──▶ History IR ──Checker──▶ verdict + counterexample
```

- Generator 只依赖 WorkloadSpec，不依赖具体 DBMS（KV 与 SQL 两套实例化器，见 03）；
- Executor 只依赖 WorkloadSpec + 目标 DBMS 能力声明（见 04）；
- Checker 只依赖 History IR（见 05/06）；`compatible_checkers` 用于自动分组对比。

## 7. Benchmark 目录规范与治理

1. **准入标准**：有权威出处（标准/论文/开源实现）或明确的生产 bug 复现来源；manifest 完整；默认参数可一键跑通；附参考运行结果（verdict + 关键 profile）。
2. **版本化**：benchmark 目录纳入 git，语义化版本；manifest 变更必须兼容旧实验结果（或显式声明 breaking）。
3. **社区贡献**：第三方按 manifest schema 提交 PR；CI 跑"冒烟矩阵"（每个 benchmark × 默认 DB × 1 个 checker）。
4. **评测基准集（leaderboard 数据集）**：平台维护一组"标准评测负载"（固定 seed），用于 checker 论文式对比（R4/R6 的对比表即此形态）。

## 8. 与现状的衔接

- 现有 `GeneralGenerator` 的参数（session/transaction/operation/readproportion/key/distribution）直接映射为 `rw-register` benchmark 的默认参数；
- 现有 `docs/use-cases.md` 的 dgraph_si.txt 等样本归入 `regression/`；
- SQL benchmark 为全新模块，依赖 04 的 SQL Executor/Logger 与 05 的 SQL 历史 IR 先行落地（P1 优先级）。
