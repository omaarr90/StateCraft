package com.omaarr90.statecraft.core.parse;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.MatrixSingleQubitGate;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.SGate;
import com.omaarr90.statecraft.quantum.SdgGate;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class OpenQasmCircuitParser implements CircuitParser {

	private static final double VERSION_EPS = 1e-9;
	private static final ComplexNumber ZERO = ComplexNumber.zero();
	private static final ComplexNumber ONE = ComplexNumber.one();

	@Override
	public QuantumCircuit parse(String source) {
		Objects.requireNonNull(source, "source");
		Parser parser = new Parser(normalizeSource(source));
		Program program = parser.parseProgram();
		return buildCircuit(program);
	}

	private QuantumCircuit buildCircuit(Program program) {
		if (program.qubitCount() <= 0) {
			throw new CircuitParseException("qubit register size must be positive");
		}
		QuantumCircuit circuit = new QuantumCircuit(program.qubitCount());
		boolean measurementSeen = false;
		for (Instruction instruction : program.instructions()) {
			if (instruction instanceof Instruction.SingleGate single) {
				rejectUnitaryAfterMeasurement(measurementSeen, single.location());
				validateIndex(single.target(), program.qubitCount(), single.location());
				circuit = circuit.append(single.gate(), single.target());
			} else if (instruction instanceof Instruction.UnitaryNoOp noOp) {
				rejectUnitaryAfterMeasurement(measurementSeen, noOp.location());
				validateIndex(noOp.target(), program.qubitCount(), noOp.location());
			} else if (instruction instanceof Instruction.Cnot cnot) {
				rejectUnitaryAfterMeasurement(measurementSeen, cnot.location());
				validateTwoQubitOperands(cnot.control(), cnot.target(), program.qubitCount(), "CNOT control and target",
						cnot.location());
				circuit = circuit.append(CnotGate.of(), cnot.control(), cnot.target());
			} else if (instruction instanceof Instruction.ControlledSingle controlled) {
				rejectUnitaryAfterMeasurement(measurementSeen, controlled.location());
				validateTwoQubitOperands(controlled.control(), controlled.target(), program.qubitCount(),
						"Controlled gate qubits", controlled.location());
				circuit = switch (controlled.gate()) {
					case "x" -> circuit.appendControlledX(controlled.control(), controlled.target());
					case "y" -> circuit.appendControlledY(controlled.control(), controlled.target());
					case "z" -> circuit.appendControlledZ(controlled.control(), controlled.target());
					default -> throw new CircuitParseException("Unknown controlled gate: c" + controlled.gate(),
							controlled.location().line(), controlled.location().column());
				};
			} else if (instruction instanceof Instruction.MultiControl multi) {
				rejectUnitaryAfterMeasurement(measurementSeen, multi.location());
				validateMultiControl(multi.controls(), multi.target(), program.qubitCount(), multi.location());
				circuit = switch (multi.gate()) {
					case "x" -> circuit.appendMultiControl(new PauliX(), multi.target(), multi.controls());
					case "y" -> circuit.appendMultiControl(new PauliY(), multi.target(), multi.controls());
					case "z" -> circuit.appendMultiControl(new PauliZ(), multi.target(), multi.controls());
					default -> throw new CircuitParseException("Unknown multi-control gate: mc" + multi.gate(),
							multi.location().line(), multi.location().column());
				};
			} else if (instruction instanceof Instruction.Swap swap) {
				rejectUnitaryAfterMeasurement(measurementSeen, swap.location());
				validateTwoQubitOperands(swap.first(), swap.second(), program.qubitCount(), "SWAP qubits",
						swap.location());
				circuit = circuit.appendSwap(swap.first(), swap.second());
			} else if (instruction instanceof Instruction.ControlledPhase phase) {
				rejectUnitaryAfterMeasurement(measurementSeen, phase.location());
				validateTwoQubitOperands(phase.control(), phase.target(), program.qubitCount(),
						"Controlled-phase qubits", phase.location());
				circuit = circuit.appendControlledPhase(phase.angle(), phase.control(), phase.target());
			} else if (instruction instanceof Instruction.Barrier) {
				// Barrier is a scheduling hint with no simulation effect.
			} else if (instruction instanceof Instruction.Measure measure) {
				measurementSeen = true;
				for (int qubit : measure.qubits()) {
					validateIndex(qubit, program.qubitCount(), measure.location());
				}
				circuit = circuit.measure(measure.qubits());
			} else {
				throw new CircuitParseException("Unsupported instruction: " + instruction);
			}
		}
		return circuit;
	}

	private static void rejectUnitaryAfterMeasurement(boolean measurementSeen, SourceLocation location) {
		if (measurementSeen) {
			throw new CircuitParseException("Unitary gate cannot follow a measurement operation", location.line(),
					location.column());
		}
	}

	private static void validateTwoQubitOperands(int first, int second, int qubitCount, String label,
			SourceLocation location) {
		validateIndex(first, qubitCount, location);
		validateIndex(second, qubitCount, location);
		if (first == second) {
			throw new CircuitParseException(label + " must be distinct", location.line(), location.column());
		}
	}

	private static void validateMultiControl(int[] controls, int target, int qubitCount, SourceLocation location) {
		if (controls.length == 0) {
			throw new CircuitParseException("Multi-control gate requires at least one control", location.line(),
					location.column());
		}
		validateIndex(target, qubitCount, location);
		int[] sorted = controls.clone();
		Arrays.sort(sorted);
		for (int index = 0; index < sorted.length; index++) {
			int control = sorted[index];
			validateIndex(control, qubitCount, location);
			if (control == target) {
				throw new CircuitParseException("Control and target must be distinct", location.line(),
						location.column());
			}
			if (index > 0 && control == sorted[index - 1]) {
				throw new CircuitParseException("Duplicate control qubit: " + control, location.line(),
						location.column());
			}
		}
	}

	private static void validateIndex(int index, int qubitCount, SourceLocation location) {
		if (index < 0 || index >= qubitCount) {
			throw new CircuitParseException("Qubit index out of range: " + index, location.line(), location.column());
		}
	}

	private static String normalizeSource(String source) {
		if (!source.isEmpty() && source.charAt(0) == '\uFEFF') {
			return source.substring(1);
		}
		return source;
	}

	private static final class Parser {

		private final Tokenizer tokenizer;
		private final Map<String, Register> qubitRegisters = new LinkedHashMap<>();
		private final Map<String, Register> bitRegisters = new LinkedHashMap<>();
		private Token current;
		private int qubitCount;
		private int bitCount;

		private Parser(String source) {
			this.tokenizer = new Tokenizer(source);
			this.current = tokenizer.nextToken();
		}

		private Program parseProgram() {
			expectIdentifier("OPENQASM");
			Token versionToken = expect(TokenType.NUMBER, "Expected OpenQASM version number");
			double version = parseDouble(versionToken);
			if (Math.abs(version - 2.0) > VERSION_EPS && Math.abs(version - 3.0) > VERSION_EPS) {
				throw new CircuitParseException("Only OPENQASM 2.0 and 3.0 are supported", versionToken.line,
						versionToken.column);
			}
			expectSymbol(";");

			List<Instruction> instructions = new ArrayList<>();
			boolean instructionsStarted = false;
			while (current.type != TokenType.EOF) {
				if (isIdentifier("include")) {
					parseInclude();
					continue;
				}
				if (isIdentifier("qreg")) {
					rejectDeclarationAfterInstruction(instructionsStarted, current);
					parseLegacyRegisterDeclaration(RegisterKind.QUBIT);
					continue;
				}
				if (isIdentifier("creg")) {
					rejectDeclarationAfterInstruction(instructionsStarted, current);
					parseLegacyRegisterDeclaration(RegisterKind.BIT);
					continue;
				}
				if (isIdentifier("qubit")) {
					rejectDeclarationAfterInstruction(instructionsStarted, current);
					parseModernRegisterDeclaration(RegisterKind.QUBIT);
					continue;
				}
				if (isIdentifier("bit")) {
					rejectDeclarationAfterInstruction(instructionsStarted, current);
					parseModernRegisterDeclaration(RegisterKind.BIT);
					continue;
				}
				instructionsStarted = true;
				instructions.add(parseInstruction());
			}

			if (qubitRegisters.isEmpty()) {
				throw new CircuitParseException("Missing qubit register declaration");
			}
			return new Program(qubitCount, instructions);
		}

		private void rejectDeclarationAfterInstruction(boolean instructionsStarted, Token token) {
			if (instructionsStarted) {
				throw new CircuitParseException("Register declarations must appear before instructions", token.line,
						token.column);
			}
		}

		private void parseInclude() {
			expectIdentifier("include");
			expect(TokenType.STRING, "Expected include file name");
			expectSymbol(";");
		}

		private void parseLegacyRegisterDeclaration(RegisterKind kind) {
			Token keyword = expectIdentifier(kind == RegisterKind.QUBIT ? "qreg" : "creg");
			Token nameToken = expect(TokenType.IDENTIFIER, "Expected register name");
			expectSymbol("[");
			Token sizeToken = expect(TokenType.NUMBER, "Expected register size");
			int size = parseInteger(sizeToken);
			expectSymbol("]");
			expectSymbol(";");
			addRegister(kind, nameToken.text, size, new SourceLocation(keyword.line, keyword.column),
					new SourceLocation(sizeToken.line, sizeToken.column));
		}

		private void parseModernRegisterDeclaration(RegisterKind kind) {
			Token keyword = expectIdentifier(kind == RegisterKind.QUBIT ? "qubit" : "bit");
			int size = 1;
			SourceLocation sizeLocation = new SourceLocation(keyword.line, keyword.column);
			if (matchSymbol("[")) {
				Token sizeToken = expect(TokenType.NUMBER, "Expected register size");
				size = parseInteger(sizeToken);
				sizeLocation = new SourceLocation(sizeToken.line, sizeToken.column);
				expectSymbol("]");
			}
			Token nameToken = expect(TokenType.IDENTIFIER, "Expected register name");
			expectSymbol(";");
			addRegister(kind, nameToken.text, size, new SourceLocation(keyword.line, keyword.column), sizeLocation);
		}

		private void addRegister(RegisterKind kind, String name, int size, SourceLocation declarationLocation,
				SourceLocation sizeLocation) {
			if (size <= 0) {
				throw new CircuitParseException("Register size must be positive", sizeLocation.line(),
						sizeLocation.column());
			}
			Map<String, Register> registers = kind == RegisterKind.QUBIT ? qubitRegisters : bitRegisters;
			if (registers.containsKey(name)) {
				throw new CircuitParseException("Duplicate register declaration: " + name, declarationLocation.line(),
						declarationLocation.column());
			}
			int offset = kind == RegisterKind.QUBIT ? qubitCount : bitCount;
			registers.put(name, new Register(name, size, offset));
			if (kind == RegisterKind.QUBIT) {
				qubitCount += size;
			} else {
				bitCount += size;
			}
		}

		private Instruction parseInstruction() {
			Token gateToken = expect(TokenType.IDENTIFIER, "Expected instruction");
			String gate = gateToken.text.toLowerCase(Locale.ROOT);
			SourceLocation location = new SourceLocation(gateToken.line, gateToken.column);

			return switch (gate) {
				case "barrier" -> {
					parseBarrierOperands();
					expectSymbol(";");
					yield new Instruction.Barrier(location);
				}
				case "measure" -> parseMeasurement(location);
				case "gate", "opaque", "if", "reset", "def", "defcal", "for", "while", "box", "delay", "cal", "let",
						"const" ->
					throw new CircuitParseException("Unsupported OpenQASM construct: " + gateToken.text, gateToken.line,
							gateToken.column);
				default -> parseGateInstruction(gate, location);
			};
		}

		private Instruction parseGateInstruction(String gate, SourceLocation location) {
			List<Double> parameters = parseOptionalParameterList();
			List<Integer> qubits = parseGateQubitOperands();
			expectSymbol(";");

			return switch (gate) {
				case "h", "x", "y", "z", "s", "sdg", "t", "tdg" -> {
					requireParameterCount(gate, parameters, 0, location);
					requireQubitCount(gate, qubits, 1, location);
					yield new Instruction.SingleGate(singleGate(gate, List.of(), location), qubits.get(0), location);
				}
				case "id", "iden" -> {
					requireParameterCount(gate, parameters, 0, location);
					requireQubitCount(gate, qubits, 1, location);
					yield new Instruction.UnitaryNoOp(qubits.get(0), location);
				}
				case "p", "u1", "rx", "ry", "rz" -> {
					requireParameterCount(gate, parameters, 1, location);
					requireQubitCount(gate, qubits, 1, location);
					yield new Instruction.SingleGate(singleGate(gate, parameters, location), qubits.get(0), location);
				}
				case "u", "u3" -> {
					requireParameterCount(gate, parameters, 3, location);
					requireQubitCount(gate, qubits, 1, location);
					yield new Instruction.SingleGate(singleGate(gate, parameters, location), qubits.get(0), location);
				}
				case "u2" -> {
					requireParameterCount(gate, parameters, 2, location);
					requireQubitCount(gate, qubits, 1, location);
					yield new Instruction.SingleGate(singleGate(gate, parameters, location), qubits.get(0), location);
				}
				case "cx", "cnot" -> {
					requireParameterCount(gate, parameters, 0, location);
					requireQubitCount(gate, qubits, 2, location);
					yield new Instruction.Cnot(qubits.get(0), qubits.get(1), location);
				}
				case "cy", "cz" -> {
					requireParameterCount(gate, parameters, 0, location);
					requireQubitCount(gate, qubits, 2, location);
					yield new Instruction.ControlledSingle(gate.substring(1), qubits.get(0), qubits.get(1), location);
				}
				case "ccx" -> {
					requireParameterCount(gate, parameters, 0, location);
					requireQubitCount(gate, qubits, 3, location);
					yield new Instruction.MultiControl("x", new int[]{qubits.get(0), qubits.get(1)}, qubits.get(2),
							location);
				}
				case "mcx", "mcy", "mcz" -> {
					requireParameterCount(gate, parameters, 0, location);
					if (qubits.size() < 2) {
						throw new CircuitParseException("Gate '" + gate + "' requires controls and a target",
								location.line(), location.column());
					}
					int[] controls = new int[qubits.size() - 1];
					for (int index = 0; index < controls.length; index++) {
						controls[index] = qubits.get(index);
					}
					yield new Instruction.MultiControl(gate.substring(2), controls, qubits.get(qubits.size() - 1),
							location);
				}
				case "swap" -> {
					requireParameterCount(gate, parameters, 0, location);
					requireQubitCount(gate, qubits, 2, location);
					yield new Instruction.Swap(qubits.get(0), qubits.get(1), location);
				}
				case "cp", "cu1" -> {
					requireParameterCount(gate, parameters, 1, location);
					requireQubitCount(gate, qubits, 2, location);
					yield new Instruction.ControlledPhase(parameters.get(0), qubits.get(0), qubits.get(1), location);
				}
				default -> throw new CircuitParseException("Unsupported instruction: " + gate, location.line(),
						location.column());
			};
		}

		private List<Double> parseOptionalParameterList() {
			if (!matchSymbol("(")) {
				return List.of();
			}
			List<Double> parameters = new ArrayList<>();
			if (!isSymbol(")")) {
				do {
					double value = parseExpression();
					if (!Double.isFinite(value)) {
						throw new CircuitParseException("Angle expression must be finite", current.line,
								current.column);
					}
					parameters.add(value);
				} while (matchSymbol(","));
			}
			expectSymbol(")");
			return parameters;
		}

		private List<Integer> parseGateQubitOperands() {
			List<Integer> qubits = new ArrayList<>();
			qubits.add(parseSingleQubitOperand());
			while (matchSymbol(",")) {
				qubits.add(parseSingleQubitOperand());
			}
			return qubits;
		}

		private Instruction parseMeasurement(SourceLocation location) {
			QubitOperand qubits = parseQubitOperand(true);
			if (matchSymbol("->")) {
				ClassicalOperand bits = parseClassicalOperand(true);
				if (qubits.size() != bits.size()) {
					throw new CircuitParseException("Measurement source and target sizes must match", location.line(),
							location.column());
				}
			}
			expectSymbol(";");
			return new Instruction.Measure(qubits.qubits(), location);
		}

		private void parseBarrierOperands() {
			parseQubitOperand(true);
			while (matchSymbol(",")) {
				parseQubitOperand(true);
			}
		}

		private int parseSingleQubitOperand() {
			QubitOperand operand = parseQubitOperand(false);
			return operand.qubits()[0];
		}

		private QubitOperand parseQubitOperand(boolean allowRegisterWide) {
			Token registerToken = expect(TokenType.IDENTIFIER, "Expected qubit register");
			Register register = qubitRegisters.get(registerToken.text);
			if (register == null) {
				throw new CircuitParseException("Unknown qubit register: " + registerToken.text, registerToken.line,
						registerToken.column);
			}
			if (matchSymbol("[")) {
				Token indexToken = expect(TokenType.NUMBER, "Expected qubit index");
				int index = parseInteger(indexToken);
				expectSymbol("]");
				validateRegisterIndex(register, index, "Qubit", indexToken);
				return new QubitOperand(new int[]{register.offset() + index});
			}
			if (!allowRegisterWide) {
				throw new CircuitParseException("Expected indexed qubit operand", registerToken.line,
						registerToken.column);
			}
			return new QubitOperand(register.flatIndices());
		}

		private ClassicalOperand parseClassicalOperand(boolean allowRegisterWide) {
			Token registerToken = expect(TokenType.IDENTIFIER, "Expected bit register");
			Register register = bitRegisters.get(registerToken.text);
			if (register == null) {
				throw new CircuitParseException("Unknown bit register: " + registerToken.text, registerToken.line,
						registerToken.column);
			}
			if (matchSymbol("[")) {
				Token indexToken = expect(TokenType.NUMBER, "Expected bit index");
				int index = parseInteger(indexToken);
				expectSymbol("]");
				validateRegisterIndex(register, index, "Bit", indexToken);
				return new ClassicalOperand(1);
			}
			if (!allowRegisterWide) {
				throw new CircuitParseException("Expected indexed bit operand", registerToken.line,
						registerToken.column);
			}
			return new ClassicalOperand(register.size());
		}

		private void validateRegisterIndex(Register register, int index, String label, Token token) {
			if (index < 0 || index >= register.size()) {
				throw new CircuitParseException(label + " index out of range: " + index, token.line, token.column);
			}
		}

		private double parseExpression() {
			return parseAdditiveExpression();
		}

		private double parseAdditiveExpression() {
			double value = parseMultiplicativeExpression();
			while (true) {
				if (matchSymbol("+")) {
					value += parseMultiplicativeExpression();
				} else if (matchSymbol("-")) {
					value -= parseMultiplicativeExpression();
				} else {
					return value;
				}
			}
		}

		private double parseMultiplicativeExpression() {
			double value = parseUnaryExpression();
			while (true) {
				if (matchSymbol("*")) {
					value *= parseUnaryExpression();
				} else if (isSymbol("/")) {
					Token operator = current;
					advance();
					double divisor = parseUnaryExpression();
					if (Math.abs(divisor) == 0.0) {
						throw new CircuitParseException("Angle expression division by zero", operator.line,
								operator.column);
					}
					value /= divisor;
				} else {
					return value;
				}
			}
		}

		private double parseUnaryExpression() {
			if (matchSymbol("+")) {
				return parseUnaryExpression();
			}
			if (matchSymbol("-")) {
				return -parseUnaryExpression();
			}
			return parsePrimaryExpression();
		}

		private double parsePrimaryExpression() {
			if (current.type == TokenType.NUMBER) {
				Token number = current;
				advance();
				return parseDouble(number);
			}
			if (current.type == TokenType.IDENTIFIER && "pi".equals(current.text)) {
				advance();
				return Math.PI;
			}
			if (matchSymbol("(")) {
				double value = parseExpression();
				expectSymbol(")");
				return value;
			}
			throw error("Expected angle expression", current);
		}

		private SingleQubitGate singleGate(String gate, List<Double> parameters, SourceLocation location) {
			return switch (gate) {
				case "h" -> new Hadamard();
				case "x" -> new PauliX();
				case "y" -> new PauliY();
				case "z" -> new PauliZ();
				case "s" -> new SGate();
				case "sdg" -> new SdgGate();
				case "t" -> phaseGate("T", Math.PI / 4.0);
				case "tdg" -> phaseGate("Tdg", -Math.PI / 4.0);
				case "p", "u1" -> phaseGate(gate, parameters.get(0));
				case "rx" -> rotationX(parameters.get(0));
				case "ry" -> rotationY(parameters.get(0));
				case "rz" -> rotationZ(parameters.get(0));
				case "u" -> uGate("U", parameters.get(0), parameters.get(1), parameters.get(2));
				case "u3" -> uGate("u3", parameters.get(0), parameters.get(1), parameters.get(2));
				case "u2" -> uGate("u2", Math.PI / 2.0, parameters.get(0), parameters.get(1));
				default -> throw new CircuitParseException("Unsupported single-qubit gate: " + gate, location.line(),
						location.column());
			};
		}

		private void requireParameterCount(String gate, List<Double> parameters, int expected,
				SourceLocation location) {
			if (parameters.size() != expected) {
				throw new CircuitParseException(
						"Gate '" + gate + "' expects " + expected + " parameter(s), got " + parameters.size(),
						location.line(), location.column());
			}
		}

		private void requireQubitCount(String gate, List<Integer> qubits, int expected, SourceLocation location) {
			if (qubits.size() != expected) {
				throw new CircuitParseException(
						"Gate '" + gate + "' expects " + expected + " qubit operand(s), got " + qubits.size(),
						location.line(), location.column());
			}
		}

		private Token expectIdentifier(String expected) {
			Token token = expect(TokenType.IDENTIFIER, "Expected identifier");
			if (!expected.equals(token.text)) {
				throw new CircuitParseException("Expected '" + expected + "'", token.line, token.column);
			}
			return token;
		}

		private boolean isIdentifier(String value) {
			return current.type == TokenType.IDENTIFIER && value.equals(current.text);
		}

		private Token expect(TokenType type, String message) {
			if (current.type != type) {
				throw error(message, current);
			}
			Token token = current;
			advance();
			return token;
		}

		private void expectSymbol(String symbol) {
			if (!isSymbol(symbol)) {
				throw error("Expected '" + symbol + "'", current);
			}
			advance();
		}

		private boolean matchSymbol(String symbol) {
			if (isSymbol(symbol)) {
				advance();
				return true;
			}
			return false;
		}

		private boolean isSymbol(String symbol) {
			return current.type == TokenType.SYMBOL && symbol.equals(current.text);
		}

		private void advance() {
			current = tokenizer.nextToken();
		}

		private int parseInteger(Token token) {
			if (token.text.contains(".") || token.text.contains("e") || token.text.contains("E")) {
				throw new CircuitParseException("Expected integer but got '" + token.text + "'", token.line,
						token.column);
			}
			try {
				return Integer.parseInt(token.text);
			} catch (NumberFormatException e) {
				throw new CircuitParseException("Invalid integer: " + token.text, token.line, token.column, e);
			}
		}

		private double parseDouble(Token token) {
			try {
				double value = Double.parseDouble(token.text);
				if (!Double.isFinite(value)) {
					throw new CircuitParseException("Invalid finite number: " + token.text, token.line, token.column);
				}
				return value;
			} catch (NumberFormatException e) {
				throw new CircuitParseException("Invalid number: " + token.text, token.line, token.column, e);
			}
		}

		private CircuitParseException error(String message, Token token) {
			return new CircuitParseException(message, token.line, token.column);
		}
	}

	private static SingleQubitGate phaseGate(String name, double angle) {
		return new MatrixSingleQubitGate(name, ONE, ZERO, ZERO, cis(angle));
	}

	private static SingleQubitGate rotationX(double angle) {
		double half = angle / 2.0;
		double cos = Math.cos(half);
		double sin = Math.sin(half);
		ComplexNumber c = new ComplexNumber(cos, 0.0);
		ComplexNumber minusISin = new ComplexNumber(0.0, -sin);
		return new MatrixSingleQubitGate("rx", c, minusISin, minusISin, c);
	}

	private static SingleQubitGate rotationY(double angle) {
		double half = angle / 2.0;
		double cos = Math.cos(half);
		double sin = Math.sin(half);
		return new MatrixSingleQubitGate("ry", new ComplexNumber(cos, 0.0), new ComplexNumber(-sin, 0.0),
				new ComplexNumber(sin, 0.0), new ComplexNumber(cos, 0.0));
	}

	private static SingleQubitGate rotationZ(double angle) {
		double half = angle / 2.0;
		return new MatrixSingleQubitGate("rz", cis(-half), ZERO, ZERO, cis(half));
	}

	private static SingleQubitGate uGate(String name, double theta, double phi, double lambda) {
		double cos = Math.cos(theta / 2.0);
		double sin = Math.sin(theta / 2.0);
		ComplexNumber m00 = new ComplexNumber(cos, 0.0);
		ComplexNumber m01 = cis(lambda).scale(-sin);
		ComplexNumber m10 = cis(phi).scale(sin);
		ComplexNumber m11 = cis(phi + lambda).scale(cos);
		return new MatrixSingleQubitGate(name, m00, m01, m10, m11);
	}

	private static ComplexNumber cis(double angle) {
		return new ComplexNumber(Math.cos(angle), Math.sin(angle));
	}

	private enum RegisterKind {
		QUBIT, BIT
	}

	private record Program(int qubitCount, List<Instruction> instructions) {
		private Program {
			instructions = instructions == null ? List.of() : List.copyOf(instructions);
		}
	}

	private record Register(String name, int size, int offset) {

		private int[] flatIndices() {
			int[] indices = new int[size];
			for (int index = 0; index < size; index++) {
				indices[index] = offset + index;
			}
			return indices;
		}
	}

	private record SourceLocation(int line, int column) {
	}

	private record QubitOperand(int[] qubits) {

		private QubitOperand {
			qubits = qubits.clone();
		}

		private int size() {
			return qubits.length;
		}

		@Override
		public int[] qubits() {
			return qubits.clone();
		}
	}

	private record ClassicalOperand(int size) {
	}

	private sealed interface Instruction permits Instruction.SingleGate, Instruction.UnitaryNoOp, Instruction.Cnot,
			Instruction.ControlledSingle, Instruction.MultiControl, Instruction.Swap, Instruction.ControlledPhase,
			Instruction.Barrier, Instruction.Measure {

		SourceLocation location();

		record SingleGate(SingleQubitGate gate, int target, SourceLocation location) implements Instruction {
		}

		record UnitaryNoOp(int target, SourceLocation location) implements Instruction {
		}

		record Cnot(int control, int target, SourceLocation location) implements Instruction {
		}

		record ControlledSingle(String gate, int control, int target, SourceLocation location) implements Instruction {
		}

		record MultiControl(String gate, int[] controls, int target, SourceLocation location) implements Instruction {
			public MultiControl {
				controls = controls.clone();
			}

			@Override
			public int[] controls() {
				return controls.clone();
			}
		}

		record Swap(int first, int second, SourceLocation location) implements Instruction {
		}

		record ControlledPhase(double angle, int control, int target, SourceLocation location) implements Instruction {
		}

		record Barrier(SourceLocation location) implements Instruction {
		}

		record Measure(int[] qubits, SourceLocation location) implements Instruction {
			public Measure {
				qubits = qubits.clone();
			}

			@Override
			public int[] qubits() {
				return qubits.clone();
			}
		}
	}

	private enum TokenType {
		IDENTIFIER, NUMBER, STRING, SYMBOL, EOF
	}

	private record Token(TokenType type, String text, int line, int column) {
	}

	private static final class Tokenizer {

		private final String source;
		private final int length;
		private int index;
		private int line;
		private int column;

		private Tokenizer(String source) {
			this.source = source;
			this.length = source.length();
			this.index = 0;
			this.line = 1;
			this.column = 1;
		}

		private Token nextToken() {
			return readToken();
		}

		private Token readToken() {
			skipWhitespaceAndComments();
			if (index >= length) {
				return new Token(TokenType.EOF, "", line, column);
			}
			char ch = source.charAt(index);
			int startLine = line;
			int startColumn = column;

			if (isIdentifierStart(ch)) {
				String text = readIdentifier();
				return new Token(TokenType.IDENTIFIER, text, startLine, startColumn);
			}
			if (Character.isDigit(ch)
					|| (ch == '.' && index + 1 < length && Character.isDigit(source.charAt(index + 1)))) {
				String text = readNumber();
				return new Token(TokenType.NUMBER, text, startLine, startColumn);
			}
			if (ch == '"') {
				String text = readString(startLine, startColumn);
				return new Token(TokenType.STRING, text, startLine, startColumn);
			}
			if (ch == '-' && index + 1 < length && source.charAt(index + 1) == '>') {
				advance();
				advance();
				return new Token(TokenType.SYMBOL, "->", startLine, startColumn);
			}
			if (isSymbolChar(ch)) {
				advance();
				return new Token(TokenType.SYMBOL, String.valueOf(ch), startLine, startColumn);
			}
			throw new CircuitParseException("Unexpected character: '" + ch + "'", startLine, startColumn);
		}

		private void skipWhitespaceAndComments() {
			boolean skipping = true;
			while (skipping && index < length) {
				skipping = false;
				while (index < length && Character.isWhitespace(source.charAt(index))) {
					advance();
					skipping = true;
				}
				if (index + 1 < length && source.charAt(index) == '/') {
					char next = source.charAt(index + 1);
					if (next == '/') {
						advance();
						advance();
						while (index < length && source.charAt(index) != '\n') {
							advance();
						}
						skipping = true;
					} else if (next == '*') {
						advance();
						advance();
						boolean closed = false;
						while (index + 1 < length) {
							if (source.charAt(index) == '*' && source.charAt(index + 1) == '/') {
								advance();
								advance();
								closed = true;
								break;
							}
							advance();
						}
						if (!closed) {
							throw new CircuitParseException("Unterminated block comment", line, column);
						}
						skipping = true;
					}
				}
			}
		}

		private String readIdentifier() {
			int start = index;
			while (index < length && isIdentifierPart(source.charAt(index))) {
				advance();
			}
			return source.substring(start, index);
		}

		private String readNumber() {
			int start = index;
			boolean seenDot = false;
			boolean seenExponent = false;
			while (index < length) {
				char ch = source.charAt(index);
				if (Character.isDigit(ch)) {
					advance();
					continue;
				}
				if (ch == '.' && !seenDot && !seenExponent) {
					seenDot = true;
					advance();
					continue;
				}
				if ((ch == 'e' || ch == 'E') && !seenExponent) {
					int exponentIndex = index + 1;
					if (exponentIndex < length
							&& (source.charAt(exponentIndex) == '+' || source.charAt(exponentIndex) == '-')) {
						exponentIndex++;
					}
					if (exponentIndex < length && Character.isDigit(source.charAt(exponentIndex))) {
						seenExponent = true;
						advance();
						if (index < length && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
							advance();
						}
						continue;
					}
				}
				break;
			}
			return source.substring(start, index);
		}

		private String readString(int startLine, int startColumn) {
			advance();
			StringBuilder builder = new StringBuilder();
			while (index < length) {
				char ch = source.charAt(index);
				if (ch == '"') {
					advance();
					return builder.toString();
				}
				if (ch == '\\') {
					advance();
					if (index >= length) {
						break;
					}
					char escaped = source.charAt(index);
					builder.append(escaped);
					advance();
					continue;
				}
				builder.append(ch);
				advance();
			}
			throw new CircuitParseException("Unterminated string literal", startLine, startColumn);
		}

		private void advance() {
			char ch = source.charAt(index++);
			if (ch == '\n') {
				line++;
				column = 1;
			} else {
				column++;
			}
		}

		private static boolean isIdentifierStart(char ch) {
			return Character.isLetter(ch) || ch == '_';
		}

		private static boolean isIdentifierPart(char ch) {
			return Character.isLetterOrDigit(ch) || ch == '_';
		}

		private static boolean isSymbolChar(char ch) {
			return ch == ';' || ch == ',' || ch == '[' || ch == ']' || ch == '(' || ch == ')' || ch == '/' || ch == '-'
					|| ch == '+' || ch == '*';
		}
	}
}
