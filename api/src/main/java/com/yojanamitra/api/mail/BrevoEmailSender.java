package com.yojanamitra.api.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Sends through Brevo's HTTP API.
 *
 * <p>Two deliberate choices. Brevo rather than Resend, because its free tier
 * (300 mails/day) delivers to any recipient once a single sender address is
 * verified, whereas Resend's free tier needs a domain you own. And the HTTP API
 * rather than SMTP, because hosts such as Render block outbound SMTP ports.
 */
@Component
@ConditionalOnProperty(name = "yojanamitra.mail.provider", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final RestClient http = RestClient.create();
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(
            @Value("${yojanamitra.mail.brevo.api-key}") String apiKey,
            @Value("${yojanamitra.mail.from-email}") String fromEmail,
            @Value("${yojanamitra.mail.from-name}") String fromName) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "yojanamitra.mail.provider=brevo but BREVO_API_KEY is empty");
        }
    }

    @Override
    public void send(String to, String subject, String htmlBody, String textBody) {
        Map<String, Object> payload = Map.of(
                "sender", Map.of("email", fromEmail, "name", fromName),
                "to", java.util.List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", htmlBody,
                "textContent", textBody);
        try {
            http.post()
                    .uri(ENDPOINT)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            // Never propagate: /auth/forgot must answer identically whether or not
            // an account exists, and a provider outage must not become an oracle.
            log.error("Brevo delivery failed for {}: {}", to, ex.getMessage());
        }
    }
}
