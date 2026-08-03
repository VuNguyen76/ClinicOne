package com.clinicone.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public CheckPhoneResponse checkPhone(String phone) {
        // Account lookup and OTP issuance will be added when the patient model is introduced.
        return new CheckPhoneResponse("OTP");
    }
}
