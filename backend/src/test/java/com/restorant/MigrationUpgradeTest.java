package com.restorant;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MigrationUpgradeTest {
    @Test void upgradingExistingBookingsPreservesTheirTableAndHistory() throws Exception {
        String url="jdbc:h2:mem:upgrade-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
            sql.executeUpdate("INSERT INTO users(id,role,full_name,email,phone_number,password_hash) VALUES(1,'CUSTOMER','Existing Guest','existing@example.com','09171234567','test-hash')");
            sql.executeUpdate("INSERT INTO restaurant_tables(id,table_number,capacity) VALUES(1,'Old table',4)");
            sql.executeUpdate("INSERT INTO reservations(id,reference_number,customer_id,table_id,starts_at,ends_at,guest_count,contact_number,status,created_at) VALUES(1,'old-reference',1,1,'2030-01-01 12:00:00','2030-01-01 14:00:00',4,'09171234567','CONFIRMED',CURRENT_TIMESTAMP)");
            Flyway.configure().dataSource(url,"sa","").load().migrate();
            try(var result=sql.executeQuery("SELECT r.status,r.deposit_status,t.table_id FROM reservations r JOIN reservation_tables t ON t.reservation_id=r.id WHERE r.id=1")) {
                assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("CONFIRMED");
                assertThat(result.getString(2)).isEqualTo("NOT_REQUIRED");assertThat(result.getLong(3)).isEqualTo(1);
                assertThat(result.next()).isFalse();
            }
        }
    }
}
