package com.clinicone.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {
    private static final Logger log = LoggerFactory.getLogger(TimeZoneConfig.class);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @PostConstruct
    public void forceClinicTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(CLINIC_ZONE));
        log.info("JVM default timezone forced to {}", CLINIC_ZONE);
    }
}