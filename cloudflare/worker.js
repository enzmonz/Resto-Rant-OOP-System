import { DurableObject } from "cloudflare:workers";

const PORT = 8080;
const REQUIRED = [
  "DB_URL", "DB_USERNAME", "DB_PASSWORD", "DB_MIGRATION_USERNAME",
  "DB_MIGRATION_PASSWORD", "SEED_ADMIN_EMAIL", "SEED_ADMIN_PASSWORD",
  "GCASH_NUMBER", "GCASH_ACCOUNT_NAME",
];

// A single named instance preserves Spring's in-memory login sessions across requests.
export class RestoRantContainer extends DurableObject {
  starting;

  async fetch(request) {
    this.starting ??= this.startAndWait().finally(() => { this.starting = undefined; });
    await this.starting;

    const original = new URL(request.url);
    const target = new URL(request.url);
    target.protocol = "http:";
    target.host = "container";
    const forwarded = new Request(target, request);
    forwarded.headers.delete("host");
    forwarded.headers.set("x-forwarded-host", original.host);
    forwarded.headers.set("x-forwarded-proto", original.protocol.slice(0, -1));
    return this.ctx.container.getTcpPort(PORT).fetch(forwarded);
  }

  async startAndWait() {
    const container = this.ctx.container;
    if (!container.running) {
      const missing = REQUIRED.filter((key) => !this.env[key]);
      if (missing.length) throw new Error(`Missing Worker secrets: ${missing.join(", ")}`);
      container.start({
        image: container.images.base,
        instance: "basic",
        enableInternet: true,
        env: {
          ...Object.fromEntries(REQUIRED.map((key) => [key, this.env[key]])),
          SERVER_ADDRESS: "0.0.0.0",
          COOKIE_SECURE: "true",
          FORWARD_HEADERS: "native",
        },
      });
    }
    await container.setInactivityTimeout(10 * 60 * 1000);
    const port = container.getTcpPort(PORT);
    for (let attempt = 0; attempt < 150; attempt++) {
      try {
        const response = await port.fetch("http://container/api/info", {
          signal: AbortSignal.timeout(1000),
        });
        await response.body?.cancel();
        if (response.ok) return;
      } catch {
        // The JVM and database migrations can take time on a cold start.
      }
      await scheduler.wait(300);
    }
    throw new Error("Resto Rant did not become ready on port 8080");
  }
}

export default {
  fetch(request, env) {
    return env.RESTO_RANT.getByName("resto-rant").fetch(request);
  },
};
