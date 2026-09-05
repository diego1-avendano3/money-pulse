package com.moneypulse.transactionapi.transaction;

import java.util.UUID;

/** Response body for a successfully accepted {@code POST /transactions}. */
public record TransactionAccepted(UUID id) {
}
