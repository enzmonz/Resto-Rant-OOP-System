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

- **The first run takes a few minutes.** It downloads Maven and the libraries the project needs. Later runs are much faster.
- **A login box pops up** asking you to choose the **admin email and password** (password must be at least 10 characters). Remember them, because this is how you log in to the admin dashboard. On later runs, press **Cancel**: the admin account is already saved.
- When you see `Started RestoRantApplication`, the app is ready.

### 4. Open it

Go to **http://127.0.0.1:8080** in your browser.

- **Customer:** click **Log in → Create an account**, then **Book a table**.
- **Admin:** log in with the admin email and password you chose in step 3.

Reservations now include a **GCash demo deposit of ₱500 per person**. Choose a date, time, and tables, then complete the simulated checkout. Payment is automatically verified; no real money, GCash login, MPIN, or OTP is used. Administrators can change the amount under **Dashboard → Deposit policy**.

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
| Forgot the admin password | Stop the app, delete the `backend/.local` folder, and start again. **This deletes all your local accounts and bookings.** |
| "No tables are available" | Bookings last 2 hours and the restaurant closes at 10 PM, so late in the day there are no slots left today. Pick tomorrow's date. |
| My HTML/CSS/JS changes don't show | Stop the app (Ctrl + C) and start it again. The frontend is packaged when the app starts. Then hard-refresh the browser (Ctrl + F5). |
| First run fails while downloading | Check your internet connection and run the command again. |

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
- ₱500-per-person GCash demo checkout with automatic simulated verification.
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
- Review contact inquiries and set the per-person deposit amount.

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

New demo databases automatically get **25 tables: 8 × 2 seats, 9 × 4 seats, and 8 × 6 seats (100 total)**. Customers can combine tables, and the server prevents overlapping use of every selected table and more than 100 simultaneous guests.

Existing databases keep their tables and booking history. To adopt the new layout, use **Dashboard → Tables → Apply 25-table / 100-seat layout** after upcoming reservations are finished or otherwise resolved. The app will refuse to overwrite a layout used by upcoming reservations.

An optional sample customer (`guest@restorant.local`) with one reservation can be created by setting a password before starting:

```powershell
$env:SEED_CUSTOMER_PASSWORD = 'YourSecurePassword'
```

## MySQL Configuration (optional)

Demo mode is enough for development and presentations. Use MySQL only if your group needs a real database server.

### 1. Create the database

Run `database/setup.sql` in MySQL Workbench.

Flyway then creates the tables automatically from `backend/src/main/resources/db/migration/V1__initial_schema.sql`. Do not manually run the older `database/phase-2-schema.sql` draft.

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

Slot spacing, maximum party size, and cancellation cutoff are in `backend/src/main/resources/application.yml`.

## Reservation Rules

- Reservations must fall within restaurant operating hours.
- Each reservation is assigned the smallest available table that fits the party.
- Reservations use configurable seating durations.
- Back-to-back reservations on the same table are allowed.
- Pending and confirmed reservations occupy their assigned tables.
- Customers can cancel up to two hours before the reservation starts.
- Reservations can only be completed after their scheduled end time.
- Cancelled and completed reservations cannot be reopened.
- Tables with active future reservations cannot be disabled or reduced in capacity.
- Deleting a table only disables it, so reservation history is kept.

All reservation times follow the configured restaurant timezone. Creation timestamps are stored in UTC.

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
| GET | `/api/availability/tables?date=YYYY-MM-DD&time=12:00&guests=2` | Check table availability |
| GET | `/api/availability/dates?date=YYYY-MM-DD&guests=2` | Suggest other dates |
| GET | `/api/deposit-policy` | Get the current deposit |
| POST | `/api/reservations/demo-checkout` | Create reservation and verify simulated GCash payment |
| POST | `/api/reservations` | Legacy unpaid request; use demo checkout for the normal flow |
| POST | `/api/reservations/{id}/demo-payment` | Pay a staff-created booking in the demo |
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
| PUT | `/api/admin/deposit-policy` | Set the deposit amount |
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
- Per-person demo deposits, automatic payment verification, retry protection, and price-change rejection.
- Staff registration, customer access, inquiry storage, and preservation of existing bookings during upgrades.

The tests use H2 in MySQL compatibility mode, which is not the same as testing on a real MySQL server. For that, use `.\run-backend.ps1 mysql-test`.

### Manual Testing Checklist

1. Register a new customer account.
2. Log in as the customer.
3. Pick a date and see available times.
4. Select tables and complete the GCash demo checkout; check the total is ₱500 × guests.
5. View it under **My reservations**.
6. Cancel a reservation.
7. Log in as the admin.
8. Confirm a pending reservation whose demo deposit is verified.
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

## License

No license has been chosen yet.

---

**Resto Rant**: a restaurant reservation system built with Java and Spring Boot.
