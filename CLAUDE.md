# CLAUDE.md — Money Pulse

Real-time personal finance app. Learning lab to prepare a Senior Engineer (Slalom Build) role.
Full context: `docs/PROJECT.md`. Current phase spec: `specs/phase-0.md`.

## Who I'm working with
Solution Architect, 20+ yrs (banking, insurance, retail). Senior in Java/Spring/AWS/event-driven.
**New to React** — go deep and teach best practices on the frontend; treat backend as senior-level.

## What this project is
Event-driven finance app: bank transactions (simulated via a synthetic generator) stream through
Kafka, get categorized, and trigger real-time spend alerts. Built to demonstrate production qualities
(scalability, resilience, security, observability, concurrency) on a full-stack + cloud-native stack.

## Stack
- Java 21, Spring Boot 4.1+, Gradle (Kotlin DSL)
- transaction-api: Spring WebFlux + R2DBC (fully reactive, no blocking calls)
- transaction-processor: Spring MVC (Servlet, actuator only) + blocking `@KafkaListener` + R2DBC as persistence driver only (`.block()` allowed here — intentional, see spec §3.2/§4.6)
- web: Vite + React 18 + TypeScript (PWA)
- Kafka (KRaft), PostgreSQL, Docker Compose `compose.yaml` (local)
- AWS (Free plan): serverless always-on + ephemeral EKS/MSK labs, DynamoDB + RDS PostgreSQL, Terraform. See `docs/aws/AWS-PLAN.md`.
- **Cost guardrail (hard rule):** never propose NAT Gateway, interface VPC endpoints, Secrets Manager, customer-managed KMS keys, MSK Serverless, AWS Organizations/IAM Identity Center, or long-running EKS/ALB. Every AWS resource is in Terraform, tagged, and labs end with `destroy`. Check `docs/aws/AWS-PLAN.md` §1.3 before adding any AWS resource.
- Tracing: Micrometer Tracing + OpenTelemetry bridge (enabled from commit 1)

## Repo layout
```
apps/transaction-api/        Spring WebFlux service (produces to Kafka, reads DB)
apps/transaction-processor/  Spring Kafka consumer (persists to DB)
apps/web/                    React + TS PWA
compose.yaml                 Local infra: Kafka (KRaft), PostgreSQL, kafka-ui (dev)
specs/                       SDD specs — the source of truth
docs/                        PROJECT.md, phase specs, architecture (C4)
```

## Commands
- Run everything: `docker compose up --build`
- Web dev: `cd apps/web && npm run dev`
- API/processor tests: `./gradlew test` (in each module)
- Trace a request across services: `docker compose logs transaction-api transaction-processor | grep <traceId>`

## Conventions
- Reactive end-to-end applies to the **API path only** (`transaction-api`, WebFlux, no blocking calls in handlers). `transaction-processor` is intentionally not reactive in its execution model — Servlet actuator, blocking `@KafkaListener`, R2DBC used only as a driver. Don't "fix" that into WebFlux; it would be reactive costume with no reactive behavior.
- Every service exposes `/actuator/health`.
- Log pattern includes `traceId`/`spanId` (MDC) — never remove it.
- Kafka: `observation-enabled: true` on template and listener (keeps trace context flowing).
- Tests: each feature ships with unit + integration tests (Testcontainers for Kafka + Postgres). Not optional.
- Conventional commits.
- Trunk-based: commit straight to `main`, one atomic commit per spec task. No per-task or per-feature branches — this is a solo learning lab. Branch only for a genuinely risky spike that shouldn't touch `main` until proven.

## How we work: Spec-Driven Development
The spec is the source of truth. Code is derived from it. This is a learning goal — explain the paradigm as we go.
1. Every feature/phase starts as a spec in `specs/`, with the six elements:
   outcomes · scope boundaries · constraints · prior decisions · task breakdown · verification criteria.
2. **Use plan mode**: explore and show the plan before writing any code. Wait for approval.
3. Derive code from the spec. Keep it aligned with architecture and the verification criteria.
4. When requirements change, **edit the spec first**, then regenerate the affected code — don't patch blindly.
5. After producing a spec/plan, briefly explain *why* it's structured that way, and critique my specs when I write them.

## Always
- Keep changes scoped to the current phase (see `docs/PROJECT.md` §7 roadmap). Don't pull future-phase concerns in early.
- Prefer teaching over doing on the React side: show the idiomatic pattern and the reasoning, not just the output.
- Flag when something in the spec is ambiguous instead of guessing.
