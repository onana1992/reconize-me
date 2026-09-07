# Guide d’implémentation — Sprint 1

**Plateforme :** Recogniz-Me  
**Produit :** Identity & Document Verification (IDV)  
**Sprint :** S1 — session, isolation, lien, consentement  
**Version :** 1.0  
**Date :** 31 août 2026  
**Statut :** **obsolète** — implémentation retirée le 6 septembre 2026. Reconstruire d’après [`specification-implementation-idv.md`](./specification-implementation-idv.md) (palier P1). Ce fichier reste une archive des contrats S1.  
**Modules :** M0 Plateforme · M1 Session & consentement  

**Documents liés :**
- [`specification-fonctionnelle-idv.md`](./specification-fonctionnelle-idv.md) — spec métier
- [`roadmap-implementation-idv.md`](./roadmap-implementation-idv.md) — ordre de build
- [`guide-implementation-s2.md`](./guide-implementation-s2.md) — sprint suivant

Ce document décrit **ce qui a été construit** en S1. Le *quoi* métier reste dans la spec. Ne pas élargir ce sprint a posteriori (pas de caméra, pas de S3, pas d’OCR).

---

## 1. Livrable

L’organisation crée une session isolée, reçoit un **lien hébergé**, l’applicant **consent ou refuse**. Le parcours s’arrête ensuite (`next: capture_unavailable`).

Démonstration : console ou `POST /v1/verifications` → ouvrir `hosted_url` sur `:3001` → accepter / refuser → statut relisible par l’API du **même** tenant ; l’autre tenant voit **404**.

---

## 2. Périmètre

| In | Out (S2+) |
|---|---|
| Clé API Bearer `ky_test_` / `ky_live_` | Caméra, SDK qualité, S3 |
| CRUD session : créer, lire, lister, annuler | Selfie, liveness, décision |
| Lien hébergé (token Redis, hash en base) | Webhooks |
| Consentement versionné (accepter / refuser) | Revue console |
| Isolation stricte (404 cross-tenant) | Statuts document / traitement / approuvée |
| Idempotence `Idempotency-Key` | Envoi du binaire pièce |
| Audit append-only, erreurs `{ error: { code, message, request_id } }` | |

**Kill :** fuite de dossier entre organisations, token ou PII applicant dans les logs / le JSON flow, lien optionnel.

---

## 3. Auth

| Surface | Auth |
|---|---|
| `/v1/verifications/**` | `Authorization: Bearer <clé>` |
| `/v1/flow/**` | Possession du **token** d’URL (pas de clé) |
| `/v1/health`, OpenAPI, actuator | Public |

Clé : préfixe 12 caractères, hash BCrypt, révoquée = rejet. Lookup `key_prefix` puis `matches`. Principal : `organizationId` + `apiKeyId`.

Filtre : `ApiKeyAuthenticationFilter`. Absence / clé invalide → **401** `unauthorized`.

---

## 4. Routes

### Organisation (clé API)

| Méthode | Chemin | Effet |
|---|---|---|
| `POST` | `/v1/verifications` | Crée la session, émet le lien. **201** ; **200** si replay d’idempotence |
| `GET` | `/v1/verifications` | Liste du tenant (`status`, `external_id`, `cursor`, `limit` 1–100, défaut 20) |
| `GET` | `/v1/verifications/{id}` | Fiche. Autre org → **404** |
| `POST` | `/v1/verifications/{id}/cancel` | Annule si `created` ou `pending_consent`. Révoque le token Redis |

Header optionnel `Idempotency-Key` : 8–64 caractères imprimables ASCII. Même clé + même corps → replay. Même clé + corps différent → **409** `idempotency_key_conflict`. TTL : `kyc.idempotency-ttl-hours` (24 h).

Corps de création (tout optionnel) : `external_id`, `applicant.{first_name,last_name,email}`, `metadata` (objet, profondeur ≤ 2, JSON ≤ 4096 octets).

Réponse session : `id`, `status`, `hosted_url` (null si lien révoqué / expiré côté store), `expires_at`, `applicant`, `metadata`, `created_at`, `updated_at`. Statuts en **minuscules**.

### Applicant (token)

| Méthode | Chemin | Effet |
|---|---|---|
| `GET` | `/v1/flow/{token}` | Hydrate le flow. `Cache-Control: no-store`. Premier GET : `created` → `pending_consent` |
| `POST` | `/v1/flow/{token}/consent` | `{ "decision": "accepted" \| "declined" }` → **201** |

JSON flow : `verification_id`, `status`, `consent_text_version`, `expires_at`. **Pas** de `organization_id`, `external_id`, ni applicant.

Consentement accepté → `pending_applicant`, `{ "status", "next": "capture_unavailable" }`. Refusé → `declined`, aucune capture. Second POST → **409** `consent_already_recorded`.

Token inconnu → **404**. Token évincé du store mais hash connu → **410** `hosted_link_expired`, statut `expired`.

---

## 5. Statuts (S1 seulement)

| Valeur API | Signification |
|---|---|
| `created` | Lien jamais ouvert |
| `pending_consent` | Lien ouvert, pas encore de décision |
| `pending_applicant` | Consentement accepté — **attente S2** |
| `declined` | Consentement refusé (terminal) |
| `expired` | Lien périmé (terminal) |
| `cancelled` | Annulation org (terminal) |

Pas de `document`, `selfie`, `processing`, `approved`, `review` en base.

Annulation autorisée uniquement en `created` / `pending_consent`. Sinon **409** `invalid_status`.

---

## 6. Données

Flyway : `V1__init.sql`, `V2__varchar_hashes.sql`, `V3__verification_metadata.sql`.

| Table | Rôle |
|---|---|
| `organizations`, `api_keys` | Tenant et secrets (hash, jamais la clé brute) |
| `verifications` | Session ; `hosted_token_hash` SHA-256 ; unique `(organization_id, external_id)` |
| `consents` | Une ligne par session ; `ip_hash` SHA-256(ip + pepper), pas d’IP en clair |
| `audit_events` | Append-only, `organization_id` obligatoire |
| `idempotency_keys` | PK `(organization_id, key)` |

Store lien : Redis (`RedisHostedTokenStore`) si `StringRedisTemplate` présent, sinon mémoire (`InMemoryHostedTokenStore`). TTL = `kyc.hosted-url-ttl-seconds` (3600). URL publique : `{kyc.public-flow-base-url}/flow/{token}`.

---

## 7. Erreurs

Enveloppe unique :

```json
{ "error": { "code": "not_found", "message": "…", "request_id": "…" } }
```

`details[]` si validation. Header `X-Request-Id` (filtre `RequestIdFilter`).

| HTTP | `code` | Cas |
|---|---|---|
| 400 | `validation_error` | Corps / query / metadata |
| 401 | `unauthorized` | Clé absente ou invalide |
| 404 | `not_found` | Id inconnu **ou** autre tenant |
| 409 | `external_id_conflict` | `external_id` déjà pris |
| 409 | `idempotency_key_conflict` | Clé réutilisée avec un autre corps |
| 409 | `consent_already_recorded` | Second consentement |
| 409 | `invalid_status` | Annulation hors fenêtre |
| 410 | `hosted_link_expired` | Lien mort |
| 503 | `dependency_unavailable` | Store du token indisponible à la création |

Cross-tenant : toujours **404**, jamais 403 (ne pas confirmer l’existence).

---

## 8. Audit (sans PII)

| Action | Acteur |
|---|---|
| `verification.created` | `api_key` |
| `hosted_link.issued` | `api_key` |
| `hosted_link.opened` | `applicant` |
| `consent.accepted` / `consent.declined` | `applicant` |
| `verification.cancelled` | `api_key` |
| `verification.expired` | `applicant` (marquage à l’ouverture) |

Logs : `organization_id`, `verification_id`, codes. Pas de token, pas d’email, pas d’IP.

---

## 9. Front

| App | Port | S1 |
|---|---|---|
| `web/console` | 3000 | Créer, liste, fiche, copier le lien, annuler |
| `web/flow` | 3001 | Consentement FR/EN ; après acceptation : message « capture sprint 2 » |

CORS API : origines `http://localhost:3000` et `http://localhost:3001`.

---

## 10. Tests (régression S1)

| Classe | Couvre |
|---|---|
| `ApiKeyAuthTest` | 401 sans Bearer / clé invalide ; 200 avec clé |
| `CreateVerificationTest` | 201, idempotence, `external_id` |
| `IsolationTest` | Org B → **404** sur l’id de A ; liste A sans les dossiers B |
| `HostedFlowTest` | Ouverture, no-store, pas de PII, IP hashée, 409 second consentement, 410 token évincé |
| `VerificationsShouldTest` | Liste, curseur, annulation |
| `HealthControllerTest` | Santé publique |

Ne pas casser ces tests en S2.

---

## 11. Code de référence

| Zone | Classes |
|---|---|
| API org | `VerificationsController`, `VerificationService` |
| Flow | `FlowController`, `HostedFlowService` |
| Auth | `ApiKeyAuthenticationFilter`, `ApiKeyAuthenticator`, `SecurityConfig` |
| Erreurs | `ApiException`, `GlobalExceptionHandler`, `ApiErrors` |
| Lien | `HostedTokenStore`, `RedisHostedTokenStore`, `CryptoTokens` |

Un seul processus Spring Boot (`api/`). Pas de second JAR.

---

## 12. Décisions figées S1

| Sujet | Décision |
|---|---|
| Isolation | 404, pas 403 |
| Lien | Obligatoire à la création ; token brut seulement dans `hosted_url` une fois |
| Flow | Pas de nom d’organisation à l’écran |
| Consentement | Une fois ; refuse = terminal, zéro média |
| Capture | Explicitement **indisponible** (`capture_unavailable`) |
| Juge / IA | Hors sprint |

---

## 13. Suite

S2 ouvre la caméra **uniquement** si statut `pending_applicant`. Voir [`guide-implementation-s2.md`](./guide-implementation-s2.md).
