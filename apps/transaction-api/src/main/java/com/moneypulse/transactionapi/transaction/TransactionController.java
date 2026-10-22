package com.moneypulse.transactionapi.transaction;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/transactions")
class TransactionController {

    private final TransactionPublisher publisher;
    private final TransactionRepository repository;

    TransactionController(TransactionPublisher publisher, TransactionRepository repository) {
        this.publisher = publisher;
        this.repository = repository;
    }

    @PostMapping
    Mono<ResponseEntity<TransactionAccepted>> create(@Valid @RequestBody CreateTransactionRequest request) {
        return publisher.publish(request)
                .map(id -> ResponseEntity.accepted().body(new TransactionAccepted(id)));
    }

    /**
     * Reads from Postgres (spec outcome 3), never from Kafka or an in-memory
     * cache. An empty table serializes as {@code 200 []} with no extra handling.
     */
    @GetMapping
    Flux<TransactionView> list() {
        return repository.findTop50ByOrderByReceivedAtDesc();
    }
}
