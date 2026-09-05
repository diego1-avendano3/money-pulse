package com.moneypulse.transactionapi.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit test for the DTO -> TransactionEvent mapping and the publish call, using
 * the real Spring-managed JsonMapper so the payload matches the wire format
 * locked down in TransactionEventJsonTest (spec §4.2).
 */
@JsonTest
class TransactionPublisherTest {

    @Autowired
    private JsonMapper jsonMapper;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC);

    private TransactionPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new TransactionPublisher(kafkaTemplate, jsonMapper, clock);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesTheMappedEventAndReturnsItsId() {
        when(kafkaTemplate.send(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        var request = new CreateTransactionRequest(
                "acc-1", new BigDecimal("-100.00"), "COP", "Shop", Instant.parse("2026-09-05T09:00:00Z"));

        UUID[] returnedId = new UUID[1];
        StepVerifier.create(publisher.publish(request))
                .assertNext(id -> returnedId[0] = id)
                .verifyComplete();

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(Topics.TRANSACTIONS_RECEIVED);

        TransactionEvent published = jsonMapper.readValue(payloadCaptor.getValue(), TransactionEvent.class);
        assertThat(published.id()).isEqualTo(returnedId[0]);
        assertThat(published.accountId()).isEqualTo("acc-1");
        assertThat(published.amount()).isEqualByComparingTo(new BigDecimal("-100.00"));
        assertThat(published.currency()).isEqualTo("COP");
        assertThat(published.merchant()).isEqualTo("Shop");
        assertThat(published.occurredAt()).isEqualTo(Instant.parse("2026-09-05T09:00:00Z"));
        assertThat(published.receivedAt()).isEqualTo(clock.instant());
    }
}
