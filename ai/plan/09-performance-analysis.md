# 09 · 性能分析方案

## 1. 设计目标

1. **分阶段 profiling**：把 checker 的验证时间/内存分解到标准阶段（Construction / Encoding / Pruning / Solving / Traversal / IO），定位优化点（现有 IsoVista 论文演示的核心能力，产品化）；
2. **扩展性研究协议**：标准化"规模-性能"实验（sessions/txns/ops/keys/并发维度扫描），支持论文式曲线；
3. **apple-to-apple 对比协议**：同历史、同预算、同环境、同指标定义，杜绝"各说各话"（核心标准 C2）；
4. **运行时画像**：CPU/内存/GC/求解器内部统计的全链路采集与关联。

## 2. Profile 数据模型

```json
{
  "run_id": "run_20261025_2315",
  "checker": "PolySI",
  "isolation": "SNAPSHOT_ISOLATION",
  "history_ref": { "sha256": "...", "n_txns": 100000, "n_ops": 500000, "model": "kv-rw" },
  "machine": { "cpu": "...", "cores": 16, "mem_gb": 64, "os": "...", "jvm": "...", "solver": "monosat-1.6" },
  "budget": { "timeout_ms": 600000, "max_mem_mb": 8192 },
  "result": "accept|reject|timeout|oom",
  "stages_ms": { "construction": 120, "encoding": 3400, "pruning": 800, "solving": 15200 },
  "total_ms": 19520,
  "max_rss_mb": 2048,
  "counterexample": { "cycle_len": 3, "edges": [...] },
  "extra": { "solver_calls": 42, "constraints": 15233 }
}
```

阶段名标准化（与 06 的 `CheckerVerdict.stages` 对齐）：

| 阶段 | 含义 | 适用家族 |
|---|---|---|
| Construction | 依赖图/ASG 构建 | 全部 |
| Encoding | SMT/约束编码 | solver 类 |
| Pruning | 约束剪枝/可达性剪枝 | solver 类 |
| Solving | 求解（SAT/SMT） | solver 类 |
| Traversal | 图遍历/环检测 | 图算法类 |
| IO | 序列化/反例输出 | 全部（可单独计量） |

## 3. 采集机制（现有组件的产品化）

| 现有组件 | 扩展 |
|---|---|
| `util/Profiler`（tick/memory/CSV） | 阶段结构化 + 机器画像 + 预算强制执行（超时中断并标记 partial） |
| `util/RuntimeInfoRecorder`（500ms CPU/内存采样） | 采样率可配；子进程 pid 聚合（Viper/Elle/Cobra 子进程 checker 的消耗纳入） |
| `util/RuntimeStageRecorder` | 阶段带扩展到"归约/分析"阶段 |
| `util/RuntimeDataSerializer` | 输出上述 Profile JSON（现有 CSV 保留为兼容格式） |

## 4. apple-to-apple 对比协议（C2 落地）

一次"对比实验"的强制约束：

1. **同输入**：所有参评 checker 消费**同一历史文件**（IR 序列化 + sha256 校验）；负载由同一 (benchmark, params, seed) 生成；
2. **同预算**：统一 timeout / 内存上限 / CPU 核数（在实验配置中声明，机器画像记录实际值）；
3. **同环境**：同一容器镜像（平台提供 benchmark 镜像）；JVM/求解器版本固定；
4. **同指标**：
   - 主指标：verdict 一致率、验证时间（中位数/P95）、峰值内存；
   - 辅指标：分阶段时间、可处理规模上限（timeout 前的最大 n_txns）、反例质量（环长/可解释性评分）；
   - 统计：每点 ≥3 次重复，报告中位数与分布（消除噪声）；
5. **同呈现**：所有对比走统一的图表规范（10 §4），图注注明协议版本。

**分歧处理**：verdict 不一致时按 06 §5 交叉验证协议归因，不简单判定谁对谁错。

## 5. 扩展性研究协议

标准扫描维度（每个维度一次参数扫描实验）：

| 维度 | 扫描参数 | 观察指标 |
|---|---|---|
| 并发规模 | sessions ∈ [5,10,20,50,100,200] | 时间/内存曲线、可扩展拐点 |
| 历史长度 | txns_per_session ∈ [10,100,1k,10k] | 时间曲线（线性/超线性） |
| 事务大小 | ops_per_txn ∈ [2,5,10,50] | 依赖图密度影响 |
| 键空间 | key_space ∈ [10,100,1k,1M] | 冲突密度影响 |
| 读写比 | read_ratio ∈ [0.1..0.9] | 各 checker 的敏感度 |
| 写值唯一性 | duplicate_writes ∈ {true,false} | 假设敏感性（R6） |
| 模型 | kv-rw / kv-list / sql | 跨模型可比性 |

产出：每维度一张对数坐标曲线（多 checker 叠加）+ 汇总表（可处理规模上限排名）。IsoVista 论文的演示场景（PG SER bug、三 SI checker 对比、百万事务 TCC）即按此协议复刻为平台内置"标准评测"。

## 6. 内存与求解器内部度量

- 峰值 RSS（现有 `Profiler.getMemory`）+ 采样曲线；
- SMT 类：求解器调用次数、约束数、unsat core 大小（若求解器暴露）；
- 图算法类：边/节点数、遍历步数、剪枝率；
- 采集开销本身也计量（Logger/Executor 侧），避免"验证快了但采集慢了"的误导结论。

## 7. 与现状衔接

现有 `*-profile.csv`、`runtime_info.csv`、`runtime_stage.csv` 与前端图表保留；本方案在其上定义 JSON 规范与协议约束，分析管道见 10。

## 8. 验收要点

1. 任意 checker 的 profile 输出符合 §2 数据模型（阶段名来自标准表）；
2. "标准评测"一键复现 IsoVista 论文三场景的量级结论（PolySI+ vs Viper vs PolySI；百万事务 TCC <3 分钟）；
3. 对比报告自动标注协议版本与机器画像；verdict 分歧 100% 带归因标签。
