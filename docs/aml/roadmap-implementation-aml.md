# Roadmap d’implémentation — AML Screening

**Produit :** Recogniz-Me, AML Screening  
**Version du document :** 1.0  
**Date :** 20 octobre 2026  
**Statut :** A1 à A8 en place. A9 (live, débit, appel OpenSanctions) non commencé.  
**Documents liés :**

- [`cahier-des-charges-aml.md`](./cahier-des-charges-aml.md) — le contrat
- [`specification-detaillee-aml.md`](./specification-detaillee-aml.md) — le contrat expliqué, avec les treize appels de référence

Ce document dit **dans quel ordre** construire le criblage. Un palier n’est pas vert tant que son livrable n’est pas démontrable, sans ouvrir le palier suivant.

La version 1 est l’appel déterministe `aml-1`. La version 2 est l’appel OpenSanctions `aml-2`, derrière le même JSON. Le bac à sable reste sur `aml-1` après l’ouverture du live.

---

## 1. Où on en est

| Brique | État |
|---|---|
| Produit console `aml` | Page annonce. `metered: false`. Pas d’intégrations, pas de débit |
| `ProductCode` | `IDENTITY` seulement. `IntegrationService` et `CreditService` créent et débitent l’identité |
| Criblage | Aucune table, aucune route `/v1/screenings` |
| OpenSanctions | Aucun client |
| Identité | Session, clé `ky_test_` / `ky_live_`, idempotence 24 h, décision. Le rattachement AML n’existe pas |

Le moteur se prouve avant la route. La route se prouve avant la console. OpenSanctions se prouve d’abord avec un faux client.

---

## 2. Cible

**Happy path version 1 :** une clé `ky_test_` du produit `aml` envoie Ivan Sanctionexample, né le 12 avril 1971. La réponse `201` porte `outcome = confirmed_match`, `engine = aml-1`, `corpus_version = fixture-1`. Aucune connexion vers OpenSanctions. Aucun débit.

**Happy path version 2 :** une clé `ky_live_` envoie une personne, reçoit le même genre de dossier avec `engine = aml-2`, et une unité `aml` est débitée seulement si `status = completed`. Une clé de test lancée juste après n’ouvre toujours aucune connexion fournisseur.

---

## 3. Décisions déjà figées

Elles viennent du cahier. Ce cycle ne les rouvre pas.

| Sujet | Décision |
|---|---|
| Livraison | Le dossier est le corps de `POST` et de `GET`. Pas d’événement de criblage. |
| Issues | `clear`, `potential_match`, `confirmed_match`. L’échec est `status = failed`. |
| Juge | La politique Recogniz-Me. Le score OpenSanctions n’est pas une décision. Leur booléen `match` n’est pas lu. |
| Confirmation automatique | Sanction seulement, avec nom au score 1 et date complète, ou numéro identique et nom encore reconnaissable. |
| PEP, RCA, déchéance, crime | Revue humaine. |
| Test | Toujours `aml-1`, corpus `fixture-1`, zéro débit, zéro appel fournisseur. |
| Live avant `aml-2` | `409` `engine_unavailable`, avant le contrôle du solde. |
| Médias adverses, personnes morales, surveillance continue | Hors de ce cycle. |

**Kill de tout le cycle :** une clé `ky_test_` qui ouvre une connexion OpenSanctions ; un `clear` produit alors que le fournisseur a échoué ou qu’une fiche au-dessus du seuil est illisible ; un nom, une date, un numéro ou la clé fournisseur dans les logs ; une intégration live AML créée avant A9 ; un débit sur un dossier `failed` ou sur une qualification ; un dossier d’une autre organisation qui ne répond pas `404`.

---

## 4. Principes d’ordre

1. **Le calcul avant la route.** Les treize lignes du cahier §11.2 sont un test Java, sans Spring MVC.
2. **La clé avant le dossier.** On ne crée un criblage qu’avec une intégration `aml`. Une clé identité reçoit `401`.
3. **`aml-policy-0` avant l’éditeur.** Les seuils d’usine sont en code. L’écran qui publie une politique vient après.
4. **La console lit un dossier qui existe déjà.** Elle ne calcule pas.
5. **Le faux OpenSanctions avant le vrai.** A8 fixe le fail-closed. A9 branche le réseau.
6. **L’identité ne se rattache qu’une fois l’appel stable.** A7 ne démarre pas sur un moteur qui échoue encore une ligne de référence.

---

## 5. Vue d’ensemble

```
A1 moteur aml-1 + 13 lignes
        │
        ▼
A2 produit aml, clé test, live refusé
        │
        ▼
A3 POST / GET, dossier dans la réponse
        │
        ▼
A4 qualification, issue recalculée
        │
        ▼
A5 console : liste et fiche
        │
        ▼
A6 politique publiable
        │
        ▼
A7 rattachement des vérifications test
        │
        ▼
A8 port OpenSanctions, client faux
        │
        ▼
A9 live, débit, un appel réel
```

| Palier | Livrable démontrable | OpenSanctions |
|---|---|---|
| **A1** | Les treize lignes du cahier §11.2 passent en test unitaire | non |
| **A2** | Intégration `aml` test créée. Live → `engine_unavailable` | non |
| **A3** | `POST /v1/screenings` renvoie le dossier. Clé test. Idempotence | non |
| **A4** | Écarter Nora renvoie `clear` dans la même réponse | non |
| **A5** | La fiche console montre ce dossier et qualifie | non |
| **A6** | Publier une politique. Le dossier suivant fige la nouvelle version. L’ancien non | non |
| **A7** | Vérification test « Nora » → revue `aml_potential_match` et `aml_screening_id` | non |
| **A8** | Faux fournisseur en échec → dossier `failed`, jamais `clear`. Clé test n’utilise pas ce port | mock |
| **A9** | Une clé live, un appel réel, un débit, puis une clé test à zéro connexion | oui |

---

## 6. Paliers

### A1 — Moteur `aml-1`

Corpus `fixture-1` chargé depuis le test, pas depuis le réseau. Quatre fiches : Ivan, Nora, Leo, Mina, telles que le cahier §11.1.

Un type sans Spring Web :

- entrée : sujet normalisé + politique `aml-policy-0` ;
- sortie : score, traits, au plus dix correspondances triées, issue.

Le score suit le cahier §7 : normalisation, jetons, distance 1 au-delà de 5 lettres, facteurs décimaux, arrondi demi-supérieur à trois décimales.

**Livrable :** un test. Les treize lignes passent, scores compris (`0,150`, `0,200`, `0,900`, `1,000`). Aucun client HTTP dans le graphe de ce test.

### A2 — Le produit existe

`ProductCode.AML`.

`IntegrationService` crée une intégration dont le produit est celui de la route, plus seulement `IDENTITY`. Créer une intégration AML `test` émet une clé `ky_test_`. Créer une intégration AML `live` répond `409` `engine_unavailable` avant de regarder le solde.

La console peut ouvrir Intégrations sur `/aml` pour cette clé de test. Le texte d’annonce qui cite les médias adverses est remplacé par la couverture réelle : sanctions, PEP, proches, déchéance.

**Livrable :** test d’API. Organisation avec solde : le live AML est refusé. La clé identité ne crée pas une intégration `aml`. Deux organisations ne lisent pas leurs clés.

### A3 — L’appel

Flyway : `screenings`, `screening_hits`. Le sujet et les correspondances sont des colonnes du dossier, pas des logs.

`POST /v1/screenings` et `GET /v1/screenings/{id}` et `GET /v1/screenings`.

La clé résout une intégration `aml`. Mode `test` → moteur A1. La réponse `201` est le dossier complet (`status`, `outcome`, `hits`, `engine`, `policy_version`, `corpus_version`). `Idempotency-Key` reprend la règle déjà en place : 8 à 64 caractères, 24 heures, même corps → même dossier, autre corps → `409` `idempotency_key_conflict`.

Isolation : un id d’une autre intégration ou d’une autre organisation → `404`.

Journal : `screening_id`, `outcome`, nombre de correspondances, durée. Pas le nom, pas la date, pas le numéro.

**Livrable :** Swagger, clé `ky_test_` AML. Ivan avec sa date → `confirmed_match`. Ada Clear → `clear` et `hits` vide. Rejouer la même clé d’idempotence ne crée pas une deuxième ligne.

### A4 — Qualification

`POST /v1/screenings/{id}/hits/{hitId}/disposition`.

La réponse `200` est le dossier entier, issue recalculée : un faux positif sur l’unique PEP de Nora → `clear`. Un vrai positif → `confirmed_match`. Un dossier `failed` → `409` `invalid_status` (ce statut n’existe encore qu’en préparation de A8 ; le garde est là).

Audit `screening.disposition_set` : acteur, identifiants, ancien et nouveau geste, motif. Pas la note si elle contient un nom — la note est en base, pas dans le journal.

`prior_disposition` : même sujet normalisé, même `entity_id`, dossier antérieur de l’organisation. La nouvelle ligne reste `unreviewed`.

**Livrable :** test. Nora écartée deux fois : la seconde réponse est encore `clear`, une seule issue, deux lignes d’audit. Un second sujet homonyme n’hérite pas de l’écart.

### A5 — Console

Liste et fiche sous `/aml`. La fiche affiche le sujet, l’issue, le moteur, chaque correspondance, ses traits et son jeu de données. Le membre qualifie. Le développeur voit la fiche et ne voit pas l’action. La lecture seule ne qualifie pas.

L’installation de l’intégration montre `POST /v1/screenings`, le corps de réponse, `GET` et la qualification. Pas de parcours hébergé.

**Livrable :** un dossier créé en A3 est visible, et un faux positif fait depuis l’écran se relit en `GET` avec la nouvelle issue.

### A6 — Politique

Table des versions. `aml-policy-0` reste le défaut tant que personne n’a publié.

L’onglet Règles de `/aml` publie : catégories (sanction obligatoire), seuils dans les bornes du cahier §7, `attach_to_identity`, `on_match`. Propriétaire et administrateur publient. Les autres lisent.

Le criblage fige `policy_version` à la création. Publier ensuite ne réécrit pas les dossiers déjà rendus.

**Livrable :** test. Revue abaissée à 0,50 : un score qui était sous 0,70 et au-dessus de 0,50 entre dans le dossier **suivant**. Le dossier précédent garde l’ancienne issue. Publier un seuil de revue à 0,90 → `400`. Retirer `sanction` → `400`.

### A7 — Rattachement, mode test seulement

Après une lecture de pièce réussie, si `attach_to_identity` est vrai et que `test_integration_id` désigne une intégration AML test de la même organisation :

- le sujet est l’identité extraite, pas le nom tapé à la création ;
- le criblage est un dossier A3 sur cette intégration ;
- la vérification reçoit `aml_screening_id`.

| `on_match` | Effet, déjà dans le cahier §14 |
|---|---|
| `ignore` | L’identité tranche sans attendre |
| `review` | `potential_match` ou `confirmed_match` → revue. `clear` → la décision d’identité continue |
| `block` | `confirmed_match` → refus. `potential_match` → revue |

Nom de pièce inexploitable, ou intégration absente, en `review` ou `block` : la vérification part en revue, motif `aml_unavailable`, sans dossier. En `ignore`, l’identité tranche quand même.

Une qualification AML ultérieure ne change pas la décision d’identité.

Pas de vérification live dans ce palier : le rattachement live attend A9. Une vérification live avec le rattachement allumé part en revue `aml_unavailable` tant que `aml-2` n’est pas en service, et ne crée pas de criblage `aml-1` sur une clé live.

**Livrable :** test. Vérification test dont la pièce est Nora Potentialexample, politique `review` → décision revue, motif `aml_potential_match`, un criblage `aml-1` lié. Zéro appel OpenSanctions.

### A8 — Port fournisseur, sans réseau

Port `SanctionsMatchPort` : un sujet, un seuil, un algorithme épinglé → fiches (id, score, schéma, `target`, thèmes, noms, dates, numéros, jeux de données) ou un échec typé (`timeout`, `unavailable`, `invalid_response`).

L’adaptateur de décision :

- ignore le booléen `match` ;
- arrondit le score fournisseur ;
- recalcule les traits du cahier §7.1 ;
- confirme une sanction seulement avec les traits forts ;
- laisse une PEP en `potential_match` même à 1,000 ;
- si une fiche au-dessus du seuil n’a pas d’id ou de score, ou n’est pas une personne cible lisible → `failed` / `provider_invalid_response`, `hits` vide, pas de débit.

Le client faux couvre : liste vide → `clear` ; PEP seule → `potential_match` ; sanction et crime → catégorie primaire `sanction` ; crime seul, politique d’usine → ligne absente ; timeout → `failed`.

La clé `ky_test_` ne reçoit pas ce port. Elle reste sur A1.

**Livrable :** tests du port. Aucun n’ouvre de socket. Un test dédié échoue si le moteur de test résout le bean OpenSanctions.

### A9 — Live

`OpenSanctionsMatchClient` implémente le port. `POST https://api.opensanctions.org/match/default`, en-tête `Authorization: ApiKey`, secret de plateforme (`kyc.opensanctions.api-key`), hors dossier et hors logs.

`limit=20`, `threshold` = seuil de revue figé, `algorithm` = constante de `aml-2`. Huit secondes, une reprise sur `429` / `502` / `503` / `504`. Pas de reprise sur `401` / `403`.

Créer l’intégration live AML devient possible. Sans solde → `403` `insufficient_credit`, aucun dossier. Avec solde : l’appel part, le débit d’une unité `aml` n’est écrit qu’après `status = completed`. `failed`, qualification et rejeu d’idempotence ne débitent pas.

`CreditService` compte le produit `AML` à côté de `IDENTITY`. Les deux unités ne se confondent pas.

Le rattachement d’une vérification live utilise `live_integration_id` et `aml-2`. Il ne retombe pas sur `fixture-1`.

**Livrable :** une clé live, un sujet réel convenu à l’avance, un dossier `aml-2` avec `retrieved_at` et des `datasets`. Puis la même suite de tests A3 avec une clé test : zéro ligne de client OpenSanctions. Coût : un appel du plan OpenSanctions, plus une unité de crédit interne.

---

## 7. Ce qui ne change pas

- Le parcours, la décision et le débit de l’identité, hors le motif AML ajouté en A7.
- `StubDocumentAi` et le bac à sable documentaire.
- L’idempotence des vérifications.
- Les clés `ky_test_` / `ky_live_` : le préfixe dit le mode, le produit dit l’intégration.
- Aucun événement nouveau. Le criblage se lit dans la réponse, et la vérification rattachée se relit par `GET`.

---

## 8. Hors de ce cycle

- Médias adverses, criblage des sociétés, bénéficiaires effectifs, surveillance quand l’index change.
- Clé OpenSanctions apportée par le client.
- Réécriture d’une décision d’identité après une qualification AML.
- Copie automatique d’un faux positif sur le dossier suivant. A4 ne pose que `prior_disposition`.
- Éditeur de formules. Les seuils ont des bornes. Il n’y a pas d’expression libre.
