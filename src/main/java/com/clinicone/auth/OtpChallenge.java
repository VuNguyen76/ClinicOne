package com.clinicone.auth;

import lombok.Getter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "otp_challenges", indexes = {
        @Index(name = "idx_otp_destination_purpose_created", columnList = "destination,purpose,created_at")
})
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 100)
    @JsonIgnore
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected OtpChallenge() {
    }

    public OtpChallenge(String destination, OtpPurpose purpose, String codeHash, Instant createdAt, Instant expiresAt) {
        this.destination = destination;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void setDefaults() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public void incrementFailedAttempts() {
        failedAttempts++;
    }

    public void markVerified(Instant now) {
        verifiedAt = now;
    }

}
