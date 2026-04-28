from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from comparison_lib import (
    compare_probabilities,
    compare_statevectors,
    load_fixture_file,
    validate_result_schema,
    write_statecraft_circuit,
)


FIXTURES = Path(__file__).resolve().parents[1] / "fixtures.json"


class ComparisonLibTest(unittest.TestCase):
    def test_loads_smoke_fixtures(self) -> None:
        _, fixtures = load_fixture_file(FIXTURES, "smoke")
        fixture_ids = {fixture["id"] for fixture in fixtures}
        self.assertIn("bell", fixture_ids)
        self.assertIn("ghz", fixture_ids)
        self.assertIn("noise_depolarizing_1q", fixture_ids)

    def test_statevector_comparison_ignores_global_phase(self) -> None:
        reference = [[1.0, 0.0], [0.0, 0.0]]
        actual = [[0.0, 1.0], [0.0, 0.0]]
        metrics = compare_statevectors(reference, actual)
        self.assertAlmostEqual(metrics["max_abs_diff"], 0.0)
        self.assertAlmostEqual(metrics["l2_diff"], 0.0)
        self.assertAlmostEqual(metrics["state_fidelity"], 1.0)

    def test_probability_comparison_uses_total_variation_distance(self) -> None:
        metrics = compare_probabilities([0.25, 0.75], [0.50, 0.50])
        self.assertAlmostEqual(metrics["total_variation_distance"], 0.25)
        self.assertAlmostEqual(metrics["max_probability_diff"], 0.25)

    def test_statecraft_fixture_writer_uses_json_schema(self) -> None:
        _, fixtures = load_fixture_file(FIXTURES, "smoke")
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bell.json"
            write_statecraft_circuit(fixtures[0], path)
            payload = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(payload["qubits"], fixtures[0]["qubits"])
        self.assertEqual(payload["operations"][0]["gate"], "h")

    def test_result_schema_validation_accepts_ok_payload(self) -> None:
        validate_result_schema(
            {
                "runner": "statecraft",
                "fixture_id": "bell",
                "category": "ideal",
                "status": "ok",
                "timed_runs_ms": [1.0],
                "mean_ms": 1.0,
                "probabilities": [1.0],
            }
        )


if __name__ == "__main__":
    unittest.main()
