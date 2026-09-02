# Coût unitaire d’une vérification

**Produit :** Recogniz-Me  
**Date :** 28 août 2026  
**Statut :** estimation COGS (pas un devis AWS)  
**Périmètre :** coût **machine** d’un dossier KYC, **sans revue humaine**  
**Documents liés :**
- [`cahier-des-charges.md`](./cahier-des-charges.md) — vision produit et stack AWS AI
- [`specs/specification-fonctionnelle-idv.md`](./specs/specification-fonctionnelle-idv.md) — produit Identity & Document Verification

Hypothèse de ce document : décision 100 % automatique (`APPROVED` / `REJECTED`). Aucun analyste. Les montants sont en **USD**, tarifs publics AWS (Oregon / Virginie ; Irlande et Canada du même ordre).

---

## 1. Chiffre à retenir

Sans humain, vise **~0,06 $** de COGS machine en V1, et **2–5 centimes** une fois les modèles propriétaires en prod à fort volume.

| Horizon | Coût variable AWS (IA + stockage) | Total chargé (IA + infra amortie) |
|---|---|---|
| **Maintenant** (Sprint 1 : création + consentement) | ~0,00 $ | négligeable |
| **V1** (Textract + Rekognition, volume faible/moyen) | **0,04 – 0,08 $** | **0,10 – 0,55 $** selon volume |
| **À terme** (modèles propriétaires, fort volume) | **0,01 – 0,03 $** | **0,02 – 0,05 $** |

Le marché revend une vérif ID + selfie autour de **0,80 – 4 $**. Les COGS machine restent largement en dessous.

Le sandbox (fixtures, sans appel AWS) reste à **0 $** d’IA.

---

## 2. Par dossier, automatique

| Volume / mois | IA AWS (Textract + Liveness + match) | Infra (RDS, Redis, ECS…) | **Total** |
|---|---|---|---|
| **Aujourd’hui** (Sprint 1, pas d’IA) | 0 $ | négligeable | **~0 $** |
| **1 000** (V1, petit staging) | 0,04 – 0,08 $ | 0,20 – 0,50 $ | **~0,25 – 0,55 $** |
| **10 000** | 0,04 – 0,07 $ | 0,04 – 0,10 $ | **~0,10 – 0,17 $** |
| **100 000** (AnalyzeID à 0,01 $/page) | 0,03 – 0,045 $ | 0,01 – 0,03 $ | **~0,04 – 0,08 $** |
| **À terme** (modèles à nous, gros volume) | 0,01 – 0,03 $ | 0,01 – 0,02 $ | **~0,02 – 0,05 $** |

Au-delà de quelques milliers de dossiers/mois, **l’infra devient négligeable**. Le coût unitaire se rapproche du variable AWS.

---

## 3. Happy path V1 (1 tentative, décision auto)

| Mix document | Coût IA |
|---|---|
| Passeport (1 page) + 1 liveness + face match | **~0,041 $** |
| CNI recto/verso (2 pages) + 1 liveness + face match | **~0,066 $** |
| Mix 50/50, 1,3 session liveness (retries machine) | **~0,06 $** |

Détail passeport : AnalyzeID 0,025 $ + Face Liveness 0,015 $ + CompareFaces 0,001 $ + S3/KMS ~0,001 $ à l’écriture. La rétention 5–7 ans ajoute ~0,01–0,02 $ **étalés**, pas au moment du check.

---

## 4. Détail des appels AWS (V1)

Hypothèse : 1 pièce (passeport **ou** CNI recto/verso) + 1 session liveness + 1 face match.

| Étape | Service | Prix unitaire | Par vérif typique |
|---|---|---|---|
| OCR identité | Textract **AnalyzeID** | **0,025 $ / page** (0,01 $ après 100k pages/mois) | 0,025 $ (passeport) ou **0,050 $** (CNI 2 faces) |
| Présence réelle | Rekognition **Face Liveness** | **0,015 $ / session** (échec **facturé** aussi) | ~0,015 – 0,030 $ (1 à 2 tentatives) |
| Même personne | Rekognition **CompareFaces** | **0,001 $** (seule l’image source compte) | 0,001 $ |
| Qualité visage (si appel séparé) | **DetectFaces** | **0,001 $** | ~0,001 $ |
| Médias | S3 + KMS | ~0,023 $/Go/mois | ~0,01 – 0,02 $ sur 5–7 ans de rétention |

**Happy path** (passeport, 1 liveness, 0 retry) : **~0,04 $**.  
**CNI + 1 retry liveness** : **~0,08 $**.  
**Pire cas UX** (3 tentatives liveness, plafond roadmap) : **~0,10 – 0,12 $**.

CompareFaces et DetectFaces sont négligeables à côté de AnalyzeID et Face Liveness.

---

## 5. Infra amortie

Postes fixes typiques d’un petit staging / prod : RDS PostgreSQL, ElastiCache Redis, ECS Fargate (API + workers), ALB, CloudFront, Secrets Manager, CloudWatch — **200 – 600 $/mois**.

| Volume | Infra / vérif |
|---|---|
| 1 000 / mois | 0,20 – 0,60 $ |
| 10 000 / mois | 0,05 – 0,10 $ |
| 100 000 / mois | quelques centimes |

Un endpoint SageMaker GPU 24/7 n’entre **pas** dans la V1. Règle roadmap : il n’existe en prod que si le coût/vérif bat Textract/Rekognition **et** que les métriques (FAR/FRR, APCER/BPCER) sont meilleures.

---

## 6. Exemple à 10 000 vérifs / mois

Mix 50 % passeports / 50 % CNI, ~1,3 session liveness, **0 % de revue**.

| Poste | Estimation |
|---|---|
| Textract AnalyzeID | ~375 $ |
| Face Liveness | ~195 $ |
| CompareFaces + DetectFaces | ~20 $ |
| S3 / KMS | ~20–50 $ |
| **IA + stockage** | **~0,06 $ / vérif** |
| Infra amortie | ~0,05 – 0,10 $ |
| **Total chargé** | **~0,10 – 0,17 $** |

À **100 000+/mois**, AnalyzeID passe à 0,01 $/page : le variable AWS peut tomber vers **0,03 – 0,04 $**.

---

## 7. Phase 2 — modèles propriétaires

Les deux postes à remplacer en priorité :

1. **Face Liveness (~0,015 $)** — le plus cher, facturé à chaque tentative.
2. **AnalyzeID (~0,025 $/page)** — surtout sur les CNI (2 pages).

À fort volume, l’inférence maison peut descendre vers **1 – 3 centimes** de compute par dossier. En dessous de quelques milliers de vérifs/mois, **rester sur AWS managé est moins cher** qu’un endpoint GPU allumé 24/7.

Bedrock (résumé de dossier, jamais décideur) n’est pas dans le coût de base. S’il est activé plus tard : quelques centimes, seulement sur les dossiers résumés.

AML / PEP / sanctions (Phase 3) : souvent **0,10 – 1,50 $** en plus via un bureau tiers — hors pipeline actuel.

---

## 8. Ce qui ferait exploser le coût (même sans humain)

- Recaptures **après** OCR (qualité client trop faible) → Textract payé plusieurs fois.
- Liveness spam / bots : chaque `CreateFaceLivenessSession` est facturée, succès ou non.
- SageMaker trop tôt, à bas volume.

Garde-fous déjà prévus dans la roadmap et le tutoriel : filtre qualité **avant** Textract, plafond d’attempts, journalisation du coût AWS par tentative.

---

## 9. Annexe — avec revue humaine (hors périmètre)

La roadmap identifie la revue comme **coût n°1** si elle est activée. Chiffres pour comparaison uniquement.

- Analyste ~3–8 min/dossier, ~25–50 $/h chargé → **2 – 5 $ par revue**.
- Si **10 %** des dossiers partent en `MANUAL_REVIEW` → **+0,20 – 0,50 $** en moyenne par vérif.
- Si **20 %** → la revue dépasse toute la facture AWS.

À 10k vérifs/mois, le total chargé passerait d’environ **0,10 – 0,17 $** (sans humain) à **0,35 – 0,55 $** (avec 10 % de revue).

---

## 10. Sources

- [Amazon Textract pricing](https://aws.amazon.com/textract/pricing/) — AnalyzeID : 0,025 $/page (100k premières), 0,01 $ ensuite
- [Amazon Rekognition pricing](https://aws.amazon.com/rekognition/pricing/) — Face Liveness : 0,015 $/session (500k premières) ; CompareFaces / DetectFaces : 0,001 $/image (1M premières)
- [Rekognition FAQs](https://aws.amazon.com/rekognition/faqs/) — CompareFaces : seule l’image source est facturée ; liveness facturé succès **et** échec
- Marché IDV 2026 (indicatif) : Veriff / Sumsub ~0,80 – 1,85 $ ; Onfido / Jumio ~2 – 5 $ ; Stripe Identity 1,50 $
