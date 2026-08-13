package com.clinicone.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_sessions")
public class LoginSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    @JsonIgnore
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // Legacy patient sessions predate role-based sessions and legitimately have no role.
    // Keep the column nullable so Hibernate can update an existing database without
    // failing on those rows; getRole() applies the patient fallback when reading them.
    @Column(length = 200)
    private String role;

    protected LoginSession() {
    }

    public LoginSession(UUID accountId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this(accountId, tokenHash, issuedAt, expiresAt, "ROLE_PATIENT");
    }

    public LoginSession(UUID accountId, String tokenHash, Instant issuedAt, Instant expiresAt, String role) {
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.role = role;
    }

    public void revoke(Instant now) { revokedAt = now; }
    public UUID getAccountId() { return accountId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRole() { return role == null || role.isBlank() ? "ROLE_PATIENT" : role; }
}
