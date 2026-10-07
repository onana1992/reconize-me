# Roadmap d’implémentation — Document IA

**Produit :** Recogniz-Me, Identity & Document Verification  
**Version du document :** 1.1  
**Date :** 6 octobre 2026  
**Statut :** V0–V2 livrés ; V3 non commencé  
**Document lié :** [`cahier-des-charges-document-ia.md`](./cahier-des-charges-document-ia.md) — le *quoi* (contrat, registre, MRZ, scores, décision `vision-1`)

Ce document dit **dans quel ordre** construire Document IA. Un palier n’est pas vert tant que son résultat n’est pas visible dans Swagger, sur le banc isolé, sans ouvrir une session de vérification.

Le bac à sable (`ky_test_` → `StubDocumentAi`) et la biométrie (Rekognition) ne bougent pas. Textract reste le chemin live de `HostedFlowService` jusqu’au palier **V8**.

---

## 1. Cible

**Happy path du banc :** dans Swagger, envoyer l’image d’un permis du Québec et lire, dans la même réponse, la qualité, le JSON du modèle, les champs normalisés, la MRZ, les scores et la décision `vision-1`. Aucune vérification n’est créée. Aucun selfie. Aucun débit. Aucun webhook.

**Happy path produit (dernier palier) :** une session `ky_live_` du permis Québec produit la même décision et une ligne `verification_document_analyses`, sans appel Textract AnalyzeID. Une session `ky_test_` du même artefact produit zéro appel au fournisseur vision.

---

## 2. Pourquoi un banc, pas le flow

Aujourd’hui la lecture document n’est joignable qu’à l’intérieur de `HostedFlowService.decide`, après consentement, upload et selfie. Ce parcours empêche de voir un palier intermédiaire (parseur seul, gate seul, MRZ seule).

Le banc est un contrôleur à part. Il appelle le pipeline Document IA et renvoie le JSON. Il ne passe pas par `VerificationService`.

| | Banc Swagger | Session live (V8 seulement) |
|---|---|---|
| Route | `/v1/document-ia/**` | `/v1/flow/{token}/**` puis `decide` |
| Auth | `Authorization: Bearer ky_live_…` | token d’URL du flow |
| Effet de bord | aucun | décision, signaux, webhook, crédits |
| Biométrie | jamais | inchangée |
| `ky_test_` | refusé sur l’appel vision (0 appel) | `StubDocumentAi`, comme aujourd’hui |

`/v1/document-ia/**` n’est pas ajouté à la liste `PUBLIC` de `ApiKeyAuthenticationFilter`. Le filtre Bearer existant suffit : Swagger a déjà le scheme `bearer-api-key`.

Flag : `kyc.document-ia.lab-enabled` (défaut `true` en local, `false` hors dev). Flag éteint → **404**, pour ne pas publier le banc.

---

## 3. Ce que Swagger montre à chaque palier

Une seule réponse, qui se remplit. Les blocs pas encore construits valent `null`. Le query `until` arrête le pipeline au bloc demandé : on voit ce palier sans payer les suivants.

`POST /v1/document-ia/analyze`

```json
{
  "pipeline": "vision-1",
  "stoppedAt": "quality",
  "provider": "vision_llm",
  "providerCalled": false,
  "schemaVersionId": null,
  "quality": null,
  "rawModel": null,
  "parsed": null,
  "fields": null,
  "mrz": null,
  "validation": null,
  "indicators": null,
  "scores": null,
  "decision": null
}
```

| Palier | `until` | Bloc qui devient non null |
|---|---|---|
| V0 | — | enveloppe seule, tout le reste `null` |
| V1 | `parse` | `parsed` (depuis une fixture, pas depuis une image) |
| V2 | `parse` | `schemaVersionId` résolu via le registre |
| V3 | `quality` | `quality`, `providerCalled: false` |
| V4 | `vision` | `rawModel`, `providerCalled: true` |
| V5 | `normalize` | `fields` (`value` brute, `normalizedValue` Java) |
| V6 | `mrz` | `mrz` |
| V7 | `decide` | `validation`, `indicators`, `scores`, `decision` |
| V8 | — | la session live persiste la même forme ; le banc reste |

Trois opérations, stables du premier au dernier palier :

| Méthode | Rôle | Réseau vision |
|---|---|---|
| `GET /v1/document-ia/catalog` | schémas `ACTIVE` envoyés au modèle | non |
| `POST /v1/document-ia/analyze` | `multipart` champ `file`, query `until` | oui, à partir de V4, et seulement en `ky_live_` |
| `POST /v1/document-ia/fixtures/{name}` | rejoue un JSON de `src/test/resources/document-ia/` | non |

`ky_test_` sur `POST /analyze` répond **403** `sandbox_no_vision` et n’ouvre pas de client vision. C’est le contrôle Swagger du critère « 0 appel » du cahier des charges, sans lancer une session.

Procédure, à chaque palier :

1. `mvn spring-boot:run` dans `api/`.
2. Ouvrir `http://localhost:8080/swagger-ui.html`.
3. **Authorize**, coller la clé `ky_live_…` (sans le mot Bearer : Swagger l’ajoute).
4. Exécuter l’opération du palier. La réponse **est** le livrable.

Aucune donnée personnelle dans les logs : `verification_id` n’existe pas sur le banc ; journaliser la durée, le code schéma, les scores et les codes d’erreur.

---

## 4. Principes d’ordre

1. **Banc avant pipeline.** V0 est le premier commit utile. Sans lui, on ne voit rien avant V8.
2. **Fixture avant fournisseur.** Le parseur, le registre, les normaliseurs et la MRZ se prouvent avec un JSON collé dans Swagger. L’appel modèle n’arrive qu’en V4.
3. **Qualité avant coût.** Dès V3, une image illisible s’arrête avec `providerCalled: false`.
4. **Java recalcule.** Check digits, dates, scores et décision ne font pas foi s’ils viennent du modèle. La réponse Swagger montre la valeur du modèle ignorée et la valeur Java.
5. **Le flow ne bouge pas avant V8.** `HostedFlowService`, `AwsDocumentAi` et `QcAnalyzeIdMapper` restent le chemin live tant que le banc `until=decide` n’est pas vert sur un permis Québec.
6. **Un document de plus = une ligne de registre** (V9). Pas une classe Java, pas un mapper.

**Arrêt (kill) :** banc sans auth ou avec le flag oublié en prod ; `ky_test_` qui appelle le fournisseur ; JSON modèle invalide persisté ; PII dans les logs ; le LLM qui écrit `APPROVED` / `DECLINED` ; Textract retiré avant que V7 soit vert sur fixture.

---

## 5. Vue d’ensemble

```
V0 banc Swagger (enveloppe vide, Bearer, flag)
        │
        ▼
V1 parseur + fixtures JSON          ─┐  sans image, sans réseau
        │                            │
        ▼                            │
V2 registre + catalogue Swagger     ─┘
        │
        ▼
V3 ImageQualityGate (upload, 0 appel)
        │
        ▼
V4 VisionDocumentPort (1er vrai appel, réponse brute)
        │
        ▼
V5 normaliseurs + L1–L3
        │
        ▼
V6 MrzEngine (TD1, TD2, TD3)
        │
        ▼
V7 scores + décision vision-1 sur le banc
        │
        ▼
V8 persistance + HostedFlowService live ; Textract quitte le chemin live
        │
        ▼
V9 autres pays = lignes + fixtures, même endpoint
```

Hors de cette roadmap : écran de revue opérateur, biométrie, boîtes au pixel, preuve d’authenticité.

| Palier | Livrable Swagger | Dépend de | Statut |
|---|---|---|---|
| **V0** | `POST /analyze` renvoie l’enveloppe, tout à `null` | — | **livré** |
| **V1** | fixture valide → `parsed` ; fixture cassée → 422, rien d’écrit | V0 | **livré** |
| **V2** | `GET /catalog` liste `QUEBEC_DRIVER_LICENSE` ; code inconnu → `UNKNOWN` | V1 | **livré** |
| **V3** | image trop petite ou trop sombre → `quality`, `providerCalled: false` | V0 |
| **V4** | image lisible → `rawModel` ; second échec fournisseur → `provider_unavailable` | V1, V3 |
| **V5** | `fields[].value` intact, `normalizedValue` calculée | V2, V4 |
| **V6** | check digits ICAO justes dans `mrz` ; vecteurs sans image | V5 |
| **V7** | `decision.rulesVersion = vision-1` et issue du §16 du cahier | V5, V6 |
| **V8** | session live permis QC = même décision, analyse en base, 0 Textract | V7 |
| **V9** | un nouveau code du catalogue passe dans le même `POST /analyze` | V8 |

V3 peut démarrer en parallèle de V1–V2 : il ne lit pas le registre. V6 peut démarrer dès V1 sur des vecteurs de lignes seules ; le branchement dans `POST /analyze` attend V5.

---

## 6. Paliers

### V0 — Banc

**Code**

- `DocumentIaController` sous `/v1/document-ia`.
- DTO de l’enveloppe (§3). `until` ignoré tant que les blocs n’existent pas, mais déjà parsé : `quality`, `parse`, `vision`, `normalize`, `mrz`, `validate`, `decide`.
- `kyc.document-ia.lab-enabled`.
- Annotations springdoc : `@Tag`, `@SecurityRequirement(name = "bearer-api-key")`, `@RequestPart` pour le fichier.

**Swagger**

- Sans Authorize → 401.
- Authorize `ky_live_…`, `POST /analyze` avec un JPEG quelconque → 200, enveloppe, blocs `null`, `providerCalled: false`.
- Flag à `false` → 404.
- L’opération apparaît dans Swagger UI sans toucher `/v1/verifications` ni `/v1/flow`.

**Hors palier :** parseur, base, appel modèle.

---

### V1 — Contrat et parseur

**Code**

- Schéma JSON unique (détection, classification, `rawText`, zones, champs) — cahier §6, §7, §9, §10, §17.
- `DocumentAnalysisParser` : propriétés inconnues ignorées ; `detection` hors enum → rejet ; boîte hors `[0,1]` → `boundingBox: null`, texte de zone conservé ; `normalizedValue` et check digits du modèle ignorés.
- Fixtures dans `api/src/test/resources/document-ia/` : une valide permis QC, une avec code hors catalogue, une JSON invalide.
- `POST /v1/document-ia/fixtures/{name}` exécute le parseur et remplit `parsed`.

**Swagger**

- `fixtures/quebec-driver-license` → 200, `stoppedAt: parse`, `parsed.classification.code`, `provider: vision_llm`, `providerCalled: false`.
- `fixtures/invalid` → 422, corps d’erreur, aucune analyse à stocker (il n’y a pas encore de table).
- `fixtures/unknown-code` → 200, code forcé à `UNKNOWN`.

**Hors palier :** tables, gate image, fournisseur.

---

### V2 — Registre

**Code**

- Flyway : `document_definitions`, `document_definition_versions`, `document_fields`, `document_validation_rules`. Pas encore `verification_document_analyses` (V8).
- Seed : `QUEBEC_DRIVER_LICENSE`, pays `CA`, type `DRIVING_LICENSE`, côté `FRONT`, `mrz_format: NONE`, version `ACTIVE`, iso-comportement du mapper actuel (juridiction `QC`).
- `SchemaRegistry` : catalogue = définitions `enabled` avec une version `ACTIVE`, plus `UNKNOWN`.
- Activation : validation du `schema_json` puis projection vers `document_fields` / `document_validation_rules`, dans la même transaction que le passage `RETIRED` de la version précédente.
- Le parseur efface les champs absents du schéma. Confiance de classe &lt; 0,80 : code conservé, marqué pour `DOCUMENT_UNKNOWN` au palier décision.

**Swagger**

- `GET /catalog` → le permis Québec, et seulement lui.
- Fixture du V1 → `schemaVersionId` renseigné.
- Fixture dont `version` n’existe pas → version `ACTIVE` retenue et indicateur `unexpected_document_structure` posé dans `parsed` (le bloc `indicators` complet arrive en V7).

**Hors palier :** les autres pays (V9). Pas d’API d’édition de schéma dans ce palier : le seed Flyway suffit pour voir le catalogue.

---

### V3 — Gate qualité

**Code**

- `ImageQualityGate` avant tout port vision. Mesures serveur : plus petit côté &lt; 480 px → `tooSmall` ; luminance moyenne &lt; 22 → `lowLight` ; &gt; 250 → `overexposed` ; netteté sous le plancher → `blur`.
- Ces quatre flags écrasent ceux du modèle le jour où le modèle répond (V4). En V3 le modèle n’est pas appelé.
- `readable` calculé comme au cahier §6.2. `readable: false` → la réponse s’arrête, raison qualité (`IMAGE_TOO_BLURRY` ou flag sévère dominant). Ce n’est pas un `DECLINED`.

**Swagger**

- JPEG dont le petit côté est &lt; 480 → `until=quality`, `quality.tooSmall: true`, `quality.readable: false`, `providerCalled: false`.
- Image nette et assez grande → `readable: true`, pipeline prêt à continuer, toujours `providerCalled: false`.

**Hors palier :** les flags que seul le modèle peut voir (`glare`, `tilted`, `cropped`, `partiallyVisible`, `multipleDocuments`) restent absents tant que V4 n’a pas renvoyé de JSON.

---

### V4 — Fournisseur vision

**Code**

- `VisionDocumentPort` : image + catalogue actif → JSON brut. Un fake injectable (fixture, zéro réseau) pour les tests. L’adaptateur réel est choisi par la config, sur le modèle de `IdvStoreConfig`.
- Qualité d’abord : si V3 dit `readable: false`, le port n’est pas appelé.
- Un seul appel tant que le catalogue actif reste petit (§5 du cahier). Échec, délai ou JSON rejeté : une nouvelle tentative. Second échec : `provider_unavailable` dans l’enveloppe, pas de boucle dans la requête.
- Prompt construit depuis `GET /catalog`, pas depuis une branche `if (quebec)`.
- `AwsDocumentAi` / Textract : toujours le chemin de `HostedFlowService`. Le banc ne les utilise pas.

**Swagger**

- Image lisible, `until=vision`, clé live, flag lab → `providerCalled: true`, `rawModel` = JSON brut, puis `parsed` rempli par le parseur du V1.
- Couper le fournisseur (mauvaise clé, timeout) → deux tentatives visibles dans les logs (durée, code d’erreur, pas de texte OCR), réponse `provider_unavailable`.
- Même image avec `ky_test_` → 403, et aucun log d’appel.

**Hors palier :** normaliser, scorer, décider, écrire en base.

---

### V5 — Normalisation et L1–L3

**Code**

- `FieldNormalizer` par type du cahier §11. `value` immuable. `normalizedValue` écrit ici ; la proposition du modèle reste ignorée.
- L1 : date impossible, sexe hors table, pays inconnu, code postal hors motif.
- L2 : requis manquant → objet `MISSING` ; champ hors schéma déjà retiré en V2.
- L3 : évaluateur Java du langage fermé (`dateOfBirth < dateOfIssue`, `expirationDate >= dateOfIssue`, expiration &lt; aujourd’hui UTC). Expression inconnue : la version ne s’active pas (déjà la règle du V2, prouvée ici par un seed `DRAFT` refusé).

**Swagger**

- `until=normalize` sur la fixture permis QC → chaque champ a `value` et `normalizedValue`.
- Fixture avec `12/05/1990` et formats ambigus → `normalizedValue: null`, `validationStatus: AMBIGUOUS`, brute conservée.
- Fixture avec naissance après délivrance → entrée L3 `DOB_AFTER_ISSUE` dans `validation` (le bloc décision reste `null` jusqu’à V7).

**Projection annoncée** (affichée dans `parsed`, persistée seulement en V8) : clés actuelles de `extracted_identity`, plus `document_code` et `schema_version`.

---

### V6 — MRZ

**Code**

- `MrzEngine` : TD1 (3×30), TD2 (2×36), TD3 (2×44). Format attendu = `mrz_format` de la définition.
- Check digits ICAO 9303, pondération 7-3-1, recalculés. Le chiffre du modèle n’est pas relu.
- Comparaison des `comparisonKey`. Écart → `MISMATCH` / `MRZ_MISMATCH`. Une substitution OCR acceptée seulement si elle rend tous les check digits du bloc valides.
- Schéma `NONE` (le permis Québec du seed) : L4 = `NOT_APPLICABLE`, `mrzScore` sort de la formule. Il faut une fixture passeport (`TD3`) dans les ressources de test pour voir la MRZ dans Swagger avant le V9.

**Swagger**

- `fixtures/passport-td3` (JSON, pas d’image) → `until=mrz`, lignes, parse, chaque check digit, `source: VISUAL_AND_MRZ` ou `MISMATCH`.
- Fixture permis QC → `mrz` présent avec `NOT_APPLICABLE`, pas une erreur.
- Vecteurs ICAO sans image : tests JUnit à 100 %. Le banc ne remplace pas ce gate ; il le rend lisible.

---

### V7 — Scores et décision

**Code**

- `ConfidenceScorer` : formules du cahier §15, avec et sans MRZ. La confiance brute du modèle n’est pas recopiée dans `overallScore`.
- `DocumentValidator` L4–L6 et ordre du §16. Première ligne qui s’applique :
  - illisible → recapture, pas une fraude ;
  - expiré → `REJECT` ;
  - `UNKNOWN` ou classe &lt; 0,80 → `REVIEW` / `DOCUMENT_UNKNOWN` (pas `DECLINED`) ;
  - requis manquant, MRZ en échec, confiance de champ &lt; 0,70, indicateur L6 → `REVIEW` ;
  - `HIGH_CONFIDENCE` sans `ERROR` ni `WARNING` → `PASS` ;
  - sinon `REVIEW` / `LOW_CONFIDENCE`.
- Projection vers `DocumentSignals` + `IdvDecisionEngine` en `rules_version = vision-1`. Le moteur de règles reste le juge. Le signal `ocr_analyze_id` n’est pas posé sur cette chaîne.
- Mapping : `PASS` → `APPROVED`, `REVIEW` → `REVIEW`, `REJECT` → `DECLINED`.

**Swagger**

- Fixture permis QC propre, `until=decide` → `scores.overallScore`, bande, `decision.issue`, `decision.rulesVersion: vision-1`.
- Fixture expirée → `decision.verificationDecision: DECLINED`, raison `document_expired`.
- Fixture `UNKNOWN` → `REVIEW`, pas `DECLINED`.
- Fixture avec `possible_alteration` → `REVIEW`, pas `REJECT`.
- `providerCalled` reste `false` sur les fixtures.

**Hors palier :** brancher `HostedFlowService`. Textract décide encore les vraies sessions live.

---

### V8 — Persistance et bascule live

**Code**

- Table `verification_document_analyses` (cahier §8.2). Écrire seulement un JSON déjà accepté par le parseur.
- `schema_version_id` figé. Une désactivation ultérieure ne réécrit pas la ligne.
- Nouvel adaptateur de `DocumentAiPort` : il appelle le pipeline V3–V7 et projette vers le record existant, `provider = vision_llm`.
- `IdvStoreConfig` : le bean `liveDocumentAi` pointe vers cet adaptateur. `stubDocumentAi` inchangé.
- `HostedFlowService` ne change pas de forme : il appelle toujours `liveDocumentAi` en live et `stubDocumentAi` en test.
- Retirer `TextractAnalyzeIdClient` et `QcAnalyzeIdMapper` du chemin live une fois la démo ci-dessous verte. Les classes peuvent rester le temps d’un commit de comparaison, puis sortent du bean live.

**Swagger, puis une session**

- Le banc `until=decide` sur une image réelle reste vert (non-régression).
- Ensuite seulement : une vérification `ky_live_` permis Québec, flow complet, décision identique au banc, ligne d’analyse en base, zéro appel AnalyzeID.
- Vérification `ky_test_` du même fichier : décision sandbox `m4-1`, zéro appel vision (test du même esprit que `SandboxNoAwsTest`).

**Hors palier :** écran de revue. Les colonnes JSON sont le dossier que cet écran lira plus tard.

---

### V9 — Autres pays

Une ligne `document_definitions` + une version `ACTIVE` + une fixture par code du catalogue initial : `CAMEROON_CNI`, `CAMEROON_PASSPORT`, `CAMEROON_DRIVER_LICENSE`, `FRANCE_CNI`, `FRANCE_PASSPORT`, `CANADA_PASSPORT`. Recto et verso sont deux définitions (même famille, `side` différent).

**Swagger :** `GET /catalog` grandit. `POST /analyze` ou `POST /fixtures/{name}` sur le nouveau code, sans nouveau contrôleur et sans nouveau mapper.

Mesure des objectifs du cahier §19 (classification, extraction, OCR, MRZ de bout en bout) : jeu annoté d’au moins 200 images par code, plus tard. Ce n’est pas un gate de V9. Les gates qui restent à 100 % sont les check digits ICAO et le rejet du JSON invalide.

---

## 7. Fichiers touchés, par palier

| Palier | Où |
|---|---|
| V0 | `controllers/DocumentIaController`, DTO `dto/documentia/`, `KycProperties`, `application.properties` |
| V1 | `services/documentia/DocumentAnalysisParser`, fixtures `src/test/resources/document-ia/` |
| V2 | `db/migration/V24__document_schema_registry.sql`, entités, `SchemaRegistry` |
| V3 | `services/documentia/ImageQualityGate` |
| V4 | `ports/VisionDocumentPort`, adaptateurs fake + fournisseur, `IdvStoreConfig` (beans du banc uniquement) |
| V5 | `FieldNormalizer`, évaluateur L3 |
| V6 | `MrzEngine`, fixture `passport-td3` |
| V7 | `ConfidenceScorer`, `DocumentValidator`, branche `vision-1` de `IdvDecisionEngine` |
| V8 | `V23__verification_document_analyses.sql`, adaptateur `DocumentAiPort`, bascule du bean `liveDocumentAi` |
| V9 | seed Flyway + fixtures |

`HostedFlowService` n’est modifié qu’en V8, et seulement si la projection ne tient pas derrière `DocumentAiPort`. L’objectif est qu’il ne change pas.
