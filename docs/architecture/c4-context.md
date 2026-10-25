# C4 — Nivel 1: Diagrama de Contexto

> Fuente de verdad: `docs/PROJECT.md` (visión de producto) y `specs/phase-0.md`
> (alcance actual). Este diagrama describe la arquitectura **objetivo** del
> producto, no solo lo implementado hoy — ver la nota sobre el agregador
> más abajo.

```mermaid
flowchart TB
    user([Usuario])
    moneypulse[[Money Pulse]]
    aggregator[[Agregador open banking<br/>Belvo / Plaid<br/>· simulado en Fase 0 ·]]
    cognito[[Cognito<br/>identidad y login<br/>· objetivo: Fase 1 ·]]
    bedrock[[Amazon Bedrock<br/>categorización y resúmenes<br/>· objetivo: Fase 2 ·]]
    webpush[[Web Push<br/>notificaciones del navegador<br/>· objetivo: Fase 4 ·]]
    ses[[Email SES<br/>resumen mensual<br/>· objetivo: Fase 6 ·]]

    user -- "ve transacciones, registra gastos<br/>(navegador, PWA)" --> moneypulse
    user -. "login<br/>(objetivo: Fase 1)" .-> cognito
    cognito -. "token OIDC" .-> moneypulse
    aggregator -. "webhooks de transacciones<br/>(Fase 2 sintético · mTLS Fase 4)" .-> moneypulse
    moneypulse -. "categorización / resumen<br/>(objetivo: Fase 2 / Fase 6)" .-> bedrock
    moneypulse -. "alerta push<br/>(objetivo: Fase 4)" .-> webpush
    moneypulse -. "resumen mensual<br/>(objetivo: Fase 6)" .-> ses
    ses -. "email" .-> user
```

## Elementos

| Elemento | Tipo | Fase | Estado | Descripción |
|---|---|---|---|---|
| Usuario | Persona | — | Existe | Dueño de las cuentas bancarias que quiere ver y entender su gasto en tiempo real. |
| Money Pulse | Sistema de software (en alcance) | — | Existe (Fase 0: walking skeleton) | Categoriza transacciones, detecta patrones de gasto y alerta al usuario. |
| Agregador open banking (Belvo / Plaid) | Sistema de software externo | Fase 2 (sintético) / Fase 4 (mTLS) | Objetivo | Fuente real de los webhooks de transacciones bancarias; hoy simulado, mTLS llega en Fase 4 (ver `docs/aws/AWS-PLAN.md` §4.4). |
| Cognito | Sistema de software externo (identidad) | Fase 1 | Objetivo | Proveedor OIDC/PKCE de login de usuarios. |
| Amazon Bedrock | Sistema de software externo (IA generativa) | Fase 2 | Objetivo | Categorización semántica de comercios (Fase 2–3) y redacción del resumen mensual (Fase 6). |
| Web Push | Sistema de software externo (notificaciones) | Fase 4 | Objetivo | Alertas de gasto push al navegador/celular. |
| Email (SES) | Sistema de software externo (entrega de email) | Fase 6 | Objetivo | Entrega del resumen mensual inteligente al usuario. |

Arquitectura objetivo en AWS detallada (contenedores): [`c4-container-target.md`](c4-container-target.md).

## Nota sobre el agregador: simulado, no integrado, en Fase 0

El agregador aparece en este diagrama porque el Contexto describe la
arquitectura objetivo del producto (`docs/PROJECT.md` §4), no el estado
literal de cada fase. **Hoy (Fase 0) no existe ninguna integración real**:
la tabla de exclusiones de `specs/phase-0.md` §2 es explícita — el
generador sintético de transacciones (lo que algún día simulará al
agregador) entra en Fase 2; en Fase 0 una transacción entra por un `POST`
manual o por el formulario de la web. El agregador real (o su simulación
sintética) se conecta en una fase posterior; hasta entonces, esta flecha es
un recordatorio de *por qué* existe el resto de la arquitectura (Kafka,
idempotencia, resiliencia), no una integración construida.
