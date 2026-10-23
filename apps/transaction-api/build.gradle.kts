plugins {
    java
    alias(libs.plugins.spring.boot)
}

description = "Reactive API: accepts transactions, publishes to Kafka, reads the store"

// Only the boot jar is packaged (task 10's Dockerfile copies build/libs/*.jar);
// without this, Gradle also produces a "-plain.jar" and the copy is ambiguous.
tasks.jar { enabled = false }

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // KafkaTemplate + observation-enabled: ReactiveKafkaProducerTemplate was
    // removed in Spring for Apache Kafka 4.x, and reactor-kafka (its dependency)
    // is out of the Boot 4 BOM and in maintenance mode (spec §3.1).
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Read side of the CQRS-lite split (spec §4.3): the API reads transactions
    // via R2DBC, never writes the table. org.postgresql:r2dbc-postgresql is the
    // current coordinate -- io.r2dbc:r2dbc-postgresql no longer resolves (task 8).
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    runtimeOnly("org.postgresql:r2dbc-postgresql")

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
    // Boot 4 split @WebFluxTest out of spring-boot-test-autoconfigure into its own module.
    testImplementation("org.springframework.boot:spring-boot-webflux-test")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // spring-boot-dependencies does not manage org.testcontainers:* versions in
    // Boot 4.1 (only its own spring-boot-testcontainers module) -- unlike Boot 3.x.
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.3"))
    // Testcontainers 2.x renamed these artifacts -- "junit-jupiter"/"kafka"/
    // "postgresql" (the 1.x names) no longer exist; the bom only has entries
    // for the "testcontainers-"-prefixed 2.x names.
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // R2DBCDatabaseContainer -- Boot's @ServiceConnection needs this to wire an
    // R2DBC connection (separate from the JDBC-oriented testcontainers-postgresql).
    testImplementation("org.testcontainers:testcontainers-r2dbc")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}
