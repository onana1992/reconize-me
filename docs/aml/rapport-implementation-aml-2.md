# Rapport d’implémentation — moteur `aml-2`

**Date :** 25 octobre 2026.

Le moteur live est branché. La clé OpenSanctions est dans `api/application-secrets.properties` (`kyc.opensanctions.enabled=true`). Ce fichier est ignoré par git. La clé n’est pas dans le code, ni dans `application.properties`, ni dans ce rapport.

## Ce qui est en service

Une intégration AML **live**, avec du crédit, appelle `POST https://api.opensanctions.org/match/default`.

| Réglage | Valeur |
|---|---|
| Authentification | `Authorization: ApiKey`, secret de plateforme |
| Collection | `default` |
| Algorithme | `logic-v2` |
| `limit` | 20 |
| `threshold` | Seuil de revue de la politique figée |
| Délai | 8 secondes, une reprise sur `429`, `502`, `503`, `504` |
| `401` / `403` | Pas de reprise. Dossier `failed` / `provider_unavailable`. Journal `opensanctions.auth_rejected` sans la clé |
| Moteur du dossier | `aml-2` |
| Corpus | `opensanctions` |
| `retrieved_at` | Instant de la réponse lisible |

Le corps public ne change pas. Le sujet part en schéma `Person`, clé de requête `q1`, propriétés `name`, `birthDate`, `nationality`, `country`, `idNumber`. Les vides ne sont pas envoyés.

Le score fournisseur est arrondi à trois décimales. Le booléen `match` n’est pas lu. Les thèmes sont rangés comme au cahier §6. Les traits du §7.1 sont recalculés ici, légende et alias compris. Une sanction se confirme seulement avec un trait fort. PEP, RCA, déchéance et crime ne se confirment pas seuls.

Une fiche au-dessus du seuil sans identifiant, sans score, qui n’est pas une `Person` ou dont `target` est faux rend le dossier entier `failed` / `provider_invalid_response`. Aucun débit.

Le débit d’une unité `aml` est écrit après `status = completed`, sur la ressource `SCREENING`. Un dossier `failed`, une qualification et un rejeu d’idempotence ne débitent pas. Sans solde, la réponse est `403` `insufficient_credit` et aucun dossier n’est créé.

## Ce qui ne change pas

Une clé `ky_test_` reste sur `aml-1` et le corpus local. Elle n’ouvre pas OpenSanctions et ne débite rien. Les tests Spring forcent `kyc.opensanctions.enabled=false`, donc la création d’une intégration live y répond encore `409` `engine_unavailable`.

La console propose le mode live pour AML lorsque le crédit le permet. Sans crédit, le choix reste fermé et le lien vers la facturation s’affiche.

Le rattachement d’une vérification live utilise `live_integration_id` et `aml-2`. Sans moteur, sans intégration ou sans crédit, la vérification part en revue `aml_unavailable`. Une qualification AML ne réécrit pas la décision d’identité.

## Fichiers

| Fichier | Rôle |
|---|---|
| `OpenSanctionsMatchClient` | Appel HTTP. Présent seulement si `kyc.opensanctions.enabled=true`. |
| `RejectingSanctionsMatch` | Reste le bean tant que le moteur est éteint. |
| `Aml2Decider` | Décision, alias, dates, nationalités, numéros. |
| `ScreeningService` | Chemin live, débit, `retrieved_at`. |
| `CreditService.debitForLiveScreening` | Unité `aml`. |
| `IntegrationService` | L’intégration live AML n’est plus refusée quand le moteur est en service. |
| `AmlAttachmentService` | Rattachement live. |
| `V44__screening_match_algorithm.sql` | Colonne `screenings.match_algorithm`. |

## Preuve

Les tests `AmlEngineTest`, `Aml2DeciderTest` et `OpenSanctionsMatchClientTest` ont été exécutés hors du répertoire `target` de l’API déjà lancée. Le client local a montré :

- `401` : une seule tentative, `provider_unavailable` ;
- score `0.91` avec `match: false` : la fiche est gardée ;
- `429` puis `200` : deux tentatives, dossier lisible.

Aucune assertion en échec n’a été imprimée. Le processus de test ne s’est pas terminé tout seul et a été arrêté. L’appel réel vers OpenSanctions n’a pas été fait dans ces tests.

## Pour s’en servir

Relancer l’API depuis l’éditeur. Flyway ajoute `match_algorithm`, et le processus charge la clé locale. Créer une intégration AML **live** (crédit requis), puis lancer un criblage. Le dossier doit porter `engine = aml-2`, `corpus_version = opensanctions` et un `retrieved_at`. Une clé de test lancée ensuite doit rester sur `aml-1`.
