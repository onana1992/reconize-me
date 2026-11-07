# Spécification détaillée — Reconstruction du moteur forensique

**Produit :** Recogniz-Me, Identity & Document Verification  
**Composant :** service `anti-spoof`, moteur `anti-spoof-2`  
**Version du document :** 1.0  
**Date :** 7 novembre 2026  
**Statut :** spécification d’approche — reconstruction des extracteurs  
**Document qui prime :** [`specification-anti-spoofing.md`](./specification-anti-spoofing.md) — codes, seuils, HTTP, décision Java  
**Lecture produit :** [`specification-detaillee-anti-spoofing.md`](./specification-detaillee-anti-spoofing.md) — cas d’utilisation, parcours, règles  
**Ordre historique :** [`roadmap-implementation-anti-spoofing.md`](./roadmap-implementation-anti-spoofing.md) — paliers S0 à S8 du premier moteur  

Ce texte remplace la **méthode de mesure** à l’intérieur du service Python. Il ne remplace pas le contrat. Les cinq alertes, les seuils 0,55 / 0,80, le bac à sable, le drapeau live et le juge `IdvDecisionEngine` restent ceux du contrat. Ce qui change : d’où viennent les scores.

---

## 1. L’idée en une page

Une photo de pièce d’identité n’est pas un dessin plat. C’est le résultat d’une chaîne physique : capteur, filtre de Bayer, démosaïquage, compression JPEG, parfois un écran, parfois une imprimante. Chaque étape laisse une **trace microscopique**. Une fraude casse cette chaîne à un endroit. Le moteur ne « devine » pas qu’une date est fausse. Il cherche la rupture de trace.

Il n’existe pas une bibliothèque magique qui traite les cinq fraudes. Un collage, une date repeinte, un JPEG ré-enregistré, une photo d’écran et une impression papier n’obéissent pas à la même physique. On assemble donc **cinq familles d’extracteurs**, chacune avec l’outil de la littérature forensique qui lui correspond, derrière le même contrat HTTP.

| Alerte | Trace physique | Outils de cette version |
|---|---|---|
| `splicing` | Deux sources dans la même image : bruit différent, ou un motif copié | PhotoHolmes (Splicebuster, Noisesniffer) + OpenCV (SIFT / copy-move) |
| `text_alteration` | Glyphes repris : alignement, largeur de trait, anneau JPEG autour du caractère | Tesseract (boîtes, pas le texte persisté) + ELA local + OpenCV |
| `compression_inconsistency` | Une zone a une autre histoire JPEG que le reste | PhotoHolmes (DQ, Zero) + ELA Pillow |
| `screen_replay` | Moiré, sous-pixels, pics FFT d’une dalle | OpenCV / SciPy FFT, analyse de phase RVB |
| `printed_copy` | Trame d’encre, gouttelettes, texture papier | scikit-image LBP + spectre de trame |

Le service mesure. Le moteur Java décide. Un score élevé veut dire « trace forte de cette fraude », pas « 91 % de chances que le dossier soit faux ».

---

## 2. Pourquoi reconstruire

Le moteur actuel (`anti-spoof-1`) a la bonne forme : cinq alertes, fusion par max, JPEG d’origine, pas de décision dans Python. Les extracteurs, eux, sont des approximations maison.

| Alerte | `anti-spoof-1` aujourd’hui | Limite |
|---|---|---|
| `splicing` | Hachage de blocs 16×16 + résidu gaussien | Rate un fond de sécurité périodique, rate un collage dont le bruit est proche, ignore SIFT |
| `text_alteration` | ELA par bandes de 32 px, sans lire les glyphes | Une date de 20 px disparaît dans la bande. Aucune mesure de police ni d’alignement |
| `compression_inconsistency` | Qualité JPEG estimée par bandes, en mémoire | Ne lit pas les coefficients DCT ni l’origine de la grille 8×8 |
| `screen_replay` | Pics FFT et carreaux | Bonne piste, mais sans séparation nette moiré / sous-pixels / reflet |
| `printed_copy` | Rosace spectrale et grain | Pas de descripteur de texture (LBP), confusion possible avec une dalle |

On ne jette pas le contrat. On remplace les extracteurs par des méthodes **publiées, reproductibles, complémentaires**. La version du moteur passe à `anti-spoof-2`. Tant que cette version n’est pas derrière le drapeau live, le parcours continue d’appeler `anti-spoof-1` ou le stub.

---

## 3. Ce qui ne change pas

Tout ce qui est visible hors du processus Python reste le contrat.

1. Cinq alertes, dans cet ordre : `splicing`, `text_alteration`, `compression_inconsistency`, `screen_replay`, `printed_copy`.
2. Le service ne renvoie pas `PASS`, `REVIEW`, `DECLINED`.
3. L’API envoie les **octets JPEG stockés**, jamais la copie ré-encodée pour la lecture vision.
4. Les hints sont des rectangles (`portrait`, `mrz`, `birth_date`, …). Aucune chaîne lue n’arrive dans la requête.
5. Une recompression **globale** du téléphone ne fait pas monter `compression_inconsistency`.
6. Clé `ky_test_` : zéro appel Python. Drapeau `kyc.anti-spoof.enabled` faux : zéro appel.
7. Panne, délai, JSON hors contrat : revue `authenticity_unavailable`, jamais une pièce saine inventée.
8. Le service ne conserve rien après la réponse. Pas de disque, pas de file, pas de copie d’image.
9. Les seuils 0,55 / 0,80 et `decline-codes` restent dans Java.

Ce document ne rouvre pas ces règles. S’il se contredit avec le contrat, le contrat gagne.

---

## 4. Pourquoi pas une seule bibliothèque

La criminalistique numérique (image forensics) n’a pas d’équivalent de « un OCR pour tout le texte ». Chaque falsification casse un étage différent de la chaîne d’acquisition.

```
scène réelle
    → optique + capteur (filtre de Bayer, PRNU)
    → démosaïquage, débruitage constructeur
    → JPEG (grille 8×8, table de quantification)
         │
         ├─ collage d’une autre capture ──────── ruptures de bruit / CFA / JPEG
         ├─ copier-coller dans la même image ── correspondances SIFT
         ├─ retouche locale puis ré-enregistrement ── double JPEG, ELA
         ├─ photo d’un écran ────────────────── moiré, FFT, sous-pixels
         └─ photo d’une impression ──────────── trame, LBP, gouttelettes
```

PhotoHolmes est la **bibliothèque Python de référence** pour enchaîner et comparer des méthodes forensiques sur une image. Ce n’est pas un détecteur unique. Sa première version publie dix méthodes, dont quatre conviennent à un service CPU interne, et six sont des réseaux (GPU, poids, licences). OpenCV, Pillow, SciPy, scikit-image et Tesseract complètent ce que PhotoHolmes ne couvre pas : copy-move SIFT, ELA, moiré, LBP, géométrie des glyphes.

Deux précisions, parce qu’elles sont souvent mal dites :

- PhotoHolmes **n’embarque pas** SIFT ni SURF. Le copy-move par descripteurs se fait avec OpenCV.
- Le **PRNU** au sens strict (empreinte du capteur) demande plusieurs photos du même appareil. En IDV on a **une** image. On ne calcule donc pas une empreinte caméra. On cherche une **incohérence locale du résidu de bruit**, ce que Noisesniffer et Splicebuster savent faire sur une seule vue.

---

## 5. Deux étages

Le budget du contrat reste : p95 ≤ 1,5 s par face sur CPU quatre cœurs, coupe API à 2,5 s. Un réseau type TruFor ou CAT-Net ne tient pas ce budget sans GPU. La reconstruction se coupe donc en deux étages. Seul l’étage A entre dans `anti-spoof-2`.

### 5.1 Étage A — `anti-spoof-2`, CPU, cette reconstruction

Méthodes **explicables**, sans poids de réseau dans l’image Docker.

| Rôle | Méthode | Origine |
|---|---|---|
| Résidu de bruit / collage | Splicebuster, Noisesniffer | PhotoHolmes |
| Grille JPEG, double quantification | Zero, DQ | PhotoHolmes |
| Copy-move | SIFT + FLANN + RANSAC | OpenCV |
| ELA | recompression qualité fixe, différence absolue | Pillow |
| Moiré / écran | FFT 2D, phase inter-canaux | OpenCV / SciPy |
| Trame papier | LBP + spectre de points | scikit-image, OpenCV |
| Glyphes | boîtes Tesseract, métriques OpenCV | Tesseract, OpenCV |

Pas de PyTorch dans cette image. Pas de VLM. Pas de modèle de langage qui « lit » la pièce pour dire si elle est vraie.

### 5.2 Étage B — plus tard, même contrat

Réseaux déjà wrappés dans PhotoHolmes (TruFor, CAT-Net, PSCC-Net, Adaptive CFA, FOCAL) ou un CNN de moiré. Image Docker à part, GPU, drapeau `kyc.anti-spoof.neural`. Ils **ajoutent** un extracteur calibré, ils ne remplacent pas l’étage A, ils ne changent ni l’URL ni le JSON. Licences des poids et coût GPU à trancher avant tout branchement live.

U-Net, cité souvent pour segmenter un collage, appartient à l’étage B. En A, la localisation vient des cartes Splicebuster / Noisesniffer / ELA, converties en rectangles du contrat.

---

## 6. Stack logicielle, étage A

Python 3.12. Processus distinct du JAR, réseau interne seulement.

| Bibliothèque | Usage dans le service | Ce qu’on n’en fait pas |
|---|---|---|
| PhotoHolmes | Wrappers Splicebuster, Noisesniffer, Zero, DQ. Accès pixels, DCT, tables de quantification | On n’appelle pas le CLI, on n’embarque pas les datasets Columbia / CASIA dans l’image |
| OpenCV headless | SIFT, FLANN, morphologie, DFT, contours de glyphes | Pas d’interface graphique, pas de SURF (SIFT suffit, brevet expiré) |
| NumPy / SciPy | Tenseurs, FFT, statistiques de cartes | — |
| Pillow (PIL) | Décodage, ELA (ré-encode en mémoire à qualité fixe) | Jamais d’écriture JPEG sur disque |
| scikit-image | `local_binary_pattern`, éventuellement filtres de texture | Pas d’apprentissage |
| Tesseract (`tesseract-ocr` + `pytesseract`) | Boîtes de glyphes, script, confiance. Le texte est jeté avant la réponse | Pas de persistance, pas de log, pas de champ JSON |
| FastAPI / Pydantic / Uvicorn | Contrat HTTP inchangé | — |

Dépendance PhotoHolmes : dépôt Git piné par commit (`photoholmes/photoholmes`), pas un paquet PyPI encore instable. On n’importe que les méthodes de l’étage A. Les poids de CAT-Net / TruFor ne sont pas téléchargés au build.

Tesseract est un binaire système dans l’image Docker (`tesseract-ocr`, `tesseract-ocr-osd`). Langues : `osd` + `latin` / `eng` suffisent pour **localiser** des glyphes sur une pièce. On n’installe pas une langue par pays : le but n’est pas de relire le nom.

---

## 7. Architecture interne

Le contrat HTTP (`/health`, `/v1/analyze`) ne bouge pas. L’intérieur du processus, oui.

```
octets JPEG d’origine
        │
        ▼
 preprocess          orientation EXIF en mémoire, plafond 12 Mpx / 8 Mo
        │            le buffer JPEG n’est pas ré-écrit
        ├──────────────► jpeg.decode   DCT 8×8 + qtables  (Zero, DQ)
        ├──────────────► pixels RGB    (tout le reste)
        └──────────────► ela.map       carte d’erreur à qualité 90, une fois
                │
                ▼
        extracteurs en parallèle bornée
                │
     ┌──────────┼──────────┬─────────────┬──────────────┐
     ▼          ▼          ▼             ▼              ▼
 splicing    text      compression    screen         printed
     │          │          │             │              │
     └──────────┴──────────┴─────────────┴──────────────┘
                           │
                           ▼
                     fusion (max par alerte)
                           │
                           ▼
                     Assessment du contrat
```

Règle d’ordonnancement : **une seule** carte ELA, **un seul** décodage DCT, **un seul** passage Tesseract. Les détecteurs lisent ces artefacts, ils ne les recalculent pas. Une exception dans un extracteur fait échouer l’appel en `500`. On ne renvoie pas un score partiel qui ressemblerait à une pièce saine.

`engine_version` : `anti-spoof-2`.  
`fusion_version` : `identity` tant que les scores bruts ne sont pas calibrés, puis `forensic-1` quand `forensic-1.json` existe.

### 7.1 Arborescence cible

```text
services/anti-spoof/
  app/
    main.py
    contract.py                 inchangé
    pipeline.py                 anti-spoof-2
    preprocess.py               EXIF, plafonds
    jpeg.py                     DCT, qtables, origine de grille
    artifacts.py                ELA, FFT amplitude, LBP, boîtes OCR — calculés une fois
    fusion.py
    detectors/
      splicing.py
      text.py
      compression.py
      screen.py
      printed.py
    forensic/
      photoholmes.py            wrappers étage A
      sift_copymove.py
      ela.py
      ocr_boxes.py              Tesseract → boîtes, texte jeté
    models/
      forensic-1.json           plus tard
  tests/
    test_contract.py
    test_splicing.py
    test_text.py
    test_compression.py
    test_screen.py
    test_printed.py
    test_privacy.py             aucune chaîne OCR dans logs ni réponse
```

---

## 8. Prétraitement et traces JPEG

Trois représentations coexistent. Les mélanger casse la preuve.

| Représentation | Contenu | Qui la lit |
|---|---|---|
| Buffer d’origine | Octets téléversés, y compris tables JPEG | `jpeg.py`, DQ, Zero |
| Pixels RGB redressés | Décodés, orientation EXIF appliquée **en mémoire** | SIFT, Splicebuster, FFT, LBP, OCR, ELA |
| Carte ELA | `abs(pixels - recompress(pixels, Q=90))` | `text_alteration`, `compression_inconsistency` (localisation) |

Interdits :

- ré-encoder le buffer avant DQ / Zero ;
- envoyer la copie vision (qualité 0,8, grand côté 1 280 px) ;
- écrire un JPEG temporaire sur disque (ELA se fait dans un `BytesIO`).

Si le plus petit côté &lt; 480 px, ou plus de 12 mégapixels, ou fichier non JPEG : `forensic_usable: false`, détecteurs non appelés, comme aujourd’hui.

---

## 9. Collage — `splicing`

### 9.1 Ce que c’est

Deux gestes distincts, une seule alerte.

**Splicing.** Un morceau vient d’**une autre** image (autre capture, autre fichier, portrait collé). Le faussaire le pose sur la pièce, souvent avec un masque doux.

**Copy-move.** Un morceau est **dupliqué dans la même** image (un 0 copié pour faire un 8, un hologramme recopié, un portrait cloné). La source et la copie partagent la même capture, donc souvent le même bruit. Le résidu de bruit ne suffit pas. Il faut retrouver la correspondance.

Le remplacement du portrait est un splicing. Il n’a pas d’alerte à part. Le rectangle hint `portrait` pondère, il n’est pas obligatoire.

### 9.2 Pourquoi ça laisse une trace

Un appareil photo n’est pas un scanner parfait. Deux phénomènes nous servent.

**Résidu de bruit.** Après un passe-haut, il reste un grain lié au capteur et au démosaïquage. Sur une capture saine, ce grain est statistiquement homogène (à l’éclairage près). Un collage importe le grain d’une autre chaîne : la variance locale, les co-occurrences du résidu, ou le modèle stochastique du bruit **cassent** à la frontière.

Ce n’est pas du PRNU d’attribution (« cette photo vient du téléphone X »). C’est de l’**incohérence de bruit sur une seule vue**.

**Descripteurs locaux (copy-move).** SIFT trouve des points invariants à la rotation et à l’échelle. Si un nuage de points dans la zone A se retrouve, après une homographie, dans la zone B, et que ce n’est pas le fond périodique de la carte, on a une copie.

### 9.3 Extracteurs

Trois extracteurs, le score d’alerte est le max après calibration.

| Code interne | Méthode | Sortie | Garde-fou |
|---|---|---|---|
| `splice_residual` | Splicebuster (PhotoHolmes) | heatmap de rupture de résidu | Une composante connexe, pas un pixel. Surface entre 0,5 % et 45 % de l’image |
| `noise_nfa` | Noisesniffer (PhotoHolmes) | masque + score NFA (nombre de fausses alarmes) | NFA trop élevé → on ignore. Le fond de sécurité n’est pas un bruit d’un autre capteur |
| `copy_move_sift` | OpenCV SIFT + FLANN + RANSAC | paires de régions, vecteur de déplacement | Rejeter les matches dont le vecteur est celui d’un motif périodique (guillochis). Minimum de matches inliers, déplacement ≥ 40 px |

**Splicebuster, en une phrase.** On estime un résidu (bruit), on calcule des co-occurrences locales, on cherche les endroits où ce modèle n’est plus le même. Hypothèse : une image saine a un seul modèle de bruit.

**Noisesniffer, en une phrase.** On ajuste un modèle stochastique du bruit, on mesure a contrario combien de fausses alarmes une anomalie produirait. Une région qui pousse par croissance à partir de ces anomalies devient le masque.

**SIFT copy-move, déroulé.**

1. Convertir en luma, limiter le grand côté à 1 600 px (comme aujourd’hui).
2. Détecter les keypoints SIFT, décrire.
3. Matcher FLANN, ratio test de Lowe (0,75).
4. Grouper les matches par vecteur de déplacement (clustering). Un copy-move vrai a **un** déplacement dominant, pas une étoile de vecteurs.
5. RANSAC : une homographie (ou une translation) doit expliquer le cluster.
6. Filtre périodique : si les keypoints du cluster tombent sur une grille régulière qui couvre tout le fond, score 0. C’est le guillochis.
7. Le hint `portrait` multiplie le score du cluster qui intersecte ce rectangle (poids 1,4, comme aujourd’hui). Il ne crée pas de cluster.

### 9.4 Score et rectangles

Chaque extracteur produit un score brut \[0, 1\] et au plus cinq rectangles normalisés. Le max calibré devient `splicing`. Les rectangles gardent les noms du contrat (`portrait` si le centre tombe dans le hint, sinon `document`).

### 9.5 Ce que cet extracteur ne voit pas

- Un collage suivi d’un ré-enregistrement fort de **toute** l’image : le bruit s’homogénéise. `compression_inconsistency` et `text_alteration` peuvent rester seuls. Trou connu, mesuré au §16.
- Un fond de sécurité très périodique : SIFT doit se taire. Si Splicebuster monte sur tout le cadre, on plafonne : ce n’est pas un collage, c’est la carte.
- Un deepfake du visage du titulaire : hors périmètre (biométrie).

### 9.6 Tests synthétiques (niveau A)

| Construction | Attendu |
|---|---|
| Carte uniforme, un JPEG | `splicing` sous 0,20 |
| Rectangle dont le résidu a une autre variance, collé | `splice_residual` monte, `screen_replay` et `printed_copy` restent bas |
| Motif SIFT copié à 120 px, hors grille | `copy_move_sift` monte |
| Guillochis périodique sans copie | `copy_move_sift` à 0 |
| Portrait hint recouvrant le collage | score ≥ score sans hint |

---

## 10. Altération de texte — `text_alteration`

### 10.1 Ce que c’est

Quelqu’un change un caractère : date d’expiration, nom, numéro, montant. Le geste typique : tampon de clonage, superposition d’un glyphe, ou inpainting local, puis enregistrement.

Sur une pièce d’identité, on ne cherche **pas** l’incohérence logique « le total de la facture est faux ». Cette règle n’a pas de sens ici. Les check digits MRZ et l’accord OCR / MRZ restent le moteur Java et Document IA. Ici on cherche la **trace visuelle** du glyphe repris.

### 10.2 Pourquoi ça laisse une trace

Un caractère imprimé en offset a une largeur de trait, une ligne de base, un crénage, et la même histoire JPEG que ses voisins. Un caractère collé :

- n’est plus sur la ligne de base (quelques pixels suffisent) ;
- a une autre largeur de trait ou une autre netteté ;
- crée un **anneau de compression** : les blocs 8×8 autour du glyphe ont été ré-encodés, les voisins non ;
- peut porter un résidu de rééchantillonnage (interpolation du tampon).

L’OCR n’est pas là pour relire le nom. Il est là pour **poser des boîtes** autour des glyphes, afin de mesurer ces écarts **au bon endroit**. Une ELA par bandes de 32 px, comme aujourd’hui, noie une date de 18 px de haut.

### 10.3 Vie privée — règle dure

Tesseract produit des chaînes. Ces chaînes sont une donnée d’identité.

1. Les chaînes vivent le temps de l’appel, dans un objet local.
2. Seules les boîtes, les scores de confiance, le script (latin / autre) et les métriques géométriques (baseline, stroke, ELA moyen de la boîte) sortent de `ocr_boxes.py`.
3. La réponse HTTP, `alerts_json`, les journaux et les exceptions **ne portent aucune chaîne**.
4. Un test `test_privacy.py` échoue si une chaîne de plus de deux caractères issus de l’OCR apparaît dans le JSON ou dans un log capturé.
5. On n’envoie rien à un VLM, ni à une API cloud. Un modèle de graphie ou un VLM, s’il existe un jour, tourne dans le même processus, avec la même règle de destruction, et seulement à l’étage B.

Document IA continue de lire le texte pour le dossier. Ce service ne le relit pas pour le stocker.

### 10.4 Extracteurs

| Code interne | Méthode | Trace | Garde-fou |
|---|---|---|---|
| `glyph_ela` | ELA moyenne par boîte OCR, comparée à la médiane des boîtes de la même ligne | Une petite île plus chaude que le texte voisin | Ignorer les boîtes de confiance OCR &lt; 30. Ignorer une ligne entière chaude (recompression globale de la ligne = pas une retouche) |
| `glyph_align` | Régression de la ligne de base des centres de glyphes | Un glyphe hors de 2,5 σ de la ligne | Une ligne à moins de 4 glyphes : score 0. Une pièce penchée : on fit une droite, pas une horizontale |
| `glyph_stroke` | Épaisseur de trait (distance au fond binarisé) | Un glyphe dont l’épaisseur sort de 2,5 σ de sa ligne | Un 1 à côté d’un 0 est plus fin : on compare par classe de largeur (étroits / larges), pas tous ensemble |
| `text_dq` | DQ / ELA restreints aux bandes de texte (hints `mrz`, `birth_date`, …) | Double quantification sous les caractères | Sans hint et sans boîte OCR, on retombe sur les bandes de contours, plus faibles |

Les hints de l’API, s’ils sont là, **bornent** Tesseract : on n’OCRise que ces rectangles, plus une marge. Sans hint, Tesseract tourne sur l’image limitée à 1 600 px. Le hint ne contient pas la chaîne.

### 10.5 Pourquoi pas EasyOCR ni un VLM dans cette version

EasyOCR tire PyTorch, donc une image Docker beaucoup plus lourde, pour un gain de localisation. Tesseract suffit à poser des boîtes sur une pièce déjà acceptée par le contrôle de qualité (nette, ≥ 480 px). Un VLM « fin » relirait le contenu, donc la donnée d’identité, et n’expliquerait pas la trace. Il reste hors de l’étage A.

L’analyse de graphie (réseau qui dit « ce 5 n’est pas de la même fonte ») est un extracteur d’étage B, derrière la même alerte `text_alteration`.

### 10.6 Trou connu

Retouche puis recompression **uniforme** de toute l’image : `glyph_ela` et `text_dq` tombent. `glyph_align` et `glyph_stroke` peuvent survivre si le faussaire a mal calé le caractère. Le critère de mise en live mesure ce trou. On ne le bouche pas par une règle du type « police bizarre ⇒ refus ».

### 10.7 Tests synthétiques

| Construction | Attendu |
|---|---|
| Ligne de glyphes homogènes, un JPEG | `text_alteration` sous 0,20 |
| Un glyphe décalé de 4 px sous la baseline, ELA locale plus chaude | l’alerte monte, rectangle nommé `birth_date` si le hint couvre |
| Toute la ligne recompressée | `glyph_ela` à 0 |
| Réponse et logs | aucune chaîne OCR |

---

## 11. Compression incohérente — `compression_inconsistency`

### 11.1 Ce que c’est

JPEG découpe l’image en blocs 8×8, les transforme en DCT, et quantifie. Quantifier deux fois n’est pas quantifier une fois. Si le faussaire édite une zone puis enregistre, **cette zone** subit un second passage, souvent avec une autre table, parfois sur une grille décalée (sélection Photoshop). Le reste de l’image n’a qu’une génération, ou une génération différente.

Ce n’est pas « l’image a été compressée ». Presque toutes les photos de téléphone le sont. C’est « **une partie** n’a pas la même histoire JPEG ».

### 11.2 ELA, expliquée sans jargon

On recompresse volontairement l’image entière à une qualité fixe, par exemple 90, **en mémoire**. On calcule la différence absolue de pixels avec l’original.

- Une zone déjà très compressée ne bouge presque plus : elle est « saturée », l’ELA est sombre.
- Une zone récemment collée, encore riche en détails, se dégrade beaucoup à ce passage : elle **brille**.
- Les contours naturels brillent aussi. L’ELA seule ne refuse pas. Elle **localise**. Le score d’alerte exige en plus DQ ou Zero, ou un écart de qualité par bloc.

Qualité fixe de cette version : **90**. Une seule carte par appel, partagée avec `text_alteration`.

Limites, à écrire dans les tests :

- plusieurs ré-enregistrements globaux → ELA quasi noire partout, score 0, c’est le cas du téléphone ;
- un ciel uni est plus sombre qu’un texte, ce n’est pas une fraude ;
- Photoshop peut « rainbow-er » les contours en sauvant : indice d’éditeur, pas preuve d’altération d’identité.

### 11.3 Extracteurs PhotoHolmes JPEG

PhotoHolmes charge trois données : pixels, **coefficients DCT**, **tables de quantification**. C’est exactement ce qu’il ne faut pas détruire en ré-encodant.

| Code interne | Méthode | Trace | Garde-fou |
|---|---|---|---|
| `dq` | DQ (double quantification DCT) | Histogramme des coefficients : pics périodiques d’une seconde quantification, **locaux** | Si la double quantification est **globale** (toute l’image), score brut = 0 |
| `jpeg_grid` | Zero (origine de la grille JPEG) | Grille 8×8 absente ou décalée dans une région, présente ailleurs | Un JPEG sain a une origine unique. Un décalage local = sélection ré-enregistrée |
| `ela_hotspot` | Carte ELA | Composante connexe plus chaude que la médiane, &lt; 40 % de la surface | Seul, plafonne sous 0,55. Il renforce DQ / Zero, il ne refuse pas |

Règle de fusion interne, **avant** calibration, identique au contrat : écart global ⇒ score d’alerte 0. Le téléphone qui ré-enregistre toute la photo n’est pas une altération.

### 11.4 Comment on lit le JPEG

`jpeg.py` parse le buffer d’origine (tables DQT, coefficients via une lib JPEG qui expose DCT, par exemple l’accès déjà prévu par PhotoHolmes). Si le parse échoue mais que Pillow décode quand même : `dq` et `jpeg_grid` valent 0, `ela_hotspot` peut encore localiser. L’alerte peut donc monter légèrement à l’ELA, et restera sous le refus tant que DQ / Zero n’ont pas parlé.

### 11.5 Tests synthétiques

| Construction | Attendu |
|---|---|
| Un JPEG, une table, pas d’édition | alerte à 0 |
| Rectangle ré-encodé à Q=60 dans une image Q=90 | `dq` ou `ela_hotspot` montent, zone ≈ le rectangle |
| Toute l’image ré-encodée à Q=75 | alerte à 0 |
| Grille 8×8 décalée de 4 px sur une bande | `jpeg_grid` monte |

---

## 12. Rejeu sur écran — `screen_replay`

### 12.1 Ce que c’est

On photographie un moniteur ou un téléphone qui **affiche** la pièce, au lieu de photographier le support. C’est une attaque de présentation, pas une retouche du fichier. PhotoHolmes, conçu pour le splicing numérique, ne la couvre pas. OpenCV et SciPy, si.

### 12.2 Pourquoi ça laisse une trace

Un écran est une grille de sous-pixels (RVB, parfois PenTile). L’appareil photo a sa propre grille de Bayer. Les deux grilles **battent** : figure de moiré, invisible à l’œil sur une petite photo, évidente dans le spectre.

La transformée de Fourier 2D (`cv2.dft` ou `numpy.fft.rfft2`) convertit cette grille en **pics**. Une photo d’un objet mat a un spectre décroissant, sans pic étroit à la période de la dalle. Une photo d’écran a :

- un pic étroit, souvent avec des harmoniques ;
- parfois un **déphasage** entre canaux R, V et B (les sous-pixels ne sont pas au même endroit) ;
- parfois des micro-reflets (spéculaires alignés).

La FFT ne « voit » pas un iPhone dans le cadre. Un bandeau d’heure ou de batterie est un indice d’UI, jamais un refus à lui seul.

### 12.3 Extracteurs

| Code interne | Méthode | Trace | Garde-fou |
|---|---|---|---|
| `fft_moire` | Module de la FFT 2D sur luma, après passe-haut (ôte le modelé de la carte) | Pic étroit, période dans \[4, 64\] px | Un pic **large** et une rosace à 4+ pics en phase → `printed_copy`, pas ici |
| `channel_phase` | Décalage du pic entre R, V, B | Spread de phase ≥ seuil dalle | Ne refuse pas seul. Multiplie `fft_moire` |
| `local_grating` | Autocorrélation par carreaux 128 px (déjà au moteur actuel) | Part des carreaux où une seule grille se répète | Une photo d’écran dont la période glisse (perspective) : le vote par carreaux survit, le pic global non |

Si un hint `document` est fourni, FFT et carreaux sont calculés **dedans**. Une vraie carte posée devant un moniteur ne doit pas être accusée pour le fond.

Un CNN de moiré (PyTorch) est étage B. L’étage A reste fréquentiel : explicable, CPU, pas de poids.

### 12.4 Séparation écran / papier

C’est le piège principal de `anti-spoof-1`. Les deux sont des grilles. On les sépare ainsi :

| Indice | Écran | Impression |
|---|---|---|
| Forme du pic | étroit, souvent un dominant | plusieurs pics, rosace |
| Phase RVB | décalée (sous-pixels) | quasi nulle (encre superposée) |
| Résidu | cellule **pleine** (dalle) | points **impulsifs** (gouttes) |
| LBP | motif directionnel fin, faible contraste | motif tacheté, fort contraste local |

Un carreau ne vote `screen_replay` que s’il a la cellule pleine **et** pas le résidu impulsif. Il vote `printed_copy` dans le cas inverse. Il ne vote pas les deux.

### 12.5 Tests synthétiques

| Construction | Attendu |
|---|---|
| Grille RVB périodique fine + déphasage | `screen_replay` monte, `printed_copy` sous 0,30 |
| Trame de points impulsifs | `screen_replay` bas |
| Passe-haut d’une carte sans grille | les deux bas |
| Hint `document` excluant un bandeau UI | le bandeau ne suffit pas |

---

## 13. Copie imprimée — `printed_copy`

### 13.1 Ce que c’est

On photographie une **impression** de la pièce (jet d’encre, laser, parfois offset de bureau), pas le support officiel (polycarbonate, Teslin, papier sécurité). L’attaque est physique : la personne a une feuille, pas une carte.

### 13.2 Pourquoi ça laisse une trace

Une imprimante ne pose pas une couleur continue. Jet d’encre : micro-gouttelettes, diffusion dans la fibre. Laser : toner en points, fusion. Offset de bureau : trame de points en rosace CMJN. Le papier ordinaire a un **grain** (fibres) que le polycarbonate n’a pas.

**LBP** (Local Binary Patterns, `skimage.feature.local_binary_pattern`) résume la texture d’un voisinage : chaque pixel compare ses voisins à lui-même, et code un motif binaire. Sur une surface lisse officielle, l’histogramme LBP est concentré. Sur une impression, il s’étale vers les motifs « tachetés » (points d’encre, fibres).

Le spectre complète : rosace, quinconce, période 8–16 px, résidu impulsif. C’est déjà dans `anti-spoof-1`. On le garde, on l’adosse au LBP.

### 13.3 Extracteurs

| Code interne | Méthode | Trace | Garde-fou |
|---|---|---|---|
| `lbp_print` | Histogramme LBP (P=8, R=1 et P=16, R=2) par carreaux, distance à un profil « support lisse » | Carreaux dont l’histogramme ressemble à une trame, pas à une surface lisse | Un document officiel offset du corridor peut avoir une trame : tant que le jeu d’évaluation ne le montre pas, le refus de cette alerte reste fermé |
| `halftone_fft` | Pics spectraux multiples, faible spread, résidu impulsif | Rosace / quinconce | Le pic unique déphasé de l’écran ne compte pas |
| `paper_grain` | Variance basse fréquence du luma après flou | Fibre de papier | Seul, plafonne sous le seuil de refus (contrat §6.5) |

On ne cherche pas un hologramme absent. Une photo RVB ne le prouve pas.

### 13.4 Tests synthétiques

| Construction | Attendu |
|---|---|
| Points impulsifs en quinconce 8–16 px | `printed_copy` monte, `screen_replay` bas |
| Grille RVB déphasée, cellules pleines | `printed_copy` bas |
| Bruit gaussien faible sur fond lisse | les deux bas |
| Grain basse fréquence seul | score &lt; 0,80 |

---

## 14. Fusion

Inchangée dans sa forme.

```
score(alerte) = max des extracteurs calibrés de cette alerte
tampering_score = max(splicing, text_alteration, compression_inconsistency)
presentation_score = max(screen_replay, printed_copy)
authenticity_score = 1 - max(tampering_score, presentation_score)
```

On ne moyenne pas. Une date repeinte doit rester visible même si splicing est à 0.

Tant que `forensic-1.json` n’existe pas, chaque extracteur est une fonction monotone \[0, 1\] → \[0, 1\] (identité ou simple clip). `fusion_version` vaut `identity`. Le jour de la calibration : régression logistique par alerte, coefficients figés, `fusion_version = forensic-1`.

Les extracteurs d’une même alerte **peuvent** se renforcer (ex. DQ × ELA) **à l’intérieur** de l’alerte, avant le max. Ils ne se mélangent pas entre alertes. `screen_replay` et `printed_copy` se **vetoient** au niveau du carreau (§12.4), pas au niveau du max final : si vraiment les deux traces sont là (photo d’une impression affichée à l’écran), les deux scores peuvent monter. C’est rare, et c’est une revue, pas un bug.

---

## 15. Contrat, versions, bascule

| | `anti-spoof-1` | `anti-spoof-2` |
|---|---|---|
| URL | `POST /v1/analyze` | identique |
| JSON | cinq alertes | identique |
| `engine_version` | `anti-spoof-1` | `anti-spoof-2` |
| Image Docker | numpy, scipy, pillow | + opencv-headless, scikit-image, tesseract, photoholmes piné |
| PyTorch | non | non (étage A) |
| Décision Java | inchangée | inchangée |

Bascule : propriété `kyc.anti-spoof.engine` = `1` ou `2`, ou simplement l’URL du processus déployé. Le drapeau `enabled` et `decline-codes` restent les portes live. On ne passe `decline-codes` sur les alertes de la v2 **qu’après** le critère du contrat §12, rejoué sur `anti-spoof-2`. Un bon score v1 ne recycle pas le droit de refuser.

Le laboratoire Document IA et le banc Swagger appellent le même port. Ils affichent `engine_version`. Un analyste doit voir s’il lit une mesure v1 ou v2.

---

## 16. Faux positifs, limites, critère live

### 16.1 Pièges par alerte

| Alerte | Faux positif typique | Parade |
|---|---|---|
| `splicing` | Guillochis, photo du portrait, ombre portée | Filtre périodique SIFT ; Noisesniffer NFA ; surface max 45 % |
| `text_alteration` | Pièce penchée, hologramme qui recouvre un chiffre, OCR qui invente une boîte | Fit de droite ; hints ; confiance OCR ; pas de refus sur l’OCR seul |
| `compression_inconsistency` | Toute photo iPhone recompressée | Règle « global ⇒ 0 » |
| `screen_replay` | Trame d’impression, filet de sécurité | Veto impulsif / rosace |
| `printed_copy` | CNI offset authentique, moiré d’écran | Veto dalle ; refus fermé tant que le corridor n’est pas dans le jeu |

### 16.2 Trous assumés

1. Retouche puis recompression globale : compression et ELA se taisent.
2. Collage très soigné, même chaîne de capture, bruit homogénéisé : splicing résiduel se tait ; SIFT peut encore voir un copy-move.
3. Recapture d’écran très floue, hors grille : le contrôle qualité doit déjà recapturer ; si elle passe, `screen_replay` peut rester bas.
4. Impression photo haute qualité sur papier glacé : LBP se rapproche d’un support lisse ; le refus reste fermé tant que le jeu ne dit pas le contraire.
5. Une seule image : pas d’attribution PRNU, pas de comparaison recto / verso dans ce service.

### 16.3 Critère de mise en live

Celui du contrat §12, **rejoué** sur `anti-spoof-2`.

1. Jeu tenu à l’écart, versionné, hors git. Étiquettes : authentique capturée, et les cinq alertes (plusieurs étiquettes possibles). Les authentiques incluent des photos téléphone déjà recompressées une fois, en entier, et des pièces officielles du corridor (offset compris).
2. Pour chaque alerte à ouvrir au refus : rappel ≥ 0,90 sur cette étiquette, faux positifs ≤ 0,05 sur les authentiques, **avec les seuils 0,55 / 0,80**, sans les bouger pour faire passer le chiffre.
3. `engine_version` = `anti-spoof-2` et la version du jeu sont notées dans le dossier d’évaluation.

Les datasets PhotoHolmes (Columbia, CASIA, Coverage) servent au **banc interne** des extracteurs splicing / JPEG. Ils ne remplacent pas le jeu corridor : ce sont des scènes naturelles, pas des pièces d’identité. Un bon AUROC sur Columbia n’ouvre pas `decline-codes`.

---

## 17. Budget de temps et parallélisme

Budget inchangé : travail interne borné à 2 s, API à 2,5 s.

Ordre recommandé dans les 2 s, sur CPU :

| Étape | Budget indicatif | Commentaire |
|---|---|---|
| Décodage + EXIF + DCT | ≤ 80 ms | Une fois |
| ELA Q=90 | ≤ 60 ms | Une fois, grand côté ≤ 1 600 px |
| Tesseract borné aux hints, sinon 1 600 px | ≤ 250 ms | Timeout OCR interne 300 ms : pas de boîtes, les extracteurs glyphes valent 0, les autres continuent |
| SIFT | ≤ 200 ms | Nombre de keypoints plafonné |
| Splicebuster + Noisesniffer | ≤ 400 ms | Si Noisesniffer dépasse, on le coupe, Splicebuster reste |
| Zero + DQ | ≤ 150 ms | |
| FFT + LBP + carreaux | ≤ 200 ms | |
| Fusion + JSON | négligeable | |

Si le deadline global saute : `500`, pas un assessment à zéros. Tesseract qui timeout n’est **pas** une panne globale : c’est une absence de boîtes.

---

## 18. Journaux et sécurité

Comme le contrat §13, plus la règle OCR du §10.3.

Journal JSON stdout : `request_id`, `verification_id`, `side`, `duration_ms`, `engine_version`, `forensic_usable`, les cinq scores, et éventuellement `ocr_boxes` (nombre entier de boîtes, pas le texte). Pas d’image, pas de jeton, pas de nom, pas de numéro.

Tesseract écrit parfois des fichiers de cache : `TESSDATA_PREFIX` en lecture seule, `HOME` / `TMP` sur `/tmp` mémoire, comme le reste du conteneur.

PhotoHolmes ne doit pas télécharger de poids au runtime. Le build de l’étage A n’a pas d’accès réseau sortant en production.

---

## 19. Ordre de reconstruction

Ce n’est pas la roadmap S0–S8 (le contrat HTTP existe déjà). C’est l’ordre pour **remplacer les extracteurs** sans casser le banc.

| Palier | Résultat visible dans Swagger du service | `engine_version` |
|---|---|---|
| F0 | Artefacts partagés : `jpeg.py`, carte ELA, FFT, LBP. Les cinq alertes répondent encore avec les extracteurs v1 | `anti-spoof-1` |
| F1 | `compression_inconsistency` bascule sur DQ + Zero + ELA. Recompression globale = 0 | `anti-spoof-2-partial` interdit : on ne publie pas d’engine_version hybride. Internement, flag de détecteur |
| F2 | `splicing` bascule sur Splicebuster + Noisesniffer + SIFT | idem |
| F3 | `text_alteration` bascule sur boîtes Tesseract + ELA de glyphe. Test privacy vert | idem |
| F4 | `screen_replay` et `printed_copy` : veto dalle / gouttes + LBP | idem |
| F5 | Tous les extracteurs v1 retirés. `engine_version = anti-spoof-2`. Tests synthétiques du contrat §10 tous verts | `anti-spoof-2` |
| F6 | Banc Java inchangé (mêmes seuils). Jeu corridor rejoué. `decline-codes` toujours vide | `anti-spoof-2` |
| F7 | Étage B éventuellement, autre image, autre drapeau | `anti-spoof-2` + extracteurs neural |

On ne mélange pas, dans une image de production, un extracteur v1 et un extracteur v2 de la **même** alerte. On peut, pendant F1–F4, basculer alerte par alerte en interne, mais `engine_version` ne devient `anti-spoof-2` qu’à F5, quand les cinq sont nouveaux. Jusque-là le live, s’il est allumé, reste sur le processus `anti-spoof-1`.

Happy path de reconstruction, palier F5 : poster un JPEG dans `/docs`, lire cinq scores, `engine_version: anti-spoof-2`, aucune chaîne OCR dans la réponse.

---

## 20. Glossaire

| Mot | Sens ici |
|---|---|
| Trace | Conséquence physique mesurable d’une étape d’acquisition ou d’une falsification |
| Extracteur | Un calcul qui sort un score brut \[0, 1\] et éventuellement des rectangles |
| PhotoHolmes | Bibliothèque Python (UdelaR / ENS / IMPA / Digital Sense, 2024) pour enchaîner des méthodes forensiques. Pas un détecteur unique |
| Splicebuster | Méthode de Cozzolino et al. : anomalie des co-occurrences du résidu |
| Noisesniffer | Méthode de Gardella et al. : modèle de bruit + NFA a contrario |
| Zero | Détection de l’origine de la grille JPEG (Nikoukhah et al.) |
| DQ | Double quantification des coefficients DCT |
| ELA | Error Level Analysis : différence à une recompression JPEG de qualité fixe |
| DCT | Transformée en cosinus discrète, blocs JPEG 8×8 |
| PRNU | Motif de bruit fixe d’un capteur. Inutilisable en attribution sur une seule vue IDV |
| SIFT | Descripteurs locaux invariants, OpenCV, copy-move |
| FFT | Transformée de Fourier, ici pour le moiré d’écran et la trame |
| Moiré | Figure d’interférence de deux grilles (dalle × Bayer) |
| LBP | Local Binary Patterns, histogramme de texture |
| Tesseract | OCR local. Ici : boîtes de glyphes. Le texte est détruit |
| Étage A | CPU, méthodes explicables, `anti-spoof-2` |
| Étage B | Réseaux (TruFor, CAT-Net, CNN moiré, U-Net, VLM), plus tard |
| JPEG d’origine | Octets stockés au téléversement, pas la copie vision |

---

## 21. Références de méthodes (étage A)

Ces citations ancrent les extracteurs. Elles ne s’affichent pas dans le produit.

- PhotoHolmes — O’Flaherty, Paganini, Sotelo, Umpiérrez, Gardella, Tailanián, Musé, *PhotoHolmes: a Python library for forgery detection in digital images*, 2024. https://github.com/photoholmes/photoholmes
- Splicebuster — Cozzolino, Poggi, Verdoliva, IEEE WIFS 2015
- Noisesniffer — Gardella, Musé, Colom, Morel, IPOL 2024
- Zero — Nikoukhah et al., CVPR Workshops 2019
- DQ — Popescu, Farid / Lin et al., double quantization JPEG
- ELA — usage forensique classique (Krawetz / FotoForensics) : localisation, pas verdict
- SIFT copy-move — Lowe ; Amerini et al. pour le clustering géométrique
- LBP — Ojala, Pietikäinen, Maenpaa, IEEE PAMI 2002
- FFT / moiré — analyse fréquentielle de recapture d’écran (présentation attack)

Les réseaux PhotoHolmes (TruFor, CAT-Net, PSCC-Net, Adaptive CFA, FOCAL, EXIF as Language) sont décrits dans le papier PhotoHolmes, table 2. Ils sont hors de `anti-spoof-2`.
