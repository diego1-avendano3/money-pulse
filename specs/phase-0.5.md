# Spec — Fase 0.5: Paso 0 de AWS (cuenta, guardrails de costo, bootstrap y CI)

> **Fuente de verdad de la Fase 0.5.** Derivado de `docs/aws/AWS-PLAN.md` §1 (guardrails)
> y §12 (roadmap, fila **0.5**), y de `CLAUDE.md` (cost guardrail, convenciones). El código
> se deriva de este spec; si un requisito cambia, se edita aquí primero y se regenera lo
> afectado. Las tareas manuales de consola están en
> [`docs/aws/RUNBOOK.md`](../docs/aws/RUNBOOK.md) — este documento solo referencia sus
> pasos y define los criterios de verificación (§6).
>
> Estado: **aprobado, en ejecución** (cuenta creada el <fecha>). Ejecución en curso
> siguiendo [`docs/aws/RUNBOOK.md`](../docs/aws/RUNBOOK.md) (tareas manuales) y el
> desglose de §5 (tareas de código).
> Fase anterior: Fase 0 (completada, ver `specs/phase-0.md`). Fase siguiente: Fase 1
> (`specs/phase-1.md`, aún no escrita; ver `docs/aws/AWS-PLAN.md` §12).
>
> **Revisión v2** (tras comentarios de diseño): corrige el argumento de "dependencia
> circular" del estado de Terraform (§4.1), separa el usuario humano del rol de
> administración con MFA (§4.2), ajusta permisos y *trust policy* del rol OIDC (§4.3),
> corrige el conteo de actividades de crédito y el manejo del budget (§4.5), vuelve
> bloqueante tflint/checkov (§4.4), y mueve el runbook manual a su propio archivo (§4.6).

---

## 1. Outcomes

Resultados observables al terminar la fase. No describen implementación.

1. **Cuenta operativa con guardrails de costo activos.** Existe una cuenta AWS en **Free
   plan**, con MFA en root (root nunca se vuelve a usar) y un usuario IAM `diego-admin`
   con MFA cuyo único permiso directo es `sts:AssumeRole` sobre un rol de administración
   (§4.2). Acceso bloqueado a cualquier región distinta de `us-east-1` **en ese rol**, y un
   *Zero-spend budget* + un budget mensual de USD 5 con alertas al 50/80/100 %, ambos bajo
   control de Terraform.
2. **Estado remoto de Terraform operativo en S3.** `infra/terraform/bootstrap/` existe, se
   aplicó una primera vez con backend local, y su estado quedó **migrado a un backend S3**
   con *locking* nativo — ya no queda ningún estado local de ningún módulo.
3. **GitHub Actions puede hacer `terraform plan` sin llaves de acceso de larga vida.**
   Existe un rol IAM asumible vía OIDC, con *trust policy* acotada a `aud` + `sub` de este
   repositorio en `main`, y permisos de solo lectura más el mínimo necesario para el
   *lockfile* de S3.
4. **CI corre en cada push y PR, y bloquea en hallazgos de infraestructura.**
   `.github/workflows/ci.yml` construye y prueba `transaction-api`/`transaction-processor`
   (Testcontainers reales) y `apps/web`, y **falla** el pipeline si tflint o checkov
   encuentran algo no suprimido explícitamente.
5. **Runbook manual ejecutado.** Todas las tareas de
   [`docs/aws/RUNBOOK.md`](../docs/aws/RUNBOOK.md) están marcadas como completadas, con la
   evidencia mínima necesaria registrada (sin secretos).
6. **Datos de entrada para fases futuras registrados.** La cuota de concurrencia de Lambda,
   el *throttle* de cuenta de API Gateway, y la disponibilidad de EKS, MSK y Bedrock en
   esta cuenta quedan anotados en §6 (alimentan `docs/aws/AWS-PLAN.md` §14 A1 y A6).

---

## 2. Límites de scope

Fuera de la Fase 0.5. Cada exclusión indica la fase donde entra (ver `docs/aws/AWS-PLAN.md` §12).

| Excluido | Entra en | Por qué no aquí |
|---|---|---|
| Lambda `janitor` + EventBridge Scheduler (higiene de costo automatizada: apagar RDS fuera de horario) | Fase 1 | El janitor gestiona RDS; no tiene nada que apagar/encender hasta que RDS exista (Fase 1, AWS-PLAN.md §12). La rutina de cierre de sesión es manual hasta entonces (`RUNBOOK.md`). |
| RDS, Cognito, S3+CloudFront para la PWA, HTTP API + JWT, Lambda `read-api` | Fase 1 | Primer recurso de **producto** desplegado en AWS; Fase 0.5 es solo cuenta + guardrails + pipeline, cero recursos de aplicación. |
| SQS, DynamoDB, EventBridge bus, SNS, Lambda `categorizer`/`ledger-writer` | Fase 2 | Núcleo event-driven; depende de que RDS (Fase 1) ya exista. |
| mTLS, REST API webhooks, WebSocket API, KMS (cifrado de `accountId`), Secrets Manager (rotación RDS), WAF opcional | Fase 4 | Seguridad de aplicación; no hay aplicación desplegada todavía. |
| CloudTrail, Athena sobre CloudTrail | Fase 5 | Observabilidad; depende de que haya algo que auditar más allá de la cuenta misma. |
| Resumen mensual, data lake (S3 Parquet/Glue/Athena), ECS Fargate, AppConfig opcional | Fase 6–7 | Fuera del camino crítico de esta fase por definición (AWS-PLAN.md §0). |
| Lab EKS / MSK: creación real de clúster | Fase 7 | En Fase 0.5 **solo se verifica elegibilidad** por Billing (outcome 6, `RUNBOOK.md` §3) — nunca el asistente de creación. |
| Módulos de Terraform más allá de `bootstrap/` (`api-rest-mtls`, `rds`, `dynamodb`, `sqs-with-dlq`, …) | Fases 1+ | Se escriben cuando la fase que los necesita llega. |
| `terraform apply` ejecutado desde GitHub Actions | Nunca en CI — ver §4.3 | `apply` siempre lo corre el humano, con el rol `moneypulse-admin` asumido. El workflow de *deploy* con aprobación manual (`deploy-always-on.yml`, AWS-PLAN.md §11) es de una fase posterior. |

**Regla de scope** (heredada de `specs/phase-0.md` §2): ningún cambio de esta fase debe
introducir dependencias, configuración o abstracciones cuyo único propósito sea una fase
posterior.

---

## 3. Constraints

Reglas que esta fase debe respetar. Derivadas de `CLAUDE.md` y `docs/aws/AWS-PLAN.md` §1, §9, §11.

### 3.1 Cuenta y guardrails de costo
- **Free plan únicamente** (nunca Paid plan) — AWS-PLAN.md §1.1.
- **Nunca** AWS Organizations, Control Tower, ni IAM Identity Center (SSO): cualquiera de
  los tres convierte el Free plan en Paid automáticamente y vence los créditos
  (AWS-PLAN.md §1.1, nota ⚠️).
- Región única: **`us-east-1`**, forzada por una *deny policy* (`aws:RequestedRegion`)
  **en el rol de administración**, no en el usuario (ver §3.2).
- Todo recurso de Terraform lleva las etiquetas `project=moneypulse`, `owner=diego`, y
  `env=always-on|lab`; **`ttl=<fecha>` para recursos efímeros, `ttl=none` para recursos
  permanentes** (el bucket de estado, el rol OIDC, el rol `moneypulse-admin`) — vía
  `default_tags` del provider.
- El rol de solo-plan (OIDC) **no puede** crear ninguno de los recursos de la lista negra
  de AWS-PLAN.md §1.3, ni ningún recurso en absoluto más allá de `plan`/lectura (§3.2).

### 3.2 Identidad y acceso — usuario, rol de administración, rol de CI

Tres identidades distintas, cada una con el mínimo necesario:

| Identidad | Quién/qué la usa | Permisos directos | Cómo se protege |
|---|---|---|---|
| Usuario IAM `diego-admin` | El humano, para autenticarse | **Ninguno más que `sts:AssumeRole`** sobre `moneypulse-admin` (tras el bootstrap inicial, §4.2) | MFA propio; la access key por sí sola no da acceso a ningún servicio sin pasar por el rol |
| Rol `moneypulse-admin` | El humano, asumido con MFA, para todo trabajo real de consola/CLI/Terraform `apply` | Permisos de administración | `sts:AssumeRole` exige `aws:MultiFactorAuthPresent = true`; *deny policy* por región (`aws:RequestedRegion != us-east-1`) adjunta al rol |
| Rol `moneypulse-github-plan` (OIDC) | GitHub Actions, para `terraform plan` | `ReadOnlyAccess` (AWS managed) + `s3:PutObject`/`s3:DeleteObject` **solo** sobre `<bucket>/*.tflock` | *Trust policy* con `aud` + `sub` acotados (§4.3); nunca `apply` |

**Una access key de `diego-admin` comprometida, sola, no permite nada**: sin el segundo
factor de `sts:AssumeRole` (MFA), no hay ninguna acción posible más allá de intentar
asumir el rol — y eso también exige MFA. Todo criterio de verificación que implique una
acción real en AWS (p. ej. V2) se ejecuta **con el rol `moneypulse-admin` ya asumido**, no
con las credenciales base de `diego-admin`.

### 3.3 Terraform
- Estado remoto en **S3 con *locking* nativo de S3** (`use_lockfile = true`, sin tabla
  DynamoDB de bloqueo) — AWS-PLAN.md §11. Requiere Terraform ≥ 1.11. Se fija la versión
  mínima en `infra/terraform/bootstrap/versions.tf`.
- El módulo `bootstrap/` **empieza** con backend `local` (único punto de partida posible,
  §4.1) y **termina** la fase con backend `s3`, migrado — ningún módulo se queda en backend
  local al cerrar la fase.
- Ningún recurso de Terraform se crea a mano en consola salvo lo explícitamente marcado en
  `RUNBOOK.md` (y solo porque Terraform no puede hacerlo sin credenciales previas, porque
  el crédito de "Explore AWS" exige el widget de consola, o porque AWS no lo expone como
  recurso gestionable).
- `.terraform.lock.hcl` **se versiona** (fija versiones de providers); `*.tfstate`,
  `*.tfstate.*`, `.terraform/`, `*.tfplan`, `crash.log`, `override.tf`,
  `override.tf.json` **nunca** se versionan (`.gitignore`, tarea 1) — este repo es
  público.

### 3.4 CI
- `.github/workflows/ci.yml` corre en `push` y `pull_request`. Usa el *Gradle wrapper*
  (`./gradlew`) y Testcontainers reales (Kafka + Postgres) — nunca mocks, consistente con
  `CLAUDE.md` Conventions.
- `permissions: contents: read` a nivel de **workflow**; `id-token: write` solo dentro del
  job `terraform-plan` (necesario para el OIDC de ese job únicamente — el resto del
  workflow no lo necesita).
- El job que corre `terraform plan` vía OIDC solo se dispara en `push` a `main` (nunca en
  `pull_request`): es la única forma de que la *trust policy* del rol (§4.3) tenga sentido,
  ya que el claim `sub` de un evento `pull_request` no coincide con `ref:refs/heads/main`.
- Toda Action de terceros se fija por **SHA de commit exacto**, con el tag legible como
  comentario (p. ej. `uses: actions/checkout@<sha-completo> # v4.x.y`) — nunca una
  referencia de tag mutable. El SHA exacto se resuelve al escribir el workflow, no se
  inventa en este spec.
- tflint y checkov son **bloqueantes** (decisión §4.4): el pipeline falla en rojo si hay
  hallazgos no suprimidos. Una excepción puntual se documenta inline con
  `#checkov:skip=<id>:<motivo>` (o el equivalente de tflint), nunca deshabilitando la regla
  globalmente.

---

## 4. Decisiones previas

Decisiones ya tomadas y su porqué, para no re-litigarlas. Las marcadas *(abierta)* se
revisan en la fase indicada.

### 4.1 Bootstrap del estado remoto: migración, no dependencia circular

El argumento original de este spec (v1) decía que el backend remoto no se podía usar para
`bootstrap/` porque "depende del bucket que él mismo crea" — eso es cierto **solo durante
el primer `apply`**, no después. La secuencia real:

1. **Primer `apply` con backend `local`**: `bootstrap/` crea el bucket S3 (versionado,
   SSE-S3, bloqueo de acceso público), el proveedor OIDC, el rol `moneypulse-github-plan`
   y el rol `moneypulse-admin` (§4.2), con su estado en un `.tfstate` local.
2. **Se añade el bloque `backend "s3" { ... use_lockfile = true }`** al propio módulo
   `bootstrap/`, apuntando al bucket que el paso 1 acaba de crear.
3. **`terraform init -migrate-state`**: Terraform copia el estado local al bucket S3 y
   confirma la migración. A partir de aquí, `bootstrap/` **también** vive en el backend
   remoto, igual que cualquier módulo futuro — no es una excepción permanente.
4. El `.tfstate` local resultante del paso 1 se borra del disco (ya está en `.gitignore`,
   nunca estuvo en git) una vez confirmada la migración.

Después del paso 3, el runner de GitHub Actions **sí** tiene acceso al estado de
`bootstrap/` (vía el bucket S3), que es justamente lo que el job `terraform-plan`
necesita para funcionar — corrigiendo el defecto de diseño de la v1, donde ese job no
podía funcionar porque el estado nunca salía de la máquina local.

### 4.2 Usuario humano, rol de administración con MFA, y el primer bootstrap

- `diego-admin` (usuario IAM, creado a mano — `RUNBOOK.md` §1) empieza con
  `AdministratorAccess` adjunto **directamente**, única y exclusivamente para poder
  ejecutar el primer `terraform apply` de §4.1 (no hay ningún rol todavía que asumir).
- Ese primer `apply` crea, entre otras cosas, el rol `moneypulse-admin`: *trust policy* que
  permite a `diego-admin` asumirlo **solo si** `aws:MultiFactorAuthPresent = true`; lleva
  adjunta la política de administración más la *deny policy* de región
  (`aws:RequestedRegion != us-east-1` → `Deny *`).
- Inmediatamente después (`RUNBOOK.md` §1.3), se **quita** `AdministratorAccess` de
  `diego-admin` y se le deja solo `sts:AssumeRole` sobre `moneypulse-admin`. Desde ese
  momento, ningún trabajo (incluyendo los `apply` de las tareas 4–8 de §5) se hace con las
  credenciales base de `diego-admin` — todo pasa por el rol asumido con MFA.
- Esto es deliberadamente una secuencia de "privilegio temporal → rol permanente con
  segundo factor", un patrón estándar de *bootstrap* de cuentas AWS nuevas, no un atajo.

### 4.3 Qué hace CI y qué no hace

`ci.yml` tiene tres jobs con disparadores distintos:

| Job | Dispara en | Qué hace | Credenciales AWS | `id-token` |
|---|---|---|---|---|
| `build` | `push`, `pull_request` | `./gradlew build` (Testcontainers Kafka+Postgres reales) + `npm run build` en `apps/web` | Ninguna | `read` (default) |
| `lint-infra` | `push`, `pull_request` | tflint + checkov sobre `infra/`, **bloqueante** (§4.4) | Ninguna | `read` (default) |
| `terraform-plan` | `push` a `main` únicamente | Asume `moneypulse-github-plan` vía OIDC; `terraform plan` sobre `infra/terraform/bootstrap` (ya en backend S3, §4.1) | OIDC, sin llaves | `write` (solo este job) |

*Trust policy* del rol `moneypulse-github-plan`:
- `token.actions.githubusercontent.com:aud` = `sts.amazonaws.com` **y**
- `token.actions.githubusercontent.com:sub` = `repo:diego1-avendano3/money-pulse:ref:refs/heads/main`

Ambas condiciones juntas (no solo `sub`) — sin el `aud` correcto, el token no es válido
para STS aunque el `sub` coincida. Permisos del rol: `ReadOnlyAccess` (AWS managed) +
una política inline que permite `s3:PutObject`/`s3:DeleteObject` **únicamente** sobre
`arn:aws:s3:::<bucket>/*.tflock` (el *lockfile* que el *native locking* de S3 crea y borra
en cada `plan`, incluso de solo lectura). Ningún job corre `terraform apply` — eso es
responsabilidad exclusiva del humano con el rol `moneypulse-admin` (§4.2), y el workflow de
*deploy* con aprobación manual (`deploy-always-on.yml`, AWS-PLAN.md §11) es de una fase
posterior.

### 4.4 tflint/checkov: bloqueantes desde el commit 1

A diferencia de la v1 de este spec (que los dejaba informativos "hasta que `infra/`
creciera"), quedan **bloqueantes desde ya**: la superficie de esta fase (IAM, S3, budgets)
es exactamente donde un hallazgo de seguridad importa más, no menos, por ser pequeña.
Una excepción puntual y justificada se documenta inline (`#checkov:skip=<id>:<motivo>`),
nunca deshabilitando la regla globalmente ni bajando el job a informativo. Cerrada, no se
revisita salvo que un hallazgo bloquee trabajo legítimo de forma repetida.

### 4.5 Budgets y el crédito de "Explore AWS": por qué uno se importa y el otro no

El crédito de hasta USD 20 de "Explore AWS" (AWS-PLAN.md §1.2) **solo se otorga por crear
el recurso desde el widget de la consola**, incluyendo el budget mensual de USD 5 — es una
de las 5 actividades que dan crédito, no una tarea de Terraform (`RUNBOOK.md` §2). Crearlo
con Terraform en vez de con el widget perdería ese crédito. Resolución:

- **Budget mensual USD 5**: se crea a mano en el widget (`RUNBOOK.md` §2.1). Luego se trae
  bajo control de Terraform con un bloque declarativo `import { to = ..., id = ... }`
  (Terraform ≥ 1.5) apuntando al recurso ya existente; `terraform plan` debe mostrar cero
  cambios antes de considerarlo importado correctamente.
- **Zero-spend budget**: no forma parte de las 5 actividades de crédito — lo crea
  Terraform directamente, sin pasar por el widget.

### 4.6 Runbook manual: archivo propio

Las tareas de consola (creación de cuenta, MFA, las 5 actividades de crédito, verificación
de EKS/MSK/Bedrock, billing preferences, Service Quotas) y la rutina de cierre de sesión
viven en [`docs/aws/RUNBOOK.md`](../docs/aws/RUNBOOK.md), no en este spec — un documento
operativo se actualiza con cada sesión de trabajo contra la cuenta real; este spec es la
fuente de verdad de *qué* debe cumplirse (§6), no el procedimiento paso a paso.

---

## 5. Desglose de tareas

Atómicas y ordenadas por dependencia. Cada una ≈ un commit (conventional), salvo las tareas
de `RUNBOOK.md` (consola, sin commit de código — se marcan con checkbox en ese archivo).

1. **`docs: AWS day-1 and session-close runbook`** — `docs/aws/RUNBOOK.md` con las tareas
   de §4.6, enlazado desde este spec.
2. **`chore(repo): add Terraform .gitignore`** — ignora `*.tfstate`, `*.tfstate.*`,
   `.terraform/`, `*.tfplan`, `crash.log`, `override.tf`, `override.tf.json`; **no**
   ignora `.terraform.lock.hcl` (§3.3).
3. **`chore(infra): scaffold Terraform bootstrap module`** — `infra/terraform/bootstrap/`
   con provider AWS pinned, versión mínima de Terraform, `default_tags`
   (`project`/`env`/`owner`/`ttl`, con `ttl=none` para los recursos permanentes de este
   módulo), backend `local` inicial.
4. **`feat(infra): S3 state bucket with native locking`** — bucket versionado, SSE-S3,
   bloqueo de acceso público, bucket policy deny-HTTP. Se aplica con backend local
   (`diego-admin` con `AdministratorAccess` temporal — `RUNBOOK.md` §1.2).
5. **`feat(infra): admin role with MFA and region lock, GitHub OIDC plan-only role`** —
   `moneypulse-admin` (MFA obligatoria para asumirlo, *deny policy* de región) y
   `moneypulse-github-plan` (OIDC, *trust policy* `aud`+`sub`, `ReadOnlyAccess` +
   `s3:PutObject`/`DeleteObject` solo sobre `*.tflock`). Mismo `apply` que la tarea 4 o
   inmediatamente después, todavía con backend local.
6. **`chore(infra): migrate bootstrap state to S3 backend`** — añade el bloque
   `backend "s3" { use_lockfile = true, ... }`; `terraform init -migrate-state`; confirma
   que el `.tfstate` ya vive en el bucket (§4.1) y borra el local. **Después de esta
   tarea**, `RUNBOOK.md` §1.3 (quitar `AdministratorAccess` de `diego-admin`) se ejecuta.
7. **`feat(infra): region lock enforcement and cost budgets`** — aplica/confirma la *deny
   policy* de región sobre el rol `moneypulse-admin` (si no quedó ya cubierta en la tarea
   5); `aws_budgets_budget` para el Zero-spend budget (directo); bloque `import` para el
   budget de USD 5 creado a mano (`RUNBOOK.md` §2.1, decisión §4.5).
8. **`ci: workflow for build, blocking infra lint and OIDC terraform plan`** —
   `.github/workflows/ci.yml` con los tres jobs de §4.3, `permissions` a nivel de workflow
   y de job (§3.4), Actions de terceros fijadas por SHA, tflint/checkov bloqueantes.
9. **`docs: phase 0.5 runbook results and quota findings`** — marca los checkboxes de
   `docs/aws/RUNBOOK.md` como completados y rellena la tabla de hallazgos de este spec
   (§6), sin exponer secretos, access keys ni el ID de cuenta completo.

---

## 6. Criterios de verificación

Compuerta ejecutable. La fase está terminada cuando **todos** pasan.

| # | Criterio | Comando / comprobación |
|---|---|---|
| V1 | La cuenta es Free plan, con MFA en root y en `diego-admin`. | Billing console → "Free plan"; IAM → MFA activo en root y en `diego-admin`. |
| V2 | Una llamada a cualquier servicio en una región distinta de `us-east-1` es denegada, **con el rol `moneypulse-admin` asumido** (no con `diego-admin` directo, que no tiene permisos propios). | `aws sts assume-role --role-arn <arn-moneypulse-admin> --role-session-name check --serial-number <mfa-arn> --token-code <código>` seguido de `aws ec2 describe-instances --region us-west-2` con esas credenciales temporales → `AccessDenied` explícito por la *deny policy* de región. |
| V3 | `diego-admin` no tiene permisos directos más allá de `sts:AssumeRole`. | `aws iam list-attached-user-policies --user-name diego-admin` y `list-user-policies` → ninguna política de administración adjunta; solo la policy que permite `sts:AssumeRole` sobre `moneypulse-admin`. |
| V4 | El Zero-spend budget y el budget mensual de USD 5 existen, con alertas, y el segundo está bajo Terraform. | `aws budgets describe-budgets --account-id <id>` muestra ambos con *notification thresholds* 50/80/100 %; `terraform -chdir=infra/terraform/bootstrap plan` tras el `import` de la tarea 7 muestra cero cambios sobre el recurso del budget de USD 5. |
| V5 | Cost Anomaly Detection y las alertas de Free Tier están activas. | `aws ce get-anomaly-monitors` devuelve ≥1 monitor activo; Billing preferences muestra "Receive Free Tier Usage Alerts" habilitado. |
| V6 | El estado de Terraform vive **en S3**, no local, y el *locking* nativo funciona. | `terraform -chdir=infra/terraform/bootstrap state list` devuelve los recursos (prueba de que el backend remoto es el real, no uno local); durante un `terraform plan` en curso, `aws s3 ls s3://<bucket>/` muestra un objeto `*.tflock` que desaparece al terminar. |
| V7 | `terraform plan` corre desde GitHub Actions **sin llaves de acceso de larga vida**. | El job `terraform-plan` de `ci.yml`, en un push a `main`, loguea la asunción del rol vía OIDC y produce un `plan` contra el backend S3 (ya migrado, V6) sin que `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` aparezcan en el repo ni en los secretos del workflow. |
| V8 | CI corre en cada push y en cada PR. | Un commit a `main` y un PR de prueba disparan `ci.yml`; el job `build` corre Gradle (con Testcontainers) y el build de `apps/web`; ambos en verde en los dos disparadores. |
| V9 | tflint/checkov **bloquean** el pipeline si hay hallazgos no suprimidos explícitamente. | Introducir deliberadamente un hallazgo (p. ej. un bucket sin cifrado) en una rama de prueba → el job `lint-infra` falla y el workflow queda en rojo; añadir `#checkov:skip=<id>:<motivo>` lo vuelve a poner en verde solo para ese hallazgo puntual. |
| V10 | Todo recurso de Terraform creado lleva las etiquetas obligatorias, incluyendo `ttl=none` en los permanentes. | `aws resourcegroupstaggingapi get-resources --tag-filters Key=project,Values=moneypulse` **y** `aws iam list-role-tags --role-name moneypulse-admin` (y lo mismo para `moneypulse-github-plan`) muestran `project`, `owner`, `env` y `ttl=none`. |
| V11 | El rol `moneypulse-github-plan` no puede hacer `apply` ni tocar recursos de la lista negra. | `aws iam list-attached-role-policies --role-name moneypulse-github-plan` → `ReadOnlyAccess`; `aws iam get-role-policy --role-name moneypulse-github-plan --policy-name <inline>` → el documento JSON solo permite `s3:PutObject`/`DeleteObject` sobre `arn:aws:s3:::<bucket>/*.tflock`, nada más de escritura. |
| V12 | Cuotas de entrada quedan registradas. | Este spec (tabla abajo) contiene el valor numérico de la cuota de concurrencia de Lambda y del *throttle* de cuenta de API Gateway. |
| V13 | Disponibilidad de EKS/MSK/Bedrock queda registrada, sin haber intentado crear un clúster. | Este spec (tabla abajo) registra, para cada uno, lo que Billing → Free Tier mostró (nunca el resultado de un asistente de creación avanzado); y si el acceso a modelos de Bedrock quedó habilitado. |

### Tabla de hallazgos (se rellena en la tarea 9 — vacía hasta entonces)

| Dato | Valor | Fecha |
|---|---|---|
| Cuota de concurrencia de Lambda (cuenta nueva) | *(pendiente)* | *(pendiente)* |
| *Account-level throttle* de API Gateway | *(pendiente)* | *(pendiente)* |
| EKS listado como elegible en Billing → Free Tier | *(pendiente)* | *(pendiente)* |
| MSK listado como elegible en Billing → Free Tier | *(pendiente)* | *(pendiente)* |
| Bedrock — modelo económico con acceso habilitado | *(pendiente)* | *(pendiente)* |

---

## 7. Notas de trazabilidad de decisiones abiertas hacia fases futuras

| Decisión | Estado | Se revisa en |
|---|---|---|
| §4.1 Migración de `bootstrap/` a backend S3 (sin excepción permanente) | **elegida** | — |
| §4.2 Secuencia privilegio temporal → rol `moneypulse-admin` con MFA | **elegida** | — |
| §4.3 CI nunca hace `apply`, solo `plan`; permisos mínimos por job | **elegida** | Fase 1 (nace `deploy-always-on.yml`) |
| §4.4 tflint/checkov bloqueantes desde el commit 1 | **elegida (cerrada)** | — |
| §4.5 Budget de USD 5 importado, Zero-spend creado por Terraform | **elegida** | — |
| §6 Cuotas Lambda/API Gateway y disponibilidad EKS/MSK/Bedrock | pendiente de medir | AWS-PLAN.md §14 A1, A6 |
