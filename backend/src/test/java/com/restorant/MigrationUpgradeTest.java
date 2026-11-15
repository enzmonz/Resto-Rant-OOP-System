package com.restorant;

import com.restorant.migration.V5__Backfill_pricing_payments_and_phones;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Upgrades databases created by earlier versions and checks that no booking, payment or contact detail is lost. */
class MigrationUpgradeTest {
    private Flyway flyway(String url,String target) {
        var configuration=Flyway.configure().dataSource(url,"sa","").javaMigrations(new V5__Backfill_pricing_payments_and_phones());
        return (target==null?configuration:configuration.target(target)).load();
    }
    private List<String> rows(Statement sql,String query) throws Exception {
        var result=new ArrayList<String>();
        try(var rows=sql.executeQuery(query)) {
            int columns=rows.getMetaData().getColumnCount();
            while(rows.next()) { var line=new StringJoiner("|");for(int i=1;i<=columns;i++)line.add(String.valueOf(rows.getObject(i)));result.add(line.toString()); }
        }
        return result;
    }
    @Test void upgradingExistingBookingsPreservesTheirTableAndHistory() throws Exception {
        String url="jdbc:h2:mem:upgrade-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1";
        flyway(url,"1").migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
            sql.executeUpdate("INSERT INTO users(id,role,full_name,email,phone_number,password_hash) VALUES(1,'CUSTOMER','Existing Guest','existing@example.com','09171234567','test-hash')");
            sql.executeUpdate("INSERT INTO restaurant_tables(id,table_number,capacity) VALUES(1,'Old table',4)");
            sql.executeUpdate("INSERT INTO reservations(id,reference_number,customer_id,table_id,starts_at,ends_at,guest_count,contact_number,status,created_at) VALUES(1,'old-reference',1,1,'2030-01-01 12:00:00','2030-01-01 14:00:00',4,'09171234567','CONFIRMED',CURRENT_TIMESTAMP)");
            flyway(url,null).migrate();
            assertThat(rows(sql,"SELECT r.status,r.deposit_status,t.table_id,r.starts_at FROM reservations r JOIN reservation_tables t ON t.reservation_id=r.id WHERE r.id=1"))
                .containsExactly("CONFIRMED|NOT_REQUIRED|1|2030-01-01 12:00:00.0");
            assertThat(rows(sql,"SELECT table_number,capacity,price,status FROM reservation_items WHERE reservation_id=1")).containsExactly("Old table|4|0.00|ACTIVE");
            assertThat(rows(sql,"SELECT COUNT(*) FROM payments")).containsExactly("0");
        }
    }
    @Test void depositEraDataBecomesPriceLinesPaymentsRefundsAndCanonicalPhoneNumbers() throws Exception {
        String url="jdbc:h2:mem:upgrade-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1";
        flyway(url,"3").migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
            sql.executeUpdate("INSERT INTO users(id,role,full_name,email,phone_number,password_hash) VALUES(1,'CUSTOMER','Local','local@example.com','09171234567','h'),"
                +"(2,'CUSTOMER','International','intl@example.com','+639181234567','h'),(3,'ADMIN','Spaced','spaced@example.com','0919 123-4567','h'),(4,'CUSTOMER','Landline','landline@example.com','(02) 8123 4567','h')");
            sql.executeUpdate("INSERT INTO restaurant_tables(id,table_number,capacity) VALUES(1,'D01',2),(2,'D02',4),(3,'D03',6),(4,'Banquet',12)");
            String columns="INSERT INTO reservations(id,reference_number,customer_id,table_id,starts_at,ends_at,guest_count,contact_number,status,created_at,deposit_amount,deposit_status,payment_reference,payment_note) VALUES";
            // 1: simulated checkout, three tables. 2: manually verified, then cancelled and refunded. 3: reference submitted, never reviewed.
            // 4: cancelled with a refund still owed. 5: deposit required but nothing submitted.
            sql.executeUpdate(columns+"(1,'r1',1,1,'2030-01-01 04:00:00','2030-01-01 06:00:00',12,'09171234567','CONFIRMED',CURRENT_TIMESTAMP,1000.00,'VERIFIED','GCASH-DEMO-abc','Automatically verified SIMULATED GCash payment.')");
            sql.executeUpdate(columns+"(2,'r2',2,2,'2030-01-02 04:00:00','2030-01-02 06:00:00',4,'+639181234567','CANCELLED',CURRENT_TIMESTAMP,2000.00,'REFUNDED','REAL-REF-2','REFUND-REF-2')");
            sql.executeUpdate(columns+"(3,'r3',1,3,'2030-01-03 04:00:00','2030-01-03 06:00:00',6,'0917-123-4567','PENDING',CURRENT_TIMESTAMP,3000.00,'SUBMITTED','REAL-REF-3',NULL)");
            sql.executeUpdate(columns+"(4,'r4',2,2,'2030-01-04 04:00:00','2030-01-04 06:00:00',4,'09181234567','CANCELLED',CURRENT_TIMESTAMP,2000.00,'REFUND_DUE','REAL-REF-4','Matched receipt')");
            sql.executeUpdate(columns+"(5,'r5',1,1,'2030-01-05 04:00:00','2030-01-05 06:00:00',2,'09171234567','PENDING',CURRENT_TIMESTAMP,1000.00,'REQUIRED',NULL,NULL)");
            sql.executeUpdate("INSERT INTO reservation_tables(reservation_id,table_id) VALUES(1,1),(1,2),(1,3),(2,2),(3,3),(4,2),(5,1)");
            sql.executeUpdate("INSERT INTO inquiries(id,full_name,email,phone_number,message,created_at) VALUES(1,'Visitor','v@example.com','639201234567','Hello',CURRENT_TIMESTAMP)");
            flyway(url,null).migrate();

            // Nothing was deleted and reservation times are untouched.
            assertThat(rows(sql,"SELECT id,status,starts_at,deposit_amount,deposit_status FROM reservations ORDER BY id")).containsExactly(
                "1|CONFIRMED|2030-01-01 04:00:00.0|1000.00|PAID","2|CANCELLED|2030-01-02 04:00:00.0|2000.00|PAID","3|PENDING|2030-01-03 04:00:00.0|3000.00|PENDING",
                "4|CANCELLED|2030-01-04 04:00:00.0|2000.00|PAID","5|PENDING|2030-01-05 04:00:00.0|1000.00|UNPAID");
            // The old per-reservation deposit is split by seats and still adds up exactly (1000 across 2+4+6 seats).
            assertThat(rows(sql,"SELECT table_number,capacity,price,status,refundable_amount FROM reservation_items WHERE reservation_id=1 ORDER BY id"))
                .containsExactly("D01|2|166.66|ACTIVE|0.00","D02|4|333.33|ACTIVE|0.00","D03|6|500.01|ACTIVE|0.00");
            assertThat(rows(sql,"SELECT reservation_id,SUM(price) FROM reservation_items GROUP BY reservation_id ORDER BY reservation_id"))
                .containsExactly("1|1000.00","2|2000.00","3|3000.00","4|2000.00","5|1000.00");
            assertThat(rows(sql,"SELECT reservation_id,status,refundable_amount FROM reservation_items WHERE reservation_id IN (2,4) ORDER BY reservation_id"))
                .containsExactly("2|CANCELLED|2000.00","4|CANCELLED|2000.00");
            // Simulated payments are labelled as such; real references keep their state. Nothing is invented for reservation 5.
            assertThat(rows(sql,"SELECT reservation_id,method,amount,status,reference FROM payments ORDER BY reservation_id")).containsExactly(
                "1|DEMO_SIMULATED|1000.00|PAID|GCASH-DEMO-abc","2|GCASH_MANUAL|2000.00|PAID|REAL-REF-2","3|GCASH_MANUAL|3000.00|PENDING|REAL-REF-3","4|GCASH_MANUAL|2000.00|PAID|REAL-REF-4");
            assertThat(rows(sql,"SELECT reservation_id,amount,status,payment_reference,refund_reference FROM refunds")).containsExactly("2|2000.00|COMPLETED|REAL-REF-2|REFUND-REF-2");
            // Sample prices are seeded, including one for the capacity that already existed.
            assertThat(rows(sql,"SELECT capacity,price FROM table_packages ORDER BY capacity")).containsExactly("2|600.00","4|1100.00","6|1600.00","12|3100.00");
            // Phone numbers: 09 and 63 spellings become +63; unrecognised values are left for staff to correct.
            assertThat(rows(sql,"SELECT phone_number FROM users ORDER BY id")).containsExactly("+639171234567","+639181234567","+639191234567","(02) 8123 4567");
            assertThat(rows(sql,"SELECT contact_number FROM reservations ORDER BY id")).containsExactly("+639171234567","+639181234567","+639171234567","+639181234567","+639171234567");
            assertThat(rows(sql,"SELECT phone_number FROM inquiries")).containsExactly("+639201234567");
        }
    }
}
