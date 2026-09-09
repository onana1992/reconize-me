# Roadmap d’implémentation — Gestion d’équipe

**Plateforme :** Recogniz-Me  
**Livrable :** console équipe type Onfido / Veriff Station  
**Version du document :** 1.0  
**Date :** 7 septembre 2026  
**Statut :** ordre de build (cycle T)  
**Documents liés :**
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — §4.2 (hors MVP : rôles avancés) ; T1 ne l’étend pas
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — calendrier M0–M6
- [`specification-m2-compte-client.md`](./specification-m2-compte-client.md) — as-built compte + équipe T0–T4
- [`guide-implementation-m2.md`](./guide-implementation-m2.md) — comment M2 a été construit

Ce document dit **quand** et **dans quel ordre** on construit la gestion d’équipe. Un sprint n’est pas vert sans son **livrable démontrable**.

Le CDC MVP (§4.2) exclut « équipe multi-rôles avancée ». **T1** reste sur `owner` / `member` (cycle de vie). **T2+** est l’exception CDC, à ouvrir volontairement.

---

## 1. Cible

**Happy path :** un owner invite un collègue, choisit un rôle, peut renvoyer ou annuler l’invitation, retirer ou désactiver un membre, et relire **qui a fait quoi** dans un journal d’activité — sans fuite de tenant ni session zombie.

Sans T1, la page `/settings/team` reste une liste morte (invite only). Sans T2, ce n’est pas Station : un developer voit les PII, un readonly peut créer des vérifs.

---

## 2. Principes d’ordre

1. **Session vraie avant nouveaux verbes** — le rôle et l’appartenance se relisent sur `memberships` à chaque requête. Sinon retirer / changer un rôle ment pendant 7 jours (TTL cookie).
2. **Cycle de vie avant rôles** — resend, cancel, remove, dernier owner. Toujours `owner` / `member`.
3. **Permissions nommées avant M3** — T2 pose `require(Permission)` ; Stripe se branche sur `BILLING_*`, pas sur des `if ("owner")` dispersés.
4. **Statut avant journal** — disable / enable, puis activity log.
5. **Un livrable démontrable par sprint.** Pas de groupes custom, SSO, SCIM, 2FA dans ce cycle.

**Kill (tout le cycle T) :** fuite tenant ; membre retiré ou désactivé encore authentifié ; PII (e-mail, nom) dans `audit_events.payload` ; dernier owner rétrogradé / retiré / bloqué.

---

## 3. Vue d’ensemble

Durée indicative : **1 semaine par sprint**, ~4–5 semaines. T0 est **déjà livré** (M2).

```
M2 comptes (fait) ──► T1 cycle de vie équipe
                      │
                      ├──► T2 rôles (owner / admin / member / readonly / developer)
                      │         ▲
M3 Stripe ────────────┴─────────┘  helper de droits avant (ou avec) billing
                      │
                      ▼
                 T3 statut actif / inactif
                      │
                      ▼
                 T4 journal d’activité console
```

| Sprint | Livrable démontrable | Statut |
|---|---|---|
| **T0** | Invite owner → member, liste équipe, clés owner-only | **Livré** (M2) |
| **T1** | Resend / cancel invite, retirer un membre, dernier owner, session relue | **Livré** |
| **T2** | Cinq rôles + matrice ; invitation avec rôle ; PATCH rôle | **Livré** |
| **T3** | Disable / enable ; logout immédiat | **Livré** |
| **T4** | `GET /v1/console/audit` + page `/settings/activity` | **Livré** |

### Parallélisme autorisé

| En même temps | Condition |
|---|---|
| **T1 ∥ M4** | M2 vert |
| **T2** avant ou avec **M3** | Routes Stripe = `BILLING_WRITE`, pas `if owner` |
| **T3, T4** après T2 | Le journal affiche les vrais rôles |
| SSO / 2FA / SCIM / groupes custom | **Après T4** |

Interdit : T2 sans relecture membership (T1) ; T3 sans `deleteByUserId` ; T4 avec e-mail dans le payload.

---

## 4. Catalogue de rôles (cible T2)

Aligné Onfido / Veriff, nommé pour **nos** surfaces (vérifs, clés, équipe, plus tard billing / webhooks). Garder le mot `member` (déjà en base).

| Rôle | Équivalent | Intention |
|---|---|---|
| `owner` | Owner / Admin-Owner | Compte : facturation, transfert, dernier filet |
| `admin` | Admin | Équipe + clés ; pas la CB ni le transfert |
| `member` | Standard | Crée et lit les vérifications |
| `readonly` | Read-only | Lit les vérifications, rien d’autre |
| `developer` | Developer | Clés / webhooks **sans** PII des sessions |

Matrice T2 :

| Action | readonly | member | developer | admin | owner |
|---|---|---|---|---|---|
| `GET /team`, `GET /me` | oui | oui | oui | oui | oui |
| Liste / fiche vérif | oui | oui | **403** | oui | oui |
| Créer / annuler une vérif | **403** | oui | **403** | oui | oui |
| `GET` préfixes de clés | **403** | **403** | oui | oui | oui |
| Émettre / révoquer une clé | **403** | **403** | oui | oui | oui |
| Inviter, renvoyer, annuler, changer rôle, retirer | **403** | **403** | **403** | oui | oui |
| Facturation (M3) | **403** | **403** | **403** | **403** | oui |
| Promouvoir / rétrograder un **owner**, transférer | **403** | **403** | **403** | **403** | oui |

Règle : **au moins un owner actif**. Interdit de se rétrograder / se retirer / se bloquer si on est le dernier.

Permissions à poser en T2 (remplace `requireOwner()`) :

```
TEAM_READ, TEAM_WRITE,
VERIFICATION_READ, VERIFICATION_WRITE,
API_KEY_READ, API_KEY_WRITE,
BILLING_READ, BILLING_WRITE,
OWNERSHIP, AUDIT_READ
```

---

## 5. T0 — As-built (ne pas reconstruire)

Déjà en place :

- Org + `memberships` (`owner` \| `member`) + invitations hashées, TTL 7 j
- Owner invite ; membre voit la liste
- Clés : owner only
- Isolation : org dans la **session**, pas un header
- UI `/settings/team` : liste + formulaire e-mail

Limites T1–T3 :

- Invitation **toujours** `member` (colonne `role` existante, ignorée à l’acceptation)
- Pas de resend / cancel / remove
- Rôle **figé dans le cookie** au login — un changement resterait ineffectif jusqu’à 7 jours
- `ConsoleSessionStore` ne révoque pas **toutes** les sessions d’un user
- Doublons d’invites possibles (pas d’unicité org + e-mail pending)

---

## 6. Sprints

### T1 — Cycle de vie

**Durée :** 1 semaine.  
**Prérequis :** M2. Peut ∥ M4.

**Livrable :** un owner invite, **renvoie**, **annule**, **retire** un membre. Le dernier owner ne peut pas partir. Un membre retiré est hors console au clic suivant.

**Statut :** livré (7 septembre 2026).

| In | Out |
|---|---|
| Resend invite (invalide le token précédent, comme verify) | Nouveaux rôles |
| Cancel invite (`cancelled_at`) | SSO, 2FA |
| Remove member | Désactivation (T3) |
| Garde « dernier owner » | Multi-org |
| Unique pending par `(organization_id, email)` | Groupes custom |
| Session = `userId` + `organizationId` ; rôle relu sur `memberships` | |
| `ConsoleSessionStore.deleteByUserId` | |

**API**

| Méthode | Chemin | Rôle | Succès | Effet |
|---|---|---|---|---|
| `POST` | `/v1/console/team/invites` | owner | **201** | `{ email }` ; pending existant → **409** `already_invited` ; expiré → rotate |
| `POST` | `/v1/console/team/invites/{id}/resend` | owner | **204** | Nouveau token, mail, TTL 7 j |
| `DELETE` | `/v1/console/team/invites/{id}` | owner | **204** | Pose `cancelled_at`, libère la clé pending |
| `DELETE` | `/v1/console/team/members/{userId}` | owner | **204** | Supprime la membership + sessions |

Hors tenant / déjà acceptée / déjà annulée → **404**. Member → **403** `forbidden`. Dernier owner → **409** `last_owner`.

**Données**

- `membership_invites.cancelled_at` nullable
- `membership_invites.pending_key` = e-mail tant que pending, **NULL** si acceptée / annulée
- Index unique `(organization_id, pending_key)` (plusieurs NULL autorisés)

**Session**

1. Authenticate : membership absente ou org ≠ session → **401**. Rôle = colonne `memberships`, pas le blob Redis.
2. `deleteByUserId` à chaque retrait (index Redis `console:user-sessions:{userId}` ; in-memory : filtre).

**UI** `/settings/team` : Renvoyer / Annuler / Retirer (owner). Pas de Retirer sur le dernier owner.

**Audit (sans PII) :** `membership.invited` (existant), `membership.invite_resent`, `membership.invite_cancelled`, `membership.removed`.

**Tests minimaux :** member 403 ; resend invalide l’ancien lien ; remove → cookie victime 401 ; dernier owner 409 ; org A ne retire pas un membre de B (404) ; double invite 409.

**Démo :** invite → resend (log mail) → accept → retirer → la victime est dehors.  
**Kill :** membre retiré encore authentifié ; org A agit sur org B.

---

### T2 — Rôles

**Durée :** 1–2 semaines.  
**Prérequis :** T1. Avant ou avec M3.

**Livrable :** à l’invitation on choisit le rôle ; un admin change le rôle dans un menu ; `developer` ne voit pas les vérifs ; `readonly` ne crée pas ; `admin` gère l’équipe mais pas Stripe.

**Statut :** livré (8 septembre 2026).

| In | Out |
|---|---|
| Cinq rôles ci-dessus | Groupes custom |
| Invite `{ email, role }` | `role: owner` à l’invitation (transfert dédié) |
| `PATCH` rôle membre | SCIM |
| Helper `require(Permission)` | 2FA |
| `POST /v1/console/team/transfer` | |

**Correctif latent :** `AccountService` force `ROLE_MEMBER` à l’acceptation et au verify. Il faut `invite.getRole()`.

**UI :** select rôle à l’invite ; menu rôle par ligne ; clés si `API_KEY_READ` ; vérifs masquées si `developer` ; facturation owner only.

**Audit :** `membership.role_changed` payload `{ "from":"member","to":"admin" }` — pas d’e-mail.

**Tests :** readonly 403 `POST /verifications` ; developer 403 GET vérif et 201 create key ; admin invite, member 403 ; admin ne PATCH pas vers `owner` ; dernier owner rétrogradé 409 ; cookie : rôle **immédiat**.

**Démo :** owner invite un developer et un readonly.  
**Kill :** developer qui lit une fiche applicant ; rôle cookie ≠ table.

---

### T3 — Statut actif / inactif

**Durée :** 1 semaine.  
**Prérequis :** T2.

**Livrable :** un admin bloque un compte → logout immédiat ; réactivation possible ; login d’un inactif = **403** `membership_disabled`.

**Statut :** livré (8 septembre 2026).

| In | Out |
|---|---|
| `memberships.status` `active` \| `disabled` | Soft-delete user global |
| Disable / enable + `deleteByUserId` | Reset MFA |
| Login refuse les disabled | Purge RGPD du user |

**Schéma**

```sql
ALTER TABLE memberships
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'active',
  ADD COLUMN disabled_at DATETIME(6) NULL,
  ADD COLUMN disabled_by_user_id CHAR(36) NULL;
```

**API :** `POST .../members/{id}/disable` | `enable`. GET `/team` liste actifs **et** inactifs.

**UI :** badge Actif / Inactif ; interdiction de se bloquer soi-même ; interdiction de bloquer le dernier owner.

**Démo :** bloquer un member connecté ailleurs → il tombe au prochain clic.  
**Kill :** inactif encore dans la console ; owner qui se bloque et verrouille l’org.

---

### T4 — Journal d’activité

**Durée :** 1 semaine.  
**Prérequis :** T3.

**Livrable :** owner/admin voient qui a invité, changé un rôle, émis une clé, bloqué un membre. Filtre par action. Pas d’e-mail dans le payload.

**Statut :** livré (8 septembre 2026).

| In | Out |
|---|---|
| `GET /v1/console/audit` paginé | Export CSV |
| Filtre `action`, curseur | Events hors org |
| UI `/settings/activity` | Payload avec e-mail / nom |

Droit : `AUDIT_READ` = admin + owner (pas readonly).

Actions à garantir : celles de T1–T3 + `api_key.issued`, `api_key.revoked`, `user.login_failed`.

**Kill :** PII dans `payload` ; member qui lit l’audit.

---

## 7. Hors cycle (volontairement)

- SSO / SAML, méthode d’auth à l’invite
- 2FA et reset MFA par admin
- SCIM
- Multi-org + switcher
- Groupes custom (Veriff Advanced Permissions)
- Prénom / nom à l’invite (colonnes user déjà là — cosmétique, pas un sprint)
- Plusieurs owners sans transfert explicite

---

## 8. Ordre interne (ne pas inverser)

1. Relecture du rôle à chaque requête + `deleteByUserId`
2. Resend / cancel / remove + dernier owner
3. Enum permissions, puis nouveaux rôles
4. `invite.getRole()` à l’acceptation
5. Disable / enable
6. Liste audit console
7. Brancher M3 sur `BILLING_*`
