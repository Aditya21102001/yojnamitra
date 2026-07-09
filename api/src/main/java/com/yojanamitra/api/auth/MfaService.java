package com.yojanamitra.api.auth;

import com.yojanamitra.api.security.SecretCipher;
import com.yojanamitra.api.user.AppUser;
import com.yojanamitra.api.user.AppUserRepository;
import com.yojanamitra.api.user.MfaRecoveryCode;
import com.yojanamitra.api.user.MfaRecoveryCodeRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TOTP (RFC 6238) second factor: enrolment, confirmation, verification and removal.
 *
 * <p>Three properties beyond "does the code match":
 * <ul>
 *   <li><b>Replay</b> — a code is only accepted if its time-step is strictly newer
 *       than the last one accepted, so an observed code dies the moment it is used.</li>
 *   <li><b>Brute force</b> — six digits is a 1-in-a-million guess, but a drift
 *       window and unlimited attempts erode that quickly, so failures are throttled.</li>
 *   <li><b>Recovery</b> — codes are BCrypt-hashed and burned on first use.</li>
 * </ul>
 */
@Service
public class MfaService {

    private static final int TIME_PERIOD_SECONDS = 30;
    /** Tolerate one step either side of now, for phone/server clock drift. */
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final PasswordEncoder encoder;
    private final SecretCipher cipher;
    private final String issuer;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final TimeProvider timeProvider;

    /**
     * Failure counters. In-memory on purpose: the API runs a single instance
     * (WEB_CONCURRENCY=1) and a restart clearing the counters is an acceptable
     * trade for not adding Redis. Revisit if the service is ever scaled out.
     */
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    /** Explicitly @Autowired: with two constructors present, Spring refuses to guess. */
    @Autowired
    public MfaService(AppUserRepository users,
                      MfaRecoveryCodeRepository recoveryCodes,
                      PasswordEncoder encoder,
                      SecretCipher cipher,
                      @Value("${yojanamitra.mfa.issuer}") String issuer) {
        this(users, recoveryCodes, encoder, cipher, issuer, new SystemTimeProvider());
    }

    /** Clock seam: replay protection is defined across time-step boundaries, and a
     *  test cannot cross one without controlling the clock. */
    MfaService(AppUserRepository users,
               MfaRecoveryCodeRepository recoveryCodes,
               PasswordEncoder encoder,
               SecretCipher cipher,
               String issuer,
               TimeProvider timeProvider) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.encoder = encoder;
        this.cipher = cipher;
        this.issuer = issuer;
        this.timeProvider = timeProvider;
    }

    // ---------------- enrolment ----------------

    /** Generates a fresh secret and returns it as a QR image plus a typeable string. */
    @Transactional
    public MfaSetupResponse beginSetup(String username) {
        AppUser user = require(username);
        if (user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Two-factor authentication is already enabled. Disable it before enrolling again.");
        }

        String secret = secretGenerator.generate();
        user.setMfaSecret(cipher.encrypt(secret));
        user.setLastTotpTimeStep(null);
        users.save(user);

        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(TIME_PERIOD_SECONDS)
                .build();

        try {
            String qr = Utils.getDataUriForImage(qrGenerator.generate(data), qrGenerator.getImageMimeType());
            return new MfaSetupResponse(secret, qr, data.getUri());
        } catch (QrGenerationException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not render the QR code", ex);
        }
    }

    /** Confirms the user can produce a code, turns MFA on, and issues recovery codes. */
    @Transactional
    public List<String> enable(String username, String code) {
        AppUser user = require(username);
        if (user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-factor authentication is already enabled.");
        }
        if (user.getMfaSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start setup before enabling.");
        }
        checkNotLockedOut(username);
        if (!consumeTotp(user, code)) {
            recordFailure(username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "That code is not valid. Check your authenticator app.");
        }
        clearFailures(username);

        user.setMfaEnabled(true);
        users.save(user);
        return regenerateRecoveryCodes(user);
    }

    /** Requires both the password and a current code, so a stolen session cannot silently weaken the account. */
    @Transactional
    public void disable(String username, String rawPassword, String code) {
        AppUser user = require(username);
        if (!user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor authentication is not enabled.");
        }
        checkNotLockedOut(username);
        if (!encoder.matches(rawPassword, user.getPassword()) || !verifySecondFactor(user, code)) {
            recordFailure(username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password or code is not valid.");
        }
        clearFailures(username);

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setLastTotpTimeStep(null);
        users.save(user);
        recoveryCodes.deleteByUser(user);
    }

    // ---------------- verification ----------------

    /** Accepts either a TOTP code or an unused recovery code. Throttled and replay-safe. */
    @Transactional
    public void verifyOrThrow(String username, String code) {
        AppUser user = require(username);
        if (!user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor authentication is not enabled.");
        }
        checkNotLockedOut(username);
        if (!verifySecondFactor(user, code)) {
            recordFailure(username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "That code is not valid.");
        }
        clearFailures(username);
    }

    @Transactional(readOnly = true)
    public MfaStatusResponse status(String username) {
        AppUser user = require(username);
        int remaining = user.isMfaEnabled() ? recoveryCodes.findByUserAndUsedAtIsNull(user).size() : 0;
        return new MfaStatusResponse(user.isMfaEnabled(), remaining);
    }

    private boolean verifySecondFactor(AppUser user, String code) {
        String cleaned = code == null ? "" : code.trim().replace(" ", "");
        if (cleaned.isEmpty()) {
            return false;
        }
        return consumeTotp(user, cleaned) || consumeRecoveryCode(user, cleaned);
    }

    /**
     * Validates a TOTP code and burns the exact time-step it belongs to.
     *
     * <p>The subtlety is that drift tolerance and replay protection interact. We
     * accept a code from any step in {@code [now-1, now+1]}, so recording "now"
     * as the used step would leave a hole: a code minted for step S, spent at S,
     * is still within tolerance at S+1 — and S+1 > S passes a naive guard. That
     * hands an attacker who captures a code a full extra window to replay it.
     *
     * <p>So we walk the candidate steps, find the one the code actually matches,
     * and burn <em>that</em> step. Steps at or below the last accepted one are
     * skipped before the code is even computed, which is what makes a replay of
     * an already-spent code fail no matter when it arrives.
     */
    private boolean consumeTotp(AppUser user, String code) {
        if (user.getMfaSecret() == null) {
            return false;
        }
        String secret = cipher.decrypt(user.getMfaSecret());
        long now = timeProvider.getTime() / TIME_PERIOD_SECONDS;
        Long last = user.getLastTotpTimeStep();

        for (long step = now - ALLOWED_DRIFT_STEPS; step <= now + ALLOWED_DRIFT_STEPS; step++) {
            if (last != null && step <= last) {
                continue;   // already spent; never accept this step again
            }
            String expected;
            try {
                expected = codeGenerator.generate(secret, step);
            } catch (CodeGenerationException ex) {
                return false;
            }
            // Constant-time: a length/prefix-sensitive compare leaks the code.
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    code.getBytes(StandardCharsets.UTF_8))) {
                user.setLastTotpTimeStep(step);
                users.save(user);
                return true;
            }
        }
        return false;
    }

    private boolean consumeRecoveryCode(AppUser user, String code) {
        for (MfaRecoveryCode candidate : recoveryCodes.findByUserAndUsedAtIsNull(user)) {
            if (encoder.matches(code, candidate.getCodeHash())) {
                candidate.markUsed();
                recoveryCodes.save(candidate);
                return true;
            }
        }
        return false;
    }

    private List<String> regenerateRecoveryCodes(AppUser user) {
        recoveryCodes.deleteByUser(user);
        String[] plaintext = recoveryCodeGenerator.generateCodes(RECOVERY_CODE_COUNT);
        List<MfaRecoveryCode> toStore = new ArrayList<>(plaintext.length);
        for (String code : plaintext) {
            toStore.add(new MfaRecoveryCode(user, encoder.encode(code)));
        }
        recoveryCodes.saveAll(toStore);
        return List.of(plaintext);
    }

    // ---------------- throttling ----------------

    private void checkNotLockedOut(String username) {
        Failures f = failures.get(username);
        if (f != null && f.count >= MAX_FAILURES && Instant.now().isBefore(f.until)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect codes. Try again later.");
        }
    }

    private void recordFailure(String username) {
        failures.compute(username, (k, existing) -> {
            Failures f = (existing == null || Instant.now().isAfter(existing.until))
                    ? new Failures()
                    : existing;
            f.count++;
            f.until = Instant.now().plus(LOCKOUT);
            return f;
        });
    }

    private void clearFailures(String username) {
        failures.remove(username);
    }

    private AppUser require(String username) {
        return users.findByUsername(username).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private static final class Failures {
        private int count;
        private Instant until = Instant.EPOCH;
    }
}
