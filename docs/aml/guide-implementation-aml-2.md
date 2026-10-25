# Guide d’implémentation — moteur `aml-2`

**Statut :** à construire. A1 à A8 sont en place. Ce guide décrit A9, l’appel réel OpenSanctions. Il ne le réalise pas.

**Contrat :** le [cahier des charges](cahier-des-charges-aml.md) gagne sur ce guide et sur la [spécification détaillée](specification-detaillee-aml.md). Le palier est [A9 de la roadmap](roadmap-implementation-aml.md).

**Documentation fournisseur, lue pour ce gel :**

- [The matching API](https://www.opensanctions.org/docs/api/matching/)
- [The request](https://www.opensanctions.org/docs/api/request/)
- [The response](https://www.opensanctions.org/docs/api/response/)
- [Quickstart](https://www.opensanctions.org/docs/api/quickstart/)
- Collection : [default](https://www.opensanctions.org/datasets/default/)

Le corps public `POST /v1/screenings` ne change pas. Le client Recogniz-Me envoie toujours un sujet. Il reçoit toujours un dossier `clear`, `potential_match`, `confirmed_match`, ou `status = failed`.

## 1. Ce qui existe déjà

| Pièce | Rôle aujourd’hui |
|---|---|
| `SanctionsMatchPort` | Sujet, seuil, candidats, ou échec typé. Aucun socket. |
| `Aml2Decider` | Ignore le booléen `match`. Arrondit le score. Mappe les thèmes. Confirme une sanction seulement avec un trait fort. Une PEP à 1,000 reste `potential_match`. Une fiche illisible au-dessus du seuil rend `provider_invalid_response`. |
| `RejectingSanctionsMatch` | Seul bean du port. Il refuse l’appel. |
| `ScreeningService`, `IntegrationService` | Une intégration ou une clé live AML répond `409` `engine_unavailable` avant le contrôle du solde. |
| Clé `ky_test_` | Reste sur `aml-1` / `fixture-1`. Zéro débit. Zéro OpenSanctions. |
| Tables | `screenings.retrieved_at`, `screening_hits.datasets_json`, `features_json`, `provider_features_json` existent déjà. |

Le décideur n’est pas encore branché sur `ScreeningService`. Le client HTTP n’existe pas.

## 2. Gel du moteur

| Champ du dossier | Valeur |
|---|---|
| `engine` | `aml-2` |
| `corpus_version` | `opensanctions` |
| `retrieved_at` | Instant UTC de la réponse HTTP réussie. Nul si le dossier est `failed` avant une réponse lisible. |
| Algorithme | `logic-v2` |

La documentation OpenSanctions du 25 octobre 2026 dit que `algorithm=best` vaut aujourd’hui `logic-v2`, et conseille d’épingler l’identifiant. On envoie `logic-v2`. On n’envoie pas `best` : cet alias peut changer sous nos pieds. Changer cette constante publie `aml-3`, pas un correctif de `aml-2`.

La constante est recopiée sur la ligne `screenings` (colonne nouvelle, migration `V44`). Le JSON public garde `engine = aml-2`. Le client ne choisit pas l’algorithme.

## 3. L’appel

`OpenSanctionsMatchClient` implémente `SanctionsMatchPort`.

```
POST https://api.opensanctions.org/match/default?limit=20&threshold={revue}&algorithm=logic-v2
Authorization: ApiKey {kyc.opensanctions.api-key}
Content-Type: application/json
```

`threshold` est le seuil de revue de la politique figée sur le dossier, avec trois décimales. La collection est `default`. On n’envoie ni `topics`, ni `include_dataset`, ni `exclude_dataset` : le filtre de catégories est le nôtre, après la réponse. Un filtre fournisseur retirerait une fiche avant que la politique puisse garder sa sanction et écarter son crime.

Un seul sujet, clé de requête `q1`, schéma `Person`. Les propriétés vides sont absentes du JSON. Chaque valeur est une liste d’une chaîne, comme l’exige FollowTheMoney.

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

| Propriété | Source |
|---|---|
| `name` | Prénom, nom intermédiaire et nom, tels que le client les a envoyés, joints par un espace. Les vides sont omis. |
| `birthDate` | `YYYY-MM-DD`, inchangé. |
| `nationality` | ISO2 en minuscules. |
| `country` | Pays de résidence, ISO2 en minuscules. Il part chez le fournisseur. Il ne change pas notre score et ne retire pas une ligne. |
| `idNumber` | Numéro tel quel. |

La documentation fournisseur préfère `firstName` et `lastName` pour la qualité du score. Le contrat Recogniz-Me envoie `name`. On ne diverge pas de là dans `aml-2`.

La clé API est un secret de plateforme, `kyc.opensanctions.api-key`. Elle n’est pas une donnée client. Elle n’entre ni dans le dossier, ni dans la console, ni dans les journaux, ni dans l’audit.

Délai d’un appel : 8 secondes. Le client HTTP de l’intégrateur, lui, reste réglé sur 30 secondes.

## 4. Lecture de la réponse

L’enveloppe officielle est `responses.<clé>.results`, plus `limit`. Pour nous la clé est `q1`.

Chaque élément de `results` a, entre autres : `id`, `caption`, `schema`, `properties`, `datasets`, `target`, `score`, `explanations`, `match`.

`score` est une confiance d’identité, entre 0 et 1. Ce n’est pas un score de risque. On le lit comme un `BigDecimal` depuis le texte JSON, jamais via un `double`. On l’arrondit à trois décimales, demi supérieur, et c’est cette valeur qui affronte les seuils.

Le booléen `match` est ignoré. OpenSanctions le pose quand le score atteint *leur* seuil. Notre seuil de revue décide si la fiche entre.

### 4.1 Une fiche illisible fait échouer le dossier

Si le fournisseur a renvoyé une ligne dont le score arrondi atteint le seuil demandé, et qu’il manque l’`id`, le `score`, le schéma `Person` ou `target = true`, le dossier entier est `failed` / `provider_invalid_response`. `hits` est vide. On ne fabrique pas un `clear` en jetant une ligne qu’on n’a pas comprise.

Même issue si l’enveloppe est illisible : `responses` absent, `q1` absent, `results` absent ou pas un tableau, `status` de la requête différent de 200, autre `4xx` que 401 et 403.

### 4.2 Ce qui est copié

| Champ Recogniz-Me | Source OpenSanctions |
|---|---|
| `entity_id` | `id` (`NK-…`). Stable d’un criblage à l’autre. |
| `listed_name` | `caption` |
| `aliases` | `properties.alias`, vingt au plus, dans l’ordre reçu |
| `birth_dates` | `properties.birthDate` |
| `countries` | `properties.nationality`, puis `citizenship`, puis `country` |
| `datasets` | `datasets` |
| catégories | `properties.topics`, table du cahier §6 |
| `provider_features` | scores numériques de `explanations`, clés `[a-z0-9_]` seulement |

`explanations.name_match.score` devient `provider_features.name_match`. Une clé ou une valeur d’une autre forme est omise. Elle ne fait pas échouer le dossier. Le texte `detail`, la légende et le reste du JSON ne sont pas renvoyés au client.

On n’appelle pas `/entities/{id}` ni `/statements` dans cette version.

### 4.3 Thèmes

| Thème | Catégorie |
|---|---|
| `sanction`, `sanction.linked`, `asset.frozen` | `sanction` |
| `role.pep` | `pep` |
| `role.rca` | `rca` |
| `debarment` | `debarment` |
| `crime`, `crime.*` | `crime` |

Un thème absent de cette table est ignoré. S’il ne reste aucune catégorie active dans la politique figée, la fiche est écartée. Ce n’est pas une erreur. Une fiche qui porte `sanction` et `crime` garde les deux ; `category` reste `sanction`.

Au plus dix correspondances sont conservées, score décroissant, puis `entity_id`.

## 5. Traits et issue

Le score affiché est celui du fournisseur, arrondi. Les traits du cahier §7.1 sont recalculés par `DeterministicAmlEngine`, pas par `explanations`.

`Aml2Decider` compare aujourd’hui le sujet au premier nom, à la première date, à la première nationalité et au premier numéro. Avant de brancher le réseau, il doit parcourir la légende et chaque alias (vingt au plus), chaque date, chaque nationalité et chaque numéro. La variante de nom retenue est celle du score de nom le plus haut. Égalité : la légende, puis les alias dans l’ordre reçu. `matched_name` est cette variante.

La confirmation automatique reste celle du cahier :

- `sanction` est dans les catégories et active dans la politique ;
- le score arrondi atteint le seuil de confirmation ;
- `name_exact` et `birth_date_exact`, ou `id_number_exact` avec un score de nom d’au moins 0,50.

PEP, RCA, déchéance et crime ne se confirment jamais seuls. Une liste vide, ou des fiches toutes écartées par la politique, donne `completed` / `clear`.

Rejouer la décision sur les correspondances déjà enregistrées et la politique figée redonne la même issue, sans rappeler OpenSanctions. Un nouveau `POST` est une nouvelle lecture de l’index, avec son propre `retrieved_at`.

## 6. Échecs

| Situation | Résultat |
|---|---|
| Délai de 8 secondes | Une reprise, puis `failed` / `provider_timeout` |
| `429`, `502`, `503`, `504` | Une reprise, puis `failed` / `provider_unavailable` |
| `401`, `403` | Pas de reprise. `failed` / `provider_unavailable`. Alerte plateforme : la clé de service est refusée |
| Corps illisible, requête `q1` absente, résultat au-dessus du seuil illisible | `failed` / `provider_invalid_response` |
| HTTP 200 et `results` vide | `completed` / `clear` |

La reprise est unique. Il n’y a pas de deuxième collection de repli.

Le dossier `failed` est créé et renvoyé en `201`. `outcome` est nul. Aucun débit. La même `Idempotency-Key` renvoie ce dossier. Une nouvelle tentative est une nouvelle clé.

## 7. Branchement

La clé `ky_test_` ne reçoit pas le client HTTP. Un test dédié échoue si le chemin `aml-1` résout `OpenSanctionsMatchClient`.

La clé `ky_live_` n’existe pour le produit `aml` que lorsque ce client est le bean en service. Jusque-là, `RejectingSanctionsMatch` reste en place et `engine_unavailable` reste devant le solde.

Quand le client est en service :

1. Créer l’intégration live AML devient possible.
2. Sans solde : `403` `insufficient_credit`, aucun dossier, aucun appel.
3. Avec solde : l’appel part. Une unité du produit `aml` est débitée seulement après `status = completed`.
4. `failed`, qualification et rejeu d’idempotence ne débitent pas.
5. L’unité `aml` ne se confond pas avec l’unité `identity`.

Le rattachement d’une vérification live utilise `live_integration_id` et `aml-2`. Il ne retombe pas sur `fixture-1`. Le sujet reste l’identité extraite de la pièce. Une qualification AML ultérieure ne réécrit pas la décision d’identité.

Journaux : `screening_id`, `integration_id`, `outcome`, nombre de correspondances, durée, statut HTTP fournisseur. Jamais le nom, la date, le numéro, la légende, ni la clé.

## 8. Plan de preuve

Sans socket, avec un faux HTTP :

- liste vide → `clear` ;
- PEP à score 1 → `potential_match` ;
- sanction et date exacte et `name_exact` → `confirmed_match` ;
- `match: false` avec un score au-dessus du seuil de revue → la fiche entre quand même ;
- `match: true` avec un score sous le seuil → la fiche n’entre pas ;
- fiche au-dessus du seuil sans `id`, ou `schema` autre que `Person`, ou `target` faux → `failed`, `hits` vide ;
- thème inconnu seul → ligne absente, dossier `clear` si rien d’autre ;
- `crime` seul et crime inactif → ligne absente ; `sanction` + `crime` → primaire `sanction` ;
- `429` puis succès → un dossier `completed`, deux tentatives ;
- `401` → une tentative, `provider_unavailable` ;
- délai → `provider_timeout`.

Puis un seul appel réel, sujet convenu à l’avance, dossier `aml-2` avec `retrieved_at` et des `datasets`. Coût : un appel du plan OpenSanctions et, si le dossier est `completed`, une unité `aml`.

Ensuite, rejouer la suite A3 avec une clé `ky_test_` : zéro ligne du client OpenSanctions, zéro débit.

## 9. Hors de cette version

Médias adverses, sociétés, bénéficiaires effectifs, surveillance continue de l’index, clé OpenSanctions apportée par le client, deuxième appel `/entities` ou `/statements`, et réécriture d’une décision d’identité après une qualification.
