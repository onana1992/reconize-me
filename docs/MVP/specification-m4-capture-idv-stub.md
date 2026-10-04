# Spécification — M4 Capture + IDV stub

**Plateforme :** Recogniz-Me  
**Sprint :** M4 — session IDV, hosted flow, capture pièce + selfie, pipeline stub, décision sandbox  
**Version du document :** 1.3  
**Date :** 2 octobre 2026  
**Statut :** spécification as-built (C1–C4 livrés). Alignée sur l’implémentation courante : sprints **I** (intégrations), **M3** (crédit) et **M5** (AWS + webhooks) sont **livrés** sur les mêmes surfaces.  
**CDC :** §11 (stub), §13 UC-IDV, critère §17.8. **Objectif O5 (sandbox).**  
**Prérequis (historiques M4) :** M0, M2 (console authentifiée, clés `ky_test_`, droits `VERIFICATION_*`). M3 n’était **pas** un prérequis de M4.

**Documents liés :**
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — contrat (§11, NF-MVP-01/03/04/05, §17.8 / §17.10 / §17.12)
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — ordre des sprints
- [`roadmap-implementation-m4.md`](./roadmap-implementation-m4.md) — ordre de build C1–C4
- [`specification-m2-compte-client.md`](./specification-m2-compte-client.md) — comptes, cookie `rm_session`, clés, matrice T2
- [`specification-m5-aws-live-webhook.md`](./specification-m5-aws-live-webhook.md) — chemin **live** (AnalyzeID, CompareFaces, webhook par intégration)
- [`../specs/specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md) — métier IDV (cycles, UC-IDV-01 à 10, RG-IDV)
- [`../specs/specification-implementation-idv.md`](../specs/specification-implementation-idv.md) — paliers P1–P3, contrats HTTP
- [`charte-visuelle.md`](./charte-visuelle.md) — `flow.css` vs `console.css`

Ce document décrit **le chemin stub** : parcours sandbox de bout en bout, **sans aucun appel AWS IA**. C’est toujours le contrat des intégrations `test` (`ky_test_`). Le *quoi* métier reste la [spec IDV](../specs/specification-fonctionnelle-idv.md) ; les **écarts MVP** (stub sandbox, corridor live distinct, pas d’enrôlement, pas d’AML) sont ceux du CDC §11. Le CDC **prime** en cas de conflit métier.

Les surfaces (session, flow, capture, ports, fiche) sont **partagées** avec le live. Ce que I / M3 / M5 ont ajouté (intégration obligatoire, débit live, AnalyzeID, webhook HTTP) est **noté as-built** ici pour ne plus dire « pas encore » ; le détail live reste la [spec M5](./specification-m5-aws-live-webhook.md). L’ordre de construction M4 reste la [roadmap M4](./roadmap-implementation-m4.md).

---

## 1. Objet

Permettre à une organisation **sandbox** de **prouver une identité à distance** : créer une session, envoyer un lien, laisser l’applicant consentir puis photographier une pièce et son visage, et relire en console une **décision justifiée**.

**Avant M4 :** tables IDV retirées (Flyway V7) ; `POST /v1/console/verifications` → **410** `idv_unavailable` ; liste console vide.

**Après M4 :** le happy path CDC §11.1 tourne en `ky_test_` : consentement → pièce → selfie → statut terminal + raisons, 0 Textract / Rekognition.

**As-built octobre 2026 (hors contrat M4, déjà en code) :** chaque session porte un `integration_id` (sprint I, V15) ; une clé Bearer est **scopée à cette intégration** ; `test` → stub `m4-1` ; `live` → `m5-1` (AnalyzeID + CompareFaces) + débit M3 à la création ; `verification.completed` est **audité et**, si un endpoint est configuré, **livré en HTTP signé** (M5). Face Liveness AWS n’est **pas** livré (liveness live = stub qualité, spec M5).

**Ce que le contrat M4 n’était pas** (livré ensuite, sauf mention)

- Une analyse live (Textract, Rekognition) — **M5**, livré.
- Face Liveness AWS — **après M5**, toujours reporté.
- Un webhook `verification.completed` HTTP client — **M5**, livré (l’audit interne existait déjà en M4).
- Un débit de crédit / clé `ky_live_` — **M3**, livré.
- Entité `Integration` + chrome sans `?env=` — **I**, livré.
- Un dataset, SageMaker, authenticité ML, enrôlement biométrique, AML attaché.
- Une revue opérée par Recogniz-Me.
- SDK InContext (`createFrame`) — snippet d’install en console ; package **pas** livré.

---

## 2. Périmètre

### 2.1 Inclus

| Domaine | Contenu |
|---|---|
| Session | Création, lecture, liste paginée, annulation ; `external_id` unique par org ; idempotence ; **as-built :** `integration_id` NOT NULL (V15) |
| Lien hébergé | Token dans l’URL, TTL 1 h (défaut), hash en base, store Redis (ou mémoire en test) |
| Consentement | Une fois ; `accepted` / `declined` ; IP hashée ; version de texte persistée |
| Capture pièce | Qualité client (`capture-sdk`) + PUT signé + filet serveur ; recapture, plafond **3** |
| Capture visage | Selfie + liveness **stub** ; recapture, plafond **3** |
| Stockage | Port objet **réel** (filesystem) ; clé préfixée `org/{organizationId}/…` ; GET/PUT signés `/v1/objects` |
| Pipeline stub | Scénario injectable ; ports `DocumentAiPort` / `BiometricAiPort` invoqués, **zéro réseau AWS** |
| Décision | Moteur `m4-1` → `approved` / `declined` / `review` + raisons **sans PII** |
| Console | Liste, création, fiche (extraits, signaux, médias URL signée courte), revue |
| Flow | App `web/flow` :3001, i18n FR/EN, charte `flow.css` |
| Isolation | Org B sur l’id / le média / le token de A → **404**, identique à un id inconnu |

### 2.2 Hors contrat M4

| Domaine | Où c’est (as-built) |
|---|---|
| AWS IA | Textract AnalyzeID + Rekognition CompareFaces — **M5**. Face Liveness AWS **pas** livré |
| Webhooks client | Un endpoint **par intégration**, HMAC, retry borné — **M5** (V19 / V20) |
| Live / crédit | `ky_live_`, débit à la création, solde insuffisant — **M3** |
| Intégration | Entité, clés rattachées, plus de `?env=` — **I** (V15–V16) |
| Vision proprio | SageMaker, dataset, Canny vendu comme moteur — **hors MVP** |
| Produits | Biométrie auth, AML, enrôlement, KYB — teasers site |
| Flow | Branding du tenant, SDK natif, liveness passif AWS — reporté |
| Opérateur RM | Revue KYC par Recogniz-Me — hors MVP |
| Verso CNI | Le kind `document_back` existe en schéma ; le stub M4 n’exige **pas** le verso (passeport FR fictif) |

### 2.3 Invariants à ne pas casser

Isolation 404, envelope `{ error }`, trois plans d’auth disjoints (clé / cookie / token URL), hash BCrypt des clés, Bearer obligatoire sur `/v1/verifications/**` (cookie insuffisant), pas de PII applicant ni de token brut dans les logs, pas de binaire dans le JSON métier.

Les organisations et tests compte (M2 / T) restent verts.

### 2.4 Décisions figées (as-built)

| Sujet | Choix |
|---|---|
| Corridor **stub** (ce document) | Passeport ICAO **FR** (fixture) ; hors corridor → `unsupported_document` |
| Corridor **live** | Permis QC recto — **M5**, pas la fixture sandbox |
| Pièce expirée | **Refus** (`document_expired`) via scénario |
| Plafond d’essais | **3** uploads par `kind` (`document` / `selfie`), même session |
| Statut CDC « REJECTED » | API minuscule **`declined`** (consentement refusé **ou** décision négative) |
| Liveness / face match | Stub déterministe selon `sandbox_scenario` |
| Worker | **In-process** dans `selfie/complete` (pas de SQS) |
| Flyway M4 | `V12__idv_session.sql`, `V13__varchar_idv_hashes.sql` |
| Flyway ensuite | V15–V16 intégrations ; V17–V18 crédit ; V19–V20 webhooks |
| SGBD | **MySQL** (`reconizme`) |
| Hash token hosted | SHA-256 du jeton brut (**sans** pepper) |
| Hash IP consentement | SHA-256(pepper + IP) |
| Décision | Pilotée par le **scénario**, pas par le contenu réel de l’image |

---

## 3. Acteurs

| Acteur | Description | Preuve d’identité |
|---|---|---|
| **Backend client** | Intégrateur, scripts, CI | Bearer `ky_test_` **ou** `ky_live_` de **l’intégration** |
| **Opérateur console** | Humain de l’org (member / admin / owner / readonly) | Cookie `rm_session` + `VERIFICATION_READ` / `_WRITE` |
| **Applicant** | Personne à vérifier | Possession du token d’URL — **jamais** de compte Recogniz-Me |
| **Analyste (client)** | Tranche les dossiers `review` | Cookie + `VERIFICATION_WRITE` |
| **Système** | Expire le lien, enchaîne stub + règles, journalise | — |

Recogniz-Me n’est **pas** analyste KYC au MVP (CDC §12).

Le **developer** n’a ni `VERIFICATION_READ` ni `VERIFICATION_WRITE` : pas de liste, pas de fiche, pas de PII de session.

---

## 4. Concepts

| Concept | Définition M4 |
|---|---|
| **Session / vérification** | Dossier isolé par organisation, rattaché à une **intégration**. Identifiant opaque UUID. |
| **Intégration** | Unité CDC 1.4, **livrée (I)** : `test` \| `live` ; porte clés, webhook (M5), sessions. Le préfixe de clé **est** le mode. |
| **Lien hébergé** | `{public-flow-base}/flow/{token}`. Token brut **une fois** dans `hosted_url` à la création (et au GET tant que le token Redis vit). |
| **Web flow** | Même `hosted_url` : redirect plein écran (as-built). Iframe InContext : snippet d’install ; package SDK **pas** livré. Pas de clé API dans le navigateur. |
| **next** | Consigne flow : `consent`, `capture_document`, `capture_selfie`, `wait`, `done`. |
| **Tentative** | Un essai photo numéroté (`attempt`) pour un `kind`. Recapture = tentative suivante, **même** session. |
| **Signal** | Code + `outcome` (`pass` / `fail` / `unavailable`) + score optionnel. Pas une décision. |
| **Raison** | Code anglais persisté dans `decision_reasons[]`, **sans PII**. |
| **Stub** | Adaptateur déterministe. Zéro réseau AWS. Scénario injectable (`metadata.sandbox_scenario`) **uniquement** si `integrations.mode = test`. |
| **URL média** | GET signé, TTL ~5 min (`/v1/objects?key&exp&sig&method=`). Jamais d’URL permanente dans une API métier ou un log. |
| **Scénario sandbox** | Clé de décision stub. **Ignoré** en live (`sandbox_scenario` posé à `null` à la création, RG-M4-13). |

---

## 5. Architecture

### 5.1 Surfaces

| App | Port | Rôle |
|---|---|---|
| `web/console` | 3000 | Opération IDV derrière session |
| `web/flow` | 3001 | Parcours applicant ; proxy `/v1/*` → API |
| `packages/capture-sdk` | — | Qualité **client** pièce / visage |
| `api/` | 8080 | Un seul JAR : compte + IDV + stub + stockage |

`web/site` (3002) : inchangé. Pas d’appel métier depuis la vitrine.

### 5.2 Trois plans d’authentification

```
Opérateur                      Backend                         Applicant
   │                              │                                │
   ▼                              ▼                                ▼
web/console                    scripts / CI                     web/flow
cookie rm_session              Bearer ky_test_ / ky_live_       token URL
   │                              │                                │
   ▼                              ▼                                ▼
/v1/console/verifications*     /v1/verifications/**             /v1/flow/**
                               (scopé à l’intégration)          /v1/objects (sig)
```

- Cookie **n’authentifie pas** `/v1/verifications/**` → **401**.
- Clé **n’authentifie pas** `/v1/console/**` ni `/v1/flow/**`.
- Token **n’authentifie pas** les routes org. `/v1/flow/**` et `/v1/objects` sont `permitAll` : la **possession du token / de la signature** fait foi.
- `SessionCreationPolicy.STATELESS` — pas de session servlet mélangée au Bearer.

CORS : `kyc.cors-allowed-origin-patterns` (localhost, LAN, tunnels), credentials, `GET` `POST` `PUT` `PATCH` `DELETE` `OPTIONS`.

### 5.3 Chaîne de filtres

1. `ApiKeyAuthenticationFilter` — `/v1/verifications/**`. Absence de Bearer → **401**, **même avec cookie**.
2. `SessionAuthenticationFilter` — `/v1/console/**` (et logout). Cookie manquant / membership inactive → **401**.
3. Métier console : `ConsoleAuth.require(VERIFICATION_READ|WRITE)` → **403** avant tout 404.
4. Flow : pas de filtre d’identité humaine ; `HostedFlowService.load(token)` → 404 / 410.

### 5.4 Chaîne métier

```
Création (clé de l’intégration ou console)
        │  test : sandbox_scenario ; live : débit M3, scenario = null
        │  transaction : store token puis ligne verifications + integration_id
        ▼
Lien Redis/mémoire + hash en base
        │
        ▼
Applicant GET flow → pending_consent
        │
        ▼
POST consent accepted → pending_applicant / declined
        │
        ▼
Capture pièce : qualité client → POST uploads → PUT /v1/objects → POST complete
        │
        ▼
Capture selfie (même schéma)
        │
        ▼
selfie/complete OK → processing
        │
        ├─ mode test  → stub + IdvDecisionEngine.decide (m4-1)
        └─ mode live  → AnalyzeID + CompareFaces + decideLive (m5-1)
        │
        ▼
approved | declined | review     (raisons persistées)
        │
        ├─ audit verification.completed
        └─ enqueue webhook si endpoint (M5)
        │
        ▼
Console : fiche + médias signés ; revue si review
```

**As-built test :** `StubDocumentAi` / `StubBiometricAi` sont **appelés** (preuves « 0 AWS ») ; la décision lue en console vient d’`IdvDecisionEngine.decide` selon le **scénario**, pas du contenu des pixels.

**As-built live :** `HostedFlowService` lit `integrations.mode`. `AwsDocumentAi` + `AwsBiometricAi` ; `sandbox_scenario` ignoré. Détail : spec M5.

### 5.5 Composants applicatifs

| Couche | Responsabilité |
|---|---|
| `VerificationsController` | Bearer `/v1/verifications` — clé scopée à `principal.integrationId` |
| `ConsoleVerificationController` | Cookie `/v1/console/verifications` — mêmes verbes, `require(Permission)` ; liste filtrable `integration_id` |
| `FlowController` | `/v1/flow/{token}` — `Cache-Control: no-store` |
| `ObjectsController` | PUT/GET signés `/v1/objects` (hidden OpenAPI) |
| `VerificationService` | Création (intégration + débit live), liste, fiche, cancel, review, URL média ; enqueue webhook à la revue |
| `HostedFlowService` | Hydrate, consentement, uploads, complete, décision in-process (**routage** stub / live) |
| `IdvDecisionEngine` | `decide` = `m4-1` / scénario ; `decideLive` = `m5-1` / signaux AWS |
| `WebhookService` | File `webhook_deliveries` (M5) — appelé aussi depuis le stub si un endpoint existe |
| `MediaQuality` | Filet serveur JPEG/PNG, ≤ 10 Mo, côté court ≥ 720 px |
| `HostedTokenStore` | Redis `hosted:v1:` / `hosted:vid:` ou `ConcurrentHashMap` |
| `ObjectStoragePort` / `FilesystemObjectStorage` | Disque `kyc.object-storage-root` |
| `capture-sdk` | `assessDocumentFrame` / `assessSelfieFrame` côté navigateur |

### 5.6 Séquence — création jusqu’à la décision

```
Opérateur/API          API                 Store              Applicant              Disque
     │                  │                    │                    │                    │
     │ POST /verifications                   │                    │                    │
     │─────────────────►│ put(token,TTL)     │                    │                    │
     │                  │───────────────────►│                    │                    │
     │                  │ INSERT verification│                    │                    │
     │ 201 hosted_url   │                    │                    │                    │
     │───────────────────────────────────────► ouvre /flow/{token}│                    │
     │                  │ GET flow           │                    │                    │
     │                  │◄───────────────────┼────────────────────│                    │
     │                  │ get(token)         │                    │                    │
     │                  │ status=pending_consent                  │                    │
     │                  │ POST consent accepted                   │                    │
     │                  │ INSERT consents    │                    │                    │
     │                  │ POST document/uploads                   │                    │
     │                  │ sign PUT           │                    │                    │
     │                  │ PUT /v1/objects    │                    │───────────────────►│
     │                  │ POST document/complete                  │                    │
     │                  │ qualité OK → selfie│                    │                    │
     │                  │ POST selfie/complete                    │                    │
     │                  │ test: stub+decide / live: AWS+decideLive│                    │
     │                  │ enqueue webhook si endpoint             │                    │
     │ GET fiche        │ approved + reasons │                    │                    │
```

### 5.7 Stockage des médias

Port `ObjectStoragePort` : `createSignedUploadUrl`, `createSignedGetUrl`, `exists`, `read`, `write`, `verifySignature`. Jamais de stream dans le JSON métier.

Clé objet **toujours** préfixée par l’organisation :

```
org/{organizationId}/verifications/{verificationId}/document/{attempt}
org/{organizationId}/verifications/{verificationId}/document_back/{attempt}
org/{organizationId}/verifications/{verificationId}/selfie/{attempt}
```

Signature : HMAC (pepper) sur `method|key|exp`. TTL upload / lecture **5 minutes**. Adapter M4 : filesystem sous `kyc.object-storage-root` (défaut `./data/media`).

Un token de l’org A ne signe **jamais** une clé de l’org B : la clé contient `organizationId` ; `mediaUrl` ne résout que les médias `accepted` du tenant.

---

## 6. Modèle de données

Migrations M4 : `V12__idv_session.sql` (tables), `V13__varchar_idv_hashes.sql` (CHAR→VARCHAR des hashs). Ne pas réécrire V1–V11. **Ensuite :** V15 rattache `verifications.integration_id` ; V17 crédit ; V19 webhooks.

### 6.1 Schéma relationnel

```
organizations
       │ 1
       ├──────── *     integrations     (V15 ; product, mode test|live)
       │                    │
       │                    │ *
verifications ────────── hosted token (Redis / mémoire, hors SQL)
 (org, integration_id, …)      clé hosted:v1:{token}
       │ 1
       ├──────── 0..1  consents          (UNIQUE verification_id)
       ├──────── *     verification_media  UNIQUE (verification_id, kind, attempt)
       └──────── *     verification_signals

idempotency_keys
 PK (organization_id, idempotency_key)
```

`audit_events` (S1) reçoit les actions IDV (`organization_id` obligatoire, payload sans PII).

### 6.2 `verifications`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK, opaque |
| `organization_id` | CHAR(36) | FK `organizations`, isolation |
| `integration_id` | CHAR(36) | FK `integrations`, **NOT NULL** (V15) |
| `external_id` | VARCHAR(128) | NULL ; UNIQUE `(organization_id, external_id)` |
| `status` | VARCHAR(32) | Voir §6.6 |
| `applicant_first_name` / `_last_name` | VARCHAR(128) | NULL — indices à la création, **jamais** dans le JSON flow |
| `applicant_email` | VARCHAR(320) | NULL |
| `metadata` | TEXT | JSON profondeur ≤ 2, ≤ 4096 octets |
| `hosted_token_hash` | VARCHAR(64) | UNIQUE — SHA-256 du token brut |
| `hosted_expires_at` | DATETIME(6) | TTL lien |
| `decision` | VARCHAR(32) | NULL puis `approved` \| `declined` \| `review` |
| `decision_reasons` | TEXT | JSON tableau de codes |
| `rules_version` | VARCHAR(32) | ex. `m4-1` |
| `sandbox_scenario` | VARCHAR(64) | Copié depuis metadata à la création |
| `extracted_identity` | TEXT | JSON après décision ; **absent** du flow |
| `created_at` / `updated_at` | DATETIME(6) | |

Index `(organization_id, created_at)`, `(organization_id, status)`, `(integration_id)`.

Isolation reste `organization_id`. La liste Bearer est **en plus** filtrée par l’intégration de la clé. La console peut passer `integration_id` en query.

### 6.3 `consents`

Une ligne par session (`UNIQUE verification_id`).

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK |
| `verification_id` | CHAR(36) | FK, unique |
| `decision` | VARCHAR(16) | `accepted` \| `declined` |
| `text_version` | VARCHAR(64) | `kyc.consent-text-version` (défaut `consent-v1`) |
| `accepted_at` | DATETIME(6) | Horodatage de **l’enregistrement** (y compris si declined) |
| `ip_hash` | VARCHAR(64) | SHA-256(pepper + IP), **pas** d’IP en clair |
| `user_agent` | VARCHAR(512) | Tronqué à 512 |

### 6.4 `idempotency_keys`

| Colonne | Type | Contraintes |
|---|---|---|
| `organization_id` | CHAR(36) | PK composite, FK |
| `idempotency_key` | VARCHAR(64) | PK composite ; header 8–64 ASCII imprimable |
| `request_hash` | VARCHAR(64) | SHA-256 du corps |
| `verification_id` | CHAR(36) | Session créée |
| `created_at` | DATETIME(6) | |

Même clé + même corps → **200** replay. Même clé + autre corps → **409** `idempotency_key_conflict`.

### 6.5 `verification_media` / `verification_signals`

**Médias**

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK |
| `verification_id` | CHAR(36) | FK |
| `kind` | VARCHAR(32) | `document` \| `document_back` \| `selfie` |
| `attempt` | INT | 1..3 |
| `object_key` | VARCHAR(512) | Chemin préfixé org |
| `content_type` | VARCHAR(64) | NULL tant que pending ; `image/jpeg` \| `image/png` |
| `byte_size` | BIGINT | NULL tant que pending |
| `status` | VARCHAR(32) | `pending` \| `accepted` \| `rejected_quality` |
| `created_at` | DATETIME(6) | |

UNIQUE `(verification_id, kind, attempt)`.

**Signaux**

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK |
| `verification_id` | CHAR(36) | FK |
| `code` | VARCHAR(64) | Anglais, sans PII |
| `outcome` | VARCHAR(32) | `pass` \| `fail` \| `unavailable` |
| `score` | DOUBLE | NULL |
| `created_at` | DATETIME(6) | |

Aucun champ d’identité en clair.

### 6.6 Machine à états

```
created
   │ GET flow
   ▼
pending_consent
   │ POST consent
   ├─ declined ──────────────────────────────► declined (terminal, raison consent_declined)
   ▼ accepted
pending_applicant
   │ premier upload pièce
   ▼
document ── qualité KO ──► recapture_requested ── 3e échec ──► declined (capture_attempts_exceeded)
   │ OK
   ▼
selfie ── qualité KO ──► recapture_requested (selfie) ── 3e échec ──► declined
   │ OK
   ▼
processing  (in-process, souvent non observé)
   │
   ├─► approved     (terminal)
   ├─► declined     (terminal)
   └─► review ── POST review ──► approved | declined
```

Autres terminaux : `expired` (TTL lien), `cancelled` (org).

| Statut | Signification | Terminal |
|---|---|---|
| `created` | Lien jamais ouvert | non |
| `pending_consent` | Lien ouvert, pas de consentement | non |
| `pending_applicant` | Consentement accepté, capture pas commencée | non |
| `document` | En attente d’une pièce acceptable | non |
| `recapture_requested` | Photo à refaire | non |
| `selfie` | Pièce acceptable, attente visage | non |
| `processing` | Médias OK, stub + règles | non |
| `review` | La machine ne tranche pas seule | non |
| `approved` | Identité vérifiée | oui |
| `declined` | Consentement refusé **ou** décision négative **ou** plafond d’essais | oui |
| `expired` | Lien périmé | oui |
| `cancelled` | Arrêt par l’organisation | oui |

Premier `GET /v1/flow/{token}` sur `created` → `pending_consent` (une fois). Audit `hosted_link.opened`.

**Annulation :** autorisée en `created`, `pending_consent`, `pending_applicant`, `document`, `recapture_requested`, `selfie`. Hors fenêtre (`processing`, `review`, déjà terminal) → **409** `invalid_status`. Annuler **révoque** le token du store.

### 6.7 Jetons hors SQL

| Jeton | Où | TTL | Hash en base |
|---|---|---|---|
| Hosted URL | Redis `hosted:v1:{token}` + index `hosted:vid:{verificationId}` (ou Map) | `kyc.hosted-url-ttl-seconds` (3600) | `verifications.hosted_token_hash` |
| Upload / GET objet | Query `exp` + `sig` | 5 min | — |

Le **jeton brut** n’est jamais stocké. `hosted_url` est reconstruite au GET org/console **uniquement** si le store a encore le token.

Expiration : GET flow alors que le store n’a plus l’entrée **ou** `hosted_expires_at` dépassé → statut `expired`, token révoqué, **410** `hosted_link_expired`. L’org crée une **nouvelle** session. Pas de régénération automatique.

### 6.8 Ce qui n’est pas dans M4 (état actuel)

`document_back` n’est **toujours pas** exigé par le moteur stub (passeport FR). Pas de SQS : worker in-process (décision) + table `webhook_deliveries` + scheduler (M5).

**Livré ensuite, présent en base :** `integrations` (V15–V16), ledger / crédit (V17–V18), `webhook_endpoints` / `webhook_deliveries` (V19, `payload_hash` VARCHAR V20), clés et sessions `live`.

---

## 7. Cas d’utilisation

Les UC-IDV-01 à 10 de la spec fonctionnelle s’appliquent, **bornés** au stub sandbox. Alternatives et post-conditions sont **normatives**.

### UC-IDV-01 — Créer une vérification

**Acteur :** Bearer de **l’intégration** **ou** `VERIFICATION_WRITE`.  
**Précondition :** intégration existante ; console = e-mail vérifié + membership active. Live : solde ≥ une unité (M3).

**Scénario nominal**

1. `POST` `{ external_id?, applicant?, metadata? }` (corps optionnel). L’intégration vient de la **clé** (Bearer) ou du sélecteur console.
2. Mode `live` : débit M3 ; `sandbox_scenario` forcé `null`. Mode `test` : `sandbox_scenario` lu dans metadata, défaut `approved` ; **0** débit.
3. Store token ; si KO → **503** `dependency_unavailable`, **aucune** ligne `verifications` (et pas de débit fantôme : le débit live est dans la même transaction).
4. Insert session `status: created`, `integration_id` NOT NULL.
5. **201** + `hosted_url`. Audit `verification.created` puis `hosted_link.issued` (acteur `api_key` ou `user`).

**Post-condition :** une session isolée, rattachée à une intégration ; un token vivant ; hash en base.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | Sans auth | **401** `unauthorized` |
| A2 | Console sans `VERIFICATION_WRITE` | **403** `forbidden` |
| A3 | Cookie seul sur `/v1/verifications` | **401** |
| A4 | `external_id` déjà pris dans l’org | **409** `external_id_conflict` |
| A5 | `Idempotency-Key` 8–64 ASCII, même corps | **200** replay (pas de 2e ligne) |
| A6 | Même clé, autre corps | **409** `idempotency_key_conflict` |
| A7 | Clé hors 8–64 / non ASCII imprimable | **400** `validation_error` |
| A8 | Metadata > 4096 octets ou profondeur > 2 | **400** `validation_error` |
| A9 | Store token indisponible | **503** `dependency_unavailable` |
| A10 | Intégration live, solde insuffisant | **403** `insufficient_credit` (M3) |

`sandbox_scenario` lu dans `metadata.sandbox_scenario` **ou** `metadata.sandbox.scenario`, défaut `approved` — **uniquement** si l’intégration est `test`.

### UC-IDV-02 — Ouvrir le lien et consentir

**Acteur :** applicant (token).

**Scénario nominal**

1. Ouvre `{public-flow-base}/flow/{token}`. Flow proxy → `GET /v1/flow/{token}`.
2. Réponse `Cache-Control: no-store`. JSON : `verification_id`, `status`, `consent_text_version`, `expires_at`, `next`. **Pas** `organization_id`, `external_id`, nom / e-mail applicant, nom d’org, extraits, raisons.
3. `next` = `consent` tant que pas enregistré.
4. `POST .../consent` `{ "decision": "accepted" | "declined" }` (query `?decision=` acceptée aussi) → **201**.
5. Accepté → `pending_applicant`, `next: capture_document`. Refusé → `declined`, raison `consent_declined`, **zéro** média.
6. Audit `consent.accepted` / `consent.declined` (acteur `applicant`, `actor_id` null). IP hashée.

**Écrans flow :** lien invalide ; lien expiré (« contactez l’entreprise ») ; consentement. **Pas** le nom du tenant.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | Token inconnu | **404** `not_found` |
| A2 | Token évincé / TTL dépassé / session cancelled | **410** `hosted_link_expired`, statut `expired` si encore ouvert |
| A3 | Second POST consentement | **409** `consent_already_recorded` |
| A4 | `decision` autre que accepted/declined | **400** `validation_error` |
| A5 | Consent hors `created` / `pending_consent` | **409** `invalid_status` |

### UC-IDV-03 — Capturer la pièce

**Précondition :** consentement `accepted` ; `next` = `capture_document` (statuts `pending_applicant` \| `document` \| `recapture_requested` sans document `accepted`).

**Scénario nominal**

1. Qualité **client** (`assessDocumentFrame`) : flou / lumière / cadrage → recapture **sans** upload.
2. `POST /v1/flow/{token}/document/uploads` → `upload_url`, `object_key`, `expires_at`, `attempt`. Premier upload passe `pending_applicant` → `document`.
3. Navigateur **PUT** `/v1/objects?...` (signature, JPEG/PNG sniffé à l’écriture).
4. `POST .../document/complete` `{ "attempt" }` : objet présent, JPEG/PNG, taille ≤ 10 Mo, côté court ≥ 720 px.
5. OK → statut `selfie`, `next: capture_selfie`.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | Qualité serveur KO | média `rejected_quality`, `recapture_requested`, `quality_rejected: true` |
| A2 | 3e échec pièce | `declined`, `capture_attempts_exceeded`, token révoqué, `next: done` |
| A3 | Sans consentement / statut illégal / document déjà `accepted` | **409** `invalid_status` |
| A4 | Complete sans objet | **400** `validation_error` (`object` missing), statut inchangé |
| A5 | `attempt` inconnu / déjà completed | **400** / **409** |
| A6 | Stockage KO à la signature | **503** `dependency_unavailable` |
| A7 | Signature PUT expirée / invalide | **404** (ne pas révéler la clé) |

### UC-IDV-04 — Capturer le visage

**Précondition :** une pièce `accepted` ; statut `selfie` ou recapture selfie.

1. Qualité visage client (`assessSelfieFrame`).
2. Même schéma `selfie/uploads` + PUT + `selfie/complete`.
3. Qualité serveur = même filet que la pièce (JPEG/PNG, 10 Mo, 720 px). **Pas** de détecteur de visages serveur en M4.
4. 3 échecs selfie → `declined`, `capture_attempts_exceeded`.
5. OK → `processing` puis décision in-process (§ UC-IDV-05). La réponse `complete` porte déjà le statut terminal (`approved` / `declined` / `review`) et `next: done` (ou `wait` si on observait `processing`).

### UC-IDV-05 — Décision automatique

1. Lecture des octets pièce + selfie.
2. `HostedFlowService` charge l’intégration de la session.
3. **Test :** `DocumentAiPort.analyze` + `BiometricAiPort.evaluate` (stub, 0 AWS) puis `IdvDecisionEngine.decide(sandbox_scenario)` → `rules_version=m4-1`, extraits fixture.
4. **Live :** `AwsDocumentAi` (AnalyzeID) + `AwsBiometricAi` (CompareFaces) puis `decideLive` → `rules_version=m5-1`, extraits réels. Liveness = stub qualité (M5). Détail mapping QC : spec M5.
5. Persist `decision`, `decision_reasons`, `rules_version`, `extracted_identity`, lignes `verification_signals`.
6. `status` = la décision (`approved` \| `declined` \| `review`).
7. Audit `verification.completed` `{ "decision" }` sans PII. **Enqueue** `verification.completed` si un webhook est configuré sur l’intégration (M5).
8. Relire : `GET` org / console, **uniquement** le tenant (et, en Bearer, la liste de **cette** intégration).

Pas de juge LLM.

### UC-IDV-06 — Revue

**Acteur :** `VERIFICATION_WRITE` (console) **ou** Bearer.

1. Liste filtrable `status=review`.
2. Fiche : extraits, signaux, raisons machine ; médias via `GET .../media/{kind}` → `{ url, expires_at }`.
3. `POST .../{id}/review` `{ "decision": "approved" | "declined" }` → terminal. Audit `verification.reviewed` `{ "decision" }`.
4. Hors `review` → **409** `invalid_status`. Autre org → **404**. Décision autre → **400**.

La revue **ne réécrit pas** `decision_reasons` machine ; elle pose le statut / `decision` humain. As-built : `enqueueCompleted` aussi après revue (M5).

### UC-IDV-07 — Isolation

Org B : GET / cancel / review / média / token de A → **404**, même corps qu’un UUID aléatoire. Liste A sans B. JSON flow sans PII. Objet stockage A : signature B invalide → **404**.

### UC-IDV-08 — Annuler

Fenêtre §6.6. Lien révoqué. Audit `verification.cancelled`. GET flow ultérieur → **410**.

### UC-IDV-09 — Expiration

TTL lien avant fin du contrat → `expired`, **410** au GET flow, audit `verification.expired`. Nouvelle session obligatoire.

### UC-IDV-10 — Pièce non supportée

`sandbox_scenario=unsupported` → `declined`, raison `unsupported_document`. Pas de parseur inventé. **Stub / vitrine sandbox :** corridor = passeport FR de fixture. **Live / vitrine produit :** corridor = permis QC (M5) ; hors mapping AnalyzeID → même code `unsupported_document`.

---

## 8. Règles de gestion

Les RG-IDV-01 à 12 de la spec fonctionnelle s’appliquent. Précisions M4 :

| ID | Règle |
|---|---|
| **RG-M4-01** | Sandbox / local : **zéro** appel Textract / Rekognition (NF-MVP-05). Test automatisé `SandboxNoAwsTest`. |
| **RG-M4-02** | Pas de média sans consentement `accepted`. |
| **RG-M4-03** | Recapture = même session, compteur d’**uploads** par `kind`, plafond 3. |
| **RG-M4-04** | Qualité KO → pas d’acceptation du média ; pas de décision stub sur image morte. |
| **RG-M4-05** | Hors corridor (scénario `unsupported`) → `unsupported_document`, pas d’OCR fantaisiste. |
| **RG-M4-06** | Toute décision terminale métier a ≥ 1 raison persistée, codes anglais, sans PII. Consentement refusé → `consent_declined`. |
| **RG-M4-07** | Face match stub = selfie ↔ pièce **de cette** session. Jamais 1:N, jamais cross-tenant. |
| **RG-M4-08** | JSON flow : pas d’org, pas d’applicant, pas de token hash, pas d’extraits, pas de raisons. |
| **RG-M4-09** | Journaux / audit : `organization_id`, `verification_id`, codes. Pas de token, e-mail, IP en clair, champs d’identité. |
| **RG-M4-10** | Binaire interdit dans JSON métier (création, complete, fiche, audit). |
| **RG-M4-11** | Médias : disque privé, GET signé court. Signature expirée = **404**. |
| **RG-M4-12** | Identité extraite = schéma normalisé de **fixture** ; la pièce stub prime sur les champs saisis à la création. |
| **RG-M4-13** | `metadata.sandbox_scenario` n’a d’effet qu’en stub. En live, `VerificationService` pose `null` (as-built M5). |
| **RG-M4-14** | Developer sans `VERIFICATION_READ` : pas de liste / fiche. Readonly : pas de création / revue / annulation. |
| **RG-M4-15** | Isolation : hors tenant = **404** `not_found`, jamais 403 « existe ailleurs ». |
| **RG-M4-16** | Cookie ≠ Bearer ≠ token URL. Un plan ne déverrouille pas les deux autres. |
| **RG-M4-17** | `hosted_url` n’est renvoyée que si le token vit encore dans le store. |
| **RG-M4-18** | Idempotence bornée au header `Idempotency-Key` + hash du corps, **par organisation**. |
| **RG-M4-19** | `external_id` unique **par org**, pas globalement. |
| **RG-M4-20** | Annulation hors fenêtre (dont `processing` / `review` / terminal) → **409** `invalid_status`. |
| **RG-M4-21** | GET flow : `Cache-Control: no-store`. |
| **RG-M4-22** | PUT objet : sniff magique JPEG/PNG ; autre type → **400**. |
| **RG-M4-23** | La décision M4 est **déterministe** pour un scénario donné (même entrée → même sortie). |
| **RG-M4-24** | `extracted_identity` uniquement en GET org/console authentifié. |
| **RG-M4-25** | L’applicant **ne voit pas** la décision à l’écran de fin (message générique). L’org la lit en console. |

### 8.1 Filet qualité serveur (pièce et selfie)

| Contrôle | Échec |
|---|---|
| Objet absent après PUT | Complete **400**, pas d’acceptation |
| MIME autre que JPEG/PNG (sniff) | Recapture (`rejected_quality`) |
| Taille > 10 Mo | Recapture |
| Côté court < 720 px | Recapture |
| Image illisible | Recapture |

Pas de Canny / détection de visages vendue comme moteur. Le SDK client filtre en amont ; le serveur est le filet.

### 8.2 Politique de décision (`rules_version` = `m4-1`)

Calibrage **stub**, versionné.

| `sandbox_scenario` | `decision` | `decision_reasons` | Signaux typiques |
|---|---|---|---|
| `approved` (défaut) | `approved` | `liveness_pass`, `face_match_pass` | `authenticity_stub_pass`, match 0,96 |
| `unsupported` | `declined` | `unsupported_document` | fail |
| `expired` | `declined` | `document_expired` | fail |
| `liveness_fail` | `declined` | `liveness_fail` | fail |
| `mismatch` | `declined` | `face_match_fail` | fail |
| `review` | `review` | `mrz_unavailable` | liveness 0,92 ; match 0,82 (zone grise) |
| *(autre / vide)* | comme `approved` | | |

**Recapture** n’est pas une décision terminale.

Consentement refusé et plafond d’essais **court-circuitent** le moteur (`consent_declined` / `capture_attempts_exceeded`).

### 8.3 Codes (V1 M4)

**Document :** `unsupported_document`, `document_expired`, `mrz_unavailable`, `capture_quality_fail` (client), `capture_attempts_exceeded`.  
**Personne :** `liveness_pass`, `liveness_fail`, `face_match_pass`, `face_match_fail`.  
**Consentement :** `consent_declined`.  
**Authenticité stub :** `authenticity_stub_pass`.

Pas de nom, pas de numéro de document **dans le code**. Le numéro fixture `XX0000000` n’apparaît que dans `extracted_identity` côté org.

### 8.4 Extraits fixture (scénario `approved` / `review`)

```
first_name: Marie
last_name: Dupont
birth_date: 1990-04-12
document_type: passport
document_country: FR
document_number: XX0000000
```

Scénarios `declined` du moteur stub : `extracted_identity` vide.

Live (`m5-1`) : extraits AnalyzeID + `issuing_jurisdiction` — schéma spec M5 §9.3. `unsupported_document` live : extraits vides.

---

## 9. Matrice des droits (console)

Inchangée T2 ; M4 **branche** le métier derrière.

| Action | `VERIFICATION_READ` | `VERIFICATION_WRITE` |
|---|---|---|
| Liste / fiche / médias signés | oui | oui |
| Créer, annuler, trancher revue | — | oui |

| Rôle | READ | WRITE |
|---|---|---|
| owner, admin, member | oui | oui |
| readonly | oui | non |
| developer | non | non |

Sans le droit → **403** `forbidden` (avant tout 404 métier). Autre org → **404**.

Bearer : la clé `ky_test_` / `ky_live_` a le métier complet sur `/v1/verifications/**` **de son intégration** (liste filtrée ; create rattache cette intégration). Pas de matrice de rôle machine.

---

## 10. Contrats d’API

Envelope d’erreur inchangée : `{ "error": { "code", "message", "request_id" } }`. Header `X-Request-Id`.

Sémantique **identique** sous `/v1/console/verifications*` (cookie, acteur audit `user`), **sauf** : create console prend `integration_id` (l’opérateur choisit l’intégration) ; liste console accepte `integration_id` en query (filtre optionnel, sinon toute l’org).  
`POST` console **n’est plus** 410 `idv_unavailable`.

### 10.1 Organisation — Bearer `/v1/verifications`

| Méthode | Chemin | Auth | Succès | Corps / notes |
|---|---|---|---|---|
| POST | `/v1/verifications` | Bearer de l’intégration | **201** / **200** replay | `{ external_id?, applicant?, metadata? }` + header `Idempotency-Key?` |
| GET | `/v1/verifications` | Bearer | **200** | Query `status`, `cursor`, `limit` (1–100, défaut 20) → `{ items, next_cursor }` — **items de cette intégration** |
| GET | `/v1/verifications/{id}` | Bearer | **200** | Fiche (404 si autre org ; l’id reste isolé par org) |
| POST | `/v1/verifications/{id}/cancel` | Bearer | **200** | Fenêtre §6.6 |
| POST | `/v1/verifications/{id}/review` | Bearer | **200** | `{ "decision": "approved" \| "declined" }` si `review` |
| GET | `/v1/verifications/{id}/media/{kind}` | Bearer | **200** | `{ url, expires_at }` ; `kind` = `document` \| `selfie` (média `accepted`) |

**Création — requête**

```json
{
  "external_id": "order-42",
  "applicant": { "first_name": "Marie", "last_name": "Dupont", "email": "marie@example.com" },
  "metadata": { "sandbox_scenario": "approved" }
}
```

Tous les champs sont optionnels.

**Création / fiche — réponse** (jamais `hosted_token_hash`, jamais binaire)

```json
{
  "id": "…",
  "status": "created",
  "integration_id": "…",
  "integration_mode": "test",
  "hosted_url": "http://localhost:3001/flow/…",
  "expires_at": "…",
  "applicant": { "first_name": "Marie", "last_name": "Dupont", "email": "marie@example.com" },
  "metadata": { "sandbox_scenario": "approved" },
  "decision": null,
  "decision_reasons": null,
  "rules_version": null,
  "signals": null,
  "extracted_identity": null,
  "created_at": "…",
  "updated_at": "…"
}
```

Après décision, `signals[]` = `{ code, outcome, score }`, `extracted_identity` = schéma §8.4.  
`hosted_url` = `null` si le token n’est plus dans le store.

Liste : mêmes champs, `hosted_url` omise (flag détail). Curseur opaque `{epochMilli}_{uuid}`.

### 10.2 Applicant — `/v1/flow/{token}`

Tous : `Cache-Control: no-store`. Auth = possession du token.

| Méthode | Chemin | Succès | Corps |
|---|---|---|---|
| GET | `/v1/flow/{token}` | **200** | `{ verification_id, status, consent_text_version, expires_at, next }` |
| POST | `/v1/flow/{token}/consent` | **201** | `{ decision }` → `{ status, next, recorded_at }` |
| POST | `/v1/flow/{token}/document/uploads` | **200** | `{ upload_url, object_key, expires_at, attempt }` |
| POST | `/v1/flow/{token}/document/complete` | **200** | `{ attempt, side? }` → `{ status, next, accepted, attempt, quality_rejected? }` |
| POST | `/v1/flow/{token}/selfie/uploads` | **200** | idem uploads |
| POST | `/v1/flow/{token}/selfie/complete` | **200** | `{ attempt }` → complete + éventuellement décision |

`next` : `consent` \| `capture_document` \| `capture_selfie` \| `wait` \| `done`.

`PUT` : URL `upload_url` (`/v1/objects?key&exp&sig&method=PUT`), **pas** l’API métier. **204**.

### 10.3 Objets signés — `/v1/objects`

`permitAll` + signature. OpenAPI hidden.

| Méthode | Effet |
|---|---|
| PUT | Écrit le binaire si `sig` valide et non expirée ; sniff JPEG/PNG |
| GET | Lit le binaire si `sig` valide ; `Cache-Control: private, max-age=60` |

Échec signature / expiré / clé inconnue → **404**.

### 10.4 Erreurs M4

| HTTP | `code` | Cas |
|---|---|---|
| 400 | `validation_error` | Corps / metadata / attempt / Idempotency-Key / decision |
| 401 | `unauthorized` | Clé ou cookie |
| 403 | `forbidden` | Permission console |
| 403 | `insufficient_credit` | Create live sans solde (M3) |
| 404 | `not_found` | Id / token / média autre tenant / signature objet |
| 409 | `external_id_conflict` | Référence déjà prise dans l’org |
| 409 | `idempotency_key_conflict` | Clé réutilisée, autre corps |
| 409 | `consent_already_recorded` | Second consentement |
| 409 | `invalid_status` | Action hors fenêtre |
| 410 | `hosted_link_expired` | Lien mort |
| 410 | ~~`idv_unavailable`~~ | **Supprimé** en M4 |
| 503 | `dependency_unavailable` | Store token ou stockage objet KO à la création / signature |

### 10.5 Audit IDV

Sans e-mail ni nom dans `payload` :

| `action` | Acteur | Quand | Payload |
|---|---|---|---|
| `verification.created` | `api_key` / `user` | POST create | `{}` |
| `hosted_link.issued` | idem | POST create | `{}` |
| `hosted_link.opened` | `applicant` | Premier GET flow | `{}` |
| `consent.accepted` / `consent.declined` | `applicant` | POST consent | `{}` |
| `verification.completed` | `applicant` | Décision stub | `{ "decision" }` |
| `verification.declined` | `applicant` | Plafond d’essais | `{ "reason": "capture_attempts_exceeded" }` |
| `verification.cancelled` | `api_key` / `user` | POST cancel | `{}` |
| `verification.reviewed` | `api_key` / `user` | POST review | `{ "decision" }` |
| `verification.expired` | `applicant` | TTL | `{}` |

---

## 11. Interfaces

### 11.1 Hosted flow (`web/flow`)

Atmosphère `flow.css` : calme, **sans** chrome console. `Wordmark` discret. FR/EN. Proxy same-origin `/v1/*` → API (`API_BASE_URL`).

| Écran (`next` / erreur) | Contenu |
|---|---|
| Lien invalide (404) | Pas de détail technique |
| Lien expiré (410) | « Ce lien a expiré, contactez l’entreprise » |
| `consent` | Texte versionné, accepter / refuser |
| `capture_document` | Caméra (`environment`) ou fichier ; viseur ; consignes ; recapture |
| `capture_selfie` | Caméra (`user`) ; consignes de présence stub |
| `wait` | Traitement (poll GET 1,5 s) — rare (décision in-process) |
| `done` | Message générique de clôture — **pas** la décision ni les extraits |

Interdit à l’écran : nom d’organisation, e-mail, `external_id`, raisons de décision, extraits.

Caméra indisponible → hint + fallback fichier. Qualité client KO → hint, **pas** d’upload.

### 11.2 Console

Le chrome **ne change pas** selon le service (CDC §9.6). IDV vit sous **Identité** (`/identity`).

**As-built :** plus de sélecteur `?env=`. Une org a des **intégrations** (`test` / `live`). Liste `/identity` = sessions org (badge d’intégration) ; `?integration=` filtre. Création = tiroir (`NewVerificationDrawer`) : choix d’intégration, scénario sandbox **si** `test`. `/identity/verifications/new` **redirige** vers `/identity`.

| Surface | Route | Contenu |
|---|---|---|
| Vue d’ensemble | `/identity` | Liste des sessions (badge intégration, clic ligne → fiche) ; CTA nouvelle vérif si `VERIFICATION_WRITE` |
| Intégrations | `/identity/integrations` | Liste `test` / `live` |
| Fiche intégration | `/identity/integrations/{id}` | Install, clés, settings (webhook M5), sessions de **cette** intégration |
| Fiche session | `/identity/verifications/{id}` | Statut (`StatusBadge`), dates, lien ou expiré, extraits, signaux, raisons, médias (URL signée), annuler / trancher |
| Accueil org | `/` | Identité **Disponible** ; usage sandbox / live ; solde |

Developer : pas d’entrée liste (permission). Readonly : pas de bouton créer / revue / annuler.

i18n FR/EN. Libellés de statut via `StatusBadge` (jamais la couleur seule).

### 11.3 Capture SDK

`packages/capture-sdk` : `assessDocumentFrame` / `assessSelfieFrame` **réels** (flou, luminance, taille). Pas de détection de coins vendue comme moteur.

### 11.4 Web flow (redirect + InContext)

As-built : l’applicant ouvre `hosted_url` en **plein écran** (`web/flow` :3001).

Page install console : snippets redirect (`window.location = hosted_url`) et `createFrame({ url: hosted_url })`. Le package SDK InContext **n’est pas** livré (cible CDC §11.7, après M4 / M6).

`onEvent` n’est **pas** la décision. Le backend client crée la session (Bearer de l’intégration) ; le front ne voit que `hosted_url`. Pas de `ky_test_` / `ky_live_` dans le JS.

---

## 12. Stub déterministe

Zéro AWS. Même scénario → même décision (`SandboxNoAwsTest`).

Priorité du scénario : `metadata.sandbox_scenario` à la création, sinon `metadata.sandbox.scenario`, sinon `approved`.

Les adaptateurs stub **classifient** (passeport FR vs unknown, expiration, scores biométriques) ; **le chemin test n’utilise pas ces structures pour trancher** — `IdvDecisionEngine.decide` lit le scénario. Les appels existent pour figer le port.

**As-built :** le choix stub vs AWS se fait au **mode de l’intégration** (`HostedFlowService` + préfixe de clé). `SandboxNoAwsTest` reste obligatoire : intégration `test` = **0** Textract / Rekognition.

---

## 13. Exigences non fonctionnelles

| ID | Applicable M4 |
|---|---|
| **NF-MVP-01** / **NF-IDV-01** | Isolation dossier **et** média ; fuite = arrêt livrable |
| **NF-MVP-03** / **NF-IDV-05** | Logs sans PII, sans token, sans binaire |
| **NF-MVP-04** / **RG-IDV-11** | URL média signée courte |
| **NF-MVP-05** | Sandbox : 0 Textract / Rekognition |
| **NF-MVP-08** | Flow + console : AA, focus clavier |
| **NF-IDV-03** | Happy path en minutes (in-process) |
| **NF-IDV-04** | Lien HTTPS hors local (local = http :3001 OK) |

Rate limit `POST /v1/verifications` : **M6** (NF-MVP-10). M4 ne le bloque pas.

---

## 14. Traçabilité CDC

| Exigence | Couverture |
|---|---|
| O5 sandbox / §17.8 | UC-IDV-03 + 04 + 05, démo §15 |
| §11.1 happy path | §5.6 (webhook HTTP = M5, enqueue as-built) |
| §11.2 analyse stub | §12, RG-M4-01, RG-M4-23 ; live = spec M5 |
| §11.3 corridor | §2.4 stub FR ; live QC = M5 |
| §11.4 décision | §8.2–8.3 (`m4-1`) ; `m5-1` = spec M5 |
| §11.6 API org | §10.1 ; webhooks M5 ; usage M3 |
| §11.7 web flow | §11.4 (redirect as-built ; InContext snippet, package non livré) |
| §9.7 intégration | **Livré (I)** — §4, §6.2, §11.2 |
| UC-IDV-01 à 10 | §7 |
| UC-ISO-01 | UC-IDV-07 |
| §17.10 isolation | Tests isolation |
| §17.12 `unsupported_document` | UC-IDV-10, scénario `unsupported` |
| §17.9 / §17.11 | **M5** (CompareFaces livré ; Face Liveness AWS reporté) |

---

## 15. Acceptation et tests

**Démo M4 :** compte sandbox connecté → nouvelle vérif → copie du lien → consentement → pièce → selfie → statut terminal en console, raisons visibles, médias en URL signée, **0 appel AWS**.

Scénario revue : `sandbox_scenario=review` → file revue → analyste approuve ou refuse.

Scénario isolation : org B 404 sur l’id et le média de A.

**Kill :** binaire dans le JSON métier ; média cross-tenant ; décision sans raisons ; token / PII dans les logs ; 410 `idv_unavailable` encore sur un create autorisé ; SageMaker ou Canny présenté comme moteur ; cookie qui authentifie `/v1/verifications`.

| Test (API) | Couvre |
|---|---|
| `CreateVerificationTest` | 201, idempotence, `external_id`, audit |
| `IsolationTest` | Org B 404 ; liste A sans B ; JSON flow sans PII |
| `HostedFlowTest` | Ouverture, `no-store`, 409 second consentement, 410 périmé |
| `DocumentCaptureTest` | Uploads + complete ; qualité KO → recapture ; 3 échecs → declined |
| `SelfieAndStubDecisionTest` | Selfie OK → décision + raisons |
| `UnsupportedDocumentTest` | Scénario `unsupported` → `unsupported_document` |
| `ReviewTest` | `review` → POST review → approved/declined ; developer 403 revue console |
| `ConsoleIsolationTest` | Cookie A ≠ vérif B |
| `ApiKeyStillBearerTest` | Cookie seul sur `/v1/verifications` → 401 |
| `SandboxNoAwsTest` | 0 client AWS invoqué (intégration test) |
| `LiveDecisionQcTest` | Mapping AnalyzeID QC + `m5-1` (AWS mocké) — M5 |
| `WebhookSignatureTest` / `WebhookSsrfTest` | HMAC + garde SSRF — M5 |

Ne pas casser `SignupAndVerifyTest`, `TeamRolesTest`, `TeamLifecycleTest`, etc.

---

## 16. Suite

| Sujet | État (oct. 2026) |
|---|---|
| Entité `Integration` + retrait chrome `?env=` | **Livré (I)** |
| Checkout carte, crédit, intégration `live` / `ky_live_` | **Livré (M3)** |
| Textract AnalyzeID + Rekognition CompareFaces + webhook par intégration | **Livré (M5)** — [spec](./specification-m5-aws-live-webhook.md) |
| Face Liveness AWS | **Reporté** (liveness live = stub qualité) |
| InContext / JS SDK (`createFrame`) | Snippet install ; **package non livré** |
| Rate limit vérifs, rétention, staging partenaire, Stripe `sk_live_` | **M6** |

Le stub **reste** le chemin des intégrations `test` (`ky_test_`). Remplacer `AwsDocumentAi` par un endpoint SageMaker plus tard **ne change pas** l’API client ni le webhook.
