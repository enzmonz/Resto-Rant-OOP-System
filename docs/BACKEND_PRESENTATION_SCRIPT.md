# Backend presentation script

**Target length:** about 7–9 minutes at a normal speaking pace. Read the quoted parts aloud; bracketed notes are cues for what to show. You can shorten sections if your class time is limited. The matching code examples are in [OOP_PRESENTATION_GUIDE.md](OOP_PRESENTATION_GUIDE.md).

## 1. Opening — about 45 seconds

[Show the Resto Rant home page.]

“Good day. Our project is Resto Rant, a restaurant reservation system built with Java 21 and Spring Boot. A customer can check availability, choose one or more tables, send assistance requests, and reserve a two-hour visit. Staff can manage customers, tables, reservations, and inquiries.

“I’ll focus on the backend: how a request travels through the code, how we use object-oriented programming, and how we prevent overlapping reservations.”

## 2. Backend structure — about 1 minute

[Open `backend/src/main/java/com/restorant/` and point to `controller`, `service`, `entity`, `repository`, and `security`.]

“We divided the backend into layers. The **controller** receives a web request and returns JSON. The **service** applies business rules, like opening hours, seating capacity, and payment state. The **entities** represent things in our restaurant, such as users, tables, and reservations. The **repositories** read and save those entities. Spring Security checks login and permissions before protected requests reach the controller.

“For example, the browser calls the demo checkout endpoint. `UpdatesController` passes the request to `BookingService`. The service checks availability, creates a `Reservation`, and saves it using `ReservationRepository`. We return a `BookingView` to the browser. This separation keeps web code out of the table-allocation rules.”

## 3. OOP technique: encapsulation — about 1 minute

[Open `entity/Reservation.java` and show `confirm()` and `assign(...)`.]

“Our first OOP technique is **encapsulation**. A reservation's fields are private. Other classes cannot simply set its status or replace its tables without checks. Instead, they call methods such as `assign`, `confirm`, and `cancel`.

“For instance, `confirm()` first checks that the deposit is verified and that the reservation is pending. Only then does it change the status to confirmed. The `assign()` method checks that selected tables are distinct, enabled, and have enough seats. This protects the reservation regardless of whether the action comes from the customer website or the admin panel.”

## 4. OOP techniques: inheritance and polymorphism — about 1 minute

[Open `entity/User.java`, then `Customer.java` and `Admin.java`.]

“Our second technique is **inheritance**. `User` is an abstract parent class with fields and validation shared by all accounts: name, email, phone number, password hash, and enabled state. `Customer` and `Admin` extend it. We avoid repeating the same identity code in both account classes.

“Our third technique is **polymorphism**. Both subclasses implement `getRole()` and `canManageReservations()` differently. When `Api.Account.of(User u)` receives a `User`, it calls those methods on the actual object. A customer supplies `CUSTOMER` and `false`; an admin supplies `ADMIN` and `true`. Spring Security also enforces who may use the admin routes, so this method is not our only permission check.”

## 5. OOP technique: abstraction — about 45 seconds

[Open `service/ReservationService.java`, then `service/BookingService.java`.]

“Our fourth technique is **abstraction**. `ReservationService` is an interface that lists booking operations such as create, availability, view, cancel, and change status. `BookingService` implements those operations. `ReservationController` depends on the interface, so it does not need to know the seating algorithm or the database queries.

“We also use `BookingMutex.acquire()` as a small abstraction: the booking service asks for a lock, while the SQL needed to obtain it stays in the repository class.”

## 6. Reservation flow and concurrency — about 1½ minutes

[Show the booking page. Set 12 guests and a future date.]

“Let’s follow a reservation for 12 guests. The frontend first requests available time slots. The backend checks that the date is in the future, the time fits our 11 AM to 10 PM hours, the visit lasts two hours, and the party is between 1 and 100 guests.

“The customer may pick tables. Two six-seat tables can fit 12 guests. If they choose automatic assignment, `SeatingPlanner.bestFit()` looks for a suitable combination with the least spare seating. The backend also counts guests in overlapping reservations so no more than 100 are present at once.

“When checkout happens, we obtain a database lock and **recheck** the chosen tables. This matters because availability may have changed since the customer opened the page. We compare every selected table with active reservations whose two-hour time window overlaps. Only if the tables are still free do we save the new reservation. That prevents two simultaneous requests from booking the same table.”

## 7. Deposit and reservation states — about 1 minute

[Show the GCash demo checkout total.]

“The required deposit is ₱500 per person by default, so 12 guests see a ₱6,000 total. This checkout is explicitly a **GCash demo**. It does not connect to GCash or move money. A simulated successful payment and the reservation are recorded in one database transaction. If booking fails or the amount changed, the transaction does not leave a paid reservation behind. A repeated checkout request uses the same checkout ID and returns the original booking rather than making a second one.

“The reservation then has two separate states. Its booking status is `PENDING` until staff confirms it. Its deposit status is already `VERIFIED` because the demo payment succeeded. A verified deposit is required before confirming a new reservation. If a verified booking is cancelled, the deposit is marked `REFUND_DUE` for staff review; the demo only records this status.”

## 8. Authentication, admin, and database — about 1 minute

[Show the admin dashboard, then the migration files.]

“Passwords are stored as BCrypt hashes. Login uses a session cookie. Spring Security limits reservation actions to customers and admin endpoints to administrators. It also requires a CSRF token for changes sent from the browser.

“Admins can manage tables, customer accounts, reservations, inquiries, and the deposit amount. New demo databases start with 25 tables: eight for two people, nine for four, and eight for six, giving 100 seats. On an existing database, the layout action refuses to replace tables while upcoming active reservations still use them.

“Flyway migration files create and update the database schema. The later migrations add table combinations, deposits, and inquiries while retaining older booking records. That means updates do not require us to erase existing reservations.”

## 9. Closing — about 30 seconds

“In summary, this backend uses OOP for more than class names. Encapsulation protects valid booking states, inheritance shares account rules, polymorphism lets customer and admin objects respond differently, and abstraction keeps controllers independent from booking internals. The database transaction and lock handle simultaneous reservations, while tests check those rules. Thank you.”

## Short answers for follow-up questions

**If asked whether the payment is real:** “No. It is a classroom GCash simulation with automatic local verification. There is no external payment provider or real transfer.”

**If asked why staff still confirms a booking:** “Demo payment verification confirms only the deposit state. Staff confirmation changes the separate reservation state from pending to confirmed.”

**If asked how duplicate bookings are prevented:** “The allocation lock serializes changes, then we check overlapping bookings for every assigned table inside the transaction.”

**If asked how 12 people fit:** “The booking stores a collection of tables. It can use two six-seat tables; automatic assignment finds a suitable combination.”

**If asked where the OOP methods are:** “Start with `User`, `Customer`, `Admin`, `ReservationService`, `BookingService`, and `Reservation`. The guide links each source file.”
