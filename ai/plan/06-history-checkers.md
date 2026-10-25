# 06 · History Checkers 统一框架方案

## 1. 设计目标

1. **统一插件框架**：任何 checker（平台自研、第三方开源、用户新算法）以同一骨架接入，≤200 行胶水代码（核心标准 C1）；
2. **前/后端分离 + IR 下沉**：借鉴 Boomslang（R25/R8）的"解析 → 抽象语义图 → 语义分析 → 低层 IR（含 superpositions）→ SMT/图算法"分层，使新隔离级别/新操作类型的支持成本最小化；
3. **家族化集成**：10+ checker 家族覆盖 RC/RR/RA/TCC/SI/SER/混合隔离/SQL 谓词；
4. **交叉验证**：多 checker 会诊同一历史，分歧即警报（假阳性/假阴性线索），同时服务 apple-to-apple 对比（C2）。

## 2. 统一 Checker 插件 API

```java
public interface CheckerPlugin {
    String NAME();                                  // 注册名
    Set<IsolationLevel> supportedLevels();          // 支持的隔离级别
    Set<DataModel> supportedModels();               // kv-rw | kv-list | sql
    Set<OpKind> requiredOps();                      // 依赖的操作类型（range/scan…）

    void init(CheckerContext ctx);                  // 配置、profile、IR 访问

    CheckerVerdict verify(AbstractHistory history); // 核心

    default void report(CheckerVerdict v, String path) { ... }  // DOT/JSON 反例输出
    default Map<String, Long> profileInfo() { ... }             // 分阶段耗时
}
```

`CheckerVerdict`：`{ accept | reject, counterexample (cycle/unsat-core), stages (构造/编码/剪枝/求解 或 构造/遍历), assumptions (唯一写值/完整 wr 关系…) }`。

与现有 `Checker` 接口的关系：现有 `verify/outputDotFile/getProfileInfo` 三方法保留为默认实现路径，`CheckerPlugin` 在其上扩展（向后兼容，现有 6 家族平滑迁移）。

**分层落点（Boomslang 式，R25）**：

```
AbstractHistory ─▶ FrontEnd: 解析 → ASG（抽象语义图）
                          ─▶ SemanticAnalysis: wr 推断、可见性闭合、superposition 标记
                          ─▶ Lowering: → 低层 IR
BackEnd:  IR ─▶ ConstraintGen（SMT 编码 / 图构造）
              ─▶ Optimize（reachability pruning、topological prioritization、unsat search）
              ─▶ Solve（MonoSAT/Z3/MiniSAT / 图遍历 / 区间推断）
```

- 平台自研 checker（C4/Plume/PolySI/VeriStrong 血统）按此分层重构；
- 外部 checker 可只实现 BackEnd（吃平台 IR），或整体作为子进程（吃导出文件）。

## 3. Checker 家族集成清单

| 家族 | 级别 | 模型 | 路线 | 集成方式 | 优先级 |
|---|---|---|---|---|---|
| **C4 / C4-list**（现有） | RC/RR/RA/TCC | kv-rw/kv-list | TAP + 图遍历 | 重构为插件 | 已集成 |
| **Plume**（现有弱隔离核心血统） | RC/RA/TCC | kv | 向量/树时钟图遍历（R7） | 即现有 C4 的高性能实现，显式化 | 已集成 |
| **PolySI**（现有） | SI | kv | 图 + 约束求解 + 剪枝 | 重构为插件 | 已集成 |
| **PolySI+ / SMT_SI**（现有） | SI | kv | MonoSAT SMT + 启发式剪枝 | 重构为插件 | 已集成 |
| **VeriStrong / SMT_SER**（现有） | SER | kv | hyper-polygraph SMT（R6） | 重构为插件；补重复写值支持 | 已集成 |
| **Viper**（现有） | SI（含 Strong/GSI/Strong Session SI） | kv + range | 子进程 Python | 保留子进程通道 | 已集成 |
| **ELLE_TCC**（现有） | TCC | kv-list | 子进程 elle-cli | 保留子进程通道 | 已集成 |
| **Elle（完整）** | SI/SER/RC/… | kv-list | 子进程 elle-cli（多 consistency-models） | 扩展现有 ELLE_TCC 为全模型 | P1 |
| **Cobra** | SER（strict/non-strict） | kv | SMT + 事务分段（R5） | 子进程或 JNI（开源可复现） | P2 |
| **Leopard** | 各种 IL（四机制镜像） | kv/sql | 区间推断 + 机制模拟（R12） | 子进程；提供白盒/黑盒双模式 | P2 |
| **AWDIT** | RC/RA/CC（最优复杂度） | kv | 算法实现（R9） | JNI/Java 复现（论文算法清晰） | P2 |
| **Vbox** | SER（含谓词） | kv/sql | SAT 编码（R24） | 子进程/JNI | P2 |
| **IsoRel** | Adya 各级别 | sql | 辅助列插桩 + 异常模式（R14） | 子进程；依赖 04 插桩 trace | P1 |
| **Emme / Enne** | SER/SI（含谓词） | kv/sql | 白盒 version certificate（R13） | 白盒通道（可选加速） | P3 |
| **CHRONOS / AION** | SI/SER（离线/在线） | kv/sql | 时间戳白盒 + 增量（R23） | 白盒/在线通道 | P3 |
| **Troc** | 各级别（语句级 oracle） | sql | 独立语句视图比对（R20） | 子进程；与 IsoRel 互补 | P3 |
| **Boomslang（框架）** | 可编程规格 | kv（任意操作） | 前端/后端框架（R25） | 作为平台 BackEnd 的参考实现蓝本 | P2 |
| **MonkeyDB（oracle）** | 弱隔离允许值 | kv/sql | mock storage（R16） | 合成/应用测试通道（见 05） | P2 |

> 说明：优先级 P1 = 平台化必需；P2 = 生态完整；P3 = 前沿/白盒可选。子进程类 checker 统一走"进程监控 + 退出码 + 输出文件解析"适配器（现有 Viper/Elle 模式的产品化）。

## 4. 规格与隔离级别管理

- 平台维护**规格库**：每个 (框架, 级别) 一条机器可读规格：
  - Adya 图论规格（依赖禁止模式）；
  - Cerone/Gotsman 公理规格（SI）；
  - Biswas-Enea 公理规格（RC/RA/CC/PC/SI/SER，R21）；
  - 厂商特例规格（如 MariaDB `SELECT FOR UPDATE` 语义，R25 Case#2）；
- 混合隔离级别（每事务不同级别，R15）作为一等配置：`checker.isolation=[{level: SI, txns: [...]}, ...]`；
- 规格库同时驱动 SpecDiffSynthesizer（05）与 checker 自检（新 checker 必须通过规格库导出的合成历史测试集）。

## 5. 交叉验证协议（会诊模式）

对同一历史运行多个兼容 checker，按结果分桶：

| 结果 | 含义 | 平台动作 |
|---|---|---|
| 全部 accept | 无警报（在该组 checker 的规格假设下） | 记录置信度 |
| 部分 reject | 规格/假设分歧（如唯一写值假设、list-append 限制） | 标注分歧原因（assumptions diff），提示人工分析 |
| 全部 reject 且反例一致 | 高置信 bug | 直接进入 reducer（07） |
| 全部 reject 但反例不一致 | 至少一个 checker 的规格可疑 | 触发规格库比对（05 §4.1），高亮显示 |

交叉验证同时是**新 checker 的试金石**：接入后先在标准负载集上与在位 checker 会诊，分歧驱动调试。

## 6. 性能公平性基础设施（支撑 C2）

- **统一预算接口**：timeout、内存上限、核数由实验配置统一下发；checker 须可被中断并报告部分结果；
- **统一 profile  schema**：阶段名标准化（Construction/Encoding/Pruning/Solving/Traversal/IO），现有 Profiler 的 tag 机制扩展为结构化阶段；
- **机器画像**：每次运行记录 CPU/内存/OS/JVM/求解器版本（apple-to-apple 的前提）；
- **同输入保证**：对比实验共享同一历史文件（IR 序列化后哈希校验）。

## 7. 与现状衔接

| 现有组件 | 处置 |
|---|---|
| `checker/Checker` 接口 | 保留并标记为 legacy 适配层；`CheckerPlugin` 为新接口 |
| `checker/{C4,PolySI,VeriStrong_SI,VeriStrong_SER,Viper,elle}` | 逐步迁移为插件；C4 内部按 Boomslang 分层拆分前端/后端 |
| `util/ConfigParser` 的 checker 映射表 | 升级为插件注册表（NAME → 能力声明） |
| `util/Profiler` | 阶段结构化 + 机器画像记录 |
| `resource/elle-cli*.jar`、`resource/Viper`（submodule） | 保留为子进程 checker 资产；新增 Cobra/Leopard 等子进程资产 |

## 8. 验收要点

1. 新 checker 参考实现 ≤200 行即可接入并跑通"验证 + 反例 + profile"全流程；
2. 6 个现有家族迁移为插件后，在标准负载集上 verdict 与迁移前逐例一致（回归保护）；
3. 任一历史上至少 3 个 checker 可并行会诊，分歧正确归因；
4. SQL/谓词历史可被至少一个 checker（IsoRel 或 Vbox 或 Emme）正确判定 RR vs SER。
