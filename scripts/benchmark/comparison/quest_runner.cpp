#include "quest.h"

#include <chrono>
#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace {

struct Operation {
	std::string gate;
	int target = -1;
	int control = -1;
	int first = -1;
	int second = -1;
	double angle = 0.0;
};

struct NoiseConfig {
	bool enabled = false;
	std::string type;
	std::vector<int> qubits;
	double probability = 0.0;
	double gamma = 0.0;
	double lambda = 0.0;
	double t1 = 0.0;
	double t2 = 0.0;
	double gateTime = 0.0;
};

struct Program {
	std::string fixtureId;
	std::string category;
	int qubits = 0;
	std::vector<Operation> operations;
	NoiseConfig noise;
};

struct Options {
	std::string programPath;
	int warmupRuns = 1;
	int timedRuns = 5;
};

struct SimulationOutput {
	std::vector<qcomp> statevector;
	std::vector<double> probabilities;
};

using Matrix = std::vector<std::vector<qcomp>>;
using KrausMatrices = std::vector<Matrix>;

std::vector<std::string> split(const std::string& line)
{
	std::istringstream input(line);
	std::vector<std::string> parts;
	std::string part;
	while (input >> part) {
		parts.push_back(part);
	}
	return parts;
}

int parseInt(const std::string& value, const std::string& label)
{
	try {
		size_t consumed = 0;
		int parsed = std::stoi(value, &consumed);
		if (consumed != value.size()) {
			throw std::invalid_argument("trailing input");
		}
		return parsed;
	} catch (const std::exception& exc) {
		throw std::runtime_error("invalid integer for " + label + ": " + value + " (" + exc.what() + ")");
	}
}

double parseDouble(const std::string& value, const std::string& label)
{
	try {
		size_t consumed = 0;
		double parsed = std::stod(value, &consumed);
		if (consumed != value.size()) {
			throw std::invalid_argument("trailing input");
		}
		return parsed;
	} catch (const std::exception& exc) {
		throw std::runtime_error("invalid double for " + label + ": " + value + " (" + exc.what() + ")");
	}
}

std::vector<int> parseQubitList(const std::string& value)
{
	std::vector<int> qubits;
	std::stringstream input(value);
	std::string part;
	while (std::getline(input, part, ',')) {
		if (!part.empty()) {
			qubits.push_back(parseInt(part, "noise qubits"));
		}
	}
	return qubits;
}

Options parseOptions(int argc, char** argv)
{
	Options options;
	for (int index = 1; index < argc; index++) {
		std::string arg = argv[index];
		if (index + 1 >= argc) {
			throw std::runtime_error("missing value for " + arg);
		}
		std::string value = argv[++index];
		if (arg == "--program") {
			options.programPath = value;
		} else if (arg == "--warmup-runs") {
			options.warmupRuns = parseInt(value, arg);
		} else if (arg == "--timed-runs") {
			options.timedRuns = parseInt(value, arg);
		} else {
			throw std::runtime_error("unknown argument: " + arg);
		}
	}
	if (options.programPath.empty()) {
		throw std::runtime_error("--program is required");
	}
	if (options.warmupRuns < 0 || options.timedRuns <= 0) {
		throw std::runtime_error("warmup-runs must be >= 0 and timed-runs must be > 0");
	}
	return options;
}

void parseNoise(Program& program, const std::vector<std::string>& parts)
{
	if (parts.size() == 2 && parts[1] == "none") {
		program.noise = NoiseConfig {};
		return;
	}
	if (parts.size() < 5 || parts[2] != "qubits") {
		throw std::runtime_error("noise line must be 'noise <type> qubits <list> ...'");
	}
	program.noise.enabled = true;
	program.noise.type = parts[1];
	program.noise.qubits = parseQubitList(parts[3]);
	for (size_t index = 4; index + 1 < parts.size(); index += 2) {
		const std::string& key = parts[index];
		const std::string& value = parts[index + 1];
		if (key == "probability") {
			program.noise.probability = parseDouble(value, key);
		} else if (key == "gamma") {
			program.noise.gamma = parseDouble(value, key);
		} else if (key == "lambda") {
			program.noise.lambda = parseDouble(value, key);
		} else if (key == "t1") {
			program.noise.t1 = parseDouble(value, key);
		} else if (key == "t2") {
			program.noise.t2 = parseDouble(value, key);
		} else if (key == "gate_time") {
			program.noise.gateTime = parseDouble(value, key);
		}
	}
}

Operation parseOperation(const std::vector<std::string>& parts)
{
	if (parts.size() < 3 || parts[0] != "op") {
		throw std::runtime_error("operation line must start with 'op'");
	}
	Operation op;
	op.gate = parts[1];
	if (op.gate == "h" || op.gate == "x" || op.gate == "y" || op.gate == "z" || op.gate == "s" ||
			op.gate == "sdg") {
		if (parts.size() != 3) {
			throw std::runtime_error("single-qubit op requires one target");
		}
		op.target = parseInt(parts[2], op.gate + " target");
	} else if (op.gate == "cx" || op.gate == "cy" || op.gate == "cz") {
		if (parts.size() != 4) {
			throw std::runtime_error("controlled op requires control and target");
		}
		op.control = parseInt(parts[2], op.gate + " control");
		op.target = parseInt(parts[3], op.gate + " target");
	} else if (op.gate == "cp") {
		if (parts.size() != 5) {
			throw std::runtime_error("cp requires control, target, and angle");
		}
		op.control = parseInt(parts[2], "cp control");
		op.target = parseInt(parts[3], "cp target");
		op.angle = parseDouble(parts[4], "cp angle");
	} else if (op.gate == "swap") {
		if (parts.size() != 4) {
			throw std::runtime_error("swap requires two qubits");
		}
		op.first = parseInt(parts[2], "swap first");
		op.second = parseInt(parts[3], "swap second");
	} else {
		throw std::runtime_error("unsupported operation: " + op.gate);
	}
	return op;
}

Program readProgram(const std::string& path)
{
	std::ifstream input(path);
	if (!input) {
		throw std::runtime_error("unable to open program: " + path);
	}
	Program program;
	std::string line;
	int lineNumber = 0;
	while (std::getline(input, line)) {
		lineNumber++;
		if (line.empty() || line[0] == '#') {
			continue;
		}
		std::vector<std::string> parts = split(line);
		if (parts.empty()) {
			continue;
		}
		try {
			if (parts[0] == "fixture" && parts.size() == 2) {
				program.fixtureId = parts[1];
			} else if (parts[0] == "category" && parts.size() == 2) {
				program.category = parts[1];
			} else if (parts[0] == "qubits" && parts.size() == 2) {
				program.qubits = parseInt(parts[1], "qubits");
			} else if (parts[0] == "noise") {
				parseNoise(program, parts);
			} else if (parts[0] == "op") {
				program.operations.push_back(parseOperation(parts));
			} else {
				throw std::runtime_error("unknown directive: " + parts[0]);
			}
		} catch (const std::exception& exc) {
			throw std::runtime_error("line " + std::to_string(lineNumber) + ": " + exc.what());
		}
	}
	if (program.fixtureId.empty() || program.category.empty() || program.qubits <= 0) {
		throw std::runtime_error("program requires fixture, category, and positive qubits");
	}
	return program;
}

Matrix matrix(qcomp a00, qcomp a01, qcomp a10, qcomp a11)
{
	return {{a00, a01}, {a10, a11}};
}

KrausMatrices canonicalKraus(const NoiseConfig& noise)
{
	if (noise.type == "depolarizing") {
		double p = noise.probability;
		double a = std::sqrt(1.0 - p);
		double b = std::sqrt(p / 3.0);
		return {
				matrix(qcomp(a, 0), qcomp(0, 0), qcomp(0, 0), qcomp(a, 0)),
				matrix(qcomp(0, 0), qcomp(b, 0), qcomp(b, 0), qcomp(0, 0)),
				matrix(qcomp(0, 0), qcomp(0, -b), qcomp(0, b), qcomp(0, 0)),
				matrix(qcomp(b, 0), qcomp(0, 0), qcomp(0, 0), qcomp(-b, 0)),
		};
	}
	if (noise.type == "phase_flip") {
		double a = std::sqrt(1.0 - noise.probability);
		double b = std::sqrt(noise.probability);
		return {
				matrix(qcomp(a, 0), qcomp(0, 0), qcomp(0, 0), qcomp(a, 0)),
				matrix(qcomp(b, 0), qcomp(0, 0), qcomp(0, 0), qcomp(-b, 0)),
		};
	}
	if (noise.type == "amplitude_damping") {
		double gamma = noise.gamma;
		return {
				matrix(qcomp(1, 0), qcomp(0, 0), qcomp(0, 0), qcomp(std::sqrt(1.0 - gamma), 0)),
				matrix(qcomp(0, 0), qcomp(std::sqrt(gamma), 0), qcomp(0, 0), qcomp(0, 0)),
		};
	}
	if (noise.type == "phase_damping") {
		double lambda = noise.lambda;
		return {
				matrix(qcomp(1, 0), qcomp(0, 0), qcomp(0, 0), qcomp(std::sqrt(1.0 - lambda), 0)),
				matrix(qcomp(0, 0), qcomp(0, 0), qcomp(0, 0), qcomp(std::sqrt(lambda), 0)),
		};
	}
	if (noise.type == "thermal_relaxation") {
		double survival = noise.gateTime == 0.0 ? 1.0 : std::exp(-noise.gateTime / noise.t1);
		double pureDephasingRate = std::max(0.0, (1.0 / noise.t2) - (1.0 / (2.0 * noise.t1)));
		double pureDephasing = noise.gateTime == 0.0 ? 1.0 : std::exp(-noise.gateTime * pureDephasingRate);
		double decay = 1.0 - survival;
		double plus = 0.5 * (1.0 + pureDephasing);
		double minus = 0.5 * (1.0 - pureDephasing);
		return {
				matrix(qcomp(std::sqrt(plus), 0), qcomp(0, 0), qcomp(0, 0),
						qcomp(std::sqrt(survival) * std::sqrt(plus), 0)),
				matrix(qcomp(std::sqrt(minus), 0), qcomp(0, 0), qcomp(0, 0),
						qcomp(-std::sqrt(survival) * std::sqrt(minus), 0)),
				matrix(qcomp(0, 0), qcomp(std::sqrt(decay), 0), qcomp(0, 0), qcomp(0, 0)),
		};
	}
	throw std::runtime_error("unsupported noise type: " + noise.type);
}

KrausMap createNoiseMap(const NoiseConfig& noise)
{
	KrausMatrices matrices = canonicalKraus(noise);
	return createInlineKrausMap(1, static_cast<int>(matrices.size()), matrices);
}

void applyOperation(Qureg qureg, const Operation& op)
{
	if (op.gate == "h") {
		applyHadamard(qureg, op.target);
	} else if (op.gate == "x") {
		applyPauliX(qureg, op.target);
	} else if (op.gate == "y") {
		applyPauliY(qureg, op.target);
	} else if (op.gate == "z") {
		applyPauliZ(qureg, op.target);
	} else if (op.gate == "s") {
		applyS(qureg, op.target);
	} else if (op.gate == "sdg") {
		applyPhaseShift(qureg, op.target, -1.57079632679489661923);
	} else if (op.gate == "cx") {
		applyControlledPauliX(qureg, op.control, op.target);
	} else if (op.gate == "cy") {
		applyControlledPauliY(qureg, op.control, op.target);
	} else if (op.gate == "cz") {
		applyControlledPauliZ(qureg, op.control, op.target);
	} else if (op.gate == "cp") {
		applyTwoQubitPhaseShift(qureg, op.control, op.target, op.angle);
	} else if (op.gate == "swap") {
		applySwap(qureg, op.first, op.second);
	} else {
		throw std::runtime_error("unsupported operation at execution time: " + op.gate);
	}
}

void applyNoise(Qureg qureg, const NoiseConfig& noise, KrausMap map)
{
	for (int qubit : noise.qubits) {
		int target = qubit;
		mixKrausMap(qureg, &target, 1, map);
	}
}

Qureg runQureg(const Program& program, KrausMap noiseMap)
{
	Qureg qureg = program.noise.enabled ? createDensityQureg(program.qubits) : createQureg(program.qubits);
	initZeroState(qureg);
	for (const Operation& operation : program.operations) {
		applyOperation(qureg, operation);
		if (program.noise.enabled) {
			applyNoise(qureg, program.noise, noiseMap);
		}
	}
	return qureg;
}

SimulationOutput extractOutput(Qureg qureg, const Program& program)
{
	qindex dimension = static_cast<qindex>(1) << program.qubits;
	SimulationOutput output;
	output.probabilities.reserve(static_cast<size_t>(dimension));
	for (qindex index = 0; index < dimension; index++) {
		output.probabilities.push_back(static_cast<double>(calcProbOfBasisState(qureg, index)));
	}
	if (!program.noise.enabled) {
		output.statevector.reserve(static_cast<size_t>(dimension));
		for (qindex index = 0; index < dimension; index++) {
			output.statevector.push_back(getQuregAmp(qureg, index));
		}
	}
	return output;
}

double mean(const std::vector<double>& values)
{
	if (values.empty()) {
		return 0.0;
	}
	double sum = 0.0;
	for (double value : values) {
		sum += value;
	}
	return sum / static_cast<double>(values.size());
}

std::string escapeJson(const std::string& value)
{
	std::ostringstream out;
	for (char ch : value) {
		if (ch == '\\' || ch == '"') {
			out << '\\' << ch;
		} else if (ch == '\n') {
			out << "\\n";
		} else {
			out << ch;
		}
	}
	return out.str();
}

void appendStringField(std::ostream& out, const std::string& name, const std::string& value)
{
	out << '"' << escapeJson(name) << "\":\"" << escapeJson(value) << '"';
}

void appendDoubleArray(std::ostream& out, const std::vector<double>& values)
{
	out << '[';
	for (size_t index = 0; index < values.size(); index++) {
		if (index > 0) {
			out << ',';
		}
		out << values[index];
	}
	out << ']';
}

void appendStatevector(std::ostream& out, const std::vector<qcomp>& statevector)
{
	out << '[';
	for (size_t index = 0; index < statevector.size(); index++) {
		if (index > 0) {
			out << ',';
		}
		out << '[' << statevector[index].real() << ',' << statevector[index].imag() << ']';
	}
	out << ']';
}

void emitJson(const Program& program, const Options& options, const std::vector<double>& timedRunsMs,
		const SimulationOutput& output)
{
	std::cout << std::setprecision(17);
	std::cout << '{';
	appendStringField(std::cout, "runner", "quest");
	std::cout << ',';
	appendStringField(std::cout, "fixture_id", program.fixtureId);
	std::cout << ',';
	appendStringField(std::cout, "category", program.category);
	std::cout << ',';
	appendStringField(std::cout, "status", "ok");
	std::cout << ",\"qubits\":" << program.qubits;
	std::cout << ",\"operations\":" << program.operations.size();
	std::cout << ",\"threads\":1";
	std::cout << ",\"warmup_runs\":" << options.warmupRuns;
	std::cout << ",\"timed_runs\":" << options.timedRuns;
	std::cout << ",\"noise_trajectories\":0";
	std::cout << ",\"timed_runs_ms\":";
	appendDoubleArray(std::cout, timedRunsMs);
	std::cout << ",\"mean_ms\":" << mean(timedRunsMs);
	std::cout << ",\"versions\":{\"quest\":\"v4.2.0\"}";
	std::cout << ",\"probabilities\":";
	appendDoubleArray(std::cout, output.probabilities);
	if (!program.noise.enabled) {
		std::cout << ",\"statevector\":";
		appendStatevector(std::cout, output.statevector);
	}
	std::cout << "}\n";
}

} // namespace

int main(int argc, char** argv)
{
	try {
		Options options = parseOptions(argc, argv);
		Program program = readProgram(options.programPath);
		initQuESTEnv();
		KrausMap noiseMap {};
		if (program.noise.enabled) {
			noiseMap = createNoiseMap(program.noise);
		}

		for (int index = 0; index < options.warmupRuns; index++) {
			Qureg warmup = runQureg(program, noiseMap);
			destroyQureg(warmup);
		}

		std::vector<double> timedRunsMs;
		timedRunsMs.reserve(static_cast<size_t>(options.timedRuns));
		Qureg lastQureg {};
		bool hasLast = false;
		for (int index = 0; index < options.timedRuns; index++) {
			if (hasLast) {
				destroyQureg(lastQureg);
				hasLast = false;
			}
			auto start = std::chrono::steady_clock::now();
			lastQureg = runQureg(program, noiseMap);
			auto end = std::chrono::steady_clock::now();
			hasLast = true;
			std::chrono::duration<double, std::milli> elapsed = end - start;
			timedRunsMs.push_back(elapsed.count());
		}

		SimulationOutput output = extractOutput(lastQureg, program);
		if (hasLast) {
			destroyQureg(lastQureg);
		}
		if (program.noise.enabled) {
			destroyKrausMap(noiseMap);
		}
		finalizeQuESTEnv();
		emitJson(program, options, timedRunsMs, output);
		return EXIT_SUCCESS;
	} catch (const std::exception& exc) {
		if (isQuESTEnvInit()) {
			finalizeQuESTEnv();
		}
		std::cerr << "quest_runner failed: " << exc.what() << '\n';
		return EXIT_FAILURE;
	}
}
