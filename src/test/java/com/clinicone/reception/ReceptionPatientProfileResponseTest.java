package com.clinicone.reception;

import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.patientprofile.PatientProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReceptionPatientProfileResponseTest {
    @Test
    void includesDemographicAndStructuredAddressFieldsUsedByTheReceptionForm() {
        PatientAccount owner = new PatientAccount("0862764830", "hash", "Nguyễn An", AccountStatus.ACTIVE, true);
        PatientProfile profile = PatientProfile.create(owner, "Nguyễn An", "Bản thân",
                LocalDate.of(1990, 4, 12), "Nữ", "0862764830", "079199000001", "Việt Nam", "Kinh",
                "12 Lê Lợi, Bến Nghé, Quận 1, TP. Hồ Chí Minh", "79", "TP. Hồ Chí Minh", "760", "Quận 1",
                "26734", "Bến Nghé", "12 Lê Lợi", true);

        ReceptionPatientProfileResponse response = ReceptionPatientProfileResponse.from(profile);

        assertThat(response.identityNumber()).isEqualTo("079199000001");
        assertThat(response.nationality()).isEqualTo("Việt Nam");
        assertThat(response.ethnicity()).isEqualTo("Kinh");
        assertThat(response.address()).isEqualTo("12 Lê Lợi, Bến Nghé, Quận 1, TP. Hồ Chí Minh");
        assertThat(response.provinceCode()).isEqualTo("79");
        assertThat(response.provinceName()).isEqualTo("TP. Hồ Chí Minh");
        assertThat(response.districtCode()).isEqualTo("760");
        assertThat(response.districtName()).isEqualTo("Quận 1");
        assertThat(response.wardCode()).isEqualTo("26734");
        assertThat(response.wardName()).isEqualTo("Bến Nghé");
        assertThat(response.streetAddress()).isEqualTo("12 Lê Lợi");
    }
}
