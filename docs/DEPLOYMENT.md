# Deploying Resto Rant

Resto Rant is one Java 21 Spring Boot app (it serves the website and the API together) plus a MySQL database. Cloudflare cannot run Java servers, so the app runs on a container host and Cloudflare provides the domain, DNS and HTTPS in front of it.

```text
Visitor → Cloudflare (your domain, HTTPS) → container host (Dockerfile from this repo) → managed MySQL
```

The `Dockerfile` in the repository root builds the app. These steps use Render as the example; Railway and Fly.io work the same way with the same environment variables.

## 1. Create the MySQL database

Use a managed MySQL 8 (Render does not offer MySQL itself; Railway, Aiven and DigitalOcean do). Create a database named `resto_rant` and two users:

- an app user with SELECT, INSERT, UPDATE, DELETE on `resto_rant.*`
- a migration user with ALL PRIVILEGES on `resto_rant.*` (Flyway uses it to create and upgrade tables)

Note the host, port and passwords. Use a long random password for each account.

## 2. Create the web service

1. In Render choose **New → Web Service** and connect the GitHub repository.
2. Language: **Docker**. The Dockerfile is at the repository root, so leave the root directory empty.
3. Pick an instance with at least 1 GB of RAM. Java needs more than the smallest free tiers give, and free instances sleep or lose data.
4. Under **Environment**, add the variables below. Keep them as secrets; never commit them (`.env.example` lists the names).

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:mysql://HOST:PORT/resto_rant?sslMode=REQUIRED&connectionTimeZone=UTC` |
| `DB_USERNAME` / `DB_PASSWORD` | the app user |
| `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD` | the migration user |
| `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` | your first administrator (password at least 10 characters). It is created only if that email does not exist yet |
| `GCASH_NUMBER` | `09778021234` |
| `GCASH_ACCOUNT_NAME` | the name on the GCash account (customers will see it) |

`COOKIE_SECURE=true`, `SERVER_ADDRESS=0.0.0.0` and proxy header handling are already set by the image. The host's `PORT` is picked up automatically.

5. Deploy. The first start creates every table. Open the host's URL, log in as the administrator, go to **Dashboard → Tables** and click **Apply 25-table / 100-seat layout**.

## 3. Point your Cloudflare domain at it

1. In the host, open the service's **Custom Domains** and add your domain, for example `www.yourdomain.com`. It shows the target to point at.
2. In Cloudflare, **DNS → Add record**: type `CNAME`, name `www`, target as shown by the host, proxy status **Proxied** (orange cloud).
3. **SSL/TLS → Overview**: set the mode to **Full (strict)**.
4. Optional: add a redirect rule from the bare domain to `www`.

Wait a few minutes for the certificate, then open your domain.

## 4. Before real customers use it

- **Money:** the payment flow is manual. You must check the GCash account and verify each payment under **Dashboard → Payments**, and send refunds yourself. Nothing is confirmed automatically.
- **Admin password:** use a strong one and log in only over HTTPS.
- **Backups:** turn on automatic backups for the MySQL database. Reservations and payment records live only there.
- **Privacy:** the app stores names, emails, phone numbers and optional payment receipt images. Mention this to your customers.
- **Maps:** the Google map on the homepage is a sample location (PLM). Replace it with your own.
- **Updates:** pushing to the connected branch redeploys. Database changes apply through Flyway automatically.

## Troubleshooting

| Problem | Likely cause |
|---|---|
| The deploy fails during the build | Check the build log on the host. The build needs about 1 GB of memory |
| App starts then stops | A wrong `DB_URL` or password. The first error line in the host log says which |
| "Access denied" in the log | The app and migration users or their passwords do not match the database |
| Too many redirects on the domain | Cloudflare SSL mode is Flexible. Set it to Full (strict) |
| Logged out on every page | `COOKIE_SECURE=true` needs HTTPS all the way. Open the site by `https://` |
