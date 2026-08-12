package com.clinicone.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class SmsContentPolicyTest {
    private final SmsContentPolicy policy = new SmsContentPolicy();

    @Test
    void acceptsOperationalSummary() {
        assertThatCode(() -> policy.validate("ClinicOne: Lịch hẹn đã được ghi nhận. Mở ứng dụng để xem."))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSecretsAndClinicalDetails() {
        assertThatThrownBy(() -> policy.validate("ClinicOne: OTP 123456, password temporary"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("ClinicOne: diagnosis is ready"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
