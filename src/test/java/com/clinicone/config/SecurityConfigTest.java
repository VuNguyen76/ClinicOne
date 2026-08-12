package com.clinicone.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {
    @Test
    void defaultUserDetailsServiceFailsClosedInsteadOfCreatingADevelopmentUser() {
        assertThatThrownBy(() -> new SecurityConfig().userDetailsService().loadUserByUsername("any-user"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
