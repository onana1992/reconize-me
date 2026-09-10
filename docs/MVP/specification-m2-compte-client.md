# Spécification — M2 Compte client (et cycle équipe T)

**Plateforme :** Recogniz-Me  
**Sprint :** M2 — inscription, e-mail, login, organisation Sandbox, console derrière session  
**Complément :** cycle T — gestion d’équipe type Onfido / Veriff Station (T0–T4)  
**Version du document :** 1.2  
**Date :** 9 septembre 2026  
**Statut :** spécification as-built (M2 + T0–T4 livrés)  
**CDC :** §9. **Objectif O3.** Critère d’acceptation §17.4. Cycle T = exception volontaire au CDC §4.2 (rôles avancés).

**Documents liés :**
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — contrat (§9, §12–15, NF-MVP-01/02/03/10)
- [`guide-implementation-m2.md`](./guide-implementation-m2.md) — *comment* M2 a été construit
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — ordre des sprints
- [`roadmap-implementation-team.md`](./roadmap-implementation-team.md) — ordre de build T0–T4
- [`../specs/guide-implementation-s1.md`](../specs/guide-implementation-s1.md) — isolation, Bearer, clés — **inchangé**
- [`charte-visuelle.md`](./charte-visuelle.md) — écrans login / signup

Ce document décrit **ce que fait** le compte client : architecture, modèle de données, cas d’utilisation, règles de gestion, contrats d’API et d’écrans. M2 pose l’identité humaine et la console fermée. Le **cycle T** (T0 livré avec M2, puis T1–T4) étend l’équipe : cycle de vie, cinq rôles, statut actif/inactif, journal d’activité. Les détails de construction M2 restent dans le [guide](./guide-implementation-m2.md) ; l’ordre T dans la [roadmap équipe](./roadmap-implementation-team.md). Le CDC **prime** en cas de conflit métier ; ce document reflète l’as-built lorsque le CDC laissait un choix — y compris l’ouverture volontaire des rôles avancés (T2+).

---

## 1. Objet

Permettre à une entreprise de **posséder un compte** sur Recogniz-Me : une personne s’inscrit, confirme son e-mail, se connecte à la console, opère une organisation Sandbox, invite des collègues avec un rôle, et relit **qui a fait quoi** — sans clé seedée dans l’environnement.

Avant M2 : organisations et clés **seedées**, console ouverte anonymement via `KYC_API_KEY`.  
Après M2 : l’identité de l’humain qui opère existe ; la console est **fermée sans session**.  
Après T : un owner invite un developer ou un readonly, peut renvoyer / annuler / retirer / désactiver, et un admin lit le journal — sans fuite de tenant ni session zombie.

**Ce que ce livrable n’est pas**

- Une facturation (M3 / Stripe) — les permissions `BILLING_*` existent déjà pour s’y brancher.
- Une authentification de l’API publique par cookie.
- Du SSO, de la 2FA, du multi-org, du SCIM, des groupes custom, un export CSV d’audit.

---

## 2. Périmètre

### 2.1 Inclus

| Domaine | Contenu | Sprint |
|---|---|---|
| Identité humaine | Utilisateur, e-mail unique, mot de passe BCrypt, prénom / nom, vérification d’e-mail | M2 |
| Tenant | Organisation créée au signup, slug unique ; sandbox gratuit (pas un plan) | M2 |
| Appartenance | Un user ∈ **une** org ; cinq rôles ; invitation e-mail avec rôle | M2 / T2 |
| Cycle de vie équipe | Resend / cancel invite, retirer un membre, dernier owner, unicité pending | T1 |
| Statut | `active` \| `disabled` ; logout immédiat ; login inactif refusé | T3 |
| Droits | Helper `require(Permission)` ; plus de `if owner` dispersés | T2 |
| Session console | Cookie `rm_session` httpOnly, SameSite=Lax, TTL 7 jours ; rôle **relu** sur `memberships` | M2 / T1 |
| Clés machine | Première `ky_test_` à la vérif e-mail du owner ; émission / liste / révocation si `API_KEY_*` | M2 / T2 |
| Journal | `GET /v1/console/audit` paginé ; page `/settings/activity` ; payload sans PII | T4 |
| Console | Login, signup, verify, forgot/reset, accueil (sélecteur de services), clés sous Identity, équipe, activité, compte, facturation org | M2 / T |
| Isolation | Org A ne voit pas org B (404), y compris avec un cookie volé | M2 / T1 |

### 2.2 Hors périmètre (M3+ / après T4)

| Domaine | Reporté |
|---|---|
| Facturation | Crédit d’organisation, Checkout carte, `GET /v1/usage` — routes = `BILLING_WRITE` |
| Clés live | `ky_live_`, `RG-SUB-02` |
| Auth avancée | SSO, 2FA (bandeau d’avertissement seulement), SCIM, reset MFA par admin |
| Multi-org | Un user dans plusieurs organisations |
| IDV métier | Capture / pipeline (M4) ; les routes console vérifs **existent** comme garde de droits (liste vide / 410) |
| Audit | Export CSV ; events hors org |
| Équipe avancée | Groupes custom, plusieurs owners sans transfert explicite |

### 2.3 Invariants S1 à ne pas casser

Isolation 404, envelope `{ error }`, hash BCrypt des clés, Bearer obligatoire sur `/v1/verifications/**`. Les organisations seed des tests API (`ky_test_orgAxxxx`, etc.) restent ; la démo interactive n’y passe pas.

Les tables IDV (`verifications`, …) ont été retirées en V7 ; le plan machine Bearer sur `/v1/verifications/**` reste (401 sans clé). La console gate les stubs `/v1/console/verifications*` par `VERIFICATION_*` jusqu’à M4.

---

## 3. Acteurs

| Acteur | Description | Preuve d’identité |
|---|---|---|
| **Visiteur** | Découvre l’offre sur `web/site` | Aucune |
| **Utilisateur console** | Humain qui opère l’org (un des cinq rôles) | Cookie `rm_session` |
| **Backend client** | Intégrateur / scripts / tests | Bearer `ky_test_` / `ky_live_` |
| **Applicant** | Personne vérifiée via le lien hébergé | Token d’URL — **jamais** de compte Recogniz-Me |
| **Système** | Rate limit, expiration des tokens, audit | — |

Rôles console (T2) :

| Rôle | Intention |
|---|---|
| `owner` | Compte : facturation (M3), transfert, dernier filet |
| `admin` | Équipe + clés + audit ; pas la CB ni le transfert |
| `member` | Crée et lit les vérifications |
| `readonly` | Lit les vérifications, rien d’autre |
| `developer` | Clés **sans** PII des sessions de vérification |

---

## 4. Concepts

| Concept | Définition |
|---|---|
| **Utilisateur** | Personne identifiée par un e-mail. Mot de passe hashé. Console interdite tant que `email_verified_at` est null. |
| **Organisation** | Tenant isolé, déjà présent en S1. Elle naît au signup (sauf invitation). Facturée plus tard (M3). |
| **Appartenance (membership)** | Lien user ↔ org + rôle + statut `active` \| `disabled`. Un user a **au plus une** membership. |
| **Permission** | Droit nommé (`TEAM_WRITE`, `API_KEY_READ`, …). Le rôle n’est plus testé en dur hors `OWNERSHIP`. |
| **Clé API** | Identité **machine**, distincte du login. Préfixe visible ; secret montré **une fois**. |
| **Session console** | Jeton opaque côté serveur, cookie `rm_session`. Le store porte `userId` + `organizationId` (le `role` du blob est **ignoré**). À chaque requête : membership absente, org ≠ session, ou statut `disabled` → **401**. Rôle = colonne `memberships`. |
| **Sandbox** | Environnement de clé `ky_test_`, toujours gratuit. Pas un plan d’organisation. Pas de colonne SQL. `GET /me` ne renvoie **plus** `plan`. |

---

## 5. Architecture

### 5.1 Surfaces et ports

| App | Port | Rôle |
|---|---|---|
| `web/site` | 3002 | CTA `SIGNUP_URL` / `LOGIN_URL` → `:3000` (déjà posés en M1) |
| `web/console` | 3000 | Auth + opération ; pas de Bearer dans le navigateur |
| `web/flow` | 3001 | **Inchangé** (applicant) |
| `api/` Spring Boot | 8080 | Account + console + S1. Un seul processus, pas de second JAR auth |

### 5.2 Deux plans d’authentification

```
Visiteur                      Utilisateur console              Backend / tests                 Applicant
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
              keys, audit         tokens hosted
```

\* Redis si disponible ; sinon store mémoire (même bascule que le hosted token S1). Le CDC §14 mentionnait PostgreSQL ; l’as-built conserve **MySQL** déjà en place (`reconizme`).

**Règle d’architecture :** ne **pas** activer `SessionCreationPolicy.ALWAYS` sur toute l’API. Une session Spring mêlée à un appel Bearer mélange les identités.

### 5.3 Chaîne de filtres (API)

Ordre :

1. `ApiKeyAuthenticationFilter` — s’applique à `/v1/verifications/**` (et chemins non publics hors account/console). Absence de Bearer → **401**, **même avec cookie**.
2. `SessionAuthenticationFilter` — s’applique à `/v1/console/**` et `POST /v1/account/logout`. Cookie manquant, inconnu, membership absente / org ≠ session / `disabled` → **401** `unauthorized`.
3. Autorisation Spring : routes account publiques en `permitAll` ; le reste de `/v1/account/**` et `/v1/console/**` authentifié.
4. Métier : `ConsoleAuth.require(Permission)` sur chaque verbe console (T2). Manque → **403** `forbidden`.

CORS : origine `http://localhost:3000`, méthodes dont `PATCH`, `allowCredentials = true`. La vitrine n’appelle pas ces routes.

### 5.4 Console → API

Les **server actions** Next lisent le cookie `rm_session` et le **forwardent** vers l’API (`Cookie: rm_session=…`).  
Le navigateur ne porte **jamais** de clé API. Après login, Next recopie le `Set-Cookie` API dans son propre cookie httpOnly (hôte `:3000`).

`GET /me` renvoie `permissions[]` pour masquer clés / activité / IDV côté UI. La **vérité** reste l’API.

### 5.5 Composants applicatifs

| Couche | Responsabilité |
|---|---|
| `AccountController` | Routes publiques compte + logout + accept invite |
| `ConsoleController` | Me, clés, équipe, transfer, disable/enable, audit |
| `ConsoleVerificationController` | Stubs `/v1/console/verifications*` — droits T2, métier M4 |
| `AccountService` | Signup, verify, login, reset, invite (rôle d’invite), mot de passe |
| `ConsoleService` | Me, clés, équipe, rôles, statut, audit ; `require(Permission)` |
| `ConsoleAuth` / `Permission` / `ConsoleRole` | Matrice de droits ; remplace `requireOwner()` |
| `SessionService` | Émission / lookup (rôle relu) / `invalidate` / `deleteByUserId` |
| `ApiKeyIssuer` | Génération `ky_test_`, BCrypt, audit `api_key.issued` |
| `MailPort` / adapters | Envoi. Token brut **uniquement** dans ce canal |
| `AuthRateLimiter` | Tentatives / fenêtre / IP sur login, resend, forgot |
| `ConsoleSessionStore` | Redis ou mémoire ; clé = hash(pepper + jeton) ; index `deleteByUserId` |

### 5.6 Séquence — inscription jusqu’à la console

```
Visiteur          Console           API              Mail/log         Collègue
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
   │                 │ POST /team/invites {email, role}  │                │
   │                 │──────────────►│──invite URL──────►│                │
   │                 │               │                   │   signup+verify│
   │                 │ GET /audit (owner/admin)          │                │
```

---

## 6. Modèle de données

Migrations Flyway : `V4__accounts.sql` (M2), `V5__user_names.sql`, `V8__team_invite_lifecycle.sql` (T1), `V9__membership_status.sql` (T3). Entités S1 (`organizations`, `api_keys`, `audit_events`) inchangées hormis `api_keys.created_by_user_id`.

### 6.1 Schéma relationnel

```
organizations (S1)
       │ 1
       │
       │ *                    1
memberships ────────────── users
(user_id, org_id,              │
 role, status, …)              │ 1
            ┌───────────────────┼───────────────────┐
            ▼                   ▼                   ▼
 email_verification_tokens  password_reset_tokens  membership_invites
 (token_hash, expires,        (token_hash, …)       (org_id, email, role,
  consumed)                                          pending_key, cancelled_at, …)

api_keys (S1)
  + created_by_user_id  NULL  ──► users.id

audit_events (S1)
  organisation_id, actor_*, action, resource_*, payload, created_at
```

### 6.2 Tables

#### `users`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | CHAR(36) | PK |
| `email` | VARCHAR(255) | UNIQUE, normalisé minuscules |
| `password_hash` | VARCHAR(255) | BCrypt, jamais renvoyé |
| `first_name` / `last_name` | VARCHAR(100) | NULL (V5) |
| `email_verified_at` | DATETIME(6) | NULL tant que non vérifié |
| `created_at` | DATETIME(6) | NOT NULL |

#### `memberships`

| Colonne | Type | Contraintes |
|---|---|---|
| `user_id` | CHAR(36) | PK composite, FK `users` |
| `organization_id` | CHAR(36) | PK composite, FK `organizations` |
| `role` | VARCHAR(16) | `owner` \| `admin` \| `member` \| `readonly` \| `developer` |
| `status` | VARCHAR(16) | `active` \| `disabled` (défaut `active`, V9) |
| `disabled_at` | DATETIME(6) | NULL si actif |
| `disabled_by_user_id` | CHAR(36) | NULL si actif |
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
| `role` | VARCHAR(16) | Rôle à poser à l’acceptation (jamais `owner`) |
| `token_hash` | CHAR(64) | UNIQUE |
| `expires_at` | DATETIME(6) | |
| `accepted_at` | DATETIME(6) | NULL = non acceptée |
| `cancelled_at` | DATETIME(6) | NULL = non annulée (V8) |
| `pending_key` | VARCHAR(255) | E-mail tant que pending ; **NULL** si acceptée / annulée |
| `created_at` | DATETIME(6) | |

Index unique `(organization_id, pending_key)` — plusieurs NULL autorisés (invites closes).

#### `api_keys` (évolution M2)

| Colonne | Type | Contraintes |
|---|---|---|
| `created_by_user_id` | CHAR(36) | NULL, FK `users` — null pour les clés seed S1 |

#### `audit_events` (S1, lu en T4)

`payload` est du JSON **sans** e-mail ni nom. Pour un changement de rôle : `{ "from":"member","to":"admin" }`.

### 6.3 Cycle de vie des jetons

| Jeton | TTL | Usage | Rotation |
|---|---|---|---|
| Vérification e-mail | 24 h | Lien `/verify?token=` | Resend invalide le précédent |
| Reset mot de passe | 1 h | Lien `/reset?token=` | Forgot invalide le précédent |
| Invitation | 7 j | Lien `/signup?invite=` | Resend invalide le précédent ; cancel pose `cancelled_at` |
| Session `rm_session` | 7 j | Cookie console | Logout ; `deleteByUserId` au retrait / disable |

### 6.4 Session (hors SQL)

Enregistrement dans Redis (`console:session:{hash}`) ou Map mémoire :

```
{ userId, organizationId, role }
```

Le champ `role` du blob est **écrit au login mais ignoré à l’auth**. Authenticate :

1. Lookup store.
2. Membership du `userId` : absente, `organizationId` ≠ session, ou `status = disabled` → session rejetée (**401**).
3. `ConsolePrincipal.role` = `memberships.role` (effet **immédiat** d’un PATCH).

`deleteByUserId` : index Redis `console:user-sessions:{userId}` ; in-memory : filtre. Appelé au **retrait** et au **disable**.

L’organisation **ne se lit pas** dans un header client.

### 6.5 Ce qui n’existe pas encore

Pas de table `Plan`, ni colonne `organizations.plan`. Le sandbox est des clés `ky_test_` + un solde à 0, jusqu’à M3 (`CreditAccount`, Checkout carte, `ky_live_`).

---

## 7. Cas d’utilisation

Les UC CDC §13 (`UC-ACC-01`, `UC-ACC-02`, `UC-ISO-01`) sont détaillés ici. Alternatives et post-conditions sont normatives.

### UC-ACC-01 — Créer un compte (owner)

**Acteur :** visiteur  
**Précondition :** e-mail non encore inscrit.

**Scénario nominal**

1. Le visiteur ouvre `/signup` (vitrine ou URL directe).
2. Il saisit e-mail, mot de passe, nom d’organisation (prénom / nom optionnels).
3. L’API crée user (non vérifié), organisation, membership `owner` **active**.
4. Un mail de vérification est émis (log URL en dev).
5. La console affiche `/verify/pending`. **Aucune session n’est ouverte.**
6. Le visiteur ouvre le lien → e-mail vérifié → **première** `ky_test_` renvoyée une fois.
7. Il se connecte (`UC-ACC-02`).

**Post-condition :** org Sandbox ; user vérifié ; une clé test existe (hash en base, secret déjà montré).

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | E-mail déjà pris | **409** `email_taken` |
| A2 | Mot de passe hors politique, e-mail invalide, nom org &lt; 2 | **400** `validation_error` |
| A3 | Lien verify déjà consommé / expiré | **400** `invalid_or_expired_token` |
| A4 | Login avant verify | **403** `email_unverified` + page pending |

### UC-ACC-02 — Se connecter et opérer

**Acteur :** utilisateur vérifié  
**Précondition :** `email_verified_at` non null ; membership **active**.

**Scénario nominal**

1. POST `/v1/account/login` avec e-mail + mot de passe.
2. **204** + cookie `rm_session`.
3. Accueil console : nom d’org, services, usage **0**, rôle, CTA selon permissions.
4. POST logout : store invalidé, cookie expiré.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | E-mail inconnu **ou** mot de passe faux | **401** `invalid_credentials` (message **identique**) + audit `user.login_failed` si le user existe (sans e-mail dans le payload) |
| A2 | E-mail non vérifié | **403** `email_unverified` |
| A3 | Membership `disabled` | **403** `membership_disabled` |
| A4 | Cookie absent / inconnu / membership absente / disabled sur `/v1/console/**` | **401** ; UI → `/login?next=` |
| A5 | Session expirée (TTL 7 j) | comme A4 |
| A6 | Rate limit login | **429** `rate_limited` |

### UC-ACC-03 — Mot de passe oublié

**Acteur :** quiconque connaît un e-mail (énumération interdite).

1. POST `/password/forgot` `{ email }` → **204 toujours**.
2. Si le compte existe : mail avec `/reset?token=` (TTL 1 h).
3. POST `/password/reset` `{ token, password }` → **204**.
4. Token rejoué → **400** `invalid_or_expired_token`.

Connecté : `POST /v1/console/account/password` exige le mot de passe **actuel**.

### UC-ACC-04 — Inviter, renvoyer, annuler

**Acteur :** `TEAM_WRITE` (admin, owner)  
**Précondition :** session active.

1. POST `/v1/console/team/invites` `{ email, role? }`. `role` omis → `member`. `role: owner` → **400** `validation_error` (transfert dédié).
2. Mail `/signup?invite=` (TTL 7 j). Audit `membership.invited`.
3. Pending déjà ouvert pour cet e-mail → **409** `already_invited`. Expiré non annulé → rotation du token (et du rôle si fourni).
4. L’invité s’inscrit **avec le même e-mail** et `invite_token` : user **sans** nouvelle org.
5. À la vérification d’e-mail : membership = **`invite.role`**, **pas** de nouvelle clé.
6. Variante : user déjà existant sans org → `POST /v1/account/invites/accept` (même rôle d’invite).
7. Resend : `POST .../invites/{id}/resend` → **204**, nouveau token, audit `membership.invite_resent`.
8. Cancel : `DELETE .../invites/{id}` → **204**, `cancelled_at`, `pending_key` NULL, audit `membership.invite_cancelled`.

**Alternatives**

| ID | Condition | Résultat |
|---|---|---|
| A1 | Appelant sans `TEAM_WRITE` | **403** `forbidden` |
| A2 | E-mail déjà membre de cette org | **409** `already_member` |
| A3 | Accept alors que le user a déjà une org | **409** `already_in_organization` |
| A4 | Token invite ≠ e-mail de signup / déjà annulé / expiré | **400** `invalid_or_expired_token` |
| A5 | Invite d’une autre org | **404** |
| A6 | Rôle inconnu ou `owner` | **400** `validation_error` (champ `role`) |

### UC-ACC-05 — Gérer les clés API

**Acteur :** `API_KEY_READ` pour lister ; `API_KEY_WRITE` pour émettre / révoquer (developer, admin, owner).

- Émission : plaintext **une fois** (`verify` du **owner** à la création d’org, ou `POST /v1/console/api-keys`). Audit `api_key.issued`.
- Liste : `id`, `key_prefix`, `created_at`, `revoked` — jamais `key` ni `key_hash`. Member / readonly → **403**.
- Révocation : **204** ; audit `api_key.revoked`. Sans `API_KEY_WRITE` → **403**.
- UI : écran masqué sans `API_KEY_READ` ; « elle ne sera plus montrée ».

### UC-ACC-06 — Vérifications depuis la console (garde T2, métier M4)

Les routes `/v1/console/verifications*` **existent** pour appliquer la matrice T2. Tant que M4 n’est pas livré :

| Méthode | Permission | As-built |
|---|---|---|
| GET liste | `VERIFICATION_READ` | **200** `[]` |
| GET `{id}` | `VERIFICATION_READ` | **404** (après le droit) |
| POST créer | `VERIFICATION_WRITE` | **410** `idv_unavailable` |
| POST cancel | `VERIFICATION_WRITE` | **404** (après le droit) |

Sans le droit → **403** `forbidden` (readonly ne crée pas ; developer ne lit pas).  
Le hosted flow applicant et le Bearer `/v1/verifications/**` restent le plan machine.

### UC-ACC-07 — Retirer un membre

**Acteur :** `TEAM_WRITE`. Retirer un **owner** exige `OWNERSHIP`.

1. `DELETE /v1/console/team/members/{userId}` → **204**.
2. Membership supprimée ; `deleteByUserId` ; audit `membership.removed` (payload `{}`, `resource_id` = user retiré).
3. La victime : cookie encore présenté → **401** au clic suivant.

**Alternatives :** hors org → **404**. Dernier **owner actif** → **409** `last_owner`. Sans droit → **403**.

### UC-ACC-08 — Changer un rôle / transférer la propriété

**Acteur :** `TEAM_WRITE` pour un rôle non-owner. Passer **vers** `owner`, ou modifier un owner, exige `OWNERSHIP`.

1. `PATCH /v1/console/team/members/{userId}` `{ "role" }` → **204**. Audit `membership.role_changed` `{ "from","to" }`.
2. Le cookie de la cible n’a **pas** besoin d’être renouvelé : `GET /me` renvoie le nouveau rôle tout de suite.
3. Dernier owner actif rétrogradé → **409** `last_owner`.
4. Admin PATCH vers `owner` → **403**.
5. Transfert : `POST /v1/console/team/transfer` `{ "user_id" }` (`OWNERSHIP`). La cible (membre **actif**, pas soi) devient `owner` ; l’acteur devient `admin`. Cible disabled → **409** `membership_disabled`. Soi-même → **409** `cannot_transfer_self`.

On n’invite **pas** en `owner`. Un second owner ne se crée que par PATCH owner ou transfert.

### UC-ACC-09 — Désactiver / réactiver

**Acteur :** `TEAM_WRITE`. Cible owner → `OWNERSHIP`.

1. `POST .../members/{userId}/disable` → **204** ; `status=disabled` ; `deleteByUserId` ; audit `membership.disabled`.
2. La victime est hors console au clic suivant (**401**). Un login ultérieur → **403** `membership_disabled`.
3. `POST .../enable` → **204** ; statut `active` ; audit `membership.enabled` ; le user peut se reconnecter.
4. `GET /team` liste les actifs **et** les inactifs (`status` sur chaque membre).
5. Se désactiver soi-même → **409** `cannot_disable_self`. Dernier owner actif → **409** `last_owner` (si l’action n’est pas déjà `cannot_disable_self`).

### UC-ACC-10 — Lire le journal d’activité

**Acteur :** `AUDIT_READ` (admin, owner).

1. `GET /v1/console/audit?action=&cursor=&limit=` → `{ events, next_cursor }`.
2. Filtre exact sur `action` ; curseur = id numérique (page suivante, plus ancien) ; `limit` défaut 20, max 100.
3. UI `/settings/activity` : filtre, tableau, page suivante. L’e-mail acteur peut être **joint** depuis `GET /team` pour l’affichage — il n’est **pas** dans `payload`.
4. Member / readonly / developer → **403**. Org A ne voit pas les events de B.

### UC-ISO-01 — Isolation tenant (rejoué)

| Tentative | Résultat |
|---|---|
| Session A, membre / invite / audit org B | **404** ou liste vide (audit filtré par org de session) |
| Cookie A + routes console | Liste / me de **A seulement** (org dans la session, pas un header) |
| Cookie A sur `/v1/verifications` sans Bearer | **401** (plan machine) |
| Bearer org A sur ressource B | **404** (S1) |
| Membre retiré ou disabled, cookie conservé | **401** |

---

## 8. Règles de gestion

| ID | Règle |
|---|---|
| **RG-ACC-01** | Un utilisateur appartient à **au plus une** organisation. |
| **RG-ACC-02** | Un signup **sans** invitation crée l’organisation et une membership `owner` active. |
| **RG-ACC-03** | Un signup **avec** invitation valide (même e-mail) ne crée **pas** d’org ; l’adhésion au **rôle de l’invite** est posée à la vérification d’e-mail. |
| **RG-ACC-04** | Toute org naît avec le sandbox gratuit (**RG-SUB-01**). Pas de `ky_live_` ici. |
| **RG-ACC-05** | E-mail unique (contrainte SQL + 409 `email_taken` au signup). |
| **RG-ACC-06** | E-mail stocké en minuscules, trim. |
| **RG-ACC-07** | Mot de passe : politique forte, BCrypt, jamais loggé, jamais renvoyé. |
| **RG-ACC-08** | Slug : dérivé du nom (`[a-z0-9-]`, 3–64). Collision → suffixe `-2`, `-3`, … |
| **RG-ACC-09** | Pas d’accès console tant que `email_verified_at` est null. |
| **RG-ACC-10** | Signup **n’ouvre pas** de session. |
| **RG-ACC-11** | Login e-mail inconnu et mauvais mot de passe : **même** 401 `invalid_credentials`. |
| **RG-ACC-12** | Forgot et resend verify : **204 toujours** (anti-énumération). |
| **RG-ACC-13** | Jeton e-mail / reset / invite : hash SHA-256 + pepper en base ; brut seulement dans l’URL du mail. |
| **RG-ACC-14** | Tokens à usage unique ; resend / forgot / resend invite invalide le précédent non consommé. |
| **RG-ACC-15** | Première `ky_test_` émise à la **vérification d’e-mail du owner**. Un invité ne reçoit pas de clé à ce moment. |
| **RG-ACC-16** | Le secret d’une clé n’est renvoyé qu’à l’émission. Tout GET ultérieur l’omet. |
| **RG-ACC-17** | Les verbes console passent par `require(Permission)` (§9). Plus de `requireOwner()` métier. |
| **RG-ACC-18** | L’organisation d’une requête console vient de la **session** (membership), jamais d’un header client. |
| **RG-ACC-19** | Cookie console **n’authentifie pas** `/v1/verifications/**` ni `/v1/flow/**`. |
| **RG-ACC-20** | Login, resend, forgot : rate limit par IP → 429 `rate_limited`. |
| **RG-ACC-21** | Invitation : rôle invitable `admin` \| `member` \| `readonly` \| `developer` ; jamais `owner` ; une pending par `(org, e-mail)`. |
| **RG-ACC-22** | Cookie `rm_session` : httpOnly, SameSite=Lax, Secure hors local, path `/`, max-age 7 jours. |
| **RG-ACC-23** | Audit **sans PII** dans `payload` (pas d’e-mail, pas de nom). |
| **RG-ACC-24** | Bandeau owner : la 2FA sera exigée plus tard — **pas** de 2FA à construire. |
| **RG-ACC-25** | Usage affiché à **0** jusqu’à M3. |
| **RG-ACC-26** | Authenticate relit `memberships` : rôle immédiat ; membership absente / org ≠ session / `disabled` → **401**. |
| **RG-ACC-27** | Retrait et disable appellent `deleteByUserId`. |
| **RG-ACC-28** | **Au moins un owner actif.** Interdit de rétrograder / retirer / désactiver le dernier → **409** `last_owner`. |
| **RG-ACC-29** | Interdit de se désactiver soi-même → **409** `cannot_disable_self`. |
| **RG-ACC-30** | Login d’une membership `disabled` → **403** `membership_disabled`. |
| **RG-ACC-31** | `AUDIT_READ` = admin + owner. Un member ne lit pas l’audit. |
| **RG-ACC-32** | M3 (Stripe) se branche sur `BILLING_READ` / `BILLING_WRITE`, pas sur `if ("owner")`. |

---

## 9. Matrice des droits

Helper : `ConsoleAuth.require(principal, Permission)`. `GET /me` expose `permissions` (noms d’enum).

```
TEAM_READ, TEAM_WRITE,
VERIFICATION_READ, VERIFICATION_WRITE,
API_KEY_READ, API_KEY_WRITE,
BILLING_READ, BILLING_WRITE,
OWNERSHIP, AUDIT_READ
```

| Permission | readonly | member | developer | admin | owner |
|---|---|---|---|---|---|
| `TEAM_READ` | oui | oui | oui | oui | oui |
| `VERIFICATION_READ` | oui | oui | **non** | oui | oui |
| `VERIFICATION_WRITE` | **non** | oui | **non** | oui | oui |
| `API_KEY_READ` / `WRITE` | **non** | **non** | oui | oui | oui |
| `TEAM_WRITE` | **non** | **non** | **non** | oui | oui |
| `AUDIT_READ` | **non** | **non** | **non** | oui | oui |
| `BILLING_*` | **non** | **non** | **non** | **non** | oui |
| `OWNERSHIP` | **non** | **non** | **non** | **non** | oui |

| Action | Droit |
|---|---|
| `GET /me`, `GET /team` | session + `TEAM_READ` |
| Liste / fiche vérif console | `VERIFICATION_READ` |
| Créer / annuler une vérif console | `VERIFICATION_WRITE` |
| Lister / émettre / révoquer une clé | `API_KEY_READ` / `WRITE` |
| Inviter, renvoyer, annuler, PATCH rôle non-owner, retirer non-owner, disable/enable non-owner | `TEAM_WRITE` |
| PATCH vers `owner`, modifier / retirer / disable un owner, `POST /transfer` | `OWNERSHIP` |
| `GET /audit` | `AUDIT_READ` |
| Facturation (M3) | `BILLING_*` |
| Signup, verify, login, forgot, reset, accept invite | public |

Hors tenant / invite close → **404**. Sans permission → **403** `forbidden`.

---

## 10. Contrats d’API

Envelope d’erreur S1 : `{ "error": { "code", "message", "request_id" } }`.

### 10.1 Compte — `/v1/account`

| Méthode | Chemin | Auth | Succès | Corps / notes |
|---|---|---|---|---|
| POST | `/signup` | public | **201** `{ user_id }` | `{ email, password, organization_name, invite_token?, first_name?, last_name? }` |
| GET | `/verify` | public | **201** `{ id, key, key_prefix }` | Query `token`. Invité : `key` null |
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

Toutes les routes : cookie session, puis permission.

| Méthode | Chemin | Permission | Succès |
|---|---|---|---|
| GET | `/me` | session | `{ email, first_name, last_name, organization: { id, name, slug }, role, permissions }` |
| POST | `/account/password` | session | **204** `{ current_password, new_password }` |
| GET | `/verifications` | `VERIFICATION_READ` | **200** `[]` (M4 : liste réelle) |
| GET | `/verifications/{id}` | `VERIFICATION_READ` | **404** tant que M4 |
| POST | `/verifications` | `VERIFICATION_WRITE` | **410** `idv_unavailable` tant que M4 |
| POST | `/verifications/{id}/cancel` | `VERIFICATION_WRITE` | **404** tant que M4 |
| GET | `/api-keys` | `API_KEY_READ` | `[{ id, key_prefix, created_at, revoked }]` |
| POST | `/api-keys` | `API_KEY_WRITE` | **201** `{ id, key, key_prefix }` |
| POST | `/api-keys/{id}/revoke` | `API_KEY_WRITE` | **204** |
| GET | `/team` | `TEAM_READ` | `{ members: [{ id, email, role, status, created_at }], invites: [{ id, email, role, expires_at }] }` |
| POST | `/team/invites` | `TEAM_WRITE` | **201** `{ email, role? }` ; pending → **409** `already_invited` |
| POST | `/team/invites/{id}/resend` | `TEAM_WRITE` | **204** |
| DELETE | `/team/invites/{id}` | `TEAM_WRITE` | **204** |
| DELETE | `/team/members/{userId}` | `TEAM_WRITE` (+ `OWNERSHIP` si cible owner) | **204** |
| PATCH | `/team/members/{userId}` | `TEAM_WRITE` (+ `OWNERSHIP` si owner) | **204** `{ role }` |
| POST | `/team/transfer` | `OWNERSHIP` | **204** `{ user_id }` |
| POST | `/team/members/{userId}/disable` | `TEAM_WRITE` (+ `OWNERSHIP` si owner) | **204** |
| POST | `/team/members/{userId}/enable` | `TEAM_WRITE` (+ `OWNERSHIP` si owner) | **204** |
| GET | `/audit` | `AUDIT_READ` | `{ events: [{ id, action, actor_type, actor_id, resource_type, resource_id, payload, created_at }], next_cursor }` query `action`, `cursor`, `limit` |

### 10.3 Codes d’erreur

| HTTP | `code` | Quand |
|---|---|---|
| 409 | `email_taken` | Signup, e-mail déjà en base |
| 403 | `email_unverified` | Login avant verify |
| 403 | `membership_disabled` | Login d’une membership inactive |
| 401 | `invalid_credentials` | Login / changement de mot de passe |
| 401 | `unauthorized` | Cookie ou Bearer manquant / invalide / membership absente ou disabled |
| 400 | `invalid_or_expired_token` | Verify, reset, invite |
| 400 | `validation_error` | Bean validation ; rôle d’invite / PATCH invalide |
| 429 | `rate_limited` | Login, resend, forgot |
| 403 | `forbidden` | Permission manquante |
| 409 | `already_member` | Invite d’un membre déjà dans l’org |
| 409 | `already_invited` | Invite pending déjà ouverte pour cet e-mail |
| 409 | `already_in_organization` | Accept invite alors qu’une membership existe |
| 409 | `last_owner` | Dernier owner actif rétrogradé / retiré / désactivé |
| 409 | `cannot_disable_self` | Disable de sa propre membership |
| 409 | `cannot_transfer_self` | Transfert vers soi-même |
| 409 | `membership_disabled` | Transfert vers un membre inactif |
| 410 | `idv_unavailable` | POST vérif console avant M4 (après le check d’écriture) |
| 404 | `not_found` | Ressource hors tenant ou invite close |

Messages login : ne pas distinguer « e-mail inconnu » et « mauvais mot de passe ».

### 10.4 Clé affichée une fois

Émission (verify owner ou POST api-keys) :

```json
{ "id": "…", "key": "ky_test_…", "key_prefix": "ky_test_abcd" }
```

`key_prefix` = 12 premiers caractères (constante S1 `PREFIX_LENGTH`).  
Format brut : `ky_test_` + 48 hex.

### 10.5 Actions d’audit garanties (T4)

Sans e-mail dans `payload` :

| `action` | Quand |
|---|---|
| `user.registered` | Signup |
| `user.email_verified` | Verify |
| `user.login_failed` | Mauvais mot de passe **et** user existant (org de sa membership) |
| `membership.invited` / `invite_resent` / `invite_cancelled` | T1 |
| `membership.removed` | T1 |
| `membership.role_changed` | T2 — `{ "from", "to" }` |
| `membership.disabled` / `enabled` | T3 |
| `api_key.issued` / `api_key.revoked` | Clés |

---

## 11. Interface console

Tokens M0 (`console.css`). Layout public **sans** nav métier ; layout app avec nav + org + déconnexion. La nav **filtre** selon `permissions` : pas de clés sans `API_KEY_READ`, pas d’activité sans `AUDIT_READ`, pas de teaser IDV sans `VERIFICATION_READ`.

| Route | Accès | Contenu |
|---|---|---|
| `/signup` | public | Formulaire ; `?invite=` masque le nom d’org |
| `/login` | public | `?next=` après middleware ; message `membership_disabled` |
| `/forgot`, `/reset` | public | Reset |
| `/verify` | public | Clé une fois + copie ; ou « compte activé » (invité) |
| `/verify/pending` | public | Consigne + renvoyer l’e-mail |
| `/` | session | Org, Sandbox, usage 0, rôle, bandeau 2FA si owner |
| `/settings/keys` | `API_KEY_READ` | Liste préfixes ; émission / révocation si `API_KEY_WRITE` |
| `/settings/team` | session | Membres (rôle, badge Actif/Inactif), invites, invite+rôle si `TEAM_WRITE` ; transfert si `OWNERSHIP` |
| `/settings/activity` | `AUDIT_READ` | Journal filtrable, pagination curseur |
| `/settings/account` | session | E-mail, rôle, changement de mot de passe |

**Équipe (owner / admin) :** Renvoyer / Annuler / Retirer ; select de rôle par ligne ; Désactiver / Réactiver ; pas de Retirer / Désactiver sur le dernier owner actif ; pas de Désactiver sur soi.

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

Log : `correlation_id`, template, **URL complète** (le token n’existe que là). Pas d’adresse e-mail, pas de mot de passe. Gabarit HTML M0 `packages/brand/email/base.html`.

---

## 13. Exigences non fonctionnelles (applicables)

| ID CDC | Application |
|---|---|
| **NF-MVP-01** | Isolation tenant : 404, org de session — kill si fuite |
| **NF-MVP-02** | Cookie Secure hors local ; secrets hors git |
| **NF-MVP-03** | Logs : ids techniques ; jamais mot de passe, clé brute, e-mail applicant ; **jamais** e-mail / nom dans `audit_events.payload` |
| **NF-MVP-08** | Contrastes AA, focus clavier (charte M0) |
| **NF-MVP-10** | Rate limit login (et resend / forgot) |

HTTPS hors local. Pas de secrets dans le dépôt.

**Kill cycle T :** fuite tenant ; membre retiré ou désactivé encore authentifié ; PII dans `payload` ; dernier owner rétrogradé / retiré / bloqué ; developer qui lit une fiche applicant (quand M4 existera : aujourd’hui 403 sur GET vérif).

---

## 14. Traçabilité CDC

| Exigence CDC | Couverture |
|---|---|
| O3 — compte + org | UC-ACC-01 |
| §9.1 une org / user | RG-ACC-01 |
| §9.2 owner / member + invitation | UC-ACC-04 ; T2 **étend** volontairement (§4.2 hors MVP) |
| §9.3 cookie vs Bearer | RG-ACC-19, architecture §5.2 |
| §9.3 2FA hors MVP | RG-ACC-24 |
| §9.4 parcours inscription | UC-ACC-01, séquence §5.6 |
| §9.5 écrans (hors facturation) | §11 — facturation = M3 (`BILLING_*`) |
| RG-SUB-01 sandbox gratuit | RG-ACC-04 |
| UC-ACC-01 / 02 / UC-ISO-01 | §7 |
| §14.1 User, Membership, tokens | §6 — ledger / Checkout carte reportés M3 |
| §17.4 (acceptation compte) | Tests §15 + démo |

Écran CDC « Facturation » et accueil usage du mois : **M3**. Ici : usage **0**, pas de badge « plan ».

---

## 15. Acceptation et tests

**Démo M2 :** compte neuf → URL de mail en log → clé une fois → login → org seed S1 toujours isolée.

**Démo T :** owner invite un developer et un readonly → resend → accept (rôle immédiat) → PATCH rôle → disable (victime dehors) → `/settings/activity` sans e-mail dans les payloads.

**Kill :** console anonyme ; mot de passe / clé brute / e-mail applicant dans les logs ; secret relisible en GET ; user A voit org B ; membre retiré ou disabled encore dans la console ; PII dans `payload`.

| Test | Couvre |
|---|---|
| `SignupAndVerifyTest` | 201, mail, clé une fois, second verify 400 |
| `SignupEmailTakenTest` | 409 |
| `LoginUnverifiedTest` | 403 puis 204 + cookie httpOnly |
| `ConsoleRequiresSessionTest` | `/me` sans cookie → 401 |
| `MemberCannotRevokeKeyTest` | 403 |
| `ConsoleIsolationTest` | session A ≠ org B |
| `ApiKeyStillBearerTest` | cookie seul sur `/v1/verifications` → 401 ; Bearer → 200 |
| `ApiKeySecretNotRelistedTest` | GET keys sans `key` |
| `TeamLifecycleTest` | T1 : member 403, resend, cancel, double invite 409, remove → 401, dernier owner 409, isolation 404 |
| `TeamRolesTest` | T2 : readonly 403 POST vérif ; developer 403 GET vérif + 201 clé ; admin invite ; admin 403 PATCH owner ; dernier owner 409 ; rôle immédiat ; invite `developer` ; transfer |
| `TeamStatusTest` | T3 : disable → 401 ; login 403 `membership_disabled` ; enable ; pas de self-disable |
| `ConsoleAuditTest` | T4 : owner lit, member 403, pas d’e-mail dans le JSON, filtre, curseur, `api_key.revoked`, `user.login_failed`, isolation |
| Tests S1 existants | Régression isolation / Bearer |

UI : middleware vérifié (`/` → `/login?next=/`).

---

## 16. Suite

M2 + T verts → [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) **M3 — Crédit d’organisation**. Freeze pricing **avant** le premier Checkout. M3 ajoute le ledger, Stripe Checkout **carte**, `ky_live_`, usage réel et l’écran `/settings/billing` du CDC §9.5 / §10, branché sur **`BILLING_WRITE`** (pas `if owner`). M4 reprend les stubs `/v1/console/verifications*` sans changer la matrice T2.
