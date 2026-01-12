package com.omaarr90.statecraft.core.parse;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class JsonCircuitParser implements CircuitParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public QuantumCircuit parse(String source) {
        Objects.requireNonNull(source, "source");
        JsonCircuitDefinition definition = parseDefinition(source);
        return buildCircuit(definition);
    }

    private JsonCircuitDefinition parseDefinition(String source) {
        JsonNode root;
        try {
            root = MAPPER.readTree(source);
        } catch (JsonProcessingException e) {
            JsonLocation location = e.getLocation();
            if (location == null) {
                throw new CircuitParseException("Invalid JSON: " + e.getOriginalMessage(), e);
            }
            throw new CircuitParseException(
                    "Invalid JSON: " + e.getOriginalMessage(),
                    (int) location.getLineNr(),
                    (int) location.getColumnNr(),
                    e);
        }

        if (root == null || root.isNull()) {
            throw new CircuitParseException("JSON source is empty");
        }
        if (!root.isObject()) {
            throw new CircuitParseException("Root JSON value must be an object");
        }

        int qubits = readRequiredInt(root, "qubits", "root");
        List<JsonOperationSpec> operations = new ArrayList<>();

        JsonNode operationsNode = root.get("operations");
        if (operationsNode != null && !operationsNode.isNull()) {
            if (!operationsNode.isArray()) {
                throw new CircuitParseException("'operations' must be an array");
            }
            int index = 0;
            for (JsonNode operationNode : operationsNode) {
                if (!operationNode.isObject()) {
                    throw new CircuitParseException("operations[" + index + "] must be an object");
                }
                String context = "operations[" + index + "]";
                String gate = readRequiredText(operationNode, "gate", context);
                Integer target = readOptionalInt(operationNode, "target", context);
                Integer control = readOptionalInt(operationNode, "control", context);
                Integer first = readOptionalInt(operationNode, "first", context);
                Integer second = readOptionalInt(operationNode, "second", context);
                Double angle = readOptionalDouble(operationNode, "angle", context);
                List<Integer> qubitsList = readOptionalIntList(operationNode, "qubits", context);
                operations.add(new JsonOperationSpec(gate, target, control, first, second, angle, qubitsList));
                index++;
            }
        }

        return new JsonCircuitDefinition(qubits, operations);
    }

    private QuantumCircuit buildCircuit(JsonCircuitDefinition definition) {
        int qubitCount = definition.qubits();
        if (qubitCount <= 0) {
            throw new CircuitParseException("'qubits' must be positive");
        }
        QuantumCircuit circuit = new QuantumCircuit(qubitCount);
        boolean measurementSeen = false;

        for (JsonOperationSpec operation : definition.operations()) {
            String gate = operation.gate().trim().toLowerCase(Locale.ROOT);
            if (measurementSeen && !gate.equals("measure")) {
                throw new CircuitParseException(
                        "Unitary gate '" + gate + "' cannot follow a measurement operation");
            }
            switch (gate) {
                case "h" -> {
                    int target = requireInt(operation.target(), "gate 'h' requires 'target'");
                    validateIndex(target, qubitCount, "target");
                    circuit = circuit.append(new Hadamard(), target);
                }
                case "x" -> {
                    int target = requireInt(operation.target(), "gate 'x' requires 'target'");
                    validateIndex(target, qubitCount, "target");
                    circuit = circuit.append(new PauliX(), target);
                }
                case "y" -> {
                    int target = requireInt(operation.target(), "gate 'y' requires 'target'");
                    validateIndex(target, qubitCount, "target");
                    circuit = circuit.append(new PauliY(), target);
                }
                case "z" -> {
                    int target = requireInt(operation.target(), "gate 'z' requires 'target'");
                    validateIndex(target, qubitCount, "target");
                    circuit = circuit.append(new PauliZ(), target);
                }
                case "cx" -> {
                    int control = requireInt(operation.control(), "gate 'cx' requires 'control'");
                    int target = requireInt(operation.target(), "gate 'cx' requires 'target'");
                    validateIndex(control, qubitCount, "control");
                    validateIndex(target, qubitCount, "target");
                    if (control == target) {
                        throw new CircuitParseException("gate 'cx' requires distinct control and target qubits");
                    }
                    circuit = circuit.append(CnotGate.of(), control, target);
                }
                case "swap" -> {
                    int first = requireInt(operation.first(), "gate 'swap' requires 'first'");
                    int second = requireInt(operation.second(), "gate 'swap' requires 'second'");
                    validateIndex(first, qubitCount, "first");
                    validateIndex(second, qubitCount, "second");
                    if (first == second) {
                        throw new CircuitParseException("gate 'swap' requires distinct qubits");
                    }
                    circuit = circuit.appendSwap(first, second);
                }
                case "cp" -> {
                    int control = requireInt(operation.control(), "gate 'cp' requires 'control'");
                    int target = requireInt(operation.target(), "gate 'cp' requires 'target'");
                    double angle = requireDouble(operation.angle(), "gate 'cp' requires 'angle'");
                    if (!Double.isFinite(angle)) {
                        throw new CircuitParseException("gate 'cp' requires a finite angle");
                    }
                    validateIndex(control, qubitCount, "control");
                    validateIndex(target, qubitCount, "target");
                    if (control == target) {
                        throw new CircuitParseException("gate 'cp' requires distinct control and target qubits");
                    }
                    circuit = circuit.appendControlledPhase(angle, control, target);
                }
                case "measure" -> {
                    measurementSeen = true;
                    List<Integer> qubits = operation.qubits();
                    if (qubits == null) {
                        throw new CircuitParseException("gate 'measure' requires 'qubits'");
                    }
                    int[] measured = normalizeMeasuredQubits(qubits, qubitCount);
                    circuit = circuit.measure(measured);
                }
                default -> throw new CircuitParseException("Unknown gate: '" + operation.gate() + "'");
            }
        }

        return circuit;
    }

    private static int readRequiredInt(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new CircuitParseException("Missing required field '" + field + "' in " + context);
        }
        if (!value.isIntegralNumber()) {
            throw new CircuitParseException("Field '" + field + "' in " + context + " must be an integer");
        }
        return value.intValue();
    }

    private static String readRequiredText(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new CircuitParseException("Missing required field '" + field + "' in " + context);
        }
        if (!value.isTextual()) {
            throw new CircuitParseException("Field '" + field + "' in " + context + " must be a string");
        }
        return value.textValue();
    }

    private static Integer readOptionalInt(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            throw new CircuitParseException("Field '" + field + "' in " + context + " must be an integer");
        }
        return value.intValue();
    }

    private static Double readOptionalDouble(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new CircuitParseException("Field '" + field + "' in " + context + " must be a number");
        }
        return value.doubleValue();
    }

    private static List<Integer> readOptionalIntList(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            throw new CircuitParseException("Field '" + field + "' in " + context + " must be an array");
        }
        List<Integer> values = new ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isIntegralNumber()) {
                throw new CircuitParseException("Field '" + field + "' in " + context + " must contain integers");
            }
            values.add(element.intValue());
        }
        return values;
    }

    private static int requireInt(Integer value, String message) {
        if (value == null) {
            throw new CircuitParseException(message);
        }
        return value;
    }

    private static double requireDouble(Double value, String message) {
        if (value == null) {
            throw new CircuitParseException(message);
        }
        return value;
    }

    private static void validateIndex(int index, int qubitCount, String label) {
        if (index < 0 || index >= qubitCount) {
            throw new CircuitParseException("" + label + " qubit out of range: " + index);
        }
    }

    private static int[] normalizeMeasuredQubits(List<Integer> qubits, int qubitCount) {
        if (qubits.isEmpty()) {
            throw new CircuitParseException("Measurement qubit list must not be empty");
        }
        int[] result = new int[qubits.size()];
        for (int i = 0; i < qubits.size(); i++) {
            Integer value = qubits.get(i);
            if (value == null) {
                throw new CircuitParseException("Measurement qubit index must not be null");
            }
            if (value < 0 || value >= qubitCount) {
                throw new CircuitParseException("Measured qubit out of range: " + value);
            }
            result[i] = value;
        }
        Arrays.sort(result);
        for (int i = 1; i < result.length; i++) {
            if (result[i] == result[i - 1]) {
                throw new CircuitParseException("Duplicate measured qubit: " + result[i]);
            }
        }
        return result;
    }

    private record JsonCircuitDefinition(int qubits, List<JsonOperationSpec> operations) {
        private JsonCircuitDefinition {
            operations = operations == null ? List.of() : List.copyOf(operations);
        }
    }

    private record JsonOperationSpec(
            String gate,
            Integer target,
            Integer control,
            Integer first,
            Integer second,
            Double angle,
            List<Integer> qubits) {
    }
}
