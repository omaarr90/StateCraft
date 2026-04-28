#!/usr/bin/env python3
"""Run comparison fixtures with Qiskit Aer CPU simulators."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path
from typing import Any

from comparison_lib import (
    canonical_kraus,
    density_probabilities,
    load_fixture_file,
    qiskit_complex_list,
    stats,
)


def build_circuit(fixture: dict[str, Any], with_noise: bool):
    import numpy as np
    from qiskit import QuantumCircuit
    from qiskit_aer.noise import kraus_error

    circuit = QuantumCircuit(fixture["qubits"])
    noise = fixture.get("noise") if with_noise else None
    noise_instructions = []
    if noise:
        for matrix in canonical_kraus(noise):
            noise_instructions.append(np.array(matrix, dtype=complex))
        noise_instruction = kraus_error(noise_instructions).to_instruction()
    else:
        noise_instruction = None

    for operation in fixture["operations"]:
        append_operation(circuit, operation)
        if noise_instruction is not None:
            for qubit in noise["qubits"]:
                circuit.append(noise_instruction, [qubit])
    return circuit


def append_operation(circuit, operation: dict[str, Any]) -> None:
    gate = operation["gate"]
    if gate == "h":
        circuit.h(operation["target"])
    elif gate == "x":
        circuit.x(operation["target"])
    elif gate == "y":
        circuit.y(operation["target"])
    elif gate == "z":
        circuit.z(operation["target"])
    elif gate == "s":
        circuit.s(operation["target"])
    elif gate == "sdg":
        circuit.sdg(operation["target"])
    elif gate == "cx":
        circuit.cx(operation["control"], operation["target"])
    elif gate == "cy":
        circuit.cy(operation["control"], operation["target"])
    elif gate == "cz":
        circuit.cz(operation["control"], operation["target"])
    elif gate == "cp":
        circuit.cp(operation["angle"], operation["control"], operation["target"])
    elif gate == "swap":
        circuit.swap(operation["first"], operation["second"])
    else:
        raise ValueError(f"unsupported Qiskit gate: {gate}")


def run_fixture(fixture: dict[str, Any]) -> dict[str, Any]:
    import qiskit
    import qiskit_aer
    from qiskit_aer import AerSimulator

    is_noise = fixture["category"] == "noise"
    warmup_runs = int(fixture["warmup_runs"])
    timed_runs = int(fixture["timed_runs"])
    simulator = AerSimulator(
        method="density_matrix" if is_noise else "statevector",
        device="CPU",
        max_parallel_threads=1,
        max_parallel_experiments=1,
    )

    def execute_once():
        circuit = build_circuit(fixture, with_noise=is_noise)
        if is_noise:
            circuit.save_density_matrix()
        else:
            circuit.save_statevector()
        result = simulator.run(circuit, shots=1).result()
        return result.data(0)

    for _ in range(warmup_runs):
        execute_once()

    timed_runs_ms = []
    last_data = None
    for _ in range(timed_runs):
        start = time.perf_counter()
        last_data = execute_once()
        timed_runs_ms.append((time.perf_counter() - start) * 1000.0)

    payload = {
        "runner": "qiskit_aer",
        "fixture_id": fixture["id"],
        "category": fixture["category"],
        "status": "ok",
        "qubits": fixture["qubits"],
        "operations": len(fixture["operations"]),
        "threads": 1,
        "warmup_runs": warmup_runs,
        "timed_runs": timed_runs,
        "timed_runs_ms": timed_runs_ms,
        "mean_ms": stats(timed_runs_ms)["mean"],
        "versions": {"qiskit": qiskit.__version__, "qiskit_aer": qiskit_aer.__version__},
    }
    if is_noise:
        matrix = last_data["density_matrix"].data
        payload["probabilities"] = density_probabilities(matrix)
        payload["noise_trajectories"] = 0
    else:
        state = last_data["statevector"].data
        payload["statevector"] = qiskit_complex_list(state)
        payload["probabilities"] = [float(abs(value) ** 2) for value in state]
        payload["noise_trajectories"] = 0
    return payload


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--profile", default="smoke")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--warmup-runs", type=int)
    parser.add_argument("--timed-runs", type=int)
    parser.add_argument("--noise-trajectories", type=int)
    args = parser.parse_args()

    _, fixtures = load_fixture_file(args.fixtures, args.profile)
    for fixture in fixtures:
        if args.warmup_runs is not None:
            fixture["warmup_runs"] = args.warmup_runs
        if args.timed_runs is not None:
            fixture["timed_runs"] = args.timed_runs
        if args.noise_trajectories is not None:
            fixture["noise_trajectories"] = args.noise_trajectories
    results = [run_fixture(fixture) for fixture in fixtures]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"runner": "qiskit_aer", "results": results}, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"qiskit_runner failed: {exc}", file=sys.stderr)
        if os.environ.get("STATECRAFT_COMPARISON_DEBUG"):
            raise
        raise SystemExit(1)
