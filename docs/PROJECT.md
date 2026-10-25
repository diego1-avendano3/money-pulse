# Money Pulse — Project Brief (fuente de verdad del laboratorio)

> Documento de contexto para el Proyecto de Claude. Pégalo en el "knowledge" del proyecto o guárdalo como `docs/PROJECT.md`. Todo lo demás (specs, código) se deriva de aquí.
> Nombre de trabajo: **Money Pulse** (provisional — confirmar o cambiar).

---

## 1. Misión del laboratorio

Construir, de punta a punta, una app de finanzas personales en tiempo real como vehículo de aprendizaje para preparar un cargo **Senior Engineer – Software Engineering** en Slalom Build (Medellín, híbrido). El laboratorio prioriza dos cosas: (1) profundizar en **React / full-stack** —zona de crecimiento— y (2) demostrar **calidad de producción** (rendimiento, escalabilidad, seguridad, resiliencia, disponibilidad, trazabilidad, concurrencia) sobre un stack event-driven. Se ejecuta como proyecto de **Spec-Driven Development (SDD)** para aprender ese paradigma en el proceso.

## 2. Objetivo profesional y alineación con el cargo

Cargo objetivo: Senior Engineer – Software Engineering (Slalom Build). Señales clave de la descripción del puesto (JD) que el lab debe demostrar:

- Full-stack: front web moderno (React) + servicios backend (Java).
- Cloud (AWS) y prácticas cloud-native.
- Software seguro y altamente escalable.
- Ownership de componentes de extremo a extremo (data + API + UI).
- Validación unitaria **y de integración** con técnicas probadas.
- Scripts/automatización y mantenimiento de componentes en producción (ownership del pipeline).
- Trabajo ágil, iterativo y colaborativo.

## 3. Perfil del ingeniero (para calibrar la mentoría)

Senior Software Engineer y Solution Architect, 20+ años entregando soluciones enterprise en banca, seguros, entretenimiento, cruceros y retail. Experto en Java, Spring Boot, GraphQL, microservicios, AWS y arquitectura orientada a eventos. Fuerte en diseño y arquitectura. Entornos anglófonos con clientes globales (LatAm, México, EE.UU.).

**Zona de crecimiento declarada: React** (sin experiencia previa). Implicación para la mentoría: no explicar arquitectura o Java a nivel básico; sí ir con profundidad y buenas prácticas en React, y tratar el backend como terreno donde el ingeniero ya es senior.

## 4. El producto: Money Pulse

App de finanzas personales que **reacciona en tiempo real** a cada transacción. El usuario conecta su banco vía un agregador de open banking (tipo Belvo en LatAm/México, Plaid en EE.UU.); la app categoriza cada transacción, detecta suscripciones recurrentes y cobros duplicados, y dispara alertas de gasto al celular.

Valor de mercado: la gente pierde dinero en suscripciones olvidadas y sobregiros; una alerta instantánea en el teléfono es valor tangible diario. El caso justifica un stack event-driven real (a diferencia de un simple CRUD de gastos personales, que no ameritaría Kafka).

Por qué el caso justifica cada tecnología:
- **Kafka**: webhooks de transacciones de miles de usuarios como streams de alto volumen; fan-out a consumidores de categorización, presupuesto y alertas.
- **Concurrencia**: múltiples eventos concurrentes por usuario; el saldo/presupuesto debe ser correcto bajo concurrencia (locking optimista, idempotency keys).
- **Resiliencia/disponibilidad**: un evento financiero no se puede perder → at-least-once + consumidores idempotentes + dead-letter queue.
- **Seguridad**: PII + datos financieros → cifrado, OAuth2/OIDC, secretos, IAM de mínimo privilegio.
- **Trazabilidad**: auditar cada transacción de extremo a extremo (tracing distribuido).
- **Móvil**: React PWA responsive + push notifications.
- **IA generativa (Bedrock)**: los comercios que no resuelve el motor de reglas (`libs/ledger-core`)
  se categorizan semánticamente con AWS Bedrock — una dependencia externa real, no simulada.
  Justifica practicar un patrón que hoy pesa en el mercado (IA generativa en producción) con los
  mismos controles que ya exige el resto del sistema: resiliencia (circuit breaker + fallback,
  UC5), caché para controlar costo, y seguridad (IAM de mínimo privilegio, sin API keys).
- **Resumen mensual inteligente por email**: el usuario recibe, sin pedirlo, un email el día 1 de
  cada mes con una lectura en español de su propio gasto (variación por categoría, suscripciones
  detectadas, cambios de precio) — el mismo valor diario de la alerta en tiempo real, pero a escala
  mensual y redactado, no solo una cifra. Bedrock solo ve agregados, nunca transacciones
  individuales ni identificadores. Detalle en `docs/aws/AWS-PLAN.md` §2.2, Fase 6.
- **Data lake de transacciones**: separar el *ledger* transaccional (RDS, optimizado para
  escritura/consistencia) de la analítica histórica (S3/Parquet/Athena, optimizada para consultas
  de rango) es una decisión de arquitectura real, no un ejercicio — es lo que alimenta el resumen
  mensual sin pegarle a RDS con consultas analíticas. Fase 6.
- **Comparativa de cómputo (ECS Fargate)**: desplegar el mismo `transaction-api` en Lambda, EKS y
  ECS Fargate y medir costo/hora, cold start y esfuerzo operativo convierte "cuál cómputo usar" en
  una decisión informada por datos propios, no por intuición — exactamente el tipo de ADR que se
  espera de un Senior/Solution Architect. Fase 7.
- **Cifrado de campo con KMS**: `accountId` es el dato más sensible del usuario en este dominio;
  cifrarlo con *envelope encryption* (customer-managed key, caché de data keys) practica un patrón
  real de protección de datos financieros, más allá del cifrado en reposo "gratis" que ya da AWS
  por defecto. Fase 4.
- **Rotación de credenciales (Secrets Manager)**: practicar rotación automática de la contraseña
  **maestra** de RDS sin tocar las apps (que siguen en IAM auth) es higiene operativa real —
  separa "credencial de administración" de "credencial de aplicación", una distinción que
  cualquier auditoría de seguridad va a preguntar. Fase 4.
- **Auditoría (CloudTrail)**: un trail de *management events* consultable por Athena es la
  respuesta a "¿quién cambió este security group?" — trazabilidad no solo de las transacciones
  (ya cubierta por OpenTelemetry) sino de los cambios a la infraestructura misma. Fase 5.

Dos funcionalidades opcionales, fuera del camino crítico, para practicar servicios adicionales sin
comprometer el cronograma: **feature flags con AppConfig** (apagar Bedrock en pruebas de carga sin
redesplegar, Fase 3) y **WAF sobre API Gateway** (lab de un día, Fase 4). Detalle de las ocho en
`docs/aws/AWS-PLAN.md` §3, §9, §12, §13, §14.

Scoping práctico: **no se requieren convenios bancarios**. El stream se simula con un generador sintético de transacciones que imita los webhooks del agregador.

## 5. Arquitectura (walking skeleton)

Rebanada vertical mínima que atraviesa todas las capas:

```
User (mobile)
   → Web app (React + TS, PWA)
   → Transaction API (Spring Boot WebFlux)
   → Kafka (topic: transactions.received)
   → Transaction processor (Spring Boot consumer)
   → PostgreSQL (transaction store)
   ← Transaction API reads back → Web app
```

Un único `traceId` (W3C `traceparent`) viaja por HTTP → header Kafka → logs del consumidor. Detalle completo en `specs/phase-0.md`.

Diagramas C4: [Contexto (nivel 1)](architecture/c4-context.md) ·
[Contenedores (nivel 2)](architecture/c4-container.md) ·
[Contenedores objetivo en AWS](architecture/c4-container-target.md).

## 6. Stack y decisiones (defaults)

- Java 21, Spring Boot 4.1+ (la línea 3.x quedó EOL antes de arrancar el lab), Gradle (Kotlin DSL).
- Transaction API: Spring WebFlux + R2DBC (reactivo E2E).
- Transaction Processor: Spring Kafka + R2DBC.
- Web: Vite + React 19 + TypeScript (PWA). *(Actualizado en la tarea 11: el
  spec original fijaba React 18; para oct-2026 el ecosistema ya giró a 19 y
  mantenerse en 18 no tendría sentido para un lab que busca enseñar React
  vigente de cara a una entrevista senior.)*
- Eventos: JSON en Fase 0 → Avro + schema registry en Fase 2.
- Observabilidad: Micrometer Tracing + bridge OpenTelemetry desde el commit 1.
- Local: docker-compose (Kafka KRaft, sin Zookeeper). Kubernetes/EKS en Fase 7.
- **AWS (capa gratuita, cuenta Free plan):** modo *always-on* serverless (API Gateway REST/HTTP/WebSocket, Cognito, Lambda, SQS/SNS, EventBridge, DynamoDB, RDS PostgreSQL, S3/CloudFront) + *labs* efímeros (EKS, Kafka/MSK, Glue Schema Registry). Plan completo y guardrails de costo: `docs/aws/AWS-PLAN.md`.
- **Persistencia políglota:** PostgreSQL = ledger (fuente de verdad); DynamoDB = idempotencia, agregados en tiempo real, registros de baja latencia.
- **IaC:** Terraform; CI/CD con GitHub Actions + OIDC.
- **IA generativa:** AWS Bedrock (on-demand, nunca Provisioned Throughput), modelo económico
  (Claude Haiku o Amazon Nova Micro), para categorizar comercios que el motor de reglas de
  `libs/ledger-core` no resuelve y para redactar el resumen mensual. Detalle, resiliencia (UC5) y
  control de costo en `docs/aws/AWS-PLAN.md` §3, §7 y §14.
- **Analítica y mensajería adicional:** Step Functions + Athena/Glue Data Catalog + SES para el
  resumen mensual y el data lake de transacciones (Fase 6); ECS Fargate como comparativa de
  cómputo (Fase 7); KMS (envelope encryption) y Secrets Manager (rotación de credencial maestra
  de RDS) en Fase 4; CloudTrail en Fase 5. Dos opcionales fuera del camino crítico: AppConfig
  (Fase 3) y WAF (Fase 4). Detalle en `docs/aws/AWS-PLAN.md` §2.2, §3, §9, §12.

## 7. Roadmap por fases (las *-ilities* como columna vertebral)

| Fase | Foco | Cualidades sistémicas | Eje AWS (ver `docs/aws/AWS-PLAN.md` §12) |
|---|---|---|---|
| 0 | Walking skeleton + arquitectura (C4) | deployability, trazabilidad (base) | — (solo local) |
| 0.5 | Cuenta AWS y guardrails de costo | costo, seguridad base | Free plan, Budgets, IAM, Terraform bootstrap, OIDC |
| 1 | Slice full-stack + profundidad de React | rendimiento (frontend), usabilidad | S3/CloudFront, Cognito, HTTP API + JWT, Lambda, RDS |
| 2 | Núcleo event-driven + concurrencia | concurrencia, resiliencia (base) | SQS, DynamoDB, EventBridge, SNS, Glue Schema Registry, **Bedrock (categorización, happy path)** |
| 3 | Resiliencia y disponibilidad (Resilience4j) | resiliencia, disponibilidad | DLQ/redrive, fallos parciales, DLT Kafka, **circuit breaker + caché sobre Bedrock (UC5)** |
| 4 | Seguridad (OAuth2/OIDC, secretos, OWASP) | seguridad | REST API + mTLS, authorizer, usage plans, WebSocket, SSM, **KMS (cifrado de `accountId`)**, **Secrets Manager (rotación credencial maestra RDS)**; opcional: **WAF** |
| 5 | Observabilidad y trazabilidad (OpenTelemetry) | trazabilidad, observabilidad | ADOT, X-Ray, CloudWatch, **CloudTrail + Athena (auditoría)** |
| 6 | Escalabilidad y rendimiento (k6, picos UC1–UC6) | escalabilidad, rendimiento | Load leveling, throttling, DynamoDB sharding, **resumen mensual (Step Functions → Athena → Bedrock → SES)**, **data lake (S3 Parquet + Glue + Athena)** |
| 7 | Production-readiness: CI/CD + Kubernetes (EKS) | automatización, deployability | Lab EKS (+MSK opcional), KEDA, k6 → N events/s, **lab ECS Fargate (comparativa de cómputo + ADR)** |

Opcionales fuera del camino crítico, no en esta tabla por fase propia: **AppConfig** (feature
flags, encaja en Fase 3) y **WAF** (lab de un día, encaja en Fase 4). Ver
`docs/aws/AWS-PLAN.md` §3, §12.

Nota sobre caché: este roadmap no incluye Redis/ElastiCache como pieza del producto — DynamoDB ya
cumple ese rol (idempotencia, agregados en tiempo real, caché por comercio de Bedrock). Si en
algún momento se practica Redis, es dentro del lab EKS únicamente (nunca *always-on*); ElastiCache
queda descartado por costo (ver `docs/aws/AWS-PLAN.md` §1.3).

Práctica transversal a todas las fases: **tests unitarios + de integración (Testcontainers)** desde el inicio.

## 8. Estado actual

- **Fase 0: completada y verificada** (ver `specs/phase-0.md`; criterios V1–V12 validados manualmente, resultados en su §8; guía de ejecución en el `README.md`).
- **Siguiente paso: Fase 0.5** — Paso 0 de AWS: guardrails de costo, cuenta Free plan, Terraform bootstrap y CI. Ver `docs/aws/AWS-PLAN.md` §1 (guardrails) y §12 (roadmap). Antes de empezar hay que escribir `specs/phase-0.5.md`.
- Fases 1–7: pendientes de detallar como specs; el eje AWS ya está planificado en `docs/aws/AWS-PLAN.md` (oct-2026).
- **No abrir la cuenta AWS hasta escribir `specs/phase-0.5.md`**: el Free plan dura 6 meses desde su creación y esos meses deben empezar a contar cuando el spec ya está listo (la Fase 0 local ya está cerrada).

## 9. Enfoque de aprendizaje: Spec-Driven Development

Idea central de SDD: la **especificación versionada —no el código— es la fuente de verdad**. Se escribe un spec con la intención y los criterios, se deriva un plan, se descompone en tareas atómicas y solo entonces se genera el código. Cuando cambian los requisitos, se edita el spec y se regenera. Es la respuesta madura al "vibe coding" (prompts ad-hoc que derivan del intento y se degradan al crecer el proyecto).

Un buen spec define seis elementos: **outcomes, límites de scope, constraints, decisiones previas, desglose de tareas y criterios de verificación**. A diferencia de un PRD (que lo lee un humano que rellena ambigüedad), el spec de SDD funciona como compuerta de validación que el agente ejecuta.

Cómo se ejecuta en este lab (ver `CLAUDE.md` para las reglas operativas):
1. Cada feature/fase nace como un spec en `specs/`.
2. Se usa **plan mode** de Claude Code: explorar y planear antes de escribir.
3. El agente deriva el código del spec; el ingeniero revisa que respete arquitectura y criterios.
4. Si algo cambia, se edita el spec, no se parcha el código a ciegas.
5. Claude explica cada artefacto (spec/plan/tareas) para enseñar el paradigma, y critica los specs del ingeniero.

## 10. Decisiones abiertas (a validar con Claude Code)

Puntos deliberadamente sin cerrar, para revisarlos al retomar:

1. **Timeline**: el roadmap de 8 fases es ambicioso para alguien nuevo en React. ¿Comprimir, alargar o recortar fases?
2. **Tooling SDD**: ¿arrancar con SDD "nativo" (plan mode + carpeta `specs/` + los seis elementos) y adoptar **GitHub Spec Kit** más adelante, o empezar con Spec Kit desde ya?
3. **Complejidad reactiva**: WebFlux + R2DBC añade carga cognitiva sobre React (que ya es nuevo). ¿Mantener reactivo E2E o simplificar el processor a JDBC/MVC?
4. **Generador sintético**: ¿servicio propio en el compose o utilidad de test?
5. **Timing de seguridad**: Slalom pondera seguridad; ¿adelantar parte de la Fase 4?
6. **Nombre del producto**: confirmar "Money Pulse" o elegir alternativa.
7. **Decisiones abiertas del eje AWS**: ver `docs/aws/AWS-PLAN.md` §14 (EKS/MSK en Free plan, dominio para mTLS, Glue SR vs Apicurio, librerías con Boot 4).
8. **Modelo de Bedrock y control de costo**: ver `docs/aws/AWS-PLAN.md` §14 (A8) — on-demand vs
   Provisioned Throughput, qué modelo, estrategia de caché por comercio.
9. **Ventana de borrado de KMS/Secrets Manager**: ver `docs/aws/AWS-PLAN.md` §14 (A9) — cuándo
   programar el borrado de la customer-managed key y de la rotación de Secrets Manager al cerrar
   la Fase 4 / el lab.
10. **Sesiones del lab ECS Fargate**: ver `docs/aws/AWS-PLAN.md` §14 (A10) — cuántas sesiones
    hacen falta para el ADR de comparativa de cómputo.

## 11. Glosario

- **JD** (Job Description): la descripción del cargo de Slalom.
- **SDD** (Spec-Driven Development): paradigma donde el spec versionado es la fuente de verdad y el código se deriva de él.
- **Walking skeleton / tracer bullet**: la rebanada vertical más delgada que atraviesa todas las capas para probar la arquitectura, sin lógica de negocio.
- **Las *-ilities***: cualidades sistémicas no funcionales (scalability, reliability, security, observability, etc.).
- **C4**: modelo de diagramas de arquitectura (Context, Container, Component, Code).
