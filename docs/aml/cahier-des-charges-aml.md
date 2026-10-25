# Cahier des charges — AML Screening

**Produit :** Recogniz-Me, AML Screening  
**Code produit :** `aml`  
**Version du document :** 1.0  
**Date :** 19 octobre 2026  
**Statut :** spécification produit — contrat de criblage  
**Documents liés :**

- [`../specs/specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md) — l’identité prouve la pièce et le visage ; elle ne crible pas un nom
- [`../specs/specification-regles-idv.md`](../specs/specification-regles-idv.md) — la politique « ignorer / revue / bloquer » est éditée ici, lue par l’IDV
- [`../specs/cahier-des-charges-integration.md`](../specs/cahier-des-charges-integration.md) — clé, mode, bac à sable
- [`specification-detaillee-aml.md`](./specification-detaillee-aml.md) — le même contrat, expliqué pas à pas
- [`roadmap-implementation-aml.md`](./roadmap-implementation-aml.md) — ordre de construction A1–A9

Ce document fixe le second produit de la gamme. Une organisation lui soumet une personne. Elle reçoit une décision de criblage : personne écartée, correspondance à revoir, ou correspondance confirmée. Chaque correspondance cite sa catégorie, sa source, son score et les traits qui ont produit ce score.

Le niveau visé est celui d’un criblage tenu en production par une équipe conformité, au même titre qu’une plateforme comme Veriff : la décision est motivée, la source est nommée, la revue laisse une trace, l’échec d’un fournisseur ne devient jamais un « rien à signaler », et le bac à sable ne consulte aucune liste officielle.

Deux versions livrent le même contrat. La première est un appel synchrone dont le résultat se recalcule. La seconde remplace le corpus local par l’API de correspondance d’OpenSanctions, sans changer le corps de la requête ni la forme de la décision.

---

## 1. Objet

AML Screening répond à une question : cette personne, décrite par un nom et les attributs disponibles, figure-t-elle dans les catégories de listes activées, avec un score suffisant pour une revue ou une confirmation.

Le produit est un appel d’API. Le serveur du client envoie le sujet. Le corps de la réponse est la décision.

Il se consomme de deux façons.

| Usage | Déclencheur |
|---|---|
| Criblage seul | `POST /v1/screenings` avec la clé du produit AML |
| Criblage attaché à une vérification d’identité | Après une lecture de pièce réussie, si la politique AML l’active |

Ouvrir une vérification d’identité dans le seul but de cribler un nom reste hors contrat. Le nom saisi à la création d’une session IDV n’est pas un sujet de criblage. Le sujet attaché est l’identité lue sur la pièce.

## 2. Promesse

| Engagement | Conséquence |
|---|---|
| La décision est un dossier, pas une recherche | Le client range `outcome`, lu dans la réponse. Il ne réinterprète pas le JSON d’un fournisseur. |
| La réponse livre le dossier | `POST` de création et `POST` de qualification renvoient le criblage complet. `GET` le relit. Rien n’est poussé ensuite. |
| Le score classe une ressemblance | Un score de 0,91 n’est pas une probabilité de 91 % que ce soit la même personne. Les bandes de décision sont une politique. |
| Même sujet, même politique, même corpus : même décision | La version 1 le garantit par calcul local. La version 2 le garantit au rejeu du dossier : la charge fournisseur stockée et la politique figée reproduisent l’issue. Un nouvel appel peut différer, parce que l’index a bougé. Ce nouvel appel est un nouveau dossier. |
| L’échec se ferme | Délai dépassé, réponse illisible, clé fournisseur refusée : le dossier est `failed`. Il n’est pas `clear`. |
| Une personne écartée par un analyste ne disparaît pas des listes | L’écart porte sur le couple (ce sujet, cette entité listée). Le prochain dossier du même sujet montre l’écart précédent. Il ne le recopie pas. |
| Test et live sont deux intégrations | Une clé `ky_test_` n’appelle pas OpenSanctions et ne débite rien. Une clé `ky_live_` n’existe pour ce produit que lorsque le moteur `aml-2` est en service. |

## 3. Périmètre

### 3.1 Dans le produit

- Création, lecture et liste d’un criblage, isolées par organisation et par intégration.
- Sujet personne : nom, nom intermédiaire, date de naissance, nationalité, pays de résidence, numéro de pièce.
- Catégories : sanction, personne politiquement exposée (PEP), proche ou apparenté (RCA), déchéance (`debarment`).
- Catégorie crime, présente dans le modèle, inactive par défaut.
- Score, traits explicatifs, source, décision.
- Revue d’une correspondance : faux positif ou vrai positif, avec motif.
- Politique versionnée, figée sur le dossier à sa création.
- Livraison dans le corps de la réponse : création, qualification, relecture.
- Rattachement optionnel à une vérification d’identité, selon les trois politiques déjà nommées côté IDV.
- Moteur `aml-1`, corpus synthétique publié, résultat reproductible.
- Moteur `aml-2`, API de correspondance OpenSanctions, même contrat public.

### 3.2 Hors de ces deux versions

| Sujet | Sort |
|---|---|
| Médias adverses | Aucune des deux versions n’en produit. OpenSanctions n’est pas une base de presse. La catégorie n’est pas acceptée dans la politique. |
| Personne morale, bénéficiaire effectif, KYB | `subject.type = legal_entity` répond `unsupported_subject`. |
| Surveillance continue | Un dossier est un criblage à un instant. Le modèle conserve l’identifiant d’entité et l’horodatage de lecture pour une version ultérieure. Il ne réouvre pas un dossier quand l’index change. |
| Fichier CSV, criblage de masse en un seul appel | Un appel porte un sujet. |
| Liste blanche globale d’une entité sanctionnée | Interdit. Un écart est local au sujet criblé. |
| Clé OpenSanctions apportée par le client | La clé est un secret de plateforme. |
| Réécriture de la décision IDV après une revue AML tardive | La vérification d’identité lit l’issue au moment où elle tranche. Une qualification ultérieure se relit sur le criblage. |

La phrase actuelle de la console (« sanctions, PEP et médias adverses ») décrit une annonce. Elle est alignée sur la couverture de ce document avant que l’API soit exposée : sanctions, PEP, proches, déchéance. Les médias adverses n’y figurent plus.

## 4. Principes

1. Le moteur propose des correspondances. La politique tranche. OpenSanctions ne décide pas du dossier, pas plus que le score local.
2. Une correspondance sans catégorie reconnue, sans identifiant d’entité ou sans score n’entre pas dans un dossier `clear`. Elle fait échouer le criblage.
3. La confirmation automatique est réservée aux sanctions, et seulement avec un trait fort : date de naissance complète identique et nom exact, ou numéro de pièce identique avec un nom encore reconnaissable. Une PEP, un proche ou une déchéance restent en revue, quel que soit le score.
4. Le client peut abaisser le seuil de revue, donc voir plus de dossiers. Il ne peut pas le relever au-delà du plafond plateforme, ni retirer la catégorie sanction.
5. Le dossier fige `engine`, `policy_version` et `corpus_version`. Changer l’algorithme OpenSanctions, le corpus ou les bornes est une nouvelle version. Les dossiers déjà lus ne bougent pas.
6. Les journaux portent `screening_id`, `integration_id`, `outcome`, le nombre de correspondances, la durée et le statut fournisseur. Ils ne portent ni le nom, ni la date de naissance, ni le numéro, ni la légende OpenSanctions.
7. Le corps de la réponse est le dossier. `GET` le relit. Le code HTTP à lui seul ne vaut pas une issue : le client lit `outcome` et `status`.

## 5. Décision

Trois issues, et un échec qui n’en est pas une.

| `outcome` | Sens | Qui peut la poser |
|---|---|---|
| `clear` | Aucune correspondance au seuil de revue, ou toutes les correspondances ont été écartées par un analyste | Moteur, puis revue |
| `potential_match` | Au moins une correspondance au seuil, aucune confirmation | Moteur, ou revue partielle |
| `confirmed_match` | Au moins une sanction confirmée par la règle automatique, ou un analyste a qualifié un vrai positif | Moteur ou revue |

| `status` | Sens |
|---|---|
| `completed` | Le moteur a terminé. `outcome` est l’une des trois valeurs. |
| `failed` | Le moteur n’a pas pu conclure. `outcome` est nul, `hits` est vide, `failure_reason` est posé. |

`failure_reason` prend `provider_timeout`, `provider_unavailable`, `provider_invalid_response`. La version 1 ne les produit pas : le corpus local est dans le processus.

Ordre de calcul de l’issue, après constitution de la liste de correspondances retenues (au plus 10) :

1. Aucune correspondance → `clear`.
2. Au moins une correspondance éligible à la confirmation automatique → `confirmed_match`.
3. Sinon → `potential_match`.

Après une revue, l’issue est recalculée sur les qualifications, pas sur le score :

1. Au moins un vrai positif → `confirmed_match`.
2. Toutes les correspondances sont des faux positifs → `clear`.
3. Il reste au moins une correspondance non revue, et aucun vrai positif → `potential_match`.

Une confirmation automatique qui est ensuite écartée par un analyste suit la même règle. L’issue peut donc passer de `confirmed_match` à `clear`. L’historique des qualifications reste sur le dossier.

## 6. Catégories

| Code | Contenu | Confirmation automatique | Défaut |
|---|---|---|---|
| `sanction` | Liste de sanctions ou gel d’avoirs | Oui, si le trait fort est là | Active, non retirable |
| `pep` | Personne politiquement exposée | Non | Active |
| `rca` | Proche ou apparenté d’une PEP | Non | Active |
| `debarment` | Déchéance, exclusion d’un marché public ou d’une profession | Non | Active |
| `crime` | Notice criminelle portée par la source | Non | Inactive |

Quand une entité porte plusieurs catégories, `categories` les contient toutes. `category` retient la plus forte, dans cet ordre : `sanction`, `debarment`, `crime`, `pep`, `rca`. La confirmation automatique regarde la présence de `sanction` dans `categories`, pas seulement le libellé primaire.

La version 2 range les thèmes OpenSanctions ainsi. Un thème non listé est ignoré. Une entité dont il ne reste aucune catégorie active est écartée de la liste, sans faire échouer le dossier.

| Thème OpenSanctions | Catégorie |
|---|---|
| `sanction`, `sanction.linked`, `asset.frozen` | `sanction` |
| `role.pep` | `pep` |
| `role.rca` | `rca` |
| `debarment` | `debarment` |
| `crime` et `crime.*` | `crime` |

## 7. Score

Le score est un nombre décimal entre 0 et 1, affiché et comparé à trois décimales, arrondi au demi supérieur. Le calcul intermédiaire est décimal, pas un flottant binaire. La valeur affichée est celle qui est comparée aux seuils.

| Seuil | Défaut | Borne que l’organisation peut publier |
|---|---|---|
| Revue | 0,70 | De 0,50 à 0,85 inclus |
| Confirmation | 0,95 | De 0,90 à 1,00 inclus, et strictement au-dessus du seuil de revue |

Une correspondance est retenue lorsque son score arrondi est supérieur ou égal au seuil de revue.

Elle est confirmée automatiquement lorsque les quatre conditions tiennent :

- `sanction` figure dans ses catégories ;
- le score arrondi est supérieur ou égal au seuil de confirmation ;
- l’un des deux traits forts est présent : `birth_date_exact` avec `name_exact`, ou `id_number_exact` ;
- la catégorie sanction est active dans la politique figée. Elle l’est toujours, au plancher.

`id_number_exact` exige en plus un score de nom d’au moins 0,50 avant l’arrondi final. Un numéro seul, posé sur un nom sans rapport, ne confirme pas.

### 7.1 Traits stables

Ces codes sont ceux du contrat. Les noms de traits internes d’OpenSanctions ne le sont pas. Ils peuvent être recopiés à part, dans `provider_features`, pour l’analyste.

| Code | Définition |
|---|---|
| `name_exact` | Le nom normalisé de la requête égale le nom canonique ou un alias. |
| `name_partial` | Le nom a contribué au score sans être exact. |
| `birth_date_exact` | Les deux côtés ont une date complète, et elle est égale. |
| `birth_year_match` | L’année est égale, et au moins un côté n’a pas de date complète. |
| `birth_date_partial` | L’année est égale, les deux dates sont complètes, le mois ou le jour diffère. |
| `birth_year_mismatch` | Les deux côtés ont une année, et elle diffère. |
| `birth_date_absent` | Au moins un côté n’a pas de date. |
| `nationality_match` | Les deux nationalités sont présentes et égales. |
| `nationality_mismatch` | Les deux nationalités sont présentes et différentes. |
| `id_number_exact` | Les deux numéros sont présents et égaux après normalisation, et le score de nom vaut au moins 0,50. |
| `id_number_mismatch` | Les deux numéros sont présents et différents. |

Le pays de résidence est conservé et, en version 2, transmis au fournisseur. Il ne modifie pas le score et ne retire pas une correspondance.

### 7.2 Normalisation

Appliquée au nom, aux alias et au numéro avant toute comparaison.

1. Forme Unicode NFKD, suppression des signes diacritiques.
2. Majuscules.
3. Tout caractère qui n’est pas une lettre ou un chiffre devient un espace. Les espaces successifs sont ramenés à un. Les extrémités sont tronquées.
4. Le numéro ignore aussi les espaces internes : il ne reste que les lettres et les chiffres.
5. La date acceptée en entrée est `YYYY-MM-DD`. Une année seule n’est pas une entrée client. Elle peut exister côté entité listée.
6. La nationalité est un code ISO 3166-1 alpha-2. La comparaison se fait en majuscules.

Le nom de requête est la jointure de `first_name`, `middle_name` et `last_name`, en omettant les vides, après normalisation de chaque partie.

### 7.3 Score de nom de la version 1

La version 1 calcule elle-même le score. La version 2 prend le score OpenSanctions et calcule seulement les traits du §7.1.

Pour chaque variante de l’entité (nom canonique, puis chaque alias) :

1. Découper la requête et la variante en jetons.
2. Un jeton de requête apparie un jeton de variante s’ils sont égaux, ou si les deux ont au moins 5 caractères et que leur distance de Levenshtein vaut 1. Un jeton plus court n’apparie que s’il est égal.
3. Chaque jeton sert au plus une fois. Les paires candidates sont examinées dans cet ordre : égalité stricte avant écart d’une lettre ; longueur du jeton de variante décroissante ; index de requête croissant ; index de variante croissant.
4. Le score de la variante est `2 × paires retenues / (jetons requête + jetons variante)`. Zéro si l’un des deux côtés n’a aucun jeton.
5. Le score de nom du dossier est le maximum parmi les variantes. La variante gagnante est `matched_name`. Égalité de score : la première dans l’ordre canonique, puis alias dans l’ordre du corpus.

Facteurs ensuite, chacun valant 1 lorsqu’il ne s’applique pas :

| Situation | Facteur | Trait |
|---|---|---|
| Date absente d’un côté | 1 | `birth_date_absent` |
| Années différentes | 0,15 | `birth_year_mismatch` |
| Année égale, date incomplète d’un côté | 1 | `birth_year_match` |
| Dates complètes, même année, mois ou jour différent | 0,85 | `birth_date_partial` |
| Dates complètes égales | 1 | `birth_date_exact` |
| Nationalités différentes | 0,90 | `nationality_mismatch` |
| Nationalités égales | 1 | `nationality_match` |
| Numéros différents | 0,20 | `id_number_mismatch` |

Si les numéros sont égaux et que le score de nom vaut au moins 0,50, le score final est 1,000 et le trait `id_number_exact` est posé. Les traits de date et de nationalité restent visibles. Ils ne réduisent plus le score.

Sinon le score final est le score de nom multiplié par le facteur de date, le facteur de nationalité et le facteur de numéro, arrondi au demi supérieur à trois décimales.

`name_exact` est posé lorsque le nom de requête égale la variante gagnante. Sinon, si le score de nom est strictement positif, `name_partial` est posé.

Les correspondances sont triées par score décroissant, puis par `entity_id` croissant. Les dix premières au seuil de revue sont retenues.

## 8. Modèle

```
Organisation
  └── Produit AML
        └── Intégration   test | live
              ├── Clé API
              └── Criblages
                    ├── Sujet
                    ├── Correspondances
                    └── Qualifications
```

Un criblage créé par le rattachement IDV appartient à l’intégration AML désignée, pas à l’intégration Identité. Les deux dossiers se citent : `verification_id` sur le criblage, `aml_screening_id` sur la vérification.

| Champ | Règle |
|---|---|
| `id` | UUID. |
| `integration_id` | Intégration AML qui a créé le dossier, ou celle désignée par le rattachement. |
| `external_id` | Référence client, optionnelle, non unique. |
| `verification_id` | Présent lorsque le criblage vient d’une vérification d’identité. |
| `engine` | `aml-1` ou `aml-2`. |
| `policy_version` | Version publiée au moment de la création. |
| `corpus_version` | `fixture-1` en version 1. `opensanctions` en version 2. |
| `retrieved_at` | Horodatage UTC de la lecture fournisseur. Nul en version 1. |
| `status` | `completed` ou `failed`. |
| `outcome` | `clear`, `potential_match`, `confirmed_match`, ou nul. |
| `created_at`, `updated_at` | UTC. |

La correspondance porte `id`, `entity_id`, `category`, `categories`, `score`, `matched_name`, `listed_name`, `aliases` (vingt au plus), `birth_dates`, `countries`, `datasets`, `features`, `provider_features`, `disposition`, `prior_disposition`, `prior_screening_id`.

`entity_id` est l’identifiant stable de la personne listée : identifiant du corpus en version 1, identifiant OpenSanctions en version 2. Il ne change pas d’un criblage à l’autre pour la même fiche source.

`disposition` vaut `unreviewed`, `false_positive` ou `true_positive`.

## 9. Sujet

`subject.type` vaut `person`. Toute autre valeur répond `unsupported_subject`.

| Champ | Règle |
|---|---|
| `first_name` | Obligatoire. 1 à 128 caractères après troncature. Au moins une lettre. |
| `last_name` | Même règle. |
| `middle_name` | Optionnel. 1 à 128 caractères s’il est envoyé. |
| `date_of_birth` | Optionnel. `YYYY-MM-DD`, date réelle, année entre 1900 et l’année courante incluse. |
| `nationality` | Optionnel. ISO 3166-1 alpha-2. |
| `country_of_residence` | Optionnel. ISO 3166-1 alpha-2. |
| `id_number` | Optionnel. 1 à 64 caractères. Comparé après normalisation. |
| `id_type` | Optionnel. `passport`, `national_id` ou `other`. Informatif. Il ne change pas le score. |
| `external_id` | Optionnel. 1 à 128 caractères. |

Un prénom et un nom d’une lettre sont refusés (`size`). Le moteur a besoin d’un nom, pas d’une initiale.

## 10. API

La clé est celle du module Intégration, produit `aml`, en-tête `Authorization: Bearer`. Une clé Identité qui appelle `/v1/screenings` reçoit la même réponse qu’une clé inconnue : `401`.

L’identifiant d’un dossier d’une autre organisation, ou d’une autre intégration de la même organisation, répond `404`.

### 10.1 Créer

`POST /v1/screenings`

En-tête optionnel `Idempotency-Key` : 8 à 64 caractères ASCII imprimables, durée de vie 24 heures, même règle que les vérifications d’identité. Même clé et même corps : le même dossier, sans second calcul et sans second débit. Même clé et autre corps : `409` `idempotency_key_conflict`.

Le corps est le sujet du §9. Réponse `201`, dossier complet. Ce corps est la livraison.

```json
{
  "id": "3f1c0c2e-7b1a-4e0d-9c2a-6b8e1d4a9f10",
  "integration_id": "b7e1a2c0-1111-4222-8333-444455556666",
  "external_id": "customer-42",
  "verification_id": null,
  "status": "completed",
  "outcome": "confirmed_match",
  "failure_reason": null,
  "engine": "aml-1",
  "policy_version": "aml-policy-0",
  "corpus_version": "fixture-1",
  "retrieved_at": null,
  "subject": {
    "type": "person",
    "first_name": "Ivan",
    "last_name": "Sanctionexample",
    "middle_name": null,
    "date_of_birth": "1971-04-12",
    "nationality": null,
    "country_of_residence": null,
    "id_number": null,
    "id_type": null
  },
  "hits": [
    {
      "id": "9aa1c0d2-0000-4000-8000-000000000001",
      "entity_id": "fx-sanction-ivan",
      "category": "sanction",
      "categories": ["sanction"],
      "score": 1.0,
      "matched_name": "IVAN SANCTIONEXAMPLE",
      "listed_name": "Ivan Sanctionexample",
      "aliases": [],
      "birth_dates": ["1971-04-12"],
      "countries": [],
      "datasets": ["fixture_sanctions"],
      "features": ["name_exact", "birth_date_exact"],
      "provider_features": {},
      "disposition": "unreviewed",
      "prior_disposition": null,
      "prior_screening_id": null
    }
  ],
  "created_at": "2026-10-19T11:00:00Z",
  "updated_at": "2026-10-19T11:00:00Z"
}
```

Un échec fournisseur de la version 2 répond aussi `201`. Le dossier existe, `status` vaut `failed`, `outcome` est nul, `hits` est vide. Le client lit ces champs. Il ne déduit pas un `clear` du code HTTP.

L’appel est synchrone dans les deux versions. La version 1 ne fait pas d’entrée-sortie réseau. La version 2 garde la connexion le temps de l’appel OpenSanctions. Le client règle son délai sur 30 secondes.

### 10.2 Lire et lister

`GET /v1/screenings/{id}` renvoie le même corps.

`GET /v1/screenings` accepte `outcome`, `status`, `external_id`, `cursor` et `limit` (défaut 20, maximum 100). L’ordre est `created_at` décroissant, puis `id` décroissant. La réponse est `{ "data": [ ... ], "next_cursor": null }`. La liste est celle de l’intégration de la clé.

Le résumé de liste reprend l’identifiant, `external_id`, `status`, `outcome`, `engine`, le nombre de correspondances et les horodatages. Le détail des correspondances est sur la fiche.

### 10.3 Qualifier une correspondance

`POST /v1/screenings/{id}/hits/{hit_id}/disposition`

```json
{
  "disposition": "false_positive",
  "reason": "different_person",
  "note": "Date de naissance incompatible avec le client."
}
```

| Champ | Règle |
|---|---|
| `disposition` | `false_positive` ou `true_positive`. |
| `reason` | Obligatoire. Liste fermée ci-dessous. |
| `note` | Optionnelle. 1 à 500 caractères si elle est envoyée. |

| Qualification | Motifs |
|---|---|
| `false_positive` | `different_person`, `date_of_birth_conflict`, `nationality_conflict`, `name_only` |
| `true_positive` | `same_person`, `listed_alias` |

La réponse est le dossier complet, code `200`, avec l’issue recalculée. Une seconde qualification sur la même correspondance remplace la précédente et renvoie de nouveau le dossier. Les deux restent dans l’audit. Le dossier `failed` refuse la qualification avec `409` `invalid_status`.

L’acteur est la clé d’API, ou l’utilisateur de console. Le développeur et la lecture seule ne qualifient pas depuis la console. La clé d’API le peut : l’outil de conformité du client est un système de qualification au même titre que l’écran.

### 10.4 Antériorité

À la création, pour chaque correspondance, la plateforme cherche dans la même organisation un dossier `completed` antérieur dont le sujet normalisé est identique : nom complet, date, nationalité et numéro, les absents égalant les absents. S’il porte une qualification sur le même `entity_id`, la nouvelle correspondance reçoit `prior_disposition` et `prior_screening_id`.

La nouvelle qualification repart de `unreviewed`. L’antériorité informe l’analyste. Elle ne retire pas la correspondance et ne change pas l’issue.

## 11. Version 1 — moteur `aml-1`

La version 1 est le produit que l’intégrateur branche en premier. Le contrat des sections 5 à 10 est entier : création, décision dans la réponse, revue, console, rattachement à l’identité en mode test.

Le moteur applique le §7.3 au corpus `fixture-1`. Ce corpus est synthétique. Il est publié dans ce document et versionné avec le code. Il ne contient aucune personne réelle et aucune copie d’une liste officielle.

Une issue `clear` de la version 1 signifie que le sujet n’atteint pas le seuil dans ce corpus. Elle ne signifie pas que la personne est absente des sanctions ou des PEP.

Une clé `ky_test_` utilise toujours `aml-1`, y compris après la mise en service de la version 2. Une clé `ky_live_` n’est pas émise pour ce produit tant que `aml-2` n’est pas le moteur de production. La création d’une intégration live AML répond `409` `engine_unavailable`, que l’organisation ait ou non du crédit. Le garde du moteur est évalué avant le garde du solde.

### 11.1 Corpus `fixture-1`

| `entity_id` | Nom | Alias | Naissance | Nationalité | Numéro | Catégorie | Jeu |
|---|---|---|---|---|---|---|---|
| `fx-sanction-ivan` | Ivan Sanctionexample | — | 1971-04-12 | — | X1234567 | `sanction` | `fixture_sanctions` |
| `fx-pep-nora` | Nora Potentialexample | Nora P Example | 1982-11-03 | FR | — | `pep` | `fixture_peps` |
| `fx-rca-leo` | Leo Potentialexample | — | 2005-01-20 | FR | — | `rca` | `fixture_peps` |
| `fx-debar-mina` | Mina Debarexample | — | 1990 | CA | — | `debarment` | `fixture_debarment` |

L’année seule de Mina est une donnée d’entité. Le client, lui, envoie une date complète ou n’envoie rien.

### 11.2 Résultats de référence

Politique plateforme par défaut : revue 0,70, confirmation 0,95, catégories sanction, PEP, RCA et déchéance actives.

| Requête | Issue | Correspondance attendue |
|---|---|---|
| Ivan Sanctionexample, 1971-04-12 | `confirmed_match` | `fx-sanction-ivan`, score 1,000, `name_exact`, `birth_date_exact` |
| Ivan Sanctionexample, 1999-01-01 | `clear` | Année différente, score 0,150, sous le seuil |
| Ivan Sanctionexample, sans date | `potential_match` | Score 1,000, `birth_date_absent`, sanction sans trait fort |
| Ivan Sanctionexampl, 1971-04-12 | `confirmed_match` | Un caractère manquant sur un jeton d’au moins 5 lettres, nom encore exact au sens du §7.3, date exacte |
| Ivan Sanctionexample, 1999-01-01, numéro X1234567 | `confirmed_match` | `id_number_exact` relève le score à 1,000. Le conflit d’année reste dans les traits |
| Ivan Sanctionexample, 1971-04-12, numéro Z999 | `clear` | Facteur numéro 0,20, score 0,200 |
| Nora Potentialexample | `potential_match` | PEP, score 1,000, jamais confirmée d’office |
| Nora, nom intermédiaire P, nom Example | `potential_match` | Alias `NORA P EXAMPLE` |
| Nora Potentialexample, nationalité US | `potential_match` | Facteur 0,90, score 0,900, la PEP reste visible |
| Leo Potentialexample, 2005-01-20 | `potential_match` | RCA |
| Mina Debarexample, 1990-06-01 | `potential_match` | Année égale, `birth_year_match`, déchéance non confirmée d’office |
| Ada Clear, 1990-01-01 | `clear` | Aucune entité au seuil |
| first_name Ivan, sans nom de famille exploitable | `400` | Le dossier n’est pas créé |

Ces lignes sont le jeu de tests de non-régression du moteur. Une modification qui en déplace une est un changement de corpus ou d’algorithme, donc une nouvelle version, pas un ajustement silencieux.

## 12. Version 2 — moteur `aml-2`

La version 2 ouvre le live. Le corps d’appel, les issues, les catégories, les seuils, la revue et la livraison dans la réponse restent ceux des sections 5 à 10.

Le moteur envoie le sujet à l’API de correspondance OpenSanctions, retient les entités selon la politique, et applique les bandes du §7. Le booléen `match` renvoyé par OpenSanctions n’est pas lu comme une décision. Un score fournisseur au-dessus de notre seuil de revue est une correspondance, que leur booléen soit vrai ou faux. Un score en dessous est écarté, que leur booléen soit vrai ou faux.

### 12.1 Appel fournisseur

| Paramètre | Valeur |
|---|---|
| URL | `https://api.opensanctions.org/match/default` |
| Méthode | `POST` |
| Authentification | `Authorization: ApiKey`, secret de plateforme, absent des dossiers, des journaux et de la console client |
| `limit` | 20 |
| `threshold` | Le seuil de revue de la politique figée sur le dossier |
| `algorithm` | L’identifiant épinglé dans la version de moteur `aml-2`, choisi parmi les algorithmes logiques documentés par OpenSanctions au moment du gel. Il est recopié sur le dossier. En changer publie `aml-3`. |

Un seul sujet par appel, sous la clé de requête `q1`, schéma `Person`. Les propriétés vides ne sont pas envoyées.

```json
{
  "queries": {
    "q1": {
      "schema": "Person",
      "properties": {
        "name": ["Nora Potentialexample"],
        "birthDate": ["1982-11-03"],
        "nationality": ["fr"],
        "country": ["ca"],
        "idNumber": ["X1234567"]
      }
    }
  }
}
```

`name` est le nom complet avant la normalisation de comparaison, tel que le client l’a envoyé, espaces internes simples. `nationality` et `country` partent en minuscules. `birthDate` part tel quel. `country` reçoit le pays de résidence.

### 12.2 Ce qui est retenu

Pour chaque résultat de `q1` :

- le schéma est `Person` ;
- `target` est vrai ;
- `id` et `score` sont présents ;
- le score arrondi à trois décimales atteint le seuil de revue ;
- au moins une catégorie du §6 est active dans la politique figée.

Une entité qui échoue à l’une des trois premières lignes, alors que le fournisseur l’a renvoyée au-dessus du seuil demandé, rend le dossier `failed` avec `provider_invalid_response`. On ne conclut pas `clear` en ayant jeté une ligne illisible.

Une entité lisible dont la catégorie n’est pas active, ou dont le thème ne mappe vers rien, est ignorée. Ce n’est pas une erreur. Le crime inactif disparaît ainsi, sans masquer une sanction portée par la même entité.

Le score affiché est le score fournisseur arrondi. Les traits du §7.1 sont recalculés par nous à partir du nom, des alias, des dates, des nationalités et des numéros de l’entité. `provider_features` conserve la carte numérique renvoyée par OpenSanctions, clés limitées à `[a-z0-9_]`. Une clé ou une valeur d’une autre forme est omise de cette carte. Elle ne fait pas échouer le dossier.

Sont copiés sur la correspondance : identifiant, légende comme `listed_name`, alias bornés à vingt, dates de naissance, pays, jeux de données (`datasets`), thèmes mappés. Le reste du JSON fournisseur n’est pas renvoyé au client.

`corpus_version` vaut `opensanctions`. `retrieved_at` est l’instant de la réponse. Rejouer la décision sur les correspondances stockées et la politique figée redonne la même issue. Un second `POST` est une nouvelle lecture de l’index.

### 12.3 Délais et échecs

| Situation | Comportement |
|---|---|
| Délai d’un appel | 8 secondes |
| `429`, `502`, `503`, `504` | Une reprise, puis `failed` / `provider_unavailable` ou `provider_timeout` |
| `401` ou `403` fournisseur | Pas de reprise. `failed` / `provider_unavailable`. Alerte plateforme : la clé de service est refusée |
| Autre `4xx`, corps illisible, objet de réponse absent, tableau de résultats absent | `failed` / `provider_invalid_response` |
| HTTP 200 et résultats vides | Dossier `completed`, issue `clear` |
| Réussite partielle | Il n’y en a pas. Un seul appel `default`. Son échec est l’échec du dossier |

Aucun de ces cas ne débite. Le dossier `failed` est créé et renvoyé en `201`, sauf les refus d’avant création (corps invalide, crédit, clé).

Rejouer la même `Idempotency-Key` renvoie ce dossier en échec. Une nouvelle tentative est une nouvelle clé.

### 12.4 Ce que la version 2 ne prétend pas

OpenSanctions couvre des listes de sanctions, des PEP, des proches et des déchéances, selon les jeux présents dans la collection `default` au moment de l’appel. La plateforme ne publie pas un nombre de listes, ni une promesse de couverture mondiale, ni une équivalence avec une base de médias adverses.

Le jeu réellement consulté est celui de la collection `default` à `retrieved_at`. Les `datasets` de chaque correspondance sont la source que l’analyste peut citer.

## 13. Politique

Une politique par organisation, pour le produit AML, partagée par ses intégrations. La publication crée une version. L’ancienne reste lisible. Le dossier fige la version active à sa création.

Tant que personne n’a publié, la version effective est `aml-policy-0` : seuils du §7, sanction, PEP, RCA et déchéance actives, crime inactif, rattachement à l’identité inactif.

| Réglage | Règle de publication |
|---|---|
| Catégories | Sanction toujours présente. PEP, RCA, déchéance et crime peuvent être retirés ou remis. Toute autre catégorie, y compris `adverse_media`, est refusée. |
| Seuil de revue | 0,50 à 0,85. |
| Seuil de confirmation | 0,90 à 1,00, strictement supérieur au seuil de revue. |
| `attach_to_identity` | Booléen. Défaut faux. |
| `on_match` | `ignore`, `review` ou `block`. Défaut `review`. Lu seulement si le rattachement est actif. |
| `test_integration_id`, `live_integration_id` | Intégrations AML de cette organisation, du mode correspondant. Requis au moment du rattachement, pas forcément à la publication. |

Publier est réservé au propriétaire et à l’administrateur. Les autres rôles lisent la politique effective et la liste des versions.

L’onglet Règles du service Identité ne porte pas ces réglages. Il continue de décrire la décision d’identité. L’onglet Règles du service AML est leur éditeur.

## 14. Rattachement à l’identité

Le rattachement suit la lecture réussie de la pièce. Le sujet est l’identité extraite : prénom, nom, date de naissance, nationalité, numéro. Si un de ces champs manque sur la pièce, le criblage part avec ceux qui sont là, pourvu que le prénom et le nom existent. S’il manque le nom ou le prénom, il n’y a pas de criblage : la vérification part en revue, motif `aml_unavailable`.

| Politique | Effet sur la vérification d’identité |
|---|---|
| `ignore` | L’identité tranche sans attendre. Le criblage est créé si l’intégration AML du mode existe. S’il ne peut pas l’être, l’identité tranche quand même. |
| `review` | L’identité attend un dossier `completed` ou `failed`. `clear` : l’identité continue sur ses propres règles. `potential_match` ou `confirmed_match` : revue, motif `aml_potential_match` ou `aml_confirmed_match`. `failed`, intégration absente ou sujet inexploitable : revue, motif `aml_unavailable`. |
| `block` | Même attente. `confirmed_match` : refus, motif `aml_confirmed_match`. `potential_match` : revue, motif `aml_potential_match`. `clear` : l’identité continue. Échec ou absence : revue, motif `aml_unavailable`. |

Le criblage créé porte le `verification_id`. La vérification porte `aml_screening_id` dès que le dossier AML existe. L’issue AML est sur ces deux dossiers, lus par `GET`.

Le mode est celui de la vérification. Une vérification test crée un criblage `aml-1` sur l’intégration AML test désignée. Une vérification live crée un criblage `aml-2` sur l’intégration AML live. Il n’y a pas de croisement.

Une qualification AML postérieure à la décision d’identité met à jour le criblage. La réponse de qualification porte la nouvelle issue. Elle ne réécrit pas la décision d’identité.

Le débit d’une vérification live et le débit d’un criblage live sont deux unités, sur deux produits. Le rattachement ne les fusionne pas.

## 15. Livraison

La décision voyage dans le corps de l’appel qui l’a produite.

| Appel | Corps renvoyé |
|---|---|
| `POST /v1/screenings` | Dossier complet : `status`, `outcome`, `failure_reason`, correspondances |
| `POST /v1/screenings/{id}/hits/{hit_id}/disposition` | Le même dossier, issue recalculée |
| `GET /v1/screenings/{id}` | Le dossier persisté |

Un `201` dont `status` vaut `failed` est un échec déjà livré. Un `201` dont `outcome` vaut `clear` est un écart déjà livré. Le client lit ces champs avant de continuer.

`GET` sert à relire un dossier déjà rendu, ou à le consulter depuis la console.

## 16. Console

Langue : français. Le service AML cesse d’être une annonce lorsque la version 1 est exposée.

| Écran | Contenu |
|---|---|
| Liste | Date, issue, référence client, catégories des correspondances, moteur. Filtre par issue. Liste vide : expliquer qu’un criblage se crée par l’API ou par une vérification rattachée. |
| Fiche | Sujet, issue, version de politique, moteur, corpus, horodatage de lecture. Chaque correspondance : nom listé, score, catégorie, jeux de données, traits, qualification, antériorité. Actions de qualification pour les rôles autorisés. |
| Règles | Politique effective, publication, historique des versions. |
| Intégrations | Clé et mode, produit `aml`. L’installation décrit `POST /v1/screenings`, le corps de réponse, la lecture et la qualification. |

| Rôle | Lire les dossiers et la politique | Qualifier | Publier la politique | Gérer l’intégration |
|---|---|---|---|---|
| Propriétaire, administrateur | Oui | Oui | Oui | Oui |
| Membre | Oui | Oui | Non | Non |
| Développeur | Oui | Non | Non | Oui |
| Lecture seule | Oui | Non | Non | Non |

Le développeur branche l’API. Il ne qualifie pas un dossier. Le membre qualifie. Il ne voit pas les secrets.

## 17. Données

Le client est responsable du traitement du sujet qu’il envoie. Recogniz-Me traite ce sujet pour rendre le criblage, et conserve le dossier pendant la vie de l’organisation. La suppression de l’organisation emporte ses criblages.

La durée légale de conservation d’un contrôle LCB-FT, lorsqu’elle s’applique, est celle du client. La plateforme ne la transforme pas en une durée de son cru, et ne refuse pas une suppression de compte au nom de cette durée.

Les requêtes ne servent pas à entraîner un modèle. La clé OpenSanctions n’est pas une donnée client.

Le sujet et les correspondances sont lisibles par l’API et par la fiche, parce que l’analyste doit pouvoir motiver l’issue. Ils ne sont pas recopiés dans les journaux techniques ni dans le journal d’audit. L’audit porte l’acteur, l’action, l’identifiant de dossier, l’identifiant de correspondance, l’ancienne et la nouvelle qualification, le motif.

Actions auditées : `screening.created`, `screening.failed`, `screening.disposition_set`, `aml_policy.published`.

## 18. Crédit

| Situation | Débit |
|---|---|
| Intégration test, moteur `aml-1` | Aucun |
| Live, dossier `completed` | Une unité du produit `aml`, après l’issue, pas avant l’appel fournisseur |
| Live, dossier `failed` | Aucun |
| Qualification, seconde qualification, rejeu d’idempotence | Aucun |
| Live sans solde | `403` `insufficient_credit`, aucun dossier |

Le garde `engine_unavailable` de la version 1 empêche de créer l’intégration live. Il est évalué avant le solde, afin qu’un crédit ne donne pas l’impression d’acheter un criblage sur liste officielle.

## 19. Erreurs

Les codes sont stables. Le client branche dessus, pas sur le texte.

| Code | HTTP | Quand |
|---|---|---|
| `unsupported_subject` | 400 | Type autre que `person`. |
| `required` | 400 | Prénom ou nom manquant. |
| `size` | 400 | Nom hors bornes, ou réduit à une initiale. |
| `invalid` | 400 | Date, pays, type de pièce, motif de qualification. |
| `idempotency_key_conflict` | 409 | Même clé d’idempotence, autre corps. |
| `invalid_status` | 409 | Qualification d’un dossier `failed`. |
| `engine_unavailable` | 409 | Intégration live, ou criblage live, avant la mise en service de `aml-2`. |
| `insufficient_credit` | 403 | Live sans solde, moteur `aml-2` en service. |
| Absence de dossier ou de correspondance dans l’intégration | 404 | Identifiant inconnu ici. |
| Clé absente, révoquée, ou d’un autre produit | 401 | — |

Les échecs OpenSanctions ne sont pas des codes HTTP séparés. Ils sont un dossier `failed` avec `failure_reason`.

## 20. Mise en production

Liste que l’équipe client coche avant un criblage de personnes réelles. Elle n’a de sens qu’à partir de la version 2.

1. Une intégration AML `live` existe, distincte de celle de test. Le solde couvre au moins un criblage. Le moteur en service est `aml-2`.
2. La clé `ky_live_` est sur le serveur du client.
3. Le client lit `outcome` et `status` dans le corps de la réponse. Il ne traite pas un `201` comme un `clear`.
4. Le même `Idempotency-Key` et le même corps renvoient le dossier déjà livré, sans second débit.
5. Un dossier `failed` dans cette réponse est une alerte chez le client, pas une acceptation.
6. La politique publiée est celle voulue : catégories, seuils, rattachement. Elle est figée par dossier.
7. Les treize lignes du §11.2 ont été rejouées sur l’intégration test. Chaque réponse porte l’issue attendue.
8. Une qualification de faux positif sur Nora Potentialexample renvoie un dossier dont l’issue est passée de `potential_match` à `clear`.
9. Si le rattachement est actif : une vérification test dont la pièce porte un nom du corpus produit un `aml_screening_id`, et la décision d’identité suit la politique `on_match`.

## 21. Critères d’acceptation

### Version 1

1. Les treize lignes du §11.2 produisent l’issue et les traits indiqués, à l’octet de score près.
2. Rejouer la même requête avec la même politique et `fixture-1` produit les mêmes correspondances, dans le même ordre.
3. Une clé `ky_test_` n’ouvre aucune connexion vers OpenSanctions et ne débite pas.
4. Créer une intégration AML live répond `engine_unavailable`, y compris avec un solde positif.
5. Même `Idempotency-Key` et même corps : même dossier. Autre corps : `idempotency_key_conflict`.
6. Écarter l’unique correspondance PEP de Nora Potentialexample renvoie un dossier `clear`. Une seconde qualification renvoie de nouveau le dossier.
7. Un dossier d’une autre organisation répond `404`.
8. Les journaux de ces parcours ne contiennent pas le nom, la date, le numéro, ni un secret.
9. Une clé Identité reçoit `401` sur `POST /v1/screenings`.
10. Avec le rattachement `review` et une intégration AML test désignée, une vérification test dont la pièce est Nora Potentialexample part en revue avec `aml_potential_match` et un `aml_screening_id`.

### Version 2

1. Les critères 5 à 10 de la version 1 tiennent encore. Le critère 3 tient pour toute clé test.
2. Une clé live avec solde crée un dossier `aml-2`. Le score, les jeux de données et `retrieved_at` viennent de la réponse OpenSanctions. L’issue est calculée par la politique Recogniz-Me.
3. Une réponse fournisseur illisible, un délai dépassé après reprise, ou un refus d’authentification produit un dossier `failed`, sans débit, jamais `clear`.
4. Une entité dont le seul thème est `crime`, politique par défaut, n’apparaît pas. Une entité `sanction` et `crime` apparaît, catégorie primaire `sanction`.
5. Une PEP à score 1,000 reste `potential_match` tant qu’un analyste ne l’a pas qualifiée.
6. Rejouer la décision sur le dossier stocké, sans rappeler le fournisseur, redonne la même issue.
7. La clé OpenSanctions n’apparaît pas dans le dossier, la console, ni les journaux.
8. Un live sans solde répond `insufficient_credit` et ne crée pas de dossier.
9. Le rattachement d’une vérification live appelle `aml-2` sur l’intégration AML live, et n’appelle pas le moteur de test.

## 22. Phasage

| Version | Moteur | Ce qui est livré | Ce qui reste fermé |
|---|---|---|---|
| 1 | `aml-1` | API, décision dans la réponse, corpus `fixture-1`, revue, console, politique, rattachement des vérifications test | Live, OpenSanctions, débit |
| 2 | `aml-2` | Le même contrat sur l’API OpenSanctions. Live, débit, rattachement des vérifications live | Médias adverses, personnes morales, surveillance continue |

La surveillance continue, le criblage des personnes morales et les médias adverses demandent chacun une source et une politique. Ils ne s’ajoutent pas en paramètre de `aml-2`.

## 23. Décisions figées

| Sujet | Décision |
|---|---|
| Forme du produit | Un appel synchrone. La décision est le corps de la réponse. |
| Issues | `clear`, `potential_match`, `confirmed_match`. L’échec est un statut, pas une quatrième issue. |
| Juge | La politique Recogniz-Me. Le fournisseur fournit un score et des fiches. |
| Confirmation automatique | Sanctions seulement, avec date complète et nom exact, ou numéro et nom encore reconnaissable. |
| PEP, RCA, déchéance, crime | Revue humaine, quel que soit le score. |
| Version 1 | Corpus synthétique `fixture-1`. Résultat reproductible. Pas une liste officielle. |
| Version 2 | OpenSanctions, collection `default`, algorithme épinglé dans `aml-2`. |
| Test | Toujours `aml-1`, sans débit, sans appel fournisseur. |
| Live avant `aml-2` | Refusé. `engine_unavailable` avant le contrôle du solde. |
| Médias adverses | Hors des deux versions. |
| Écart analyste | Local au couple sujet + entité. Jamais une suppression globale. |
| Identité | Le criblage peut contraindre la décision IDV. Il ne la remplace pas. Une revue AML tardive ne la réécrit pas. |
| Score | Rang de ressemblance, trois décimales, pas une probabilité. |
