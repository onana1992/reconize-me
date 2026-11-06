# Spécification détaillée — Service d’authenticité Python

**Produit :** Recogniz-Me, Identity & Document Verification  
**Composant :** service `anti-spoof`  
**Version du document :** 1.0  
**Date :** 5 novembre 2026  
**Statut :** spécification pédagogique — le contrat expliqué  
**Document qui prime :** [`specification-anti-spoofing.md`](./specification-anti-spoofing.md)  
**Ordre de construction :** [`roadmap-implementation-anti-spoofing.md`](./roadmap-implementation-anti-spoofing.md)

Ce texte s’adresse à quatre lecteurs : la personne qui implémente le service Python, la personne qui le branche dans l’API, l’analyste qui lit un dossier en revue, et la personne qui déploie. Il reprend le contrat et le raconte dans l’ordre où on s’en sert. Les codes, les seuils et les codes HTTP sont ceux du contrat. Si une phrase d’ici et une ligne du contrat se contredisent, le contrat gagne.

Le service n’existe pas encore. Tant qu’il n’est pas branché, le parcours live pose `authenticity_stub_pass` sans regarder l’image.

---

## 1. L’idée en une page

Une personne photographie sa pièce d’identité. La photo peut être celle d’une vraie carte. Elle peut aussi être une carte retouchée, une photo d’un écran, ou une impression papier.

Le service Python regarde cette photo et répond par des nombres. Cinq alertes, chacune entre 0 et 1. Plus le nombre est haut, plus la trace de cette fraude est forte. Il renvoie aussi, quand il localise, des rectangles. Il ne dit jamais « approuvé », « revue » ou « refusé ».

L’API Spring lit ces nombres et applique des seuils. Sous 0,55, l’alerte est ignorée. De 0,55 à 0,80, le dossier part en revue. À partir de 0,80, le dossier peut être refusé, et seulement si cette alerte a été ouverte après une évaluation. Une panne n’approuve pas et ne refuse pas : le dossier part en revue.

Deux portes mènent au même moteur de règles.

| | Bac à sable, clé `ky_test_` | Live, drapeau faux | Live, drapeau vrai |
|---|---|---|---|
| Qui est appelé | Personne | Personne | Le service Python |
| Ce que le dossier prouve | Le scénario demandé | Rien sur l’image | Les traces mesurées sur le JPEG stocké |
| Signal si rien ne monte | `authenticity_stub_pass` | `authenticity_stub_pass` | `document_authentic` |

Un `document_authentic` veut dire : les cinq alertes sont sous 0,55 sur cette image. Cela ne veut pas dire que la personne est le titulaire, ni que la pièce n’est pas expirée, ni que le texte lu est juste.

## 2. Les mots

| Mot | Sens |
|---|---|
| Alerte | Une des cinq fraudes mesurées. Code stable, score entre 0 et 1. |
| Famille | Regroupement. `digital_forgery` pour les trois retouches. `presentation_spoof` pour l’écran et le papier. |
| Extracteur | Un calcul à l’intérieur d’une alerte. Il produit un score brut. Il ne décide pas. |
| Calibration | La transformation du score brut en score d’alerte, figée dans `fusion-1.json`. |
| Score d’alerte | Le plus fort des extracteurs calibrés de cette alerte. |
| `tampering_score` | Le plus fort des trois alertes numériques. |
| `presentation_score` | Le plus fort des deux alertes de présentation. |
| `authenticity_score` | `1` moins le plus fort des deux scores ci-dessus. |
| Zone (`region`) | Un rectangle normalisé, entre 0 et 1, là où la trace est la plus forte. |
| Hint | Un rectangle envoyé par l’API pour dire « regarde plus fort ici ». Il ne contient pas le texte lu. |
| JPEG d’origine | Les octets téléversés et stockés. Pas la copie éventuellement ré-encodée pour la lecture vision. |
| Mesurable | `forensic_usable: true`. L’image a pu être lue comme un JPEG exploitable. |
| Seuil de revue | 0,55. En dessous, l’alerte ne produit rien. |
| Seuil de refus | 0,80. Au-dessus, refus seulement si le code est dans `decline-codes`. |
| Drapeau | `kyc.anti-spoof.enabled`. Faux par défaut. Les clés de test l’ignorent. |
| Assessment | La ligne enregistrée par face : versions, scores, rectangles, durée. |

Un score de 0,91 se lit « trace forte ». Il ne se lit pas « 91 % de chances que le document soit faux ».

## 3. Cas d’utilisation

Chaque cas dit qui agit, ce qui est vrai dans le monde, ce que le service renvoie, et ce que le dossier devient. Les chiffres sont ceux du contrat.

### UC-01 — Pièce photographiée, rien à signaler

Le titulaire photographie une carte physique. Le JPEG est net, le plus petit côté fait au moins 480 px. Les cinq scores restent sous 0,55.

Le service renvoie `forensic_usable: true` et un `authenticity_score` proche de 1. L’API pose `document_authentic`. Si la lecture et la biométrie sont saines, le dossier peut être approuvé. L’authenticité seule ne l’approuve pas.

### UC-02 — Date ou nom repris dans un logiciel

Quelqu’un ouvre la photo, change des caractères, et ré-enregistre seulement cette zone. Le reste du JPEG garde son ancienne quantification.

`text_alteration` et `compression_inconsistency` montent ensemble. Si le plus haut atteint 0,80 et que son code est ouvert, le dossier est refusé. La raison principale est le code de l’alerte la plus haute. L’autre alerte, si elle est au-dessus de 0,55, est aussi un signal.

### UC-03 — Portrait ou bloc collé depuis une autre image

Un visage, un numéro ou une bande de texte vient d’une autre capture. Le résidu de bruit de cette zone ne ressemble pas au reste, ou le même motif se répète sans être le fond de sécurité.

`splicing` monte. Le rectangle `portrait`, s’il est fourni, augmente le poids de cette zone. Sans ce rectangle, la mesure reste globale. Le portrait remplacé n’a pas d’alerte à part : c’est un splicing.

### UC-04 — Photo d’un écran

Le titulaire présente un moniteur ou un téléphone qui affiche la pièce. Le spectre montre une grille fine d’affichage, souvent avec un décalage de phase entre les canaux.

`screen_replay` monte. `printed_copy` ne doit pas monter pour la même grille. Un bandeau d’heure ou de batterie peut renforcer l’indice. Il ne suffit jamais, seul, à faire monter l’alerte au refus.

### UC-05 — Impression papier

La pièce a été imprimée au jet d’encre ou au laser, puis photographiée. Le spectre montre une trame de points, pas la grille fine d’un écran.

`printed_copy` monte. Le grain du papier est un appui. Seul, il ne fait pas franchir le seuil de refus tant que l’évaluation ne l’a pas justifié. Le service ne cherche pas un hologramme absent.

### UC-06 — Le téléphone ré-enregistre toute la photo

Beaucoup de captures honnêtes sont recompressées une fois, en entier, par l’appareil. Une seule table de quantification couvre toute l’image.

`compression_inconsistency` reste à 0. Les autres alertes ne montent pas pour ce seul motif. Le dossier n’est pas mis en revue pour une compression globale.

### UC-07 — Retouche puis recompression de toute l’image

Les caractères ont changé, puis le fichier entier a été ré-enregistré à une seule qualité. La trace locale de compression a disparu. `compression_inconsistency` reste à 0. `text_alteration` peut rester sous le seuil. Ce trou est connu. Il est mesuré par le jeu d’évaluation. Il n’est pas bouché par une règle sur la police. Si la pièce a été ré-imprimée, c’est `printed_copy` qui peut la voir.

### UC-08 — Recto sain, verso imprimé

Deux appels, un par face. Le recto est sous 0,55. Le verso porte `printed_copy` au-dessus du seuil. Le dossier suit la face la plus mauvaise. Le recto ne lave pas le verso.

### UC-09 — Image inutilisable

La photo est floue, trop petite, ou le contrôle de qualité a déjà demandé une recapture. L’API n’appelle pas le service. Il n’y a pas de signal de fraude. La décision reste la recapture.

Si une image passe quand même et que le service ne peut pas la mesurer (fichier qui n’est pas un JPEG, plus petit côté sous 480 px, plus de 12 mégapixels), il répond `forensic_usable: false` et des scores nuls. L’API met le dossier en revue avec `authenticity_inconclusive`.

### UC-10 — Le service ne répond pas

Délai au-delà de 2,5 s, HTTP 500, ou JSON qui n’a pas les champs du contrat. L’API met le dossier en revue avec `authenticity_unavailable`. Un refus déjà posé par la lecture (pièce expirée, pièce inconnue) reste un refus. On n’invente pas une pièce saine.

### UC-11 — Alerte forte, refus encore fermé

`splicing` vaut 0,90, et `decline-codes` ne contient pas `splicing`. Le signal `splicing_detected` est enregistré. La décision est la revue, pas le refus. C’est l’état normal tant que le jeu d’évaluation n’a pas ouvert cette alerte.

### UC-12 — Intégrateur en bac à sable

La clé est `ky_test_`. Aucun appel HTTP, même si le drapeau est vrai. Le scénario choisi à la création fixe la décision.

| Scénario | Décision | Raison |
|---|---|---|
| `approved` | approuvée | `authenticity_stub_pass`, avec les signaux biométriques déjà prévus |
| `splicing` | refusée | `splicing_detected` |
| `text_alteration` | refusée | `text_alteration_suspected` |
| `compression` | refusée | `compression_inconsistency` |
| `screen_replay` | refusée | `screen_replay_detected` |
| `printed` | refusée | `printed_copy_detected` |
| `authenticity_review` | revue | `tampering_suspected` |

Ces scénarios servent à brancher le webhook et l’écran. Ils ne prouvent pas que le service verrait la fraude sur une vraie image.

### UC-13 — Analyste en revue

Le dossier est en revue pour `tampering_suspected` ou pour une alerte fermée au refus. L’analyste de la même organisation voit l’image, les cinq scores, et les rectangles. Il approuve ou il refuse. Une autre organisation ne voit pas ce dossier.

Le webhook reçu par l’intégrateur porte les raisons et les signaux. Il ne porte pas les rectangles, ni l’image.

### UC-14 — Laboratoire Document IA

Le labo envoie une image déjà lisible au même port que le parcours. Il affiche les cinq scores. Il n’approuve pas un dossier de vérification.

## 4. Parcours

### 4.1 Parcours du titulaire

Le titulaire ne voit pas les scores. Il voit le parcours qu’il connaît déjà.

```
lien
  → consentement
  → photo du recto
  → qualité
       ├─ insuffisante → on lui redemande une photo
       └─ suffisante → la photo est stockée
            → (en parallèle, invisible pour lui)
                 lecture du texte
                 mesure d'authenticité
  → verso, si cette pièce en demande un
  → visage et présence
  → écran de fin : accepté, refusé, ou « en cours d'examen »
```

Une recapture pour flou n’est pas un refus pour fraude. Un refus pour `screen_replay_detected` ne lui demande pas de recommencer : la décision est prise.

### 4.2 Parcours de l’image

```
téléversement JPEG
  → stockage des octets tels quels
  → ImageQualityGate
       ├─ illisible → recapture, stop
       └─ lisible
            ├─ VisionFrame peut fabriquer une copie plus petite pour le modèle de lecture
            └─ anti-spoof reçoit les octets stockés, jamais cette copie
                 → cinq alertes
                 → ligne d'assessment
                 → applyAuthenticity après le verdict de lecture
```

La copie vision peut être ré-encodée en qualité 0,8 quand le grand côté dépasse 1 280 px. Envoyer cette copie au service efface la preuve de compression. Le branchement juste se reconnaît à ceci : les octets postés sont ceux du stockage.

### 4.3 Parcours de l’analyste

```
file « revue »
  → ouvrir le dossier de son organisation
  → lire la raison (tampering_suspected, ou un code d'alerte)
  → regarder la face signalée
  → voir le rectangle, s'il y en a un
  → approuver ou refuser
  → l'action est journalisée
```

L’analyste ne corrige pas le score. Le score reste celui du moteur. Sa décision remplace la décision machine, comme pour les autres revues.

### 4.4 Parcours de l’intégrateur

Il crée une vérification. Il ne parle jamais au service Python. Il lit, sur le dossier et dans le webhook :

- la décision ;
- les raisons ;
- les signaux, avec le score quand il existe.

Il ne reçoit pas les rectangles dans le webhook. Il les trouve sur la lecture du dossier de son tenant, avec les scores de tête.

### 4.5 Parcours d’exploitation

```
image Docker publiée
  → sonde /health verte
  → secret posé des deux côtés
  → drapeau encore faux
  → les dossiers live portent authenticity_stub_pass
  → drapeau vrai, decline-codes vide
  → une alerte forte met en revue, elle ne refuse pas
  → un code est ajouté à decline-codes
  → cette alerte peut refuser
```

Couper le drapeau sur une clé live ramène `authenticity_stub_pass`. Les dossiers ne sont plus mesurés. Ce n’est pas le bon geste pour un mauvais modèle : vider `decline-codes` garde la mesure et transforme les alertes fortes en revue. Revenir à l’image précédente garde les codes ouverts sur un calcul connu.

## 5. Architecture

```
navigateur (parcours hébergé)
        │ JPEG
        ▼
API Spring  ──── octets d'origine ────►  service Python anti-spoof
        │                                      │
        │                                      ├─ preprocess
        │                                      ├─ 5 détecteurs
        │                                      └─ fusion-1.json
        │ scores + rectangles
        ▼
applyAuthenticity
        │
        ├─ signaux de vérification
        ├─ table verification_authenticity_assessments
        └─ webhook (signaux et raisons seulement)
```

Le service Python n’est pas sur Internet. Pas de CORS, pas de clé d’organisation, pas d’accès à la base, pas d’accès à S3. L’API est le seul client. Elle s’authentifie avec un secret partagé.

À l’intérieur du processus :

| Module | Rôle |
|---|---|
| `app/main.py` | `GET /health` et `POST /v1/analyze`. Vérifie le jeton sur l’analyse. |
| `app/contract.py` | Forme du JSON. Un champ inconnu rejette l’appel. |
| `app/preprocess.py` | Orientation EXIF, plafond de pixels. Ne ré-encode pas le flux lu par la compression. |
| `app/detectors/*` | Un fichier par alerte. Score brut et rectangles. |
| `app/fusion.py` | Applique `fusion-1.json`. Produit les trois scores de tête. |
| `app/pipeline.py` | Ordonne les détecteurs. Coupe à 2 s. Une exception devient un HTTP 500, pas un score partiel. |
| `app/models/fusion-1.json` | Coefficients de la régression logistique. Version `fusion-1`. |

Dépendances d’exécution : Python 3.12, FastAPI, Uvicorn, Pydantic v2, NumPy, SciPy, Pillow, OpenCV headless. Pas de PyTorch dans cette image. Remplacer plus tard un extracteur par un réseau ne change pas l’URL ni le JSON.

Le moteur de langage de Document IA continue de lire le texte. Il ne reçoit pas le droit de dire que la pièce est vraie. Les check digits MRZ et les dates restent des signaux Java, à côté.

### 5.1 Où vivent les points du cahier des charges

Le cahier des charges §10 liste plus de questions que ce service. Voici où chacune est traitée, pour ne pas les chercher au mauvais endroit.

| Question du cahier | Où elle est traitée |
|---|---|
| Zones modifiées, manipulation locale, photo remplacée | Ce service. La photo remplacée est l’alerte `splicing`. |
| Anomalies de compression | Alerte `compression_inconsistency`. |
| Anomalies de texture | Résidu de bruit (`splicing`) et grain du support (`printed_copy`). |
| Capture d’écran, impression | `screen_replay`, `printed_copy`. |
| Cohérence des données, incohérence OCR / MRZ | Moteur Java déjà prévu. Pas un appel Python. |
| Anomalies de police | Pas dans cette version. Une police bizarre ne compense pas une trace absente. |
| Positionnement des éléments | Pas dans ce service. Voir §5.2. |
| Incohérence recto / verso | Pas dans cette version. Chaque face est notée seule. Le pire score gagne. |
| Hologramme, UV, reflet de la MRZ sous flash | Hors de portée d’une photo RVB. |

### 5.2 Positionnement des éléments

Ce contrôle n’est pas une sixième alerte du service Python. Il compare des boîtes à un gabarit. Il a besoin d’une pièce déjà classée et de boîtes mesurées. Les boîtes de Document IA sont aujourd’hui indicatives (`APPROXIMATE`). Elles ne peuvent pas porter ce jugement.

Le jour où un modèle de mise en page fournit des boîtes mesurées, le contrôle se fait dans l’API, après la classification :

1. Sans `document_code` et sans version, on ne compare à rien. Le contrôle est sauté. Il n’approuve pas et il ne refuse pas.
2. Les coordonnées sont ramenées au rectangle de la pièce. Une carte penchée ou décentrée dans la photo ne compte pas : tout le gabarit a bougé ensemble.
3. Pour chaque élément obligatoire de cette version (portrait, nom, date, numéro, MRZ), on mesure deux écarts. L’écart absolu : le centre ou la taille sort de la tolérance du gabarit, tolérance apprise sur des pièces authentiques de cette version. L’écart relatif : le portrait n’est plus à gauche du nom, la MRZ n’est plus la bande la plus basse, la date ne partage plus la ligne de son libellé.
4. Le score est l’écart le plus fort. Les seuils, s’ils sont un jour les mêmes, restent dans le moteur de règles. Un élément facultatif absent ne fait pas monter le score.

Une date réécrite au bon endroit reste `text_alteration`. Une photo collée dans le bon cadre reste `splicing`. Le positionnement ne voit que ce qui n’est pas à sa place.

## 6. Ce qui se passe pendant un appel

`POST /v1/analyze`, corps JSON, 8 Mo au plus.

```
jeton présent et juste ?
  → JSON conforme, champs connus seulement ?
  → JPEG décodable, taille et pixels dans les plafonds ?
  → redresser l'orientation sans ré-encoder les octets de compression
  → cinq détecteurs
  → calibration
  → cinq alertes, toujours, dans l'ordre du contrat
  → trois scores de tête
```

Ordre des alertes dans la réponse : `splicing`, `text_alteration`, `compression_inconsistency`, `screen_replay`, `printed_copy`.

| Étape qui échoue | Réponse |
|---|---|
| Jeton absent ou faux | `401`, corps vide |
| JSON invalide, champ inconnu, hint hors liste, boîte hors cadre | `400`, `{ "error": "invalid_request" }` |
| Octets qui ne sont pas un JPEG lisible | `400`, `{ "error": "invalid_image" }` |
| Corps au-dessus de 8 Mo | `413`, `{ "error": "payload_too_large" }` |
| Plus petit côté sous 480 px, ou plus de 12 mégapixels | `200`, `forensic_usable: false`, scores `null` |
| Exception dans un détecteur, ou budget de 2 s dépassé à l’intérieur | `500`, `{ "error": "internal" }` |

`GET /health` ne demande pas de jeton. Il répond `{ "status": "ok", "engine_version": "anti-spoof-1" }`.

Les hints acceptés sont `document`, `portrait`, `mrz`, `birth_date`, `document_number`, `surname`, `given_names`. Une boîte est `[x, y, w, h]`, origine en haut à gauche de l’image redressée, côtés strictement positifs, boîte entière dans le cadre. `document_code` est informatif. Son absence ne bloque pas l’analyse.

Le service ne écrit rien. Pas de fichier, pas de file, pas de base. Après la réponse, l’image n’est plus là.

Journal, une ligne JSON : `request_id`, `verification_id`, `side`, `duration_ms`, `engine_version`, `forensic_usable`, les cinq scores. Pas le corps, pas le jeton, pas un nom, pas un numéro.

## 7. Les cinq alertes, vues de l’intérieur

Chaque alerte prend le maximum de ses extracteurs après calibration. On ne moyenne pas. Une seule trace forte doit rester visible.

### 7.1 `splicing` — famille `digital_forgery`

Trois extracteurs.

La copie interne cherche des blocs qui se répètent. Le fond guilloché d’une vraie pièce se répète aussi : une répétition régulière est ignorée. Ce qui compte, c’est une copie qui n’appartient pas au motif.

Le résidu de bruit passe l’image dans des filtres passe-haut. Une zone collée depuis une autre capture n’a pas le même grain de capteur que le reste. Un pixel isolé ne suffit pas : il faut une zone connexe.

La frontière de collage cherche un contour fermé dans ce résidu, qui ne suit pas un bord déjà imprimé. Ce contour sert à dessiner le rectangle. Il ne sert pas à trouver la pièce dans la photo.

### 7.2 `text_alteration` — famille `digital_forgery`

L’ELA local recompressse l’image à une qualité fixe, en mémoire, et compare l’erreur. Une petite zone plus abîmée que le texte voisin a souvent été reprise.

La double quantification locale regarde les blocs JPEG dont la quantification n’est pas celle de la table dominante, et qui tombent sur une ligne de texte (forte densité de contours horizontaux). Les rectangles hint d’identité augmentent le poids de ces blocs. Le texte lu n’est pas dans l’appel.

Le résidu de rééchantillonnage cherche les traces d’interpolation d’un tampon ou d’un glyphe déplacé.

### 7.3 `compression_inconsistency` — famille `digital_forgery`

Cet extracteur lit le flux JPEG : tables de quantification et coefficients. Il ne décode pas pour ré-enregistrer.

Il construit une carte de qualité par bloc de 8×8. Une composante dont la qualité s’écarte du reste fait monter le score. Une grille décalée, typique d’une sélection ré-enregistrée dans un logiciel de retouche, fait de même. L’ELA aide à placer le rectangle. Elle ne refuse pas à elle seule.

Règle avant calibration : si toute l’image partage une seule qualité, le score brut vaut 0. C’est le cas UC-06.

### 7.4 `screen_replay` — famille `presentation_spoof`

Le spectre cherche des pics étroits et symétriques, à la période d’une grille d’écran. Un pic large de trame d’impression est laissé à `printed_copy`.

Le déphasage des canaux rouge, vert et bleu renforce ce spectre. Il ne porte pas l’alerte seul.

Si un rectangle `document` est fourni, le spectre est calculé dedans. Une vraie carte posée devant un moniteur ne doit pas être jugée sur le fond. Sans ce rectangle, le spectre porte sur l’image entière.

### 7.5 `printed_copy` — famille `presentation_spoof`

La trame cherche la rosace ou les points d’une impression, avec une géométrie différente du moiré d’écran. Le grain compare une variance basse fréquence : fibre de papier contre surface plus lisse. Le grain est un appui. Seul, il reste sous le seuil de refus tant que l’évaluation ne l’a pas admis.

Une pièce officielle imprimée en offset peut elle-même porter une trame. Le jeu d’évaluation doit contenir ces pièces du corridor. Sinon le refus de cette alerte reste fermé.

### 7.6 Fusion

```
score(alerte)        = max des extracteurs calibrés
tampering_score      = max(splicing, text_alteration, compression_inconsistency)
presentation_score   = max(screen_replay, printed_copy)
authenticity_score   = 1 - max(tampering_score, presentation_score)
```

Les trois scores de tête sont arrondis à trois décimales, dans \[0, 1\]. Quand `forensic_usable` est faux, ils valent `null`, et chaque alerte a `score: null` et une liste de rectangles vide.

Chaque alerte renvoie au plus cinq rectangles, les plus scorés. Un rectangle de réponse a la même forme qu’un hint, plus un `score`.

## 8. Règles de gestion

Les règles RG-01 à RG-09 sont les principes du contrat. Les suivantes décrivent la décision, qui n’appartient pas au service.

| Id | Règle |
|---|---|
| RG-01 | Le service mesure. L’API décide. Une réponse sans les champs attendus est une indisponibilité. |
| RG-02 | Le score n’est pas une probabilité. Les seuils sont dans `IdvDecisionEngine`, version `auth-1`. |
| RG-03 | La compression se lit sur les octets stockés. L’API n’envoie pas la copie `VisionFrame`. |
| RG-04 | Une recompression globale laisse `compression_inconsistency` à 0. Seul un écart local compte. |
| RG-05 | Une alerte absente du jeu d’évaluation, ou sous son critère, ne peut pas refuser. Elle peut mettre en revue. |
| RG-06 | Aucun texte de la pièce n’est envoyé. Un hint est un rectangle. |
| RG-07 | Les journaux ne portent ni image, ni jeton, ni identité lue. |
| RG-08 | Le service ne conserve pas l’image après la réponse. |
| RG-09 | Une panne met en revue. Elle n’approuve pas et elle ne refuse pas au titre de l’authenticité. |
| RG-10 | Image illisible au contrôle de qualité : pas d’appel. |
| RG-11 | Deux faces : deux appels. Le dossier suit la plus mauvaise. |
| RG-12 | Alerte &lt; 0,55 : aucun signal pour elle. |
| RG-13 | Alerte dans \[0,55 ; 0,80\) : signal d’échec, et revue si rien de plus grave n’est ouvert. |
| RG-14 | Alerte ≥ 0,80 et code présent dans `decline-codes` : refus. La raison principale est le signal de l’alerte la plus haute. Les autres alertes ≥ 0,55 restent des signaux. |
| RG-15 | Alerte ≥ 0,80 et code absent de `decline-codes` : revue. Le signal est quand même enregistré. `decline-codes` vide : toute alerte forte reste une revue. |
| RG-16 | Toutes les alertes &lt; 0,55 et image mesurable : signal `document_authentic`, score = `authenticity_score`. La décision de lecture est conservée. Une pièce expirée et saine au sens de l’image reste refusée pour expiration. |
| RG-17 | `forensic_usable: false` : revue, `authenticity_inconclusive`. |
| RG-18 | Délai, HTTP 5xx, corps hors contrat : revue, `authenticity_unavailable`. |
| RG-19 | Un refus de lecture déjà posé le reste. L’assessment est quand même enregistré si l’image était lisible. |
| RG-20 | Clé `ky_test_` : aucun HTTP. Le scénario du bac à sable décide. |
| RG-21 | Drapeau faux sur une clé live : aucun HTTP, signal `authenticity_stub_pass`. Les seuils ne s’appliquent pas. |
| RG-22 | `authenticity_stub_pass` et `document_authentic` ne sont pas posés ensemble. |
| RG-23 | La sonde est rouge et le drapeau est vrai : les dossiers lisibles partent en revue. On ne revient pas en silence au signal factice. |
| RG-24 | Le webhook porte les signaux et les raisons. Les rectangles restent sur la lecture du dossier. |
| RG-25 | Une seconde analyse de la même face remplace la ligne d’assessment. |

Signaux posés par l’API :

| Code | Outcome | Condition |
|---|---|---|
| `document_authentic` | `PASS` | RG-16 |
| `splicing_detected` | `FAIL` | `splicing` ≥ 0,55 |
| `text_alteration_suspected` | `FAIL` | `text_alteration` ≥ 0,55 |
| `compression_inconsistency` | `FAIL` | `compression_inconsistency` ≥ 0,55 |
| `screen_replay_detected` | `FAIL` | `screen_replay` ≥ 0,55 |
| `printed_copy_detected` | `FAIL` | `printed_copy` ≥ 0,55 |
| `tampering_suspected` | `FAIL` | Revue sans refus ouvert |
| `authenticity_inconclusive` | `UNAVAILABLE` | RG-17 |
| `authenticity_unavailable` | `UNAVAILABLE` | RG-18 |
| `authenticity_stub_pass` | `PASS` | RG-20 ou RG-21 |

### 8.1 Deux dossiers chiffrés

**Dossier A.** Scores : splicing 0,20, text_alteration 0,91, compression 0,62, screen 0,04, printed 0,03. `decline-codes` contient `text_alteration`.

`tampering_score` = 0,91. `presentation_score` = 0,04. `authenticity_score` = 0,09. Décision : refus. Raison principale : `text_alteration_suspected`. Signal supplémentaire : `compression_inconsistency`. Les trois autres alertes ne produisent pas de signal.

**Dossier B.** Mêmes scores, `decline-codes` vide. Décision : revue. Raison : `tampering_suspected`. Signaux : `text_alteration_suspected` et `compression_inconsistency`. Rien n’est refusé.

**Dossier C.** Toutes les alertes à 0,10, et la lecture dit pièce expirée. Signal `document_authentic` avec `authenticity_score` 0,90. Décision : refus pour expiration.

## 9. Ce qui est enregistré

Table `verification_authenticity_assessments`. Clé `(verification_id, side)`.

| Colonne | Contenu |
|---|---|
| `engine_version`, `fusion_version` | Valeurs renvoyées, ou `stub` |
| `forensic_usable` | Booléen |
| `authenticity_score`, `tampering_score`, `presentation_score` | Null si non mesuré |
| `alerts_json` | Les cinq alertes, scores et rectangles |
| `duration_ms` | Mesuré par l’API |
| `created_at` | Horodatage |

L’image n’est pas recopiée dans cette table. Une indisponibilité laisse des scores nuls. La cause est dans le journal, pas dans `alerts_json`.

La console et le `GET` du même tenant montrent les scores, les codes et les rectangles. Une autre organisation reçoit 404 sur le dossier, donc sur ces scores.

Propriétés de l’API :

| Clé | Défaut | Effet |
|---|---|---|
| `kyc.anti-spoof.enabled` | `false` | Live seulement |
| `kyc.anti-spoof.base-url` | vide | Origine interne, sans barre finale |
| `kyc.anti-spoof.token` | vide | Secret, hors du dépôt |
| `kyc.anti-spoof.timeout-ms` | `2500` | Au-delà, RG-18 |
| `kyc.anti-spoof.decline-at` | `0.80` | RG-14 |
| `kyc.anti-spoof.review-at` | `0.55` | RG-12 |
| `kyc.anti-spoof.decline-codes` | vide | RG-15 |

## 10. Procédure de test

Quatre niveaux. Chacun prouve une chose précise. Le niveau suivant ne rattrape pas un niveau sauté. Aucun niveau ne commite une vraie pièce d’identité.

### 10.1 Niveau A — extracteurs, sur images fabriquées

Où : `services/anti-spoof`, `pytest`, sans réseau. Les images sont dessinées par le test.

| Cas | Construction | Attendu |
|---|---|---|
| A1 | Carte uniforme, un seul JPEG | cinq scores sous 0,55 |
| A2 | Rectangle collé depuis une autre texture de bruit | `splicing` au-dessus de `printed_copy` et de `screen_replay` |
| A3 | Bande de glyphes repeinte, puis cette bande ré-encodée à une autre qualité | `text_alteration` et `compression_inconsistency` montent ensemble |
| A4 | Tout le fichier ré-enregistré à une seule qualité | `compression_inconsistency` vaut 0 |
| A5 | Grille RVB périodique fine ajoutée | `screen_replay` monte, `printed_copy` reste en dessous |
| A6 | Trame de points type jet d’encre | `printed_copy` monte, `screen_replay` reste en dessous |

Ce niveau prouve que chaque extracteur réagit à la trace pour laquelle il est écrit. Il ne prouve pas le taux de faux positifs sur de vraies pièces.

### 10.2 Niveau B — contrat HTTP

Lancer le processus en local avec un jeton de test. Appeler avec `curl` ou le client de test.

| Cas | Geste | Attendu |
|---|---|---|
| B1 | `GET /health` sans jeton | `200`, `engine_version` = `anti-spoof-1` |
| B2 | `POST /v1/analyze` sans jeton | `401`, corps vide |
| B3 | Jeton juste, champ `"comment"` en plus | `400`, `invalid_request` |
| B4 | Jeton juste, `image_base64` vide ou octets au hasard | `400`, `invalid_image` |
| B5 | Corps au-dessus de 8 Mo | `413`, `payload_too_large` |
| B6 | JPEG de l’A1, hint `portrait` valide | `200`, cinq alertes dans l’ordre, scores à trois décimales |
| B7 | Boîte `[1.2, 0, 0.1, 0.1]` ou nom de hint `address` | `400`, `invalid_request` |
| B8 | JPEG dont le plus petit côté est sous 480 px | `200`, `forensic_usable` faux, scores `null` |
| B9 | Relire le journal de B6 | les identifiants et les scores sont là, le base64 n’y est pas |

### 10.3 Niveau C — décision Java

Pas de processus Python. Le port est remplacé par un faux qui renvoie des scores.

| Cas | Entrée | Attendu |
|---|---|---|
| C1 | Drapeau faux, clé live | aucun HTTP, `authenticity_stub_pass` |
| C2 | Drapeau vrai, clé `ky_test_`, scénario `screen_replay` | aucun HTTP, refus `screen_replay_detected` |
| C3 | Cinq scores à 0,10, lecture approuvable | `document_authentic`, pas de signal de fraude |
| C4 | Dossier A du §8.1 | refus, deux signaux |
| C5 | Dossier B du §8.1 | revue `tampering_suspected` |
| C6 | Dossier C du §8.1 | refus pour expiration, et `document_authentic` |
| C7 | Le faux port dépasse le délai | revue `authenticity_unavailable` |
| C8 | Lecture déjà en refus « pièce inconnue », scores mesurés | le refus de lecture reste, la ligne d’assessment existe |
| C9 | Image en recapture | le port n’est pas appelé |
| C10 | Recto à 0,10, verso `printed_copy` à 0,90, code ouvert | le dossier suit le verso |

Les scénarios `splicing`, `text_alteration`, `compression`, `printed`, `authenticity_review` sont exercés comme C2.

### 10.4 Niveau D — les octets justes

Sur un environnement interne, drapeau vrai, une pièce de travail (pas un client).

1. Téléverser un JPEG dont le grand côté dépasse 1 280 px.
2. Vérifier que la lecture vision peut ré-encoder sa copie.
3. Vérifier, sur la trace de l’appel Python, que la taille du corps correspond au fichier stocké, pas à la copie vision.
4. La réponse contient les cinq alertes.

Si l’étape 3 échoue, le déploiement ne continue pas. La compression serait mesurée sur une image déjà détruite.

### 10.5 Niveau E — évaluation, hors intégration continue

Ce niveau ouvre le droit de refuser. Il ne tourne pas dans GitHub Actions. Le jeu ne va pas dans git. Pas de pièce client.

1. Constituer un jeu versionné du corridor visé. Étiquettes : pièce authentique capturée, `splicing`, `text_alteration`, `compression_inconsistency`, `screen_replay`, `printed_copy`. Plusieurs étiquettes possibles sur la même image.
2. Inclure des photos de téléphone recompressées une fois, en entier, dans les authentiques.
3. Inclure des pièces officielles du corridor qui portent une trame d’offset, avant d’ouvrir `printed_copy`.
4. Calculer, avec les seuils 0,55 et 0,80, sans les bouger pour faire passer le chiffre : pour chaque alerte candidate au refus, rappel ≥ 0,90 sur ses attaques, et taux de faux positifs ≤ 0,05 sur les authentiques.
5. Vérifier que la recompression globale des authentiques ne fait pas dépasser ce taux pour `compression_inconsistency`.
6. Noter la version du jeu et `fusion-1` dans le dossier d’entraînement. `engine_version` servi doit correspondre.
7. Ajouter à `decline-codes` uniquement les codes qui tiennent ces chiffres.

Une alerte sous le critère reste calculée. Son refus reste fermé.

### 10.6 Niveau F — panne

Drapeau vrai, service arrêté ou sonde rouge.

1. Une vérification live sur une image lisible se termine en revue, raison `authenticity_unavailable`.
2. Le signal n’est pas `authenticity_stub_pass`.
3. Le bac à sable, lui, se termine selon son scénario. Il ne dépend pas de la sonde.

## 11. Déploiement

Le service suit le même geste que le reste de la plateforme : image Docker, sonde, secret hors du dépôt, pas de déploiement automatique à chaque push. Il n’est pas dans le blueprint Render public tant que le niveau D n’a pas été fait sur un environnement interne. Le mettre sur Internet serait une faute : il n’a pas de notion d’organisation.

### 11.1 Image

Build en deux étapes. Utilisateur non root. Racine en lecture seule. `/tmp` en mémoire, sans volume. Variable `ANTI_SPOOF_TOKEN` obligatoire : si elle manque, le processus refuse de démarrer. La sonde est `GET /health`.

Aucun secret dans l’image. `fusion-1.json` est un fichier de coefficients, versionné avec le code. Ce n’est pas un secret. Changer les coefficients change `fusion_version` et demande de refaire le niveau E pour les codes déjà ouverts.

### 11.2 Environnement local

Le compose actuel porte MySQL et Redis. Le service s’ajoute à côté, sur le réseau interne de compose. L’API reçoit :

- `kyc.anti-spoof.base-url` pointé sur le nom du service, pas sur une adresse publique ;
- `kyc.anti-spoof.token` égal à `ANTI_SPOOF_TOKEN` ;
- `kyc.anti-spoof.enabled=false` tant que les niveaux A, B et C ne sont pas verts.

Le port n’est pas publié sur la machine hôte, sauf le temps du niveau B, et seulement en local.

Ordre de vérification locale :

1. `GET /health` depuis le réseau compose.
2. Niveau B.
3. Démarrer l’API avec le drapeau faux. Une clé live ne produit aucun appel. Le journal Python reste vide.
4. Passer le drapeau à vrai sur cette API locale. Une clé de test ne produit toujours aucun appel.
5. Une clé live locale, sur une image de travail, produit une ligne d’assessment.

### 11.3 Environnement interne, puis production

| Étape | Geste | Preuve que l’on attend |
|---|---|---|
| 1 | Publier l’image. Sonde verte. Drapeau encore faux partout. | Les dossiers live portent `authenticity_stub_pass`. Le service peut être arrêté sans effet sur eux. |
| 2 | Poser le même secret dans le service et dans l’API. `base-url` interne. | Un appel manuel authentifié depuis le réseau de l’API répond 200. Un appel sans jeton répond 401. |
| 3 | Drapeau vrai sur l’environnement interne seulement. `decline-codes` vide. | Niveau D. Une alerte forte met en revue, elle ne refuse pas. |
| 4 | Niveau E. | Rapport de rappel et de faux positifs par alerte. |
| 5 | Drapeau vrai en production. `decline-codes` encore vide. | Les dossiers lisibles sont mesurés. Aucun refus nouveau. Les revues sont surveillées. |
| 6 | Ajouter un code à `decline-codes`, un seul à la fois. | Ce code peut refuser. Les autres alertes fortes restent en revue. |

La CI gagne un job : les niveaux A et B sur l’image, avant qu’un déploiement du service soit demandé. Les niveaux D, E et F restent des gestes humains, notés avec la version d’image.

### 11.4 Reprise et retour arrière

| Incident | Geste |
|---|---|
| Sonde rouge en production, drapeau vrai | Laisser le drapeau vrai. Les dossiers partent en revue (RG-23). Réparer l’image. Ne pas basculer au signal factice pour « débloquer ». |
| Trop de revues après un nouveau `fusion-1` | Revenir à l’image précédente, ou vider `decline-codes` si des refus ont déjà été ouverts sur cette image. |
| Un code refuse à tort | Retirer ce code de `decline-codes`. La mesure continue. Les dossiers concernés passent en revue. |
| Il faut arrêter toute mesure | Drapeau faux. Les nouveaux dossiers live portent `authenticity_stub_pass`. C’est un arrêt du contrôle, pas une réparation. |

Rotation du secret : le poser des deux côtés dans la même fenêtre. L’ancien jeton cesse de répondre `200`. Un décalage se voit comme une vague de `authenticity_unavailable`.

### 11.5 Ce que l’on surveille

Nombre d’appels, durée, part de `forensic_usable` faux, part de chaque alerte au-dessus de 0,55, part de `authenticity_unavailable`. Pas de contenu d’image dans ces compteurs.

Un palier utile : la durée p95 par face, hors réseau, reste sous 1,5 s sur l’instance de référence à quatre cœurs. Au-delà, le délai de 2,5 s de l’API commence à transformer des mesures en revues pour panne.

## 12. Ordre de construction

L’ordre est celui de [`roadmap-implementation-anti-spoofing.md`](./roadmap-implementation-anti-spoofing.md). On s’arrête à un palier tant que sa réponse n’est pas celle attendue dans Swagger. Le flow reste sur `authenticity_stub_pass` jusqu’au palier S8.
