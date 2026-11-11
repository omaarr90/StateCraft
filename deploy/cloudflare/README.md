# Statecraft API on Cloudflare Containers

This deployment runs the Quarkus JVM application from `api/Dockerfile`. The Worker forwards the allowlisted API routes to one named Durable Object container. The Java simulator remains in the existing `core` and `engines` modules.

## Deploy through GitHub Actions

The `Cloudflare API` workflow runs for deployment-related pushes to `feat/quarkus-simulator-api`. It uses GitHub's encrypted repository secrets, so Cloudflare's interactive device-login flow is not needed.

Configure these repository secrets:

| Secret | Value |
| --- | --- |
| `CLOUDFLARE_ACCOUNT_ID` | The Cloudflare account to deploy into. |
| `CLOUDFLARE_API_TOKEN` | A custom Cloudflare token restricted to that account, with Workers Scripts Edit, Containers Edit, and Account Settings Read. |
| `STATECRAFT_API_TOKEN` | A strong service token shared with the website's server-side proxy. Keep this value stable across deployments. |

Create the Cloudflare token in [API Tokens](https://dash.cloudflare.com/profile/api-tokens) and enter it directly into [GitHub Actions secrets](https://github.com/omaarr90/StateCraft/settings/secrets/actions). Token values belong in provider secret stores; source files and workflow logs must not contain them.

The workflow checks the Worker configuration, builds and uploads the Linux container, deploys the Worker, installs its service secret through stdin, and verifies the live Java API. It reports the actual `workers.dev` origin in the job summary. When required secrets are missing, the job explains what is missing and skips deployment; rerun it after configuring the secrets.

Before deploying, the workflow checks the configured account through Cloudflare's account-details API and verifies ownership of any existing Worker. It preserves an existing `workers.dev` account subdomain. If Cloudflare explicitly reports that no subdomain is registered (code `10007`), it registers `omaarr90-statecraft` and reads it back before constructing the API origin. If that label is unavailable (code `10031`), deployment stops with a concrete error; register an available account subdomain in Cloudflare, then rerun. Other HTTP 404 responses remain failures. Provider failures identify the endpoint, HTTP status, and bounded, sanitized error codes and messages; token values and raw response bodies are not logged.

Cloudflare Containers requires an eligible Workers account. An authorization or plan error during deployment is reported by the workflow; configuring a token does not change billing or upgrade the account.

## Connect the website

Set these production runtime values in Sites, then deploy the saved frontend version:

- `STATECRAFT_API_URL`: the verified HTTPS API origin from the deployment summary.
- `STATECRAFT_API_TOKEN`: the same service token configured in GitHub and the Worker.
- `STATECRAFT_API_TIMEOUT_MS`: optional; the frontend defaults to 90,000 ms to allow a cold container to start.

The browser calls the website's `/api/statecraft/*` proxy. The service credential stays in the server environment. The proxy calls the real Java simulator; no browser quantum executor is used.

## Local deployment and updates

Use the GitHub workflow for the first deployment; it records an ownership marker so later runs can identify this repository's Worker. For subsequent local updates, install the locked tooling with `npm ci`, run `npm run check` and `npm run check:bundle`, then use `npm run deploy` with Docker running and Cloudflare authenticated. The local deploy command preserves the ownership marker and runtime variables. The safe bundle check disables container rollout; an ordinary Wrangler dry run can still build container images.

The container sleeps after five minutes without activity. With the `durable_object` scheduling policy, a running container retains its current image and environment until it stops. After an update, allow it to become idle before verifying the new image. Repeated health polling keeps an existing container active. Keep the service token unchanged during ordinary redeployments; a deliberate token rotation also requires restarting the existing container.

See [Cloudflare's GitHub Actions authentication instructions](https://developers.cloudflare.com/workers/ci-cd/external-cicd/github-actions/) and [Container configuration reference](https://developers.cloudflare.com/containers/reference/wrangler-configuration/).
