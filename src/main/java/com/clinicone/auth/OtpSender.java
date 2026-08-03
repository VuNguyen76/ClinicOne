package com.clinicone.auth;

public interface OtpSender {
    void send(String email, OtpPurpose purpose, String code);
}
