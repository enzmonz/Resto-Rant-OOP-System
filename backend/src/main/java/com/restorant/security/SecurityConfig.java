package com.restorant.security;
import com.restorant.repository.UserRepository;
import com.restorant.service.AccountService;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetails(UserRepository users) {
        return email -> users.findByEmail(AccountService.normalize(email)).map(u ->
            User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles(u.getRole()).disabled(!u.isEnabled()).build())
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean SecurityFilterChain security(HttpSecurity http, SecurityContextRepository contexts) throws Exception {
        return http.securityContext(c -> c.securityContextRepository(contexts))
            .csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/index.html", "/app.js", "/experience.js", "/style.css", "/assets/**", "/error",
                    "/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/api/info", "/api/availability/**", "/api/deposit-policy", "/api/inquiries").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/reservations/**").hasRole("CUSTOMER")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Please log in to continue\"}"); })
                .accessDeniedHandler((req,res,ex) -> { res.setStatus(403); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Access denied or session token expired. Refresh and try again.\"}"); }))
            .logout(l -> l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; img-src 'self' https://images.unsplash.com; style-src 'self'; script-src 'self'; frame-src https://www.google.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
            .build();
    }
}
