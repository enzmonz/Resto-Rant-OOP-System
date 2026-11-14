# Resto Rant: OOP presentation guide

This guide explains the **Java backend that is actually in this repository**. Open the linked source files while presenting. The companion [speaking script](BACKEND_PRESENTATION_SCRIPT.md) gives you a ready-to-practice explanation of the whole backend.

## One-sentence introduction

“Resto Rant is a restaurant reservation system where Java objects represent accounts, tables, reservations, and their rules, while Spring Boot connects those objects to web requests and a database.”

## A simple map of the backend

```text
Browser (frontend/app.js and frontend/experience.js)
    ↓ JSON requests
Spring Security → Controllers → Services → Repositories → Database
                         ↓          ↓
                    DTO validation  Domain entities
```

The browser sends a request such as `POST /api/reservations/demo-checkout`. [SecurityConfig.java](../backend/src/main/java/com/restorant/security/SecurityConfig.java) checks who may call it. [UpdatesController.java](../backend/src/main/java/com/restorant/controller/UpdatesController.java) receives and validates the request, then calls [BookingService.java](../backend/src/main/java/com/restorant/service/BookingService.java). The service applies booking rules, asks repositories for data, and changes [Reservation.java](../backend/src/main/java/com/restorant/entity/Reservation.java). Spring Data JPA saves that object to the database. The controller returns a response object from [Api.java](../backend/src/main/java/com/restorant/dto/Api.java).

**Important distinction:** The controller does not decide whether a table is free; the service does. The database does not decide whether a reservation may be confirmed; the `Reservation` object does. These rules belong in the parts of the system that own them.

## The four OOP techniques

### 1. Encapsulation: objects protect their own data

**Definition:** Fields are kept private, and changes go through methods that enforce rules.

**Show:** [Reservation.java](../backend/src/main/java/com/restorant/entity/Reservation.java). Its fields such as `status`, `assignedTables`, `guestCount`, and `depositStatus` are private. There is no general `setStatus()` that can turn a cancelled booking back into a confirmed one. Instead, methods such as `assign(...)`, `confirm()`, `cancel()`, `submitPayment(...)`, and `reviewPayment(...)` check whether the requested change is legal.

```java
public void confirm() {
    if (!depositStatus.equals("VERIFIED") && !depositStatus.equals("NOT_REQUIRED"))
        throw new IllegalStateException("Verify the reservation deposit before confirming");
    if (status != Status.PENDING)
        throw new IllegalStateException("Only pending reservations can be confirmed");
    status = Status.CONFIRMED;
}
```

**What to say:** “A caller cannot directly set a booking to confirmed. It must ask the reservation to confirm itself, and the reservation checks that its deposit is verified and it is still pending.” `NOT_REQUIRED` is retained for older bookings created before the deposit update; new bookings require a deposit.

Another example is [User.java](../backend/src/main/java/com/restorant/entity/User.java): `email` and `passwordHash` are private. The constructor validates the email and requires a BCrypt hash. `updateContact(...)` validates a new name and phone number before changing them. [RestaurantTable.java](../backend/src/main/java/com/restorant/entity/RestaurantTable.java) also validates its number and capacity.

**Why it matters here:** The same reservation can be changed from a customer page, an admin page, or an API client. Keeping the status rules in the object means each path uses the same protection.

### 2. Inheritance: related account types share common data

**Definition:** A subclass reuses fields and behavior from a parent class and adds its own behavior.

**Show:** [User.java](../backend/src/main/java/com/restorant/entity/User.java), [Customer.java](../backend/src/main/java/com/restorant/entity/Customer.java), and [Admin.java](../backend/src/main/java/com/restorant/entity/Admin.java).

```java
public abstract class User {
    private String fullName;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    public abstract String getRole();
    public abstract boolean canManageReservations();
}

public class Customer extends User {
    @Override public String getRole() { return "CUSTOMER"; }
    @Override public boolean canManageReservations() { return false; }
}

public class Admin extends User {
    @Override public String getRole() { return "ADMIN"; }
    @Override public boolean canManageReservations() { return true; }
}
```

The excerpts omit annotations and constructors; use the source files for the complete code. `User` holds identity and contact validation once. `Customer` and `Admin` inherit those rules. JPA's `SINGLE_TABLE` inheritance stores both account types in `users`, using the `role` discriminator.

**What to say:** “Customers and admins both have names, emails, phone numbers, and password hashes. I put those shared fields in `User` instead of copying them into two separate classes.”

### 3. Polymorphism: one type, different behavior

**Definition:** Code can work with a parent type while the actual object supplies the method implementation.

**Show:** [Api.java](../backend/src/main/java/com/restorant/dto/Api.java), specifically `Account.of(User u)`. It calls `u.getRole()` and `u.canManageReservations()`. If `u` is a `Customer`, the methods return `CUSTOMER` and `false`. If it is an `Admin`, they return `ADMIN` and `true`. The caller does not need separate account conversion methods for each class.

```java
public static Account of(User u) {
    return new Account(u.getId(), u.getFullName(), u.getEmail(),
        u.getPhoneNumber(), u.getRole(), u.canManageReservations(), u.isEnabled());
}
```

Another form of polymorphism is [BookingService.java](../backend/src/main/java/com/restorant/service/BookingService.java) implementing [ReservationService.java](../backend/src/main/java/com/restorant/service/ReservationService.java). [ReservationController.java](../backend/src/main/java/com/restorant/controller/ReservationController.java) holds a `ReservationService` reference, but at runtime Spring supplies `BookingService`.

**Be precise:** The admin permissions are also enforced by Spring Security. `canManageReservations()` helps represent capabilities in account data; it is not the sole security check.

### 4. Abstraction: expose what a component does, hide how it does it

**Definition:** Other code depends on a clear operation or contract instead of its internal implementation.

**Show:** [ReservationService.java](../backend/src/main/java/com/restorant/service/ReservationService.java) declares operations such as `create`, `availability`, `mine`, `cancel`, and `changeStatus`. The controller can call those operations without knowing how tables are selected or how the database is locked.

[BookingMutex.java](../backend/src/main/java/com/restorant/repository/BookingMutex.java) is another small example. Its public `acquire()` method hides the `SELECT ... FOR UPDATE` database query from booking and table services. [SeatingPlanner.java](../backend/src/main/java/com/restorant/service/SeatingPlanner.java) exposes `bestFit(...)` and `remainingCapacity(...)`, leaving the combination and occupancy calculations inside the planner.

**What to say:** “The controller asks the booking service to create a reservation. It does not contain SQL or the seating algorithm. This makes the controller shorter and lets the booking rules be tested separately.”

## One reservation, traced end to end

Use **12 guests at noon** as your class example:

1. A customer chooses a future date, 12:00 PM, and either two six-seat tables or automatic assignment. The frontend gathers contact details, assistance needs, and requests.
2. [Api.Booking](../backend/src/main/java/com/restorant/dto/Api.java) carries the validated request into the backend. Bean Validation catches missing or invalid fields.
3. [BookingService.validate(...)](../backend/src/main/java/com/restorant/service/BookingService.java) checks future time, 11 AM–10 PM opening hours, two-hour duration, 30-minute time slots, and the 1–100 guest rule.
4. `BookingMutex.acquire()` serializes booking and table changes. The service rechecks availability inside the database transaction so two requests cannot take the same tables at the same time.
5. [SeatingPlanner.bestFit(...)](../backend/src/main/java/com/restorant/service/SeatingPlanner.java) finds the smallest sufficient group when tables were not chosen manually. For 12 guests, two six-seat tables are a possible exact fit. The service checks every selected table against overlapping two-hour bookings and checks the 100-person occupancy limit.
6. A `Reservation` object receives the selected tables and the required deposit. At the default **₱500 per person**, 12 guests have a **₱6,000 demo deposit**.
7. The normal website calls `POST /api/reservations/demo-checkout`. In one transaction, the backend creates the reservation and records a **simulated** GCash payment as verified. It does not connect to GCash or move money. A retry with the same checkout ID returns the same customer's reservation instead of creating a duplicate.
8. Staff can confirm the `PENDING` reservation once the deposit is `VERIFIED`. A cancellation changes a verified deposit to `REFUND_DUE` for staff review; recording a refund is only a record in this demo.

**Useful distinction for questions:** `PENDING` or `CONFIRMED` is the **reservation status**. `REQUIRED`, `VERIFIED`, or `REFUND_DUE` is the **deposit status**. They describe different things.

## Other backend pieces you can point to

| Component | What it does | Source |
|---|---|---|
| Authentication and roles | Session login, BCrypt passwords, customer/admin route rules, CSRF protection | [SecurityConfig.java](../backend/src/main/java/com/restorant/security/SecurityConfig.java), [AuthController.java](../backend/src/main/java/com/restorant/controller/AuthController.java) |
| Customer accounts | Register customers/admins; edit or disable customers while retaining history | [AccountService.java](../backend/src/main/java/com/restorant/service/AccountService.java) |
| Table administration | Add/edit/disable tables; safely apply 8×2 + 9×4 + 8×6 layout | [AdminService.java](../backend/src/main/java/com/restorant/service/AdminService.java) |
| Contact inquiries | Save guest form messages; let admins view and resolve them | [InquiryService.java](../backend/src/main/java/com/restorant/service/InquiryService.java) |
| Database access | `JpaRepository` interfaces for users, tables, reservations, inquiries and deposit settings | [repository folder](../backend/src/main/java/com/restorant/repository/) |
| Database upgrades | Flyway creates the schema and adds multi-table bookings, deposits, and inquiries while retaining old rows | [migrations folder](../backend/src/main/resources/db/migration/) |
| Input and output | `Api` records define requests and responses; `BookingView` exposes safe booking details | [Api.java](../backend/src/main/java/com/restorant/dto/Api.java) |
| Errors | Invalid input, unavailable tables, and state conflicts return clear HTTP responses | [ErrorHandler.java](../backend/src/main/java/com/restorant/exception/ErrorHandler.java) |

## Questions your instructor may ask

**“Why not put all booking logic in the controller?”** The controller's job is HTTP. `BookingService` owns booking rules, `SeatingPlanner` owns seat selection, and `Reservation` owns its valid state changes. Keeping each responsibility together makes the rules easier to reuse and test.

**“How do you stop double bookings?”** Both booking and table mutations acquire the same database lock. After obtaining it, the service checks all tables assigned to overlapping active reservations before saving. Adjacent bookings are allowed: an 11 AM–1 PM booking does not overlap one starting at 1 PM.

**“Why use an interface?”** `ReservationController` depends on the `ReservationService` contract. The concrete `BookingService` can change its database or allocation details without changing that contract.

**“Is the GCash integration real?”** No. It is a clearly labeled classroom simulation. The app stores a demo payment reference and verified state, but sends no payment to GCash and collects no MPIN or OTP.

**“Does deleting a customer erase reservations?”** The admin action disables the account. Historical bookings remain. Cancelling a reservation likewise keeps its history instead of deleting the row.

**“What happens to old bookings after an upgrade?”** Flyway migrates the schema and preserves their table assignments. Old bookings have `NOT_REQUIRED` as a compatibility status. New bookings require a deposit.

## Quick live demo plan

1. Open the home page and show Menu, About Us, and Contact Us.
2. Log in as a demo customer. Choose a date before showing time slots.
3. Enter 12 guests; select two six-seat tables or automatic assignment. Show the ₱6,000 total and the “GCash demo” label.
4. Simulate a declined payment first to show that it makes no booking; then simulate success.
5. Open My reservations and show `PENDING` reservation status and `VERIFIED` deposit status.
6. Log in as an admin. Show the reservation, customer history, deposit amount, inquiries, and the 25-table layout control.

Use test accounts and test data for the class demo. The [backend cheatsheet](BACKEND_CHEATSHEET.md) has the startup commands and API quick reference.
