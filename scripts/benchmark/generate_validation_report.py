#!/usr/bin/env python3
"""Generate the benchmark-validation deliverable report."""

from __future__ import annotations

import argparse
import json
import math
import os
import platform
import re
import shlex
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


@dataclass
class BenchmarkRun:
    run: int
    aos_ms: float
    parallel_aos_ms: float
    split_ms: float
    speedup_x: float
    parallel_speedup_x: float
    max_abs_diff: float
    parallel_max_abs_diff: float
    parallelism: int


@dataclass
class SuiteSummary:
    module: str
    name: str
    tests: int
    failures: int
    errors: int
    skipped: int
    time_sec: float
    file: str


def run_command(command: list[str], cwd: Path, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=check)


def render_command(command: list[str]) -> str:
    return shlex.join(command)


def first_output_line(command: list[str], cwd: Path) -> str:
    try:
        completed = run_command(command, cwd, check=False)
        lines = (completed.stdout + completed.stderr).splitlines()
        return lines[0].strip() if lines else "UNKNOWN"
    except Exception:
        return "UNKNOWN"


def parse_value(output: str, pattern: str, label: str) -> float:
    match = re.search(pattern, output)
    if not match:
        raise RuntimeError(f"Unable to parse {label} from benchmark output:\n{output}")
    return float(match.group(1))


def run_benchmark(java_executable: str, classpath: str, main_class: str, repo_root: Path, run_index: int) -> BenchmarkRun:
    command = [
        java_executable,
        "--enable-preview",
        "--add-modules",
        "jdk.incubator.vector",
        "--class-path",
        classpath,
        main_class,
    ]
    completed = run_command(command, repo_root)
    output = completed.stdout + completed.stderr
    return BenchmarkRun(
        run=run_index,
        aos_ms=parse_value(output, r"AoS kernels:\s*([0-9]+(?:\.[0-9]+)?)\s*ms", "AoS kernels"),
        parallel_aos_ms=parse_value(
            output, r"Parallel AoS kernels:\s*([0-9]+(?:\.[0-9]+)?)\s*ms", "Parallel AoS kernels"
        ),
        split_ms=parse_value(output, r"Split reference:\s*([0-9]+(?:\.[0-9]+)?)\s*ms", "Split reference"),
        speedup_x=parse_value(output, r"Speedup\s*\(split/AoS\):\s*([0-9]+(?:\.[0-9]+)?)x", "Speedup"),
        parallel_speedup_x=parse_value(
            output, r"Speedup\s*\(AoS/parallel AoS\):\s*([0-9]+(?:\.[0-9]+)?)x", "Parallel speedup"
        ),
        max_abs_diff=parse_value(
            output, r"Max\s+\|.\|:\s*([0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)", "Max abs diff"
        ),
        parallel_max_abs_diff=parse_value(
            output,
            r"Parallel max\s+\|.\|:\s*([0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)",
            "Parallel max abs diff",
        ),
        parallelism=int(parse_value(output, r"Parallelism:\s*([0-9]+)", "Parallelism")),
    )


def stats(values: list[float]) -> dict[str, float]:
    if not values:
        raise RuntimeError("Cannot compute stats for empty value set.")
    mean = sum(values) / len(values)
    variance = sum((value - mean) ** 2 for value in values) / len(values)
    return {
        "count": len(values),
        "mean": mean,
        "min": min(values),
        "max": max(values),
        "stddev": math.sqrt(variance),
    }


def parse_junit_suites(repo_root: Path) -> list[SuiteSummary]:
    suites: list[SuiteSummary] = []
    for module in ("core", "engines", "app"):
        directory = repo_root / module / "build/test-results/test"
        if not directory.is_dir():
            continue
        for file in sorted(directory.glob("*.xml")):
            root = ET.parse(file).getroot()
            if root.tag != "testsuite":
                continue
            suites.append(
                SuiteSummary(
                    module=module,
                    name=root.attrib.get("name", ""),
                    tests=int(root.attrib.get("tests", "0")),
                    failures=int(root.attrib.get("failures", "0")),
                    errors=int(root.attrib.get("errors", "0")),
                    skipped=int(root.attrib.get("skipped", "0")),
                    time_sec=float(root.attrib.get("time", "0")),
                    file=file.relative_to(repo_root).as_posix(),
                )
            )
    suites.sort(key=lambda suite: (suite.module, suite.name))
    if not suites:
        raise RuntimeError("No JUnit XML files found under */build/test-results/test.")
    return suites


def module_summaries(suites: list[SuiteSummary]) -> list[dict[str, Any]]:
    modules = sorted({suite.module for suite in suites})
    summaries = []
    for module in modules:
        module_suites = [suite for suite in suites if suite.module == module]
        summaries.append(
            {
                "module": module,
                "suites": len(module_suites),
                "tests": sum(suite.tests for suite in module_suites),
                "failures": sum(suite.failures for suite in module_suites),
                "errors": sum(suite.errors for suite in module_suites),
                "skipped": sum(suite.skipped for suite in module_suites),
                "time_sec": sum(suite.time_sec for suite in module_suites),
            }
        )
    return summaries


def cpu_name(repo_root: Path) -> str:
    if sys.platform == "darwin":
        return first_output_line(["sysctl", "-n", "machdep.cpu.brand_string"], repo_root)
    if Path("/proc/cpuinfo").is_file():
        for line in Path("/proc/cpuinfo").read_text(encoding="utf-8", errors="ignore").splitlines():
            if line.startswith("model name"):
                return line.split(":", 1)[1].strip()
    return platform.processor() or platform.machine()


def cpu_topology(repo_root: Path) -> tuple[int, int]:
    logical = os.cpu_count() or 0
    physical = logical
    if sys.platform == "darwin":
        physical_line = first_output_line(["sysctl", "-n", "hw.physicalcpu"], repo_root)
        logical_line = first_output_line(["sysctl", "-n", "hw.logicalcpu"], repo_root)
        return int(physical_line or physical), int(logical_line or logical)
    return physical, logical


def memory_gib(repo_root: Path) -> float:
    if sys.platform == "darwin":
        value = first_output_line(["sysctl", "-n", "hw.memsize"], repo_root)
        return int(value) / (1024**3) if value.isdigit() else 0.0
    meminfo = Path("/proc/meminfo")
    if meminfo.is_file():
        for line in meminfo.read_text(encoding="utf-8", errors="ignore").splitlines():
            if line.startswith("MemTotal:"):
                return int(line.split()[1]) * 1024 / (1024**3)
    return 0.0


def decimal3(value: float) -> str:
    return f"{value:.3f}"


def scientific3(value: float) -> str:
    return f"{value:.3e}"


def table(rows: list[list[str]], header: list[str], aligns: list[str]) -> str:
    lines = ["| " + " | ".join(header) + " |", "| " + " | ".join(aligns) + " |"]
    lines.extend("| " + " | ".join(row) + " |" for row in rows)
    return "\n".join(lines)


def run_external_comparison(args: argparse.Namespace, java_executable: str) -> tuple[str, list[str]]:
    output_dir = Path(args.comparison_output_dir)
    command = [
        "python3",
        str(args.repo_root / "scripts/benchmark/comparison/run_comparison.py"),
        "--fixtures",
        str(args.repo_root / "scripts/benchmark/comparison/fixtures.json"),
        "--profile",
        args.comparison_profile,
        "--output-dir",
        str(output_dir),
        "--java-executable",
        java_executable,
        "--statecraft-classpath",
        args.statecraft_classpath,
        "--statecraft-main-class",
        "com.omaarr90.statecraft.engines.comparison.StatecraftComparisonRunner",
        "--statecraft-parallelism",
        "1",
        "--timed-runs",
        str(args.comparison_runs),
        "--require-external",
    ]
    run_command(command, args.repo_root)
    summary_file = output_dir / "comparison-summary.md"
    if not summary_file.is_file():
        raise RuntimeError(f"External comparison summary was not produced: {summary_file}")
    return summary_file.read_text(encoding="utf-8").strip(), command


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--runs", type=int, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--report-file", type=Path, required=True)
    parser.add_argument("--java-executable", required=True)
    parser.add_argument("--benchmark-classpath", required=True)
    parser.add_argument("--benchmark-main-class", required=True)
    parser.add_argument("--statecraft-classpath", required=True)
    parser.add_argument("--gradle-invocation", default="./gradlew")
    parser.add_argument("--gradle-version", default="UNKNOWN")
    parser.add_argument("--include-external-comparisons", action="store_true")
    parser.add_argument("--comparison-profile", default="smoke")
    parser.add_argument("--comparison-runs", type=int, default=5)
    parser.add_argument("--comparison-output-dir", type=Path, required=True)
    args = parser.parse_args()

    args.repo_root = args.repo_root.resolve()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    args.report_file.parent.mkdir(parents=True, exist_ok=True)

    benchmark_main_class = args.benchmark_main_class
    benchmark_command = [
        "java",
        "--enable-preview",
        "--add-modules",
        "jdk.incubator.vector",
        "--class-path",
        args.benchmark_classpath,
        benchmark_main_class,
    ]
    benchmark_runs = [
        run_benchmark(args.java_executable, args.benchmark_classpath, benchmark_main_class, args.repo_root, run_index)
        for run_index in range(1, args.runs + 1)
    ]

    suites = parse_junit_suites(args.repo_root)
    modules = module_summaries(suites)
    total = {
        "suites": len(suites),
        "tests": sum(suite.tests for suite in suites),
        "failures": sum(suite.failures for suite in suites),
        "errors": sum(suite.errors for suite in suites),
        "skipped": sum(suite.skipped for suite in suites),
        "time_sec": sum(suite.time_sec for suite in suites),
    }
    failures = [
        {
            "module": suite.module,
            "suite": suite.name,
            "failures": suite.failures,
            "errors": suite.errors,
            "file": suite.file,
        }
        for suite in suites
        if suite.failures or suite.errors
    ]

    external_summary = "- External comparisons were not run. Re-run with `-PincludeExternalComparisons=true -PcomparisonProfile=full`."
    external_script_command: list[str] | None = None
    if args.include_external_comparisons:
        external_summary, external_script_command = run_external_comparison(args, args.java_executable)

    now = datetime.now().astimezone()
    now_utc = datetime.now(UTC)
    physical_cpus, logical_cpus = cpu_topology(args.repo_root)
    git_commit = first_output_line(["git", "rev-parse", "HEAD"], args.repo_root)
    git_branch = first_output_line(["git", "branch", "--show-current"], args.repo_root)
    java_version = first_output_line([args.java_executable, "-version"], args.repo_root)

    report_command = [
        args.gradle_invocation,
        "benchmarkValidationReport",
        "--console=plain",
        f"-PbenchmarkRuns={args.runs}",
        f"-PbenchmarkOutputDir={args.output_dir.relative_to(args.repo_root).as_posix()}",
        f"-PbenchmarkReportPath={args.report_file.relative_to(args.repo_root).as_posix()}",
    ]
    if args.include_external_comparisons:
        report_command.extend(
            [
                "-PincludeExternalComparisons=true",
                f"-PcomparisonProfile={args.comparison_profile}",
                f"-PcomparisonRuns={args.comparison_runs}",
                f"-PcomparisonOutputDir={args.comparison_output_dir.relative_to(args.repo_root).as_posix()}",
            ]
        )
    external_gradle_command = [
        args.gradle_invocation,
        "externalComparisonBenchmark",
        "--console=plain",
        f"-PcomparisonProfile={args.comparison_profile}",
        f"-PcomparisonRuns={args.comparison_runs}",
        f"-PcomparisonOutputDir={args.comparison_output_dir.relative_to(args.repo_root).as_posix()}",
    ]

    benchmark_results = {
        "benchmark": {
            "command": render_command(benchmark_command),
            "runs": [asdict(run) for run in benchmark_runs],
            "summary": {
                "aos_ms": stats([run.aos_ms for run in benchmark_runs]),
                "parallel_aos_ms": stats([run.parallel_aos_ms for run in benchmark_runs]),
                "split_ms": stats([run.split_ms for run in benchmark_runs]),
                "speedup_x": stats([run.speedup_x for run in benchmark_runs]),
                "parallel_speedup_x": stats([run.parallel_speedup_x for run in benchmark_runs]),
            },
            "correctness": {
                "max_abs_diff": max(run.max_abs_diff for run in benchmark_runs),
                "parallel_max_abs_diff": max(run.parallel_max_abs_diff for run in benchmark_runs),
            },
        }
    }
    validation_results = {
        "validation": {
            "command": f"{args.gradle_invocation} test --console=plain",
            "total": total,
            "by_module": modules,
            "suites": [asdict(suite) for suite in suites],
            "failures": failures,
        }
    }
    metadata = {
        "run_metadata": {
            "timestamp_local": now.strftime("%Y-%m-%d %H:%M:%S %z"),
            "timestamp_utc": now_utc.strftime("%Y-%m-%d %H:%M:%S UTC"),
            "timezone": now.tzname() or "UNKNOWN",
            "timezone_offset": now.strftime("%z"),
            "git": {"branch": git_branch, "commit": git_commit},
            "environment": {
                "os": platform.platform(),
                "cpu_name": cpu_name(args.repo_root),
                "cpu_cores": physical_cpus,
                "cpu_logical_processors": logical_cpus,
                "memory_gib": memory_gib(args.repo_root),
                "java_version": java_version,
                "gradle_version": args.gradle_version,
            },
            "configuration": {
                "runs": args.runs,
                "output_dir": str(args.output_dir),
                "report_path": str(args.report_file),
                "include_external_comparisons": args.include_external_comparisons,
                "comparison_profile": args.comparison_profile,
                "comparison_runs": args.comparison_runs,
                "comparison_output_dir": str(args.comparison_output_dir),
            },
        }
    }

    (args.output_dir / "benchmark-results.json").write_text(
        json.dumps(benchmark_results, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (args.output_dir / "validation-results.json").write_text(
        json.dumps(validation_results, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (args.output_dir / "run-metadata.json").write_text(
        json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    summary = benchmark_results["benchmark"]["summary"]
    template = (args.repo_root / "docs/deliverables/benchmark-validation-report-template.md").read_text(encoding="utf-8")
    replacements = {
        "{{run_metadata}}": "\n".join(
            [
                f"- Run timestamp (local): {metadata['run_metadata']['timestamp_local']}",
                f"- Run timestamp (UTC): {metadata['run_metadata']['timestamp_utc']}",
                f"- Timezone: {metadata['run_metadata']['timezone']} (offset {metadata['run_metadata']['timezone_offset']})",
                f"- Git branch: {git_branch}",
                f"- Git commit: {git_commit}",
                f"- OS: {platform.platform()}",
                f"- CPU: {metadata['run_metadata']['environment']['cpu_name']}",
                f"- CPU topology: {physical_cpus} cores / {logical_cpus} logical processors",
                f"- RAM: {decimal3(metadata['run_metadata']['environment']['memory_gib'])} GiB",
                f"- Java: {java_version}",
                f"- Gradle: {args.gradle_version}",
            ]
        ),
        "{{benchmark_config}}": "\n".join(
            [
                f"- Gradle task: `{render_command(report_command)}`",
                f"- Iterations: {args.runs}",
                f"- Benchmark JVM command: `{render_command(benchmark_command)}`",
                f"- Classpath: `{args.benchmark_classpath}`",
                f"- External comparison included: {args.include_external_comparisons}",
                f"- External comparison profile: `{args.comparison_profile}`",
                f"- External comparison timed runs: {args.comparison_runs}",
            ]
        ),
        "{{benchmark_run_table}}": table(
            [
                [
                    str(run.run),
                    decimal3(run.aos_ms),
                    decimal3(run.parallel_aos_ms),
                    decimal3(run.split_ms),
                    decimal3(run.speedup_x),
                    decimal3(run.parallel_speedup_x),
                    scientific3(run.max_abs_diff),
                    scientific3(run.parallel_max_abs_diff),
                    str(run.parallelism),
                ]
                for run in benchmark_runs
            ],
            [
                "Run",
                "Serial AoS (ms)",
                "Parallel AoS (ms)",
                "Split (ms)",
                "Split/Serial (x)",
                "Serial/Parallel (x)",
                "Max abs diff",
                "Parallel max abs diff",
                "Parallelism",
            ],
            ["---", "---:", "---:", "---:", "---:", "---:", "---:", "---:", "---:"],
        ),
        "{{benchmark_summary}}": "\n".join(
            [
                table(
                    [
                        ["AoS (ms)", decimal3(summary["aos_ms"]["mean"]), decimal3(summary["aos_ms"]["min"]), decimal3(summary["aos_ms"]["max"]), decimal3(summary["aos_ms"]["stddev"])],
                        [
                            "Parallel AoS (ms)",
                            decimal3(summary["parallel_aos_ms"]["mean"]),
                            decimal3(summary["parallel_aos_ms"]["min"]),
                            decimal3(summary["parallel_aos_ms"]["max"]),
                            decimal3(summary["parallel_aos_ms"]["stddev"]),
                        ],
                        ["Split (ms)", decimal3(summary["split_ms"]["mean"]), decimal3(summary["split_ms"]["min"]), decimal3(summary["split_ms"]["max"]), decimal3(summary["split_ms"]["stddev"])],
                        [
                            "Split/Serial speedup (x)",
                            decimal3(summary["speedup_x"]["mean"]),
                            decimal3(summary["speedup_x"]["min"]),
                            decimal3(summary["speedup_x"]["max"]),
                            decimal3(summary["speedup_x"]["stddev"]),
                        ],
                        [
                            "Serial/Parallel speedup (x)",
                            decimal3(summary["parallel_speedup_x"]["mean"]),
                            decimal3(summary["parallel_speedup_x"]["min"]),
                            decimal3(summary["parallel_speedup_x"]["max"]),
                            decimal3(summary["parallel_speedup_x"]["stddev"]),
                        ],
                    ],
                    ["Metric", "Mean", "Min", "Max", "Stddev"],
                    ["---", "---:", "---:", "---:", "---:"],
                ),
                "",
                f"- Worst max abs diff: {scientific3(benchmark_results['benchmark']['correctness']['max_abs_diff'])}",
                f"- Worst parallel max abs diff: {scientific3(benchmark_results['benchmark']['correctness']['parallel_max_abs_diff'])}",
            ]
        ),
        "{{validation_summary}}": "\n".join(
            [
                f"- Validation task inputs: `{args.gradle_invocation} test --console=plain`",
                f"- Suites: {total['suites']}",
                f"- Tests: {total['tests']}",
                f"- Failures: {total['failures']}",
                f"- Errors: {total['errors']}",
                f"- Skipped: {total['skipped']}",
                f"- Total time (from JUnit XML): {decimal3(total['time_sec'])} s",
            ]
        ),
        "{{validation_module_table}}": table(
            [
                [
                    module["module"],
                    str(module["suites"]),
                    str(module["tests"]),
                    str(module["failures"]),
                    str(module["errors"]),
                    str(module["skipped"]),
                    decimal3(module["time_sec"]),
                ]
                for module in modules
            ],
            ["Module", "Suites", "Tests", "Failures", "Errors", "Skipped", "Time (s)"],
            ["---", "---:", "---:", "---:", "---:", "---:", "---:"],
        ),
        "{{validation_suite_table}}": table(
            [
                [
                    suite.module,
                    suite.name,
                    str(suite.tests),
                    str(suite.failures),
                    str(suite.errors),
                    str(suite.skipped),
                    decimal3(suite.time_sec),
                ]
                for suite in suites
            ],
            ["Module", "Suite", "Tests", "Failures", "Errors", "Skipped", "Time (s)"],
            ["---", "---", "---:", "---:", "---:", "---:", "---:"],
        ),
        "{{external_comparison_summary}}": external_summary,
        "{{repro_commands}}": "\n".join(
            [
                "```bash",
                render_command(report_command),
                render_command(external_gradle_command),
                *(
                    ["# Direct harness command used by the report task:", render_command(external_script_command)]
                    if external_script_command
                    else []
                ),
                "```",
            ]
        ),
    }
    report_text = template
    for placeholder, value in replacements.items():
        report_text = report_text.replace(placeholder, value)
    args.report_file.write_text(report_text, encoding="utf-8")

    if total["failures"] or total["errors"]:
        raise RuntimeError(f"Validation failed with {total['failures']} failure(s) and {total['errors']} error(s).")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"generate_validation_report failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
