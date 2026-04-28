import com.sun.management.OperatingSystemMXBean
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import org.gradle.util.GradleVersion
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow
import kotlin.math.sqrt

data class CommandCapture(
    val command: List<String>,
    val outputLines: List<String>,
    val exitCode: Int,
)

data class BenchmarkRun(
    val run: Int,
    val aosMs: Double,
    val parallelAosMs: Double,
    val splitMs: Double,
    val speedupX: Double,
    val parallelSpeedupX: Double,
    val maxAbsDiff: Double,
    val parallelMaxAbsDiff: Double,
    val parallelism: Int,
)

data class StatsSummary(
    val count: Int,
    val mean: Double,
    val min: Double,
    val max: Double,
    val stddev: Double,
)

data class SuiteSummary(
    val module: String,
    val name: String,
    val tests: Int,
    val failures: Int,
    val errors: Int,
    val skipped: Int,
    val timeSec: Double,
    val file: String,
)

data class ModuleSummary(
    val module: String,
    val suites: Int,
    val tests: Int,
    val failures: Int,
    val errors: Int,
    val skipped: Int,
    val timeSec: Double,
)

data class FailureSummary(
    val module: String,
    val suite: String,
    val failures: Int,
    val errors: Int,
    val file: String,
)

fun externalComparisonClasspath(
    project: Project,
    repoRoot: File,
): String =
    project
        .files(
            repoRoot.resolve("engines/build/classes/java/main"),
            repoRoot.resolve("engines/build/classes/java/test"),
            repoRoot.resolve("core/build/classes/java/main"),
            project.project(":engines").configurations.getByName("runtimeClasspath"),
            project.project(":core").configurations.getByName("runtimeClasspath"),
        ).asPath

fun externalComparisonScriptCommand(
    repoRoot: File,
    javaExecutable: String,
    statecraftClasspath: String,
    outputDir: File,
    profile: String,
    timedRuns: Int,
): List<String> =
    listOf(
        "python3",
        repoRoot.resolve("scripts/benchmark/comparison/run_comparison.py").absolutePath,
        "--fixtures",
        repoRoot.resolve("scripts/benchmark/comparison/fixtures.json").absolutePath,
        "--profile",
        profile,
        "--output-dir",
        outputDir.absolutePath,
        "--java-executable",
        javaExecutable,
        "--statecraft-classpath",
        statecraftClasspath,
        "--statecraft-main-class",
        "com.omaarr90.statecraft.engines.comparison.StatecraftComparisonRunner",
        "--statecraft-parallelism",
        "1",
        "--timed-runs",
        timedRuns.toString(),
        "--require-external",
    )

abstract class BenchmarkValidationReportTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
        private val javaToolchains: JavaToolchainService,
    ) : DefaultTask() {
        @get:Input
        abstract val runs: org.gradle.api.provider.Property<Int>

        @get:Input
        abstract val includeExternalComparisons: org.gradle.api.provider.Property<Boolean>

        @get:Input
        abstract val comparisonProfile: org.gradle.api.provider.Property<String>

        @get:Input
        abstract val comparisonRuns: org.gradle.api.provider.Property<Int>

        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @get:OutputDirectory
        abstract val comparisonOutputDir: DirectoryProperty

        @get:OutputFile
        abstract val reportFile: RegularFileProperty

        @TaskAction
        fun generate() {
            val repoRoot = project.rootDir
            val outputDirFile = outputDir.get().asFile.apply { mkdirs() }
            val reportFileValue = reportFile.get().asFile
            reportFileValue.parentFile.mkdirs()

            val templateFile = repoRoot.resolve("docs/deliverables/benchmark-validation-report-template.md")
            if (!templateFile.isFile) {
                throw GradleException("Template file not found: ${templateFile.absolutePath}")
            }

            val isWindows = System.getProperty("os.name").lowercase(Locale.US).contains("win")
            val isLinux = System.getProperty("os.name").lowercase(Locale.US).contains("linux")
            val isMac = System.getProperty("os.name").lowercase(Locale.US).contains("mac")
            val gradleWrapper = if (isWindows) ".\\gradlew.bat" else "./gradlew"
            val gradleInvocation = if (isWindows) ".\\gradlew.bat" else "./gradlew"
            val pathSeparator = File.pathSeparator
            val classPathEntries =
                listOf(
                    "engines/build/classes/java/main",
                    "engines/build/classes/java/test",
                    "core/build/classes/java/main",
                )
            val benchmarkClassPath = classPathEntries.joinToString(pathSeparator)
            val benchmarkMainClass = "com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark"
            val comparisonOutputDirFile = comparisonOutputDir.get().asFile
            val comparisonProfileValue = comparisonProfile.get()
            val comparisonRunsValue = comparisonRuns.get()

            val nowLocal = ZonedDateTime.now(ZoneId.systemDefault())
            val nowUtc = OffsetDateTime.now(ZoneId.of("UTC"))
            val timestampLocal = nowLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx"))
            val timestampUtc = nowUtc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"))
            val timezoneId = ZoneId.systemDefault().id
            val timezoneOffset = nowLocal.offset.id.replace("Z", "+00:00")

            val gitCommit =
                captureOrNull(listOf("git", "rev-parse", "HEAD"), repoRoot)
                    ?.outputLines
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "UNKNOWN" }
            val gitBranch =
                captureOrNull(listOf("git", "branch", "--show-current"), repoRoot)
                    ?.outputLines
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "UNKNOWN" }

            val javaLauncher =
                javaToolchains
                    .launcherFor {
                        languageVersion.set(JavaLanguageVersion.of(25))
                    }.get()
            val javaExecutable = javaLauncher.executablePath.asFile.absolutePath
            val comparisonClassPath = externalComparisonClasspath(project, repoRoot)
            val javaVersion =
                captureCommand(listOf(javaExecutable, "-version"), repoRoot, ignoreExit = false)
                    .outputLines
                    .firstOrNull()
                    .orEmpty()
                    .ifBlank { "UNKNOWN" }

            val cpuTopology = detectCpuTopology(repoRoot, isWindows, isLinux, isMac)
            val cpuName = detectCpuName(repoRoot, isWindows, isLinux, isMac)
            val memoryGiB = detectTotalMemoryGiB()
            val osDescription = System.getProperty("os.name") + " " + System.getProperty("os.version")

            val benchmarkRuns =
                (1..runs.get()).map { runIndex ->
                    val output = runBenchmark(javaExecutable, benchmarkMainClass, repoRoot)
                    parseBenchmarkRun(runIndex, output)
                }

            val aosStats = computeStats(benchmarkRuns.map { it.aosMs })
            val parallelAosStats = computeStats(benchmarkRuns.map { it.parallelAosMs })
            val splitStats = computeStats(benchmarkRuns.map { it.splitMs })
            val speedupStats = computeStats(benchmarkRuns.map { it.speedupX })
            val parallelSpeedupStats = computeStats(benchmarkRuns.map { it.parallelSpeedupX })
            val worstMaxAbsDiff = benchmarkRuns.maxOfOrNull { it.maxAbsDiff } ?: 0.0
            val worstParallelMaxAbsDiff = benchmarkRuns.maxOfOrNull { it.parallelMaxAbsDiff } ?: 0.0

            val suiteSummaries = parseJUnitSuites(repoRoot)
            if (suiteSummaries.isEmpty()) {
                throw GradleException("No JUnit XML files found under */build/test-results/test.")
            }

            val moduleSummaries =
                suiteSummaries
                    .groupBy { it.module }
                    .map { (module, suites) ->
                        ModuleSummary(
                            module = module,
                            suites = suites.size,
                            tests = suites.sumOf { it.tests },
                            failures = suites.sumOf { it.failures },
                            errors = suites.sumOf { it.errors },
                            skipped = suites.sumOf { it.skipped },
                            timeSec = suites.sumOf { it.timeSec },
                        )
                    }.sortedBy { it.module }

            val totalSuites = suiteSummaries.size
            val totalTests = suiteSummaries.sumOf { it.tests }
            val totalFailures = suiteSummaries.sumOf { it.failures }
            val totalErrors = suiteSummaries.sumOf { it.errors }
            val totalSkipped = suiteSummaries.sumOf { it.skipped }
            val totalTimeSec = suiteSummaries.sumOf { it.timeSec }

            val failureSummaries =
                suiteSummaries
                    .filter { it.failures > 0 || it.errors > 0 }
                    .map {
                        FailureSummary(
                            module = it.module,
                            suite = it.name,
                            failures = it.failures,
                            errors = it.errors,
                            file = it.file,
                        )
                    }

            val externalComparisonScriptCommand =
                externalComparisonScriptCommand(
                    repoRoot = repoRoot,
                    javaExecutable = javaExecutable,
                    statecraftClasspath = comparisonClassPath,
                    outputDir = comparisonOutputDirFile,
                    profile = comparisonProfileValue,
                    timedRuns = comparisonRunsValue,
                )
            val externalComparisonSummary =
                if (includeExternalComparisons.get()) {
                    execOperations.exec {
                        commandLine(externalComparisonScriptCommand)
                        workingDir = repoRoot
                    }
                    val summaryFile = comparisonOutputDirFile.resolve("comparison-summary.md")
                    if (!summaryFile.isFile) {
                        throw GradleException("External comparison summary was not produced: ${summaryFile.absolutePath}")
                    }
                    summaryFile.readText(StandardCharsets.UTF_8).trim()
                } else {
                    "- External comparisons were not run. Re-run with `-PincludeExternalComparisons=true -PcomparisonProfile=full`."
                }

            val reportCommand =
                buildList {
                    add(gradleInvocation)
                    add("benchmarkValidationReport")
                    add("--console=plain")
                    add("-PbenchmarkRuns=${runs.get()}")
                    add("-PbenchmarkOutputDir=${outputDirFile.relativeTo(repoRoot).invariantSeparatorsPath}")
                    add("-PbenchmarkReportPath=${reportFileValue.relativeTo(repoRoot).invariantSeparatorsPath}")
                    if (includeExternalComparisons.get()) {
                        add("-PincludeExternalComparisons=true")
                        add("-PcomparisonProfile=$comparisonProfileValue")
                        add("-PcomparisonRuns=$comparisonRunsValue")
                        add("-PcomparisonOutputDir=${comparisonOutputDirFile.relativeTo(repoRoot).invariantSeparatorsPath}")
                    }
                }

            val externalComparisonGradleCommand =
                listOf(
                    gradleInvocation,
                    "externalComparisonBenchmark",
                    "--console=plain",
                    "-PcomparisonProfile=$comparisonProfileValue",
                    "-PcomparisonRuns=$comparisonRunsValue",
                    "-PcomparisonOutputDir=${comparisonOutputDirFile.relativeTo(repoRoot).invariantSeparatorsPath}",
                )

            val benchmarkCommand =
                listOf(
                    "java",
                    "--enable-preview",
                    "--add-modules",
                    "jdk.incubator.vector",
                    "--class-path",
                    benchmarkClassPath,
                    benchmarkMainClass,
                )

            val benchmarkResults =
                mapOf(
                    "benchmark" to
                        mapOf(
                            "command" to renderCommand(benchmarkCommand),
                            "runs" to
                                benchmarkRuns.map {
                                    mapOf(
                                        "run" to it.run,
                                        "aos_ms" to it.aosMs,
                                        "parallel_aos_ms" to it.parallelAosMs,
                                        "split_ms" to it.splitMs,
                                        "speedup_x" to it.speedupX,
                                        "parallel_speedup_x" to it.parallelSpeedupX,
                                        "max_abs_diff" to it.maxAbsDiff,
                                        "parallel_max_abs_diff" to it.parallelMaxAbsDiff,
                                        "parallelism" to it.parallelism,
                                    )
                                },
                            "summary" to
                                mapOf(
                                    "aos_ms" to statsToJson(aosStats),
                                    "parallel_aos_ms" to statsToJson(parallelAosStats),
                                    "split_ms" to statsToJson(splitStats),
                                    "speedup_x" to statsToJson(speedupStats),
                                    "parallel_speedup_x" to statsToJson(parallelSpeedupStats),
                                ),
                            "correctness" to
                                mapOf(
                                    "max_abs_diff" to worstMaxAbsDiff,
                                    "parallel_max_abs_diff" to worstParallelMaxAbsDiff,
                                ),
                        ),
                )

            val validationResults =
                mapOf(
                    "validation" to
                        mapOf(
                            "command" to "$gradleInvocation test --console=plain",
                            "total" to
                                mapOf(
                                    "suites" to totalSuites,
                                    "tests" to totalTests,
                                    "failures" to totalFailures,
                                    "errors" to totalErrors,
                                    "skipped" to totalSkipped,
                                    "time_sec" to totalTimeSec,
                                ),
                            "by_module" to
                                moduleSummaries.map {
                                    mapOf(
                                        "module" to it.module,
                                        "suites" to it.suites,
                                        "tests" to it.tests,
                                        "failures" to it.failures,
                                        "errors" to it.errors,
                                        "skipped" to it.skipped,
                                        "time_sec" to it.timeSec,
                                    )
                                },
                            "suites" to
                                suiteSummaries.map {
                                    mapOf(
                                        "module" to it.module,
                                        "name" to it.name,
                                        "tests" to it.tests,
                                        "failures" to it.failures,
                                        "errors" to it.errors,
                                        "skipped" to it.skipped,
                                        "time_sec" to it.timeSec,
                                        "file" to it.file,
                                    )
                                },
                            "failures" to
                                failureSummaries.map {
                                    mapOf(
                                        "module" to it.module,
                                        "suite" to it.suite,
                                        "failures" to it.failures,
                                        "errors" to it.errors,
                                        "file" to it.file,
                                    )
                                },
                        ),
                )

            val runMetadata =
                mapOf(
                    "run_metadata" to
                        mapOf(
                            "timestamp_local" to timestampLocal,
                            "timestamp_utc" to timestampUtc,
                            "timezone" to timezoneId,
                            "timezone_offset" to timezoneOffset,
                            "git" to
                                mapOf(
                                    "branch" to gitBranch,
                                    "commit" to gitCommit,
                                ),
                            "environment" to
                                mapOf(
                                    "os" to osDescription,
                                    "cpu_name" to cpuName,
                                    "cpu_cores" to cpuTopology.first,
                                    "cpu_logical_processors" to cpuTopology.second,
                                    "memory_gib" to memoryGiB,
                                    "java_version" to javaVersion,
                                    "gradle_version" to GradleVersion.current().version,
                                ),
                            "configuration" to
                                mapOf(
                                    "runs" to runs.get(),
                                    "output_dir" to outputDirFile.absolutePath,
                                    "report_path" to reportFileValue.absolutePath,
                                    "include_external_comparisons" to includeExternalComparisons.get(),
                                    "comparison_profile" to comparisonProfileValue,
                                    "comparison_runs" to comparisonRunsValue,
                                    "comparison_output_dir" to comparisonOutputDirFile.absolutePath,
                                ),
                        ),
                )

            outputDirFile.resolve("benchmark-results.json").writeText(
                JsonOutput.prettyPrint(JsonOutput.toJson(benchmarkResults)),
                StandardCharsets.UTF_8,
            )
            outputDirFile.resolve("validation-results.json").writeText(
                JsonOutput.prettyPrint(JsonOutput.toJson(validationResults)),
                StandardCharsets.UTF_8,
            )
            outputDirFile.resolve("run-metadata.json").writeText(
                JsonOutput.prettyPrint(JsonOutput.toJson(runMetadata)),
                StandardCharsets.UTF_8,
            )

            val template = templateFile.readText(StandardCharsets.UTF_8)
            val replacements =
                mapOf(
                    "{{run_metadata}}" to
                        listOf(
                            "- Run timestamp (local): $timestampLocal",
                            "- Run timestamp (UTC): $timestampUtc",
                            "- Timezone: $timezoneId (offset $timezoneOffset)",
                            "- Git branch: $gitBranch",
                            "- Git commit: $gitCommit",
                            "- OS: $osDescription",
                            "- CPU: $cpuName",
                            "- CPU topology: ${cpuTopology.first} cores / ${cpuTopology.second} logical processors",
                            "- RAM: ${formatDecimal3(memoryGiB)} GiB",
                            "- Java: $javaVersion",
                            "- Gradle: ${GradleVersion.current().version}",
                        ).joinToString(System.lineSeparator()),
                    "{{benchmark_config}}" to
                        listOf(
                            "- Gradle task: `${renderCommand(reportCommand)}`",
                            "- Iterations: ${runs.get()}",
                            "- Benchmark JVM command: `${renderCommand(benchmarkCommand)}`",
                            "- Classpath: `$benchmarkClassPath`",
                            "- External comparison included: ${includeExternalComparisons.get()}",
                            "- External comparison profile: `$comparisonProfileValue`",
                            "- External comparison timed runs: $comparisonRunsValue",
                        ).joinToString(System.lineSeparator()),
                    "{{benchmark_run_table}}" to
                        buildString {
                            appendLine(
                                "| Run | Serial AoS (ms) | Parallel AoS (ms) | Split (ms) | Split/Serial (x) | Serial/Parallel (x) | Max abs diff | Parallel max abs diff | Parallelism |",
                            )
                            appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
                            benchmarkRuns.forEach {
                                appendLine(
                                    "| ${it.run} | ${formatDecimal3(it.aosMs)} | ${formatDecimal3(it.parallelAosMs)} | " +
                                        "${formatDecimal3(it.splitMs)} | ${formatDecimal3(it.speedupX)} | " +
                                        "${formatDecimal3(it.parallelSpeedupX)} | ${formatScientific3(it.maxAbsDiff)} | " +
                                        "${formatScientific3(it.parallelMaxAbsDiff)} | ${it.parallelism} |",
                                )
                            }
                        }.trimEnd(),
                    "{{benchmark_summary}}" to
                        buildString {
                            appendLine("| Metric | Mean | Min | Max | Stddev |")
                            appendLine("| --- | ---: | ---: | ---: | ---: |")
                            appendLine(
                                "| AoS (ms) | ${formatDecimal3(aosStats.mean)} | ${formatDecimal3(aosStats.min)} | " +
                                    "${formatDecimal3(aosStats.max)} | ${formatDecimal3(aosStats.stddev)} |",
                            )
                            appendLine(
                                "| Parallel AoS (ms) | ${formatDecimal3(parallelAosStats.mean)} | " +
                                    "${formatDecimal3(parallelAosStats.min)} | ${formatDecimal3(parallelAosStats.max)} | " +
                                    "${formatDecimal3(parallelAosStats.stddev)} |",
                            )
                            appendLine(
                                "| Split (ms) | ${formatDecimal3(splitStats.mean)} | ${formatDecimal3(splitStats.min)} | " +
                                    "${formatDecimal3(splitStats.max)} | ${formatDecimal3(splitStats.stddev)} |",
                            )
                            appendLine(
                                "| Split/Serial speedup (x) | ${formatDecimal3(
                                    speedupStats.mean,
                                )} | ${formatDecimal3(speedupStats.min)} | " +
                                    "${formatDecimal3(speedupStats.max)} | ${formatDecimal3(speedupStats.stddev)} |",
                            )
                            appendLine(
                                "| Serial/Parallel speedup (x) | ${formatDecimal3(parallelSpeedupStats.mean)} | " +
                                    "${formatDecimal3(parallelSpeedupStats.min)} | " +
                                    "${formatDecimal3(parallelSpeedupStats.max)} | " +
                                    "${formatDecimal3(parallelSpeedupStats.stddev)} |",
                            )
                            appendLine()
                            append("- Worst max abs diff: ${formatScientific3(worstMaxAbsDiff)}")
                            appendLine()
                            append("- Worst parallel max abs diff: ${formatScientific3(worstParallelMaxAbsDiff)}")
                        },
                    "{{validation_summary}}" to
                        listOf(
                            "- Validation task inputs: `$gradleInvocation test --console=plain`",
                            "- Suites: $totalSuites",
                            "- Tests: $totalTests",
                            "- Failures: $totalFailures",
                            "- Errors: $totalErrors",
                            "- Skipped: $totalSkipped",
                            "- Total time (from JUnit XML): ${formatDecimal3(totalTimeSec)} s",
                        ).joinToString(System.lineSeparator()),
                    "{{validation_module_table}}" to
                        buildString {
                            appendLine("| Module | Suites | Tests | Failures | Errors | Skipped | Time (s) |")
                            appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: |")
                            moduleSummaries.forEach {
                                appendLine(
                                    "| ${it.module} | ${it.suites} | ${it.tests} | ${it.failures} | ${it.errors} | " +
                                        "${it.skipped} | ${formatDecimal3(it.timeSec)} |",
                                )
                            }
                        }.trimEnd(),
                    "{{validation_suite_table}}" to
                        buildString {
                            appendLine("| Module | Suite | Tests | Failures | Errors | Skipped | Time (s) |")
                            appendLine("| --- | --- | ---: | ---: | ---: | ---: | ---: |")
                            suiteSummaries.forEach {
                                appendLine(
                                    "| ${it.module} | ${it.name} | ${it.tests} | ${it.failures} | ${it.errors} | " +
                                        "${it.skipped} | ${formatDecimal3(it.timeSec)} |",
                                )
                            }
                        }.trimEnd(),
                    "{{external_comparison_summary}}" to externalComparisonSummary,
                    "{{repro_commands}}" to
                        buildString {
                            appendLine("```bash")
                            appendLine(renderCommand(reportCommand))
                            appendLine(renderCommand(externalComparisonGradleCommand))
                            if (includeExternalComparisons.get()) {
                                appendLine("# Direct harness command used by the report task:")
                                appendLine(renderCommand(externalComparisonScriptCommand))
                            }
                            appendLine("```")
                        }.trimEnd(),
                )

            var reportText = template
            replacements.forEach { (placeholder, value) ->
                reportText = reportText.replace(placeholder, value)
            }
            reportFileValue.writeText(reportText, StandardCharsets.UTF_8)

            if (totalFailures > 0 || totalErrors > 0) {
                throw GradleException("Validation failed with $totalFailures failure(s) and $totalErrors error(s).")
            }
        }

        private fun parseJUnitSuites(repoRoot: File): List<SuiteSummary> {
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()
            try {
                documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {
            }

            val builder = documentBuilderFactory.newDocumentBuilder()
            val modules = listOf("core", "engines", "app")
            return modules
                .flatMap { module ->
                    val dir = repoRoot.resolve("$module/build/test-results/test")
                    if (!dir.isDirectory) {
                        emptyList()
                    } else {
                        dir
                            .listFiles { file -> file.isFile && file.extension == "xml" }
                            ?.sortedBy { it.name }
                            .orEmpty()
                            .mapNotNull { file ->
                                val document = builder.parse(file)
                                val suite = document.documentElement ?: return@mapNotNull null
                                if (suite.tagName != "testsuite") {
                                    return@mapNotNull null
                                }
                                SuiteSummary(
                                    module = module,
                                    name = suite.getAttribute("name"),
                                    tests = suite.getAttribute("tests").toInt(),
                                    failures = suite.getAttribute("failures").toInt(),
                                    errors = suite.getAttribute("errors").toInt(),
                                    skipped = suite.getAttribute("skipped").toInt(),
                                    timeSec = suite.getAttribute("time").toDouble(),
                                    file = file.relativeTo(repoRoot).invariantSeparatorsPath,
                                )
                            }
                    }
                }.sortedWith(compareBy(SuiteSummary::module, SuiteSummary::name))
        }

        private fun runBenchmark(
            javaExecutable: String,
            benchmarkMainClass: String,
            repoRoot: File,
        ): String {
            val output = ByteArrayOutputStream()
            execOperations.javaexec {
                executable = javaExecutable
                workingDir = repoRoot
                jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
                classpath =
                    project.files(
                        repoRoot.resolve("engines/build/classes/java/main"),
                        repoRoot.resolve("engines/build/classes/java/test"),
                        repoRoot.resolve("core/build/classes/java/main"),
                    )
                mainClass.set(benchmarkMainClass)
                standardOutput = output
                errorOutput = output
            }
            return output.toString(StandardCharsets.UTF_8).trim()
        }

        private fun parseBenchmarkRun(
            runIndex: Int,
            output: String,
        ): BenchmarkRun {
            fun parse(
                pattern: String,
                label: String,
            ): Double {
                val regex = Regex(pattern)
                val match = regex.find(output) ?: throw GradleException("Unable to parse $label from benchmark output.")
                return match.groupValues[1].toDouble()
            }

            return BenchmarkRun(
                run = runIndex,
                aosMs = parse("""AoS kernels:\s*([0-9]+(?:\.[0-9]+)?)\s*ms""", "AoS kernels"),
                parallelAosMs = parse("""Parallel AoS kernels:\s*([0-9]+(?:\.[0-9]+)?)\s*ms""", "Parallel AoS kernels"),
                splitMs = parse("""Split reference:\s*([0-9]+(?:\.[0-9]+)?)\s*ms""", "Split reference"),
                speedupX = parse("""Speedup\s*\(split/AoS\):\s*([0-9]+(?:\.[0-9]+)?)x""", "Speedup"),
                parallelSpeedupX =
                    parse(
                        """Speedup\s*\(AoS/parallel AoS\):\s*([0-9]+(?:\.[0-9]+)?)x""",
                        "Parallel speedup",
                    ),
                maxAbsDiff = parse("""Max\s+\|.\|:\s*([0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)""", "Max abs diff"),
                parallelMaxAbsDiff =
                    parse(
                        """Parallel max\s+\|.\|:\s*([0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)""",
                        "Parallel max abs diff",
                    ),
                parallelism = parse("""Parallelism:\s*([0-9]+)""", "Parallelism").toInt(),
            )
        }

        private fun computeStats(values: List<Double>): StatsSummary {
            if (values.isEmpty()) {
                throw GradleException("Cannot compute stats for empty value set.")
            }

            val mean = values.average()
            val sumSq = values.sumOf { (it - mean).pow(2.0) }
            return StatsSummary(
                count = values.size,
                mean = mean,
                min = values.minOrNull() ?: mean,
                max = values.maxOrNull() ?: mean,
                stddev = sqrt(sumSq / values.size),
            )
        }

        private fun captureCommand(
            command: List<String>,
            workingDir: File,
            ignoreExit: Boolean,
        ): CommandCapture {
            val output = ByteArrayOutputStream()
            val result =
                execOperations.exec {
                    commandLine(command)
                    this.workingDir = workingDir
                    isIgnoreExitValue = true
                    standardOutput = output
                    errorOutput = output
                }
            val text = output.toString(StandardCharsets.UTF_8).trimEnd()
            if (!ignoreExit && result.exitValue != 0) {
                throw GradleException("Command failed (${result.exitValue}): ${renderCommand(command)}\n$text")
            }
            val lines = if (text.isBlank()) emptyList() else text.lineSequence().toList()
            return CommandCapture(command, lines, result.exitValue)
        }

        private fun captureOrNull(
            command: List<String>,
            workingDir: File,
        ): CommandCapture? =
            try {
                captureCommand(command, workingDir, ignoreExit = true)
            } catch (_: Exception) {
                null
            }

        private fun detectCpuName(
            repoRoot: File,
            isWindows: Boolean,
            isLinux: Boolean,
            isMac: Boolean,
        ): String {
            if (isLinux) {
                val cpuInfo = File("/proc/cpuinfo")
                if (cpuInfo.isFile) {
                    cpuInfo.useLines { lines ->
                        lines
                            .firstOrNull { it.startsWith("model name") }
                            ?.substringAfter(':')
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { return it }
                    }
                }
            }

            if (isMac) {
                captureOrNull(listOf("sysctl", "-n", "machdep.cpu.brand_string"), repoRoot)
                    ?.outputLines
                    ?.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }

            if (isWindows) {
                System
                    .getenv("PROCESSOR_IDENTIFIER")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }

            return System.getProperty("os.arch")
        }

        private fun detectCpuTopology(
            repoRoot: File,
            isWindows: Boolean,
            isLinux: Boolean,
            isMac: Boolean,
        ): Pair<Int, Int> {
            var physical = Runtime.getRuntime().availableProcessors()
            var logical = Runtime.getRuntime().availableProcessors()

            if (isLinux) {
                val cpuInfo = File("/proc/cpuinfo")
                if (cpuInfo.isFile) {
                    val lines = cpuInfo.readLines()
                    logical = lines.count { it.startsWith("processor") }.takeIf { it > 0 } ?: logical
                    val coresPerSocket =
                        lines
                            .firstOrNull { it.startsWith("cpu cores") }
                            ?.substringAfter(':')
                            ?.trim()
                            ?.toIntOrNull()
                    val sockets =
                        lines
                            .filter { it.startsWith("physical id") }
                            .mapNotNull { it.substringAfter(':').trim().toIntOrNull() }
                            .distinct()
                            .size
                    if (coresPerSocket != null) {
                        physical = coresPerSocket * (if (sockets > 0) sockets else 1)
                    }
                }
                return physical to logical
            }

            if (isMac) {
                captureOrNull(listOf("sysctl", "-n", "hw.physicalcpu"), repoRoot)
                    ?.outputLines
                    ?.firstOrNull()
                    ?.trim()
                    ?.toIntOrNull()
                    ?.let { physical = it }
                captureOrNull(listOf("sysctl", "-n", "hw.logicalcpu"), repoRoot)
                    ?.outputLines
                    ?.firstOrNull()
                    ?.trim()
                    ?.toIntOrNull()
                    ?.let { logical = it }
                return physical to logical
            }

            if (isWindows) {
                captureOrNull(
                    listOf(
                        "powershell",
                        "-NoProfile",
                        "-Command",
                        "(Get-CimInstance Win32_Processor | Measure-Object -Property NumberOfCores -Sum).Sum",
                    ),
                    repoRoot,
                )?.outputLines?.firstOrNull()?.trim()?.toIntOrNull()?.let { physical = it }
                captureOrNull(
                    listOf(
                        "powershell",
                        "-NoProfile",
                        "-Command",
                        "(Get-CimInstance Win32_Processor | Measure-Object -Property NumberOfLogicalProcessors -Sum).Sum",
                    ),
                    repoRoot,
                )?.outputLines?.firstOrNull()?.trim()?.toIntOrNull()?.let { logical = it }
            }

            return physical to logical
        }

        private fun detectTotalMemoryGiB(): Double {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            if (osBean is OperatingSystemMXBean) {
                return osBean.totalMemorySize.toDouble() / (1024.0 * 1024.0 * 1024.0)
            }
            return 0.0
        }

        private fun renderCommand(command: List<String>): String =
            command.joinToString(" ") { part ->
                if (part.any { it.isWhitespace() || it == '"' || it == ';' || it == '&' || it == '|' }) {
                    "\"${part.replace("\"", "\\\"")}\""
                } else {
                    part
                }
            }

        private fun statsToJson(stats: StatsSummary): Map<String, Any> =
            mapOf(
                "count" to stats.count,
                "mean" to stats.mean,
                "min" to stats.min,
                "max" to stats.max,
                "stddev" to stats.stddev,
            )

        private fun formatDecimal3(value: Double): String = String.format(Locale.US, "%.3f", value)

        private fun formatScientific3(value: Double): String = String.format(Locale.US, "%.3e", value)
    }

val benchmarkRuns = providers.gradleProperty("benchmarkRuns").orNull?.toInt() ?: 5
val benchmarkOutputDir = providers.gradleProperty("benchmarkOutputDir").orNull ?: "build/reports/benchmark-validation"
val benchmarkReportPath = providers.gradleProperty("benchmarkReportPath").orNull ?: "docs/deliverables/benchmark-validation-report.md"
val includeExternalComparisonsProvider =
    providers.gradleProperty("includeExternalComparisons").map(String::toBoolean).orElse(false)
val comparisonProfileProvider = providers.gradleProperty("comparisonProfile").orElse("smoke")
val comparisonRunsProvider = providers.gradleProperty("comparisonRuns").map(String::toInt).orElse(5)
val comparisonOutputDirProvider =
    providers.gradleProperty("comparisonOutputDir").orElse("$benchmarkOutputDir/external-comparison")

tasks.register("externalComparisonBenchmark") {
    group = "verification"
    description = "Runs CPU-only StateCraft, Qiskit Aer, and QuEST comparison fixtures."
    dependsOn(":engines:compileTestJava")
    inputs.property("comparisonProfile", comparisonProfileProvider)
    inputs.property("comparisonRuns", comparisonRunsProvider)
    outputs.dir(file(comparisonOutputDirProvider.get()))
    outputs.upToDateWhen { false }
    doLast {
        val repoRoot = project.rootDir
        val javaCompiler =
            project
                .project(":core")
                .tasks
                .named("compileJava", JavaCompile::class.java)
                .get()
                .javaCompiler
                .get()
        val javaExecutable =
            javaCompiler.metadata.installationPath
                .file("bin/java")
                .asFile.absolutePath
        val command =
            externalComparisonScriptCommand(
                repoRoot = repoRoot,
                javaExecutable = javaExecutable,
                statecraftClasspath = externalComparisonClasspath(project, repoRoot),
                outputDir = file(comparisonOutputDirProvider.get()),
                profile = comparisonProfileProvider.get(),
                timedRuns = comparisonRunsProvider.get(),
            )
        val process =
            ProcessBuilder(command)
                .directory(repoRoot)
                .redirectErrorStream(true)
                .start()
        val output =
            process.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .readText()
                .trimEnd()
        val exitCode = process.waitFor()
        if (output.isNotBlank()) {
            logger.lifecycle(output)
        }
        if (exitCode != 0) {
            throw GradleException("External comparison command failed ($exitCode): ${command.joinToString(" ")}")
        }
    }
}

tasks.register("benchmarkValidationReport") {
    group = "verification"
    description = "Runs the statevector microbenchmark and writes benchmark/validation report artifacts."
    dependsOn(":engines:compileTestJava", ":app:test", ":core:test", ":engines:test")
    inputs.property("benchmarkRuns", benchmarkRuns)
    inputs.property("includeExternalComparisons", includeExternalComparisonsProvider)
    inputs.property("comparisonProfile", comparisonProfileProvider)
    inputs.property("comparisonRuns", comparisonRunsProvider)
    outputs.dir(file(benchmarkOutputDir))
    outputs.dir(file(comparisonOutputDirProvider.get()))
    outputs.file(file(benchmarkReportPath))
    outputs.upToDateWhen { false }
    doLast {
        val repoRoot = project.rootDir
        val javaCompiler =
            project
                .project(":core")
                .tasks
                .named("compileJava", JavaCompile::class.java)
                .get()
                .javaCompiler
                .get()
        val javaExecutable =
            javaCompiler.metadata.installationPath
                .file("bin/java")
                .asFile.absolutePath
        val pathSeparator = File.pathSeparator
        val benchmarkClassPath =
            listOf(
                "engines/build/classes/java/main",
                "engines/build/classes/java/test",
                "core/build/classes/java/main",
            ).joinToString(pathSeparator)
        val isWindows = System.getProperty("os.name").lowercase(Locale.US).contains("win")
        val gradleInvocation = if (isWindows) ".\\gradlew.bat" else "./gradlew"
        val command =
            listOf(
                "python3",
                repoRoot.resolve("scripts/benchmark/generate_validation_report.py").absolutePath,
                "--repo-root",
                repoRoot.absolutePath,
                "--runs",
                benchmarkRuns.toString(),
                "--output-dir",
                file(benchmarkOutputDir).absolutePath,
                "--report-file",
                file(benchmarkReportPath).absolutePath,
                "--java-executable",
                javaExecutable,
                "--benchmark-classpath",
                benchmarkClassPath,
                "--benchmark-main-class",
                "com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark",
                "--statecraft-classpath",
                externalComparisonClasspath(project, repoRoot),
                "--gradle-invocation",
                gradleInvocation,
                "--gradle-version",
                GradleVersion.current().version,
            ) +
                buildList {
                    if (includeExternalComparisonsProvider.get()) {
                        add("--include-external-comparisons")
                    }
                    add("--comparison-profile")
                    add(comparisonProfileProvider.get())
                    add("--comparison-runs")
                    add(comparisonRunsProvider.get().toString())
                    add("--comparison-output-dir")
                    add(file(comparisonOutputDirProvider.get()).absolutePath)
                }
        val process =
            ProcessBuilder(command)
                .directory(repoRoot)
                .redirectErrorStream(true)
                .start()
        val output =
            process.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .readText()
                .trimEnd()
        val exitCode = process.waitFor()
        if (output.isNotBlank()) {
            logger.lifecycle(output)
        }
        if (exitCode != 0) {
            throw GradleException(
                "Benchmark validation report command failed ($exitCode): ${command.joinToString(" ")}",
            )
        }
    }
}
