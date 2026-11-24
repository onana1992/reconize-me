# Tutoriel — Amazon Rekognition dans Recogniz-Me

Rekognition répond à deux questions différentes, avec des appels différents.

| Question | Produit | Appels |
|---|---|---|
| La personne est-elle réelle, et son visage correspond-il à la photo de la pièce ? | Identité | Face Liveness, puis `CompareFaces` |
| Est-ce encore la personne déjà enrôlée ? | Authentification | Face Liveness, puis `SearchFacesByImage` dans une collection |

La région est `us-east-1`. Face Liveness n’existe que dans cette région, et la collection d’authentification y est créée aussi. Les clés du serveur vivent dans `kyc.aws.rekognition`. Le navigateur ne reçoit jamais ces clés.

Le score Rekognition est un nombre de 0 à 100. Le code le divise par 100 et le garde entre 0 et 1, sur trois décimales. 90 chez Rekognition devient `0.900` dans la décision.

---

## 1. Les trois briques

### Face Liveness — la personne est devant la caméra

Rekognition ouvre une session de défi (`FaceMovementAndLightChallenge` : mouvement du visage et changement de lumière). Le navigateur envoie la vidéo directement à AWS. L’API Recogniz-Me ne reçoit pas le flux.

Deux appels serveur, dans `RekognitionFaceLivenessClient` :

1. `CreateFaceLivenessSession` crée la session et rend un `sessionId`. `auditImagesLimit` est à 0 : on ne demande pas d’images d’audit en plus. L’image utile est la référence renvoyée avec le résultat.
2. `GetFaceLivenessSessionResults` lit le statut, la confiance, et les octets de `referenceImage`.

La confiance est ramenée sur 0–1. Le seuil de présence du produit est **0,80**. En dessous, on ne compare pas le visage.

Le navigateur a besoin d’un droit AWS pour démarrer le défi, et de rien d’autre. `StsLivenessCredentials` appelle STS `AssumeRole` pendant 15 minutes, avec une politique de session limitée à :

`rekognition:StartFaceLivenessSession`

Pas d’`IndexFaces`, pas de `SearchFacesByImage`, pas de `DeleteFaces`, pas de `CompareFaces`. Le rôle est `kyc.aws.rekognition.liveness-role-arn`. La session Rekognition elle-même expire en quelques minutes.

### CompareFaces — la pièce contre le visage vivant

`RekognitionCompareFacesClient.compare` envoie deux images en mémoire :

- source : la référence du liveness (pas un JPEG uploadé à part) ;
- cible : la photo de la pièce.

`similarityThreshold` est à 0 pour que Rekognition rende le score même s’il est bas. C’est `IdvDecisionEngine` qui tranche (refus, revue, approbation), pas le seuil AWS.

Cet appel n’existe que pour l’identité. L’authentification ne l’utilise pas.

### Collection — le vecteur déjà enregistré

Une collection Rekognition garde un vecteur de visage, pas une photo réversible. Une collection par organisation. Son identifiant est `rmauth` suivi de l’UUID d’organisation sans tirets (`RekognitionFaceCollectionClient.collectionId`).

| Appel | Moment | Effet |
|---|---|---|
| `CreateCollection` | Premier enrôlement de l’organisation | Crée le conteneur. S’il existe déjà, on continue. |
| `IndexFaces` | Identité live approuvée, option d’enrôlement active | Un seul visage, filtre de qualité `AUTO`. `ExternalImageId` = identifiant de la fiche, jamais l’`external_id` du client. |
| `SearchFacesByImage` | Fin du défi d’authentification | Cherche ce vecteur à partir de l’image de référence du liveness, encore en mémoire. |
| `DeleteFaces` | Remplacement ou révocation | Retire le vecteur avant de marquer la fiche `retired` ou `revoked`. |

`IndexFaces` exige exactement un visage. Zéro visage, plusieurs visages, ou qualité trop basse (`LOW_SHARPNESS`, `LOW_BRIGHTNESS`, `LOW_CONFIDENCE`, `EXTREME_POSE`) : aucune fiche `active`. Les visages déjà indexés par erreur sont supprimés.

`SearchFacesByImage` demande jusqu’à 10 candidats. Le moteur n’en garde qu’un : le `FaceId` figé sur la session. Un autre visage, même très ressemblant, est un refus. La réponse ne dit pas à qui il ressemble.

---

## 2. Comment c’est branché

`kyc.aws.enabled=true` active les vrais clients. Sinon, les beans de repli refusent l’appel (`ProviderUnavailableException`) ou, pour la collection, utilisent `InMemoryFaceCollectionClient`. Ce faux client sert aux tests. Ce n’est pas un parcours utilisateur.

| Bean | Fichier | Quand |
|---|---|---|
| `RekognitionFaceLivenessClient` | `IdvStoreConfig` | `kyc.aws.enabled=true` |
| `RekognitionCompareFacesClient` | `IdvStoreConfig` | idem |
| `StsLivenessCredentials` | `IdvStoreConfig` | idem |
| `AwsBiometricAi` | `IdvStoreConfig`, qualifié `liveBiometricAi` | toujours, mais il appelle les clients ci-dessus |
| `RekognitionFaceCollectionClient` | `AuthCollectionConfig` | `kyc.aws.enabled=true` |
| `InMemoryFaceCollectionClient` | `AuthCollectionConfig` | s’il n’y a pas déjà un `FaceCollectionClient` |

Les services ne connaissent que les ports : `FaceLivenessClient`, `CompareFacesClient`, `FaceCollectionClient`, `LivenessCredentialPort`. Changer de fournisseur se fait dans l’adaptateur, pas dans le parcours.

---

## 3. Parcours identité

`HostedFlowService` ouvre la session de liveness, remet au navigateur le `sessionId` et les credentials STS, puis à la fin appelle `AwsBiometricAi`.

`AwsBiometricAi.match` :

1. Lit `GetFaceLivenessSessionResults`.
2. Si le statut n’est pas `SUCCEEDED`, ou si la confiance est sous 0,80, ou s’il n’y a pas d’image de référence : pas de `CompareFaces`.
3. Sinon `CompareFaces` entre la photo de la pièce et cette image de référence.
4. `IdvDecisionEngine` applique les seuils d’identité (présence, refus, bande grise, approbation).

Si l’identité est approuvée, que l’organisation a activé l’enrôlement, et que les raisons contiennent `liveness_pass` et `face_match_pass`, `EnrollmentService` enchaîne :

1. `ensureCollection` ;
2. `IndexFaces` sur la même image de référence du liveness ;
3. s’il existait déjà une fiche `active` pour ce couple (organisation, `external_id`) : `DeleteFaces` de l’ancien `FaceId`, puis publication de la nouvelle fiche.

L’échec d’indexation ne change pas la décision d’identité déjà prise.

---

## 4. Parcours authentification

`AuthenticationHostedFlowService` réutilise le même `FaceLivenessClient` et les mêmes credentials STS. Le défi est le même widget.

Après un liveness réussi (confiance ≥ 0,80) :

1. L’image comparée est uniquement `referenceImage` du résultat. Un fichier envoyé à part ne part pas en recherche.
2. `SearchFacesByImage` interroge la collection de l’enrôlement. Le seuil envoyé à Rekognition est le plancher de recherche stocké (0,80), en points : 80. Rekognition peut donc rendre un score sous le seuil d’approbation.
3. `AuthDecisionEngine` ne regarde que le seuil d’approbation de la session (0,90 à 1,00). Au-dessus, avec la présence réussie : `approved`. En dessous : `declined` `face_match_fail`. Il n’y a pas de revue.
4. La décision est dans le corps de la réponse. Aucun webhook.

`CompareFaces` n’est pas appelé. La référence n’est pas la photo de la pièce, c’est le `FaceId` figé à la création de la session.

---

## 5. Ce que chaque couche a le droit de voir

| Acteur | Droit |
|---|---|
| Navigateur | `StartFaceLivenessSession` seulement, via STS, le temps du défi |
| API, identité | Créer et lire la session de liveness ; `CompareFaces` |
| API, authentification | Créer et lire la session de liveness ; créer la collection ; indexer ; chercher ; supprimer |
| Journaux | Durée, statut, raison (`provider_timeout`, `provider_unavailable`, `no_face`). Pas d’octets, pas de `FaceId`, pas d’`external_id` |

Une panne Rekognition (délai, accès refusé, réponse illisible) devient `provider_timeout` ou `provider_unavailable`. Pour l’authentification, la session passe `failed`, sans débit, et n’est jamais `approved`.

---

## 6. Ordre de lecture dans le code

1. `RekognitionFaceLivenessClient` et `StsLivenessCredentials` — le défi et le droit du navigateur.
2. `AwsBiometricAi` puis `RekognitionCompareFacesClient` — l’identité.
3. `EnrollmentService` puis `RekognitionFaceCollectionClient` — l’écriture du vecteur.
4. `AuthenticationHostedFlowService` et `AuthDecisionEngine` — la relecture 1:1.
5. `IdvStoreConfig` et `AuthCollectionConfig` — le choix réel ou faux client selon `kyc.aws.enabled`.
