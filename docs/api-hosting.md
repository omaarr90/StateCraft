# Hosting the Java simulator API

The Quarkus API runs the canonical Java 25 simulator, including the incubating
Vector API. Use the JVM image in [`api/Dockerfile`](../api/Dockerfile); a GraalVM
native executable is not required or claimed to work for this engine.

Ordinary Cloudflare Workers use V8 and do not provide a JVM. Workers' WebAssembly
support does not make the existing Quarkus JAR or Linux native executable a Worker.
Cloudflare **Containers** can run this API image behind a Worker. Google **Cloud
Run** is the simpler starting point for a stateless HTTP API with managed request
autoscaling. Both examples below are deployment preparations; neither provisioned
an account, uploaded an image, registered a domain, or deployed a service.

| Path | Runtime | Scaling in this example | Provider prerequisites |
| --- | --- | --- | --- |
| [Cloud Run](../deploy/cloud-run/) | Java 25 Linux container | 0–2 instances, 4 concurrent HTTP requests per instance | A configured Google Cloud project, billing, registry, service identity, and Secret Manager secret |
| [Cloudflare Containers](../deploy/cloudflare/) | Java 25 Linux container reached through a Worker | One named Durable Object/container, idle shutdown after 5 minutes | Workers Paid, a Docker-compatible builder, and current Wrangler |

Cloudflare's `durable_object` scheduling policy is in public beta. Its older
`default` policy and the `@cloudflare/containers` wrapper are deprecated in the
current documentation. This example uses `DurableObject` from `cloudflare:workers`
and `ctx.container` directly, with the current `exports` configuration.

## Server proxy and secrets

The public Site should call its own server route, which forwards requests to the
Java service. Configure these values only in the Site's server environment:

- `STATECRAFT_API_URL`: the deployment's HTTPS origin, without `/api/v1`.
- `STATECRAFT_API_TOKEN`: a long random service token stored as a server secret.

The server sends `Authorization: Bearer <service token>` on `/api/v1/*` calls.
The Java service must receive the same `STATECRAFT_API_TOKEN` from the hosting
provider's secret store. Never put the token in browser code, a `NEXT_PUBLIC_*` or
`VITE_*` variable, source control, Docker build arguments, CLI arguments, or logs.
The browser must not choose the upstream origin. CORS does not replace this token.

`https://api-state-craft.omaarr90.com` is an **example desired hostname**. It has
not been registered, mapped, or checked for ownership by this work. Start with the
provider URL; configure a custom domain separately after deployment. The examples
contain no account IDs, zone IDs, or domain routes.

## Cloud Run

The [deployment script](../deploy/cloud-run/deploy.sh) submits the repository root
to Cloud Build using [`cloudbuild.yaml`](../deploy/cloud-run/cloudbuild.yaml).
Cloud Build builds `api/Dockerfile` for `linux/amd64` and pushes the image. The
script then deploys the image to Cloud Run, binding a **numeric version** of an
existing Secret Manager secret as `STATECRAFT_API_TOKEN`.
Its upload allowlist includes the Gradle modules and build configuration, excluding
local environment files, Worker test secrets, package dependencies, and build output.

Prepare these resources in the chosen project before running it:

1. Enable Cloud Build, Artifact Registry, Cloud Run, and Secret Manager APIs and
   configure billing. Authenticate the Google Cloud CLI yourself.
2. Create a Docker Artifact Registry repository. Give the build identity the
   permissions needed to write there and run builds. Give the deployer the
   permissions needed to deploy Cloud Run and use the runtime service account.
3. Create a dedicated runtime service account. In Secret Manager's console,
   create a secret containing the API token using the provider's secret input,
   and grant that runtime identity Secret Manager Secret Accessor on that secret.
   Record the secret name and numeric version, not its value.
4. Choose a supported region and an image URL in the existing repository. Use a
   unique image tag for each release. All arguments below are resource names.

From the repository root, replace the example resource identifiers:

```sh
bash deploy/cloud-run/deploy.sh \
  YOUR_PROJECT YOUR_REGION \
  YOUR_REGION-docker.pkg.dev/YOUR_PROJECT/YOUR_REPOSITORY/statecraft-api:YOUR_RELEASE \
  YOUR_RUNTIME_ACCOUNT@YOUR_PROJECT.iam.gserviceaccount.com \
  YOUR_TOKEN_SECRET 1
```

This command performs external writes when run. The script does not log in,
enable APIs, create registries, create identities, or create/read token values.
It configures a publicly reachable HTTPS service because the Site server uses
the application's Bearer token rather than a Google IAM identity token. The
Java API still rejects protected calls without the service token. `/q/health`
remains a public operational health endpoint.

The example sets one vCPU, 1 GiB RAM, four concurrent HTTP requests, zero minimum
instances, two maximum instances, and a 30-second request timeout. These are
initial playground settings, not a cost guarantee or a substitute for provider
quotas and budget monitoring. Cloud Run's request timeout does not forcibly
cancel work already running inside Java. The API separately limits simulation
size and concurrent execution. Review settings against observed traffic.

Copy the service's reported HTTPS origin into the Site server's
`STATECRAFT_API_URL`. Put the matching token into its server secret store. To
rotate a token, add a Secret Manager version, update the Site secret, and deploy
a revision with that numeric version; coordinate the switch to avoid mismatches.

## Cloudflare Containers

The [Worker](../deploy/cloudflare/src/index.ts) forwards only the five known
`/api/v1/*` endpoints and `GET /q/health` to port 8080. API requests require the
service token before starting the container; Java validates the same token.
The Worker preserves the caller's Authorization header. It never turns an
unauthenticated incoming API call into an authenticated one.

The Worker buffers at most 256 KiB of body data, shares startup across concurrent
requests, allows up to 60 seconds for JVM readiness, and bounds each forwarded
HTTP request to 30 seconds. The container shuts down after 5 minutes of inactivity.
`/q/health` can wake it, so periodic health polling can keep it running. There is
one logical instance named `playground`; this is not request-based autoscaling.
The configured `standard-1` instance currently provides 0.5 vCPU and 4 GiB RAM.
The Java process allows four concurrent simulations by default and returns 429
when that work limit is occupied. Container internet egress is disabled because
the simulator does not need it.

The [Wrangler configuration](../deploy/cloudflare/wrangler.jsonc) sets the named
image's Dockerfile to `../../api/Dockerfile` and `build_context` to `../..`, both
relative to `deploy/cloudflare/`. This builds the complete Gradle workspace
instead of an incomplete API-only directory. Cloudflare requires `linux/amd64`;
verify that architecture when building from Apple Silicon.

Prepare a Workers Paid account, authenticate Wrangler yourself, and start a
Docker-compatible engine. With Node.js and npm already installed, run:

```sh
cd deploy/cloudflare
npm install
npm run check
npx wrangler secret put STATECRAFT_API_TOKEN
npm run deploy
```

Enter the service token only at Wrangler's secret prompt. The secret is read by
the Durable Object and passed to the JVM at startup. It is absent from
`wrangler.jsonc`. If the Worker does not exist yet, Wrangler's secret workflow may
offer to create it; review the provider's account selection and resource prompt.
Do not put a production token in a local development file. Local-only test tokens
can be provided in `.dev.vars`, which this directory ignores.

These install/check/deploy commands have not been run as part of preparing the
example. They will install packages and write to the chosen Cloudflare account
when run. The configuration uses only the provider's `workers.dev` endpoint.
Copy its HTTPS origin to the Site's server-only `STATECRAFT_API_URL`.

A running container keeps its startup image and environment. The current
`durable_object` policy does not restart it automatically when a named image or
Worker secret changes. After a release or token rotation, explicitly stop/restart
the existing container through the provider's controls, or allow it to reach its
idle shutdown before sending traffic. Check readiness and the deployed API's
capabilities afterward. Container disks are ephemeral; the simulator stores no
session state there.

## Deployment checks

After either provider deployment, check `GET /q/health`, then call
`GET /api/v1/capabilities` through the Site's authenticated server proxy. Confirm
that it reports the Java statevector engine with the expected limits: 6 qubits,
256 operations, and 10,000 shots. Exercise a Bell-state circuit and the parser,
collapse, and noise flows through the Site. Confirm a direct API request without
the token receives 401. Do not include secrets in copied terminal output.

## Official platform references

- [Workers languages](https://developers.cloudflare.com/workers/languages/) and
  [V8 runtime](https://developers.cloudflare.com/workers/reference/how-workers-works/)
- [Cloudflare Containers overview and plan prerequisite](https://developers.cloudflare.com/containers/)
- [Current scheduling policy and image build configuration](https://developers.cloudflare.com/containers/configuration/scheduling-policy/)
- [Migration from the deprecated Containers package](https://developers.cloudflare.com/containers/guides/migrate-to-durable-object-container-api/)
- [Container runtime secrets](https://developers.cloudflare.com/containers/examples/env-vars-and-secrets/),
  [limits](https://developers.cloudflare.com/containers/platform/limits/), and
  [ephemeral disk behavior](https://developers.cloudflare.com/containers/faq/)
- [Cloud Run container contract](https://docs.cloud.google.com/run/docs/container-contract)
- [Cloud Build Docker configuration](https://docs.cloud.google.com/build/docs/building/build-containers)
- [Cloud Run Secret Manager bindings](https://docs.cloud.google.com/run/docs/configuring/services/secrets)
