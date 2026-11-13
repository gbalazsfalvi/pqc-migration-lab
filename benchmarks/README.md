# PQC benchmarks

Independent OpenJDK JMH experiments around the unchanged laboratory adapters. Requires JDK 25 and the repository's Gradle wrapper; no Spring application, Node process, database or external service is needed.

From the repository root:

```powershell
.\lab.ps1 benchmark -BenchmarkProfile smoke
.\lab.ps1 benchmark -BenchmarkProfile quick
.\lab.ps1 benchmark -BenchmarkProfile standard
```

Portable Gradle equivalents are `benchmarkSmoke`, `benchmarkQuick` and `benchmark`.

The standard matrix contains 57 cases: three size-independent key-generation cases, then six operations × three signature profiles × three decoded payload sizes. Every result retains its method identity. Smoke results check the harness and must not support comparative performance claims.

Each run writes a unique directory under `build/reports/benchmarks/` with a Markdown report, versioned summary, untouched JMH JSON/log and completion manifest.

For an installable distribution:

```powershell
.\gradlew.bat :benchmarks:installDist
.\benchmarks\build\install\benchmarks\bin\benchmarks.bat --help
```

Use JDK 25 when launching the distribution. The optional `--output` argument selects a parent directory; every invocation creates a new child rather than replacing earlier evidence.

See [the full method](../docs/milestone-04a.md) before interpreting the numbers.
