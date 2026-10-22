package com.moneypulse.transactionprocessor.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity for the {@code transactions} table (migration in task 7). Column
 * names are resolved by Spring Data Relational's default camelCase -> snake_case
 * convention, already matching {@code V1__create_transactions.sql}.
 */
@Table("transactions")
public record TransactionRow(
        @Id UUID id,
        String accountId,
        BigDecimal amount,
        String currency,
        String merchant,
        Instant occurredAt,
        Instant receivedAt,
        Instant persistedAt) {
}
