package com.moneypulse.transactionapi.transaction;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps a validated {@link CreateTransactionRequest} to the {@link TransactionEvent}
 * wire contract and publishes it to {@link Topics#TRANSACTIONS_RECEIVED}.
 */
@Component
class TransactionPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    TransactionPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper, Clock clock) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    Mono<UUID> publish(CreateTransactionRequest request) {
        TransactionEvent event = new TransactionEvent(
                UUID.randomUUID(),
                request.accountId(),
                request.amount(),
                request.currency(),
                request.merchant(),
                request.occurredAt(),
                clock.instant());

        // No key in Phase 0: with a single partition (spec §4.1) it has no effect
        // on ordering. Phase 6 revisits the key strategy (likely accountId).
        // Without a log line here, nothing observes the traceId this request
        // carries (context-propagation:auto puts it in the MDC, but no log call
        // means it's never surfaced) -- spec V6 greps both services' logs for
        // the same traceId.
        return Mono.fromCallable(() -> jsonMapper.writeValueAsString(event))
                .flatMap(payload -> Mono.fromFuture(() -> kafkaTemplate.send(Topics.TRANSACTIONS_RECEIVED, payload)))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(event.id())
                .doOnNext(id -> log.info("Published transaction {}", id));
    }
}
