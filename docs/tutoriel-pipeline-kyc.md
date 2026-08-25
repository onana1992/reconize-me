# Tutoriel — le cycle KYC de bout en bout

**Produit :** Recogniz-Me — SaaS KYC avec moteur IA propriétaire  
**Date :** 25 août 2026  
**Statut :** tutoriel conceptuel  
**Documents liés :** [`cahier-des-charges.md`](./cahier-des-charges.md) · [`roadmap-implementation.md`](./roadmap-implementation.md) · [`architecture-implementation.md`](./architecture-implementation.md)

Ce document explique **les concepts** et **les cycles** du parcours global. La vision produit reste dans le cahier des charges ; l’ordre de build reste dans la roadmap.

---

## 0. Ce que le pipeline répond

Une **vérification KYC** répond à trois questions, dans cet ordre :

1. **La pièce est-elle réelle et lisible ?** (Document AI)
2. **La personne devant la caméra est-elle réelle et est-ce la même que sur la pièce ?** (Biometric AI)
3. **Le dossier, dans son ensemble, est-il assez sûr pour une décision automatique ?** (Fraud + Risk + Decision)

Le schéma du cahier des charges n’est pas une liste de features. C’est une **machine à états** : chaque étape consomme la précédente, produit un signal, et peut **bloquer**, **faire recapturer**, ou **laisser passer avec un score**.

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

---

## 1. Trois cycles, pas un seul tuyau

Il y a trois horloges différentes. Les confondre est la principale source de confusion.

```
CYCLE A — CONTRAT (humain, synchrone)
  Client B2B crée un dossier
       → lien KYC
       → applicant consent
       → capture (caméra)

CYCLE B — ANALYSE DOCUMENT (machine, asynchrone)
  Qualité → détection → classification
       → OCR → MRZ
       → authenticité → fraude document

CYCLE C — BIOMÉTRIE + DÉCISION (machine, asynchrone)
  Selfie → visage → liveness → face match
       → risk → APPROVE / REJECT / REVIEW
```

**Cycle A** se passe dans le navigateur (hosted flow Next.js).  
**Cycles B et C** se passent dans Spring Boot + workers SQS + AWS. Le front n’appelle jamais Textract ni Rekognition.

Un dossier n’est « terminé » que lorsque C a produit une décision. Jusque-là, le client SaaS voit un statut intermédiaire (`created`, `pending_consent`, `document.processing`, etc.).

Les cinq moteurs du produit se mappent ainsi :

```
Document AI     = capture → qualité → détection → classification
                  → OCR → MRZ → authenticité → fraude document
Biometric AI    = selfie → face detection → liveness → face matching
Fraud AI        = fraude document + signaux transverses (mince en V1)
Risk Engine     = agrégation des scores
Decision Engine = APPROVE / REJECT / REVIEW
```

---

## 2. Les acteurs

| Acteur | Rôle | Canal |
|---|---|---|
| **Client SaaS** (fintech, banque) | Démarre la vérif, récupère le résultat | API `POST /v1/verifications` ou console |
| **Applicant** | La personne à identifier | Lien hosted flow, **sans** clé API |
| **Moteurs IA** | Produisent des scores, pas la décision finale | Textract, Rekognition, plus tard SageMaker |
| **Risk / Decision** | Trancher de façon auditable | Règles Spring Boot |
| **Analyste** (Sprint 6+) | Tranche les `MANUAL_REVIEW` | Console |

Règle produit : **Bedrock n’est jamais le juge**. Il pourra résumer un dossier ; la décision reste des règles et des modèles mesurables.

---

## Cycle A — Ouvrir le dossier

### A.1 Création de vérification

**Concept.** Un dossier vide, isolé par tenant (`organization_id`). Pas encore d’image, pas encore d’identité extraite. C’est un **contrat** : « vérifie cette personne pour moi ».

Le client envoie éventuellement un `external_id` (son ID utilisateur) et des métadonnées. L’API crée une `Verification` au statut `created`, écrit un `AuditEvent`, et retourne un id opaque.

**Pourquoi ça existe.** Sans ce dossier, il n’y a nulle part où accrocher consentement, médias S3, scores et décision. C’est aussi le point d’**idempotence** (`Idempotency-Key`) : un retry réseau ne doit pas créer deux KYC.

**Sortie.** `id`, `status`, `hosted_url`, `expires_at`.

---

### A.2 Lien KYC

**Concept.** URL publique du hosted flow, authentifiée par un **token court** (Redis), pas par la clé API du client.

L’applicant n’est pas un utilisateur de la console. Il ne doit voir **que** son parcours. Le token expire ; un lien périmé affiche une page d’erreur, pas le dossier.

**Pourquoi.** Le client SaaS n’a pas à héberger la caméra. Il envoie un SMS/email avec le lien (comme Onfido Studio / hosted workflow).

**Cycle.** Le lien est le pont entre le backend client et l’humain. S’il expire avant le consentement, le dossier reste `created` / expire ; le client doit en créer un autre (ou renouveler le token, selon les règles produit).

---

### A.3 Consentement

**Concept.** Base légale du traitement : biométrie + pièce d’identité. On enregistre **qui**, **quand**, **quelle version du texte**.

- Accepter → statut du type `pending_applicant` (prêt pour la caméra).
- Refuser → `declined`. Le pipeline s’arrête. Aucune capture.

**Pourquoi avant la caméra.** Sans consentement horodaté, on ne peut pas stocker une photo de passeport ni un selfie. Le Sprint 1 s’arrête volontairement ici.

**Cycle.** C’est un **gate** binaire. Pas de score, pas de retry IA. Soit on entre dans le cycle document, soit on sort.

---

## Cycle B — Comprendre le document

À partir d’ici, l’idée est : **ne jamais analyser une mauvaise photo**, et **ne jamais faire confiance à une seule source de données**.

Deux boucles de retry existent :

```
[Caméra] → qualité client KO → recapture (l’humain recommence)
[S3]     → qualité serveur KO → recapture ou REJECT
         → type non supporté → unsupported_document (sortie)
         → OCR/MRZ incohérents → signal de risque (on continue souvent)
```

Les premiers échecs sont **opérationnels** (flou). Les derniers sont **fraude** (tamper). On ne les traite pas de la même façon.

---

### B.1 Capture document

**Concept.** Obtenir une image utilisable : accès caméra, cadrage, capture auto ou manuelle, **recto/verso** si la pièce l’exige (carte d’identité).

Le fichier va dans **S3 chiffré (KMS)** via URL signée. Jamais le binaire en JSON. Événement interne `document.uploaded`.

**Pourquoi.** La qualité du reste du pipeline (OCR, MRZ, authenticité, crop du portrait) dépend presque entièrement de cette image. Une capture médiocre coûte cher : revue humaine = coût n°1.

**Cycle.** L’humain est encore dans la boucle. Le SDK peut recapturer **avant** l’upload. Après l’upload, le worker prend le relais ; l’applicant attend ou passe au selfie selon le produit.

V1 : capture manuelle + contrôles client. La détection auto des coins viendra ensuite.

---

### B.2 Document Quality

**Concept.** Filtre **avant** l’IA chère. On pose : « cette image peut-elle être lue et authentifiée ? »

Signaux typiques : flou, luminosité, surexposition, sous-exposition, reflets, document coupé, perspective, résolution, document trop petit, image inutilisable.

**Deux étages :**

| Étage | Où | But |
|---|---|---|
| Client (SDK) | Téléphone, avant upload | Recapture immédiate, zéro coût Textract |
| Serveur | Worker, après S3 | Taille, MIME, dimensions ; filet de sécurité |

**Cycle.** Qualité KO → **on ne lance pas OCR**. On demande une nouvelle photo ou on rejette. Qualité OK → on passe à la détection.

C’est le premier **fail-fast** du cycle B.

---

### B.3 Document Detection

**Concept.** Computer vision : y a-t-il une pièce ? Où sont les **quatre coins** ? Quelle orientation / perspective ?

Sortie type :

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

Puis **correction de perspective** (redresser le document comme s’il était vu de face).

**Pourquoi.** OCR et MRZ sur une photo de travers sont mauvais. Le crop du portrait (pour le face match plus tard) a besoin d’une géométrie stable.

**Cycle.** Pas de document / coins incohérents → recapture. Document détecté → image « aplatie » envoyée à la classification.

En V1, ce sera souvent heuristique (contours, ratio). Le modèle propriétaire est Phase 2.

---

### B.4 Document Classification

**Concept.** Répondre : **quel pays, quel type, éventuellement quelle version** (`CA` + `passport`, `FR` + `national_id`, …).

Sans ça, on ne sait pas :

- s’il faut un verso ;
- quel layout OCR attendre ;
- si une MRZ TD3 (passeport, 2 lignes) ou TD1 (carte, 3 lignes) s’applique ;
- si la pièce est dans l’**allow-list V1** (2–4 pays).

Hors allow-list → `unsupported_document` → en pratique **REJECTED** (règle Sprint 5). On n’essaie pas de « deviner » un permis inconnu.

**Cycle.** Classification = aiguillage du parseur. Une erreur ici cascade : mauvais parseur MRZ, faux mismatch OCR, faux positif fraude.

---

### B.5 OCR

**Concept.** Lire les champs **visuels** (zone humaine) : nom, prénom, date de naissance, numéro, expiration.

MVP : Amazon Textract `AnalyzeID`. Le code **normalise** vers un JSON canonique :

```json
{
  "first_name": "JOHN",
  "last_name": "SMITH",
  "date_of_birth": "1990-05-12",
  "document_number": "ABC123456",
  "expiration_date": "2030-05-12"
}
```

**Pourquoi normaliser.** Chaque pays écrit les dates et les noms autrement. Le risk engine et le dashboard ne doivent connaître qu’**un** schéma.

**Limite.** L’OCR visuel se trompe (O/0, reflets, polices). On ne s’y fie jamais seul — d’où l’étape suivante.

**Cycle.** OCR produit `extracted_identity` **provisoire**. Document expiré (date < aujourd’hui) est déjà un signal de rejet, même si la pièce est authentique.

---

### B.6 MRZ / Data Parsing

**Concept.** Seconde source d’identité, indépendante de la zone visuelle.

La **MRZ** (Machine Readable Zone) : 2 ou 3 lignes de caractères OCR-B en bas de la pièce (norme ICAO 9303). Elle encode les mêmes champs, plus des **check digits**.

Exemple de zone passeport (TD3) :

```
P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
L898902C36UTO7408122F1204159ZE184226B<<<<<10
```

Le parsing fait six choses :

1. Localiser la zone (`mrz_region`)
2. Lire les lignes
3. Décoder le layout (TD3 passeport / TD1 carte / TD2)
4. Valider les check digits
5. Extraire les champs (dates `YYMMDD` → `YYYY-MM-DD`, `ERIKSSON<<ANNA` → nom / prénom)
6. **Comparer MRZ ↔ OCR visuel**

```
OCR  name = JOHN SMITH
MRZ  name = JOHN SMITH
         → MATCH
```

Incohérence → **signal de risque**, pas forcément un arrêt immédiat. Un check digit faux est plus grave (zone altérée ou OCR MRZ raté).

**Data parsing** = le travail plus large : fusionner Textract + MRZ en **un** enregistrement applicant propre (translitération, dates, types).

| Couche | Source | Rôle |
|---|---|---|
| **OCR** | Champs visuels (Textract AnalyzeID) | Données lisibles, souvent bruitées |
| **MRZ parse** | Zone ICAO + check digits | Données structurées, checksummées |
| **Compare** | Les deux | Signal tamper / transcription / faux document |

**Cycle.** C’est le premier **contrôle croisé**. Deux lectures d’accord → confiance. Désaccord fort → authenticité / fraude / plus tard `REJECTED` ou `MANUAL_REVIEW`.

---

### B.7 Document Authenticity

**Concept.** « Cette image est-elle celle d’une **vraie pièce physique non altérée** ? » — cœur de la propriété intellectuelle du produit.

On ne relit plus le nom. On cherche la **falsification** :

- cohérence des données (déjà MRZ ↔ OCR)
- cohérence visuelle (hologrammes, polices, positions)
- zones retouchées, compression anormale, texture, police
- photo collée / remplacée
- manipulation locale
- recto ≠ verso
- screenshot, photocopie, impression

Sortie :

```json
{
  "authenticity_score": 0.96,
  "tampering_score": 0.04,
  "status": "PASS"
}
```

**V1 vs plus tard.** V1 : règles + heuristiques (MRZ, dates, qualité, éventuellement signaux Textract). Phase 2 : modèle SageMaker entraîné sur dataset annoté (`tampering_region`, `fraud_type`).

**Cycle.** `FAIL` fort → le risk engine inclinera vers **REJECT**. Score gris → **REVIEW**. On enchaîne quand même souvent vers la biométrie : un faux document + un vrai visage (ou l’inverse) est une information.

---

### B.8 Fraud Detection (document)

**Concept.** Couche au-dessus de l’authenticité : combiner **OCR + MRZ + features image** en une **probabilité de fraude**.

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

- **Authenticité** = « la pièce a-t-elle été bidouillée ? »
- **Fraud document** = « ce dossier ressemble-t-il aux fraudes connues ? » (screenshot, print, photo morphée, etc.)

En V1, ce moteur est encore **maigre** (règles). Le modèle propriétaire est Phase 2, une fois le dataset constitué.

**Cycle.** Fin du cycle B. Événement `document.completed`. L’applicant passe au selfie. Rien n’est encore `APPROVED` : on n’a pas prouvé que **la personne** est là.

---

## Cycle C — Prouver la personne, puis trancher

Question : **un humain vivant, qui est le titulaire de la pièce, est-il devant la caméra maintenant ?**

Trois sous-questions distinctes :

| Étape | Question | Attaque si on saute |
|---|---|---|
| Face detection / quality | Y a-t-il un visage utilisable ? | Selfie flou, 2 visages |
| Liveness | Est-ce un humain **présent**, pas une attaque ? | Photo imprimée, écran, replay |
| Face matching | Est-ce **la même** personne que sur la pièce ? | Pièce volée + complice |

Un liveness parfait + un mismatch visage = usurpation. Un match parfait + liveness nul = attaque de présentation. Il faut les **deux**.

---

### C.1 Selfie / Video

**Concept.** Capture biométrique : photo et/ou vidéo courte, selon liveness actif.

Même discipline que le document : caméra, qualité, S3 chiffré, jamais de média dans l’API JSON. Événements `liveness.started` / `liveness.completed`.

**Cycle.** Gate UX : consignes (« tournez la tête à gauche »). L’humain est de nouveau dans la boucle. Après upload, tout est machine.

---

### C.2 Face Detection

**Concept.** Un visage, et un seul : présence, position, taille, orientation, nombre.

- Plusieurs visages → refus de la session (quel visage comparer ?).
- Visage trop petit / de profil extrême → qualité insuffisante → recapture.

**Face quality** (souvent collé ici) : luminosité, netteté, occlusion, traits visibles.

```json
{
  "quality_score": 0.93,
  "face_size": "valid",
  "pose": "valid",
  "lighting": "valid"
}
```

**Cycle.** Fail-fast biométrique, analogue à Document Quality. Pas de liveness sur une image inutilisable.

---

### C.3 Liveness

**Concept.** Détecter une **attaque de présentation** : photo papier, photo sur téléphone, écran, replay vidéo, injection de flux, plus tard deepfake.

**V1 = liveness actif (challenge) :**

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

Le système **tire au sort** un geste, puis vérifie qu’il a bien eu lieu. Une photo figée ne tourne pas la tête à la demande.

**Plus tard = liveness passif :** une vidéo sans consigne, modèle anti-spoof spatial + temporel. Meilleure UX, plus dur à fiabiliser.

**Cycle.** Liveness faible → en V1 plutôt **MANUAL_REVIEW** (pas toujours REJECT : les faux négatifs liveness sont fréquents). Photo imprimée / écran figé doit échouer ou partir en revue.

Chaque tentative est journalisée (coût AWS + audit).

---

### C.4 Face Matching

**Concept.** Deux embeddings (vecteurs) :

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

MVP : Rekognition `CompareFaces`. Le **seuil** ne se choisit pas « au feeling » : il se calibre sur un dataset (faux acceptés vs faux rejets).

**Cycle.** Sous le seuil → `MANUAL_REVIEW` ou `REJECTED`. Au-dessus → signal vert pour le risk engine.

C’est la dernière brique **métier** avant l’agrégation. Événement `face_match.completed`.

---

## Cycle D — Trancher (et rester auditable)

Les moteurs AI **proposent des nombres**. Ils ne disent pas « ce client a le droit d’ouvrir un compte ». Ça, c’est le métier du client SaaS, exprimé en **règles versionnées**.

---

### D.1 Risk Engine

**Concept.** Agréger tous les signaux en un niveau de risque, **configurable par organisation**.

Entrées V1 :

```
Document (qualité, expiry, MRZ match, authenticity)
+ Liveness
+ Face match
(+ plus tard : device, comportement, historique, AML)
```

Exemple d’entrée :

```
Document = 0.97
Liveness = 0.94
Face Match = 0.92
Fraud = 0.06
```

Sortie :

```json
{
  "risk_level": "LOW",
  "risk_score": 0.08
}
```

Ce n’est **pas** encore la décision. Deux orgs peuvent avoir le même score et des politiques différentes (fintech stricte vs marketplace souple).

**Cycle.** Un seul passage, après tous les checks. Événement `risk.completed`. Pas de recapture ici : trop tard ; on décide.

---

### D.2 Decision Engine

**Concept.** Mapper scores + flags vers **trois issues**, avec **reasons persistées**.

| Issue | Sens | Suite |
|---|---|---|
| `APPROVED` | Tous les contrôles au-dessus des seuils | Webhook `verification.approved` ; le client onboard |
| `REJECTED` | Preuve suffisante d’échec / fraude / pièce invalide | Stop ; le client refuse |
| `MANUAL_REVIEW` | Zone grise | File analyste (Sprint 6) |

Exemples de règles V1 (à calibrer) :

| Signal | Décision typique |
|---|---|
| Document expiré / type non supporté | `REJECTED` |
| Tamper / mismatch MRZ fort | `REJECTED` ou `MANUAL_REVIEW` |
| Liveness faible | `MANUAL_REVIEW` |
| Face match sous seuil | `MANUAL_REVIEW` ou `REJECTED` |
| Tous les checks OK | `APPROVED` |

**Invariant go-live :** aucun `APPROVED` si un check **critique** a échoué.

Le système conserve **pourquoi** (`reasons[]` + audit qui / quand). C’est ce qui rend le produit vendable à une équipe compliance.

Bedrock pourra expliquer ou résumer un dossier ; il ne tranche pas.

---

## 3. Carte mentale : où ça boucle, où ça coule

```
                    ┌─ recapture ─┐
                    ▼             │
Consent ──► Capture ──► Quality ──┘
                │ OK
                ▼
         Detect → Classify
                │ unsupported → REJECT
                ▼
         OCR ⇄ MRZ (cross-check)
                ▼
         Authenticity / Fraud     ← signaux, on continue
                ▼
         Selfie ──► Face quality ──► recapture ?
                ▼
         Liveness ──► Face match
                ▼
         Risk ──► Decision ──► APPROVE | REJECT | REVIEW
                                      │
                                      └── REVIEW → analyste → décision humaine
```

- **Boucles** = qualité capture (document et visage). L’humain recommence.
- **Sorties précoces** = refus de consentement, pièce non supportée, qualité irrécupérable, expiry.
- **Flux unique** = authenticité → biométrie → risk → decision. On n’« essaie pas une autre IA » après REJECT.

---

## 4. Exemple de parcours

1. La fintech crée une vérif → lien SMS.
2. Marie accepte le consentement.
3. Elle photographie son passeport ; le SDK refuse une photo floue (**qualité**, boucle).
4. L’image nette est redressée (**detection**), classée `CA / passport` (**classification**).
5. Textract lit `MARIE DUPONT`, `1994-02-03` (**OCR**).
6. La MRZ dit la même chose, check digits OK (**parsing**). Match OCR/MRZ.
7. Pas de signe de photoshop grossier (**authenticité** V1).
8. Challenge « tournez à gauche » réussi (**liveness**).
9. Similarité portrait ↔ selfie 0.96 (**face match**).
10. Risk `LOW` → **APPROVED**, reasons `["document_valid","mrz_match","liveness_pass","face_match_above_threshold"]`.
11. Webhook signé vers la fintech.

Même parcours, MRZ `MARIE` vs OCR `MARIA` + tamper élevé → **REJECT** ou **REVIEW**, jamais un APPROVE silencieux.

---

## 5. Où ça s’implémente (Phase 1)

| Bloc du schéma | Sprint | En V1 |
|---|---|---|
| Création + lien + consentement | S1 | Spécifié |
| Capture + qualité client | S2 | Pas d’OCR |
| OCR Textract + MRZ + classification allow-list | S3 | Mock sandbox |
| Liveness actif | S4 | Rekognition ou challenge pose |
| Face match + risk + decision | S5 | Règles Spring, pas Bedrock |
| Revue manuelle | S6 | Analyste |
| Webhooks | S7 | HMAC + retry |

Hors V1 : SageMaker en production, OCR propriétaire, liveness passif, AML/KYB, Bedrock comme décideur.
