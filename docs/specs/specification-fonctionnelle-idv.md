# Spécification fonctionnelle — Identity & Document Verification

**Plateforme :** Recogniz-Me  
**Produit :** Identity & Document Verification (IDV)  
**Version du document :** 1.1  
**Date :** 31 août 2026  
**Statut :** spécification métier détaillée — cible produit  
**Documents liés :**
- [`../cahier-des-charges.md`](../cahier-des-charges.md) — vision produit
- [`guide-implementation-s1.md`](./guide-implementation-s1.md) — S1 livré
- [`guide-implementation-s2.md`](./guide-implementation-s2.md) — S2 capture
- [`roadmap-implementation-idv.md`](./roadmap-implementation-idv.md) — ordre de build

Ce document décrit **uniquement** le produit Identity & Document Verification : ce qu’il prouve, pour qui, comment le parcours se déroule, comment on analyse, comment on tranche. Les détails d’implémentation restent dans les guides de sprint.

---

## 1. Introduction

Identity & Document Verification est une **solution de confiance d’identité** de la gamme Recogniz-Me. Elle permet à une organisation de **prouver qui est une personne à distance**, à l’onboarding (ouverture de compte, KYC, recrutement, accès à un service réglementé).

Le client consomme un **résultat** : il n’a pas à opérer le parcours, traiter ni conserver les documents. Il crée une session, envoie un **lien hébergé**, relit le dossier et reçoit une **décision justifiée**.

C’est le **premier produit de la gamme à livrer de bout en bout**. La fondation déjà en place (dossier isolé par organisation, lien jusqu’au consentement) en est le socle, pas le produit entier.

**Ce que ce produit n’est pas**

- Un tunnel caméra sans décision.
- Le produit **Biometric Authentication** : ici le selfie sert à lier un visage **à une pièce**. Là-bas, on recompare un visage **déjà enrôlé**, sans redemander la pièce.
- Un criblage AML : l’AML est une autre solution, **attachable** à une session IDV.

---

## 2. Proposition de valeur

Trois questions, **dans cet ordre**. On ne saute pas une étape pour « aller plus vite ».

| # | Question | Si on échoue |
|---|---|---|
| 1 | La pièce est-elle **réelle et lisible** ? | Recapture, refus, ou pièce non supportée |
| 2 | La personne devant la caméra est-elle **réelle** et **la même** que sur la pièce ? | Recapture, revue, ou refus (usurpation / attaque) |
| 3 | Le dossier est-il assez sûr pour une **décision automatique** ? | Revue humaine, ou refus selon la politique |

**Entrée :** l’organisation crée une session (référence client et identité optionnelles) et transmet le lien.  
**Sortie :** approuvée, refusée, recapture demandée, revue, expirée ou annulée — plus l’identité extraite, les signaux, les raisons de décision.

---

## 3. Périmètre

### 3.1 Inclus

- Création, lecture, liste, annulation d’une session IDV, isolée par organisation
- Lien hébergé, consentement versionné (accepter / refuser)
- Capture document (recto, verso si la pièce l’exige)
- Qualité (client puis serveur), détection, classification, lecture, MRZ, authenticité, fraude document
- Selfie, qualité visage, preuve de présence réelle, comparaison au portrait de la pièce
- Moteur de risque et de décision (règles versionnées, pas un modèle de langage juge)
- Revue manuelle des dossiers en zone grise
- Enrôlement biométrique à l’approbation, si l’organisation l’active (pour une autre solution de la gamme)
- AML **attaché** optionnel (le criblage est spécifié dans le produit AML ; ici on dit seulement comment il **alimente** la décision IDV)
- Notifications de résultat (cible plateforme)
- Audit, pas de données personnelles dans les journaux

### 3.2 Hors périmètre (autres solutions ou plus tard)

- Authentification sans pièce (produit Biometric Authentication)
- AML standalone, packs de listes, surveillance continue (produit AML)
- KYB, estimation d’âge, vérification en base gouvernementale
- SDK natif iOS / Android (le hosted flow d’abord)
- Branding avancé du parcours par organisation (V1 : pas de nom du tenant à l’écran)
- Liveness passif, modèles documentaires propriétaires (après le MVP règles + accélérateurs)

### 3.3 Couverture documents V1

Liste courte de **pays × types** (à figer avant la classification) : typiquement 2–4 pays, passeport et carte d’identité.

Hors liste → pièce **non supportée** → refus (pas d’invention de parseur). L’expansion du corridor est un chantier produit, pas un « on verra à l’OCR ».

---

## 4. Glossaire IDV

Les termes plateforme (organisation, personne, applicant, session, lien hébergé, référence client) s’appliquent. Spécifiques à l’IDV :

| Terme | Définition |
|---|---|
| **Vérification** | Nom historique de la **session IDV**. Même objet métier. |
| **Pièce** | Document d’identité (passeport, carte, éventuellement permis selon corridor). |
| **Recto / verso** | Faces de la pièce. Le verso n’est exigé que si le type classé le requiert. |
| **MRZ** | Zone machine-readable (norme ICAO) : seconde lecture, avec checksums. |
| **Signal** | Résultat d’une étape (succès / échec / avertissement + code + score). Pas une décision. |
| **Présence réelle (liveness)** | Preuve que c’est un humain **présent maintenant**, pas une photo ou un écran. |
| **Face match (onboarding)** | Similarité selfie ↔ portrait **de la pièce**. |
| **Recapture** | Nouvel essai photo **sur la même session**. |
| **Revue** | Un analyste de l’organisation tranche à la place de la machine. |

---

## 5. Acteurs

| Acteur | Dans l’IDV |
|---|---|
| **Backend client** | Crée la session, récupère lien et décision, liste, annule. Clé d’intégration. |
| **Opérateur console** | Crée, copie le lien, lit la fiche. |
| **Applicant** | Ouvre le lien, consent, capture pièce puis visage. Jamais de clé. |
| **Analyste** | File des dossiers en revue : médias + signaux → approuve ou refuse. |
| **Système** | Expire le lien, enchaîne les analyses, agrège, décide, notifie, journalise. |

L’applicant ne choisit pas l’organisation : le **lien** la détermine.

---

## 6. Trois horloges

Confondre ces cycles est la principale source d’erreur de conception.

| Cycle | Nature | Contenu |
|---|---|---|
| **A — Contrat** | Humain, synchrone | Créer → lien → consentir → capturer |
| **B — Document** | Machine, asynchrone | Qualité → détection → classification → lecture → MRZ → authenticité → fraude |
| **C — Personne + décision** | Machine, asynchrone | Visage → présence réelle → face match → risque → décision |

Le parcours web **n’appelle pas** les moteurs d’analyse. Il capture. Les travailleurs analysent. Un dossier n’est **terminé** (côté preuve d’identité) que lorsque le cycle C a produit une décision — sauf sortie anticipée (refus de consentement, expiration, annulation, pièce non supportée, plafond d’essais).

---

## 7. Cycle de vie de la session

```
                         création
                            │
                            ▼
                         créée
                            │
          ouverture lien    │     le client annule
                ┌───────────┼───────────┐
                ▼           ▼           ▼
         consentement    annulée     expirée
                │
         accept / refuse
           ┌────┴────┐
           ▼         ▼
    en attente     refusée
    de capture   (consentement)
           │
           ▼
       document  ←── recapture qualité / verso
           │
           ▼
        selfie   ←── recapture présence / visage
           │
           ▼
      traitement
           │
      ┌────┼──────────┐
      ▼    ▼          ▼
 approuvée  refusée   revue ──► analyste ──► approuvée ou refusée
      │
      └── enrôlement (si option active)
```

**Recapture demandée** relance la photo sur **la même** session, dans la limite d’essais (trois par défaut). On ne crée pas un second dossier.

| Statut | Signification | Terminal |
|---|---|---|
| Créée | Dossier ouvert, lien jamais ouvert | non |
| Consentement | Page de consentement affichée | non |
| En attente de capture | Consentement accepté, caméra pas commencée | non |
| Document | En attente d’une pièce acceptable | non |
| Selfie | Pièce acceptable, en attente du visage | non |
| Traitement | Médias reçus, analyses en cours | non |
| Revue | La machine ne tranche pas seule | non |
| Recapture demandée | Photo à refaire (qualité, verso, présence) | non |
| Approuvée | Identité vérifiée | oui |
| Refusée | Consentement refusé **ou** décision négative | oui |
| Expirée | Lien ou session périmée | oui |
| Annulée | Arrêt par l’organisation | oui |

**Fondation livrée :** créée, consentement, en attente de capture, refusée (consentement), expirée, annulée. Les autres n’existent qu’avec la capture et la décision — ne pas les inventer en base avant le sprint qui les utilise.

**Annulation (fondation) :** autorisée tant que le dossier n’a pas dépassé le consentement. Ensuite : tant qu’il n’est pas trop avancé en traitement. Annuler **révoque le lien**.

---

## 8. Cycle A — Contrat (humain)

### 8.1 Créer une session

L’organisation, authentifiée par sa clé, ouvre un dossier vide : pas encore d’image, pas encore d’identité extraite. C’est le **contrat** : « prouve cette personne pour moi ».

Champs possibles, **tous optionnels** pour l’IDV : référence client, prénom, nom, email, métadonnées (contexte métier du client, pas de secrets).

L’identité saisie à la création est un **indice**. La vérité après analyse, c’est la **pièce** (et la MRZ). Un écart indicé / lu peut devenir un signal, pas une preuve à lui seul.

**Effets :** identifiant opaque généré par la plateforme ; statut *créée* ; lien hébergé émis ; audit.

**Conflits :**

- Même référence client déjà utilisée **dans cette organisation** (même produit, selon politique) → conflit.
- Rejouer la **même** création (idempotence) → **le même** dossier, pas un doublon.
- Même clé d’idempotence, autre contenu → conflit.

Sans session, rien n’accroche le consentement, les médias, les signaux ni la décision.

### 8.2 Lien hébergé

URL publique du parcours, authentifiée par **possession du secret** dans l’URL, pas par la clé de l’organisation.

- Un lien = **une** session.
- TTL (défaut : une heure). Inconnu → « lien invalide ». Périmé → « ce lien a expiré, contactez l’entreprise ».
- Pas de régénération automatique.
- Relire le dossier : le lien n’est renvoyé que s’il est encore valide.
- **Interdit d’afficher** dans le parcours : nom, email, référence client, nom de l’organisation (jusqu’au branding).

Premier ouvert d’un dossier *créée* → passage en *consentement* (événement une seule fois).

### 8.3 Consentement

Base légale **avant** toute photo de pièce ou de visage. On enregistre la décision, l’horodatage, la **version du texte**.

| Décision applicant | Effet |
|---|---|
| Accepte | En attente de capture. La caméra peut s’ouvrir. |
| Refuse | Session *refusée*, terminale. **Aucune** capture. |

Un second consentement est refusé. Texte placeholder (à remplacer avant go-live) :

> En continuant, vous acceptez que vos pièces d’identité et données biométriques soient traitées pour vérifier votre identité pour le compte de l’entreprise qui vous a envoyé ce lien.

Langue du parcours : français par défaut, anglais si le navigateur le demande. Accessibilité : actions claires, pas de jargon technique en cas d’erreur.

**Fondation :** le parcours s’arrête ici (écran d’attente « capture à venir ») jusqu’au sprint capture.

### 8.4 Capture — document

But : une image **utilisable**. Accès caméra (idéalement capteur arrière), cadrage, capture manuelle en V1 (auto-détection des coins ensuite), **verso** si le type de pièce l’exige.

Qualité **côté appareil**, avant envoi : flou, luminosité, recadrage, résolution, pièce trop petite. Échec → recapture **immédiate**, sans lancer l’analyse.

Le fichier va dans un stockage **privé** (accès temporaire signé). Jamais le binaire dans un message d’API métier. L’applicant n’attend pas l’OCR à cet écran : il enchaîne vers le selfie une fois la pièce acceptée par la qualité (client, puis filet serveur).

### 8.5 Capture — selfie et présence réelle

Même discipline : un visage utilisable, consignes de présence (V1 : **challenge actif**, ex. tourner la tête). Qualité visage insuffisante (flou, trop petit, deux visages, profil extrême) → recapture, pas de liveness sur une image morte.

Après envoi : l’applicant voit un écran d’attente (et éventuellement une redirection vers l’URL configurée par l’organisation). Le client, lui, voit *traitement* puis une décision.

---

## 9. Cycle B — Comprendre le document

Principe : **ne jamais analyser une mauvaise photo**, et **ne jamais faire confiance à une seule source**.

Deux boucles :

- Qualité appareil KO → l’humain recommence (pas de coût d’analyse).
- Qualité serveur KO / pièce coupée → recapture ou refus.
- Type hors corridor → sortie *non supportée* (refus).
- Lectures incohérentes (OCR vs MRZ) → **signal**, on continue souvent.

Les premiers échecs sont **opérationnels** (flou). Les derniers sont **fraude**. On ne les traite pas pareil.

Chaque étape produit un **signal** (succès / échec / avertissement, code, score). Aucune étape ne décide à elle seule *approuvée*.

### 9.1 Qualité serveur

Filet après stockage : type de fichier, taille, dimensions minimales. Image inutilisable → **pas d’OCR**. Recapture ou refus selon le nombre d’essais.

C’est le premier **arrêt rapide** du cycle B.

### 9.2 Détection

Y a-t-il une pièce ? Où sont les coins ? Orientation, perspective. Puis **redressement** (vue de face).

Sans géométrie stable, lecture et portrait (pour le face match) sont mauvais. Pas de pièce / coins incohérents → recapture.

V1 : **modèle de computer vision propriétaire** (coins, présence, orientation), inférence SageMaker. Pas de détecteur de contours / plus grand quad en chemin produit. Échec du modèle → recapture. Dataset annoté et endpoint sont des prérequis du livrable, pas une phase ultérieure.

### 9.3 Classification

**Quel pays, quel type**, éventuellement quelle version — **classifieur propriétaire**. Le corridor V1 (liste courte de classes) borne le dataset, pas un parseur à règles.

Hors classes du modèle (corridor V1) → *unsupported_document* → **refus**. On n’essaie pas de deviner un permis inconnu.

Sans classification, on ne sait pas s’il faut un verso, quel layout de lecture, quelle MRZ. Une erreur cascade : mauvais parseur, faux mismatch, faux positif fraude.

### 9.4 Lecture (zone visuelle)

Lire les champs humains : prénom, nom, date de naissance, numéro de pièce, expiration. Normaliser vers un **schéma unique** (dates, casses), quel que soit le pays.

La lecture visuelle se trompe (O/0, reflets). On ne s’y fie **jamais seule**.

Pièce **expirée** (date < aujourd’hui) : signal de refus fort, même si la pièce est authentique — selon politique d’organisation.

### 9.5 MRZ et contrôle croisé

Seconde source, indépendante de la zone visuelle. Zone ICAO : localiser, lire, reconnaître le layout, **valider les checksums**, extraire, **comparer à la lecture visuelle**.

| Accord | Désaccord |
|---|---|
| Confiance renforcée | Signal de risque (transcription, altération, ou lecture MRZ ratée) |
| Checksum faux | Plus grave qu’un accent mal lu |

Fusion : un enregistrement d’identité **propre** (translittération, dates). C’est le premier **contrôle croisé** du produit.

### 9.6 Authenticité

Question : cette image est-elle celle d’une **vraie pièce physique non altérée** ?

On ne relit plus le nom. On cherche la falsification : cohérence des données (déjà MRZ ↔ visuel), cohérence visuelle, zones retouchées, photo remplacée, recto ≠ verso, capture d’écran, photocopie, impression.

V1 : **modèle d’authenticité / tampering propriétaire**. Les checksums MRZ et dates restent des signaux déterministes **à côté** du modèle.

Échec fort → le risque inclinera vers **refus**. Zone grise → **revue**. On enchaîne souvent vers la biométrie : un faux document + un vrai visage (ou l’inverse) est une information.

### 9.7 Fraude document

Couche au-dessus : « ce dossier **ressemble-t-il** aux fraudes connues ? » (écran, print, morphing…).

V1 : **modèle de fraude documentaire** sur le même horizon que la détection (dataset + SageMaker), pas un palier « règles seules en attendant ».

Fin du cycle B : le document est compris. **Rien n’est encore approuvé** : on n’a pas prouvé que **la personne** est là.

---

## 10. Cycle C — Prouver la personne, puis trancher

Question : **un humain vivant, titulaire de la pièce, est-il devant la caméra maintenant ?**

| Étape | Question | Si on saute |
|---|---|---|
| Qualité visage | Un seul visage utilisable ? | Selfie flou, deux têtes |
| Présence réelle | Humain **présent**, pas une attaque ? | Photo papier, écran, rejeu |
| Face match | **La même** personne que sur la pièce ? | Pièce volée + complice |

Un liveness parfait + visages différents = usurpation. Un match parfait + liveness nul = attaque de présentation. Il faut **les deux**.

Ceci est la **biométrie d’onboarding**, interne à l’IDV — pas le produit Authentification.

### 10.1 Qualité visage

Un visage, **un seul** : taille, pose, lumière, netteté, pas d’occlusion majeure. Plusieurs visages → essai refusé (quel visage comparer ?). Trop petit / profil extrême → recapture.

Arrêt rapide biométrique, analogue à la qualité document.

### 10.2 Présence réelle

Détecter une **attaque de présentation** : papier, téléphone, écran, rejeu, plus tard injection / deepfake.

**V1 = actif (challenge) :** un geste tiré au sort, vérification qu’il a eu lieu. Une photo figée ne tourne pas la tête à la demande.

**Plus tard = passif :** meilleure expérience, plus dur à fiabiliser.

Présence faible en V1 → plutôt **revue** (faux rejets fréquents), pas toujours refus. Photo imprimée / écran figé doit échouer ou partir en revue.

Chaque tentative est journalisée (coût + audit).

### 10.3 Comparaison des visages (onboarding)

Portrait recadré de la pièce ↔ selfie. Score de similarité. Seuil **calibré** (faux acceptés vs faux rejets), versionné par politique d’organisation — pas choisi « au feeling ».

Sous le seuil → revue ou refus selon la règle. Au-dessus → signal vert pour le risque.

Dernière brique métier avant l’agrégation.

### 10.4 Moteur de risque

Les analyses **proposent des nombres**. Elles ne disent pas « cette personne a le droit d’ouvrir un compte ». Ça, c’est la **politique de l’organisation**, en règles versionnées.

Entrées V1 : qualité et authenticité document, expiration, accord MRZ / lecture, présence réelle, face match — plus tard appareil, comportement, AML attaché.

Sortie : un **niveau de risque** (ex. bas / moyen / élevé) et un score, **sans données personnelles** dans les raisons.

Configurable **par organisation**.

### 10.5 Moteur de décision

Agrège risque + règles métier. Toute décision a des **codes de raisons** stables, en anglais côté intégration, **sans PII**.

Un modèle de langage **ne juge pas**. Il pourra plus tard résumer un dossier pour l’analyste ; la décision reste des règles et des scores mesurables.

| Décision | Quand (politique V1, à calibrer) |
|---|---|
| **Approuvée** | Pièce supportée et non expirée (politique), authenticité OK, présence réelle OK, visages concordants, pas de hit AML bloquant si AML attaché |
| **Refusée** | Consentement refusé ; pièce non supportée ; expiration hors politique ; fraude / altération forte ; usurpation (mismatch franc) ; hit AML confirmé si politique « bloquer » ; plafond d’essais atteint |
| **Revue** | Zone grise (présence réelle limite, similarité limite, mismatch MRZ partiel, pièce rare, hit AML *possible*) |
| **Recapture demandée** | Qualité image, document coupé, verso manquant, visage inutilisable, challenge de présence incomplet |

Le client est notifié (cible). Il relit le dossier s’il a besoin des champs extraits. Les **médias** ne voyagent pas dans la notification : accès temporaire signé, jamais d’URL publique permanente.

---

## 11. Revue manuelle

Statut *revue* : un **analyste de la même organisation** voit :

- les images (accès temporaire) ;
- l’identité extraite ;
- les signaux et scores ;
- les raisons proposées par la machine.

Il **approuve** ou **refuse**. L’action est journalisée (qui, quand, décision). Il ne voit **aucun** dossier d’un autre tenant.

La revue n’est pas une quatrième « question d’identité » : c’est un **humain dans la boucle** quand la machine est dans la zone grise.

---

## 12. Identité extraite et personne

Après une lecture réussie, la session porte une identité **normalisée** (nom, prénom, naissance, numéro, expiration, pays, type de pièce).

- Si une **référence client** était fournie, on rattache (ou on crée) la **personne** chez ce tenant.
- Si l’organisation active l’**enrôlement** et que la décision est *approuvée*, le selfie de confiance devient le template — pour une **autre** solution de la gamme, pas pour rejouer l’IDV.

L’indicé à la création (prénom saisi par le client) peut diverger de la pièce : signal éventuel, la pièce prime pour la preuve d’identité.

---

## 13. Composition avec le reste de la gamme

```
Personne (référence client)
    │
    ├── IDV  ──enrôle──►  Authentification (autre produit)
    └── IDV  ──attaché──►  AML (autre produit)
```

| Si AML attaché | Comportement IDV |
|---|---|
| Politique « ignorer » | L’IDV décide sans attendre le criblage |
| « Revue » | Hit possible → *revue* |
| « Bloquer » | Hit confirmé → *refusée* |

Le criblage part **après** lecture de la pièce, pas sur le seul nom fourni à la création (repli si la lecture échoue → plutôt revue). Le résultat AML reste consultable **à part**.

Interdit : ouvrir un IDV **uniquement** pour cribler un nom (utiliser l’AML seul).

---

## 14. Parcours applicant (écrans)

Mobile d’abord.

| État | Écran |
|---|---|
| Chargement | Attente |
| Consentement | Texte versionné, J’accepte / Je refuse |
| Capture pièce | Viseur, consignes, recapture, verso si besoin |
| Capture visage | Consignes de présence, recapture |
| Attente | Traitement en cours |
| Fin succès / refus de consentement | Message court, redirection optionnelle |
| Expiré | « Ce lien a expiré, contactez l’entreprise » |
| Invalide | « Lien invalide » |
| Erreur | Réessayer, sans jargon |

---

## 15. Console (organisation)

Langue : français.

| Écran | Contenu IDV |
|---|---|
| Nouvelle vérification | Référence client et identité optionnelles, copie du lien, expiration |
| Fiche dossier | Statut, dates, personne, lien (ou « expiré »), puis extraits, signaux, décision, raisons |
| Liste | Filtre statut / référence |
| File revue | Dossiers à trancher |

**Livré :** nouvelle vérification + fiche (statut fondation, pas encore d’images ni de décision). Liste paginée et bouton annuler : à faire.

Badges fondation : Créée, Consentement, En attente de capture, Refusée, Expirée, Annulée. Ensuite : Document, Selfie, Traitement, Revue, Approuvée, Recapture.

---

## 16. Cas d’utilisation

**UC-IDV-01 — Créer une vérification**  
Précondition : clé valide, produit IDV activé. Le client crée un dossier ; un lien est émis. Alternatives : non authentifié ; référence déjà prise ; rejeu idempotent (même dossier) ; idempotence + autre contenu ; lien impossible à émettre (dépendance) → échec explicite, pas un dossier « sans lien » silencieux côté client.

**UC-IDV-02 — Ouvrir le lien et consentir**  
L’applicant ouvre le lien. Accepte → attente de capture. Refuse → refusée, pas de média. Lien mort ou faux → écrans dédiés. Second consentement → refus.

**UC-IDV-03 — Capturer la pièce**  
Qualité appareil puis serveur. Recapture in-flow. Verso si exigé. Plafond d’essais → refus ou revue selon politique.

**UC-IDV-04 — Capturer le visage**  
Un visage, présence réelle, puis analyse. Recapture si qualité / challenge insuffisant.

**UC-IDV-05 — Décision automatique**  
Les cycles B et C s’enchaînent. Décision persistée avec raisons. Notification (cible). Dossier relisible par l’organisation uniquement.

**UC-IDV-06 — Revue**  
Analyste de l’org tranche. Audit. Pas d’accès cross-tenant.

**UC-IDV-07 — Isolation**  
Org B sur l’id de A → introuvable, identique à un id au hasard. Le lien de A ne révèle pas B. Liste de A sans B.

**UC-IDV-08 — Annuler**  
L’organisation annule tant que c’est autorisé. Lien révoqué. Déjà terminale ou trop avancée → conflit de statut.

**UC-IDV-09 — Expiration**  
TTL du lien avant fin du contrat → *expirée*. L’organisation crée une nouvelle session (ou, plus tard, renouvelle le lien explicitement).

**UC-IDV-10 — Pièce non supportée**  
Classification hors corridor → refus avec raison stable, sans tenter un parseur fantaisiste.

---

## 17. Règles de gestion IDV

Les règles plateforme (clé, isolation 404, PII, idempotence, lien) s’appliquent. Ci-dessous le spécifique IDV.

| ID | Règle |
|---|---|
| **RG-IDV-01** | Les trois questions s’enchaînent : pas de décision *approuvée* sans document acceptable **et** biométrie d’onboarding (sauf politique documentaire explicite hors V1). |
| **RG-IDV-02** | Pas de média sans consentement accepté. |
| **RG-IDV-03** | Recapture = même session, compteur d’essais, plafond (défaut 3). |
| **RG-IDV-04** | Qualité KO → pas d’OCR / pas de liveness sur image morte. |
| **RG-IDV-05** | Hors corridor pays × type → refus *non supporté*. |
| **RG-IDV-06** | La décision a des raisons persistées, sans PII. Pas de juge LLM. |
| **RG-IDV-07** | Face match IDV = selfie ↔ **pièce**, jamais ↔ template d’un autre tenant, jamais recherche 1:N. |
| **RG-IDV-08** | Enrôlement seulement si session *approuvée* et option d’organisation active. |
| **RG-IDV-09** | AML attaché n’est pas une décision IDV ; il peut seulement **contraindre** revue ou refus selon politique. |
| **RG-IDV-10** | L’analyste ne voit que les dossiers de son organisation. |
| **RG-IDV-11** | Médias : stockage privé, accès court, jamais d’URL permanente. |
| **RG-IDV-12** | Identité extraite = schéma normalisé ; la pièce prime sur les champs saisis à la création. |

---

## 18. Options d’organisation

| Option | Rôle | V1 |
|---|---|---|
| Corridor pays × types | Classes du modèle V1 | Obligatoire, liste courte |
| Présence réelle | Actif puis passif | Actif |
| Essais max de capture | Recaptures | 3 |
| Seuils face match / présence / authenticité | Calibrage | Versionnés, provisoires |
| Politique expiration pièce | Refus vs revue | À figer |
| AML attaché + politique on-match | Ignorer / revue / bloquer | Après produit AML |
| Enrôlement à l’approbation | Nourrit l’auth | Après décision IDV |
| URL de retour | Après le parcours | Optionnelle |
| Langue forcée du flow | Sinon navigateur | FR défaut |

---

## 19. Exigences non fonctionnelles (IDV)

| ID | Exigence |
|---|---|
| **NF-IDV-01** | Isolation : fuite de dossier ou de média = arrêt livrable. |
| **NF-IDV-02** | Lien fort ; HTTPS hors local. |
| **NF-IDV-03** | Happy path lien → capture → décision : **minutes**, pas des heures. |
| **NF-IDV-04** | Médias chiffrés au repos avant mise en production de la décision. |
| **NF-IDV-05** | Journaux : identifiants techniques seulement. |
| **NF-IDV-06** | Notifications (quand livrées) : au moins une fois ; le client ignore les doublons. |

---

## 20. Phasage

L’ordre de build détaillé (sprints, modèles, kill criteria) est dans [`roadmap-implementation-idv.md`](./roadmap-implementation-idv.md). Comment construire : [`guide-implementation-s1.md`](./guide-implementation-s1.md), [`guide-implementation-s2.md`](./guide-implementation-s2.md).

| Phase | Contenu | Statut |
|---|---|---|
| Fondation | Session, isolation, lien, consentement, audit | **Livré** |
| Capture | Caméra pièce, qualité, stockage | à venir |
| Dataset | Corpus corridor V1, annotation | à venir (bloquant analyse) |
| Document | Modèles proprio (détection, classification, authenticité), lecture, MRZ | à venir |
| Décision | Présence réelle, face match, risque, revue | à venir |
| Intégration | Notifications, bac à sable, usage | à venir |

Les autres solutions de la gamme (authentification, AML) **attendent** une décision IDV automatique démontrable.

**Arrêt :** fuite tenant, PII ou token dans les logs, lien traité comme optionnel, décision sans raisons, heuristiques de coins en prod.

---

## 21. Décisions figées (IDV)

| Sujet | Décision |
|---|---|
| Nom | Identity & Document Verification ; session aussi appelée vérification |
| Parcours V1 | Lien hébergé d’abord, SDK ensuite |
| Biométrie dans l’IDV | Onboarding (pièce + présence + match), distincte de l’authentification |
| Corridor V1 | Liste courte pays × types ; le reste = non supporté |
| Liveness V1 | Actif (challenge) |
| Vision documentaire V1 | Modèles proprio (détection, classification, authenticité) ; pas d’heuristiques de coins en prod |
| Juge | Règles + scores persistés |
| Recapture | Même session |
| Régénération auto du lien | Non |
| Cross-tenant | Introuvable, jamais interdit |

---

## Annexe — Signaux (codes métier, V1)

Liste indicative, à stabiliser à l’implémentation. Langue d’intégration : anglais.

**Document :** `blur_detected`, `image_unusable`, `unsupported_document`, `document_expired`, `mrz_checksum_invalid`, `mrz_ocr_mismatch`, `document_authentic`, `tampering_suspected`.

**Personne :** `face_not_detected`, `multiple_faces`, `liveness_pass`, `liveness_fail`, `face_match_pass`, `face_match_fail`.

**Décision :** `approved`, `declined`, `review`, `resubmission_requested` + les codes ci-dessus dans les raisons.
