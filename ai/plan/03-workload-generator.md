# 03 · Workload Generator 方案

## 1. 设计目标

把"负载生成"从 IsoVista 现有的单一 `GeneralGenerator` 演进为**四类生成器 + 统一参数空间 + 完全可复现**的子系统：

| 生成器 | 思路 | 文献依据 | 解决的问题 |
|---|---|---|---|
| **GT-Gen**（通用） | 随机事务负载（session×txn×ops，分布可控） | 现有 GeneralGenerator 泛化；R4/R6 用法 | 大规模压力测试、建立信心 |
| **DBStorm-Gen** | 数据状态镜像 + 冲突图空间探索 + 异常植入 | R10（ISSTA'24） | 有效性（避免无效/冗余负载）、异常敏感（按 IL 定制） |
| **MT-Gen** | Mini-Transaction（≤4 操作的 RMW 事务） | R22（ICDE'25） | 强隔离级别端到端效率（生成快、验证快） |
| **Adaptive-Gen** | 反馈驱动（根据已发现异常/未覆盖冲突图调整） | R10 + R26 思路 | 覆盖率导向的持续测试 |

四类生成器共享**同一 WorkloadSpec 输入与参数命名**（见 02），可混合编排（如同一实验中 GT 打底 + MT 补刀 + Adaptive 收尾）。

## 2. 统一参数空间（所有生成器）

```yaml
# workload spec（benchmark manifest params 的实例）
sessions: 20                # 并发会话数
txns_per_session: 100       # 每会话事务数
ops_per_txn: 5              # 每事务操作数（MT-Gen 固定 ≤4）
read_ratio: 0.5             # 读比例
key_space: 1000             # 键空间/记录数
distribution: uniform       # uniform | zipf | hotspot | custom
zipf_theta: 1.0
hotspot_fraction: 0.2
duplicate_writes: true      # 是否允许重复写值（R6：现实负载普遍存在）
txn_shape: random           # random | rmw | template:<name>
abort_ratio: 0.0            # 注入 abort 的比例
seed: 0                     # 全局种子（一切随机性的根）
sql_predicate_ratio: 0.3    # SQL 模型：谓词读占比（02 的 sql benchmark）
scan_range: 10              # 范围扫描宽度
```

可复现性约定：**实例 ID = hash(benchmark, params, seed)**；生成器内部一切随机（含交错调度模拟）均由 seed 决定的确定性 PRNG 产生。

## 3. GT-Gen（通用生成器）

**现状升级**：现有 `GeneralGenerator`（线程池并行生成、uniform/zipf/hotspot、写值按 key 自增）直接 benchmark 化（rw-register），并扩展：

1. **事务模板支持**：除纯随机外，支持 smallbank/twitter/rubis 等模板化事务（`r(k1); w(k1,f(v)); r(k2)` DSL 实例化）；
2. **abort 注入**：按比例把事务标记为 aborted（检验 AbortedRead/ThinAirRead 类异常，C4 的 RC TAP 集依赖此类历史）；
3. **list-append 模型**：Elle 语义的 append 操作生成（版本序可高效推断）；
4. **重复写值开关**：关闭时写值全局唯一（Elle/Viper 的 collector 假设）；开启时允许重复（VeriStrong 路线）；
5. **SQL 实例化器**：同一参数空间生成参数化 SQL 事务计划（表、谓词、参数值），供 SQL Executor 执行。

## 4. DBStorm-Gen（有效负载生成）

核心三件套（R10）：

### 4.1 数据状态镜像（Data State Mirroring）

- **分布引导法**（fk/nk 属性）：以理论分布函数镜像属性值的存在性与体量，Δ-偏差有界（R10 定理 1：遵循 insert/delete/update 生成规则 R1–R3 后，经验分布与理论分布偏差 ≤ Δ 的概率 > 1−2e^{−2NrΔ²}）；
- **分区法**（pk 属性）：静态键常驻 + 动态键内存维护（每键 ⟨L,λ⟩ 锁与存在位），保证"每个操作都能访问到预期记录"（访问率 α=1）；
- 平台落地为独立组件 `DataMirror`：Generator 与 Executor 共享（执行前建镜像，执行中随写操作更新），使**谓词/范围操作也能命中预期行集**。

### 4.2 冲突图空间探索（Dissimilar Conflict Graphs）

- history-independent 的相邻图生成（图编辑距离驱动），保证负载间冲突图不相似（R10）；
- 平台落地为 `GraphSpaceExplorer`：维护已观测冲突图集合，新负载按"最大化图空间距离"采样；对 KV 模型用事务依赖图，对 SQL 模型用行级冲突图。

### 4.3 异常植入（Anomaly Implantation）

- 按目标 IL 的异常定义，构造 conflict path + pivot transaction（R10 §3④），把定向异常"种"进负载；
- 平台落地为 `AnomalyInjector`：输入异常类型（lost-update/write-skew/phantom/fractured-read…）+ 目标 IL，输出带定向异常的负载；与 `anomaly-suite` benchmark（02）配合，实现"每个异常一个最小复现负载"。

## 5. MT-Gen（Mini-Transactions）

设计约束（R22）：

- 每事务 ≤4 个 r/w 操作，且遵循 **RMW 模式**（每个写前有同键读）；
- 写值唯一（开启 MT 快速验证的前提；关闭则 NP-hard）；
- 事务形状从 14 种文献记载异常模式反推（R22：MTs 可捕获全部 14 种异常）。

平台落地：

- `MTShapeLibrary`：枚举全部 ≤4 操作 RMW 形状（按读写键组合分类），每形状标注可触发的异常模式；
- `MT-Gen` = 形状采样 + 键/值分配 + 会话编排；与 GT-Gen 共用分布与 seed 机制；
- **效率收益**（R22 实测）：生成阶段减少阻塞与重试；验证阶段线性/近线性时间——端到端显著优于通用负载，尤其适合强隔离级别的大批量日常测试。

## 6. Adaptive-Gen（自适应生成）

闭环：`生成 → 执行 → 检查 → 反馈 → 再生成`。

- 反馈信号：已发现异常类型集合、未覆盖的冲突图区域、checker 阶段耗时（哪类负载验证慢）、执行中止率；
- 策略：
  - 覆盖率导向：向未覆盖冲突图区域倾斜采样（R10 图空间探索的在线版）；
  - 难度导向：对验证耗时高的负载形状做参数扰动（帮助 checker 研究者做最坏情况分析）；
  - 异常导向：对已发现异常做参数邻域采样（评估 bug 的触发面）；
- 停止条件：预算耗尽（时间/历史数）或覆盖率收敛；
- 该生成器依赖 10 的实验编排层驱动（每轮是一个完整 mini-experiment）。

## 7. 调度与并发语义

- Generator 产出的是**事务计划**（谁在哪个会话按什么顺序发什么操作），不直接决定真实交错；
- 真实交错由 Executor 的并发执行自然产生（见 04）；对 KV 模型保留现有"线程池 + synchronized"的生成方式；
- 对**定向异常**负载（DBStorm 植入/MT 形状），Executor 提供"协同执行原语"（barrier/延迟注入），保证定向冲突按计划形成（R10 的 pivot 协调思想）。

## 8. 与现状衔接

| 现有组件 | 处置 |
|---|---|
| `generator/general/GeneralGenerator` |  benchmark 化为 GT-Gen 的 rw-register 实例，保留线程池实现 |
| `generator/general/{Bernoulli,HotspotInteger}Distribution` | 复用（分布采样组件） |
| `generator/tpcc/TpccGenerator`（空壳） | 废弃或重定向到 `benchmarks/sql/tpcc` + SQL 实例化器 |
| `config.properties` 的 workload.* | 映射为 WorkloadSpec 参数（保持向后兼容的键名） |

## 9. 验收要点

1. 同一 (benchmark, params, seed) 在两次运行产出逐字节相同的负载计划；
2. DBStorm-Gen 在任一目标 DBMS 上访问率 α=1（每个操作都访问到记录），并有分布拟合度 β 报告；
3. MT-Gen 负载在 SER/SI 下的端到端时间显著低于同等事务数的 GT 负载（复现 R22 的量级结论）；
4. Adaptive-Gen 在固定预算下覆盖的异常模式数 ≥ 随机基线。
