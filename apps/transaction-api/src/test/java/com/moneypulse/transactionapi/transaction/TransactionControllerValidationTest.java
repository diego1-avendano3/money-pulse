package com.moneypulse.transactionapi.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** Proves untrusted input is rejected before it ever reaches the publisher (spec §5 task 5). */
@WebFluxTest(TransactionController.class)
class TransactionControllerValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TransactionPublisher publisher;

    @Test
    void validRequestIsAcceptedAndReturnsTheGeneratedId() {
        UUID id = UUID.randomUUID();
        when(publisher.publish(any())).thenReturn(Mono.just(id));

        webTestClient.post().uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"accountId":"acc-1","amount":"-100.00","currency":"COP","merchant":"Shop","occurredAt":"2026-09-05T09:00:00Z"}
                        """)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody().jsonPath("$.id").isEqualTo(id.toString());
    }

    @Test
    void blankAccountIdIsRejected() {
        webTestClient.post().uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"accountId":"","amount":"-100.00","currency":"COP","merchant":"Shop","occurredAt":"2026-09-05T09:00:00Z"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void lowercaseCurrencyIsRejected() {
        webTestClient.post().uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"accountId":"acc-1","amount":"-100.00","currency":"cop","merchant":"Shop","occurredAt":"2026-09-05T09:00:00Z"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void missingAmountIsRejected() {
        webTestClient.post().uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"accountId":"acc-1","currency":"COP","merchant":"Shop","occurredAt":"2026-09-05T09:00:00Z"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void futureOccurredAtIsRejected() {
        webTestClient.post().uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"accountId":"acc-1","amount":"-100.00","currency":"COP","merchant":"Shop","occurredAt":"2999-01-01T00:00:00Z"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void malformedJsonIsRejected() {
        webTestClient.post().uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{not json")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
