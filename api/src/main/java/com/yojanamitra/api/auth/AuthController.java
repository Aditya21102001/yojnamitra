package com.yojanamitra.api.auth;

import com.yojanamitra.api.security.JwtService;
import com.yojanamitra.api.security.TokenType;
import com.yojanamitra.api.user.AppUser;
import com.yojanamitra.api.user.AppUserRepository;
import io.jsonwebtoken.JwtException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final MfaService mfa;
    private final PasswordResetService passwordReset;

    public AuthController(AppUserRepository users, PasswordEncoder encoder,
                          AuthenticationManager authManager, JwtService jwt, MfaService mfa,
                          PasswordResetService passwordReset) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
        this.mfa = mfa;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        String email = (req.email() == null || req.email().isBlank()) ? null : req.email().trim();
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered");
        }
        AppUser user = users.save(new AppUser(req.username(), encoder.encode(req.password()), email));
        return AuthResponse.authenticated(jwt.generateAccess(user.getUsername()), user.getUsername());
    }

    // ---- forgotten password ----

    /** Always 204, for any input: a different answer would reveal which accounts exist. */
    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordReset.requestReset(req.usernameOrEmail());
    }

    @GetMapping("/reset/precheck")
    public ResetPrecheckResponse resetPrecheck(@RequestParam String token) {
        return passwordReset.precheck(token);
    }

    /** Issues no session on purpose — the user logs in again, which re-applies MFA. */
    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest req) {
        passwordReset.reset(req.token(), req.newPassword(), req.code());
    }

    /**
     * First factor. When the account has MFA enabled this deliberately returns
     * no access token — only a short-lived challenge that is useless without a code.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        AppUser user = users.findByUsername(req.username()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (user.isMfaEnabled()) {
            return AuthResponse.mfaChallenge(jwt.generateMfaChallenge(user.getUsername()), user.getUsername());
        }
        return AuthResponse.authenticated(jwt.generateAccess(user.getUsername()), user.getUsername());
    }

    /** Second factor. Exchanges a challenge token plus a code for a real session. */
    @PostMapping("/mfa/verify")
    public AuthResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest req) {
        String username;
        try {
            username = jwt.extractUsername(req.mfaToken(), TokenType.MFA_CHALLENGE);
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your login attempt expired. Start again.");
        }
        mfa.verifyOrThrow(username, req.code());
        return AuthResponse.authenticated(jwt.generateAccess(username), username);
    }

    // ---- enrolment management (authenticated; see SecurityConfig) ----

    @GetMapping("/mfa/status")
    public MfaStatusResponse mfaStatus(Authentication auth) {
        return mfa.status(currentUser(auth));
    }

    @PostMapping("/mfa/setup")
    public MfaSetupResponse mfaSetup(Authentication auth) {
        return mfa.beginSetup(currentUser(auth));
    }

    /** Returns the recovery codes in plaintext exactly once — they are hashed on the way in. */
    @PostMapping("/mfa/enable")
    public Map<String, List<String>> mfaEnable(Authentication auth, @Valid @RequestBody MfaCodeRequest req) {
        return Map.of("recoveryCodes", mfa.enable(currentUser(auth), req.code()));
    }

    @PostMapping("/mfa/disable")
    public Map<String, Boolean> mfaDisable(Authentication auth, @Valid @RequestBody MfaDisableRequest req) {
        mfa.disable(currentUser(auth), req.password(), req.code());
        return Map.of("enabled", false);
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getName());
        return Map.of("authenticated", authenticated, "username", authenticated ? auth.getName() : "");
    }

    private String currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return auth.getName();
    }
}
