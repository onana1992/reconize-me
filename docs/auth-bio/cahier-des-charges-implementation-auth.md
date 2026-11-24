# Cahier des charges — Implémentation de l’authentification biométrique

**Produit :** Recogniz-Me, Biometric Authentication  
**Code produit :** `authentication`  
**Version du document :** 1.2  
**Date :** 22 novembre 2026  
**Statut :** spécification d’implémentation — **C0–C7 terminés**, C8 suivant  
**Moteur :** `auth-2` (production uniquement)  
**Documents liés :**

- [`specification-detaillee-auth.md`](./specification-detaillee-auth.md) — le contrat d’implémentation expliqué (acteurs, flux, API, données, règles)
- [`roadmap-implementation-auth.md`](./roadmap-implementation-auth.md) — l’ordre de construction (paliers C0–C9 ; C0 clos)
- [`../authentification/cahier-des-charges-authentification.md`](../authentification/cahier-des-charges-authentification.md) — le contrat d’octobre. Il tient, sauf la livraison (pas de webhook) et l’environnement (pas de bac à sable)
- [`../Biometrie/roadmap-implementation-biometrie.md`](../Biometrie/roadmap-implementation-biometrie.md) — Face Liveness et CompareFaces déjà livrés pour l’identité
- [`../specs/cahier-des-charges-integration.md`](../specs/cahier-des-charges-integration.md) — clé, mode, crédit. Le webhook de ce module ne s’applique pas ici. Le bac à sable des autres produits ne s’applique pas non plus.

---

## 1. Objet

Livrer le troisième produit : reconnaître une personne **déjà enrôlée**, dans une organisation, sous une référence client.

Le parcours est court. Le serveur du client ouvre une session. La personne consent, passe le défi de présence déjà utilisé par l’identité, et la plateforme compare ce visage à la référence de **cette** personne. La décision est le corps de la réponse qui termine le défi. Il n’y a pas de webhook. Le client ne laisse entrer que si ce statut est `approved`. Recogniz-Me ne délivre pas le jeton de l’application du client.

Ce produit n’a **pas de bac à sable**, **pas de clé test**, et **pas de parcours test**. Aucune clé `ky_test_` n’est jamais émise pour `authentication`. Pas d’intégration `test`, pas de scénario déterministe, pas d’enrôlement fictif, pas de branche flow stub. Un seul parcours hébergé : **production** — consentement, Face Liveness réel, recherche réelle. Une seule intégration : `live`, clé `ky_live_`, moteur `auth-2`.

## 2. Pourquoi ce montage

Aujourd’hui, l’identité appelle `CompareFaces` à chaque dossier : les deux JPEG sont relus, décodés, et les traits sont extraits à la volée. Reprendre ce geste à chaque connexion ferait deux choses que ce produit ne veut pas.

- Il rallongerait chaque authentification du téléchargement et du décodage de la photo de référence.
- Il garderait un second visage brut dont la seule raison d’être est d’être renvoyé au fournisseur.

Amazon Rekognition sait garder le vecteur du visage dans une **collection**. Ce vecteur n’est pas une image, et il n’est pas réversible en photo. L’enrôlement l’écrit une fois (`IndexFaces`). L’authentification envoie seulement le visage fraîchement prouvé par le défi de présence (`SearchFacesByImage`).

`CompareFaces` reste le face match de l’identité, pièce contre selfie. Il n’est pas le moteur de ce produit.

## 3. Ce que le contrat interdit de changer

| Sujet | Règle déjà écrite |
|---|---|
| Question | Ce visage est-il celui de la référence annoncée par `external_id`. Jamais « qui est ce visage ». |
| Forme | `POST /v1/authentications`, lien hébergé, décision dans la réponse de clôture. `GET` relit le même dossier. Pas de webhook. |
| Entrée | Seul `approved` ouvre l’accès chez le client. `review` ne l’ouvre pas. |
| Environnement | Production uniquement. Pas de bac à sable. **Aucune clé `ky_test_` émise**. **Pas de parcours test** (pas de flow stub, pas de scénario). Pas de `sandbox_scenario`. |
| Source | Une identité **live** `approved`, option d’enrôlement active, `external_id` présent, présence et correspondance à la pièce réussies. |
| Seuils | Plancher 0,90 pour approuver, 0,80 pour la bande grise. L’organisation peut seulement durcir. L’exemple « 95 % » d’un schéma est une politique publiable, pas un nouveau plancher. |
| Présence | Exigée, non désactivable. Même défi que l’identité : `FaceMovementAndLightChallenge`, seuil 0,80. |
| Fournisseur en panne | `failed`, sans débit, jamais `approved`. |
| Jeton | Le client émet sa propre session après avoir lu `approved`. Ce produit ne signe pas de JWT. |
| Identité | Une authentification ne modifie pas la vérification qui a enrôlé. |

## 4. Décision d’architecture

Une collection Rekognition par organisation (production). Région `us-east-1`, la même que Face Liveness.

| Moment | Appel | Ce qu’on garde |
|---|---|---|
| Enrôlement | `IndexFaces` sur l’image de référence du liveness de l’identité, déjà en mémoire | `FaceId` sur la fiche d’enrôlement. `ExternalImageId` = identifiant de la fiche, pas l’`external_id` du client. |
| Authentification | Défi de présence, puis `SearchFacesByImage` sur l’image de référence de **ce** défi | Rien dans la collection. Le selfie de la session reste un média d’audit, comme un média d’identité. |
| Révocation ou remplacement | `DeleteFaces` tout de suite | La fiche passe à `revoked` ou `retired`. Le vecteur ne sert plus, même si la photo d’identité vit encore son délai. |

La recherche parcourt la collection, mais le moteur n’en fait pas une identification.

1. Le seuil envoyé à Rekognition est le seuil de revue de la politique, en points sur 100.
2. Le moteur ne retient un candidat que si son `FaceId` est **exactement** celui figé sur la session.
3. Un autre visage, même très ressemblant, est un `face_match_fail`. La réponse ne dit pas à qui il ressemble.
4. Aucune route ne renvoie une liste de personnes.

S’il n’y a pas exactement un visage indexable, aucune fiche `active` n’est écrite. La décision d’identité reste celle qu’elle était.

## 5. Périmètre

**Dans ce chantier**

- Le code produit `AUTHENTICATION`, à côté de `IDENTITY` et `AML`.
- Les fiches d’enrôlement et les sessions d’authentification, isolées par organisation, en **production uniquement**.
- `auth-2` : collection, `IndexFaces`, Face Liveness réutilisé, `SearchFacesByImage`, `DeleteFaces`.
- Le branchement sur une identité live approuvée, quand l’option d’enrôlement est active.
- Le parcours hébergé **production** : consentement, puis le même écran de présence que l’identité (liveness réel). Pas de pièce, pas de lecture. **Pas de parcours test.**
- La décision dans la réponse de clôture. Crédit et revue selon le contrat. Politique `auth-policy-0`.
- Intégration `live` seulement. Créer une intégration `test` pour ce produit répond `invalid`. **Aucune clé `ky_test_` n’est émise** pour `authentication`.

**Hors de ce chantier**

| Sujet | Sort |
|---|---|
| Bac à sable, `ky_test_`, parcours test / flow stub, scénarios `sandbox_scenario`, enrôlement fictif | **Hors produit.** Contrairement à Identité (qui a un parcours sandbox). |
| `CompareFaces` de l’identité | Inchangé. Pièce contre selfie de la vérification. |
| Recherche 1:N, dédoublonnage, liste noire | Interdit par le contrat. |
| Enrôlement par un selfie seul, sans identité approuvée | Version ultérieure. |
| Webhook `authentication.decided` ou `authentication.reviewed` | Pas dans ce produit. La décision est déjà dans la réponse. |
| Jeton, cookie ou session de l’application cliente | Le client les émet chez lui, après avoir lu `approved`. |
| Liveness passif, signaux d’appareil | Un autre moteur. |
| Copie chiffrée supplémentaire de la photo d’enrôlement | La photo source reste celle du dossier d’identité. Pas un second objet. |
| Console au-delà des écrans déjà décrits au contrat | Même contrat, pas un nouveau dessin. |

## 6. Ancrage dans le code actuel

| Élément | Aujourd’hui | Devenir |
|---|---|---|
| `ProductCode` | `IDENTITY`, `AML` | Ajouter `AUTHENTICATION`. Une clé de ce produit reçoit `401` sur les routes des deux autres, et l’inverse. |
| Intégration | Identité et AML : `test` ou `live` | Ce produit : **`live` seulement**. Une tentative `test` est refusée. |
| `FaceLivenessClient` | Crée la session et lit le résultat pour une vérification. | Réutilisé. Le navigateur ne reçoit toujours que le droit `rekognition:StartFaceLivenessSession`. |
| `CompareFacesClient` | Face match d’identité. | Pas appelé par ce produit. |
| `AwsBiometricAi` | Liveness puis `CompareFaces` vers la pièce. | L’identité le garde. L’authentification a son propre adaptateur : liveness, puis recherche dans la collection. |
| `IdvDecisionEngine` | Seuils 0,80 / 0,75 / 0,90 pour l’identité. | Ne tranche pas ce produit. La politique `auth-policy-0` tranche. |
| Stockage S3 | Préfixe `kyc/` pour les dossiers. | Le selfie d’une session d’authentification y va, comme un média de dossier. Aucun visage n’est écrit sous `templates/`. |
| `api/Dockerfile` | Inchangé. Aucun secret de construction. | Les clés Rekognition existent déjà au lancement du conteneur. |

Les tests automatisés (faux client Rekognition) restent un outil d’ingénierie. Ce n’est **ni** un environnement client, **ni** une clé `ky_test_`, **ni** un parcours hébergé test.

## 7. Enrôlement

Déclencheur : la vérification d’identité **live** passe `approved`, l’option d’enrôlement est active, `external_id` est présent, la présence réelle et la correspondance à la pièce ont réussi.

1. Lire l’image de référence du liveness de cette vérification. Ne pas relire une photo envoyée à part par le navigateur.
2. S’assurer que la collection de l’organisation existe. La créer sinon.
3. `IndexFaces`. Un seul visage. Qualité insuffisante, zéro visage ou plusieurs visages : ne pas créer de fiche `active`. Journaliser l’échec sans octets et sans `external_id`.
4. Écrire la fiche : `source = idv`, `source_verification_id`, `face_id`, identifiant de collection. L’ancienne fiche `active` du même couple (organisation, `external_id`) passe à `retired`, et son `FaceId` est supprimé de la collection avant que la nouvelle ne soit `active`.
5. Si `DeleteFaces` ou `IndexFaces` échoue après coup, ne pas laisser deux fiches `active`. La nouvelle n’est publiée qu’une fois l’ancienne retirée du fournisseur.

`POST /v1/enrollments` répond `enrollment_requires_idv`. Il n’y a pas d’écriture manuelle ni d’enrôlement fictif.

La personne est le couple (organisation, `external_id`) : le mode est toujours `live`.

## 8. Authentification

1. `POST /v1/authentications` exige une fiche `active`, fige `enrollment_id`, `face_id` et la politique, et rend le lien. Aucun appel Rekognition à la création. Cette réponse n’a pas encore de décision. Clé : `ky_live_` uniquement. Champ `sandbox_scenario` présent → `invalid`.
2. Consentement `auth-consent-1`, puis le défi de présence. Sous 0,80, ou défi non abouti : pas de recherche. Les essais restants redemandent la capture. Au plafond : `declined` `attempt_limit` ou `presentation_attack`, selon le contrat.
3. Au-dessus du seuil de présence, `SearchFacesByImage` avec l’image de référence de ce défi, encore en mémoire.
4. Similarité Rekognition (0 à 100) divisée par 100. Le score du dossier est ce nombre, entre 0 et 1, trois décimales, seulement si le `FaceId` est celui de la session.
5. La politique applique ensuite l’ordre du contrat : présence, puis bande sous 0,80, puis bande grise, puis approbation à partir de 0,90.
6. Si la fiche figée n’est plus `active` au moment de la recherche : `declined` `enrollment_inactive`. Pas d’appel, pas de débit.
7. Réponse illisible, délai dépassé, collection ou visage introuvable chez le fournisseur : `failed`. Pas un refus de la personne.

L’appel qui termine le défi répond dans le même corps : `status`, `reasons`, `match_score` s’il y a eu comparaison, `failure_reason` si `failed`. Le serveur du client lit cette réponse. S’il a seulement remis le lien hébergé, il relit tout de suite `GET /v1/authentications/{id}` : le dossier est déjà décidé. Rien n’est poussé vers une URL du client.

Une revue répond `review` dans ce même corps. Le passage ultérieur à `approved` ou `declined` se relit par `GET`. Il n’y a pas d’événement `authentication.reviewed`.

Le selfie de la session est conservé pour l’audit et la revue. Le vecteur de la collection n’est pas relu, pas exporté, pas renvoyé par l’API.

## 9. Collection

| Règle | Valeur |
|---|---|
| Région | `us-east-1` |
| Une collection | Une organisation (production) |
| Nom chez Rekognition | Identifiant opaque dérivé de l’organisation. Pas le nom de l’organisation, pas un `external_id`. |
| Contenu | Un `FaceId` par fiche `active`. Les fiches `retired` et `revoked` n’y sont plus. |
| Navigateur | Aucun droit `IndexFaces`, `SearchFacesByImage` ou `DeleteFaces`. |

Supprimer l’organisation supprime la collection après les fiches. Un échec de `DeleteFaces` empêche de déclarer la fiche retirée : on réessaie. On ne marque pas `revoked` tant que le vecteur est encore cherchable.

## 10. Données

| Donnée | Où | Durée |
|---|---|---|
| `FaceId`, identifiant de collection | Base, sur la fiche | Vie de la fiche `active`. Effacés du fournisseur dès `retired` ou `revoked`. |
| Vecteur | Collection Rekognition | Même instant que le `DeleteFaces`. Recogniz-Me n’en garde pas de copie. |
| Photo d’enrôlement | Déjà le média de la vérification d’identité source | Le délai du dossier d’identité. Pas de second fichier. |
| Selfie de la session | Stockage des dossiers, préfixe `kyc/` | Vie du dossier d’authentification, pour la revue et l’audit. |
| Journaux | `authentication_id`, `enrollment_id`, statut, raisons, durée, statut fournisseur | Pas d’image, pas de `FaceId`, pas d’`external_id`, pas de vecteur. |

`GET` d’un média rend une URL courte vers le selfie de la session, ou vers le selfie de la vérification source tant qu’il existe. Le vecteur ne sort pas.

## 11. Moteur

| Moteur | Ce qu’il fait |
|---|---|
| `auth-2` | Présence réelle et recherche dans la collection. Décision dans la réponse de clôture. Débit d’une unité selon le contrat. |

Il n’y a pas de moteur `auth-1`. Les tests unitaires utilisent un faux client Rekognition côté ingénierie. Le client ne dispose d’aucun parcours déterministe sans caméra ni AWS : le seul parcours hébergé appelle Face Liveness et la collection pour de vrai.

Créer une intégration `authentication` en mode `test` répond `invalid` : **aucune clé n’est créée**. La plateforme n’émet jamais de préfixe `ky_test_` pour ce produit. Une clé `ky_test_` d’un autre produit (Identité, AML) présentée sur les routes d’authentification reçoit `401`.

## 12. Critères d’acceptation

1. Une identité live approuvée aux conditions du contrat écrit une fiche `active` avec un `FaceId`. Une identité refusée, ou approuvée sans `external_id`, n’écrit rien et n’appelle pas `IndexFaces`.
2. Une seconde identité approuvée pour le même couple (organisation, `external_id`) retire l’ancien `FaceId` avant de publier le nouveau.
3. `SearchFacesByImage` qui renvoie un autre `FaceId`, même au-dessus de 0,90, produit `declined` `face_match_fail`. La réponse ne nomme pas cette autre personne.
4. Le même `FaceId`, score au seuil d’approbation, présence réussie, produit `approved`.
5. Sous le seuil de présence, `SearchFacesByImage` n’est pas appelé.
6. Créer une intégration `authentication` en `test`, ou envoyer `sandbox_scenario`, répond `invalid`. **Aucune clé `ky_test_` n’existe pour ce produit** (ni émise, ni acceptée).
7. Révoquer une fiche supprime son `FaceId`. Une session déjà ouverte dessus finit `enrollment_inactive` sans recherche.
8. Une panne Rekognition produit `failed`, sans débit.
9. Aucune route ne prend un visage et ne renvoie une liste de personnes.
10. La réponse de clôture porte `status` et les raisons. Aucun webhook n’est émis. Le produit ne signe pas de JWT.
11. Les journaux de ces parcours ne contiennent pas d’image, de vecteur, de `FaceId`, ni d’`external_id`.
12. `CompareFaces` de l’identité se comporte comme avant ce chantier.
13. Sans enrôlement `active`, `POST /v1/authentications` répond `no_enrollment` et ne crée pas de session.
14. Live sans solde répond `insufficient_credit` et ne crée pas de dossier.

## 13. Ordre de livraison

L’ordre détaillé, avec livrables et kill list, est dans [`roadmap-implementation-auth.md`](./roadmap-implementation-auth.md) (paliers **C0–C9**). En résumé :

1. Produit `AUTHENTICATION`, intégration **live seulement**, fiches, sessions, politique `auth-policy-0`, décision dans la réponse. Pas de webhook. Pas de sandbox.
2. Adaptateur de collection, derrière un port, avec un faux client dans les tests automatisés.
3. Enrôlement à partir d’une identité live approuvée, puis `auth-2`.
4. Parcours hébergé branché sur le défi de présence existant.
5. Révocation, console, client Rekognition réel et débit.

Le dessin qui livre un JWT à la fin du défi n’est pas une étape. Le serveur du client le fait après avoir lu `approved` dans la réponse, ou dans le `GET` qui suit.
