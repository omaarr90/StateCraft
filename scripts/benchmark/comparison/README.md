# External Simulator Comparison

This directory contains the CPU-only comparison harness for the proposal claim that StateCraft is benchmarked against Qiskit Aer and QuEST.

The Gradle entry point is:

```bash
./gradlew externalComparisonBenchmark -PcomparisonProfile=smoke -PcomparisonRuns=1 --console=plain
```

To include the comparison table in the deliverable report:

```bash
./gradlew benchmarkValidationReport -PincludeExternalComparisons=true -PcomparisonProfile=full --console=plain
```

The harness writes normalized JSON artifacts under `build/reports/benchmark-validation/external-comparison/`:

- `statecraft-results.json`
- `qiskit-results.json`
- `quest-results.json`
- `comparison-results.json`
- `comparison-summary.md`

Qiskit Aer is pinned to `qiskit-aer==0.17.2`. QuEST is cloned and built from tag `v4.2.0` at commit `9d7618d7263e3bfba433b88cf1eac0647f08fa0a` with CPU-only CMake flags.
