package com.fitt.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// The time source for code whose answer depends on the current time, such as
// the codes of an authenticator app. A bean so a test can put its own clock in.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
