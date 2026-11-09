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
    public User require(String email) { return users.findByEmail(normalize(email)).orElseThrow(() -> new ApiException(401, "Please log in")); }
}
