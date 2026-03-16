package com.omaarr90.statecraft.engines.statevector;

import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Lightweight microbenchmark that compares the AoS statevector kernels against
 * a split-buffer reference implementation. Designed for manual execution:
 *
 * <pre>
 * ./gradlew :engines:compileTestJava
 * java --enable-preview --add-modules jdk.incubator.vector \
 *      -cp engines/build/classes/java/test:engines/build/classes/java/main \
 *      com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark
 * </pre>
 *
 * The harness prints timing information and the maximum absolute deviation
 * between the two paths.
 */
public final class StatevectorKernelMicrobenchmark {

	private static final int QUBITS = 16;
	private static final int GATE_COUNT = 384;
	private static final int ITERATIONS = 64;

	private StatevectorKernelMicrobenchmark() {
	}

	public static void main(String[] args) {
		SplittableRandom rng = new SplittableRandom(0xBEEFL);
		GateSpec[] gates = randomGates(rng, GATE_COUNT, QUBITS);

		double[] baseline = randomNormalizedState(rng, 1 << QUBITS);
		warmUp(baseline.clone(), gates);

		RunResult aoS = runAoS(baseline.clone(), gates, ITERATIONS);
		RunResult split = runSplit(baseline.clone(), gates, ITERATIONS);

		double maxDiff = maxAbsDiff(aoS.state(), split.state());
		double ratio = (double) split.nanos() / (double) aoS.nanos();

		System.out.printf(Locale.US,
				"AoS kernels: %.3f ms%nSplit reference: %.3f ms%nSpeedup (split/AoS): %.2fx%nMax |Δ|: %.3e%n",
				aoS.nanos() / 1_000_000.0, split.nanos() / 1_000_000.0, ratio, maxDiff);
	}

	private static void warmUp(double[] baseline, GateSpec[] gates) {
		int warmIterations = Math.max(8, ITERATIONS / 4);
		runAoS(baseline.clone(), gates, warmIterations);
		runSplit(baseline.clone(), gates, warmIterations);
	}

	private static RunResult runAoS(double[] state, GateSpec[] gates, int iterations) {
		long start = System.nanoTime();
		for (int iter = 0; iter < iterations; iter++) {
			for (GateSpec gate : gates) {
				StatevectorOps.applySingleGate(state, gate.target(), gate.g00r(), gate.g00i(), gate.g01r(), gate.g01i(),
						gate.g10r(), gate.g10i(), gate.g11r(), gate.g11i());
			}
		}
		long nanos = System.nanoTime() - start;
		return new RunResult(nanos, state);
	}

	private static RunResult runSplit(double[] state, GateSpec[] gates, int iterations) {
		int dimension = state.length >> 1;
		double[] real = new double[dimension];
		double[] imag = new double[dimension];
		for (int index = 0; index < dimension; index++) {
			int base = index << 1;
			real[index] = state[base];
			imag[index] = state[base + 1];
		}

		long start = System.nanoTime();
		for (int iter = 0; iter < iterations; iter++) {
			for (GateSpec gate : gates) {
				applySingleGateSplit(real, imag, gate);
			}
		}
		long nanos = System.nanoTime() - start;
		return new RunResult(nanos, toAoS(real, imag));
	}

	private static void applySingleGateSplit(double[] real, double[] imag, GateSpec gate) {
		int stride = 1 << gate.target();
		int period = stride << 1;
		int dimension = real.length;
		for (int base = 0; base < dimension; base += period) {
			for (int offset = 0; offset < stride; offset++) {
				int idx0 = base + offset;
				int idx1 = idx0 + stride;

				double a0r = real[idx0];
				double a0i = imag[idx0];
				double a1r = real[idx1];
				double a1i = imag[idx1];

				double new0r = gate.g00r() * a0r - gate.g00i() * a0i + gate.g01r() * a1r - gate.g01i() * a1i;
				double new0i = gate.g00r() * a0i + gate.g00i() * a0r + gate.g01r() * a1i + gate.g01i() * a1r;
				double new1r = gate.g10r() * a0r - gate.g10i() * a0i + gate.g11r() * a1r - gate.g11i() * a1i;
				double new1i = gate.g10r() * a0i + gate.g10i() * a0r + gate.g11r() * a1i + gate.g11i() * a1r;

				real[idx0] = new0r;
				imag[idx0] = new0i;
				real[idx1] = new1r;
				imag[idx1] = new1i;
			}
		}
	}

	private static GateSpec[] randomGates(SplittableRandom rng, int count, int qubits) {
		SingleQubitGate[] candidates = {new PauliX(), new PauliY(), new PauliZ(), new Hadamard()};
		GateSpec[] specs = new GateSpec[count];
		for (int i = 0; i < count; i++) {
			SingleQubitGate gate = candidates[rng.nextInt(candidates.length)];
			int target = rng.nextInt(qubits);
			specs[i] = GateSpec.fromGate(target, gate);
		}
		return specs;
	}

	private static double[] randomNormalizedState(SplittableRandom rng, int dimension) {
		double[] data = new double[dimension << 1];
		double normSq = 0.0;
		for (int index = 0; index < dimension; index++) {
			int base = index << 1;
			double real = rng.nextDouble() - 0.5;
			double imag = rng.nextDouble() - 0.5;
			data[base] = real;
			data[base + 1] = imag;
			normSq += (real * real) + (imag * imag);
		}
		double scale = 1.0 / Math.sqrt(normSq);
		for (int i = 0; i < data.length; i++) {
			data[i] *= scale;
		}
		return data;
	}

	private static double[] toAoS(double[] real, double[] imag) {
		double[] state = new double[real.length << 1];
		for (int index = 0; index < real.length; index++) {
			int base = index << 1;
			state[base] = real[index];
			state[base + 1] = imag[index];
		}
		return state;
	}

	private static double maxAbsDiff(double[] a, double[] b) {
		if (a.length != b.length) {
			throw new IllegalArgumentException("array lengths differ");
		}
		double max = 0.0;
		for (int i = 0; i < a.length; i++) {
			double diff = Math.abs(a[i] - b[i]);
			if (diff > max) {
				max = diff;
			}
		}
		return max;
	}

	private record RunResult(long nanos, double[] state) {
	}

	private record GateSpec(int target, double g00r, double g00i, double g01r, double g01i, double g10r, double g10i,
			double g11r, double g11i) {

		static GateSpec fromGate(int target, SingleQubitGate gate) {
			return new GateSpec(target, gate.element(0, 0).real(), gate.element(0, 0).imag(), gate.element(0, 1).real(),
					gate.element(0, 1).imag(), gate.element(1, 0).real(), gate.element(1, 0).imag(),
					gate.element(1, 1).real(), gate.element(1, 1).imag());
		}
	}
}
