"""Shared helpers for StateCraft external simulator comparisons."""

from __future__ import annotations

import cmath
import json
import math
import random
import statistics
from pathlib import Path
from typing import Any


RUNNER_NAMES = ("statecraft", "qiskit_aer", "quest")
IDEAL_CATEGORIES = {"ideal", "scalability"}


def load_fixture_file(path: Path, profile: str) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1:
        raise ValueError(f"unsupported fixture schema_version: {data.get('schema_version')}")
    defaults = data.get("defaults", {})
    fixtures = []
    for fixture in data.get("fixtures", []):
        if profile not in fixture.get("profiles", ["full"]):
            continue
        expanded = dict(fixture)
        expanded["operations"] = expand_operations(fixture)
        expanded["warmup_runs"] = int(fixture.get("warmup_runs", defaults.get("warmup_runs", 1)))
        expanded["timed_runs"] = int(fixture.get("timed_runs", defaults.get("timed_runs", 5)))
        expanded["noise_trajectories"] = int(
            fixture.get("noise_trajectories", defaults.get("noise_trajectories", 512))
        )
        validate_fixture(expanded)
        fixtures.append(expanded)
    return defaults, fixtures


def expand_operations(fixture: dict[str, Any]) -> list[dict[str, Any]]:
    if "operations" in fixture:
        return [dict(operation) for operation in fixture["operations"]]
    generator = fixture.get("generator", {})
    if generator.get("type") != "random_layers":
        raise ValueError(f"fixture {fixture.get('id')} has no operations and no supported generator")

    rng = random.Random(int(generator["seed"]))
    qubits = int(fixture["qubits"])
    layers = int(generator["layers"])
    operations: list[dict[str, Any]] = []
    single_gates = ("h", "x", "z", "s", "sdg")
    for layer in range(layers):
        for qubit in range(qubits):
            gate = single_gates[(rng.randrange(len(single_gates)) + qubit + layer) % len(single_gates)]
            operations.append({"gate": gate, "target": qubit})
        shuffled = list(range(qubits))
        rng.shuffle(shuffled)
        for offset in range(0, qubits - 1, 2):
            control = shuffled[offset]
            target = shuffled[offset + 1]
            operations.append({"gate": "cx", "control": control, "target": target})
        if qubits >= 2 and layer % 3 == 0:
            control = rng.randrange(qubits)
            target = (control + 1 + rng.randrange(qubits - 1)) % qubits
            angle = math.pi / (2 ** (1 + (layer % 3)))
            operations.append({"gate": "cp", "control": control, "target": target, "angle": angle})
    return operations


def validate_fixture(fixture: dict[str, Any]) -> None:
    fixture_id = fixture.get("id")
    if not fixture_id:
        raise ValueError("fixture id is required")
    qubits = fixture.get("qubits")
    if not isinstance(qubits, int) or qubits <= 0:
        raise ValueError(f"fixture {fixture_id}: qubits must be a positive integer")
    if fixture.get("category") not in {"ideal", "scalability", "noise"}:
        raise ValueError(f"fixture {fixture_id}: unsupported category {fixture.get('category')}")
    for operation in fixture.get("operations", []):
        validate_operation(fixture_id, qubits, operation)
    if fixture.get("category") == "noise":
        validate_noise(fixture_id, qubits, fixture.get("noise"))


def validate_operation(fixture_id: str, qubits: int, operation: dict[str, Any]) -> None:
    gate = operation.get("gate")
    if gate in {"h", "x", "y", "z", "s", "sdg"}:
        require_qubit(fixture_id, qubits, operation, "target")
    elif gate in {"cx", "cy", "cz", "cp"}:
        require_qubit(fixture_id, qubits, operation, "control")
        require_qubit(fixture_id, qubits, operation, "target")
        if operation["control"] == operation["target"]:
            raise ValueError(f"fixture {fixture_id}: {gate} control and target must differ")
        if gate == "cp" and "angle" not in operation:
            raise ValueError(f"fixture {fixture_id}: cp requires angle")
    elif gate == "swap":
        require_qubit(fixture_id, qubits, operation, "first")
        require_qubit(fixture_id, qubits, operation, "second")
        if operation["first"] == operation["second"]:
            raise ValueError(f"fixture {fixture_id}: swap qubits must differ")
    else:
        raise ValueError(f"fixture {fixture_id}: unsupported gate {gate}")


def require_qubit(fixture_id: str, qubits: int, operation: dict[str, Any], field: str) -> None:
    value = operation.get(field)
    if not isinstance(value, int) or value < 0 or value >= qubits:
        raise ValueError(f"fixture {fixture_id}: invalid qubit field {field}={value}")


def validate_noise(fixture_id: str, qubits: int, noise: dict[str, Any] | None) -> None:
    if not isinstance(noise, dict):
        raise ValueError(f"fixture {fixture_id}: noise fixture requires noise object")
    noise_type = noise.get("type")
    if noise_type not in {
        "depolarizing",
        "phase_flip",
        "amplitude_damping",
        "phase_damping",
        "thermal_relaxation",
    }:
        raise ValueError(f"fixture {fixture_id}: unsupported noise type {noise_type}")
    noise_qubits = noise.get("qubits", [])
    if not noise_qubits:
        raise ValueError(f"fixture {fixture_id}: noise qubits are required")
    for qubit in noise_qubits:
        if not isinstance(qubit, int) or qubit < 0 or qubit >= qubits:
            raise ValueError(f"fixture {fixture_id}: invalid noise qubit {qubit}")


def write_statecraft_circuit(fixture: dict[str, Any], path: Path) -> None:
    operations = []
    for operation in fixture["operations"]:
        gate = operation["gate"]
        if gate in {"h", "x", "y", "z", "s", "sdg"}:
            operations.append({"gate": gate, "target": operation["target"]})
        elif gate in {"cx", "cy", "cz"}:
            operations.append({"gate": gate, "control": operation["control"], "target": operation["target"]})
        elif gate == "cp":
            operations.append(
                {
                    "gate": gate,
                    "control": operation["control"],
                    "target": operation["target"],
                    "angle": operation["angle"],
                }
            )
        elif gate == "swap":
            operations.append({"gate": gate, "first": operation["first"], "second": operation["second"]})
        else:
            raise ValueError(f"unsupported StateCraft gate: {gate}")
    payload = {"qubits": fixture["qubits"], "operations": operations}
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_quest_program(fixture: dict[str, Any], path: Path) -> None:
    lines = [f"fixture {fixture['id']}", f"category {fixture['category']}", f"qubits {fixture['qubits']}"]
    noise = fixture.get("noise")
    if noise:
        fields = ["noise", noise["type"], "qubits", ",".join(str(q) for q in noise["qubits"])]
        for key in ("probability", "gamma", "lambda", "t1", "t2", "gate_time"):
            if key in noise:
                fields.extend([key, str(noise[key])])
        lines.append(" ".join(fields))
    else:
        lines.append("noise none")
    for operation in fixture["operations"]:
        gate = operation["gate"]
        if gate in {"h", "x", "y", "z", "s", "sdg"}:
            lines.append(f"op {gate} {operation['target']}")
        elif gate in {"cx", "cy", "cz"}:
            lines.append(f"op {gate} {operation['control']} {operation['target']}")
        elif gate == "cp":
            lines.append(f"op cp {operation['control']} {operation['target']} {operation['angle']}")
        elif gate == "swap":
            lines.append(f"op swap {operation['first']} {operation['second']}")
        else:
            raise ValueError(f"unsupported QuEST gate: {gate}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def stats(values: list[float]) -> dict[str, float]:
    if not values:
        return {"count": 0, "mean": 0.0, "min": 0.0, "max": 0.0, "stddev": 0.0}
    return {
        "count": len(values),
        "mean": statistics.fmean(values),
        "min": min(values),
        "max": max(values),
        "stddev": statistics.pstdev(values) if len(values) > 1 else 0.0,
    }


def validate_result_schema(result: dict[str, Any]) -> None:
    for field in ("runner", "fixture_id", "category", "status", "timed_runs_ms", "mean_ms", "probabilities"):
        if field not in result:
            raise ValueError(f"result missing field: {field}")
    if result["status"] != "ok":
        raise ValueError(f"cannot validate non-ok result for {result.get('runner')}: {result.get('error')}")
    if result["runner"] not in RUNNER_NAMES:
        raise ValueError(f"unknown runner: {result['runner']}")
    if not isinstance(result["timed_runs_ms"], list):
        raise ValueError("timed_runs_ms must be a list")
    if not isinstance(result["probabilities"], list):
        raise ValueError("probabilities must be a list")


def compare_statevectors(reference: list[list[float]], actual: list[list[float]]) -> dict[str, float]:
    ref = [complex(pair[0], pair[1]) for pair in reference]
    act = [complex(pair[0], pair[1]) for pair in actual]
    if len(ref) != len(act):
        raise ValueError(f"statevector length mismatch: {len(ref)} != {len(act)}")
    ref_norm = math.sqrt(sum(abs(value) ** 2 for value in ref))
    act_norm = math.sqrt(sum(abs(value) ** 2 for value in act))
    inner = sum(a.conjugate() * b for a, b in zip(ref, act))
    if abs(inner) > 0:
        phase = inner / abs(inner)
        act = [value / phase for value in act]
    diffs = [a - b for a, b in zip(ref, act)]
    return {
        "max_abs_diff": max((abs(value) for value in diffs), default=0.0),
        "l2_diff": math.sqrt(sum(abs(value) ** 2 for value in diffs)),
        "state_fidelity": (abs(inner) ** 2) / ((ref_norm**2) * (act_norm**2)) if ref_norm and act_norm else 0.0,
    }


def compare_probabilities(reference: list[float], actual: list[float]) -> dict[str, float]:
    if len(reference) != len(actual):
        raise ValueError(f"probability length mismatch: {len(reference)} != {len(actual)}")
    tvd = 0.5 * sum(abs(a - b) for a, b in zip(reference, actual))
    max_abs = max((abs(a - b) for a, b in zip(reference, actual)), default=0.0)
    return {"total_variation_distance": tvd, "max_probability_diff": max_abs}


def render_markdown_summary(results: dict[str, Any]) -> str:
    lines = [
        "- Profile: `{}`".format(results["profile"]),
        "- Fixtures: {}".format(len(results["fixtures"])),
        "- CPU-only policy: StateCraft, Qiskit Aer, and QuEST are run with one worker thread unless overridden.",
        "",
        "| Fixture | Category | Qubits | Aer metric | QuEST metric | StateCraft mean (ms) | Aer mean (ms) | QuEST mean (ms) |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for entry in results["fixtures"]:
        fixture = entry["fixture"]
        statecraft = entry["runners"].get("statecraft", {})
        qiskit = entry["runners"].get("qiskit_aer", {})
        quest = entry["runners"].get("quest", {})
        qiskit_metric = format_comparison_metric(entry.get("comparisons", {}).get("qiskit_aer"), fixture["category"])
        quest_metric = format_comparison_metric(entry.get("comparisons", {}).get("quest"), fixture["category"])
        lines.append(
            "| {id} | {category} | {qubits} | {qiskit_metric} | {quest_metric} | {sc_ms} | {qa_ms} | {qu_ms} |".format(
                id=fixture["id"],
                category=fixture["category"],
                qubits=fixture["qubits"],
                qiskit_metric=qiskit_metric,
                quest_metric=quest_metric,
                sc_ms=format_ms(statecraft),
                qa_ms=format_ms(qiskit),
                qu_ms=format_ms(quest),
            )
        )
    lines.extend(
        [
            "",
            "Metric is max statevector amplitude difference for ideal/scalability fixtures and total variation distance for noise fixtures.",
        ]
    )
    return "\n".join(lines)


def format_comparison_metric(metrics: dict[str, Any] | None, category: str) -> str:
    if not metrics:
        return "missing"
    if category in IDEAL_CATEGORIES:
        return "{:.3e}".format(metrics.get("max_abs_diff", float("nan")))
    return "{:.3e}".format(metrics.get("total_variation_distance", float("nan")))


def format_ms(result: dict[str, Any]) -> str:
    if result.get("status") != "ok":
        return "missing"
    return "{:.3f}".format(float(result.get("mean_ms", 0.0)))


def qiskit_complex_list(statevector: Any) -> list[list[float]]:
    return [[float(complex(value).real), float(complex(value).imag)] for value in statevector]


def density_probabilities(matrix: Any) -> list[float]:
    return [float(complex(matrix[index][index]).real) for index in range(len(matrix))]


def canonical_kraus(noise: dict[str, Any]) -> list[list[list[complex]]]:
    noise_type = noise["type"]
    if noise_type == "depolarizing":
        p = float(noise["probability"])
        return [
            [[math.sqrt(1.0 - p), 0.0], [0.0, math.sqrt(1.0 - p)]],
            [[0.0, math.sqrt(p / 3.0)], [math.sqrt(p / 3.0), 0.0]],
            [[0.0, -1j * math.sqrt(p / 3.0)], [1j * math.sqrt(p / 3.0), 0.0]],
            [[math.sqrt(p / 3.0), 0.0], [0.0, -math.sqrt(p / 3.0)]],
        ]
    if noise_type == "phase_flip":
        p = float(noise["probability"])
        return [
            [[math.sqrt(1.0 - p), 0.0], [0.0, math.sqrt(1.0 - p)]],
            [[math.sqrt(p), 0.0], [0.0, -math.sqrt(p)]],
        ]
    if noise_type == "amplitude_damping":
        gamma = float(noise["gamma"])
        return [
            [[1.0, 0.0], [0.0, math.sqrt(1.0 - gamma)]],
            [[0.0, math.sqrt(gamma)], [0.0, 0.0]],
        ]
    if noise_type == "phase_damping":
        lam = float(noise["lambda"])
        return [
            [[1.0, 0.0], [0.0, math.sqrt(1.0 - lam)]],
            [[0.0, 0.0], [0.0, math.sqrt(lam)]],
        ]
    if noise_type == "thermal_relaxation":
        t1 = float(noise["t1"])
        t2 = float(noise["t2"])
        gate_time = float(noise["gate_time"])
        survival = 1.0 if gate_time == 0.0 else math.exp(-gate_time / t1)
        pure_dephasing = 1.0 if gate_time == 0.0 else math.exp(-gate_time * max(0.0, (1.0 / t2) - (1.0 / (2.0 * t1))))
        decay = 1.0 - survival
        plus = 0.5 * (1.0 + pure_dephasing)
        minus = 0.5 * (1.0 - pure_dephasing)
        return [
            [[math.sqrt(plus), 0.0], [0.0, math.sqrt(survival) * math.sqrt(plus)]],
            [[math.sqrt(minus), 0.0], [0.0, -math.sqrt(survival) * math.sqrt(minus)]],
            [[0.0, math.sqrt(decay)], [0.0, 0.0]],
        ]
    raise ValueError(f"unsupported noise type: {noise_type}")


def json_dump(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
