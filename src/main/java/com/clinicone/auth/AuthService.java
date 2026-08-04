package com.clinicone.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final PatientAccountRepository accountRepository;

    public AuthService(PatientAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public CheckPhoneResponse checkPhone(String phone) {
        return new CheckPhoneResponse(accountRepository.existsByPhone(phone.trim()));
    }
}
