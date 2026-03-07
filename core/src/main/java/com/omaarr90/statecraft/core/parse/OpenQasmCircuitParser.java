package com.omaarr90.statecraft.core.parse;

import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class OpenQasmCircuitParser implements CircuitParser {

    @Override
    public QuantumCircuit parse(String source) {
        Objects.requireNonNull(source, "source");
        Parser parser = new Parser(source);
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
                if (measurementSeen) {
                    throw new CircuitParseException(
                            "Unitary gate cannot follow a measurement operation",
                            single.location.line(),
                            single.location.column());
                }
                int target = single.target();
                validateIndex(target, program.qubitCount(), single.location());
                circuit = switch (single.gate()) {
                    case "h" -> circuit.append(new Hadamard(), target);
                    case "x" -> circuit.append(new PauliX(), target);
                    case "y" -> circuit.append(new PauliY(), target);
                    case "z" -> circuit.append(new PauliZ(), target);
                    default -> throw new CircuitParseException(
                            "Unknown gate: " + single.gate(),
                            single.location.line(),
                            single.location.column());
                };
            } else if (instruction instanceof Instruction.Cnot cnot) {
                if (measurementSeen) {
                    throw new CircuitParseException(
                            "Unitary gate cannot follow a measurement operation",
                            cnot.location.line(),
                            cnot.location.column());
                }
                validateIndex(cnot.control(), program.qubitCount(), cnot.location());
                validateIndex(cnot.target(), program.qubitCount(), cnot.location());
                if (cnot.control() == cnot.target()) {
                    throw new CircuitParseException(
                            "CNOT control and target must be distinct",
                            cnot.location.line(),
                            cnot.location.column());
                }
                circuit = circuit.append(CnotGate.of(), cnot.control(), cnot.target());
            } else if (instruction instanceof Instruction.ControlledSingle controlled) {
                if (measurementSeen) {
                    throw new CircuitParseException(
                            "Unitary gate cannot follow a measurement operation",
                            controlled.location.line(),
                            controlled.location.column());
                }
                validateIndex(controlled.control(), program.qubitCount(), controlled.location());
                validateIndex(controlled.target(), program.qubitCount(), controlled.location());
                if (controlled.control() == controlled.target()) {
                    throw new CircuitParseException(
                            "Controlled gate qubits must be distinct",
                            controlled.location.line(),
                            controlled.location.column());
                }
                circuit = switch (controlled.gate()) {
                    case "x" -> circuit.appendControlledX(controlled.control(), controlled.target());
                    case "y" -> circuit.appendControlledY(controlled.control(), controlled.target());
                    case "z" -> circuit.appendControlledZ(controlled.control(), controlled.target());
                    default -> throw new CircuitParseException(
                            "Unknown controlled gate: c" + controlled.gate(),
                            controlled.location.line(),
                            controlled.location.column());
                };
            } else if (instruction instanceof Instruction.MultiControlX multiX) {
                if (measurementSeen) {
                    throw new CircuitParseException(
                            "Unitary gate cannot follow a measurement operation",
                            multiX.location.line(),
                            multiX.location.column());
                }
                validateIndex(multiX.target(), program.qubitCount(), multiX.location());
                int[] controls = multiX.controls();
                if (controls.length < 2) {
                    throw new CircuitParseException(
                            "Multi-control X requires at least two controls",
                            multiX.location.line(),
                            multiX.location.column());
                }
                for (int control : controls) {
                    validateIndex(control, program.qubitCount(), multiX.location());
                    if (control == multiX.target()) {
                        throw new CircuitParseException(
                                "Control and target must be distinct",
                                multiX.location.line(),
                                multiX.location.column());
                    }
                }
                circuit = circuit.appendMultiControl(new PauliX(), multiX.target(), controls);
            } else if (instruction instanceof Instruction.Swap swap) {
                if (measurementSeen) {
                    throw new CircuitParseException(
                            "Unitary gate cannot follow a measurement operation",
                            swap.location.line(),
                            swap.location.column());
                }
                validateIndex(swap.first(), program.qubitCount(), swap.location());
                validateIndex(swap.second(), program.qubitCount(), swap.location());
                if (swap.first() == swap.second()) {
                    throw new CircuitParseException(
                            "SWAP qubits must be distinct",
                            swap.location.line(),
                            swap.location.column());
                }
                circuit = circuit.appendSwap(swap.first(), swap.second());
            } else if (instruction instanceof Instruction.ControlledPhase phase) {
                if (measurementSeen) {
                    throw new CircuitParseException(
                            "Unitary gate cannot follow a measurement operation",
                            phase.location.line(),
                            phase.location.column());
                }
                validateIndex(phase.control(), program.qubitCount(), phase.location());
                validateIndex(phase.target(), program.qubitCount(), phase.location());
                if (phase.control() == phase.target()) {
                    throw new CircuitParseException(
                            "Controlled-phase qubits must be distinct",
                            phase.location.line(),
                            phase.location.column());
                }
                circuit = circuit.appendControlledPhase(phase.angle(), phase.control(), phase.target());
            } else if (instruction instanceof Instruction.Barrier) {
                // Barrier is a scheduling hint with no simulation effect.
            } else if (instruction instanceof Instruction.Measure measure) {
                measurementSeen = true;
                validateIndex(measure.qubit(), program.qubitCount(), measure.location());
                circuit = circuit.measure(measure.qubit());
            } else if (instruction instanceof Instruction.MeasureAll measureAll) {
                measurementSeen = true;
                int[] all = new int[program.qubitCount()];
                for (int index = 0; index < program.qubitCount(); index++) {
                    all[index] = index;
                }
                circuit = circuit.measure(all);
            } else {
                throw new CircuitParseException("Unsupported instruction: " + instruction);
            }
        }
        return circuit;
    }

    private static void validateIndex(int index, int qubitCount, SourceLocation location) {
        if (index < 0 || index >= qubitCount) {
            throw new CircuitParseException(
                    "Qubit index out of range: " + index,
                    location.line(),
                    location.column());
        }
    }

    private static final class Parser {

        private final Tokenizer tokenizer;
        private Token current;
        private Integer qubitCount;
        private String qubitRegister;
        private Integer bitCount;
        private String bitRegister;

        private Parser(String source) {
            this.tokenizer = new Tokenizer(source);
            this.current = tokenizer.nextToken();
        }

        private Program parseProgram() {
            expectIdentifier("OPENQASM");
            Token versionToken = expect(TokenType.NUMBER, "Expected OpenQASM version number");
            double version = parseDouble(versionToken);
            if (Math.abs(version - 3.0) > 1e-9) {
                throw new CircuitParseException(
                        "Only OPENQASM 3.0 is supported",
                        versionToken.line,
                        versionToken.column);
            }
            expectSymbol(";");

            List<Instruction> instructions = new ArrayList<>();
            boolean instructionsStarted = false;

            while (current.type != TokenType.EOF) {
                if (isIdentifier("qubit")) {
                    if (instructionsStarted) {
                        throw error("Qubit declaration must appear before instructions", current);
                    }
                    parseQubitDeclaration();
                    continue;
                }
                if (isIdentifier("bit")) {
                    if (instructionsStarted) {
                        throw error("Bit declaration must appear before instructions", current);
                    }
                    parseBitDeclaration();
                    continue;
                }
                instructionsStarted = true;
                instructions.add(parseInstruction());
            }

            if (qubitCount == null || qubitRegister == null) {
                throw new CircuitParseException("Missing qubit register declaration");
            }

            return new Program(
                    qubitCount,
                    qubitRegister,
                    bitCount,
                    bitRegister,
                    instructions);
        }

        private void parseQubitDeclaration() {
            Token keyword = expectIdentifier("qubit");
            expectSymbol("[");
            Token sizeToken = expect(TokenType.NUMBER, "Expected qubit register size");
            int size = parseInteger(sizeToken);
            expectSymbol("]");
            Token nameToken = expect(TokenType.IDENTIFIER, "Expected qubit register name");
            String name = nameToken.text;
            expectSymbol(";");

            if (!"q".equals(name)) {
                throw new CircuitParseException(
                        "Only a qubit register named 'q' is supported",
                        nameToken.line,
                        nameToken.column);
            }
            if (size <= 0) {
                throw new CircuitParseException(
                        "Qubit register size must be positive",
                        sizeToken.line,
                        sizeToken.column);
            }
            if (qubitCount != null) {
                throw new CircuitParseException(
                        "Multiple qubit register declarations are not supported",
                        keyword.line,
                        keyword.column);
            }
            qubitCount = size;
            qubitRegister = name;
        }

        private void parseBitDeclaration() {
            Token keyword = expectIdentifier("bit");
            expectSymbol("[");
            Token sizeToken = expect(TokenType.NUMBER, "Expected bit register size");
            int size = parseInteger(sizeToken);
            expectSymbol("]");
            Token nameToken = expect(TokenType.IDENTIFIER, "Expected bit register name");
            String name = nameToken.text;
            expectSymbol(";");

            if (!"c".equals(name)) {
                throw new CircuitParseException(
                        "Only a bit register named 'c' is supported",
                        nameToken.line,
                        nameToken.column);
            }
            if (size <= 0) {
                throw new CircuitParseException(
                        "Bit register size must be positive",
                        sizeToken.line,
                        sizeToken.column);
            }
            if (bitCount != null) {
                throw new CircuitParseException(
                        "Multiple bit register declarations are not supported",
                        keyword.line,
                        keyword.column);
            }
            bitCount = size;
            bitRegister = name;
        }

        private Instruction parseInstruction() {
            Token gateToken = expect(TokenType.IDENTIFIER, "Expected instruction");
            String gate = gateToken.text.toLowerCase(Locale.ROOT);
            SourceLocation location = new SourceLocation(gateToken.line, gateToken.column);

            return switch (gate) {
                case "h", "x", "y", "z" -> {
                    int target = parseQubitOperand();
                    expectSymbol(";");
                    yield new Instruction.SingleGate(gate, target, location);
                }
                case "cx" -> {
                    int control = parseQubitOperand();
                    expectSymbol(",");
                    int target = parseQubitOperand();
                    expectSymbol(";");
                    yield new Instruction.Cnot(control, target, location);
                }
                case "cy", "cz" -> {
                    int control = parseQubitOperand();
                    expectSymbol(",");
                    int target = parseQubitOperand();
                    expectSymbol(";");
                    yield new Instruction.ControlledSingle(gate.substring(1), control, target, location);
                }
                case "ccx" -> {
                    int firstControl = parseQubitOperand();
                    expectSymbol(",");
                    int secondControl = parseQubitOperand();
                    expectSymbol(",");
                    int target = parseQubitOperand();
                    expectSymbol(";");
                    yield new Instruction.MultiControlX(new int[] {firstControl, secondControl}, target, location);
                }
                case "swap" -> {
                    int first = parseQubitOperand();
                    expectSymbol(",");
                    int second = parseQubitOperand();
                    expectSymbol(";");
                    yield new Instruction.Swap(first, second, location);
                }
                case "cp" -> {
                    expectSymbol("(");
                    double angle = parseAngle();
                    expectSymbol(")");
                    int control = parseQubitOperand();
                    expectSymbol(",");
                    int target = parseQubitOperand();
                    expectSymbol(";");
                    yield new Instruction.ControlledPhase(angle, control, target, location);
                }
                case "barrier" -> {
                    parseBarrierOperands();
                    expectSymbol(";");
                    yield new Instruction.Barrier(location);
                }
                case "measure" -> {
                    Token registerToken = expect(TokenType.IDENTIFIER, "Expected qubit register");
                    if (!"q".equals(registerToken.text)) {
                        throw new CircuitParseException(
                                "Unknown qubit register: " + registerToken.text,
                                registerToken.line,
                                registerToken.column);
                    }
                    if (matchSymbol("[")) {
                        Token indexToken = expect(TokenType.NUMBER, "Expected qubit index");
                        int qubit = parseInteger(indexToken);
                        expectSymbol("]");
                        if (matchSymbol("->")) {
                            parseClassicalOperand();
                        }
                        expectSymbol(";");
                        yield new Instruction.Measure(qubit, location);
                    }
                    if (matchSymbol("->")) {
                        throw new CircuitParseException(
                                "Register-wide measurement does not support classical targets",
                                current.line,
                                current.column);
                    }
                    expectSymbol(";");
                    yield new Instruction.MeasureAll(location);
                }
                default -> throw new CircuitParseException(
                        "Unsupported instruction: " + gateToken.text,
                        gateToken.line,
                        gateToken.column);
            };
        }

        private void parseBarrierOperands() {
            Token registerToken = expect(TokenType.IDENTIFIER, "Expected barrier operand");
            if (!"q".equals(registerToken.text)) {
                throw new CircuitParseException(
                        "Unknown qubit register: " + registerToken.text,
                        registerToken.line,
                        registerToken.column);
            }
            if (matchSymbol("[")) {
                Token indexToken = expect(TokenType.NUMBER, "Expected qubit index");
                parseInteger(indexToken);
                expectSymbol("]");
                while (matchSymbol(",")) {
                    parseQubitOperand();
                }
                return;
            }
            while (matchSymbol(",")) {
                parseQubitOperand();
            }
        }

        private int parseQubitOperand() {
            Token register = expect(TokenType.IDENTIFIER, "Expected qubit register");
            if (!"q".equals(register.text)) {
                throw new CircuitParseException(
                        "Unknown qubit register: " + register.text,
                        register.line,
                        register.column);
            }
            expectSymbol("[");
            Token indexToken = expect(TokenType.NUMBER, "Expected qubit index");
            int index = parseInteger(indexToken);
            expectSymbol("]");
            return index;
        }

        private void parseClassicalOperand() {
            Token register = expect(TokenType.IDENTIFIER, "Expected bit register");
            if (bitRegister == null || bitCount == null) {
                throw new CircuitParseException(
                        "Bit register must be declared before measurement targets",
                        register.line,
                        register.column);
            }
            if (!bitRegister.equals(register.text)) {
                throw new CircuitParseException(
                        "Unknown bit register: " + register.text,
                        register.line,
                        register.column);
            }
            expectSymbol("[");
            Token indexToken = expect(TokenType.NUMBER, "Expected bit index");
            int index = parseInteger(indexToken);
            expectSymbol("]");
            if (index < 0 || index >= bitCount) {
                throw new CircuitParseException(
                        "Bit index out of range: " + index,
                        indexToken.line,
                        indexToken.column);
            }
        }

        private double parseAngle() {
            boolean negative = matchSymbol("-");
            if (current.type == TokenType.IDENTIFIER && "pi".equals(current.text)) {
                advance();
                double value = Math.PI;
                if (matchSymbol("/")) {
                    Token denomToken = expect(TokenType.NUMBER, "Expected denominator after '/'");
                    int denom = parseInteger(denomToken);
                    if (denom == 0) {
                        throw new CircuitParseException("pi division by zero", denomToken.line, denomToken.column);
                    }
                    value /= denom;
                }
                return negative ? -value : value;
            }
            if (current.type == TokenType.NUMBER) {
                Token numberToken = current;
                advance();
                double parsed = parseDouble(numberToken);
                return negative ? -parsed : parsed;
            }
            throw error("Expected angle literal", current);
        }

        private Token expectIdentifier(String expected) {
            Token token = expect(TokenType.IDENTIFIER, "Expected identifier");
            if (!expected.equals(token.text)) {
                throw new CircuitParseException(
                        "Expected '" + expected + "'",
                        token.line,
                        token.column);
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
            if (token.text.contains(".")) {
                throw new CircuitParseException(
                        "Expected integer but got '" + token.text + "'",
                        token.line,
                        token.column);
            }
            try {
                return Integer.parseInt(token.text);
            } catch (NumberFormatException e) {
                throw new CircuitParseException(
                        "Invalid integer: " + token.text,
                        token.line,
                        token.column,
                        e);
            }
        }

        private double parseDouble(Token token) {
            try {
                return Double.parseDouble(token.text);
            } catch (NumberFormatException e) {
                throw new CircuitParseException(
                        "Invalid number: " + token.text,
                        token.line,
                        token.column,
                        e);
            }
        }

        private CircuitParseException error(String message, Token token) {
            return new CircuitParseException(message, token.line, token.column);
        }
    }

    private record Program(
            int qubitCount,
            String qubitRegister,
            Integer bitCount,
            String bitRegister,
            List<Instruction> instructions) {
        private Program {
            instructions = instructions == null ? List.of() : List.copyOf(instructions);
        }
    }

    private record SourceLocation(int line, int column) {
    }

    private sealed interface Instruction permits Instruction.SingleGate,
            Instruction.Cnot,
            Instruction.ControlledSingle,
            Instruction.MultiControlX,
            Instruction.Swap,
            Instruction.ControlledPhase,
            Instruction.Barrier,
            Instruction.Measure,
            Instruction.MeasureAll {

        SourceLocation location();

        record SingleGate(String gate, int target, SourceLocation location) implements Instruction {
        }

        record Cnot(int control, int target, SourceLocation location) implements Instruction {
        }

        record ControlledSingle(String gate, int control, int target, SourceLocation location) implements Instruction {
        }

        record MultiControlX(int[] controls, int target, SourceLocation location) implements Instruction {
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

        record Measure(int qubit, SourceLocation location) implements Instruction {
        }

        record MeasureAll(SourceLocation location) implements Instruction {
        }
    }

    private enum TokenType {
        IDENTIFIER,
        NUMBER,
        SYMBOL,
        EOF
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
            if (Character.isDigit(ch) || (ch == '.' && index + 1 < length && Character.isDigit(source.charAt(index + 1)))) {
                String text = readNumber();
                return new Token(TokenType.NUMBER, text, startLine, startColumn);
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
            throw new CircuitParseException(
                    "Unexpected character: '" + ch + "'",
                    startLine,
                    startColumn);
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
            while (index < length) {
                char ch = source.charAt(index);
                if (Character.isDigit(ch)) {
                    advance();
                    continue;
                }
                if (ch == '.' && !seenDot) {
                    seenDot = true;
                    advance();
                    continue;
                }
                break;
            }
            return source.substring(start, index);
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
            return ch == ';'
                    || ch == ','
                    || ch == '['
                    || ch == ']'
                    || ch == '('
                    || ch == ')'
                    || ch == '/'
                    || ch == '-';
        }
    }
}
