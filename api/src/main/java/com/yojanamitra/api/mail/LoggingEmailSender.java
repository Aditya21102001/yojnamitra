package com.yojanamitra.api.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The default. Prints the message — reset link and all — to the application log
 * instead of sending it.
 *
 * <p>This keeps local development and a free demo deploy working with no mail
 * provider, no API key and no internet. It is obviously unfit for real users:
 * anyone who can read the logs can take over any account, which is why startup
 * says so out loud.
 */
@Component
@ConditionalOnProperty(name = "yojanamitra.mail.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    public LoggingEmailSender() {
        log.warn("Mail provider is 'log': emails are written to this log, not delivered. "
                + "Set yojanamitra.mail.provider=brevo (and BREVO_API_KEY) for real delivery.");
    }

    @Override
    public void send(String to, String subject, String htmlBody, String textBody) {
        log.info("""

                ---------------- EMAIL (not sent) ----------------
                To:      {}
                Subject: {}

                {}
                --------------------------------------------------""", to, subject, textBody);
    }
}
