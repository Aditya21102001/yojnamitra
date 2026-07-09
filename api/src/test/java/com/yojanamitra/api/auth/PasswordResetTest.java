package com.yojanamitra.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yojanamitra.api.mail.EmailSender;
import com.yojanamitra.api.user.AppUser;
import com.yojanamitra.api.user.AppUserRepository;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class PasswordResetTest {

    private static final String PASS = "password123";
    private static final String NEW_PASS = "brand-new-pass";
    private static final Pattern LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    /** Captures what would have been emailed, so the test can read the token out of it. */
    static class Inbox implements EmailSender {
        final List<String> bodies = new ArrayList<>();

        @Override
        public void send(String to, String subject, String htmlBody, String textBody) {
            bodies.add(textBody);
        }

        String lastToken() {
            String body = bodies.get(bodies.size() - 1);   // getLast() is Java 21; this project is 17
            Matcher m = LINK.matcher(body);
            if (!m.find()) {
                throw new IllegalStateException("no token in: " + body);
            }
            return m.group(1);
        }
    }

    @TestConfiguration
    static class MailConfig {
        @Bean
        @Primary
        Inbox inbox() {
            return new Inbox();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Inbox inbox;
    @Autowired MfaService mfa;
    @Autowired AppUserRepository users;

    private String user;
    private String email;

    @BeforeEach
    void register() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        user = "pr" + id;
        email = user + "@example.test";
        inbox.bodies.clear();

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","email":"%s"}""".formatted(user, PASS, email)))
                .andExpect(status().isOk());
    }

    // ---------------- enumeration ----------------

    /** The response must not distinguish a real account from a made-up one. */
    @Test
    void forgotAnswersIdenticallyForKnownAndUnknownAccounts() throws Exception {
        mvc.perform(forgot(user)).andExpect(status().isNoContent());
        assertEquals(1, inbox.bodies.size(), "a real account should be mailed");

        inbox.bodies.clear();
        mvc.perform(forgot("definitely-not-a-user")).andExpect(status().isNoContent());
        assertTrue(inbox.bodies.isEmpty(), "an unknown account must not be mailed");
    }

    @Test
    void forgotAcceptsEitherUsernameOrEmail() throws Exception {
        mvc.perform(forgot(email)).andExpect(status().isNoContent());
        assertEquals(1, inbox.bodies.size());
    }

    // ---------------- the happy path ----------------

    @Test
    void resetChangesThePasswordAndIssuesNoSession() throws Exception {
        mvc.perform(forgot(user));
        String token = inbox.lastToken();

        // 204 with an empty body: no token is handed out here, by design.
        MvcResult res = mvc.perform(reset(token, NEW_PASS, null))
                .andExpect(status().isNoContent())
                .andReturn();
        assertTrue(res.getResponse().getContentAsString().isBlank());

        mvc.perform(login(user, PASS)).andExpect(status().isUnauthorized());
        mvc.perform(login(user, NEW_PASS)).andExpect(status().isOk());
    }

    @Test
    void aTokenWorksOnlyOnce() throws Exception {
        mvc.perform(forgot(user));
        String token = inbox.lastToken();

        mvc.perform(reset(token, NEW_PASS, null)).andExpect(status().isNoContent());
        mvc.perform(reset(token, "another-password", null)).andExpect(status().isBadRequest());
    }

    @Test
    void requestingANewLinkRetiresThePreviousOne() throws Exception {
        mvc.perform(forgot(user));
        String first = inbox.lastToken();
        mvc.perform(forgot(user));
        String second = inbox.lastToken();

        assertFalse(first.equals(second));
        mvc.perform(reset(first, NEW_PASS, null)).andExpect(status().isBadRequest());
        mvc.perform(reset(second, NEW_PASS, null)).andExpect(status().isNoContent());
    }

    @Test
    void garbageTokensAreRejected() throws Exception {
        mvc.perform(reset("not-a-real-token", NEW_PASS, null)).andExpect(status().isBadRequest());
    }

    // ---------------- interaction with MFA ----------------

    /** Email access alone must not seize an account protected by a second factor. */
    @Test
    void resetOnAnMfaAccountRequiresACode() throws Exception {
        enableMfa();
        mvc.perform(forgot(user));
        String token = inbox.lastToken();

        mvc.perform(reset(token, NEW_PASS, null)).andExpect(status().isUnauthorized());
        mvc.perform(reset(token, NEW_PASS, "000000")).andExpect(status().isUnauthorized());

        // Still unchanged after those failures.
        mvc.perform(login(user, PASS)).andExpect(status().isOk());
    }

    @Test
    void precheckTellsThePageWhetherACodeIsNeeded() throws Exception {
        mvc.perform(forgot(user));
        String plain = inbox.lastToken();
        mvc.perform(get("/api/auth/reset/precheck").param("token", plain))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var node = json.readTree(result.getResponse().getContentAsString());
                    assertTrue(node.get("valid").asBoolean());
                    assertFalse(node.get("mfaRequired").asBoolean());
                });

        mvc.perform(get("/api/auth/reset/precheck").param("token", "rubbish"))
                .andExpect(status().isOk())
                .andExpect(result -> assertFalse(
                        json.readTree(result.getResponse().getContentAsString()).get("valid").asBoolean()));
    }

    // ---------------- session eviction ----------------

    /** A stolen token must not outlive the reset that was meant to evict it. */
    @Test
    void tokensIssuedBeforeTheResetStopWorking() throws Exception {
        MvcResult r = mvc.perform(login(user, PASS)).andExpect(status().isOk()).andReturn();
        String stolen = json.readTree(r.getResponse().getContentAsString()).get("token").asText();

        mvc.perform(get("/api/history").header("Authorization", "Bearer " + stolen))
                .andExpect(status().isOk());

        // iat has one-second resolution; make sure the reset lands in a later second.
        Thread.sleep(1100);
        mvc.perform(forgot(user));
        mvc.perform(reset(inbox.lastToken(), NEW_PASS, null)).andExpect(status().isNoContent());

        mvc.perform(get("/api/history").header("Authorization", "Bearer " + stolen))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aFreshLoginAfterTheResetWorks() throws Exception {
        Thread.sleep(1100);
        mvc.perform(forgot(user));
        mvc.perform(reset(inbox.lastToken(), NEW_PASS, null)).andExpect(status().isNoContent());

        MvcResult r = mvc.perform(login(user, NEW_PASS)).andExpect(status().isOk()).andReturn();
        String fresh = json.readTree(r.getResponse().getContentAsString()).get("token").asText();

        mvc.perform(get("/api/history").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk());
    }

    /** An account with no email cannot be reset — and still must not be revealed. */
    @Test
    void anAccountWithoutAnEmailIsSilentlySkipped() throws Exception {
        String noMail = "nomail" + UUID.randomUUID().toString().substring(0, 6);
        users.save(new AppUser(noMail, "irrelevant"));
        inbox.bodies.clear();

        mvc.perform(forgot(noMail)).andExpect(status().isNoContent());
        assertTrue(inbox.bodies.isEmpty());
    }

    // ---------------- helpers ----------------

    private void enableMfa() throws Exception {
        String secret = mfa.beginSetup(user).secret();
        mfa.enable(user, new DefaultCodeGenerator().generate(secret, Instant.now().getEpochSecond() / 30));
    }

    private static org.springframework.test.web.servlet.RequestBuilder forgot(String id) {
        return post("/api/auth/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"" + id + "\"}");
    }

    private static org.springframework.test.web.servlet.RequestBuilder reset(String token, String pw, String code) {
        String body = code == null
                ? "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, pw)
                : "{\"token\":\"%s\",\"newPassword\":\"%s\",\"code\":\"%s\"}".formatted(token, pw, code);
        return post("/api/auth/reset").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String u, String p) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(u, p));
    }
}
