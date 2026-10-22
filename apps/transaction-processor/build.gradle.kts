plugins {
    java
    alias(libs.plugins.spring.boot)
}

description = "Kafka consumer: persists received transactions to the store"

// Only the boot jar is packaged (task 10's Dockerfile copies build/libs/*.jar);
// without this, Gradle also produces a "-plain.jar" and the copy is ambiguous.
tasks.jar { enabled = false }

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    // Servlet (Tomcat), not WebFlux: this service has no reactive HTTP path of its
    // own, only /actuator/health. WebFlux here would be reactive costume with no
    // reactive behavior (spec §3.2) -- its execution model is intentionally
    // blocking (@KafkaListener + .block() on R2DBC, per §4.6 Option A).
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Schema ownership (spec §4.4): this service runs Flyway at startup, before
    // its readiness probe reports UP (§4.5). Flyway has no R2DBC support, so its
    // migration runner always speaks JDBC -- unrelated to the r2dbc-postgresql
    // driver task 8 adds for the service's actual (reactive) queries. Spring Boot
    // builds Flyway's one-off DataSource from spring.flyway.url/user/password, so
    // no spring-boot-starter-jdbc or DataSource bean is needed.
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    // The R2DBC Postgres driver moved coordinates from io.r2dbc:r2dbc-postgresql
    // to org.postgresql:r2dbc-postgresql (pgjdbc/r2dbc-postgresql); the old
    // group id no longer resolves a current version.
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    // Boot 4 split FlywayAutoConfiguration out of spring-boot-autoconfigure into its
    // own module (same pattern as the tracing/Kafka starters above) -- flyway-core on
    // the classpath alone does not trigger it.
    implementation("org.springframework.boot:spring-boot-flyway")

    // Same two-dependency tracing requirement as transaction-api (Boot 4 split
    // this out of starter-actuator):
    //  - spring-boot-micrometer-tracing-opentelemetry: Boot autoconfig, creates the Tracer
    //  - micrometer-tracing-bridge-otel: the SLF4J listener that pushes traceId/spanId
    //    into the MDC the log pattern reads (spec §3.3)
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
