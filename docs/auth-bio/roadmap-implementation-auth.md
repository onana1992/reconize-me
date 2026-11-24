# Roadmap d’implémentation — Authentification biométrique

**Produit :** Recogniz-Me, Biometric Authentication  
**Code produit :** `authentication`  
**Version du document :** 1.6  
**Date :** 22 novembre 2026  
**Statut :** **C0–C7 terminés** (hors C8–C9). Prochain : C8 console.  
**Moteur :** `auth-2` (production uniquement)  
**Documents liés :**

- [`cahier-des-charges-implementation-auth.md`](./cahier-des-charges-implementation-auth.md) — le contrat d’implémentation (prime)
- [`specification-detaillee-auth.md`](./specification-detaillee-auth.md) — le contrat expliqué
- [`../authentification/cahier-des-charges-authentification.md`](../authentification/cahier-des-charges-authentification.md) — le contrat produit
- [`../Biometrie/roadmap-implementation-biometrie.md`](../Biometrie/roadmap-implementation-biometrie.md) — Face Liveness déjà livré pour l’identité
- [`../specs/cahier-des-charges-integration.md`](../specs/cahier-des-charges-integration.md) — clé, crédit. Bac à sable et webhook **ne s’appliquent pas** ici

Ce document dit **dans quel ordre** construire le produit. Un palier n’est pas vert tant que son livrable n’est pas démontrable (test), sans ouvrir le palier suivant.

Ce produit n’a **pas de bac à sable**, **pas de clé test**, et **pas de parcours test**. Aucune `ky_test_` n’est jamais émise. Pas de branche `web/flow` stub, pas de `auth-1`, pas de scénario. Un seul parcours hébergé : production. Les faux clients Rekognition sont un outil d’ingénierie, pas un parcours utilisateur. `CompareFaces` de l’identité ne bouge pas.

---

## 1. Où on en est

**C0–C7 sont clos** (C8 console suivante). Moteur, enrôlement, API sessions, parcours hébergé consentement → liveness → clôture.

| Brique | État |
|---|---|
| `ProductCode` | `IDENTITY`, `AML`, `AUTHENTICATION` |
| Intégrations | `authentication` : `live` seulement (`test` → `invalid`). Clé `ky_live_` uniquement |
| Enrôlement / session auth | Enrôlement C5 + `POST/GET/cancel` `/v1/authentications` (idempotence, crédit, `no_enrollment`) |
| Collection Rekognition | Port `FaceCollectionClient` + faux client. Pas encore d’adaptateur AWS |
| Flow | Parcours auth hébergé (C7) : consentement → Face Liveness → Search → décision dans la clôture |
| Console | Produit `authentication` listé : intégration live, sans mode test, sans webhook. Écrans session/revue = C8 |
| Crédit | Unité reconnue ; garde `insufficient_credit` à la création. Débit à la décision = C9 |

Face Liveness identité est déjà en place (`FaceLivenessClient`, STS `StartFaceLivenessSession`, seuil 0,80). Ce cycle le **réutilise**. Il n’ajoute pas un second widget.

```
Identité approved (+ option, external_id, bio OK)
        │  IndexFaces
        ▼
fiche enrollment active (FaceId)
        │
POST /v1/authentications (ky_live_)
        │  fige enrollment_id, face_id, politique
        ▼
lien hébergé → consentement → Face Liveness
        │  SearchFacesByImage (FaceId figé seulement)
        ▼
corps de clôture = status / reasons / match_score
GET relit. Pas de webhook.
```

---

## 2. Cible

**Happy path production :** une organisation a une intégration `authentication` `live`, un solde, et une personne déjà enrôlée depuis une identité live `approved`. `POST /v1/authentications` avec `ky_live_` renvoie un lien. La personne consent et passe le défi. La réponse de clôture porte `status = approved`, `reasons = [liveness_pass, face_match_pass]`, `match_score`. Une unité `AUTHENTICATION` est débitée. Aucun webhook. Aucun JWT.

**Preuve sans caméra (ingénierie) :** les paliers C3–C6 se démontrent avec un faux `FaceCollectionClient` et un faux `FaceLivenessClient`. Ce n’est pas un environnement client.

---

## 3. Décisions déjà figées

Elles viennent du cahier d’implémentation. Ce cycle ne les rouvre pas.

| Sujet | Décision |
|---|---|
| Environnement | Production uniquement. Intégration `test` → `invalid` (**aucune clé créée**). **Jamais** de `ky_test_`. **Pas de parcours test** (pas de flow stub / scénario). |
| Moteur | `auth-2` seulement. Pas de `auth-1`. |
| Livraison | Décision dans la réponse de clôture. `GET` relit. Pas de webhook. |
| Comparaison | Collection + `SearchFacesByImage`, filtre strict sur le `FaceId` figé. Pas `CompareFaces`. |
| Question | 1:1. Jamais « qui est ce visage ». |
| Source d’enrôlement | Identité live `approved` + option + `external_id` + présence + match pièce. |
| Personne | Couple (organisation, `external_id`). |
| Seuils | Plancher 0,90 / 0,80. Présence 0,80, non désactivable. |
| Région | `us-east-1`. |
| Jeton applicatif | Le client l’émet chez lui après `approved`. |

**Kill de tout le cycle :** une clé `ky_test_` émise ou acceptée ; un parcours hébergé stub / scénario pour ce produit ; `SearchFacesByImage` sous le seuil de présence ; deux fiches `active` pour le même couple ; un `approved` alors que le `FaceId` n’est pas celui de la session ; un webhook d’authentification ; un JWT signé par Recogniz-Me ; `CompareFaces` appelé depuis ce produit ; `FaceId`, `external_id`, octets d’image ou vecteur dans les logs ; un scénario déterministe exposé au client.

---

## 4. Principes d’ordre

1. **Le produit avant le moteur.** On refuse le `test` et on isole la clé avant d’indexer un visage.
2. **La politique et la décision en Java avant Rekognition.** Les bandes 0,80 / 0,90 et le filtre `FaceId` se prouvent en test unitaire.
3. **Le port collection avant le vrai AWS.** Faux client d’abord. Le réseau arrive en dernier.
4. **L’enrôlement avant la session.** Sans fiche `active`, `POST /v1/authentications` répond `no_enrollment`.
5. **Liveness avant recherche.** Sous 0,80 → recapture ou refus, **zéro** `SearchFacesByImage`.
6. **La clôture avant la console.** La console lit un dossier déjà décidé. Elle ne calcule pas.
7. **Réutiliser Face Liveness identité.** Pas de second challenge, pas de second STS.
8. **Un livrable par palier.** Pas de parcours test « en attendant », pas de webhook « comme l’identité », pas de selfie-seul.

---

## 5. Vue d’ensemble

```
C0  décisions (ce document + cahier)
        │
        ▼
C1  produit AUTHENTICATION, live only
        │
        ▼
C2  tables enrollments / authentications, auth-policy-0
        │
        ▼
C3  moteur de décision (signaux faux, sans AWS)
        │
        ▼
C4  port FaceCollectionClient + faux client
        │
        ▼
C5  enrôlement depuis identité live approved
        │
        ▼
C6  API sessions + GET + crédit + idempotence
        │
        ▼
C7  parcours hébergé (consentement + liveness + clôture)
        │
        ▼
C8  console (liste, fiche, revue, enrôlements, règles)
        │
        ▼
C9  client Rekognition réel + débit live
```

| Palier | Livrable démontrable | Rekognition réel |
|---|---|---|
| **C0** | Décisions figées, kill list — **terminé** | non |
| **C1** | Intégration `authentication` `live` créée. `test` → `invalid`. Clé auth → `401` sur IDV/AML — **terminé** | non |
| **C2** | Flyway + entités. Politique d’usine en code — **terminé** | non |
| **C3** | Tests unitaires : `approved` / `face_match_fail` / bande grise / `presentation_attack` / autre `FaceId` — **terminé** | non |
| **C4** | Faux `IndexFaces` / `Search` / `Delete`. Fail-closed typé — **terminé** | mock |
| **C5** | Identité live approved écrit une fiche `active` (faux Index). Seconde identité → `DeleteFaces` puis nouveau `active` — **terminé** | mock |
| **C6** | `POST /v1/authentications` + `GET`. `no_enrollment`, idempotence, `insufficient_credit`. Pas de webhook — **terminé** | mock |
| **C7** | Flow : consentement → liveness (réutilisé) → clôture avec `status` dans la réponse — **terminé** | mock liveness + mock collection |
| **C8** | Console : session, revue, révocation, politique | mock |
| **C9** | Un parcours réel `us-east-1`, un débit, journaux propres | oui |

---

## 6. Paliers

### C0 — Décisions — **terminé**

Aucune ligne de code produit. Les documents suivants sont la loi et ne se rouvrent plus dans ce cycle :

| Document | Rôle |
|---|---|
| [`cahier-des-charges-implementation-auth.md`](./cahier-des-charges-implementation-auth.md) v1.2 | Contrat d’implémentation (prime) |
| [`specification-detaillee-auth.md`](./specification-detaillee-auth.md) v1.1 | Contrat expliqué |
| [`../authentification/cahier-des-charges-authentification.md`](../authentification/cahier-des-charges-authentification.md) v1.1 | Contrat produit (prod only, pas de webhook) |
| Ce roadmap §3 | Kill list et décisions figées |

#### Checklist d’acceptation C0

| # | Décision | Statut |
|---|---|---|
| 1 | Production uniquement : pas de bac à sable, pas de clé `ky_test_`, pas de parcours test / flow stub | accepté |
| 2 | Moteur `auth-2` seul ; pas de `auth-1` | accepté |
| 3 | Décision dans la réponse de clôture ; `GET` relit ; **pas de webhook** | accepté |
| 4 | Comparaison = collection + `SearchFacesByImage`, filtre `FaceId` figé ; **pas** `CompareFaces` | accepté |
| 5 | Question 1:1 ; jamais « qui est ce visage » ; aucune route ne renvoie une liste de personnes | accepté |
| 6 | Enrôlement uniquement depuis identité live `approved` + option + `external_id` + bio OK | accepté |
| 7 | Personne = couple (organisation, `external_id`) | accepté |
| 8 | Seuils plancher 0,90 / 0,80 ; présence 0,80 non désactivable ; `FaceMovementAndLightChallenge` | accepté |
| 9 | Région `us-east-1` ; réutiliser Face Liveness / STS identité | accepté |
| 10 | Pas de JWT / session applicative émis par Recogniz-Me | accepté |
| 11 | Une authentification ne réécrit pas la vérification source | accepté |
| 12 | Kill list §3 ci-dessus : tout écart = échec du cycle | accepté |

**Livrable C0 :** cette checklist est acceptée. Le palier suivant est **C1** (`ProductCode.AUTHENTICATION`, intégration live only).

### C1 — Le produit existe — **terminé**

`ProductCode.AUTHENTICATION`.

`IntegrationService` :

- crée une intégration `authentication` **uniquement** en `live` → clé **`ky_live_` seulement** ;
- mode `test` → `400` `invalid`, **aucune ligne d’intégration, aucune clé** ;
- le préfixe `ky_test_` n’est **jamais** généré pour `ProductCode.AUTHENTICATION` ;
- une clé Identité ou AML ne crée pas une intégration `authentication` ;
- une clé `authentication` reçoit `401` sur `/v1/verifications/**` et `/v1/screenings/**` (et l’inverse, dès que les routes auth existent en C6).

La console peut ouvrir Intégrations sur le produit `authentication` : installation live, **sans** choix de mode test, **sans** webhook. L’UI n’offre pas « Sandbox » pour ce produit.

`CreditService` reconnaît le produit `AUTHENTICATION` (compteur séparé). Pas encore de débit : le solde est seulement consultable.

**Livrable :** test d’API. Créer `authentication` `test` → `invalid`, zéro clé en base. Créer `live` → une seule clé, préfixe `ky_live_`. Aucune ligne `ky_test_` pour ce produit. Clé Identité sur une route auth → `401` dès C6 ; en C1, au minimum le garde d’intégration.

**Preuve C1 :** `AuthIntegrationTest` — `test` → `invalid` ; `live` → `ky_live_` ; clé auth sur `/v1/verifications` et `/v1/screenings` → `401`.

### C2 — Données et politique d’usine — **terminé**

Flyway :

- `enrollments` (org, `external_id`, status, source, `source_verification_id`, `face_id`, `collection_id`, dates) — contrainte : au plus un `active` par (`organization_id`, `external_id`) ;
- `authentications` (org, integration, `external_id`, `enrollment_id`, `face_id` figé, engine, `policy_version`, status, reasons, score, metadata, consent, token hash, attempts, dates) ;
- versions de politique d’authentification (ou équivalent) ;
- réutiliser l’idempotence existante pour le produit.

`auth-policy-0` en code : approbation 0,90, revue 0,80, `on_borderline = review`, essais 3, présence exigée.

Aucune route publique encore, hors ce que C1 a ouvert pour les intégrations.

**Livrable :** migration appliquée. Test de persistance : deux `active` pour le même couple sont refusés au niveau service ou contrainte.

**Preuve C2 :** `V53__authentication.sql` ; entités ; `AuthPolicy.factory()` = `auth-policy-0` ; `EnrollmentPersistenceTest` refuse deux `active` pour le même couple.

### C3 — Moteur de décision — **terminé**

Type sans Spring Web, sans AWS :

- entrée : présence (pass / fail / score), candidats `Search` (FaceId + similarité), politique figée, statut d’enrôlement figé ;
- sortie : `status`, `reasons`, `match_score` nullable, `failure_reason` nullable.

Ordre du contrat :

1. enrôlement plus `active` → `declined` `enrollment_inactive` ;
2. attaque de présentation → `declined` `presentation_attack`, pas de score ;
3. aucun candidat au `FaceId` figé, ou autre `FaceId` même ≥ 0,90 → `declined` `face_match_fail` ;
4. score &lt; seuil de revue → `face_match_fail` ;
5. bande grise → `review` ou `declined` `face_match_borderline` ;
6. score ≥ seuil d’approbation + présence OK → `approved` (`liveness_pass`, `face_match_pass`) ;
7. panne typée → `failed`.

Score : similarité / 100, trois décimales, demi-supérieur.

**Livrable :** tests unitaires. Autre `FaceId` à 0,96 → `face_match_fail`. Même `FaceId` à 0,90 → `approved`. Présence 0,79 → pas de comparaison (signalé en amont ; le moteur ne reçoit pas de candidats).

**Preuve C3 :** `AuthDecisionEngine` + `AuthDecisionEngineTest`.

### C4 — Port collection — **terminé**

Port `FaceCollectionClient` :

- `ensureCollection(organizationId)` ;
- `indexFaces(collectionId, imageBytes, externalImageId)` → `FaceId` ou échec (zéro / plusieurs / qualité) ;
- `searchFacesByImage(collectionId, imageBytes, thresholdPoints)` → liste (FaceId, similarité) ou échec typé ;
- `deleteFaces(collectionId, faceIds)`.

Faux client en mémoire pour les tests. Aucun socket. `ExternalImageId` = `enrollment.id`, jamais l’`external_id` client.

`CompareFacesClient` **n’est pas** injecté dans ce graphe.

**Livrable :** tests du port. Index un visage → Search retrouve le même FaceId. Delete puis Search → plus de candidat. Timeout simulé → erreur typée, jamais un `approved` fabriqué.

**Preuve C4 :** `FaceCollectionClient` + `InMemoryFaceCollectionClient` ; `FaceCollectionClientTest` (index→search, delete→vide, `provider_timeout` typé, zéro/plusieurs visages). `CompareFacesClient` n’est pas dans ce graphe.

### C5 — Enrôlement depuis l’identité — **terminé**

Quand une vérification **live** passe `approved` :

- option d’enrôlement active ;
- `external_id` présent ;
- présence réelle et correspondance à la pièce réussies ;
- image de référence du liveness de **cette** vérification, déjà en mémoire.

Alors : `ensureCollection` → `IndexFaces` → écrire fiche `active`. S’il existait un `active` : `DeleteFaces` de l’ancien **avant** de publier le nouveau (`retired`).

Sinon : rien. Pas d’appel. La décision d’identité ne change pas.

`POST /v1/enrollments` → `409` `enrollment_requires_idv`.  
`GET /v1/enrollments?external_id=` → `active` / dernière retirée / `absent`.  
`POST /v1/enrollments/{id}/revoke` → `DeleteFaces` puis `revoked` (garde : pas sur `retired`).

Échec Index (0 / plusieurs visages) : pas de fiche `active`, journal sans octets ni `external_id`.

**Livrable :** test. Identité live approved aux conditions → fiche avec `face_id`. Sans `external_id` → zéro Index. Seconde identité → un seul `active`, ancien FaceId supprimé. Révocation → Search ne trouve plus le FaceId.

**Preuve C5 :** `EnrollmentService` + hook `HostedFlowService.completeLiveness` ; option `organizations.enrollment_enabled` ; `EnrollmentFromIdvTest` (active, sans `external_id`, remplacement, revoke, `enrollment_requires_idv`).

### C6 — API de session — **terminé**

`POST /v1/authentications` (Bearer `ky_live_` du produit) :

- exige une fiche `active` sinon `409` `no_enrollment` ;
- solde insuffisant → `403` `insufficient_credit`, aucun dossier ;
- `sandbox_scenario` présent → `400` `invalid` ;
- fige `enrollment_id`, `face_id`, `policy_version`, `engine = auth-2` ;
- rend `hosted_url`, `status = created` ;
- `Idempotency-Key` : même corps → même session ; autre corps → `idempotency_key_conflict` ;
- **aucun** appel Rekognition à la création.

`GET /v1/authentications/{id}`, liste filtrable, `POST …/cancel`, médias (URL courte). Isolation : autre intégration / autre org → `404`.

Pas de webhook. Pas de débit à la création.

**Livrable :** Swagger / test API. Sans enrôlement → `no_enrollment`. Avec enrôlement C5 → session `created` + lien. Rejeu d’idempotence : une seule ligne. Clé Identité → `401`.

**Preuve C6 :** `AuthenticationService` + `AuthenticationsController` ; `AuthenticationApiTest` (`no_enrollment`, create+GET, idempotence, crédit, `sandbox_scenario` invalid, clé IDV `401`, cancel).

### C7 — Parcours hébergé (production seulement) — **terminé**

Nouveau parcours court sur `web/flow` (ou branche dédiée du token auth). **Une seule branche** : live. Pas de chemin stub, pas de scénario, pas d’écran « mode test ». Contrairement à l’identité, il n’y a **pas** de parcours sandbox parallèle.

1. Consentement `auth-consent-1`. Refuse → `declined` `consent_refused` **dans la réponse**, sans image, sans débit.
2. Réutilise le défi Face Liveness identité (`FaceMovementAndLightChallenge`, STS inchangé). Pas de pièce. Liveness **réel** dès qu’un lien est ouvert.
3. Sous 0,80 ou défi incomplet → `recapture_requested`, pas de Search. Plafond → `attempt_limit` / `presentation_attack`.
4. Fiche plus `active` → `enrollment_inactive`, pas de Search, pas de débit.
5. Sinon `SearchFacesByImage` sur l’image de référence de **ce** défi, encore en mémoire.
6. Moteur C3 → statut terminal ou `review`.
7. Corps de clôture = décision. Selfie session stocké sous `kyc/` pour audit.

Le navigateur n’a toujours que `StartFaceLivenessSession`.

**Livrable :** test d’intégration d’ingénierie (faux liveness + faux collection injectés **en test Java**, pas via une clé ou un lien « test »). Présence OK + même FaceId ≥ 0,90 → réponse de clôture `approved`. Autre FaceId → `face_match_fail`. `GET` confirme. Zéro webhook. Aucune URL hébergée ne contourne Rekognition.

**Preuve C7 :** `AuthenticationHostedFlowService` + routage `FlowController` ; `AuthenticationHostedFlowTest` (`approved`, `face_match_fail`, recapture sans Search, `enrollment_inactive`, `consent_refused`, panne → `failed`, rejet upload pièce).

### C8 — Console

Écrans français, contrat inchangé (pas un nouveau dessin) :

| Écran | Contenu |
|---|---|
| Sessions | Liste, filtre statut |
| Fiche | Statut, raisons, score, politique, enrôlement, médias, action revue si `review` |
| Enrôlements | Statut, source, révocation |
| Règles | Lecture / publication `auth-policy-*` (bornes du contrat) |
| Intégrations | Clé live, guide : création, lien, clôture, `GET`. Pas de webhook |

`POST /v1/authentications/{id}/review` : analyste → `approved` / `declined`. Audit. `GET` relit. Pas d’événement poussé.

Rôles : comme le contrat (développeur ne revoit pas / ne révoque pas).

**Livrable :** une session `review` créée en C7 (bande grise) est visible ; l’analyste approuve ; `GET` porte `analyst_approved`. Publier `on_borderline = decline` : la **nouvelle** session change, l’ancienne non.

### C9 — Rekognition réel et débit

`RekognitionFaceCollectionClient` implémente le port en `us-east-1`. Création de collection, Index, Search, Delete. Les credentials plateforme existent déjà au lancement du conteneur.

Débit : une unité `AUTHENTICATION` à la décision live quand `approved` / `review` / `declined` pour présence, score ou plafond. Pas de débit sur `consent_refused`, `enrollment_inactive`, `failed`, `expired`, `cancelled`, recapture, revue, replay d’idempotence.

Journaux : ids, statut, raisons, durée, statut fournisseur. Pas d’image, pas de FaceId, pas d’`external_id`, pas de vecteur.

**Livrable :** un parcours réel convenu (enrôlement depuis une identité live de recette, puis authentification). `approved` ou `declined` motivé. Une ligne de grand livre. Puis les tests C3–C7 restent verts sur le faux client. `CompareFaces` identité inchangé (suite de tests identité verte).

---

## 7. Ce qui ne change pas

- Face Liveness et CompareFaces de l’**identité** (seuils `bio-1` / décision IDV).
- Le bac à sable Identité et AML (`ky_test_`).
- Le webhook `verification.completed` de l’identité.
- STS : droit navigateur limité à `StartFaceLivenessSession`.
- Région `us-east-1`.
- Isolation multi-org : id hors périmètre → `404`.

---

## 8. Hors de ce cycle

- Bac à sable client, clé test, parcours test / flow stub, `auth-1`, scénarios, enrôlement fictif.
- Webhook `authentication.*`.
- JWT / session applicative émis par Recogniz-Me.
- Enrôlement par selfie seul, sans identité approuvée.
- Recherche 1:N, liste noire, dédoublonnage.
- Liveness passif, signaux d’appareil.
- Copie supplémentaire de la photo d’enrôlement.
- Nouveau dessin console au-delà des écrans du contrat.

---

## 9. Ordre de lecture utile

| Si vous êtes | Lisez |
|---|---|
| Implémenteur C1–C2 | Cahier §5–§6, spec détaillée §13–§15 |
| Implémenteur C3–C5 | Cahier §4, §7, §9 ; spec §5–§6, §9–§10 |
| Implémenteur C6–C7 | Spec §7–§8, §12 ; cahier §8 |
| Implémenteur C8–C9 | Spec §14, §16–§17 ; critères cahier §12 |
