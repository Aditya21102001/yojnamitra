package com.yojanamitra.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A registered user. Password is stored BCrypt-hashed. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private Instant createdAt = Instant.now();

    /**
     * The TOTP shared secret, AES-GCM encrypted (see SecretCipher). Set during
     * enrolment and kept even before {@link #mfaEnabled} flips, so the user can
     * confirm a code against it. Null once MFA is disabled.
     */
    @Column(length = 512)
    private String mfaSecret;

    /**
     * A DB-level default is required, not just the Java one. Hibernate's
     * {@code ddl-auto: update} would otherwise emit
     * {@code ALTER TABLE app_user ADD COLUMN mfa_enabled boolean not null},
     * which Postgres rejects on a table that already has rows. SchemaUpdate logs
     * that failure and carries on booting, leaving the app running against a
     * column that does not exist.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean mfaEnabled = false;

    /**
     * The last TOTP time-step accepted for this user. A code is only valid if its
     * step is strictly greater, so a code observed over the shoulder (or captured
     * in transit) cannot be replayed inside its own 30-second window.
     */
    private Long lastTotpTimeStep;

    protected AppUser() {
    }

    public AppUser(String username, String password) {
        this.username = username;
        this.password = password;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public void setMfaSecret(String mfaSecret) {
        this.mfaSecret = mfaSecret;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public Long getLastTotpTimeStep() {
        return lastTotpTimeStep;
    }

    public void setLastTotpTimeStep(Long lastTotpTimeStep) {
        this.lastTotpTimeStep = lastTotpTimeStep;
    }
}
