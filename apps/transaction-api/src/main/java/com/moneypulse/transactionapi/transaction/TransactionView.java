package com.moneypulse.transactionapi.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Read-only R2DBC view of a row in {@code transactions} (owned and written by
 * {@code transaction-processor}; this service never writes it, spec §4.3).
 * Doubles as the {@code GET /transactions} response DTO, same pragmatic style
 * as {@code TransactionEvent} -- no separate mapping layer for an identical
 * shape. {@code persistedAt} is deliberately left out: it is the processor's
 * internal bookkeeping, not part of the read contract.
 */
@Table("transactions")
public record TransactionView(
        @Id UUID id,
        String accountId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency,
        String merchant,
        Instant occurredAt,
        Instant receivedAt) {
}
