package com.moneypulse.transactionapi.transaction;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

interface TransactionRepository extends ReactiveCrudRepository<TransactionView, UUID> {

    Flux<TransactionView> findTop50ByOrderByReceivedAtDesc();
}
