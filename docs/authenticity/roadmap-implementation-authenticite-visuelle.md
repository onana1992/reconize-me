# Roadmap d’implémentation — Authenticité visuelle par gabarit

**Produit :** Recogniz-Me, Identity & Document Verification  
**Version du document :** 1.0  
**Date :** 15 novembre 2026  
**Statut :** A0 et A1 livrés  
**Moteur :** `template-1`  
**Document lié :** [`cahier-des-charges-authenticite-visuelle.md`](./cahier-des-charges-authenticite-visuelle.md) — le *quoi* (principe, COCO, contrôles, verdict)

Ce document dit **dans quel ordre** construire le module. Un palier n’est pas vert tant que son résultat n’est pas visible dans Swagger, sur le banc isolé, sans ouvrir une session de vérification.

Document IA, la biométrie et le parcours `HostedFlowService` ne bougent pas avant le dernier palier.

---

## 1. Cible

**Happy path du banc :** dans Swagger, envoyer l’image d’une pièce et le `documentId` d’un gabarit déposé, puis lire dans la même réponse le redressement, les scores des éléments fixes, les positions et le verdict `conforme` / `a_verifier` / `suspect`. Aucune vérification n’est créée. Aucun selfie. Aucun débit. Aucun webhook. Aucun OCR. Aucun appel réseau.

**Happy path produit (dernier palier) :** après la classification Document IA, la session live exécute le même moteur ; un `suspect` refuse le dossier ; un `conforme` fige `authenticity_version = template-1` et le `documentId` sur la session.

---

## 2. Pourquoi un banc, pas le flow

Le contrôle d’authenticité n’existe pas encore dans le parcours. Le brancher trop tôt empêcherait de voir un palier intermédiaire (redressement seul, positions seules).

Le banc est un contrôleur à part. Il appelle le pipeline `template-1` et renvoie le JSON. Il ne passe pas par `VerificationService`.

| | Banc Swagger | Session live (A6 seulement) |
|---|---|---|
| Route | `/v1/authenticity/**` | flux existant, après la classification |
| Auth | `Authorization: Bearer` (`ky_test_` ou `ky_live_`) | token d’URL du flow |
| Effet de bord | aucun | décision, signaux, version figée |
| OCR / vision LLM | jamais | Document IA reste le lecteur |
| Réseau | aucun | aucun pour `template-1` |

`/v1/authenticity/**` n’est pas ajouté à la liste `PUBLIC`. Le filtre Bearer existant suffit. Swagger a déjà le scheme `bearer-api-key`.

Flag : `kyc.authenticity.lab-enabled` (défaut `true` en local, `false` hors développement). Flag éteint → **404**.

---

## 3. Ce que Swagger montre à chaque palier

Une seule réponse, qui se remplit. Les blocs pas encore construits valent `null`. Le query `until` arrête le pipeline au bloc demandé.

`POST /v1/authenticity/check`

```json
{
  "pipeline": "template-1",
  "stoppedAt": "rectify",
  "documentId": "CODE:VERSION:SIDE",
  "verdict": null,
  "codes": [],
  "rectify": null,
  "fixed": null,
  "positions": null
}
```

| Palier | `until` | Bloc qui devient non null |
|---|---|---|
| A0 | — | enveloppe seule ; `GET /templates` liste les gabarits |
| A1 | `rectify` | `rectify` (canevas dérivé du COCO, voie `corners` ou `features`) |
| A2 | `project` | boîtes projetées visibles dans `rectify` ou un bloc `project` |
| A3 | `fixed` | `fixed` (scores NCC/SSIM) |
| A4 | `positions` | `positions` (IoU, écart px) |
| A5 | `decide` | `verdict` + `codes` |
| A6 | — | branchement session live ; le banc reste |

Deux opérations, stables du premier au dernier palier :

| Méthode | Rôle | Réseau |
|---|---|---|
| `GET /v1/authenticity/templates` | gabarits chargés (référence + COCO) | non |
| `POST /v1/authenticity/check` | `multipart` champ `file`, query `documentId` + `until` | non |

Procédure, à chaque palier :

1. `mvn spring-boot:run` dans `api/`.
2. Ouvrir `http://localhost:8080/swagger-ui.html`.
3. **Authorize**, coller une clé API (sans le mot Bearer : Swagger l’ajoute).
4. Exécuter l’opération du palier. La réponse **est** le livrable.

Aucune image dans les logs : journaliser `documentId`, durée, voie d’alignement, verdict, codes et scores.

---

## 4. Principes d’ordre

1. **Banc avant pipeline.** A0 est le premier commit utile.
2. **Gabarit = référence + COCO.** Pas de `template.json`. Ratio et canevas dérivés de `images[].width/height` et de la `bbox` `document`.
3. **Règles générales.** Seuils NCC/IoU/marge = constantes `template-1`, communes à toutes les pièces.
4. **Deux contrôles seulement.** Ressemblance des éléments fixes, puis positions. Pas d’OCR.
5. **Le flow ne bouge pas avant A6.** `HostedFlowService` reste inchangé tant que le banc `until=decide` n’est pas vert sur le gabarit déposé.
6. **Une édition de plus = un dossier de plus** sous `classpath:authenticity/{documentId}/`. Pas une classe Java.

**Arrêt (kill) :** banc sans auth ou flag oublié hors dev ; image écrite en base depuis le banc ; un `suspect` transformé en approbation ; OCR dans `template-1` ; `template.json` ou ratio enregistré par édition ; branche nommée d’après une pièce.

---

## 5. Vue d’ensemble

```
A0 banc Swagger (enveloppe, Bearer, flag, GET /templates)
        │
        ▼
A1 CardRectifier (coins → perspective ; sinon ORB/SIFT)
        │
        ▼
A2 BoxProjector (boîtes COCO sur le canevas dérivé)
        │
        ▼
A3 FixedMatcher (NCC/SSIM, constantes globales)
        │
        ▼
A4 PositionMeasurer (IoU / écart px)
        │
        ▼
A5 AuthenticityVerdict (conforme / a_verifier / suspect)
        │
        ▼
A6 branchement HostedFlowService (après classification)
```

Premier gabarit de preuve : `QUEBEC_DRIVER_LICENSE:2024:FRONT` (`docs/authenticity/QUEBEC_DRIVER_LICENSE_2024_FRONT/` → `classpath:authenticity/...`). Le moteur qui le fait passer est le même pour le gabarit suivant.

---

## 6. Paliers

### A0 — Banc Swagger — **livré**

**But :** joindre le module sans logique image.

**Livrable Swagger**

- `GET /v1/authenticity/templates` → liste (peut être vide ou déjà le gabarit Québec si le registre stub le voit).
- `POST /v1/authenticity/check` → enveloppe `pipeline: template-1`, blocs à `null`.
- Sans clé → **401**. Flag éteint → **404**.

**Fait quand :** les deux routes répondent ; aucune session créée.

**As-built :** `AuthenticityController` `/v1/authenticity/**`, flag `kyc.authenticity.lab-enabled`, enveloppe `AuthenticityCheckResponse`, liste vide jusqu’à A1.

---

### A1 — Redressement

**Contrat :** [`specification-fonctionnelle-a1-redressement.md`](./specification-fonctionnelle-a1-redressement.md)

**But :** carte au canevas dérivé du COCO.

**À construire**

- Dépendance OpenCV / JavaCV.
- `TemplateRegistry` : charge `reference` + `annotations.coco.json`, dérive ratio et canevas depuis `document`.
- `CardRectifier` : coins + perspective ; fallback ORB/SIFT + RANSAC.

**Livrable Swagger** (`until=rectify`)

- Photo avec carte → `rectify.method`, `rectify.canvas`, pas de `FAIL`/`suspect`.
- Photo sans carte → `verdict: a_verifier`, code `alignment_failed`.

**Fait quand :** référence du gabarit se redresse ; image sans carte → `alignment_failed`.

**As-built :** `TemplateRegistry` et `CardRectifier`. OpenCV via JavaCV. `until=rectify` renvoie `method` et `canvas` `[729, 457]`. Image grise → `a_verifier` / `alignment_failed`. Le `reference.png` du classpath est un spécimen géométrique 1280×813 : le remplacer par la photo réelle avant les paliers qui comparent les pixels.

---

### A2 — Projection des boîtes

**But :** chaque boîte COCO a un rectangle sur le canevas.

**À construire**

- `BoxProjector` : ramener à `document`, mettre à l’échelle.

**Livrable Swagger** (`until=project`)

- Liste des boîtes projetées (nom, rôle, rectangle canevas) pour le gabarit chargé.

**Fait quand :** le nombre de boîtes projetées = annotations non `document` du COCO ; toutes dans le canevas.

**As-built :** `BoxProjector`. Chaque boîte hors `document` est translatée sur le rectangle de la carte, puis mise à l’échelle du canevas. `until=project` renvoie nom, rôle et rectangle. `until=rectify` laisse `project` à `null`. Alignement impossible : `project` reste `null`.

---

### A3 — Éléments fixes

**But :** score de ressemblance par marque et par libellé.

**À construire**

- `FixedMatcher` (NCC ou SSIM selon constante globale).
- Constantes §6.3 du cahier.

**Livrable Swagger** (`until=fixed`)

- Chaque `fixed` / `bbox_label` : `score`, `shiftPx`, `status`.
- Référence vs elle-même → scores hauts.
- Marque masquée (fixture de test) → sous `similaritySuspect`.

**Fait quand :** référence auto-comparée passe ; marque absente tombe en `suspect` sur cet élément.

**As-built :** `FixedMatcher`. NCC, seuils 0,75 / 0,50, marge 15 px. `until=fixed` score chaque catégorie `fixed` et chaque `bbox_label`. La référence comparée à elle-même est `conforme` sur chaque élément. Une marque masquée passe sous 0,50 et devient `suspect` ; les autres mesures restent là. Le verdict global reste `null` jusqu'à `decide`.

---

### A4 — Positions

**But :** IoU et écart en pixels pour `value` et `region`.

**À construire**

- `PositionMeasurer` : détection d’encre / contenu, IoU, `maxShiftPx`.

**Livrable Swagger** (`until=positions`)

- Chaque champ : `iou`, `deltaPx`, `status`.
- Champ déplacé synthétiquement ≥ 35 px → `suspect`.

**Fait quand :** référence auto-comparée conforme sur les positions ; déplacement synthétique détecté.

**As-built :** `PositionMeasurer`. Pour chaque `value` et chaque `region`, la boîte attendue est `bbox_value` si elle existe, sinon `bbox`. La fenêtre dépasse cette boîte de 35 px. Le rectangle retenu est celui de l'encre (contenu visible pour une région), pas les pixels de la référence. Une encre plus courte ou plus longue reste `conforme` si elle part de la boîte et que sa hauteur ne s'écarte pas de 35 px. Un décalage du départ de ligne ou de la hauteur d'au moins 35 px est `suspect`. Aucun contenu donne `suspect` / `field_absent`. Une fenêtre sans contraste donne `a_verifier` / `zone_unmeasured`. `until=positions` laisse le verdict `null`. L'alignement par points masque ces mêmes champs sur la référence.

---

### A5 — Verdict

**But :** décision globale selon l’ordre du cahier §11.

**À construire**

- `AuthenticityVerdict`.
- Remplir `verdict` et `codes` ; `until=decide` par défaut.

**Livrable Swagger** (`until=decide`)

- Référence vs elle-même → `conforme`.
- Cas d’échec préparés → `suspect` ou `a_verifier` selon la table.

**Fait quand :** les gates du cahier §14 sont verts sur le banc. Latence journalisée (`durationMs`) ; objectif indicatif p95 &lt; 500 ms.

**As-built :** `AuthenticityVerdict`. `until=decide` pose le verdict après les mesures : un élément `suspect` donne `suspect`, sinon un `a_verifier`, sinon `conforme`. Les codes globaux sont ceux des éléments qui ont tranché (`field_absent`, `zone_unmeasured`). L'alignement manqué reste `a_verifier` / `alignment_failed` quel que soit `until`. Avant `decide`, le verdict reste `null`. La référence du permis comparée à elle-même est `conforme`. Une marque masquée donne `suspect`, les autres mesures restent là.

---

### A6 — Branchement parcours

**But :** après classification Document IA, appeler `template-1` avec le triplet `code:version:side`.

**À construire**

- Appel depuis `HostedFlowService` (ou service dédié) une fois la classe connue.
- `suspect` → `DECLINED` / `authenticity_failed` (non contournable par revue).
- `conforme` → figer `authenticity_version` + `documentId`.
- `a_verifier` (alignement / zone) → recapture.
- Classe sans gabarit → `authenticity_unsupported`, pas d’approbation.
- Le banc Swagger **reste**.

**Fait quand :** une session live avec gabarit déposé produit le même verdict que le banc sur la même image ; une session sans gabarit pour la classe ne s’approuve pas.

**As-built :** `AuthenticitySession`, appelée depuis `HostedFlowService` sur le recto live une fois la classe connue, seulement si `kyc.authenticity.lab-enabled` est vrai. `conforme` fige `authenticity_version = template-1` et le `documentId`. `suspect` devient `DECLINED` / `authenticity_failed` avant le visage, et un analyste ne peut pas approuver. `alignment_failed` ou `zone_unmeasured` redemandent la capture tant qu'il reste un essai. Une classe de recto sans gabarit donne `unsupported` et bloque l'approbation. Le verso est mesuré seulement si son gabarit est chargé ; sinon il est ignoré. Le pire verdict des faces mesurées est conservé. Le banc Swagger reste.

**Hors A6 :** détection écran/photocopie ; verso / autres éditions (dossiers COCO supplémentaires seulement).

---

## 7. Ordre des dépendances

| Palier | Dépend de | Débloque |
|---|---|---|
| A0 | rien | tout le reste |
| A1 | A0, OpenCV, 1er COCO | A2–A5 |
| A2 | A1 | A3, A4 |
| A3 | A2 | A5 |
| A4 | A2 | A5 |
| A5 | A3 + A4 | A6 |
| A6 | A5 vert + classification Document IA | conformité CANAFE côté authenticité visuelle |

A3 et A4 peuvent avancer en parallèle après A2.

---

## 8. Démo à chaque palier (checklist)

À la fin de chaque palier, cocher avant de passer au suivant.

| Palier | Démo |
|---|---|
| A0 | Swagger : Authorize → `GET /templates` → `POST /check` enveloppe `null` ; sans clé → 401 |
| A1 | Image OK → `rectify.canvas` ; sans carte → `alignment_failed` |
| A2 | Nombre de boîtes projetées = COCO |
| A3 | Auto-comparaison scores hauts ; marque masquée → score bas |
| A4 | Auto-comparaison IoU haut ; champ décalé → `suspect` |
| A5 | `decide` → `conforme` sur la référence ; cas d’échec → `suspect` / `a_verifier` |
| A6 | Session live : même verdict que le banc ; `suspect` non approuvable |

---

## 9. Hors roadmap (reporté)

| Sujet | Quand |
|---|---|
| OCR / règles de contenu dans ce moteur | Jamais dans `template-1` ; reste Document IA |
| `template.json` par édition | Jamais ; règles globales seulement |
| Écran, photocopie, photo d’une photo | Contrôle distinct ultérieur |
| Autres éditions / verso | Dossier COCO + référence de plus, même moteur |
| Écran opérateur dédié authenticité | Après persistance en session |
| Recalibrage des seuils sur grand jeu | Avant d’élargir le catalogue en production |
