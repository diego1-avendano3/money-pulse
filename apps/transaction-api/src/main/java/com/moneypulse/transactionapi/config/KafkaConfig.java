package com.moneypulse.transactionapi.config;

import com.moneypulse.transactionapi.transaction.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
class KafkaConfig {

    /**
     * Declared here so the auto-configured {@link KafkaAdmin} creates it on startup
     * (spec §4.1): 1 partition, 1 replica (single broker in Phase 0). Pre-creating
     * the topic keeps producer metadata warm, which is what bounds
     * {@code max.block.ms} to a rare edge case instead of the common path.
     */
    @Bean
    NewTopic transactionsReceived() {
        return TopicBuilder.name(Topics.TRANSACTIONS_RECEIVED)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
