package com.omaarr90.statecraft.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.SGate;
import com.omaarr90.statecraft.quantum.SdgGate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenQasmCircuitParserTest {

	private static final double EPS = 1e-12;

	@Test
	void bellCircuitParsesAndSimulates() {
		String qasm = """
				OPENQASM 3.0;
				qubit[2] q;

				h q[0];
				cx q[0], q[1];
				""";

		OpenQasmCircuitParser parser = new OpenQasmCircuitParser();
		QuantumCircuit circuit = parser.parse(qasm);

		QuantumCircuit expectedCircuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);

		assertStateMatches(expectedCircuit.apply(), circuit.apply());
	}

	@Test
	void openQasm2BellWithIncludeParsesAndSimulates() {
		String qasm = """
				OPENQASM 2.0;
				include "qelib1.inc";
				qreg qr[2];
				creg cr[2];

				h qr[0];
				cx qr[0], qr[1];
				""";

		QuantumCircuit circuit = new OpenQasmCircuitParser().parse(qasm);
		QuantumCircuit expectedCircuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);

		assertStateMatches(expectedCircuit.apply(), circuit.apply());
	}

	@Test
	void multipleArbitraryRegistersFlattenInDeclarationOrder() {
		String qasm = """
				OPENQASM 3.0;
				qubit[1] ancilla;
				qubit[2] data;

				h data[0];
				cx data[0], ancilla[0];
				""";

		QuantumCircuit circuit = new OpenQasmCircuitParser().parse(qasm);
		QuantumCircuit expectedCircuit = new QuantumCircuit(3).append(new Hadamard(), 1).append(CnotGate.of(), 1, 0);

		assertStateMatches(expectedCircuit.apply(), circuit.apply());
	}

	@Test
	void measurementInMiddleIsRejected() {
		String qasm = """
				OPENQASM 3.0;
				qubit[1] q;
				bit[1] c;

				h q[0];
				measure q[0] -> c[0];
				x q[0];
				""";

		OpenQasmCircuitParser parser = new OpenQasmCircuitParser();
		CircuitParseException error = assertThrows(CircuitParseException.class, () -> parser.parse(qasm));
		assertTrue(error.getMessage().toLowerCase().contains("measurement"));
	}

	@Test
	void controlledAndMultiControlGatesParseAndSimulate() {
		String qasm = """
				OPENQASM 3.0;
				qubit[3] q;

				x q[0];
				x q[1];
				ccx q[0], q[1], q[2];
				cy q[2], q[0];
				cz q[2], q[1];
				""";

		OpenQasmCircuitParser parser = new OpenQasmCircuitParser();
		QuantumCircuit circuit = parser.parse(qasm);

		QuantumCircuit expectedCircuit = new QuantumCircuit(3).append(new PauliX(), 0).append(new PauliX(), 1)
				.appendToffoli(0, 1, 2).appendControlledY(2, 0).appendControlledZ(2, 1);

		assertStateMatches(expectedCircuit.apply(), circuit.apply());
	}

	@Test
	void registerWideMeasurementParsesToAllQubits() {
		String qasm = """
				OPENQASM 3.0;
				qubit[3] q;

				h q[0];
				barrier q;
				measure q;
				""";

		OpenQasmCircuitParser parser = new OpenQasmCircuitParser();
		QuantumCircuit circuit = parser.parse(qasm);
		QuantumCircuit.Operation.MeasureOperation measure = (QuantumCircuit.Operation.MeasureOperation) circuit
				.operations().get(circuit.operations().size() - 1);

		assertArrayEquals(new int[]{0, 1, 2}, measure.qubits());
	}

	@Test
	void qasm2RegisterWideMeasurementWithClassicalTargetParses() {
		String qasm = """
				OPENQASM 2.0;
				qreg q[2];
				creg out[2];

				h q[0];
				cx q[0], q[1];
				measure q -> out;
				""";

		QuantumCircuit circuit = new OpenQasmCircuitParser().parse(qasm);
		QuantumCircuit.Operation.MeasureOperation measure = (QuantumCircuit.Operation.MeasureOperation) circuit
				.operations().get(circuit.operations().size() - 1);

		assertArrayEquals(new int[]{0, 1}, measure.qubits());
	}

	@Test
	void sAndSdgParseAndSimulate() {
		String qasm = """
				OPENQASM 3.0;
				qubit[1] q;

				x q[0];
				s q[0];
				sdg q[0];
				""";

		QuantumCircuit circuit = new OpenQasmCircuitParser().parse(qasm);
		QuantumCircuit expectedCircuit = new QuantumCircuit(1).append(new PauliX(), 0).append(new SGate(), 0)
				.append(new SdgGate(), 0);

		assertStateMatches(expectedCircuit.apply(), circuit.apply());
	}

	@Test
	void rotationAndPhaseGatesParseAndSimulate() {
		String qasm = """
				OPENQASM 2.0;
				include "qelib1.inc";
				qreg q[1];

				ry((pi / 4) * 2 + pi / 2) q[0];
				p(pi / 2) q[0];
				t q[0];
				tdg q[0];
				u1(0) q[0];
				""";

		QuantumCircuit circuit = new OpenQasmCircuitParser().parse(qasm);
		ComplexNumber[] state = circuit.apply();

		assertEquals(0.0, state[0].real(), EPS);
		assertEquals(0.0, state[0].imag(), EPS);
		assertEquals(0.0, state[1].real(), EPS);
		assertEquals(1.0, state[1].imag(), EPS);
	}

	@Test
	void universalAndRotationGatesParseAndSimulate() {
		String qasm = """
				OPENQASM 3.0;
				qubit[1] q;

				U(pi, 0, pi) q[0];
				rx(pi) q[0];
				rz(2 * pi) q[0];
				""";

		QuantumCircuit circuit = new OpenQasmCircuitParser().parse(qasm);
		ComplexNumber[] state = circuit.apply();

		assertEquals(0.0, state[0].real(), EPS);
		assertEquals(1.0, state[0].imag(), EPS);
		assertEquals(0.0, state[1].real(), EPS);
		assertEquals(0.0, state[1].imag(), EPS);
	}

	@Test
	void multiControlPauliAliasesParseAndSimulate() {
		String qasm = """
				OPENQASM 3.0;
				qubit[3] q;

				x q[0];
				x q[1];
				mcy q[0], q[1], q[2];
				mcz q[0], q[1], q[2];
				mcx q[0], q[1], q[2];
				""";

		QuantumCircuit circuit = new OpenQasmCircuitParser().parse(qasm);
		QuantumCircuit expectedCircuit = new QuantumCircuit(3).append(new PauliX(), 0).append(new PauliX(), 1)
				.appendMultiControl(new PauliY(), 2, 0, 1).appendMultiControl(new PauliZ(), 2, 0, 1)
				.appendMultiControl(new PauliX(), 2, 0, 1);

		assertStateMatches(expectedCircuit.apply(), circuit.apply());
	}

	@Test
	void bomPrefixedQasmParsesDirectlyAndViaAutoDetection(@TempDir Path tempDir) throws IOException {
		String qasm = "\uFEFF" + """
				OPENQASM 3.0;
				qubit[2] q;

				h q[0];
				cx q[0], q[1];
				""";

		QuantumCircuit direct = new OpenQasmCircuitParser().parse(qasm);
		Path input = tempDir.resolve("bell.qasm");
		Files.writeString(input, qasm);
		QuantumCircuit autodetected = CircuitParsers.parse(input, CircuitFormat.AUTO);

		QuantumCircuit expected = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);

		assertStateMatches(expected.apply(), direct.apply());
		assertStateMatches(expected.apply(), autodetected.apply());
	}

	@Test
	void scientificNotationAnglesParseAndSimulate() {
		String qasm = """
				OPENQASM 3.0;
				qubit[2] q;

				h q[0];
				h q[1];
				cp(1e-3) q[0], q[1];
				cp(-2.5E-1) q[1], q[0];
				""";

		QuantumCircuit circuit = new OpenQasmCircuitParser().parse(qasm);
		QuantumCircuit expectedCircuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(new Hadamard(), 1)
				.appendControlledPhase(1e-3, 0, 1).appendControlledPhase(-2.5E-1, 1, 0);

		assertStateMatches(expectedCircuit.apply(), circuit.apply());
	}

	@Test
	void rejectsUnsupportedCustomGateDefinition() {
		String qasm = """
				OPENQASM 2.0;
				qreg q[1];
				gate custom a { x a; }
				custom q[0];
				""";

		CircuitParseException error = assertThrows(CircuitParseException.class,
				() -> new OpenQasmCircuitParser().parse(qasm));
		assertTrue(error.getMessage().contains("Unsupported OpenQASM construct"));
	}

	@Test
	void rejectsUnsupportedConditional() {
		String qasm = """
				OPENQASM 2.0;
				qreg q[1];
				creg c[1];
				if (c==1) x q[0];
				""";

		CircuitParseException error = assertThrows(CircuitParseException.class,
				() -> new OpenQasmCircuitParser().parse(qasm));
		assertTrue(error.getMessage().contains("Unsupported OpenQASM construct"));
	}

	@Test
	void rejectsMeasurementRegisterSizeMismatch() {
		String qasm = """
				OPENQASM 2.0;
				qreg q[2];
				creg c[1];
				measure q -> c;
				""";

		CircuitParseException error = assertThrows(CircuitParseException.class,
				() -> new OpenQasmCircuitParser().parse(qasm));
		assertTrue(error.getMessage().contains("Measurement source and target sizes must match"));
	}

	@Test
	void rejectsBadAngleExpression() {
		String qasm = """
				OPENQASM 3.0;
				qubit[1] q;
				rz(pi / 0) q[0];
				""";

		CircuitParseException error = assertThrows(CircuitParseException.class,
				() -> new OpenQasmCircuitParser().parse(qasm));
		assertTrue(error.getMessage().contains("division by zero"));
	}

	private static void assertStateMatches(ComplexNumber[] expected, ComplexNumber[] actual) {
		assertEquals(expected.length, actual.length);
		for (int index = 0; index < expected.length; index++) {
			ComplexNumber exp = expected[index] == null ? ComplexNumber.zero() : expected[index];
			ComplexNumber act = actual[index] == null ? ComplexNumber.zero() : actual[index];
			assertEquals(exp.real(), act.real(), EPS, "real mismatch at " + index);
			assertEquals(exp.imag(), act.imag(), EPS, "imag mismatch at " + index);
		}
	}
}
