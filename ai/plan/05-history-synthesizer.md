# 05 · History Synthesizer 方案

## 1. 定位与职责

History Synthesizer 是平台的数据中枢，承担四类职责：

1. **归一化（Normalize）**：把 Logger 的统一 trace、用户上传的 Jepsen/elle 历史、text r/w 历史统一转为平台**历史 IR**；
2. **合成（Synthesize）**：不经过真实 DBMS，直接产出具有特定语义的历史——用于 checker 单元测试、隔离级别规格比对、应用弱隔离测试；
3. **变换（Transform）**：对既有历史做结构化变换（裁剪、合并、回放、注释增强）；
4. **导出（Export）**：按各 checker/工具的格式要求导出（text、elle EDN、JSON、Viper JSON、Boomslang ASG 等）。

## 2. 统一历史 IR（Intermediate Representation）

在现有 `History/Session/Transaction/Operation` 之上扩展为两层：

```java
// 抽象层（与数据模型无关）
class AbstractHistory {
    List<Session> sessions;              // so 关系
    List<AbstractTxn> txns;              // po 关系（事务内操作序）
    List<AbstractOp> ops;                // 见下
    VisibilityOracle visibility;         // 可选：白盒/合成时给定的可见性信息
}

class AbstractOp {
    OpId id; SessionId session; TxnId txn;
    OpKind kind;         // READ / WRITE / RMW / SCAN / INSERT / DELETE / COMMIT / ABORT
    // KV 语义
    Key key; Value value;                // value = null 表示未观测（如 list-append 空读）
    // SQL 语义
    String table; String predicate;      // 谓词（含范围）
    Set<RowId> rowsReturned;             // 返回行集
    Set<RowId> rowsChecked;              // 被检查行集（插桩可得；否则记为 partial）
    // 元数据
    long startTs, endTs;                 // 执行区间（黑盒采集必有）
    boolean uniqueValue;                 // 写值是否全局唯一（影响 checker 假设）
}
```

设计原则：

- **IR 是超集**：KV 历史（text/elle）是 IR 的特例（table=null、predicate=null）；
- **可见性可选**：黑盒采集的 visibility 为空；合成/白盒历史可携带（供 Emme/CHRONOS 类 checker 消费）；
- **完整/部分观测标记**：每段历史标注 `complete`（wr 关系完备，可黑盒构造）或 `partial`（如 R15 的 partial-observation：SQL 查询只暴露返回行）。

## 3. 归一化流水线（Normalize）

```
text r/w 历史 ──┐
elle EDN 历史 ──┼─▶ Parser ──▶ Trace ──▶ IRBuilder ──▶ AbstractHistory ──▶ 校验器
平台 trace JSON ─┘                        （行级/谓词增强）      （内部一致性、wr 可推理性）
```

- 现有 `TextHistoryLoader` / `ElleHistoryLoader` 保留为 Parser 的两个实现；
- 新增 `TraceLoader`（读 04 的统一 trace JSON）；
- **IRBuilder** 负责从 trace 恢复事务结构与读写匹配（KV：值匹配；SQL：行匹配）；
- **校验器**执行现有 `Utils.verifyInternalConsistency` 的扩展版：内部一致性（如读到的值必须来自某次写）、aborted 写集合、重复写值统计——这些元数据随 IR 传递，供 checker 选择算法路线。

## 4. 历史合成（Synthesize）

### 4.1 规格区分合成（Isolde 路线，R17）

- **问题**：给定两个隔离级别规格 A、B（平台内置 Adya/Cerone/Biswas-Enea 等多框架的编码），找出"被 A 允许但不被 B 允许"的历史；
- **方法**：CEGIS（反例引导的归纳合成）——平台落地为 `SpecDiffSynthesizer`：
  1. 规格以约束（可见性/冲突关系上的公理）编码；
  2. 有界枚举 + SMT 求解候选历史；
  3. 用平台内置 checker（当作 oracle）验证候选历史的归属；
  4. 迭代直至找到区分历史或证明有界内不存在；
- **用途**：checker 规格的交叉验证（R17 曾用此法发现某 SOTA checker 的规格错误）、教学演示、回归测试用例生成。

### 4.2 允许值枚举合成（MonkeyDB 路线，R16）

- **问题**：应用在弱隔离下的健壮性测试需要"合法但罕见"的返回值序列；
- **方法**：`AllowedValueSynthesizer`：给定隔离级别公理与当前已观测执行，计算某读操作的**全部合法返回值集合**，随机返回其一；以 mock storage（`MockExecutor`，见 04）形式提供给应用测试；
- **用途**：应用级测试（无需真实 DBMS 复现 bug）；检查器覆盖率实验（R16 证明其覆盖在极限下完备）。

### 4.3 异常模式合成（定向最小反例）

- 输入：异常模式（14 种文献异常，R22）+ 目标 IL；
- 输出：触发该异常的最小历史（2–4 事务、8–10 操作，R11 的统计先验）；
- 与 02 的 `anomaly-suite` benchmark、07 的 bug reducer 互为逆操作（合成 ↔ 归约），共用同一异常模式库。

## 5. 历史变换（Transform）

| 变换 | 说明 | 用途 |
|---|---|---|
| `project(txns/ops)` | 投影出子集历史 | 归约（07）的底层操作；教学聚焦 |
| `merge(histories)` | 多历史合并（重编 id） | 长时测试拼接；分布式多 shard 历史合并 |
| `replay-annotate(trace)` | 用执行区间注释历史（wr/rw 推断） | 黑盒→半白盒增强（Leopard 区间法，R12） |
| `rekey/revalue` | 键/值重命名 | 去重前的规范化（07） |
| `to-elle/to-text/to-json` | 格式导出 | 对接外部工具 |
| `crop-to-cycle` | 裁剪到反例环 + 支撑子图 | 可视化聚焦（08）；Pisco 归约的可视化伴随 |

## 6. 与现有组件的衔接

| 现有组件 | 处置 |
|---|---|
| `history/History/Session/Transaction/Operation` | 作为 IR 的 KV 特化保留；新增 `AbstractHistory` 超集与双向转换 |
| `history/loader/*` | 归一化为 Parser 接口的实现 |
| `history/serializer/*` | 归一化为 Exporter 接口的实现 |
| `history/History.addInitSession()/addInitSessionElle()` | 上移为 IRBuilder 的标准化步骤（初始化事务注入策略可配置） |
| `checker/PolySI` 对 elle 历史的特判（`KnownGraph.setElleHistory`） | 改为 IR 上的"模型标签"（list-append 模型），消除 checker 内的格式分支 |

## 7. 验收要点

1. 现有全部 checker 在"trace → IR → 导出原格式"往返后输出不变（回归保护）；
2. SQL 历史 IR 可表达 R13 Fig.1 的 phantom read 历史与 R15 的混合隔离级别历史；
3. `SpecDiffSynthesizer` 能复现 R17 的两个已知结论（SI 规格的微妙差异 + 某 checker 规格错误）；
4. `AllowedValueSynthesizer` 在购物车例子上 20 次运行内触发断言失败（复现 R16 量级）。
