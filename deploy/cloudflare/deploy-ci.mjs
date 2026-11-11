/**
 * GitHub-only deployment. Secrets remain in process memory and secret-put stdin;
 * CLI diagnostics are bounded and redacted; HTTP response bodies stay private.
 */
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { appendFile, mkdtemp, readFile, rm, symlink } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

const DIRECTORY = dirname(fileURLToPath(import.meta.url));
const REPOSITORY = "omaarr90/StateCraft";
const BRANCH_REF = "refs/heads/feat/quarkus-simulator-api";
const OWNER_KEY = "STATECRAFT_DEPLOYMENT_OWNER";
const OWNER_VALUE = `${REPOSITORY}:quarkus-api:v1`;
const VERSION_TAG = "statecraft-quarkus-api-v1";
const REQUIRED_SECRETS = ["CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID", "STATECRAFT_API_TOKEN"];
const API_ROOT = "https://api.cloudflare.com/client/v4";
const FIRST_SUBDOMAIN = "omaarr90-statecraft";

class DeploymentFailure extends Error {
  constructor(message, cloudflareCodes = []) {
    super(message);
    this.cloudflareCodes = cloudflareCodes;
  }
}

function ensure(condition, message) {
  if (!condition) throw new DeploymentFailure(message);
}

export function missingSecrets(environment) {
  return REQUIRED_SECRETS.filter((name) => !environment[name]?.trim());
}

export function assertOwnedWorker(settings) {
  if (settings === null) return;
  const bindings = settings.bindings ?? [];
  const marked = bindings.some((binding) => binding.type === "plain_text" &&
    binding.name === OWNER_KEY && binding.text === OWNER_VALUE);
  const hasContainer = bindings.some((binding) => binding.type === "durable_object_namespace" &&
    binding.name === "STATECRAFT_API");
  ensure(marked && hasContainer,
    "Worker name collision: statecraft-api already exists without this repository's ownership marker and container binding. Refusing to overwrite it. Review the existing Worker or choose a separate name in the configuration.");
}

export function verifiedOrigin(workerName, accountSubdomain) {
  const label = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
  ensure(typeof workerName === "string" && typeof accountSubdomain === "string" &&
    label.test(workerName) && label.test(accountSubdomain),
    "Cloudflare did not return a valid workers.dev account subdomain. No API origin was guessed.");
  return `https://${workerName}.${accountSubdomain}.workers.dev`;
}

export function assertBellResult(result) {
  ensure(result.engine === "statevector" && result.runtime === "java" && result.qubits === 2,
    "Bell simulation did not identify the Java statevector engine.");
  const near = (actual, expected) => Number.isFinite(actual) && Math.abs(actual - expected) < 1e-9;
  const probabilities = result.probabilities;
  ensure(Array.isArray(probabilities) && probabilities.length === 4 &&
    [0.5, 0, 0, 0.5].every((expected, index) => near(probabilities[index], expected)),
    "Bell simulation probabilities differ from 0.5 at |00> and |11>.");
  const h = Math.SQRT1_2;
  const expectedSteps = [[1, 0, 0, 0], [h, h, 0, 0], [h, 0, 0, h]];
  ensure(Array.isArray(result.steps) && result.steps.length === expectedSteps.length &&
    expectedSteps.every((expected, step) => Array.isArray(result.steps[step]) &&
      result.steps[step].length === 4 && expected.every((value, basis) =>
        near(result.steps[step][basis]?.re, value) && near(result.steps[step][basis]?.im, 0))),
    "Bell simulation intermediate amplitudes differ from the expected little-endian Java states.");
  ensure(Array.isArray(result.amplitudes) && result.amplitudes.length === 4 &&
    expectedSteps[2].every((value, basis) =>
      near(result.amplitudes[basis]?.re, value) && near(result.amplitudes[basis]?.im, 0)),
    "Bell simulation final amplitudes differ from its last intermediate state.");
}

export function redactDiagnostics(output, secrets) {
  let redacted = output;
  for (const secret of new Set(secrets.filter(Boolean))) redacted = redacted.split(secret).join("[REDACTED]");
  return redacted;
}

async function summary(message) {
  if (process.env.GITHUB_STEP_SUMMARY) await appendFile(process.env.GITHUB_STEP_SUMMARY, `${message}\n`);
}

function childEnvironment(logSink) {
  const environment = { ...process.env, CI: "true", WRANGLER_SEND_METRICS: "false", WRANGLER_LOG: "log", WRANGLER_LOG_PATH: logSink };
  // The simulator token enters Wrangler only through secret-put's stdin.
  delete environment.STATECRAFT_API_TOKEN;
  return environment;
}

async function wrangler(args, phase, input) {
  console.log(phase);
  const cli = resolve(DIRECTORY, "node_modules/wrangler/bin/wrangler.js");
  // Wrangler requires a .log suffix. A temporary symlink sends debug output to
  // the null device, so request bodies can never persist in a CLI log file.
  const logDirectory = await mkdtemp(join(tmpdir(), "statecraft-wrangler-"));
  const logSink = join(logDirectory, "discard.log");
  await symlink("/dev/null", logSink);
  try {
    await new Promise((resolvePromise, reject) => {
      const child = spawn(process.execPath, [cli, ...args], {
        cwd: DIRECTORY,
        env: childEnvironment(logSink),
        stdio: ["pipe", "pipe", "pipe"],
        shell: false,
      });
      let diagnostics = "", diagnosticsExceeded = false;
      const capture = (chunk) => {
        if (diagnosticsExceeded) return;
        diagnostics += chunk.toString("utf8");
        if (diagnostics.length > 2 * 1024 * 1024) {
          // Discard the entire output rather than risk exposing a truncated secret.
          diagnosticsExceeded = true;
          diagnostics = "";
        }
      };
      child.stdout.on("data", capture);
      child.stderr.on("data", capture);
      const printDiagnostics = () => {
        if (diagnosticsExceeded) {
          console.log("Wrangler diagnostic output exceeded the 2 MiB safety limit and was omitted.");
        } else if (diagnostics.trim()) {
          const boundary = randomUUID();
          console.log(`::stop-commands::${boundary}`);
          console.log(redactDiagnostics(diagnostics.trim(), [process.env.CLOUDFLARE_API_TOKEN,
            process.env.STATECRAFT_API_TOKEN, input?.trim()]));
          console.log(`::${boundary}::`);
        }
      };
      let timedOut = false, forcedTermination;
      const heartbeat = setInterval(() => console.log(`${phase} (still running)`), 30_000);
      const deadline = setTimeout(() => {
        timedOut = true;
        child.kill("SIGTERM");
        forcedTermination = setTimeout(() => child.kill("SIGKILL"), 10_000);
        forcedTermination.unref();
      }, 25 * 60_000);
      child.once("error", () => {
        clearInterval(heartbeat); clearTimeout(deadline);
        reject(new DeploymentFailure(`${phase} could not start the locked Wrangler executable.`));
      });
      child.once("close", (code) => {
        clearInterval(heartbeat); clearTimeout(deadline); clearTimeout(forcedTermination);
        printDiagnostics();
        if (timedOut) reject(new DeploymentFailure(`${phase} exceeded the 25-minute limit. See the redacted diagnostics above.`));
        else if (code === 0) resolvePromise();
        else reject(new DeploymentFailure(`${phase} failed (exit ${code ?? "unknown"}). See the redacted Wrangler diagnostics above for the provider or build error.`));
      });
      // Ignore EPIPE here: the close handler gives a controlled, nonsecret failure.
      child.stdin.on("error", () => {});
      child.stdin.end(input ?? "");
    });
  } finally {
    await rm(logDirectory, { recursive: true, force: true });
  }
}

export async function cloudflare(accountId, apiToken, suffix, {
  allowMissing = false, method = "GET", json, fetchImplementation = fetch, redactSecrets = [], enveloped = true,
} = {}) {
  ensure(enveloped || (suffix === "/containers/me" && method === "GET"),
    "Bare Cloudflare responses are accepted only for the Containers access preflight.");
  const endpoint = `${method} /accounts/<account-id>${suffix}`;
  let response;
  try {
    response = await fetchImplementation(`${API_ROOT}/accounts/${accountId}${suffix}`, {
      method,
      headers: { Authorization: `Bearer ${apiToken}`, ...(json === undefined ? {} : { "Content-Type": "application/json" }) },
      ...(json === undefined ? {} : { body: JSON.stringify(json) }),
      redirect: "error",
      signal: AbortSignal.timeout(20_000),
    });
  } catch {
    throw new DeploymentFailure(`Cloudflare ${endpoint} could not connect.`);
  }
  let body;
  try { body = await response.json(); } catch { body = null; }
  const errors = Array.isArray(body?.errors) ? [...body.errors] : [];
  if (typeof body?.error === "string") errors.push({ code: body.code, message: body.error });
  else if (typeof body?.error?.message === "string") errors.push({ code: body.error.code ?? body.code, message: body.error.message });
  if (typeof body?.message === "string") errors.push({ code: body.code, message: body.message });
  const fallbackCode = body?.error?.code ?? body?.code;
  if (errors.length === 0 && (typeof fallbackCode === "string" || Number.isSafeInteger(fallbackCode))) {
    errors.push({ code: fallbackCode });
  }
  const codes = errors.map((error) => error?.code).filter(Number.isSafeInteger);
  // Only Cloudflare's explicit missing-resource code permits creation. A generic
  // 404 can indicate the wrong account or route and must remain a failure.
  if (allowMissing && codes.includes(10007)) return null;
  if (!response.ok || (enveloped && body?.success !== true)) {
    const clean = (value, limit) => redactDiagnostics(value, [apiToken, ...redactSecrets])
      .replace(/[\u0000-\u001f\u007f]/g, " ").slice(0, limit);
    const details = errors.slice(0, 3).map((error) => {
      const code = Number.isSafeInteger(error?.code) ? error.code
        : typeof error?.code === "string" ? clean(error.code, 64) : "unknown";
      const message = clean(typeof error?.message === "string" ? error.message : "No error message provided", 240);
      return `code ${code}: ${message}`;
    }).join("; ") || "No structured Cloudflare error was returned";
    throw new DeploymentFailure(`Cloudflare ${endpoint} failed with HTTP ${response.status}: ${details}.`, codes);
  }
  return enveloped ? body.result : body;
}

export async function ensureAccountSubdomain(request) {
  const existing = await request("/workers/subdomain", { allowMissing: true });
  if (existing !== null) return existing;
  try {
    await request("/workers/subdomain", { method: "PUT", json: { subdomain: FIRST_SUBDOMAIN } });
  } catch (error) {
    if (error instanceof DeploymentFailure && error.cloudflareCodes.includes(10031)) {
      throw new DeploymentFailure(`The workers.dev label "${FIRST_SUBDOMAIN}" is unavailable (Cloudflare code 10031). Register an available workers.dev subdomain for this account in Cloudflare, then rerun the workflow. No alternative name was selected. ${error.message}`, [10031]);
    }
    throw error;
  }
  const verified = await request("/workers/subdomain");
  ensure(verified?.subdomain === FIRST_SUBDOMAIN,
    "Cloudflare did not confirm the newly registered workers.dev subdomain. No API origin was guessed.");
  return verified;
}

async function requestJson(origin, path, options = {}, timeoutMs = 35_000) {
  const response = await fetch(`${origin}${path}`, {
    ...options, redirect: "error", signal: AbortSignal.timeout(timeoutMs),
  });
  let body;
  try { body = await response.json(); } catch { body = null; }
  return { status: response.status, body };
}

async function readiness(origin) {
  const deadline = Date.now() + 90_000;
  do {
    try {
      const result = await requestJson(origin, "/q/health", {}, Math.min(65_000, Math.max(1, deadline - Date.now())));
      if (result.status === 200 && result.body?.status === "UP") return;
    } catch { /* Cold start or DNS propagation; keep the public response private. */ }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 3000));
  } while (Date.now() < deadline);
  throw new DeploymentFailure("Public health did not become UP within the bounded readiness window. Check Cloudflare's deployment status. On later releases, a running named container can retain its previous image/environment until idle shutdown; stop it through Cloudflare's controls before retrying.");
}

async function verify(origin, serviceToken) {
  await readiness(origin);
  console.log("Public health: UP.");
  const unauthenticated = await requestJson(origin, "/api/v1/capabilities");
  ensure(unauthenticated.status === 401, "Unauthenticated capabilities did not return HTTP 401.");
  const headers = { Authorization: `Bearer ${serviceToken}` };
  const capabilities = await requestJson(origin, "/api/v1/capabilities", { headers });
  ensure(capabilities.status === 200 && capabilities.body?.runtime === "java" &&
    capabilities.body?.engine === "statevector" && capabilities.body?.maxQubits === 6,
    "Authenticated capabilities did not identify the expected Java statevector API. A previously running container may retain an old token/image; stop it through Cloudflare's controls and rerun.");
  const circuit = { qubits: 2, operations: [{ gate: "h", target: 0 }, { gate: "cx", control: 0, target: 1 }] };
  const bell = await requestJson(origin, "/api/v1/simulate", {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify({ format: "json", source: JSON.stringify(circuit), includeSteps: true }),
  });
  ensure(bell.status === 200, "Authenticated Bell simulation did not return HTTP 200.");
  assertBellResult(bell.body);
  console.log("Access checks and Java Bell simulation with intermediate states passed.");
}

async function main() {
  ensure(process.env.GITHUB_ACTIONS === "true" && process.env.GITHUB_REPOSITORY === REPOSITORY &&
    process.env.GITHUB_REF === BRANCH_REF,
    "This deployment entry point runs only in the StateCraft feature-branch GitHub Actions workflow.");
  const missing = missingSecrets(process.env);
  if (missing.length > 0) {
    const message = `Deployment skipped successfully. Add the repository Actions secrets ${missing.join(", ")} and rerun this workflow run. No Cloudflare resources were changed.`;
    console.log(message);
    await summary(message);
    return;
  }
  const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;
  const apiToken = process.env.CLOUDFLARE_API_TOKEN;
  const serviceToken = process.env.STATECRAFT_API_TOKEN;
  ensure(/^[a-f0-9]{32}$/i.test(accountId), "CLOUDFLARE_ACCOUNT_ID must contain the 32-character Cloudflare account ID.");
  ensure(!/[\r\n]/.test(apiToken) && !/[\r\n]/.test(serviceToken) && serviceToken.trim() === serviceToken,
    "Deployment tokens must be nonempty single-line values without surrounding whitespace.");
  const config = JSON.parse(await readFile(resolve(DIRECTORY, "wrangler.jsonc"), "utf8"));
  ensure(config.name === "statecraft-api" && config.workers_dev === true,
    "Review the deployment script before changing the Worker name or workers.dev setting.");
  const accountRequest = (suffix, options = {}) => cloudflare(accountId, apiToken, suffix,
    { ...options, redactSecrets: [serviceToken] });
  const account = await accountRequest("");
  ensure(account?.id === accountId,
    "Cloudflare account details did not confirm CLOUDFLARE_ACCOUNT_ID. Check the account ID and Account Settings Read permission before deploying.");
  const settingsPath = `/workers/scripts/${config.name}/settings`;
  const before = await accountRequest(settingsPath, { allowMissing: true });
  assertOwnedWorker(before);
  await accountRequest("/containers/me", { enveloped: false });
  console.log("Cloudflare Containers access check passed.");
  const subdomain = await ensureAccountSubdomain(accountRequest);
  const origin = verifiedOrigin(config.name, subdomain?.subdomain);
  console.log("Cloudflare account and Worker ownership checks passed.");
  await wrangler(["deploy", "--keep-vars", "--strict", "--tag", VERSION_TAG,
    "--var", `${OWNER_KEY}:${OWNER_VALUE}`], "Deploying Worker and Java container image");
  // Do not request public health or otherwise cold-start a fresh container before this succeeds.
  // This installs the existing GitHub service secret unchanged; the workflow never generates or rotates it.
  await wrangler(["secret", "put", "STATECRAFT_API_TOKEN"], "Installing the existing simulator service secret", `${serviceToken}\n`);
  const after = await accountRequest(settingsPath);
  assertOwnedWorker(after);
  const workerSubdomain = await accountRequest(`/workers/scripts/${config.name}/subdomain`);
  ensure(workerSubdomain?.enabled === true, "Cloudflare reports that this Worker's workers.dev endpoint is disabled. No API origin was guessed.");
  await verify(origin, serviceToken);
  await summary(`Verified API origin: ${origin}\n\nChecks passed: public health UP; unauthenticated capabilities 401; authenticated Java statevector capabilities; Bell probabilities 0.5 at |00> and |11>; initial, Hadamard, and entangled amplitude steps.\n\nSet this origin as the Site's server-only STATECRAFT_API_URL. Keep its server-only STATECRAFT_API_TOKEN equal to the existing repository secret.`);
  console.log(`Verified API origin: ${origin}`);
}

async function selfTest() {
  assert.equal(redactDiagnostics("example: fake-provider-token fake-service-token", ["fake-provider-token", "fake-service-token"]),
    "example: [REDACTED] [REDACTED]");
  assert.deepEqual(missingSecrets({ STATECRAFT_API_TOKEN: "test-value" }), ["CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID"]);
  assertOwnedWorker(null);
  assert.throws(() => assertOwnedWorker({ bindings: [] }), /name collision/);
  const owned = { bindings: [{ type: "plain_text", name: OWNER_KEY, text: OWNER_VALUE },
    { type: "durable_object_namespace", name: "STATECRAFT_API" }] };
  assertOwnedWorker(owned);
  assert.throws(() => assertOwnedWorker({ bindings: [owned.bindings[0]] }), /name collision/);
  assert.equal(verifiedOrigin("statecraft-api", "verified-account"), "https://statecraft-api.verified-account.workers.dev");
  assert.throws(() => verifiedOrigin("statecraft-api", "example.com/path"), /No API origin was guessed/);
  assert.throws(() => verifiedOrigin("statecraft-api", undefined), /No API origin was guessed/);
  assert.throws(() => verifiedOrigin("statecraft-api", null), /No API origin was guessed/);
  const h = Math.SQRT1_2;
  const steps = [[1, 0, 0, 0], [h, h, 0, 0], [h, 0, 0, h]].map((state) => state.map((re) => ({ re, im: 0 })));
  const bell = { runtime: "java", engine: "statevector", qubits: 2, probabilities: [.5, 0, 0, .5], steps, amplitudes: steps[2] };
  assertBellResult(bell);
  assert.throws(() => assertBellResult({ ...bell, probabilities: [1, 0, 0, 0] }), /probabilities/);
  assert.throws(() => assertBellResult({ ...bell, steps: steps.slice(1) }), /intermediate/);
  const mockResponse = (status, body) => new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": "application/json" },
  });
  const fakeAccount = "a".repeat(32);
  const bareAccount = { enabled: true, fakeAccountInformation: "not-for-logs" };
  const access = await cloudflare(fakeAccount, "fake-provider-token", "/containers/me", {
    enveloped: false,
    fetchImplementation: async () => mockResponse(200, bareAccount),
  });
  assert.deepEqual(access, bareAccount);
  for (const errorBody of [
    { error: "Authentication error fake-provider-token fake-service-token", accountInformation: "not-for-logs" },
    { code: "AUTHORIZATION_ERROR", message: "Authentication error fake-provider-token fake-service-token" },
    { error: { code: 403, message: "Authentication error fake-provider-token fake-service-token" } },
  ]) {
    await assert.rejects(cloudflare(fakeAccount, "fake-provider-token", "/containers/me", {
      enveloped: false, redactSecrets: ["fake-service-token"],
      fetchImplementation: async () => mockResponse(403, errorBody),
    }), (error) => error.message.includes("GET /accounts/<account-id>/containers/me") &&
      error.message.includes("HTTP 403") && error.message.includes("Authentication error [REDACTED] [REDACTED]") &&
      !error.message.includes("fake-provider-token") && !error.message.includes("fake-service-token") &&
      !error.message.includes("not-for-logs"));
  }
  await assert.rejects(cloudflare(fakeAccount, "fake-provider-token", "/containers/me", {
    enveloped: false, fetchImplementation: async () => mockResponse(403, { code: "FEATURE_DISABLED", unrelated: "not-for-logs" }),
  }), (error) => error.message.includes("code FEATURE_DISABLED: No error message provided") &&
    !error.message.includes("not-for-logs"));
  await assert.rejects(cloudflare(fakeAccount, "fake-provider-token", "/workers/subdomain", {
    enveloped: false, fetchImplementation: async () => { assert.fail("An unsupported bare-response route must not connect"); },
  }), /only for the Containers access preflight/);
  const existingCalls = [];
  const existing = await ensureAccountSubdomain(async (path, options = {}) => {
    existingCalls.push({ path, method: options.method ?? "GET" });
    return { subdomain: "preserved-account" };
  });
  assert.equal(existing.subdomain, "preserved-account");
  assert.deepEqual(existingCalls, [{ path: "/workers/subdomain", method: "GET" }]);

  const creationCalls = [];
  const creationResponses = [
    [404, { success: false, errors: [{ code: 10007, message: "Subdomain not registered" }] }],
    [200, { success: true, result: { subdomain: FIRST_SUBDOMAIN } }],
    [200, { success: true, result: { subdomain: FIRST_SUBDOMAIN } }],
  ];
  const created = await ensureAccountSubdomain((path, options = {}) => cloudflare(fakeAccount, "fake-provider-token", path, {
    ...options,
    fetchImplementation: async (_url, init) => {
      creationCalls.push({ method: init.method, json: init.body ? JSON.parse(init.body) : null });
      const [status, body] = creationResponses.shift();
      return mockResponse(status, body);
    },
  }));
  assert.equal(created.subdomain, FIRST_SUBDOMAIN);
  assert.deepEqual(creationCalls, [{ method: "GET", json: null },
    { method: "PUT", json: { subdomain: FIRST_SUBDOMAIN } }, { method: "GET", json: null }]);

  await assert.rejects(cloudflare(fakeAccount, "fake-provider-token", "/workers/subdomain", {
    allowMissing: true, redactSecrets: ["fake-service-token"],
    fetchImplementation: async () => mockResponse(404, { success: false,
      errors: [{ code: 7003, message: "Wrong route fake-provider-token fake-service-token" }] }),
  }), (error) => error.message.includes("GET /accounts/<account-id>/workers/subdomain") &&
    error.message.includes("HTTP 404") && error.message.includes("code 7003: Wrong route [REDACTED] [REDACTED]") &&
    !error.message.includes("fake-provider-token") && !error.message.includes("fake-service-token"));

  const conflictResponses = [
    [404, { success: false, errors: [{ code: 10007, message: "Subdomain not registered" }] }],
    [409, { success: false, errors: [{ code: 10031, message: "Subdomain is unavailable" }] }],
  ];
  await assert.rejects(ensureAccountSubdomain((path, options = {}) => cloudflare(fakeAccount, "fake-provider-token", path, {
    ...options, fetchImplementation: async () => {
      const [status, body] = conflictResponses.shift();
      return mockResponse(status, body);
    },
  })), /omaarr90-statecraft.*unavailable.*10031/);
  assert.equal(conflictResponses.length, 0);
  console.log("Deployment safeguards passed offline: secret redaction, ownership collision, verified origin, analytic Bell states, preserved subdomain, first-time registration, unexpected 404 rejection, deterministic-label conflict, and bare Containers access/403 diagnostics.");
}

if (process.argv.includes("--self-test")) {
  await selfTest();
} else if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { await main(); }
  catch (error) {
    const message = error instanceof DeploymentFailure ? error.message : "Deployment failed unexpectedly. Raw diagnostics were withheld to protect secrets.";
    console.error(message);
    await summary(`Deployment or verification failed. ${message}`);
    process.exitCode = 1;
  }
}
