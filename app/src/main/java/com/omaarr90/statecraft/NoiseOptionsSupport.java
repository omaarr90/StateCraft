package com.omaarr90.statecraft;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import picocli.CommandLine;

final class NoiseOptionsSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NoiseOptionsSupport() {
    }

    static ResolvedNoiseSpec resolve(NoiseOptionsInput input, CommandLine commandLine) {
        NoiseOptionsInput resolvedInput = input == null ? NoiseOptionsInput.empty() : input;
        ConfigNoiseSpec configSpec = resolvedInput.noiseConfig() == null
                ? ConfigNoiseSpec.empty()
                : loadConfig(resolvedInput.noiseConfig(), commandLine);

        List<GlobalChannelSpec> channels = new ArrayList<>(configSpec.globalChannels());
        appendFlagChannels(resolvedInput, channels, commandLine);

        OptionalLong resolvedSeed = resolvedInput.noiseSeed() != null
                ? OptionalLong.of(resolvedInput.noiseSeed())
                : configSpec.noiseSeed();

        if (resolvedInput.noiseSeed() != null && channels.isEmpty()) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    "--noise-seed requires at least one noise channel source (flags or --noise-config)");
        }

        return new ResolvedNoiseSpec(channels, resolvedSeed);
    }

    private static void appendFlagChannels(
            NoiseOptionsInput input,
            List<GlobalChannelSpec> channels,
            CommandLine commandLine) {
        if (input.noiseDepolarizing() != null) {
            channels.add(new DepolarizingSpec(
                    validateProbability(input.noiseDepolarizing(), "--noise-depolarizing", commandLine)));
        }
        if (input.noiseAmplitudeDamping() != null) {
            channels.add(new AmplitudeDampingSpec(
                    validateProbability(input.noiseAmplitudeDamping(), "--noise-amplitude-damping", commandLine)));
        }
        if (input.noisePhaseFlip() != null) {
            channels.add(new PhaseFlipSpec(
                    validateProbability(input.noisePhaseFlip(), "--noise-phase-flip", commandLine)));
        }
        if (input.noisePhaseDamping() != null) {
            channels.add(new PhaseDampingSpec(
                    validateProbability(input.noisePhaseDamping(), "--noise-phase-damping", commandLine)));
        }
        boolean hasAnyThermal = input.noiseThermalT1() != null
                || input.noiseThermalT2() != null
                || input.noiseThermalGateTime() != null;
        boolean hasAllThermal = input.noiseThermalT1() != null
                && input.noiseThermalT2() != null
                && input.noiseThermalGateTime() != null;
        if (hasAnyThermal && !hasAllThermal) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    "--noise-thermal-t1, --noise-thermal-t2, and --noise-thermal-gate-time must be provided together");
        }
        if (hasAllThermal) {
            channels.add(new ThermalRelaxationSpec(
                    validatePositive(input.noiseThermalT1(), "--noise-thermal-t1", commandLine),
                    validatePositive(input.noiseThermalT2(), "--noise-thermal-t2", commandLine),
                    validateNonNegative(input.noiseThermalGateTime(), "--noise-thermal-gate-time", commandLine),
                    commandLine,
                    "--noise-thermal-*"));
        }
    }

    private static ConfigNoiseSpec loadConfig(Path path, CommandLine commandLine) {
        JsonNode root;
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            root = MAPPER.readTree(source);
        } catch (JsonProcessingException e) {
            JsonLocation location = e.getLocation();
            String locationSuffix = "";
            if (location != null) {
                locationSuffix = " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ")";
            }
            throw new CommandLine.ParameterException(
                    commandLine,
                    "Invalid JSON in noise config '" + path + "': " + e.getOriginalMessage() + locationSuffix,
                    e);
        } catch (IOException e) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    "Failed to read noise config '" + path + "': " + e.getMessage(),
                    e);
        }
        if (root == null || root.isNull()) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    "Noise config '" + path + "' is empty");
        }
        if (!root.isObject()) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    "Noise config root must be a JSON object");
        }

        rejectUnknownFields(
                root,
                Set.of("noiseSeed", "global"),
                "noise config root",
                commandLine);

        OptionalLong noiseSeed = readOptionalLong(root, "noiseSeed", "noise config root", commandLine);
        List<GlobalChannelSpec> channels = new ArrayList<>();

        JsonNode globalNode = root.get("global");
        if (globalNode != null && !globalNode.isNull()) {
            if (!globalNode.isObject()) {
                throw new CommandLine.ParameterException(
                        commandLine,
                        "Field 'global' in noise config must be an object");
            }
            rejectUnknownFields(
                    globalNode,
                    Set.of("depolarizing", "amplitudeDamping", "phaseFlip", "phaseDamping", "thermalRelaxation"),
                    "noise config global",
                    commandLine);

            Double depolarizing = readOptionalDouble(globalNode, "depolarizing", "noise config global", commandLine);
            if (depolarizing != null) {
                channels.add(new DepolarizingSpec(
                        validateProbability(depolarizing, "global.depolarizing", commandLine)));
            }

            Double amplitudeDamping = readOptionalDouble(
                    globalNode,
                    "amplitudeDamping",
                    "noise config global",
                    commandLine);
            if (amplitudeDamping != null) {
                channels.add(new AmplitudeDampingSpec(
                        validateProbability(amplitudeDamping, "global.amplitudeDamping", commandLine)));
            }

            Double phaseFlip = readOptionalDouble(globalNode, "phaseFlip", "noise config global", commandLine);
            if (phaseFlip != null) {
                channels.add(new PhaseFlipSpec(
                        validateProbability(phaseFlip, "global.phaseFlip", commandLine)));
            }

            Double phaseDamping = readOptionalDouble(globalNode, "phaseDamping", "noise config global", commandLine);
            if (phaseDamping != null) {
                channels.add(new PhaseDampingSpec(
                        validateProbability(phaseDamping, "global.phaseDamping", commandLine)));
            }

            JsonNode thermalNode = globalNode.get("thermalRelaxation");
            if (thermalNode != null && !thermalNode.isNull()) {
                if (!thermalNode.isObject()) {
                    throw new CommandLine.ParameterException(
                            commandLine,
                            "Field 'global.thermalRelaxation' must be an object");
                }
                rejectUnknownFields(
                        thermalNode,
                        Set.of("t1", "t2", "gateTime"),
                        "noise config global.thermalRelaxation",
                        commandLine);
                double t1 = validatePositive(
                        readRequiredDouble(thermalNode, "t1", "global.thermalRelaxation", commandLine),
                        "global.thermalRelaxation.t1",
                        commandLine);
                double t2 = validatePositive(
                        readRequiredDouble(thermalNode, "t2", "global.thermalRelaxation", commandLine),
                        "global.thermalRelaxation.t2",
                        commandLine);
                double gateTime = validateNonNegative(
                        readRequiredDouble(thermalNode, "gateTime", "global.thermalRelaxation", commandLine),
                        "global.thermalRelaxation.gateTime",
                        commandLine);
                channels.add(new ThermalRelaxationSpec(
                        t1,
                        t2,
                        gateTime,
                        commandLine,
                        "global.thermalRelaxation"));
            }
        }

        return new ConfigNoiseSpec(channels, noiseSeed);
    }

    private static void rejectUnknownFields(
            JsonNode node,
            Set<String> allowed,
            String context,
            CommandLine commandLine) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new CommandLine.ParameterException(
                        commandLine,
                        "Unknown field '" + field + "' in " + context);
            }
        });
    }

    private static OptionalLong readOptionalLong(
            JsonNode node,
            String field,
            String context,
            CommandLine commandLine) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return OptionalLong.empty();
        }
        if (!value.isIntegralNumber()) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    "Field '" + field + "' in " + context + " must be an integer");
        }
        return OptionalLong.of(value.longValue());
    }

    private static Double readOptionalDouble(
            JsonNode node,
            String field,
            String context,
            CommandLine commandLine) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    "Field '" + field + "' in " + context + " must be a number");
        }
        return value.doubleValue();
    }

    private static double readRequiredDouble(
            JsonNode node,
            String field,
            String context,
            CommandLine commandLine) {
        Double value = readOptionalDouble(node, field, context, commandLine);
        if (value == null) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    "Missing required field '" + field + "' in " + context);
        }
        return value;
    }

    private static double validateProbability(double value, String label, CommandLine commandLine) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    label + " must be in [0, 1], got " + value);
        }
        return value;
    }

    private static double validatePositive(double value, String label, CommandLine commandLine) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    label + " must be positive and finite, got " + value);
        }
        return value;
    }

    private static double validateNonNegative(double value, String label, CommandLine commandLine) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new CommandLine.ParameterException(
                    commandLine,
                    label + " must be non-negative and finite, got " + value);
        }
        return value;
    }

    private interface GlobalChannelSpec {
        ErrorChannel channelForQubit(int qubit);
    }

    private record DepolarizingSpec(double probability) implements GlobalChannelSpec {
        @Override
        public ErrorChannel channelForQubit(int qubit) {
            return ErrorChannel.depolarizing(probability, qubit);
        }
    }

    private record AmplitudeDampingSpec(double gamma) implements GlobalChannelSpec {
        @Override
        public ErrorChannel channelForQubit(int qubit) {
            return ErrorChannel.amplitudeDamping(gamma, qubit);
        }
    }

    private record PhaseFlipSpec(double probability) implements GlobalChannelSpec {
        @Override
        public ErrorChannel channelForQubit(int qubit) {
            return ErrorChannel.phaseFlip(probability, qubit);
        }
    }

    private record PhaseDampingSpec(double lambda) implements GlobalChannelSpec {
        @Override
        public ErrorChannel channelForQubit(int qubit) {
            return ErrorChannel.phaseDamping(lambda, qubit);
        }
    }

    private record ThermalRelaxationSpec(
            double t1,
            double t2,
            double gateTime) implements GlobalChannelSpec {
        private ThermalRelaxationSpec(
                double t1,
                double t2,
                double gateTime,
                CommandLine commandLine,
                String labelPrefix) {
            this(t1, t2, gateTime);
            if (t2 > (2.0 * t1)) {
                throw new CommandLine.ParameterException(
                        commandLine,
                        labelPrefix + " requires t2 <= 2 * t1, got t1=" + t1 + ", t2=" + t2);
            }
        }

        @Override
        public ErrorChannel channelForQubit(int qubit) {
            return ErrorChannel.thermalRelaxation(t1, t2, gateTime, qubit);
        }
    }

    record NoiseOptionsInput(
            Path noiseConfig,
            Long noiseSeed,
            Double noiseDepolarizing,
            Double noiseAmplitudeDamping,
            Double noisePhaseFlip,
            Double noisePhaseDamping,
            Double noiseThermalT1,
            Double noiseThermalT2,
            Double noiseThermalGateTime) {
        static NoiseOptionsInput empty() {
            return new NoiseOptionsInput(null, null, null, null, null, null, null, null, null);
        }
    }

    private record ConfigNoiseSpec(List<GlobalChannelSpec> globalChannels, OptionalLong noiseSeed) {
        private ConfigNoiseSpec {
            globalChannels = List.copyOf(globalChannels);
            noiseSeed = noiseSeed == null ? OptionalLong.empty() : noiseSeed;
        }

        static ConfigNoiseSpec empty() {
            return new ConfigNoiseSpec(List.of(), OptionalLong.empty());
        }
    }

    record ResolvedNoiseSpec(List<GlobalChannelSpec> globalChannels, OptionalLong noiseSeed) {
        ResolvedNoiseSpec {
            globalChannels = List.copyOf(globalChannels);
            noiseSeed = noiseSeed == null ? OptionalLong.empty() : noiseSeed;
        }

        boolean hasNoiseChannels() {
            return !globalChannels.isEmpty();
        }

        SimulationRequest applyTo(SimulationRequest request) {
            if (!hasNoiseChannels()) {
                return request;
            }
            int qubitCount = request.circuit().qubitCount();
            NoiseModel noiseModel = toNoiseModel(qubitCount);
            SimulationRequest resolved = request.withNoiseModel(noiseModel);
            if (noiseSeed.isPresent()) {
                resolved = resolved.withNoiseSeed(noiseSeed.getAsLong());
            }
            return resolved;
        }

        NoiseModel toNoiseModel(int qubitCount) {
            NoiseModel.Builder builder = NoiseModel.builder();
            for (GlobalChannelSpec channelSpec : globalChannels) {
                for (int qubit = 0; qubit < qubitCount; qubit++) {
                    builder.afterAllGates(channelSpec.channelForQubit(qubit));
                }
            }
            return builder.build();
        }
    }
}
