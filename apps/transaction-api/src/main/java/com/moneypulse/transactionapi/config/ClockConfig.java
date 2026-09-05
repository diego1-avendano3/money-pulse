package com.moneypulse.transactionapi.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfig {

    /** Injectable so {@code receivedAt} is deterministic in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
