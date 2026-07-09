package com.yojanamitra.api.auth;

import com.yojanamitra.api.security.SecretCipher;
import com.yojanamitra.api.user.AppUser;
import com.yojanamitra.api.user.AppUserRepository;
import com.yojanamitra.api.user.MfaRecoveryCodeRepository;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The replay guard is only interesting across a time-step boundary, because the
 * verifier tolerates one step of clock drift. A code minted for step S stays
 * cryptographically valid through step S+1 — so "was this code already spent?"
 * cannot be answered by comparing against the current step. These tests drive a
 * fake clock across the boundary to pin that behaviour down.
 */
@SpringBootTest
@ActiveProfiles("h2")
class TotpReplayTest {

    /** A clock the test moves by hand. */
    private static final class FakeClock implements TimeProvider {
        private long seconds = 1_700_000_000L;   // arbitrary fixed instant

        @Override public long getTime() {
            return seconds;
        }

        void advanceSeconds(long by) {
            seconds += by;
        }

        long step() {
            return seconds / 30;
        }
    }

    @Autowired AppUserRepository users;
    @Autowired MfaRecoveryCodeRepository recoveryCodes;
    @Autowired PasswordEncoder encoder;
    @Autowired SecretCipher cipher;

    private final FakeClock clock = new FakeClock();
    private final DefaultCodeGenerator codes = new DefaultCodeGenerator();

    private MfaService mfa;
    private String user;
    private String secret;

    @BeforeEach
    void setUp() throws Exception {
        mfa = new MfaService(users, recoveryCodes, encoder, cipher, "YojanaMitra", clock);

        user = "clk" + UUID.randomUUID().toString().substring(0, 8);
        users.save(new AppUser(user, encoder.encode("password123")));

        secret = mfa.beginSetup(user).secret();
        mfa.enable(user, codes.generate(secret, clock.step()));
    }

    /**
     * The regression. Spend a code inside its own step, then let the clock roll
     * one step forward. The code is still within drift tolerance, so a guard that
     * merely required {@code currentStep > lastStep} would wave it straight
     * through — handing an attacker who captured it a whole extra window.
     */
    @Test
    void aSpentCodeIsRejectedAfterTheClockCrossesIntoTheNextStep() throws Exception {
        String code = codes.generate(secret, clock.step() + 1);

        clock.advanceSeconds(30);                        // now inside step S+1
        assertDoesNotThrow(() -> mfa.verifyOrThrow(user, code), "first use must succeed");

        clock.advanceSeconds(30);                        // now inside step S+2, code still within drift
        assertThrows(ResponseStatusException.class, () -> mfa.verifyOrThrow(user, code),
                "a code that was already spent must never be accepted again");
    }

    /** Drift tolerance must survive the fix: an unspent neighbouring step still works. */
    @Test
    void anUnspentCodeFromTheAdjacentStepIsStillAccepted() throws Exception {
        clock.advanceSeconds(60);
        String nextStep = codes.generate(secret, clock.step() + 1);

        assertDoesNotThrow(() -> mfa.verifyOrThrow(user, nextStep));
    }

    /** Codes older than the last accepted step are refused even without an exact replay. */
    @Test
    void aCodeFromAnAlreadyPassedStepIsRejected() throws Exception {
        clock.advanceSeconds(60);
        long current = clock.step();

        mfa.verifyOrThrow(user, codes.generate(secret, current));           // burns step `current`
        String stale = codes.generate(secret, current - 1);                 // still within drift

        assertThrows(ResponseStatusException.class, () -> mfa.verifyOrThrow(user, stale));
    }
}
