package com.moneypulse.transactionapi.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Untrusted input for {@code POST /transactions}. Deliberately separate from
 * {@link TransactionEvent}: this is what a client may send (no {@code id}, no
 * {@code receivedAt} — the server sets those), and it carries the Bean
 * Validation constraints that the wire contract does not need.
 */
public record CreateTransactionRequest(
        @NotBlank String accountId,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO-4217 currency code") String currency,
        @NotBlank String merchant,
        @NotNull @PastOrPresent Instant occurredAt) {
}
