package com.clinicone.reception;

public interface TemporaryProfileCreationLock {
    void lock(String normalizedPhone);
}
