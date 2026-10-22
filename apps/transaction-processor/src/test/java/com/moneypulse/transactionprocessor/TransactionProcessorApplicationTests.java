package com.moneypulse.transactionprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// Flyway is disabled here: unlike the Kafka admin/listener (which connect
// lazily, see contextLoads() below), Flyway's migration run is eager and would
// fail this context-smoke test without a reachable Postgres. The real
// migration run against Testcontainers Postgres is covered by task 14.
@SpringBootTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
class TransactionProcessorApplicationTests {

    @Test
    void contextLoads() {
        // Must pass even with no broker reachable: the KafkaAdmin and the listener
        // container connect lazily in the background (fatalIfBrokerNotAvailable=false).
    }

    @Test
    void tracingBridgeIsWired(@Autowired Tracer tracer) {
        assertThat(tracer).isNotNull();
    }

    @Test
    void spanContextReachesTheLogMdc(@Autowired Tracer tracer) {
        Span span = tracer.nextSpan().name("mdc-check").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            assertThat(MDC.get("traceId")).isEqualTo(span.context().traceId());
            assertThat(MDC.get("spanId")).isEqualTo(span.context().spanId());
        } finally {
            span.end();
        }
        assertThat(MDC.get("traceId")).isNull();
    }
}
