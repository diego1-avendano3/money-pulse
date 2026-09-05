package com.moneypulse.transactionprocessor.transaction;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@link Topics#TRANSACTIONS_RECEIVED}. Deliberately empty: mapping
 * the raw JSON to {@code TransactionEvent} and persisting it via R2DBC (with
 * {@code .block()}, spec §4.6 Option A) land in tasks 8 and 9.
 */
@Component
class TransactionEventListener {

    @KafkaListener(topics = Topics.TRANSACTIONS_RECEIVED)
    void onMessage(String payload) {
        // Intentionally empty (spec §5 task 6).
    }
}
