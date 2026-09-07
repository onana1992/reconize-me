# Spécification d’implémentation — Identity & Document Verification

**Plateforme :** Recogniz-Me  
**Produit :** Identity & Document Verification (IDV)  
**Version :** 1.0  
**Date :** 6 septembre 2026  
**Statut :** à implémenter — le code IDV a été retiré ; ce document est le contrat de reconstruction  
**Public :** ingénierie

**Documents liés**

- [`specification-fonctionnelle-idv.md`](./specification-fonctionnelle-idv.md) — *quoi* métier (cycles, UC, règles). Ce fichier ne le remplace pas.
- [`../MVP/cahier-des-charges-mvp.md`](../MVP/cahier-des-charges-mvp.md) — premier livrable vendable : Textract / Rekognition (ou stubs), pas SageMaker.
- [`../MVP/charte-visuelle.md`](../MVP/charte-visuelle.md) — tokens, console vs flow.
- [`../MVP/specification-m2-compte-client.md`](../MVP/specification-m2-compte-client.md) — comptes, session `rm_session`, clés API. **Déjà livré, à réutiliser.**
- [`roadmap-implementation-idv.md`](./roadmap-implementation-idv.md) — ordre vision (SageMaker). Ne pas démarrer S3–S9 avant M6.
- Anciens guides S1 / S2 : **obsolètes** (as-built d’un code retiré). Les contrats utiles sont repris ici.

Le *quoi* reste dans la spec fonctionnelle. Ici : **où brancher**, **quels contrats HTTP**, **quelles tables**, **quels tests**, **dans quel ordre**, sur la plateforme actuelle.

---

## 1. Situation au 6 septembre 2026

### 1.1 Conservé (ne pas reconstruire)

| Surface | Rôle |
|---|---|
| `api/` Spring Boot unique | Compte, session console, clés API, audit, santé |
| `web/console` | Shell back-office (sidebar rétractable), auth, clés, équipe, compte |
| `web/site` | Vitrine |
| `packages/brand` | Tokens, `console.css`, `flow.css`, logo |
| Redis | Sessions console (`console:session:*`) |
| MySQL + Flyway V1–V6 | Orgs, users, memberships, clés, audit |

Les clés `ky_test_` existent. **Aucune route produit Bearer** n’existe plus. Le filtre API key protège encore tout chemin hors `/v1/health`, `/v1/account/**`, `/v1/console/**` : un `GET /v1/verifications` sans Bearer → **401** ; avec Bearer valide → **404** (pas de contrôleur). C’est voulu : le plan API reste réservé.

### 1.2 Retiré

- Contrôleurs `/v1/verifications/**`, `/v1/flow/**`, `/v1/console/verifications*`
- Entités `Verification`, `Consent`, `IdempotencyKey` et tables (Flyway **V7** : `DROP consents, idempotency_keys, verifications`)
- App `web/flow` (:3001) et `packages/capture-sdk`
- Store Redis `hosted:*`
- Ports S3 / SQS / Textract / Rekognition (stubs)

La console affiche IDV, biométrie et AML en **Bientôt**. Pas de CTA d’activation.

### 1.3 Cible de reconstruction

Reposer l’IDV **sur** le compte et le shell, pas à côté. Premier palier démontrable = session, isolation, lien, consentement. Ensuite capture, puis décision MVP (AWS / stub).

---

## 2. Principes d’implémentation

1. Un seul JAR `api/`. Pas de second service auth ou IDV.
2. Trois plans d’auth, jamais mélangés :
   - **Clé API** `Authorization: Bearer ky_test_|ky_live_` → `/v1/verifications/**`
   - **Cookie** `rm_session` → `/v1/console/**` (y compris `/v1/console/verifications*`)
   - **Possession du token d’URL** → `/v1/flow/**` (pas de clé, pas de cookie)
3. Isolation tenant : org B sur l’id de A → **404**, jamais 403. Identique à un id inconnu.
4. Le JSON flow **ne contient pas** `organization_id`, `external_id`, ni applicant.
5. Journaux : `organization_id`, `verification_id`, codes. Pas de token, e-mail, IP en clair.
6. Couleurs uniquement dans `packages/brand/tokens.css`. Flow = `flow.css` (calme, sans chrome console). Console = `console.css` + AppShell existant.
7. i18n FR + EN sur flow et sur les nouvelles chaînes console.
8. CORS : `http://localhost:3000` **et** `http://localhost:3001` dès que `web/flow` existe.
9. Ne pas inventer les statuts `document`, `selfie`, `processing`, `approved`, `review` avant le sprint qui les utilise.

**Kill :** fuite de dossier ou de média entre organisations ; token ou PII applicant dans les logs / le JSON flow ; lien optionnel à la création ; décision sans raisons (quand la décision arrive).

---

## 3. Ordre de build

| Palier | Livrable démontrable | Ne pas ouvrir ensuite si… |
|---|---|---|
| **P1 — Contrat** | Créer une session (clé **ou** console) → lien → consentir / refuser → relire côté **même** tenant ; l’autre voit 404 | Isolation cassée, pas de lien, PII dans le flow |
| **P2 — Capture** | Après acceptation : photo pièce (qualité client + PUT signé + filet serveur). `next: capture_document` | Binaire dans le JSON métier, média cross-tenant |
| **P3 — Décision MVP** | Selfie + liveness actif + lecture AWS/stub → `approved` / `declined` / `review` relisible | Décision sans raisons, SageMaker « en attendant » |

Le CDC MVP **prime** sur la vision SageMaker pour P3. Les modèles proprio restent la roadmap IDV S3+ après M6.

---

## 4. Palier P1 — Session, lien, consentement

### 4.1 Propriétés (`kyc.*`)

Réintroduire dans `KycProperties` + `application.properties` (tests inclus) :

| Clé | Défaut | Rôle |
|---|---|---|
| `kyc.hosted-url-ttl-seconds` | 3600 | TTL Redis + `hosted_expires_at` |
| `kyc.public-flow-base-url` | `http://localhost:3001` | `{base}/flow/{token}` |
| `kyc.consent-text-version` | `consent-v1` | Version du texte persistée |
| `kyc.idempotency-ttl-hours` | 24 | Replay `Idempotency-Key` |

**Conserver** `kyc.ip-hash-pepper` (déjà utilisé par les comptes) et Redis (sessions console).

Helper : `hostedUrl(token)` → `{publicFlowBaseUrl}/flow/{token}`.

### 4.2 Schéma (nouvelle migration, ne pas réécrire V1–V7)

Ajouter p.ex. `V8__idv_session.sql` :

**`verifications`**

- `id` CHAR(36) PK
- `organization_id` CHAR(36) FK `organizations`
- `external_id` VARCHAR(128) NULL, unique `(organization_id, external_id)`
- `status` VARCHAR(32)
- `applicant_first_name`, `applicant_last_name`, `applicant_email`
- `metadata` TEXT NULL (JSON, profondeur ≤ 2, ≤ 4096 octets)
- `hosted_token_hash` CHAR(64) unique (SHA-256 du token brut)
- `hosted_expires_at`, `created_at`, `updated_at`
- Index `(organization_id, created_at)`, `(organization_id, status)`

**`consents`**

- Une ligne par session (`UNIQUE verification_id`)
- `decision` `accepted` \| `declined`
- `text_version`, `accepted_at`
- `ip_hash` SHA-256(ip + pepper), **pas** d’IP en clair
- `user_agent` VARCHAR(512)

**`idempotency_keys`**

- PK `(organization_id, key)`
- `request_hash` CHAR(64), `verification_id`, `created_at`

`audit_events` **existe déjà**. Y écrire les actions IDV (`organization_id` obligatoire).

### 4.3 Statuts P1 (API en minuscules)

| Valeur | Signification |
|---|---|
| `created` | Lien jamais ouvert |
| `pending_consent` | Lien ouvert, pas de décision |
| `pending_applicant` | Consentement accepté — attente P2 |
| `declined` | Consentement refusé (terminal) |
| `expired` | Lien périmé (terminal) |
| `cancelled` | Annulation org (terminal) |

Annulation **uniquement** en `created` / `pending_consent`. Sinon **409** `invalid_status`. Annuler révoque le token Redis.

Premier `GET /v1/flow/{token}` sur `created` → `pending_consent` (une fois).

### 4.4 Auth et sécurité

**`SecurityConfig`**

- `permitAll` : `/v1/health`, `/v1/flow/**`, swagger, actuator, routes account publiques actuelles
- `authenticated` : `/v1/account/**` (reste), `/v1/console/**`, **`/v1/verifications/**`**
- CORS : origines `3000` et `3001`, credentials, `GET` `POST` `OPTIONS`

**`ApiKeyAuthenticationFilter`**

- Ne **pas** filtrer `/v1/flow/**`, `/v1/account/**`, `/v1/console/**`, health, swagger
- Sur `/v1/verifications/**` : Bearer obligatoire. Cookie session **insuffisant** → 401

**`SessionAuthenticationFilter`** inchangé : `/v1/console/**`.

Le cookie **n’authentifie pas** `/v1/verifications/**`. La clé **n’authentifie pas** `/v1/console/**`.

### 4.5 Store du lien

Port `HostedTokenStore` : `put(token, organizationId, verificationId, ttl)`, `get(token)`, `revokeByVerificationId`.

- Redis si `StringRedisTemplate` présent (`hosted:v1:{token}`, éventuellement index `hosted:vid:{id}`)
- Sinon mémoire (tests)

Token brut : généré via `CryptoTokens` (déjà là). Hash SHA-256 en base. Brut **une fois** dans `hosted_url`.

Indisponibilité du store à la création → **503** `dependency_unavailable`, **pas** de ligne `verifications` orpheline (transaction).

### 4.6 API organisation (clé)

Même sémantique console sous `/v1/console/verifications*` (acteur audit `user` + `userId`).

| Méthode | Chemin | Effet |
|---|---|---|
| `POST` | `/v1/verifications` | Crée, émet le lien. **201** ; **200** si replay idempotent |
| `GET` | `/v1/verifications` | Liste du tenant (`status`, `external_id`, `cursor`, `limit` 1–100, défaut 20) |
| `GET` | `/v1/verifications/{id}` | Fiche. Autre org → 404 |
| `POST` | `/v1/verifications/{id}/cancel` | Si `created` \| `pending_consent` |

Header optionnel `Idempotency-Key` : 8–64 ASCII imprimables. Même clé + même corps → replay. Même clé + autre corps → **409** `idempotency_key_conflict`.

Corps de création (tout optionnel) : `external_id`, `applicant.{first_name,last_name,email}`, `metadata`.

Réponse session : `id`, `status`, `hosted_url` (null si révoqué / expiré côté store), `expires_at`, `applicant`, `metadata`, `created_at`, `updated_at`. **Jamais** `hosted_token_hash`.

`external_id` déjà pris dans l’org → **409** `external_id_conflict`.

### 4.7 API applicant (token)

| Méthode | Chemin | Effet |
|---|---|---|
| `GET` | `/v1/flow/{token}` | Hydrate. `Cache-Control: no-store`. Inconnu → 404 |
| `POST` | `/v1/flow/{token}/consent` | `{ "decision": "accepted" \| "declined" }` → **201** |

JSON GET : `verification_id`, `status`, `consent_text_version`, `expires_at`.

Accepté → `pending_applicant`, `{ "status", "next": "capture_unavailable" }` jusqu’à P2. Refusé → `declined`, zéro média. Second POST → **409** `consent_already_recorded`.

Token évincé Redis mais hash connu et TTL dépassé → **410** `hosted_link_expired`, statut `expired`.

### 4.8 Erreurs

Enveloppe unique :

```json
{ "error": { "code": "not_found", "message": "…", "request_id": "…" } }
```

`details[]` si validation. Header `X-Request-Id` (filtre existant).

| HTTP | `code` | Cas |
|---|---|---|
| 400 | `validation_error` | Corps / query / metadata |
| 401 | `unauthorized` | Clé absente ou invalide |
| 404 | `not_found` | Id inconnu **ou** autre tenant ; token inconnu |
| 409 | `external_id_conflict` | `external_id` déjà pris |
| 409 | `idempotency_key_conflict` | Clé réutilisée, autre corps |
| 409 | `consent_already_recorded` | Second consentement |
| 409 | `invalid_status` | Annulation hors fenêtre |
| 410 | `hosted_link_expired` | Lien mort |
| 503 | `dependency_unavailable` | Store token KO à la création |

Ne pas réutiliser le handler générique `DataIntegrityViolation` pour **tout** conflit SQL (les comptes ont leurs propres codes). Mapper explicitement la contrainte unique IDV.

### 4.9 Audit (sans PII)

| Action | Acteur |
|---|---|
| `verification.created` | `api_key` ou `user` |
| `hosted_link.issued` | idem |
| `hosted_link.opened` | `applicant` |
| `consent.accepted` / `consent.declined` | `applicant` |
| `verification.cancelled` | `api_key` ou `user` |
| `verification.expired` | `applicant` (au GET périmé) |

### 4.10 Classes cibles (P1)

| Zone | Classes |
|---|---|
| API org | `VerificationsController`, `VerificationService` |
| Console | méthodes sur `ConsoleController` (session → `organizationId`) |
| Flow | `FlowController`, `HostedFlowService` |
| Lien | `HostedTokenStore`, `RedisHostedTokenStore`, `InMemoryHostedTokenStore`, `HostedTokenStoreConfig` |
| Données | `Verification`, `VerificationStatus`, `Consent`, `ConsentDecision`, `IdempotencyKey` + repos |
| DTO | `CreateVerificationRequest`, `VerificationResponse`, `VerificationListResponse`, `FlowSessionResponse`, `ConsentRequest` / `ConsentResponse` |

Organisation d’une requête console = **session**, jamais un header client.

### 4.11 Front P1

**`web/flow`** (recréer le workspace, port **3001**)

- Atmosphère `flow.css` : pas de sidebar console. `Wordmark` discret.
- Route `/flow/[token]` : charger GET flow ; FR/EN ; accepter / refuser.
- Après acceptation : message d’attente capture (P2). Pas de nom d’organisation à l’écran.
- Workspace npm `web/flow`, script `dev:flow`.

**`web/console`** (brancher dans AppShell existant)

- Groupe nav **Identity & Document Verification** (plus « Bientôt ») : `/verifications`, `/verifications/new`.
- Pages : liste, création (champs optionnels + copie du lien), fiche (statut, dates, `hosted_url` ou expiré, annuler si autorisé).
- Dashboard : IDV **Disponible**, CTA « Nouvelle vérification » dans la topbar sur `/` et `/verifications`.
- Appels via `/v1/console/verifications*` + cookie. Types dans `lib/api.ts`. Statuts : badge `StatusBadge` (libellé + ton).
- i18n `console.*` FR/EN.

**`packages/brand/flow.css`** existe déjà. Favicon : reprendre `packages/brand/assets/icon.svg`.

### 4.12 Tests P1 (régression obligatoire)

| Classe | Couvre |
|---|---|
| `ApiKeyAuthTest` | 401 sans Bearer / clé invalide ; 200 avec clé sur une fiche à soi |
| `CreateVerificationTest` | 201, idempotence, `external_id`, audit `verification.created` + `hosted_link.issued` |
| `IsolationTest` | Org B → 404 sur l’id de A ; liste A sans B ; JSON flow sans PII |
| `HostedFlowTest` | Ouverture, `no-store`, IP hashée, 409 second consentement, 410 token évincé |
| `VerificationsShouldTest` | Liste, curseur, annulation, révocation du lien |
| `ConsoleIsolationTest` | Cookie A ne lit pas la vérif de B |
| `ApiKeyStillBearerTest` | Cookie **401** sur `/v1/verifications` ; Bearer OK |

Ne pas casser les tests compte existants (`SignupAndVerifyTest`, `ConsoleRequiresSessionTest`, etc.).

---

## 5. Palier P2 — Capture document

Détail métier : spec fonctionnelle §8.4, §9.1.

Après `pending_applicant` seulement :

1. Qualité **client** (`packages/capture-sdk`) — flou / lumière / cadrage → recapture **sans** upload.
2. `POST /v1/flow/{token}/document/uploads` → `upload_url`, `object_key`, `expires_at`, `attempt`.
3. Navigateur **PUT** le fichier (jamais de binaire dans le JSON métier).
4. `POST /v1/flow/{token}/document/complete` `{ "attempt": n }` : objet présent, MIME, taille, dimensions.
5. OK → attente selfie. KO → recapture même session. 3 échecs → `declined`.

Auth = possession du token. Clé d’objet **préfixée par `organization_id`**. Token org A ne signe jamais une clé org B.

Statuts à ajouter **dans ce palier** : `document`, `recapture_requested`. Pas `processing` / `approved` / `review`.

Stockage : port `ObjectStoragePort` + adapter **local réel** (filesystem ou MinIO). TTL upload court (~5 min). CORS bucket pour `:3001` si PUT S3.

`next` après consentement : `capture_document` (plus `capture_unavailable`).

---

## 6. Palier P3 — Personne et décision (MVP)

Selfie + challenge de présence + face match pièce + lecture document.

**MVP (CDC) :** Amazon Textract / Rekognition en production ; **stubs déterministes** en sandbox et en local. Pas de SageMaker, pas de dataset annoté.

Décision = **règles versionnées** + scores persistés. Pas de LLM juge. Raisons sans PII.

Statuts : `selfie`, `processing`, `approved`, `review` (+ `declined` déjà là).

Console : fiche enrichie (extraits, signaux, raisons). Revue manuelle = **analyste du client**, pas Recogniz-Me (MVP).

Webhooks de résultat : hors P1/P2 ; les spécifier avec P3 (au moins une fois ; le client ignore les doublons).

---

## 7. Intégration console (shell actuel)

Fichiers à toucher au P1, sans casser le chrome :

| Fichier | Changement |
|---|---|
| `web/console/components/app-shell.tsx` | Groupe IDV live ; CTA topbar ; titres `/verifications*` |
| `web/console/app/(app)/page.tsx` | Carte IDV disponible + raccourcis |
| `web/console/i18n/fr.json` + `en.json` | Clés nav / home / statuts |
| `web/console/lib/api.ts` | Types `Verification`, helpers console |
| `web/console/lib/status.ts` | `statusLabel` / `statusTone` (réintroduire) |
| `packages/brand/console.css` | Compositions liste / fiche déjà là (`.rm-table`, `.rm-details`) |

Le layout `(app)` reste le shell. Les pages IDV sont des `children` dans `.rm-page`.

Auth `(auth)` : ne pas y mettre de sidebar ni de flow applicant.

---

## 8. Monorepo

```
api/                 # un processus :8080
web/console          # :3000 opérateurs
web/flow             # :3001 applicant (à recréer au P1)
web/site             # :3002 vitrine (inchangée)
packages/brand
packages/capture-sdk # P2
```

`package.json` racine : workspaces `web/flow` + `packages/capture-sdk` et script `dev:flow` au P1/P2.

---

## 9. Décisions figées (implémentation)

| Sujet | Décision |
|---|---|
| Isolation | 404, pas 403 |
| Lien | Obligatoire à la création ; brut une fois dans `hosted_url` |
| Flow | Pas de nom d’organisation à l’écran (V1) |
| Consentement | Une fois ; refuse = terminal, zéro média |
| Capture | Interdite tant que `next` n’est pas `capture_document` |
| Cookie vs clé | Plans disjoints |
| Flyway | Additive (V8+). Ne pas réécrire V1–V7 |
| Premier analyseur | AWS / stub (MVP), pas SageMaker |

---

## 10. Critères d’acceptation P1

- [ ] `POST /v1/verifications` avec Bearer → 201 + `hosted_url` sur `:3001`
- [ ] Même création via console (cookie) → même org que la session
- [ ] Ouvrir le lien → consentement FR/EN ; accepter → `pending_applicant` ; refuser → terminal sans média
- [ ] Org B : 404 sur l’id de A ; JSON 404 identique à un id aléatoire
- [ ] Cookie sur `/v1/verifications` → 401
- [ ] Bearer sur `/v1/console/me` → 401
- [ ] Aucun token / e-mail applicant dans les logs ni le JSON flow
- [ ] Sidebar console : entrées Vérifications actives ; biométrie / AML restent Bientôt
- [ ] Tests listés §4.12 verts ; tests compte toujours verts
