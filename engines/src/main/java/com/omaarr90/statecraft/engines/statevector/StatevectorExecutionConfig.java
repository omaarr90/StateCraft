package com.omaarr90.statecraft.engines.statevector;

/**
 * Execution settings for the dense statevector engine.
 *
 * @param parallelism
 *            worker count used by the ForkJoin layer; {@code 1} forces serial
 *            execution
 * @param minimumWorkUnits
 *            minimum independent amplitude groups required before the parallel
 *            path is used
 */
public record StatevectorExecutionConfig(int parallelism, int minimumWorkUnits) {

	/**
	 * Default minimum work units before ForkJoin execution is considered.
	 */
	public static final int DEFAULT_MINIMUM_WORK_UNITS = 4_096;

	/**
	 * Creates validated execution settings.
	 */
	public StatevectorExecutionConfig {
		if (parallelism < 1) {
			throw new IllegalArgumentException("parallelism must be at least 1");
		}
		if (minimumWorkUnits < 1) {
			throw new IllegalArgumentException("minimumWorkUnits must be at least 1");
		}
	}

	/**
	 * Uses the available processor count with the default work threshold.
	 */
	public static StatevectorExecutionConfig auto() {
		return withParallelism(Runtime.getRuntime().availableProcessors());
	}

	/**
	 * Uses an explicit ForkJoin worker count with the default work threshold.
	 */
	public static StatevectorExecutionConfig withParallelism(int parallelism) {
		return new StatevectorExecutionConfig(parallelism, DEFAULT_MINIMUM_WORK_UNITS);
	}

	static StatevectorExecutionConfig withParallelismAndMinimumWorkUnits(int parallelism, int minimumWorkUnits) {
		return new StatevectorExecutionConfig(parallelism, minimumWorkUnits);
	}
}
