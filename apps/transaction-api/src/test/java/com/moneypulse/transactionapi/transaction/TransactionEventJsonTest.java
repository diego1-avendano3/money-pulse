package com.moneypulse.transactionapi.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

/**
 * Locks down the {@code transactions.received} wire format (spec §4.2) against the
 * Spring-configured ObjectMapper.
 */
@JsonTest
class TransactionEventJsonTest {

    private static final UUID ID = UUID.fromString("0b9c1f2e-3d4a-4b5c-8d6e-7f8091a2b3c4");

    @Autowired
    private JacksonTester<TransactionEvent> json;

    private static TransactionEvent sampleEvent() {
        return new TransactionEvent(
                ID,
                "acc-001",
                new BigDecimal("-42000.50"),
                "COP",
                "Éxito",
                Instant.parse("2026-09-03T14:00:00Z"),
                Instant.parse("2026-09-03T14:00:03.512Z"));
    }

    private static final String CANONICAL_JSON =
            """
            {
              "id": "0b9c1f2e-3d4a-4b5c-8d6e-7f8091a2b3c4",
              "accountId": "acc-001",
              "amount": "-42000.50",
              "currency": "COP",
              "merchant": "Éxito",
              "occurredAt": "2026-09-03T14:00:00Z",
              "receivedAt": "2026-09-03T14:00:03.512Z"
            }
            """;

    @Test
    void serializesToTheExpectedWireFormat() throws Exception {
        var written = json.write(sampleEvent());

        assertThat(written).extractingJsonPathStringValue("$.id")
                .isEqualTo("0b9c1f2e-3d4a-4b5c-8d6e-7f8091a2b3c4");
        // amount is a JSON string, exact, plain (no scientific notation)
        assertThat(written).extractingJsonPathStringValue("$.amount").isEqualTo("-42000.50");
        assertThat(written).extractingJsonPathValue("$.amount").isInstanceOf(String.class);
        // timestamps are RFC-3339 UTC strings, not epoch numbers
        assertThat(written).extractingJsonPathStringValue("$.occurredAt").isEqualTo("2026-09-03T14:00:00Z");
        assertThat(written).extractingJsonPathStringValue("$.receivedAt").isEqualTo("2026-09-03T14:00:03.512Z");
    }

    @Test
    void largeAmountStaysPlain() throws Exception {
        var event = new TransactionEvent(ID, "acc-001", new BigDecimal("1000000.00"), "COP", "Payroll",
                Instant.parse("2026-09-03T14:00:00Z"), Instant.parse("2026-09-03T14:00:00Z"));

        assertThat(json.write(event)).extractingJsonPathStringValue("$.amount").isEqualTo("1000000.00");
    }

    @Test
    void deserializesFromCanonicalJson() throws Exception {
        assertThat(json.parseObject(CANONICAL_JSON)).isEqualTo(sampleEvent());
    }

    @Test
    void roundTrips() throws Exception {
        TransactionEvent original = sampleEvent();
        assertThat(json.parseObject(json.write(original).getJson())).isEqualTo(original);
    }

    @Test
    void rejectsMissingField() {
        String missingMerchant =
                """
                {
                  "id": "0b9c1f2e-3d4a-4b5c-8d6e-7f8091a2b3c4",
                  "accountId": "acc-001",
                  "amount": "-42000.50",
                  "currency": "COP",
                  "occurredAt": "2026-09-03T14:00:00Z",
                  "receivedAt": "2026-09-03T14:00:03.512Z"
                }
                """;

        assertThatThrownBy(() -> json.parseObject(missingMerchant))
                .hasRootCauseInstanceOf(NullPointerException.class);
    }
}
