# How to Implement a New Checker

Custom checkers should implement the `Checker` interface.
You should add a static String field "NAME" in your custom checker class for IsoVista to identify it.

The following methods should be implemented:

## `verify`

The detection code for the custom checker is implemented in this method. 
If the given history does not have isolation violation, it returns true; otherwise, it returns false.


If this custom checker is written in another language instead of Java, it can use JNI or start a subprocess to run the custom checker.
If a subprocess is used to invoke the custom checker, after starting this subprocess, use `RuntimeInfoRecorder.addPid(process.pid());` so that IsoVista can monitor the CPU and memory usage of this subprocess.

## `outputDotFile`

After checking, if an isolation bug is discovered, you can use IsoVista to visualize this bug. 
IsoVista accepts a DOT format string and includes built-in interpretation tools for SI and SER isolation bugs.

For example:
```java
AnomalyInterpreter.interpretSI(history);
```

## `getProfileInfo`

IsoVista performs a decomposition analysis that breaks down the checking time into stages.
This method needs to return a `Map<String, Long>` to represent the duration (in milliseconds) of checking time at different stages by the custom checker, for display by IsoVista.
For solver-based checkers, it can be divided into four stages: Construction, Encoding, Pruning, and Solving. For graph-based search checkers, it can be divided into two stages: Construction and Traversal.

For details, please refer to `src/main/java/checker`.