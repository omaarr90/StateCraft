package com.omaarr90.statecraft.core.engine;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Convenience accessors for simulator engines discovered through
 * {@link ServiceLoader}.
 */
public final class SimulatorEngines {

	private SimulatorEngines() {
	}

	/**
	 * Loads all simulator engines visible to the current class loader.
	 *
	 * @return discovered engines sorted by stable engine id
	 */
	public static List<SimulatorEngine> loadAll() {
		return ServiceLoader.load(SimulatorEngine.class).stream().map(ServiceLoader.Provider::get)
				.sorted(Comparator.comparing(SimulatorEngine::id)).toList();
	}

	/**
	 * Finds a simulator engine by id.
	 *
	 * @param id
	 *            engine id to find
	 * @return matching engine, if one is available
	 */
	public static Optional<SimulatorEngine> findById(String id) {
		Objects.requireNonNull(id, "id");
		return loadAll().stream().filter(engine -> engine.id().equals(id)).findFirst();
	}

	/**
	 * Requires a simulator engine by id.
	 *
	 * @param id
	 *            engine id to load
	 * @return matching engine
	 * @throws IllegalArgumentException
	 *             when no engine with the given id is visible
	 */
	public static SimulatorEngine require(String id) {
		return findById(id).orElseThrow(() -> new IllegalArgumentException("No simulator engine found with id: " + id));
	}
}
