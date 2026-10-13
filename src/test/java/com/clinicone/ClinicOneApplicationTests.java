package com.clinicone;

import com.clinicone.auth.OtpIssuanceLock;
import com.clinicone.queue.QueueTicketRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ClinicOneApplicationTests {
	@Autowired
	private OtpIssuanceLock otpIssuanceLock;
	@Autowired
	private QueueTicketRepository queueTicketRepository;
	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
		Assertions.assertEquals("InMemoryOtpIssuanceLock", otpIssuanceLock.getClass().getSimpleName());
	}

	@Test
	void readinessProbeIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk());
	}

	@Test
	void livenessProbeIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk());
	}

	@Test
	void healthDetailsRemainProtected() throws Exception {
		mockMvc.perform(get("/actuator/health/db"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@Transactional
	void queueTicketCanBeLoadedWithWriteLock() {
		Assertions.assertTrue(queueTicketRepository.findByIdForUpdate(UUID.randomUUID()).isEmpty());
	}

}
