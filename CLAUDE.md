# Claude Code Instructions

## Code Style

### Imports — no inline FQCNs
Always use simple class names with explicit `import` statements. Never use fully qualified class names (FQCNs) inline in code.

**Wrong:**
```java
private final tradingbot.agent.application.PerformanceTrackingService performanceService;
java.time.Instant.now()
java.util.Arrays.stream(values)
```

**Right:**
```java
import tradingbot.agent.application.PerformanceTrackingService;
import java.time.Instant;
import java.util.Arrays;

private final PerformanceTrackingService performanceService;
Instant.now()
Arrays.stream(values)
```

**Exception:** MapStruct `defaultExpression = "java(...)"` strings must use FQCNs because they are processed by the annotation processor, not the Java compiler.
