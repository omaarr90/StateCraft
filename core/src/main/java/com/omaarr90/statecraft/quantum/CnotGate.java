package com.omaarr90.statecraft.quantum;

/**
 * Controlled-NOT (CNOT) gate. When the control qubit is |1>, flips the target
 * qubit.
 */
public record CnotGate() {

	private static final CnotGate INSTANCE = new CnotGate();

	public static CnotGate of() {
		return INSTANCE;
	}

	public String name() {
		return "CNOT";
	}
}
