package com.moneypulse.transactionprocessor.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit test for the JSON -> TransactionEvent -> TransactionRow mapping and the
 * persist call, using the real Spring-managed JsonMapper so the payload matches
 * the wire format locked down on the API side (spec §4.2). R2dbcEntityOperations
 * is mocked -- this does not touch a real database (that's task 14, Testcontainers).
 */
@JsonTest
class TransactionEventListenerTest {

    @Autowired
    private JsonMapper jsonMapper;

    private final R2dbcEntityOperations entityOperations = mock(R2dbcEntityOperations.class);

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

    private TransactionEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TransactionEventListener(entityOperations, jsonMapper, clock);
    }

    @Test
    void mapsTheConsumedEventAndPersistsIt() {
        TransactionRow anyRow = new TransactionRow(
                UUID.randomUUID(), "x", BigDecimal.ZERO, "COP", "x", clock.instant(), clock.instant(), clock.instant());
        when(entityOperations.insert(any(TransactionRow.class))).thenReturn(Mono.just(anyRow));

        TransactionEvent event = new TransactionEvent(
                UUID.randomUUID(),
                "acc-1",
                new BigDecimal("-100.00"),
                "COP",
                "Shop",
                Instant.parse("2026-09-05T09:00:00Z"),
                Instant.parse("2026-09-05T09:00:01Z"));
        String payload = jsonMapper.writeValueAsString(event);

        listener.onMessage(payload);

        ArgumentCaptor<TransactionRow> rowCaptor = ArgumentCaptor.forClass(TransactionRow.class);
        verify(entityOperations).insert(rowCaptor.capture());

        TransactionRow row = rowCaptor.getValue();
        assertThat(row.id()).isEqualTo(event.id());
        assertThat(row.accountId()).isEqualTo("acc-1");
        assertThat(row.amount()).isEqualByComparingTo(new BigDecimal("-100.00"));
        assertThat(row.currency()).isEqualTo("COP");
        assertThat(row.merchant()).isEqualTo("Shop");
        assertThat(row.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(row.receivedAt()).isEqualTo(event.receivedAt());
        assertThat(row.persistedAt()).isEqualTo(clock.instant());
        assertThat(row.persistedAt()).isNotEqualTo(row.receivedAt());
    }
}
