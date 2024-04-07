# How to Check a New Database

## JDBC Dependency
Add the JDBC driver dependency for the new database to `pom.xml`.

For example, the JDBC driver dependency for the MySQL is

```
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

## Collector

Then, create a new package in the `src/main/java/collector` directory, and create two classes in this package to extend `Collector` and `DBClient`.

Custom collector should have a String field `NAME` to represent the category of this database. 
IsoVista uses this field in `config.properties` to select the database for the history collector.

```java
public static final String NAME = "MYSQL";
```

The collector needs to explicitly load the JDBC driver in the code to prevent conflicts between drivers.
```java
static {
    try {
        Class.forName("com.mysql.cj.jdbc.Driver");
    } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
    }
}
```

There are 4 methods the custom collector need to implement:

- `collect`: Use `DBClient` and a thread pool to simulate multiple database client accesses to the database.
- `crateTable`: Use JDBC to create a database table for subsequent history collection.
- `createVariables`: Pre-insert the initial values of variables into the created database table.
- `dropDatabase`: After completing the history collection, delete the database table.

For details, please refer to `src/main/java/collector/mysql/MySQLClient.java`.

## DBClient

`DBClient` uses JDBC to connect to the database and converts a read-write(or list-append) operation into SQL and executes it.

For example, the operation "write value 2 to variable 1" in SQL is
```SQL
UPDATE [table] SET val=2 WHERE var=1;
```

The operation "read value 4 from variable 3"

```SQL
SELECT val FROM dbtest.variables WHERE var=3;
```
