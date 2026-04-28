#!/usr/bin/env python3
"""Run StateCraft, Qiskit Aer, and QuEST on shared comparison fixtures."""

from __future__ import annotations

import argparse
import json
import os
import platform
import shlex
import subprocess
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from comparison_lib import (
    IDEAL_CATEGORIES,
    compare_probabilities,
    compare_statevectors,
    json_dump,
    load_fixture_file,
    render_markdown_summary,
    stats,
    validate_result_schema,
    write_quest_program,
    write_statecraft_circuit,
)


COMPARISON_DIR = Path(__file__).resolve().parent
REPO_ROOT = COMPARISON_DIR.parents[2]
QISKIT_AER_VERSION = "0.17.2"
STATECRAFT_RUNNER = "com.omaarr90.statecraft.engines.comparison.StatecraftComparisonRunner"


def run_command(command: list[str], cwd: Path = REPO_ROOT, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=check)


def render_command(command: list[str]) -> str:
    return shlex.join(command)


def parse_stdout_json(result: subprocess.CompletedProcess[str]) -> dict[str, Any]:
    lines = [line for line in result.stdout.splitlines() if line.strip()]
    if not lines:
        raise RuntimeError(f"command produced no stdout: {render_command(result.args)}\n{result.stderr}")
    return json.loads(lines[-1])


def apply_overrides(
    fixtures: list[dict[str, Any]],
    warmup_runs: int | None,
    timed_runs: int | None,
    noise_trajectories: int | None,
) -> list[dict[str, Any]]:
    overridden = []
    for fixture in fixtures:
        copy = dict(fixture)
        copy["operations"] = [dict(operation) for operation in fixture["operations"]]
        if "noise" in fixture:
            copy["noise"] = dict(fixture["noise"])
        if warmup_runs is not None:
            copy["warmup_runs"] = warmup_runs
        if timed_runs is not None:
            copy["timed_runs"] = timed_runs
        if noise_trajectories is not None:
            copy["noise_trajectories"] = noise_trajectories
        overridden.append(copy)
    return overridden


def normalize_result(result: dict[str, Any]) -> dict[str, Any]:
    normalized = dict(result)
    if normalized.get("status") != "ok":
        return normalized
    validate_result_schema(normalized)
    timing_stats = stats([float(value) for value in normalized.get("timed_runs_ms", [])])
    normalized["mean_ms"] = timing_stats["mean"]
    normalized["min_ms"] = timing_stats["min"]
    normalized["max_ms"] = timing_stats["max"]
    normalized["stddev_ms"] = timing_stats["stddev"]
    normalized["performance"] = {
        "mean_ms": timing_stats["mean"],
        "min_ms": timing_stats["min"],
        "max_ms": timing_stats["max"],
        "stddev_ms": timing_stats["stddev"],
        "thread_count": normalized.get("threads", 1),
        "versions": normalized.get("versions", {}),
    }
    return normalized


def missing_result(runner: str, fixture: dict[str, Any], reason: str) -> dict[str, Any]:
    return {
        "runner": runner,
        "fixture_id": fixture["id"],
        "category": fixture["category"],
        "status": "missing",
        "error": reason,
        "qubits": fixture["qubits"],
        "operations": len(fixture["operations"]),
        "timed_runs_ms": [],
        "mean_ms": 0.0,
        "probabilities": [],
    }


def run_statecraft_fixture(
    fixture: dict[str, Any],
    generated_dir: Path,
    java_executable: str,
    classpath: str,
    main_class: str,
    parallelism: int,
) -> tuple[dict[str, Any], str]:
    circuit_path = generated_dir / f"{fixture['id']}.statecraft.json"
    write_statecraft_circuit(fixture, circuit_path)
    command = [
        java_executable,
        "--enable-preview",
        "--add-modules",
        "jdk.incubator.vector",
        "--class-path",
        classpath,
        main_class,
        "--fixture-id",
        fixture["id"],
        "--category",
        fixture["category"],
        "--circuit-json",
        str(circuit_path),
        "--warmup-runs",
        str(fixture["warmup_runs"]),
        "--timed-runs",
        str(fixture["timed_runs"]),
        "--parallelism",
        str(parallelism),
    ]
    noise = fixture.get("noise")
    if noise:
        command.extend(["--noise-type", noise["type"]])
        command.extend(["--noise-qubits", ",".join(str(qubit) for qubit in noise["qubits"])])
        command.extend(["--noise-trajectories", str(fixture["noise_trajectories"])])
        command.extend(["--noise-seed", str(int(noise.get("seed", 0)))])
        if "probability" in noise:
            command.extend(["--noise-probability", str(noise["probability"])])
        if "gamma" in noise:
            command.extend(["--noise-gamma", str(noise["gamma"])])
        if "lambda" in noise:
            command.extend(["--noise-lambda", str(noise["lambda"])])
        if "t1" in noise:
            command.extend(["--noise-t1", str(noise["t1"])])
        if "t2" in noise:
            command.extend(["--noise-t2", str(noise["t2"])])
        if "gate_time" in noise:
            command.extend(["--noise-gate-time", str(noise["gate_time"])])

    result = run_command(command)
    return normalize_result(parse_stdout_json(result)), render_command(command)


def has_qiskit_aer(python: str) -> bool:
    script = (
        "import qiskit_aer, sys; "
        f"sys.exit(0 if qiskit_aer.__version__ == '{QISKIT_AER_VERSION}' else 1)"
    )
    try:
        return subprocess.run([python, "-c", script], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    except FileNotFoundError:
        return False


def ensure_qiskit_python(repo_root: Path, explicit_python: str | None, install: bool) -> str:
    if explicit_python:
        if not has_qiskit_aer(explicit_python):
            raise RuntimeError(f"{explicit_python} does not provide qiskit-aer=={QISKIT_AER_VERSION}")
        return explicit_python

    venv_dir = repo_root / "build/external/qiskit-aer-0.17.2-venv"
    python = venv_dir / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    if not has_qiskit_aer(str(python)):
        if not install:
            raise RuntimeError(f"qiskit-aer=={QISKIT_AER_VERSION} is not installed in {venv_dir}")
        subprocess.run([sys.executable, "-m", "venv", str(venv_dir)], check=True)
        subprocess.run(
            [str(python), "-m", "pip", "install", "--disable-pip-version-check", f"qiskit-aer=={QISKIT_AER_VERSION}"],
            check=True,
        )
    return str(python)


def run_qiskit(
    fixtures_path: Path,
    profile: str,
    output_dir: Path,
    warmup_runs: int | None,
    timed_runs: int | None,
    noise_trajectories: int | None,
    explicit_python: str | None,
    install: bool,
) -> tuple[list[dict[str, Any]], str]:
    python = ensure_qiskit_python(REPO_ROOT, explicit_python, install)
    output = output_dir / "qiskit-results.json"
    command = [
        python,
        str(COMPARISON_DIR / "qiskit_runner.py"),
        "--fixtures",
        str(fixtures_path),
        "--profile",
        profile,
        "--output",
        str(output),
    ]
    if warmup_runs is not None:
        command.extend(["--warmup-runs", str(warmup_runs)])
    if timed_runs is not None:
        command.extend(["--timed-runs", str(timed_runs)])
    if noise_trajectories is not None:
        command.extend(["--noise-trajectories", str(noise_trajectories)])
    run_command(command)
    payload = json.loads(output.read_text(encoding="utf-8"))
    return [normalize_result(result) for result in payload["results"]], render_command(command)


def build_or_get_quest_runner(explicit_executable: str | None) -> tuple[str, str]:
    if explicit_executable:
        return explicit_executable, explicit_executable
    command = [sys.executable, str(COMPARISON_DIR / "build_quest_runner.py")]
    result = run_command(command)
    lines = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if not lines:
        raise RuntimeError("build_quest_runner.py produced no executable path")
    return lines[-1], render_command(command)


def run_quest(
    fixtures: list[dict[str, Any]],
    generated_dir: Path,
    explicit_executable: str | None,
) -> tuple[list[dict[str, Any]], list[str]]:
    executable, build_command = build_or_get_quest_runner(explicit_executable)
    commands = [build_command]
    results = []
    for fixture in fixtures:
        program_path = generated_dir / f"{fixture['id']}.quest.txt"
        write_quest_program(fixture, program_path)
        command = [
            executable,
            "--program",
            str(program_path),
            "--warmup-runs",
            str(fixture["warmup_runs"]),
            "--timed-runs",
            str(fixture["timed_runs"]),
        ]
        result = run_command(command)
        results.append(normalize_result(parse_stdout_json(result)))
        commands.append(render_command(command))
    return results, commands


def comparison_for_fixture(fixture: dict[str, Any], statecraft: dict[str, Any], actual: dict[str, Any]) -> dict[str, Any] | None:
    if statecraft.get("status") != "ok" or actual.get("status") != "ok":
        return None
    if fixture["category"] in IDEAL_CATEGORIES:
        metrics = compare_statevectors(statecraft["statevector"], actual["statevector"])
    else:
        metrics = compare_probabilities(statecraft["probabilities"], actual["probabilities"])
    metrics["status"] = "ok"
    return metrics


def system_metadata(java_executable: str) -> dict[str, Any]:
    def command_output(command: list[str]) -> str:
        try:
            completed = run_command(command, check=False)
            lines = (completed.stdout + completed.stderr).splitlines()
            return lines[0].strip() if lines else "UNKNOWN"
        except Exception:
            return "UNKNOWN"

    return {
        "generated_at_utc": datetime.now(UTC).isoformat(),
        "os": platform.platform(),
        "machine": platform.machine(),
        "processor": platform.processor(),
        "logical_cpus": os.cpu_count(),
        "python": sys.version.split()[0],
        "java": command_output([java_executable, "-version"]),
        "compiler": command_output(["c++", "--version"]),
    }


def write_runner_payload(path: Path, runner: str, results: list[dict[str, Any]]) -> None:
    path.write_text(json.dumps({"runner": runner, "results": results}, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixtures", type=Path, default=COMPARISON_DIR / "fixtures.json")
    parser.add_argument("--profile", default="smoke")
    parser.add_argument("--output-dir", type=Path, default=REPO_ROOT / "build/reports/benchmark-validation/external-comparison")
    parser.add_argument("--java-executable", default="java")
    parser.add_argument("--statecraft-classpath", required=True)
    parser.add_argument("--statecraft-main-class", default=STATECRAFT_RUNNER)
    parser.add_argument("--statecraft-parallelism", type=int, default=1)
    parser.add_argument("--warmup-runs", type=int)
    parser.add_argument("--timed-runs", type=int)
    parser.add_argument("--noise-trajectories", type=int)
    parser.add_argument("--qiskit-python")
    parser.add_argument("--quest-executable")
    parser.add_argument("--require-external", action="store_true")
    parser.add_argument("--no-install-qiskit", action="store_true")
    args = parser.parse_args()

    output_dir = args.output_dir
    generated_dir = output_dir / "generated"
    generated_dir.mkdir(parents=True, exist_ok=True)

    _, loaded_fixtures = load_fixture_file(args.fixtures, args.profile)
    fixtures = apply_overrides(loaded_fixtures, args.warmup_runs, args.timed_runs, args.noise_trajectories)
    if not fixtures:
        raise RuntimeError(f"profile {args.profile!r} selected no fixtures from {args.fixtures}")

    command_log: dict[str, Any] = {"statecraft": [], "qiskit_aer": None, "quest": []}

    statecraft_results = []
    for fixture in fixtures:
        result, command = run_statecraft_fixture(
            fixture,
            generated_dir,
            args.java_executable,
            args.statecraft_classpath,
            args.statecraft_main_class,
            args.statecraft_parallelism,
        )
        statecraft_results.append(result)
        command_log["statecraft"].append(command)
    write_runner_payload(output_dir / "statecraft-results.json", "statecraft", statecraft_results)

    try:
        qiskit_results, command = run_qiskit(
            args.fixtures,
            args.profile,
            output_dir,
            args.warmup_runs,
            args.timed_runs,
            args.noise_trajectories,
            args.qiskit_python,
            install=not args.no_install_qiskit,
        )
        command_log["qiskit_aer"] = command
    except Exception as exc:
        if args.require_external:
            raise
        qiskit_results = [missing_result("qiskit_aer", fixture, str(exc)) for fixture in fixtures]
    write_runner_payload(output_dir / "qiskit-results.json", "qiskit_aer", qiskit_results)

    try:
        quest_results, commands = run_quest(fixtures, generated_dir, args.quest_executable)
        command_log["quest"] = commands
    except Exception as exc:
        if args.require_external:
            raise
        quest_results = [missing_result("quest", fixture, str(exc)) for fixture in fixtures]
    write_runner_payload(output_dir / "quest-results.json", "quest", quest_results)

    by_runner = {
        "statecraft": {result["fixture_id"]: result for result in statecraft_results},
        "qiskit_aer": {result["fixture_id"]: result for result in qiskit_results},
        "quest": {result["fixture_id"]: result for result in quest_results},
    }

    entries = []
    for fixture in fixtures:
        statecraft = by_runner["statecraft"][fixture["id"]]
        qiskit = by_runner["qiskit_aer"].get(fixture["id"], missing_result("qiskit_aer", fixture, "missing result"))
        quest = by_runner["quest"].get(fixture["id"], missing_result("quest", fixture, "missing result"))
        entries.append(
            {
                "fixture": fixture,
                "runners": {"statecraft": statecraft, "qiskit_aer": qiskit, "quest": quest},
                "comparisons": {
                    "qiskit_aer": comparison_for_fixture(fixture, statecraft, qiskit),
                    "quest": comparison_for_fixture(fixture, statecraft, quest),
                },
            }
        )

    payload = {
        "schema_version": 1,
        "profile": args.profile,
        "fixtures": entries,
        "metadata": system_metadata(args.java_executable),
        "commands": command_log,
        "policy": {
            "cpu_only": True,
            "statecraft_parallelism": args.statecraft_parallelism,
            "qiskit_aer_version": QISKIT_AER_VERSION,
            "quest_tag": "v4.2.0",
            "quest_commit": "9d7618d7263e3bfba433b88cf1eac0647f08fa0a",
        },
    }
    json_dump(output_dir / "comparison-results.json", payload)
    (output_dir / "comparison-summary.md").write_text(render_markdown_summary(payload) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"run_comparison failed: {exc}", file=sys.stderr)
        if os.environ.get("STATECRAFT_COMPARISON_DEBUG"):
            raise
        raise SystemExit(1)
