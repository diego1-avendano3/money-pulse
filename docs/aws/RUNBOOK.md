# Runbook — Fase 0.5: cuenta AWS y cierre de sesión

> Tareas de consola, no automatizables por Terraform (o que no vale la pena modelar para un
> uso único), más la rutina de cierre de cada sesión de trabajo en AWS. Referenciado desde
> `specs/phase-0.5.md` §4 y §6 — el spec conserva solo los criterios de verificación; este
> documento es el procedimiento. Marca cada checkbox al completarla y registra la evidencia
> mínima necesaria en la tabla de hallazgos de `specs/phase-0.5.md` §6 — **nunca** un
> secreto, access key o ID de cuenta completo en texto plano (este repo es público).

## 1. Cuenta y usuario administrador (prerequisito estricto de todo lo demás)

- [ ] **1.1 Crear la cuenta AWS** → elegir explícitamente **Free plan** (no Paid plan,
  AWS-PLAN.md §1.1). Habilitar **MFA en root** inmediatamente. No volver a usar root
  después de este paso salvo emergencia documentada.
- [ ] **1.2 Usuario IAM `diego-admin`** con MFA propio (credencial distinta de root).
  **Solo para el primer bootstrap de Terraform**, adjuntarle temporalmente
  `AdministratorAccess` directo — es la única forma de crear, sin nada previo, el bucket de
  estado, el proveedor OIDC y el rol de administración (`specs/phase-0.5.md` §4.2). Generar
  una access key de este usuario para ese primer `apply`.
- [ ] **1.3 Tras el primer `terraform apply` de `bootstrap/`** (que ya creó el rol
  `moneypulse-admin` con MFA + bloqueo de región — spec §4.2): **quitar
  `AdministratorAccess` de `diego-admin`** y dejarlo únicamente con `sts:AssumeRole` sobre
  `moneypulse-admin`. De aquí en adelante, todo trabajo de consola/CLI se hace asumiendo
  ese rol (con MFA), nunca con el usuario directo. La access key del paso 1.2 se rota o se
  elimina una vez hecho este cambio.

## 2. Las 5 actividades de "Explore AWS" (dan hasta USD 20 de crédito)

El crédito **solo se gana haciendo cada actividad desde el widget "Explore AWS" de la
consola** — recrear el mismo recurso por fuera del widget (p. ej. con Terraform) no cuenta
para el crédito. Son **5**, no 4:

- [ ] **2.1 Crear el budget mensual de USD 5 desde el widget** (con alertas 50/80/100 %).
  Esta es, en sí misma, una de las 5 actividades. **No lo crees con Terraform** — créalo
  aquí a mano y luego impórtalo al estado (`specs/phase-0.5.md` §4.5, tarea 8). El
  *Zero-spend budget* es independiente de este crédito y **sí** lo crea Terraform
  directamente, sin pasar por el widget.
- [ ] **2.2 EC2**: lanzar una instancia y terminarla.
- [ ] **2.3 RDS**: lanzar una instancia y eliminarla.
- [ ] **2.4 Lambda**: crear una función con *function URL* y borrarla.
- [ ] **2.5 Bedrock playground**: usarlo una vez. *(Solo para el crédito; el uso real de
  Bedrock en el producto llega en Fases 2–3 — no se adelanta nada aquí, AWS-PLAN.md §3,
  §7, §14 A8.)*

**Terminar/borrar todo lo creado en 2.2–2.4 al completar cada una.**

## 3. Disponibilidad de EKS, MSK y Bedrock en este Free plan

> ⚠️ **Nunca hacer clic en "Create"/"Launch" de un clúster EKS o MSK en este paso.** El
> objetivo es solo verificar elegibilidad, no crear un clúster — eso es Fase 7, y un
> clúster EKS cobra el control plane por hora desde el minuto en que existe.

- [ ] **3.1** En **Billing → Free Tier** (la lista de servicios/beneficios elegibles bajo
  este Free plan), revisar si EKS y MSK aparecen listados. **No** usar el asistente de
  creación como forma de comprobarlo. Si la consola no lo deja claro, abrir un ticket de
  soporte (plan Basic, gratis) en vez de arrancar la creación para "ver si deja".
- [ ] **3.2** Verificar **acceso a modelos de Bedrock** ("Model access" en la consola de
  Bedrock) para al menos un modelo económico (Claude Haiku / Amazon Nova Micro) — esto sí
  es seguro de comprobar sin costo, a diferencia de EKS/MSK.
- [ ] **3.3** Registrar los tres resultados en `specs/phase-0.5.md` §6.

## 4. Billing preferences y Service Quotas

- [ ] **4.1** Billing preferences: activar alertas de Free Tier por email.
- [ ] **4.2** Activar **Cost Anomaly Detection** (gratis).
- [ ] **4.3** Service Quotas: anotar la cuota de **concurrencia de Lambda** y el
  *account-level throttle* de API Gateway. Registrar en `specs/phase-0.5.md` §6.

---

## Rutina de cierre de cada sesión de trabajo en AWS

Adaptado de `docs/aws/AWS-PLAN.md` §1.5 — válido desde esta fase, aunque algunos pasos solo
tienen efecto una vez que existan labs o RDS.

```bash
terraform -chdir=infra/terraform/labs/eks destroy -auto-approve      # solo si hubo lab (Fase 7+; no aplica aún)
aws rds stop-db-instance --db-instance-identifier moneypulse-ledger  # solo si RDS existe (Fase 1+; no aplica aún) — se re-enciende sola a los 7 días ⚠️
aws resourcegroupstaggingapi get-resources --tag-filters Key=env,Values=lab --query 'ResourceTagMappingList[].ResourceARN'
```

**En esta fase (0.5), sin RDS ni labs todavía**, lo que sí aplica en cada sesión:

1. Correr el tercer comando de arriba (`get-resources` con `env=lab`) — debe devolver
   vacío; cualquier resultado es un recurso huérfano de una prueba manual de la §2.
2. Revisar el *Billing dashboard* (AWS-PLAN.md §1.4): si se pasa de USD 15/mes, parar y
   revisar antes de seguir.
3. Confirmar que ninguna de las actividades 2.2–2.4 de este runbook quedó sin borrar.

La Lambda `janitor` + EventBridge Scheduler que automatizan esta rutina (apagar RDS fuera
de horario, alertar sobre recursos `env=lab` con `ttl` vencido) entran en **Fase 1**, junto
con RDS — no existen todavía (`specs/phase-0.5.md` §2). Hasta entonces, esta rutina es
manual, y se repite al final de cada sesión de trabajo contra la cuenta AWS.
