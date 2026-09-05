package com.moneypulse.transactionprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
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
