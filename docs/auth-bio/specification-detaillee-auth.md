# Spécification détaillée — Authentification biométrique

**Produit :** `authentication` · **Moteur :** `auth-2` · **Région :** `us-east-1`  
**Version :** 1.2 · **Date :** 24 novembre 2026  
**Document qui prime :** [`cahier-des-charges-implementation-auth.md`](./cahier-des-charges-implementation-auth.md)  

Une organisation a déjà prouvé une identité. Plus tard, elle vérifie que **c’est encore cette personne** : 1:1 contre la référence figée sous (`organization`, `external_id`). Pas de pièce, pas de 1:N, pas de webhook, pas de bac à sable, pas de `ky_test_`, pas de JWT Recogniz-Me. Seul `approved` ouvre l’accès chez le client.

| Statut | Sens |
|---|---|
| `approved` | Présence ≥ 0,80 et visage = `FaceId` figé, score au seuil d’approbation. |
| `declined` | Refus motivé. Le compte reste fermé. |
| `review` | Bande grise. Un humain doit trancher. **Pas un accès.** |
| `failed` | Panne fournisseur. Pas un refus. Pas de débit. |
| `expired` / `cancelled` | Lien périmé ou annulé. Rien n’a été comparé. |

Clé : Bearer `ky_live_` du produit `authentication`. Autre produit, clé absente, révoquée ou `ky_test_` → `401`. Hors périmètre → `404` (jamais `403` entre organisations).

---

## 1. Cas d’utilisation

| ID | Cas | Acteur | Préconditions | Résultat |
|---|---|---|---|---|
| CU-01 | Enrôler depuis l’identité | Produit Identité | Vérification **live** `approved`, option active, `external_id` présent, présence et correspondance pièce réussies | Fiche `active` + `FaceId`. Sinon rien, et `IndexFaces` n’est pas appelé. L’identité déjà tranchée ne change pas. |
| CU-02 | Remplacer la référence | Produit Identité | CU-01 pour un couple qui a déjà un `active` | `DeleteFaces` de l’ancien, ancienne fiche `retired`, puis seulement la nouvelle fiche `active`. |
| CU-03 | Lire un enrôlement | Backend client | Clé `ky_live_` | `GET /v1/enrollments?external_id=` → `active`, sinon dernière fiche `revoked`/`retired`, sinon `absent` (`200`). Autre organisation → `404`. |
| CU-04 | Révoquer | Client ou console (pas le développeur) | Fiche `active` | `DeleteFaces` d’abord, puis `revoked`. Second appel : même fiche. Fiche `retired` → `409` `invalid_status`. |
| CU-05 | Ouvrir une session | Backend client | Enrôlement `active`, solde, `Idempotency-Key` | `POST /v1/authentications` fige `enrollment_id`, `face_id`, politique, `auth-2`. Répond `hosted_url`. Pas d’appel Rekognition. |
| CU-06 | Consentir et passer le défi | Personne (jeton d’URL) | Session non terminale, lien valide | Consentement `auth-consent-1`, puis Face Liveness. La clé secrète ne va pas au navigateur. |
| CU-07 | Trancher | Système | Défi terminé ou consentement refusé ou plafond | Le corps de clôture porte `status`, `reasons`, et `match_score` s’il y a eu comparaison. `GET` relit le même dossier. |
| CU-08 | Revoir une bande grise | Analyste (membre, admin, propriétaire) | Session `review` | `POST …/review` `{decision: approved\|declined}`. Le score ne change pas. Relecture par `GET`. |
| CU-09 | Annuler | Backend client | Statut non terminal | `POST …/cancel`. Lien révoqué. Déjà terminal → `invalid_status`. |
| CU-10 | Consulter | Backend ou console | Périmètre de l’intégration (session) ou de l’organisation (enrôlement) | `GET` unitaire, liste (`status`, `external_id`), média signé `selfie` ou `reference`. |
| CU-11 | Publier la politique | Propriétaire ou admin | Seuils dans les bornes du §3 | Nouvelle version. Les sessions déjà ouvertes gardent la leur. |

`POST /v1/enrollments` répond `409` `enrollment_requires_idv`. Pas d’écriture manuelle, pas d’enrôlement fictif.

**Création de session**

```http
POST /v1/authentications
Authorization: Bearer ky_live_…
Idempotency-Key: <obligatoire>
{ "external_id": "user-123", "metadata": { } }
```

| Condition | Issue |
|---|---|
| `external_id` absent | `400` `required`. Pas de dossier. |
| `sandbox_scenario`, metadata trop gros, intégration `test` | `400` `invalid`. Aucune `ky_test_` n’est émise. |
| Pas d’`active` | `409` `no_enrollment`. Pas de dossier. Ne dit pas si absent ou révoqué. |
| Même clé d’idempotence, autre corps | `409` `idempotency_key_conflict` |
| Même clé, même corps | Même session |
| Sans solde | `403` `insufficient_credit`. Pas de dossier. |

Réponse : `id`, `integration_id`, `integration_mode=live`, `enrollment_id`, `status=created`, `hosted_url`, `expires_at` (1 h), `policy_version`, `engine=auth-2`. Jamais `face_id`.

**Clôture `approved`**

```json
{
  "status": "approved",
  "reasons": ["liveness_pass", "face_match_pass"],
  "match_score": "0.960",
  "failure_reason": null,
  "engine": "auth-2",
  "policy_version": "auth-policy-0",
  "enrollment_id": "…",
  "external_id": "user-123",
  "decided_at": "2026-11-22T17:04:11Z"
}
```

Le code HTTP ne vaut pas une décision. Un `200` avec `failed` n’ouvre pas l’accès. L’écran utilisateur ne montre ni score, ni code, ni image.

---

## 2. Workflow

### 2.1 Session

```
création (active exigé) → created
  ├─ lien expiré ──────────────► expired
  ├─ client annule ────────────► cancelled
  └─ ouverture du lien ────────► pending_consent
        ├─ refuse ─────────────► declined (consent_refused)
        └─ accepte ────────────► selfie
              ├─ qualité / liveness < 0,80 ► recapture_requested (même session)
              └─ présence OK ──► processing
                    ├─ fournisseur en échec ► failed
                    ├─ fiche plus active ───► declined (enrollment_inactive)
                    ├─ approved
                    ├─ declined
                    └─ review ──► analyste ──► approved ou declined
```

Terminaux : `approved`, `declined`, `failed`, `expired`, `cancelled`. `review` n’est pas un accès. Recapture = même dossier, dans le plafond, sans second débit.

### 2.2 Parcours hébergé

```
POST /v1/authentications
        │  fige enrollment_id, face_id, politique, auth-2
        ▼
lien /flow/{token}          (1 h, pas de régénération)
        ▼
consentement auth-consent-1
        ├─ refuse ──────────► declined, aucune image, décision dans cet appel
        ▼
Face Liveness (FaceMovementAndLightChallenge)
        ├─ flou, sombre, trop petit, 0 ou plusieurs visages ► recapture, pas de recherche
        ├─ défi incomplet ou confiance < 0,80             ► recapture, pas de recherche
        ├─ attaque de présentation                        ► declined presentation_attack
        ├─ plafond (défaut 3, borné 1–3)                  ► declined attempt_limit
        ▼
SearchFacesByImage sur l’image GetFaceLivenessSessionResults (mémoire seule)
        ▼
corps de clôture = décision          GET relit le même dossier
```

Le navigateur n’a que `rekognition:StartFaceLivenessSession`. Jamais Index, Search ou Delete. Un JPEG uploadé à part ne part pas en recherche. Le parcours n’affiche ni nom, ni `external_id`, ni organisation.

Texte `auth-consent-1` : le visage est comparé à la référence déjà enregistrée, pour confirmer que c’est bien la personne qui demande l’accès, pour le compte de l’entreprise qui a envoyé le lien. Français par défaut, anglais si le navigateur le demande. Même décision renvoyée : sans effet. Décision contraire ensuite : `invalid_status`.

### 2.3 Décision (`auth-2`)

Une fois l’image acceptée, dans cet ordre :

1. Fiche figée plus `active` → `declined` `enrollment_inactive`. Pas de recherche, pas de débit.
2. Attaque de présentation → `declined` `presentation_attack`. Pas de score.
3. `SearchFacesByImage`, seuil Rekognition = seuil de revue × 100. On ne garde que le `FaceId` **exactement** figé.
4. Autre `FaceId`, ou aucun, ou score &lt; 0,80 → `declined` `face_match_fail`. La réponse ne nomme personne.
5. Score sous le seuil d’approbation → `declined` `face_match_fail`.
6. Score ≥ seuil d’approbation **et** présence réussie → `approved`, raisons `liveness_pass` et `face_match_pass`.

Les deux portes sont exigées. Panne, délai, collection ou visage introuvable → `failed` (`provider_timeout`, `provider_unavailable`, `provider_invalid_response`). Jamais `approved`.

Le score est la similarité Rekognition (0–100) / 100, trois décimales, arrondi au demi-supérieur. Ce n’est pas une probabilité. Le client ne réapplique pas son propre seuil.

### 2.4 Enrôlement

1. Lire l’image de référence du liveness **de cette** vérification.
2. `ensureCollection` (une collection par organisation ; nom opaque, pas le nom de l’org ni un `external_id`).
3. `IndexFaces`. Pas exactement un visage → pas de fiche `active`. Journal sans octets ni `external_id`.
4. S’il existe un `active` : `DeleteFaces`, puis `retired`. La nouvelle fiche n’est publiée qu’ensuite.
5. Échec de `DeleteFaces` : ne pas déclarer `retired` / `revoked` tant que le vecteur est cherchable. Réessayer. Jamais deux `active`.

`ExternalImageId` = `enrollment.id`. Activer l’option plus tard ne rejoue pas les identités déjà approuvées.

---

## 3. Règles de gestion

### 3.1 Politique `auth-policy-0`

Une politique par organisation, versionnée, copiée sur la session. `auth-policy-0` s’applique si rien n’est publié. La politique d’identité est un autre objet.

| Paramètre | Défaut | Publier |
|---|---|---|
| Seuil d’approbation | 0,90 | De 0,90 à 1,00 inclus. En dessous, la session est refusée. |
| Essais | 3 | 1, 2 ou 3. Non retirable. |
| Présence | exigée, 0,80 | Non désactivable. |

Abaisser un seuil ou désactiver la présence → `invalid`.

### 3.2 Raisons

Codes stables, anglais dans l’API. Aucune raison ne porte de donnée personnelle. `failed` a `failure_reason`, pas une raison de ce tableau.

| Code | Statut |
|---|---|
| `consent_refused` | `declined` — aucune image |
| `presentation_attack` | `declined` |
| `face_match_fail` | `declined` — sous la revue, ou autre `FaceId` |
| `face_match_borderline` | `declined` | Ancien code de bande grise. Les nouvelles décisions sous le seuil d’approbation portent `face_match_fail`. |
| `attempt_limit` | `declined` |
| `enrollment_inactive` | `declined` — pas de comparaison |
| `liveness_pass` + `face_match_pass` | `approved` |
| `analyst_approved` / `analyst_declined` | tranche la revue ; les raisons machine restent |

### 3.3 Crédit

Une unité `AUTHENTICATION` de **0,10 $** **à la fin** de la session live exécutée, pas à la création, pour `approved`, `review`, ou `declined` de présence, score ou plafond.

Aucun débit : `consent_refused`, `enrollment_inactive`, `failed`, `expired`, `cancelled`, recapture, revue, rejeu d’idempotence. Ce n’est pas une unité d’identité.

### 3.4 Isolation, conservation, audit

| Objet | Périmètre |
|---|---|
| Enrôlement, collection | Organisation |
| Session | Intégration qui l’a créée. Autre intégration → `404` |
| Navigateur | Jeton d’URL seulement |

| Donnée | Durée |
|---|---|
| `FaceId`, vecteur | Vie de la fiche `active`. `DeleteFaces` dès `retired` ou `revoked`. Pas de copie du vecteur. |
| Photo d’enrôlement | Média du dossier d’identité. Pas de second fichier. Rien sous `templates/`. |
| Selfie de session | Stockage `kyc/`, vie du dossier (revue et audit). |
| Journaux | `authentication_id`, `enrollment_id`, `integration_id`, statut, raisons, durée, statut fournisseur. Pas d’image, de vecteur, de `FaceId`, ni d’`external_id`. |

Audité : `enrollment.created|retired|revoked`, `authentication.created|decided|failed|reviewed|expired|cancelled`, `authentication_policy.published`. Supprimer l’organisation emporte sessions, médias, enrôlements, puis la collection.

| Rôle | Lire | Revue | Révoquer | Politique | Intégration |
|---|---|---|---|---|---|
| Propriétaire, admin | oui | oui | oui | oui | oui |
| Membre | oui | oui | oui | non | non |
| Développeur | oui | non | non | non | oui |
| Lecture seule | oui | non | non | non | non |

L’analyste voit selfie, référence, score, raisons, politique. Il ne crée pas de référence et ne révoque pas en approuvant.

### 3.5 Règles invariantes

| Id | Règle |
|---|---|
| RG-01 | Question 1:1 contre l’`enrollment_id` / `FaceId` figé. Jamais « qui est ce visage ». |
| RG-02 | Seul `approved` ouvre l’accès. `review` ne l’ouvre pas. |
| RG-03 | La décision est le corps de clôture. Aucun webhook. |
| RG-04 | Le client lit `status`, pas le code HTTP. |
| RG-05 | `failed` n’est pas un `declined`. Pas de débit. Jamais `approved`. |
| RG-06 | Production uniquement. Pas de `ky_test_`, pas de parcours test, pas de scénario. Intégration `test` → `invalid`, zéro clé. |
| RG-07 | Moteur `auth-2` seulement. Pas de `auth-1`. `CompareFaces` reste à l’identité. |
| RG-08 | `SearchFacesByImage` seulement si présence ≥ 0,80 et fiche figée encore `active`. |
| RG-09 | Autre `FaceId` = `face_match_fail`, même au-dessus de 0,90. La réponse ne le nomme pas. |
| RG-10 | `ExternalImageId` = id de fiche, jamais l’`external_id` client. |
| RG-11 | Au plus un `active` par (organisation, `external_id`). `DeleteFaces` avant le nouveau statut. Pas de statut retiré tant que le vecteur est cherchable. |
| RG-12 | Pas exactement un visage indexable → pas de fiche `active`. L’identité déjà tranchée ne change pas. |
| RG-13 | Enrôlement seulement depuis une identité live `approved` avec l’option. `POST /v1/enrollments` → `enrollment_requires_idv`. |
| RG-14 | La session fige `engine`, `policy_version`, `enrollment_id`, `face_id`. Un enrôlement plus récent ne remplace pas une session ouverte. |
| RG-15 | L’organisation peut durcir les seuils. Elle ne peut ni les abaisser ni couper la présence. |
| RG-16 | Pas de JWT, cookie ou session applicative signés par Recogniz-Me. Une authentification ne réécrit pas la vérification source. |
| RG-17 | Journaux sans image, vecteur, `FaceId`, `external_id`. Le navigateur n’a que `StartFaceLivenessSession`. |
| RG-18 | Score affiché = score comparé : trois décimales, demi-supérieur. |
| RG-19 | Recapture, revue, replay : pas de second débit. |
| RG-20 | Org B sur un id de A → `404`. Clé d’un autre produit ou `ky_test_` → `401`. |
| RG-21 | Le faux client Rekognition sert aux tests d’ingénierie. Ce n’est pas un environnement client. |

### 3.6 Erreurs

| Code | HTTP | Quand |
|---|---|---|
| `required` | 400 | `external_id` absent |
| `invalid` | 400 | `sandbox_scenario`, seuil illégal, metadata trop gros, revue inconnue, intégration `test` |
| `no_enrollment` | 409 | Création sans `active` |
| `enrollment_requires_idv` | 409 | `POST /v1/enrollments` |
| `idempotency_key_conflict` | 409 | Même clé, autre corps |
| `invalid_status` | 409 | Annulation, revue ou révocation sur un statut qui ne l’accepte pas |
| `insufficient_credit` | 403 | Sans solde |
| — | 404 | Inconnu dans le périmètre |
| — | 401 | Clé absente, révoquée, `ky_test_`, ou d’un autre produit |

Hors produit : webhook `authentication.decided` / `authentication.reviewed`, route 1:N, JWT, enrôlement par selfie seul, moteur `auth-1`, liveness passif, signaux d’appareil.

---

## 4. Modèle de données

Chaque ligne porte `organization_id`, sauf les tables filles qui le tiennent par leur session. Les valeurs d’énumération sont stockées par leur nom ; l’API les rend en minuscules.

### 4.1 Entités

#### `enrollments`

Référence biométrique d’une personne, le couple (organisation, `external_id`). `ExternalImageId` Rekognition = `id`. Au plus une fiche `active` à la fois.

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | uuid | PK. |
| `organization_id` | uuid | NOT NULL. FK `organizations`. |
| `external_id` | varchar(128) | NOT NULL. Opaque, 1 à 128 caractères. |
| `status` | `EnrollmentStatus` | NOT NULL. |
| `source` | `EnrollmentSource` | NOT NULL. |
| `source_verification_id` | uuid | NOT NULL. Vérification d’identité live source. |
| `face_id` | varchar(128) | NULL. Obligatoire si `active`. Jamais renvoyé, jamais journalisé. |
| `collection_id` | varchar(128) | NULL. Obligatoire si `active`. |
| `active_marker` | varchar(200) | NULL. `organization_id:external_id` tant que `active`, sinon NULL. UNIQUE. |
| `created_at` | timestamp | NOT NULL. UTC. |
| `ended_at` | timestamp | NULL. Posé dès la sortie de `active`. |

Index : (`organization_id`, `external_id`, `created_at`).

#### `authentications`

Session d’authentification. À la création elle fige l’enrôlement, le `FaceId`, le moteur et la politique. Elle appartient à l’intégration qui l’a créée.

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | uuid | PK. |
| `organization_id` | uuid | NOT NULL. FK `organizations`. |
| `integration_id` | uuid | NOT NULL. FK `integrations`. Périmètre de lecture. |
| `external_id` | varchar(128) | NOT NULL. Pour le GET client. Pas dans les journaux techniques. |
| `enrollment_id` | uuid | NOT NULL. FK `enrollments`. Figé, jamais réécrit. |
| `face_id` | varchar(128) | NOT NULL. Copie figée à la création. Jamais renvoyé, jamais journalisé. |
| `engine` | varchar(16) | NOT NULL. Figé. Valeur `auth-2`. |
| `policy_version` | varchar(32) | NOT NULL. Figé. Défaut `auth-policy-0`. |
| `status` | `AuthenticationStatus` | NOT NULL. |
| `reasons` | varchar(256) | NULL. JSON des codes de raison. |
| `failure_reason` | varchar(64) | NULL. Seulement si `failed`. |
| `match_score` | decimal(4,3) | NULL. Seulement si le `FaceId` figé a été comparé. |
| `metadata` | text | NULL. JSON borné, rendu tel quel. Pas de secret. |
| `consent_version` | varchar(32) | NULL. Posé avant toute image. |
| `consent_at` | timestamp | NULL. |
| `hosted_token_hash` | varchar(64) | NOT NULL. Hash du jeton de lien, pas le jeton. |
| `expires_at` | timestamp | NOT NULL. Défaut création + 1 h. |
| `attempts` | int | NOT NULL. Défaut 0. Plafonné par la politique figée. |
| `created_at` | timestamp | NOT NULL. |
| `decided_at` | timestamp | NULL. Posé dès un statut terminal ou `review`. |

Index : (`integration_id`, `created_at`, `id`), (`organization_id`, `external_id`).

#### `authentication_policies`

Une version publiée des seuils. `auth-policy-0` est le défaut d’usine : aucune ligne n’est obligatoire. La présence réelle n’est pas une colonne.

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | uuid | PK. |
| `organization_id` | uuid | NOT NULL. FK `organizations`. |
| `integration_id` | uuid | NULL. Intégration concernée. |
| `version` | int | NOT NULL. UNIQUE avec `integration_id`. |
| `active` | boolean | NOT NULL. Une seule version active par intégration. |
| `active_marker` | varchar(36) | NULL. `integration_id` tant que `active`, sinon NULL. UNIQUE. |
| `approval_threshold` | decimal(4,3) | NOT NULL. De 0,900 à 0,980. |
| `review_threshold` | decimal(4,3) | NOT NULL. De 0,800 à strictement moins que l’approbation. |
| `on_borderline` | `AuthOnBorderline` | NOT NULL. |
| `max_capture_attempts` | int | NOT NULL. 1, 2 ou 3. |
| `published_at` | timestamp | NOT NULL. |
| `published_by` | uuid | NOT NULL. |

#### `authentication_idempotency_keys`

Rejeu de `POST /v1/authentications`. Même clé et même hash renvoient la même session ; même clé et autre hash → `idempotency_key_conflict`. Conservation 24 h.

| Colonne | Type | Contraintes |
|---|---|---|
| `organization_id` | uuid | PK. |
| `idempotency_key` | varchar(64) | PK. |
| `request_hash` | varchar(64) | NOT NULL. Hash du corps. |
| `authentication_id` | uuid | NOT NULL. Session créée. |
| `created_at` | timestamp | NOT NULL. |

#### `authentication_liveness_checks`

Un essai Face Liveness d’une session. L’image comparée n’est pas stockée ici : elle reste celle renvoyée par le fournisseur, puis le selfie d’audit.

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | uuid | PK. |
| `authentication_id` | uuid | NOT NULL. FK `authentications`. UNIQUE avec `attempt`. |
| `attempt` | int | NOT NULL. |
| `session_id` | varchar(64) | NOT NULL. Session Rekognition. |
| `region` | varchar(32) | NOT NULL. `us-east-1`. |
| `expires_at` | timestamp | NOT NULL. |
| `status` | `LivenessCheckStatus` | NOT NULL. |
| `confidence` | double | NULL. Posée à la complétion. |
| `created_at` | timestamp | NOT NULL. |

Index : (`authentication_id`, `status`).

#### `authentication_media`

Selfie de la session, préfixe de stockage `kyc/`. La référence lue par l’API est le média d’identité source, pas une copie dans cette table. Session sans image : `404` sur le média, dossier toujours lisible. Le vecteur ne sort pas.

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | uuid | PK. |
| `authentication_id` | uuid | NOT NULL. FK `authentications`. |
| `kind` | `MediaKind` | NOT NULL. Ce produit n’écrit que `selfie`. |
| `attempt` | int | NOT NULL. |
| `object_key` | varchar(512) | NOT NULL. |
| `content_type` | varchar(128) | NULL. Posé à l’acceptation. |
| `byte_size` | bigint | NULL. Posé à l’acceptation. |
| `status` | `MediaStatus` | NOT NULL. |
| `created_at` | timestamp | NOT NULL. |

Index : (`authentication_id`, `kind`, `status`, `attempt`).

### 4.2 Énumérations

#### `EnrollmentStatus`

Cycle de la fiche. `retired` est un remplacement. `revoked` est un acte explicite et ne se défait pas.

| Valeur | Sens |
|---|---|
| `active` | Référence cherchable. Une session déjà ouverte la compare encore. Une nouvelle session peut s’y attacher. |
| `retired` | Remplacée. Session déjà ouverte → `enrollment_inactive`, sans recherche. Une nouvelle session prend le nouvel `active` s’il existe. |
| `revoked` | Retirée. Même effet sur une session ouverte. Aucune nouvelle session tant qu’un enrôlement n’est pas réécrit. |

#### `EnrollmentSource`

Origine de la référence.

| Valeur | Sens |
|---|---|
| `idv` | Identité live `approved`, seule source admise. |

#### `AuthenticationStatus`

Cycle de la session. Terminaux : `approved`, `declined`, `failed`, `expired`, `cancelled`. `review` n’est pas un accès.

| Valeur | Sens |
|---|---|
| `created` | Dossier ouvert, lien émis. Aucune comparaison. |
| `pending_consent` | Lien ouvert, consentement pas encore répondu. |
| `selfie` | Consentement accepté. Le défi peut commencer. |
| `recapture_requested` | Qualité ou présence insuffisante. Même session, pas de recherche. |
| `processing` | Présence acceptée, décision en cours. |
| `approved` | Présence et `FaceId` figé au seuil. Seul statut qui ouvre l’accès. |
| `declined` | Refus motivé. |
| `review` | Bande grise. En attente d’un analyste. |
| `failed` | Panne fournisseur. Pas un refus. |
| `expired` | Lien périmé avant décision. |
| `cancelled` | Annulé par le client. |

#### `AuthOnBorderline`

Conduite d’un score entre le seuil de revue et le seuil d’approbation.

| Valeur | Sens |
|---|---|
| `review` | La session passe `review`. Défaut. |
| `decline` | La session passe `declined` `face_match_borderline`. |

#### `LivenessCheckStatus`

État d’un essai de présence.

| Valeur | Sens |
|---|---|
| `open` | Session Rekognition créée, résultat pas encore lu. |
| `completed` | Résultat lu. `confidence` est posée. |
| `expired` | L’essai n’est plus utilisable. |

#### `MediaKind`

Partagée avec l’identité. Ce produit n’écrit que `selfie`.

| Valeur | Sens |
|---|---|
| `selfie` | Image d’audit de la session. |
| `document` | Pièce d’identité. Hors de ce produit. |
| `document_back` | Verso de pièce. Hors de ce produit. |

#### `MediaStatus`

État du fichier stocké.

| Valeur | Sens |
|---|---|
| `pending` | Objet réservé, pas encore accepté. |
| `accepted` | Fichier reçu. `content_type` et `byte_size` sont posés. |
| `rejected_quality` | Rejeté par les mesures de qualité. |
