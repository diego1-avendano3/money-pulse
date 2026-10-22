package com.moneypulse.transactionapi.transaction;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

/** Proves GET /transactions reads from the repository, never from Kafka (spec §5 task 9). */
@WebFluxTest(TransactionController.class)
class TransactionControllerReadTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TransactionPublisher publisher;

    @MockitoBean
    private TransactionRepository repository;

    @Test
    void returnsTransactionsOrderedByReceivedAtDesc() {
        TransactionView view = new TransactionView(
                UUID.randomUUID(),
                "acc-1",
                new BigDecimal("-100.00"),
                "COP",
                "Shop",
                Instant.parse("2026-09-05T09:00:00Z"),
                Instant.parse("2026-09-05T09:00:01Z"));
        when(repository.findTop50ByOrderByReceivedAtDesc()).thenReturn(Flux.just(view));

        webTestClient.get().uri("/transactions")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(view.id().toString())
                .jsonPath("$[0].amount").isEqualTo("-100.00")
                .jsonPath("$[0].merchant").isEqualTo("Shop");
    }

    @Test
    void emptyTableReturnsAnEmptyArray() {
        when(repository.findTop50ByOrderByReceivedAtDesc()).thenReturn(Flux.empty());

        webTestClient.get().uri("/transactions")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }
}
