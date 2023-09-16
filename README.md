# DBTest
Platform for Database Isolation Level Testing

# How to build

To compile DBTest, the following environments are required:

- JDK 11
- Maven
- Git

Execute the following commands to compile the DBTest source code:

```
git clone https://github.com/hengxin/db-testing-platform
cd db-testing-platform
mvn clean package
```

Then you can find the compiled shaded jar file in the target directory.

# Usage

After the compilation, you can use the following command to run DBTest:
```
java -jar ./target/DBTest-1.0-SNAPSHOT-shaded.jar config.properties
```

To test the performance for checkers, use the following command and the output file will be under result/:
```
bash test_performance.bash
```
