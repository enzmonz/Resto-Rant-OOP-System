package com.restorant.controller;
import com.restorant.dto.Api;
import com.restorant.exception.ApiException;
import com.restorant.service.AccountService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    public AuthController(AccountService accounts, AuthenticationManager authentication, SecurityContextRepository contexts) {
        this.accounts=accounts; this.authentication=authentication; this.contexts=contexts;
    }
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Api.Account register(@Valid @RequestBody Api.Register input) { return accounts.register(input); }
    @PostMapping("/login") public Api.Account login(@Valid @RequestBody Api.Login input, HttpServletRequest req, HttpServletResponse res) {
        try {
            var auth = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
            if (req.getSession(false) != null) req.changeSessionId();
            var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth);
            SecurityContextHolder.setContext(context); contexts.saveContext(context, req, res);
            return Api.Account.of(accounts.require(auth.getName()));
        } catch (AuthenticationException e) { throw new ApiException(401, "Email or password is incorrect"); }
    }
    @GetMapping("/me") public Api.Account me(Principal user) { return Api.Account.of(accounts.require(user.getName())); }
}
