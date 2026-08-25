# Cahier de spécification — Sprint 1

**Titre :** Vérifications KYC et isolation multi-tenant  
**Produit :** Recogniz-Me  
**Sprint :** 1 (semaines 3–4 de la Phase 1)  
**Durée :** 2 semaines  
**Prérequis :** Sprint 0 terminé (`Organization`, `Membership`, `ApiKey`, Docker Compose, CI, OpenAPI stub)  
**Documents liés :** `docs/cahier-des-charges.md` · `docs/roadmap-implementation.md`  
**Statut :** spécification d’implémentation — à suivre tel quel pendant le sprint

---

## 1. Objet

Permettre à un client B2B (tenant) de **créer une vérification d’identité**, d’**obtenir un lien hosted flow**, et de **relire le dossier via API**, sans aucune fuite de données vers un autre tenant.

À la fin du sprint, le parcours métier s’arrête sur une **page de consentement**. Pas de caméra, pas d’OCR, pas de webhook sortant.

---

## 2. Objectif démontrable (Done)

1. Le tenant A crée une vérification avec sa clé API.  
2. Il copie un lien KYC depuis la console.  
3. L’applicant ouvre le lien sur mobile et voit une page de consentement (texte placeholder).  
4. `GET /v1/verifications/{id}` avec la clé du tenant A renvoie le dossier.  
5. La même requête avec la clé du tenant B renvoie `404` (pas `403`).  
6. Un second `POST` identique avec le même `Idempotency-Key` ne crée pas un second dossier.

---

## 3. Périmètre

### 3.1 Inclus (Must)

| ID | Exigence |
|---|---|
| S1-F01 | `POST /v1/verifications` authentifié par clé API |
| S1-F02 | `GET /v1/verifications/{id}` isolé par tenant |
| S1-F03 | Entité `Verification` persistée, identifiant opaque |
| S1-F04 | Token hosted flow en Redis, TTL configurable |
| S1-F05 | URL publique du hosted flow retournée à la création |
| S1-F06 | Page Next.js consentement (token valide / expiré / inconnu) |
| S1-F07 | Enregistrement du consentement applicant (horodatage + version du texte) |
| S1-F08 | Journal `AuditEvent` append-only pour les actions du sprint |
| S1-F09 | Idempotence des POST de création |
| S1-F10 | Isolation `organization_id` + tests de non-fuite |
| S1-F11 | Console : formulaire « Nouvelle vérification » + copie du lien |
| S1-F12 | Console : fiche lecture d’une vérification (statut, lien, dates) |
| S1-F13 | Erreurs API normalisées (`code`, `message`, `request_id`) |
| S1-F14 | Aucune PII dans les logs applicatifs |

### 3.2 Should (si le Must est vert avant la fin du sprint)

| ID | Exigence |
|---|---|
| S1-S01 | `GET /v1/verifications` paginé (liste du tenant courant) |
| S1-S02 | Annulation `POST /v1/verifications/{id}/cancel` si statut `created` ou `pending_consent` |
| S1-S03 | Métadonnées client `metadata` (objet JSON, max 4 Ko, pas de secrets) |

### 3.3 Hors scope (Sprint 2+)

- Accès caméra, capture document, upload S3, qualité d’image  
- OCR / Textract / MRZ / liveness / face match  
- Webhooks sortants, retry, HMAC  
- Décision `APPROVED` / `REJECTED` / `MANUAL_REVIEW`  
- Sandbox fixtures IA, usage metering, 2FA console  
- Liste des pays / types de documents (saisie libre `country` optionnelle seulement)  
- SDK mobile natif  

---

## 4. Acteurs

| Acteur | Canal | Droits Sprint 1 |
|---|---|---|
| **Backend client** | API Bearer (clé live ou test) | Créer / lire les vérifications de **son** organisation |
| **Utilisateur console** | Next.js, session Sprint 0 | Créer une vérif, copier le lien, ouvrir la fiche |
| **Applicant** | Hosted flow, token URL | Lire le consentement, l’accepter ou le refuser |
| **Système** | Jobs / TTL Redis | Expirer le lien hosted flow |

L’applicant n’a **jamais** de clé API. Le hosted flow n’expose **aucune** donnée d’un autre dossier.

---

## 5. Cas d’usage

### UC-01 — Créer une vérification (API)

1. Le backend client envoie `POST /v1/verifications` avec `Authorization: Bearer ky_…` et optionnellement `Idempotency-Key`.  
2. L’API authentifie la clé, résout `organization_id`.  
3. Une `Verification` est créée au statut `created`.  
4. Un token hosted est écrit dans Redis.  
5. Un `AuditEvent` `verification.created` est inséré.  
6. Réponse `201` : id, statut, `hosted_url`, `expires_at`.

### UC-02 — Créer depuis la console

1. L’utilisateur saisit un `external_id` optionnel + prénom/nom/email applicant optionnels.  
2. La console appelle la même API (clé org ou session utilisateur mappée à l’org).  
3. L’écran affiche le lien + bouton Copier.

### UC-03 — Relire une vérification

- API : `GET /v1/verifications/{id}`  
- Console : page détail  
- 404 si l’id n’existe pas **ou** n’appartient pas au tenant (réponse identique)

### UC-04 — Applicant ouvre le lien

1. `GET /flow/{token}` (Next.js).  
2. Le BFF/API valide le token Redis.  
3. Pages : consentement · token expiré · token invalide.  
4. « J’accepte » → `POST /v1/flow/{token}/consent` → statut `pending_applicant` (en attente de capture au Sprint 2).  
5. « Je refuse » → statut `declined`.

### UC-05 — Isolation

Toute lecture/écriture est scoped `organization_id` de la clé (ou de la session). Tests automatisés obligatoires (voir §16).

---

## 6. Cycle de vie — Sprint 1

```
                    POST /v1/verifications
                              │
                              ▼
                           created
                              │
              applicant ouvre le lien (optionnel)
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
     pending_consent    token expiré      (lien jamais ouvert)
     (page affichée)    → expired              │
              │                                │
    accept / refuse                            │
              │                                ▼
     ┌────────┴────────┐                 expired (job ou accès tardif)
     ▼                 ▼
pending_applicant   declined
(prêt capture S2)
```

| Statut | Signification | Transitions sortantes Sprint 1 |
|---|---|---|
| `created` | Dossier créé, lien valide, applicant pas encore vu | `pending_consent`, `expired`, `declined` |
| `pending_consent` | Page consentement affichée | `pending_applicant`, `declined`, `expired` |
| `pending_applicant` | Consentement accepté ; capture **non** ouverte | `expired` uniquement (S1) |
| `declined` | Applicant a refusé le traitement | terminal S1 |
| `expired` | Token hosted périmé | terminal S1 |
| `cancelled` | Annulé par le client (Should S1-S02) | terminal S1 |

Les statuts `document_pending`, `processing`, `approved`, etc. **n’existent pas encore** en base : ne pas les inventer dans ce sprint.

Expiration : `hosted_url_ttl_seconds` (défaut **3600**). Configurable par variable d’environnement, pas encore par org.

---

## 7. Modèle de données

Schéma PostgreSQL, migrations Flyway. Toutes les tables métier portent `organization_id` sauf `audit_events` qui le porte aussi pour le filtre.

### 7.1 `verifications`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | `UUID` | PK, généré serveur (`uuidv7` ou UUIDv4) |
| `organization_id` | `UUID` | NOT NULL, FK `organizations(id)` |
| `external_id` | `VARCHAR(128)` | NULL, unique **par org** |
| `status` | `VARCHAR(32)` | NOT NULL, check enum S1 |
| `applicant_first_name` | `VARCHAR(100)` | NULL |
| `applicant_last_name` | `VARCHAR(100)` | NULL |
| `applicant_email` | `VARCHAR(255)` | NULL |
| `hosted_expires_at` | `TIMESTAMPTZ` | NOT NULL |
| `metadata` | `JSONB` | NOT NULL DEFAULT `{}` |
| `created_at` | `TIMESTAMPTZ` | NOT NULL |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL |

Index :

- `(organization_id, id)`  
- `(organization_id, external_id)` UNIQUE WHERE `external_id IS NOT NULL`  
- `(organization_id, created_at DESC)`  
- `(organization_id, status)`

**Ne pas stocker le token hosted en clair en PostgreSQL.** Le token vit dans Redis. On peut stocker un **hash** SHA-256 du token (lookup / révocation) :

| Colonne | Type | Notes |
|---|---|---|
| `hosted_token_hash` | `CHAR(64)` | hex SHA-256, UNIQUE |

### 7.2 `consents`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | `UUID` | PK |
| `organization_id` | `UUID` | NOT NULL, FK |
| `verification_id` | `UUID` | NOT NULL, FK, UNIQUE (un consentement décisionnel par dossier S1) |
| `decision` | `VARCHAR(16)` | `accepted` \| `declined` |
| `text_version` | `VARCHAR(32)` | ex. `consent-v1` |
| `accepted_at` | `TIMESTAMPTZ` | NULL si declined |
| `ip_hash` | `CHAR(64)` | SHA-256(IP + pepper), **pas** l’IP en clair |
| `user_agent` | `VARCHAR(512)` | tronqué |
| `created_at` | `TIMESTAMPTZ` | NOT NULL |

### 7.3 `audit_events`

Append-only : **pas d’UPDATE, pas de DELETE** applicatif.

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `organization_id` | `UUID` | NOT NULL |
| `actor_type` | `VARCHAR(32)` | `api_key` \| `user` \| `applicant` \| `system` |
| `actor_id` | `UUID` | NULL |
| `action` | `VARCHAR(64)` | voir §10 |
| `resource_type` | `VARCHAR(32)` | `verification` \| `consent` |
| `resource_id` | `UUID` | NOT NULL |
| `payload` | `JSONB` | **sans** PII (pas de nom, email, token, IP) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL |

Index : `(organization_id, created_at DESC)`, `(resource_type, resource_id)`.

### 7.4 Redis — session hosted flow

Clé : `hosted:v1:{token}`  
TTL : identique à `hosted_expires_at`.  
Valeur JSON :

```json
{
  "verification_id": "018f...",
  "organization_id": "018f...",
  "status_at_issue": "created"
}
```

Le token est un secret cryptographique : **32 bytes** random, encodé `base64url` (sans padding). Il n’apparaît **que** dans l’URL hosted et en mémoire Redis.

---

## 8. Identifiants et conventions API

| Règle | Valeur |
|---|---|
| Base URL locale | `http://localhost:8080` |
| Préfixe | `/v1` |
| Format id | UUID, jamais d’entier auto-incrémenté exposé |
| Auth API | `Authorization: Bearer <api_key>` |
| Content-Type | `application/json` |
| Idempotence | header `Idempotency-Key` (UUID ou string 8–64 chars) sur POST création |
| Corrélation | header réponse `X-Request-Id` |
| Dates | ISO-8601 UTC (`2026-08-23T12:00:00Z`) |

Clés API héritées du Sprint 0 : préfixe `ky_test_` / `ky_live_`, **hash** en base (Argon2id ou BCrypt), comparaison constant-time.

---

## 9. Contrats API

### 9.1 `POST /v1/verifications`

**Auth :** obligatoire.  
**Headers :** `Idempotency-Key` recommandé.

**Body :**

```json
{
  "external_id": "user-42",
  "applicant": {
    "first_name": "Jean",
    "last_name": "Martin",
    "email": "jean.martin@example.com"
  },
  "metadata": {
    "source": "onboarding"
  }
}
```

Tous les champs sont optionnels. Body vide `{}` accepté.

**Validation :**

| Champ | Règle |
|---|---|
| `external_id` | 1–128, `[A-Za-z0-9._:-]` |
| `applicant.first_name` / `last_name` | 1–100, trim |
| `applicant.email` | format email, max 255 |
| `metadata` | objet, profondeur ≤ 2, taille JSON ≤ 4096 octets |
| `Idempotency-Key` | si présent : 8–64 caractères ASCII |

**201 Created :**

```json
{
  "id": "018f3c2a-9c1e-7d44-b8a1-2f0e91c4d001",
  "external_id": "user-42",
  "status": "created",
  "applicant": {
    "first_name": "Jean",
    "last_name": "Martin",
    "email": "jean.martin@example.com"
  },
  "hosted_url": "http://localhost:3000/flow/AbCDefgh...",
  "expires_at": "2026-08-23T13:00:00Z",
  "metadata": {
    "source": "onboarding"
  },
  "created_at": "2026-08-23T12:00:00Z",
  "updated_at": "2026-08-23T12:00:00Z"
}
```

`hosted_url` : `{PUBLIC_FLOW_BASE_URL}/flow/{token}`.

**Idempotence :**

- Même org + même `Idempotency-Key` + **même body hash** → `200` (ou `201` rejoué) avec **la même** ressource.  
- Même clé + body **différent** → `409` `idempotency_key_conflict`.  
- Stockage : table `idempotency_keys` (`organization_id`, `key`, `request_hash`, `verification_id`, `created_at`), TTL 24 h.

**Erreurs :**

| HTTP | `code` | Cas |
|---|---|---|
| 400 | `validation_error` | champ invalide |
| 401 | `unauthorized` | clé absente / invalide / révoquée |
| 409 | `external_id_conflict` | `external_id` déjà utilisé pour l’org |
| 409 | `idempotency_key_conflict` | clé réutilisée avec un autre body |
| 429 | `rate_limited` | Should : 60 créations / min / org |

### 9.2 `GET /v1/verifications/{id}`

**200 :** même schéma que la création, **sans** token brut.  
Si le lien est encore valide, renvoyer `hosted_url` (reconstruction : le token n’est plus en Postgres — **ne pas** renvoyer l’URL après émission si le token n’est plus récupérable).

Règle Sprint 1 :

- `hosted_url` est renvoyé **uniquement** tant que Redis contient le token.  
- Sinon : `"hosted_url": null` et `expires_at` inchangé.  
- **Pas** de régénération automatique du lien (évite les sessions fantômes). Should ultérieur : `POST /v1/verifications/{id}/hosted-link`.

**404** `not_found` : id inconnu **ou** autre tenant. Corps identique :

```json
{
  "error": {
    "code": "not_found",
    "message": "Verification not found",
    "request_id": "req_..."
  }
}
```

### 9.3 `GET /v1/verifications` (Should)

Query : `status`, `external_id`, `cursor`, `limit` (défaut 20, max 100).  
Réponse : `{ "data": [ ... ], "next_cursor": "..." }`.  
Toujours filtré par `organization_id`.

### 9.4 Hosted flow — API interne / publique token

Ces routes **n’utilisent pas** la clé API. Auth = possession du token.

#### `GET /v1/flow/{token}`

Utilisée par Next.js (server) pour hydrater la page.

**200 :**

```json
{
  "verification_id": "018f...",
  "status": "created",
  "consent_text_version": "consent-v1",
  "expires_at": "2026-08-23T13:00:00Z"
}
```

Ne pas renvoyer nom, email, `external_id`, `organization_id`.

**404** token inconnu.  
**410** `hosted_link_expired` token périmé (Redis miss + hash connu **ou** `hosted_expires_at` dépassé).

Au premier `GET` réussi, si statut `created` → passer en `pending_consent` + audit `hosted_link.opened`.

#### `POST /v1/flow/{token}/consent`

```json
{
  "decision": "accepted"
}
```

`decision` : `accepted` | `declined`.

- `accepted` → `pending_applicant`  
- `declined` → `declined`  
- Déjà décidé → `409` `consent_already_recorded`  
- Token expiré → `410`

Réponse `201` :

```json
{
  "status": "pending_applicant",
  "next": "capture_unavailable"
}
```

`next: capture_unavailable` est **volontaire** : le Sprint 2 brancherera `capture_document`. L’UI affiche un écran « Merci, la suite sera bientôt disponible » (FR + EN).

---

## 10. Audit

| `action` | Déclencheur | `payload` autorisé |
|---|---|---|
| `verification.created` | POST API / console | `{ "via": "api" \| "console" }` |
| `hosted_link.issued` | création token Redis | `{ "expires_at": "..." }` |
| `hosted_link.opened` | premier GET flow | `{}` |
| `consent.accepted` | POST consent | `{ "text_version": "consent-v1" }` |
| `consent.declined` | POST consent | `{ "text_version": "consent-v1" }` |
| `verification.expired` | TTL / accès tardif | `{}` |
| `verification.cancelled` | Should cancel | `{}` |
| `verification.read_denied` | GET cross-tenant (optionnel, attention au volume) | ne **pas** logger l’id demandé si brute-force ; préférer métrique |

Interdit dans `payload` et logs : nom, email, token, IP brute, `external_id` si considéré identifiant client sensible — `external_id` **interdit** dans les logs INFO.

---

## 11. Isolation multi-tenant

### 11.1 Règles

1. Chaque requête API authentifiée par clé résout **une** `organization_id`.  
2. Tous les `Repository` Spring Data exposent des méthodes `findByIdAndOrganizationId`.  
3. **Interdiction** de `findById(id)` seul sur `Verification` et `Consent`.  
4. Le hosted flow résout l’org **uniquement** via le document Redis, jamais via un id passé par l’applicant.  
5. Réponse cross-tenant = **404**, jamais 403 (énumération d’ids).

### 11.2 Couche persistance (exemple)

```java
Optional<Verification> findByIdAndOrganizationId(UUID id, UUID organizationId);
```

Filtre Hibernate (optionnel Sprint 1, recommandé) : `@FilterDef` `organizationFilter`. S’il est ajouté, les tests doivent échouer si le filtre est oublié.

### 11.3 Console

La session utilisateur Sprint 0 appartient à une org. Les appels navigateur passent par le BFF / routes Next qui attachent l’org serveur. L’org **n’est pas** un champ du body client.

---

## 12. Frontend Next.js

Deux surfaces déjà prévues au Sprint 0.

### 12.1 Console SaaS

**Route** `/verifications/new`

- Champs : external id, prénom, nom, email (tous optionnels)  
- CTA : « Créer la vérification »  
- Succès : panneau avec `hosted_url`, bouton Copier, `expires_at`, lien « Voir le dossier »  
- Erreurs : 409 external_id, 401 session expirée  

**Route** `/verifications/[id]`

- Statut (badge)  
- Dates  
- Applicant (si saisi)  
- Lien hosted si encore valide, sinon mention « Lien expiré »  
- Pas d’images, pas d’OCR  

**Route** `/verifications` (Should) : tableau id / statut / créé.

Auth console : réutiliser le login Sprint 0. Pas de 2FA (Sprint 8).

### 12.2 Hosted flow (applicant)

**Route** `/flow/[token]` — mobile-first, 360 px min, FR par défaut, EN si `Accept-Language`.

États UI :

| État | UI |
|---|---|
| Loading | skeleton |
| Consent | titre, texte `consent-v1`, boutons Accepter / Refuser |
| Thanks | consentement accepté, pas de caméra |
| Declined | message de refus |
| Expired | « Ce lien a expiré, contactez l’entreprise » |
| Invalid | « Lien invalide » |
| Error | retry générique, **sans** détail technique |

Texte consentement `consent-v1` (placeholder legal, à remplacer avant go-live) :

> En continuant, vous acceptez que vos pièces d’identité et données biométriques soient traitées pour vérifier votre identité pour le compte de l’entreprise qui vous a envoyé ce lien.

Ne **pas** afficher le nom du tenant si non fourni par `GET /v1/flow/{token}` (Sprint 1 : pas de branding).

Accessibilité : boutons focusables, contrastes, labels.

---

## 13. Erreurs — format unique

```json
{
  "error": {
    "code": "validation_error",
    "message": "external_id is invalid",
    "request_id": "req_2b7c",
    "details": [
      { "field": "external_id", "code": "pattern" }
    ]
  }
}
```

`message` : anglais, stable pour les intégrateurs.  
Pas de stacktrace, pas de SQL, pas d’id d’un autre tenant.

---

## 14. Sécurité Sprint 1

| Sujet | Exigence |
|---|---|
| Token hosted | 256 bits entropy, HTTPS en staging (HTTP OK en local) |
| Redis | pas de dump du token dans les logs Redis clients |
| PII | logs : `verification_id`, `organization_id`, `request_id` uniquement |
| Rate limit | Should : 60 POST / min / org ; 120 GET flow / min / IP (hash) |
| CORS | API : origines console + flow strict allow-list |
| Headers flow | `Cache-Control: no-store` sur `/flow/*` |
| SQL | requêtes paramétrées uniquement (JPA) |
| IDOR | tests §16 — bloquant |

Hors Sprint 1 : KMS, chiffrement colonne PII, 2FA, WAF prod.

---

## 15. Configuration

| Variable | Défaut | Rôle |
|---|---|---|
| `HOSTED_URL_TTL_SECONDS` | `3600` | TTL lien |
| `PUBLIC_FLOW_BASE_URL` | `http://localhost:3000` | host de `hosted_url` |
| `CONSENT_TEXT_VERSION` | `consent-v1` | version audit |
| `IDEMPOTENCY_TTL_HOURS` | `24` | rétention clés |

---

## 16. Tests d’acceptation

Automatisés dans `api` (JUnit + Testcontainers Postgres + Redis). Cas front : Playwright sur UC-02 et UC-04 (au moins 1 happy path).

| ID | Scénario | Résultat attendu |
|---|---|---|
| T01 | POST sans auth | 401 |
| T02 | POST `{}` avec clé org A | 201, `status=created`, `hosted_url` non vide |
| T03 | GET id avec clé A | 200, mêmes champs |
| T04 | GET id A avec clé org B | 404, body `not_found` |
| T05 | GET UUID aléatoire clé A | 404 identique à T04 |
| T06 | POST `external_id` dupliqué même org | 409 `external_id_conflict` |
| T07 | POST même `external_id` org B | 201 (pas de collision globale) |
| T08 | POST ×2 même Idempotency-Key + même body | une seule ligne `verifications` |
| T09 | POST ×2 même Idempotency-Key, body différent | 409 `idempotency_key_conflict` |
| T10 | GET flow token valide | 200, pas de PII applicant |
| T11 | Premier GET flow | statut `pending_consent` |
| T12 | POST consent `accepted` | `pending_applicant`, ligne `consents` |
| T13 | POST consent `declined` | `declined` |
| T14 | POST consent après T12 | 409 |
| T15 | Token inexistant | 404 |
| T16 | Après TTL Redis | 410, statut `expired` |
| T17 | Audit : `verification.created` existe, payload sans email | assert JSON |
| T18 | Log capture : créer avec email, grep logs ≠ email | pas de match |
| T19 | Console : créer + copier lien (Playwright) | clipboard / champ visible |
| T20 | Isolation Hibernate : `findById` nu interdit ou test fail si filtre off | policy repo |

**Go / no-go sprint :** T02, T03, T04, T08, T10, T12, T18 **obligatoires**.

---

## 17. Observabilité

- Métriques : `verifications_created_total{org_id_hash}`, `hosted_link_opened_total`, `consent_accepted_total`, `http_4xx` (pas d’email en label).  
- Trace : `X-Request-Id` = `verification_id` **jamais** comme id de trace unique si ça fuit dans des outils tiers ; garder `request_id` distinct.  
- Health : déjà Sprint 0. Redis down → POST vérification **échoue** `503` `dependency_unavailable` (le lien ne peut pas être émis).

---

## 18. Architecture logicielle (backend)

Packages (`api/`, un seul projet) :

```
com.kyc
  ├── controllers   VerificationsController, HealthController
  ├── services      VerificationService
  ├── entities      Verification, Consent, Organization, ApiKey, AuditEvent
  ├── repositories  JPA
  ├── config        Security, OpenAPI
  ├── ports         S3, SQS, Textract, Rekognition
  ├── adapters      no-op AWS
  └── workers       consommateurs SQS (sprint 2+)
```

Règles :

- Le controller ne parle pas à Redis ni à JPA.  
- Le token est généré dans `CreateVerificationService`.  
- MapStruct : entity → `VerificationResponse` (jamais le token hash).  
- OpenAPI Springdoc à jour pour POST/GET (contrat publié dans `/v3/api-docs`).

Transaction : création vérif + hash token + audit **dans la même** TX Postgres ; écriture Redis **après** commit (sinon token orphelin). Si Redis échoue après commit : marquer vérif ou compenser (retry Redis) — documenter le choix dans le PR (recommandé : retry 3× puis `503` + compensation delete vérif si Redis down avant d’avoir exposé l’URL ; si 201 déjà parti, job de repair).

Recommandation simple Sprint 1 : **écrire Redis, puis commit Postgres n’est pas idéal**. Ordre :

1. TX : insert verification (`hosted_token_hash`) + audit + idempotency  
2. SET Redis  
3. Si Redis fail : `503` et la ligne reste sans session — `GET` renverra `hosted_url: null`. Acceptable S1 si testé.

---

## 19. Critères de fin de sprint

Le Sprint 1 est **accepté** si :

1. T02–T04, T08, T10, T12, T18 verts en CI.  
2. Démo : org A crée, copie le lien, Chrome mobile (ou 360 px) montre le consentement ; org B tape l’UUID → 404.  
3. OpenAPI décrit POST et GET.  
4. Aucun média S3, aucun appel AWS AI.  
5. README dev : comment obtenir une clé, créer une vérif, ouvrir le flow.

**Kill (ne pas démarrer le Sprint 2) :** fuite cross-tenant, token dans les logs, Redis optionnel « on verra plus tard » (le lien est le cœur du produit).

---

## 20. Enchaînement Sprint 2

Le Sprint 2 part de `status = pending_applicant` et ajoute :

- UI capture après l’écran « Merci »  
- `Document` + URL signée S3  
- transition `document_pending` / `document.uploaded`

Ne pas pré-créer les tables `documents` pendant le Sprint 1 sauf si ça débloque un conflit d’équipe (évité par défaut).

---

## 21. Estimations internes

| Lot | Owner | Charge indicative |
|---|---|---|
| Schéma Flyway + entités + repos scoped org | Backend | 1,5 j |
| POST/GET + idempotence + erreurs | Backend | 2 j |
| Redis hosted token + GET/POST flow | Backend | 1,5 j |
| Audit + tests isolation Testcontainers | Backend | 1,5 j |
| Console new + détail + copie lien | Frontend | 2 j |
| Flow consentement + i18n FR/EN + états token | Frontend | 2 j |
| Playwright T19 + polish | Fullstack | 0,5 j |

Buffer 1 j pour les T18 logs et les 404 homogènes.

---

## 22. Décisions figées pour ce sprint

| Sujet | Décision |
|---|---|
| Cross-tenant | HTTP 404 |
| Token | Redis + hash SHA-256 en Postgres |
| Régénération de lien | non |
| Consentement | persisté, versionné `consent-v1` |
| Langue API | anglais (`code` / `message`) |
| Langue flow | FR défaut, EN selon navigateur |
| Webhooks `verification.created` | **non** (Sprint 7) |

Toute demande hors §3.1 pendant les 2 semaines = report Sprint 2 ou avenant de scope.
