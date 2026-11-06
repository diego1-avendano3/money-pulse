# Spec — Fase AI: Pulse Agent (harness engineering)

> **Fuente de verdad del eje de IA del laboratorio.** No es una fase numerada de
> `docs/PROJECT.md` §7 (que versiona el eje AWS) — es una **línea de trabajo paralela**,
> independiente de esa numeración, que puede avanzar en cualquier momento porque sus
> prerrequisitos (lectura de `transactions` vía `transaction-api`) ya existen desde la
> Fase 0. Cuando este spec se apruebe, `docs/PROJECT.md` §7 debe ganar una fila o sección
> propia que la referencie — no se hace en este documento (ver §2, "No hace en este spec").
>
> Estado: **propuesto, pendiente de aprobación final** (2026-11-06). No hay código ni
> commits de esta fase todavía — este documento es el primer artefacto.
>
> **Revisión v2** (tras decisiones del usuario sobre la v1): recorta el alcance a
> **AI-1–AI-5** — MCP, producción/seguridad y la comparación con framework pasan a §8,
> "Trabajo futuro" (antes AI-6, AI-7, AI-8). Corrige §4.2 (sin clase stub de Bedrock, solo
> interfaz + ADR), §4.4 (endpoints de `transaction-api` estrechos y de solo lectura,
> aprobado), §4.5 (el `userId` se inyecta en el mismo punto que usará la AuthN real, con un
> criterio de verificación nuevo si el modelo intenta enviarlo), y §4.7 (`FakeLlmClient`
> separado en dos usos: mecánica del harness en CI vs evals contra el modelo real).
>
> **Revisión v3**: añade §3.4 (manejo de `ANTHROPIC_API_KEY` — `.env` local con deny en
> `.claude/settings.json`, secreto de CI acotado solo al workflow de evals); fija el
> workflow de evals a **solo manual** (`workflow_dispatch`), dejando una corrida programada
> como opción futura; los evals corren contra el **mismo modelo configurado para el
> agente** (no uno reservado "dorado") y lo registran en el resultado versionado; AI-2 gana
> una prueba mínima de prompt injection; y AI-3 cambia de `GET` a `POST` para el streaming
> (§4.8), para que la pregunta nunca viaje en la URL ni en los logs.
>
> **Revisión v4**: la prueba de prompt injection de la v3 mezclaba dos propiedades que una
> sola prueba no puede medir — se separa en (a) una propiedad **estructural y determinista
> del harness** (§3.2: el contenido de una tool viaja siempre como `tool_result`, nunca
> concatenado al prompt — probada con `FakeLlmClient`, AI-1 tarea 8) y (b) 1–2 casos
> **adversariales reales**, movidos al dataset de AI-5 (tarea 22) para medirse contra el
> modelo real, no contra un guion que ya "sabe" la respuesta. §3.4/tarea 25 cambia de un
> secreto de repositorio a un **GitHub Environment `evals`** (environment secret +
> *required reviewer*), porque un secreto de repositorio es visible para cualquier
> workflow. La tarea 2 define el mecanismo concreto por el que `ANTHROPIC_API_KEY` llega a
> Spring Boot: `.env` → `compose.yaml` (`environment:` interpolado por Compose) → variable
> de entorno real del contenedor → *relaxed binding* de Spring Boot.
>
> Objetivo de aprendizaje explícito (distinto del resto del lab): **harness engineering**.
> El bucle agéntico (tool-use loop) se construye a mano sobre la API de mensajes antes de
> considerar cualquier framework (Spring AI, LangChain4j) — ver §4.1. Eso cambia el orden de
> prioridades de este spec frente a los demás `specs/phase-*.md`: aquí el *cómo* se construye
> el harness es en sí mismo un outcome, no un detalle de implementación a esconder.

---

## 1. Outcomes

Resultados observables, divididos en cinco sub-fases incrementales (AI-1 a AI-5). Cada una
entrega algo demostrable por sí sola, sin esperar a las siguientes. MCP (antes AI-6),
producción/seguridad (antes AI-7) y la comparación con framework (antes AI-8) quedan fuera
de este spec — ver §8, "Trabajo futuro".

### AI-1 — Harness mínimo
Un nuevo módulo `apps/agent-service` (Java 21, Spring Boot 4.1+) expone un endpoint que
recibe una pregunta en texto libre y devuelve una respuesta final, habiendo ejecutado
internamente un bucle agéntico propio: el modelo pide una herramienta → el harness la
ejecuta → el resultado vuelve al modelo → se repite hasta una respuesta final o hasta un
límite de iteraciones. **Demostrable:** con una única herramienta trivial de prueba (p. ej.
`get_current_time`), una pregunta que la requiere dispara exactamente una llamada a
herramienta visible en los logs (iteración, nombre de la tool, argumentos, resultado), y el
harness corta limpio si se fuerza un bucle infinito (herramienta que siempre reclama más
trabajo) al llegar al límite de iteraciones, devolviendo un error controlado, no una
excepción sin manejar ni un proceso colgado. También queda probado, con el mismo
`FakeLlmClient` determinista (ningún modelo real involucrado), que lo que una tool devuelve
viaja siempre como un bloque `tool_result` separado en el mensaje que vuelve al modelo —
nunca concatenado a la cadena del system prompt ni a ninguna instrucción (§3.2, tarea 8).
Esta es la precondición estructural de cualquier defensa de prompt injection, no la defensa
en sí — esa parte depende de cómo reacciona el **modelo real** y se mide en AI-5, no aquí.

### AI-2 — Herramientas de solo lectura sobre datos reales
3–4 tools responden preguntas reales sobre los datos sembrados por la Fase 0/1 (buscar
transacciones, agregar por categoría/periodo, detectar cargos recurrentes). **Demostrable:**
la pregunta "¿en qué categoría gasté más este mes?" produce una llamada a la tool de
agregación con el periodo correcto inferido, y la cifra que el agente reporta coincide
exactamente con una consulta SQL directa contra la misma base de datos sembrada.

### AI-3 — Streaming
Un endpoint **`POST`** (no `GET` — la pregunta nunca viaja en la URL, ver §4.8) emite
eventos tipados en formato SSE (`token`, `tool_call_started`, `tool_call_result`, `final`,
`error`) y la web de React los consume, vía `fetch`, en un panel de chat nuevo.
**Demostrable:** en el navegador, al hacer una pregunta se ven aparecer tokens de texto
progresivamente y una insignia visual cuando el agente invoca una herramienta, antes de la
respuesta final — no una barra de carga opaca; y la pregunta no aparece en ningún log de
acceso ni en el historial del navegador, porque nunca estuvo en la URL.

### AI-4 — Context engineering
System prompt versionado en un archivo (no en código Java inline), historial de
conversación con límite de tokens y una estrategia explícita de resumen/recorte al
superarlo, y registro de tokens de entrada/salida y costo estimado por turno.
**Demostrable:** una conversación de prueba diseñada para superar el límite de tokens
dispara el recorte/resumen (visible en logs: "historial recortado de N a M mensajes") y
sigue respondiendo coherentemente; cada turno deja una línea de log o fila con
`inputTokens`, `outputTokens`, `estimatedCostUsd`.

### AI-5 — Evals
Un dataset de 20–30 casos (pregunta, datos semilla, herramienta esperada, respuesta esperada
o criterio de aceptación) y un runner que mide selección correcta de herramienta, exactitud
numérica contra la base de datos, y latencia/costo. **Un `FakeLlmClient` no puede evaluar
nada de esto de forma significativa**: la selección de herramienta es precisamente lo que el
modelo real decide — un guion escrito a mano ya "sabe" la respuesta. Por eso el runner de
AI-5 corre contra el **mismo modelo configurado para el agente** (§3.1), no un modelo
distinto reservado para esto, y no contra el falso (ver §4.7 para por qué el falso sí
sirve, pero para otra cosa, en AI-1). **Demostrable:** una tarea de Gradle
(`./gradlew evalRun`), disparada a mano (`workflow_dispatch` en GitHub Actions o un comando
local) — nunca en cada push —, corre el dataset completo contra la API real respetando un
tope de costo por corrida, y guarda un resultado versionado (versión del system prompt +
modelo usado + las tres métricas + timestamp) que se puede comparar con corridas anteriores
para ver si un cambio de prompt (o de modelo) mejoró o empeoró el *pass rate*. Una corrida
programada (nocturna) queda como opción futura, no parte de este outcome. El dataset incluye
1–2 casos adversariales (una instrucción embebida en `merchant`, igual que en AI-1 tarea 8,
pero aquí evaluados contra el **modelo real** en vez de con el `FakeLlmClient` determinista)
— es la única medición de si el modelo real, no solo el harness, resiste ese caso; la
cobertura sistemática de prompt injection sigue siendo trabajo futuro (§8).

---

## 2. Límites de scope

### 2.1 Qué no entra en absoluto en esta línea de trabajo

| Excluido | Por qué |
|---|---|
| Datos financieros reales, cuentas bancarias reales, convenios de open banking | Constraint dura (§3.1) — coherente con el resto del lab (`PROJECT.md` §4: "no se requieren convenios bancarios"); solo datos sintéticos sembrados. |
| Herramientas de **escritura** (que el agente cree/modifique transacciones, presupuestos o alertas) | Fuera de todo el roadmap de IA aquí descrito. Si se quisiera en el futuro, es un spec nuevo con su propio análisis de riesgo (un agente que escribe es una superficie de ataque muy distinta a uno que solo lee). |
| Fine-tuning o entrenamiento de un modelo propio | Nunca — viola el guardrail de costo (§3.1) y no es el objetivo de aprendizaje (harness engineering, no ML). |
| A2A (Agent-to-Agent, protocolo multi-agente) | **Mención únicamente como trabajo futuro.** No se diseña, no se especifica, no se agenda. Se vuelve relevante solo si en algún momento Pulse Agent necesita delegar en otro agente autónomo (ninguna sub-fase de este documento, ni las de §8, lo requiere). |
| Despliegue de `agent-service` en AWS | **Depende explícitamente de fases AWS que hoy no están listas.** Este spec cubre únicamente ejecución local vía `compose.yaml`, igual que el resto del proyecto en Fase 0/0.5. Desplegarlo en el eje *always-on* (Lambda/API Gateway) o en el lab EKS es trabajo de una fase posterior, cuando exista cómputo de aplicación en AWS (Fase 1+) y, para producción real, la capa de seguridad de Fase 4 (AuthN/AuthZ) de la que el agente depende para no inventarse su propio usuario — ver §4.5. |
| Interfaz de voz, notificaciones push del agente, acceso multi-tenant/multi-usuario concurrente | No planeado; un solo usuario sintético basta para demostrar cada outcome. |

### 2.2 Qué no entra todavía en cada sub-fase (se resuelve en la siguiente)

| Sub-fase | Explícitamente fuera (entra en) |
|---|---|
| AI-1 | Herramientas reales sobre datos de negocio (AI-2); streaming (AI-3); gestión de historial/presupuesto de tokens (AI-4); un segundo adaptador de `LlmClient` (ver §4.2 — la interfaz es provider-agnostic desde AI-1, Bedrock no se implementa en ningún punto de este spec). |
| AI-2 | Streaming de las llamadas a herramienta (AI-3); exposición MCP de estas mismas tools (futuro, §8). |
| AI-3 | Persistencia de conversaciones entre sesiones del navegador (fuera de todo el roadmap — se mantiene en memoria del proceso, se pierde al reiniciar `agent-service`; nunca se especifica persistencia de chat en este documento). |
| AI-4 | Evaluación automática de que el resumen/recorte no pierda información relevante (eso es, en parte, lo que AI-5 mide indirectamente vía exactitud numérica). |
| AI-5 | Evals adversariales de seguridad (prompt injection) **a escala de dataset dedicado** — ese trabajo sistemático es de la sub-fase de producción/seguridad, futura (§8). AI-5 solo incluye 1–2 casos adversariales dentro de su dataset normal (tarea 22), evaluados contra el modelo real junto con el resto; AI-1 (tarea 8) cubre, por separado y con `FakeLlmClient`, que el harness nunca concatena el contenido de una tool al prompt — ninguna de las dos cosas es un dataset adversarial completo. El dataset de AI-5 tampoco corre en cada push (ver outcome de AI-5): el único gate en CI es el de mecánica del harness (§3.3, `FakeLlmClient`), no el dataset de evals. |

**Regla de scope** (heredada de `specs/phase-0.md` §2 y `specs/phase-0.5.md` §2): ningún
cambio de una sub-fase introduce dependencias o abstracciones cuyo único propósito sea una
sub-fase posterior.

### 2.3 Lo que este spec no hace (seguimiento fuera de este documento)

- No modifica `docs/PROJECT.md` §7 ni el roadmap AWS. Al aprobarse este spec, se añade una
  referencia desde `PROJECT.md` (tarea separada, no incluida en §5).
- No escribe código ni ejecuta `git commit` — pedido explícito del usuario para esta
  entrega. §5 describe las tareas que se ejecutarán **después** de aprobar este documento.

---

## 3. Constraints

### 3.1 Costo y datos (transversales a las cinco sub-fases)

- **Techo de gasto de API: USD 10/mes** durante todo el aprendizaje (AI-1 a AI-5, y lo que
  venga de §8). Para
  cumplirlo:
  - El modelo por defecto en desarrollo diario es el **más económico disponible** (p. ej.
    Claude Haiku) — igual que el guardrail ya aplicado a Bedrock en `AWS-PLAN.md` §1.3
    ("modelo más económico disponible"), configurable.
  - Los evals de AI-5 corren contra el **mismo modelo que usa el agente**, vía la misma
    configuración — no se reserva un modelo distinto, de mayor capacidad, para esas
    corridas. El modelo usado en cada corrida queda registrado en el resultado versionado
    junto con la versión del system prompt (§4.7(b)), precisamente porque es configurable y
    puede cambiar entre corridas.
  - El dataset de AI-5 **nunca** corre contra el modelo real en cada push — ver §4.7. Por
    ahora corre **solo manual** (`workflow_dispatch`, §4.7(b)), con un **tope de costo por
    corrida** (p. ej. "esta corrida no gasta más de USD X"). Una corrida programada
    (nocturna) queda como opción futura, a activar solo una vez que se conozca el costo real
    de una corrida manual — no se agenda a ciegas.
  - *(Precio exacto por verificar en el momento de implementar — igual que `AWS-PLAN.md`
    pide verificar siempre en el Pricing Calculator antes de fijar un número; aquí, verificar
    el pricing vigente de la API de Anthropic antes de fijar el tope de costo por corrida de
    AI-5 y el presupuesto de tokens por usuario de la sub-fase de producción/seguridad
    (futura, §8).)*
- **Sin datos financieros reales en ningún punto.** Todo dato que el agente lee, en
  cualquier sub-fase, viene de transacciones sintéticas sembradas (fixtures de prueba o,
  cuando exista, el generador sintético de la Fase 2 del eje de producto) — nunca de una
  cuenta bancaria real ni de un agregador real.
- **Nada de esta fase depende de la Fase 1 de AWS**, salvo lo marcado explícitamente:
  - Un adaptador Bedrock de `LlmClient` **no se construye en este spec, ni siquiera como
    stub** (§4.2) — depende de que exista acceso a Bedrock en una cuenta AWS operativa
    (Fase 0.5 ya lo deja verificado como elegible, pero usarlo de verdad necesita la cuenta
    encendida). Se documenta con un ADR corto por qué se difiere, no con una clase a medias.
  - Un eventual despliegue de `agent-service` en AWS (§2.1) depende de Fase 1 (cómputo de
    aplicación) y Fase 4 (AuthN/AuthZ) — explícitamente fuera de este spec.
  - Todo lo demás (el harness, las herramientas de AI-2, streaming, evals) corre por
    completo en local vía `compose.yaml`, sin ningún recurso de AWS.

### 3.2 Plataforma y arquitectura

- Java 21, Spring Boot 4.1+, Gradle (Kotlin DSL) — mismo stack que `transaction-api` /
  `transaction-processor`. Nuevo módulo: `apps/agent-service`.
- `agent-service` **no** accede directamente a PostgreSQL. Las herramientas de AI-2 llaman a
  `transaction-api` por HTTP (ver decisión §4.4) — una sola fuente de verdad para lecturas,
  coherente con el CQRS-lite ya establecido en `specs/phase-0.md` §4.3.
- El bucle agéntico de AI-1 es **bloqueante** (sin WebFlux), por decisión explícita — ver
  §4.3. No se "arregla" a reactivo antes de que AI-3 lo necesite de verdad.
- Ninguna tool ejecuta SQL libre generado por el modelo. Toda tool tiene una firma fija
  (parámetros tipados, validados contra un JSON Schema) que se traduce a una llamada HTTP o
  a un método de repositorio concreto — nunca a una cadena SQL ensamblada con la salida del
  modelo.
- **El harness inyecta `userId`; el modelo nunca lo decide.** Ninguna tool expone `userId`
  en el JSON Schema que ve el modelo — el harness lo añade a la llamada real (HTTP o interna)
  a partir de su propio contexto de sesión, no de los argumentos que el modelo generó. Esto
  no es solo una regla de scope: es la defensa principal contra que un usuario (o una
  inyección de prompt) pida datos de otra cuenta.
  **Defensa en profundidad:** si, a pesar de no estar en el esquema, el modelo intenta de
  todos modos enviar un campo tipo `userId`/`accountId` en los argumentos de una tool (un
  modelo no está obligado a respetar el esquema), el harness lo **ignora por completo** y
  usa siempre el que él mismo inyectó — nunca una validación que solo "advierte" o que falla
  abierto. Hay una prueba dedicada a este caso (§6, AI2-V2 ampliado).
  **Punto único de inyección:** el harness inyecta `userId` en el mismo punto de código que,
  más adelante, leerá la identidad real de la sesión autenticada (Fase 4 del eje AWS,
  OAuth2/OIDC) — hoy ese punto lee un valor fijo de configuración (§4.5), pero su *forma*
  (una función `currentUserId()` que el resto del harness llama, nunca un valor que viaja
  desde el modelo) ya es la que se usará con AuthN real.
- **El contenido que devuelve una tool viaja siempre como un bloque `tool_result` propio en
  el mensaje que vuelve al modelo — nunca concatenado a la cadena del system prompt ni a
  ninguna instrucción.** Es una propiedad estructural y determinista del harness (probada
  con `FakeLlmClient`, sin modelo real de por medio — AI-1, tarea 8): es la precondición
  para que cualquier defensa de prompt injection tenga sentido, pero no es esa defensa en
  sí misma. Que el **modelo real** efectivamente ignore una instrucción adversaria dentro de
  ese bloque es una propiedad del modelo, no del harness, y se mide por separado contra el
  modelo real en AI-5 (dataset, tarea 22) — mezclar ambas cosas en una sola prueba habría
  sido probar dos propiedades distintas con una herramienta que solo puede medir una.

### 3.3 Calidad y pruebas

- Cada sub-fase entrega **test unitario + test de integración**, igual que el resto del
  proyecto (`CLAUDE.md` Conventions). Para `agent-service`:
  - Unitario: el bucle agéntico, el parseo/validación de argumentos de tool, la estrategia
    de recorte de historial — con un `LlmClient` **falso** (determinista, sin red).
  - Integración con Testcontainers: al menos un test por tool de AI-2 que levanta Postgres
    real (con el esquema de `transaction-processor`) y una instancia real de `transaction-api`
    apuntándole, siembra filas, y verifica que la tool devuelve exactamente lo que una
    consulta SQL directa devolvería. El resto de los tests de tool (variantes de argumentos,
    casos de error) pueden mockear la llamada HTTP (WireMock) — Testcontainers se reserva
    para probar la integración real al menos una vez por tool, no para cada variante.
  - Ningún test que corre en cada push llama a la API real de Anthropic — todos usan el
    `LlmClient` falso de §4.7(a). La única llamada a un proveedor real es la corrida de
    evals de AI-5, disparada a mano (`workflow_dispatch`), nunca parte del gate de cada push
    (§4.7(b)).
- Conventional commits; trunk-based a `main`, igual que el resto del repo (`CLAUDE.md`).

### 3.4 Secretos: `ANTHROPIC_API_KEY`

- **Local — cómo llega a Spring Boot.** Spring Boot no lee un `.env` por sí solo. El
  mecanismo es el que ya usa el resto del repo para configuración por entorno
  (`CLAUDE.md`: "sin secretos hardcodeados… configuración por variables de entorno /
  docker-compose"): un `.env` en la raíz del repo, junto a `compose.yaml` — Docker Compose
  lo carga de forma nativa para interpolar variables dentro del propio `compose.yaml`. El
  servicio `agent-service` en `compose.yaml` declara
  `environment: [ "ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}" ]`, que Compose resuelve desde
  ese `.env` e inyecta como variable de entorno real del contenedor; Spring Boot la expone
  automáticamente como la propiedad `ANTHROPIC_API_KEY` (*relaxed binding*), referenciada en
  `application.yml` como `${ANTHROPIC_API_KEY}` donde se configure `AnthropicLlmClient`.
  Para correr `agent-service` fuera de `compose` (p. ej. `./gradlew bootRun` directo), la
  variable se exporta en el shell o en la configuración de ejecución del IDE — no hay un
  `.env` de Spring que lo haga por uno. `.env` se añade a `.gitignore` si no está ya
  cubierto por un patrón existente. Además, se añade `Read(.env)` (y cualquier patrón
  equivalente, p. ej. `Read(**/.env)`) al bloque `deny` de `.claude/settings.json` — así ni
  este propio asistente puede leer el valor de la clave del disco, defensa adicional contra
  que termine citada en una respuesta o un log de sesión.
- **CI — GitHub Environment, no un secreto de repositorio.** Un secreto de repositorio es
  visible para **cualquier** workflow que corra sobre el repo — demasiado amplio para una
  clave de API real. En su lugar: un **GitHub Environment** llamado `evals`, con
  `ANTHROPIC_API_KEY` configurado como *environment secret* (no secreto de repositorio) y
  un **required reviewer** (el usuario) — cada disparo del workflow queda en espera hasta
  que esa aprobación llega, nunca corre solo porque alguien lo disparó. **Solo** el job de
  evals (tarea 25) declara `environment: evals` en su definición de `ci.yml`/workflow
  separado; ningún otro job (y mucho menos `ci.yml`'s `build`/`lint-infra`) tiene ese
  `environment:`, así que ninguno puede resolver el secreto. Mismo espíritu de permisos
  mínimos por job que ya usa `specs/phase-0.5.md` §4.3 para el rol OIDC de Terraform, llevado
  un paso más allá con la aprobación manual que un Environment añade.
- Ningún test ni fixture de evals contiene la clave en texto plano; los tests que usan
  `LlmClient` falso (§4.7(a)) no la necesitan en absoluto.

---

## 4. Decisiones previas

Decisiones ya tomadas (o resueltas en este documento) y su porqué, para no re-litigarlas al
implementar. Las marcadas *(abierta)* quedan para que el usuario las confirme al revisar
este spec — están resueltas con una recomendación, no en blanco, siguiendo la regla de
`CLAUDE.md` de señalar la ambigüedad en vez de adivinar en silencio.

### 4.1 Harness propio antes que framework
El objetivo de aprendizaje declarado es *harness engineering*, no "tener un agente
funcionando lo antes posible". Por eso AI-1 a AI-5 se construyen a mano sobre la API de
mensajes cruda, y la comparación con Spring AI/LangChain4j (que habría sido el camino más
rápido) se **aplaza deliberadamente** a la sub-fase opcional de §8 ("Trabajo futuro"), al
final, cuando ya hay algo propio y entendido con lo que comparar. Construir el
framework-de-comparación primero habría ocultado exactamente lo que se quiere aprender (qué
hace el loop por debajo).

### 4.2 `LlmClient`: interfaz provider-agnostic, sin segundo adaptador ni stub
`LlmClient` es la única abstracción de proveedor del harness (método central, algo como
`send(List<Message> history, List<ToolDefinition> tools) -> ModelResponse`, con
`ModelResponse` modelando tanto texto como una o más solicitudes de tool-use). **Decisión
revisada (aprobada por el usuario):** en este spec existe **una sola** implementación,
`AnthropicLlmClient` (Messages API, HTTP), construida en AI-1. No se crea una
`BedrockLlmClient`, ni siquiera como clase *stub* que lance
`UnsupportedOperationException` — una clase que existe solo para no hacer nada es deuda
disfrazada de diseño, no diseño. En su lugar, la tarea 4 de §5 escribe un **ADR corto**
(`docs/architecture/adr/`) que documenta: por qué la interfaz se diseñó provider-agnostic
desde el día 1 (para no acoplar el harness a la forma exacta de la Messages API de
Anthropic), por qué Bedrock queda fuera de este spec (depende de una cuenta AWS operativa,
§3.1), y qué debe ser cierto para que valga la pena escribir el segundo adaptador (cuenta
AWS activa + una razón real de negocio para cambiar de proveedor, no solo "porque se puede").
Escribir `BedrockLlmClient` de verdad, cuando corresponda, es trabajo de un spec futuro que
cite este ADR, no de este documento.

### 4.3 Bucle agéntico bloqueante en AI-1; reactivo solo cuando AI-3 lo exige
Paralelo directo con la decisión ya tomada en `specs/phase-0.md` §4.6 para
`transaction-processor` (Opción A: bloquear donde el foco de la fase no es concurrencia).
Aquí aplica el mismo razonamiento: el foco de AI-1/AI-2 es la mecánica del loop y las tools,
no el modelo de concurrencia. Un `WebClient.block()` dentro de un método de servicio plano es
más fácil de depurar con logs lineales mientras se aprende el protocolo de tool-use. AI-3
introduce el endpoint SSE; en ese punto el bucle se envuelve en un `Flux` que emite un evento
por cada paso (no hace falta reescribir el loop entero a reactivo puro, solo adaptarlo al
borde de salida) — revisión explícita en AI-3, no un "ya veremos".

**Nota para AI-3:** evaluar `spring.threads.virtual.enabled` en ese punto, no antes — el
loop bloqueante espera varios segundos por turno a la respuesta del LLM (I/O de red, no
CPU-bound), exactamente el patrón que los hilos virtuales (Project Loom) favorecen. No se
activa en AI-1: es una nota para revisar cuando el endpoint se envuelve para streaming, no
un cambio de comportamiento de esta sub-fase.

### 4.4 Lecturas vía `transaction-api` HTTP, no acceso directo a Postgres
`agent-service` no abre su propia conexión a la base de datos del ledger. Las tools de AI-2
llaman a `transaction-api` por HTTP — extendiendo su path reactivo de lectura con los
endpoints de agregación que hoy no existen (p. ej. `GET /transactions/aggregate?period=...`,
`GET /transactions/recurring`). Razones: (1) una sola fuente de verdad para lecturas, igual
que ya establece `specs/phase-0.md` §4.3 (CQRS-lite: `transaction-api` es quien lee); (2) la
lógica de agregación queda en el servicio reactivo que ya existe y ya tiene sus tests, en vez
de duplicarse; (3) si más adelante `agent-service` se despliega en AWS (fuera de scope aquí,
§2.1), seguir llamando por HTTP a `transaction-api` es exactamente el mismo patrón que
usaría cualquier otro cliente — nada especial que desenredar.

**Aprobado por el usuario**, con un matiz explícito: los endpoints nuevos en
`transaction-api` son **estrechos y de solo lectura** — cada uno responde exactamente a una
necesidad de una tool concreta (agregar por categoría/periodo, buscar, detectar
recurrencias), no un endpoint genérico de "consulta lo que quieras". Esto implica que
**AI-2 incluye tareas en `transaction-api`**, no solo en `agent-service` — reflejado en §5.
Alternativa descartada: que `agent-service` lea Postgres directamente con su propio
repositorio R2DBC de solo lectura — más rápido de escribir al inicio, pero duplica lógica de
agregación y rompe la regla de una sola fuente de verdad para lecturas.

### 4.5 Identidad de usuario antes de que exista AuthN (Fase 4)
El harness inyecta `userId` (§3.2), pero hoy no existe sesión de usuario real en ningún
servicio — OAuth2/OIDC es Fase 4 del eje AWS, todavía no escrita. **Aprobado por el
usuario**, con un requisito explícito: mientras no exista esa capa, `agent-service` usa un
`userId` fijo de configuración (coherente con "un solo usuario sintético basta", §2.1), pero
la inyección ocurre en el **mismo punto de código** que, en Fase 4, leerá el `sub` real de
Cognito/JWT — una función única (p. ej. `currentUserId()`) que hoy devuelve el valor de
configuración y que ninguna tool ni el modelo pueden invocar ni sobrescribir. Cuando Fase 4
llegue, solo esa función cambia de implementación; ninguna tool se toca. La prueba de
defensa en profundidad de §3.2 (el harness ignora un `userId` que el modelo intente colar en
los argumentos) corre contra este mismo punto, para que quede probado desde ya con el valor
fijo, no solo documentado como intención para cuando haya AuthN real.

### 4.6 Dataset de evals: fixtures propios, no el generador sintético del producto
El generador sintético de transacciones (`AWS-PLAN.md`, Fase 2 del eje de producto) todavía
no existe como servicio. El dataset de AI-5 (20–30 casos) usa sus **propios** fixtures
(SQL/JSON versionados en `apps/agent-service/src/test/resources/evals/`), independientes de
ese generador — evita que el eje de IA quede bloqueado esperando una pieza de otro eje.
Si el generador sintético llega primero, se evalúa reutilizarlo como fuente de semillas en
una revisión posterior, no se fuerza aquí.

### 4.7 `FakeLlmClient` tiene dos usos distintos — no uno solo (decisión corregida)
La v1 de este spec trataba el "modo falso" como una sola cosa que servía tanto para no gastar
en CI como para evaluar al agente. **Eso es incorrecto y se corrige aquí**: un guion escrito
a mano ya conoce la respuesta esperada, así que no puede medir si el **modelo real** elige
bien una herramienta — evaluaría el guion, no el agente. `FakeLlmClient` tiene exactamente
dos usos, separados:

**(a) Pruebas de mecánica del harness (AI-1, corre en cada push, costo cero).**
Un `FakeLlmClient` escrito a mano devuelve, para cada test, una secuencia guionada de
respuestas (texto / tool-use / error) que ejercita el *código del harness*: el loop corta en
el límite de iteraciones, un error de tool se serializa y vuelve al modelo, un timeout de
tool se respeta. Aquí el guion es correcto por diseño — el test lo escribe para probar una
rama de código concreta, no para medir inteligencia del modelo. Esto es lo que ya cubre la
tarea 8 de AI-1; AI-5 no reintroduce este uso.

**(b) Evals de AI-5 (manual, contra el modelo real, con tope de costo).**
La selección de herramienta, la exactitud numérica y la latencia/costo del dataset de AI-5
solo tienen sentido medidas contra el **`AnthropicLlmClient` real** — nunca contra el falso.
Corre contra el **mismo modelo configurado para el agente** (§3.1) — no un modelo distinto
reservado para esto. Cada corrida:
- Respeta un tope de costo por corrida (§3.1), calculado a partir del tamaño del dataset y
  el precio vigente del modelo usado (verificar antes de fijar el número).
- Guarda un resultado **versionado**: la versión del system prompt (AI-4) vigente en esa
  corrida, **el modelo usado**, las tres métricas (selección de tool, exactitud numérica,
  latencia/costo), y un timestamp — para poder comparar, por ejemplo, si cambiar el system
  prompt (o el modelo) mejoró o empeoró el *pass rate* frente a la corrida anterior.
- No es parte del gate de cada push (ningún job de `ci.yml` la dispara en
  `push`/`pull_request`) — por ahora corre **solo a mano**, vía `workflow_dispatch` en
  GitHub Actions o un comando local documentado. **Una corrida programada (nocturna) queda
  como opción futura**: se activa solo una vez que una serie de corridas manuales deje claro
  cuánto cuesta realmente una corrida completa — agendarla antes sería adivinar el número
  que el tope de costo (§3.1) necesita para tener sentido.

### 4.8 Streaming por `POST`, no por `GET`
AI-3 expone el endpoint de streaming como `POST /pulse/ask/stream`, no `GET`. Con `GET`, la
pregunta del usuario tendría que viajar en la URL (query param) — y una URL se guarda en el
historial del navegador, en los logs de acceso de cualquier proxy/balanceador intermedio, y
potencialmente en herramientas de observabilidad que registran la URL completa de cada
request. Una pregunta financiera ("¿cuánto gasté en...", con montos y comercios) no debería
poder aparecer en ningún log por el simple hecho de haber viajado en la URL. La consecuencia
técnica: el navegador no puede usar `EventSource` (que solo soporta `GET`) para consumir el
stream — AI-3 usa `fetch` con el cuerpo de la respuesta leído como `ReadableStream` en su
lugar, parseando los eventos SSE a mano del cuerpo. Es más código que `EventSource`, pero la
alternativa (URL con datos sensibles) no es aceptable para un endpoint financiero.

---

## 5. Desglose de tareas

Atómicas, agrupadas por sub-fase y ordenadas por dependencia dentro de cada una. Cada una
≈ un commit (conventional). Las tareas aquí **no se ejecutan en esta entrega** — se listan
para que la implementación, una vez aprobado el spec, las siga en orden.

### AI-1 — Harness mínimo
1. `chore: scaffold agent-service module` — nueva carpeta `apps/agent-service`, Spring Boot
   4.1+, Java 21 toolchain, `/actuator/health`, patrón de log con `traceId`/`spanId` (mismo
   setup que `transaction-api`/`transaction-processor`).
2. `chore(agent): local secret handling for ANTHROPIC_API_KEY` — `.env` en la raíz del repo
   (§3.4): confirmar que `.gitignore` lo cubre (o añadir el patrón si no); añadir
   `Read(.env)` al bloque `deny` de `.claude/settings.json`; añadir
   `agent-service` a `compose.yaml` con `environment: ["ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}"]`
   para que Compose la resuelva desde ese `.env` e inyecte la variable real al contenedor.
   Antes de escribir ningún adaptador que use la clave.
3. `feat(agent): LlmClient interface and Anthropic adapter` — interfaz `LlmClient` (§4.2),
   `AnthropicLlmClient` sobre la Messages API (tool-use), leyendo `${ANTHROPIC_API_KEY}` en
   `application.yml` (la variable de entorno que inyecta la tarea 2, vía Compose o vía el
   shell/IDE si se corre fuera de `compose`); modelo económico por defecto, configurable
   (§3.1). Sin segundo adaptador.
4. `docs: ADR — LlmClient provider-agnostic, Bedrock deferred` — el ADR corto de §4.2, en
   `docs/architecture/adr/`.
5. `feat(agent): tool registry and schema validation` — `ToolDefinition` (nombre,
   descripción, JSON Schema de argumentos), registro de tools, validación de argumentos
   recibidos del modelo antes de ejecutar.
6. `feat(agent): agentic loop with iteration limit and tool timeout` — el bucle descrito en
   §1 (AI-1); límite de iteraciones y timeout por tool configurables
   (`application.yml`), con valores por defecto razonables a fijar en la implementación.
   Errores de tool (timeout, excepción, validación fallida) se serializan como
   `tool_result` con error, nunca se propagan como excepción no controlada al llamador.
7. `feat(agent): test tool and ask endpoint` — tool trivial (`get_current_time` o
   equivalente) para probar el mecanismo sin depender de AI-2; `POST /pulse/ask` síncrono
   (sin streaming todavía).
8. `test(agent): unit + Testcontainers-free integration with FakeLlmClient` — unitarios del
   loop, el registro de tools y la validación de esquemas; un test de "bucle infinito
   forzado" que verifica el corte en el límite de iteraciones. Este es el uso (a) de
   `FakeLlmClient` descrito en §4.7 — mecánica del harness, corre en cada push, costo cero.

### AI-2 — Herramientas de solo lectura
9. `feat(api): narrow read-only aggregate and recurring-charges endpoints` — en
   `transaction-api` (decisión §4.4, aprobada): endpoints estrechos y de solo lectura, uno
   por necesidad concreta de una tool —
   `GET /transactions/aggregate?period=...&groupBy=category`,
   `GET /transactions/recurring`, `GET /transactions/search?...`. Reactivo, mismo estilo que
   el `GET /transactions` ya existente; tests unit + Testcontainers propios de ese módulo.
10. `feat(agent): search-transactions tool`
11. `feat(agent): aggregate-by-category-or-period tool`
12. `feat(agent): detect-recurring-charges tool`
13. `feat(agent): single userId-injection point, ignored if model-supplied` — la función
    única (§4.5) que inyecta el `userId` configurado en cada llamada HTTP de tool; ningún
    JSON Schema lo expone; si el modelo envía de todos modos un campo tipo
    `userId`/`accountId` en los argumentos, el harness lo descarta y usa siempre el inyectado
    (defensa en profundidad de §3.2).
14. `test(agent): tool unit tests (WireMock) + one Testcontainers integration per tool, plus
    the userId-override-ignored test` — según §3.3 y §4.5/§3.2 (AI2-V2 ampliado, §6).

### AI-3 — Streaming
15. `feat(agent): typed SSE event model` — `token`, `tool_call_started`,
    `tool_call_result`, `final`, `error` (§1 AI-3).
16. `feat(agent): wrap agentic loop for streaming over POST` — adaptación descrita en §4.3;
    endpoint `POST /pulse/ask/stream` (la pregunta viaja en el cuerpo, nunca en la URL ni en
    query params — ver §4.8), SSE como formato de los eventos del cuerpo de la respuesta.
17. `feat(web): Pulse Agent chat panel` — nuevo panel en `apps/web`, consumo del stream vía
    `fetch` con `ReadableStream` (no `EventSource`, que solo soporta `GET` — ver §4.8),
    render incremental de tokens y de las insignias de tool-call.
18. `test(agent): SSE event ordering and error-event integration test`.

### AI-4 — Context engineering
19. `feat(agent): versioned system prompt file` — archivo versionado (no Java inline),
    cargado al arrancar, con su propia fecha/versión en el encabezado.
20. `feat(agent): conversation history with token budget and trim/summary strategy` —
    estrategia explícita a definir en la implementación (p. ej. recorte de los turnos más
    antiguos + resumen de lo recortado en una línea de system); test que fuerza superar el
    límite.
21. `feat(agent): per-turn token and cost logging` — `inputTokens`, `outputTokens`,
    `estimatedCostUsd` por turno (§3.1, verificar pricing vigente antes de fijar la
    constante de costo por token).

### AI-5 — Evals
22. `feat(agent): eval dataset format and fixtures` — 20–30 casos (§4.6), formato versionado
    (pregunta, semilla, tool esperada, criterio de aceptación); incluye 1–2 casos
    adversariales (instrucción embebida en `merchant`, §3.2) evaluados contra el modelo real
    junto con el resto — distinto del test determinista de AI-1 (tarea 8), que no usa modelo
    real.
23. `feat(agent): eval runner against the real LlmClient` — tool selection, numeric
    accuracy, latency/cost; tarea de Gradle dedicada (`evalRun`); usa el mismo modelo
    configurado para el agente (§3.1); enforcement del tope de costo por corrida; nunca usa
    `FakeLlmClient` (§4.7(b)).
24. `feat(agent): versioned eval result storage` — guarda, por corrida, la versión del
    system prompt (AI-4), **el modelo usado**, las tres métricas y un timestamp, en un
    formato comparable entre corridas (p. ej. JSON/markdown versionado en el repo).
25. `ci: manual-only eval workflow via a GitHub Environment with required reviewer` —
    workflow separado de `ci.yml` (§3.4), disparado **solo** por `workflow_dispatch` — nunca
    por `push`/`pull_request` ni por un cron. Su job declara `environment: evals`
    (Environment nuevo, con `ANTHROPIC_API_KEY` como *environment secret* y un *required
    reviewer* — el usuario); ningún otro job/workflow, `ci.yml` incluido, declara ese
    `environment:` ni puede resolver el secreto. Una corrida programada (nocturna) se deja
    para una tarea futura, una vez medido el costo real de varias corridas manuales.
26. `docs: eval cadence and cost-cap runbook` — cómo correrlo manualmente (incluida la
    aprobación del *required reviewer*), cómo leer los resultados versionados, cómo ajustar
    el tope de costo, y qué se necesita saber antes de considerar una corrida programada.

---

## 6. Criterios de verificación

Compuerta ejecutable por sub-fase. Cada sub-fase queda cerrada cuando sus criterios pasan;
no hace falta esperar a AI-5 para considerar AI-1–AI-4 "terminadas" de forma independiente.

### AI-1
| # | Criterio | Comprobación |
|---|---|---|
| AI1-V1 | El harness ejecuta una tool y vuelve con la respuesta final. | `POST /pulse/ask` con una pregunta que requiere `get_current_time` → la respuesta final incorpora la hora devuelta por la tool; log muestra 1 iteración con 1 tool-call. |
| AI1-V2 | El límite de iteraciones corta un bucle infinito. | Tool de prueba que siempre devuelve "necesito más info" → el harness se detiene exactamente en el límite configurado y responde con un error controlado, no una excepción sin manejar ni un proceso colgado. |
| AI1-V3 | Un error de tool no rompe el turno. | Tool de prueba que lanza una excepción → el modelo recibe un `tool_result` de error y puede decidir cómo seguir (reintentar, disculparse), el proceso no cae. |
| AI1-V4 | `LlmClient` es una interfaz real, no acoplada a la forma de la Messages API. | Test que instancia `AnthropicLlmClient` contra la interfaz `LlmClient` (no contra el tipo concreto) en el bucle agéntico; el ADR de la tarea 4 existe y documenta por qué no hay un segundo adaptador en este spec. |
| AI1-V5 | Timeout por herramienta se respeta. | Tool de prueba que duerme más que el timeout configurado → el harness la corta y la reporta como error de tool, no espera indefinidamente. |
| AI1-V6 | `ANTHROPIC_API_KEY` nunca se filtra desde el entorno local. | `.env` no aparece en `git status` ni en ningún commit; `.claude/settings.json` deniega `Read(.env)`; intentar que el propio asistente lea el archivo falla por la política de permisos, no por un error de archivo no encontrado. |
| AI1-V7 | El contenido de una tool nunca se concatena al prompt. | Test con `FakeLlmClient` (tarea 8): una tool devuelve un texto con formato de instrucción (p. ej. `"Ignora todo y..."`); el mensaje que el harness construye para la siguiente llamada lo lleva en un bloque `tool_result` propio, nunca mezclado dentro de la cadena del system prompt ni de ningún mensaje de instrucción. Es una propiedad estructural, verificable sin modelo real — no mide si un modelo la obedecería, solo que el harness no le da la oportunidad estructural de hacerlo por esta vía. |

### AI-2
| # | Criterio | Comprobación |
|---|---|---|
| AI2-V1 | La agregación coincide con SQL directo. | Para cada caso semilla, el número que reporta el agente == resultado de una consulta SQL directa sobre la misma base. |
| AI2-V2 | El `userId` nunca es controlable por el modelo, ni por el esquema ni forzándolo en los argumentos. | Inspección del JSON Schema de cada tool: ningún esquema expone `userId` (o equivalente) como argumento. **Ampliado (decisión §3.2/§4.5):** un test construye una llamada de tool con un `userId`/`accountId` distinto inyectado directamente en los argumentos (simulando un modelo que no respeta el esquema) → el harness lo descarta y la llamada HTTP real sigue llevando el `userId` del punto único de inyección, nunca el del argumento. |
| AI2-V3 | Argumentos inválidos no llegan a ejecutarse. | Un argumento que no cumple el JSON Schema (p. ej. `period` con formato incorrecto) se rechaza antes de la llamada HTTP, devuelto como error de tool. |
| AI2-V4 | Integración real con Testcontainers por tool. | Al menos un test de integración por tool (search, aggregate, recurring) con Postgres + `transaction-api` reales vía Testcontainers, verificando datos sembrados. |
| AI2-V5 | Los endpoints nuevos de `transaction-api` son estrechos, no una consulta genérica. | Inspección de la tarea 9: cada endpoint tiene una forma fija (parámetros tipados y acotados), ninguno acepta un fragmento de consulta o filtro arbitrario del lado del cliente. |

*(La prueba de prompt injection no vive en AI-2: la propiedad estructural del harness es
AI1-V7, y el comportamiento del modelo real ante 1–2 casos adversariales es AI5-V7 — ver
§4, decisión de la Revisión v4.)*

### AI-3
| # | Criterio | Comprobación |
|---|---|---|
| AI3-V1 | Los eventos llegan en orden y tipados. | Un test de integración sobre `POST /pulse/ask/stream` recibe, en orden, ≥1 `token`, al menos un `tool_call_started`/`tool_call_result` si la pregunta usa una tool, y exactamente un `final`. |
| AI3-V4 | La pregunta nunca viaja en la URL. | Inspección del endpoint (tarea 17): `POST`, sin query params con el texto de la pregunta; un test confirma que la URL del request no contiene el contenido de la pregunta. |
| AI3-V2 | La web renderiza en vivo. | En el navegador, al hacer una pregunta se observan tokens aparecer progresivamente y una insignia de tool-call antes del texto final (verificación manual, igual que V8 de `specs/phase-0.md`). |
| AI3-V3 | Un error de proveedor se traduce en un evento `error`, no en una conexión colgada. | Simular una falla del `LlmClient` a mitad de stream → el cliente recibe un evento `error` bien formado y la conexión SSE se cierra limpia. |

### AI-4
| # | Criterio | Comprobación |
|---|---|---|
| AI4-V1 | El system prompt es un archivo versionado, no código. | El archivo existe fuera de `.java`, con versión/fecha en el encabezado; cambiarlo no requiere recompilar lógica de negocio (solo reiniciar o recargar config). |
| AI4-V2 | El recorte/resumen se activa al superar el límite. | Una conversación de prueba diseñada para superar el límite de tokens → log muestra el recorte/resumen aplicado; el turno siguiente sigue siendo coherente (manual o con criterio de eval). |
| AI4-V3 | Cada turno registra tokens y costo. | Cada respuesta deja una entrada con `inputTokens`, `outputTokens`, `estimatedCostUsd` (log estructurado o tabla), verificable para al menos un turno de prueba. |

### AI-5
| # | Criterio | Comprobación |
|---|---|---|
| AI5-V1 | El runner mide contra el mismo modelo del agente, nunca contra `FakeLlmClient`. | Inspección de la tarea 23: el `evalRun` inyecta `AnthropicLlmClient` configurado igual que el agente; no existe una ruta de configuración que le permita usar el falso ni un modelo distinto al del agente. |
| AI5-V2 | La corrida solo se dispara a mano, nunca en un push normal. | Inspección de `ci.yml` y del workflow de evals (tarea 25): ningún job de `push`/`pull_request` ejecuta `evalRun`; el workflow solo tiene `workflow_dispatch` como disparador — sin `schedule`. |
| AI5-V3 | El tope de costo por corrida se respeta. | Una corrida real mide el costo acumulado contra el tope configurado (§3.1) y se detiene/alerta si lo va a superar, en vez de seguir gastando sin control. |
| AI5-V4 | El dataset cubre selección de tool, exactitud numérica y al menos un caso de latencia/costo medido. | Inspección del dataset: cada categoría tiene ≥1 caso; el reporte del runner desglosa las tres métricas por separado, no un único agregado. |
| AI5-V5 | Los resultados quedan versionados y son comparables entre corridas. | Dos corridas sucesivas, con un cambio deliberado al system prompt entre ambas, producen dos resultados guardados (tarea 24) con versiones de prompt distintas (y el modelo usado en cada una) con métricas que se pueden diferenciar una de otra. |
| AI5-V6 | El secreto de la API solo está disponible para el entorno de evals, con aprobación requerida. | Inspección de la configuración del repositorio: existe un GitHub Environment `evals` con `ANTHROPIC_API_KEY` como *environment secret* y un *required reviewer* configurado; el workflow de la tarea 25 declara `environment: evals` en su job; ningún otro job (incluidos los de `ci.yml`) declara ese `environment:` ni puede resolver el secreto; disparar el workflow deja la corrida en espera hasta que el reviewer aprueba. |
| AI5-V7 | El modelo real no se desvía en los casos adversariales del dataset. | Los 1–2 casos de la tarea 22 con instrucción embebida en `merchant`, evaluados en una corrida real: el agente no revela el system prompt ni se desvía de la pregunta original — a diferencia de AI1-V7, esto mide al **modelo**, no al harness. |

---

## 7. Notas de trazabilidad de decisiones abiertas

Todas las decisiones de este spec quedaron **aprobadas por el usuario**, en tres rondas de
revisión el 2026-11-06 (v1→v2→v3→v4, ver cabecera). No queda ninguna marcada "pendiente de
confirmación" — lo que sigue abierto es solo lo que este documento deja fuera a propósito
(valores numéricos concretos, precio vigente de la API).

| Decisión | Estado | Se revisa en |
|---|---|---|
| §4.2 `LlmClient` provider-agnostic, sin segundo adaptador ni stub; Bedrock vía ADR | **aprobada** | Cuando exista una cuenta AWS operativa y una razón de negocio real para el segundo adaptador |
| §4.3 Bucle agéntico bloqueante en AI-1, reactivo solo en el borde SSE de AI-3 | **aprobada** (paralelo directo a `specs/phase-0.md` §4.6) | AI-3 |
| §4.4 Tools de AI-2 vía HTTP a `transaction-api` (endpoints estrechos de solo lectura), no acceso directo a Postgres | **aprobada** | — |
| §4.5 `userId` fijo de configuración, inyectado en el mismo punto que usará la AuthN real de Fase 4 | **aprobada**, con el criterio de verificación AI2-V2 ampliado | Fase 4 del eje AWS (cuando exista sesión real) |
| §4.6 Fixtures propios de evals, independientes del generador sintético del producto | **elegida** | Si el generador sintético (Fase 2) llega antes que AI-5 |
| §4.7 `FakeLlmClient` con dos usos separados: mecánica del harness (CI, cada push) vs evals (mismo modelo del agente, solo `workflow_dispatch` por ahora, tope de costo y resultados versionados) | **aprobada** | Corrida programada (nocturna): opción futura, una vez medido el costo real |
| §3.2 Propiedad estructural "tool result nunca se concatena al prompt" separada de "el modelo real resiste una instrucción adversaria" (AI1-V7 vs AI5-V7) | **aprobada (v4)** — corrige la v3, que las mezclaba en una sola prueba de AI-2 | Cobertura sistemática: trabajo futuro, §8 |
| §3.4 Manejo de `ANTHROPIC_API_KEY`: `.env` local (deny en `.claude/settings.json`) → `compose.yaml` → variable de entorno real → *relaxed binding* de Spring Boot; CI vía GitHub Environment `evals` (environment secret + required reviewer), no secreto de repositorio | **aprobada (v4)** — corrige la v3, que no especificaba el mecanismo local ni usaba un Environment | — |
| §4.8 Streaming por `POST`, no `GET` (la pregunta nunca viaja en la URL) | **aprobada** | — |
| Valores concretos de límite de iteraciones, timeout por tool, presupuesto de tokens por usuario y tope de costo por corrida de evals | **sin fijar en este spec** — se proponen en la implementación con valores razonables, ajustables por configuración | AI-1 (iteraciones/timeout), AI-5 (tope por corrida), sub-fase de producción/seguridad futura (presupuesto por usuario, §8) |
| Precio vigente de la API de Anthropic para calcular costo por turno (AI-4) y tope de costo por corrida (AI-5) | **sin verificar** — este documento no asume un precio; verificar en la página de pricing vigente antes de fijar constantes | AI-4, AI-5 |

---

## 8. Trabajo futuro

Fuera de este spec. Cada línea es una sub-fase completa con sus propios outcomes, scope,
constraints, decisiones, tareas y criterios — a especificar en un documento aparte cuando
corresponda, no aquí.

- **MCP** (antes AI-6): exponer las mismas herramientas de solo lectura de AI-2 como
  servidor MCP, y documentar la diferencia entre tool use interno y MCP. **Será lo primero
  del siguiente spec** de esta línea de trabajo, una vez cerradas AI-1–AI-5.
- **Producción y seguridad** (antes AI-7): defensa contra prompt injection **sistemática**
  (más que la propiedad estructural de AI1-V7 o los 1–2 casos de AI5-V7 — un dataset
  adversarial propio, con más casos y más variantes), presupuesto de tokens por usuario a
  nivel de aplicación,
  un span de OpenTelemetry por iteración y por herramienta, y los modos de falla de
  proveedor caído / herramienta lenta documentados y probados de forma sistemática (no solo
  los casos puntuales ya cubiertos por AI1-V2/V5).
- **Comparación con framework** (antes AI-8, opcional): reimplementar un subconjunto
  pequeño del harness con Spring AI o LangChain4j y un ADR que compare ambos enfoques —
  tiene sentido solo después de haber operado el harness propio el tiempo suficiente para
  comparar con criterio informado, no por adelantado.

*(A2A sigue mencionado únicamente en §2.1, como trabajo futuro no planeado — no forma parte
de esta lista porque ninguna sub-fase, ni las de este spec ni las de arriba, lo requiere.)*

---

## 9. Mapa de aprendizaje

Qué concepto de ingeniería de agentes practica cada sub-fase — el objetivo declarado de esta
línea de trabajo es *harness engineering*, no solo "tener un agente que funcione".

| Sub-fase | Concepto principal | Por qué esa sub-fase y no otra lo enseña |
|---|---|---|
| **AI-1** | Bucle agéntico (tool-use loop) + abstracción de proveedor | Es el mecanismo crudo: pedir herramienta → ejecutar → devolver resultado → repetir, sin ningún framework que lo esconda. La interfaz `LlmClient` enseña a no acoplar el harness a un proveedor específico desde la primera línea. También enseña una distinción sutil (AI1-V7): que el resultado de una tool viaje en un bloque propio, nunca concatenado al prompt, es una garantía del *harness* — no dice nada todavía sobre si el *modelo* resistiría una instrucción adversaria ahí dentro (eso se mide en AI5-V7, contra el modelo real). |
| **AI-2** | Tool use / function calling con límites de seguridad | Esquemas JSON validados y la regla "el modelo nunca decide `userId`" son la lección central de *por qué* un harness de herramientas necesita una frontera de confianza explícita, no solo "funciones que el modelo puede llamar". |
| **AI-3** | Streaming y eventos tipados | Pasar de "espera y devuelve todo" a "emite progreso" obliga a modelar el estado del loop como una secuencia de eventos observables desde afuera — la base de cualquier UI de agente seria. |
| **AI-4** | Context engineering | Versionar el system prompt y gestionar el historial bajo un presupuesto de tokens es la disciplina que separa "un prompt que funcionó una vez" de un sistema que se puede operar y depurar con el tiempo. |
| **AI-5** | Evals | Sin un dataset y un runner, cualquier cambio al prompt o al harness es una apuesta. Medir selección de tool, exactitud y costo por separado enseña que "el agente respondió bien" no es una sola métrica — y que un guion falso no puede medir esa primera métrica, solo la mecánica alrededor de ella (§4.7). |

Las sub-fases de §8 ("Trabajo futuro") seguirán este mismo mapa cuando se especifiquen: MCP
enseña la diferencia entre tool use interno y un protocolo estándar; producción/seguridad
aplica las *-ilities* del resto del lab (seguridad, trazabilidad, resiliencia) a un
componente cuyo comportamiento depende de un modelo; la comparación con framework solo
rinde después de haber sentido el peso de no tener uno.
