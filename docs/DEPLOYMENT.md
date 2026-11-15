# Deploying Resto Rant on Cloudflare

Resto Rant is a Java 21 Spring Boot app that serves the website and API from one origin. Cloudflare Containers can run the repository's Dockerfile behind a Worker. The database must be a separate managed MySQL 8 service; Container disk is not durable application storage. Cloudflare Containers requires a Workers Paid plan. See the [Cloudflare Containers overview](https://developers.cloudflare.com/containers/) and [deployment guide](https://developers.cloudflare.com/containers/guides/deploy/).

```text
Visitor → Cloudflare Worker → one Resto Rant Container → managed MySQL
```

The Worker routes all requests to one named Container because Spring currently stores login sessions in memory. A restart or ten minutes of inactivity may end sessions, so users may need to sign in again. This is a single-instance setup; scaling beyond one instance requires shared session storage.

## 1. Prepare MySQL

Create a managed MySQL 8 database named `resto_rant`, reachable from Cloudflare Containers over the internet with TLS. Create an app user with SELECT, INSERT, UPDATE, DELETE on `resto_rant.*`, and a migration user with ALL PRIVILEGES on `resto_rant.*`. Allow the provider's appropriate network access. The first application start runs Flyway migrations. Turn on database backups.

Use a JDBC URL like `jdbc:mysql://HOST:3306/resto_rant?sslMode=VERIFY_IDENTITY&connectionTimeZone=UTC` when the server certificate validates against its hostname. If your provider requires a different TLS mode, follow its JDBC instructions; do not disable TLS.

## 2. Prepare Cloudflare

Install Node.js and Docker, and start Docker. From the repository root:

```powershell
npm install
npx wrangler login
docker info
```

The Cloudflare account needs a Workers Paid plan. The Container uses the `basic` size (1 GiB RAM). The [instance types](https://developers.cloudflare.com/containers/platform/limits/) page lists current sizes and costs.

## 3. Set Worker secrets

Run each command from the repository root. Wrangler prompts for each value. Do not put passwords in `wrangler.jsonc`, shell history, or Git.

```powershell
npx wrangler secret put DB_URL
npx wrangler secret put DB_USERNAME
npx wrangler secret put DB_PASSWORD
npx wrangler secret put DB_MIGRATION_USERNAME
npx wrangler secret put DB_MIGRATION_PASSWORD
npx wrangler secret put SEED_ADMIN_EMAIL
npx wrangler secret put SEED_ADMIN_PASSWORD
npx wrangler secret put GCASH_NUMBER
npx wrangler secret put GCASH_ACCOUNT_NAME
```

Use a strong administrator password of at least 10 characters. The initial administrator is created only if the email does not exist. `GCASH_NUMBER` and `GCASH_ACCOUNT_NAME` are shown to customers; staff must verify transfers manually.

## 4. Deploy and check

```powershell
npm run deploy
```

Wrangler builds the Dockerfile, uploads the image, and prints a `workers.dev` URL. First provisioning can take several minutes. Open the URL and check `/api/info`. Then sign in as the administrator, open **Dashboard → Tables**, and apply the **25-table / 100-seat layout**. Make a test booking and confirm it appears in the dashboard.

If the container fails to start, inspect **Workers & Pages → Containers** and the Worker logs. The most common causes are an invalid database URL, missing secrets, a database firewall, or failed migrations. A cold start waits for the app to answer `/api/info`; database startup can take longer than a normal request.

## 5. Connect a domain and GitHub

In **Workers & Pages**, open the `resto-rant` Worker and add your custom domain under **Settings → Domains & Routes**. Cloudflare handles DNS and HTTPS for a domain in the account. Test the custom domain before sharing it.

For automatic updates, connect this GitHub repository under the Worker's **Settings → Builds**. Use the repository root as the build root and `npm run deploy` as the production deploy command. Keep the secrets in the Worker settings. Cloudflare's [Workers Builds guide for Containers](https://developers.cloudflare.com/containers/guides/deploy/#deploy-with-workers-builds) notes that non-production branch uploads do not roll out container images; use a separate staging Worker for full previews.

## Before real customers use it

- Confirm payment claims against the GCash account yourself and process refunds manually.
- Keep MySQL backups enabled; reservations and payment records live there.
- Replace the sample PLM map location with the restaurant's address.
- Publish a privacy notice for stored names, contact information, and optional payment receipt images.
- Confirm the domain uses HTTPS and that login, logout, CSRF-protected actions, and booking work through it.

If you do not have a Workers Paid plan, the existing Dockerfile can run on a separate container host with Cloudflare DNS and proxy in front. The same MySQL and environment variables apply, but Cloudflare Pages alone cannot run this Spring Boot server.
