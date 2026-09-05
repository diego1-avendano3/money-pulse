package com.moneypulse.transactionapi.transaction;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/transactions")
class TransactionController {

    private final TransactionPublisher publisher;

    TransactionController(TransactionPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping
    Mono<ResponseEntity<TransactionAccepted>> create(@Valid @RequestBody CreateTransactionRequest request) {
        return publisher.publish(request)
                .map(id -> ResponseEntity.accepted().body(new TransactionAccepted(id)));
    }
}
