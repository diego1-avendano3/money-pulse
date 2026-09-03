plugins {
    java
    alias(libs.plugins.spring.boot)
}

description = "Reactive API: accepts transactions, publishes to Kafka, reads the store"

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Tracing over OpenTelemetry (Boot 4 split this out of starter-actuator):
    //  - spring-boot-micrometer-tracing-opentelemetry: Boot autoconfig, creates the Tracer
    //  - micrometer-tracing-bridge-otel: the OTel bridge + the SLF4J listener that
    //    pushes traceId/spanId into the MDC the log pattern reads (spec §3.3)
    // Neither pulls an OTLP exporter, so spans are created but not shipped anywhere
    // in Phase 0 — exporting to a backend is Phase 5.
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
