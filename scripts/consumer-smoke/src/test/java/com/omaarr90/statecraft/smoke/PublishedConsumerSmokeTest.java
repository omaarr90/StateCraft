package com.omaarr90.statecraft.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.engine.SimulatorEngines;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishedConsumerSmokeTest {

	@Test
	void discoversStatevectorAndRunsBellCircuitDeterministically() {
		QuantumCircuit bell = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);
		MeasurementInstruction measurement = MeasurementInstruction.countsAll(64).withSeed(0x5157A7ECL);
		SimulationRequest request = SimulationRequest.zeroState(bell).withMeasurement(measurement, false);

		SimulatorEngine engine = SimulatorEngines.require("statevector");
		SimulationResult first = engine.simulate(request);
		SimulationResult second = engine.simulate(request);

		MeasurementResult.Histogram firstHistogram = (MeasurementResult.Histogram) first.measurement().orElseThrow();
		MeasurementResult.Histogram secondHistogram = (MeasurementResult.Histogram) second.measurement().orElseThrow();
		Map<Integer, Integer> counts = firstHistogram.counts();

		assertEquals(secondHistogram.counts(), counts);
		assertEquals(64, counts.values().stream().mapToInt(Integer::intValue).sum());
		assertTrue(counts.keySet().stream().allMatch(outcome -> outcome == 0b00 || outcome == 0b11));
	}
}
