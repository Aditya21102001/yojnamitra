package com.yojanamitra.api.user;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single-use escape hatch for a user who has lost their authenticator device.
 * Only the BCrypt hash is stored, so a database leak does not hand over working
 * codes; the plaintext is shown exactly once, at enrolment.
 */
@Entity
@Table(name = "mfa_recovery_code")
public class MfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @jakarta.persistence.Column(nullable = false)
    private String codeHash;

    /** Non-null once redeemed; a redeemed code is never accepted again. */
    private Instant usedAt;

    protected MfaRecoveryCode() {
    }

    public MfaRecoveryCode(AppUser user, String codeHash) {
        this.user = user;
        this.codeHash = codeHash;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }
}
