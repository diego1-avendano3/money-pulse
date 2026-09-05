package com.moneypulse.transactionprocessor.transaction;

/**
 * Kafka topic names consumed by this service (spec §4.1). Duplicated from
 * transaction-api on purpose (spec §4.7): the contract is the topic, not a
 * shared class.
 */
public final class Topics {

    public static final String TRANSACTIONS_RECEIVED = "transactions.received";

    private Topics() {
    }
}
