# Cahier des charges — Module Intégration

**Produit :** Recogniz-Me, Identity & Document Verification  
**Module :** Intégration  
**Version du document :** 1.0  
**Date :** 17 octobre 2026  
**Statut :** spécification produit — contrat d’intégration client  
**Documents liés :**

- [`specification-fonctionnelle-idv.md`](./specification-fonctionnelle-idv.md) — ce que prouve une vérification
- [`specification-regles-idv.md`](./specification-regles-idv.md) — politique de décision figée sur le dossier
- [`../MVP/specification-m5-aws-live-webhook.md`](../MVP/specification-m5-aws-live-webhook.md) — livraison webhook actuelle

Ce document fixe le contrat par lequel une entreprise branche Recogniz-Me dans son produit. Il décrit l’intégration comme une unité d’exploitation isolée : identifiants, environnement, session, parcours utilisateur, notification de décision, et conditions de passage en production.

Le niveau visé est celui d’un fournisseur d’identité utilisé en production par des équipes d’ingénierie : une clé ne décide rien, un webhook signé est la source de vérité, le bac à sable ne coûte rien et ne touche aucun fournisseur, et la mise en production est un geste explicite.

---

## 1. Objet

Une intégration est la configuration nommée par laquelle une organisation consomme le produit Identity.

Elle porte, et elle seule :

- l’environnement (`test` ou `live`) ;
- la clé secrète qui crée et lit les sessions ;
- l’endpoint qui reçoit la décision ;
- le parcours ouvert à l’utilisateur final (redirection aujourd’hui, cadre intégré ensuite) ;
- l’ensemble des vérifications créées avec cette clé.

L’organisation peut en avoir plusieurs. Chacune correspond à une surface réelle : site web, application mobile, partenaire, environnement de recette. Une clé d’une intégration ne voit pas les dossiers d’une autre.

L’intégration ne porte pas la politique de décision. Les pièces acceptées, les seuils et la version de règles vivent dans le module Règles. Une vérification créée par l’intégration fige la version de politique active à cet instant.

## 2. Promesse envers l’intégrateur

| Engagement | Conséquence |
|---|---|
| La décision n’est jamais dans le navigateur de l’utilisateur final | La clé secrète reste sur le serveur du client. Le parcours hébergé ne porte qu’un jeton à durée de vie courte. |
| Le webhook est la notification. `GET` est la réconciliation | Un `2xx` tardif, un retry ou un double envoi ne changent pas la décision. L’intégrateur déduplique sur l’identifiant d’événement. |
| Test et live sont deux intégrations | Une clé `ky_test_` ne crée aucun appel fournisseur et ne débite aucun crédit. Une clé `ky_live_` ne fonctionne pas sans solde. |
| Le bac à sable est déterministe | Les mêmes entrées de test produisent les mêmes décisions, sans pièce réelle. |
| Une intégration révoquée cesse d’émettre | Révoquer la clé coupe la création. Les dossiers déjà ouverts restent lisibles par la console, selon les droits. |

## 3. Périmètre

### 3.1 Dans le module

- Création, liste et fiche d’une intégration, isolée par organisation et par produit.
- Mode `test` ou `live`, choisi à la création, immuable ensuite.
- Émission, lecture du préfixe, révocation et rotation de la clé secrète.
- Endpoint webhook unique : URL, secret, rotation, journal des livraisons, relance manuelle.
- API publique de session : créer, lire, lister, annuler.
- Ouverture du parcours : URL hébergée, puis cadre intégré sur la même URL.
- Signature HMAC des notifications, reprises, garde contre les URL internes.
- Guide d’installation dans la console : appel de création, redirection, lecture de la décision.
- Journal d’audit des gestes d’intégration, sans donnée personnelle.

### 3.2 Hors du module

- Contenu de la décision, signaux, identité extraite, revue humaine : produit Identity et module Règles.
- Facturation et solde : le module ne fait qu’exiger un solde avant d’autoriser une intégration `live`.
- SDK natifs iOS et Android.
- Marque blanche du parcours (logo, couleurs, nom du tenant à l’écran).
- Plusieurs endpoints webhook par intégration, filtrage d’événements par type, file d’attente exposée au client.
- Liste blanche d’adresses IP.

## 4. Principes

1. Une intégration égale un environnement, une clé active, un endpoint, un ensemble de dossiers.
2. Le mode ne change pas. Passer en production, c’est créer une intégration `live` et y faire pointer le serveur du client.
3. La clé secrète n’est jamais envoyée au navigateur de l’utilisateur final, ni journalisée, ni remise dans une URL.
4. Toute notification est signée. Une livraison sans signature valide est ignorée par l’intégrateur. Recogniz-Me ne considère une livraison acquittée que sur un statut HTTP `2xx`.
5. Le même événement n’est pas réémis si la décision n’a pas changé. L’empreinte est `verification_id + décision + updated_at`.
6. Le serveur du client est la seule partie qui crée une session. Le parcours hébergé ne crée pas de dossier.
7. Une intégration `test` n’appelle aucun fournisseur de vision, de biométrie ou de paiement. Une intégration `live` refuse les URL de webhook qui résolvent vers un réseau privé ou une adresse de métadonnées.
8. Les journaux portent `integration_id`, `verification_id`, `event_id`, le statut HTTP et le code d’erreur. Ils ne portent ni le secret, ni le nom, ni le numéro de pièce, ni le corps OCR.

## 5. Modèle

```
Organisation
  └── Produit Identity
        └── Intégration  (nom unique, mode test | live)
              ├── Clé API          ky_test_… | ky_live_…
              ├── Endpoint webhook URL + whsec_…
              └── Vérifications    créées par cette clé
```

| Champ | Règle |
|---|---|
| `id` | UUID. Identifiant stable, cité dans chaque dossier et chaque événement. |
| `organization_id` | Locataire. Aucune lecture croisée. |
| `product` | `identity` pour ce module. Le même modèle servira aux autres produits mesurés. |
| `mode` | `test` ou `live`. Immuable. |
| `name` | 1 à 128 caractères, unique sans tenir compte de la casse dans l’organisation et le produit. |
| `created_at` | Horodatage UTC. |

Il n’y a pas d’archivage silencieux. Une intégration créée reste listée. Couper l’accès se fait en révoquant la clé et, si besoin, en supprimant l’endpoint.

## 6. Environnements

| | Test | Live |
|---|---|---|
| Préfixe de clé | `ky_test_` | `ky_live_` |
| Création | Immédiate | Refusée sans solde de crédits (`insufficient_credit`) |
| Analyse documentaire | Bouchon déterministe | Moteur de vision du produit |
| Biométrie | Bouchon | Fournisseur réel |
| Débit | Aucun | Une unité à la complétion, selon la facturation |
| Webhook | `https://` public, ou `http://localhost` / `http://127.0.0.1` | `https://` uniquement, hôte public après résolution DNS |
| Usage | Développement, recette, démonstration | Utilisateurs réels |

Le client tient au moins deux intégrations : une de test, une de production. Les mélanger dans le même binaire, avec un drapeau, est une erreur d’intégration. Le préfixe de la clé suffit à savoir dans quel monde on se trouve.

## 7. Clé d’API

### 7.1 Forme

La clé est un secret porteur, transmis dans `Authorization: Bearer`.

- Préfixe public : 12 caractères (`ky_test_` ou `ky_live_` plus un discriminant). Le préfixe est le seul fragment stocké en clair pour l’affichage et l’audit.
- Secret complet : entropie suffisante pour qu’une recherche en ligne soit vaine. Il est stocké sous forme d’empreinte pour l’authentification.
- Une copie chiffrée permet à la console de le recopier tant que la clé est active. Cible : ne plus le réafficher après la fermeture de l’écran d’émission, et ne garder que le préfixe. La copie chiffrée actuelle est un écart assumé le temps que la rotation soit fiable.

### 7.2 Cycle

| Action | Qui | Effet |
|---|---|---|
| Émission à la création | Rôle autorisé à écrire les clés | Une clé active est rendue dans la réponse. Elle n’est pas renvoyée par un `GET` ultérieur dans la cible. |
| Émission manuelle | Même droit | Refusée s’il existe déjà une clé active (`key_exists`). |
| Révocation | Même droit | La clé cesse d’authentifier immédiatement. Les dossiers restent. |
| Rotation | Même droit | Cible : émettre la clé suivante, laisser les deux valides pendant une fenêtre courte, puis révoquer l’ancienne. Aujourd’hui la contrainte d’unicité empêche une seconde ligne : la rotation complète est un écart à fermer. |

Une clé révoquée répond `401`. Elle ne répond pas `403`, pour ne pas confirmer qu’elle a existé à un appelant qui ne l’a plus.

### 7.3 Ce que la clé peut faire

La clé est scopée à son intégration.

| Opération | Autorisée |
|---|---|
| `POST /v1/verifications` | Oui, dans cette intégration |
| `GET /v1/verifications` et `GET /v1/verifications/{id}` | Oui, dossiers de cette intégration |
| `POST /v1/verifications/{id}/cancel` | Oui, dossier non terminal |
| `GET /v1/verifications/{id}/media/{kind}` | Oui, URL signée à durée courte |
| `POST /v1/verifications/{id}/review` | Réservé à un usage serveur de revue. La console reste le chemin nominal de la revue humaine. |
| Gérer le webhook de cette intégration | Oui |
| Lire ou écrire une autre intégration, la facturation, l’équipe | Non |

`Idempotency-Key` est obligatoire pour un client qui peut réessayer `POST /v1/verifications`. La même clé d’idempotence et le même corps renvoient la même session. Un corps différent avec la même clé renvoie un conflit.

## 8. Session

Créer une vérification ne lance pas l’analyse. Cela ouvre un dossier et rend une URL.

### 8.1 Requête

```http
POST /v1/verifications
Authorization: Bearer ky_live_…
Idempotency-Key: order-123
Content-Type: application/json

{
  "external_id": "order-123",
  "applicant": {
    "first_name": "Ada",
    "last_name": "Lovelace",
    "email": "ada@example.com"
  }
}
```

| Champ | Règle |
|---|---|
| `external_id` | Référence métier du client. Opaque pour Recogniz-Me. Reprise dans le webhook pour rapprocher le dossier sans table de correspondance fragile. |
| `applicant` | Optionnel. Sert de comparaison quand la politique l’exige. Ce n’est pas une preuve. |
| `metadata` | Objet JSON borné, rendu tel quel au `GET`. Pas de secret dedans. |

Le corps ne contient pas de document, pas de selfie, pas de décision souhaitée.

### 8.2 Réponse

| Champ | Rôle |
|---|---|
| `id` | Identifiant du dossier. |
| `integration_id` | Intégration qui l’a créé. |
| `integration_mode` | `test` ou `live`. |
| `status` | `created` à l’ouverture. |
| `hosted_url` | URL à remettre à l’utilisateur. Jeton à durée de vie bornée (`expires_at`). |
| `expires_at` | Au-delà, le lien n’ouvre plus le parcours. |

`hosted_url` est absent ou inutilisable une fois le dossier terminal, annulé, ou le jeton révoqué.

### 8.3 Lecture

`GET /v1/verifications/{id}` renvoie le statut, la décision, les raisons, la version de règles, la version de politique, les signaux et l’identité extraite.

L’intégrateur s’en sert pour :

- afficher un état dans son back-office ;
- rattraper un webhook manqué ;
- vérifier qu’un webhook reçu correspond au dossier qu’il a créé.

Il ne s’en sert pas comme boucle de sondage à intervalle court. Le webhook est le chemin nominal. Un sondage de réconciliation se fait après l’échéance des reprises, ou sur action d’un opérateur.

## 9. Ouverture du parcours

Le seul mode d’installation est le parcours hébergé. La session est créée dans l’application du client, pas dans le navigateur de l’utilisateur final. L’application envoie :

| Donnée | Règle |
|---|---|
| Clé API | Obligatoire. En-tête `Authorization: Bearer`. |
| `external_id` | Optionnel. |
| `first_name` | Optionnel. |
| `last_name` | Optionnel. |
| `email` | Optionnel. |

Dans l’application du client, l’utilisateur voit l’élément natif `<recogniz-me-button>` : bouton **Get verified**, sceau, et la ligne qui précise que la vérification d’identité est réalisée par Recogniz-Me. Le client ne redessine pas cette interface. L’onglet Installation de la console montre cet aperçu. Le clic crée la session sur le serveur du client, puis ouvre `hosted_url` selon le mode choisi. Aucun des deux modes n’embarque la clé secrète.

### 9.1 Redirection

L’utilisateur quitte la page du client et continue sur le parcours hébergé. Le navigateur n’appelle que le serveur du client, qui lui a renvoyé `hosted_url`.

```text
const { hosted_url } = await fetch("/api/verifications", { method: "POST", ... });
window.location.assign(hosted_url);
```

À la fin, l’utilisateur voit l’écran de fin Recogniz-Me. Le client apprend le résultat par webhook, puis par `GET` s’il doit l’afficher dans son propre produit.

### 9.2 InContext

La page du client reste en place. Un cadre ouvre la même `hosted_url`, toujours sans la clé.

```text
const { hosted_url } = await fetch("/api/verifications", { method: "POST", ... });
frame.src = hosted_url;
frame.allow = "camera; microphone";
```

Contrat du cadre :

- même jeton, même durée de vie, même parcours que la redirection ;
- aucune clé dans le cadre ;
- le parent n’apprend pas la décision par le cadre. Il l’apprend par webhook.

### 9.3 Ce que l’utilisateur final ne fait pas

Il ne crée pas la session. Il ne choisit pas l’intégration. Il ne voit pas la clé, le secret de webhook, ni le solde. Il consent, capture, et attend l’écran de fin.

## 10. Webhooks

### 10.1 Endpoint

Une intégration a zéro ou un endpoint.

| Opération | Console | API porteuse |
|---|---|---|
| Créer ou remplacer l’URL | `PUT /v1/console/integrations/{id}/webhook` | `PUT /v1/webhooks` |
| Lire | `GET` | `GET` |
| Supprimer | `DELETE` | `DELETE` |
| Régénérer le secret | `POST …/webhook/rotate` | `POST /v1/webhooks/rotate` |
| Journal | `GET …/webhook/deliveries` | — |
| Relancer | `POST …/deliveries/{eventId}/retry` | `POST /v1/webhooks/deliveries/{eventId}/retry` |

Le secret complet (`whsec_` suivi de 32 octets hexadécimaux) n’est rendu qu’à la création et à la rotation. Les lectures suivantes ne rendent que `secret_prefix` et l’URL.

Remplacer l’URL ne régénère pas le secret. Régénérer le secret invalide l’ancien immédiatement : l’intégrateur déploie le nouveau secret avant, ou accepte une fenêtre de signatures refusées.

### 10.2 Événement livré

`verification.completed` est émis quand le dossier porte une décision.

Enveloppe :

```json
{
  "id": "evt_<32 hex>",
  "type": "verification.completed",
  "created_at": "2026-10-17T12:00:00Z",
  "data": {
    "id": "<verification uuid>",
    "integration_id": "<integration uuid>",
    "external_id": "order-123",
    "status": "completed",
    "decision": "approved",
    "decision_reasons": [],
    "extracted_identity": {},
    "created_at": "2026-10-17T11:40:00Z",
    "updated_at": "2026-10-17T12:00:00Z"
  }
}
```

`decision` prend les valeurs du produit Identity (`approved`, `declined`, `review`, `resubmission_requested`, et les terminaux équivalents déjà exposés par l’API). L’intégrateur ne déduit pas une approbation d’un simple `2xx` de livraison : il lit `data.decision`.

Cible d’événements supplémentaires, sans changer l’enveloppe : `verification.started`, `verification.submitted`, `verification.expired`, `verification.canceled`. Ils ne remplacent pas `verification.completed`. Tant qu’ils ne sont pas émis, l’intégrateur ne les attend pas.

### 10.3 Signature

Chaque envoi porte :

| En-tête | Valeur |
|---|---|
| `Content-Type` | `application/json` |
| `User-Agent` | `RecognizMe-Webhook/1` |
| `X-RecognizMe-Event` | `verification.completed` |
| `X-RecognizMe-Signature` | `t=<secondes unix>,v1=<hmac sha256 hex>` |

Le HMAC porte la chaîne `t + "." + corps brut`, avec le secret `whsec_…` comme clé UTF-8. L’intégrateur recalcule sur les octets reçus, pas sur un JSON reformaté.

Vérification attendue côté client :

1. Lire `t`. Refuser si l’horloge locale s’en écarte de plus de cinq minutes.
2. Recalculer `v1`. Comparer en temps constant.
3. Acquitter `2xx` seulement après avoir persisté l’événement, ou après avoir constaté que `id` était déjà traité.
4. Sur l’échec de traitement, répondre `5xx` pour obtenir une reprise. Ne pas répondre `2xx` puis traiter plus tard sans file propre.

### 10.4 Livraison

| Règle | Valeur |
|---|---|
| Succès | HTTP 200 à 299 |
| Délai d’une tentative | 10 secondes de connexion, 10 secondes d’appel |
| Redirections | Aucune. Un `3xx` est un échec. |
| Reprises | Tentative immédiate, puis 30 s, 2 min, 10 min, 1 h |
| Échec final | Après la dernière tentative, statut `failed`, événement d’audit `webhook.failed` |
| Relance manuelle | Depuis la console ou l’API, sur un envoi `failed` ou déjà `delivered` |
| Absence d’endpoint | Aucun envoi. Le dossier reste lisible par `GET`. |
| Endpoint retiré en cours de file | La livraison en attente se termine en échec `endpoint_missing` |

Un dossier dont la décision n’a pas bougé ne produit pas un second événement. Si la décision est réécrite (revue humaine), l’empreinte change et un nouvel événement part.

### 10.5 Garde d’URL

| Mode | Accepté | Refusé |
|---|---|---|
| Test | `https` public ; `http://localhost`, `http://127.0.0.1`, `http://[::1]` | Adresses privées autres, schémas exotiques |
| Live | `https` dont le nom ne résout pas vers loopback, lien-local, RFC 1918 ou métadonnées | `http`, IP privées, noms `.local` / `.internal`, résolution impossible |

Le contrôle est refait au moment de l’envoi, pas seulement à l’enregistrement. Un nom qui pointait vers l’extérieur et qui résout ensuite vers un réseau interne est refusé (`ssrf_denied`).

## 11. Console

L’écran Intégrations du produit Identity est l’outil de l’équipe qui branche le service. Il n’est pas l’outil de l’agent de revue.

### 11.1 Liste

Chaque carte montre le nom, le mode, la date de création. Un clic ouvre l’intégration. La liste vide explique qu’il faut en créer une. Le produit non mesuré n’affiche pas d’intégrations.

Créer demande le nom et le mode. Le mode live est proposé seulement si l’organisation a un solde. La réponse de création montre la clé une fois, avec une action de copie.

### 11.2 Fiche

Trois onglets.

| Onglet | Contenu |
|---|---|
| Installation | Parcours hébergé. Champs envoyés par l’application, choix redirection ou InContext, extrait à copier, aperçu du bouton Get verified et de la mention fournisseur. Le préfixe de clé seulement, jamais la clé réelle. |
| Clés | Préfixe, date, statut actif ou révoqué, copie du secret tant que la clé est active, révocation, émission s’il n’y a pas de clé active. |
| Réglages | Nom, mode, identifiant, date. Formulaire webhook : URL, préfixe du secret, rotation, suppression, journal des livraisons, relance. |

Depuis la fiche, un lien ouvre les vérifications filtrées sur cette intégration.

### 11.3 Droits

| Rôle | Lister et lire | Créer, émettre, révoquer, webhook |
|---|---|---|
| Propriétaire, administrateur | Oui | Oui |
| Développeur | Oui | Oui |
| Membre, lecture seule | Non | Non |

Un développeur gère l’intégration. Il ne tranche pas les dossiers : ce droit reste à la revue. Un membre voit les dossiers. Il ne voit pas les secrets.

## 12. Sécurité

| Exigence | Application |
|---|---|
| Secret de clé hors des URL, des journaux et du parcours utilisateur | Authentification par en-tête seulement. Le jeton hébergé est distinct et à durée courte. |
| Secret de webhook chiffré au repos | AES-GCM. Le clair ne sort qu’à l’émission, à la rotation, et au calcul de signature. |
| Comparaison de signature | HMAC-SHA256 sur le corps brut. Fenêtre de cinq minutes. |
| Isolation | Toute requête porteuse est résolue vers une organisation et une intégration. Un identifiant d’une autre organisation répond comme une absence. |
| Révocation | Effet immédiat sur l’authentification. |
| Pas de suivi de redirection webhook | Évite qu’une URL publique fasse relayer le corps vers une adresse interne. |
| Audit | `integration.created`, `api_key.issued`, `api_key.revoked`, `webhook.upserted`, `webhook.rotated`, `webhook.deleted`, `webhook.delivered`, `webhook.failed`. Acteur utilisateur, clé, ou système. |

La clé active recopiable depuis la console est un écart avec la pratique « montré une fois ». Il se ferme quand la rotation permet de remplacer une clé perdue sans la relire. D’ici là, l’accès à l’écran Clés reste limité à `API_KEY_READ`.

## 13. Bac à sable

Une intégration `test` sert à brancher le parcours de bout en bout avant toute pièce réelle.

| Attendu | Comportement |
|---|---|
| Création de session | Identique au live : même corps, même `hosted_url`, même idempotence. |
| Parcours | Le même écran hébergé. Les captures n’appellent pas le fournisseur. |
| Décision | Produite par le bouchon, selon les règles de test du produit Identity, de façon reproductible. |
| Webhook | Même enveloppe, même signature, mêmes reprises. L’URL peut être locale. |
| Crédit | Aucun débit. |
| Données | Les dossiers de test restent dans l’intégration de test. Ils n’apparaissent pas dans une intégration live. |

L’intégrateur ne « simule » pas une approbation en écrivant un statut. Il fait le parcours de test, reçoit `verification.completed`, vérifie la signature, et rapproche `external_id`.

## 14. Mise en production

Liste que l’équipe client coche avant de créer des dossiers réels. La console ne bloque pas chaque ligne : le contrat du module rend les lignes 1 à 4 mécaniques.

1. Une intégration `live` existe, distincte de celle de test. Le solde couvre au moins une vérification.
2. La clé `ky_live_` est uniquement sur le serveur du client. Le navigateur ne reçoit que `hosted_url`.
3. L’endpoint est en `https` public. La signature est vérifiée, y compris la fenêtre de temps, avant tout traitement.
4. Le traitement est idempotent sur `id` d’événement. Un `2xx` n’est renvoyé qu’une fois l’événement enregistré.
5. `external_id` porte la référence métier. Le back-office du client sait ouvrir le dossier Recogniz-Me correspondant.
6. L’échec webhook est visible : journal des livraisons, alerte interne si le statut passe à `failed`.
7. La politique de pièces et de décision active est celle voulue en production. Elle est figée par dossier, pas par intégration.
8. Un dossier de test complet a été rejoué sur l’intégration `test` : création, parcours, webhook signé, `GET` de réconciliation, annulation.

## 15. Erreurs

Les erreurs d’intégration sont stables. Le client branche dessus, pas sur le texte.

| Code | HTTP | Quand |
|---|---|---|
| `no_integration` | 409 | Émission de clé sans intégration. |
| `name_taken` | 409 | Nom déjà utilisé dans l’organisation. |
| `key_exists` | 409 | Une clé active est déjà en place. |
| `insufficient_credit` | 403 | Création live sans solde. |
| `ssrf_denied` | 400 | URL de webhook live interdite. |
| `invalid` sur `url` | 400 | URL mal formée, ou schéma interdit en test. |
| `invalid` sur `mode` | 400 | Mode autre que `test` ou `live`. |
| `required` sur `name` | 400 | Nom vide. |
| `size` sur `name` | 400 | Nom au-delà de 128 caractères. |
| `invalid_status` | 409 | Relance d’une livraison encore en cours. |
| Absence d’intégration, de webhook ou de livraison | 404 | Identifiant inconnu dans l’organisation. |

Une clé absente, révoquée ou mal formée répond `401` sur l’API publique.

## 16. Critères d’acceptation

Une intégration est conforme à ce cahier quand les faits suivants tiennent.

1. Deux organisations ne lisent pas leurs intégrations, leurs clés, leurs webhooks ni leurs dossiers.
2. Deux intégrations de la même organisation ne partagent ni clé, ni endpoint, ni liste de dossiers.
3. Créer une intégration live sans solde échoue. La créer avec solde émet une clé `ky_live_` et n’émet pas de clé `ky_test_`.
4. Une clé `ky_test_` produit un dossier, une `hosted_url`, un parcours et un webhook sans appel fournisseur et sans débit.
5. Une clé révoquée reçoit `401` sur l’appel suivant.
6. `POST /v1/verifications` avec la même `Idempotency-Key` et le même corps renvoie le même dossier.
7. À la décision, un seul événement `verification.completed` part. Le corps contient `integration_id`, `external_id` et `decision`. La signature se vérifie avec le secret courant.
8. Un endpoint qui répond `500` est repris selon le calendrier, puis marqué `failed`. Une relance manuelle le remet en file.
9. Une URL live vers `http`, `localhost`, une IP privée ou une adresse de métadonnées est refusée à l’enregistrement et à l’envoi.
10. Le secret de webhook complet n’apparaît qu’à la création et à la rotation.
11. Les journaux d’audit et les journaux techniques ne contiennent pas le secret, ni le corps d’identité.
12. Un rôle sans `API_KEY_READ` ne voit pas les clés. Un rôle sans `API_KEY_WRITE` ne crée pas d’intégration et ne modifie pas le webhook.

## 17. Ancrage et écarts

| Exigence | État |
|---|---|
| Intégration nommée, mode immuable, unicité du nom | Livré |
| Clé unique active, préfixes `ky_test_` / `ky_live_`, révocation | Livré |
| Live refusé sans crédit | Livré |
| Session, `hosted_url`, idempotence, isolation par intégration | Livré |
| Redirection et InContext sur la même URL hébergée | Livré dans l’onglet Installation. Le cadre est l’URL hébergée, pas un paquet séparé. |
| Webhook unique, signature `t=,v1=`, reprises, SSRF, journal, relance | Livré |
| Événement `verification.completed` | Livré |
| Événements de cycle de vie autres que la complétion | Non livrés |
| Rotation de clé avec recouvrement | Non livré. La contrainte unique sur `integration_id` n’autorise qu’une ligne de clé. |
| Secret de clé montré une seule fois | Non livré. La console peut recopier le secret tant que la clé est active. |
| SDK natifs, marque blanche, plusieurs endpoints | Hors de ce module |

Le code de référence est `Integration`, `IntegrationService`, `ConsoleIntegrationController`, `WebhooksController`, `WebhookService`, `WebhookDispatcher`, `WebhookSigner`, `ApiKeyIssuer`, et les écrans `web/console/app/(app)/[product]/integrations`.
