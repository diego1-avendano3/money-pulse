# Money Pulse

Real-time personal finance app built as a learning lab: bank transactions
(simulated) stream through Kafka, get categorized, and trigger real-time spend
alerts. Event-driven, full-stack, cloud-native — used to demonstrate production
qualities (scalability, resilience, security, observability, concurrency).

**Status:** Phase 0 — walking skeleton (scaffolding).

## Where things are

| Path | What |
|---|---|
| [`docs/PROJECT.md`](docs/PROJECT.md) | Project brief — the lab's source of truth |
| [`specs/phase-0.md`](specs/phase-0.md) | Current phase spec (SDD) |
| [`CLAUDE.md`](CLAUDE.md) | Working agreement and conventions |
| `apps/transaction-api/` | Spring WebFlux service (produces to Kafka, reads DB) |
| `apps/transaction-processor/` | Spring Kafka consumer (persists to DB) |
| `apps/web/` | React + TypeScript PWA |
| `docs/architecture/` | C4 diagrams |

## Build

Requires JDK 21 (a bootstrap JVM); Gradle is provided via the wrapper.

```bash
./gradlew build
```

The full run-and-verify guide lands with §5 task 16 of the phase-0 spec.
