# Spec — Fase 0: Walking Skeleton

> **Fuente de verdad de la Fase 0.** Derivado de `docs/PROJECT.md` §5–6. El código
> se deriva de este spec; si un requisito cambia, se edita aquí primero y se
> regenera lo afectado.
>
> Estado: **aprobado, pendiente de implementación.**
> Fase siguiente: `specs/phase-1.md` (aún no escrita).

---

## 1. Outcomes

Resultados observables al terminar la fase. No describen implementación.

1. **Arranque de un comando.** `docker compose up --build` levanta 5 servicios y
   todos alcanzan estado *healthy*: `web`, `transaction-api`,
   `transaction-processor`, `kafka` (KRaft), `postgres`.
2. **Rebanada vertical E2E.** Una transacción enviada por `POST /transactions` a
   `transaction-api` se publica en el topic Kafka `transactions.received`, la
   consume `transaction-processor` y queda persistida en PostgreSQL.
3. **Lectura de vuelta.** `GET /transactions` en `transaction-api` devuelve las
   transacciones leídas de PostgreSQL (no de un cache en memoria, no de Kafka).
4. **La web cierra el lazo.** La app React lista las transacciones desde
   `GET /transactions` y ofrece un formulario que dispara el `POST`. Tras crear
   una, aparece en la lista.
5. **Trazabilidad base.** Un único `traceId` (contexto W3C `traceparent`) es
   rastreable de extremo a extremo: HTTP entrante → header del registro Kafka →
   logs de `transaction-processor`. Un `grep` por ese `traceId` sobre los logs de
   ambos servicios devuelve líneas de los dos.
6. **Salud expuesta.** `GET /actuator/health` responde `UP` en los tres servicios
   Spring.
7. **Arquitectura documentada.** Diagramas C4 nivel 1 (Context) y nivel 2
   (Container) versionados en `docs/architecture/`, enlazados desde `PROJECT.md`.
8. **Cobertura mínima verde.** `./gradlew test` pasa en `transaction-api` y en
   `transaction-processor`, cada uno con al menos un test de integración con
   Testcontainers (Kafka y PostgreSQL reales).

---

## 2. Límites de scope

Fuera de la Fase 0. Cada exclusión indica la fase donde entra (ver `PROJECT.md` §7).

| Excluido | Entra en |
|---|---|
| Categorización de transacciones, detección de suscripciones y cobros duplicados | Fase 1–2 |
| Presupuestos y alertas de gasto (push) | Fase 1–2 |
| Generador sintético de transacciones como servicio del `compose` | Fase 2 (en Fase 0, la transacción entra por un `POST` manual o el botón de la web) |
| Avro + schema registry (Fase 0 usa **JSON**) | Fase 2 |
| Consumidor Kafka reactivo real / no-bloqueo en el processor | Fase 2 (ver §4.6) |
| Idempotencia, claves de idempotencia, optimistic locking | Fase 2 |
| Dead-letter queue, retries/backoff, `@RetryableTopic`, circuit breakers (Resilience4j) | Fase 3 |
| AuthN/AuthZ: OAuth2/OIDC, gestión de secretos, hardening OWASP | Fase 4 |
| OpenTelemetry Collector, exportación a un backend de trazas, dashboards, métricas de negocio | Fase 5 |
| Redis, HPA, pruebas de carga (k6), tuning de rendimiento | Fase 6 |
| Kubernetes/EKS, pipeline CI/CD, IaC | Fase 7 |
| PWA con service worker de negocio, offline, push real (Fase 0 = solo `manifest` mínimo vía plugin de Vite) | Fase 1+ |
| Frontend: librería de estado, routing multi-vista, sistema de diseño, i18n | Fase 1 |
| Multi-partición / multi-consumidor en el topic (Fase 0 = 1 partición, 1 grupo de consumidores) | Fase 6 |

**Regla de scope:** ningún cambio de esta fase debe introducir dependencias,
configuración o abstracciones cuyo único propósito sea una fase posterior.

---

## 3. Constraints

Reglas que el código debe respetar. Derivadas de `CLAUDE.md` y `PROJECT.md` §6.

> **"Reactivo E2E" en este spec** = no-bloqueo en el *path de la API* (WebFlux + R2DBC, sin `.block()` en los handlers); el `transaction-processor` puede bloquear el hilo del listener en la persistencia en Fase 0 (ver §3.2 y §4.6). No significa "todo el sistema".

### 3.1 Path de la API — reactivo estricto
- `transaction-api` es Spring WebFlux. **Cero llamadas bloqueantes** en los
  handlers: sin `.block()`, sin `.toFuture().get()`, sin JDBC. Acceso a datos por
  **R2DBC**; publicación a Kafka por API reactiva (`ReactiveKafkaProducerTemplate`
  o `KafkaTemplate` con `observation-enabled` consumido de forma no bloqueante).

### 3.2 Path del processor — R2DBC como driver, bloqueo permitido en Fase 0
- `transaction-processor` usa **R2DBC** como driver de persistencia (coherencia de
  stack, migración futura a no-bloqueo sin cambiar de driver).
- En Fase 0 el consumo es `@KafkaListener` (listener container de Spring Kafka) y
  **se permite bloquear el hilo del listener en la persistencia**
  (`repository.save(...).block()`). El "reactivo de extremo a extremo" aplica al
  *path de la API*, no al processor en esta fase.
- El no-bloqueo real del consumidor se **difiere a Fase 2** (ver §4.6). El spec no
  debe optimizar el pool del listener ni el pool R2DBC del processor en Fase 0
  más allá de valores por defecto razonables.

### 3.3 Trazabilidad — desde el commit 1
- Micrometer Tracing + bridge OpenTelemetry activos en los tres servicios Spring.
- `observation-enabled: true` en el `KafkaTemplate`/producer y en la listener
  container factory, para que el contexto de traza fluya HTTP → Kafka → consumidor.
- El patrón de log incluye `traceId` y `spanId` vía MDC en los tres servicios.
  **No se elimina ese patrón.**

### 3.4 Plataforma y lenguaje
- Java 21, Spring Boot **4.1+**, Gradle con Kotlin DSL. (La línea 3.x quedó EOL
  antes de arrancar el lab; se fija `4.1.1` en `gradle/libs.versions.toml`.)
- React 18 + TypeScript + Vite para `web`.
- Kafka en modo **KRaft** (sin ZooKeeper).
- PostgreSQL como *transaction store*.

### 3.5 Calidad
- Cada feature entrega **test unitario + test de integración** (Testcontainers
  para Kafka y PostgreSQL). No es opcional.
- Conventional commits.
- Sin secretos hardcodeados, ni siquiera en local: configuración por variables de
  entorno / `docker-compose`.
- Cada servicio expone `/actuator/health`.

---

## 4. Decisiones previas

Decisiones ya tomadas y su porqué, para no re-litigarlas. Las marcadas *(abierta)*
se revisan en la fase indicada.

### 4.1 Nombre del topic y particionado
`transactions.received`, **1 partición**, 1 grupo de consumidores en Fase 0.
Multi-partición y estrategia de key se revisan en Fase 6 (escalabilidad).

### 4.2 Contrato del evento (JSON)
Campos mínimos del mensaje publicado en `transactions.received`:

| Campo | Tipo | Origen |
|---|---|---|
| `id` | UUID (string) | generado por `transaction-api` al recibir el `POST` |
| `accountId` | string | del cuerpo del `POST` |
| `amount` | número decimal (`BigDecimal`), **con signo**: negativo = egreso | del cuerpo del `POST` |
| `currency` | string ISO-4217 (p. ej. `COP`, `USD`) | del cuerpo del `POST` |
| `merchant` | string | del cuerpo del `POST` |
| `occurredAt` | timestamp ISO-8601 (`Instant`) | del cuerpo del `POST` (cuándo ocurrió en el banco) |
| `receivedAt` | timestamp ISO-8601 (`Instant`) | fijado por `transaction-api` al recibir |

- El `traceparent` **no** viaja en el cuerpo; viaja como header del registro Kafka
  (lo inyecta la instrumentación de observación).
- Amount con signo (un solo campo) en lugar de `amount` + `direction`: es lo
  mínimo para el skeleton. El modelo de dominio (dirección explícita, minor units,
  cuenta como agregado) se define en Fase 1.

### 4.3 CQRS-lite: quién escribe la tabla
- `transaction-api` **escribe a Kafka y lee de la tabla `transactions`**. Nunca
  escribe la tabla directamente.
- `transaction-processor` es el **único dueño de la escritura** en `transactions`.
- Consecuencia: `GET /transactions` puede devolver una lista que aún no incluye un
  `POST` recién hecho (consistencia eventual). Es aceptable y esperado en Fase 0;
  la web no asume read-your-writes.

### 4.4 Migraciones de esquema — Flyway en el processor
- `transaction-processor` posee el ciclo de vida del esquema y ejecuta **Flyway**
  al arrancar (durante el refresh del contexto Spring, antes de servir tráfico).
- `transaction-api` **no** corre migraciones. Declara las entidades R2DBC que
  necesita para leer y asume que el esquema ya existe.
- Un job de migración dedicado (contenedor one-shot, desacoplado de ambos
  servicios) se considera en Fase 3/7; en Fase 0 se prioriza simplicidad.

### 4.5 Orden de arranque: Flyway (processor) vs. lectura de la tabla por la API
Problema: `transaction-api` lee `transactions` en `GET /transactions`; esa tabla
la crea Flyway dentro de `transaction-processor`. Si la API sirviera un `GET`
antes de que exista la tabla, fallaría.

**Resolución (Fase 0):**
1. **Postgres** expone healthcheck; `transaction-processor` y `transaction-api`
   declaran `depends_on: { postgres: { condition: service_healthy } }`.
2. `transaction-processor` corre Flyway durante el arranque; su
   `/actuator/health` (grupo `readiness`) **solo reporta `UP` tras completar las
   migraciones** (comportamiento por defecto de Spring Boot: las migraciones
   corren antes de que el endpoint de salud sirva `UP`).
3. `transaction-api` declara
   `depends_on: { transaction-processor: { condition: service_healthy } }`.
   Así, cuando la API empieza a aceptar tráfico, la tabla ya existe.
4. Defensa en profundidad: `GET /transactions` sobre una tabla vacía devuelve
   `200 []`, nunca error. (No se añade tolerancia a "tabla inexistente"; el
   ordenamiento de arriba la hace innecesaria y esconderla sería deuda.)

Nota: este `depends_on` acopla el arranque de la API a la salud del processor.
Es aceptable para un walking skeleton; **la resiliencia al arranque desordenado
y a la caída del processor es tema de Fase 3**, que probablemente introduzca el
job de migración dedicado de §4.4 y elimine este acoplamiento.

### 4.6 Conciliación de R2DBC con `@KafkaListener` en el processor *(elección tomada; revisión en Fase 2)*

`transaction-processor` recibe por Kafka (modelo de hilos del listener container
de Spring Kafka) y persiste con R2DBC (API `Mono`/`Flux`, pensada para runtime
no bloqueante). Hay que reconciliar ambos modelos. Opciones evaluadas:

**Opción A — `@KafkaListener` imperativo + `.block()` en la persistencia**
```java
@KafkaListener(topics = "transactions.received")
void onMessage(TransactionEvent event) {
    repository.save(toRow(event)).block();
}
```
- **A favor:** conserva intacto todo el andamiaje de Spring Kafka — commit de
  offsets, `DefaultErrorHandler`, retries, `@RetryableTopic`/DLQ (Fase 3),
  métricas del container, e instrumentación de observación que propaga el
  `traceparent` de forma automática. Mínima carga cognitiva. Back-pressure vía
  `max.poll.records` × `concurrency`.
- **En contra:** bloquea un hilo del listener por cada persistencia; el no-bloqueo
  no aporta nada aquí (el driver sigue siendo R2DBC pero se consume bloqueando).
  Hay que dimensionar a mano `concurrency` y el pool R2DBC. Inconsistente con la
  aspiración "reactivo E2E" del proyecto.

**Opción B — `ReactiveKafkaConsumerTemplate` (reactor-kafka)**
```java
consumerTemplate.receive()
    .flatMap(rec -> repository.save(toRow(rec.value()))
        .then(Mono.fromRunnable(() -> rec.receiverOffset().acknowledge())), CONCURRENCY)
    .subscribe();
```
- **A favor:** no-bloqueo real de extremo a extremo; back-pressure nativo de
  Reactor; concurrencia explícita con `flatMap(n)`; coherente con el objetivo de
  aprendizaje de concurrencia reactiva.
- **En contra:** se pierde casi todo el andamiaje del listener container (error
  handling, retry/backoff, DLQ, métricas) y hay que reconstruirlo sobre Reactor.
  reactor-kafka es más artesanal y con menos ejemplos. La propagación del contexto
  de observación exige cuidado manual.

**Punto intermedio (descartado):** `@KafkaListener` con `Acknowledgment` manual y
suscripción del `Mono` en un scheduler acotado — no elimina el bloqueo del hilo de
poll salvo ack asíncrono, y queda frágil.

**Elección para Fase 0: Opción A.** El foco de la Fase 0 es *deployability* y
*trazabilidad*, no concurrencia. La Opción A entrega el skeleton antes y deja el
andamiaje de errores/DLQ para la Fase 3, donde ese código se toca de todos modos
con Resilience4j.

**Disparador de revisión: Fase 2** (núcleo event-driven + concurrencia). En esa
fase se evalúa migrar el processor a la Opción B o a un consumo no bloqueante
equivalente, con la concurrencia del consumidor como objeto de estudio explícito.

### 4.7 DTO del evento compartido vs. duplicado
En Fase 0 el `record` del evento (`TransactionEvent`) se **duplica** en
`transaction-api` y `transaction-processor`. Se evita un módulo Gradle `events`
compartido para no introducir acoplamiento de build ni versionado de contrato
antes de tiempo. Con la llegada de Avro + schema registry en Fase 2, el contrato
pasa a ser el schema y esta duplicación desaparece.

### 4.8 Data fetching en la web
En Fase 0, `fetch` nativo encapsulado en un custom hook (`useTransactions`). Sin
TanStack Query ni SWR. La librería de data-fetching y su patrón idiomático se
introducen y se enseñan en Fase 1 (profundidad de React).

### 4.9 Docker Compose es el único entorno de Fase 0
El archivo es `compose.yaml` (nombre canónico actual) en la raíz. Sin perfiles de
Spring por entorno más allá de `default` + `docker`. Sin overrides de `compose`.
Kubernetes/Helm entran en Fase 7. Incluye un servicio `kafka-ui` marcado como
tooling de dev (no lo requiere el skeleton).

---

## 5. Desglose de tareas

Atómicas y ordenadas por dependencia. Cada una ≈ un commit (conventional).

1. **`chore: scaffold monorepo`** — estructura de carpetas (`apps/transaction-api`,
   `apps/transaction-processor`, `apps/web`, `docs/architecture`),
   `settings.gradle.kts`, build raíz, `.gitignore`, `.editorconfig`, versión de
   toolchain Java 21.
2. **`build: local infra via docker-compose`** — `compose.yaml` con `kafka`
   (KRaft) y `postgres`, healthchecks, red y volúmenes nombrados. Más `kafka-ui`
   como tooling de dev (comentado como opcional). Sin servicios de app todavía.
3. **`feat(api): scaffold WebFlux service`** — `transaction-api` arranca, expone
   `/actuator/health`, configura patrón de log con `traceId`/`spanId`, tracing +
   bridge OTel. Aún sin endpoints de negocio.
4. **`feat(api): define TransactionEvent contract`** — `record TransactionEvent`
   (JSON) según §4.2. Serialización configurada.
5. **`feat(api): POST /transactions publishes to Kafka`** — valida el cuerpo,
   genera `id` y `receivedAt`, publica en `transactions.received` de forma no
   bloqueante, responde `202 Accepted` con el `id`. `observation-enabled: true`.
6. **`feat(processor): scaffold Kafka consumer service`** — `transaction-processor`
   arranca, `/actuator/health`, patrón de log, tracing. `@KafkaListener` vacío
   suscrito a `transactions.received`.
7. **`feat(processor): Flyway migration for transactions table`** — script
   `V1__create_transactions.sql` (columnas de §4.2 + `persisted_at`). Flyway
   corre al arranque; health `readiness` gated por migraciones (§4.5).
8. **`feat(processor): persist consumed events via R2DBC`** — el listener mapea el
   evento a fila y persiste con `repository.save(...).block()` (Opción A, §4.6).
9. **`feat(api): GET /transactions reads from Postgres`** — entidad R2DBC de solo
   lectura sobre `transactions`, ordenada por `receivedAt` desc, con límite.
   Tabla vacía → `200 []`.
10. **`feat: wire service startup ordering in compose`** — añade `transaction-api`
    y `transaction-processor` al `compose` con los `depends_on` de §4.5 y las
    variables de entorno (sin secretos hardcodeados).
11. **`feat(web): scaffold Vite + React + TS PWA`** — proyecto, plugin PWA con
    `manifest` mínimo, servicio en el `compose`, proxy al `transaction-api`.
12. **`feat(web): list and create transactions`** — hook `useTransactions`
    (`fetch` nativo), vista con lista (`GET`) y formulario (`POST`).
13. **`test(api): unit + Testcontainers integration`** — unit del handler y del
    publisher; integración: `POST` → mensaje presente en Kafka; `GET` → filas
    sembradas en Postgres devueltas.
14. **`test(processor): unit + Testcontainers integration`** — unit del mapper;
    integración: mensaje en Kafka → fila en Postgres, con `traceId` presente en
    los logs del consumidor.
15. **`docs: C4 context and container diagrams`** — diagramas nivel 1 y 2 en
    `docs/architecture/`, enlazados desde `PROJECT.md`.
16. **`docs: run and verify guide`** — README con el arranque y el checklist de
    verificación de §6.

---

## 6. Criterios de verificación

Compuerta ejecutable. La fase está terminada cuando **todos** pasan.

| # | Criterio | Comando / comprobación |
|---|---|---|
| V1 | Los 5 contenedores alcanzan `healthy`. | `docker compose up --build` → `docker compose ps` muestra `healthy` en `web`, `transaction-api`, `transaction-processor`, `kafka`, `postgres`. |
| V2 | Un `POST` válido se acepta. | `curl -i -X POST localhost:8080/transactions -H 'content-type: application/json' -d '{"accountId":"acc-1","amount":-42000,"currency":"COP","merchant":"Éxito","occurredAt":"2026-09-02T14:00:00Z"}'` → `202`, cuerpo con `id`. |
| V3 | El evento se persiste. | En < 5 s, `docker compose exec postgres psql -U mp -d moneypulse -c 'select count(*) from transactions'` incrementa. |
| V4 | La API lo lee de vuelta. | `curl localhost:8080/transactions` incluye la transacción creada. |
| V5 | Tabla vacía no rompe el `GET`. | Con `transactions` vacía, `curl localhost:8080/transactions` → `200 []`. |
| V6 | El `traceId` atraviesa E2E. | `docker compose logs transaction-api transaction-processor \| grep <traceId>` (el `traceId` de la respuesta/logs del `POST`) → ≥1 línea de **cada** servicio. |
| V7 | Salud verde. | `curl localhost:8080/actuator/health` y equivalente del processor → `{"status":"UP"}`. |
| V8 | La web cierra el lazo. | En el navegador, crear una transacción por el formulario → aparece en la lista sin recargar manualmente el contenedor. |
| V9 | Tests verdes con infra real. | `./gradlew test` en `transaction-api` y en `transaction-processor` → BUILD SUCCESSFUL; cada módulo ejecuta ≥1 test con Testcontainers (Kafka + Postgres). |
| V10 | Sin bloqueo en el path de la API. | Inspección de código: cero `.block()` / `.get()` / JDBC en `transaction-api`. (ArchUnit opcional.) |
| V11 | Arquitectura documentada. | `docs/architecture/` contiene los diagramas C4 nivel 1 y 2, enlazados desde `PROJECT.md`. |
| V12 | Orden de arranque correcto. | Arranque en frío (`docker compose down -v` y luego `up`): ningún `GET /transactions` devuelve error de "relación inexistente" en los logs. |

---

## 7. Notas de trazabilidad de decisiones abiertas hacia fases futuras

| Decisión | Estado | Se revisa en |
|---|---|---|
| §4.1 Particionado del topic (1 partición) | provisional | Fase 6 |
| §4.5 Acoplamiento de arranque API → processor vía `depends_on` | provisional | Fase 3 |
| §4.6 Opción A (`@KafkaListener` + `.block()`) en el processor | **elegida** | Fase 2 |
| §4.7 DTO duplicado (sin módulo compartido) | provisional | Fase 2 (Avro) |
| §4.8 `fetch` nativo en la web | provisional | Fase 1 |
