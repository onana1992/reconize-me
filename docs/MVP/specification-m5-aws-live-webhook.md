# Spécification — M5 AWS live + webhooks

**Plateforme :** Recogniz-Me  
**Sprint :** M5 — analyse live (Textract + Rekognition) + livraison webhook **par intégration**  
**Version du document :** 1.0  
**Date :** 20 septembre 2026  
**Statut :** spécification de contrat (à implémenter) ; M4 / M3 / I livrés  
**CDC :** §11 live, §11.3, §11.6, critères §17.9 / §17.11 / §17.10 / §17.12. **Objectif O5 (production).**  
**Prérequis :** M4 **et** M3. Entité `Integration` livrée (I). Au moins une intégration `live` créable (solde ≥ une unité).

**Documents liés :**
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — contrat (§11, NF-MVP-01/03/04/05/06, §17.9 / §17.11)
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — ordre des sprints ; corridor live = permis QC
- [`roadmap-implementation-m5.md`](./roadmap-implementation-m5.md) — ordre de build D1–D4
- [`specification-m4-capture-idv-stub.md`](./specification-m4-capture-idv-stub.md) — flow, statuts, ports, fiche — **réutilisés, pas réécrits**
- [`specification-m2-compte-client.md`](./specification-m2-compte-client.md) — comptes, cookie, clés, matrice T2, intégrations
- [`roadmap-implementation-m3.md`](./roadmap-implementation-m3.md) — débit live à la création ; webhook Stripe ≠ webhook client
- [`../specs/specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md) — métier IDV
- [`../cout-unitaire-verification.md`](../cout-unitaire-verification.md) — COGS AnalyzeID / CompareFaces / Liveness
- [`guide-aws-textract.md`](./guide-aws-textract.md) — palier D1 : mesurer AnalyzeID sur un recto QC (IAM, CLI, go / no-go)
- [`charte-visuelle.md`](./charte-visuelle.md) — `flow.css` vs `console.css`

Ce document décrit **ce que fait** M5 : le parcours **live** de bout en bout (permis de conduire québécois), le basculement stub / AWS selon le **mode de l’intégration**, et la livraison HTTP signée `verification.completed`. Le *quoi* métier reste la [spec IDV](../specs/specification-fonctionnelle-idv.md) ; les **écarts MVP** sont ceux du CDC §11. Le CDC **prime** en cas de conflit métier. Les routes flow, la machine à états et la fiche console **ne changent pas** : seuls les adaptateurs IA, le moteur de décision live, et la file webhook s’ajoutent. L’ordre de construction reste la [roadmap M5](./roadmap-implementation-m5.md).

Si M4 n’est pas démontrable, **ne pas** ouvrir M5.

---

## 1. Objet

Permettre à une organisation **crédité** de **prouver une identité en live** : une session créée avec `ky_live_` photographie le **recto d’un permis de conduire du Québec**, Textract lit la pièce, Rekognition CompareFaces compare le visage, les règles versionnées tranchent, la console affiche des **extraits réels**, et le backend client reçoit un webhook **signé** — sans média dans le payload, sans facture AWS sur le sandbox.

**Avant M5 :** décision stub (`rules_version = m4-1`, scénario injectable) ; 0 Textract / Rekognition ; audit interne `verification.completed` mais **pas** de HTTP client ; fiche intégration : placeholder webhooks.

**Après M5 :**

| Mode d’intégration | Analyse | Décision | Webhook |
|---|---|---|---|
| `test` (`ky_test_`) | Stub M4, **0 AWS** | `m4-1` + `sandbox_scenario` | Livré si un endpoint est configuré |
| `live` (`ky_live_`) | AnalyzeID + CompareFaces | `m5-1` sur **signaux réels** ; `sandbox_scenario` **ignoré** | Idem |

**Ce que ce livrable n’est pas**

- Face Liveness AWS / Amplify (reporté ; liveness live = stub **documenté**, §2.4).
- SageMaker, Bedrock, Ground Truth, authenticité ML, juge LLM.
- Catalogue « tout permis canadien », passeport live, CNI, verso `document_back`.
- Webhook Stripe (`POST /v1/webhooks/stripe`) — déjà M3 ; **autre** contrat.
- SDK InContext, rate limit `POST /v1/verifications` (M6), paiement Stripe `sk_live_`.

---

## 2. Périmètre

### 2.1 Inclus

| Domaine | Contenu |
|---|---|
| Routage IA | Stub vs AWS selon `integrations.mode` de la **session**, pas un profil Spring global |
| Document live | Textract **AnalyzeID** sur le recto ; mapping → `driving_license` + `CA` + juridiction `QC` |
| Repli lecture | **Seulement si** le palier de mesure AnalyzeID échoue : DetectText + mapping **de ce spécimen QC** ; alors amender le CDC §11.2 **avant** le vert |
| Biométrie live | Rekognition **CompareFaces** (selfie ↔ portrait de **cette** session) |
| Liveness live | Stub M4 **documenté** (qualité selfie `accepted` → `liveness_pass`) |
| MRZ | Parseur déterministe **si** zone présente ; permis QC → `mrz_unavailable`, **non bloquant** |
| Décision | `IdvDecisionEngine` live `m5-1` consomme les signaux du port ; seuils versionnés |
| Qualité | Filet M4 **avant** tout appel AWS payant |
| Webhook | Un endpoint **par intégration** ; HMAC ; retry borné ; pas de médias |
| Console | Même fiche (extraits réels) ; settings intégration = URL + secret une fois + dernier statut de livraison |
| Vitrine | `/products/identity-verification` : une ligne Canada / permis QC / champs, pas de MRZ |
| Isolation | Org B sur l’id / le média / le webhook / la livraison de A → **404** |
| Preuve sandbox | Intégration `test` = **0** appel Textract / Rekognition (test automatisé, même après M5) |

### 2.2 Hors périmètre

| Domaine | Reporté |
|---|---|
| Face Liveness AWS | Amplify `FaceLivenessDetector`, `CreateFaceLivenessSession` — après M5 (roadmap MVP §9) |
| Catalogue documents | Passeport live, permis hors QC, CNI, verso comme 2ᵉ source |
| Vision proprio | SageMaker, dataset, Canny vendu comme moteur, Bedrock vision |
| Webhooks vision | `liveness.started`, `document.uploaded`, etc. |
| Multi-endpoints | Un seul URL par intégration au MVP (upsert, pas une liste) |
| SQS | File AWS pour le pipeline ou les webhooks ; retry = table + scheduler in-process |
| Opérateur RM | Revue KYC par Recogniz-Me |
| Stripe live | `sk_live_` = M6 / juridique |

### 2.3 Invariants à ne pas casser

Isolation 404, envelope `{ error }`, trois plans d’auth disjoints, hash BCrypt des clés, Bearer obligatoire sur `/v1/verifications/**`, pas de PII applicant ni de token brut dans les logs, pas de binaire dans le JSON métier, débit live **à la création** (M3, inchangé), intégration `test` jamais débitée, cookie ≠ Bearer ≠ token URL.

Les organisations, tests compte (M2 / T), ledger (M3) et parcours stub (M4) restent verts.

### 2.4 Décisions figées

| Sujet | Choix |
|---|---|
| Corridor **live** | **Canada × permis de conduire × Québec**, **recto seulement**. Figé 20 sept. 2026. Pas « permis canadien ». |
| Corridor **sandbox** | Fixture M4 inchangée (passeport FR fictif + `sandbox_scenario`). Ce n’est **pas** la liste publiée. |
| Lecture | AnalyzeID d’abord. DetectText + mapping QC **uniquement** si le spécimen de mesure échoue (alors CDC §11.2). Pas de Bedrock. |
| MRZ permis QC | Absente → signal `mrz_unavailable`, **pas** un refus, **pas** une revue à lui seul |
| Face Liveness AWS | **Hors M5.** Liveness live = stub qualité (CDC §17.9 à amender **avant** le vert). CompareFaces **reste** exigé. |
| Routage IA | Par `verification.integration_id` → `integrations.mode`. Même JVM, test et live coexistent. |
| `sandbox_scenario` | Ignoré en live (déjà : `VerificationService` pose `null`). RG-M4-13. |
| Webhook | Un endpoint par intégration ; événement unique `verification.completed` ; HMAC-SHA256 ; 5 tentatives |
| Worker | In-process (décision) + table `webhook_deliveries` + scheduler. **Pas** de SQS. |
| Région AWS | Configurable `kyc.aws.region`, défaut **`ca-central-1`**. Si AnalyzeID y est indisponible au palier de mesure → région documentée (`us-east-1` ou `eu-west-1`) **et** note dans le CDC. |
| Flyway | **V19+** (V18 = crédit). Ne pas réécrire V1–V18. |
| Secret webhook | Montré **une fois** ; préfixe affiché ensuite ; valeur stockée chiffrée (pepper applicatif), jamais dans les logs |
| Juge | Règles Spring `m5-1`, pas un LLM |

---

## 3. Acteurs

| Acteur | Description | Preuve d’identité |
|---|---|---|
| **Backend client** | Reçoit le webhook ; crée des sessions | Bearer `ky_test_` / `ky_live_` de **cette** intégration |
| **Opérateur console** | Relit la fiche, tranche `review` | Cookie `rm_session` + `VERIFICATION_*` |
| **Developer** | Configure l’URL webhook et les clés, **sans** PII de session | Cookie + `API_KEY_READ` / `_WRITE` |
| **Applicant** | Photographie le permis QC + selfie | Token d’URL — jamais de compte Recogniz-Me |
| **AWS** | Textract AnalyzeID, Rekognition CompareFaces | IAM (clés ou rôle), hors navigateur |
| **Endpoint client** | HTTPS du client | Possession du secret HMAC de **cette** intégration |
| **Système** | Qualité → AWS → règles → enqueue webhook → retry | — |

Recogniz-Me n’est **pas** analyste KYC au MVP (CDC §12).

---

## 4. Concepts

| Concept | Définition M5 |
|---|---|
| **Intégration** | Unité Veriff : `test` \| `live`. Porte clés, **webhook**, sessions. Le préfixe de clé **est** le mode. |
| **Session live** | `verifications.integration_id` → `mode = live`. Débitée à la création. Analyse AWS. |
| **Session test** | `mode = test`. Stub M4. Jamais débitée. 0 AWS. |
| **Corridor live** | Seule classe acceptée par le mapping AnalyzeID : permis QC recto. Hors mapping → `unsupported_document`. |
| **Signal** | Code + `outcome` + score optionnel. Produit par le port (AWS ou stub), **pas** une décision. |
| **Raison** | Code anglais dans `decision_reasons[]`, sans PII. |
| **Webhook endpoint** | Un URL HTTPS (localhost HTTP autorisé en `test`) rattaché à **une** intégration. |
| **Livraison** | Tentative HTTP POST signée. Idempotente côté client via `event_id` stable par (session, décision). |
| **Signature** | `X-RecognizMe-Signature: t={unix},v1={hmac}` — HMAC-SHA256 du secret sur `{t}.{body}`. |
| **Provider** | `stub` \| `textract_analyze_id` \| `textract_detect_text` \| `rekognition_compare_faces`. Journalisé, jamais les champs d’identité. |

---

## 5. Architecture

### 5.1 Surfaces (inchangées)

| App | Port | Rôle M5 |
|---|---|---|
| `web/console` | 3000 | Fiche (extraits réels) ; settings intégration = webhook |
| `web/flow` | 3001 | Parcours applicant **identique** M4 (liveness stub) |
| `packages/capture-sdk` | — | Qualité client **avant** PUT |
| `web/site` | 3002 | Copy corridor = permis QC (plus la fixture sandbox) |
| `api/` | 8080 | Adaptateurs `Aws*`, moteur `m5-1`, dispatcher webhook |

Pas d’appel métier depuis la vitrine. Pas de `ky_*` dans le navigateur.

### 5.2 Trois plans d’authentification

Inchangés M4. Ajout :

| Plan | Nouvelles routes |
|---|---|
| Bearer (`ky_*` de l’intégration) | `POST/GET/DELETE /v1/webhooks` — **scoped à cette intégration** |
| Cookie + `API_KEY_*` | `/v1/console/integrations/{id}/webhook` |
| Token URL / signature objet | Inchangé (`/v1/flow/**`, `/v1/objects`) |

`POST /v1/webhooks/stripe` reste `permitAll` + signature Stripe. **Ne pas** confondre avec `POST /v1/webhooks`.

Le developer configure le webhook **sans** `VERIFICATION_READ` : il ne voit pas les extraits ni les médias.

### 5.3 Chaîne métier live

```
Création ky_live_  (débit M3, sandbox_scenario = null)
        │
        ▼
Lien → consentement → pièce (qualité client + serveur) ── KO → recapture (0 AWS)
        │ OK
        ▼
Selfie (qualité, liveness stub) ── KO → recapture (0 AWS)
        │ OK
        ▼
processing
        │
        ├─ DocumentAiPort = AwsDocumentAi
        │     AnalyzeID (recto)
        │     mapping QC ── hors corridor → unsupported_document (CompareFaces peut être sauté)
        │
        ├─ BiometricAiPort = AwsBiometric   (si pièce supportée)
        │     CompareFaces (selfie ↔ document de CETTE session)
        │     liveness = stub (média accepted → pass)
        │
        ▼
IdvDecisionEngine.decideLive(signals)   rules_version = m5-1
        │
        ├─► approved | declined | review
        │
        ▼
Persist extraits + signaux + raisons
Audit interne verification.completed
Enqueue webhook_deliveries  (si endpoint configuré)
        │
        ▼
POST HTTPS signé vers l’URL de l’intégration
        retry borné si ≠ 2xx
```

**Sandbox** (même JAR) : `DocumentAiPort` / `BiometricAiPort` = stub ; `decide(scenario)` `m4-1` ; enqueue webhook identique.

### 5.4 Routage des adaptateurs

Les beans Spring **ne remplacent pas** le stub globalement (test et live coexistent).

```
HostedFlowService.decide
        │
        ├─ charge Integration via verification.integration_id
        │
        ├─ mode == test  → StubDocumentAi + StubBiometricAi
        │                   engine.decide(sandbox_scenario)     // m4-1
        │
        └─ mode == live  → AwsDocumentAi + AwsBiometric
                            engine.decideLive(docSignals, bioSignals)  // m5-1
```

Implémentation : deux ports injectés **ou** un routeur `DocumentAiRouter(integration)` ; l’appel site **doit** connaître le mode. Interdit : `@ConditionalOnProperty` qui inactive le stub en prod (casserait NF-MVP-05).

`SandboxNoAwsTest` **change** : il ne vérifie plus « les classes `Aws*` n’existent pas ». Il vérifie qu’une session `test` n’invoque **aucun** client AWS (mock / spy / compteur).

### 5.5 Composants nouveaux

| Couche | Responsabilité |
|---|---|
| `AwsDocumentAi` | AnalyzeID (bytes JPEG/PNG) → `DocumentSignals` enrichis ; mapping QC |
| `QcAnalyzeIdMapper` | Type AnalyzeID + juridiction → `driving_license`/`CA`/`QC` ou unsupported |
| `AwsBiometric` | CompareFaces ; liveness stub pass-through |
| `IdvDecisionEngine.decideLive` | Règles `m5-1` sur signaux, **sans** scénario |
| `WebhookEndpoint` | URL + secret chiffré + `secret_prefix`, unique par intégration |
| `WebhookDelivery` | Tentatives, statut, `event_id`, HTTP code, **pas** le body de réponse PII |
| `WebhookService` | CRUD endpoint ; enqueue ; signer |
| `WebhookDispatcher` | Scheduler (ex. toutes les 5 s) : due retries, timeout 10 s, SSRF |
| `ConsoleIntegrationController` | GET/PUT/DELETE webhook de la fiche |
| `WebhooksController` | Bearer `/v1/webhooks` |

Inchangés : `FlowController`, `ObjectsController`, `MediaQuality`, `capture-sdk`, machine à états §6.6 M4.

### 5.6 Séquence — live jusqu’au webhook

```
Client                 API                    AWS                 Endpoint client
  │                     │                      │                       │
  │ POST /v1/verifications  (ky_live_)         │                       │
  │────────────────────►│ débit ledger         │                       │
  │ 201 hosted_url      │                      │                       │
  │                     │  (applicant : consent + pièce + selfie)      │
  │                     │                      │                       │
  │                     │ AnalyzeID(recto)     │                       │
  │                     │─────────────────────►│                       │
  │                     │ fields + type        │                       │
  │                     │ CompareFaces         │                       │
  │                     │─────────────────────►│                       │
  │                     │ similarity           │                       │
  │                     │ decideLive m5-1      │                       │
  │                     │ INSERT delivery      │                       │
  │                     │ POST /webhook  HMAC  │                       │
  │                     │─────────────────────────────────────────────►│
  │                     │                      │              2xx      │
  │ GET /v1/verifications/{id}                 │                       │
  │◄────────────────────│ extraits réels       │                       │
```

Si AnalyzeID (ou CompareFaces) lève : **pas** de 500 applicant. Session → `review`, raison `provider_unavailable`, **0** retry AWS silencieux en boucle. Le débit n’est **pas** remboursé (comptage à la création, M3).

### 5.7 Qualité avant COGS

Aucun appel Textract / Rekognition si :

- le média n’est pas `accepted` (qualité serveur KO) ;
- le consentement n’est pas `accepted` ;
- l’intégration est `test`.

Le SDK client filtre en amont ; le serveur reste le filet (JPEG/PNG, ≤ 10 Mo, côté court ≥ 720 px) — M4 §8.1.

### 5.8 Isolation AWS et journaux

| Autorisé dans les logs | Interdit |
|---|---|
| `verification_id`, `organization_id`, `integration_id` | Nom, numéro de permis, date de naissance |
| Nom d’API (`AnalyzeID`, `CompareFaces`) | Bytes image, URL média |
| Durée, HTTP AWS, request-id AWS | Secret webhook, `ky_*`, HMAC |
| `provider`, codes de signal / raison | Payload AnalyzeID brut (champs d’identité) |

Le mapping lit les champs en mémoire, persiste le schéma normalisé `extracted_identity` **en base** (comme M4), et ne les journalise pas.

---

## 6. Workflows

### 6.1 Happy path live (démo M5)

**Préconditions :** org avec solde ≥ 0,90 $ ; intégration `live` + `ky_live_` ; endpoint webhook de test joignable ; spécimen = photo nette du **recto** d’un permis QC (génération photographiée en staging — pas une copie d’écran, pas un verso seul).

1. Backend (ou console `VERIFICATION_WRITE`) : `POST /v1/verifications` `{ "integration_id" }` avec Bearer live.
2. Ledger débité (M3). `hosted_url` émise.
3. Applicant : consentement → photo recto (qualité OK) → selfie (qualité OK).
4. `processing` : AnalyzeID + mapping QC + CompareFaces + liveness stub.
5. Décision terminale ou `review` ; extraits réels en console (nom, naissance, numéro, expiration, `driving_license` / `CA` / `QC`).
6. `POST` signé `verification.completed` reçu par l’endpoint de test, **sans** `url` média.
7. Org B sur l’id → **404**.

### 6.2 Sandbox inchangé

Même flow, `ky_test_`, 0 AWS, décision `m4-1` + scénario. Si un webhook est configuré **sur l’intégration test**, il est quand même livré (les développeurs testent la signature sans payer l’IA).

### 6.3 Pièce hors corridor live

AnalyzeID (ou DetectText) ne mappe pas vers permis QC → `declined`, `unsupported_document`. CompareFaces **non appelé** (COGS). Webhook livré.

Exemples : passeport, permis Ontario, CNI, photo floue classée `unknown`, verso seul.

### 6.4 Revue humaine

Machine → `review` → webhook `verification.completed` (`decision: review`).  
Analyste `POST .../review` → `approved` \| `declined` → **second** événement `verification.completed` (nouvel `event_id`, même `verification_id`).

La revue **ne réécrit pas** `decision_reasons` machine (M4).

### 6.5 Annulation / expiration

Pas de webhook M5 (catalogue minimal). Le client relit `GET /v1/verifications/{id}`.

### 6.6 Palier de mesure AnalyzeID (D1, avant code mapping figé)

Procédure locale (IAM, CLI / boto3, checklist, sanitisation CI) : [`guide-aws-textract.md`](./guide-aws-textract.md).

1. Photo nette recto QC, qualité SDK déjà OK.
2. Un AnalyzeID staging.
3. **Go** si le JSON contient de quoi mapper : type permis **et** nom **et** naissance **et** expiration **et** numéro.
4. **No-go** : DetectText + mapping de **ce** spécimen seulement ; amender CDC §11.2 **avant** le vert ; toujours **pas** de Bedrock.

Le mapping publié = le mapping **mesuré**. Pas d’optimisme « AnalyzeID lit tous les permis CA ».

---

## 7. Modèle de données

Migrations **V19+**. Ne pas réécrire V1–V18.

### 7.1 Schéma (ajouts)

```
integrations
       │ 1
       │
       ├──────── 0..1  webhook_endpoints     UNIQUE (integration_id)
       │                    │ 1
       │                    │
       │                    └──── * webhook_deliveries
       │
       └──────── * verifications   (déjà V15)
```

Pas de colonne AWS sur `verifications` : le `provider` vit dans les signaux / un champ optionnel JSON `extracted_identity.provider` **ou** un signal `ocr_provider` (`pass` + code). Recommandé : signal `ocr_analyze_id` / `ocr_detect_text` / `ocr_stub` pour la console, **sans** PII.

### 7.2 `webhook_endpoints`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK |
| `organization_id` | CHAR(36) | FK, isolation |
| `integration_id` | CHAR(36) | FK, **UNIQUE** |
| `url` | VARCHAR(2048) | HTTPS ; `http://localhost` / `127.0.0.1` autorisé si intégration `test` |
| `secret_cipher` | VARBINARY / TEXT | Secret chiffré (pepper) ; **jamais** loggé |
| `secret_prefix` | VARCHAR(16) | Ex. `whsec_ab12` — seul affichage après création |
| `status` | VARCHAR(16) | `active` \| `disabled` |
| `created_at` / `updated_at` | DATETIME(6) | |

Un `PUT` remplace l’URL ; **ne régénère pas** le secret sauf `POST .../webhook/rotate`. Rotation = nouveau secret montré une fois, livraisons suivantes signées avec le nouveau.

### 7.3 `webhook_deliveries`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK = `event_id` exposé |
| `organization_id` | CHAR(36) | FK |
| `integration_id` | CHAR(36) | FK |
| `endpoint_id` | CHAR(36) | FK |
| `verification_id` | CHAR(36) | FK |
| `event_type` | VARCHAR(64) | `verification.completed` |
| `payload_hash` | CHAR(64) | SHA-256 du body (debug sans PII dans les logs) |
| `attempt` | INT | 1..5 |
| `status` | VARCHAR(16) | `pending` \| `delivered` \| `failed` |
| `http_status` | INT | NULL puis code distant |
| `next_attempt_at` | DATETIME(6) | |
| `last_error_code` | VARCHAR(32) | `timeout`, `ssrf_denied`, `http_4xx`, `http_5xx`, `network` — **pas** le body distant |
| `created_at` / `updated_at` | DATETIME(6) | |

UNIQUE `(verification_id, event_type, decision_fingerprint)` pour ne pas doubler un enqueue dans la même transaction. Un review humain = nouvel `event_id`.

### 7.4 Ports enrichis

`DocumentAiPort.DocumentSignals` (évolution **compatible** stub) :

| Champ | Stub M4 | Live M5 |
|---|---|---|
| `documentType` | `passport` / `unknown` | `driving_license` / `unknown` |
| `documentCountry` | `FR` / `ZZ` | `CA` / `ZZ` |
| `issuingJurisdiction` | `null` | `QC` ou `null` |
| `expired` | selon scénario | `EXPIRATION_DATE` < aujourd’hui (UTC date) |
| `supported` | selon scénario | mapping QC OK |
| `mrzAvailable` | `false` | `false` sur permis QC |
| `firstName`, `lastName`, `birthDate`, `documentNumber`, `expirationDate` | ignorés par `m4-1` | ISO / texte normalisé |
| `provider` | `stub` | `textract_analyze_id` ou `textract_detect_text` |

`BiometricAiPort.BiometricSignals` : inchangé (`livenessPass`, `faceMatchPass`, scores). Live : `livenessPass = true` si selfie `accepted` (stub) ; `faceMatchScore` = similarité CompareFaces ∈ [0, 1].

Signature des ports : conserver `analyze(bytes, sandboxScenario)` / `evaluate(doc, selfie, sandboxScenario)`. En live le scénario est `null` et **ignoré** dans `Aws*`.

### 7.5 Machine à états

**Inchangée** (M4 §6.6). M5 ne crée pas de statut. `processing` peut durer quelques secondes (appels AWS) : le flow poll déjà `wait` (1,5 s). NF-MVP-06 : happy path en **minutes**.

---

## 8. Cas d’utilisation

Les UC-IDV-01 à 10 restent. Ci-dessous : **précisions live / webhook**. Alternatives et post-conditions sont **normatives**.

### UC-M5-01 — Décider en live (permis QC)

**Acteur :** système, après `selfie/complete` OK.  
**Précondition :** session `live` ; médias `document` + `selfie` `accepted` ; qualité déjà passée.

**Scénario nominal**

1. Lire les octets depuis le stockage (clé préfixée org).
2. `AwsDocumentAi.analyze` → AnalyzeID.
3. Mapper vers corridor QC (§10). Supporté, non expiré, champs requis présents.
4. `AwsBiometric.evaluate` → CompareFaces ; liveness stub pass.
5. `decideLive` → `approved` (seuils §9.2), raisons sans PII, `extracted_identity` réel, `rules_version = m5-1`.
6. Audit interne `{ "decision" }`.
7. Enqueue livraison si endpoint `active`.

**Post-condition :** statut terminal ou `review` ; extraits relisibles par le **même** tenant ; 0 PII dans les logs.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | Mapping hors QC | `declined` + `unsupported_document` ; pas de CompareFaces |
| A2 | Permis expiré | `declined` + `document_expired` |
| A3 | Champs requis manquants (nom / naissance / numéro / expiration) | `review` + `document_fields_incomplete` |
| A4 | Similarité zone grise | `review` + `face_match_borderline` |
| A5 | Similarité sous seuil bas | `declined` + `face_match_fail` |
| A6 | Textract / Rekognition indisponible ou 5xx | `review` + `provider_unavailable` |
| A7 | Session `test` | Branche stub ; 0 AWS (UC-IDV-05) |
| A8 | `sandbox_scenario` envoyé en live | Ignoré (déjà `null` en base) |

### UC-M5-02 — Configurer le webhook

**Acteur :** Bearer de l’intégration **ou** cookie `API_KEY_WRITE`.

**Scénario nominal**

1. `PUT` `{ "url": "https://example.com/hooks/idv" }`.
2. Si premier enregistrement : générer secret `whsec_` + 32 octets hex ; le renvoyer **une fois** dans `secret`.
3. Persister URL + cipher + prefix. Audit `webhook.upserted` `{ }` (pas d’URL dans l’audit si elle contient un token de query — stocker l’URL en base, audit = `{}` ou host seulement).
4. Réponse : `{ url, secret_prefix, secret? , status }`.

**Post-condition :** au plus un endpoint actif par intégration.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | Sans auth | **401** |
| A2 | Console sans `API_KEY_WRITE` | **403** |
| A3 | Intégration d’une autre org | **404** |
| A4 | URL non HTTPS (sauf localhost en `test`) | **400** `validation_error` |
| A5 | Live + URL privée / metadata / link-local | **400** `ssrf_denied` |
| A6 | Developer (a `API_KEY_WRITE`) | **200** — il ne voit toujours pas les sessions |
| A7 | `GET` | `{ url, secret_prefix, status }` — **jamais** le secret |
| A8 | `DELETE` | Endpoint retiré ; livraisons pending → `failed` / annulées |
| A9 | Rotation | Nouveau `secret` une fois ; prefix mis à jour |

### UC-M5-03 — Livrer `verification.completed`

**Acteur :** système.

**Précondition :** décision machine **ou** revue humaine venant d’être persistée ; endpoint `active`.

**Scénario nominal**

1. Sérialiser le payload §11.3 (pas de média).
2. `event_id` = UUID de la livraison.
3. POST, timeout 10 s, **pas** de suivi de redirect.
4. Header `X-RecognizMe-Signature`, `X-RecognizMe-Event: verification.completed`, `Content-Type: application/json`, `User-Agent: RecognizMe-Webhook/1`.
5. HTTP 2xx → `delivered`. Sinon retry selon §11.4.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | Pas d’endpoint | No-op, décision quand même persistée |
| A2 | Endpoint `disabled` | No-op |
| A3 | 5 échecs | `failed` ; visible en console ; bouton **Renvoyer** (`API_KEY_WRITE`) |
| A4 | Replay manuel | Nouvelle tentative, **même** `event_id` et même body (le client dédoublonne sur `event_id`) |
| A5 | Timeout / réseau | Retry ; `last_error_code = timeout` |
| A6 | URL devenue privée entre PUT et POST | Tentative `ssrf_denied`, pas d’envoi |

### UC-M5-04 — Vérifier la signature (contrat client)

Le backend client :

1. Parse `t` et `v1`.
2. Rejette si `|now - t| > 300` s (replay).
3. Calcule `HMAC-SHA256(secret, "{t}.{raw_body}")` en hex minuscule.
4. Compare en temps constant à `v1`.

Sans secret valide → ignorer (ne pas faire confiance au body).

### UC-M5-05 — Isolation

Org B : GET/PUT webhook, livraison, session, média de A → **404**, même corps qu’un UUID aléatoire. Une clé `ky_live_` de l’intégration 1 ne lit / n’écrit **pas** le webhook de l’intégration 2 (même org) : le Bearer est **scoped** à son `integration_id`.

Cookie `VERIFICATION_READ` voit les sessions, pas le secret webhook.  
Cookie `API_KEY_WRITE` voit le webhook, pas les extraits.

### UC-M5-06 — Sandbox ne facture pas AWS

Toute session `test` : compteur d’appels AWS = 0. Test automatisé obligatoire (`SandboxNoAwsTest` réécrit + spy sur clients AWS lors d’un happy path stub).

### UC-IDV-10 (live) — Pièce non supportée

Hors mapping QC → `unsupported_document`. Pas de parseur inventé pour un passeport ou un permis Ontario. Affichage vitrine = corridor **mesuré**.

---

## 9. Règles de gestion

Les RG-M4-01…25 restent pour le chemin `test`. Ajouts / précisions live :

| ID | Règle |
|---|---|
| **RG-M5-01** | Intégration `test` : **zéro** Textract / Rekognition (NF-MVP-05). |
| **RG-M5-02** | Intégration `live` : `sandbox_scenario` n’a **aucun** effet (RG-M4-13). |
| **RG-M5-03** | Qualité KO → recapture, **aucun** appel AWS. |
| **RG-M5-04** | CompareFaces uniquement selfie ↔ pièce **de cette** session. Jamais 1:N, jamais cross-tenant, jamais une photo d’une autre vérif. |
| **RG-M5-05** | Hors mapping QC → `unsupported_document` ; pas d’appel CompareFaces. |
| **RG-M5-06** | `mrz_unavailable` sur permis QC n’est ni un refus ni une revue à lui seul. |
| **RG-M5-07** | Décision live : signaux du port + `m5-1`. Pas de LLM. |
| **RG-M5-08** | Logs : `verification_id` + type d’appel AWS. Pas de champs d’identité, pas de binaire. |
| **RG-M5-09** | Payload webhook : **aucun** champ `url` / `object_key` / binaire (NF-MVP-04). |
| **RG-M5-10** | Secret webhook montré une fois ; GET ultérieur = `secret_prefix` seulement. |
| **RG-M5-11** | Signature HMAC obligatoire ; un POST non signé n’est pas « notre » contrat client (c’est **leur** vérif). |
| **RG-M5-12** | Retry borné à **5** tentatives ; pas de file infinie. |
| **RG-M5-13** | Live : URL webhook HTTPS public. `test` : localhost HTTP OK. SSRF : pas de metadata / link-local / RFC1918 en `live`. |
| **RG-M5-14** | Bearer webhook scoped à l’intégration de la clé. Console : `API_KEY_*`, pas `VERIFICATION_*`. |
| **RG-M5-15** | Débit live inchangé (création). Échec AWS ≠ remboursement automatique. |
| **RG-M5-16** | Vitrine et docs publient **uniquement** le corridor mesuré (permis QC). La fixture sandbox n’est pas un argument marketing. |
| **RG-M5-17** | Recto seulement : `document_back` non exigé par `m5-1`. |
| **RG-M5-18** | Isolation 404 y compris endpoint et livraisons. |
| **RG-M5-19** | `event_id` stable pour une livraison ; le client dédoublonne dessus. |
| **RG-M5-20** | Face match live = seuil versionné `m5-1`, pas un calibrage « au feeling » en prod. |

### 9.1 Politique de décision live (`rules_version` = `m5-1`)

Calibrage **provisoire figé pour M5**, versionné. Changer un seuil = bumper `m5-2`, pas un hotfix silencieux.

| Priorité | Condition | `decision` | Raisons |
|---|---|---|---|
| 1 | `supported = false` | `declined` | `unsupported_document` |
| 2 | `expired = true` | `declined` | `document_expired` |
| 3 | Champs requis absents | `review` | `document_fields_incomplete` |
| 4 | `livenessPass = false` | `declined` | `liveness_fail` |
| 5 | `faceMatchScore < 0.75` | `declined` | `face_match_fail` |
| 6 | `0.75 ≤ faceMatchScore < 0.90` | `review` | `face_match_borderline` |
| 7 | Sinon (supporté, non expiré, champs OK, liveness pass, match ≥ 0.90) | `approved` | `liveness_pass`, `face_match_pass` |

`mrz_unavailable` est persisté comme **signal** `unavailable`, **jamais** comme seule raison de `review` / `declined` sur ce corridor.

Consentement refusé et plafond d’essais **court-circuitent** toujours le moteur (`consent_declined` / `capture_attempts_exceeded`) — 0 AWS.

Provider KO (A6) : `review` + `provider_unavailable` (priorité après les court-circuits, avant le tableau).

### 9.2 Codes nouveaux (en plus M4 §8.3)

| Code | Rôle |
|---|---|
| `document_fields_incomplete` | OCR incomplet |
| `face_match_borderline` | Zone grise CompareFaces |
| `provider_unavailable` | AWS KO |
| `ocr_analyze_id` / `ocr_detect_text` | Signal provider (outcome `pass`) |

Pas de nom, pas de numéro de permis **dans le code**.

### 9.3 Extraits live (schéma)

Même objet que M4, plus la juridiction :

```
first_name:          (AnalyzeID FIRST_NAME)
last_name:           (LAST_NAME)
birth_date:          (DATE_OF_BIRTH, ISO 8601 date)
document_type:       driving_license
document_country:    CA
issuing_jurisdiction: QC
document_number:     (DOCUMENT_NUMBER / LICENSE_NUMBER)
expiration_date:     (EXPIRATION_DATE, ISO 8601 date)
```

Pas d’adresse, pas de photo. Scénarios `declined` net (unsupported / expired / match fail) : `extracted_identity` **peut** rester partiel s’il a été lu (utile à l’analyste) **sauf** `unsupported_document` (vide, comme M4). `review` : extraits disponibles.

---

## 10. Mapping AnalyzeID — permis QC

### 10.1 Classe acceptée

Une pièce est **supportée** ssi **toutes** les conditions tiennent après mapping :

1. Type document = permis de conduire (`DRIVER LICENSE` / équivalent AnalyzeID).
2. Pays = Canada (`CA` / `CAN` / `CANADA`).
3. Juridiction émettrice = Québec (`QC` / `QUEBEC` / `QUÉBEC` / `QUE.` / texte SAAQ mesuré en D1).
4. Face = recto (une image). Le verso n’est pas lu.

Sinon → `supported = false`.

### 10.2 Champs AnalyzeID (à confirmer en D1)

Noms **indicatifs** AWS ; la table D1 les fige dans le code (constantes, pas de magie) :

| Notre champ | Clés AnalyzeID candidates |
|---|---|
| `first_name` | `FIRST_NAME`, `GIVEN_NAME` |
| `last_name` | `LAST_NAME`, `SURNAME`, `FAMILY_NAME` |
| `birth_date` | `DATE_OF_BIRTH` |
| `expiration_date` | `EXPIRATION_DATE` |
| `document_number` | `DOCUMENT_NUMBER`, `ID`, `LICENSE_ID` |
| Type | `ID_TYPE` / `DOCUMENT_TYPE` |
| Juridiction | `STATE_NAME`, `STATE_IN_ISO`, `PLACE_OF_ISSUE`, `COUNTY` |

Dates : parser les formats renvoyés (souvent `YYYY-MM-DD` ou local) → ISO. Échec de parse d’une date requise → champ absent → `document_fields_incomplete` si les autres règles n’ont pas déjà refusé.

### 10.3 Repli DetectText

**Uniquement** si D1 no-go. Mapping **de ce spécimen** (libellés « PERMIS DE CONDUIRE », « QUÉBEC », positions / regex versionnées). Pas un OCR générique. CDC §11.2 amendé avant le vert.

### 10.4 Ce qui n’est pas du mapping

- Heuristique Canny / coins vendue comme moteur.
- Un second modèle « authenticité ».
- Étendre à l’Ontario « puisque AnalyzeID a un STATE_NAME ».

---

## 11. Webhooks — contrat

### 11.1 Événement M5

Un seul type : **`verification.completed`**.

Déclenché quand `decision` est posée par la machine (`approved` \| `declined` \| `review`) **et** quand une revue humaine termine (`approved` \| `declined`).

Pas de `verification.approved` / `rejected` séparés au MVP (CDC : « si peu coûteux » — on simplifie ; le client lit `payload.data.decision`).

### 11.2 Headers

```
POST {url}
Content-Type: application/json
User-Agent: RecognizMe-Webhook/1
X-RecognizMe-Event: verification.completed
X-RecognizMe-Signature: t=1710000000,v1=abcdef…
```

`t` = Unix seconds. `v1` = HMAC-SHA256 hex du secret UTF-8 sur la chaîne `{t}.{raw_json}`.

### 11.3 Body

```json
{
  "id": "evt_…",
  "type": "verification.completed",
  "created_at": "2026-09-20T18:00:00Z",
  "data": {
    "id": "<verification uuid>",
    "integration_id": "<uuid>",
    "external_id": "order-42",
    "status": "approved",
    "decision": "approved",
    "decision_reasons": ["liveness_pass", "face_match_pass"],
    "extracted_identity": {
      "first_name": "…",
      "last_name": "…",
      "birth_date": "1990-04-12",
      "document_type": "driving_license",
      "document_country": "CA",
      "issuing_jurisdiction": "QC",
      "document_number": "…",
      "expiration_date": "2028-06-01"
    },
    "created_at": "…",
    "updated_at": "…"
  }
}
```

`id` (enveloppe) = `event_id` = `webhook_deliveries.id`.  
`external_id` peut être `null`.  
**Interdit :** `hosted_url`, `signals` complets optionnels (OK de les omettre), toute URL, tout `object_key`, tout binaire, `applicant.email` n’est **pas** requis dans le webhook (le client l’a déjà s’il l’a envoyé ; on n’ajoute pas de PII contact). Les extraits d’identité **sont** dans `extracted_identity` : c’est le résultat que le client a acheté, livré sur **son** HTTPS.

`extracted_identity` est `null` si vide (unsupported).

### 11.4 Retry

| Tentative | Délai depuis l’enqueue |
|---|---|
| 1 | immédiat (async, hors requête flow) |
| 2 | +30 s |
| 3 | +2 min |
| 4 | +10 min |
| 5 | +1 h |

Succès = HTTP **2xx**. 3xx (redirect) = échec (pas de follow). 4xx : on **retry quand même** jusqu’à 5 (l’endpoint peut être mal déployé) sauf 410/404 persistants — toujours 5, simplicité.

Timeout lecture/connexion : **10 s**.

Après 5 : `failed`. Console : « Dernière livraison : échec » + **Renvoyer** (`API_KEY_WRITE` / Bearer).

### 11.5 SSRF

Avant chaque POST :

- `live` : schéma `https` seulement ; host non résolu vers loopback, link-local (`169.254.0.0/16`), RFC1918, `::1`.
- `test` : `http://localhost`, `http://127.0.0.1`, `https://` public.

Deny → `ssrf_denied`, pas d’envoi.

### 11.6 Ce n’est pas le webhook Stripe

| | Stripe (M3) | Client (M5) |
|---|---|---|
| Chemin | `POST /v1/webhooks/stripe` (nous écoutons) | nous **appelons** l’URL client |
| Auth | `Stripe-Signature` | `X-RecognizMe-Signature` |
| Effet | crédit ledger | notifier le backend client |

---

## 12. Contrats d’API

Envelope d’erreur inchangée. Routes flow / vérifs M4 **inchangées** (même JSON de fiche : `extracted_identity` devient réel en live).

### 12.1 Organisation — Bearer, scoped intégration

La clé authentifie **une** intégration. Les routes webhook ne prennent **pas** d’`integration_id` dans l’URL : il est celui de la clé.

| Méthode | Chemin | Succès | Corps |
|---|---|---|---|
| PUT | `/v1/webhooks` | **200** | `{ "url" }` → `{ url, secret_prefix, secret?, status }` (`secret` seulement à la création ou rotation) |
| GET | `/v1/webhooks` | **200** | `{ url, secret_prefix, status }` ou **404** si aucun |
| DELETE | `/v1/webhooks` | **204** | — |
| POST | `/v1/webhooks/rotate` | **200** | nouveau `secret` une fois |
| POST | `/v1/webhooks/deliveries/{eventId}/retry` | **202** | relance si `failed` / `delivered` (ops) ; **404** autre intégration |

`POST /v1/webhooks` du CDC = upsert (ici `PUT` REST ; accepter aussi `POST` comme alias d’upsert si on veut coller au tableau CDC mot pour mot). **Choix figé :** `PUT /v1/webhooks` + `POST` alias même handler.

### 12.2 Console — cookie

| Méthode | Chemin | Permission | Succès |
|---|---|---|---|
| GET | `/v1/console/integrations/{id}/webhook` | `API_KEY_READ` | comme GET Bearer |
| PUT | `/v1/console/integrations/{id}/webhook` | `API_KEY_WRITE` | comme PUT Bearer |
| DELETE | idem | `API_KEY_WRITE` | **204** |
| POST | `.../webhook/rotate` | `API_KEY_WRITE` | nouveau secret |
| GET | `.../webhook/deliveries?limit=` | `API_KEY_READ` | liste **sans** payload : `event_id`, `verification_id`, `status`, `attempt`, `http_status`, `created_at` |
| POST | `.../webhook/deliveries/{eventId}/retry` | `API_KEY_WRITE` | **202** |

Le developer voit `verification_id` sur une livraison (uuid opaque) **sans** extraits. Pas de lien fiche s’il n’a pas `VERIFICATION_READ`.

### 12.3 Erreurs nouvelles

| HTTP | `code` | Cas |
|---|---|---|
| 400 | `validation_error` | URL, schéma |
| 400 | `ssrf_denied` | URL live privée |
| 404 | `not_found` | Pas d’endpoint / autre org / autre intégration |
| 409 | `webhook_exists` | (si POST création stricte ; inutilisé si upsert PUT) |

### 12.4 Audit M5

Sans URL secrète, sans extraits :

| `action` | Acteur | Payload |
|---|---|---|
| `webhook.upserted` | `api_key` / `user` | `{}` |
| `webhook.deleted` | idem | `{}` |
| `webhook.rotated` | idem | `{}` |
| `webhook.delivered` | `system` | `{ "event_id" }` |
| `webhook.failed` | `system` | `{ "event_id" }` |
| `verification.completed` | inchangé M4 | `{ "decision" }` |

---

## 13. Interfaces

### 13.1 Hosted flow

**Inchangé.** Pas de Face Liveness Amplify. Consignes selfie = présence stub M4. L’applicant **ne voit pas** la décision (RG-M4-25).

Copy capture pièce : consigne **recto du permis de conduire du Québec** en live n’est **pas** branchée sur le mode (le flow n’a pas le mode). La consigne reste générique M4 (« photographiez votre pièce ») **ou** une phrase neutre. Le corridor publié est la vitrine, pas un texte qui trahirait l’org.

### 13.2 Console — fiche vérification

Inchangée structurellement. En live : extraits **réels**, signaux CompareFaces (score), `mrz_unavailable`, éventuellement `ocr_analyze_id`. Badge d’intégration déjà là.

Pas de bouton « relancer AWS » au MVP.

### 13.3 Console — fiche intégration

Onglet **Settings** : le placeholder `console.product.integrationsWebhooks` devient le formulaire.

| Élément | Comportement |
|---|---|
| URL | Champ HTTPS ; save `API_KEY_WRITE` |
| Secret | Affiché une fois à la création / rotation ; ensuite `secret_prefix` + bouton **Régénérer** |
| Statut dernière livraison | `delivered` / `failed` / `pending` / « aucune » |
| Liste courte | Dernières livraisons (ids, statut) |
| Renvoyer | Si `failed` |
| Developer | Accès **oui** (clés + webhook). Pas d’entrée liste vérifs |

Readonly : pas de PUT. Member **sans** `API_KEY_*` : pas de secret, pas de formulaire (le member n’est pas developer).

i18n FR/EN. `StatusBadge` pour le statut de livraison (jamais la couleur seule).

### 13.4 Vitrine

`/products/identity-verification` (FR + EN) :

- Une classe documentaire live : **permis de conduire du Québec (Canada), recto**.
- Champs lus : nom, naissance, numéro, expiration.
- **Pas** de MRZ présentée comme exigence.
- **Pas** « passeports ICAO CA/FR/US » tant que non mesurés.
- Sandbox : reste « testez sans carte / stubs » sans afficher la fixture Marie Dupont comme corridor commercial.

`/pricing` inchangé (0,90 $ live).

### 13.5 Amendement CDC (avant vert M5)

À porter dans [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) **avant** de déclarer M5 terminé :

**§11.3 Corridor documents MVP** — remplacer la liste passeport/CNI par :

> Live (staging / prod) : **Canada — permis de conduire du Québec**, recto. Hors mapping → `unsupported_document`.  
> Sandbox : fixture stub (passeport FR fictif) ; **n’est pas** la liste publiée.

**§17.9** — remplacer l’exigence Face Liveness par :

> Live (staging) : même parcours avec Textract AnalyzeID + Rekognition CompareFaces. Liveness = stub qualité **documenté** (Face Liveness AWS reporté). Face match AWS **exigé**.

Si D1 no-go AnalyzeID : amender aussi **§11.2** (DetectText + mapping spécimen).

---

## 14. Exigences non fonctionnelles

| ID | Applicable M5 |
|---|---|
| **NF-MVP-01** | Isolation dossier, média, webhook, livraison |
| **NF-MVP-03** | Logs sans PII, sans secret, sans binaire |
| **NF-MVP-04** | Pas d’URL permanente / signée dans un webhook |
| **NF-MVP-05** | Sandbox : 0 Textract / Rekognition |
| **NF-MVP-06** | Happy path live en minutes (AnalyzeID + CompareFaces in-process) |
| **NF-MVP-08** | Formulaire webhook : focus, labels, FR/EN |
| **COGS** | Qualité avant l’appel ; pas de CompareFaces si unsupported ; ~0,026 $ IA sans Liveness AWS ([coûts](../cout-unitaire-verification.md)) |

Rate limit `POST /v1/verifications` : **M6**. Secrets AWS : env / instance role, **hors git** (`application-secrets.properties` déjà le pattern Stripe).

---

## 15. Traçabilité CDC

| Exigence | Couverture |
|---|---|
| O5 production / §17.9 | UC-M5-01, démo §17 ; Liveness stub + amendement §13.5 |
| §17.11 webhook signé | UC-M5-03, §11 |
| §17.10 isolation | UC-M5-05 |
| §17.12 `unsupported_document` | UC-IDV-10 live, RG-M5-05 |
| §11.1 happy path | §6.1 (avec webhook) |
| §11.2 analyse | §5.3, §10 ; DetectText seulement si D1 no-go |
| §11.3 corridor | §2.4, §10, vitrine §13.4, amendement CDC |
| §11.4 décision | §9.1 |
| §11.5 webhooks minimum | `verification.completed` seul |
| §11.6 `POST /v1/webhooks` | §12.1 |
| NF-MVP-04 / 05 | RG-M5-09, RG-M5-01 |
| RG-M4-13 | RG-M5-02 |
| UC-ISO-01 | UC-M5-05 |

---

## 16. Acceptation et tests

**Démo M5 :** compte crédité → intégration live (`ky_live_`) → lien → consentement → **recto permis QC** → selfie → décision en console (extraits réels) → webhook reçu sur un endpoint de test (signature vérifiée) → org B **404**. Sandbox parallèle : même compte, intégration test, 0 AWS.

**Kill :** sandbox qui invoque AWS ; URL média dans un webhook ; juge LLM ; corridor vitrine plus large que le permis QC mesuré ; secret webhook re-lisible ; webhook Stripe et webhook client confondus ; `ky_live_` sans débit.

### 16.1 Tests API (minimum)

| Test | Couvre |
|---|---|
| `SandboxNoAwsTest` (réécrit) | Session test → 0 client AWS ; classes `Aws*` **peuvent** exister |
| `LiveDecisionQcTest` | Fixture AnalyzeID QC → mapping + `approved` / extraits (AWS mocké) |
| `UnsupportedLiveTest` | Fixture passeport / unknown → `unsupported_document`, 0 CompareFaces |
| `ExpiredLiveTest` | Expiration passée → `document_expired` |
| `FaceMatchThresholdTest` | Scores 0.74 / 0.80 / 0.91 → declined / review / approved |
| `SandboxScenarioIgnoredLiveTest` | `metadata.sandbox_scenario=approved` en live + signaux fail → pas approved |
| `WebhookSignatureTest` | HMAC + `t` ; body altéré rejeté |
| `WebhookRetryTest` | 500 distant → 2ᵉ tentative ; 2xx → `delivered` |
| `WebhookReplayIdempotenceTest` | Même `event_id` renvoyé au retry manuel |
| `WebhookNoMediaTest` | JSON livraison sans `http` média / `object_key` |
| `WebhookIsolationTest` | Org B 404 ; clé intégration 1 ≠ webhook intégration 2 |
| `WebhookSsrfTest` | `http://169.254.169.254/` en live → 400 / pas d’envoi |
| `ProviderUnavailableTest` | Textract 500 → `review` + `provider_unavailable` |
| Régression | `CreateVerificationTest`, `LiveDebitTest`, `IsolationTest`, `SandboxNoAws` path M4, `LiveIntegrationGateTest` |

Tester AnalyzeID **réel** : hors CI (secret). Un job manuel D1 + [guide Textract](./guide-aws-textract.md). La CI utilise des **JSON AnalyzeID** / réponses CompareFaces enregistrés (pas de compte AWS dans GitHub Actions au MVP).

### 16.2 Mesure D1 (go / no-go)

Checklist **bloquante** avant d’écrire le mapper de prod. Détail opérationnel : [`guide-aws-textract.md`](./guide-aws-textract.md).

- [ ] Photo nette recto QC, qualité SDK OK
- [ ] AnalyzeID staging exécuté
- [ ] Type + nom + naissance + expiration + numéro présents **ou** CDC §11.2 DetectText amendé
- [ ] Juridiction QC identifiable
- [ ] Région AWS notée (`ca-central-1` ou repli)

---

## 17. Ordre de construction (paliers)

Un palier n’est pas vert sans livrable démontrable. Durée indicative : **2 semaines**. Détail In/Out, tests, démos : [`roadmap-implementation-m5.md`](./roadmap-implementation-m5.md).

```
D1  Mesure AnalyzeID spécimen QC + freeze mapping / région
        │
        ▼
D2  AwsDocumentAi + decideLive m5-1 + extraits console
        │     (CompareFaces encore mockable)
        ▼
D3  AwsBiometric CompareFaces + seuils ; liveness stub documenté
        │
        ▼
D4  Webhook HMAC + retry + console settings + isolation + vitrine corridor
        │
        ▼
Démo §16 + amendements CDC §11.3 / §17.9 (et §11.2 si DetectText)
```

| Palier | Livrable démontrable | Interdit tant que… |
|---|---|---|
| **D1** | JSON AnalyzeID d’un vrai recto QC ; tableau de mapping écrit ([guide](./guide-aws-textract.md)) | Coder un catalogue « CA » au feeling |
| **D2** | Session live mockée → extraits QC en fiche ; test 0 AWS | Brancher Rekognition |
| **D3** | CompareFaces mock + seuils `m5-1` ; unsupported saute le match | Face Liveness Amplify |
| **D4** | Endpoint de test reçoit un POST signé ; org B 404 ; vitrine QC | Catalogue d’événements, SQS |

Interdit : D2 sans D1 mesuré (ou no-go DetectText écrit) ; D4 avec média dans le payload ; vert M5 sans amendement CDC.

---

## 18. Suite

| Après M5 vert | Sprint |
|---|---|
| Rate limit, rétention affichée, staging partenaire sandbox | **M6** |
| Face Liveness AWS (Amplify) si on lève le stub | Après M5 / M6 (roadmap MVP §9) |
| Passeport / autre province **après mesure** + amendement §11.3 | Hors cycle |
| SageMaker / dataset | Roadmap IDV, **après M6** |
| InContext JS SDK | Après M4 (indépendant, pas bloquant M5) |

M5 **réutilise** ports, statuts, raisons, flow et fiche. Le stub **reste** le chemin `ky_test_`. Remplacer plus tard `AwsDocumentAi` par un endpoint SageMaker **ne change pas** l’API client ni le webhook.

---

## 19. Glossaire (ajouts M5)

| Terme | Sens |
|---|---|
| **Corridor live** | Seule classe documentaire réellement parsée et **publiée** : permis QC recto |
| **AnalyzeID** | API Textract de lecture de pièces d’identité |
| **CompareFaces** | API Rekognition 1:1 selfie ↔ portrait de session |
| **`m5-1`** | Version des règles live (signaux réels) |
| **`m4-1`** | Version des règles stub (scénario) — inchangée |
| **Endpoint** | URL HTTPS du client, une par intégration |
| **`event_id`** | Id de livraison, clé d’idempotence côté client |
| **D1** | Palier de mesure AnalyzeID, avant le mapping figé |

Les termes session, applicant, signal, revue : spec IDV §4 et spec M4 §4.
