# Benchmark and Validation Report

## Run Metadata
- Run timestamp (local): 2026-04-27 23:02:09 +0300
- Run timestamp (UTC): 2026-04-27 20:02:09 UTC
- Timezone: +03 (offset +0300)
- Git branch: main
- Git commit: 17ae0e38810d04f0e26e55b22cb28f75080b0a0b
- OS: macOS-26.3-arm64-arm-64bit-Mach-O
- CPU: Apple M4 Max
- CPU topology: 14 cores / 14 logical processors
- RAM: 36.000 GiB
- Java: openjdk version "25.0.1" 2025-10-21
- Gradle: 9.0.0

## Benchmark Configuration
- Gradle task: `./gradlew benchmarkValidationReport --console=plain -PbenchmarkRuns=1 -PbenchmarkOutputDir=build/reports/benchmark-validation -PbenchmarkReportPath=docs/deliverables/benchmark-validation-report.md -PincludeExternalComparisons=true -PcomparisonProfile=smoke -PcomparisonRuns=1 -PcomparisonOutputDir=build/reports/benchmark-validation/external-comparison`
- Iterations: 1
- Benchmark JVM command: `java --enable-preview --add-modules jdk.incubator.vector --class-path engines/build/classes/java/main:engines/build/classes/java/test:core/build/classes/java/main com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark`
- Classpath: `engines/build/classes/java/main:engines/build/classes/java/test:core/build/classes/java/main`
- External comparison included: True
- External comparison profile: `smoke`
- External comparison timed runs: 1

## Benchmark Runs
| Run | Serial AoS (ms) | Parallel AoS (ms) | Split (ms) | Split/Serial (x) | Serial/Parallel (x) | Max abs diff | Parallel max abs diff | Parallelism |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 1305.188 | 1876.164 | 1525.308 | 1.170 | 0.700 | 0.000e+00 | 0.000e+00 | 14 |

## Benchmark Summary
| Metric | Mean | Min | Max | Stddev |
| --- | ---: | ---: | ---: | ---: |
| AoS (ms) | 1305.188 | 1305.188 | 1305.188 | 0.000 |
| Parallel AoS (ms) | 1876.164 | 1876.164 | 1876.164 | 0.000 |
| Split (ms) | 1525.308 | 1525.308 | 1525.308 | 0.000 |
| Split/Serial speedup (x) | 1.170 | 1.170 | 1.170 | 0.000 |
| Serial/Parallel speedup (x) | 0.700 | 0.700 | 0.700 | 0.000 |

- Worst max abs diff: 0.000e+00
- Worst parallel max abs diff: 0.000e+00

## Validation Summary
- Validation task inputs: `./gradlew test --console=plain`
- Suites: 24
- Tests: 235
- Failures: 0
- Errors: 0
- Skipped: 0
- Total time (from JUnit XML): 0.733 s

## Validation by Module
| Module | Suites | Tests | Failures | Errors | Skipped | Time (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| app | 2 | 25 | 0 | 0 | 0 | 0.230 |
| core | 12 | 125 | 0 | 0 | 0 | 0.134 |
| engines | 10 | 85 | 0 | 0 | 0 | 0.369 |

## Validation by Suite
| Module | Suite | Tests | Failures | Errors | Skipped | Time (s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| app | com.omaarr90.statecraft.NoiseOptionsSupportTest | 11 | 0 | 0 | 0 | 0.135 |
| app | com.omaarr90.statecraft.StatecraftCliTest | 14 | 0 | 0 | 0 | 0.095 |
| core | com.omaarr90.statecraft.core.engine.MeasurementInstructionTest | 6 | 0 | 0 | 0 | 0.008 |
| core | com.omaarr90.statecraft.core.engine.MeasurementResultTest | 2 | 0 | 0 | 0 | 0.002 |
| core | com.omaarr90.statecraft.core.engine.SimulationRequestTest | 9 | 0 | 0 | 0 | 0.007 |
| core | com.omaarr90.statecraft.core.math.ComplexArraysTest | 11 | 0 | 0 | 0 | 0.004 |
| core | com.omaarr90.statecraft.core.math.ComplexArraysTinyOpsTest | 13 | 0 | 0 | 0 | 0.004 |
| core | com.omaarr90.statecraft.core.noise.ErrorChannelTest | 28 | 0 | 0 | 0 | 0.037 |
| core | com.omaarr90.statecraft.core.noise.NoiseModelTest | 15 | 0 | 0 | 0 | 0.005 |
| core | com.omaarr90.statecraft.core.parse.JsonCircuitParserTest | 5 | 0 | 0 | 0 | 0.051 |
| core | com.omaarr90.statecraft.core.parse.OpenQasmCircuitParserTest | 17 | 0 | 0 | 0 | 0.012 |
| core | com.omaarr90.statecraft.quantum.QuantumCircuitTest | 10 | 0 | 0 | 0 | 0.001 |
| core | com.omaarr90.statecraft.quantum.SingleQubitGateTest | 6 | 0 | 0 | 0 | 0.002 |
| core | com.omaarr90.statecraft.quantum.StateVectorTest | 3 | 0 | 0 | 0 | 0.001 |
| engines | com.omaarr90.statecraft.engines.CrossEngineConformanceTest | 5 | 0 | 0 | 0 | 0.059 |
| engines | com.omaarr90.statecraft.engines.SimulatorEnginesTest | 3 | 0 | 0 | 0 | 0.002 |
| engines | com.omaarr90.statecraft.engines.stabilizer.StabilizerEngineTest | 15 | 0 | 0 | 0 | 0.009 |
| engines | com.omaarr90.statecraft.engines.stabilizer.StabilizerRandomCliffordConformanceTest | 1 | 0 | 0 | 0 | 0.031 |
| engines | com.omaarr90.statecraft.engines.statevector.CircuitParsingIntegrationTest | 3 | 0 | 0 | 0 | 0.062 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorAlgorithmSuiteTest | 4 | 0 | 0 | 0 | 0.002 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorEngineTest | 25 | 0 | 0 | 0 | 0.183 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorNoiseTest | 13 | 0 | 0 | 0 | 0.012 |
| engines | com.omaarr90.statecraft.engines.tensornetwork.MpsStateTest | 3 | 0 | 0 | 0 | 0.005 |
| engines | com.omaarr90.statecraft.engines.tensornetwork.TensorNetworkEngineTest | 13 | 0 | 0 | 0 | 0.004 |

## External Simulator Comparison
- Profile: `smoke`
- Fixtures: 3
- CPU-only policy: StateCraft, Qiskit Aer, and QuEST are run with one worker thread unless overridden.

| Fixture | Category | Qubits | Aer metric | QuEST metric | StateCraft mean (ms) | Aer mean (ms) | QuEST mean (ms) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| bell | ideal | 2 | 1.110e-16 | 0.000e+00 | 0.108 | 0.271 | 0.018 |
| ghz | ideal | 3 | 1.110e-16 | 0.000e+00 | 0.384 | 0.171 | 0.019 |
| noise_depolarizing_1q | noise | 1 | 2.248e-02 | 2.248e-02 | 19.539 | 0.900 | 0.029 |

Metric is max statevector amplitude difference for ideal/scalability fixtures and total variation distance for noise fixtures.

## Reproducibility Commands
```bash
./gradlew benchmarkValidationReport --console=plain -PbenchmarkRuns=1 -PbenchmarkOutputDir=build/reports/benchmark-validation -PbenchmarkReportPath=docs/deliverables/benchmark-validation-report.md -PincludeExternalComparisons=true -PcomparisonProfile=smoke -PcomparisonRuns=1 -PcomparisonOutputDir=build/reports/benchmark-validation/external-comparison
./gradlew externalComparisonBenchmark --console=plain -PcomparisonProfile=smoke -PcomparisonRuns=1 -PcomparisonOutputDir=build/reports/benchmark-validation/external-comparison
# Direct harness command used by the report task:
python3 /Users/omar/development/StateCraft/scripts/benchmark/comparison/run_comparison.py --fixtures /Users/omar/development/StateCraft/scripts/benchmark/comparison/fixtures.json --profile smoke --output-dir /Users/omar/development/StateCraft/build/reports/benchmark-validation/external-comparison --java-executable /Users/omar/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home/bin/java --statecraft-classpath /Users/omar/development/StateCraft/engines/build/classes/java/main:/Users/omar/development/StateCraft/engines/build/classes/java/test:/Users/omar/development/StateCraft/core/build/classes/java/main:/Users/omar/development/StateCraft/core/build/libs/core-0.1.0.jar:/Users/omar/.gradle/caches/modules-2/files-2.1/org.ejml/ejml-ddense/0.43.1/a74df7b79121914a6921194b9a39d6c0a73b7744/ejml-ddense-0.43.1.jar:/Users/omar/.gradle/caches/modules-2/files-2.1/org.ejml/ejml-zdense/0.43.1/5b8b11fd46abafe095c37e7926896adccd58277e/ejml-zdense-0.43.1.jar:/Users/omar/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-annotations/2.17.1/fca7ef6192c9ad05d07bc50da991bf937a84af3a/jackson-annotations-2.17.1.jar:/Users/omar/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-core/2.17.1/5e52a11644cd59a28ef79f02bddc2cc3bab45edb/jackson-core-2.17.1.jar:/Users/omar/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-databind/2.17.1/524dcbcccdde7d45a679dfc333e4763feb09079/jackson-databind-2.17.1.jar:/Users/omar/.gradle/caches/modules-2/files-2.1/org.ejml/ejml-core/0.43.1/378837ef010fedab1a8c25f19079503aaf054a81/ejml-core-0.43.1.jar --statecraft-main-class com.omaarr90.statecraft.engines.comparison.StatecraftComparisonRunner --statecraft-parallelism 1 --timed-runs 1 --require-external
```
