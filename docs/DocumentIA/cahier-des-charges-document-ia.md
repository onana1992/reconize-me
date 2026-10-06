# Cahier des charges — Document IA (vision multimodale)

**Produit :** Recogniz-Me, Identity & Document Verification  
**Date :** 4 octobre 2026  
**Statut :** spécification d’implémentation  
**Documents liés :**

- [`../MVP/cahier-des-charges-mvp.md`](../MVP/cahier-des-charges-mvp.md) — contrat public, sandbox, qualité avant analyse
- [`../MVP/specification-m5-aws-live-webhook.md`](../MVP/specification-m5-aws-live-webhook.md) — chemin live actuel (Textract AnalyzeID)
- [`../specs/roadmap-implementation-idv.md`](../specs/roadmap-implementation-idv.md) — décision par règles, MRZ déterministe

---

## 1. Objet

Remplacer la lecture live Amazon Textract AnalyzeID par un LLM multimodal à vision, sans dépendre d’un moteur OCR classique.

Le LLM est le moteur de compréhension du document. Il reçoit l’image et produit, dans un JSON strict :

- détection du document et de la qualité ;
- classification (pays, type, version, recto/verso) ;
- texte brut, texte par zone, champs, confiances ;
- lecture des lignes MRZ ;
- indicateurs de doute.

Le serveur recalcule ensuite tout ce qui est calculable : normalisation, parse MRZ, check digits, validations, scores et décision.

Le produit doit accueillir progressivement plusieurs pays et plusieurs types de documents. Ajouter un document est une donnée du registre, pas une nouvelle classe Java.

## 2. Périmètre

**Dans ce chantier**

- Port vision, parseur de JSON, registre de schémas, normaliseurs, moteur MRZ, validations L1–L6, scores, projection vers la décision existante.
- Persistance de l’analyse pour une revue humaine ultérieure.
- Premier document de bascule : permis de conduire du Québec (`QUEBEC_DRIVER_LICENSE`), aujourd’hui seul cas de `QcAnalyzeIdMapper`.
- Catalogue initial prévu : `CAMEROON_CNI`, `CAMEROON_PASSPORT`, `CAMEROON_DRIVER_LICENSE`, `FRANCE_CNI`, `FRANCE_PASSPORT`, `CANADA_PASSPORT`, `QUEBEC_DRIVER_LICENSE`, plus `UNKNOWN`.

**Hors de ce chantier**

- Biométrie (liveness, face match Rekognition) : inchangée.
- Écran de revue opérateur : les données sont livrées, l’interface vient après.
- Boîtes englobantes précises au pixel.
- Preuve d’authenticité d’un document. Le LLM ne certifie pas qu’une pièce est vraie ou fausse.
- Bac à sable : `StubDocumentAi` reste le chemin des clés `ky_test_`. Zéro appel au fournisseur vision, comme aujourd’hui zéro appel Textract.

## 3. Ancrage dans le code actuel

| Élément | Rôle aujourd’hui | Devenir |
|---|---|---|
| `DocumentAiPort.analyze` | Retourne un `DocumentSignals` plat | Conservé. L’adaptateur live projette l’analyse riche vers ce record. |
| `AwsDocumentAi` + `TextractAnalyzeIdClient` | AnalyzeID | Retiré du chemin live une fois `vision-1` accepté. |
| `QcAnalyzeIdMapper` | Permis QC uniquement, `provider = textract_analyze_id` | Remplacé par le registre et les normaliseurs. |
| `IdvDecisionEngine.decideLive` | `rules_version = m5-1` | Nouvelle version `vision-1`. Le moteur de règles reste le juge. |
| `extracted_identity` | Noms, dates, numéro, pays, type | Mêmes clés, plus `document_code` et `schema_version`. |
| `verification_signals` | `ocr_analyze_id`, `unsupported_document`, `document_expired`, … | Nouveaux codes du §15, sans libellé d’authenticité. |
| SDK `assessDocumentFrame` | `too_small` (&lt; 480 px), `too_dark`, `too_bright`, `too_blurry` avant envoi | Le serveur refait ces mesures. Le client peut être contourné. |

`HostedFlowService` continue de choisir l’adaptateur selon le mode d’intégration : stub en test, vision en live.

## 4. Principes

1. Le LLM lit l’image. Il ne décide pas du dossier.
2. Toute sortie qui n’est pas le JSON du contrat est rejetée. Elle n’est pas persistée.
3. Check digits MRZ (ICAO 9303, pondération 7-3-1), dates, cohérence inter-champs et scores sont recalculés en Java. La valeur proposée par le modèle sur ces points est ignorée.
4. La valeur OCR brute est immuable. `normalizedValue` est une colonne dérivée. Si la normalisation échoue, la brute reste et le statut devient `AMBIGUOUS` ou `INVALID`.
5. Les doutes s’expriment par `fraud_indicator`, `anomaly`, `suspicious`, `requires_review`. Les mots `authentic` et `fake` sont réservés à une conclusion déterministe réelle (exemple : date d’expiration dépassée).
6. Qualité avant coût : le gate image s’exécute avant l’appel au modèle. Une image illisible ne déclenche pas le LLM.
7. Aucune donnée personnelle (texte OCR, nom, numéro) dans les logs. Journaliser `verification_id`, durée, code schéma, scores et codes d’erreur.
8. Une vérification fige `schema_version_id`. Désactiver ou remplacer un schéma ne réécrit pas les dossiers passés.

## 5. Architecture

```
image acceptée
  → ImageQualityGate          Java, avant tout appel
  → VisionDocumentPort        adaptateur fournisseur, image + catalogue actif
  → DocumentAnalysisParser    JSON strict, rejet si invalide
  → SchemaRegistry            code → version ACTIVE
  → FieldNormalizer           par type de champ
  → MrzEngine                 parse, check digits, comparaison visuelle
  → DocumentValidator         L1 à L6
  → ConfidenceScorer
  → DecisionMapper            DocumentSignals + IdvDecisionEngine vision-1
```

Tant que le catalogue actif reste sous environ 15 schémas, un seul appel vision porte la détection, la classification et l’extraction. Au-delà, deux appels : (A) détection, qualité et classification sur la liste fermée ; (B) extraction limitée au schéma retenu. L’image illisible s’arrête au gate, avant tout appel.

| Port | Rôle |
|---|---|
| `VisionDocumentPort` | Image + catalogue des schémas actifs → JSON brut. Injectable en test, sans réseau, sur le modèle de `AnalyzeIdClient`. |
| `SchemaRegistry` | Définitions, version active, champs, règles. |
| `MrzEngine` | TD1, TD2, TD3. Indépendant du modèle. |
| `DocumentAiPort` | Inchangé côté appelant. `provider` devient `vision_llm`. |

`IdvDecisionEngine` cesse de poser le signal `ocr_analyze_id` sur cette chaîne.

## 6. Détection du document

Deux familles de signaux. Les flags qualité se combinent. Le statut de présence est unique.

### 6.1 Présence

| Code | Définition |
|---|---|
| `NO_DOCUMENT` | Aucun rectangle de pièce d’identité dans le cadre. |
| `DOCUMENT_PRESENT` | Un seul document, bords visibles, surface utile exploitable. |
| `PARTIALLY_VISIBLE` | Le document est là, mais une zone d’identité sort du cadre ou est masquée. |
| `CROPPED` | Au moins un bord est coupé et un champ imprimé attendu est hors image. |
| `MULTIPLE_DOCUMENTS` | Deux supports d’identité distincts. L’extraction s’arrête. |
| `TOO_SMALL` | Le document occupe trop peu du cadre. Côté pixels : plus petit côté inférieur à 480 px, même plancher que le SDK. |
| `UNREADABLE` | Un document est présent, et aucun champ d’identité n’est lisible. |

Un document incliné et encore lisible reste `DOCUMENT_PRESENT` avec `tilted: true`.

### 6.2 Qualité

| Flag | Origine | Définition |
|---|---|---|
| `blur` | Serveur, confirmation modèle possible | Netteté sous le plancher document du SDK (netteté &lt; 4 sur l’échantillon). |
| `lowLight` | Serveur | Luminance moyenne &lt; 22. |
| `overexposed` | Serveur | Luminance moyenne &gt; 250. |
| `glare` | Modèle | Reflet qui couvre du texte. |
| `tilted` | Modèle | Inclinaison qui gêne la lecture. |
| `cropped` | Modèle | Même sens que `CROPPED`. |
| `partiallyVisible` | Modèle | Même sens que `PARTIALLY_VISIBLE`. |
| `multipleDocuments` | Modèle | Même sens que `MULTIPLE_DOCUMENTS`. |
| `tooSmall` | Serveur | Même sens que `TOO_SMALL`. |
| `unreadable` | Serveur, à partir des flags | Présent et non exploitable. |

Le serveur écrase `blur`, `lowLight`, `overexposed` et `tooSmall` avec ses propres mesures. La proposition du modèle sur ces quatre flags ne fait pas foi.

`readable` est calculé par le serveur. Il est vrai seulement si la présence est `DOCUMENT_PRESENT` (y compris le cas incliné lisible) et si aucun flag sévère n’est vrai. Flags sévères : `blur`, `glare` couvrant du texte, `cropped`, `partiallyVisible`, `multipleDocuments`, `tooSmall`, `lowLight`, `overexposed`.

`documentDetected` est faux pour `NO_DOCUMENT`. Il est vrai pour les autres présences, y compris `MULTIPLE_DOCUMENTS` — ce dernier arrête quand même la suite.

Si `readable` est faux : nouvelle capture (`recapture_requested`), raison qualité. Ce n’est pas un rejet fraude. Le plafond actuel reste 3 échecs, puis `declined`.

### 6.3 Modèle de résultat

```json
{
  "documentDetected": true,
  "detection": "DOCUMENT_PRESENT",
  "quality": {
    "readable": true,
    "blur": false,
    "glare": false,
    "cropped": false,
    "partiallyVisible": false,
    "multipleDocuments": false,
    "tooSmall": false,
    "lowLight": false,
    "overexposed": false,
    "tilted": false,
    "unreadable": false
  },
  "qualityScore": 0.91
}
```

`detection` appartient à l’enum fermée : `NO_DOCUMENT`, `DOCUMENT_PRESENT`, `PARTIALLY_VISIBLE`, `CROPPED`, `MULTIPLE_DOCUMENTS`, `TOO_SMALL`, `UNREADABLE`. Une valeur inconnue invalide le JSON.

## 7. Classification

Le modèle choisit un code dans la liste fermée des définitions `enabled` dont une version est `ACTIVE`, plus `UNKNOWN`.

| Code | Pays | Type | Côté |
|---|---|---|---|
| `CAMEROON_CNI` | `CM` | `NATIONAL_ID` | `FRONT` ou `BACK` |
| `CAMEROON_PASSPORT` | `CM` | `PASSPORT` | `BIOGRAPHIC` |
| `CAMEROON_DRIVER_LICENSE` | `CM` | `DRIVING_LICENSE` | `FRONT` |
| `FRANCE_CNI` | `FR` | `NATIONAL_ID` | `FRONT` ou `BACK` |
| `FRANCE_PASSPORT` | `FR` | `PASSPORT` | `BIOGRAPHIC` |
| `CANADA_PASSPORT` | `CA` | `PASSPORT` | `BIOGRAPHIC` |
| `QUEBEC_DRIVER_LICENSE` | `CA` | `DRIVING_LICENSE` | `FRONT` |
| `UNKNOWN` | `ZZ` | `UNKNOWN` | `UNKNOWN` |

Recto, verso et page biographique sont des définitions distinctes (même `code` de famille, `side` différent), pas un second type métier. La version (`2025`, `2024`) est un attribut de la définition. Elle n’est gardée que si cette version existe pour ce code. Sinon le serveur retient la version `ACTIVE` et pose `unexpected_document_structure`.

```json
{
  "classification": {
    "code": "QUEBEC_DRIVER_LICENSE",
    "country": "CA",
    "documentType": "DRIVING_LICENSE",
    "version": "2024",
    "side": "FRONT",
    "confidence": 0.94
  }
}
```

Le prompt reçoit le catalogue généré depuis la base. Aucune branche `if (cameroon)` dans le pipeline. Le parseur force `UNKNOWN` si le code renvoyé n’est pas actif. Confiance de classe inférieure à 0,80 : le code proposé est conservé pour la revue, et la décision part en `DOCUMENT_UNKNOWN`.

`UNKNOWN` ne déclenche plus un `DECLINED` / `unsupported_document` automatique. Une erreur de classe du modèle ne doit pas refuser le dossier.

Projection vers `extracted_identity` : `document_type`, `document_country`, `issuing_jurisdiction` (ex. `QC`), plus `document_code`.

## 8. Registre de schémas

### 8.1 Modèle

Une définition identifie un document de façon stable. Une version porte les champs et les règles à un instant donné.

```json
{
  "code": "CAMEROON_CNI",
  "country": "CM",
  "documentType": "NATIONAL_ID",
  "version": "2025",
  "side": "FRONT",
  "mrzFormat": "TD1",
  "fields": [
    { "name": "documentNumber", "type": "DOCUMENT_NUMBER", "required": true },
    { "name": "lastName", "type": "NAME", "required": true },
    { "name": "firstName", "type": "NAME", "required": true },
    { "name": "dateOfBirth", "type": "DATE", "required": true, "formats": ["dd/MM/yyyy"] }
  ]
}
```

Types de champ : `STRING`, `NAME`, `DATE`, `SEX`, `COUNTRY`, `NATIONALITY`, `DOCUMENT_NUMBER`, `ADDRESS`, `POSTAL_CODE`, `MRZ`.

### 8.2 Tables et relations

```
document_definitions 1 ── * document_definition_versions
document_definition_versions 1 ── * document_fields
document_definition_versions 1 ── * document_validation_rules
verifications 1 ── 1 verification_document_analyses
verification_document_analyses * ── 0..1 document_definition_versions
```

**`document_definitions`**

| Colonne | Rôle |
|---|---|
| `id` | UUID |
| `code` | Unique. Ex. `CAMEROON_CNI` |
| `country` | ISO 3166-1 alpha-2 |
| `document_type` | `NATIONAL_ID`, `PASSPORT`, `DRIVING_LICENSE` |
| `side` | `FRONT`, `BACK`, `BIOGRAPHIC` |
| `mrz_format` | `NONE`, `TD1`, `TD2`, `TD3` |
| `enabled` | Activation globale |
| `created_at`, `updated_at` | |

**`document_definition_versions`**

| Colonne | Rôle |
|---|---|
| `id` | UUID |
| `definition_id` | FK |
| `version` | Ex. `2025` |
| `status` | `DRAFT`, `ACTIVE`, `RETIRED` |
| `schema_json` | Document édité : champs, règles, indices visuels du prompt |
| `activated_at`, `retired_at` | |
| Unique `(definition_id, version)` | |
| Une seule ligne `ACTIVE` par définition | Index partiel |

**`document_fields`**

| Colonne | Rôle |
|---|---|
| `id` | UUID |
| `version_id` | FK |
| `name` | Unique dans la version |
| `value_type` | Enum du §8.1 |
| `required` | Booléen |
| `field_order` | Ordre d’affichage en revue |
| `normalizer` | Ex. `ISO_DATE`, `ICAO_NAME` |
| `formats` | Liste, pour les dates et les codes postaux |

**`document_validation_rules`**

| Colonne | Rôle |
|---|---|
| `id` | UUID |
| `version_id` | FK |
| `level` | `L3` ou `L5` |
| `code` | Ex. `DOB_AFTER_ISSUE` |
| `expression` | Ex. `dateOfBirth < dateOfIssue` |
| `severity` | `ERROR` ou `WARNING` |

**`verification_document_analyses`**

| Colonne | Rôle |
|---|---|
| `verification_id` | FK, une analyse par vérification au premier livrable |
| `schema_version_id` | Nullable si `UNKNOWN` |
| `provider` | `vision_llm` |
| `model_id` | Fournisseur et modèle, pour l’audit |
| `detection_json` | §6 |
| `classification_json` | §7 |
| `fields_json` | Brutes et normalisées |
| `mrz_json` | Lignes, parse, check digits |
| `validation_json` | L1–L6 |
| `indicators_json` | §13 |
| `scores_json` | §14 |
| `created_at` | |

`schema_json` est la source écrite. À l’activation il est validé puis projeté dans `document_fields` et `document_validation_rules`. Ajouter un champ ne demande pas de migration de code.

### 8.3 Cycle de vie

- **Brouillon.** Éditable. Absent du catalogue envoyé au modèle.
- **Validation à l’activation.** Noms de champs uniques, types connus, au moins un format sur chaque `DATE`, champs `MRZ` présents si `mrz_format` n’est pas `NONE`, expressions L3 limitées au langage autorisé (`dateOfBirth < dateOfIssue`, `expirationDate >= dateOfIssue`, comparaisons du même genre entre champs datés du schéma). Échec : la version reste `DRAFT`.
- **Activation.** Dans la même transaction, la version `ACTIVE` précédente passe `RETIRED` et sort du catalogue.
- **Désactivation.** `enabled = false` retire toutes les versions du catalogue. Les analyses déjà liées à un `schema_version_id` restent lisibles.
- **Historique.** Un rejeu relit la version figée sur l’analyse. Il ne recharge pas la version active du jour.
- **Inconnu.** `code = UNKNOWN`, `schema_version_id` null, aucun champ métier. Décision : `REVIEW` / `DOCUMENT_UNKNOWN`.

## 9. OCR

```json
{
  "rawText": "REPUBLIQUE DU CAMEROUN\nONANA\n…",
  "zones": [
    {
      "id": "z1",
      "text": "ONANA",
      "confidence": 0.98,
      "boundingBox": { "x": 0.2, "y": 0.3, "width": 0.2, "height": 0.05 }
    }
  ]
}
```

`x`, `y`, `width`, `height` sont dans `[0,1]`, origine en haut à gauche. L’absence de boîte est autorisée. Une boîte hors intervalle est annulée (`boundingBox: null`) ; le texte de la zone est conservé.

### Limite des boîtes et architecture retenue

Un LLM multimodal ne localise pas un glyphe au pixel. La boîte qu’il renvoie est une estimation sémantique, souvent décalée d’une ligne ou d’un bloc. Elle ne participe ni à la validation d’un champ, ni au check digit, ni à un indicateur d’altération.

La décision s’appuie sur `rawText` et les champs. Chaque boîte est stockée avec `bboxPrecision: APPROXIMATE` et `bboxSource: MODEL_ESTIMATE`, pour un surlignage indicatif dans la revue. Des boîtes exploitables supposent un modèle de mise en page distinct, derrière un autre port. Ce n’est pas un prérequis de ce chantier.

## 10. Extraction des champs

Le catalogue envoyé au modèle ne contient que les noms du schéma actif. Le parseur supprime tout champ absent de ce schéma.

```json
{
  "field": "dateOfBirth",
  "value": "12/05/1990",
  "normalizedValue": "1990-05-12",
  "confidence": 0.97,
  "source": "VISUAL_TEXT",
  "validationStatus": "VALID"
}
```

| Attribut | Règle serveur |
|---|---|
| `value` | Texte OCR brut. Immuable. Jamais remplacé par la forme normalisée. |
| `normalizedValue` | Écrit par le normaliseur Java. La proposition du modèle est ignorée. |
| `confidence` | Nombre du modèle dans `[0,1]`. Absent → `null`. |
| `source` | `VISUAL_TEXT`, `MRZ`, `VISUAL_AND_MRZ`. |
| `validationStatus` | `VALID`, `INVALID`, `MISSING`, `AMBIGUOUS`, `MISMATCH`, `NOT_APPLICABLE`. |

Un champ requis absent est un objet `{ "field": "dateOfBirth", "value": null, "validationStatus": "MISSING" }`. Le modèle ne complète pas un trou par une valeur déduite.

## 11. Normalisation

`value` reste la preuve de lecture. `normalizedValue` est la forme canonique affichable. `comparisonKey` sert uniquement au rapprochement MRZ et n’est pas exposée comme identité légale.

| Type | Canonique | Échec |
|---|---|---|
| `DATE` | `YYYY-MM-DD`, selon les formats déclarés sur le champ (`dd/MM/yyyy`, `yyyy-MM-dd`, …) | Deux formats également possibles → `normalizedValue = null`, statut `AMBIGUOUS`. Jour calendaire impossible → `INVALID`. La brute est conservée dans les deux cas. |
| `NAME` | Affichage : brute, espaces resserrés. Clé : majuscules, accents retirés, particules du schéma conservées | Vide après nettoyage et champ requis → `MISSING`. |
| `SEX` | `M`, `F` ou `X`, via la table du schéma (`M`, `F`, `MASCULIN`, `FÉMININ`, …) | Hors table → `INVALID`, brute conservée. |
| `COUNTRY` | ISO 3166-1 alpha-2 | Inconnu → `INVALID`. |
| `NATIONALITY` | Alpha-2 à l’affichage. Code ICAO à 3 lettres dans la clé MRZ | Inconnu → `INVALID`. |
| `DOCUMENT_NUMBER` | Brute conservée. Clé : majuscules, espaces et tirets retirés | Vide et requis → `MISSING`. |
| `ADDRESS` | Espaces et césures resserrés. Pas de géocodage | — |
| `POSTAL_CODE` | Motif du schéma (Québec `A1A 1A1`, France `75001`) | Hors motif → `INVALID`, brute conservée. |

## 12. MRZ

Le modèle peut transcrire les lignes. `MrzEngine` est le seul à parser et à calculer.

Formats : `TD3` (passeport, 2×44), `TD1` (carte, 3×30), `TD2` (2×36). Le format attendu vient de `mrz_format` sur la définition, pas du modèle.

1. Repérer dans `rawText` et dans les champs de type `MRZ` un bloc aux longueurs ICAO et à l’alphabet autorisé.
2. Découper les positions fixes : nom, prénom, numéro, nationalité, naissance, sexe, expiration, donnée optionnelle.
3. Recalculer chaque check digit (pondération 7-3-1). Le chiffre renvoyé par le modèle n’est pas réutilisé.
4. Comparer les `comparisonKey` visuelles et MRZ (nom, prénom, date de naissance, numéro, expiration, sexe). Égalité → `source: VISUAL_AND_MRZ`. Écart → `validationStatus: MISMATCH`, sévérité `WARNING`, raison `MRZ_MISMATCH`.
5. Une seule substitution de caractère OCR est acceptée si elle rend tous les check digits du bloc valides. Sinon la ligne reste en échec `mrz_check_digit_failed`.

Schéma avec MRZ et bloc absent : signal `mrz_unavailable` (`UNAVAILABLE`), `mrzScore = 0`. Schéma `mrz_format: NONE` : le niveau L4 vaut `NOT_APPLICABLE` et `mrzScore` sort de la formule.

## 13. Validation

Chaque item est `{ "level", "code", "severity", "field" }`. Un `ERROR` interdit `PASS`.

| Niveau | Moteur | Contenu |
|---|---|---|
| L1 syntaxe | Normaliseur, par type | Date non parseable, sexe hors table, code pays inconnu, code postal hors motif |
| L2 schéma | Registre | Requis manquant, champ hors schéma ignoré |
| L3 inter-champs | Règles de la version | Voir exemples |
| L4 MRZ | `MrzEngine` | Format, check digits, comparaison visuelle |
| L5 cohérence document | Règles de la version | Ex. passeport dont `mrz_format = TD3` sans bloc MRZ |
| L6 indicateurs | Liste fermée du §14 | Doute, pas un jugement d’authenticité |

Exemples L3 :

| Règle | Sévérité | Code |
|---|---|---|
| `dateOfBirth > dateOfIssue` | `ERROR` | `DOB_AFTER_ISSUE` |
| `expirationDate < dateOfIssue` | `ERROR` | `EXPIRY_BEFORE_ISSUE` |
| `expirationDate < aujourd’hui` (UTC) et date `VALID` | `ERROR` | `document_expired` → décision `REJECT` |

Exemple L4 : nom MRZ différent du nom visuel après normalisation → `WARNING` / `MISMATCH`, raison de revue `MRZ_MISMATCH`.

Le langage d’expression L3/L5 est fermé et évalué en Java. Une expression inconnue empêche l’activation de la version.

## 14. Indicateurs

Le modèle peut proposer un doute. Il ne prouve pas qu’un document est authentique. Codes et libellés utilisent `fraud_indicator`, `anomaly`, `suspicious`, `requires_review`.

| Code | Sévérité | Origine |
|---|---|---|
| `document_expired` | `ERROR` | Date calculée en Java |
| `inconsistent_dates` | `ERROR` | L3 |
| `impossible_date` | `ERROR` | Calendrier impossible, ou naissance dans le futur |
| `mrz_mismatch` | `WARNING` | L4, après normalisation |
| `mrz_check_digit_failed` | `WARNING` | L4, calcul Java |
| `missing_expected_field` | `WARNING` | L2 |
| `suspicious_text` | `WARNING` | Modèle : libellé ou alphabet incohérent avec le schéma |
| `suspicious_layout` | `WARNING` | Modèle : structure inattendue |
| `poor_image_quality` | `WARNING` | Qualité faible alors qu’une lecture a quand même été tentée |
| `possible_alteration` | `WARNING` | Modèle : zone visuellement suspecte. Motif de revue |
| `unexpected_document_structure` | `WARNING` | Version ou côté absent du registre |

`possible_alteration`, `suspicious_text` et `suspicious_layout` envoient le dossier en revue. Ils ne produisent pas `REJECT`. Seule une règle déterministe qui conclut vraiment (document expiré, date impossible) produit un `ERROR` et, pour l’expiration, un `REJECT`.

## 15. Scores

Tous les scores sont dans `[0,1]` et calculés en Java. La confiance brute du modèle n’est pas recopiée comme score global.

| Score | Calcul |
|---|---|
| `documentTypeScore` | Confiance du modèle si le code est actif, sinon `0` |
| `qualityScore` | `1`, moins `0,25` par flag sévère, plancher `0`. Image non `readable` → `0` |
| `ocrScore` | Moyenne des confiances des champs requis extraits. Un requis manquant n’entre pas dans cette moyenne ; il baisse `fieldScore` |
| `fieldScore` | Nombre de requis en `VALID` / nombre de requis |
| `mrzScore` | `1` si format, check digits et comparaison passent ; `0,4` si le bloc est parsé avec un écart ou un check digit faux ; `0` si la MRZ est requise et absente |
| `validationScore` | `1`, moins `0,35` par `ERROR` et `0,15` par `WARNING`, plancher `0` |
| `overallScore` | Combinaison ci-dessous |

Avec MRZ :

```
overallScore = 0,15·documentTypeScore
             + 0,20·qualityScore
             + 0,15·ocrScore
             + 0,25·fieldScore
             + 0,15·mrzScore
             + 0,10·validationScore
```

Sans MRZ (`mrz_format = NONE`), le poids `0,15` de `mrzScore` est réparti : `fieldScore` passe à `0,35` et `validationScore` à `0,15`. La somme des poids reste 1.

| Bande | Condition |
|---|---|
| `HIGH_CONFIDENCE` | `overallScore` ≥ 0,90 |
| `MEDIUM_CONFIDENCE` | ≥ 0,75 et &lt; 0,90 |
| `LOW_CONFIDENCE` | ≥ 0,55 et &lt; 0,75 |
| `REVIEW` | &lt; 0,55 |

La bande ne décide pas seule. Un `ERROR` ou une raison du §16 empêche `PASS` même au-dessus de 0,90.

## 16. Revue humaine

L’évaluation suit cet ordre. La première ligne qui s’applique tranche.

| Condition | Issue | Raison |
|---|---|---|
| `readable = false` | Recapture, pas une décision fraude | `IMAGE_TOO_BLURRY`, ou le flag sévère dominant |
| `document_expired` et date `VALID` | `REJECT` | `document_expired` |
| Code `UNKNOWN` ou confiance de classe &lt; 0,80 | `REVIEW` | `DOCUMENT_UNKNOWN` |
| Champ requis `MISSING` | `REVIEW` | `MISSING_REQUIRED_FIELD` |
| Écart MRZ ou check digit en échec | `REVIEW` | `MRZ_MISMATCH` |
| Confiance d’un champ requis &lt; 0,70 | `REVIEW` | `LOW_OCR_CONFIDENCE` |
| Indicateur L6 en `WARNING` | `REVIEW` | `SUSPICIOUS_INDICATOR` |
| Bande `HIGH_CONFIDENCE`, aucun `ERROR`, aucun `WARNING` | `PASS` | — |
| Tout le reste | `REVIEW` | `LOW_CONFIDENCE` |

Plusieurs raisons peuvent être enregistrées. L’issue reste celle de la ligne la plus haute qui s’applique.

| Issue métier | `VerificationDecision` persistée |
|---|---|
| `PASS` | `APPROVED` |
| `REVIEW` | `REVIEW` |
| `REJECT` | `DECLINED` |

`rules_version` de ce chemin : `vision-1`.

### Dossier pour le dashboard futur

`verification_document_analyses` et le média déjà stocké fournissent à l’opérateur :

- l’image ;
- le code, le côté et la version de schéma figée ;
- chaque champ : `value`, `normalizedValue`, confiance, source, statut ;
- les lignes MRZ, le parse et le résultat de chaque check digit ;
- les indicateurs et les six scores plus `overallScore` ;
- les raisons de décision.

L’action de l’analyste réutilise la décision manuelle auditée de la console. Cet écran n’est pas dans le présent chantier.

## 17. Contrat JSON du modèle

Un seul schéma décrit la réponse attendue (détection, classification, `rawText`, zones, champs). Le parseur valide ce schéma avant toute écriture. Propriétés inconnues : ignorées. Code de classification hors catalogue : forcé à `UNKNOWN`. `normalizedValue` et check digits fournis par le modèle : ignorés, puis recalculés.

Échec de validation, délai ou erreur fournisseur : une nouvelle tentative au plus. Second échec : `provider_unavailable`, décision `REVIEW`, comme `IdvDecisionEngine.providerUnavailable()` aujourd’hui. Pas de boucle de retry dans la requête du flow.

## 18. Livraison

1. **Contrat et tests.** Schéma JSON, parseur, fixture sans réseau, `provider = vision_llm`. Le bac à sable reste sur `StubDocumentAi`.
2. **Registre.** Tables, activation de `QUEBEC_DRIVER_LICENSE` à iso-comportement avec le mapper actuel : pays `CA`, type permis, juridiction `QC`.
3. **Gate qualité** puis adaptateur live. `HostedFlowService` appelle toujours `DocumentAiPort`.
4. **Normalisation et L1–L3.** Projection `extracted_identity` : clés actuelles plus `document_code` et `schema_version`.
5. **`MrzEngine`** sur `TD1` et `TD3`, comparaison visuelle.
6. **Scores, indicateurs, `decideLive` en `vision-1`.** Textract peut rester branché le temps du basculement, puis quitte le chemin live.
7. **Autres pays** = lignes de registre et fixtures. Pas de nouveau mapper Java.
8. **Écran de revue**, une fois les analyses persistées.

Critère du premier palier : une session live permis Québec produit une décision justifiée et une analyse persistée, sans appel Textract AnalyzeID. Une session `ky_test_` du même artefact produit zéro appel au fournisseur vision.

## 19. Critères d’acceptation

Deux natures. Les gates d’ingénierie conditionnent la livraison. Les pourcentages du modèle sont des objectifs initiaux : ils ne deviennent un gate qu’une fois mesurés sur un jeu annoté réel — au moins 200 images par code actif, recto et verso séparés, avec flou, reflet, document coupé et pièces hors catalogue.

| Critère | Objectif initial | Mesure |
|---|---|---|
| Classification | ≥ 97 % | Bon code sur les images à document unique et `readable`. `UNKNOWN` est juste lorsque la pièce est hors catalogue. Une erreur de version seule est un échec. |
| Extraction des champs | ≥ 95 % | Champs requis dont `normalizedValue` égale la vérité terrain, sur les images bien classées. |
| OCR | ≥ 97 % | Caractères corrects sur les zones d’identité annotées (taux d’erreur caractère ≤ 3 %). |
| MRZ de bout en bout | ≥ 99 % | Lignes MRZ lisibles dont le parse Java, les check digits et la comparaison visuelle sont justes. |
| Check digits | 100 % | Vecteurs ICAO 9303 TD1, TD2 et TD3, sans image et sans LLM. Gate de livraison. |
| JSON Schema | 100 % | Toute analyse persistée valide le schéma. Une sortie modèle invalide est rejetée ou retentée, jamais stockée. Gate de livraison. |
| Disponibilité API | ≥ 99 % | Sur 30 jours, hors maintenance annoncée. Indisponible = erreur 5xx sur le parcours de décision live, ou délai supérieur à 30 s. |
| Bac à sable | 0 appel | Session `ky_test_` : zéro appel vision. Gate de livraison, dans l’esprit de `SandboxNoAwsTest`. |

Les objectifs de classification, d’extraction, d’OCR et de MRZ de bout en bout seront recalibrés si le jeu réel ne les porte pas. Le moteur de check digits et la validité du JSON stocké restent à 100 % indépendamment du jeu d’images.
