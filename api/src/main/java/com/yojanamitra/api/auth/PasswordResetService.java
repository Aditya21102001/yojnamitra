package com.yojanamitra.api.auth;

import com.yojanamitra.api.mail.EmailSender;
import com.yojanamitra.api.user.AppUser;
import com.yojanamitra.api.user.AppUserRepository;
import com.yojanamitra.api.user.PasswordResetToken;
import com.yojanamitra.api.user.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Forgot password" over a single-use, short-lived, emailed token.
 *
 * <p>Design notes that are easy to get wrong:
 * <ul>
 *   <li><b>No user enumeration.</b> {@link #requestReset} returns normally for an
 *       unknown username, a known one, and a provider outage alike. Any
 *       difference — a distinct status, a slower response, an error — turns the
 *       endpoint into an oracle for which accounts exist.</li>
 *   <li><b>MFA is not bypassable.</b> If the account has a second factor, a code
 *       is required to reset. And because {@link #reset} issues no session, the
 *       user must still log in afterwards, where MFA is enforced again.</li>
 *   <li><b>Old sessions die.</b> Changing the password stamps
 *       {@code passwordChangedAt}, and {@code JwtAuthFilter} rejects every token
 *       issued before it. Otherwise an attacker's stolen token would outlive the
 *       reset that was meant to evict them.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32;             // 256 bits
    private static final int MAX_REQUESTS = 5;
    private static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final MfaService mfa;
    private final EmailSender email;
    private final Duration ttl;
    private final String webBaseUrl;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Attempts> requests = new ConcurrentHashMap<>();

    public PasswordResetService(AppUserRepository users,
                                PasswordResetTokenRepository tokens,
                                PasswordEncoder encoder,
                                MfaService mfa,
                                EmailSender email,
                                @Value("${yojanamitra.reset.ttl-minutes}") long ttlMinutes,
                                @Value("${yojanamitra.web.base-url}") String webBaseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mfa = mfa;
        this.email = email;
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.webBaseUrl = webBaseUrl.endsWith("/")
                ? webBaseUrl.substring(0, webBaseUrl.length() - 1)
                : webBaseUrl;
    }

    /**
     * Always succeeds, whatever the caller passed. A user without an email on
     * file simply receives nothing — there is no channel to reach them on.
     */
    @Transactional
    public void requestReset(String usernameOrEmail) {
        String identifier = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        if (identifier.isEmpty() || isThrottled(identifier)) {
            return;
        }
        recordRequest(identifier);

        Optional<AppUser> found = users.findByUsername(identifier)
                .or(() -> users.findByEmailIgnoreCase(identifier));
        if (found.isEmpty()) {
            log.debug("Password reset requested for unknown identifier");
            return;
        }
        AppUser user = found.get();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.debug("Password reset requested for an account with no email on file");
            return;
        }

        tokens.deleteByUser(user);   // a new link retires the old ones

        String raw = newToken();
        tokens.save(new PasswordResetToken(user, sha256(raw), Instant.now().plus(ttl)));
        sendResetEmail(user, raw);
    }

    /**
     * Lets the reset page know whether to ask for a second factor. Only useful to
     * someone already holding the token, so it discloses nothing new.
     */
    @Transactional(readOnly = true)
    public ResetPrecheckResponse precheck(String rawToken) {
        return redeemable(rawToken)
                .map(t -> new ResetPrecheckResponse(true, t.getUser().isMfaEnabled()))
                .orElseGet(() -> new ResetPrecheckResponse(false, false));
    }

    /** Changes the password. Deliberately returns no session: the user logs in again, through MFA. */
    @Transactional
    public void reset(String rawToken, String newPassword, String code) {
        PasswordResetToken token = redeemable(rawToken).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired. Request a new one."));

        AppUser user = token.getUser();
        if (user.isMfaEnabled()) {
            // Email access alone must not be enough to seize an MFA-protected account.
            if (code == null || code.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "This account uses two-factor authentication. Enter a code to continue.");
            }
            mfa.verifyOrThrow(user.getUsername(), code);
        }

        user.changePassword(encoder.encode(newPassword));
        users.save(user);

        token.markUsed();
        tokens.save(token);
        List<PasswordResetToken> others = tokens.findByUser(user).stream()
                .filter(t -> !t.getId().equals(token.getId()))
                .toList();
        tokens.deleteAll(others);

        clearRequests(user.getUsername());
        if (user.getEmail() != null) {
            clearRequests(user.getEmail());
        }
        log.info("Password reset completed for user id {}", user.getId());
    }

    // ---------------- internals ----------------

    private Optional<PasswordResetToken> redeemable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(sha256(rawToken.trim()))
                .filter(t -> t.isRedeemable(Instant.now()));
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private void sendResetEmail(AppUser user, String rawToken) {
        String link = webBaseUrl + "/reset-password?token=" + rawToken;
        long minutes = ttl.toMinutes();
        String text = """
                Hello %s,

                Someone asked to reset your YojanaMitra password. Open this link to choose a new one:

                %s

                The link works once and expires in %d minutes.
                If this wasn't you, ignore this email — your password has not changed.
                """.formatted(user.getUsername(), link, minutes);
        String html = """
                <p>Hello %s,</p>
                <p>Someone asked to reset your YojanaMitra password.</p>
                <p><a href="%s">Choose a new password</a></p>
                <p>The link works once and expires in %d minutes.<br>
                If this wasn't you, ignore this email — your password has not changed.</p>
                """.formatted(user.getUsername(), link, minutes);

        email.send(user.getEmail(), "Reset your YojanaMitra password", html, text);
    }

    private boolean isThrottled(String identifier) {
        Attempts a = requests.get(identifier);
        return a != null && a.count >= MAX_REQUESTS && Instant.now().isBefore(a.until);
    }

    private void recordRequest(String identifier) {
        requests.compute(identifier, (k, existing) -> {
            Attempts a = (existing == null || Instant.now().isAfter(existing.until))
                    ? new Attempts()
                    : existing;
            a.count++;
            a.until = Instant.now().plus(REQUEST_WINDOW);
            return a;
        });
    }

    private void clearRequests(String identifier) {
        requests.remove(identifier);
    }

    private static final class Attempts {
        private int count;
        private Instant until = Instant.EPOCH;
    }
}
