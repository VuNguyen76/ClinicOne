package com.clinicone.auth;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PatientAccountRepository accountRepository;

    public CheckPhoneResponse checkPhone(String phone) {
        return new CheckPhoneResponse(accountRepository.existsByPhone(phone.trim()));
    }
}
