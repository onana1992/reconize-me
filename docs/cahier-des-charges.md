# Cahier des charges — Recogniz-Me

**Date :** 23 août 2026  
**Statut :** spécification produit (vision)

**Implémentation en cours :** le livrable métier immédiat est le [cahier des charges MVP](./MVP/cahier-des-charges-mvp.md) ([roadmap](./MVP/roadmap-implementation-mvp.md)). Le présent document reste la **vision** (moteurs IA propriétaires). Le MVP ne livre pas SageMaker ni les produits Biometric Authentication et AML.

---

## 1. Objet du projet

Développer **Recogniz-Me**, une plateforme SaaS KYC / Identity Verification comparable dans son positionnement à Onfido, permettant aux entreprises de vérifier l'identité de leurs utilisateurs à distance.

La particularité du projet est un **moteur IA propriétaire dès le premier livrable d’analyse documentaire** : détection de pièce, classification, authenticité et fraude visuelle. On **n’emprunte pas** un palier heuristique (contours, ratios, règles de coins) avant le modèle : la vision documentaire est entraînée et servie (SageMaker), pas un enchaînement OpenCV « en attendant ».

Les services AWS AI/ML restent l’**infrastructure et des accélérateurs ciblés** (annotation Ground Truth, entraînement / endpoints SageMaker, OCR Textract ou lecture propriétaire, Rekognition en complément biométrique si besoin, Bedrock hors décision).

L'objectif n'est pas de construire un agrégateur d'APIs KYC, ni un MVP « règles puis on verra l’IA ». La décision KYC reste des **règles auditables** ; la perception de la pièce est **dès le départ** un modèle à nous.

---

## 2. Objectifs

Le système devra permettre à une entreprise cliente de :

- créer une vérification KYC ;
- envoyer une URL de vérification à son utilisateur ;
- capturer une pièce d'identité ;
- analyser automatiquement le document ;
- extraire les informations ;
- vérifier la cohérence des données ;
- détecter les signes de fraude ;
- capturer un selfie ou une vidéo ;
- effectuer une vérification de présence réelle ;
- comparer le visage au document ;
- calculer un score de risque ;
- obtenir une décision automatique ;
- envoyer certains dossiers en revue manuelle ;
- récupérer le résultat via API ou webhook.

---

## 3. Positionnement du produit

Le produit sera composé de cinq grands moteurs :

```
                   KYC PLATFORM
                        │
      ┌─────────────────┼─────────────────┐
      ▼                 ▼                 ▼
Document AI       Biometric AI       Fraud AI
      │                 │                 │
      └─────────────────┼─────────────────┘
                        ▼
                   Risk Engine
                        │
                        ▼
                 Decision Engine
                        │
            ┌───────────┴───────────┐
            ▼                       ▼
         APPROVED              MANUAL REVIEW
```

Un sixième composant, AML/KYB, pourra être ajouté dans une phase ultérieure.

---

## 4. Architecture fonctionnelle

### 4.1 Parcours global

```
Création de vérification
         ↓
    Lien KYC
         ↓
    Consentement
         ↓
  Capture document
         ↓
  Document Quality
         ↓
Document Detection
         ↓
 Document Classification
         ↓
       OCR
         ↓
  MRZ / Data Parsing
         ↓
Document Authenticity
         ↓
   Fraud Detection
         ↓
     Selfie / Video
         ↓
   Face Detection
         ↓
     Liveness
         ↓
    Face Matching
         ↓
     Risk Engine
         ↓
  Decision Engine
         ↓
APPROVE / REJECT / REVIEW
```

Spécification du premier produit : [`specs/specification-fonctionnelle-idv.md`](./specs/specification-fonctionnelle-idv.md).

---

## 5. Module Document AI

### 5.1 Capture du document

Le SDK web/mobile devra permettre :

- accès à la caméra ;
- détection automatique du document ;
- cadrage automatique ;
- capture automatique ;
- capture manuelle ;
- recto/verso lorsque nécessaire ;
- contrôle de la qualité avant upload.

### Contrôles qualité

Le système devra détecter :

- flou ;
- mauvaise luminosité ;
- surexposition ;
- sous-exposition ;
- reflets ;
- document coupé ;
- mauvaise perspective ;
- résolution insuffisante ;
- document trop petit ;
- image inutilisable.

---

## 6. Document Detection

La détection est un **modèle de computer vision propriétaire** (coins / présence / orientation), déployé en inférence (SageMaker). **Pas de chemin produit en heuristiques** (Canny, plus grand quadrilatère, ratio carte/passeport) : trop fragile hors labo, et ça retarderait le dataset.

Le modèle devra déterminer :

- présence d'un document ;
- position du document ;
- coins du document ;
- orientation ;
- perspective.

Prérequis de livrable : dataset versionné + annotation (Ground Truth) des coins / présence, métriques d’évaluation, endpoint d’inférence. Une image de **repli** (échec modèle → recapture) est autorisée ; un détecteur de contours **n’est pas** le moteur V1.

Sortie attendue :

```json
{
  "document_detected": true,
  "confidence": 0.98,
  "corners": {
    "top_left": [120, 80],
    "top_right": [910, 75],
    "bottom_right": [920, 590],
    "bottom_left": [110, 600]
  }
}
```

Une correction de perspective sera ensuite effectuée.

---

## 7. Document Classification

Le moteur est un **classifieur propriétaire** (pays, type, éventuellement version), pas une allow-list de règles ou d’heuristiques de layout. Le corridor V1 (nombre limité de pays et de types) borne le **dataset et les classes**, pas un substitut au modèle.

Le moteur devra identifier :

- pays ;
- type de document ;
- éventuellement version du document.

Exemple :

```json
{
  "country": "CA",
  "document_type": "passport",
  "confidence": 0.96
}
```

Le MVP devra commencer avec un nombre limité de pays et de documents.

---

## 8. OCR

### Lecture

La lecture des champs visuels peut s’appuyer sur Amazon Textract **en accélérateur de lecture** (pas un substitut à la détection / classification / authenticité). L’alternative cible reste un OCR / parseur propriétaire entraîné sur le même corpus.

Le moteur devra normaliser les résultats pour produire :

```json
{
  "first_name": "JOHN",
  "last_name": "SMITH",
  "date_of_birth": "1990-05-12",
  "document_number": "ABC123456",
  "expiration_date": "2030-05-12"
}
```

Les données de production (anonymisées) alimentent le réentraînement OCR et des autres modèles documentaires.

---

## 9. MRZ

Pour les documents concernés :

- localisation de la MRZ ;
- OCR ;
- parsing ;
- validation des check digits ;
- extraction des données ;
- comparaison MRZ ↔ OCR.

Exemple :

```
OCR name       = JOHN SMITH
MRZ name       = JOHN SMITH
               ↓
             MATCH
```

Toute incohérence devra générer un signal de risque.

---

## 10. Document Authenticity Engine

Ce moteur constitue une partie importante de la propriété intellectuelle du projet. Il est un **modèle (ou un ensemble de modèles) propriétaire** dès le premier pipeline d’analyse, pas une couche de règles visuelles « en attendant SageMaker ». Les contrôles déterministes (checksums MRZ, dates) restent des **signaux** à côté du modèle, pas le moteur d’authenticité.

Il devra analyser :

- cohérence des données ;
- cohérence visuelle ;
- zones modifiées ;
- anomalies de compression ;
- anomalies de texture ;
- anomalies de police ;
- positionnement des éléments ;
- photo remplacée ;
- manipulation locale de l'image ;
- incohérences entre recto et verso ;
- incohérences OCR/MRZ.

### Sortie

```json
{
  "authenticity_score": 0.96,
  "tampering_score": 0.04,
  "status": "PASS"
}
```

---

## 11. Architecture du modèle Document Fraud

```
                Document
                   │
       ┌───────────┼────────────┐
       ▼           ▼            ▼
      OCR         MRZ       Image Features
       │           │            │
       └───────────┼────────────┘
                   ▼
            Feature Engine
                   │
                   ▼
            Fraud ML Model
                   │
                   ▼
            Risk Probability
```

Le modèle de fraude documentaire est entraîné et déployé avec Amazon SageMaker AI **dans le même horizon que la détection**, pas dans une « phase 2 IA » après un MVP heuristique.

---

## 12. Dataset IA

Le projet devra constituer le dataset **avant** (ou en parallèle bloquante de) la première mise en production de la détection / classification / authenticité. Sans corpus annoté, on ne bascule pas sur des heuristiques de contours pour « avancer quand même ».

### Catégories

**Documents légitimes**

- passeports ;
- cartes d'identité ;
- permis ;
- différents pays ;
- différentes versions.

**Documents frauduleux**

- modification de texte ;
- modification de photo ;
- document reconstruit ;
- screenshot ;
- photocopie ;
- document imprimé ;
- document expiré ;
- manipulation numérique ;
- faux document.

Les données devront être correctement anonymisées, sécurisées et utilisées conformément aux obligations applicables.

---

## 13. Data Annotation

Amazon SageMaker Ground Truth pourra être utilisé pour annoter les datasets.

Annotations possibles :

- `document_type`
- `country`
- `fraud_type`
- `tampering_region`
- `image_quality`
- `document_orientation`
- `face_region`
- `mrz_region`

Le système devra conserver la version du dataset utilisée pour chaque entraînement.

---

## 14. Biometric AI

Le deuxième grand moteur sera consacré au visage.

Pipeline :

```
Camera
  ↓
Video Capture
  ↓
Face Detection
  ↓
Face Quality
  ↓
Liveness / Anti-Spoofing
  ↓
Face Embedding
  ↓
Face Matching
  ↓
Biometric Score
```

---

## 15. Face Detection

Le système devra détecter :

- présence du visage ;
- position ;
- taille ;
- orientation ;
- nombre de visages.

Un seul visage devra être accepté pour une vérification standard.

---

## 16. Face Quality

Le système devra évaluer :

- luminosité ;
- netteté ;
- taille du visage ;
- orientation ;
- occlusion ;
- visibilité des caractéristiques faciales.

Exemple :

```json
{
  "quality_score": 0.93,
  "face_size": "valid",
  "pose": "valid",
  "lighting": "valid"
}
```

---

## 17. Liveness / Anti-Spoofing

Le projet devra développer un moteur permettant de détecter les attaques de présentation.

### Attaques à considérer

- photo imprimée ;
- photo affichée sur smartphone ;
- photo affichée sur écran ;
- replay vidéo ;
- manipulation vidéo ;
- injection de flux ;
- deepfake selon le modèle de menace ;
- autres attaques de présentation identifiées lors des tests.

---

## 18. Liveness actif

Le MVP pourra utiliser un challenge dynamique.

Exemples :

- Tournez la tête à gauche.
- Regardez vers le haut.

Le système devra vérifier que l'action demandée est réellement exécutée.

Pipeline :

```
Challenge
    ↓
Video
    ↓
Face Tracking
    ↓
Head Pose Estimation
    ↓
Temporal Analysis
    ↓
Challenge Validation
```

---

## 19. Passive Liveness

Dans une phase ultérieure, le système devra pouvoir effectuer une analyse sans challenge explicite.

```
Video
 ↓
Frame Sampling
 ↓
Face Crop
 ↓
Anti-Spoof Model
 ↓
Temporal Model
 ↓
Liveness Score
```

L'objectif sera d'améliorer l'expérience utilisateur tout en conservant un niveau de sécurité élevé.

---

## 20. Modèle Liveness

Le système pourra utiliser plusieurs modèles spécialisés :

```
            Video
              │
      ┌───────┼────────┐
      ▼       ▼        ▼
   Quality  Spatial  Temporal
      │       │        │
      └───────┼────────┘
              ▼
       Anti-Spoof Model
              │
              ▼
        Liveness Score
```

### Technologies

- PyTorch ;
- ONNX ;
- SageMaker AI ;
- éventuellement inférence Edge/mobile pour certaines étapes.

---

## 21. Face Matching

Le système devra comparer :

```
Photo document
      │
      ▼
Face Embedding A

Selfie
      │
      ▼
Face Embedding B

A ↔ B
  ↓
Similarity Score
```

Résultat :

```json
{
  "similarity_score": 0.94,
  "match": true
}
```

Le seuil de décision devra être déterminé à partir des performances mesurées sur un dataset représentatif, et non choisi arbitrairement.

---

## 22. Fraud AI

Un moteur transversal devra combiner différents signaux.

Exemple :

```
Document Risk
      +
Liveness Risk
      +
Face Match
      +
Device Signals
      +
Behavior Signals
      +
Verification History
      ↓
Fraud Risk Model
      ↓
Risk Score
```

Cela permettra à terme de détecter des comportements frauduleux qui ne sont pas visibles sur le seul document.

---

## 23. Risk Engine

Le moteur de risque recevra les résultats de tous les modèles.

```
Document = 0.97
Liveness = 0.94
Face Match = 0.92
Fraud = 0.06
```

Il produira :

```json
{
  "risk_level": "LOW",
  "risk_score": 0.08,
  "decision": "APPROVED"
}
```

Les règles devront être configurables.

---

## 24. Decision Engine

Trois résultats principaux :

- `APPROVED`
- `REJECTED`
- `MANUAL_REVIEW`

Exemple :

```
Liveness faible
       ↓
Manual Review

Document falsification élevée
       ↓
Reject

Tous les contrôles satisfaits
       ↓
Approve
```

Le système devra conserver les raisons de la décision.

---

## 25. Amazon Bedrock

Bedrock sera utilisé pour les fonctions où un LLM apporte une réelle valeur.

### Cas d'utilisation

- explication des résultats ;
- résumé d'un dossier ;
- classification de texte ;
- analyse de documents non structurés ;
- analyse d'adverse media ;
- assistance aux analystes ;
- génération de rapports.

Exemple :

```
Document Checks
AML Results
Risk Signals
Manual Review Notes
       ↓
     Bedrock
       ↓
Résumé du dossier
```

Bedrock ne devra pas être le décideur final du KYC.

Les décisions critiques devront rester basées sur des règles et modèles évaluables et auditables.

---

## 26. Stack technique applicative

La plateforme applicative (hors modèles IA) reposera sur une stack Java / TypeScript déployée sur AWS.

```
Applicants / SDK capture          Clients SaaS (dashboard)
        │                                    │
        ▼                                    ▼
   Next.js (hosted flow)              Next.js (console)
        │                                    │
        └────────────────┬───────────────────┘
                         ▼
              API Gateway / ALB
                         ▼
         Spring Boot — Verification API
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
   PostgreSQL      Redis / SQS          S3
   (Amazon RDS)    (jobs, cache)     (médias)
         │
         ▼
   Risk / Decision Engine (Spring)
         │
         ▼
   AWS AI (Textract, Rekognition, SageMaker, Bedrock)
```

### Backend — Spring Boot

- Java 21 LTS
- Spring Boot 3 (Web, Security, Validation, Actuator)
- Spring Data JPA / Hibernate
- Spring WebFlux ou RestClient pour les appels AWS asynchrones
- AWS SDK for Java v2 (S3, SQS, Textract, Rekognition, Bedrock, KMS)
- Flyway pour les migrations de schéma
- Springdoc OpenAPI pour la documentation `/v1`
- MapStruct pour le mapping DTO ↔ domaine

Responsabilités du backend :

- API publique multi-tenant ;
- orchestration du pipeline de vérification ;
- moteur de règles et de décision ;
- workers SQS (document, liveness, face match, risk, webhooks) ;
- audit logs, rétention, suppression ;
- isolation des tenants.

### Base de données — PostgreSQL

- Amazon RDS for PostgreSQL (région UE, chiffrement KMS)
- schéma relationnel versionné (Flyway)
- isolation multi-tenant par `organization_id` (row-level) ; schéma ou base dédiée possible en phase avancée
- types JSONB pour payloads vendor / scores ML
- pgcrypto ou colonnes chiffrées applicativement pour les PII sensibles

Entités principales : `Organization`, `ApiKey`, `Verification`, `Document`, `Selfie`, `CheckResult`, `Decision`, `WebhookEndpoint`, `WebhookDelivery`, `AuditEvent`, `Consent`.

### Cache, files et asynchrone

- Amazon ElastiCache (Redis) : sessions hosted flow, rate limiting, locks
- Amazon SQS : file de traitement document / biométrie / webhooks
- Amazon EventBridge (optionnel) : événements internes `verification.*`

### Frontend — Next.js

- Next.js (App Router) + TypeScript
- Tailwind CSS
- deux surfaces :

  | Application | Rôle |
  |---|---|
  | Console SaaS | dashboard, détail d'une vérification, webhooks, usage, revue manuelle |
  | Hosted flow | parcours applicant : consentement, capture document, liveness, statut |

- SDK web de capture (caméra, qualité, cadrage) intégré au hosted flow
- authentification console : email + 2FA (Spring Security / OIDC)

Le frontend n'appelle pas Textract, Rekognition ni SageMaker directement. Tous les appels IA passent par l'API Spring Boot.

### Infrastructure applicative AWS

| Composant | Service |
|---|---|
| API & workers | Amazon ECS Fargate (conteneurs Spring Boot) |
| Console & hosted flow | Amazon CloudFront + S3, ou ECS Fargate (Next.js) |
| Base | Amazon RDS PostgreSQL |
| Cache | Amazon ElastiCache Redis |
| Médias | Amazon S3 + KMS, URLs signées TTL court |
| Secrets | AWS Secrets Manager |
| Observabilité | CloudWatch, traces corrélées `verification_id` (sans PII) |

### Principes

- un backend unique Spring Boot pour l'API, l'orchestration et les workers (modules Maven/Gradle séparés si besoin) ;
- PostgreSQL comme source de vérité des dossiers, décisions et audit ;
- S3 comme source de vérité des médias ;
- Next.js pour l'UX client et applicant uniquement ;
- les modèles IA restent des services appelés, jamais embarqués dans le JVM du MVP.

---

## 27. API SaaS

L'API permettra aux entreprises clientes d'intégrer le service.

### Principales routes

| Méthode | Chemin | Description |
|---|---|---|
| `POST` | `/v1/verifications` | Créer une vérification |
| `GET` | `/v1/verifications/{id}` | Lire une vérification |
| `POST` | `/v1/verifications/{id}/document` | Uploader un document |
| `POST` | `/v1/verifications/{id}/selfie` | Uploader un selfie |
| `GET` | `/v1/verifications/{id}/results` | Récupérer le résultat |
| `POST` | `/v1/webhooks` | Enregistrer un webhook |
| `GET` | `/v1/usage` | Consulter la consommation |

---

## 28. Webhooks

Événements :

- `verification.created`
- `document.uploaded`
- `document.processing`
- `document.completed`
- `liveness.started`
- `liveness.completed`
- `face_match.completed`
- `risk.completed`
- `verification.approved`
- `verification.rejected`
- `verification.manual_review`

---

## 29. Dashboard

Le client SaaS devra disposer de :

### Dashboard

- nombre de vérifications ;
- taux de réussite ;
- taux de rejet ;
- taux de revue manuelle ;
- temps moyen ;
- fraude détectée ;
- consommation.

### Détail d'une vérification

```
Applicant
   │
   ├── Document
   │    ├── OCR
   │    ├── MRZ
   │    ├── Authenticity
   │    └── Fraud
   │
   ├── Face
   │    ├── Quality
   │    ├── Liveness
   │    └── Matching
   │
   └── Risk
        └── Decision
```

---

## 30. Architecture AWS IA

```
                        CLIENT
                           │
              Next.js (hosted flow / SDK)
                           │
                           ▼
                    API Gateway / ALB
                           │
                           ▼
              Spring Boot — Verification API
                           │
            ┌──────────────┼──────────────┐
            │              │              │
            ▼              ▼              ▼
     RDS PostgreSQL      S3 + KMS      Redis
            │
            ▼
       SQS / Events
            │
    ┌───────┴────────┐
    │                │
    ▼                ▼
Document ML       Biometric ML
    │                │
    └───────┬────────┘
            ▼
 Textract / Rekognition / SageMaker AI
            │
     ┌──────┴──────┐
     ▼             ▼
 Fraud Model   Liveness Model
     │             │
     └──────┬──────┘
            ▼
  Risk Engine (Spring Boot)
            │
            ▼
 Decision Engine (Spring Boot)
            │
       ┌────┴────┐
       ▼         ▼
    Approve    Review
            │
            ▼
     Next.js — Dashboard SaaS
```

---

## 31. Stack IA AWS

### Document AI (dès le premier pipeline d’analyse)

- Amazon SageMaker Ground Truth → annotation (coins, type, fraude, qualité, …)
- Amazon SageMaker AI → entraînement, registry, **endpoints** détection / classification / authenticité-fraude
- Amazon S3 → datasets versionnés et médias
- Amazon Textract → OCR des champs **en complément** de la vision propriétaire (remplaçable par OCR proprio)
- Amazon Bedrock → hors décision (résumés analyste, etc.)

### Biométrie

- Amazon Rekognition → face detection / comparaison / liveness **en complément** tant que le liveness / match propriétaire n’égale pas les métriques
- SageMaker → liveness / anti-spoof / embeddings propriétaires dès que le dataset biométrique le permet (pas un palier « Rekognition forever »)

Il n’y a **pas** de phase « heuristiques documentaires puis modèle ». La phase avancée, c’est l’**élargissement** (plus de classes, liveness passif, OCR 100 % proprio, multimodal), pas le premier modèle.

---

## 32. MLOps

Le projet devra intégrer une véritable chaîne MLOps :

```
Data
 ↓
Annotation
 ↓
Dataset Version
 ↓
Training
 ↓
Evaluation
 ↓
Model Registry
 ↓
Staging
 ↓
A/B Test
 ↓
Production
 ↓
Monitoring
 ↓
Retraining
```

Avec SageMaker :

- SageMaker Training ;
- SageMaker Ground Truth ;
- SageMaker Model Registry ;
- SageMaker Pipelines ;
- endpoints d'inférence ;
- monitoring des modèles.

---

## 33. Évaluation des modèles

Les performances devront être mesurées séparément.

### Document

- OCR accuracy ;
- document classification accuracy ;
- fraud detection ;
- false acceptance rate ;
- false rejection rate.

### Liveness

- APCER ;
- BPCER ;
- taux d'échec ;
- taux d'abandon ;
- performance par appareil ;
- performance par conditions lumineuses ;
- performance par type d'attaque.

### Face Matching

- false match rate ;
- false non-match rate ;
- performance selon différents seuils.

---

## 34. Sécurité et données

Les documents, selfies et vidéos sont des données extrêmement sensibles.

Le système devra prévoir :

- chiffrement ;
- KMS ;
- contrôle d'accès ;
- isolation des tenants ;
- audit logs ;
- rétention configurable ;
- suppression ;
- accès restreint aux images ;
- anonymisation/pseudonymisation des datasets ML ;
- séparation production/training ;
- traçabilité des accès.

Les exigences réglementaires devront être définies selon les marchés ciblés, notamment concernant les données biométriques et la protection des données personnelles.

---

## 35. Roadmap

Vision produit : **V1 = plateforme + vision documentaire propriétaire** → V2 élargissement des modèles et biométrie proprio → V3 avantage technologique (voir §36). Pas de V1 « Textract + heuristiques de coins ».

Le détail du premier produit (parcours, décision) est dans [`specs/specification-fonctionnelle-idv.md`](./specs/specification-fonctionnelle-idv.md). **Ordre de build IDV :** [`specs/roadmap-implementation-idv.md`](./specs/roadmap-implementation-idv.md). Guides d’implémentation : [`specs/guide-implementation-s1.md`](./specs/guide-implementation-s1.md) (livré), [`specs/guide-implementation-s2.md`](./specs/guide-implementation-s2.md).

### Phase 1 — MVP

Moteurs : Document (modèles proprio) · Liveness · Face Match · décision par **règles** Spring.

Stack : Spring Boot, PostgreSQL, Next.js. Dataset + Ground Truth + endpoints SageMaker (détection, classification, authenticité). Lecture : Textract et/ou OCR proprio + MRZ déterministe. Biométrie : Rekognition en complément si le modèle liveness/match n’est pas encore au niveau. Dashboard, API, webhooks.

| Sprint | Semaines | Livrable |
|---|---|---|
| 0 | 1–2 | Fondation : repo, Docker, CI, org / clés API |
| 1 | 3–4 | Vérifications multi-tenant + lien KYC |
| 2 | 5–6 | Hosted flow, capture document, S3 ; **amorçage dataset / annotation coins-type-fraude** |
| 3 | 7–8 | **Inférence SageMaker** détection + classification ; lecture + MRZ ; pas de détecteur heuristique |
| 4 | 9–10 | Authenticité / fraude document (modèle) ; liveness |
| 5 | 11–12 | Face match, risk engine, décision auto |
| 6 | 13–14 | Dashboard + revue manuelle |
| 7 | 15–16 | Webhooks, sandbox, usage |
| 8 | 17–18 | KMS, rétention, 2FA, isolation |
| 9 | 19–20 | Staging AWS + go-live design partners |

Hors premier go-live : liveness passif, OCR 100 % proprio si Textract encore en lecture, AML/KYB, corridor documents élargi.

### Phase 2 — Élargissement IA

- plus de pays / types / fraudes ;
- réentraînement continu, A/B, monitoring dérive ;
- liveness / embeddings propriétaires dès que les métriques battent le complément Rekognition ;
- OCR propriétaire si encore hybride.

### Phase 3 — plateforme avancée

- passive liveness ;
- deepfake / injection / device intelligence ;
- AML, PEP, sanctions ;
- KYB, UBO ;
- continuous monitoring.

---

## 36. Priorité stratégique

Le produit sera défini autour de cette progression :

```
               V1
                │
     Plateforme (API, flow, isolation)
     + dataset annoté
     + modèles documentaires propriétaires
     (détection, classification, authenticité)
     AWS = infra + accélérateurs (GT, train, OCR lecture, bio complément)
                │
                ▼
               V2
                │
      Plus de classes, biométrie proprio,
      OCR proprio, MLOps industriel
                │
                ▼
               V3
                │
      AWS infrastructure
      +
      YOUR AI TECHNOLOGY
```

AWS est l’infrastructure et l’usine ML. L’avantage documentaire (détection, classification, authenticité / fraude) **n’attend pas** une génération heuristique : il est le cœur du premier pipeline d’analyse. La décision d’approbation / refus / revue reste des règles mesurables, pas un LLM juge.
