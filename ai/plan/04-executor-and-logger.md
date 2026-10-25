# 04 · Executor 与 Logger 方案

## 1. 设计目标

1. **Executor 覆盖所有主流数据库**：从现有 4 库扩展到 ≥12 类，含关系型、云 NewSQL、分布式 KV 与文档库；
2. **Logger 独立化**：trace 采集从 Collector 中剥离为一等组件，统一格式、可插桩、可对接 Jepsen/Elle 生态；
3. **SQL 执行通道**：支持谓词读、范围扫描、insert/delete/update 的行级记录（含"被检查但未返回的行"，R15）；
4. **能力声明**：每个 Executor 声明目标 DBMS 的能力（是否支持谓词、快照读取、abort 注入、协同执行原语），供 Generator/Checker 做兼容性检查。

## 2. 数据库覆盖矩阵

### 2.1 目标清单（P1 必做 / P2 扩展 / P3 可选）

| 数据库 | 类型 | 接入方式 | 隔离级别（SQL-92 / 自有） | 优先级 |
|---|---|---|---|---|
| MySQL / MariaDB | 关系型（现有） | JDBC（现有 collector） | RU/RC/RR/SER | 已集成 |
| PostgreSQL | 关系型（现有） | JDBC（现有 collector） | RC/RR/SER(SSI) | 已集成 |
| H2 | 嵌入式（现有） | JDBC（现有） | 全谱系 | 已集成（测试用） |
| Oracle | 关系型 | JDBC | RC/SER | P1 |
| SQL Server | 关系型 | JDBC (mssql-jdbc) | RC/RR/SER/SI | P1 |
| CockroachDB | 云 NewSQL | JDBC (postgres 兼容) | SER(SSI) | P1 |
| TiDB | 云 NewSQL | JDBC (mysql 兼容) | RC/RR/SER | P1 |
| YugabyteDB | 云 NewSQL | JDBC (postgres 兼容) | SER/SI/RR/RC | P1 |
| OceanBase | 分布式关系型 | JDBC (mysql/ob 兼容) | RC/RR/SER | P2 |
| openGauss / PolarDB / GaussDB | 国产关系型 | JDBC (postgres 兼容) | RC/RR/SER | P2 |
| Dgraph | 分布式图数据库 | HTTP/gRPC + 事务 API | SI（已知 SI bug 样本来源） | P2 |
| etcd | 分布式 KV | gRPC (MVCC 事务) | SER(linearizable) | P2 |
| Redis | KV | RESP + Lua/事务 | 弱隔离 | P2 |
| MongoDB | 文档库 | Java Driver（快照读/事务） | RC/SER（副本集事务） | P3 |
| TiKV / RocksDB | KV 引擎 | 客户端/Java | SI/SER | P3 |
| DynamoDB / CosmosDB（本地模拟或云端） | 云托管 KV | AWS SDK / SDK | SER/快照 | P3（凭据受限） |
| JuiceFS 元数据（审计场景，R25） | 文件系统元数据 KV | FUSE 客户端 | SER | P3 |

覆盖依据：R12 对 18 个流行 DBMS 的调查（PostgreSQL/OpenGauss/Yugabyte 2PL+MVCC+SSI；InnoDB/SQL Server/Aurora/PolarDB；TiDB Percolator；RocksDB；SQLite；FoundationDB；SingleStore；CockroachDB TO+MVCC；Spanner；Oracle/SAP HANA/OceanBase/NuoDB 等）与 R14 的五库评测（MySQL/PostgreSQL/MariaDB/CockroachDB/TiDB）——上表至少完整覆盖两者并集的主体。

### 2.2 接入分层

```
Executor（抽象）
├── JdbcExecutor        # 关系型通用实现（SQL 解析/参数绑定/结果集遍历）
│   ├── MySQLExecutor / MariaDBExecutor / OracleExecutor / SqlServerExecutor ...
│   └── PgCompatExecutor # PostgreSQL 兼容族：PG/CockroachDB/Yugabyte/openGauss/PolarDB
├── MySqlCompatExecutor # MySQL 兼容族：MySQL/TiDB/OceanBase
├── ProtocolExecutor    # 非 JDBC：Dgraph(HTTP)、etcd(gRPC)、Redis(RESP)、MongoDB(Driver)
└── MockExecutor        # MonkeyDB 式 mock storage（无真实 DBMS 时的应用测试，R16）
```

## 3. Executor 接口与能力声明

```java
public interface Executor {
    String NAME();                       // 注册名（沿用 Reflections + NAME 字段机制）
    ExecutorCapabilities capabilities(); // 能力声明
    void connect(ExecutorConfig cfg);    // 连接/建库建表（schema 来自 benchmark）
    Trace execute(WorkloadSpec spec, LoadPlan plan);  // 执行负载并产出 trace
    void close();
}

public record ExecutorCapabilities(
    boolean sql,              // 是否 SQL
    boolean predicateRead,    // 谓词读
    boolean rangeScan,        // 范围扫描
    boolean snapshotRead,     // 快照读（SI 语义前提）
    boolean abortInjection,   // 可注入 abort
    boolean coordinationHooks // 支持 barrier/延迟注入（定向异常执行）
) {}
```

执行语义（沿用并扩展现有 DBClient 的正确性做法）：

- 每会话一个连接；`setAutoCommit(false)` + `setTransactionIsolation(...)`（SQL-92 级别由 benchmark/实验配置指定）；
- 事务失败 rollback 重试（保留现有 maxRestartTimes=1000 策略，可配置）；
- **SQL 模型**：读操作遍历 ResultSet，记录主键、谓词、返回行集；写操作记录受影响行（`UPDATE ... WHERE pred` 须记录 where 子句与匹配行）；
- **KV 模型**：维持现有 `SELECT/UPDATE dbtest.variables` 翻译，但落 trace 而非直接回填 History（见 §5）。

## 4. SQL 插桩方案（谓词与行级记录）

两条路线并行，按 DBMS 能力选择：

### 4.1 辅助列插桩（IsoRel 路线，R14）

- 建表时每表加两列（如 `__rw_epoch`、`__txn_tag`）；
- 读写语句由平台重写（如 `SELECT ... WHERE pred` → 同时选中辅助列；`UPDATE` 前后打标），从而**精确记录每条 SQL 访问的行集**；
- 优点：不改 DBMS 内核、不依赖白盒接口；对 MySQL/PG/TiDB/CockroachDB 均适用（R14 已在五库验证）。

### 4.2 显式行集协议（DatabaseView 路线，R20 思路）

- 对无法改 SQL 的场景，用"事务 oracle"对照：把事务拆为独立语句在构造视图上执行，与实际执行比对；
- 作为插桩路线的补充校验（而非主路线）。

### 4.3 部分观测（partial-observation）

- R15 指出：黑盒只能观测"返回的行"，而"被检查但未返回的行"决定了 phantom 语义；平台 trace 记录 **predicate + returnedRows + (可选)checkedRows**（插桩路线可拿到 checkedRows；纯黑盒标注为 partial）。

## 5. Logger：统一 Trace 格式

### 5.1 Trace 记录项（每条操作）

```json
{
  "op_id": 10231,
  "session": 7, "txn": 512,
  "type": "read|write|commit|abort|begin",
  "sql": "SELECT c2 FROM t WHERE c1 > 7",     // SQL 模型（原文）
  "key": 3, "value": 42,                      // KV 模型
  "table": "t", "predicate": "c1 > 7",
  "rows_returned": [1, 5, 9],
  "rows_checked": [1, 2, 5, 9],
  "start_ts": 1710000000123, "end_ts": 1710000000145,   // 微秒级，R11/R12 区间法
  "result": "ok|error|empty",
  "pid": 12345                                  // 供运行时监控（现有 addPid 机制）
}
```

要点：

- **时间区间**（start/end）是黑盒推断执行序的基础（R12 Leopard 的区间法；R11 Pisco 的复现排序）；
- 兼容导出为 **Jepsen/elle EDN** 与 **text r/w** 两种既有格式（对接 Elle-cli 与现有 checker，见 06）；
- trace 与 History IR 的转换在 Synthesizer（05）中完成，Logger 本身保持"忠实记录、不做解释"。

### 5.2 采集开销控制

- 现有 `RuntimeInfoRecorder` 的 500ms 采样 + 子进程 pid 注册机制保留；
- SQL 插桩引入的额外往返需计量（R23 实测约 5% 吞吐影响，作为平台开销基线报告）。

## 6. 定向执行与协同原语（DBStorm 植入/MT 负载需要）

- **Barrier**：多会话在指定操作点对齐（保证冲突操作按计划相遇）；
- **Delay injection**：在操作前后注入可控延迟（扩大重叠区间，逼迫特定交错）；
- 接口由 `coordinationHooks` 能力声明；不支持协同的 DBMS 自动降级为纯随机执行（定向负载退化为概率触发）。

## 7. 与现状衔接

| 现有组件 | 处置 |
|---|---|
| `collector/Collector` + `DBClient` | 泛化为 `JdbcExecutor` 基类；`collect()` 拆为"执行 + trace 落盘"两步 |
| `collector/{mysql,postgresql,maria,H2}/*` | 改为薄封装（驱动加载 + 方言差异），公共逻辑上移 |
| `collector/postgresql_reproduce/*` | 作为复现专用 executor 保留（回归测试用） |
| `collector/IsolationLevel` | 统一到 `checker/IsolationLevel` + SQL-92 级别映射表 |
| `Config.DB_*` | 扩展为 ExecutorConfig（url/user/pwd/isolation + schema + 能力覆盖） |

## 8. 验收要点

1. 覆盖矩阵中 P1 数据库全部跑通 rw-register 与 TPC-C 两个 benchmark；
2. SQL trace 的行级记录在 MySQL/PG/TiDB/CockroachDB 上与 IsoRel 式插桩一致（抽测）；
3. 同一负载两次执行的 trace 除时间戳外结构一致（时间区间本身允许不同）；
4. 采集开销基线报告（含/不含插桩的吞吐对比）。
