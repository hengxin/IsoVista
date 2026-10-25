# IsoVista 项目总结

> 本文档基于对 `D:/code-repo/IsoVista` 仓库源码的实地阅读整理，涵盖项目定位、总体架构、目录结构、各模块职责、隔离级别支持矩阵、部署方式与扩展机制。
> 生成日期：2026-10-25。代码行数、文件数为快照时点的静态统计。

---

## 1. 项目定位

**IsoVista 是一个数据库隔离性黑盒检测系统（black-box isolation checking system）。** 它通过生成/采集数据库执行历史（history），用多种检查器验证历史是否违反指定隔离级别，发现违反时给出可视化的反例（dependency graph）。

核心能力（依据 README 与源码确认）：

| 维度 | 支持范围 |
|---|---|
| 隔离级别 | Read Committed (RC)、Repeatable Read (RR)、Read Atomicity (RA)、Transactional Causal Consistency (TCC)、Snapshot Isolation (SI)、Serializable (SER) |
| 数据库 | MySQL、PostgreSQL、MariaDB（另有 H2 用于开发测试，以及一份 postgresql_reproduce 复现代码） |
| 数据模型 | read-write register（`r/w(key, value, session id, txn id)` 文本格式）、list-append（Jepsen/Elle EDN 格式） |
| 检查器家族 | C4、PolySI、SMT_SI（PolySI+）、SMT_SER、Viper、ELLE_TCC |
| 形态 | Web 平台：Vue 3 前端 + FastAPI 后端 + Java 检测引擎（子进程方式被后端调用） |

学术工具渊源（README 声明）：PolySI、Viper、elle、elle-cli 均集成或参考了对应开源项目；`resource/` 下以 git submodule 引入 MonoSAT 与 Viper 源码。

---

## 2. 总体架构

```
┌────────────────────────────────────────────────────────────────────┐
│ Browser (Vue 3 SPA, :8080)                                         │
│  Configuration / RunList / RunView / BugList / BugView             │
│  AntV G6（依赖图） + ECharts（性能曲线） + Element Plus              │
└───────────────┬────────────────────────────────────────────────────┘
                │ HTTP (VITE_BACKEND_URL, 默认 http://127.0.0.1:8000/)
┌───────────────▼────────────────────────────────────────────────────┐
│ FastAPI 后端 (backend/main.py, :8000)                              │
│  - BugStore / RunStore：扫描 result/run_*/metadata.json            │
│  - run_queue + run_worker：串行执行 java -jar DBTest               │
│  - pydot 解析 conflict.dot → 节点/边 JSON（供 G6 渲染）              │
│  - 实时数据：日志、CPU/内存、runtime stage                          │
└───────────────┬────────────────────────────────────────────────────┘
                │ subprocess: java -jar DBTest-1.0-SNAPSHOT-shaded.jar config.properties
┌───────────────▼────────────────────────────────────────────────────┐
│ Java 引擎 (src/main/java, Maven artifactId=DBTest, Java 11)        │
│  Main → Generator(生成) → Collector(JDBC 采集) → Checker(验证)      │
│  ├─ C4 / C4-list      ：TAP + 图遍历（RC/RR/RA/TCC）                │
│  ├─ PolySI            ：图 + 约束求解（SI）                         │
│  ├─ SMT_SI / SMT_SER  ：SMT 编码 + JNI 本地库（SI / SER）            │
│  ├─ Viper             ：子进程调用 Python 脚本（SI）                 │
│  └─ ELLE_TCC          ：子进程调用 elle-cli jar（TCC）              │
│  本地库：libmonosat.so / libsmtchecker-{si,ser}.so / minisat / z3   │
└────────────────────────────────────────────────────────────────────┘
```

数据流：前端提交参数 → 后端写 `config.properties` 并入队 → Java 引擎生成 workload 或在真实数据库上采集历史 → 检查器验证 → 发现 bug 时输出 `result/run_<ts>/bug_N/{bug_hist.txt, conflict.dot}` 与 `metadata.json`、`*-profile.csv`、`runtime_info.csv`、`runtime_stage.csv` → 后端扫描 result 目录 → 前端列表/图表/依赖图展示。

---

## 3. 仓库目录结构

```
IsoVista/
├── src/main/java/               # Java 引擎源码（80 个 .java）
│   ├── Main.java                # CLI 入口（picocli），主编排流程
│   ├── config/Config.java       # 配置键名与默认值常量
│   ├── generator/               # 工作负载生成器（general / tpcc）
│   ├── collector/               # 历史采集器（mysql / postgresql / maria / H2 / postgresql_reproduce）
│   ├── history/                 # 执行历史数据模型 + loader/serializer（text / elle）
│   ├── checker/                 # 六个检查器家族（详见 §4.6）
│   ├── util/                    # Profiler、运行时记录、异常解释、工厂等
│   └── exceptions/              # NotImplementedException
├── src/main/resources/          # 6 个 .so 本地库 + logback.xml
├── src/test/java/               # JUnit 5 测试（4 个类）
├── backend/                     # FastAPI 后端（main.py + requirements.txt）
├── frontend/                    # Vue 3 + Vite 前端
├── resource/                    # 外部工具：elle-cli jar；monosat / Viper 为 git submodule
├── scripts/standalone/          # MySQL / PostgreSQL 的 docker-compose
├── docs/                        # use-cases / how-to 文档
├── history/                     # 预采集历史样本（3 个 .txt）
├── images/                      # README 配图（架构图、bug 可视化图）
├── ai/                          # 项目元信息目录（meta / docs / plan / brainstorm 等）
├── config.properties            # 引擎配置样例
├── Dockerfile                   # 多阶段构建（maven → ubuntu22.04 → 编译 monosat → 前端构建）
├── run.sh                       # 容器启动脚本（http-server + main.py）
├── pom.xml                      # Maven 构建（shade 插件打 fat jar）
└── .gitmodules                  # submodule：resource/monosat、resource/Viper
```

---

## 4. Java 引擎（核心）

### 4.1 入口与主流程（Main.java）

- 基于 picocli 的 CLI：`java -jar DBTest-1.0-SNAPSHOT-shaded.jar <config.properties>`。
- **插件化注册机制**：启动时用 Reflections 扫描 `collector`、`checker` 包，读取每个实现类的静态 `NAME` 字段，以小写为键建立注册表。`db.type`、`checker.isolation` 配置据此反查实现类（反射 `Properties` 构造器实例化）。
- 单轮执行（`runOneShot`）：
  1. **生成**：`GeneralGenerator(config).generate()` 生成初始 history；
  2. **采集**：实例化对应 Collector，在真实数据库上执行 history 中的事务，回填真实读值（失败事务最多重试 1000 次）；
  3. **验证**：对每个 (checker, isolation) 组合调用 `verify(history)`；
  4. **发现 bug**：创建 `./current/bug_N/`，写 `bug_hist.txt`（文本序列化）与 `conflict.dot`（依赖图），运行结束后随 `RuntimeDataSerializer` 归档到 `result/run_<ts>/`；
  5. **性能**：Profiler 按 tag 计时/记内存，生成 `*-profile.csv`。
- **批量扫描**：`workload.variable`（如 `session`）可配一组值（`[5,10,15,20]`），逐值跑完整轮次并汇总 profile，用于扩展性实验。
- 支持 `workload.skipgeneration=true`：跳过生成与采集，直接从 `history.path` 加载用户历史（离线分析 bug 场景）。

### 4.2 配置体系

- `Config.java` 定义全部配置键：`db.*`（url/username/password/isolation/type）、`workload.*`（type/history/session/transaction/operation/readproportion/key/distribution/variable/skipgeneration）、`history.*`（path/type）、`checker.isolation`、`profiler.enable`、`output.path`。
- `ConfigParser` 维护 **配置串 → (Checker NAME, IsolationLevel)** 映射表（共 9 条，见 §7），以及阶段缩写表（RC/RR/RA/TCC/SER/PolySI/Viper/PolySI+/ELLE_TCC）。
- `checker.isolation` 支持列表语法（`['SNAPSHOT_ISOLATION', 'SERIALIZABLE']`），一次运行可用多个检查器对照。
- 注意：数据库侧隔离级别（`db.isolation`，SQL-92 的 `TRANSACTION_*`）必须**不低于**检查器目标级别，否则会出现假阳性（docs/use-cases.md 明确提示）。

### 4.3 执行历史数据模型（history 包）

- `History`： sessions / transactions 两张 Map + abortedWrites + keySet；提供 `addInitSession()`（读写寄存器模型）与 `addInitSessionElle()`（Elle 模型，避开已占用的 0 号 id）。
- `Session` → `Transaction` → `Operation`（READ/WRITE，含 key/value/事务内序号）；`Operation` 的 equals/hashCode 仅按显式字段（Lombok `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`）。
- 序列化/加载走工厂模式：`HistoryLoaderFactory` / `HistorySerializerFactory`，目前 text 完整支持，elle 的 serializer 分支抛 `NotImplementedException`（Elle 序列化在各 checker 内部自行完成）。

### 4.4 工作负载生成器（generator 包）

- `GeneralGenerator`：按 session × transaction × operation 三维参数生成；读比例由 Bernoulli 分布采样；key 分布支持 `uniform`（UniformIntegerDistribution）、`zipf`（ZipfDistribution, s=1.5）、`hotspot`（自研 HotspotIntegerDistribution, 0.2 热度）；线程池并行生成，写值按 key 自增计数保证可复现的版本序。
- `TpccGenerator`：**空壳，未实现**（直接返回 null）。当前仓库实际只用 general 生成器。

### 4.5 采集器（collector 包）

- `Collector`（抽象类）：持有 JDBC 连接、key 数、目标隔离级别；抽象方法 `collect` / `createTable` / `createVariables` / `dropDatabase`。
- `DBClient`：把 history 操作翻译为 SQL——写：`UPDATE dbtest.variables SET val=? WHERE var=?`，读：`SELECT * FROM dbtest.variables WHERE var=?`；连接上设置 `setAutoCommit(false)` 与 `setTransactionIsolation(...)`；事务失败 rollback 后重试（上限 1000 次）。
- 实现：`mysql/MySQLCollector+MySQLClient`、`postgresql/PostgreSQLCollector+PostgreSQLClient`、`maria/MariaCollector+MariaClient`、`H2/H2Collector+H2Client`（H2 内存库，便于本地无依赖运行），以及 `postgresql_reproduce/`（一份并行的复现实现）。
- 约定：每个 Collector/Client 显式 `Class.forName` 加载 JDBC 驱动以防驱动冲突；扩展新数据库的方法见 `docs/how-to-check-a-new-database.md`。

### 4.6 检查器（checker 包）——重点模块

统一接口 `Checker<KeyType, ValType>`：`verify(history)` / `outputDotFile(path)` / `getProfileInfo()`。发现 bug 时 `Main` 调用 `outputDotFile` 产出 Graphviz DOT，供后端解析渲染。

| Checker（NAME） | 隔离级别 | 算法路线 | 外部依赖 |
|---|---|---|---|
| **C4 / C4-list** | RC / RR / RA / TCC | TAP（Transactional Anomaly Patterns）禁止模式表 + 事务依赖图（CO/CM 边）遍历 + 环检测 + 向量时钟 | 纯 Java（Guava、RoaringBitmap） |
| **PolySI** | SI | 已知边/反例约束图 + 剪枝（Pruning）+ 约束合并（coalescing）的图搜索 | 纯 Java |
| **SMT_SI**（VeriStrong_SI，前端标注 PolySI+） | SI | 历史序列化为文本 → JNI 调 `libsmtchecker-si.so`，内部 MonoSAT/minisat 图论编码求解；求解前先做内部一致性检查（`Utils.verifyInternalConsistency`） | JNI：`libsmtchecker-si.so`、`libmonosat.so`、minisat |
| **SMT_SER**（VeriStrong_SER） | SER | 同上路线，调 `libsmtchecker-ser.so` | JNI：`libsmtchecker-ser.so`、`libz3.so` 等 |
| **Viper** | SI | 历史写 JSON/YAML 配置 → 子进程 `python3 resource/Viper/src/main_allcases.py` → 解析 perf 文件（test_run/sat/constructing_graph/encoding/solving） | 子进程 + Python（resource/Viper submodule） |
| **ELLE_TCC** | TCC | 历史写 EDN → 子进程 `java -jar resource/elle-cli-0.1.7-standalone.jar --model list-append --consistency-models causal-cerone` | 子进程（elle-cli jar） |

要点：

- **C4**：静态表 `PROHIBITED_TAPS` 定义各级别禁止的异常模式（RC 含 ThinAirRead/AbortedRead/FutureRead/NotMyOwnWrite/NotMyLastWrite/IntermediateRead/CyclicCO/NonMono*；RR 加 NonRepeatableRead；RA 再加 FracturedRead*；TCC 再加 COConflictCM/ConflictCM）。流程：buildCO（含 SO 边、WR/RW 反例）→ checkCOTAP →（RR 可提前返回）→ syncClock/buildCM → CM 环检测 → checkCMTAP。Elle 模型下自动委托 `C4List`（值类型为 `ElleValue`）。
- **PolySI**：`SIVerifier.audit()` 主循环，`SISolver/SISolver2/SERSolver` 做约束求解，`Pruning` 剪枝，`KnownGraph/MatrixGraph` 管理依赖图；profile 细分 Construction/Encoding/Pruning/Solving 阶段。`AnomalyInterpreter.interpretSI` 把反例约束展开为完整违反场景（前端 "Expand" 功能的数据基础）。
- 跨语言 checker 的接入约定（docs/how-to-implement-a-new-checker.md）：实现 `Checker` 接口 + 静态 `NAME` 字段；子进程方式必须 `RuntimeInfoRecorder.addPid(pid)` 以便监控 CPU/内存；profile 阶段划分——solver 类四阶段（Construction/Encoding/Pruning/Solving），图搜索类两阶段（Construction/Traversal）。

### 4.7 工具与可观测性（util 包）

- `Profiler`：单例，tag 级计时与内存峰值，输出 `*-profile.csv`（按 workload.variable 汇总多轮）。
- `RuntimeInfoRecorder`：独立线程每 500ms 采样 CPU%/内存，写 `current/runtime_info.csv`；可注册子进程 pid 一并统计。
- `RuntimeStageRecorder`：记录阶段时间线（History Collection → * Verification → …），写 `runtime_stage.csv`，前端用于阶段色带。
- `RuntimeDataSerializer`：运行结束后把 `current/` 归档为 `result/run_<ts>/`（metadata.json、config.properties、output.log、profile CSV、runtime CSV、bug_*/）。
- `AnomalyInterpreter`：SI/SER 反例的结构化解释（已知边 vs 推导边、环、可展开子图），生成带 `ops`/`relate_to`/`in_cycle` 属性的 DOT。
- `utils`：进程内存查询（JNA/`OperatingSystemMXBean`）等辅助函数。

---

## 5. 前端（frontend/）

- 技术栈：Vue 3.3 + Vite 5 + vue-router（hash 模式）+ Element Plus 2.4 + AntV G6 4.8（依赖图）+ ECharts 5.4（曲线）+ axios。
- 路由（`src/router/index.js`）：`/configuration`（配置页，默认页）、`/runs`、`/run_view/:id`、`/bugs`、`/bug_view/:id`。
- `Configuration.vue`：完整参数表单——数据库（MySQL/PostgreSQL/MariaDB × SQL-92 隔离级别）、workload（history 数、session/transaction/operation、读比例、key 数、分布）、历史输入（Skip Generator + 上传文件或 text/elle 路径）、检查器（9 个隔离配置项，含前端显示名映射，如 "Snapshot Isolation(Viper)"）、profiler 开关；含输入校验与保存选项。
- `RunView.vue`：运行时/历史运行的 CPU-内存曲线（ECharts）、runtime stage 时间线、分 checker 的 time/memory/stage_times 图表；运行中轮询 `/current_*` 端点实时刷新。
- `BugView.vue`：G6 渲染 `conflict.dot` 解析结果——节点（事务及其 ops）、边（WR/SO/RW 等，区分已知边与推导边）、`in_cycle` 高亮、右键 Expand 展开推导链。
- `api.js`：封装 20 个后端端点（run/stop、bug_list、view、download、tag、profile、runtime_info/stage 等）。
- `.env`：`VITE_BACKEND_URL=http://127.0.0.1:8000/`，容器部署时由 `run.sh` 对构建产物做字符串替换注入。

---

## 6. 后端（backend/main.py）

- FastAPI + uvicorn（0.105/0.24，监听 0.0.0.0:8000），CORS 全开；依赖 pydot（DOT 解析）、pandas（CSV）、python-multipart（上传）。
- **存储模型**：`BugStore` / `RunStore` 扫描 `result/` 下 `run_*` 目录，读 `metadata.json` 汇总；bug 按时间戳排序编号。
- **任务执行**：`POST /run` 把前端参数（键名下划线转点号）写成 `config.properties` 入队；`run_worker` 守护线程串行 `subprocess.Popen(["java","-jar",...])`；`PUT /stop` 终止进程并清理 `current/`。
- **可视化数据**：`GET /view/{bug_id}` 用 pydot 解析 DOT，提取节点（id/label/ops/relate_to/in_cycle）与边（source/target/label/relate_to/style/in_cycle），边的 key 标注规整为 `WR(k378)` 形式。
- **实时端点**：`/current_log`（从 `N of M` 正则算进度百分比）、`/current_runtime_stage`、`/current_runtime_info`、`/current_profile`、`/current_run_id`。
- **其他**：`/upload/` 保存用户 history 为 `user_history.txt`；`/download/{bug_id}`、`/download_run/{run_id}` 打 zip（文件名固定 `download.zip`）；`/bug/tag` 给 bug 打标签并回写 metadata.json。
- 代码观察（非阻塞问题）：`bug_store`/`run_store` 为进程级单例且扫描无锁；zip 文件名固定存在并发覆盖风险；`Run.__init__` 中 `self.profile_path` 在无 profile 时未定义（依赖 `getattr` 前的分支判断）。整体作为单用户研究平台够用。

---

## 7. 隔离级别 / 检查器支持矩阵

来自 `ConfigParser.ConfigToCheckerIsolationMap`（源码事实）：

| 配置串（checker.isolation） | Checker NAME | IsolationLevel | 前端显示名 |
|---|---|---|---|
| READ_COMMITTED | C4 | READ_COMMITTED | Read Committed |
| REPEATABLE_READ | C4 | REPEATABLE_READ | Repeatable Read |
| READ_ATOMICITY | C4 | READ_ATOMICITY | Read Atomicity |
| TRANSACTIONAL_CAUSAL_CONSISTENCY | C4 | CAUSAL_CONSISTENCY | Transactional Causal Consistency |
| SNAPSHOT_ISOLATION | PolySI | SNAPSHOT_ISOLATION | Snapshot Isolation(PolySI) |
| SERIALIZABLE | SMT_SER | SERIALIZABLE | Serializable(VeriStrong) |
| VIPER_SNAPSHOT_ISOLATION | Viper | SNAPSHOT_ISOLATION | Snapshot Isolation(Viper) |
| POLYSI+_SNAPSHOT_ISOLATION | SMT_SI | SNAPSHOT_ISOLATION | Snapshot Isolation(VeriStrong) |
| ELLE_TRANSACTIONAL_CAUSAL_CONSISTENCY | ELLE_TCC | SNAPSHOT_ISOLATION | Transactional Causal Consistency(Elle) |

数据库侧（collector）：MYSQL、POSTGRES、MARIA（+ H2、postgresql_reproduce 变体）。

---

## 8. 部署与运行

- **Docker（推荐）**：多阶段构建——`maven:3.8.4-openjdk-11-slim` 打 shaded jar → `ubuntu:22.04` 装 python3/node 20/JDK/cmake，复制 `.so` 到 `/lib/x86_64-linux-gnu/` 并做 monosat 软链，pip 装 Viper 依赖，源码编译 MonoSAT（`-DJAVA=ON -DPYTHON=ON`），`npm run build` 前端，`http-server` 托管 dist。暴露 8080（前端）/8000（后端），`CMD ./run.sh`。官方镜像：`ghcr.io/hengxin/isovista:main`。
- **本地数据库**：`scripts/standalone/{mysql,postgres}/docker-compose.yml` 一键起库（dbtest 库、root_pwd）。
- **配置注入**：`VITE_BACKEND_URL` 环境变量，`run.sh` 对 `dist/assets/index-*.js` 做 sed 替换。
- **测试**：`src/test/java` 有 JUnit 5 用例（HistoryLoaderSerializerTest、TestLoader、TestSERVerifier、TestSIVerifier），Docker 构建阶段 `mvn -DskipTests package` 跳过。

---

## 9. 扩展机制

1. **新检查器**：实现 `Checker` 接口 + 静态 `NAME` 字段，放入 `checker` 包即被反射自动注册；异语言 checker 可用 JNI（参考 `LibSMTCheckerSI/SER`）或子进程（参考 Viper/Elle，需 `RuntimeInfoRecorder.addPid`）。
2. **新数据库**：`pom.xml` 加 JDBC 驱动 + `collector` 包新建 `XxxCollector`/`XxxClient`（显式加载驱动，实现建表/初始化/采集/清理四方法）。
3. **新隔离级别判定**：在 `ConfigParser` 映射表与 checker 内部级别表中登记。

---

## 10. 已知限制

README 声明：

- 前后端多条连接并发传输，网络拥塞时图表/表格可能延迟或不及时。
- workload 参数极端值（如 session=10000）可能耗尽系统资源，已对部分参数加限制。

代码快照观察（供后续改进参考）：

- `TpccGenerator` 未实现；`HistorySerializerFactory` 的 elle 分支未实现（各 checker 内部自行序列化）。
- 后端为单工作者队列 + 无锁内存 store，属单用户研究平台定位，不建议直接多租户化。
- `resource/monosat`、`resource/Viper` 为 git submodule，全新 clone 需 `git submodule update --init --recursive`，否则 Docker 构建与 Viper/SMT 检查器不可用。

---

## 11. 关键文件速查

| 目的 | 文件 |
|---|---|
| 主流程编排 | `src/main/java/Main.java` |
| 配置键 / 映射表 | `src/main/java/config/Config.java`、`src/main/java/util/ConfigParser.java` |
| 数据模型 | `src/main/java/history/{History,Session,Transaction,Operation}.java` |
| 负载生成 | `src/main/java/generator/general/GeneralGenerator.java` |
| 采集执行 | `src/main/java/collector/{Collector,DBClient}.java` |
| 检查器接口 | `src/main/java/checker/Checker.java` |
| SI 求解 | `src/main/java/checker/PolySI/verifier/SIVerifier.java` |
| SMT 接入 | `src/main/java/checker/VeriStrong_{SI,SER}/LibSMTChecker*.java` |
| 反例解释 | `src/main/java/util/AnomalyInterpreter.java` |
| 后端服务 | `backend/main.py` |
| 前端配置页 | `frontend/src/components/Configuration.vue` |
| 部署 | `Dockerfile`、`run.sh`、`scripts/standalone/*/docker-compose.yml` |
