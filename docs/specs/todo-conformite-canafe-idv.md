# À faire — Vérification d’identité conforme au CANAFE

**Plateforme :** Recogniz-Me  
**Produit :** Identity & Document Verification (IDV)  
**Version du document :** 1.0  
**Date :** 13 novembre 2026  
**Statut :** liste de travaux — la méthode « pièce avec photo, personne non présente » n’est pas tenue

**Documents liés :**

- [`specification-fonctionnelle-idv.md`](./specification-fonctionnelle-idv.md) — parcours, décision, revue
- [`specification-regles-idv.md`](./specification-regles-idv.md) — politique d’organisation et planchers
- [`../DocumentIA/cahier-des-charges-document-ia.md`](../DocumentIA/cahier-des-charges-document-ia.md) — lecture `vision-1`, hors authenticité
- [`../Biometrie/roadmap-implementation-biometrie.md`](../Biometrie/roadmap-implementation-biometrie.md) — liveness et face match après un document approuvé

Ce document fixe les travaux pour qu’une session `APPROVED` vaille la méthode d’identification du CANAFE (FINTRAC) par pièce d’identité avec photo, lorsque la personne n’est pas physiquement présente. Il ne couvre pas le reste du KYC : adresse, profession, tiers, bénéficiaires effectifs, personnes politiquement vulnérables, surveillance continue.

Le CANAFE ne certifie aucun outil. L’entité déclarante reste responsable. Une approbation produit n’est pas, à elle seule, le dossier qu’elle doit pouvoir montrer.

Pour cette méthode, trois preuves doivent être réunies : la pièce est authentique, elle est en vigueur, le nom et la photo sont ceux de la personne. Aujourd’hui une approbation peut sortir sans les trois.

---

## 1. Figer le contrat d’approbation

Une session ne passe à `APPROVED` que si les quatre conditions sont vraies en même temps :

- pièce du corridor gouvernemental, avec photo et numéro unique ;
- authenticité évaluée et acceptée ;
- date d’expiration postérieure au jour de la vérification ;
- présence réelle et correspondance du visage acceptées.

`Verification.applyReview` refuse une approbation humaine si l’une de ces conditions manque. La revue peut confirmer une zone grise de similarité faciale. Elle ne remplace pas une preuve absente.

Tests de verrouillage : pièce expirée, indicateur suspect, échec MRZ, liveness absent, face match absent. Aucun de ces cas ne finit approuvé, ni par le moteur ni par un analyste.

## 2. Évaluer l’authenticité de la pièce

Le pipeline `vision-1` lit et signale un doute. Il ne certifie pas qu’une pièce est vraie. Il faut un contrôle d’authenticité distinct, dont l’échec est un refus, pas une revue.

- Détecter une reproduction : écran, photocopie, photo d’une photo.
- Passeport TD3 : un échec de chiffre de contrôle MRZ ou un écart visuel / MRZ est un refus. Une MRZ cohérente ne déclare pas la pièce authentique.
- Permis du Québec et carte de résident permanent, sans MRZ ICAO : contrôler les éléments de sécurité propres à la pièce (code à barres, mise en page attendue), pas seulement l’OCR.
- `possible_alteration`, `suspicious_text`, `suspicious_layout` et `unexpected_document_structure` interdisent l’approbation.
- Ne jamais déduire « authentique » d’une bande de confiance OCR haute.

La version du contrôle d’authenticité est enregistrée sur la session, au même titre que `vision-1` et `bio-1`.

## 3. Rendre l’expiration non négociable

Retirer le choix `expired_outcome = REVIEW` de la politique. Une date expirée, illisible ou absente est un refus `document_expired`. Le plancher de `IdvPolicyService` rejette toute publication qui réouvre cette voie.

## 4. Lier le visage avant toute approbation

La présence réelle ne démarre aujourd’hui que si le document est déjà `APPROVED`. Un dossier en revue peut donc être approuvé sans selfie.

Dès que la pièce est lisible et du bon type, exécuter le liveness puis la comparaison au portrait, y compris si l’authenticité ou un champ envoie le dossier en revue. `applyReview(APPROVED)` exige `liveness_pass` et une similarité au moins égale au seuil de refus (0,75). Entre 0,75 et 0,90, l’analyste peut confirmer. Le dossier garde qui a confirmé, et quand.

## 5. Livrer le dossier que l’entité déclarante doit conserver

Pour cette méthode, le dossier attendu contient le nom, la date de la vérification, le type de pièce, le numéro unique, l’autorité et le pays de délivrance, et la date d’expiration. Ces champs sont déjà extraits. Il reste à les rendre durables :

- date et heure de la vérification, méthode utilisée, versions `vision`, authenticité et `bio` ;
- refus d’approuver si le numéro, le type ou l’autorité de délivrance manquent ;
- conservation de ce dossier au moins cinq ans, y compris après suppression de l’organisation, ou remise d’un export que le client conserve lui-même. La suppression de compte ne doit pas être le seul sort de ces preuves.

## 6. Limiter le corridor aux pièces admissibles

Chaque schéma du catalogue déclare qu’il est délivré par un gouvernement, qu’il porte une photo et un numéro unique. Sans ces trois attributs, le schéma ne peut pas être activé pour une politique. Les cartes d’assurance maladie restent exclues. Une pièce hors corridor reste un refus, jamais une revue approuvable.

## 7. Empêcher la politique d’assouplir ces règles

Les planchers actuels restent : liveness 0,80, similarité 0,75 / 0,90. Y ajouter : expiration toujours refusée, indicateur suspect jamais approuvable, approbation interdite sans authenticité et sans visage. Une publication qui franchit ces planchers répond `422`, comme aujourd’hui pour un seuil trop bas.
