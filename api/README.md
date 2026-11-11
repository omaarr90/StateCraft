# Statecraft Quarkus API

This module makes the canonical Java Statecraft simulator available over HTTP. It depends directly on `:engines` and `:core`; it does not copy or replace quantum kernels. The existing Java modules and CLI keep their original responsibilities.

## Run locally

Use JDK 25 (including GraalVM for JDK 25). From the repository root:

```sh
./gradlew :api:quarkusDev
```

The service listens on `http://localhost:8080`. Development/test launchers add `--enable-preview --add-modules jdk.incubator.vector`, as required by the engine. To build, test, and run the packaged JVM application:

```sh
./gradlew :api:test :api:quarkusBuild
java --enable-preview --add-modules=jdk.incubator.vector -jar api/build/quarkus-app/quarkus-run.jar
```

Quarkus platform and Gradle plugin are pinned to the 3.33 LTS patch `3.33.4.2`. The service uses the JVM distribution. Native-image compatibility is not claimed or required; the canonical statevector engine uses the incubating JDK Vector API.

## HTTP contract

All API paths start with `/api/v1`. Request and response bodies are JSON. Failures use an HTTP status and `{ "error": { "code": "INVALID_REQUEST", "message": "..." } }`. Request parsing failures may use `INVALID_JSON` or `HTTP_400`. HTTP 429 means the worker limit is occupied; retry after a delay. `/q/health` exposes Quarkus health and `/q/openapi` exposes the OpenAPI contract. Swagger UI is available in development at `/q/swagger-ui`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/capabilities` | Actual engine identity, configured safety limits, noise channels, and collapse capabilities |
| POST | `/parse` | Parse with the canonical JSON or OpenQASM parser and return normalized UI operations |
| POST | `/simulate` | Run the actual statevector engine, optional intermediate states, shots, and noise |
| POST | `/collapse` | Sample through the core engine and explicitly project a Java StateVector in the API adapter |
| POST | `/noise` | Apply one actual core Kraus channel through an identity operation on the engine |

Parse request:

```json
{"format":"json","source":"{\"qubits\":2,\"operations\":[{\"gate\":\"h\",\"target\":0},{\"gate\":\"cx\",\"control\":0,\"target\":1}]}"}
```

The parser returns `{ "qubits": 2, "operations": [...] }`. Operation gate names are uppercase. Each operation includes `target`; `control`, `controls`, `angle` (radians), and `qubits` are included where relevant. SWAP represents its first qubit as `target` and second as `control`. Terminal MEASURE includes its full sorted `qubits` array. The actual parsers omit barrier and identity metadata because these have no state effect, so they do not add steps.

Simulate request adds optional fields to the parse request:

```json
{
  "format":"qasm",
  "source":"OPENQASM 3.0; qubit[2] q; h q[0]; cx q[0],q[1];",
  "includeSteps":true,
  "shots":1000,
  "seed":42,
  "measuredQubits":[0,1],
  "noise":{"channel":"phase-flip","strength":0.01,"target":0,"seed":42}
}
```

The response includes `engine: "statevector"`, `runtime: "java"`, `qubits`, `operations`, `amplitudes` (`[{"re":...,"im":...}]`), `probabilities`, and `steps`. With `includeSteps: true`, steps contain the initial state followed by one state for every normalized operation. Otherwise steps is an empty array. `initialState` may supply a normalized complex array of exactly `2^qubits` amplitudes. If omitted the state begins at zero.

Provide `shots` to request counts. `counts` maps fixed-width binary labels to integers; `measuredQubits` describes the sorted qubits represented by those labels. For measured qubits `[0,2]`, label `01` means q0=1 and q2=0. Explicit `measuredQubits` overrides terminal MEASURE metadata. Without an explicit subset, terminal measurements select their sorted union; otherwise all qubits are sampled. Sampling does not change returned state amplitudes.

For collapse, send `{ "qubits": 2, "amplitudes": [...], "target": 0, "seed": 42 }`. The response identifies `extension: "api-collapse"`, with the projected amplitudes, probabilities, sampled `outcome` (0 or 1), and that branch's pre-collapse `probability`. The core engine does not support unitary operations after a circuit MEASURE. Collapse is an explicit adapter extension and is not presented as native mid-circuit support.

For one noise application, send `{ "qubits": 2, "amplitudes": [...], "target": 0, "channel": "amplitude-damping", "strength": 0.1, "seed": 42 }`. All noise uses canonical `ErrorChannel` Kraus implementations via `StatevectorEngine`. The four supported names are `phase-flip`, `depolarizing`, `amplitude-damping`, and `phase-damping`. They sample a pure-state trajectory, not a mixed-state density matrix. In simulation, channels run after every unitary gate; omitted target means every qubit. Prefix replay uses the same noise seed for each prefix so the displayed states follow the same trajectory as the final state. Seeds reproduce Java sampling; browser PRNG results are not used.

## Parser scope and limits

The API accepts canonical JSON and OpenQASM 2.0/3.0. JSON supports H, X, Y, Z, S, SDG, CX, CY, CZ, SWAP, CP, CCX, MCX, MCY, MCZ, MEASURE, and BARRIER. T, TDG, RX, RY, RZ, and P require QASM. QASM U/U2/U3 are understood by the core parser but rejected by this playground adapter because its editor cannot represent them. Parser behavior, including terminal measurements, remains canonical.

The public playground is limited to 1–6 qubits, 256 operations, 10,000 shots, 65,536 source characters, and a 256 KiB HTTP body. Register sizes, integer ranges, expression depth, and operation counts are checked before running the engine. Initial states must be finite and normalized to tolerance `1e-9`. Seeds must be integers in the JavaScript safe range. Fractional and quoted integer inputs are rejected instead of coerced. At most four requests execute concurrently by default, with serial statevector execution inside each request.

## Production configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8080` | HTTP listen port |
| `STATECRAFT_API_TOKEN` | empty | Optional Bearer service token for `/api/v1/*` |
| `STATECRAFT_MAX_CONCURRENT` | `4` | Concurrent work limit, clamped to 1–16 |
| `STATECRAFT_CORS_ORIGINS` | Local development and Statecraft Site origins | Comma-separated browser origin allowlist |

The website should proxy requests from its server to this API. Store `STATECRAFT_API_TOKEN` as a server secret and forward it as `Authorization: Bearer ...`; never place it in a public browser bundle. CORS is a browser policy, not authentication. For a public deployment, use HTTPS at the hosting ingress and configure the optional token if access should be limited to the Site proxy.

Build the container from the repository root:

```sh
docker build -f api/Dockerfile -t statecraft-api .
docker run --rm -p 8080:8080 -e STATECRAFT_API_TOKEN statecraft-api
```

The Dockerfile builds with JDK 25 and runs on a JRE 25 as an unprivileged user. No image is uploaded or published by these commands. For Cloudflare Containers, build the Linux amd64 image on Apple Silicon using `docker build --platform linux/amd64 -f api/Dockerfile -t statecraft-api .` and route to container port 8080. JVM applications cannot execute inside the ordinary Workers JavaScript isolate; a container-capable host is required.
