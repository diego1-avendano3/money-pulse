package com.moneypulse.transactionapi.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload published to the {@code transactions.received} Kafka topic (spec §4.2).
 *
 * <p>Immutable, and every field is mandatory: the compact constructor rejects a
 * {@code null}, so a malformed message fails fast and loud rather than flowing
 * through as corrupt data.
 *
 * <p>Wire format: {@code amount} is a JSON <em>string</em> with an exact decimal
 * (never a JSON number — that would lose scale/precision through a JS consumer),
 * and both timestamps are RFC-3339 UTC strings.
 *
 * <p>This record is duplicated in {@code transaction-processor}, not shared
 * (spec §4.7): the contract is the topic, not a shared class.
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
