package com.clinicone.config;

import com.clinicone.auth.AccountStatus;
import com.clinicone.auth.PatientAccount;
import com.clinicone.auth.PatientAccountRepository;
import com.clinicone.patientprofile.PatientProfile;
import com.clinicone.patientprofile.PatientProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One-shot, opt-in seeder for synthetic patient accounts and profiles only. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.demo-data.seed-patients", havingValue = "true")
public class DemoPatientDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoPatientDataSeeder.class);
    private static final int PATIENT_COUNT = 100;
    private static final String PHONE_PREFIX = "0998";
    private static final String DEMO_NAME_PREFIX = "Bệnh nhân Demo ";

    private final PatientAccountRepository patientRepository;
    private final PatientProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final String patientPassword;
    private final String confirmedRemoteTarget;
    private final String datasourceUrl;

    public DemoPatientDataSeeder(PatientAccountRepository patientRepository,
                                 PatientProfileRepository profileRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${LOCAL_PATIENT_PASSWORD}") String patientPassword,
                                 @Value("${app.demo-data.confirmed-remote-target:}") String confirmedRemoteTarget,
                                 @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.patientRepository = patientRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.patientPassword = patientPassword;
        this.confirmedRemoteTarget = confirmedRemoteTarget;
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!LocalDataSafetyPolicy.isLocalDatabaseUrl(datasourceUrl)
                && !targetIdentity(datasourceUrl).equalsIgnoreCase(confirmedRemoteTarget.trim())) {
            throw new IllegalStateException("Demo patient seed requires an explicit exact host/database confirmation.");
        }

        List<PatientAccount> accounts = findAndValidateExistingDemoAccounts();
        int profilesCreated = 0;
        for (int index = 1; index <= PATIENT_COUNT; index++) {
            PatientAccount account = accounts.get(index - 1);
            String fullName = demoName(index);
            if (account == null) {
                String phone = demoPhone(index);
                account = patientRepository.save(new PatientAccount(phone, passwordEncoder.encode(patientPassword),
                        fullName, AccountStatus.ACTIVE, false));
            }

            if (profileRepository.findByOwnerIdAndActiveTrueOrderByPrimaryProfileDescCreatedAtAsc(account.getId())
                    .stream().noneMatch(profile -> fullName.equals(profile.getFullName()))) {
                profileRepository.save(createDemoProfile(account, index));
                profilesCreated++;
            }
        }

        log.info("Synthetic patient seed finished: {} demo accounts checked, {} profiles created.",
                PATIENT_COUNT, profilesCreated);
    }

    private List<PatientAccount> findAndValidateExistingDemoAccounts() {
        List<PatientAccount> accounts = new ArrayList<>(PATIENT_COUNT);
        List<String> conflicts = new ArrayList<>();
        for (int index = 1; index <= PATIENT_COUNT; index++) {
            PatientAccount account = patientRepository.findByPhone(demoPhone(index)).orElse(null);
            if (account != null && !demoName(index).equals(account.getFullName())) {
                conflicts.add(String.format(Locale.ROOT, "%04d", index));
            }
            accounts.add(account);
        }
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Demo phone range conflicts with existing accounts; no demo data was changed.");
        }
        return accounts;
    }

    private PatientProfile createDemoProfile(PatientAccount account, int index) {
        LocalDate birthDate = LocalDate.of(1975 + index % 35, 1 + index % 12, 1 + index % 28);
        String gender = index % 2 == 0 ? "Nữ" : "Nam";
        return PatientProfile.create(account, demoName(index), "Bản thân", birthDate, gender, demoPhone(index), null,
                "Việt Nam", "Kinh", "Địa chỉ dữ liệu kiểm thử, Thành phố Hồ Chí Minh",
                "79", "Thành phố Hồ Chí Minh", "760", "Quận 1", "26734", "Phường Bến Nghé",
                "Địa chỉ kiểm thử", true);
    }

    private String demoPhone(int index) {
        return PHONE_PREFIX + String.format(Locale.ROOT, "%06d", index);
    }

    private String demoName(int index) {
        return DEMO_NAME_PREFIX + String.format(Locale.ROOT, "%04d", index);
    }

    private String targetIdentity(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql://")) return "";
        URI uri = URI.create(url.substring("jdbc:".length()));
        return uri.getHost() + uri.getPath();
    }
}
