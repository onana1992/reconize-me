# Cahier des charges — Authenticité visuelle par gabarit

**Produit :** Recogniz-Me, Identity & Document Verification  
**Version du document :** 1.3  
**Date :** 15 novembre 2026  
**Statut :** spécification d’implémentation  
**Moteur :** `template-1`  
**Documents liés :**

- [`roadmap-implementation-authenticite-visuelle.md`](./roadmap-implementation-authenticite-visuelle.md) — ordre de construction (paliers A0–A6)
- [`specification-fonctionnelle-a1-redressement.md`](./specification-fonctionnelle-a1-redressement.md) — contrat du palier A1 (registre, canevas, deux voies)
- [`../DocumentIA/cahier-des-charges-document-ia.md`](../DocumentIA/cahier-des-charges-document-ia.md) — lecture et classification `vision-1`
- [`../DocumentIA/roadmap-implementation-document-ia.md`](../DocumentIA/roadmap-implementation-document-ia.md) — le banc Swagger isolé, modèle de livraison
- [`../specs/todo-conformite-canafe-idv.md`](../specs/todo-conformite-canafe-idv.md) — une approbation exige une authenticité évaluée et acceptée

---

## 1. Objet

Dire si une photo de pièce d’identité porte la mise en page d’une édition connue.

Le moteur compare la carte à vérifier à un **gabarit** : l’image de référence de cette édition et son fichier COCO, qui indique où se trouve chaque élément. Le même principe vaut pour toute pièce. Une édition de plus est un gabarit de plus. Aucune branche du moteur ne nomme un pays, un type ou une face.

Les règles de mesure sont générales : un seul jeu de constantes `template-1` s’applique à tous les gabarits. Il ne lit pas le texte des champs variables. Il ne décide pas que le titulaire est la personne filmée. Il ne remplace pas `vision-1`.

## 2. Deux temps

Le moteur se construit d’abord seul, visible dans Swagger. Il rejoint le parcours de vérification quand le banc tranche sur un gabarit déposé.

| Temps | Où | Entrée | Effet |
|---|---|---|---|
| 1. Banc | `POST /v1/authenticity/check` | Image + identifiant de gabarit | JSON du contrôle. Aucune session. |
| 2. Parcours | Après la classification Document IA, dans `HostedFlowService` | Image déjà acceptée + `code`, `version`, `side` | Le verdict entre dans la décision. Hors de ce chantier. |

Le temps 2 est spécifié pour que le banc ait déjà la forme que le parcours consommera. Il n’est pas livré avec le banc.

## 3. Périmètre

**Dans ce chantier**

- Redressement, projection des boîtes COCO, ressemblance des éléments fixes, position des champs, verdict.
- Un gabarit par édition et par face = une image de référence + un fichier COCO. Pas de fichier de config par pièce. Pas une classe Java.
- Un jeu de règles de mesure commun à toutes les pièces (constantes du moteur `template-1`).
- Banc Swagger, même discipline que `/v1/document-ia` : clé Bearer, flag, aucun dossier créé.

**Hors de ce chantier**

| Sujet | Sort |
|---|---|
| Classification du document | Document IA. Sur le banc, l’appelant nomme le gabarit. Au temps 2, la classe le choisit. |
| Lecture OCR, format et cohérence des champs | Document IA (`vision-1`). `template-1` ne lit pas les valeurs. |
| Branchement dans `HostedFlowService`, signaux, webhook, crédits | Temps 2, après un banc vert. |
| Police, chasse, forme des glyphes | Hors `template-1`. |
| Code-barres, MRZ | Document IA. |
| Écran, photocopie, photo d’une photo | Contrôle distinct. Une mise en page juste ne prouve pas que le support est une carte. |
| Modèle qui juge la pièce en la « regardant » | `template-1` mesure contre le gabarit. |
| Fichier de seuils ou de ratio par édition | Interdit. Les seuils sont globaux ; le ratio se déduit du COCO. |

## 4. Ancrage dans le code actuel

| Élément | Aujourd’hui | Devenir |
|---|---|---|
| `DocumentIaController` `/v1/document-ia` | Banc de lecture, sans session | Modèle du banc. Les deux routes restent séparées. |
| Classification `code` / `version` / `side` | Produite par `vision-1` | Au temps 2, elle choisit le gabarit. |
| `HostedFlowService` | Décide après la lecture | Inchangé tant que le banc n’est pas accepté. |
| Brouillon JSON sous `resources/authenticity/` | Ancien plan de mesures | Remplacé par le contrat du §6 (référence + COCO seulement). |
| `IdvDecisionEngine` `vision-1` | Lit et doute. Ne certifie pas la pièce. | Au temps 2, un verdict `suspect` est un refus que ce moteur ne peut pas annuler. |

## 5. Principe

On compare une carte à vérifier à un gabarit. Le gabarit est l’image de référence de l’édition et le fichier COCO qui dit où est chaque élément.

1. **Redresser la carte.** Détecter ses quatre coins, puis appliquer une transformation de perspective vers le canevas dérivé du rectangle `document` du COCO. Si les coins ne se détachent pas du fond, une autre voie fait correspondre des points caractéristiques (ORB ou SIFT) avec l’image de référence, puis calcule l’homographie avec RANSAC. Les deux voies aboutissent au même canevas.
2. **Projeter les boîtes COCO.** Une fois la carte alignée, chaque boîte du gabarit indique où regarder.
3. **Vérifier chaque zone**, de deux façons.
   - **Éléments fixes.** Logo, titre, emblème, libellé imprimé identique sur toutes les cartes de l’édition. Comparaison avec le crop du gabarit par corrélation croisée normalisée ou SSIM. Le score doit dépasser un seuil global.
   - **Positions.** La boîte détectée de chaque champ doit rester proche de celle du gabarit. La mesure est un IoU ou un écart en pixels, avec les seuils globaux.
4. **Décider.** Combiner les scores en une décision : `conforme`, `a_verifier` ou `suspect`.

Le fichier COCO est la géométrie du gabarit. Il décrit une seule image de référence. La carte à vérifier n’a pas de COCO. Le texte des champs variables n’est pas lu ici : la lecture et la cohérence restent chez Document IA.

## 6. Gabarit

Un gabarit = géométrie d’une édition. Pas de configuration de mesure dans le dossier.

```
authenticity/{documentId}/
  reference.jpg
  annotations.coco.json
```

`documentId` est le triplet `{code}:{version}:{side}`. `reference.jpg` est un spécimen de cette face, sans être la carte d’un dossier de vérification. `annotations.coco.json` porte les boîtes et les rôles. Aucun `template.json`, aucun ratio, aucun seuil n’est enregistré à côté.

### 6.1 COCO

| Champ | Format | Usage |
|---|---|---|
| `images[]` | Une image : `file_name`, `width`, `height` | Dimensions de la photo de référence. Elles situent toutes les boîtes. |
| `categories[]` | `id`, `name`, `role` | Le moteur lit le rôle, pas le nom. |
| `bbox` | `[x, y, largeur, hauteur]` en pixels de la référence | Emprise de l’élément. |
| `attributes.bbox_value` | `[x, y, largeur, hauteur]` | Rectangle de la valeur (position). Pas d’OCR dans ce moteur. |
| `attributes.bbox_label` | `[x, y, largeur, hauteur]`, ou `null` | Libellé imprimé, traité comme élément fixe. |

| Rôle | Contrôles |
|---|---|
| `document` | Rectangle de la carte sur la référence. Une seule catégorie. Origine de la projection et du canevas. |
| `fixed` | Ressemblance du crop. Le libellé (`bbox_label`) d’une autre catégorie est traité de la même façon. |
| `value` | Position de `bbox_value` (présence d’encre et emplacement). Pas d’OCR. |
| `region` | Position et présence. Portrait, signature, ou zone dont on vérifie qu’elle est là. |

### 6.2 Canevas et ratio (dérivés, non enregistrés)

Rien n’écrit `aspectRatio` ni `canvas` dans un fichier de gabarit.

1. `images[].width` et `images[].height` donnent la taille de la photo de référence.
2. La `bbox` de la catégorie `document` donne le rectangle de la carte sur cette photo.
3. Le **ratio attendu** est `document.largeur / document.hauteur`.
4. Le **canevas** de travailtravail est ce rectangle remis à plat : largeur × hauteur en pixels de la `bbox` `document` (ou une mise à l’échelle proportionnelle fixe du moteur, identique pour tous les gabarits).

Une carte ID-1 et un passeport n’ont pas le même ratio : chacun l’obtient de son propre rectangle `document`.

Au démarrage, le registre charge chaque dossier `{documentId}`, lit le COCO et l’image, vérifie qu’il y a exactement une catégorie `document`, que les boîtes sont dans l’image et que les rôles sont connus. Un COCO invalide empêche le démarrage.

### 6.3 Règles générales du moteur (`template-1`)

Un seul jeu, commun à toutes les pièces. Constantes du code (ou config globale d’application), jamais dans un dossier de gabarit.

| Constante | Valeur initiale | Usage |
|---|---|---|
| `similarity` | `NCC` | Ressemblance des éléments fixes (`NCC` ou `SSIM`). |
| `similarityPass` | 0,75 | Score au-dessus → `conforme`. |
| `similaritySuspect` | 0,50 | Score en dessous → `suspect`. |
| `searchMarginPx` | 15 | Marge de glissement du crop. |
| `iouPass` | 0,50 | IoU au-dessus → `conforme` (avec l’écart). |
| `iouSuspect` | 0,20 | IoU en dessous → `suspect`. |
| `maxShiftPx` | 35 | Écart max en pixels avant `suspect`. |
| `aspectTolerance` | 0,01 | Écart relatif max au ratio dérivé du `document`. |

Ces valeurs seront recalibrées sur un jeu annoté avant le branchement au parcours. Les changer recalibre tout le catalogue d’un coup.

## 7. Architecture

```
image + documentId
  → TemplateRegistry     documentId → référence + COCO (boîtes, canevas dérivé)
  → CardRectifier        coins + perspective, sinon ORB/SIFT + RANSAC
  → BoxProjector         boîtes du gabarit sur le canevas
  → FixedMatcher         NCC ou SSIM (constantes globales)
  → PositionMeasurer     IoU / écart (constantes globales)
  → AuthenticityVerdict  conforme, a_verifier ou suspect
```

| Pièce | Rôle |
|---|---|
| `TemplateRegistry` | Charge `classpath:authenticity/{documentId}/`. Refuse un COCO mal formé au démarrage. |
| `CardRectifier` | Image canonique. Note la voie : `corners` ou `features`. |
| `BoxProjector` | Chaque boîte du COCO devient un rectangle du canevas dérivé. |
| `FixedMatcher` | Compare le crop de la référence au même endroit de la carte alignée. |
| `PositionMeasurer` | Trouve la boîte du contenu dans la fenêtre et la compare au gabarit. |
| `AuthenticityVerdict` | Seul endroit qui pose le verdict. |

OpenCV fournit le contour, la perspective, ORB, SIFT et RANSAC. Le reste est en Java. Les seuils sont les constantes globales du §6.3.

Le query `until` arrête le pipeline. Les blocs suivants valent `null` : `rectify`, `project`, `fixed`, `positions`, `decide`.

## 8. Redresser

La photo arrive penchée, avec un fond et une lumière quelconques. Après cette étape, une boîte du gabarit tombe sur la même zone de la carte.

1. Calculer le ratio attendu depuis la `bbox` `document` du COCO : `largeur / hauteur`.
2. Repérer les quatre coins du plus grand quadrilatère dont le rapport largeur / hauteur est à `aspectTolerance` près de ce ratio.
3. Appliquer la transformation de perspective vers le canevas dérivé de cette `bbox` `document`.
4. Si aucun quadrilatère ne convient, estimer une homographie par ORB ou SIFT entre la carte et l’image de référence, avec RANSAC, puis amener le résultat au même canevas.
5. Si les deux voies échouent : verdict `a_verifier`, code `alignment_failed`. On ne conclut pas qu’une carte non isolable est fausse.

L’écart de ratio se mesure sur le quadrilatère, avant la mise à plat. La voie retenue est écrite dans la réponse (`corners` ou `features`). Le canevas renvoyé est celui dérivé du COCO, pas une taille codée en dur par type de pièce.

## 9. Projeter les boîtes

Les boîtes du COCO sont en pixels de la photo de référence (`images[].width` × `images[].height`). La catégorie `document` est le rectangle de la carte sur cette photo.

1. Ramener chaque rectangle à ce rectangle `document`.
2. Le mettre à l’échelle du canevas dérivé.
3. Si la référence elle-même est penchée, transformer les quatre coins de la boîte, puis prendre le rectangle englobant sur le canevas.

`bbox`, `bbox_value` et `bbox_label` se lisent `[x, y, largeur, hauteur]`.

À l’exécution, la carte à vérifier est alignée sur le même canevas. Les boîtes déjà projetées disent où découper.

## 10. Deux contrôles

### 10.1 Éléments fixes

Pour chaque catégorie `fixed`, et pour chaque `bbox_label` non nul :

1. Découper le crop sur la référence déjà redressée.
2. Découper la même fenêtre sur la carte à vérifier, élargie de `searchMarginPx`.
3. Glisser le crop dans la fenêtre. Retenir le meilleur score de NCC ou de SSIM, selon la constante `similarity`, et le décalage de ce meilleur endroit.

NCC vaut 1 pour une copie fidèle et peut être négatif. SSIM est dans `[0, 1]`. Le décalage à l’intérieur de la marge ne fait pas échouer l’élément.

| Score | Statut de l’élément |
|---|---|
| ≥ `similarityPass` | `conforme` |
| < `similaritySuspect` | `suspect` |
| Entre les deux | `a_verifier` |

### 10.2 Positions

Pour chaque catégorie `value` ou `region`, la boîte attendue est `bbox_value` s’il existe, sinon `bbox`.

1. Ouvrir cette boîte sur la carte alignée, élargie au-delà de `maxShiftPx`, pour qu’un champ déplacé soit encore trouvé.
2. Détecter la boîte du contenu dans cette fenêtre : rectangle de l’encre pour un texte, rectangle du contenu visible pour une `region`. Aucune reconnaissance de caractères.
3. Comparer à la boîte projetée. L’IoU est l’intersection sur l’union. L’écart en pixels est le maximum de `|Δleft|`, `|Δtop|`, `|Δwidth|` et `|Δheight|`.

| Mesure | Statut du champ |
|---|---|
| IoU ≥ `iouPass` et écart < `maxShiftPx` | `conforme` |
| IoU < `iouSuspect` ou écart ≥ `maxShiftPx` | `suspect` |
| Entre les deux | `a_verifier` |
| Aucun contenu dans la fenêtre | `suspect`, code `field_absent` |
| Fenêtre brûlée, contenu non séparable | `a_verifier`, code `zone_unmeasured` |

## 11. Décider

Le relevé de chaque élément reste dans la réponse. Le verdict global suit cet ordre. La première ligne qui s’applique tranche.

| Ordre | Condition | Verdict |
|---|---|---|
| 1 | Image illisible, vide, ou type hors `image/jpeg` et `image/png` | HTTP 422 `image_rejected` |
| 2 | Gabarit inconnu | HTTP 422 `template_not_found` |
| 3 | Alignement impossible | `a_verifier`, code `alignment_failed` |
| 4 | Au moins un élément `suspect` | `suspect` |
| 5 | Aucun `suspect`, au moins un `a_verifier` | `a_verifier` |
| 6 | Tout le reste | `conforme` |

`conforme` veut dire que les éléments fixes et les positions tiennent. Il ne veut pas dire que le support est une vraie carte, ni que le titulaire est la personne filmée, ni que le texte lu par Document IA est cohérent.

`a_verifier` veut dire qu’une mesure est limite ou qu’une zone n’a pas pu être mesurée. Ce n’est pas un faux constaté.

`suspect` veut dire qu’une marque ne ressemble pas au gabarit, ou qu’un champ est franchement déplacé ou absent.

### Sens des verdicts au temps 2

| Verdict | Effet sur la session | Approbation |
|---|---|---|
| `conforme` | Authenticité visuelle acceptée. `authenticity_version = template-1` et le `documentId` sont figés sur la session. | Possible, si les autres conditions du dossier sont là (dont la lecture Document IA). |
| `suspect` | `DECLINED`, signal `authenticity_failed`. | Impossible, y compris par un analyste. |
| `a_verifier` avec `alignment_failed` ou `zone_unmeasured` | Nouvelle capture. Ce n’est pas un signal de fraude. | Impossible tant que le contrôle n’a pas abouti. |
| `a_verifier` pour un score limite | Revue. | Impossible tant que l’authenticité n’est pas `conforme`. |
| Pas de gabarit pour la classe renvoyée par `vision-1` | Signal `authenticity_unsupported`. La pièce n’est pas déclarée fausse. | Impossible. |

`vision-1` continue de poser ses propres doutes et de lire les champs. `template-1` ne les efface pas. Un `conforme` ne transforme pas ces doutes en approbation.

## 12. Banc Swagger

| | Banc | Parcours (plus tard) |
|---|---|---|
| Route | `/v1/authenticity/**` | flux existant, après la classe |
| Auth | `Authorization: Bearer`, clé `ky_test_` ou `ky_live_` | token du flow |
| Effet | aucun | décision et version figée |
| Réseau | aucun | aucun |
| Flag éteint | **404** | le parcours n’appelle pas le moteur |

Le flag est `kyc.authenticity.lab-enabled`. Défaut `true` en local, `false` hors développement. La route n’est pas publique : le filtre Bearer déjà en place suffit.

Une clé `ky_test_` peut appeler le banc. Elle ne crée pas de session.

### `GET /v1/authenticity/templates`

Liste les gabarits chargés : `documentId`, `code`, `version`, `side`, dimensions de l’image de référence, canevas dérivé du `document`, nombre d’éléments fixes et de champs.

### `POST /v1/authenticity/check`

`multipart`, champ `file`. Query :

| Paramètre | Rôle |
|---|---|
| `documentId` | Obligatoire. Le triplet du gabarit. |
| `until` | `rectify`, `project`, `fixed`, `positions` ou `decide`. Défaut : `decide`. |

```json
{
  "pipeline": "template-1",
  "stoppedAt": "decide",
  "documentId": "CODE:VERSION:SIDE",
  "verdict": "conforme",
  "codes": [],
  "rectify": { "method": "corners", "canvas": [728, 456] },
  "fixed": [],
  "positions": []
}
```

`canvas` dans la réponse est le couple largeur × hauteur dérivé de la `bbox` `document` du COCO chargé. Ce n’est pas une constante par type de pièce.

Chaque élément de `fixed` et `positions` porte son nom de catégorie, sa mesure, son statut (`conforme`, `a_verifier`, `suspect`) et son code éventuel.

Les blocs au-delà de `until` sont `null`. Le verdict est `null` tant que `until` n’est pas `decide`, sauf si l’alignement a déjà échoué : le verdict est alors `a_verifier` et les blocs suivants restent `null`.

| Cas | HTTP | Code |
|---|---|---|
| Flag éteint | 404 | — |
| Sans clé, ou clé invalide | 401 | `unauthorized` |
| Fichier absent, vide, ou type refusé | 422 | `image_rejected` |
| `documentId` absent ou inconnu | 422 | `template_not_found` |
| `until` hors liste | 422 | `invalid_until` |

Aucune image n’entre dans les logs. On journalise le `documentId`, la voie d’alignement, le verdict, les codes, les scores et la durée. Deux appels sur la même image et le même gabarit produisent le même JSON.

## 13. Livraison

Chaque palier est visible dans Swagger avant le suivant. `mvn spring-boot:run` dans `api/`, puis `http://localhost:8080/swagger-ui.html`, clé Bearer.

| Palier | `until` | Ce qui devient vrai |
|---|---|---|
| A0 | — | `GET /templates` et `POST /check` répondent. Enveloppe `template-1`, reste à `null`. Flag et 401 couverts. |
| A1 | `rectify` | La carte est au canevas dérivé du COCO, voie `corners` ou `features` indiquée. Sans carte : `a_verifier` / `alignment_failed`. |
| A2 | `project` | Chaque boîte COCO a un rectangle sur le canevas. |
| A3 | `fixed` | Chaque élément fixe et chaque libellé a un score. |
| A4 | `positions` | Chaque champ a un IoU et un écart en pixels. |
| A5 | `decide` | `conforme` sur la référence comparée à elle-même. `suspect` dès qu’une règle du §11 casse. |

Le parcours de vérification ne bouge à aucun de ces paliers. Le premier gabarit déposé sert de preuve du banc. Le moteur qui le fait passer est le même que pour le gabarit suivant.

**Arrêt :** banc joignable sans clé ; flag laissé ouvert hors développement ; image ou score écrit en base depuis le banc ; un `suspect` transformé en approbation ; une catégorie traitée par une classe Java nommée d’après une pièce ; un OCR dans `template-1` ; un `template.json` ou un ratio enregistré par édition.

## 14. Critères d’acceptation

| Critère | Exigence | Nature |
|---|---|---|
| Même image, même gabarit | JSON identique | Gate |
| Alignement impossible | `a_verifier` / `alignment_failed`, jamais `suspect` | Gate |
| Élément fixe sous `similaritySuspect` | `suspect`, et les autres mesures présentes | Gate |
| Écart de champ ≥ `maxShiftPx` ou IoU sous `iouSuspect` | `suspect` | Gate |
| Score ou IoU dans la bande intermédiaire | `a_verifier` sur cet élément | Gate |
| Gabarit inconnu | 422 `template_not_found` | Gate |
| Clé absente | 401 | Gate |
| Banc | Zéro ligne de vérification | Gate |
| Référence comparée à elle-même | `conforme` | Gate, sur le gabarit déposé |
| Taux sur un jeu d’images réel | À chiffrer avant de brancher le parcours | Pas un gate de ce chantier |

Le branchement au flow reste fermé tant que ces gates sont verts.
