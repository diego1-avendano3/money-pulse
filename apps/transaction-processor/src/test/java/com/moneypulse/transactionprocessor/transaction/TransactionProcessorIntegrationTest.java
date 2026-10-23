package com.moneypulse.transactionprocessor.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testcontainers integration test (spec §5 task 14): real Kafka and
 * Postgres, proving the listener's actual end-to-end behavior that
 * {@link TransactionEventListenerTest}'s mocked unit test can't -- that a
 * message landing on the topic really gets persisted, and that doing so
 * logs with a traceId (spec V6's "grep both services' logs" depends on this
 * listener actually emitting a line, not just propagating the MDC silently
 * -- see the task-10 fix, commit 493129f).
 *
 * <p>Unlike {@code TransactionApiIntegrationTest} (task 13), the schema
 * here is <em>not</em> duplicated: this service owns the Flyway migration
 * (spec §4.4), so the real {@code V1__create_transactions.sql} runs against
 * the Testcontainers Postgres, the same migration used in production.
 */
@SpringBootTest
@Testcontainers
class TransactionProcessorIntegrationTest {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void consumesTheEventPersistsItAndLogsWithATraceId() {
        Logger logger = (Logger) LoggerFactory.getLogger(TransactionEventListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            UUID id = UUID.randomUUID();
            TransactionEvent event = new TransactionEvent(
                    id,
                    "acc-1",
                    new BigDecimal("-100.00"),
                    "COP",
                    "Shop",
                    Instant.parse("2026-09-05T09:00:00Z"),
                    Instant.parse("2026-09-05T09:00:01Z"));
            String payload = jsonMapper.writeValueAsString(event);

            Map<String, Object> producerProps = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
            try (KafkaProducer<String, String> producer =
                    new KafkaProducer<>(producerProps, new StringSerializer(), new StringSerializer())) {
                producer.send(new ProducerRecord<>(Topics.TRANSACTIONS_RECEIVED, payload)).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                String merchant = databaseClient.sql("SELECT merchant FROM transactions WHERE id = :id")
                        .bind("id", id)
                        .map(row -> row.get("merchant", String.class))
                        .one()
                        .block();
                assertThat(merchant).isEqualTo("Shop");
            });

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(appender.list).anySatisfy(logEvent -> {
                assertThat(logEvent.getFormattedMessage()).contains("Persisted transaction " + id);
                assertThat(logEvent.getMDCPropertyMap()).containsKey("traceId");
                assertThat(logEvent.getMDCPropertyMap().get("traceId")).isNotBlank();
            }));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
