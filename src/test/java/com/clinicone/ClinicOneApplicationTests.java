package com.clinicone;

import com.clinicone.auth.OtpIssuanceLock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ActiveProfiles("test")
class ClinicOneApplicationTests {
	@Autowired
	private OtpIssuanceLock otpIssuanceLock;

	@Test
	void contextLoads() {
		Assertions.assertEquals("InMemoryOtpIssuanceLock", otpIssuanceLock.getClass().getSimpleName());
	}

}
