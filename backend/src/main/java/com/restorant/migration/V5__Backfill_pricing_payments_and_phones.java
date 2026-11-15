package com.restorant.migration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Moves existing data into the structures added by V4 without deleting anything:
 * a price line for every table of every reservation, a payment row for every recorded payment reference,
 * a refund row for every recorded refund, the new payment status names, and +63 phone numbers.
 * Written in Java (plain JDBC) so the same statements run on MySQL and on the H2 demo database.
 * Registered as a Spring bean; Spring Boot hands every JavaMigration bean to Flyway.
 */
@Component
public class V5__Backfill_pricing_payments_and_phones extends BaseJavaMigration {
    private static final Pattern SEPARATORS = Pattern.compile("[\\s().-]");
    private static final Pattern MOBILE = Pattern.compile("(?:\\+?63|0)?(9\\d{9})");
    private record Booking(long id, String status, BigDecimal amount, String paymentState, String reference, String note, Timestamp createdAt) {}
    private record Seat(long tableId, String number, int capacity) {}

    @Override public void migrate(Context context) throws Exception {
        Connection db = context.getConnection();
        var bookings = new ArrayList<Booking>();
        try (var query = db.createStatement(); var rows = query.executeQuery(
                "SELECT id, status, deposit_amount, deposit_status, payment_reference, payment_note, created_at FROM reservations ORDER BY id")) {
            while (rows.next()) bookings.add(new Booking(rows.getLong(1), rows.getString(2), rows.getBigDecimal(3), rows.getString(4),
                rows.getString(5), rows.getString(6), rows.getTimestamp(7)));
        }
        var seats = new HashMap<Long, List<Seat>>();
        try (var query = db.createStatement(); var rows = query.executeQuery(
                "SELECT rt.reservation_id, t.id, t.table_number, t.capacity FROM reservation_tables rt JOIN restaurant_tables t ON t.id = rt.table_id ORDER BY rt.reservation_id, t.id")) {
            while (rows.next()) seats.computeIfAbsent(rows.getLong(1), key -> new ArrayList<>()).add(new Seat(rows.getLong(2), rows.getString(3), rows.getInt(4)));
        }
        try (var item = db.prepareStatement("INSERT INTO reservation_items (reservation_id, table_id, table_number, capacity, price, status, refundable_amount) VALUES (?,?,?,?,?,?,?)");
             var payment = db.prepareStatement("INSERT INTO payments (reservation_id, method, amount, status, reference, has_proof, submitted_at, submitted_by, reviewed_at, reviewed_by, review_note) VALUES (?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
             var refund = db.prepareStatement("INSERT INTO refunds (reservation_id, payment_id, amount, reason, payment_reference, refund_reference, status, created_at, created_by, completed_at, completed_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)");
             var state = db.prepareStatement("UPDATE reservations SET deposit_status = ? WHERE id = ?")) {
            for (var booking : bookings) {
                BigDecimal total = booking.amount() == null ? BigDecimal.ZERO : booking.amount();
                boolean cancelled = "CANCELLED".equals(booking.status());
                boolean refundOwed = "REFUND_DUE".equals(booking.paymentState()) || "REFUNDED".equals(booking.paymentState());
                // The old deposit was one amount per reservation. It is split across the tables by seats, with any
                // rounding remainder on the last table, so the lines always add up to the amount that was recorded.
                var tables = seats.getOrDefault(booking.id(), List.of());
                int allSeats = tables.stream().mapToInt(Seat::capacity).sum();
                BigDecimal assigned = BigDecimal.ZERO;
                for (int i = 0; i < tables.size(); i++) {
                    var seat = tables.get(i);
                    BigDecimal price = i == tables.size() - 1 ? total.subtract(assigned)
                        : total.multiply(BigDecimal.valueOf(seat.capacity())).divide(BigDecimal.valueOf(allSeats), 2, RoundingMode.DOWN);
                    assigned = assigned.add(price);
                    item.setLong(1, booking.id()); item.setLong(2, seat.tableId()); item.setString(3, seat.number()); item.setInt(4, seat.capacity());
                    item.setBigDecimal(5, price); item.setString(6, cancelled ? "CANCELLED" : "ACTIVE");
                    item.setBigDecimal(7, cancelled && refundOwed ? price : BigDecimal.ZERO);
                    item.executeUpdate();
                }
                String paymentState = switch (booking.paymentState() == null ? "" : booking.paymentState()) {
                    case "NOT_REQUIRED" -> "NOT_REQUIRED";
                    case "SUBMITTED" -> "PENDING";
                    case "VERIFIED", "REFUND_DUE", "REFUNDED" -> "PAID";
                    case "REJECTED" -> "REJECTED";
                    default -> "UNPAID";
                };
                if (booking.reference() != null && !booking.reference().isBlank() && total.signum() > 0 && !"UNPAID".equals(paymentState) && !"NOT_REQUIRED".equals(paymentState)) {
                    // Payments made in the earlier classroom simulator are labelled as simulated: no money was received for them.
                    boolean simulated = booking.reference().startsWith("GCASH-DEMO-");
                    boolean reviewed = !"PENDING".equals(paymentState);
                    payment.setLong(1, booking.id()); payment.setString(2, simulated ? "DEMO_SIMULATED" : "GCASH_MANUAL"); payment.setBigDecimal(3, total);
                    payment.setString(4, paymentState); payment.setString(5, booking.reference()); payment.setBoolean(6, false);
                    payment.setTimestamp(7, booking.createdAt()); payment.setString(8, "migration");
                    payment.setTimestamp(9, reviewed ? booking.createdAt() : null); payment.setString(10, reviewed ? "migration" : null);
                    payment.setString(11, "REFUNDED".equals(booking.paymentState()) ? null : booking.note());
                    payment.executeUpdate();
                    if ("REFUNDED".equals(booking.paymentState())) {
                        long paymentId;
                        try (var keys = payment.getGeneratedKeys()) { keys.next(); paymentId = keys.getLong(1); }
                        String refundReference = booking.note() == null || booking.note().isBlank() ? "LEGACY-" + booking.id() : booking.note();
                        refund.setLong(1, booking.id()); refund.setLong(2, paymentId); refund.setBigDecimal(3, total);
                        refund.setString(4, "Refund recorded before the refund ledger existed"); refund.setString(5, booking.reference());
                        refund.setString(6, refundReference.length() > 150 ? refundReference.substring(0, 150) : refundReference); refund.setString(7, "COMPLETED");
                        refund.setTimestamp(8, booking.createdAt()); refund.setString(9, "migration"); refund.setTimestamp(10, booking.createdAt()); refund.setString(11, "migration");
                        refund.executeUpdate();
                    }
                } else if (!"NOT_REQUIRED".equals(paymentState)) {
                    paymentState = total.signum() > 0 ? "UNPAID" : "NOT_REQUIRED";
                }
                state.setString(1, paymentState); state.setLong(2, booking.id()); state.executeUpdate();
            }
        }
        normalizePhones(db, "users", "phone_number");
        normalizePhones(db, "reservations", "contact_number");
        normalizePhones(db, "inquiries", "phone_number");
    }

    /** Rewrites 09XXXXXXXXX, 639XXXXXXXXX and spaced or dashed spellings as +639XXXXXXXXX. Anything else is left untouched. */
    private void normalizePhones(Connection db, String table, String column) throws SQLException {
        var changes = new LinkedHashMap<Long, String>();
        try (var query = db.createStatement(); var rows = query.executeQuery("SELECT id, " + column + " FROM " + table)) {
            while (rows.next()) {
                String stored = rows.getString(2);
                if (stored == null) continue;
                var match = MOBILE.matcher(SEPARATORS.matcher(stored.trim()).replaceAll(""));
                if (match.matches() && !stored.equals("+63" + match.group(1))) changes.put(rows.getLong(1), "+63" + match.group(1));
            }
        }
        try (var update = db.prepareStatement("UPDATE " + table + " SET " + column + " = ? WHERE id = ?")) {
            for (var change : changes.entrySet()) { update.setString(1, change.getValue()); update.setLong(2, change.getKey()); update.executeUpdate(); }
        }
    }
}
