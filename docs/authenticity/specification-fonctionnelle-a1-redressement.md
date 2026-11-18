# Spécification fonctionnelle — A1 Redressement

**Produit :** Recogniz-Me, Identity & Document Verification  
**Version du document :** 1.0  
**Date :** 15 novembre 2026  
**Statut :** palier A1 implémenté  
**Moteur :** `template-1`  
**Documents liés :**

- [`cahier-des-charges-authenticite-visuelle.md`](./cahier-des-charges-authenticite-visuelle.md) — le moteur entier (principe, COCO, verdict)
- [`roadmap-implementation-authenticite-visuelle.md`](./roadmap-implementation-authenticite-visuelle.md) — ordre A0–A6
- [`../DocumentIA/cahier-des-charges-document-ia.md`](../DocumentIA/cahier-des-charges-document-ia.md) — lecture `vision-1`, hors de ce palier

En cas d’écart sur le redressement, ce document fait foi pour A1. Le cahier des charges reste la référence du moteur complet. Les paliers A2 à A6 ne sont pas redéfinis ici.

---

## 1. Objet

Amener la photo d’une carte au **canevas dérivé du rectangle `document` du COCO**, et le montrer dans Swagger.

Deux issues, toutes deux en HTTP 200 :

| Photo | Réponse |
|---|---|
| Une carte a pu être isolée | `rectify.method` (`corners` ou `features`), `rectify.canvas` = le canevas du gabarit. `verdict` reste `null`. |
| Aucune carte n’a pu être isolée | `verdict` = `a_verifier`, `codes` = `["alignment_failed"]`, `rectify` = `null`. |

Le palier est vert quand les deux lignes sont vraies sur le gabarit déposé : la référence du gabarit se redresse, une image sans carte produit `alignment_failed`.

Un redressement réussi constate qu’un quadrilatère acceptable a été mis à plat. Il ne constate pas que la pièce est authentique. `suspect` n’est pas un résultat de ce palier.

## 2. Périmètre

### 2.1 Livré par A1

- Dépendance OpenCV via JavaCV, binaires natifs inclus, sans installation système.
- `TemplateRegistry` : au démarrage, charge `reference` + `annotations.coco.json`, dérive le ratio et le canevas depuis la catégorie `document`.
- `CardRectifier` : coins puis perspective ; à défaut, ORB puis SIFT, homographie RANSAC, même canevas.
- `GET /v1/authenticity/templates` rempli avec le gabarit chargé.
- `POST /v1/authenticity/check` exécute le redressement pour tout `until` (`rectify` et les suivants).
- Schéma Swagger du bloc `rectify`.

### 2.2 Inchangé

- Route, Bearer (`ky_test_` et `ky_live_`), flag `kyc.authenticity.lab-enabled`.
- Flag éteint → HTTP 404. Sans clé → HTTP 401 `unauthorized`.
- `until` hors liste → HTTP 400 `validation_error`, détail `until`.
- Aucune session, aucun crédit, aucun webhook, aucune ligne de vérification.
- `HostedFlowService`, Document IA, biométrie.
- `fixed` et `positions` restent `null`.

### 2.3 Hors A1

Projection des boîtes, scores NCC/SSIM, IoU, verdict `conforme` / `suspect`, image redressée renvoyée dans le JSON, fichier de seuils par édition, OCR.

## 3. État de départ

A0 est livré. `AuthenticityLabService` renvoie une enveloppe vide et une liste de gabarits vide. Le fichier n’est pas décodé : trois octets déclarés JPEG passent en HTTP 200.

Le COCO d’auteur est `docs/authenticity/QUEBEC_DRIVER_LICENSE_2024_FRONT/annotations_coco.json`.

| Mesure | Valeur |
|---|---|
| `images[0]` | `width` 1280, `height` 813, `file_name` `objects3.jpeg` |
| `bbox` `document` | `[287, 185, 729, 457]` |
| Canevas dérivé | **729 × 457** |
| Ratio | 729 / 457 |
| Part de la carte dans la photo | 0,320 |
| Catégories | 1 `document`, 5 `fixed`, 14 `value`, 2 `region` |
| Boîtes | toutes dans l’image ; les boîtes hors `document` sont dans le rectangle de la carte |

`file_name` ne sert pas à ouvrir un fichier. Le rectangle d’exemple `[728, 456]` du cahier des charges illustre la forme du JSON. Pour ce gabarit, le canevas renvoyé est `[729, 457]`.

Au 15 novembre 2026, le dossier d’auteur contient le COCO, `annotations.csv` et `verification_visuelle.png`. Il ne contient pas la photo du spécimen. L’overlay dessiné ne sert pas de référence. Le classpath embarque un `reference.png` géométrique de 1280×813, avec le rectangle `document` au bon endroit, pour que le banc démarre et que les gates de redressement soient mesurables. Cette image se remplace par la photo réelle de mêmes dimensions avant tout palier qui compare le contenu des pixels. Un seul des deux fichiers `reference.jpg` ou `reference.png` doit rester dans le dossier.

## 4. Glossaire

| Terme | Sens dans A1 |
|---|---|
| **Gabarit** | Un dossier : une image de référence et un COCO. |
| **documentId** | Triplet `code:version:side`, identifiant public. Exemple : `QUEBEC_DRIVER_LICENSE:2024:FRONT`. |
| **Canevas** | Taille de sortie du redressement, égale à la largeur et à la hauteur de la `bbox` `document`. |
| **Ratio attendu** | `largeur / hauteur` de cette `bbox`. |
| **Voie `corners`** | Quatre coins d’un quadrilatère, puis perspective. |
| **Voie `features`** | Points ORB ou SIFT, homographie RANSAC, puis la même perspective. |
| **Référence** | Spécimen du gabarit. Ce n’est pas la carte d’un dossier. |

## 5. Registre des gabarits

### 5.1 Fichiers

L’exécution lit le classpath, pas `docs/`.

```
api/src/main/resources/authenticity/{dossier}/
  reference.jpg     ou reference.png, un seul des deux
  annotations.coco.json
```

Le COCO d’auteur `annotations_coco.json` est copié sous le nom `annotations.coco.json`. Un dossier du classpath sans ces deux fichiers, ou avec les deux images de référence, empêche le démarrage.

Le dossier du premier gabarit est `QUEBEC_DRIVER_LICENSE_2024_FRONT`.

### 5.2 Identifiant

Le nom de dossier ne contient pas de `:`. Il se lit depuis la droite :

1. Dernier segment, séparé par `_` : `side`, égal à `FRONT` ou `BACK`.
2. Segment précédent : `version`, non vide.
3. Segments restants, réunis par `_` : `code`, non vide.
4. `documentId` = `code + ":" + version + ":" + side`.

| Dossier | documentId |
|---|---|
| `QUEBEC_DRIVER_LICENSE_2024_FRONT` | `QUEBEC_DRIVER_LICENSE:2024:FRONT` |

La recherche à l’appel est le `documentId` complet, après suppression des espaces de bord, sensible à la casse.

Un classpath `authenticity/` vide est valide : la liste est vide, et tout `documentId` répond `template_not_found`. Dès qu’un dossier est présent, il est valide ou l’application ne démarre pas.

### 5.3 Contrôle du COCO au démarrage

Le registre refuse le contexte Spring en nommant le dossier et la règle cassée.

| Règle | Exigence |
|---|---|
| Image COCO | Une seule entrée `images[]`. |
| Spécimen | Décode en JPEG ou PNG. Largeur et hauteur égales à `images[0]`. L’orientation EXIF du spécimen est `1` ou absente. |
| `file_name` | Ignoré pour l’ouverture. |
| Catégorie `document` | Une seule, et une seule annotation. |
| Rôles | `document`, `fixed`, `value`, `region`. |
| Annotations | Une par catégorie, `bbox` de quatre nombres, largeur et hauteur positives. |
| Emprise | Chaque `bbox` est dans l’image (`x + largeur ≤ width`, `y + hauteur ≤ height`). |
| Carte | Chaque boîte dont le rôle n’est pas `document` est dans le rectangle `document`. |
| Attributs | Présents seulement sur `value`. `bbox_value` et `bbox_label` sont `null` ou un rectangle inclus dans la `bbox`. |

`segmentation` et `iscrowd` ne participent pas au canevas. Le rectangle utilisé est `bbox`.

### 5.4 Canevas et ratio

Rien n’est écrit dans le dossier du gabarit.

```
ratio   = document.largeur / document.hauteur
canevas = [document.largeur, document.hauteur]
```

Les deux nombres du canevas sont les entiers du COCO. Le facteur d’échelle du moteur vaut `canvasScale = 1` : la sortie a exactement cette taille. Le même facteur vaut pour tous les gabarits.

Pour le permis déposé : ratio `729 / 457`, canevas `[729, 457]`.

### 5.5 Liste Swagger

`GET /v1/authenticity/templates`, triée par `documentId`.

```json
{
  "templates": [
    {
      "documentId": "QUEBEC_DRIVER_LICENSE:2024:FRONT",
      "code": "QUEBEC_DRIVER_LICENSE",
      "version": "2024",
      "side": "FRONT",
      "imageWidth": 1280,
      "imageHeight": 813,
      "canvasWidth": 729,
      "canvasHeight": 457,
      "fixedCount": 5,
      "fieldCount": 16
    }
  ]
}
```

`fixedCount` compte les catégories `fixed`. `fieldCount` compte les catégories `value` et `region`. Les `bbox_label` ne sont pas comptés ici ; ils seront mesurés au palier des éléments fixes.

## 6. Contrat de `POST /v1/authenticity/check`

`multipart`, champ `file`. Query `documentId` (obligatoire) et `until` (défaut `decide`).

L’ordre des refus suit le cahier :

| Ordre | Condition | HTTP | Code |
|---|---|---|---|
| 1 | Fichier absent, vide, type hors JPEG/PNG, ou octets illisibles | 422 | `image_rejected` |
| 2 | `documentId` vide ou absent du registre | 422 | `template_not_found` |
| 3 | `until` hors `rectify`, `project`, `fixed`, `positions`, `decide` | 400 | `validation_error` |

Le type accepté est `image/jpeg` ou `image/png`, et les octets doivent se décoder. Un corps qui ne se décode pas est `image_rejected`, y compris les trois octets qui passaient en A0.

Le redressement s’exécute pour toutes les valeurs de `until`, parce qu’il est la première étape. Les blocs `fixed` et `positions` restent `null`. `stoppedAt` reste la valeur demandée.

### 6.1 Carte isolée

HTTP 200. `verdict` = `null`. `codes` = `[]`.

```json
{
  "pipeline": "template-1",
  "stoppedAt": "rectify",
  "documentId": "QUEBEC_DRIVER_LICENSE:2024:FRONT",
  "verdict": null,
  "codes": [],
  "rectify": { "method": "corners", "canvas": [729, 457] },
  "fixed": null,
  "positions": null
}
```

| Champ | Contenu |
|---|---|
| `rectify.method` | `corners` ou `features` |
| `rectify.canvas` | `[largeur, hauteur]` du canevas dérivé, ici `[729, 457]` |

`method` vaut `corners` lorsque la voie des coins a abouti. Elle vaut `features` lorsque seuls ORB ou SIFT ont abouti. Le nom du détecteur (ORB ou SIFT) reste dans le journal.

Le JSON ne contient pas l’image redressée. Elle existe en mémoire le temps de la requête, en couleur, à la taille du canevas, pour les paliers suivants. Elle n’est ni journalisée, ni écrite sur disque, ni enregistrée en base.

Même forme si `until` vaut `project`, `fixed`, `positions` ou `decide` : `rectify` est rempli, le verdict reste `null`, les blocs suivants restent `null`. A1 ne fabrique pas ces blocs.

### 6.2 Carte non isolée

HTTP 200. Les deux voies ont échoué.

```json
{
  "pipeline": "template-1",
  "stoppedAt": "rectify",
  "documentId": "QUEBEC_DRIVER_LICENSE:2024:FRONT",
  "verdict": "a_verifier",
  "codes": ["alignment_failed"],
  "rectify": null,
  "fixed": null,
  "positions": null
}
```

`codes` contient exactement `alignment_failed`. Le verdict n’est pas `suspect`. Le statut HTTP n’est pas 4xx. Cette réponse est la même pour tout `until`, y compris `decide` : l’échec d’alignement pose déjà `a_verifier`, et les blocs après l’échec restent `null`.

### 6.3 Ce que le banc ne conclut pas

Une autre carte dont le quadrilatère tombe dans la tolérance de ratio peut se redresser. Le permis déposé a un ratio de 1,595 ; une carte ID-1 (85,60 / 53,98) s’en écarte d’environ 0,6 %, donc sous `aspectTolerance`. A1 la met à plat et laisse `verdict` à `null`. La décision sur la mise en page appartient aux paliers suivants.

## 7. Redressement

```
octets + documentId
  → valider l’image
  → TemplateRegistry
  → redresser l’orientation EXIF
  → réduire pour la détection si le grand côté dépasse detectLongSide
  → voie corners
  → si elle échoue : voie features (ORB, puis SIFT)
  → perspective vers le canevas
  → si les deux voies échouent : alignment_failed
```

La voie `corners` est tentée en premier. La voie `features` ne l’est que si aucun quadrilatère n’a convenu.

### 7.1 Préparer la photo

1. Décoder le JPEG ou le PNG. Jeter le canal alpha. Travailler en RVB.
2. Appliquer l’orientation EXIF 2 à 8 avant toute détection, pour que les coins soient ceux de l’image debout. L’orientation `1` ou absente laisse les pixels tels quels.
3. Si le grand côté dépasse `detectLongSide` (1600), la détection (contours et points) travaille sur une copie dont le grand côté vaut 1600, proportions gardées. Les coins trouvés sont ramenés à l’image debout pleine résolution. La perspective finale part de cette image pleine. Le canevas de sortie ne change pas.

### 7.2 Voie `corners`

1. Passer en niveaux de gris et extraire le plus grand contour fermé qui s’approxime en quatre sommets.
2. Garder les quadrilatères convexes.
3. Ordonner les sommets, de façon stable : haut-gauche = plus petit `x + y`, bas-droite = plus grand `x + y`, haut-droite = plus grand `x − y`, bas-gauche = plus petit `x − y`.
4. Largeur mesurée = moyenne des longueurs des côtés haut et bas. Hauteur mesurée = moyenne des longueurs des côtés gauche et droit. Le ratio mesuré est `largeur / hauteur`.
5. Accepter le quadrilatère lorsque `|ratio mesuré − ratio attendu| / ratio attendu ≤ aspectTolerance` (`0,01`) et que son aire couvre au moins `minCardAreaRatio` (`0,08`) de l’image de détection.
6. S’il en reste plusieurs, garder la plus grande aire.

`aspectTolerance` se mesure sur ce quadrilatère, avant la mise à plat. Elle ne s’applique pas à la voie `features` : une photo penchée change le ratio apparent, et c’est cette voie-là qui doit alors aboutir.

### 7.3 Perspective

Les quatre coins ordonnés vont vers le rectangle du canevas :

| Sommet source | Destination |
|---|---|
| haut-gauche | `(0, 0)` |
| haut-droite | `(largeur − 1, 0)` |
| bas-droite | `(largeur − 1, hauteur − 1)` |
| bas-gauche | `(0, hauteur − 1)` |

L’image produite mesure exactement `largeur × hauteur` pixels du canevas. Interpolation linéaire. Le bord hors source est le pixel le plus proche (`BORDER_REPLICATE`).

### 7.4 Voie `features`

Comparaison avec l’image de référence entière, pas avec le seul crop de la carte.

1. Détecter et décrire les points sur l’image à vérifier et sur la référence. ORB d’abord. Si ORB ne donne pas d’homographie acceptable, SIFT. Les deux s’inscrivent `method = features`.
2. Appariement avec le test de Lowe : un appariement est gardé lorsque la distance du meilleur voisin est inférieure à `loweRatio` (`0,75`) fois celle du second.
3. Estimer l’homographie par RANSAC. Elle envoie un point de la photo vers un point de la référence.
4. Accepter l’homographie lorsqu’elle satisfait toutes les lignes suivantes.
   - Au moins `minInliers` (12) points restés.
   - Ces points forment au moins `minInlierRatio` (`0,40`) des appariements de Lowe.
   - La partie linéaire 2×2 n’est pas dégénérée.
   - Les quatre coins du rectangle `document` de la référence, ramenés dans la photo par l’homographie inverse, forment un quadrilatère convexe.
   - L’aire de ce quadrilatère couvre au moins `minCardAreaRatio` de la photo.
   - Chaque coin peut sortir de la photo d’au plus `quadOverflow` (`0,05`) de la largeur ou de la hauteur, selon l’axe. Au-delà, l’homographie est refusée.
5. La perspective du §7.3 part de ces quatre coins et vise le même canevas que la voie `corners`.

Si ORB et SIFT échouent, le résultat est `alignment_failed`.

### 7.5 Constantes

Un seul jeu, dans le code du moteur, commun à tous les gabarits. Aucune de ces valeurs ne vit dans le dossier du gabarit.

| Constante | Valeur | Rôle |
|---|---|---|
| `canvasScale` | 1 | Taille de sortie = `bbox` `document`. |
| `aspectTolerance` | 0,01 | Écart relatif de ratio, voie `corners` seulement. |
| `minCardAreaRatio` | 0,08 | Aire minimale du quadrilatère, rapportée à l’image de détection. |
| `detectLongSide` | 1600 | Grand côté maximal de l’image de détection. |
| `loweRatio` | 0,75 | Seuil du test de Lowe. |
| `minInliers` | 12 | Points RANSAC minimaux. |
| `minInlierRatio` | 0,40 | Part minimale d’appariements conservés. |
| `ransacReprojPx` | 3 | Seuil de reprojection, en pixels de l’image de détection. |
| `quadOverflow` | 0,05 | Dépassement relatif autorisé des coins ramenés dans la photo. |
| `rngSeed` | 1 | Graine OpenCV, posée pour chaque redressement. |

`similarityPass`, `iouPass` et les autres seuils du cahier §6.3 ne sont pas lus en A1.

## 8. Règles de gestion

| Id | Règle |
|---|---|
| RG-A1-01 | Le canevas de la réponse est celui du gabarit demandé. Il ne dépend pas de la taille de la photo envoyée. |
| RG-A1-02 | La voie retenue est la première qui aboutit : `corners`, sinon `features`. |
| RG-A1-03 | Les deux voies visent le même rectangle de destination. |
| RG-A1-04 | Échec des deux voies → HTTP 200, `verdict` `a_verifier`, code `alignment_failed`, `rectify` `null`. |
| RG-A1-05 | Réussite → `verdict` `null`, `codes` vide, quel que soit `until`. |
| RG-A1-06 | `aspectTolerance` filtre seulement les quadrilatères de la voie `corners`. |
| RG-A1-07 | Deux appels avec les mêmes octets et le même `documentId` produisent le même JSON. |
| RG-A1-08 | Une requête concurrente ne change pas le JSON d’une autre. La graine et l’appel natif sont pris sous la même exclusion. |
| RG-A1-09 | L’image, redressée ou non, n’entre ni dans les logs, ni en base, ni dans la réponse. |
| RG-A1-10 | Le banc ne crée aucune vérification. |
| RG-A1-11 | Un COCO invalide ou un spécimen dont la taille diffère du COCO empêche le démarrage. |
| RG-A1-12 | Le moteur ne contient aucune branche nommée d’après le permis, le pays ou la face. Le gabarit suivant est un dossier de plus. |

## 9. Journal et durée

Une ligne par appel réussi ou par `alignment_failed` : `documentId`, `until`, `method` (`corners`, `features` ou vide), détecteur (`orb`, `sift` ou vide), `verdict`, `codes`, `durationMs`.

Les refus 401, 404 et 422 gardent le journal déjà en place pour ces statuts. Aucun pixel, aucun base64, aucun chemin de fichier temporaire.

OpenCV est chargé au démarrage avec le registre. Un natif absent empêche le démarrage. Le premier appel ne découvre pas l’absence de la bibliothèque.

Les matrices natives sont libérées avant la fin de la requête.

## 10. Critères d’acceptation

Gates du palier. La référence déposée (1280 × 813) est la photo « avec carte ». L’image sans carte est produite dans le test : rectangle 640 × 400, tous les pixels à 180, encodé en JPEG.

| Id | Cas | Résultat |
|---|---|---|
| G1 | `GET /templates` avec Bearer | Un gabarit, canevas 729 × 457, `fixedCount` 5, `fieldCount` 16. |
| G2 | `until=rectify`, fichier = `reference.jpg` | HTTP 200, `method` ∈ {`corners`, `features`}, `canvas` = `[729, 457]`, `verdict` `null`, `codes` vide. |
| G3 | La même référence, mesurée hors JSON | L’image redressée, comparée au crop `document` de la référence ramené au canevas, a une NCC en niveaux de gris ≥ 0,95. |
| G4 | Référence déformée par une perspective connue (coins déplacés d’environ 8 % du côté, fond gris 180), générée dans le test | HTTP 200, même canevas, `method` ∈ {`corners`, `features`}, NCC ≥ 0,85 contre le crop `document`. |
| G5 | JPEG gris uniforme | HTTP 200, `verdict` `a_verifier`, `codes` = `["alignment_failed"]`, `rectify` `null`. Le mot `suspect` est absent. |
| G6 | `documentId` inconnu, JPEG valide | HTTP 422 `template_not_found`. |
| G7 | Fichier absent, vide, ou octets non décodables | HTTP 422 `image_rejected`. |
| G8 | Sans clé | HTTP 401. Flag éteint → HTTP 404. |
| G9 | Après l’appel | Le nombre de vérifications en base est inchangé. |
| G10 | Deux appels G2 | JSON identique. |

G3 et G4 s’évaluent dans le test, en appelant le redresseur. Ils ne modifient pas le JSON public.

NCC : corrélation croisée normalisée, moyenne retirée, sur les niveaux de gris du canevas entier. 1 est une copie fidèle.

### Assertions A0 à réécrire

| Test A0 | Devient |
|---|---|
| Liste de gabarits vide | G1 |
| Trois octets JPEG → enveloppe 200, `rectify` `null` | G7 |
| `until=rectify` sur ces trois octets → 200 | G7 |
| Enveloppe `decide` avec `rectify` `null` pour un JPEG réel du gabarit | `rectify` rempli, `verdict` `null`, `stoppedAt` `decide` |

Restent verts sans changement de sens : clé absente → 401, `documentId` blanc → 422 `template_not_found`, `until` inconnu → 400, clé `ky_test_` acceptée, flag éteint → 404.

### Démo Swagger

1. `mvn spring-boot:run` dans `api/`.
2. `http://localhost:8080/swagger-ui.html`, Authorize, clé sans le mot Bearer.
3. `GET /v1/authenticity/templates` : la ligne du permis, canevas 729 × 457.
4. `POST /v1/authenticity/check`, `until=rectify`, `documentId` `QUEBEC_DRIVER_LICENSE:2024:FRONT`, fichier = la référence : `rectify.method` et `rectify.canvas` = `[729, 457]`.
5. Le même appel avec le JPEG gris : `verdict` `a_verifier`, code `alignment_failed`.

## 11. Dépendance

`org.bytedeco:javacv-platform`, version épinglée dans `api/pom.xml`, livraison OpenCV 4 qui expose les contours, `warpPerspective`, ORB, SIFT et `findHomography`.

Les binaires natifs viennent avec l’artefact. Le développeur Windows et la CI n’installent pas OpenCV à part. Le moteur n’appelle pas d’autre bibliothèque native pour ce palier, et n’ouvre aucune connexion réseau pour redresser.

## 12. Arrêt

Le palier n’est pas accepté si l’une de ces situations est vraie.

- La réponse d’une photo sans carte est `suspect`, un code `FAIL`, ou une erreur HTTP.
- Le canevas renvoyé est une constante écrite pour le permis, différente de la `bbox` `document`.
- Un `template.json`, un ratio ou un seuil est enregistré dans le dossier du gabarit.
- L’overlay `verification_visuelle.png` est chargé comme spécimen.
- Le banc écrit une vérification, un fichier image ou un score en base.
- Une classe du redresseur est nommée d’après une pièce, un pays ou une face.
