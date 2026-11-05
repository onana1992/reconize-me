# Spécification — Anti-spoofing documentaire

**Produit :** Recogniz-Me, Identity & Document Verification  
**Composant :** Document Authenticity Engine, service `anti-spoof`  
**Version du document :** 1.0  
**Date :** 5 novembre 2026  
**Statut :** spécification d’implémentation — le service n’existe pas encore  
**Documents liés :**

- [`../cahier-des-charges.md`](../cahier-des-charges.md) §10 à §13 — place du moteur, dataset, annotation
- [`../specs/specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md) §9.6 — la question d’authenticité, et qui tranche
- [`../DocumentIA/cahier-des-charges-document-ia.md`](../DocumentIA/cahier-des-charges-document-ia.md) — la vision lit le document et ne certifie pas qu’il est vrai

---

## 1. Objet

Concevoir, développer et brancher un composant d’anti-spoofing documentaire dans le parcours IDV.

Le composant regarde l’image d’une pièce d’identité déjà acceptée par le contrôle de qualité. Il estime si cette image porte une fraude numérique (retouche, copier-coller, ré-enregistrement local) ou une fraude de présentation (photo d’un écran, photo d’une impression papier). Il renvoie des scores et des zones. Il ne tranche pas le dossier.

Le juge reste `IdvDecisionEngine`. Aujourd’hui, le chemin bac à sable pose `authenticity_stub_pass` sans regarder l’image. Cette spécification remplace ce signal factice, d’abord par un port injectable, ensuite par l’appel au service lorsque le critère de mise en live du §12 est atteint.

## 2. Périmètre

**Dans cette version**

Cinq alertes, deux familles.

| Famille | Code d’alerte | Ce que l’alerte désigne |
|---|---|---|
| Falsification numérique | `splicing` | Un morceau (texte, chiffres, portrait) vient d’une autre image, ou a été dupliqué dans celle-ci. Le portrait remplacé est un cas de splicing. |
| Falsification numérique | `text_alteration` | Des caractères ont été repris localement (date, nom, numéro) dans un logiciel d’édition. |
| Falsification numérique | `compression_inconsistency` | Une zone a été ré-enregistrée à part (qualité JPEG ou grille 8×8 différente du reste). |
| Spoofing de présentation | `screen_replay` | La pièce montrée est l’affichage d’un écran (moniteur ou téléphone), photographié de nouveau. |
| Spoofing de présentation | `printed_copy` | La pièce est une impression jet d’encre ou laser sur papier ordinaire, et non le support officiel. |

Le service est un processus Python interne. L’API Spring l’appelle. Le bac à sable ne l’appelle pas.

**Hors de cette version**

- Liveness du visage, face match, injection caméra, deepfake du titulaire. Cela reste le cycle biométrique.
- Check digits MRZ, dates, accord MRZ / lecture visuelle. Déjà des signaux à côté du moteur.
- Incohérence recto / verso. Prévue par le cahier des charges, pas dans ce livrable. Chaque face est notée seule.
- Hologramme, encre UV, lumière rasante, reflet MRZ sous flash. Une seule photo RVB ne les prouve pas.
- Un jugement du modèle de langage. Le LLM de Document IA ne reçoit pas ce rôle.
- Détection de la pièce par contours, ratios ou coins. Ce service ne cherche pas le document. Il reçoit une image déjà retenue.

## 3. Ancrage dans le code actuel

| Élément | Aujourd’hui | Devenir |
|---|---|---|
| `IdvDecisionEngine.decide` | `authenticity_stub_pass` sur les scénarios approuvés | Le stub reste pour `ky_test_`. De nouveaux scénarios du §8.4 forcent chaque alerte. |
| `IdvDecisionEngine.decideVision` | Aucun score d’authenticité. `rules_version = vision-1` | Après le verdict de lecture, `applyAuthenticity` ajoute les signaux. `rules_version = auth-1` dès que l’appel a lieu. |
| `VisionFrame` | Peut ré-encoder le JPEG en qualité 0,8 au-delà de 1 280 px, pour l’envoi au modèle de lecture | Interdit pour cet appel. Le service reçoit les octets stockés, ceux du téléversement. |
| `ImageQualityGate` | Arrête une image illisible avant la lecture | Même arrêt avant l’anti-spoof. Une image en recapture n’est pas envoyée. |
| Clé `ky_test_` | Zéro appel Textract, zéro appel vision | Zéro appel `anti-spoof`. |
| Webhook | Raisons et signaux, pas de média | Inchangé. Les zones restent hors du webhook. |

## 4. Principes

1. Le service mesure. Le moteur de règles décide. Une réponse Python sans les champs du §7 est rejetée et traitée comme une indisponibilité, pas comme une pièce saine.
2. Un score élevé veut dire « beaucoup d’indices de cette fraude ». Ce n’est pas une probabilité légale. Les seuils qui transforment le score en revue ou en refus vivent dans le moteur Java, versionnés, pas dans le service.
3. L’analyse de compression lit le flux JPEG d’origine (tables de quantification, coefficients DCT). Décoder puis ré-encoder avant l’appel détruit la preuve. L’API envoie les octets stockés.
4. Une seconde compression globale, celle d’un téléphone qui ré-enregistre toute la photo, n’est pas une altération. Seule une zone dont la quantification diffère du reste de l’image peut faire monter `compression_inconsistency`.
5. Tant qu’une alerte n’a pas passé le critère du §12 sur un jeu tenu à l’écart, elle ne peut pas refuser un dossier live. Elle peut encore envoyer en revue. Le drapeau `kyc.anti-spoof.enabled` reste `false` tant que ce jeu n’existe pas.
6. Aucun texte lu sur la pièce ne part vers le service. Les régions d’intérêt éventuelles sont des rectangles, sans le contenu.
7. Les journaux portent `verification_id`, la face, la durée, la version du moteur et les scores. Ils ne portent ni image, ni jeton, ni nom, ni numéro.
8. Le service ne conserve rien. Pas de disque, pas de file, pas de copie de l’image après la réponse.
9. En cas de panne, le dossier part en revue. Il n’est ni approuvé ni refusé pour une fraude que le moteur n’a pas vue.

## 5. Place dans le pipeline

L’anti-spoof part dès que l’image a passé le contrôle de qualité, en parallèle de la lecture vision. Les deux se rejoignent avant la décision. On n’attend pas la fin de l’OCR pour chercher une retouche : les deux lectures sont indépendantes.

```
JPEG stocké (octets d’origine)
        │
        ▼
 ImageQualityGate
        │
        ├─ illisible ──────────────► recapture, aucun appel anti-spoof
        │
        ├─► lecture vision (peut ré-encoder sa propre copie)
        │
        └─► POST anti-spoof (octets d’origine, jamais la copie vision)
                │
                ▼
        applyAuthenticity
                │
                ▼
        décision vision-1 puis auth-1
```

Recto et verso requis sont deux appels. Le score de session est le plus mauvais des deux. Un recto sain ne compense pas un verso signalé.

Budget de temps, par face, hors réseau : p95 ≤ 1,5 s sur l’instance de référence CPU, quatre cœurs. L’API coupe à 2,5 s et passe en revue pour indisponibilité. Le service borne son propre travail à 2 s pour répondre avant cette coupure.

## 6. Détecteurs

Chaque alerte est portée par un ou plusieurs détecteurs. Chacun produit un score brut dans \[0, 1\] et, quand il localise, des rectangles normalisés. La fusion du §8.1 en fait le score d’alerte.

Les méthodes ci-dessous sont des extracteurs de traces. Elles ne décident pas. Le calibrateur du §8.1 est le modèle de cette version : une régression logistique dont les coefficients voyagent avec le service (`app/models/fusion-1.json`). Remplacer un extracteur par un réseau ne change pas le contrat HTTP.

Le prétraitement commun :

- lire l’orientation EXIF et redresser les pixels sans ré-encoder le JPEG analysé par le détecteur de compression ;
- refuser au-delà de 12 mégapixels décodés et au-delà de 8 Mo d’octets ;
- si le plus petit côté est inférieur à 480 px, répondre `forensic_usable: false` sans score ;
- les détecteurs spatiaux travaillent au plus sur un grand côté de 1 600 px, calculé en mémoire, sans écriture JPEG.

### 6.1 Splicing — `splicing`

Trois extracteurs, le score d’alerte est le max après calibration.

| Extracteur | Trace cherchée | Garde-fou |
|---|---|---|
| Copie interne | Blocs dont le contenu se répète hors d’un motif périodique (guillochis, fond de sécurité). | Une répétition régulière du fond est ignorée. |
| Résidu de bruit | Le résidu passe-haut (filtres de type SRM) d’une zone n’a pas la même statistique que le reste de la pièce. C’est le signe d’un collage depuis une autre capture. | Le score exige une composante connexe, pas un pixel isolé. |
| Frontière de collage | Le résidu s’aligne sur un contour fermé qui ne suit pas un bord imprimé du layout. | Ce contour n’est pas un détecteur de pièce. Il ne sert qu’à localiser le collage. |

Le remplacement du portrait est ce détecteur. Si l’appel fournit un rectangle `portrait`, les blocs de ce rectangle sont pondérés plus fort. L’absence du rectangle ne bloque pas l’analyse : elle reste globale.

### 6.2 Altération textuelle — `text_alteration`

| Extracteur | Trace cherchée |
|---|---|
| ELA local | Erreur de recompression, à une qualité fixe, plus haute sur une petite zone que sur le texte voisin. |
| Double quantification locale | Blocs DCT dont l’histogramme de quantification n’est pas celui de la table dominante, et qui tombent dans une zone à forte densité de contours horizontaux (ligne de texte). |
| Résidu de rééchantillonnage | Traces d’interpolation locale, typiques d’un tampon de clonage ou d’un déplacement de glyphes. |

Les zones de texte sont estimées par le service (densité de contours). Un rectangle hint `mrz`, `birth_date`, `document_number`, `surname`, `given_names` augmente le poids de ces blocs. Le hint ne contient pas la chaîne lue.

Un changement de caractère qui ne laisse aucune trace locale (ré-impression complète déjà couverte par `printed_copy`, ou retouche suivie d’une recompression uniforme de toute l’image) peut rester sous le seuil. Le critère du §12 mesure ce trou. Il n’est pas compensé par une règle sur la police.

### 6.3 Incohérence de compression — `compression_inconsistency`

Cet extracteur lit le JPEG. Il ne décode pas pour ré-encoder.

| Mesure | Usage |
|---|---|
| Table de quantification et carte de qualité par bloc 8×8 | Repérer une composante connexe dont la qualité estimée s’écarte de la qualité dominante. |
| Grille décalée | Blocs dont l’énergie DCT indique un second JPEG non aligné sur la grille d’origine (enregistrement Photoshop / GIMP d’une sélection). |
| ELA | Indice de localisation, jamais un refus à lui seul. |

Règle de fusion interne, avant calibration : si l’écart de qualité est global (toute l’image, une seule table), le score brut de cette alerte est 0. Le signal utile est l’écart local.

### 6.4 Rejeu sur écran — `screen_replay`

| Extracteur | Trace cherchée | Garde-fou |
|---|---|---|
| Spectre | Pics étroits et symétriques dans le module de la FFT, à la période d’une grille d’affichage (moiré LCD / OLED). | Un pic large de trame d’impression part vers `printed_copy`, pas ici. |
| Déphasage des canaux | Décalage de phase R/V/B typique d’une recapture de sous-pixels. | Ne refuse pas seul. Il renforce le spectre. |

Un bandeau d’interface (heure, batterie) peut monter un indice auxiliaire. Il ne suffit jamais. Une vraie carte posée devant un moniteur ne doit pas être refusée pour ce qui est hors de la pièce. Si un rectangle `document` est fourni, le spectre est calculé dedans. Sinon, il est calculé sur l’image entière.

### 6.5 Copie papier — `printed_copy`

| Extracteur | Trace cherchée | Garde-fou |
|---|---|---|
| Trame d’impression | Rosace CMJN ou points jet d’encre dans le spectre, géométrie différente du moiré d’écran. | Le pic de `screen_replay` ne compte pas ici. |
| Grain du support | Variance basse fréquence : fibre de papier contre surface lisse d’un polycarbonate. | Signal d’appui. Seul, il plafonne sous le seuil de refus tant que le §12 n’a pas montré un faux-positif acceptable. |

Ce détecteur ne cherche pas un hologramme absent. Une pièce officielle imprimée en offset peut porter une trame : le jeu d’évaluation doit contenir ces pièces du corridor, sinon le seuil de refus de cette alerte reste fermé.

### 6.6 Ce que le service répond quand il ne peut pas mesurer

`forensic_usable: false` et des scores nuls lorsque :

- le décodage échoue ou le fichier n’est pas un JPEG ;
- le plus petit côté est sous 480 px ;
- le décodage dépasse 12 mégapixels.

Les détecteurs spatiaux tournent quand même sur un JPEG dont la table est unique. Seule l’alerte compression reste à 0 dans ce cas, par la règle du §6.3.

## 7. Contrat HTTP

Réseau privé. Pas de route publique, pas de CORS. Authentification : `Authorization: Bearer`, secret partagé avec l’API. Jeton absent ou faux : `401`, corps vide.

`GET /health` → `200`

```json
{ "status": "ok", "engine_version": "anti-spoof-1" }
```

`POST /v1/analyze`  
`Content-Type: application/json`  
Corps maximal : 8 Mo. Au-delà : `413`.

```json
{
  "request_id": "6f1c0e2a-1b4d-4e0a-9c3a-8a1d2f4b6c70",
  "verification_id": "3b9c1a44-7e2d-4f1a-9a55-0c1e8d2a4b11",
  "side": "FRONT",
  "media_type": "image/jpeg",
  "image_base64": "<octets du JPEG stocké>",
  "hints": {
    "document_code": "FRANCE_CNI",
    "rois": [
      { "name": "portrait", "box": [0.06, 0.22, 0.28, 0.46] },
      { "name": "birth_date", "box": [0.40, 0.48, 0.32, 0.06] }
    ]
  }
}
```

| Champ | Règle |
|---|---|
| `request_id`, `verification_id` | UUID. Le premier identifie l’appel, le second le dossier. |
| `side` | `FRONT` ou `BACK`. |
| `media_type` | `image/jpeg` seulement. |
| `image_base64` | JPEG d’origine. Pas la copie produite par `VisionFrame`. |
| `hints` | Optionnel. `document_code` est le code du registre Document IA, ou absent. |
| `rois[].name` | Liste fermée : `document`, `portrait`, `mrz`, `birth_date`, `document_number`, `surname`, `given_names`. |
| `rois[].box` | `[x, y, w, h]` dans \[0, 1\], origine en haut à gauche de l’image redressée. `w` et `h` strictement positifs, boîte dans le cadre. |
| Champs inconnus | `400`. Le service ne devine pas. |

Réponse `200` :

```json
{
  "engine_version": "anti-spoof-1",
  "fusion_version": "fusion-1",
  "forensic_usable": true,
  "authenticity_score": 0.960,
  "tampering_score": 0.040,
  "presentation_score": 0.020,
  "alerts": [
    {
      "code": "splicing",
      "family": "digital_forgery",
      "score": 0.040,
      "regions": []
    },
    {
      "code": "text_alteration",
      "family": "digital_forgery",
      "score": 0.030,
      "regions": []
    },
    {
      "code": "compression_inconsistency",
      "family": "digital_forgery",
      "score": 0.020,
      "regions": []
    },
    {
      "code": "screen_replay",
      "family": "presentation_spoof",
      "score": 0.010,
      "regions": []
    },
    {
      "code": "printed_copy",
      "family": "presentation_spoof",
      "score": 0.020,
      "regions": []
    }
  ]
}
```

Les cinq alertes sont toujours présentes, dans cet ordre. `family` vaut `digital_forgery` ou `presentation_spoof`. `score` est arrondi à trois décimales. `regions` contient au plus cinq boîtes, les plus scorées, même format que les hints, plus un `score` de boîte. Quand `forensic_usable` est `false`, les trois scores de tête sont `null` et chaque alerte a `score: null` et `regions: []`.

`400` : JSON invalide, JPEG illisible, hint hors liste. Corps : `{ "error": "invalid_image" }` ou `{ "error": "invalid_request" }`.  
`413` : `{ "error": "payload_too_large" }`.  
`500` : `{ "error": "internal" }`. Pas de trace, pas d’extrait d’image.

Le service ne renvoie pas `PASS`, `REVIEW` ni `DECLINED`.

## 8. Scores et décision

### 8.1 Fusion, côté Python

Pour chaque extracteur, le score brut passe par la calibration chargée dans `fusion-1.json` (régression logistique par alerte, coefficients figés avec la version).

```
score(alerte) = max des extracteurs calibrés de cette alerte
tampering_score = max(splicing, text_alteration, compression_inconsistency)
presentation_score = max(screen_replay, printed_copy)
authenticity_score = 1 - max(tampering_score, presentation_score)
```

Les trois sont dans \[0, 1\]. Monter `tampering_score` ou `presentation_score`, c’est baisser `authenticity_score`. On ne moyenne pas : une seule alerte forte doit rester visible.

### 8.2 Seuils, côté Java

Provisoires, comme les seuils biométriques. Ils vivent dans `IdvDecisionEngine`, version de règles `auth-1`, et peuvent être repris par la politique d’organisation plus tard. Défaut de plateforme :

| Score d’alerte | Effet sur cette alerte |
|---|---|
| &lt; 0,55 | aucun |
| \[0,55 ; 0,80\) | revue |
| ≥ 0,80 | refus, si le §12 a ouvert le refus pour cette alerte |

Agrégation, après le verdict de lecture :

| Situation | Décision du dossier |
|---|---|
| Image illisible | Recapture. Pas d’appel. |
| Appel impossible, délai dépassé, `5xx`, corps hors contrat | Revue, raison `authenticity_unavailable`. Les raisons de lecture déjà posées restent. |
| `forensic_usable: false` | Revue, raison `authenticity_inconclusive`. |
| Au moins une alerte au seuil de refus, et cette alerte est ouverte | Refus. La raison principale est le code de signal de l’alerte la plus haute. Les autres alertes au-dessus de 0,55 sont aussi des signaux. |
| Alerte au seuil de refus, alerte encore fermée par le §12 | Revue, pas refus. Le signal de l’alerte est quand même enregistré. |
| Aucun refus, au moins une revue | Revue, raison `tampering_suspected`, plus le signal de chaque alerte concernée. |
| Toutes les alertes sous 0,55, image mesurable | Signal `document_authentic`. La décision de lecture est conservée (une pièce saine mais expirée reste refusée). |

Un refus de lecture (pièce inconnue, pièce hors corridor) n’efface pas l’appel : si l’image est lisible, l’assessment est persisté pour la revue. La décision reste le refus de lecture.

### 8.3 Signaux

Langue d’intégration : anglais. `outcome` reprend `SignalOutcome`.

| Code | Outcome | Quand |
|---|---|---|
| `document_authentic` | `PASS` | Mesure faite, toutes les alertes sous 0,55. Le score porté est `authenticity_score`. |
| `splicing_detected` | `FAIL` | Alerte `splicing` ≥ 0,55. Score = score d’alerte. |
| `text_alteration_suspected` | `FAIL` | Alerte `text_alteration` ≥ 0,55. |
| `compression_inconsistency` | `FAIL` | Alerte `compression_inconsistency` ≥ 0,55. |
| `screen_replay_detected` | `FAIL` | Alerte `screen_replay` ≥ 0,55. |
| `printed_copy_detected` | `FAIL` | Alerte `printed_copy` ≥ 0,55. |
| `tampering_suspected` | `FAIL` | Raison de revue quand aucune alerte ouverte n’atteint le refus. |
| `authenticity_inconclusive` | `UNAVAILABLE` | Image non mesurable. |
| `authenticity_unavailable` | `UNAVAILABLE` | Panne, délai, contrat cassé. |
| `authenticity_stub_pass` | `PASS` | Réservé au bac à sable et au live tant que `kyc.anti-spoof.enabled` est faux. |

`authenticity_stub_pass` et `document_authentic` ne sont pas posés ensemble.

### 8.4 Bac à sable

Clé `ky_test_` : aucun HTTP. `IdvDecisionEngine.decide` gagne les scénarios suivants, en plus de ceux déjà livrés.

| `sandbox_scenario` | Décision | Raison |
|---|---|---|
| `splicing` | refus | `splicing_detected` |
| `text_alteration` | refus | `text_alteration_suspected` |
| `compression` | refus | `compression_inconsistency` |
| `screen_replay` | refus | `screen_replay_detected` |
| `printed` | refus | `printed_copy_detected` |
| `authenticity_review` | revue | `tampering_suspected` |

Le scénario `approved` continue de poser `authenticity_stub_pass`.

### 8.5 Port Java

```text
DocumentAuthenticityPort.analyze(byte[] originalJpeg, AuthenticityRequest request)
    → AuthenticityAssessment
```

`AuthenticityRequest` porte `verificationId`, `side`, `documentCode` nullable, et les rectangles. Deux implémentations :

| Classe | Usage |
|---|---|
| `StubDocumentAuthenticity` | Clés de test, et live tant que le drapeau est faux. Réponse fixe : `forensic_usable true`, scores d’alerte à 0, `authenticity_score` à 1. Le moteur pose alors `authenticity_stub_pass` et n’applique pas les seuils. |
| `HttpDocumentAuthenticity` | Live, drapeau vrai. `POST` du §7. |

Propriétés :

| Clé | Défaut | Rôle |
|---|---|---|
| `kyc.anti-spoof.enabled` | `false` | Live seulement. Le test l’ignore. |
| `kyc.anti-spoof.base-url` | vide | Origine interne, sans barre finale. |
| `kyc.anti-spoof.token` | vide | Secret. Absent du dépôt. |
| `kyc.anti-spoof.timeout-ms` | `2500` | Au-delà : `authenticity_unavailable`. |
| `kyc.anti-spoof.decline-at` | `0.80` | Seuil de refus. |
| `kyc.anti-spoof.review-at` | `0.55` | Seuil de revue. |
| `kyc.anti-spoof.decline-codes` | vide | Codes d’alerte autorisés à refuser. Vide : toute alerte forte reste une revue. |

`decline-codes` est la matérialisation du §12. Exemple, le jour où splicing et screen ont passé le jeu : `splicing,screen_replay`.

## 9. Persistance

Table `verification_authenticity_assessments`. Clé primaire `(verification_id, side)`.

| Colonne | Contenu |
|---|---|
| `engine_version`, `fusion_version` | Versions renvoyées, ou `stub` |
| `forensic_usable` | Booléen |
| `authenticity_score`, `tampering_score`, `presentation_score` | Null si non mesuré |
| `alerts_json` | Les cinq alertes, scores et rectangles |
| `duration_ms` | Vu de l’API |
| `created_at` | Horodatage |

Pas d’image dans cette table. L’image reste celle déjà stockée pour la face.

La lecture du dossier (console, et `GET` de vérification du même tenant) expose les scores, les codes et les rectangles. Le webhook de décision n’ajoute que les signaux et les raisons, comme aujourd’hui.

Une seconde analyse de la même face remplace la ligne. On ne garde pas l’historique des essais dans cette table : le journal d’audit suffit.

## 10. Construction du service Python

Emplacement : `services/anti-spoof/`. Processus distinct du JAR. Python 3.12. Image Docker sans GPU pour cette version.

```text
services/anti-spoof/
  Dockerfile
  pyproject.toml
  app/
    main.py            FastAPI, /health et /v1/analyze
    contract.py        modèles Pydantic, rejet des champs inconnus
    pipeline.py        ordonnancement, budget de 2 s
    preprocess.py      EXIF, plafond pixels, aucun ré-encodage du flux compression
    fusion.py          lecture de fusion-1.json
    detectors/
      splicing.py
      text_alteration.py
      compression.py
      screen_replay.py
      printed_copy.py
    models/
      fusion-1.json    coefficients, version fusion-1
  tests/
    test_contract.py
    test_detectors_synthetic.py
    fixtures/          images générées par les tests, jamais une vraie pièce
```

Dépendances d’exécution : FastAPI, Uvicorn, Pydantic v2, NumPy, SciPy, Pillow, OpenCV headless. Pas de PyTorch dans l’image de cette version. Un extracteur appris viendra dans une image suivante, derrière le même contrat.

Image :

- build en deux étapes ;
- utilisateur non root ;
- système de fichiers racine en lecture seule ;
- `/tmp` en mémoire ;
- aucun volume ;
- variable `ANTI_SPOOF_TOKEN` obligatoire au démarrage, sinon le processus refuse de lancer ;
- `GET /health` sans jeton, pour la sonde.

`pipeline.py` appelle les cinq détecteurs. La compression reçoit les octets. Les quatre autres reçoivent l’image redressée en mémoire. Une exception dans un détecteur fait échouer l’appel en `500` : on ne renvoie pas un score partiel qui ressemblerait à une pièce saine.

Journal JSON sur la sortie standard : `request_id`, `verification_id`, `side`, `duration_ms`, `engine_version`, `forensic_usable`, les cinq scores. Niveau info. Le corps et le jeton ne sont pas journalisés.

Jeu de tests synthétiques, généré dans le test, sans fichier binaire de pièce réelle :

| Cas | Attendu |
|---|---|
| Carte uniforme, un seul JPEG | les cinq scores sous 0,55 |
| Rectangle collé depuis une autre texture de bruit | `splicing` au-dessus de `printed_copy` et de `screen_replay` |
| Bande de glyphes repeinte puis ré-encodée à une autre qualité | `text_alteration` et `compression_inconsistency` montent ensemble |
| Ré-enregistrement de toute l’image à une seule qualité | `compression_inconsistency` reste à 0 |
| Grille RVB périodique fine ajoutée | `screen_replay` monte, `printed_copy` reste en dessous |
| Trame de points type jet d’encre | `printed_copy` monte, `screen_replay` reste en dessous |
| Corps sans image, champ inconnu, jeton absent | `400`, `400`, `401` |

Ces tests prouvent que les extracteurs réagissent aux traces pour lesquelles ils sont écrits. Ils ne remplacent pas le critère du §12.

## 11. Branchement API

Ordre dans le flux hébergé, une fois la face acceptée et l’image lisible :

1. Lancer la lecture vision et `DocumentAuthenticityPort.analyze` sur les octets stockés.
2. Persister l’assessment, y compris en cas d’indisponibilité (ligne avec scores nuls et la cause dans le journal, pas dans `alerts_json`).
3. `decideVision`, puis `applyAuthenticity`.
4. Le résultat suit le chemin déjà utilisé vers les signaux de vérification.

`DocumentAnalysisStore` et le laboratoire Document IA passent par le même port, pour que le labo montre les cinq scores. Le labo n’approuve pas un dossier.

Tests Java minimum :

- drapeau faux : aucun client HTTP, signal `authenticity_stub_pass` ;
- clé de test : aucun client HTTP, même si le drapeau est vrai ;
- délai dépassé : revue `authenticity_unavailable`, décision de lecture conservée si elle était déjà un refus ;
- alerte à 0,90 avec code absent de `decline-codes` : revue ;
- même alerte avec le code présent : refus ;
- recompression globale fictive (score compression 0, les autres bas) : pas de signal de fraude ;
- les scénarios du §8.4.

## 12. Critère de mise en live

Le drapeau live reste faux tant que les trois conditions suivantes ne sont pas vraies.

1. Un jeu tenu à l’écart, versionné, couvre le corridor visé. Chaque image porte une étiquette parmi : pièce authentique capturée, `splicing`, `text_alteration`, `compression_inconsistency`, `screen_replay`, `printed_copy`. Une image peut porter plusieurs étiquettes. Les pièces authentiques incluent des photos téléphones déjà recompressées une fois, en entier.
2. Pour chaque alerte que l’on veut autoriser au refus : rappel ≥ 0,90 sur les attaques de cette étiquette, à un taux de faux positifs ≤ 0,05 sur les pièces authentiques du jeu. Mesuré avec les seuils du §8.2, pas en changeant le seuil pour faire passer le chiffre.
3. La version du jeu et `fusion-1` sont notées dans le dossier d’entraînement. `engine_version` renvoyé par le service correspond à ce dossier.

Une alerte sous le critère reste calculée et visible, avec le refus fermé (`decline-codes` ne la contient pas). Une recompression globale du jeu authentique ne doit pas dépasser le taux de faux positifs de `compression_inconsistency`.

Le jeu ne va pas dans git. Pas de pièce client. Les images d’attaque sont produites pour l’évaluation (retouches, recaptures d’écran, impressions) et tenues hors du dépôt, avec la même discipline d’anonymisation que le cahier des charges §12.

## 13. Sécurité

- Le service n’écoute que le réseau interne de la plateforme.
- Un secret par environnement. Rotation : l’API et le service prennent le nouveau secret ensemble. L’ancien cesse de marcher.
- Limite de corps, limite de pixels, rejet des JPEG tronqués : contre une image qui explose en mémoire.
- Le conteneur n’a pas de droit d’écriture hors `/tmp`, et `/tmp` n’est pas un volume.
- L’assessment stocké ne contient pas de donnée lue sur la pièce. Les rectangles disent où regarder, pas ce qui est écrit.
- Isolation des dossiers : inchangée. L’assessment suit `verification_id`. Une autre organisation reçoit 404 sur le dossier, donc sur les scores.

## 14. Exploitation

Sonde : `GET /health`. Échec de la sonde : l’API, si le drapeau est vrai, traite les appels comme `authenticity_unavailable` et met les dossiers lisibles en revue. On ne bascule pas en silence vers `authenticity_stub_pass` sur une clé live.

Indicateurs : nombre d’appels, durée, part de `forensic_usable false`, part de chaque alerte au-dessus de 0,55, part de `authenticity_unavailable`. Pas de contenu d’image.

Le bac à sable et le live désactivé ne dépendent pas de la santé du service.

## 15. Ordre de construction

| Étape | Livrable | Preuve |
|---|---|---|
| 1 | Port, stub, table, `applyAuthenticity`, scénarios bac à sable, drapeau à faux | Tests Java du §11. Aucun processus Python requis. |
| 2 | Service Python, contrat, cinq détecteurs, tests synthétiques, image Docker | `GET /health` et les cas du §10. |
| 3 | `HttpDocumentAuthenticity` branché hors production, drapeau vrai sur un jeu interne | Un JPEG stocké réel du flux, pas la copie `VisionFrame`, revient avec cinq alertes. |
| 4 | Jeu du §12 et `fusion-1.json` calibré | Rapport de rappel et de faux positifs par alerte. |
| 5 | Ouverture du refus, alerte par alerte, via `decline-codes` | Seulement les alertes qui tiennent le critère. |

L’étape 1 peut partir sans modèle. Elle ne change pas la décision live : le signal reste `authenticity_stub_pass`.
