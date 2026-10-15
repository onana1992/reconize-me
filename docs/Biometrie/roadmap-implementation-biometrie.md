# Roadmap d’implémentation — pipeline biométrie IDV (Face Liveness + CompareFaces)

**Produit :** Recogniz-Me, Identity & Document Verification  
**Version du document :** 1.0  
**Date :** 12 octobre 2026  
**Statut :** B0–B2 livrés (région `us-east-1`, seuil liveness dans le moteur, document avant le visage)  
**Documents liés :**
- [`../MVP/roadmap-implementation-m5.md`](../MVP/roadmap-implementation-m5.md) — CompareFaces livré ; Face Liveness explicitement hors M5
- [`../MVP/specification-m5-aws-live-webhook.md`](../MVP/specification-m5-aws-live-webhook.md) — port `BiometricAiPort`, seuils `m5-1`, stub liveness
- [`../MVP/guide-aws-iam-local.md`](../MVP/guide-aws-iam-local.md) — clés Rekognition actuelles (CompareFaces)
- [`../cout-unitaire-verification.md`](../cout-unitaire-verification.md) — 0,015 $ par check liveness, facturé aussi en échec
- [`../specs/roadmap-implementation-idv.md`](../specs/roadmap-implementation-idv.md) — biométrie propriétaire SageMaker, après ce cycle

Ce document dit **dans quel ordre** remplacer le liveness stub par Amazon Rekognition Face Liveness, puis brancher CompareFaces sur l’image de référence de cette session. Un palier n’est pas vert tant que son livrable n’est pas démontrable (test ou session), sans ouvrir le palier suivant.

Le bac à sable (`ky_test_` → `StubBiometricAi`) ne bouge pas. Document IA ne bouge pas. CompareFaces reste le face match ; ce cycle ne le remplace pas.

---

## 1. Où on en est

| Brique | État |
|---|---|
| Capture selfie | Image fixe dans `web/flow` (`assessSelfieFrame`), upload, qualité `acceptableSelfie` |
| Face match live | `RekognitionCompareFacesClient` : selfie = source, pièce = cible, similarité / 100 |
| Liveness live | `AwsBiometricAi` force `livenessPass = true` et `livenessScore = 1.0` dès que le selfie est `accepted` |
| Décision | `IdvDecisionEngine.applyFace` : liveness KO → `DECLINED` `liveness_fail` ; score &lt; 0,75 → `face_match_fail` ; [0,75 ; 0,90[ → `REVIEW` `face_match_borderline` ; ≥ 0,90 → `APPROVED` |
| Ordre actuel | L’analyse document et CompareFaces partent **ensemble** dans `HostedFlowService.decide`, **après** l’upload du selfie |
| Sandbox | `StubBiometricAi` : `liveness_fail`, `mismatch`, `review`, `approved`. Zéro Rekognition |
| Région AWS | **`us-east-1`** pour Textract, Rekognition (CompareFaces et Face Liveness) et S3. Défaut `kyc.aws.region` |

Face Liveness n’est pas un second `CompareFaces`. Le navigateur streame une vidéo vers Rekognition (`StartFaceLivenessSession`, composant Amplify). L’API ne reçoit pas la vidéo : elle crée la session, puis lit le résultat.

```
API  CreateFaceLivenessSession          → SessionId (expire en 3 minutes, usage unique)
Flow FaceLivenessDetector               → StartFaceLivenessSession (credentials courtes)
API  GetFaceLivenessSessionResults      → Confidence 0–100, Status, ReferenceImage (bytes)
API  CompareFaces                       → ReferenceImage ↔ recto de cette session
```

---

## 2. Cible

**Happy path live :** une session `ky_live_` dont le **document est déjà `APPROVED`** ouvre une session Face Liveness en `us-east-1`. L’applicant passe le challenge dans le flow. L’API stocke l’image de référence comme média `SELFIE`, compare cette image au recto, et `applyFace` écrit `liveness_pass` / `liveness_fail` avec le **vrai** score. Une pièce non approuvée ne crée **aucune** session liveness et n’appelle **pas** CompareFaces.

**Happy path sandbox :** le flow selfie fixe actuel, `StubBiometricAi`, **zéro** appel Rekognition, **zéro** credential AWS dans le navigateur.

---

## 3. Décisions figées (B0)

Sans ces choix, le palier suivant mélange région, stockage et seuil.

| Sujet | Décision |
|---|---|
| Région | **`us-east-1` pour tous les services AWS** (Textract, Rekognition, S3). Une seule clé : `kyc.aws.region`. Face Liveness n’existe pas en `ca-central-1` ; le quota `us-east-1` est 25 TPS / 75 sessions. Un `kyc.aws.<service>.region` vide reprend ce défaut. |
| Images | **Bytes bruts** (`GetFaceLivenessSessionResults`). Pas d’`OutputConfig` S3 : le bucket médias est au Canada, et le bucket liveness devrait être **dans la région du check**. Les bytes repartent dans le stockage objet déjà utilisé pour `SELFIE`. |
| Source du face match | L’image de **référence** renvoyée par GetResults. Le navigateur n’uploade plus un JPEG après un liveness réussi : une autre photo casserait la preuve. |
| Credentials navigateur | **STS** `AssumeRole`, politique de session limitée à `rekognition:StartFaceLivenessSession`, TTL ≤ durée de session (3 min), délivrées seulement au détenteur du token de flow. Pas de pool Cognito non authentifié : il permettrait de brûler le quota à 0,015 $ le check sans session KYC. |
| Challenge | **`FaceMovementAndLightChallenge`** (défaut AWS, le plus fort). `FaceMovementChallenge` seulement si la matrice téléphones du palier B5 montre que le flash couleur casse le parcours. C’est un réglage de `CreateFaceLivenessSession`, pas un autre pipeline. |
| Seuil liveness initial | **0,80** (confidence AWS 80), version de règles **`bio-1`**. Le moteur tranche, pas l’adaptateur. Le seuil face match **ne change pas** (0,75 / 0,90). On ne calibre pas « au feeling » au-delà de ce point de départ documenté ; un jeu d’essais réels pourra le bouger **dans** `bio-1` avant le vert B6. |
| Échec liveness | Confidence sous le seuil, ou `Status` d’échec utilisateur → `DECLINED` `liveness_fail`. Panne Rekognition, timeout, credential → `REVIEW` `provider_unavailable` (déjà le filet de `decideLive`). |
| Plafond | **3** checks **facturés** par vérification (un GetResults qui renvoie une confidence, succès ou échec). `CreateFaceLivenessSession` non consommé (expiration 3 min sans stream) **ne compte pas**. |
| Quand créer la session | Au moment où l’écran liveness s’affiche, **après** décision document `APPROVED`. Jamais à la création de la vérification. |
| Sandbox | `integrations.mode = test` : pas de CreateSession, pas de STS, pas de paquet Amplify chargé. |

**Kill de tout le cycle :** `ky_test_` qui appelle Rekognition ou reçoit des clés STS ; CompareFaces sur une image qui n’est pas la référence de **cette** session ; liveness lancé alors que le document n’est pas `APPROVED` ; SessionId accepté s’il n’appartient pas à la vérification du token ; PII ou bytes d’image dans les logs ; seuil codé en dur hors `IdvDecisionEngine` ; un client AWS hors `us-east-1`.

---

## 4. Principes d’ordre

1. **Serveur avant widget.** Le composant Amplify est le dernier morceau visible. Les paliers B1–B4 se prouvent avec un faux client Rekognition, sans caméra.
2. **Document avant liveness.** Aujourd’hui l’analyse document est dans `decide`, après le selfie. Il faut la rendre disponible **avant** `CreateFaceLivenessSession`, sinon chaque pièce illisible ou hors corridor coûte 0,015 $.
3. **Liveness avant CompareFaces.** Confidence insuffisante → `liveness_fail`, **zéro** CompareFaces.
4. **Java tranche.** L’adaptateur renvoie confidence, status et bytes. `IdvDecisionEngine` pose `livenessPass` avec le seuil `bio-1`. AWS ne renvoie pas `APPROVED`.
5. **Le stub reste le chemin `test`.** `StubBiometricAi` et les scénarios `liveness_fail` / `mismatch` / `review` ne sont pas réécrits.
6. **Un livrable par palier.** Pas de SageMaker, pas de liveness passif, pas de file SQS, pas du catalogue webhook `liveness.started` dans ce cycle.

---

## 5. Vue d’ensemble

```
B0 décisions (ce document)
        │
        ▼
B1 port liveness + moteur          faux GetResults, 0 CompareFaces si KO
        │
        ▼
B2 document APPROVED avant session analyse pièce sortie de decide()
        │
        ▼
B3 API flow session + complete     sandbox 0 AWS, référence = média SELFIE
        │
        ▼
B4 face match sur la référence     règles bio-1, plafond 3
        │
        ▼
B5 widget Amplify (live seulement) sandbox = selfie fixe inchangé
        │
        ▼
B6 runbook IAM + 1 permis QC réel
```

| Palier | Livrable démontrable | Réseau Rekognition |
|---|---|---|
| **B0** | Région `us-east-1` sur Textract, Rekognition et S3. Décisions du §3 | non |
| **B1** | Test : confidence 0,50 → `DECLINED` `liveness_fail`, CompareFaces non appelé. Confidence 0,90 → CompareFaces sur le selfie | mock — **livré** |
| **B2** | Pièce non `APPROVED` → décision immédiate, 0 session liveness. Pièce `APPROVED` → `capture_selfie` | mock — **livré** |
| **B3** | `POST` session + `POST` complete. Token test → **403**. Session expirée non facturée | mock — **livré** |
| **B4** | CompareFaces sur l’image de référence. Règles `bio-1`. 4ᵉ check facturé refusé | mock — **livré** |
| **B5** | Widget Amplify sur `next=liveness`. Sandbox reste sur la caméra selfie | live seulement — **livré** |
| **B6** | Une session `ky_live_` permis QC, de bout en bout, logs sans PII | oui (`us-east-1`) |

---

## 6. Paliers

### B1 — Séparer le liveness du pass automatique

`AwsBiometricAi.evaluate` ne peut plus poser `livenessPass = true`.

Introduire un port étroit, à côté de `CompareFacesClient` :

- `createSession()` → `sessionId`
- `getResults(sessionId)` → confidence ∈ [0, 1], status, `referenceImage`

`BiometricSignals` garde sa forme (`livenessPass`, `livenessScore`, `faceMatchScore`). Le booléen liveness est calculé dans `IdvDecisionEngine` (seuil 0,80). L’adaptateur AWS ne le fige plus.

`faceMatchPass` aujourd’hui calculé dans l’adaptateur (`>= 0.90`) peut rester pour ce palier : le moteur recalcule déjà avec `FACE_MATCH_FAIL` / `FACE_MATCH_PASS`. Ne pas élargir ce diff.

**Livrable :** test unitaire. Confidence basse → `liveness_fail` et **aucun** appel `compare`. Confidence haute → CompareFaces appelé comme aujourd’hui (encore sur les bytes « selfie » ; la référence réelle arrive en B4).

### B2 — Décision document avant la session

`HostedFlowService.decideLive` n’analyse la pièce qu’une fois le selfie accepté. Déplacer cet appel (replay `verification_document_analyses` inclus) au moment où le recto — et le verso s’il est requis — est accepté.

Si la décision document n’est pas `APPROVED`, la vérification se termine là (`unsupported_document`, `document_expired`, revue champs, etc.) avec le webhook `verification.completed` déjà en place. Le flow n’affiche pas l’étape liveness.

Le selfie fixe sandbox reste après le document : le sandbox ne consulte pas cette décision AWS.

**Livrable :** test. Document non `APPROVED` → le client liveness n’est pas appelé. Document `APPROVED` → l’étape suivante est autorisée.

### B3 — Contrat flow

Deux opérations sur le token de flow existant (pas de nouvelle auth) :

| Méthode | Rôle |
|---|---|
| `POST /v1/flow/{token}/liveness/session` | Vérifie document `APPROVED`, tente &lt; 3, mode live. `CreateFaceLivenessSession` (`ClientRequestToken` dérivé de la vérification + n° de tentative). Persiste `sessionId`, région, échéance, n° de tentative. Répond `sessionId`, `region`, credentials STS |
| `POST /v1/flow/{token}/liveness/complete` | Relit **le** `sessionId` stocké (celui du body est ignoré s’il diffère → **409**). `GetFaceLivenessSessionResults`. Stocke `ReferenceImage` comme média `SELFIE` accepté. Enchaîne le face match (B4) puis `applyFace` |

`ky_test_` sur ces deux routes : **403**, aucun bean Rekognition liveness invoqué.

Le `SessionId` AWS expire en 3 minutes et ne sert qu’une fois. Complete après expiration → erreur dédiée `liveness_expired`, sans incrémenter le plafond des checks facturés, et le flow redemande une session.

Journal : `verification_id`, durée, status, confidence, n° de tentative. Pas de nom, pas de bytes, pas de SessionId en clair dans un webhook.

**Livrable :** les deux routes dans Swagger, couvertes par le mock. Une clé test ne construit pas le client.

### B4 — CompareFaces sur la référence, règles `bio-1`

`RekognitionCompareFacesClient` ne change pas de forme : source = bytes visage, cible = recto. L’appelant passe les bytes de **référence**, plus le JPEG que le navigateur aurait pris.

Règles `bio-1` (nouveau `RULES_VERSION` live biométrie ; `vision-1` reste la version document) :

| Condition | Décision |
|---|---|
| confidence liveness &lt; 0,80 ou status d’échec | `DECLINED` `liveness_fail` — stop, pas de CompareFaces |
| similarité &lt; 0,75 | `DECLINED` `face_match_fail` |
| 0,75 ≤ similarité &lt; 0,90 | `REVIEW` `face_match_borderline` |
| similarité ≥ 0,90 | `APPROVED` `liveness_pass` + `face_match_pass` |
| exception fournisseur | `REVIEW` `provider_unavailable` |

Les signaux continuent d’être écrits dans `verification_signal` (score liveness réel, plus le 1,0 du stub).

Plafond : au 4ᵉ GetResults facturable, **409** `liveness_attempts_exceeded` sans appel AWS, décision `DECLINED` `liveness_fail`.

**Livrable :** test qui échoue si la source CompareFaces n’est pas l’image de référence du mock. Sandbox : les trois scénarios biométriques passent encore sans AWS.

### B5 — Widget dans le flow

`web/flow` charge `@aws-amplify/ui-react-liveness` **uniquement** si `next` est l’étape liveness **et** la session n’est pas sandbox. Import dynamique, composant client, pas de SSR : le détecteur ouvre la caméra.

Le flow :

1. arrive sur l’étape visage ;
2. appelle `POST .../liveness/session` ;
3. monte `FaceLivenessDetector` avec `sessionId`, région `us-east-1`, credentials reçues ;
4. sur `onAnalysisComplete`, appelle `POST .../liveness/complete` ;
5. affiche la décision déjà renvoyée par le flow (écran de fin actuel).

Le mode test garde `assessSelfieFrame` et l’upload selfie. Pas de paquet Amplify dans ce bundle si on peut le découper ; au minimum, pas d’appel réseau Amplify.

HTTPS obligatoire (`getUserMedia`). `localhost` suffit ; une IP de LAN ne suffit pas. Les scripts `dev:https` et `dev:tunnel` du flow couvrent le téléphone.

**Livrable :** une session live de développement passe l’ovale et une session test passe encore par la caméra selfie, sans credential AWS dans l’onglet réseau.

### B6 — IAM et une vraie session

Politique de l’utilisateur Rekognition **serveur** (en plus de `rekognition:CompareFaces`) :

- `rekognition:CreateFaceLivenessSession`
- `rekognition:GetFaceLivenessSessionResults`

Rôle **assumé par le navigateur**, rien d’autre :

- `rekognition:StartFaceLivenessSession`

`AmazonRekognitionFullAccess` peut rester le choix local déjà documenté dans le guide IAM. Le resserrement ci-dessus est le critère B6, pas un prérequis de B1.

Région : Textract, Rekognition et S3 utilisent `kyc.aws.region` (`us-east-1`). Les bytes de référence n’ont pas besoin d’un second bucket.

**Livrable :** un recto de permis du Québec, sur une intégration `live`, produit une décision `bio-1` avec un score liveness ≠ 1,0 et un score de similarité CompareFaces. Une intégration `test` lancée juste après montre zéro ligne Rekognition dans les logs. Coût attendu de cet essai : 0,015 $ (liveness) + 0,001 $ (CompareFaces), plus un second 0,015 $ si le premier challenge échoue.

---

## 7. Ce qui ne change pas

- `StubBiometricAi` et les scénarios console `liveness_fail`, `mismatch`, `review`.
- Seuils face match 0,75 / 0,90.
- Document IA, Textract, MRZ, webhook `verification.completed` (pas de média dans le payload).
- CompareFaces : selfie source, pièce cible, similarité / 100, seuil d’appel AWS à 0 pour laisser le moteur zoner.
- Le visage ne rattrape pas un document non `APPROVED` (`applyFace` n’est atteint qu’après cette garde, déjà le cas dans `decideLive`).

---

## 8. Hors de ce cycle

- Liveness passif, embeddings SageMaker, remplacement de Rekognition (roadmap IDV S4–S5, quand un corpus existe).
- Événements `liveness.started` / `liveness.completed` : le CDC les nomme ; M5 a déjà reporté ce catalogue. `verification.completed` reste l’événement client.
- Recadrage du portrait sur la pièce avant CompareFaces. À ouvrir seulement si B6 montre des similarités systématiquement basses sur le permis QC alors que le visage est bon.
- Images d’audit (`AuditImagesLimit`). La référence suffit comme preuve de la session. Les audits sont un choix de rétention biométrique, pas un prérequis du match.
- Pool Cognito, SQS, file d’attente biométrie.
- Second corridor documentaire.

---

## 9. Coût rappelé

| Appel | Prix | Quand il part |
|---|---|---|
| Face Liveness | 0,015 $ / check, **échec inclus** | B5/B6, au stream, pas au `Create` |
| CompareFaces | 0,001 $ | seulement si liveness ≥ 0,80 |
| Sandbox | 0 $ | toujours |

Plafond 3 : pire cas biométrie **0,045 $** de liveness + au plus un CompareFaces si le dernier check passe. Une pièce refusée avant B2 coûte **0 $** de Rekognition.
