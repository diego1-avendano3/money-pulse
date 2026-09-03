package com.moneypulse.transactionapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransactionApiApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void tracingBridgeIsWired(@Autowired Tracer tracer) {
        // The Micrometer Tracing -> OpenTelemetry bridge must produce a usable Tracer,
        // otherwise no spans are created (spec §3.3).
        assertThat(tracer).isNotNull();
    }

    @Test
    void spanContextReachesTheLogMdc(@Autowired Tracer tracer) {
        // Proves the correlation pattern [app,traceId,spanId] will actually be
        // populated: entering a span's scope must push traceId/spanId into SLF4J's
        // MDC, which is what the log pattern reads (spec §3.3).
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
