package com.omaarr90.statecraft.quantum;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record QuantumCircuit(int qubitCount, List<QuantumCircuit.Operation> operations) {

	private static final ComplexNumber ZERO = ComplexNumber.zero();
	private static final ComplexNumber ONE = ComplexNumber.one();

	public QuantumCircuit(int qubitCount) {
		this(qubitCount, List.of());
	}

	public QuantumCircuit {
		if (qubitCount <= 0) {
			throw new IllegalArgumentException("qubitCount must be positive");
		}
		operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
		for (Operation operation : operations) {
			Objects.requireNonNull(operation, "operation");
			operation.validateTargets(qubitCount);
		}
	}

	public QuantumCircuit append(SingleQubitGate gate, int targetQubit) {
		Operation.SingleGateOperation operation = new Operation.SingleGateOperation(
				Objects.requireNonNull(gate, "gate"), targetQubit);
		return appendOperation(operation);
	}

	public QuantumCircuit append(CnotGate gate, int controlQubit, int targetQubit) {
		Operation.CnotOperation operation = new Operation.CnotOperation(Objects.requireNonNull(gate, "gate"),
				controlQubit, targetQubit);
		return appendOperation(operation);
	}

	public QuantumCircuit appendTwoQubitUnitary(ComplexNumber[] matrix, int firstQubit, int secondQubit) {
		Operation.TwoQubitGateOperation operation = new Operation.TwoQubitGateOperation(
				copyMatrix(Objects.requireNonNull(matrix, "matrix")), firstQubit, secondQubit);
		return appendOperation(operation);
	}

	public QuantumCircuit appendDiagonalTwoQubit(ComplexNumber[] diagonal, int firstQubit, int secondQubit) {
		Operation.TwoQubitDiagonalOperation operation = new Operation.TwoQubitDiagonalOperation(
				copyDiagonal(Objects.requireNonNull(diagonal, "diagonal")), firstQubit, secondQubit);
		return appendOperation(operation);
	}

	public QuantumCircuit appendControlledPhase(double angle, int controlQubit, int targetQubit) {
		ComplexNumber phase = new ComplexNumber(Math.cos(angle), Math.sin(angle));
		ComplexNumber[] diagonal = new ComplexNumber[]{ONE, ONE, ONE, phase};
		return appendDiagonalTwoQubit(diagonal, controlQubit, targetQubit);
	}

	public QuantumCircuit appendControlled(SingleQubitGate gate, int controlQubit, int targetQubit) {
		return appendMultiControl(Objects.requireNonNull(gate, "gate"), targetQubit, controlQubit);
	}

	public QuantumCircuit appendControlledX(int controlQubit, int targetQubit) {
		return appendControlled(new PauliX(), controlQubit, targetQubit);
	}

	public QuantumCircuit appendControlledY(int controlQubit, int targetQubit) {
		return appendControlled(new PauliY(), controlQubit, targetQubit);
	}

	public QuantumCircuit appendControlledZ(int controlQubit, int targetQubit) {
		return appendControlled(new PauliZ(), controlQubit, targetQubit);
	}

	public QuantumCircuit appendToffoli(int firstControlQubit, int secondControlQubit, int targetQubit) {
		return appendMultiControl(new PauliX(), targetQubit, firstControlQubit, secondControlQubit);
	}

	public QuantumCircuit appendSwap(int firstQubit, int secondQubit) {
		Operation.SwapOperation operation = new Operation.SwapOperation(firstQubit, secondQubit);
		return appendOperation(operation);
	}

	public QuantumCircuit appendMultiControl(SingleQubitGate gate, int targetQubit, int... controlQubits) {
		Operation.MultiControlOperation operation = new Operation.MultiControlOperation(
				Objects.requireNonNull(gate, "gate"), targetQubit, copyControls(controlQubits));
		return appendOperation(operation);
	}

	public QuantumCircuit measure(int... qubits) {
		Operation.MeasureOperation operation = new Operation.MeasureOperation(Objects.requireNonNull(qubits, "qubits"));
		return appendOperation(operation);
	}

	private QuantumCircuit appendOperation(Operation operation) {
		operation.validateTargets(qubitCount);
		List<Operation> next = new ArrayList<>(operations);
		next.add(operation);
		return new QuantumCircuit(qubitCount, next);
	}

	private static ComplexNumber[] copyMatrix(ComplexNumber[] matrix) {
		if (matrix.length != 16) {
			throw new IllegalArgumentException("two-qubit matrix must contain 16 elements");
		}
		ComplexNumber[] copy = matrix.clone();
		for (ComplexNumber element : copy) {
			Objects.requireNonNull(element, "matrix element");
		}
		return copy;
	}

	private static ComplexNumber[] copyDiagonal(ComplexNumber[] diagonal) {
		if (diagonal.length != 4) {
			throw new IllegalArgumentException("two-qubit diagonal must contain 4 elements");
		}
		ComplexNumber[] copy = diagonal.clone();
		for (ComplexNumber element : copy) {
			Objects.requireNonNull(element, "diagonal element");
		}
		return copy;
	}

	private static int[] copyControls(int[] controls) {
		Objects.requireNonNull(controls, "controlQubits");
		if (controls.length == 0) {
			throw new IllegalArgumentException("at least one control qubit is required");
		}
		int[] copy = controls.clone();
		for (int control : copy) {
			if (control < 0) {
				throw new IllegalArgumentException("control qubit index must be non-negative");
			}
		}
		Arrays.sort(copy);
		for (int index = 1; index < copy.length; index++) {
			if (copy[index] == copy[index - 1]) {
				throw new IllegalArgumentException("duplicate control qubit: " + copy[index]);
			}
		}
		return copy;
	}

	public ComplexNumber[] apply() {
		int dimension = 1 << qubitCount;
		ComplexNumber[] state = new ComplexNumber[dimension];
		state[0] = ONE;
		for (int i = 1; i < dimension; i++) {
			state[i] = ZERO;
		}
		return apply(state);
	}

	public ComplexNumber[] apply(ComplexNumber[] initialState) {
		Objects.requireNonNull(initialState, "initialState");
		int dimension = 1 << qubitCount;
		if (initialState.length != dimension) {
			throw new IllegalArgumentException(
					"Expected state vector of length " + dimension + ", got " + initialState.length);
		}
		ComplexNumber[] state = new ComplexNumber[dimension];
		for (int i = 0; i < dimension; i++) {
			state[i] = initialState[i] == null ? ZERO : initialState[i];
		}
		for (Operation operation : operations) {
			applyOperation(state, operation);
		}
		return state;
	}

	private void applyOperation(ComplexNumber[] state, Operation operation) {
		if (operation instanceof Operation.SingleGateOperation single) {
			applySingleGate(state, single);
		} else if (operation instanceof Operation.CnotOperation cnot) {
			applyCnot(state, cnot);
		} else if (operation instanceof Operation.TwoQubitGateOperation twoQubit) {
			applyTwoQubitGate(state, twoQubit);
		} else if (operation instanceof Operation.TwoQubitDiagonalOperation diagonal) {
			applyDiagonalTwoQubit(state, diagonal);
		} else if (operation instanceof Operation.SwapOperation swap) {
			applySwap(state, swap);
		} else if (operation instanceof Operation.MultiControlOperation multi) {
			applyMultiControl(state, multi);
		} else if (operation instanceof Operation.MeasureOperation) {
			throw new UnsupportedOperationException(
					"Measurement operations cannot be evaluated using QuantumCircuit.apply");
		} else {
			throw new IllegalStateException("Unsupported operation type: " + operation.getClass().getName());
		}
	}

	private void applySingleGate(ComplexNumber[] state, Operation.SingleGateOperation operation) {
		int target = operation.qubit();
		SingleQubitGate gate = operation.gate();
		ComplexNumber g00 = gate.element(0, 0);
		ComplexNumber g01 = gate.element(0, 1);
		ComplexNumber g10 = gate.element(1, 0);
		ComplexNumber g11 = gate.element(1, 1);
		int stride = 1 << target;
		int period = stride << 1;
		for (int base = 0; base < state.length; base += period) {
			for (int offset = 0; offset < stride; offset++) {
				int idx0 = base + offset;
				int idx1 = idx0 + stride;
				ComplexNumber alpha0 = valueOrZero(state[idx0]);
				ComplexNumber alpha1 = valueOrZero(state[idx1]);
				ComplexNumber new0 = g00.times(alpha0).plus(g01.times(alpha1));
				ComplexNumber new1 = g10.times(alpha0).plus(g11.times(alpha1));
				state[idx0] = new0;
				state[idx1] = new1;
			}
		}
	}

	private void applyCnot(ComplexNumber[] state, Operation.CnotOperation operation) {
		int control = operation.controlQubit();
		int target = operation.targetQubit();
		int controlMask = 1 << control;
		int targetMask = 1 << target;
		int pairMask = controlMask | targetMask;
		for (int base = 0; base < state.length; base++) {
			if ((base & pairMask) == 0) {
				int idx00 = base;
				int idx01 = base | targetMask;
				int idx10 = base | controlMask;
				int idx11 = base | pairMask;

				ComplexNumber amp00 = valueOrZero(state[idx00]);
				ComplexNumber amp01 = valueOrZero(state[idx01]);
				ComplexNumber amp10 = valueOrZero(state[idx10]);
				ComplexNumber amp11 = valueOrZero(state[idx11]);

				state[idx00] = amp00;
				state[idx01] = amp01;
				state[idx10] = amp11;
				state[idx11] = amp10;
			}
		}
	}

	private void applyTwoQubitGate(ComplexNumber[] state, Operation.TwoQubitGateOperation operation) {
		int first = operation.firstQubit();
		int second = operation.secondQubit();
		int firstMask = 1 << first;
		int secondMask = 1 << second;
		ComplexNumber[] matrix = operation.matrix();
		for (int base = 0; base < state.length; base++) {
			if ((base & firstMask) == 0 && (base & secondMask) == 0) {
				int idx00 = base;
				int idx01 = base | secondMask;
				int idx10 = base | firstMask;
				int idx11 = base | firstMask | secondMask;

				ComplexNumber a00 = valueOrZero(state[idx00]);
				ComplexNumber a01 = valueOrZero(state[idx01]);
				ComplexNumber a10 = valueOrZero(state[idx10]);
				ComplexNumber a11 = valueOrZero(state[idx11]);

				ComplexNumber new00 = multiplyRow(matrix, 0, a00, a01, a10, a11);
				ComplexNumber new01 = multiplyRow(matrix, 1, a00, a01, a10, a11);
				ComplexNumber new10 = multiplyRow(matrix, 2, a00, a01, a10, a11);
				ComplexNumber new11 = multiplyRow(matrix, 3, a00, a01, a10, a11);

				state[idx00] = new00;
				state[idx01] = new01;
				state[idx10] = new10;
				state[idx11] = new11;
			}
		}
	}

	private void applyDiagonalTwoQubit(ComplexNumber[] state, Operation.TwoQubitDiagonalOperation operation) {
		int first = operation.firstQubit();
		int second = operation.secondQubit();
		int firstMask = 1 << first;
		int secondMask = 1 << second;
		ComplexNumber[] diagonal = operation.diagonal();
		for (int base = 0; base < state.length; base++) {
			if ((base & firstMask) == 0 && (base & secondMask) == 0) {
				int idx00 = base;
				int idx01 = base | secondMask;
				int idx10 = base | firstMask;
				int idx11 = base | firstMask | secondMask;

				ComplexNumber a00 = valueOrZero(state[idx00]);
				ComplexNumber a01 = valueOrZero(state[idx01]);
				ComplexNumber a10 = valueOrZero(state[idx10]);
				ComplexNumber a11 = valueOrZero(state[idx11]);

				state[idx00] = diagonal[0].times(a00);
				state[idx01] = diagonal[1].times(a01);
				state[idx10] = diagonal[2].times(a10);
				state[idx11] = diagonal[3].times(a11);
			}
		}
	}

	private void applySwap(ComplexNumber[] state, Operation.SwapOperation operation) {
		int first = operation.firstQubit();
		int second = operation.secondQubit();
		int firstMask = 1 << first;
		int secondMask = 1 << second;
		for (int base = 0; base < state.length; base++) {
			if ((base & firstMask) == 0 && (base & secondMask) == 0) {
				int idx01 = base | secondMask;
				int idx10 = base | firstMask;

				ComplexNumber amp01 = valueOrZero(state[idx01]);
				ComplexNumber amp10 = valueOrZero(state[idx10]);

				state[idx01] = amp10;
				state[idx10] = amp01;
			}
		}
	}

	private void applyMultiControl(ComplexNumber[] state, Operation.MultiControlOperation operation) {
		int target = operation.targetQubit();
		int targetMask = 1 << target;
		int controlMask = computeControlMask(operation.controlQubits());
		SingleQubitGate gate = operation.gate();
		ComplexNumber g00 = gate.element(0, 0);
		ComplexNumber g01 = gate.element(0, 1);
		ComplexNumber g10 = gate.element(1, 0);
		ComplexNumber g11 = gate.element(1, 1);
		for (int base = 0; base < state.length; base++) {
			if ((base & controlMask) == controlMask && (base & targetMask) == 0) {
				int idx0 = base;
				int idx1 = base | targetMask;
				ComplexNumber alpha0 = valueOrZero(state[idx0]);
				ComplexNumber alpha1 = valueOrZero(state[idx1]);
				ComplexNumber new0 = g00.times(alpha0).plus(g01.times(alpha1));
				ComplexNumber new1 = g10.times(alpha0).plus(g11.times(alpha1));
				state[idx0] = new0;
				state[idx1] = new1;
			}
		}
	}

	private static int computeControlMask(int[] controlQubits) {
		int mask = 0;
		for (int control : controlQubits) {
			mask |= 1 << control;
		}
		return mask;
	}

	private ComplexNumber valueOrZero(ComplexNumber value) {
		return value == null ? ZERO : value;
	}

	private ComplexNumber multiplyRow(ComplexNumber[] matrix, int row, ComplexNumber a00, ComplexNumber a01,
			ComplexNumber a10, ComplexNumber a11) {
		int base = row << 2;
		ComplexNumber m0 = matrix[base];
		ComplexNumber m1 = matrix[base + 1];
		ComplexNumber m2 = matrix[base + 2];
		ComplexNumber m3 = matrix[base + 3];
		return m0.times(a00).plus(m1.times(a01)).plus(m2.times(a10)).plus(m3.times(a11));
	}

	public sealed interface Operation permits Operation.SingleGateOperation, Operation.CnotOperation,
			Operation.TwoQubitGateOperation, Operation.TwoQubitDiagonalOperation, Operation.SwapOperation,
			Operation.MultiControlOperation, Operation.MeasureOperation {

		void validateTargets(int qubitCount);

		record SingleGateOperation(SingleQubitGate gate, int qubit) implements Operation {

			public SingleGateOperation {
				Objects.requireNonNull(gate, "gate");
				if (qubit < 0) {
					throw new IllegalArgumentException("qubit index must be non-negative");
				}
			}

			@Override
			public void validateTargets(int qubitCount) {
				if (qubit >= qubitCount) {
					throw new IllegalArgumentException("target qubit out of range: " + qubit);
				}
			}
		}

		record CnotOperation(CnotGate gate, int controlQubit, int targetQubit) implements Operation {

			public CnotOperation {
				Objects.requireNonNull(gate, "gate");
				if (controlQubit < 0) {
					throw new IllegalArgumentException("control qubit index must be non-negative");
				}
				if (targetQubit < 0) {
					throw new IllegalArgumentException("target qubit index must be non-negative");
				}
				if (controlQubit == targetQubit) {
					throw new IllegalArgumentException("control and target qubits must differ");
				}
			}

			@Override
			public void validateTargets(int qubitCount) {
				if (controlQubit >= qubitCount) {
					throw new IllegalArgumentException("control qubit out of range: " + controlQubit);
				}
				if (targetQubit >= qubitCount) {
					throw new IllegalArgumentException("target qubit out of range: " + targetQubit);
				}
			}
		}

		record TwoQubitGateOperation(ComplexNumber[] matrix, int firstQubit, int secondQubit) implements Operation {

			public TwoQubitGateOperation {
				Objects.requireNonNull(matrix, "matrix");
				if (matrix.length != 16) {
					throw new IllegalArgumentException("matrix must contain 16 elements");
				}
				for (ComplexNumber element : matrix) {
					Objects.requireNonNull(element, "matrix element");
				}
				if (firstQubit < 0 || secondQubit < 0) {
					throw new IllegalArgumentException("qubit indices must be non-negative");
				}
				if (firstQubit == secondQubit) {
					throw new IllegalArgumentException("qubits must be distinct");
				}
			}

			@Override
			public void validateTargets(int qubitCount) {
				if (firstQubit >= qubitCount) {
					throw new IllegalArgumentException("first qubit out of range: " + firstQubit);
				}
				if (secondQubit >= qubitCount) {
					throw new IllegalArgumentException("second qubit out of range: " + secondQubit);
				}
			}
		}

		record TwoQubitDiagonalOperation(ComplexNumber[] diagonal, int firstQubit,
				int secondQubit) implements Operation {

			public TwoQubitDiagonalOperation {
				Objects.requireNonNull(diagonal, "diagonal");
				if (diagonal.length != 4) {
					throw new IllegalArgumentException("diagonal must contain 4 elements");
				}
				for (ComplexNumber element : diagonal) {
					Objects.requireNonNull(element, "diagonal element");
				}
				if (firstQubit < 0 || secondQubit < 0) {
					throw new IllegalArgumentException("qubit indices must be non-negative");
				}
				if (firstQubit == secondQubit) {
					throw new IllegalArgumentException("qubits must be distinct");
				}
			}

			@Override
			public void validateTargets(int qubitCount) {
				if (firstQubit >= qubitCount) {
					throw new IllegalArgumentException("first qubit out of range: " + firstQubit);
				}
				if (secondQubit >= qubitCount) {
					throw new IllegalArgumentException("second qubit out of range: " + secondQubit);
				}
			}
		}

		record SwapOperation(int firstQubit, int secondQubit) implements Operation {

			public SwapOperation {
				if (firstQubit < 0 || secondQubit < 0) {
					throw new IllegalArgumentException("qubit indices must be non-negative");
				}
				if (firstQubit == secondQubit) {
					throw new IllegalArgumentException("cannot swap qubit with itself");
				}
			}

			@Override
			public void validateTargets(int qubitCount) {
				if (firstQubit >= qubitCount) {
					throw new IllegalArgumentException("first qubit out of range: " + firstQubit);
				}
				if (secondQubit >= qubitCount) {
					throw new IllegalArgumentException("second qubit out of range: " + secondQubit);
				}
			}
		}

		record MultiControlOperation(SingleQubitGate gate, int targetQubit, int[] controlQubits) implements Operation {

			public MultiControlOperation {
				Objects.requireNonNull(gate, "gate");
				Objects.requireNonNull(controlQubits, "controlQubits");
				controlQubits = controlQubits.clone();
				Arrays.sort(controlQubits);
				if (targetQubit < 0) {
					throw new IllegalArgumentException("target qubit index must be non-negative");
				}
				if (controlQubits.length == 0) {
					throw new IllegalArgumentException("at least one control qubit is required");
				}
				for (int control : controlQubits) {
					if (control < 0) {
						throw new IllegalArgumentException("control qubit index must be non-negative");
					}
					if (control == targetQubit) {
						throw new IllegalArgumentException("control and target qubits must differ");
					}
				}
				for (int index = 1; index < controlQubits.length; index++) {
					if (controlQubits[index] == controlQubits[index - 1]) {
						throw new IllegalArgumentException("duplicate control qubit: " + controlQubits[index]);
					}
				}
			}

			@Override
			public void validateTargets(int qubitCount) {
				if (targetQubit >= qubitCount) {
					throw new IllegalArgumentException("target qubit out of range: " + targetQubit);
				}
				for (int control : controlQubits) {
					if (control >= qubitCount) {
						throw new IllegalArgumentException("control qubit out of range: " + control);
					}
				}
			}

			@Override
			public int[] controlQubits() {
				return controlQubits.clone();
			}
		}

		record MeasureOperation(int[] qubits) implements Operation {

			public MeasureOperation {
				Objects.requireNonNull(qubits, "qubits");
				if (qubits.length == 0) {
					throw new IllegalArgumentException("at least one qubit must be measured");
				}
				int[] copy = qubits.clone();
				for (int qubit : copy) {
					if (qubit < 0) {
						throw new IllegalArgumentException("measured qubit index must be non-negative");
					}
				}
				Arrays.sort(copy);
				for (int index = 1; index < copy.length; index++) {
					if (copy[index] == copy[index - 1]) {
						throw new IllegalArgumentException("duplicate measured qubit: " + copy[index]);
					}
				}
				qubits = copy;
			}

			@Override
			public void validateTargets(int qubitCount) {
				for (int qubit : qubits) {
					if (qubit >= qubitCount) {
						throw new IllegalArgumentException("measured qubit out of range: " + qubit);
					}
				}
			}

			@Override
			public int[] qubits() {
				return qubits.clone();
			}
		}
	}
}
