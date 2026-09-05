plugins {
    java
    alias(libs.plugins.spring.boot)
}

description = "Kafka consumer: persists received transactions to the store"

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    // Servlet (Tomcat), not WebFlux: this service has no reactive HTTP path of its
    // own, only /actuator/health. WebFlux here would be reactive costume with no
    // reactive behavior (spec §3.2) -- its execution model is intentionally
    // blocking (@KafkaListener + .block() on R2DBC, per §4.6 Option A).
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Same two-dependency tracing requirement as transaction-api (Boot 4 split
    // this out of starter-actuator):
    //  - spring-boot-micrometer-tracing-opentelemetry: Boot autoconfig, creates the Tracer
    //  - micrometer-tracing-bridge-otel: the SLF4J listener that pushes traceId/spanId
    //    into the MDC the log pattern reads (spec §3.3)
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
