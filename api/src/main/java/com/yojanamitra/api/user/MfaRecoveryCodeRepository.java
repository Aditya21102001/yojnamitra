package com.yojanamitra.api.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    /** Unredeemed codes only — a used code must never match again. */
    List<MfaRecoveryCode> findByUserAndUsedAtIsNull(AppUser user);

    void deleteByUser(AppUser user);
}
