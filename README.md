# IsoVista

IsoVista is a black-box isolation checking system for databases that supports various isolation levels, ensures accurate and efficient bug detection, provides clear bug visualizations, and offers benchmarking for performance analysis. 
It is designed to comprehensively test and verify the isolation guarantees of databases, helping developers to find and fix issues more effectively.

View the [use cases](docs/use-cases.md) and try IsoVista [here](http://43.129.197.141:8080).

## Architecture

![arch](images/arch.png)

## Key Features

- **Support for Multiple Isolation Levels**: IsoVista is capable of checking a wide spectrum of isolation levels, including read committed (RC), repeatable read (RR), read atomicity (RA), transactional causal consistency (TCC), snapshot isolation (SI) and serializability (SER). 

- **Complete and Efficient Checking**: IsoVista is built upon sound and complete formal characterizations of all supported isolation levels, which ensures the absence of false positives and the detection of all pertinent bugs in DBMS execution histories. 
IsoVista enhances checking performance through optimized SMT encoding for stronger isolation levels and efficient data structures for managing transactional dependency graphs in weaker levels.

- **Informative Bug Visualization**: Upon detecting an isolation bug, IsoVista provides detailed visualizations of the bug scenario, including the core transactions involved and their dependencies. 
This feature aids developers in understanding the nature of the bug and facilitates the debugging process.

- **Benchmarking and Performance Profiling**: IsoVista includes tools for monitoring runtime information like CPU and memory utilization, and for profiling checking statistics such as time and memory usage across different workloads. 
These capabilities help developers adjust their checking strategies and explore new optimizations for their tools.

## Support

### Databases

- MySQL
- PostgreSQL
- MariaDB

### Isolation Levels

- Read Committed
- Repeatable Read
- Read Atomicity
- Transactional Causal Consistency
- Snapshot Isolation
- Serializable

### Models

#### read-write register

`r/w(key, value, session id, txn id)`

Example of history:

```
w(1,0,0,0)
w(2,0,0,0)
w(1,1,1,1)
r(2,0,1,2)
```

#### list-append

Refer to [jepsen history](https://github.com/jepsen-io/history).

Example of history:

```
{:index 1, :type :invoke, :process 0, :value [ [ :append 1 1 ] [ :append 2 1 ] ]}
{:index 2, :type :ok, :process 0, :value [ [ :append 1 1 ] [ :append 2 1 ] ]}
{:index 3, :type :invoke, :process 1, :value [ [ :r 1 nil ] ]}
{:index 4, :type :ok, :process 1, :value [ [ :r 1 [1] ] ]}
```

## How to deploy

### Docker

Pull Docker image and deploy.

```bash
docker pull ghcr.io/hengxin/IsoVista:main
docker run --name IsoVista -p 8080:8080 -p 8000:8000 --rm -d ghcr.io/hengxin/IsoVista:main
```

Then, use the browser to access the address http://127.0.0.1:8080 to use.

### Build on your own(not recommended)

Refer to the [Dockerfile](Dockerfile).

## How to extend IsoVista

- [How to check a new database](docs/how-to-check-a-new-database.md)
- [How to implement a new checker](docs/how-to-implement-a-new-checker.md)

## Known limitations

- IsoVista utilizes multiple network connections to transmit data between the frontend and backend. In cases of network congestion, delays may occur, causing charts or tables to be displayed slowly or not promptly.
- If the workload parameter is set very high (e.g., #session=10000), it might exhaust system resources. Therefore, IsoVista has imposed limits on the size of some parameters.

## Related GitHub Repository

- [PolySI](https://github.com/amnore/PolySI)
- [Viper](https://github.com/Khoury-srg/Viper)
- [elle](https://github.com/jepsen-io/elle)
- [elle-cli](https://github.com/ligurio/elle-cli)
