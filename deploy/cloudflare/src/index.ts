import { DurableObject } from "cloudflare:workers";

interface Env {
  STATECRAFT_API: DurableObjectNamespace<StatecraftApi>;
  STATECRAFT_API_TOKEN: string;
}

const MAX_BODY_BYTES = 256 * 1024;
const IDLE_TIMEOUT_MS = 5 * 60 * 1000;
const STARTUP_TIMEOUT_MS = 60 * 1000;
const REQUEST_TIMEOUT_MS = 30 * 1000;
const ROUTES = new Map([
  ["/api/v1/capabilities", "GET"],
  ["/api/v1/parse", "POST"],
  ["/api/v1/simulate", "POST"],
  ["/api/v1/collapse", "POST"],
  ["/api/v1/noise", "POST"],
  ["/q/health", "GET"],
]);

function failure(status: number, code: string, message: string): Response {
  return Response.json({ error: { code, message } }, { status });
}

async function boundedRequest(request: Request): Promise<Request> {
  if (!request.body) return request;
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY_BYTES) {
        await reader.cancel();
        throw failure(413, "REQUEST_TOO_LARGE", "Request body exceeds 256 KiB.");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const body = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new Request(request, { body });
}

export class StatecraftApi extends DurableObject<Env> {
  private starting: Promise<void> | undefined;

  async fetch(request: Request): Promise<Response> {
    try {
      // One startup promise keeps concurrent requests from starting the JVM twice.
      this.starting ??= this.startAndWaitForHealth().finally(() => {
        this.starting = undefined;
      });
      await this.starting;

      const url = new URL(request.url);
      url.protocol = "http:";
      url.host = "container";
      const forwarded = new Request(url, request);
      forwarded.headers.delete("host");
      return await this.ctx.container!.getTcpPort(8080).fetch(
        new Request(forwarded, { signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) }),
      );
    } catch {
      // Never return upstream exception messages or runtime environment values.
      return failure(503, "API_UNAVAILABLE", "Simulator API is starting or unavailable. Retry shortly.");
    }
  }

  private async startAndWaitForHealth(): Promise<void> {
    const container = this.ctx.container!;
    if (!container.running) {
      container.start({
        image: container.images.api,
        instance: "standard-1",
        enableInternet: false,
        env: {
          PORT: "8080",
          STATECRAFT_API_TOKEN: this.env.STATECRAFT_API_TOKEN,
          STATECRAFT_MAX_CONCURRENT: "4",
        },
      });
    }
    await container.setInactivityTimeout(IDLE_TIMEOUT_MS);
    const deadline = Date.now() + STARTUP_TIMEOUT_MS;
    const port = container.getTcpPort(8080);
    while (Date.now() < deadline) {
      try {
        const response = await port.fetch("http://container/q/health", {
          signal: AbortSignal.timeout(Math.max(1, Math.min(1000, deadline - Date.now()))),
        });
        await response.body?.cancel();
        if (response.ok) return;
      } catch {
        // A newly started container may not have opened its HTTP port yet.
      }
      await scheduler.wait(200);
    }
    throw new Error("Simulator readiness deadline exceeded");
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const pathname = new URL(request.url).pathname;
    const method = ROUTES.get(pathname);
    if (!method) return failure(404, "NOT_FOUND", "Unknown API route.");
    if (request.method !== method) {
      return new Response(null, { status: 405, headers: { Allow: method } });
    }
    if (!env.STATECRAFT_API_TOKEN) {
      return failure(503, "API_NOT_CONFIGURED", "Simulator API is not configured.");
    }
    if (pathname !== "/q/health" &&
        request.headers.get("Authorization") !== `Bearer ${env.STATECRAFT_API_TOKEN}`) {
      return failure(401, "UNAUTHORIZED", "A valid API service token is required.");
    }

    try {
      const forwarded = await boundedRequest(request);
      // A fixed name bounds this playground to one logical container instance.
      return await env.STATECRAFT_API.getByName("playground").fetch(forwarded);
    } catch (error) {
      if (error instanceof Response) return error;
      return failure(503, "API_UNAVAILABLE", "Simulator API is unavailable. Retry shortly.");
    }
  },
} satisfies ExportedHandler<Env>;
