# Resto Rant

A full-stack restaurant reservation system built with **Java 21, Spring Boot, MySQL, and HTML, CSS, and JavaScript**.

Customers can sign up, check open tables and book a reservation. Admins get a dashboard to confirm bookings and manage tables and customers.

---

## Quick start (for my groupmates mga ya)

You only need **one thing installed: Java 21**. Maven and the database are handled for you.

### 1. Install Java 21

1. Go to **https://adoptium.net/** and download **Temurin 21 (LTS)** for your computer.
2. Run the installer. On the "Custom Setup" screen, click **Set JAVA_HOME variable** and choose **"Will be installed on local hard drive"**.
3. Close and reopen any open PowerShell / terminal windows.

Check it worked:

```bash
java -version
```

It should say `21` somewhere in the first line. If it says `1.8` or another number, see [Troubleshooting](#troubleshooting).

### 2. Get the code

**With Git:**

```bash
git clone https://github.com/enzmonz/Resto-Rant-OOP-System.git
cd Resto-Rant-OOP-System
```

**Without Git:** on the GitHub page click **Code → Download ZIP**, unzip it, and open the folder.

### 3. Start the app (Windows)

Open PowerShell **inside the project folder** (in File Explorer: click the address bar, type `powershell`, press Enter) and run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-backend.ps1 demo
```

What happens:

- **The first run can take several minutes.** The wrapper downloads Maven, then Maven downloads the project's dependencies. Keep PowerShell open while this runs; later starts are faster. If no new output appears for more than 5 minutes, see [Troubleshooting](#troubleshooting).
- **A login box pops up** asking you to choose the **admin email and password** (password must be at least 10 characters). Save them somewhere safe; this is how you log in to the admin dashboard. On later runs, press **Cancel**: the admin account is already saved.
- When you see `Started RestoRantApplication`, the app is ready.

### 4. Open it

Go to **http://127.0.0.1:8080** in your browser.

- **Customer:** click **Log in → Create an account**, then **Book a table**.
- **Admin:** log in with the admin email and password you chose in step 3.

Each reserved table has a **package price by its number of seats** (sample prices: ₱600 for 2 seats, ₱1,100 for 4, ₱1,600 for 6), and a reservation costs the sum of its tables. Customers pay by **manual GCash transfer to 0977 802 1234** and submit the reference number; the payment stays *pending* until an administrator finds the transfer in that account and marks it paid under **Dashboard → Payments**. Prices are edited under **Dashboard → Package prices**. See [Payments and refunds](#payments-and-refunds).

To stop the app, go back to PowerShell and press **Ctrl + C**.

Your data (accounts, bookings) is saved in `backend/.local/` on your own computer and stays there between restarts. Each groupmate has their own separate data.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `Java 21 (JDK) was not found` | Install Temurin 21 (step 1). Make sure you ticked **Set JAVA_HOME**, then reopen PowerShell. |
| `java -version` shows `1.8` | An old Java is first on your PATH. That's OK as long as JAVA_HOME points to Java 21; the script uses JAVA_HOME. |
| `running scripts is disabled on this system` | Use the full command from step 3 (`powershell -NoProfile -ExecutionPolicy Bypass -File ...`). |
| `Port 8080 was already in use` | Another program (or a second copy of the app) is using it. Close it, or run `$env:SERVER_PORT = '8081'` first and open http://127.0.0.1:8081 instead. |
| Forgot the admin password | Stop the app, then follow [Reset the demo database](#reset-the-demo-database). This deletes all local accounts and bookings. |
| "No tables are available" | A start time is only offered when the whole stay fits before the 10 PM closing time (restaurant time, Asia/Manila), so late in the day there are no slots left today. Pick tomorrow's date or a shorter stay. |
| My HTML/CSS/JS changes don't show | Stop the app (Ctrl + C) and start it again. The frontend is packaged when the app starts. Then hard-refresh the browser (Ctrl + F5). |
| First run fails while downloading | Check your internet connection and run the command again. |
| Startup seems stuck | The first start downloads Maven dependencies. Wait a few minutes and watch for new output. If there is no new output after 5 minutes, copy the last lines from PowerShell and check your internet connection. Do not delete `backend/.local` unless you intend to reset the demo data. |

### Reset the demo database

Use this if you forgot the admin password or want a clean demo database. Stop the app first with **Ctrl + C**. This removes **all local accounts, reservations, and demo settings**; it cannot be undone. If you want to keep a backup, copy `backend/.local/` somewhere safe before continuing.

In PowerShell opened in the project folder, run:

```powershell
Remove-Item -Recurse -Force .\backend\.local
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-backend.ps1 demo
```

When prompted, create a new admin email and password (at least 10 characters). Wait for `Started RestoRantApplication`, then open http://127.0.0.1:8080. The app recreates the database and demo tables on startup.

---

## Working on the project together

- **Before you start working:** `git pull` to get everyone's latest changes.
- **Run the tests before you push:**
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\run-backend.ps1 verify
  ```
  It should end with `BUILD SUCCESS`.
- **Don't commit** `backend/.local/`, `backend/target/`, `.tools/`, passwords, or `.env` files. The `.gitignore` already skips these.
- Where things live:
  - Web pages (what users see): `frontend/` (`index.html`, `app.js`, `style.css`)
  - Server logic: `backend/src/main/java/com/restorant/`
  - Database tables: `backend/src/main/resources/db/migration/`
  - Tests: `backend/src/test/java/com/restorant/`

---

## Features

### Customer Features
- Customer registration and login.
- Secure password hashing using BCrypt.
- Browse restaurant information and operating hours.
- Check reservation availability.
- Create restaurant reservations.
- Select and combine 2-, 4-, and 6-seat tables, or use automatic assignment.
- Date-first availability, color-coded time slots, and alternative date suggestions.
- Assistance and dietary requests with an expanding text area.
- Food and drinks menu on the homepage (Filipino, Chinese, Japanese, drinks). It is a menu listing only; there is no online ordering.
- Itemized package price per selected table, shown before reserving and stored as a price snapshot.
- “Assign tables for me” highlights the exact tables that will be booked.
- Manual GCash transfer with reference number and optional receipt image; pending until staff verify it.
- Cancel a whole reservation or only some of its tables.
- View reservation details and booking history.
- Cancel eligible reservations.
- Session-based authentication.

### Administration Features
- Secure administrator login.
- Dashboard with live reservation and customer statistics.
- View, search, and filter reservations.
- Confirm, cancel, and complete reservations.
- Manage restaurant tables and seating capacities.
- Check table availability.
- Search customers and view their reservation history.
- Register staff, create/edit/disable customers, and create/edit/cancel reservations.
- Review contact inquiries and edit table package prices.
- Verify or reject GCash payments against the receiving account.
- Track manual refunds (amount, reason, payment reference, refund reference, status, actor) with an audit history.
- Cancel selected tables of a multi-table reservation.

### Security and Reliability
- Role-based access control (RBAC).
- CSRF protection.
- Secure HTTP-only session cookies.
- Transactional reservation processing.
- Database-level locking to prevent overlapping bookings.
- Input validation and safe API responses.
- Flyway database migrations.
- Automated unit and integration testing.

## Technology Stack

| Component | Technology |
|---|---|
| Backend | Java 21 |
| Framework | Spring Boot 3.5 |
| Build Tool | Maven (via the included Maven Wrapper) |
| Frontend | HTML, CSS, JavaScript |
| Database | MySQL 8.0.16+ |
| Demo Database | H2 (no install needed) |
| ORM | Spring Data JPA / Hibernate |
| Database Migrations | Flyway |
| Authentication | Spring Security |
| Password Hashing | BCrypt |
| Testing | JUnit, Mockito, Spring Boot Test |

## Project Structure

```text
Resto Rant/
├── backend/
│   ├── mvnw.cmd                Maven Wrapper (downloads Maven automatically)
│   ├── pom.xml                 Build settings and libraries
│   └── src/
│       ├── main/
│       │   ├── java/com/restorant/
│       │   │   ├── config/         Settings and demo data
│       │   │   ├── controller/     API endpoints
│       │   │   ├── dto/            Request/response objects
│       │   │   ├── entity/         User, Customer, Admin, Reservation, RestaurantTable
│       │   │   ├── exception/      Error handling
│       │   │   ├── repository/     Database access
│       │   │   ├── security/       Login and permissions
│       │   │   └── service/        Business logic
│       │   └── resources/
│       │       ├── application.yml         Main settings (MySQL)
│       │       ├── application-demo.yml    Demo settings (H2)
│       │       └── db/migration/           Database schema
│       └── test/                   Automated tests
├── frontend/
│   ├── index.html
│   ├── app.js
│   ├── style.css
│   └── assets/
├── database/
│   └── setup.sql               Creates the MySQL database
├── run-backend.ps1             Windows start/test script
└── README.md
```

The build packages the `frontend/` folder into the backend, so the website and the API run together from one app on port 8080.

## Running Commands

All commands are run in PowerShell from the project folder.

| Command | What it does |
|---|---|
| `.\run-backend.ps1 demo` | Start the app with the built-in demo database |
| `.\run-backend.ps1 verify` | Run all automated tests |
| `.\run-backend.ps1 run` | Start the app using MySQL (see below) |
| `.\run-backend.ps1 mysql-test` | Test the MySQL connection |

If PowerShell blocks the script, prefix each with `powershell -NoProfile -ExecutionPolicy Bypass -File`, as in the Quick start.

The script finds Java 21 automatically (from `JAVA_HOME`, the PATH, or the usual install folders).

### Demo Data

New demo databases automatically get **25 tables: 8 × 2 seats, 9 × 4 seats, and 8 × 6 seats (100 total)**. Customers can combine tables. The server requires the selected (or automatically assigned) tables themselves to have enough seats for the party, and prevents overlapping use of every table and more than 100 simultaneous guests. New databases also get the sample package prices.

Existing databases keep their tables and booking history. To adopt the new layout, use **Dashboard → Tables → Apply 25-table / 100-seat layout** after upcoming reservations are finished or otherwise resolved. The app will refuse to overwrite a layout used by upcoming reservations.

An optional sample customer (`guest@restorant.local`) with one reservation can be created by setting a password before starting:

```powershell
$env:SEED_CUSTOMER_PASSWORD = 'YourSecurePassword'
```

## MySQL Configuration (optional)

Demo mode is enough for development and presentations. Use MySQL only if your group needs a real database server.

### 1. Create the database

Run `database/setup.sql` in MySQL Workbench.

Flyway then creates and upgrades the tables automatically from the migrations in `backend/src/main/resources/db/migration/` (plus one Java migration in `com.restorant.migration` that carries existing bookings, payments and phone numbers forward). Existing data is migrated, not reset. Do not manually run the older `database/phase-2-schema.sql` draft.

### 2. Create database accounts

Create two MySQL accounts:

- Application account: SELECT, INSERT, UPDATE, DELETE on `resto_rant.*`.
- Migration account: permission to create and change tables.

Avoid using the MySQL root account for the app.

### 3. Set environment variables (PowerShell)

```powershell
$env:DB_USERNAME = 'resto_rant_app'
$env:DB_PASSWORD = 'your_database_password'

$env:DB_MIGRATION_USERNAME = 'resto_rant_migrator'
$env:DB_MIGRATION_PASSWORD = 'your_migration_password'

$env:DB_URL = 'jdbc:mysql://localhost:3306/resto_rant?sslMode=REQUIRED&connectionTimeZone=UTC'

$env:SEED_ADMIN_EMAIL = 'admin@example.com'
$env:SEED_ADMIN_PASSWORD = 'your_secure_admin_password'
```

### 4. Start the application

```powershell
.\run-backend.ps1 mysql-test
.\run-backend.ps1 run
```

Restaurant tables can then be added through the admin dashboard.

**Important:**
- Never commit database credentials or passwords.
- Use HTTPS for deployed environments and set `COOKIE_SECURE=true`.
- Use `sslMode=VERIFY_IDENTITY` with a trusted certificate for production MySQL connections.
- Demo mode is for local use, not production.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | Application port |
| `SERVER_ADDRESS` | `127.0.0.1` | Server bind address |
| `RESTAURANT_ZONE` | `Asia/Manila` | Restaurant timezone |
| `RESTAURANT_OPENING` | `11:00` | Opening time |
| `RESTAURANT_CLOSING` | `22:00` | Closing time |
| `RESERVATION_DURATION` | `120` | Reservation length in minutes |
| `SEED_ADMIN_EMAIL` | `admin@restorant.local` | Initial administrator email |
| `SEED_ADMIN_PASSWORD` | none | Initial administrator password (min. 10 characters) |
| `SEED_CUSTOMER_PASSWORD` | none | Demo customer password |
| `GCASH_NUMBER` | `09778021234` | GCash number customers send payments to |
| `GCASH_ACCOUNT_NAME` | empty | Account name shown next to the number (recommended, so customers can check the recipient) |
| `PAYMENT_PROVIDER` | `manual` | Only `manual` exists. Any other value stops the app at startup |

Slot spacing, maximum party size, and cancellation cutoff are in `backend/src/main/resources/application.yml`.

## Reservation Rules

- Reservations must fall within restaurant operating hours.
- A reservation holds one or more tables whose own seats cover the party: 100 guests need tables adding up to 100 seats.
- Automatic assignment picks the smallest free combination; the browser shows and submits exactly those tables.
- Guests choose how long they will stay: 1, 1½, 2, 2½, or 3 hours (2 hours if none is chosen). A start time is offered and accepted only if the whole stay ends by closing time and every assigned table is free for the whole stay; this is rechecked inside the booking lock.
- Reservations can be made up to 365 days ahead.
- Phone numbers are Philippine mobile numbers. Forms show a fixed `+63` prefix and take the 10 digits after it (starting with 9). The server also accepts `09XXXXXXXXX`, `639XXXXXXXXX` and `+639XXXXXXXXX`, and stores every number as `+639XXXXXXXXX`.
- Back-to-back reservations on the same table are allowed.
- Pending and confirmed reservations occupy their assigned tables.
- Customers can cancel a reservation, or some of its tables, up to two hours before it starts. Tables cancelled before that cutoff are refundable in full; staff can still cancel later, but those tables are recorded as not refundable.
- When tables are cancelled, the remaining tables must still seat the guests, or the guest count must be reduced in the same request. At least one table must remain.
- A reservation can be confirmed only after its payment is verified.
- Reservations can only be completed after their scheduled end time.
- Cancelled and completed reservations cannot be reopened.
- Tables with active future reservations cannot be disabled or reduced in capacity.
- Deleting a table only disables it, so reservation history is kept.

### Time and timezone

Asia/Manila (`RESTAURANT_ZONE`) is the only timezone that matters. "Today", the booking window, opening hours and availability are computed on the server from its clock in that zone; the browser takes them from `/api/info` and never uses the device clock or timezone to decide a date or time. Pages state the timezone next to times.

Reservation times are stored as the UTC instant of the restaurant's wall-clock time, so changing the timezone setting of the computer running the app no longer moves existing bookings. No internet time service is used: the operating system's UTC clock does not depend on the timezone setting. Reservations saved by an earlier version are read correctly as long as the computer was set to Philippine time when they were saved.

## Payments and refunds

### What is implemented: manual GCash transfer

No payment provider is connected. This project has no merchant account or API credentials, and a personal GCash number cannot receive API-confirmed payments, so the app never claims on its own that money arrived.

1. The customer reserves. The server prices each table from the package list and stores that snapshot. Prices sent by a browser are ignored; a mismatching displayed total is rejected.
2. The customer sends the total in their own GCash app to the configured number and submits the reference number, optionally with a receipt image (PNG, JPEG or WebP, up to 3 MB). The payment is **PENDING**.
3. An administrator opens **Dashboard → Payments**, checks the transaction history of the receiving GCash account, ticks the attestation, and marks the payment **PAID**, or rejects it with a note so the customer can resubmit.
4. Only then can the reservation be confirmed.

The app never asks for or stores a GCash MPIN or OTP. A customer-entered reference number is treated as a claim, not proof, and the same reference cannot be pending or paid on two reservations.

An unpaid reservation keeps its tables until staff cancel it; there is no automatic expiry.

### Refunds

Refunds are also manual. Cancelling tables before the cutoff makes their package price "owed" (only against verified payments). Under **Dashboard → Refunds** an administrator records a refund (amount and reason), sends it by hand in GCash, then records the refund's reference number. The app prevents more than one open refund per reservation, a refund reference being used twice, and refunds exceeding the verified payment minus earlier refunds. Every step is written to an append-only audit history shown in the reservation details.

Payments migrated from the earlier classroom simulator are labelled as simulated and cannot be refunded, because no money was received.

### Enabling automatic payment confirmation later

This needs things outside the code:

1. A registered business and a merchant account with a provider that offers GCash, for example PayMongo, Xendit or Maya Business, or GCash's own merchant programme. Check the provider's current requirements and fees.
2. The provider's secret key and webhook signing secret, supplied as environment variables. Never commit them.
3. A public HTTPS address for the provider's webhook.

Then, in code: add a provider implementation behind `PaymentService` that creates a checkout for the reservation's amount due and redirects the customer; add a webhook endpoint that verifies the provider's signature and marks the payment PAID only from that verified event (never from the browser's return URL); call the provider's refund API from the refund flow and keep the manual flow as a fallback; and allow the new value in `PaymentSettings`, which currently rejects anything except `manual`.

## Backend Architecture and OOP Implementation

The backend uses a layered architecture so each part has one job.

| Layer | Responsibility |
|---|---|
| Entity | Domain models and encapsulated application state |
| Repository | Database access and persistence |
| Service | Business logic and reservation processing |
| Controller | HTTP requests and API endpoints |
| DTO | Validated request objects and safe response structures |
| Security | Authentication, authorization, and session management |
| Config | Application configuration and initialization |

### Object-Oriented Programming Concepts

- **Encapsulation:** Domain entities control access to their internal state.
- **Inheritance:** An abstract `User` entity is extended by `Customer` and `Admin`.
- **Polymorphism:** Role-specific behavior is implemented through overridden methods.
- **Abstraction:** Service contracts separate business operations from their implementations.
- **Interfaces:** Repository and service interfaces define contracts between layers.

Relevant Java source files contain comments explaining the OOP techniques used.

### Reservation Concurrency

Reservation processing uses database transactions and locking so two people can't book the same table for overlapping times.

A single database lock row is acquired with `SELECT ... FOR UPDATE`. Availability is checked again after getting the lock, so simultaneous requests are handled safely. Entity versioning also catches stale updates.

## REST API Documentation

All endpoints are served under `/api`.

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/auth/csrf` | Retrieve CSRF token |
| POST | `/api/auth/register` | Register customer |
| POST | `/api/auth/login` | Authenticate user |
| GET | `/api/auth/me` | Get current account |
| POST | `/api/auth/logout` | End session |

### Reservations

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/info` | Get restaurant information |
| GET | `/api/availability?date=YYYY-MM-DD&guests=2` | Check available seating |
| GET | `/api/availability/tables?date=YYYY-MM-DD&time=12:00&guests=2&duration=120` | Tables with price, availability and the `suggested` automatic assignment |
| GET | `/api/availability/dates?date=YYYY-MM-DD&guests=2` | Suggest other dates |
| GET | `/api/pricing` | Package prices, GCash payment instructions, cancellation terms |
| POST | `/api/reservations` | Create a reservation (unpaid; priced by the server) |
| POST | `/api/reservations/{id}/payments` | Submit a GCash reference and optional `proof` image (multipart); stays pending |
| POST | `/api/reservations/{id}/cancel-tables` | Cancel selected tables, optionally reducing `guestCount` |
| GET | `/api/reservations/my` | Get customer reservations |
| GET | `/api/reservations/{id}` | Get reservation details |
| PUT | `/api/reservations/{id}/cancel` | Cancel reservation |

### Administration

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/admin/overview` | Dashboard statistics |
| GET | `/api/admin/reservations` | Search and filter reservations |
| PUT | `/api/admin/reservations/{id}/status` | Update reservation status |
| GET | `/api/admin/tables` | List restaurant tables |
| POST | `/api/admin/tables` | Add restaurant table |
| PUT | `/api/admin/tables/{id}` | Update restaurant table |
| DELETE | `/api/admin/tables/{id}` | Disable restaurant table |
| GET | `/api/admin/customers?q=...` | Search customers |
| GET | `/api/admin/customers/{id}/reservations` | View customer history |
| POST | `/api/admin/accounts` | Register another administrator |
| POST / PUT / DELETE | `/api/admin/customers` and `/{id}` | Create, edit, or disable customers |
| POST | `/api/admin/customers/{id}/reservations` | Book for a customer |
| PUT / DELETE | `/api/admin/reservations/{id}` | Edit or cancel a reservation |
| POST | `/api/admin/layout` | Apply the 25-table layout when safe |
| PUT | `/api/admin/pricing/{capacity}` | Set the package price for a table size |
| GET | `/api/admin/payments` | List payments (`?status=PENDING`) |
| GET | `/api/admin/payments/{id}/proof` | View the receipt image |
| PUT | `/api/admin/payments/{id}/review` | Mark paid (requires `verifiedAgainstAccount`) or reject |
| GET | `/api/admin/refunds` and `/due` | Refund records, and reservations owed a refund |
| POST | `/api/admin/reservations/{id}/refunds` | Record a refund to send |
| PUT | `/api/admin/refunds/{id}/complete` and `/void` | Record the sent refund's reference, or void it |
| POST | `/api/admin/reservations/{id}/cancel-tables` | Cancel selected tables as staff |
| GET | `/api/admin/reservations/{id}/audit` | Payment, cancellation and refund history |
| GET / PUT | `/api/admin/inquiries` and `/{id}/resolve` | Read and resolve inquiries |

### API Security

The app uses session login and CSRF protection. For POST, PUT, and DELETE requests (e.g. in Postman):

1. Call `/api/auth/csrf`.
2. Keep the returned session cookie (Postman does this automatically).
3. Send the returned CSRF token in the request header it names.
4. Get a new token after logging in or out.

### HTTP Response Codes

| Status | Meaning |
|---|---|
| 200 | Request successful |
| 201 | Resource created |
| 204 | Request successful with no response body |
| 400 | Invalid request or validation error |
| 401 | Not logged in |
| 403 | Not allowed, or CSRF token missing/expired |
| 404 | Resource not found or inaccessible |
| 409 | Reservation or state conflict |

## Testing

For class presentations, use the [OOP presentation guide](docs/OOP_PRESENTATION_GUIDE.md) and the [backend speaking script](docs/BACKEND_PRESENTATION_SCRIPT.md). The guide links the Java classes to encapsulation, inheritance, polymorphism, and abstraction.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-backend.ps1 verify
```

The automated tests cover:

- Customer registration and password validation.
- Login, session handling, and logout.
- Role-based authorization and CSRF protection.
- Reservation ownership and access control.
- Availability checking and table allocation.
- Overlapping and back-to-back reservations.
- Cancellation and reservation status rules.
- Table capacity and management constraints.
- Simultaneous reservation requests.
- Flyway migrations and Hibernate schema validation.
- Multi-table reservations, the 100-person limit, alternative dates, and layout replacement safeguards.
- Timezone independence: stored times and "today" under different machine timezones.
- Capacity: 100 guests across 25 tables, rejection of too few seats, concurrent large parties.
- Overlap boundaries and lengths of stay, including a one-hour stay in a 30-minute gap.
- Package pricing, price snapshots, rejection of client-supplied prices, retry protection.
- Payment states (pending, paid, rejected, resubmitted), receipt image validation.
- Partial cancellation, the cancellation cutoff, refund limits and the audit history.
- Phone number normalization in registration, reservations, inquiries and admin forms.
- Migration of deposit-era data into price lines, payments, refunds and +63 numbers.
- Staff registration, customer access, inquiry storage, and preservation of existing bookings during upgrades.

The tests use H2 in MySQL compatibility mode, which is not the same as testing on a real MySQL server. For that, use `.\run-backend.ps1 mysql-test`.

### Manual Testing Checklist

1. Register a new customer account.
2. Log in as the customer.
3. Pick a date and see available times.
4. Click **Assign tables for me** (the assigned tables turn solid green), check the itemized total, reserve, and submit a GCash reference number.
5. View it under **My reservations**.
6. Cancel a reservation.
7. Log in as the admin.
8. Under **Payments**, verify the payment, then confirm the reservation. Cancel one table of a multi-table booking and record its refund under **Refunds**.
9. Add or edit a table.
10. Search for a customer and view their history.

## Build a JAR

```powershell
cd backend
.\mvnw.cmd clean package
```

The app is created at `backend/target/resto-rant-0.0.1-SNAPSHOT.jar` and can be started with:

```bash
java -jar backend/target/resto-rant-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

## Deploy

See [the Cloudflare deployment guide](docs/DEPLOYMENT.md) for Cloudflare Containers, managed MySQL, secrets, and a custom domain.

## License

No license has been chosen yet.

---

**Resto Rant**: a restaurant reservation system built with Java and Spring Boot.
