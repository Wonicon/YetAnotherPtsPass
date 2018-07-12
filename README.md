## General

`PointerAnalysis`: the entrance.

`WholeProgramTransformer`: Iterates over methods and prepares SSA. Todo:

1. Buffer methods and iterate until no updates.
2. Maintain functions/callsites argument pointsToSet (pts) environment.

`PtsAnalysis`: Forward flow analysis on SSA of one method.

1. Receive and update functions/callsites argument pts environment.
2. Neccessary data flow operations.

Advanced topics:

1. Field sensitivity.
2. Context sensitivity.
3. Path sensitivity (detecting constant condition and exclusive condition).

## Build

```
mvn package assembly:single
java -jar target/analyzer-1.0-jar-with-dependencies.jar code test.Hello
```

When using Intellij Idea, set `pts.PointerAnalysis` as the main class and `code test.Hello` as the cmd line arguments.

To update test inputs:

```
cd code
javac `find . -name "*.java"`
``````
