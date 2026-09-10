# Spécification — M4 Capture + IDV stub

**Plateforme :** Recogniz-Me  
**Sprint :** M4 — session IDV, hosted flow, capture pièce + selfie, pipeline stub, décision sandbox  
**Version du document :** 1.0  
**Date :** 10 septembre 2026  
**Statut :** à implémenter (contrat de build)  
**CDC :** §11 (stub), §13 UC-IDV, critère §17.8. **Objectif O5 (sandbox).**  
**Prérequis :** M0, M2 (console authentifiée, clés `ky_test_`, droits `VERIFICATION_*`). M3 n’est **pas** un prérequis.

**Documents liés :**
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — contrat (§11, NF-MVP-01/03/04/05, §17.8 / §17.10 / §17.12)
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — ordre des sprints ; M5 = AWS + webhooks
- [`roadmap-implementation-m4.md`](./roadmap-implementation-m4.md) — ordre de build C1–C4
- [`specification-m2-compte-client.md`](./specification-m2-compte-client.md) — comptes, cookie `rm_session`, clés, matrice T2
- [`../specs/specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md) — métier IDV (cycles, UC-IDV-01 à 10, RG-IDV)
- [`../specs/specification-implementation-idv.md`](../specs/specification-implementation-idv.md) — paliers P1–P3, contrats HTTP, tests
- [`charte-visuelle.md`](./charte-visuelle.md) — `flow.css` vs `console.css`

Ce document décrit **ce que fait** M4 : le parcours sandbox de bout en bout, sans Amazon. Le *quoi* métier reste la [spec IDV](../specs/specification-fonctionnelle-idv.md) ; les **écarts MVP** (stub, corridor court, pas d’enrôlement, pas d’AML, pas de webhook) sont ceux du CDC §11. Le CDC **prime** en cas de conflit. L’ordre de construction est la [roadmap M4](./roadmap-implementation-m4.md). Les détails de câblage (classes, migrations exactes) restent dans la [spec d’implémentation](../specs/specification-implementation-idv.md) — à lire avec la contrainte **Flyway V12+** (V8–V11 sont déjà prises par l’équipe).

---

## 1. Objet

Permettre à une organisation **sandbox** de **prouver une identité à distance** : créer une session, envoyer un lien, laisser l’applicant consentir puis photographier une pièce et son visage, et relire en console une **décision justifiée** — **sans aucun appel AWS IA**.

Avant M4 : les tables IDV ont été retirées (Flyway V7) ; `POST /v1/console/verifications` répond **410** `idv_unavailable` ; la liste console est vide ; `web/flow` et `packages/capture-sdk` n’existent plus.

Après M4 : le happy path CDC §11.1 tourne en `ky_test_` : consentement → pièce → selfie → statut terminal + raisons, 0 Textract / Rekognition.

**Ce que ce livrable n’est pas**

- Une analyse live (Textract, Rekognition, Face Liveness AWS) — **M5**.
- Un webhook `verification.completed` — **M5**.
- Un débit de crédit / clé `ky_live_` — **M3**.
- Un dataset, SageMaker, authenticité ML, enrôlement biométrique, AML attaché.
- Une revue opérée par Recogniz-Me.

Si M4 n’est pas démontrable, **ne pas** ouvrir M5.

---

## 2. Périmètre

### 2.1 Inclus

| Domaine | Contenu |
|---|---|
| Session | Création, lecture, liste, annulation ; `external_id` unique par org ; idempotence |
| Lien hébergé | Token dans l’URL, TTL 1 h, hash en base, store Redis (ou mémoire en test) |
| Consentement | Une fois ; accepté / refusé ; IP hashée ; version de texte persistée |
| Capture pièce | Qualité client (`capture-sdk`) + PUT signé + filet serveur ; recto ; verso si le type l’exige ; recapture, plafond 3 |
| Capture visage | Selfie + challenge liveness **stub** (`liveness_pass` / fail injectable) ; recapture, plafond 3 |
| Stockage | Port objet **réel** (filesystem ou MinIO) ; clé préfixée `org/{organizationId}/…` |
| Pipeline stub | Champs d’identité, MRZ optionnelle, scores, classification corridor |
| Décision | Règles versionnées → `approved` / `declined` / `review` + raisons **sans PII** |
| Console | Liste, création, fiche (extraits, signaux, médias URL signée courte), file revue (analyste **du client**) |
| Flow | App `web/flow` :3001, i18n FR/EN, charte `flow.css` |
| Isolation | Org B sur l’id / le média / le token de A → **404**, identique à un id inconnu |

M4 **réintroduit** le palier session + lien + consentement (P1) : le code a été retiré. Capture et décision stub ne s’ajoutent pas sur un vide.

### 2.2 Hors périmètre (M5+ / autres sprints)

| Domaine | Reporté |
|---|---|
| AWS IA | Textract AnalyzeID, CompareFaces, Face Liveness |
| Webhooks | `POST /v1/webhooks`, `verification.completed` signé |
| Live / crédit | `ky_live_`, débit unitaire, solde insuffisant |
| Vision proprio | SageMaker, dataset, Canny vendu comme moteur |
| Produits | Biométrie auth, AML, enrôlement, KYB |
| Flow | Branding du tenant, SDK natif, liveness passif |
| Opérateur RM | Revue KYC par Recogniz-Me |

### 2.3 Invariants à ne pas casser

Isolation 404, envelope `{ error }`, trois plans d’auth disjoints (clé / cookie / token URL), hash BCrypt des clés, Bearer obligatoire sur `/v1/verifications/**` (cookie insuffisant), pas de PII applicant ni de token brut dans les logs, pas de binaire dans le JSON métier.

Les organisations et tests compte (M2 / T) restent verts.

### 2.4 Décisions figées pour M4

| Sujet | Choix | Source |
|---|---|---|
| Corridor affiché = corridor réel | Passeport ICAO **CA, FR** ; carte d’identité **FR** | Roadmap §6, CDC §11.3 |
| Pièce expirée | **Refus** (`document_expired`) | Roadmap §6 |
| Plafond d’essais | **3** pièce, **3** selfie (même session) | Spec IDV RG-IDV-03 |
| Statut CDC « REJECTED » | API minuscule **`declined`** (consentement refusé **ou** décision négative ; les raisons departagent) | Spec IDV §7, P1 existant |
| Liveness sandbox | Stub `liveness_pass` / `liveness_fail`, injectable | CDC §11.2 |
| Face match sandbox | Stub score ; seuil versionné (défaut **0,90** → pass) | Roadmap §6 |
| Worker M4 | **In-process** après le dernier média OK (pas de SQS / SageMaker) | Démo minutes, NF-IDV-03 |
| Flyway | Additive **V12+**. Ne pas réécrire V1–V11 | V8–V11 = équipe |
| SGBD | **MySQL** déjà en place (`reconizme`) | As-built M2 |

---

## 3. Acteurs

| Acteur | Preuve d’identité | Rôle M4 |
|---|---|---|
| **Backend client** | Bearer `ky_test_` | Crée, liste, lit, annule |
| **Opérateur console** | Cookie `rm_session` + `VERIFICATION_READ` / `_WRITE` | Même métier, UI |
| **Applicant** | Possession du token d’URL | Consent, capture ; jamais de compte Recogniz-Me |
| **Analyste (client)** | Cookie + `VERIFICATION_WRITE` | Tranche les dossiers `review` |
| **Système** | — | Expire le lien, enchaîne stub + règles, journalise |

Recogniz-Me n’est **pas** analyste KYC au MVP (CDC §12).

---

## 4. Concepts

| Concept | Définition M4 |
|---|---|
| **Session / vérification** | Dossier isolé par organisation. Identifiant opaque UUID. |
| **Lien hébergé** | `{public-flow-base}/flow/{token}`. Token brut **une fois** dans `hosted_url`. |
| **next** | Consigne flow : `capture_document`, `capture_document_back`, `capture_selfie`, `wait`, `done`. Plus `capture_unavailable`. |
| **Tentative** | Un essai photo (pièce ou selfie) numéroté. Recapture = tentative suivante, **même** session. |
| **Signal** | Code + issue (`pass` / `fail` / `unavailable`) + score optionnel. Pas une décision. |
| **Raison** | Code anglais persisté sur la décision, **sans PII**. |
| **Stub** | Adaptateur déterministe. Zéro réseau AWS. Scénario injectable (métadonnée sandbox **ou** convention de fichier). |
| **URL média** | GET signé, TTL court (~5 min). Jamais d’URL permanente dans une API ou un log. |

---

## 5. Architecture

### 5.1 Surfaces

| App | Port | Rôle |
|---|---|---|
| `web/console` | 3000 | Opération IDV derrière session |
| `web/flow` | 3001 | Parcours applicant (à recréer) |
| `packages/capture-sdk` | — | Qualité **client** pièce / visage (à recréer) |
| `api/` | 8080 | Un seul JAR : compte + IDV + stub |

`web/site` (3002) : inchangé. Pas d’appel métier depuis la vitrine.

### 5.2 Trois plans d’authentification

```
Opérateur                      Backend                         Applicant
   │                              │                                │
   ▼                              ▼                                ▼
web/console                    scripts / CI                     web/flow
cookie rm_session              Bearer ky_test_                  token URL
   │                              │                                │
   ▼                              ▼                                ▼
/v1/console/verifications*     /v1/verifications/**             /v1/flow/**
```

- Cookie **n’authentifie pas** `/v1/verifications/**` → **401**.
- Clé **n’authentifie pas** `/v1/console/**` ni `/v1/flow/**`.
- Token **n’authentifie pas** les routes org.

CORS : origines `http://localhost:3000` **et** `http://localhost:3001`, credentials, `GET` `POST` `PUT` `OPTIONS`.

### 5.3 Chaîne

```
Création (clé ou console)
        │
        ▼
Lien Redis + ligne verifications (transaction : pas d’orphelin si store KO)
        │
        ▼
Applicant GET flow → consentement
        │
        ▼
Capture pièce : qualité client → POST uploads → PUT stockage → POST complete
        │
        ▼
Capture selfie + liveness stub (même schéma uploads / complete)
        │
        ▼
status = processing → StubDocumentAi + StubBiometric + règles
        │
        ▼
approved | declined | review     (raisons persistées)
        │
        ▼
Console : fiche + médias signés ; revue si review
```

Sandbox / local : les ports `DocumentAiPort` et `BiometricAiPort` n’ont **que** l’adaptateur stub. Brancher `Aws*` = M5.

### 5.4 Stockage des médias

Port `ObjectStoragePort` : `createSignedUploadUrl`, `exists`, `createSignedGetUrl`, jamais de stream dans le JSON métier.

Clé objet **toujours** préfixée par l’organisation :

```
org/{organizationId}/verifications/{verificationId}/document/{attempt}
org/{organizationId}/verifications/{verificationId}/document_back/{attempt}
org/{organizationId}/verifications/{verificationId}/selfie/{attempt}
```

Un token de l’org A ne signe **jamais** une clé de l’org B.

TTL upload ~5 min. TTL lecture console ~5 min. Adapter M4 : filesystem **ou** MinIO/LocalStack, **fonctionnel** (PUT réel).

---

## 6. Modèle de données

Migration additive p.ex. `V12__idv_session.sql` (+ V13 médias / signaux si besoin). Ne pas réécrire V1–V11.

### 6.1 `verifications`

| Colonne | Rôle |
|---|---|
| `id` CHAR(36) PK | Opaque |
| `organization_id` CHAR(36) FK | Isolation |
| `external_id` VARCHAR(128) NULL | Unique `(organization_id, external_id)` |
| `status` VARCHAR(32) | Voir §6.5 |
| `applicant_first_name`, `applicant_last_name`, `applicant_email` | Indices optionnels à la création |
| `metadata` TEXT NULL | JSON profondeur ≤ 2, ≤ 4096 octets |
| `hosted_token_hash` CHAR(64) unique | SHA-256 du token brut |
| `hosted_expires_at`, `created_at`, `updated_at` | |
| `decision` VARCHAR(32) NULL | `approved` \| `declined` \| `review` une fois terminale métier |
| `decision_reasons` TEXT NULL | JSON tableau de codes |
| `rules_version` VARCHAR(32) NULL | Version du moteur |
| `sandbox_scenario` VARCHAR(64) NULL | Scénario stub (ignoré en live / M5) |

Index `(organization_id, created_at)`, `(organization_id, status)`.

### 6.2 `consents`

Une ligne par session (`UNIQUE verification_id`). `decision` `accepted` \| `declined`. `text_version`, `accepted_at`. `ip_hash` = SHA-256(ip + pepper), **pas** d’IP en clair. `user_agent` VARCHAR(512).

### 6.3 `idempotency_keys`

PK `(organization_id, key)`. `request_hash`, `verification_id`, `created_at`. TTL métier 24 h.

### 6.4 `verification_media` / `verification_signals`

**Médias :** `verification_id`, `kind` (`document` \| `document_back` \| `selfie`), `attempt`, `object_key`, `content_type`, `byte_size`, `status` (`pending` \| `accepted` \| `rejected_quality`), `created_at`.

**Signaux :** `verification_id`, `code`, `outcome`, `score` NULL, `created_at`. Aucun champ d’identité en clair.

`audit_events` existe : y écrire les actions IDV (`organization_id` obligatoire, payload sans PII).

### 6.5 Statuts (API minuscules)

| Statut | Signification | Terminal |
|---|---|---|
| `created` | Lien jamais ouvert | non |
| `pending_consent` | Lien ouvert, pas de décision de consentement | non |
| `pending_applicant` | Consentement accepté, capture pas commencée | non |
| `document` | En attente d’une pièce acceptable | non |
| `recapture_requested` | Photo à refaire (qualité, verso, visage) | non |
| `selfie` | Pièce acceptable, attente visage | non |
| `processing` | Médias OK, stub + règles en cours | non |
| `review` | La machine ne tranche pas seule | non |
| `approved` | Identité vérifiée | oui |
| `declined` | Consentement refusé **ou** décision négative | oui |
| `expired` | Lien périmé | oui |
| `cancelled` | Arrêt par l’organisation | oui |

Premier `GET /v1/flow/{token}` sur `created` → `pending_consent` (une fois).

**Annulation :** autorisée en `created`, `pending_consent`, `pending_applicant`, `document`, `recapture_requested`, `selfie`. Hors fenêtre ou déjà terminal / `processing` / `review` → **409** `invalid_status`. Annuler **révoque** le token Redis.

---

## 7. Cas d’utilisation

Les UC-IDV-01 à 10 de la spec fonctionnelle s’appliquent, **bornés** au stub sandbox. Ci-dessous le contrat M4.

### UC-IDV-01 — Créer une vérification

**Acteur :** Bearer **ou** `VERIFICATION_WRITE`.  
**Précondition :** org sandbox, e-mail vérifié (console).

1. `POST` `{ external_id?, applicant?, metadata? }` → **201**, `hosted_url`, `status: created`.
2. Audit `verification.created` + `hosted_link.issued` (acteur `api_key` ou `user`).
3. Header `Idempotency-Key` (8–64 ASCII) : même clé + même corps → **200** replay. Même clé + autre corps → **409** `idempotency_key_conflict`.
4. `external_id` déjà pris dans l’org → **409** `external_id_conflict`.
5. Store token indisponible → **503** `dependency_unavailable`, **aucune** ligne `verifications`.

**Alternatives :** sans auth → **401**. Console sans droit → **403**. Métadonnées trop profondes / trop longues → **400** `validation_error`.

### UC-IDV-02 — Ouvrir le lien et consentir

**Acteur :** applicant (token).

1. `GET /v1/flow/{token}` : `Cache-Control: no-store`. JSON : `verification_id`, `status`, `consent_text_version`, `expires_at`, `next`. **Pas** `organization_id`, `external_id`, nom / e-mail applicant, nom d’org.
2. Inconnu → **404**. Token évincé et TTL dépassé → **410** `hosted_link_expired`, statut `expired`.
3. `POST .../consent` `{ "decision": "accepted" | "declined" }` → **201**.
4. Accepté → `pending_applicant`, `next: capture_document`. Refusé → `declined`, **zéro** média.
5. Second POST → **409** `consent_already_recorded`.
6. Audit `hosted_link.opened`, `consent.accepted` / `consent.declined` (acteur `applicant`). IP hashée.

Écrans flow : lien invalide ; lien expiré (« contactez l’entreprise ») ; consentement ; pas le nom du tenant.

### UC-IDV-03 — Capturer la pièce

**Précondition :** consentement `accepted` ; `next` ∈ `capture_document` \| `capture_document_back` \| recapture pièce.

1. Qualité **client** (`assessDocumentFrame`) : flou / lumière / cadrage / pièce trop petite → recapture **sans** upload.
2. `POST /v1/flow/{token}/document/uploads` → `upload_url`, `object_key`, `expires_at`, `attempt`.
3. Navigateur **PUT** le fichier (`Content-Type` figé à la signature).
4. `POST .../document/complete` `{ "attempt" }` : objet présent, JPEG/PNG, taille ≤ ~10 Mo, côté court ≥ 720 px.
5. OK recto, type qui exige un verso → `next: capture_document_back`. OK dossier pièce complet → `selfie`, `next: capture_selfie`.
6. Qualité serveur KO → `recapture_requested`, même session, `attempt+1`.
7. 3 échecs pièce → `declined`, raison `capture_attempts_exceeded`.
8. Sans consentement / statut illégal → **409** `invalid_status`. Complete sans objet → 4xx, statut inchangé.

Passeport : recto seulement. CNI FR : recto **et** verso.

### UC-IDV-04 — Capturer le visage et la présence

**Précondition :** pièce acceptable.

1. Qualité visage client : un visage, taille, netteté. Plusieurs visages / profil extrême / trop petit → recapture, **pas** de liveness sur image morte.
2. Même schéma `selfie/uploads` + PUT + `selfie/complete`.
3. Challenge liveness **stub** : consigne simple (ex. « tournez la tête ») ; résultat `liveness_pass` ou `liveness_fail` selon scénario §12, pas Rekognition.
4. 3 échecs selfie → `declined`, `capture_attempts_exceeded`.
5. OK → `processing`.

### UC-IDV-05 — Décision automatique (stub)

1. Adaptateurs stub : classification corridor, champs d’identité, MRZ si zone simulée, expiration, liveness, face match.
2. Règles §8.2 → `approved` \| `declined` \| `review`. Raisons persistées. `rules_version` posée.
3. Signaux persistés (codes, pas de PII).
4. Durée happy path : **minutes** (in-process).
5. Relire : `GET` org / console, **uniquement** le tenant.

Pas de webhook en M4. Pas de juge LLM.

### UC-IDV-06 — Revue

**Acteur :** `VERIFICATION_WRITE`.

1. Liste filtrable `status=review`.
2. Fiche : médias (URL signée), extraits, signaux, raisons machine.
3. `POST .../{id}/review` `{ "decision": "approved" | "declined" }` → terminal. Audit `verification.reviewed` `{ "decision" }` sans PII.
4. Hors `review` → **409** `invalid_status`. Autre org → **404**.

### UC-IDV-07 — Isolation

Org B : GET / cancel / média / token de A → **404**, même corps qu’un UUID aléatoire. Liste A sans B. JSON flow sans PII. Objet stockage A illisible avec un token B.

### UC-IDV-08 — Annuler

Voir fenêtre §6.5. Lien révoqué. Audit `verification.cancelled`.

### UC-IDV-09 — Expiration

TTL lien (défaut 3600 s) avant fin du contrat → `expired`, **410** au GET flow. L’org crée une **nouvelle** session. Pas de régénération automatique du token.

### UC-IDV-10 — Pièce non supportée

Classification stub hors corridor → `declined`, raison `unsupported_document`. Pas de parseur inventé. Affichage site / console = cette liste.

---

## 8. Règles de gestion

Les RG-IDV-01 à 12 s’appliquent. Précisions M4 :

| ID | Règle |
|---|---|
| **RG-M4-01** | Sandbox / local : **zéro** appel Textract / Rekognition (NF-MVP-05). Test automatisé. |
| **RG-M4-02** | Pas de média sans consentement `accepted`. |
| **RG-M4-03** | Recapture = même session, compteur, plafond 3 par étape (pièce / selfie). |
| **RG-M4-04** | Qualité KO → pas de stub « lecture » / liveness sur image morte. |
| **RG-M4-05** | Hors corridor → `unsupported_document`, pas d’OCR fantaisiste. |
| **RG-M4-06** | Toute décision (`approved` / `declined` / `review`) a ≥ 1 raison persistée, codes anglais, sans PII. |
| **RG-M4-07** | Face match = selfie ↔ portrait de **cette** pièce. Jamais 1:N, jamais cross-tenant. |
| **RG-M4-08** | JSON flow : pas d’org, pas d’applicant, pas de token hash. |
| **RG-M4-09** | Journaux : `organization_id`, `verification_id`, codes. Pas de token, e-mail, IP en clair, champs d’identité. |
| **RG-M4-10** | Binaire interdit dans JSON métier (création, complete, fiche, audit). |
| **RG-M4-11** | Médias : bucket / disque privé, GET signé court. |
| **RG-M4-12** | Identité extraite = schéma normalisé ; la pièce (stub) prime sur les champs saisis à la création. |
| **RG-M4-13** | `metadata.sandbox_scenario` (ou équivalent) n’a d’effet qu’en stub. M5 l’ignore. |
| **RG-M4-14** | Developer sans `VERIFICATION_READ` : pas de liste / fiche. Readonly : pas de création / revue / annulation. |

### 8.1 Filet qualité serveur (pièce et selfie)

| Contrôle | Échec |
|---|---|
| Objet absent après PUT | Complete 4xx, pas d’acceptation |
| MIME autre que JPEG/PNG | Recapture |
| Taille > ~10 Mo | Recapture |
| Côté court < 720 px (pièce) | Recapture |
| Selfie : 0 ou > 1 visage (stub géométrie / heuristique légère, **pas** Canny vendu comme moteur) | Recapture |

### 8.2 Politique de décision par défaut (stub)

Calibrage **provisoire**, versionné (`rules_version`, ex. `m4-1`).

| Décision | Condition |
|---|---|
| **Approuvée** | Pièce dans le corridor, non expirée, lectures cohérentes (ou MRZ `unavailable` sans contradiction), `liveness_pass`, face match ≥ seuil |
| **Refusée** | Consentement refusé ; `unsupported_document` ; `document_expired` ; plafond d’essais ; `liveness_fail` ; face match franchement sous seuil |
| **Revue** | Zone grise : match limite (seuil − 0,10 jusqu’au seuil), MRZ partielle, liveness limite (si le scénario le demande) |
| **Recapture** | Qualité, verso manquant, visage inutilisable — **pas** une décision terminale |

### 8.3 Codes de raisons / signaux (V1 M4)

**Document :** `unsupported_document`, `document_expired`, `mrz_unavailable`, `mrz_checksum_fail`, `ocr_mrz_mismatch`, `capture_quality_fail`, `capture_attempts_exceeded`.  
**Personne :** `face_not_detected`, `multiple_faces`, `liveness_pass`, `liveness_fail`, `face_match_pass`, `face_match_fail`.  
**Décision :** les codes ci-dessus dans `decision_reasons[]`. Pas de nom, pas de numéro de document dans le code.

Authenticité ML **hors MVP**. Score d’authenticité stub : `PASS` par défaut (`authenticity_stub_pass`).

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

---

## 10. Contrats d’API

Envelope d’erreur inchangée : `{ "error": { "code", "message", "request_id" } }`. Header `X-Request-Id`.

### 10.1 Organisation — Bearer `/v1/verifications`

| Méthode | Chemin | Effet |
|---|---|---|
| `POST` | `/v1/verifications` | Crée + lien. **201** / **200** replay |
| `GET` | `/v1/verifications` | Liste tenant (`status`, `external_id`, `cursor`, `limit` 1–100, défaut 20) |
| `GET` | `/v1/verifications/{id}` | Fiche (extraits, signaux, raisons, `hosted_url` ou null) |
| `POST` | `/v1/verifications/{id}/cancel` | Si fenêtre §6.5 |
| `POST` | `/v1/verifications/{id}/review` | Si `review` ; `{ "decision": "approved" \| "declined" }` |

Sémantique **identique** sous `/v1/console/verifications*` (cookie, acteur audit `user`).  
`POST` console **n’est plus** 410 `idv_unavailable`.

Réponse session (jamais `hosted_token_hash`, jamais binaire) :

`id`, `status`, `hosted_url`, `expires_at`, `applicant`, `metadata`, `decision`, `decision_reasons`, `signals`, `extracted_identity` (null tant que pas traité), `created_at`, `updated_at`.

`extracted_identity` (après stub) : `first_name`, `last_name`, `birth_date`, `document_type`, `document_country`, `document_number` **uniquement** en GET org/console authentifié — **absent** du JSON flow.

Médias : `GET .../{id}/media/{kind}` → **302** ou JSON `{ "url", "expires_at" }` signé court. Autre org → **404**.

### 10.2 Applicant — `/v1/flow/{token}`

| Méthode | Chemin | Effet |
|---|---|---|
| `GET` | `/v1/flow/{token}` | Hydrate |
| `POST` | `/v1/flow/{token}/consent` | Accept / refuse |
| `POST` | `/v1/flow/{token}/document/uploads` | URL signée pièce |
| `POST` | `/v1/flow/{token}/document/complete` | `{ "attempt", "side"?: "front" \| "back" }` |
| `POST` | `/v1/flow/{token}/selfie/uploads` | URL signée selfie |
| `POST` | `/v1/flow/{token}/selfie/complete` | `{ "attempt" }` |

`PUT` : URL de stockage, pas l’API métier.

`next` après consentement : `capture_document` (plus `capture_unavailable`).

### 10.3 Erreurs M4

| HTTP | `code` | Cas |
|---|---|---|
| 400 | `validation_error` | Corps / metadata / attempt |
| 401 | `unauthorized` | Clé ou cookie |
| 403 | `forbidden` | Permission console |
| 404 | `not_found` | Id / token / média autre tenant |
| 409 | `external_id_conflict` | Référence déjà prise |
| 409 | `idempotency_key_conflict` | Clé réutilisée, autre corps |
| 409 | `consent_already_recorded` | Second consentement |
| 409 | `invalid_status` | Action hors fenêtre |
| 410 | `hosted_link_expired` | Lien mort |
| 410 | ~~`idv_unavailable`~~ | **Supprimé** en M4 |
| 503 | `dependency_unavailable` | Store token ou stockage objet KO à la création / signature |

---

## 11. Interfaces

### 11.1 Hosted flow (`web/flow`)

Atmosphère `flow.css` : calme, **sans** chrome console. `Wordmark` discret. FR/EN.

| Écran | Contenu |
|---|---|
| Lien invalide | 404 métier, pas de détail technique |
| Lien expiré | « Ce lien a expiré, contactez l’entreprise » |
| Consentement | Texte versionné, accepter / refuser |
| Capture pièce | Viseur, consignes, recapture, verso si exigé |
| Capture visage | Consignes de présence (challenge stub), recapture |
| Attente | Traitement en cours |
| Fin | Message générique de clôture — **pas** la décision ni les extraits (l’org les lit en console) |

Interdit à l’écran : nom d’organisation, e-mail, `external_id`, raisons de décision.

### 11.2 Console

Le chrome **ne change pas** selon le service (CDC §9.6). IDV vit sous **Identité** (`/identity`), onglets existants + pages dossier.

| Surface | Route | Contenu |
|---|---|---|
| Vue d’ensemble | `/identity` | Liste des sessions (plus empty-state « bientôt ») ; CTA nouvelle vérif si `VERIFICATION_WRITE` |
| Création | `/identity/verifications/new` | Champs optionnels + copie du lien une fois créé |
| Fiche | `/identity/verifications/{id}` | Statut (`StatusBadge`), dates, lien ou « expiré », extraits, signaux, raisons, médias (URL signée), annuler / trancher si autorisé |
| Accueil org | `/` | Identité **Disponible** (déjà le cas si `metered`) ; le CTA ouvre la création |

Developer : pas d’entrée liste (permission). Readonly : pas de bouton créer / revue / annuler.

i18n FR/EN. Libellés de statut via `StatusBadge` (jamais la couleur seule).

### 11.3 Capture SDK

`packages/capture-sdk` : `assessDocumentFrame` / `assessSelfieFrame` **réels** (flou, luminance, taille). Plus le stub `usable: true` de l’archive S2. Pas de détection de coins vendue comme moteur.

---

## 12. Stub déterministe

Zéro AWS. Même entrée → même décision.

### 12.1 Scénario

Priorité : `metadata.sandbox_scenario` à la création (API / console), sinon défaut `approved`.

| `sandbox_scenario` | Décision | Raisons typiques |
|---|---|---|
| `approved` (défaut) | `approved` | `liveness_pass`, `face_match_pass` |
| `unsupported` | `declined` | `unsupported_document` |
| `expired` | `declined` | `document_expired` |
| `liveness_fail` | `declined` | `liveness_fail` |
| `mismatch` | `declined` | `face_match_fail` |
| `review` | `review` | `face_match_pass` (score limite) + `mrz_unavailable` |

Convention fichier (démo caméra) : un nom / EXIF de test peut mapper aux mêmes scénarios si la métadonnée est absente — **documenté** dans le README flow, pas magique.

### 12.2 Champs extraits (scénario `approved`)

Valeurs **fixes** de fixture (ex. passeport FR fictif), jamais une PII réelle d’environnement. Suffisantes pour remplir la fiche console.

### 12.3 Garde M5

Dès qu’un adaptateur AWS existe, le choix stub vs AWS se fait au **préfixe de clé** (`ky_test_` vs `ky_live_`). M4 n’implémente que stub ; le test « sandbox = 0 AWS » reste obligatoire après M5.

---

## 13. Exigences non fonctionnelles

| ID | Applicable M4 |
|---|---|
| **NF-MVP-01** / **NF-IDV-01** | Isolation dossier **et** média ; fuite = arrêt livrable |
| **NF-MVP-03** / **NF-IDV-05** | Logs sans PII, sans token, sans binaire |
| **NF-MVP-04** / **RG-IDV-11** | URL média signée courte |
| **NF-MVP-05** | Sandbox : 0 Textract / Rekognition |
| **NF-MVP-08** | Flow + console : AA, focus clavier |
| **NF-IDV-03** | Happy path en minutes |
| **NF-IDV-04** | Lien HTTPS hors local (local = http :3001 OK) |

Rate limit `POST /v1/verifications` : **M6** (NF-MVP-10). M4 ne le bloque pas.

---

## 14. Traçabilité CDC

| Exigence | Couverture |
|---|---|
| O5 sandbox / §17.8 | UC-IDV-03 + 04 + 05, démo §15 |
| §11.1 happy path | §5.3 (sans webhook) |
| §11.2 analyse stub | §12, RG-M4-01 |
| §11.3 corridor | §2.4, UC-IDV-10 |
| §11.4 décision | §8.2–8.3 |
| §11.6 API org | §10.1 (usage / webhooks = M3 / M5) |
| UC-IDV-01 à 10 | §7 |
| UC-ISO-01 | UC-IDV-07 |
| §17.10 isolation | Tests isolation |
| §17.12 `unsupported_document` | UC-IDV-10, scénario `unsupported` |
| §17.9 / §17.11 | **M5**, pas M4 |

---

## 15. Acceptation et tests

**Démo M4 :** compte sandbox connecté → nouvelle vérif → copie du lien → consentement → pièce → selfie → statut terminal en console, raisons visibles, médias en URL signée, **0 appel AWS**.

Scénario revue : `sandbox_scenario=review` → file revue → analyste approuve ou refuse.

Scénario isolation : org B 404 sur l’id et le média de A.

**Kill :** binaire dans le JSON métier ; média cross-tenant ; décision sans raisons ; token / PII dans les logs ; 410 `idv_unavailable` encore sur un create autorisé ; `capture_unavailable` après consentement ; SageMaker ou Canny présenté comme moteur.

| Test (API) | Couvre |
|---|---|
| `CreateVerificationTest` | 201, idempotence, `external_id`, audit |
| `IsolationTest` | Org B 404 ; liste A sans B ; JSON flow sans PII |
| `HostedFlowTest` | Ouverture, `no-store`, 409 second consentement, 410 périmé |
| `DocumentCaptureTest` | Uploads + complete ; qualité KO → recapture ; 3 échecs → declined |
| `SelfieAndStubDecisionTest` | Selfie OK → processing → décision + raisons |
| `UnsupportedDocumentTest` | Scénario `unsupported` → `unsupported_document` |
| `ReviewTest` | `review` → POST review → approved/declined |
| `ConsoleIsolationTest` | Cookie A ≠ vérif B |
| `ApiKeyStillBearerTest` | Cookie seul sur `/v1/verifications` → 401 |
| `SandboxNoAwsTest` | Compteur / mock : 0 client AWS invoqué |

Ne pas casser `SignupAndVerifyTest`, `TeamRolesTest`, `TeamLifecycleTest`, etc.

---

## 16. Suite

| Après M4 vert | Sprint |
|---|---|
| Checkout carte, crédit, `ky_live_` | **M3** (peut être déjà en cours en parallèle T ; **avant** M5) |
| Textract + Rekognition + webhook signé, même fiche console | **M5** |
| Rate limit vérifs, rétention, staging partenaire | **M6** |

M5 **réutilise** ports, statuts, raisons, flow et fiche. Seuls les adaptateurs IA et la livraison webhook s’ajoutent. Le stub reste le chemin `ky_test_`.
