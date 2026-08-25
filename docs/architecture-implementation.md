# Architecture d’implémentation

**Produit :** Recogniz-Me  
**Date :** 25 août 2026  
**Statut :** architecture cible Phase 1 (MVP)  
**Documents liés :** [`cahier-des-charges.md`](./cahier-des-charges.md) · [`roadmap-implementation.md`](./roadmap-implementation.md) · [`tutoriel-pipeline-kyc.md`](./tutoriel-pipeline-kyc.md) · [`specs/sprint-01-verifications-multi-tenant.md`](./specs/sprint-01-verifications-multi-tenant.md)

Ce document dit **comment on construit** le pipeline. Le CDC dit **quoi** ; le tutoriel dit **pourquoi** ; la roadmap dit **quand**.

---

## 1. Décisions figées

| Décision | Choix V1 | Interdit |
|---|---|---|
| Forme | Monolithe modulaire Maven, 2 processus (API + workers) | Microservices par étape OCR / MRZ / liveness |
| Style | Ports & adapters (déjà amorcé dans `kyc-adapters-aws`) | Appels AWS depuis un controller Next.js ou un `@RestController` |
| Orchestration | State machine PostgreSQL + commandes SQS | Saga distribuée, Kafka, Step Functions |
| Décision KYC | Règles Spring dans le domaine | Bedrock / LLM comme juge |
| Médias | S3 + KMS, URL signée | Binaire dans JSON, URL publique permanente |
| Multi-tenant | `organization_id` sur chaque ligne métier | Schéma Postgres par tenant en V1 |
| IA | Textract + Rekognition derrière des ports | Modèle dans la JVM |
| Sandbox | Fixtures déterministes, mêmes ports | Branche `if sandbox` au milieu des steps |

---

## 2. Vue d’ensemble

```
 Applicants                         Clients SaaS
 (hosted flow)                      (console)
      │                                  │
      │  Next.js :3001                   │  Next.js :3000
      │  + capture-sdk                   │
      └──────────────┬───────────────────┘
                     │  HTTPS /v1
                     ▼
              kyc-api :8080
              (auth, commandes synchrones,
               presign S3, lecture dossier)
                     │
         ┌───────────┼───────────┐
         ▼           ▼           ▼
    PostgreSQL     Redis       S3 (+ KMS)
    (vérité        (token      (médias)
     dossier)       hosted,
                    rate limit,
                    locks)
                     │
                     │  SQS commands
                     ▼
              kyc-workers :8081
              (pipeline document,
               biométrie, décision,
               webhooks)
                     │
                     ▼
              Ports AWS
              Textract / Rekognition / S3 / SQS
              (no-op ou fixtures en local/sandbox)
```

Deux processus Spring Boot, **un seul modèle de domaine**. L’API ne calcule pas l’OCR. Les workers n’exposent pas de REST métier.

---

## 3. Un seul projet Spring Boot

Le backend est **un** module Maven (`api/pom.xml`), **un** processus `:8080`. Les jobs async vivent dans le package `com.kyc.workers` (pas un second JAR).

```
api/src/main/java/com/kyc/
  entities/        Verification, Consent, Organization, ApiKey, AuditEvent
  repositories/
  services/
  controllers/     REST /v1
  config/          Security, OpenAPI
  ports/           S3, SQS, Textract, Rekognition
  adapters/        no-op local (AWS réel plus tard)
  workers/         consommateurs SQS (sprint 2+)
```

Règle : **un step OCR appelle `DocumentAiPort`, jamais `TextractClient`.**

---

## 4. Couches dans un processus

```
Adapter entrant          Application              Domaine               Adapter sortant
───────────────          ───────────              ───────               ───────────────
REST controller    →  CreateVerification    →  Verification      →  VerificationRepository
Flow controller    →  RecordConsent         →  Consent           →  RedisHostedSession
SQS listener       →  ProcessDocument       →  Pipeline + Steps  →  DocumentAiPort
Review controller  →  ApplyManualDecision   →  Decision          →  QueuePort (webhook)
```

- **Adapter entrant** : HTTP ou message SQS. Validation de forme, mapping DTO.
- **Use case** : une intention métier, une transaction, un audit.
- **Domaine** : transitions légales, steps, règles. Pas d’I/O.
- **Port** : interface dans `kyc-adapters-aws` (ou `kyc-domain` si le port est métier pur).
- **Adapter sortant** : Postgres, Redis, S3, Textract, HTTP webhook.

Les entités JPA sont aujourd’hui dans `kyc-domain` — acceptable en V1. Ne pas introduire un second modèle « anémique DTO partout ».

---

## 5. Machine à états

Source de vérité : colonne `verifications.status`. Les workers **ne sautent pas** d’étape ; ils demandent une transition nommée. Toute transition illégale lève une erreur de domaine (et le message SQS part en retry/DLQ, pas en APPROVE).

### 5.1 Cycle A — contrat (Sprint 1)

```
created → pending_consent → pending_applicant
        ↘ declined
        ↘ expired
        ↘ cancelled
```

### 5.2 Cycles B–D — analyse (Sprint 2–5)

```
pending_applicant
        ↓
document_pending          # URL signée émise, en attente upload
        ↓
document_uploaded         # objet S3 reçu → enqueue kyc-document
        ↓
document_processing       # worker cycle B
        ↓
document_completed        # OCR/MRZ/auth done ; waiting selfie
        ↓
liveness_pending
        ↓
liveness_processing
        ↓
liveness_completed
        ↓
processing                # face match + risk + decision (interne, court)
        ↓
   ┌────┼────────────┐
   ▼    ▼            ▼
approved  rejected  manual_review
```

Statuts terminaux V1 : `declined`, `expired`, `cancelled`, `approved`, `rejected`, `manual_review` (ce dernier redevient `approved` / `rejected` après revue Sprint 6).

Recapture document ou selfie : **on ne crée pas un second Verification**. On reste sur le même id, on incrémente `attempt` sur `documents` / `liveness_sessions`, on republie une commande. Cap d’attempts (ex. 3) → `rejected` ou `manual_review` selon règle.

### 5.3 Transitions

Une seule classe (ou package) `VerificationStateMachine` dans `kyc-domain` :

```
transition(current, event) → next | illegal
```

Exemples d’événements internes : `CONSENT_ACCEPTED`, `DOCUMENT_UPLOADED`, `DOCUMENT_FAILED_QUALITY`, `DOCUMENT_COMPLETED`, `LIVENESS_COMPLETED`, `DECISION_APPROVED`.

Les webhooks publics (`document.completed`, `verification.approved`, …) sont **projetés** depuis ces événements, pas l’inverse.

---

## 6. Pipeline document et biométrie

Le schéma produit (qualité → détection → OCR → MRZ → …) n’est **pas** une file SQS par case. C’est une **chaîne de steps** dans un seul worker, avec persistance de chaque check.

### 6.1 Contrat d’un step

```
interface CheckStep {
  String name();           // "quality", "ocr", "mrz", "liveness", ...
  CheckResult execute(PipelineContext ctx);
}
```

`CheckResult` : `status` (`PASS` | `FAIL` | `WARN`), `score` optionnel, `code` (ex. `blur_detected`, `mrz_checksum_invalid`), `payload` JSONB **sans image**.

`PipelineContext` : `verificationId`, `organizationId`, `s3Key`, `documentType?`, résultats des steps précédents (OCR fields pour le compare MRZ).

Politique :

| Résultat | Comportement |
|---|---|
| `FAIL` fail-fast (qualité, type non supporté) | Stop pipeline, statut recapture ou `rejected` |
| `FAIL` signal (MRZ mismatch) | Persister le check, **continuer** |
| `WARN` | Continuer |
| `PASS` | Continuer |

### 6.2 Chaînes V1

**DocumentWorker** (`kyc-document`) :

1. `ServerQualityStep` — mime, taille, dimensions  
2. `ClassificationStep` — allow-list pays × type (règles, pas de modèle)  
3. `OcrStep` — `DocumentAiPort.analyzeIdentityDocument` + normalizer  
4. `MrzLocateAndParseStep` — parse ICAO + check digits  
5. `MrzOcrCompareStep` — signaux de mismatch  
6. `AuthenticityRulesStep` — expiry, checksum, mismatch fort (pas de CNN V1)

**BiometricWorker** (`kyc-biometric`) :

1. `FaceQualityStep`  
2. `LivenessStep` — `BiometricAiPort` (challenge / Rekognition)  
3. `FaceMatchStep` — crop portrait document + selfie

**DecisionWorker** (`kyc-decision`) — uniquement si document **et** liveness sont `completed` :

1. `RiskAggregationStep` — lecture des `check_results`  
2. `DecisionRulesStep` — `APPROVED` / `REJECTED` / `MANUAL_REVIEW` + `reasons[]`

Sandbox : les mêmes steps, ports branchés sur **fixtures** (`SandboxDocumentAiAdapter`), pas un `if (sandbox)` dans le step.

---

## 7. Files, commandes, idempotence

Peu de files, isolation des retries.

| File SQS | Commande | Producteur | Consommateur |
|---|---|---|---|
| `kyc-document` | `ProcessDocument` | API après upload confirmé | DocumentWorker |
| `kyc-biometric` | `ProcessLiveness` | API après selfie/vidéo | BiometricWorker |
| `kyc-decision` | `DecideVerification` | DocumentWorker **ou** BiometricWorker quand l’autre côté est déjà `completed` | DecisionWorker |
| `kyc-webhooks` | `DeliverWebhook` | tout worker après événement public | WebhookWorker |

Payload minimal (pas d’image, pas de MRZ) :

```json
{
  "command": "ProcessDocument",
  "verification_id": "018f…",
  "organization_id": "018f…",
  "document_id": "018f…",
  "attempt": 1
}
```

Idempotence worker : table `processed_commands (command_id PK)` ou lock Redis `lock:verif:{id}:document`. Un double delivery SQS ne relance pas Textract si le check `ocr` est déjà `PASS` pour cet `attempt`.

DLQ par file. Après N retries : statut `manual_review` + reason `pipeline_timeout` (jamais un `approved` par défaut).

Local : LocalStack SQS **ou**, tant que Compose n’a pas LocalStack, un `InMemoryQueuePort` + scheduler dans `kyc-workers`. Le port reste le même.

---

## 8. Modèle de données (cible V1)

Toutes les tables métier portent `organization_id`. Lectures API **toujours** `WHERE organization_id = :tenant`.

| Table | Sprint | Rôle |
|---|---|---|
| `organizations`, `api_keys`, `memberships` | 0 | Tenant |
| `verifications` | 1 | Agrégat racine, `status` |
| `consents` | 1 | Décision + version du texte |
| `audit_events` | 1 | Append-only, sans PII |
| `idempotency_keys` | 1 | POST création |
| `documents` | 2 | `s3_key`, side `front`/`back`, `attempt`, mime |
| `check_results` | 3 | Un row par step (`name`, `status`, `score`, `payload` JSONB, `vendor`, `model_version`) |
| `extracted_identities` | 3 | Champs normalisés (PII chiffrée ou colonne dédiée) |
| `liveness_sessions` | 4 | Challenge, `s3_key` vidéo, attempts |
| `decisions` | 5 | `outcome`, `reasons[]`, `rule_set_version`, `risk_score` |
| `review_notes` | 6 | Analyste |
| `webhook_endpoints`, `webhook_deliveries` | 7 | Sortant HMAC |
| `usage_counters` | 7 | Billing / quota |

JSONB pour payloads vendor (Textract brut). **Ne pas** logger ce JSON. Les champs d’identité extraits vont dans `extracted_identities`, pas dans `audit_events`.

S3 keys (convention) :

```
org/{organization_id}/verif/{verification_id}/doc/{document_id}/front.jpg
org/{organization_id}/verif/{verification_id}/live/{session_id}/video.mp4
```

Jamais de nom de personne dans la key.

---

## 9. Ports à implémenter

Interfaces actuelles à **spécialiser** (le no-op unique `NoOpAwsAdapters` devra être scindé) :

| Port | Méthodes cibles | Impl live | Impl local/sandbox |
|---|---|---|---|
| `ObjectStoragePort` | `presignPut`, `presignGet`, `exists`, `getObject` | S3 + KMS | LocalStack / filesystem |
| `QueuePort` | `publish(queue, command)` | SQS | In-memory / LocalStack |
| `DocumentAiPort` | `analyzeId(bytes) → raw + normalized` | Textract `AnalyzeID` | Fixture JSON par `external_id` |
| `MrzPort` | `parse(image or lines) → MrzFields` | Lib ICAO maison (pas AWS) | Fixture |
| `BiometricAiPort` | `detectFaces`, `liveness`, `compareFaces` | Rekognition | Fixture scores |
| `ClockPort` | `now()` | système | tests |
| `HostedSessionPort` | get/put/delete token Redis | Redis | Redis Compose |

`MrzPort` n’est **pas** Textract : parsing déterministe dans `kyc-domain` ou un petit module `kyc-mrz`. L’OCR des lignes peut réutiliser Textract DetectDocumentText, mais le checksum reste du code à nous.

Profil Spring :

- `local` → no-op / in-memory / LocalStack  
- `sandbox` → fixtures  
- `prod` → AWS réel  

Un seul bytecode. Pas de `kyc-api-sandbox` séparé.

---

## 10. API HTTP — surfaces

Trois audiences, **une** app `kyc-api`.

| Préfixe | Audience | Auth |
|---|---|---|
| `/v1/verifications`, `/v1/webhooks`, `/v1/usage` | Backend client | Bearer `ky_live_` / `ky_test_` |
| `/v1/flow/{token}/…` | Applicant hosted | Token Redis (pas de clé API) |
| `/v1/console/…` (ou BFF cookie) | Utilisateur dashboard | Session Spring / OIDC |

L’applicant n’appelle jamais `/v1/verifications/{id}`.

Upload média (Sprint 2+) :

1. `POST /v1/flow/{token}/document/presign` → `{ upload_url, document_id }`  
2. PUT S3 direct depuis le navigateur  
3. `POST /v1/flow/{token}/document/complete` → enqueue `ProcessDocument`

Même schéma pour le liveness.

Lecture résultats client : `GET /v1/verifications/{id}/results` — champs d’identité selon politique (souvent minimisés) ; **pas** d’URL S3 longue durée.

---

## 11. Front — deux apps, un SDK

```
web/console     dashboard, revue, copie du lien          :3000
web/flow        consentement → capture → liveness → wait :3001
packages/capture-sdk   caméra, cadrage, qualité client
```

Règles :

- Next.js = UX. Zéro SDK AWS dans le browser.
- `capture-sdk` ne connaît pas l’API métier : il expose `getFrameQuality()` / `capture()`. Le flow orchestre presign + complete.
- Qualité **client** = boucle de recapture. Qualité **serveur** = filet dans le worker.
- Console et flow ne partagent pas le cookie. Isolation XSS : le token hosted est dans l’URL/path, TTL court.

---

## 12. Infra locale vs AWS

**Local (Compose actuel + à ajouter Sprint 2) :**

| Service | Local | Prod |
|---|---|---|
| API / workers | `mvn spring-boot:run` :8080 / :8081 | ECS Fargate |
| PostgreSQL | Compose `postgres:16` | RDS + KMS |
| Redis | Compose `redis:7` | ElastiCache |
| S3 / SQS / KMS | LocalStack (à ajouter) | AWS natif |
| Console / flow | `npm run dev:*` | CloudFront + S3 ou ECS |

Observabilité : `X-Request-Id` + `verification_id` dans les logs workers. **Jamais** nom, email, MRZ, numéro de pièce.

---

## 13. Sécurité (contraintes d’archi)

- Isolation : toute query métier scoped `organization_id`. 404 identique si autre tenant.
- Token hosted hashé en base (`hosted_token_hash`) ; clair uniquement Redis + URL.
- Médias : bucket privé, presign TTL court, GetObject seulement via API/console après auth.
- Workers IAM : lecture S3 du préfixe `org/` + Textract/Rekognition ; pas de `s3:*` global.
- Décision : `reasons[]` + `rule_set_version` persistés. Un APPROVE sans check critique `PASS` est un bug d’architecture (guard dans `DecisionRulesStep`).

---

## 14. Mapping sprints → chantier archi

| Sprint | On ajoute | On ne touche pas |
|---|---|---|
| **1** | Use cases création/consent dans API, Redis hosted, state machine A | Tables document, SQS, steps |
| **2** | `documents`, `ObjectStoragePort` réel, file `kyc-document`, extraire `kyc-application`, qualité serveur minimale | Textract |
| **3** | Steps OCR/MRZ/classif, `check_results`, `extracted_identities` | Liveness |
| **4** | `liveness_sessions`, file `kyc-biometric`, FaceQuality + Liveness | Decision auto |
| **5** | FaceMatch + files `kyc-decision`, `decisions` | Dashboard revue |
| **6** | Console lecture `check_results`, revue | Webhooks |
| **7** | `kyc-webhooks`, sandbox profile, usage | KMS retention |
| **8** | Chiffrement, rétention, 2FA | Nouveau moteur IA |
| **9** | ECS, RDS, secrets, DLQ CloudWatch | — |

Si un sprint ajoute un step, il implémente `CheckStep` et l’enregistre dans la chaîne. Pas de rewrite du worker.

---

## 15. Packages Java cibles (repère)

```
com.kyc.domain.verification      Verification, VerificationStatus, StateMachine
com.kyc.domain.consent
com.kyc.domain.document          Document, ExtractedIdentity
com.kyc.domain.check             CheckResult, CheckStatus
com.kyc.domain.liveness
com.kyc.domain.decision          Decision, Reason, RuleSet
com.kyc.domain.pipeline          CheckStep, PipelineContext, PipelineRunner

com.kyc.application.verification CreateVerification, GetVerification, CancelVerification
com.kyc.application.flow         OpenHostedSession, RecordConsent, PresignDocument
com.kyc.application.pipeline     ProcessDocument, ProcessLiveness, DecideVerification
com.kyc.application.review       ApplyManualDecision

com.kyc.aws                      ports
com.kyc.aws.s3 / sqs / textract / rekognition / noop / sandbox

com.kyc.api.web.v1               VerificationsController, FlowController, …
com.kyc.api.config               Security, OpenAPI

com.kyc.workers.document         DocumentCommandListener
com.kyc.workers.biometric
com.kyc.workers.decision
com.kyc.workers.webhook
```

---

## 16. Exemple de séquence (happy path)

```
Client                 API                  Redis/S3/SQS           Workers              AWS AI
  │                      │                      │                     │                    │
  │ POST /verifications  │                      │                     │                    │
  │─────────────────────►│  persist created     │                     │                    │
  │                      │  SET hosted token    │                     │                    │
  │◄──── hosted_url ─────│                      │                     │                    │
  │                      │                      │                     │                    │
Applicant GET /flow/tok  │  GET Redis           │                     │                    │
  │  accept consent      │  → pending_applicant │                     │                    │
  │  presign + PUT S3    │                      │                     │                    │
  │  document/complete   │  status uploaded     │                     │                    │
  │                      │  publish document    │                     │                    │
  │                      │                      │── ProcessDocument ─►│ quality→ocr→mrz   │
  │                      │                      │                     │───────────────────►│ Textract
  │                      │                      │                     │ persist checks     │
  │                      │                      │                     │ document_completed │
  │  liveness complete   │  publish biometric   │                     │                    │
  │                      │                      │── ProcessLiveness ─►│ live + match       │
  │                      │                      │                     │───────────────────►│ Rekognition
  │                      │                      │── Decide ──────────►│ rules              │
  │                      │                      │                     │ approved + reasons │
  │                      │                      │── DeliverWebhook ──►│ HMAC POST client   │
```

---

## 17. Anti-patterns

| À éviter | Faire à la place |
|---|---|
| Un `@Service` de 800 lignes « VerificationService » | Use cases + `PipelineRunner` + steps |
| Appeler Textract depuis le controller d’upload | Enqueue + worker |
| Une queue par case du schéma produit | 4 files + chaîne de steps |
| Stocker le JSON Textract dans `audit_events` | `check_results.payload` + pas de log |
| `similarity_score > 0.9` en dur dans 4 classes | `RuleSet` versionné, seuils en config org |
| Next.js qui parle à Rekognition | Interdit |
| Nouveau microservice « mrz-service » en V1 | `MrzPort` dans le même worker |

---

## 18. Critère d’architecture « done » (Phase 1)

1. `kyc-api` et `kyc-workers` partagent `kyc-application` + `kyc-domain`.  
2. Aucun SDK AWS hors `kyc-adapters-aws`.  
3. Chaque case du pipeline produit est un `CheckStep` persistant un `check_result`.  
4. Une décision a toujours `reasons[]` et `rule_set_version`.  
5. Impossible d’`APPROVED` si un check critique n’est pas `PASS` (test unitaire de domaine).  
6. Sandbox = autre implémentation des ports, même pipeline.
