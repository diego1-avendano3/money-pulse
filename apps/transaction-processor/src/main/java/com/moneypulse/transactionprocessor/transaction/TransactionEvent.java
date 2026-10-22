package com.moneypulse.transactionprocessor.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload consumed from the {@code transactions.received} Kafka topic (spec §4.2).
 *
 * <p>Duplicated from {@code transaction-api}, not shared (spec §4.7): the contract
 * is the topic, not a shared class.
 */
public record TransactionEvent(
        UUID id,
        String accountId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency,
        String merchant,
        Instant occurredAt,
        Instant receivedAt) {

    public TransactionEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(merchant, "merchant");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
