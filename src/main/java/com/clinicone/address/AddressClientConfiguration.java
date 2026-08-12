package com.clinicone.address;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AddressClientConfiguration {

    @Bean
    RestClient.Builder addressRestClientBuilder() {
        return RestClient.builder();
    }
}
