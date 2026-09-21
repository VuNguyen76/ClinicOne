package com.clinicone.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private final PatientAccountRepository accountRepository;

    public CheckPhoneResponse checkPhone(String phone) {
        return new CheckPhoneResponse(accountRepository.existsByPhone(phone.trim()));
    }
}
