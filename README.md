# Money Pulse

Real-time personal finance app built as a learning lab: bank transactions
(simulated) stream through Kafka, get categorized, and trigger real-time spend
alerts. Event-driven, full-stack, cloud-native — used to demonstrate production
qualities (scalability, resilience, security, observability, concurrency).

**Status:** Phase 0 — walking skeleton **complete and verified** (V1–V12, see
[`specs/phase-0.md`](specs/phase-0.md) §6 and §8). `docker compose up --build -d`
runs the full stack (web → API → Kafka → processor → Postgres → API) end to end.
Next: Phase 0.5 (AWS guardrails, see [`docs/aws/AWS-PLAN.md`](docs/aws/AWS-PLAN.md)).

## Where it's heading

Today this repo is a local walking skeleton. The destination is an event-driven
system on AWS that reacts in real time to every transaction — categorization
with generative AI (Bedrock), real security (mTLS, OAuth2/OIDC, field-level
encryption), its own data lake with a monthly email summary, and a measured
compute comparison (Lambda / ECS Fargate / EKS) — all under strict cost
guardrails (free tier, `terraform destroy` on every lab).

| Phase | Focus | Key AWS services |
|---|---|---|
| 0 | Walking skeleton (this repo, today) | — (local only) |
| 0.5 | Cost guardrails | Free plan, Budgets, Terraform bootstrap, OIDC |
| 1 | Full-stack slice + React | S3/CloudFront, Cognito, API Gateway HTTP, Lambda, RDS |
| 2 | Event-driven + concurrency | SQS, DynamoDB, EventBridge, SNS, **Bedrock** (categorization) |
| 3 | Resilience | DLQ/redrive, circuit breaker on Bedrock (UC5) |
| 4 | Security | mTLS, usage plans, WebSocket, **KMS**, **Secrets Manager** |
| 5 | Observability | X-Ray, CloudWatch, **CloudTrail** |
| 6 | Scalability + analytics practice | k6, **monthly summary** (Step Functions/Athena/**Bedrock**/SES), data lake |
| 7 | Production-readiness | EKS lab, **ECS Fargate lab** (compute comparison), k6 → N events/s |

Full detail: [`docs/PROJECT.md`](docs/PROJECT.md) (product and roadmap),
[`docs/aws/AWS-PLAN.md`](docs/aws/AWS-PLAN.md) (AWS plan and cost guardrails),
and the C4 diagrams: [target context](docs/architecture/c4-context.md) ·
[target containers in AWS](docs/architecture/c4-container-target.md).

## Where things are

| Path | What |
|---|---|
| [`docs/PROJECT.md`](docs/PROJECT.md) | Project brief — the lab's source of truth |
| [`specs/phase-0.md`](specs/phase-0.md) | Phase 0 spec (SDD) and its verification results |
| [`CLAUDE.md`](CLAUDE.md) | Working agreement and conventions |
| `apps/transaction-api/` | Spring WebFlux service (produces to Kafka, reads DB) |
| `apps/transaction-processor/` | Spring Kafka consumer (persists to DB) |
| `apps/web/` | React + TypeScript PWA |
| [`docs/architecture/`](docs/architecture/c4-context.md) | C4 diagrams ([context](docs/architecture/c4-context.md), [containers](docs/architecture/c4-container.md), [target containers in AWS](docs/architecture/c4-container-target.md)) |

Internal design docs (`PROJECT.md`, `specs/`, the AWS plan, C4 diagrams) are written in Spanish.

## Run it

Requires Docker Desktop running. Commands below are **PowerShell** (Windows),
run from the repo root.

```powershell
docker compose up --build -d
docker compose ps
```

First run takes a few minutes — it compiles both Java modules and the web
app inside Docker. `docker compose ps` should eventually show all 5
services `healthy`: `postgres`, `kafka`, `transaction-processor`,
`transaction-api`, `web`. (`kafka-ui` also comes up, as dev tooling — not
required by the skeleton.)

Then open **http://localhost:5173** for the web app, or hit the API
directly (see the checklist below).

> On Git Bash / macOS / Linux the equivalent `curl` commands work too; the
> checklist is written for PowerShell because that's what it was verified with.

## Verify (spec `phase-0.md` §6)

Every step below is a criterion from the phase-0 spec. They were run, in
PowerShell on Windows, against this exact stack. Run them in order.

### V1 — All 5 containers reach `healthy`

```powershell
docker compose ps
```

Expected: `healthy` for `web`, `transaction-api`, `transaction-processor`,
`kafka`, and `postgres`. Note: `docker compose ps` **only lists running
containers**; one that crashed simply won't show up (use `docker compose ps -a`
to see it).

### V2 — A valid `POST` is accepted

```powershell
$body = '{"accountId":"acc-1","amount":"-42000","currency":"COP","merchant":"Exito","occurredAt":"2026-09-02T14:00:00Z"}'
$r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri http://localhost:8080/transactions -ContentType 'application/json' -Body $body
$r.StatusCode   # 202
$r.Content      # {"id":"<uuid>"}
```

`Invoke-WebRequest` is used (not `Invoke-RestMethod`) because the latter
doesn't expose the status code. `amount` goes as a **string** (contract §4.2).

### V3 — The event is persisted

```powershell
docker compose exec postgres psql -U mp -d moneypulse -c 'select count(*) from transactions'
```

Expected: in steady state, the count increases in < 5 s after a `POST`. To
measure it:

```powershell
$antes = [int](docker compose exec -T postgres psql -U mp -d moneypulse -t -A -c 'select count(*) from transactions')
$sw = [Diagnostics.Stopwatch]::StartNew()
$null = Invoke-RestMethod -Method Post -Uri http://localhost:8080/transactions -ContentType 'application/json' -Body $body
do { $n = [int](docker compose exec -T postgres psql -U mp -d moneypulse -t -A -c 'select count(*) from transactions'); Start-Sleep -Milliseconds 100 } until ($n -gt $antes -or $sw.Elapsed.TotalSeconds -gt 10)
"count=$n (antes: $antes) en $([int]$sw.Elapsed.TotalMilliseconds) ms"
```

(the timing includes the cost of each `docker compose exec`, ~0.5–1 s: useful
for checking the 5 s threshold, not for measuring the 28 ms steady-state
figure). **The first event after the processor starts up is slower** (see
"Observed behavior"): send a warm-up event before marking V3 as failed.

### V4 — The API reads it back

```powershell
Invoke-RestMethod http://localhost:8080/transactions | Select-Object -First 1 | ConvertTo-Json
```

Expected: the list includes the created transaction, with `amount` as a string.

### V5 — An empty table doesn't break `GET`

This can only be checked with an empty database, i.e. right after a cold
start (see V12):

```powershell
$g = Invoke-WebRequest -UseBasicParsing http://localhost:8080/transactions
"$($g.StatusCode) $($g.Content)"   # 200 []
```

### V6 — The `traceId` travels end to end

```powershell
$id = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/transactions -ContentType 'application/json' -Body $body).id
Start-Sleep -Seconds 2
$linea = docker compose logs transaction-api | Select-String "Published transaction $id" | Select-Object -First 1
$traceId = [regex]::Match($linea.Line, '\[transaction-api,([0-9a-f]{32}),').Groups[1].Value
docker compose logs transaction-api transaction-processor | Select-String $traceId
```

Expected: ≥1 line from **each** service with the same `traceId`.

### V7 — Green health

```powershell
(Invoke-RestMethod http://localhost:8080/actuator/health).status
(docker compose exec -T transaction-processor curl -s http://localhost:8082/actuator/health | ConvertFrom-Json).status
```

Expected: `UP` for both. The processor doesn't publish a port to the host,
which is why it's queried from inside the compose network. (The full JSON also
includes `components`; what matters here is `status`.)

### V8 — The web app closes the loop

At http://localhost:5173, create a transaction using the form.

Expected: it appears in the list without reloading the page (the hook polls
every 3 s). To check that the web app's proxy reaches the API:

```powershell
(Invoke-RestMethod http://localhost:5173/transactions).Count
```

### V9 — Green tests with real infra

```powershell
.\gradlew.bat test --rerun-tasks
```

Requires Docker (Testcontainers spins up real Kafka and Postgres). **Use
`--rerun-tasks`**: without it, Gradle can report `UP-TO-DATE` without running
anything, and `BUILD SUCCESSFUL` proves nothing. Expected: `BUILD SUCCESSFUL`
with the integration tests of both modules passing.

### V10 — No blocking in the API's path

```powershell
Get-ChildItem apps\transaction-api\src\main -Recurse -Filter *.java | Select-String -Pattern '\.block\(\)|\.toFuture\(\)|JdbcTemplate'
```

Expected: no results.

### V11 — Architecture documented

Open [`docs/architecture/c4-context.md`](docs/architecture/c4-context.md) and
[`c4-container.md`](docs/architecture/c4-container.md): C4 level 1 and 2
diagrams, linked from `docs/PROJECT.md`.

### V12 — Correct startup order (cold start)

```powershell
docker compose down -v          # also deletes volumes: data is lost
docker compose up --build -d
docker compose ps               # wait until services are healthy
docker compose logs | Select-String 'relation ".*" does not exist'
```

Expected: no matches. Search for that exact pattern, not the generic
`does not exist`: on a cold start, two benign lines contain it —
`FATAL: database "moneypulse" does not exist` (Postgres's healthcheck probing
while the database initializes) and Flyway's INFO line
`Schema history table ... does not exist yet` (first migration).

## Observed behavior

Measurements and findings from Phase 0's manual verification (detail in
[`specs/phase-0.md`](specs/phase-0.md) §8):

- **API → Postgres latency.** The first event after the processor started up
  took **13.4 s** (cold start: consumer group join and Kafka metadata). In
  steady state, **28 ms**.
- **`receivedAt` loses precision on persist.** The event goes out with
  nanoseconds (9 decimal places); PostgreSQL `timestamptz` stores
  microseconds, so `GET` returns 6. Open debt, to be decided in Phase 1.
- **`amount` in `GET` is a JSON string**, not a number (e.g.
  `"-1500.50"`), same as in the event. Complies with §4.2; no open decision.
- **UI:** income and expenses look the same in the list (`amount currency`,
  with no visual distinction beyond the sign). Improvement for Phase 1.

## Troubleshooting

**Docker Desktop with no DNS** — `docker compose up --build` fails with
`lookup auth.docker.io: no such host` (or `dial tcp: lookup registry-1.docker.io`):
this is a networking issue in the Docker Desktop VM, not the project. Run
`wsl --shutdown` and reopen Docker Desktop.

**Docker's engine crashes from memory pressure** — free up RAM:

```powershell
.\gradlew.bat --stop              # old Gradle daemons still holding memory
docker compose stop kafka-ui      # dev tooling, not needed by the skeleton
```

**"A service disappeared from `docker compose ps`"** — `docker compose ps`
only lists **running** containers. If it's not there, it's stopped or
crashed: `docker compose ps -a` and `docker compose logs <service>`.

## Development loop

To iterate without rebuilding containers every time, see the commands in
[`CLAUDE.md`](CLAUDE.md#commands) (`./gradlew test` per module,
`cd apps/web && npm run dev`).

## Shutting down

```powershell
docker compose down        # keeps the data (volumes)
docker compose down -v     # cold start — deletes volumes too
```
