package com.clinicone.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {
    Optional<LoginSession> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoginSession session set session.revokedAt = :revokedAt "
            + "where session.accountId = :accountId and session.revokedAt is null")
    int revokeActiveByAccountId(@Param("accountId") UUID accountId, @Param("revokedAt") Instant revokedAt);
}
