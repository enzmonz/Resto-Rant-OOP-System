# Resto Rant backend cheatsheet

## Start and test

From the project root in PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-backend.ps1 demo
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-backend.ps1 verify
```

Java 21 is required. The included Maven wrapper downloads Maven when needed. Open http://127.0.0.1:8080. Demo data persists in `backend/.local`; do not delete it to apply updates. Flyway applies migrations automatically. MySQL uses the existing `run` command and DB environment settings documented in README.

## Where to find the logic

| File / class | Responsibility |
|---|---|
| `controller/ReservationController` | Customer booking, history, availability |
| `controller/UpdatesController` | Demo checkout, assistance/table availability, deposit settings, staff CRUD, inquiries |
| `service/BookingService` | Validate operating hours, allocate tables, enforce capacity, payment and booking transitions |
| `service/SeatingPlanner` | Smallest suitable table combination and peak simultaneous occupancy |
| `repository/BookingMutex` | Locks a database singleton row so concurrent mutations cannot double-book |
| `service/AdminService` | Table administration and safe 25-table layout replacement |
| `service/AccountService` | Registration, password hashing, customer edits and disabling |
| `config/DepositPolicy` | Default ₱500/person and persisted administrator overrides |
| `entity/Reservation` | Encapsulated booking, table assignments, deposit and refund state |
| `security/SecurityConfig` | Session authentication, role checks, CSRF and browser security headers |
| `db/migration/V1…V3` | Base schema, multi-table/payment/inquiry upgrade, deposit settings |

All Java paths above are under `backend/src/main/java/com/restorant/`; migrations are under `backend/src/main/resources/`.

## Booking and demo payment flow

1. Public `GET /api/availability?date=YYYY-MM-DD&guests=12` returns seating times and remaining seats.
2. `GET /api/availability/tables?date=...&time=12:00&guests=12` returns table choices. `GET /api/availability/dates?date=...&guests=12` suggests up to three dates within the next 30 days.
3. Customer chooses tables or automatic assignment, assistance, dietary notes, and accepts the deposit. Checkout shows the total.
4. `POST /api/reservations/demo-checkout` receives `{booking, checkoutId, expectedAmount}`. The booking contains `date`, `time`, `guestCount`, `contactNumber`, `specialRequests`, `tableIds`, `guestNeeds`, and `depositAccepted`.
5. Under one database transaction and allocation lock, the server checks the price and capacity, creates the reservation, and records a **simulated** verified payment. A repeated checkout UUID returns the same customer's booking. Reusing another customer's UUID is rejected.
6. The deposit is `VERIFIED`; the reservation stays `PENDING` until staff confirms. Declining or closing the demo checkout makes no booking. A failed allocation or changed price records no payment.
7. Staff-created unpaid reservations can be paid by their owner using `POST /api/reservations/{id}/demo-payment` from My reservations.

This is a classroom simulator, **not a GCash integration**. It collects no payment credentials and makes no external payment calls. A live system would need an authenticated provider checkout/webhook integration replacing these demo endpoints.

## Administrator operations

All `/api/admin/**` routes require an authenticated administrator; all mutations require CSRF.

| Endpoint | Action |
|---|---|
| `POST /admin/accounts` | Register another administrator |
| `POST /admin/customers` | Create customer |
| `PUT /admin/customers/{id}` | Edit name, phone and enabled state |
| `DELETE /admin/customers/{id}` | Disable customer; preserve history |
| `POST /admin/customers/{id}/reservations` | Book on behalf of customer |
| `PUT /admin/reservations/{id}` | Edit date, time, tables, contact and requests |
| `DELETE /admin/reservations/{id}` | Cancel booking; preserve history |
| `PUT /admin/reservations/{id}/status` | Confirm, cancel or complete |
| `POST /admin/layout` | Apply 8×2 + 9×4 + 8×6 layout if no upcoming active bookings |
| `PUT /admin/deposit-policy` | Save amount, method description and per-person setting |
| `GET /admin/inquiries` | Read the latest 200 guest inquiries |
| `PUT /admin/inquiries/{id}/resolve` | Mark inquiry resolved |

Prefix these paths with `/api`. Guest-count edits require a replacement booking so an existing deposit is not silently altered. Existing V1 bookings are preserved with `NOT_REQUIRED` deposit status; new bookings require deposits. Cancelled verified deposits become `REFUND_DUE`; staff can record a refund reference. These records do not move money. Manual payment review endpoints are retained for staff-entered records, but customer demo checkout verifies automatically.

## OOP explanation for class

- **Encapsulation:** private fields and validated transitions in Reservation; callers cannot directly change its table collection or statuses.
- **Inheritance:** Customer and Admin extend User.
- **Polymorphism:** account capabilities come from the concrete User subtype; BookingService implements ReservationService.
- **Abstraction:** controllers use services; repositories hide persistence; BookingMutex hides the locking query.

## Debugging

Fetch `/api/auth/csrf`, retain the session cookie, and send the returned token/header name on POST, PUT and DELETE. Fetch a fresh token after login/logout. 400 means invalid input, 401 login required, 403 permissions/CSRF, 404 unavailable or another customer's record, and 409 a seating/payment/state conflict. Rebuild/restart after frontend edits because Maven packages the frontend into the JAR.
