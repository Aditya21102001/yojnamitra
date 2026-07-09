package com.yojanamitra.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end cover for the two-step login and the guarantees around it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class MfaFlowTest {

    private static final String PASS = "password123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MfaService mfa;

    /** H2 runs with DB_CLOSE_DELAY=-1 and the context is shared, so rows outlive
     *  each test. A fresh username per test keeps register() from colliding. */
    private String user;
    private String secret;
    private String enrolCode;
    private List<String> recoveryCodes;

    @BeforeEach
    void enrol() throws Exception {
        user = "mfa" + UUID.randomUUID().toString().substring(0, 8);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(user, PASS)))
                .andExpect(status().isOk());

        // Enrol out-of-band so the test does not depend on the setup endpoint's shape.
        MfaSetupResponse setup = mfa.beginSetup(user);
        secret = setup.secret();
        enrolCode = currentCode(secret);
        recoveryCodes = mfa.enable(user, enrolCode);
        assertEquals(10, recoveryCodes.size());
    }

    /**
     * enable() already spent this code's time-step. Because the step the code
     * belongs to is burned (rather than whatever step "now" happens to be), the
     * replay fails even if the 30s window rolled over between the two calls —
     * which is what makes this assertion deterministic rather than flaky.
     */
    @Test
    void aSpentTotpCodeIsNeverAcceptedAgain() {
        assertThrows(ResponseStatusException.class, () -> mfa.verifyOrThrow(user, enrolCode));
    }

    /** A code for a step that was never spent still works, so drift tolerance survives. */
    @Test
    void anUnspentTotpCodeFromTheNextStepIsAccepted() throws Exception {
        String nextStep = new DefaultCodeGenerator()
                .generate(secret, Instant.now().getEpochSecond() / 30 + 1);
        mfa.verifyOrThrow(user, nextStep);   // throws on failure
    }

    @Test
    void loginWithMfaEnabledReturnsAChallengeAndNoAccessToken() throws Exception {
        JsonNode res = login();
        assertTrue(res.get("mfaRequired").asBoolean());
        assertTrue(res.get("token").isNull(), "login must not hand out a session before the second factor");
        assertNotNull(res.get("mfaToken").asText());
    }

    /** The bypass this whole design exists to prevent. */
    @Test
    void mfaChallengeTokenCannotAuthenticateProtectedEndpoints() throws Exception {
        String challenge = login().get("mfaToken").asText();

        mvc.perform(get("/api/history").header("Authorization", "Bearer " + challenge))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recoveryCodeCompletesLoginAndIsSingleUse() throws Exception {
        String challenge = login().get("mfaToken").asText();
        String code = recoveryCodes.get(0);

        MvcResult ok = mvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"" + challenge + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode res = json.readTree(ok.getResponse().getContentAsString());
        String access = res.get("token").asText();
        assertNotNull(access);

        // The access token is a real session; the challenge token never was.
        mvc.perform(get("/api/history").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        // Burned: the same code must not work a second time.
        String second = login().get("mfaToken").asText();
        mvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"" + second + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Exercises the derived deleteByUser and returns the account to single-factor login. */
    @Test
    void disableClearsTheSecondFactorAndRecoveryCodes() throws Exception {
        mfa.disable(user, PASS, recoveryCodes.get(0));

        assertEquals(false, mfa.status(user).enabled());
        assertEquals(0, mfa.status(user).recoveryCodesRemaining());

        JsonNode res = login();
        assertEquals(false, res.get("mfaRequired").asBoolean());
        assertTrue(res.get("token").isTextual(), "login should hand out a session again once MFA is off");
    }

    /** /api/auth/** is permitAll, so enrolment endpoints need their own matcher. */
    @Test
    void enrolmentEndpointsRejectAnonymousCallers() throws Exception {
        mvc.perform(post("/api/auth/mfa/setup")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/mfa/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void userWithoutMfaStillGetsATokenStraightFromLogin() throws Exception {
        String plain = user + "p";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(plain, PASS)))
                .andExpect(status().isOk());

        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(plain, PASS)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode res = json.readTree(r.getResponse().getContentAsString());
        assertTrue(res.get("token").isTextual());
        assertTrue(res.get("mfaToken").isNull());
        assertEquals(false, res.get("mfaRequired").asBoolean());
    }

    // ---- helpers ----

    private JsonNode login() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(user, PASS)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    private static String body(String user, String pass) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}";
    }

    private static String currentCode(String secret) throws Exception {
        return new DefaultCodeGenerator().generate(secret, Instant.now().getEpochSecond() / 30);
    }
}
