# Spécification — M2 Compte client

**Plateforme :** Recogniz-Me  
**Sprint :** M2 — inscription, e-mail, login, organisation Sandbox, console derrière session  
**Version du document :** 1.0  
**Date :** 4 septembre 2026  
**Statut :** spécification as-built (implémentation livrée)  
**CDC :** §9. **Objectif O3.** Critère d’acceptation §17.4.

**Documents liés :**
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — contrat (§9, §12–15, NF-MVP-01/02/03/10)
- [`guide-implementation-m2.md`](./guide-implementation-m2.md) — *comment* construire (tranches A–F)
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — ordre des sprints
- [`../specs/guide-implementation-s1.md`](../specs/guide-implementation-s1.md) — isolation, Bearer, clés — **inchangé**
- [`charte-visuelle.md`](./charte-visuelle.md) — écrans login / signup

Ce document décrit **ce que fait** le compte client M2 : architecture, modèle de données, cas d’utilisation, règles de gestion, contrats d’API et d’écrans. Les détails de construction restent dans le [guide d’implémentation](./guide-implementation-m2.md). Le CDC **prime** en cas de conflit métier ; ce document reflète l’as-built lorsque le CDC laissait un choix d’implémentation.

---

## 1. Objet

Permettre à une entreprise de **posséder un compte** sur Recogniz-Me : une personne s’inscrit, confirme son e-mail, se connecte à la console, opère une organisation Sandbox, et crée des vérifications S1 **sans clé seedée dans l’environnement**.

Avant M2 : organisations et clés **seedées**, console ouverte anonymement via `KYC_API_KEY`.  
Après M2 : l’identité de l’humain qui opère existe ; la console est **fermée sans session**.

**Ce que M2 n’est pas**

- Une facturation (M3 / Stripe).
- Une authentification de l’API publique par cookie.
- Du SSO, de la 2FA, du multi-org ou du SCIM.

---

## 2. Périmètre

### 2.1 Inclus

| Domaine | Contenu |
|---|---|
| Identité humaine | Utilisateur, e-mail unique, mot de passe BCrypt, vérification d’e-mail |
| Tenant | Organisation créée au signup, slug unique, plan Sandbox (libellé) |
| Appartenance | Un user ∈ **une** org ; rôles `owner` / `member` ; invitation e-mail |
| Session console | Cookie `rm_session` httpOnly, SameSite=Lax, TTL 7 jours |
| Clés machine | Première `ky_test_` à la vérif e-mail ; émission / liste / révocation owner |
| Console | Login, signup, verify, forgot/reset, accueil, vérifs S1, clés, équipe, compte |
| Isolation | Org A ne voit pas org B (404), y compris avec un cookie volé |

### 2.2 Hors périmètre (M3+)

| Domaine | Reporté |
|---|---|
| Facturation | Checkout, portail Stripe, `GET /v1/usage`, quotas chiffrés |
| Clés live | `ky_live_`, `RG-SUB-02` |
| Auth avancée | SSO, 2FA (bandeau d’avertissement seulement), SCIM |
| Multi-org | Un user dans plusieurs organisations |
| Session | Révocation distante d’un autre appareil |
| Messagerie | SMTP / Mailhog câblé (dev : log de l’URL) |

### 2.3 Invariants S1 à ne pas casser

Isolation 404, envelope `{ error }`, hash BCrypt des clés, hosted flow par token d’URL, Bearer obligatoire sur `/v1/verifications/**`. Les organisations seed des tests API (`ky_test_orgAxxxx`, etc.) restent ; la démo M2 n’y passe pas.

---

## 3. Acteurs

| Acteur | Description | Preuve d’identité M2 |
|---|---|---|
| **Visiteur** | Découvre l’offre sur `web/site` | Aucune |
| **Utilisateur console** | Humain qui opère l’org (owner ou member) | Cookie `rm_session` |
| **Backend client** | Intégrateur / scripts / tests S1 | Bearer `ky_test_` / `ky_live_` |
| **Applicant** | Personne vérifiée via le lien hébergé | Token d’URL — **jamais** de compte Recogniz-Me |
| **Système** | Rate limit, expiration des tokens, audit | — |

---

## 4. Concepts

| Concept | Définition |
|---|---|
| **Utilisateur** | Personne identifiée par un e-mail. Mot de passe hashé. Console interdite tant que `email_verified_at` est null. |
| **Organisation** | Tenant isolé, déjà présent en S1. En M2 elle naît au signup (sauf invitation). Facturée plus tard (M3). |
| **Appartenance (membership)** | Lien user ↔ org + rôle. Un user MVP a **au plus une** membership. |
| **Clé API** | Identité **machine**, distincte du login. Préfixe visible ; secret montré **une fois**. |
| **Session console** | Jeton opaque côté serveur, cookie `rm_session`. Porte `userId`, `organizationId`, `role`. |
| **Sandbox** | Plan par défaut de toute org (RG-SUB-01). Pas de colonne SQL en M2 ; le `GET /me` renvoie `plan: "sandbox"`. |

---

## 5. Architecture

### 5.1 Surfaces et ports

| App | Port | Rôle M2 |
|---|---|---|
| `web/site` | 3002 | CTA `SIGNUP_URL` / `LOGIN_URL` → `:3000` (déjà posés en M1) |
| `web/console` | 3000 | Auth + opération ; pas de Bearer dans le navigateur |
| `web/flow` | 3001 | **Inchangé** (applicant) |
| `api/` Spring Boot | 8080 | Account + console + S1. Un seul processus, pas de second JAR auth |

### 5.2 Deux plans d’authentification

```
Visiteur                      Utilisateur console              Backend / tests S1              Applicant
   │                                  │                               │                           │
   ▼                                  ▼                               ▼                           ▼
web/site                        web/console                      scripts / CI                    web/flow
   │                          cookie rm_session                 Bearer ky_test_                 token URL
   │                                  │                               │                           │
   │                                  ▼                               ▼                           ▼
   │                         /v1/account/**                    /v1/verifications/**            /v1/flow/**
   │                         /v1/console/**
   └──────────────────────────────────┴───────────────────────────────┴───────────────────────────┘
                                      ▼
                         Spring Boot (stateless HTTP)
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
                 MySQL              Redis *           (S3 M4+)
              users, orgs,        sessions console,
              keys, vérifs        tokens hosted S1
```

\* Redis si disponible ; sinon store mémoire (même bascule que le hosted token S1). Le CDC §14 mentionnait PostgreSQL ; l’as-built M2 conserve **MySQL** déjà en place (`reconizme`).

**Règle d’architecture :** ne **pas** activer `SessionCreationPolicy.ALWAYS` sur toute l’API. Une session Spring mêlée à un appel Bearer mélange les identités.

### 5.3 Chaîne de filtres (API)

Ordre :

1. `ApiKeyAuthenticationFilter` — s’applique à `/v1/verifications/**` (et chemins non publics hors account/console). Absence de Bearer → **401**, **même avec cookie**.
2. `SessionAuthenticationFilter` — s’applique à `/v1/console/**` et `POST /v1/account/logout`. Cookie manquant ou inconnu → **401** `unauthorized`.
3. Autorisation Spring : routes account publiques en `permitAll` ; le reste de `/v1/account/**` et `/v1/console/**` authentifié.

CORS : origines `http://localhost:3000` et `http://localhost:3001`, `allowCredentials = true`. La vitrine n’appelle pas ces routes.

### 5.4 Console → API

Les **server actions** Next lisent le cookie `rm_session` et le **forwardent** vers l’API (`Cookie: rm_session=…`).  
Le navigateur ne porte **jamais** de clé API. Après login, Next recopie le `Set-Cookie` API dans son propre cookie httpOnly (hôte `:3000`).

### 5.5 Composants applicatifs

| Couche | Responsabilité |
|---|---|
| `AccountController` | Routes publiques compte + logout + accept invite |
| `ConsoleController` | Routes métier sous session |
| `AccountService` | Signup, verify, login, reset, invite, mot de passe |
| `ConsoleService` | Me, clés, équipe, droits owner |
| `SessionService` | Émission / lookup / invalidation du jeton de session |
| `ApiKeyIssuer` | Génération `ky_test_`, BCrypt, audit `api_key.issued` |
| `VerificationService` | Inchangé métier ; surcharge `organizationId` + acteur (`api_key` \| `user`) |
| `MailPort` / `LoggingMailAdapter` | Envoi (dev : log URL). Token brut **uniquement** dans ce canal |
| `AuthRateLimiter` | 5 tentatives / 15 min / IP sur login, resend, forgot |
| `ConsoleSessionStore` | Redis ou mémoire ; clé = hash(pepper + jeton) |

### 5.6 Séquence — inscription jusqu’à la première vérification

```
Visiteur          Console           API              Mail/log         Applicant
   │                 │               │                   │                │
   │  CTA signup     │               │                   │                │
   │────────────────►│ POST /signup  │                   │                │
   │                 │──────────────►│ user+org+owner    │                │
   │                 │               │──verify URL──────►│                │
   │                 │ 201 user_id   │                   │                │
   │                 │ /verify/pending                   │                │
   │  ouvre le lien  │               │                   │                │
   │────────────────►│ GET /verify?token                 │                │
   │                 │──────────────►│ email_verified    │                │
   │                 │               │ + ky_test_ once   │                │
   │                 │ affiche clé   │                   │                │
   │  POST /login    │               │                   │                │
   │────────────────►│──────────────►│ Set-Cookie        │                │
   │                 │ POST /console/verifications       │                │
   │                 │──────────────►│ hosted URL        │                │
   │                 │               │                   │   ouvre lien   │
   │                 │               │◄──────────────────┼────────────────│
   │                 │               │ consentement S1   │                │
```

---

## 6. Modèle de données

Migration Flyway `V4__accounts.sql`. Entités S1 (`organizations`, `api_keys`, `verifications`, `audit_events`, …) inchangées hormis `api_keys.created_by_user_id`.

### 6.1 Schéma relationnel

```
organizations (S1)
       │ 1
       │
       │ *                    1
memberships ────────────── users
(user_id, org_id, role)         │
                                │ 1
            ┌───────────────────┼───────────────────┐
            ▼                   ▼                   ▼
 email_verification_tokens  password_reset_tokens  membership_invites
 (token_hash, expires,        (token_hash, …)       (org_id, email,
  consumed)                                          token_hash, …)

api_keys (S1)
  + created_by_user_id  NULL  ──► users.id
```

### 6.2 Tables M2

#### `users`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK |
| `email` | VARCHAR(255) | UNIQUE, normalisé minuscules |
| `password_hash` | VARCHAR(255) | BCrypt, jamais renvoyé |
| `email_verified_at` | DATETIME(6) | NULL tant que non vérifié |
| `created_at` | DATETIME(6) | NOT NULL |

#### `memberships`

| Colonne | Type | Contraintes |
|---|---|---|
| `user_id` | CHAR(36) | PK composite, FK `users` |
| `organization_id` | CHAR(36) | PK composite, FK `organizations` |
| `role` | VARCHAR(16) | `owner` \| `member` |
| `created_at` | DATETIME(6) | NOT NULL |

#### `email_verification_tokens` / `password_reset_tokens`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK |
| `user_id` | CHAR(36) | FK `users` |
| `token_hash` | CHAR(64) | UNIQUE — SHA-256(pepper + jeton brut) |
| `expires_at` | DATETIME(6) | |
| `consumed_at` | DATETIME(6) | NULL = encore valable |

Le **jeton brut** n’est jamais stocké. Un resend **consomme** les tokens antérieurs non utilisés (rotation).

#### `membership_invites`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK |
| `organization_id` | CHAR(36) | FK `organizations` |
| `email` | VARCHAR(255) | Destinataire, minuscules |
| `role` | VARCHAR(16) | M2 : toujours `member` |
| `token_hash` | CHAR(64) | UNIQUE |
| `expires_at` | DATETIME(6) | |
| `accepted_at` | DATETIME(6) | NULL = en cours |
| `created_at` | DATETIME(6) | |

#### `api_keys` (évolution)

| Colonne | Type | Contraintes |
|---|---|---|
| `created_by_user_id` | CHAR(36) | NULL, FK `users` — null pour les clés seed S1 |

### 6.3 Cycle de vie des jetons

| Jeton | TTL | Usage | Rotation |
|---|---|---|---|
| Vérification e-mail | 24 h | Lien `/verify?token=` | Resend invalide le précédent |
| Reset mot de passe | 1 h | Lien `/reset?token=` | Forgot invalide le précédent |
| Invitation | 7 j | Lien `/signup?invite=` | Une acceptation ; usage unique |
| Session `rm_session` | 7 j | Cookie console | Logout supprime l’entrée store |

### 6.4 Session (hors SQL)

Enregistrement dans Redis (`console:session:{hash}`) ou Map mémoire :

```
{ userId, organizationId, role }
```

L’organisation **ne se lit pas** dans un header client : elle vient de la membership au login, puis du store de session.

### 6.5 Ce qui n’existe pas en M2

Pas de table `Subscription`, `UsagePeriod`, ni colonne `organizations.plan`. Le plan Sandbox est une constante d’API / d’UI jusqu’à M3.

---

## 7. Cas d’utilisation

Les UC CDC §13 (`UC-ACC-01`, `UC-ACC-02`, `UC-ISO-01`) sont détaillés ici. Alternatives et post-conditions sont normatives.

### UC-ACC-01 — Créer un compte (owner)

**Acteur :** visiteur  
**Précondition :** e-mail non encore inscrit.

**Scénario nominal**

1. Le visiteur ouvre `/signup` (vitrine ou URL directe).
2. Il saisit e-mail, mot de passe (≥ 10), nom d’organisation.
3. L’API crée user (non vérifié), organisation, membership `owner`.
4. Un mail de vérification est émis (log URL en dev).
5. La console affiche `/verify/pending`. **Aucune session n’est ouverte.**
6. Le visiteur ouvre le lien → e-mail vérifié → **première** `ky_test_` renvoyée une fois.
7. Il se connecte (`UC-ACC-02`) et crée une vérification S1.

**Post-condition :** org Sandbox ; user vérifié ; une clé test existe (hash en base, secret déjà montré).

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | E-mail déjà pris | **409** `email_taken` |
| A2 | Mot de passe &lt; 10 ou &gt; 128, e-mail invalide, nom org &lt; 2 | **400** `validation_error` |
| A3 | Lien verify déjà consommé / expiré | **400** `invalid_or_expired_token` |
| A4 | Login avant verify | **403** `email_unverified` + page pending |

### UC-ACC-02 — Se connecter et opérer

**Acteur :** utilisateur vérifié  
**Précondition :** `email_verified_at` non null ; membership existante.

**Scénario nominal**

1. POST `/v1/account/login` avec e-mail + mot de passe.
2. **204** + cookie `rm_session`.
3. Accueil console : nom d’org, plan Sandbox, usage **0**, CTA nouvelle vérif.
4. Il liste / crée / lit des vérifications de **son** org via `/v1/console/**`.
5. POST logout : store invalidé, cookie expiré.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | E-mail inconnu **ou** mot de passe faux | **401** `invalid_credentials` (message **identique**) |
| A2 | E-mail non vérifié | **403** `email_unverified` |
| A3 | Cookie absent / inconnu sur `/v1/console/**` | **401** ; UI → `/login?next=` |
| A4 | Session expirée (TTL 7 j) | comme A3 |
| A5 | Rate limit login | **429** `rate_limited` |

### UC-ACC-03 — Mot de passe oublié

**Acteur :** quiconque connaît un e-mail (énumération interdite).

1. POST `/password/forgot` `{ email }` → **204 toujours**.
2. Si le compte existe : mail avec `/reset?token=` (TTL 1 h).
3. POST `/password/reset` `{ token, password }` → **204**.
4. Token rejoué → **400** `invalid_or_expired_token`.

Connecté : `POST /v1/console/account/password` exige le mot de passe **actuel**.

### UC-ACC-04 — Inviter un membre

**Acteur :** owner  
**Précondition :** session owner.

1. POST `/v1/console/team/invites` `{ email }`.
2. Mail `/signup?invite=` (TTL 7 j).
3. L’invité s’inscrit **avec le même e-mail** et `invite_token` : user **sans** nouvelle org.
4. À la vérification d’e-mail : membership `member`, **pas** de nouvelle clé (l’org en a déjà).
5. Variante : user déjà existant sans org → `POST /v1/account/invites/accept`.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | Appelant member | **403** `forbidden` |
| A2 | E-mail déjà membre de cette org | **409** `already_member` |
| A3 | Accept alors que le user a déjà une org | **409** `already_in_organization` |
| A4 | Token invite ≠ e-mail de signup | **400** `invalid_or_expired_token` |
| A5 | User inexistant à l’accept | **400** `invalid_or_expired_token` |

### UC-ACC-05 — Gérer les clés API

**Acteur :** owner (émission / révocation) ; member (lecture des préfixes).

- Émission : plaintext **une fois** (`verify` owner ou `POST /v1/console/api-keys`).
- Liste : `id`, `key_prefix`, `created_at`, `revoked` — jamais `key` ni `key_hash`.
- Révocation : owner ; member → **403** `forbidden`.
- UI : « elle ne sera plus montrée » ; quitter l’écran = secret perdu côté client.

### UC-ACC-06 — Opérer une vérification depuis la console

Délègue au métier S1 avec `organizationId` de session (acteur audit `user`).  
Même isolation : id d’une autre org → **404**.  
Le hosted flow et le consentement applicant sont **inchangés**.

### UC-ISO-01 — Isolation tenant (rejoué)

| Tentative | Résultat |
|---|---|
| Session A, `GET` vérif org B | **404** |
| Cookie A + routes console | Liste / me de **A seulement** (org dans la session, pas un header) |
| Cookie A sur `/v1/verifications` sans Bearer | **401** (plan machine) |
| Bearer org A sur ressource B | **404** (S1) |

---

## 8. Règles de gestion

| ID | Règle |
|---|---|
| **RG-ACC-01** | Un utilisateur MVP appartient à **au plus une** organisation. |
| **RG-ACC-02** | Un signup **sans** invitation crée l’organisation et une membership `owner`. |
| **RG-ACC-03** | Un signup **avec** invitation valide (même e-mail) ne crée **pas** d’org ; l’adhésion `member` est posée à la vérification d’e-mail. |
| **RG-ACC-04** | Toute org naît en Sandbox (**RG-SUB-01**). Pas de `ky_live_` en M2. |
| **RG-ACC-05** | E-mail unique (contrainte SQL + 409 `email_taken` au signup). |
| **RG-ACC-06** | E-mail stocké en minuscules, trim. |
| **RG-ACC-07** | Mot de passe : 10–128 caractères, BCrypt, jamais loggé, jamais renvoyé. |
| **RG-ACC-08** | Slug : dérivé du nom (`[a-z0-9-]`, 3–64). Collision → suffixe `-2`, `-3`, … |
| **RG-ACC-09** | Pas d’accès console tant que `email_verified_at` est null. |
| **RG-ACC-10** | Signup **n’ouvre pas** de session. |
| **RG-ACC-11** | Login e-mail inconnu et mauvais mot de passe : **même** 401 `invalid_credentials`. |
| **RG-ACC-12** | Forgot et resend verify : **204 toujours** (anti-énumération). |
| **RG-ACC-13** | Jeton e-mail / reset / invite : hash SHA-256 + pepper en base ; brut seulement dans l’URL du mail. |
| **RG-ACC-14** | Tokens à usage unique ; resend / forgot invalide le précédent non consommé. |
| **RG-ACC-15** | Première `ky_test_` émise à la **vérification d’e-mail du owner**. Un member ne reçoit pas de clé à ce moment. |
| **RG-ACC-16** | Le secret d’une clé n’est renvoyé qu’à l’émission. Tout GET ultérieur l’omet. |
| **RG-ACC-17** | Seul l’**owner** invite et émet / révoque des clés. Le member crée et lit les vérifications. |
| **RG-ACC-18** | L’organisation d’une requête console vient de la **session** (membership au login), jamais d’un header client. |
| **RG-ACC-19** | Cookie console **n’authentifie pas** `/v1/verifications/**` ni `/v1/flow/**`. |
| **RG-ACC-20** | Login, resend, forgot : au plus **5** tentatives / **15 min** / IP → 429 `rate_limited`. |
| **RG-ACC-21** | Invitation : rôle `member` uniquement ; lien à usage unique. |
| **RG-ACC-22** | Cookie `rm_session` : httpOnly, SameSite=Lax, Secure hors local, path `/`, max-age 7 jours. |
| **RG-ACC-23** | Audit sans PII : `user.registered`, `user.email_verified`, `user.login_failed` (sans e-mail), `api_key.issued`, `membership.invited`. |
| **RG-ACC-24** | Bandeau owner : la 2FA sera exigée plus tard — **pas** de 2FA à construire. |
| **RG-ACC-25** | Usage affiché à **0** jusqu’à M3. |

---

## 9. Matrice des droits

| Action | Public | Member | Owner |
|---|---|---|---|
| Signup, verify, login, forgot, reset, accept invite | oui | — | — |
| `GET /v1/console/me` | | oui | oui |
| Vérifications CRUD-lite (create, list, get, cancel) | | oui | oui |
| `GET /v1/console/api-keys` | | oui (préfixes) | oui |
| `POST /v1/console/api-keys` | | **403** | oui |
| Révoquer une clé | | **403** | oui |
| `GET /v1/console/team` | | oui | oui |
| Inviter | | **403** | oui |
| Changer son mot de passe | | oui | oui |
| `/v1/verifications` (Bearer) | clé de l’org | — | — |

---

## 10. Contrats d’API

Envelope d’erreur S1 : `{ "error": { "code", "message", "request_id" } }`.

### 10.1 Compte — `/v1/account`

| Méthode | Chemin | Auth | Succès | Corps / notes |
|---|---|---|---|---|
| POST | `/signup` | public | **201** `{ user_id }` | `{ email, password, organization_name, invite_token? }` |
| GET | `/verify` | public | **201** `{ id, key, key_prefix }` | Query `token`. Member : `key` null |
| POST | `/verify/resend` | public, RL | **204** | `{ email }` |
| POST | `/login` | public, RL | **204** + Set-Cookie | `{ email, password }` |
| POST | `/logout` | session | **204** | Expire le cookie |
| POST | `/password/forgot` | public, RL | **204** | `{ email }` |
| POST | `/password/reset` | public | **204** | `{ token, password }` |
| POST | `/invites/accept` | public | **204** | `{ token }` |

RL = rate limité (RG-ACC-20).

**Login 204 — cookie**

```
Set-Cookie: rm_session=<opaque>; Path=/; Max-Age=604800; HttpOnly; SameSite=Lax
```

`Secure` si `kyc.session-cookie-secure=true` (hors local).

### 10.2 Console — `/v1/console`

Toutes les routes : cookie session.

| Méthode | Chemin | Rôle min. | Succès |
|---|---|---|---|
| GET | `/me` | member | `{ email, organization: { id, name, slug, plan: "sandbox" }, role }` |
| POST | `/account/password` | member | **204** `{ current_password, new_password }` |
| POST | `/verifications` | member | **201** (ou **200** idempotent) — DTO S1 |
| GET | `/verifications` | member | Liste S1 (`status`, `external_id`, `cursor`, `limit`) |
| GET | `/verifications/{id}` | member | Fiche S1 ; autre org **404** |
| POST | `/verifications/{id}/cancel` | member | DTO S1 |
| GET | `/api-keys` | member | `[{ id, key_prefix, created_at, revoked }]` |
| POST | `/api-keys` | owner | **201** `{ id, key, key_prefix }` |
| POST | `/api-keys/{id}/revoke` | owner | **204** |
| GET | `/team` | member | `{ members: [{ id, email, role, created_at }], invites: [{ id, email, role, expires_at }] }` |
| POST | `/team/invites` | owner | **201** `{ email }` |

### 10.3 Codes d’erreur M2

| HTTP | `code` | Quand |
|---|---|---|
| 409 | `email_taken` | Signup, e-mail déjà en base |
| 403 | `email_unverified` | Login avant verify |
| 401 | `invalid_credentials` | Login / changement de mot de passe |
| 401 | `unauthorized` | Cookie ou Bearer manquant / invalide |
| 400 | `invalid_or_expired_token` | Verify, reset, invite |
| 400 | `validation_error` | Bean validation |
| 429 | `rate_limited` | Login, resend, forgot |
| 403 | `forbidden` | Member sur action owner |
| 409 | `already_member` | Invite d’un membre déjà dans l’org |
| 409 | `already_in_organization` | Accept invite alors qu’une membership existe |
| 404 | `not_found` | Ressource hors tenant (comme S1) |

Messages login : ne pas distinguer « e-mail inconnu » et « mauvais mot de passe ».

### 10.4 Clé affichée une fois

Émission (verify owner ou POST api-keys) :

```json
{ "id": "…", "key": "ky_test_…", "key_prefix": "ky_test_abcd" }
```

`key_prefix` = 12 premiers caractères (constante S1 `PREFIX_LENGTH`).  
Format brut : `ky_test_` + 48 hex.

---

## 11. Interface console

Tokens M0 (`console.css`). Layout public **sans** nav métier ; layout app avec nav + org + déconnexion.

| Route | Accès | Contenu |
|---|---|---|
| `/signup` | public | Formulaire ; `?invite=` masque le nom d’org |
| `/login` | public | `?next=` après middleware |
| `/forgot`, `/reset` | public | Reset |
| `/verify` | public | Clé une fois + copie ; ou « compte activé » (member) |
| `/verify/pending` | public | Consigne + renvoyer l’e-mail |
| `/` | session | Org, Sandbox, usage 0, CTA vérif, bandeau 2FA si owner |
| `/verifications`, `/new`, `/[id]` | session | Liste / création / fiche S1 |
| `/settings/keys` | session | Liste préfixes ; émission owner |
| `/settings/team` | session | Membres, invites, formulaire owner |
| `/settings/account` | session | E-mail, rôle, changement de mot de passe |

**Middleware Next :** pas de cookie → `/login?next=<path>`. Cookie présent sur `/login` ou `/signup` → `/`.  
Routes publiques : `/login`, `/signup`, `/forgot`, `/reset`, `/verify` (et sous-chemins).

---

## 12. E-mail (dev)

Port : `MailPort.send(correlationId, template, url)`.

| Template | URL console | Correlation log |
|---|---|---|
| `email_verify` | `/verify?token=` | `user_id` |
| `password_reset` | `/reset?token=` | `user_id` |
| `team_invite` | `/signup?invite=` | `user_id` ou `invite:{id}` |

Log : `correlation_id`, template, **URL complète** (le token n’existe que là). Pas d’adresse e-mail, pas de mot de passe. Gabarit HTML M0 `packages/brand/email/base.html` : cible d’intégration SMTP ; non obligatoire pour la démo locale.

---

## 13. Exigences non fonctionnelles (applicables)

| ID CDC | Application M2 |
|---|---|
| **NF-MVP-01** | Isolation tenant : 404, org de session — kill si fuite |
| **NF-MVP-02** | Cookie Secure hors local ; secrets hors git |
| **NF-MVP-03** | Logs : ids techniques ; jamais mot de passe, clé brute, e-mail applicant |
| **NF-MVP-08** | Contrastes AA, focus clavier (charte M0) |
| **NF-MVP-10** | Rate limit login (et resend / forgot) |

HTTPS hors local. Pas de secrets dans le dépôt.

---

## 14. Traçabilité CDC

| Exigence CDC | Couverture M2 |
|---|---|
| O3 — compte + org | UC-ACC-01 |
| §9.1 une org / user | RG-ACC-01 |
| §9.2 owner / member + invitation | RG-ACC-17, UC-ACC-04 |
| §9.3 cookie vs Bearer | RG-ACC-19, architecture §5.2 |
| §9.3 2FA hors MVP | RG-ACC-24 |
| §9.4 parcours inscription | UC-ACC-01, séquence §5.6 |
| §9.5 écrans (hors facturation) | §11 — facturation = M3 |
| RG-SUB-01 Sandbox | RG-ACC-04 |
| UC-ACC-01 / 02 / UC-ISO-01 | §7 |
| §14.1 User, Membership, tokens | §6 — `Subscription` reporté M3 |
| §17.4 (acceptation compte) | Tests §15 + démo |

Écran CDC « Facturation » et accueil « quota du mois » : **M3**. M2 affiche usage **0** et le libellé Sandbox.

---

## 15. Acceptation et tests

**Démo :** compte neuf → URL de mail en log → clé une fois → login → vérif S1 → org seed S1 toujours isolée.

**Kill :** console anonyme ; mot de passe / clé brute / e-mail applicant dans les logs ; secret relisible en GET ; user A voit org B.

| Test | Couvre |
|---|---|
| `SignupAndVerifyTest` | 201, mail, clé une fois, second verify 400 |
| `SignupEmailTakenTest` | 409 |
| `LoginUnverifiedTest` | 403 puis 204 + cookie httpOnly |
| `ConsoleRequiresSessionTest` | `/me` sans cookie → 401 |
| `MemberCannotRevokeKeyTest` | 403 |
| `ConsoleIsolationTest` | session A, id B → 404 |
| `ApiKeyStillBearerTest` | cookie seul sur `/v1/verifications` → 401 ; Bearer → 200 |
| `ApiKeySecretNotRelistedTest` | GET keys sans `key` |
| Tests S1 existants | Régression isolation / Bearer / flow |

UI : middleware vérifié (`/` → `/login?next=/`).

---

## 16. Suite

M2 vert → [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) **M3 — Souscription**. Freeze pricing **avant** le premier Checkout. M3 ajoutera `Subscription`, `ky_live_`, usage réel et l’écran facturation du CDC §9.5.
