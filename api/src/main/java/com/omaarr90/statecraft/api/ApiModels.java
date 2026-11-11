package com.omaarr90.statecraft.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** JSON transport types; amplitudes use little-endian basis indices. */
public final class ApiModels {
	private ApiModels() {
	}
	public record Complex(double re, double im) {
	}
	public record ParseRequest(String format, String source) {
	}
	public record Noise(String channel, Double strength, Integer target, Long seed) {
	}
	public record SimulateRequest(String format, String source, Boolean includeSteps, List<Complex> initialState,
			Integer shots, Long seed, int[] measuredQubits, Noise noise) {
	}
	public record CollapseRequest(Integer qubits, List<Complex> amplitudes, Integer target, Long seed) {
	}
	public record NoiseRequest(Integer qubits, List<Complex> amplitudes, Integer target, String channel,
			Double strength, Long seed) {
	}
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Operation(String gate, int target, Integer control, int[] controls, Double angle, int[] qubits) {
	}
	public record ParseResponse(int qubits, List<Operation> operations) {
	}
	public record Capabilities(String engine, String runtime, int maxQubits, int maxOperations, int maxShots,
			boolean midCircuitMeasurement, boolean collapseExtension, List<String> noiseChannels) {
	}
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SimulateResponse(String engine, String runtime, int qubits, List<Operation> operations,
			List<Complex> amplitudes, double[] probabilities, List<List<Complex>> steps, Map<String, Integer> counts,
			int[] measuredQubits) {
	}
	public record CollapseResponse(String engine, String runtime, String extension, List<Complex> amplitudes,
			double[] probabilities, int outcome, double probability) {
	}
	public record NoiseResponse(String engine, String runtime, String channel, List<Complex> amplitudes,
			double[] probabilities) {
	}
	public record ErrorDetail(String code, String message) {
	}
	public record ErrorResponse(ErrorDetail error) {
	}
}
