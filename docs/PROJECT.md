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

## 6. Stack y decisiones (defaults)

- Java 21, Spring Boot 3.3+, Gradle (Kotlin DSL).
- Transaction API: Spring WebFlux + R2DBC (reactivo E2E).
- Transaction Processor: Spring Kafka + R2DBC.
- Web: Vite + React 18 + TypeScript (PWA).
- Eventos: JSON en Fase 0 → Avro + schema registry en Fase 2.
- Observabilidad: Micrometer Tracing + bridge OpenTelemetry desde el commit 1.
- Local: docker-compose (Kafka KRaft, sin Zookeeper). Kubernetes/EKS en Fase 7.

## 7. Roadmap por fases (las *-ilities* como columna vertebral)

| Fase | Foco | Cualidades sistémicas |
|---|---|---|
| 0 | Walking skeleton + arquitectura (C4) | deployability, trazabilidad (base) |
| 1 | Slice full-stack + profundidad de React | rendimiento (frontend), usabilidad |
| 2 | Núcleo event-driven + concurrencia | concurrencia, resiliencia (base) |
| 3 | Resiliencia y disponibilidad (Resilience4j) | resiliencia, disponibilidad |
| 4 | Seguridad (OAuth2/OIDC, secretos, OWASP) | seguridad |
| 5 | Observabilidad y trazabilidad (OpenTelemetry) | trazabilidad, observabilidad |
| 6 | Escalabilidad y rendimiento (k6, HPA, Redis) | escalabilidad, rendimiento |
| 7 | Production-readiness: CI/CD + Kubernetes (EKS) | automatización, deployability |

Práctica transversal a todas las fases: **tests unitarios + de integración (Testcontainers)** desde el inicio.

## 8. Estado actual

- **Fase 0: definida** (ver `specs/phase-0.md`). Pendiente: generar el repo ejecutable.
- Fases 1–7: pendientes de detallar.

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

## 11. Glosario

- **JD** (Job Description): la descripción del cargo de Slalom.
- **SDD** (Spec-Driven Development): paradigma donde el spec versionado es la fuente de verdad y el código se deriva de él.
- **Walking skeleton / tracer bullet**: la rebanada vertical más delgada que atraviesa todas las capas para probar la arquitectura, sin lógica de negocio.
- **Las *-ilities***: cualidades sistémicas no funcionales (scalability, reliability, security, observability, etc.).
- **C4**: modelo de diagramas de arquitectura (Context, Container, Component, Code).
