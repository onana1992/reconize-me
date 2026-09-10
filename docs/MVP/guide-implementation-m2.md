# Guide d’implémentation — M2 Compte client

**Plateforme :** Recogniz-Me  
**Sprint :** M2 — inscription, e-mail, login, organisation Sandbox, console derrière session  
**Version :** 1.1  
**Date :** 9 septembre 2026  
**Statut :** livré — voir la [spécification as-built](./specification-m2-compte-client.md)  
**CDC :** §9. **Objectif O3.** Critère d’acceptation §17.4.

**Documents liés :**
- [`specification-m2-compte-client.md`](./specification-m2-compte-client.md) — *quoi* (architecture, modèle, UC, RG-ACC)
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — contrat (§9, §14.1, NF-MVP-02/03)
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — ordre des sprints
- [`../specs/guide-implementation-s1.md`](../specs/guide-implementation-s1.md) — isolation, Bearer, clés — **ne pas casser**
- [`charte-visuelle.md`](./charte-visuelle.md) — écrans login / signup

Ce document dit **comment construire M2**. Pas de Stripe, pas de `ky_live_`, pas de 2FA.

---

## 1. Livrable démontrable

Un visiteur enchaîne, **sans clé seedée dans l’environnement** :

1. Vitrine → **Créer un compte** (`/signup` console).
2. E-mail + mot de passe + nom d’organisation.
3. Ouvre le lien de vérification (Mailhog / log dev).
4. Se connecte. Voit **une fois** la clé `ky_test_…`.
5. Crée une vérification S1 depuis la console. L’applicant ouvre le lien. Consentement comme aujourd’hui.

Sans session : `/` console, liste et création → **redirection `/login`**.  
La clé brute n’est **plus** relisible après avoir quitté l’écran d’émission.

**Kill :** console encore ouverte anonymement ; mot de passe, clé brute ou e-mail applicant dans les logs ; secret de clé renvoyé par un `GET` ultérieur ; user A voit l’org B.

Si ce livrable n’est pas là, **ne pas** ouvrir M3.

---

## 2. État de départ (aujourd’hui)

| Surface | État |
|---|---|
| `web/console` | Aucun login. `KYC_API_KEY` en env, Bearer injecté dans `lib/api.ts` |
| `web/site` | CTA déjà vers `http://localhost:3000/signup` et `/login` — pages **absentes** |
| API `/v1/verifications/**` | Bearer uniquement (`ApiKeyAuthenticationFilter`) |
| `organizations` / `api_keys` | Tables S1. Seed **tests** seulement (`ky_test_orgAxxxx`, etc.) |
| Utilisateur humain | N’existe pas |

S1 reste : isolation 404, envelope `{ error }`, hash BCrypt des clés, flow par token d’URL. Les tests S1 (`ApiKeyAuthTest`, `IsolationTest`, …) doivent rester verts.

---

## 3. Périmètre

| In | Out (M3+) |
|---|---|
| `User`, `Membership`, tokens e-mail / reset | SSO, 2FA, SCIM, multi-org |
| Signup, verify, login, logout, forgot / reset | Facturation, Checkout, `GET /v1/usage` |
| Création org + slug unique ; sandbox gratuit (libellé d’environnement de clé, pas un plan) | `ky_live_`, crédit org, Checkout carte |
| Cookie session httpOnly | Auth `/v1/verifications` par cookie |
| Rôles propriétaire / membre + invitation e-mail | RBAC fin, révocation de session distante |
| Écrans : login, signup, verify, forgot, clés sous Identity, équipe, compte | Checkout Stripe, ledger |

Une org naît avec le sandbox gratuit (RG-SUB-01, sans Stripe). L’écran d’accueil affiche les services, usage à **0** jusqu’à M3.

---

## 4. Décisions figées M2

| Sujet | Décision |
|---|---|
| Planes d’auth | **Deux.** Cookie = humain (console). Bearer = machine (`/v1/verifications`, `/v1/flow` inchangés) |
| Préfixe console API | `/v1/account/**` (public : signup, login, verify, reset) et `/v1/console/**` (session obligatoire) |
| Session | Côté serveur (Spring Session + Redis déjà prévu au CDC §14). Cookie `rm_session`, httpOnly, `SameSite=Lax`, `Secure` hors local, TTL 7 jours |
| Console → API | Server Actions / Route Handlers Next **forwardent le cookie**. Pas de Bearer dans le navigateur |
| Première clé | Émise **à la vérification d’e-mail**, `ky_test_` uniquement, plaintext **une fois** (réponse verify / create) |
| Accès console | Interdit tant que `email_verified_at` est null (page « vérifiez votre boîte ») |
| Appartenance | Un user MVP ∈ **une** org. Signup **crée** l’org |
| Slug | Dérivé du nom (minuscules, `[a-z0-9-]`, 3–64). Collision → suffixe `-2`, `-3`… |
| Mot de passe | BCrypt (bean S1). Longueur 10–128. Jamais loggé, jamais renvoyé |
| E-mail unique | Contrainte SQL. Doublon signup → **409** `email_taken` (ne pas révéler si l’e-mail existe au login : même message « identifiants invalides ») |
| Invitation | Lien à usage unique, rôle `member`. Propriétaire seul invite et révoque les clés |
| Orgs seed S1 | Conservées pour les tests API. La démo M2 n’y passe pas |

---

## 5. Ordre de build

Durée indicative : **2 semaines**. Ne pas commencer la console lock tant que login + verify ne tiennent pas en API.

### Tranche A — Données (jour 1)

Flyway `V4__accounts.sql` :

| Table | Rôle |
|---|---|
| `users` | `id`, `email` unique, `password_hash`, `email_verified_at` nullable, `created_at` |
| `memberships` | PK `(user_id, organization_id)`, `role` (`owner` \| `member`), `created_at` |
| `email_verification_tokens` | `id`, `user_id`, `token_hash` SHA-256, `expires_at`, `consumed_at` |
| `password_reset_tokens` | idem |
| `membership_invites` | `id`, `organization_id`, `email`, `role`, `token_hash`, `expires_at`, `accepted_at` |

Ajouter sur `api_keys` : `created_by_user_id` nullable (clés seed S1 restent sans auteur).

`organizations` : pas de colonne plan en M2. M3 ajoutera `CreditAccount` et le Checkout **carte**, **pas** un plan d’abonnement ni un meter.

Entités JPA + repositories. Pas encore de routes.

### Tranche B — Compte sans UI (jours 2–4)

Services : `AccountService`, `SessionService`, `MailPort` (dev : log + fichier ; option Mailhog).

| Méthode | Chemin | Auth | Effet |
|---|---|---|---|
| `POST` | `/v1/account/signup` | public | Corps `{ email, password, organization_name }` → **201** `{ user_id }` ; envoie le mail. **Pas** de session |
| `GET` | `/v1/account/verify?token=` | public | Consomme le token, pose `email_verified_at`, crée `ky_test_`, **201** `{ api_key }` **plaintext une fois** |
| `POST` | `/v1/account/verify/resend` | public, rate-limité | Corps `{ email }` ; toujours **204** (pas d’énumération) |
| `POST` | `/v1/account/login` | public | `{ email, password }` → **204** + `Set-Cookie`. E-mail non vérifié → **403** `email_unverified` |
| `POST` | `/v1/account/logout` | session | Invalide la session, expire le cookie |
| `POST` | `/v1/account/password/forgot` | public | `{ email }` → **204** toujours |
| `POST` | `/v1/account/password/reset` | public | `{ token, password }` → **204** |

Envelope d’erreur S1. Codes nouveaux :

| HTTP | `code` |
|---|---|
| 409 | `email_taken` |
| 403 | `email_unverified` |
| 401 | `invalid_credentials` |
| 400 | `invalid_or_expired_token` |
| 429 | `rate_limited` (login + resend + forgot) |

Filtre session **après** le filtre clé : une requête `/v1/verifications` **sans** Bearer reste **401**, même avec cookie.

CORS : `http://localhost:3000` avec `allowCredentials`. La vitrine (`:3002`) n’appelle pas ces routes.

Audit (sans PII) : `user.registered`, `user.email_verified`, `user.login_failed` (compteur, pas l’e-mail), `api_key.issued`.

### Tranche C — Console API sous session (jours 5–6)

Principal session : `userId` + `organizationId` + `role`.

| Méthode | Chemin | Rôle min. | Effet |
|---|---|---|---|
| `GET` | `/v1/console/me` | member | `{ email, organization: { id, name, slug }, role }` |
| `POST` | `/v1/console/verifications` | member | Délègue à `VerificationService` avec l’org de session |
| `GET` | `/v1/console/verifications` | member | Liste du tenant |
| `GET` | `/v1/console/verifications/{id}` | member | Fiche. Autre org → **404** |
| `POST` | `/v1/console/verifications/{id}/cancel` | member | Comme S1 |
| `GET` | `/v1/console/api-keys` | member | Liste : `id`, `key_prefix`, `created_at`, `revoked` — **jamais** le hash ni le secret |
| `POST` | `/v1/console/api-keys` | owner | Émet `ky_test_`, plaintext **une fois** |
| `POST` | `/v1/console/api-keys/{id}/revoke` | owner | Membre → **403** `forbidden` |
| `GET` | `/v1/console/team` | member | Membres + invitations en cours |
| `POST` | `/v1/console/team/invites` | owner | `{ email }` → mail + token |
| `POST` | `/v1/account/invites/accept` | public (token) | Crée le membership `member` si l’user existe / après signup |

`VerificationService` ne change pas de métier : il prend un `organizationId`. Le contrôleur console le lit sur la session au lieu de `ApiPrincipal`.

### Tranche D — Console UI (jours 7–9)

Tokens M0 (`console.css`). Pages :

| Route | Accès |
|---|---|
| `/signup`, `/login`, `/forgot`, `/reset`, `/verify` | Public. Layout **sans** nav métier |
| `/verify/pending` | Après signup, e-mail non vérifié |
| `/` | Session. Accueil : org, services, CTA |
| `/verifications`, `/verifications/new`, `/verifications/[id]` | Session. Remplacer `KYC_API_KEY` par les appels `/v1/console/**` |
| `/settings/keys` | Session. Secret affiché uniquement juste après create / verify |
| `/settings/team` | Session |
| `/settings/account` | Session. Changement de mot de passe (session + mot de passe actuel) |

Middleware Next : pas de cookie / 401 session → `/login?next=`.  
`lib/api.ts` : plus de `KYC_API_KEY`. Forward `Cookie` depuis les server actions.

Bandeau (owner) : « L’authentification à deux facteurs sera exigée plus tard » — pas de 2FA à construire.

### Tranche E — Vitrine + e-mail (jour 10)

- CTA M1 : `SIGNUP_URL` / `LOGIN_URL` pointent déjà sur `:3000` — **vérifier** que les pages existent.
- Gabarit M0 `packages/brand/email/base.html` : verify, reset, invite. Substitutions existantes. Dev : log de l’URL complète (le token brut n’existe **que** dans ce canal).
- Rate limit login : 5 / 15 min / IP (in-memory OK en local, Redis en staging).

### Tranche F — Tests et démo (jours 11–12)

Voir §11. Démo : compte neuf → mail → clé une fois → vérif S1 → org seed S1 toujours isolée.

---

## 6. Auth : deux planes

```
Navigateur console          Intégrateur / tests S1         Applicant
        │                            │                          │
        ▼                            ▼                          ▼
 cookie rm_session           Bearer ky_test_ / ky_live_      token URL
        │                            │                          │
        ▼                            ▼                          ▼
 /v1/account/**              /v1/verifications/**           /v1/flow/**
 /v1/console/**
```

`SecurityConfig` aujourd’hui : stateless + filtre clé. M2 :

1. Garder le filtre clé pour `/v1/verifications/**`.
2. Session **seulement** sur `/v1/account/**` (sauf signup/login/verify/reset publics) et `/v1/console/**`.
3. `/v1/flow/**`, health, OpenAPI : inchangés.

Ne **pas** passer `SessionCreationPolicy.ALWAYS` sur toute l’API : une session sur un appel Bearer mélange les identités.

---

## 7. Clé `ky_test_` affichée une fois

À l’émission (verify e-mail ou `POST /v1/console/api-keys`) :

```json
{ "id": "…", "key": "ky_test_…", "key_prefix": "ky_test_abcd" }
```

Tout `GET` ultérieur : `id`, `key_prefix`, `created_at`, `revoked`. Pas de `key`.

UI : champ + bouton copier + phrase « elle ne sera plus montrée ». Navigation ailleurs → disparue.

---

## 8. Front — ports

| App | Port | M2 |
|---|---|---|
| `web/site` | 3002 | Liens signup / login déjà posés |
| `web/console` | 3000 | Auth + écrans compte ; vérifs S1 derrière session |
| `web/flow` | 3001 | **Inchangé** |
| API | 8080 | Account + console + S1 |

---

## 9. E-mail (dev)

`MailPort.send(toHashOrId, template, url)`. En log : `user_id`, template, **pas** l’adresse (ou adresse seulement en profil `dev` derrière un flag explicite). Le token brut est dans l’URL du mail, jamais en base (hash SHA-256 + pepper, comme le token hosted S1).

TTL verify : 24 h. TTL reset : 1 h. TTL invite : 7 jours. Rotation : un nouveau resend invalide le précédent.

---

## 10. Isolation et droits

| Cas | Résultat |
|---|---|
| User A, session, `GET` vérif org B | **404** (comme S1) |
| Membre `POST .../api-keys/{id}/revoke` | **403** `forbidden` |
| Login e-mail inconnu / mauvais mot de passe | **401** `invalid_credentials` identique |
| Signup e-mail déjà pris | **409** `email_taken` |
| Cookie volé d’A sur les routes de B | 404 / liste vide — l’org vient de la **membership**, pas d’un header |

---

## 11. Tests minimaux

En plus de la régression S1 :

| Test | Couvre |
|---|---|
| `SignupAndVerifyTest` | 201, mail token, clé une fois, second verify 400 |
| `SignupEmailTakenTest` | 409 |
| `LoginUnverifiedTest` | 403 ; après verify, 204 + cookie |
| `ConsoleRequiresSessionTest` | `/v1/console/me` sans cookie → 401 ; pages console redirect login |
| `MemberCannotRevokeKeyTest` | 403 |
| `ConsoleIsolationTest` | session A, id de B → 404 |
| `ApiKeyStillBearerTest` | `/v1/verifications` cookie seul → 401 ; Bearer seed S1 → 200 |
| `ApiKeySecretNotRelistedTest` | GET keys sans champ `key` |

---

## 12. Code de référence (à créer)

| Zone | Classes visées |
|---|---|
| Compte | `AccountController`, `AccountService`, `ConsoleController` |
| Session | `SessionAuthenticationFilter` (cookie), `ConsolePrincipal` |
| Mail | `MailPort`, `LoggingMailAdapter` |
| Données | `User`, `Membership`, `EmailVerificationToken`, `PasswordResetToken` |
| Console | `web/console/app/login`, `signup`, `settings/keys`, middleware session |
| Existants à réutiliser | `PasswordEncoder`, `CryptoTokens`, `ApiKey` / émission actuelle, `VerificationService`, `GlobalExceptionHandler` |

Un seul processus Spring Boot. Pas de second JAR auth.

---

## 13. Hors M2 (rappel)

Stripe, `ky_live_`, usage, portail facturation → **M3**.  
Capture, stub IDV → **M4**.  
Ne pas « profiter » de M2 pour du SSO ou de la 2FA.

---

## 14. Suite

M2 vert → [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) **M3 — Facturation à l’usage**. Freeze pricing **avant** le premier Checkout.
