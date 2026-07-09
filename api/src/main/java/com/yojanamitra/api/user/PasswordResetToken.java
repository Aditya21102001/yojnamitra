package com.yojanamitra.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A one-shot credential emailed to a user who has forgotten their password.
 *
 * <p>Only the SHA-256 hash is stored — the raw token exists solely inside the
 * email, so a database leak yields nothing usable. SHA-256 rather than BCrypt
 * because the token is 256 bits of {@code SecureRandom}: there is nothing to
 * brute-force, and a plain digest can be looked up by index instead of forcing
 * a scan-and-compare over every outstanding row.
 */
@Entity
@Table(name = "password_reset_token",
        indexes = @Index(name = "idx_prt_token_hash", columnList = "tokenHash"))
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Non-null once redeemed. A redeemed token is never accepted again. */
    private Instant usedAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(AppUser user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public boolean isRedeemable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
