# 项目简介
DBTest 是一个用于测试数据库事务隔离级别的平台。通过集成多种隔离级别的测试方法（checker），方便快捷地对各种数据库进行测试。当然，也可以扩展新的测试方法，来进行测试方法本身的测试。总而言之，DBTest 具有可扩展性强、易用性高等特点。

# 构建方式
要编译 DBTest，需要具备以下环境：
- JDK 11
- Maven
- Git

执行以下命令可编译 DBTest 的源代码：
```shell
git clone https://github.com/hengxin/db-testing-platform
cd db-testing-platform
mvn clean package
```
然后可以在 `target` 目录下找到编译好的 `jar` 文件。

# 运行
## 参数设置
由于运行参数较多，所有参数均放在 `config.properties` 之中。以下是各个运行参数的详细解释：
**运行模式**
```
mode= # 运行模式
```
**数据库连接**
```
db.url= # 数据库URL  
db.username= # 数据库用户名  
db.password= # 数据库密码  
db.isolation= # 数据库隔离级别  
db.type= # 数据库类型
```
**通用工作负载配置**
#TODO 
```
workload.type= # 工作负载类型
workload.history= # 工作负载历史事务数  
workload.session= # 模拟会话数  
workload.transaction= # 每个会话的事务数  
workload.operation= # 每个事务的操作数  
workload.readproportion= # 读操作比例  
workload.key= # 
workload.distribution= # 事务访问数据的分布类型
```
**特定工作负载配置**
```
# 检查器配置
checker.type= # 检查器类型  
checker.isolation= # 检查隔离级别

# 数据配置
profiler.enable= # 是否启用性能测试
```

## 运行方法
编译完成后，可以用以下命令运行 DBTest：
```shell
java -jar ./target/DBTest-1.0-SNAPSHOT-shaded.jar config.properties
```

如果要测试 `Checker` 性能，可以使用以下命令：
```shell
bash test_performance.bash
```
结果会输出在 `result/` 目录下，得到一个类似的 csv 文件：

| sessions | duration (ms) | memory (MB) |
| -------- | ------------- | ----------- |
| 2        | 7             | 34.2        |
| 5        | 8             | 41.2        |
| 10       | 11            | 52.2        |
| 20       | 30            | 71.7        |
| 30       | 38            | 93.2        |

# 平台扩展
## History
`History` 类表示一个操作历史记录，包含了一系列的会话和事务。每个会话包含多个事务，每个事务包含多个操作。此外，还包含了一些被中止的写操作。`History`的相关实现会使用到`Operation`、`Session`以及`Transaction`类，并且通过`loader`加载，使用`serializer`进行记录。

**字段说明**
- `Map<Long, Session<KeyType, ValType>> sessions`：存储所有会话的映射，其键是会话的 ID，值是对应的 `Session` 对象。
- `Map<Long, Transaction<KeyType, ValType>> transactions`：存储所有事务的映射，其键是事务的 ID，值是对应的 `Transaction` 对象。
- `Set<Pair<KeyType, ValType>> abortedWrites`：存储所有被中止的写操作。每个中止的写操作表示为一个键值对。

**方法**
- `Session<KeyType, ValType> getSession(long id)`：根据给定的 ID 返回对应的会话。
- `Transaction<KeyType, ValType> getTransaction(long id)`：根据给定的 ID 返回对应的事务。
- `Session<KeyType, ValType> addSession(long id)`：添加一个新的会话并返回它。新会话的 ID 是给定的参数。
- `Transaction<KeyType, ValType> addTransaction(Session<KeyType, ValType> session, long id)`：在给定的会话中添加一个新的事务并返回它。新事务的 ID 是给定的参数。
- `Operation<KeyType, ValType> addOperation(Transaction<KeyType, ValType> transaction, Operation.Type type, KeyType variable, ValType value)`：在给定的事务中添加一个新的操作并返回它。新操作的类型、变量和值是给定的参数。
- `void addAbortedWrite(KeyType variable, ValType value)`：添加一个新的被中止的写操作。被中止的写操作的变量和值是给定的参数。
- `List<Operation<KeyType, ValType>> getOperations()`：返回所有操作的列表。
- `List<Transaction<KeyType, ValType>> getFlatTransactions()`：返回所有事务的列表。这个列表是按照会话和事务的顺序排列的。

## Checker
#TODO 
`Checker` 是一个Java接口，它定义了一个验证方法 `verify()`，该方法接受一个 `History` 对象，并返回一个布尔值。`History` 对象通常代表特定类型的键值对（`KeyType` 和 `ValType`）的历史记录。

### C4 实现
`C4`是`Checker`接口的一个实现，它专门用于验证数据库运行产生的`History`对象。

## 数据库
### 当前支持种类
目前平台已经支持 MySQL、Postgres 以及 H2 数据库的测试。并且能够进行部署+启动的脚本化构建方式。

### 扩展数据库类型
有关数据库实现方面的代码在 `src/main/java/collector` 目录下。为了支持更多种类的数据库，需要完成两部分代码：`Client` 和 `Collector`。
#### Client
DBClient 用于定义数据库客户端的通用逻辑框架。它包含了数据库连接信息的属性,并在构造函数中初始化数据库连接。定义了一个抽象方法execSession(),由子类实现,用于执行给定的会话操作。

**字段说明**
- `String url`：数据库的URL。
- `String username`：用于数据库连接的用户名。
- `String password`：用于数据库连接的密码。
- `Connection connection`：数据库连接对象，由 `java.sql.Connection` 类型实例化。
- `int maxRestartTimes`：最大重启次数，默认值为1000。

**构造函数**
`DBClient(String url, String username, String password)`：构造函数接收数据库的URL、用户名和密码作为参数，并使用这些信息建立数据库连接。如果连接失败，将抛出异常。

**抽象方法**
`void execSession(Session<Long, Long> session)`：一个抽象方法，用于执行特定的数据库会话。这个方法的实现应该在 `DBClient` 的具体子类中提供。

详细实现方法可参考`mysql/MySQLClient`或`postgresql/PostgreSQLClient`
#### Collector
`Collector` 用于定义收集历史记录的模版方法。提供了创建表、创建变量、删除数据库等抽象方法, 由子类实现具体的数据库操作。

**字段说明**
- `String url`：数据库的URL。
- `String username`：用于数据库连接的用户名。
- `String password`：用于数据库连接的密码。
- `Connection connection`：数据库连接对象，由 `java.sql.Connection` 类型实例化。
- `long nKey`：工作负载的键值。

**构造函数**
`Collector(Properties config)`：构造函数接收一个 `Properties` 对象作为参数，该对象包含数据库连接的详细信息和工作负载的键值。这些信息包含在 `Properties` 对象的属性中，属性的键分别是 `"db.url"`、`"db.username"`、`"db.password"` 和 `"workload.key"`。构造函数使用这些信息建立数据库连接并设置工作负载的键值。

**抽象方法**
- `History<KeyType, ValType> collect(History<KeyType, ValType> history)`：一个抽象方法，用于收集数据库中的历史记录。这个方法的实现应该在 `Collector` 类的具体子类中提供。
- `void createTable()`：一个抽象方法，用于在数据库中创建表。这个方法的实现应该在 `Collector` 类的具体子类中提供。
- `void createVariables(long nKey)`：一个抽象方法，用于创建变量。这个方法的实现应该在 `Collector` 类的具体子类中提供。
- `void dropDatabase()`：一个抽象方法，用于删除数据库。这个方法的实现应该在 `Collector` 类的具体子类中提供。

详细实现方法可参考`mysql/MySQLCollector`或`postgresql/PostgreSQLCollector`

## 扩展历史生成
需要在`generator/`下完成对应历史的生成，可参考以下`GeneralGenerator`实现

**字段说明**
- `long session, transaction, operation, key`：从配置文件中获取的会话数量、事务数量、操作数量和键值。
- `double readProportion`：读取比例，从配置文件中获取。
- `IntegerDistribution keyDistribution`：键值的分布方式，可以是均匀分布 (`UniformIntegerDistribution`)、Zipf 分布 (`ZipfDistribution`) 或热点分布 (`HotspotIntegerDistribution`)。
- `BernoulliDistribution readProbability`：读取概率，由读取比例计算得出。

**构造函数**
`GeneralGenerator(Properties config)`：构造函数接收一个 `Properties` 对象作为参数，该对象包含生成历史记录所需的各种配置参数。这些参数包括会话数量(`workload.session`)、事务数量(`workload.transaction`)、操作数量(`workload.operation`)、读取比例(`workload.readproportion`)、键值(`workload.key`)和键值分布方式(`workload.distribution`)。

**方法**
`History<Long, Long> generate()`：根据指定的参数生成历史记录。首先，创建一个空的 `History` 对象和一个用于计数的 `ConcurrentHashMap`。然后，为每个会话创建一个任务，每个任务生成指定数量的事务，每个事务生成指定数量的操作。操作类型可以是读取或写入，根据读取概率随机确定。对于写入操作，还会更新计数器。所有任务在一个固定大小的线程池中并发执行。最后，返回生成的历史记录。


