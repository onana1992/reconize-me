# Roadmap d’implémentation — Identity & Document Verification

**Plateforme :** Recogniz-Me  
**Produit :** Identity & Document Verification (IDV)  
**Version du document :** 1.0  
**Date :** 31 août 2026  
**Statut :** ordre de build (Phase 1 MVP)  
**Documents liés :**
- [`specification-fonctionnelle-idv.md`](./specification-fonctionnelle-idv.md) — spec métier
- [`../cahier-des-charges.md`](../cahier-des-charges.md) — vision et stack IA
- [`guide-implementation-s1.md`](./guide-implementation-s1.md) — S1 livré
- [`guide-implementation-s2.md`](./guide-implementation-s2.md) — S2 à construire

**Cycle en cours :** l’ordre de build immédiat est la [roadmap MVP SaaS](../MVP/roadmap-implementation-mvp.md) (M0–M6, AWS / stubs). Le présent document reste l’ordre **vision** (SageMaker). S3–S9 ne démarrent **pas** avant la fin de M6.

Ce document dit **quand** on construit l’IDV. Le *quoi* reste dans la spec fonctionnelle.

---

## 1. Cible

**Happy path :** l’organisation crée une session → l’applicant consent et capture → modèles documentaires + biométrie d’onboarding → **décision justifiée** (approuvée / refusée / revue), relisible en console.

---

## 2. Principes d’ordre

1. **Plateforme avant inférence** — session, isolation, lien, médias, avant SageMaker.
2. **Dataset avant détection** — pas de détecteur de contours « en attendant » le modèle.
3. **Vision proprio + lecture hybride** — détection / classification / authenticité = nos endpoints ; OCR Textract possible en complément ; MRZ = code déterministe.
4. **Décision = règles** — jamais un LLM juge.
5. **Un livrable démontrable par sprint.** Si le livrable n’est pas là, on ne démarre pas le scope suivant.

Les autres solutions de la gamme (authentification, AML) **attendent** une décision IDV automatique démontrable.

**Arrêt produit (kill) :** fuite tenant, PII ou token dans les logs, lien traité comme optionnel, décision sans raisons, pipeline document lancé **sans** modèle de détection (heuristiques de coins en prod).

---

## 3. Vue d’ensemble

| Phase | Contenu | Statut |
|---|---|---|
| Fondation | Session, isolation, lien, consentement, audit | **Livré** (Sprint 1) |
| Capture | Caméra, qualité client (`@kyc/capture-sdk`), qualité serveur, stockage | à venir |
| Dataset | Corpus corridor V1, annotation coins / type / fraude | à venir (bloquant analyse) |
| Document | Inférence SageMaker détection + classification ; lecture + MRZ ; authenticité / fraude modèles | à venir |
| Décision | Présence réelle, face match, risque, revue console | à venir |
| Intégration | Notifications, bac à sable, usage, durcissement, staging | à venir |

```
S0 fondation repo → S1 session + consentement (fait)
  → S2 capture + S3 + amorçage dataset
  → S3 détection + classification (SageMaker) + lecture + MRZ
  → S4 authenticité/fraude (modèle) + liveness
  → S5 face match + risk + décision
  → S6 console revue
  → S7 webhooks / sandbox
  → S8 sécu / rétention
  → S9 staging / design partners
```

---

## 4. Sprints (Phase 1)

Durée indicative : 2 semaines par sprint. Les numéros S0–S9 suivent le cahier des charges.

| Sprint | Livrable démontrable | Détail |
|---|---|---|
| **S0** | Stack locale | Repo, Docker, CI, org / clés. **Fait** (socle). |
| **S1** | Contrat jusqu’au consentement | Création session, isolation, lien, consentement, audit. **Livré.** Guide : [`guide-implementation-s1.md`](./guide-implementation-s1.md). |
| **S2** | Capture bout en bout | Flow : caméra pièce, qualité client, upload URL signée, qualité serveur (MIME / taille / dimensions), recapture, statuts *document*. **Amorçage dataset** (captures légitimes + protocoles d’annotation coins / type / fraude). Pas d’OCR. Guide : [`guide-implementation-s2.md`](./guide-implementation-s2.md). |
| **S3** | Document « lu » | Endpoints SageMaker **détection** (coins → redressement) et **classification** (pays × type du corridor). Hors classes → *unsupported_document*. Lecture (Textract et/ou OCR) + **MRZ** déterministe + compare lecture/MRZ. **Interdit :** Canny / plus grand quad en chemin prod. |
| **S4** | Pièce jugée + présence | Modèle **authenticité / fraude**. Liveness **actif** (challenge) ; Rekognition en complément si le modèle liveness n’est pas prêt. Qualité visage, recapture. |
| **S5** | Décision automatique | Face match selfie ↔ portrait pièce (Rekognition en complément). Risk + Decision **règles** versionnées, raisons persistées. Happy path sandbox → approuvée / refusée / revue **sans** bricolage. |
| **S6** | Opération | Console : liste, fiche (signaux, extraits, médias signés), **file revue**, décision analyste + audit. |
| **S7** | Intégration client | Webhooks décision (signature, retry), sandbox fixtures, usage. Guide d’intégration. |
| **S8** | Données | KMS médias, rétention / purge, 2FA console, revue des logs (zéro PII), rate limit. |
| **S9** | Go-live restreint | Staging AWS, 1–2 design partners, corridor documents **figé**. |

---

## 5. Modèles — calendrier

| Modèle propriétaire | Sprint cible | Prérequis |
|---|---|---|
| Détection (présence, coins, orientation) | S3 | Dataset annoté dès S2 |
| Classification (pays, type) | S3 | Mêmes images + labels classes |
| Authenticité / tampering | S4 | Labels `tampering_region` / `fraud_type` |
| Fraude document (écran, print, …) | S4 | Peut fusionner avec authenticité (multi-têtes) |
| OCR proprio | Après S9 si encore Textract | Corpus lecture |
| Liveness / embeddings proprio | S4–S5 en complément Rekognition ; remplacement quand les métriques gagnent | Corpus selfie / vidéo |

Pas un modèle : qualité client (SDK), qualité serveur, parseur MRZ, moteur de décision (règles).

---

## 6. Décisions à figer avant S3

Sans ça, le classifieur n’a pas de classes.

| Décision | Proposition V1 |
|---|---|
| Pays × types | 2–4 pays, passeport + carte d’identité |
| Liveness | Challenge actif ; Rekognition Face Liveness **ou** modèle proprio si déjà au niveau |
| Région AWS (SageMaker + S3) | À choisir (ex. `eu-west-1` / `ca-central-1`) |
| Seuils match / liveness / authenticité | Versionnés, calibrés sur le dataset, pas « au feeling » |

---

## 7. Done / hors IDV V1

**IDV V1 est livré** quand : un design partner crée une session, l’applicant va au bout du flow, une décision automatique arrive avec raisons, l’analyste peut trancher une revue, aucune fuite tenant, détection = **inférence modèle**.

**Hors ce produit / ce go-live :** authentification sans pièce, AML, KYB, liveness passif, SDK natif, branding tenant, régénération auto du lien, OCR 100 % proprio si Textract suffit encore, élargissement du corridor.

---

## 8. Après le go-live IDV (Phase 2–3)

- Réentraînement, plus de classes, A/B, monitoring dérive.
- OCR et biométrie 100 % proprio quand les métriques le justifient.
- Enrôlement à l’*approuvée* → débloque le produit Authentification.
- AML attaché → autre spec / autre phase.
