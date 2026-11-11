# API examples

These examples come from a class of the project. Each build compiles this class, so the examples follow the API. Read the [API guide](api.md) first, and use the [API reference](api-reference.md) for all the methods.

The examples use the identifiers of a mod `mymod`, and these constants:

```java
--8<-- "src/gametest/java/io/github/nicolasgoyon/cartofold/gametest/ApiExamples.java:constants"
```

## On the server

Call the methods of `CartofoldLayers` on the server thread, after the start of the server.

### A layer for all the players

The players that join later also get this layer.

```java
--8<-- "src/gametest/java/io/github/nicolasgoyon/cartofold/gametest/ApiExamples.java:territories"
```

### A layer for one player

The player keeps this layer until the player leaves the server. Send it again when the player joins again.

```java
--8<-- "src/gametest/java/io/github/nicolasgoyon/cartofold/gametest/ApiExamples.java:player-layer"
```

### Clicks on the server

```java
--8<-- "src/gametest/java/io/github/nicolasgoyon/cartofold/gametest/ApiExamples.java:clicks"
```

### Markers that move

Send the layer once with `setForAll`, then send only the changes.

```java
--8<-- "src/gametest/java/io/github/nicolasgoyon/cartofold/gametest/ApiExamples.java:updates"
```

### Blocks that mark the survey maps

```java
--8<-- "src/gametest/java/io/github/nicolasgoyon/cartofold/gametest/ApiExamples.java:paper-source"
```

## On the client

Keep these calls in classes that only the client loads.

### A layer that the client draws

```java
--8<-- "src/gametest/java/io/github/nicolasgoyon/cartofold/gametest/ApiExamples.java:client-layer"
```

### A screen with a map

This journal selects a marker with a click, on the client. It keeps its view, shows a full area and opens the map of the world.

```java
--8<-- "src/gametest/java/io/github/nicolasgoyon/cartofold/gametest/ApiExamples.java:journal"
```

### A map for an operator

This frame shows places that the player did not explore. The layers with `onlyExplored = false` show on the background.

```java
--8<-- "src/gametest/java/io/github/nicolasgoyon/cartofold/gametest/ApiExamples.java:admin-frame"
```
