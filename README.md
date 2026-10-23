# Money Pulse

Real-time personal finance app built as a learning lab: bank transactions
(simulated) stream through Kafka, get categorized, and trigger real-time spend
alerts. Event-driven, full-stack, cloud-native — used to demonstrate production
qualities (scalability, resilience, security, observability, concurrency).

**Status:** Phase 0 — walking skeleton **complete and verified** (V1–V12, see
[`specs/phase-0.md`](specs/phase-0.md) §6 and §8). `docker compose up --build -d`
runs the full stack (web → API → Kafka → processor → Postgres → API) end to end.
Next: Phase 0.5 (AWS guardrails, see [`docs/aws/AWS-PLAN.md`](docs/aws/AWS-PLAN.md)).

## Where things are

| Path | What |
|---|---|
| [`docs/PROJECT.md`](docs/PROJECT.md) | Project brief — the lab's source of truth |
| [`specs/phase-0.md`](specs/phase-0.md) | Phase 0 spec (SDD) and its verification results |
| [`CLAUDE.md`](CLAUDE.md) | Working agreement and conventions |
| `apps/transaction-api/` | Spring WebFlux service (produces to Kafka, reads DB) |
| `apps/transaction-processor/` | Spring Kafka consumer (persists to DB) |
| `apps/web/` | React + TypeScript PWA |
| [`docs/architecture/`](docs/architecture/c4-context.md) | C4 diagrams ([context](docs/architecture/c4-context.md), [containers](docs/architecture/c4-container.md)) |

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

### V1 — Los 5 contenedores alcanzan `healthy`

```powershell
docker compose ps
```

Esperado: `healthy` en `web`, `transaction-api`, `transaction-processor`,
`kafka` y `postgres`. Ojo: `docker compose ps` **solo lista contenedores en
ejecución**; uno que se cayó simplemente no aparece (usa `docker compose ps -a`
para verlo).

### V2 — Un `POST` válido se acepta

```powershell
$body = '{"accountId":"acc-1","amount":"-42000","currency":"COP","merchant":"Exito","occurredAt":"2026-09-02T14:00:00Z"}'
$r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri http://localhost:8080/transactions -ContentType 'application/json' -Body $body
$r.StatusCode   # 202
$r.Content      # {"id":"<uuid>"}
```

Se usa `Invoke-WebRequest` (no `Invoke-RestMethod`) porque este último no
expone el código de estado. `amount` va como **string** (contrato §4.2).

### V3 — El evento se persiste

```powershell
docker compose exec postgres psql -U mp -d moneypulse -c 'select count(*) from transactions'
```

Esperado: en estado estable, el conteo incrementa en < 5 s tras un `POST`. Para
medirlo:

```powershell
$antes = [int](docker compose exec -T postgres psql -U mp -d moneypulse -t -A -c 'select count(*) from transactions')
$sw = [Diagnostics.Stopwatch]::StartNew()
$null = Invoke-RestMethod -Method Post -Uri http://localhost:8080/transactions -ContentType 'application/json' -Body $body
do { $n = [int](docker compose exec -T postgres psql -U mp -d moneypulse -t -A -c 'select count(*) from transactions'); Start-Sleep -Milliseconds 100 } until ($n -gt $antes -or $sw.Elapsed.TotalSeconds -gt 10)
"count=$n (antes: $antes) en $([int]$sw.Elapsed.TotalMilliseconds) ms"
```

(el tiempo incluye el costo de cada `docker compose exec`, ~0,5–1 s: sirve para
comprobar el umbral de 5 s, no para medir los 28 ms de estado estable).
**El primer evento tras arrancar el
processor es más lento** (ver «Comportamiento observado»): envía un evento de
calentamiento antes de dar V3 por fallida.

### V4 — La API lo lee de vuelta

```powershell
Invoke-RestMethod http://localhost:8080/transactions | Select-Object -First 1 | ConvertTo-Json
```

Esperado: la lista incluye la transacción creada, con `amount` como string.

### V5 — Tabla vacía no rompe el `GET`

Solo se puede comprobar con la base vacía, es decir, justo después de un
arranque en frío (ver V12):

```powershell
$g = Invoke-WebRequest -UseBasicParsing http://localhost:8080/transactions
"$($g.StatusCode) $($g.Content)"   # 200 []
```

### V6 — El `traceId` atraviesa E2E

```powershell
$id = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/transactions -ContentType 'application/json' -Body $body).id
Start-Sleep -Seconds 2
$linea = docker compose logs transaction-api | Select-String "Published transaction $id" | Select-Object -First 1
$traceId = [regex]::Match($linea.Line, '\[transaction-api,([0-9a-f]{32}),').Groups[1].Value
docker compose logs transaction-api transaction-processor | Select-String $traceId
```

Esperado: ≥1 línea de **cada** servicio con el mismo `traceId`.

### V7 — Salud verde

```powershell
(Invoke-RestMethod http://localhost:8080/actuator/health).status
(docker compose exec -T transaction-processor curl -s http://localhost:8082/actuator/health | ConvertFrom-Json).status
```

Esperado: `UP` en ambos. El processor no publica puerto al host, por eso se
consulta dentro de la red de compose. (El JSON completo trae además
`components`; lo que importa es `status`.)

### V8 — La web cierra el lazo

En http://localhost:5173, crear una transacción con el formulario.

Esperado: aparece en la lista sin recargar la página (el hook hace poll cada
3 s). Para comprobar que el proxy de la web llega a la API:

```powershell
(Invoke-RestMethod http://localhost:5173/transactions).Count
```

### V9 — Tests verdes con infra real

```powershell
.\gradlew.bat test --rerun-tasks
```

Requiere Docker (Testcontainers levanta Kafka y Postgres reales). **Usa
`--rerun-tasks`**: sin él Gradle puede responder `UP-TO-DATE` sin ejecutar
nada y el `BUILD SUCCESSFUL` no prueba nada. Esperado: `BUILD SUCCESSFUL`
con los tests de integración de ambos módulos.

### V10 — Sin bloqueo en el path de la API

```powershell
Get-ChildItem apps\transaction-api\src\main -Recurse -Filter *.java | Select-String -Pattern '\.block\(\)|\.toFuture\(\)|JdbcTemplate'
```

Esperado: sin resultados.

### V11 — Arquitectura documentada

Abrir [`docs/architecture/c4-context.md`](docs/architecture/c4-context.md) y
[`c4-container.md`](docs/architecture/c4-container.md): diagramas C4 nivel 1 y
2, enlazados desde `docs/PROJECT.md`.

### V12 — Orden de arranque correcto (arranque en frío)

```powershell
docker compose down -v          # borra también los volúmenes: los datos se pierden
docker compose up --build -d
docker compose ps               # espera a que los servicios estén healthy
docker compose logs | Select-String 'relation ".*" does not exist'
```

Esperado: ninguna coincidencia. Busca ese patrón y no el genérico
`does not exist`: en un arranque en frío aparecen dos líneas benignas que lo
contienen — `FATAL: database "moneypulse" does not exist` (el healthcheck de
Postgres pregunta mientras la base se inicializa) y el INFO de Flyway
`Schema history table ... does not exist yet` (primera migración).

## Comportamiento observado

Mediciones y hallazgos de la verificación manual de la Fase 0 (detalle en
[`specs/phase-0.md`](specs/phase-0.md) §8):

- **Latencia API → Postgres.** El primer evento tras arrancar el processor
  tardó **13,4 s** (arranque en frío: unión al grupo de consumidores y
  metadatos de Kafka). En estado estable, **28 ms**.
- **`receivedAt` pierde precisión al persistir.** El evento sale con
  nanosegundos (9 decimales); PostgreSQL `timestamptz` guarda microsegundos,
  así que el `GET` devuelve 6. Deuda abierta, se decide en Fase 1.
- **`amount` en el `GET` es un string JSON**, no un número (p. ej.
  `"-1500.50"`), igual que en el evento. Cumple §4.2; no hay decisión abierta.
- **UI:** ingresos y gastos se ven igual en la lista (`monto moneda`, sin
  distinción visual más allá del signo). Mejora para Fase 1.

## Solución de problemas

**Docker Desktop sin DNS** — `docker compose up --build` falla con
`lookup auth.docker.io: no such host` (o `dial tcp: lookup registry-1.docker.io`):
es un problema de red de la VM de Docker Desktop, no del proyecto. Ejecuta
`wsl --shutdown` y vuelve a abrir Docker Desktop.

**El engine de Docker se cae por memoria** — libera RAM:

```powershell
.\gradlew.bat --stop              # daemons de Gradle viejos que siguen ocupando memoria
docker compose stop kafka-ui      # tooling de dev, no lo necesita el skeleton
```

**«Un servicio desapareció de `docker compose ps`»** — `docker compose ps`
solo lista contenedores **en ejecución**. Si no aparece, está detenido o se
cayó: `docker compose ps -a` y `docker compose logs <servicio>`.

## Loop de desarrollo

Para iterar sin reconstruir contenedores cada vez, ver los comandos en
[`CLAUDE.md`](CLAUDE.md#commands) (`./gradlew test` por módulo,
`cd apps/web && npm run dev`).

## Apagar

```powershell
docker compose down        # conserva los datos (volúmenes)
docker compose down -v     # arranque en frío — borra volúmenes también
```
