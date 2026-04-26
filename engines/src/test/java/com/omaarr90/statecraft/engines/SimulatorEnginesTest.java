package com.omaarr90.statecraft.engines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.engine.SimulatorEngines;
import com.omaarr90.statecraft.engines.stabilizer.StabilizerEngine;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.engines.tensornetwork.TensorNetworkEngine;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatorEnginesTest {

	@Test
	void loadAllDiscoversPublishedEngines() {
		List<String> ids = SimulatorEngines.loadAll().stream().map(SimulatorEngine::id).toList();

		assertEquals(List.of(StabilizerEngine.ID, StatevectorEngine.ID, TensorNetworkEngine.ID), ids);
	}

	@Test
	void findByIdReturnsMatchingEngine() {
		SimulatorEngine engine = SimulatorEngines.findById(StatevectorEngine.ID).orElseThrow();

		assertInstanceOf(StatevectorEngine.class, engine);
	}

	@Test
	void requireThrowsForMissingEngine() {
		assertThrows(IllegalArgumentException.class, () -> SimulatorEngines.require("missing-engine"));
	}
}
