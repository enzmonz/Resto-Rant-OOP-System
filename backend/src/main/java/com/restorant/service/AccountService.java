package com.restorant.service;
import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.exception.ApiException;
import com.restorant.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public AccountService(UserRepository users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }
    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    @Transactional public Api.Account register(Api.Register input) {
        if (!input.password().equals(input.confirmPassword())) throw new ApiException(400, "Passwords do not match");
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new ApiException(400, "Password must be at most 72 UTF-8 bytes");
        if (users.existsByEmail(normalize(input.email()))) throw new ApiException(409, "An account with that email already exists");
        return Api.Account.of(users.saveAndFlush(new Customer(input.fullName(), input.email(), input.phoneNumber(), encoder.encode(input.password()))));
    }
    @Transactional public Api.Account registerAdmin(Api.Register input) {
        if(!input.password().equals(input.confirmPassword()))throw new ApiException(400,"Passwords do not match");
        if(input.password().getBytes(StandardCharsets.UTF_8).length>72)throw new ApiException(400,"Password exceeds 72 UTF-8 bytes");
        if(users.existsByEmail(normalize(input.email())))throw new ApiException(409,"That email already has an account");
        return Api.Account.of(users.saveAndFlush(new Admin(input.fullName(),input.email(),input.phoneNumber(),encoder.encode(input.password()))));
    }
    /** Administrator accounts, newest first. */
    public java.util.List<Api.Account> staff() {
        return users.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"id")).stream()
            .filter(u->u instanceof Admin).map(Api.Account::of).toList();
    }
    public Customer customer(Long id) {return users.findById(id).filter(u->u instanceof Customer).map(u->(Customer)u).orElseThrow(()->new ApiException(404,"Customer not found"));}
    @Transactional public Api.Account updateCustomer(Long id,Api.Profile input){var c=customer(id);c.updateContact(input.fullName(),input.phoneNumber());c.setEnabled(input.enabled());return Api.Account.of(c);}
    @Transactional public void disableCustomer(Long id){customer(id).setEnabled(false);}
    public User require(String email) { return users.findByEmail(normalize(email)).filter(User::isEnabled).orElseThrow(() -> new ApiException(401, "Please log in with an active account")); }
}
