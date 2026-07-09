package com.yojanamitra.api.mail;

/** Delivers transactional mail. Implementations are chosen by {@code yojanamitra.mail.provider}. */
public interface EmailSender {

    void send(String to, String subject, String htmlBody, String textBody);
}
