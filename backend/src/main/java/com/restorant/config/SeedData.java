package com.restorant.config;
import com.restorant.entity.*;
import com.restorant.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class SeedData {
    @Bean ApplicationRunner bootstrap(UserRepository users,TableRepository tables,ReservationRepository bookings,
            PasswordEncoder encoder,Environment env,Clock clock,RestaurantSettings settings,TransactionTemplate tx,DepositPolicy deposit) {
        return args -> tx.executeWithoutResult(status -> {
            String adminPassword=env.getProperty("SEED_ADMIN_PASSWORD");
            if(adminPassword!=null && !adminPassword.isBlank()) {
                String email=env.getProperty("SEED_ADMIN_EMAIL","admin@restorant.local").trim().toLowerCase(Locale.ROOT);
                if(adminPassword.length()<10 || adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
                    throw new IllegalArgumentException("SEED_ADMIN_PASSWORD must contain at least 10 characters and at most 72 UTF-8 bytes");
                if(!users.existsByEmail(email)) users.save(new Admin("Restaurant Manager",email,"09170000000",encoder.encode(adminPassword)));
            }
            if (!env.acceptsProfiles(org.springframework.core.env.Profiles.of("demo"))) return;
            if(tables.count()==0) {
                for(int i=1;i<=25;i++) tables.save(new RestaurantTable("D"+String.format("%02d",i),i<=8?2:i<=17?4:6));
            }
            String password=env.getProperty("SEED_CUSTOMER_PASSWORD");
            if(password==null || password.isBlank()) return;
            if(password.length()<10 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Invalid seed customer password length");
            String email="guest@restorant.local";
            if(!users.existsByEmail(email)) {
                var customer=users.save(new Customer("Alex Rivera",email,"09171234567",encoder.encode(password)));
                var start=LocalDate.now(clock).plusDays(1).atTime(settings.opening());
                if(deposit.getAmount().signum()>0) {
                    var sample=new Reservation(customer,tables.findAllByOrderByCapacityAscIdAsc().getFirst(),start,
                        start.plusMinutes(settings.durationMinutes()),2,"A quiet corner, please.",clock);
                    sample.requireDeposit(deposit.total(2));bookings.save(sample);
                }
            }
        });
    }
}
