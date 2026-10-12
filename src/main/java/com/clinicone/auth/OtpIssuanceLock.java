package com.clinicone.auth;

public interface OtpIssuanceLock {
    void lock(String destination);
}
