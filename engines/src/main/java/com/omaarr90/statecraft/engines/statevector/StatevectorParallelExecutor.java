package com.omaarr90.statecraft.engines.statevector;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

final class StatevectorParallelExecutor {

	@FunctionalInterface
	interface RangeOperation {
		void apply(int startInclusive, int endExclusive);
	}

	private static final int TARGET_TASKS_PER_WORKER = 8;
	private static final ConcurrentMap<Integer, ForkJoinPool> POOLS = new ConcurrentHashMap<>();

	private final StatevectorExecutionConfig config;

	StatevectorParallelExecutor(StatevectorExecutionConfig config) {
		this.config = Objects.requireNonNull(config, "config");
	}

	boolean shouldParallelize(int workUnits) {
		return config.parallelism() > 1 && workUnits >= config.minimumWorkUnits();
	}

	void forRange(int workUnits, RangeOperation operation) {
		Objects.requireNonNull(operation, "operation");
		if (!shouldParallelize(workUnits)) {
			operation.apply(0, workUnits);
			return;
		}
		int targetLeafSize = targetLeafSize(workUnits);
		pool().invoke(new RangeTask(0, workUnits, targetLeafSize, operation));
	}

	private ForkJoinPool pool() {
		return POOLS.computeIfAbsent(config.parallelism(), ForkJoinPool::new);
	}

	private int targetLeafSize(int workUnits) {
		int targetTasks = Math.max(1, config.parallelism() * TARGET_TASKS_PER_WORKER);
		int balancedLeafSize = Math.max(1, (workUnits + targetTasks - 1) / targetTasks);
		return Math.max(config.minimumWorkUnits(), balancedLeafSize);
	}

	private static final class RangeTask extends RecursiveAction {
		private final int startInclusive;
		private final int endExclusive;
		private final int targetLeafSize;
		private final RangeOperation operation;

		RangeTask(int startInclusive, int endExclusive, int targetLeafSize, RangeOperation operation) {
			this.startInclusive = startInclusive;
			this.endExclusive = endExclusive;
			this.targetLeafSize = targetLeafSize;
			this.operation = operation;
		}

		@Override
		protected void compute() {
			int size = endExclusive - startInclusive;
			if (size <= targetLeafSize) {
				operation.apply(startInclusive, endExclusive);
				return;
			}
			int mid = startInclusive + (size >>> 1);
			invokeAll(new RangeTask(startInclusive, mid, targetLeafSize, operation),
					new RangeTask(mid, endExclusive, targetLeafSize, operation));
		}
	}
}
