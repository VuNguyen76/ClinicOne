package com.clinicone.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StaffManagementService {
    private final StaffAccountRepository accountRepository;
    private final LoginSessionRepository sessionRepository;
    private final Clock clock;

    public StaffManagementService(StaffAccountRepository accountRepository,
                                  LoginSessionRepository sessionRepository,
                                  Clock clock) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StaffAccountResponse> list() {
        return accountRepository.findAllByOrderByFullNameAsc().stream()
                .map(StaffAccountResponse::from)
                .toList();
    }

    @Transactional
    public StaffAccountResponse lock(UUID staffId, String actor) {
        StaffAccount account = find(staffId);
        account.lock();
        StaffAccount saved = accountRepository.save(account);
        sessionRepository.revokeActiveByAccountId(staffId, Instant.now(clock));
        return StaffAccountResponse.from(saved);
    }

    @Transactional
    public StaffAccountResponse unlock(UUID staffId, String actor) {
        StaffAccount account = find(staffId);
        account.unlock();
        return StaffAccountResponse.from(accountRepository.save(account));
    }

    private StaffAccount find(UUID staffId) {
        return accountRepository.findById(staffId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "STAFF_ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản nhân viên."));
    }
}
