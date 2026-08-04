package com.clinicone.auth;

public interface OtpSender {
    void send(String destination, OtpPurpose purpose, String code);
}
