plugins {
    java
    alias(libs.plugins.spring.boot)
}

description = "Pulse Agent: hand-rolled agentic tool-use loop over the Messages API"

// Same reasoning as transaction-processor: avoids the ambiguous "-plain.jar"
// once a Dockerfile (task 2) copies build/libs/*.jar.
tasks.jar { enabled = false }

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    // Servlet (Tomcat), not WebFlux: the agentic loop is intentionally blocking
    // in AI-1 (spec §4.3, same reasoning as transaction-processor's §4.6 Option A).
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Same two-dependency tracing requirement as the other two services (Boot 4
    // split this out of starter-actuator).
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
