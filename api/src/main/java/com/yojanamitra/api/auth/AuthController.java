package com.yojanamitra.api.auth;

import com.yojanamitra.api.security.JwtService;
import com.yojanamitra.api.user.AppUser;
import com.yojanamitra.api.user.AppUserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AuthController(AppUserRepository users, PasswordEncoder encoder,
                          AuthenticationManager authManager, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        AppUser user = users.save(new AppUser(req.username(), encoder.encode(req.password())));
        return new AuthResponse(jwt.generate(user.getUsername()), user.getUsername());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return new AuthResponse(jwt.generate(req.username()), req.username());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getName());
        return Map.of("authenticated", authenticated, "username", authenticated ? auth.getName() : "");
    }
}
