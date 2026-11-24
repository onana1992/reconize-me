# Cahier des charges — Authentification biométrique

**Produit :** Recogniz-Me, Biometric Authentication  
**Code produit :** `authentication`  
**Version du document :** 1.1  
**Date :** 22 novembre 2026  
**Statut :** spécification produit — contrat d’authentification  
**Moteur :** `auth-2` (production uniquement)  
**Documents liés :**

- [`../auth-bio/cahier-des-charges-implementation-auth.md`](../auth-bio/cahier-des-charges-implementation-auth.md) — la livraison technique (collection, enrôlement, clôture sans webhook)
- [`../auth-bio/specification-detaillee-auth.md`](../auth-bio/specification-detaillee-auth.md) — le contrat d’implémentation expliqué (acteurs, flux, API, données, règles)
- [`../auth-bio/roadmap-implementation-auth.md`](../auth-bio/roadmap-implementation-auth.md) — l’ordre de construction (paliers C0–C9)
- [`../specs/specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md) — la biométrie d’onboarding lie un visage à une pièce ; elle ne réauthentifie pas
- [`../specs/cahier-des-charges-integration.md`](../specs/cahier-des-charges-integration.md) — clé, mode, crédit. Le webhook et le bac à sable de ce module **ne s’appliquent pas** à ce produit
- [`../specs/specification-regles-idv.md`](../specs/specification-regles-idv.md) — la politique d’identité ; l’enrôlement n’y est pas un paramètre libre
- [`../aml/cahier-des-charges-aml.md`](../aml/cahier-des-charges-aml.md) — l’autre produit de la gamme, même discipline de décision

> **Note (v1.1).** Ce contrat produit tient, avec deux corrections de livraison déjà figées dans le [cahier d’implémentation v1.2](../auth-bio/cahier-des-charges-implementation-auth.md) et la [spécification détaillée](../auth-bio/specification-detaillee-auth.md) : **pas de bac à sable** (production uniquement) et **pas de webhook** (la décision est dans la réponse de clôture ; `GET` relit).

Ce document fixe le produit qui reconnaît une personne **déjà enrôlée**. L’organisation ouvre une session, l’utilisateur prend un selfie, la plateforme compare ce visage à la référence de cette personne et rend une décision motivée.

Le niveau visé est celui d’une réauthentification tenue en production : une approbation veut dire que ce visage, à cet instant, correspond à la référence active de la référence client annoncée. L’échec d’un fournisseur ne devient jamais une approbation. Une recherche dans une galerie de visages n’existe pas.

Une seule livraison : **production**, moteur `auth-2`, clé `ky_live_`. Pas d’intégration `test`, pas de clé test, **pas de parcours test**, pas de scénario déterministe, pas d’enrôlement fictif.

---

## 1. Objet

L’authentification biométrique répond à une question : la personne devant la caméra est-elle la personne déjà enrôlée sous cette référence client, dans cette organisation.

Le produit est un parcours court. Le serveur du client crée une session. L’utilisateur ouvre un lien hébergé, consent, se filme. La décision est le corps de la réponse qui termine le défi. `GET` la relit. Il n’y a pas de webhook.

Il se place **après** une identité déjà prouvée. Il ne relit pas de pièce, n’extrait pas d’état civil, ne crible pas un nom.

| Usage | Déclencheur |
|---|---|
| Réauthentification | `POST /v1/authentications` avec la clé live du produit et la référence client déjà enrôlée |
| Step-up | Le même appel, avec un `metadata` qui dit au client pourquoi (paiement, changement d’appareil, action sensible) |

Ouvrir ce produit pour prouver une identité la première fois reste hors contrat. Ce parcours est celui de l’[identité](../specs/specification-fonctionnelle-idv.md).

## 2. Promesse

| Engagement | Conséquence |
|---|---|
| Seule une session `approved` autorise l’accès | `review`, `declined`, `failed`, `expired` et `cancelled` laissent le compte fermé. Le client lit `status`. |
| La comparaison est 1:1 | Le visage est comparé à la référence désignée par `external_id`. Aucune recherche « qui est ce visage ». |
| La décision est un fait daté | `approved` décrit cet instant. Ce n’est pas un jeton réutilisable. Le client consomme la décision une fois, chez lui. |
| L’échec fournisseur se ferme | Délai dépassé, réponse illisible, moteur indisponible : la session est `failed`. Elle n’est pas `approved`, ni `declined`. |
| Production uniquement | Une seule intégration : `live`, clé `ky_live_`, moteur `auth-2`. **Aucune clé `ky_test_`**. **Pas de parcours test.** Pas de scénario, pas de second monde. |
| La clôture porte la décision, `GET` réconcilie | Le serveur du client lit la réponse qui termine le défi. S’il a seulement remis le lien, il relit tout de suite `GET /v1/authentications/{id}`. |

La plateforme ne crée pas la session du client (cookie, jeton d’application, session bancaire). Elle rend un dossier. L’ouverture du compte reste chez l’organisation. Recogniz-Me ne signe pas de JWT.

## 3. Périmètre

### 3.1 Dans le produit

- Enrôlement d’une référence par personne, par organisation — toujours en production.
- Création, lecture, liste et annulation d’une session, isolées par intégration live.
- Lien hébergé, consentement versionné, capture d’un selfie, plafond d’essais.
- Présence réelle, comparaison à la référence annoncée, décision motivée.
- Politique versionnée, figée sur la session à sa création.
- Révocation d’une référence. La suivante authentification ne la compare plus.
- Décision dans la réponse de clôture. Relecture par `GET`.
- Revue humaine des sessions en zone grise.
- Moteur `auth-2` : présence réelle et recherche dans la collection, même contrat public.

### 3.2 Hors produit

| Sujet | Sort |
|---|---|
| Bac à sable, `ky_test_`, parcours test / flow stub, scénarios `sandbox_scenario`, enrôlement fictif | **Hors produit.** Contrairement à Identité (parcours sandbox). Créer une intégration `test` → `invalid`. |
| Webhook `authentication.decided` ou `authentication.reviewed` | **Retiré.** La décision est déjà dans la réponse de clôture. |
| Moteur `auth-1` | N’existe pas. Une seule livraison : `auth-2`. |
| Preuve d’identité, lecture de pièce, extraction d’état civil | Produit Identité. |
| Enrôlement par un selfie seul, sans vérification d’identité approuvée | Une version ultérieure. La seule source est l’identité live approuvée. |
| Recherche 1:N, dédoublonnage de visages, liste noire de visages | Interdit. Le produit ne répond pas « qui est-ce ». |
| Jeton biométrique de longue durée, session cliente, mot de passe | Le client garde son propre cycle de session. |
| Liveness passif, signaux d’appareil, signaux de comportement | Hors moteur. Un changement de présence réelle est une version ultérieure. |
| Estimation d’âge, KYB, criblage AML | Autres produits, ou hors gamme. |
| Réécriture d’une décision d’identité | Une authentification ne modifie pas la vérification qui a enrôlé. |
| Marque blanche du parcours, SDK natif | Même report que le module Intégration. |

## 4. Principes

1. Le moteur propose un score et des signaux. La politique tranche. Un score élevé avec une présence réelle en échec ne donne pas `approved`.
2. La référence comparée est celle figée sur la session à sa création. Un enrôlement plus récent, survenu pendant que le lien est ouvert, ne remplace pas cette référence en cours de route.
3. Une référence qui n’est plus `active` au moment de la soumission (révoquée ou remplacée) ferme la session en `declined`, raison `enrollment_inactive`. On ne compare pas un visage à une référence retirée.
4. L’organisation peut durcir les seuils. Elle ne peut pas les abaisser sous le plancher plateforme, ni désactiver la présence réelle.
5. La session fige `engine`, `policy_version` et `enrollment_id`. Changer l’algorithme ou les bornes est une nouvelle version. Les dossiers déjà lus ne bougent pas.
6. Les journaux portent `authentication_id`, `integration_id`, `enrollment_id`, `status`, les codes de raison, la durée et le statut fournisseur. Ils ne portent ni image, ni vecteur, ni `FaceId`, ni nom, ni `external_id`.
7. Le code HTTP ne vaut pas une décision. Le client lit `status`.

## 5. Place dans la gamme

```
Personne (organisation, external_id) — toujours live
    │
    ├── Identité live approuvée + option d’enrôlement  ──écrit──►  référence active
    │
    └── Authentification  ──compare 1:1──►  cette référence
```

La biométrie de l’identité répond à une autre question : ce visage est-il celui de la pièce. Le score d’onboarding n’est pas relu ici. Ce produit refait une présence réelle et une comparaison, contre la référence enrôlée.

Règle de rattachement, côté identité, que ce contrat exige :

| Condition au moment où l’identité live devient `approved` | Effet |
|---|---|
| Option d’enrôlement active, `external_id` présent, selfie accepté, présence réelle et correspondance à la pièce réussies | La référence active de cette personne est remplacée par le selfie de cette vérification. L’ancienne passe à `retired`. |
| Option inactive, ou `external_id` absent | Aucune référence écrite. La décision d’identité ne change pas. |
| Décision autre que `approved` | Aucune référence écrite. |

Activer l’option plus tard ne rejoue pas les vérifications déjà approuvées. Remplacer une référence exige une nouvelle identité live approuvée.

## 6. Enrôlement

Un enrôlement est la référence active d’une personne. Une personne est le couple (organisation, `external_id`). Le mode est toujours `live`.

| Champ | Règle |
|---|---|
| `id` | UUID. Cité par chaque session qui s’y attache. |
| `organization_id` | Locataire. |
| `external_id` | Référence client, 1 à 128 caractères. Opaque. Unique parmi les enrôlements `active` de ce couple. |
| `status` | `active`, `retired`, `revoked`. |
| `source` | `idv` uniquement. |
| `source_verification_id` | Toujours renseigné. |
| `created_at` | Horodatage UTC. |
| `ended_at` | Renseigné quand le statut quitte `active`. |

Un seul `active` par couple. La session d’authentification copie `enrollment_id` à la création et ne le réécrit pas.

| Statut | Comparé par une session déjà ouverte | Peut recevoir une nouvelle session |
|---|---|---|
| `active` | Oui, tant qu’il l’est encore à la soumission | Oui |
| `retired` | Non. La session ouverte dessus finit `enrollment_inactive`. | Non. La nouvelle session s’attache au nouvel `active`, s’il existe. |
| `revoked` | Non. Même issue. | Non, tant qu’un nouvel enrôlement n’a pas été écrit. |

`retired` est le remplacement par une preuve plus récente. `revoked` est un acte explicite : compte clos, suspicion de prise de contrôle, demande de la personne. Les deux empêchent la comparaison. Seule une nouvelle preuve réenrôle. Une révocation ne se défait pas.

### 6.1 Écriture

Seul le produit Identité écrit, aux conditions du §5, sur une vérification **live** `approved`. `POST /v1/enrollments` répond `enrollment_requires_idv`. Il n’y a pas d’écriture manuelle ni d’enrôlement fictif.

### 6.2 Lecture et révocation

`GET /v1/enrollments?external_id=` répond dans l’organisation de la clé live.

| Situation | Corps |
|---|---|
| Référence active | `status = active`, `id`, `source`, `created_at` |
| Dernière référence retirée, aucune active | `status = revoked` ou `retired`, `id` de cette fiche, `ended_at` |
| Rien pour cette référence | `status = absent` |

`absent` est un `200`. L’identifiant inconnu d’une **fiche** (`GET /v1/enrollments/{id}` d’une autre organisation) reste un `404`.

`POST /v1/enrollments/{id}/revoke` passe `active` à `revoked`. Un second appel sur une fiche déjà `revoked` renvoie la même fiche. Révoquer une fiche `retired` répond `invalid_status`.

Les enrôlements sont lisibles par toutes les intégrations d’authentification live de l’organisation : le site et l’application s’appuient sur la même personne. Les **sessions** restent isolées par intégration.

## 7. Session

Créer une session ne lance pas la comparaison. Cela vérifie qu’une référence `active` existe, fige son identifiant et la politique, et rend une URL. Aucun appel fournisseur à la création. Cette réponse n’a pas encore de décision.

### 7.1 Cycle de vie

```
création (référence active exigée)
        │
        ▼
      créée
        │
        ├── lien expiré ──────────────► expirée
        ├── client annule ────────────► annulée
        │
        ▼
   consentement
        │
        ├── refuse ───────────────────► refusée (consent_refused)
        ▼
      selfie  ◄── recapture (qualité, défi incomplet)
        │
        ▼
    traitement
        │
        ├── fournisseur en échec ─────► failed
        ├── référence plus active ────► refusée (enrollment_inactive)
        │
        ├── approuvée
        ├── refusée
        └── revue ──► analyste ──► approuvée ou refusée
```

La recapture reste sur la même session, dans le plafond d’essais. Elle ne crée pas un second dossier et ne débite pas une seconde unité.

| Statut | Terminal | La personne peut entrer |
|---|---|---|
| `created` | non | non |
| `pending_consent` | non | non |
| `selfie` | non | non |
| `recapture_requested` | non | non |
| `processing` | non | non |
| `review` | non, jusqu’à la revue | non |
| `approved` | oui | oui, côté client, pour cette décision |
| `declined` | oui | non |
| `failed` | oui | non |
| `expired` | oui | non |
| `cancelled` | oui | non |

### 7.2 Création

```http
POST /v1/authentications
Authorization: Bearer ky_live_…
Idempotency-Key: login-user-123-2026-11-22T13:04:00Z
Content-Type: application/json

{
  "external_id": "user-123",
  "metadata": { "purpose": "step_up_payment" }
}
```

| Champ | Règle |
|---|---|
| `external_id` | Obligatoire. Désigne la personne. Absent → `required`. |
| `metadata` | Objet JSON borné, rendu tel quel au `GET`. Pas de secret dedans. |
| `sandbox_scenario` | N’existe pas pour ce produit. Champ présent → `invalid`. |

Clé : `ky_live_` uniquement. Une clé `ky_test_` présentée sur ces routes reçoit `401`.

`Idempotency-Key` est obligatoire. La même clé et le même corps renvoient la même session. Un autre corps avec la même clé répond `idempotency_key_conflict`.

Sans enrôlement `active` pour ce couple, la création répond `409` `no_enrollment` et n’ouvre pas de dossier. Le client envoie alors la personne vers l’identité, ou constate une révocation s’il a lu l’enrôlement avant.

### 7.3 Réponse

| Champ | Rôle |
|---|---|
| `id` | Identifiant du dossier. |
| `integration_id` | Intégration qui l’a créé. |
| `integration_mode` | Toujours `live`. |
| `enrollment_id` | Référence figée. |
| `status` | `created`. |
| `hosted_url` | URL à remettre à l’utilisateur. Jeton à durée de vie bornée. |
| `expires_at` | Au-delà, le lien n’ouvre plus le parcours. Défaut : une heure. |
| `policy_version` | Politique figée. |
| `engine` | `auth-2`. |

Le corps de création ne contient pas de selfie et pas de décision.

### 7.4 Lien

Mêmes règles que l’identité : un lien, une session, possession du jeton, pas de régénération automatique, TTL d’une heure. Inconnu → lien invalide. Périmé → `expired`.

Le parcours n’affiche pas le nom, l’adresse, la référence client, ni le nom de l’organisation.

### 7.5 Annulation

`POST /v1/authentications/{id}/cancel` annule tant que le statut n’est pas terminal. Le lien est révoqué. Une session déjà terminale répond `invalid_status`.

## 8. Parcours hébergé

La clé secrète reste sur le serveur du client. Le navigateur ne porte que le jeton du lien.

### 8.1 Consentement

Le consentement est enregistré avant toute image : décision, horodatage, version du texte. Version de ce contrat : `auth-consent-1`.

> En continuant, vous acceptez que votre visage soit comparé à la référence biométrique déjà enregistrée pour vous, afin de confirmer que c’est bien vous qui demandez l’accès, pour le compte de l’entreprise qui vous a envoyé ce lien.

| Décision | Effet |
|---|---|
| Accepte | Passage à `selfie`. La caméra peut s’ouvrir. |
| Refuse | `declined`, raison `consent_refused`. Aucune image conservée. |

Un second envoi avec la même décision est sans effet. Une décision contraire après coup répond `invalid_status`.

Langue : français par défaut, anglais si le navigateur le demande.

### 8.2 Capture

Un visage, un seul. Le serveur refait les mesures de qualité. Le client du parcours peut être contourné : une image illisible ne part pas au moteur de comparaison.

| Constat | Effet |
|---|---|
| Trop flou, trop sombre, trop petit, aucun visage, plusieurs visages | `recapture_requested`. Pas d’appel de comparaison. |
| Défi de présence incomplet | `recapture_requested`. |
| Attaque de présentation franche (photo, écran, rejeu) | `declined`, raison `presentation_attack`. Le score de comparaison n’est pas calculé. |
| Plafond d’essais atteint | `declined`, raison `attempt_limit`. |

Plafond par défaut : 3 essais de capture sur la session. L’organisation peut le fixer de 1 à 3. Elle ne peut pas le retirer.

La présence réelle est celle servie par la plateforme : même défi que l’identité (`FaceMovementAndLightChallenge`), seuil 0,80. Sous ce seuil, ou défi non abouti : pas de recherche. Les essais restants redemandent la capture.

### 8.3 Fin de parcours

L’utilisateur voit un écran de clôture sans le score, sans les codes internes, sans image. Le détail est pour le serveur du client et pour la console. Une URL de retour optionnelle, si l’intégration l’a configurée, renvoie au site du client après cet écran. Le paramètre d’URL ne porte que l’identifiant de session.

L’appel qui termine le défi répond dans le même corps : `status`, `reasons`, `match_score` s’il y a eu comparaison, `failure_reason` si `failed`. Le serveur du client lit cette réponse. S’il a seulement remis le lien hébergé, il relit tout de suite `GET /v1/authentications/{id}` : le dossier est déjà décidé.

## 9. Décision

Le statut terminal **est** la décision. Il n’y a pas un second champ d’issue.

| Statut | Sens | Qui le pose |
|---|---|---|
| `approved` | Présence réelle réussie, et score au seuil d’approbation, référence encore `active` | Moteur, ou analyste |
| `declined` | Refus motivé : consentement, présence, score sous la bande, plafond, référence retirée | Moteur, ou analyste |
| `review` | Score dans la bande grise, politique `on_borderline = review` | Moteur |
| `failed` | Le moteur n’a pas pu conclure | Plateforme |
| `expired` | Lien non mené à terme dans l’heure | Plateforme |
| `cancelled` | Le client a annulé | Client |

`failure_reason`, seulement si `failed` : `provider_timeout`, `provider_unavailable`, `provider_invalid_response`.

Ordre de calcul, une fois l’image acceptée et la référence encore `active` :

1. Présence réelle en échec franc → `declined` `presentation_attack`.
2. Score strictement sous le seuil de revue → `declined` `face_match_fail`.
3. Score dans la bande grise → `review` si la politique dit `review`, sinon `declined` `face_match_borderline`.
4. Score au seuil d’approbation et présence réelle réussie → `approved`.

Les deux portes sont exigées. Un score au-dessus du seuil avec une présence en échec reste un refus. Une présence réussie avec un visage différent reste un refus.

Le moteur ne retient un candidat que si son identifiant de visage est **exactement** celui figé sur la session. Un autre visage, même très ressemblant, est un `face_match_fail`. La réponse ne dit pas à qui il ressemble.

Après revue, l’analyste pose `approved` ou `declined`. Il ne modifie pas le score. La session quitte `review`. Une session qui n’est pas `review` répond `invalid_status` à la qualification. Le passage ultérieur à `approved` ou `declined` se relit par `GET`.

Le score est un nombre décimal entre 0 et 1, affiché à trois décimales, arrondi au demi supérieur. Ce n’est pas une probabilité. Le client qui réappliquerait son propre seuil sortirait du contrat : la politique déjà figée a tranché.

## 10. Politique

Une politique par organisation. Elle est publiée, versionnée, et copiée sur la session. Les dossiers déjà ouverts gardent leur version.

| Paramètre | Défaut | Ce que l’organisation peut publier |
|---|---|---|
| Seuil d’approbation | 0,90 | De 0,90 à 0,98 inclus. Jamais en dessous du plancher. |
| Seuil de revue | 0,80 | De 0,80 à strictement moins que le seuil d’approbation. Jamais en dessous du plancher. |
| `on_borderline` | `review` | `review` ou `decline`. |
| Essais de capture | 3 | 1, 2 ou 3. |
| Présence réelle | exigée | Non retirable. |

Ces bornes sont provisoires, à calibrer avant le premier live. Les changer est une nouvelle `policy_version` pour les sessions suivantes, et, si le plancher plateforme bouge, une nouvelle version de moteur. Les nombres ci-dessus sont ceux de `auth-policy-0`, appliqués tant que l’organisation n’a rien publié. L’exemple « 95 % » d’un schéma est une politique publiable, pas un nouveau plancher.

Publier une politique qui abaisserait un seuil sous le plancher, ou qui désactiverait la présence réelle, répond `invalid`.

La politique d’identité et la politique d’authentification sont deux objets. Durcir le face match d’onboarding ne durcit pas cette comparaison, et l’inverse non plus.

## 11. Raisons

Codes stables, en anglais dans l’API. La console les montre en français. Aucune raison ne contient de donnée personnelle.

| Code | Statut | Sens |
|---|---|---|
| `consent_refused` | `declined` | L’utilisateur a refusé. Aucune image. |
| `liveness_pass` | `approved` | Présence réelle acceptée. Accompagne `face_match_pass`. |
| `presentation_attack` | `declined` | Photo, écran ou rejeu. |
| `face_match_pass` | `approved` | Score au seuil d’approbation. |
| `face_match_fail` | `declined` | Score sous le seuil de revue, ou visage trouvé qui n’est pas la référence figée. |
| `face_match_borderline` | `review` ou `declined` | Bande grise. Le statut dépend de `on_borderline`. |
| `attempt_limit` | `declined` | Plafond d’essais de capture. |
| `enrollment_inactive` | `declined` | La référence figée n’était plus `active` à la soumission. Aucune comparaison. |
| `analyst_approved` | `approved` | L’analyste a tranché. Les raisons machine restent sur le dossier. |
| `analyst_declined` | `declined` | L’analyste a tranché. |

`failed` n’a pas de raison de ce tableau : il a `failure_reason`.

## 12. Pas de bac à sable, pas de clé test, pas de parcours test

Ce produit n’a **pas de bac à sable**, **pas de clé test**, et **pas de parcours test**.

- **Aucune clé `ky_test_` n’est jamais émise** pour `authentication`. Seul le préfixe `ky_live_` existe pour ce produit.
- Pas d’intégration `test`. Créer une intégration en mode `test` répond `invalid` : aucune intégration, aucune clé.
- Une `ky_test_` d’un autre produit (Identité, AML) sur ces routes → `401`.
- **Pas de parcours hébergé test** : pas de branche flow stub, pas de scénario qui court-circuite la caméra ou Rekognition. Le seul lien hébergé ouvre le défi réel.
- Pas de champ `sandbox_scenario`. Présent → `invalid`.
- Pas d’enrôlement fictif (`test_fixture`).
- Pas de moteur `auth-1`.
- Pas de dualité test / live pour la personne : le couple (organisation, `external_id`) est toujours live.

Les tests automatisés côté ingénierie (faux client Rekognition) restent un outil interne. Ce n’est ni un environnement client, ni une clé de test, ni un parcours utilisateur.

Pour les autres produits (Identité, AML), le bac à sable du module Intégration continue de s’appliquer. Pas ici.

## 13. Lecture du dossier

`GET /v1/authentications/{id}` renvoie le dossier de cette intégration.

| Champ | Présence |
|---|---|
| `id`, `integration_id`, `integration_mode`, `external_id` | Toujours |
| `enrollment_id`, `policy_version`, `engine` | Toujours (`engine` = `auth-2`, `integration_mode` = `live`) |
| `status`, `reasons`, `failure_reason` | `failure_reason` seulement si `failed` |
| `match_score` | Quand une comparaison a eu lieu |
| `metadata`, `created_at`, `decided_at` | `decided_at` quand un statut terminal est posé |
| `consent_version` | Après la réponse de l’utilisateur |

`GET /v1/authentications/{id}/media/{kind}` rend une URL signée à durée courte. `kind` vaut `selfie` ou `reference` (selfie de la vérification source tant qu’il existe). Le vecteur de la collection ne sort pas de l’API. Une session sans image (refus de consentement, expiration avant capture) répond `404` sur le média, le dossier lui-même restant lisible.

La liste `GET /v1/authentications` est filtrable par `status` et `external_id`, paginée, bornée à l’intégration de la clé.

Une clé d’un autre produit, une clé `ky_test_`, ou une clé révoquée, reçoit `401`. L’identifiant d’une session d’une autre intégration ou d’une autre organisation reçoit `404`.

## 14. Décision dans la réponse de clôture

Il n’y a **pas de webhook** pour ce produit. Les événements `authentication.decided` et `authentication.reviewed` sont **retirés**.

| Moment | Ce que lit le client |
|---|---|
| Fin du défi de présence | Corps de la réponse de clôture : `status`, `reasons`, `match_score` s’il y a eu comparaison, `failure_reason` si `failed` |
| Lien hébergé seulement, ou après revue | `GET /v1/authentications/{id}` — le dossier est déjà décidé |
| `expired`, `cancelled` | Relus par `GET`. Rien n’a été comparé |

Rien n’est poussé vers une URL du client. Le module Intégration décrit un webhook pour d’autres produits ; il ne s’applique pas ici.

Une revue répond `review` dans le corps de clôture. Le passage ultérieur à `approved` ou `declined` se relit par `GET`. Il n’y a pas d’événement `authentication.reviewed`.

## 15. Revue

Une session `review` est une zone grise, pas un accès provisoire. Tant que l’analyste n’a pas posé `approved`, le client laisse le compte fermé.

L’analyste de l’organisation voit le selfie de la session, la référence enrôlée, le score, les raisons, la version de politique. Il approuve ou refuse, avec un motif libre borné, sans donnée inutile. L’action est auditée : qui, quand, ancien statut, nouveau statut, identifiant de session.

Il ne voit pas les dossiers d’une autre organisation. Il ne crée pas une référence depuis cet écran. Il ne révoque pas l’enrôlement en approuvant ou en refusant : la révocation est un geste séparé.

`POST /v1/authentications/{id}/review` est le chemin serveur. La console reste le chemin nominal.

```json
{ "decision": "approved", "note": "Contrôle visuel conforme" }
```

`decision` vaut `approved` ou `declined`.

## 16. Console

Langue : français. Le service est exposé sur les intégrations **live** du produit.

| Écran | Contenu |
|---|---|
| Sessions | Date, statut, référence client, moteur. Filtre par statut. Liste vide : une session se crée par l’API, après un enrôlement. |
| Fiche session | Statut, raisons, score, politique, enrôlement lié, métadonnées. Selfie et référence par URL courte. Action de revue si `review`. |
| Enrôlements | Référence client, statut, source, date, vérification d’identité source. Action de révocation. |
| Règles | Politique effective, publication, historique. Paramètres du §10 seulement. |
| Intégrations | Clé live, produit `authentication`. L’installation décrit la création, le lien, la clôture, la lecture par `GET`. Pas de webhook, pas de mode test. |

| Rôle | Lire | Revue | Révoquer un enrôlement | Publier la politique | Gérer l’intégration |
|---|---|---|---|---|---|
| Propriétaire, administrateur | Oui | Oui | Oui | Oui | Oui |
| Membre | Oui | Oui | Oui | Non | Non |
| Développeur | Oui | Non | Non | Non | Oui |
| Lecture seule | Oui | Non | Non | Non | Non |

Le développeur branche l’API. Il ne tranche pas un visage et ne retire pas une référence. Le membre tranche et révoque. Il ne voit pas les secrets.

## 17. Données

Le client est responsable du traitement. Recogniz-Me traite le visage pour rendre cette décision, pour le compte de l’organisation.

| Donnée | Durée | Usage |
|---|---|---|
| Identifiant de visage et de collection | Vie de la fiche `active`. Effacés du fournisseur dès `retired` ou `revoked` | Comparaison 1:1 |
| Vecteur | Chez le fournisseur, jusqu’au `DeleteFaces`. Recogniz-Me n’en garde pas de copie | Recherche |
| Photo d’enrôlement | Déjà le média de la vérification d’identité source. Pas de second fichier | Source de l’indexation |
| Selfie de la session | Vie du dossier, comme les médias d’identité | Audit de cette décision, revue |
| Suppression de l’organisation | Emporte sessions, médias, enrôlements et collection | — |

Les images des clients ne servent pas à entraîner un modèle. Les journaux techniques n’en portent pas. L’audit porte l’acteur, l’action, les identifiants, l’ancien et le nouveau statut.

Actions auditées : `enrollment.created`, `enrollment.retired`, `enrollment.revoked`, `authentication.created`, `authentication.decided`, `authentication.failed`, `authentication.reviewed`, `authentication.expired`, `authentication.cancelled`, `authentication_policy.published`.

`external_id` figure dans le dossier parce que le client doit rapprocher la décision de son utilisateur. Il ne figure pas dans les journaux techniques.

## 18. Crédit

| Situation | Débit |
|---|---|
| Live, `approved`, `review`, ou `declined` pour présence, score ou plafond d’essais | Une unité du produit, à la décision, pas à la création |
| Live, `declined` pour `consent_refused` ou `enrollment_inactive` | Aucun. Aucune comparaison. |
| Live, `failed`, `expired`, `cancelled` | Aucun |
| Recapture, revue, rejeu d’idempotence | Aucun de plus |
| Live sans solde | `403` `insufficient_credit`, aucun dossier |

## 19. Erreurs

Les codes sont stables. Le client branche dessus, pas sur le texte.

| Code | HTTP | Quand |
|---|---|---|
| `required` | 400 | `external_id` absent. |
| `invalid` | 400 | `sandbox_scenario` présent, intégration `test` demandée, seuil illégal, `metadata` trop gros, décision de revue inconnue. |
| `no_enrollment` | 409 | Création sans référence `active` pour ce couple. |
| `enrollment_requires_idv` | 409 | `POST /v1/enrollments` (écriture manuelle refusée). |
| `idempotency_key_conflict` | 409 | Même clé d’idempotence, autre corps. |
| `invalid_status` | 409 | Annulation ou revue sur un statut qui ne l’accepte pas. Révocation d’une fiche `retired`. |
| `insufficient_credit` | 403 | Live sans solde. |
| Absence de dossier ou d’enrôlement dans le périmètre de la clé | 404 | Identifiant inconnu ici. |
| Clé absente, révoquée, `ky_test_`, ou d’un autre produit | 401 | — |

`no_enrollment` ne crée pas de session. Le client distingue « pas encore enrôlé » et « révoqué » en lisant `GET /v1/enrollments?external_id=` avant, s’il a besoin du motif. La création, elle, ne dit que l’absence d’une référence utilisable.

## 20. Mise en production

Liste que l’équipe client coche avant une authentification de personnes réelles.

1. Une intégration `live` existe pour le produit `authentication`. Le solde couvre au moins une session. Le moteur est `auth-2`.
2. La clé `ky_live_` est sur le serveur du client. Elle n’est pas dans le navigateur de l’utilisateur.
3. L’option d’enrôlement de l’identité est active. Les personnes concernées ont une vérification live `approved` avec `external_id`. `GET /v1/enrollments` les montre `active`, `source = idv`.
4. Le client ne laisse entrer que sur `status = approved`, lu dans la réponse de clôture ou dans le `GET` qui suit. `review` et `failed` bloquent.
5. Le même `Idempotency-Key` et le même corps renvoient la session déjà créée, sans second débit.
6. Une session `failed` est une alerte et un nouvel essai, pas un refus de la personne.
7. La politique publiée est celle voulue. Elle est figée par session.
8. Révoquer un enrôlement, puis créer une session, répond `no_enrollment`.
9. Une clé Identité reçoit `401` sur `POST /v1/authentications`. Créer une intégration `test` pour ce produit répond `invalid`.

## 21. Critères d’acceptation

1. Une identité live approuvée aux conditions du §5 écrit une référence `active` `source = idv`. Une identité refusée, ou approuvée sans `external_id`, n’écrit rien.
2. Une seconde identité approuvée pour le même couple (organisation, `external_id`) passe l’ancienne référence à `retired` et la nouvelle à `active`.
3. Un visage nettement différent de la référence, présence réelle réussie, produit `declined` `face_match_fail`. Un autre visage trouvé dans la collection, même au-dessus de 0,90, produit le même refus. La réponse ne nomme pas cette autre personne.
4. Le même visage, score au seuil d’approbation, présence réussie, produit `approved`.
5. Sous le seuil de présence, la comparaison n’est pas appelée.
6. Une attaque de présentation produit `declined` `presentation_attack` sans score de convenance.
7. Créer une intégration `authentication` en `test`, ou envoyer `sandbox_scenario`, répond `invalid`. **Aucune clé `ky_test_` n’existe pour ce produit** (ni émise, ni acceptée).
8. Sans enrôlement `active`, `POST /v1/authentications` répond `no_enrollment` et ne crée pas de session.
9. Même `Idempotency-Key` et même corps : même session. Autre corps : `idempotency_key_conflict`.
10. Un refus de consentement pose `declined` `consent_refused`, sans média et sans débit.
11. Révoquer une référence `active`, puis soumettre une session déjà ouverte dessus, pose `declined` `enrollment_inactive`, sans débit et sans appel de comparaison.
12. Une réponse fournisseur illisible, un délai dépassé, ou un moteur indisponible produit `failed`, sans débit, jamais `approved`.
13. Un live sans solde répond `insufficient_credit` et ne crée pas de dossier.
14. Une session `review` ne devient `approved` que par un analyste de la même organisation. L’audit porte l’acteur et les deux statuts. Le nouveau statut se relit par `GET`.
15. Avec la politique par défaut, un score en bande grise pose `review`. Après publication de `on_borderline = decline`, une **nouvelle** session en bande grise pose `declined`. La session déjà ouverte ne change pas.
16. Une session d’une autre organisation, ou d’une autre intégration, répond `404`.
17. Une clé Identité reçoit `401` sur les routes de ce produit. Une clé de ce produit reçoit `401` sur `POST /v1/verifications`.
18. `POST /v1/enrollments` répond `enrollment_requires_idv`.
19. La réponse de clôture porte `status` et les raisons. Aucun webhook n’est émis. Le produit ne signe pas de JWT.
20. Les journaux de ces parcours ne contiennent pas d’image, de vecteur, de `FaceId`, de nom, ni d’`external_id`.
21. Aucune route ne prend un visage et ne renvoie une liste de personnes.

## 22. Livraison

Une seule livraison en production.

| Élément | Valeur |
|---|---|
| Moteur | `auth-2` |
| Intégration | `live` seulement |
| Enrôlement | Identité live approuvée |
| Décision | Réponse de clôture + `GET` |
| Webhook | Aucun |
| Bac à sable | Aucun |

L’enrôlement sans pièce, la présence passive et les signaux d’appareil demandent chacun un moteur et une politique. Ils ne s’ajoutent pas en paramètre de `auth-2`.

## 23. Décisions figées

| Sujet | Décision |
|---|---|
| Question du produit | Ce visage est-il celui de la référence déjà enrôlée sous cet `external_id`. |
| Forme | Session, lien hébergé, décision dans la réponse de clôture, relecture par `GET`. Pas de webhook. |
| Seule entrée positive | `approved`. `review` ne donne pas l’accès. |
| Comparaison | 1:1 contre l’`enrollment_id` figé. Jamais 1:N. |
| Périmètre de la référence | Organisation + `external_id`. Toujours live. |
| Périmètre de la session | L’intégration qui l’a créée. |
| Environnement | Production uniquement. Pas de `ky_test_`, pas de parcours test, pas de `auth-1`, pas de `sandbox_scenario`, pas de `test_fixture`. |
| Source | Uniquement une identité live `approved` avec l’option, l’`external_id` et les signaux biométriques d’onboarding. |
| Remplacement | Nouvelle preuve → ancienne `retired`. La révocation est un autre geste, et ne se défait pas. |
| Session déjà ouverte | Compare la référence figée, ou `enrollment_inactive` si elle n’est plus `active`. |
| Juge | La politique Recogniz-Me. Le fournisseur fournit un score et un signal de présence. |
| Seuils | Plancher plateforme (0,90 / 0,80). L’organisation peut seulement durcir. |
| Présence réelle | Exigée. Non désactivable. Même défi que l’identité, seuil 0,80. |
| Échec fournisseur | `failed`, sans débit, distinct d’un refus. |
| Intégration test | `invalid`. |
| Identité | Ce produit consomme une référence. Il ne réécrit pas la vérification source. |
| Jeton | Le client émet sa propre session après avoir lu `approved`. Ce produit ne signe pas de JWT. |
| Score | Rang de similarité, trois décimales, pas une probabilité, pas un second juge côté client. |
| Conservation | Vecteur effacé dès la révocation ou le remplacement. Photo source = média d’identité. Pas d’entraînement sur les images client. |
