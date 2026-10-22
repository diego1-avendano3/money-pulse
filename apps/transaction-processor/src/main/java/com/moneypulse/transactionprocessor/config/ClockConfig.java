package com.moneypulse.transactionprocessor.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfig {

    /** Injectable so {@code persistedAt} is deterministic in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
