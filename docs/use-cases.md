# Use Cases

Try IsoVista [here](http://43.129.197.141:8080).

## Test database isolation levels

Enter the JDBC URL for the database connection along with some other parameters, then click `Start` to begin the test. 

We have pre-deployed three databases on our server that can be used for testing (username is 'root' and password is 'dbtest_pwd'):

- MySQL: jdbc:mysql://172.17.0.1:3306/
- PostgreSQL: jdbc:postgresql://172.17.0.5:5432/
- MariaDB: jdbc:mariadb://172.17.0.6:3306/

**Note that**:

1. The JDBC URLs provided here are local addresses, therefore, these three JDBC URLs can only be used in our deployment.
2. Please set the database's isolation level (SQL-92 standard) higher than the checker's isolation level, otherwise there will be false positives.

## Visually analyse isolation bugs

In the [`history/bugs`](/history/bugs) directory, there are some pre-collected history files that can be analyzed by IsoVista. The file [`dgraph_si.txt`](/history/bugs/dgraph_si.txt) contains the history of SI bugs we previously collected on Dgraph. The following will demonstrate how to use IsoVista to analyze isolation bugs using this history as an example.

In the Workload Setting, select `Skip Generator`, select `Read-Write Registers(Text)` and upload this file, and then in the Checker Setting, select `Snapshot Isolation (PolySI)` and click `Start`. After the checking is complete, you can view the bugs found in the `Bug Table`. Click the `View` button to see the counter example:

![dgraph_si_bug_collapsed](/images/dgraph_si_bug_collapsed.png)

This bug manifests as a cycle. There are three types of edges in the cycle: WR, SO, and RW. Among them, WR and SO are known edges, while RW is a derived edge. IsoVista uses different colors to distinguish these edges.

Right-click on `RW(k378)` and then click `Expand` to reconstruct a comprehensive violating scenario to see how this edge is inferred:

![dgraph_si_bug_expanded](/images/dgraph_si_bug_expanded.png)

We can see that the graph expand from the original cycle to include some other edges and nodes. Form the expanded graph, We can infer that the `RW(k378)` edge from `Txn(id429)` to `Txn(id400)` is derived from the `WW(k378)` edge from `Txn(id234)` to `Txn(id400)` and the `WR(k378)` edge from `Txn(234)` to `Txn(id429)`; the other possibility of the WW dependency between `Txn(id400)` and `Txn(id234)`, depicted as a orange dashed arrow from `Txn(id400)` to `Txn(id234)` is pruned due to the presence of the SO and WR edges from `Txn(id234)` to `Txn(id400)`.

## Benchmark isolation checkers

Select `Snapshot Isolation(PolySI)`, `Snaphshot Isolation(Viper)` and `Snapshot Isolation(PolySI+)` in Checker Setting to benchmark the three SI checkers.

![runtime_info](/images/runtime_info.png)
