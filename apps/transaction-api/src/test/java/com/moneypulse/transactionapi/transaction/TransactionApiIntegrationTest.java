package com.moneypulse.transactionapi.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * First Testcontainers integration test of the project (spec §5 task 13):
 * real Kafka and Postgres, not mocks, proving what the unit tests (task 5,
 * 9) can't -- that POST actually lands a message on the topic, and GET
 * actually reads rows back from the store.
 *
 * <p>The schema is created here, not via Flyway: transaction-api never
 * migrates, not even in tests (spec §4.4) -- it assumes the schema already
 * exists, same as in production once transaction-processor has run. The
 * CREATE TABLE below is duplicated from the processor's
 * V1__create_transactions.sql on purpose (spec §4.7: duplicate, don't
 * couple test setup to another module).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class TransactionApiIntegrationTest {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void ensureSchema() {
        // Blocking is fine here: this is test fixture setup, not the request
        // path spec §3.1 forbids .block() on.
        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id           UUID PRIMARY KEY,
                    account_id   TEXT NOT NULL,
                    amount       NUMERIC NOT NULL,
                    currency     VARCHAR(3) NOT NULL,
                    merchant     TEXT NOT NULL,
                    occurred_at  TIMESTAMPTZ NOT NULL,
                    received_at  TIMESTAMPTZ NOT NULL,
                    persisted_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """)
                .then()
                .block();
    }

    @Test
    void postPublishesTheEventToKafka() {
        TransactionAccepted accepted = webTestClient.post().uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"accountId":"acc-1","amount":"-100.00","currency":"COP","merchant":"Shop","occurredAt":"2026-09-05T09:00:00Z"}
                        """)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(TransactionAccepted.class)
                .returnResult()
                .getResponseBody();

        assertThat(accepted).isNotNull();

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), "test-group", true);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(consumerProps, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.TRANSACTIONS_RECEIVED));
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, Topics.TRANSACTIONS_RECEIVED, Duration.ofSeconds(10));
            assertThat(record.value()).contains(accepted.id().toString());
        }
    }

    @Test
    void getReadsSeededRowsFromPostgres() {
        UUID seededId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO transactions (id, account_id, amount, currency, merchant, occurred_at, received_at)
                VALUES (:id, :accountId, :amount, :currency, :merchant, :occurredAt, :receivedAt)
                """)
                .bind("id", seededId)
                .bind("accountId", "acc-seed")
                .bind("amount", new BigDecimal("-200.00"))
                .bind("currency", "COP")
                .bind("merchant", "Seeded Shop")
                .bind("occurredAt", Instant.parse("2026-09-05T09:00:00Z"))
                .bind("receivedAt", Instant.parse("2026-09-05T09:00:01Z"))
                .then()
                .block();

        List<TransactionView> transactions = webTestClient.get().uri("/transactions")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TransactionView.class)
                .returnResult()
                .getResponseBody();

        assertThat(transactions).anySatisfy(transaction -> {
            assertThat(transaction.id()).isEqualTo(seededId);
            assertThat(transaction.merchant()).isEqualTo("Seeded Shop");
            assertThat(transaction.amount()).isEqualByComparingTo(new BigDecimal("-200.00"));
        });
    }
}
