# Roadmap d’implémentation — Anti-spoofing documentaire

**Produit :** Recogniz-Me, Identity & Document Verification  
**Composant :** service Python `anti-spoof`  
**Version du document :** 1.0  
**Date :** 5 novembre 2026  
**Statut :** non commencé  
**Documents liés :**

- [`specification-anti-spoofing.md`](./specification-anti-spoofing.md) — le contrat. Les codes, les seuils et les HTTP sont les siens.
- [`specification-detaillee-anti-spoofing.md`](./specification-detaillee-anti-spoofing.md) — cas d’utilisation, règles, procédures de test
- [`specification-moteur-forensique.md`](./specification-moteur-forensique.md) — reconstruction des extracteurs (`anti-spoof-2`). Paliers F0–F7, après S8.

Ce document dit **dans quel ordre** construire. Un palier n’est pas vert tant que son résultat n’est pas visible dans Swagger, sans ouvrir une session de vérification. Le flow (`HostedFlowService`) ne bouge pas avant **S8**.

Si une phrase d’ici contredit un chiffre du contrat, le contrat gagne. L’ordre de construction, lui, est celui de cette roadmap.

---

## 1. Cible

**Happy path Swagger, palier S0 à S7.** On envoie une image et on lit la réponse dans Swagger. Aucune vérification n’est créée. Aucun selfie. Aucun webhook. Aucun débit. Le parcours hébergé continue de poser `authenticity_stub_pass`.

**Happy path produit, palier S8 seulement.** Une session `ky_live_`, drapeau `kyc.anti-spoof.enabled` à vrai, envoie au service les octets stockés (pas la copie vision). La ligne `verification_authenticity_assessments` existe. Une session `ky_test_` du même moment n’ouvre aucune connexion vers Python.

Le refus automatique (un code dans `decline-codes`) n’est pas un palier de cette construction. Il attend le jeu d’évaluation du contrat §12, après S8.

---

## 2. Pourquoi Swagger avant le flow

Aujourd’hui l’authenticité n’est qu’un signal factice, posé au moment de la décision du parcours. Brancher le service là dès le premier commit oblige à refaire consentement, upload et selfie pour voir un score. On ne verrait pas un détecteur seul, ni un contrat cassé, ni un mauvais JPEG.

Deux Swagger se suivent. Le flow arrive après les deux.

| | Swagger du service (S0–S5) | Banc de l’API (S6–S7) | Session de vérification (S8) |
|---|---|---|---|
| Où | `http://localhost:8090/docs` | `http://localhost:8080/swagger-ui.html` | parcours `/flow/{token}` |
| Qui répond | le processus Python | l’API, qui appelle Python | l’API, dans `HostedFlowService` |
| Auth | `Authorization: Bearer` égal à `ANTI_SPOOF_TOKEN` | clé `ky_live_…` | jeton d’URL du flow |
| Effet de bord | aucun | aucun dossier | décision, signaux, webhook |
| `ky_test_` | cette porte ne connaît pas les clés produit | `403` `sandbox_no_anti_spoof`, zéro appel Python | stub, zéro appel Python |
| Flow | inchangé | inchangé | branché ici seulement |

Le Swagger du service est un outil de construction. Il n’est pas publié. En image de production, `ANTI_SPOOF_DOCS=false` : `/docs` et l’upload multipart répondent 404. L’API continue d’appeler `POST /v1/analyze` en JSON, sur le réseau interne.

Le banc de l’API reprend le geste de Document IA. La route `/v1/anti-spoof/**` n’est pas ajoutée à la liste publique du filtre de clé. Swagger a déjà le scheme `bearer-api-key`. Flag `kyc.anti-spoof.lab-enabled` : `true` en local, `false` hors développement. Flag éteint → **404**.

---

## 3. Ce que Swagger montre

### 3.1 Service Python

Deux opérations. Le JSON est le contrat, celui que l’API appellera. L’upload existe pour qu’on puisse choisir un fichier dans Swagger sans fabriquer un base64 à la main. Les deux passent par le même pipeline. L’upload n’existe que si `ANTI_SPOOF_DOCS=true`.

| Méthode | Rôle |
|---|---|
| `GET /health` | sonde, sans jeton |
| `POST /v1/analyze` | JSON du contrat |
| `POST /v1/analyze-upload` | multipart, champ `file`. Même réponse. Absent si la doc est éteinte. |

Réponse, dès S0, forme du contrat. Les cinq alertes sont là, scores à 0. `engine_version` vaut `anti-spoof-0` tant que les détecteurs ne mesurent pas. Ce numéro dit : la forme est bonne, la mesure ne l’est pas. On ne lit pas ces zéros comme une pièce saine.

À partir de S5, `engine_version` vaut `anti-spoof-1`. Les scores qui restent à 0 sont alors de vraies mesures basses.

| Palier | Ce qui change dans la réponse Swagger |
|---|---|
| S0 | forme complète, scores à 0, `anti-spoof-0` |
| S1 | JPEG illisible → `400` ; trop petit ou trop lourd en pixels → `forensic_usable: false` |
| S2 | `compression_inconsistency` réagit. Une recompression globale reste à 0 |
| S3 | `splicing` réagit |
| S4 | `text_alteration` réagit |
| S5 | `screen_replay` et `printed_copy` réagissent. Version `anti-spoof-1` |

### 3.2 Banc de l’API

`POST /v1/anti-spoof/analyze`, multipart champ `file`. Une seule réponse, qui se complète.

```json
{
  "pipeline": "auth-1",
  "providerCalled": false,
  "engineVersion": null,
  "assessment": null,
  "decision": null
}
```

| Palier | Bloc qui se remplit |
|---|---|
| S6 | `providerCalled: true`, `engineVersion`, `assessment` = le JSON Python. `decision` reste `null` |
| S7 | `decision` : issue, raisons et signaux que `applyAuthenticity` poserait. Rien n’est écrit en base de vérification |

`decision` sur le banc ne crée pas de dossier. C’est un aperçu. Le flow, au palier S8, persiste le même calcul.

Procédure, à chaque palier Python :

1. Lancer le service (`ANTI_SPOOF_DOCS=true`, un jeton de développement).
2. Ouvrir `http://localhost:8090/docs`.
3. **Authorize**, coller le jeton.
4. `POST /v1/analyze-upload` avec le fichier du palier. La réponse **est** le livrable.

Procédure, à partir de S6 :

1. Le service Python tourne.
2. `mvn spring-boot:run` dans `api/`.
3. Ouvrir `http://localhost:8080/swagger-ui.html`.
4. **Authorize**, coller une clé `ky_live_…` (sans le mot Bearer).
5. `POST /v1/anti-spoof/analyze`. Aucune ligne dans `verifications`.

Aucune image et aucun nom dans les logs. Le banc n’a pas de `verification_id` : journaliser la durée, `engine_version`, les cinq scores et le code d’erreur.

---

## 4. Principes d’ordre

1. **Le service se voit dans son Swagger avant que l’API existe.** S0 est le premier commit utile.
2. **Un détecteur à la fois.** On ne branche pas les cinq d’un coup. Chaque palier S2–S5 a une image fabriquée qui le fait monter, et les autres alertes ne montent pas à sa place.
3. **`anti-spoof-0` n’est pas une pièce saine.** Tant que ce numéro est servi, personne ne branche le flow.
4. **Le banc API avant le flow.** S6 et S7 prouvent l’appel et la décision sans session.
5. **Le flow ne bouge pas avant S8.** `HostedFlowService` et `authenticity_stub_pass` restent le chemin live.
6. **Le drapeau produit reste faux au moment du branchement.** S8 pose le code. Il ne l’allume pas en production. `decline-codes` reste vide : une alerte forte mise en revue, elle ne refuse pas.
7. **Les octets du stockage, jamais la copie vision.** Dès S6, le banc envoie le fichier reçu. Au S8, le flow envoie les octets stockés, pas le résultat de `VisionFrame`.

**Arrêt (kill) :** `/docs` joignable sur l’image de production ; banc sans clé ou avec le flag lab oublié en production ; une clé `ky_test_` qui ouvre une connexion Python ; le flow branché avant que S7 soit vert dans Swagger ; une réponse Python qui contient `PASS`, `REVIEW` ou `DECLINED` ; un score partiel renvoyé alors qu’un détecteur a levé une exception ; une image ou un nom dans les logs.

---

## 5. Vue d’ensemble

```
S0 Swagger Python : forme du contrat, scores à 0
        │
        ▼
S1 prétraitement (JPEG, taille, pixels)
        │
        ▼
S2 compression
        │
        ▼
S3 splicing
        │
        ▼
S4 altération de texte
        │
        ▼
S5 écran et papier  →  engine anti-spoof-1
        │
        ▼
S6 banc Swagger de l’API, assessment seul
        │
        ▼
S7 le banc montre la décision, sans dossier
        │
        ▼
S8 le flow appelle le service ; le drapeau produit naît à faux
```

| Palier | Livrable visible | Flow |
|---|---|---|
| **S0** | `/docs`, upload, cinq alertes à 0, `anti-spoof-0` | inchangé |
| **S1** | fichier trop petit → `forensic_usable: false` ; octets au hasard → `400` | inchangé |
| **S2** | recompression globale → compression à 0 ; zone ré-encodée → compression monte | inchangé |
| **S3** | collage de texture → `splicing` monte, écran et papier non | inchangé |
| **S4** | glyphes repris → `text_alteration` monte avec la compression | inchangé |
| **S5** | grille d’écran et trame papier se distinguent. Version `anti-spoof-1` | inchangé |
| **S6** | Swagger API, clé live, `assessment` rempli, zéro ligne `verifications` | inchangé |
| **S7** | le même appel montre `decision` (`auth-1`), toujours zéro dossier | inchangé |
| **S8** | session live, drapeau vrai en local : ligne d’assessment, octets d’origine | branché |

S2 à S5 peuvent se relire dans `/docs` sans relancer l’API. S6 ne démarre pas si S5 n’affiche pas `anti-spoof-1`.

---

## 6. Paliers

### S0 — Swagger du service, forme seule

**Code**

- Dossier `services/anti-spoof/`, Python 3.12, FastAPI.
- `GET /health` sans jeton.
- `POST /v1/analyze` : corps du contrat. Champ inconnu → `400` `invalid_request`. Jeton absent ou faux → `401`, corps vide.
- `POST /v1/analyze-upload` : champ fichier. Même pipeline. Présent seulement si `ANTI_SPOOF_DOCS=true`.
- `/docs` présent dans le même cas, absent sinon.
- JPEG valide : `200`, cinq alertes dans l’ordre du contrat, scores `0.000`, `forensic_usable: true`, `engine_version: anti-spoof-0`, `fusion_version: none`.
- Le processus refuse de démarrer si `ANTI_SPOOF_TOKEN` manque.
- Port d’écoute local : `8090`.

**Swagger**

- Sans Authorize → `401` sur l’analyse.
- Authorize, upload d’un JPEG → `200`, cinq scores à 0, version `anti-spoof-0`.
- Champ JSON `"comment"` en plus → `400`.

**Hors palier :** détecteurs, API Spring, table, flow.

---

### S1 — Prétraitement

**Code**

- Orientation EXIF redressée en mémoire. Le flux JPEG gardé pour la compression n’est pas ré-écrit.
- Plus de 8 Mo → `413` `payload_too_large`.
- Décodage impossible → `400` `invalid_image`.
- Plus petit côté sous 480 px, ou plus de 12 mégapixels → `200`, `forensic_usable: false`, scores `null`.
- Les détecteurs ne sont pas appelés dans ces cas.

**Swagger**

- Fichier qui n’est pas un JPEG → `400` `invalid_image`.
- JPEG de 200 px de côté → `forensic_usable: false`, cinq scores `null`.
- JPEG correct → encore `anti-spoof-0`, scores à 0. S1 ne mesure pas la fraude.

**Hors palier :** les cinq extracteurs.

---

### S2 — Compression

**Code**

- `detectors/compression.py` lit tables et coefficients. Il ne décode pas pour ré-encoder.
- Écart local → score brut qui monte. Écart global (une seule qualité sur toute l’image) → score brut 0, avant calibration.
- Les quatre autres alertes restent à 0.
- `engine_version` reste `anti-spoof-0` : la mesure n’est pas complète.

**Swagger**

- Image fabriquée, toute ré-enregistrée à une qualité → `compression_inconsistency` à 0.
- Même image, une bande ré-encodée à une autre qualité → `compression_inconsistency` au-dessus de 0,55, les autres à 0.
- Le rectangle, s’il est renvoyé, tombe sur la bande.

**Hors palier :** splicing, texte, écran, papier. Pas de calibration apprise : le passage brut → score est l’identité, notée `fusion_version: identity`.

---

### S3 — Splicing

**Code**

- Copie interne, résidu de bruit, frontière de collage. Le fond périodique ne compte pas.
- Hint `portrait` optionnel : il pondère, il n’est pas obligatoire.
- Une exception dans le détecteur → `500` `internal`. Pas de JSON avec les autres scores remplis.

**Swagger**

- Rectangle collé depuis une autre texture → `splicing` au-dessus de `screen_replay` et de `printed_copy` (ceux-là encore à 0).
- Carte uniforme du S0 → `splicing` sous 0,55.

---

### S4 — Altération de texte

**Code**

- ELA local, double quantification sur les lignes de texte, résidu de rééchantillonnage.
- Les hints `mrz`, `birth_date`, `document_number`, `surname`, `given_names` pondèrent. Aucune chaîne n’entre dans la requête.

**Swagger**

- Bande de glyphes repeinte puis ré-encodée à part → `text_alteration` et `compression_inconsistency` montent ensemble.
- Hint `birth_date` sur cette bande → le score de texte ne baisse pas. Hint hors cadre → `400`.

---

### S5 — Écran, papier, version mesurée

**Code**

- Spectre et déphasage pour `screen_replay`. La grille fine ne fait pas monter `printed_copy`.
- Trame d’impression pour `printed_copy`. Le grain seul reste sous 0,80.
- Hint `document` : le spectre d’écran est calculé dans ce rectangle.
- Les cinq détecteurs sont branchés. `engine_version` passe à `anti-spoof-1`. `fusion_version` reste `identity` jusqu’au jeu d’évaluation.
- `pipeline.py` coupe à 2 s. Une exception ou un dépassement → `500`, jamais un score à 0 qui ressemblerait à une pièce saine.

**Swagger**

- Grille RVB fine → `screen_replay` monte, `printed_copy` reste en dessous.
- Trame de points → l’inverse.
- Carte uniforme → cinq scores sous 0,55, et cette fois le numéro est `anti-spoof-1`.
- `GET /health` annonce `anti-spoof-1`.

**Hors palier :** API, flow, coefficients appris. Ce palier est le niveau A du contrat. Il ne remplace pas le jeu tenu à l’écart.

---

### S6 — Banc Swagger de l’API

**Code**

- `AntiSpoofController` sous `/v1/anti-spoof`.
- `kyc.anti-spoof.lab-enabled`. Éteint → 404, la route n’est pas publique pour autant.
- Annotations springdoc : `@Tag`, `@SecurityRequirement(name = "bearer-api-key")`, fichier en `@RequestPart`.
- `HttpDocumentAuthenticity` : `POST` JSON vers `kyc.anti-spoof.base-url`, jeton `kyc.anti-spoof.token`, délai 2,5 s.
- Clé `ky_live_` : l’API envoie les octets du fichier, tels quels. Réponse : `providerCalled: true` et `assessment`.
- Clé `ky_test_` : `403` `sandbox_no_anti_spoof`. Aucun client HTTP construit.
- Service arrêté ou délai dépassé : `502` avec `providerCalled: true` et un code `authenticity_unavailable` dans l’enveloppe. Pas de décision de dossier, puisqu’il n’y a pas de dossier.
- `HostedFlowService` n’est pas modifié. Aucune écriture dans `verifications` ni dans `verification_authenticity_assessments`.

**Swagger**

- Sans Authorize → 401.
- Clé live, JPEG, service S5 en marche → `assessment.engine_version` = `anti-spoof-1`, cinq alertes, `decision: null`.
- Même appel avec `ky_test_` → 403, et le journal Python ne montre pas cet appel.
- Flag lab à `false` → 404.
- L’opération apparaît sans changer `/v1/verifications` ni `/v1/flow`.

**Hors palier :** `applyAuthenticity`, table, scénarios bac à sable du flow.

---

### S7 — La décision, encore sur le banc

**Code**

- `applyAuthenticity`, version de règles `auth-1`, seuils 0,55 et 0,80.
- `kyc.anti-spoof.decline-codes` vide par défaut.
- Le banc remplit `decision` : issue (`PASS`, `REVIEW`, `REJECT`), raisons, signaux. Ce sont les mots du moteur de règles, pas ceux du service Python.
- Score ≥ 0,80 avec le code absent de `decline-codes` → `decision.issue` = `REVIEW`, signal de l’alerte quand même présent.
- Cinq scores sous 0,55 → signal `document_authentic`.
- `forensic_usable: false` → `authenticity_inconclusive`.
- Toujours aucune ligne de vérification.

**Swagger**

- Image du S5 « grille d’écran », `decline-codes` vide → `decision.issue` = `REVIEW`, raison `tampering_suspected`, signal `screen_replay_detected`.
- Même image après avoir ajouté `screen_replay` dans `decline-codes` en local → `REJECT`, raison `screen_replay_detected`.
- JPEG uniforme `anti-spoof-1` → `document_authentic`.
- Compter les lignes de `verifications` avant et après : le nombre ne change pas.

**Hors palier :** persistance, webhook, flow.

---

### S8 — Branchement du flow

On n’ouvre ce palier que si S7 a été exécuté dans Swagger sur une image uniforme et sur une image d’écran.

**Code**

- Table `verification_authenticity_assessments`, clé `(verification_id, side)`.
- Après le contrôle de qualité, en parallèle de la lecture vision : `DocumentAuthenticityPort.analyze` sur les octets stockés.
- Image en recapture : le port n’est pas appelé.
- Puis `decideVision`, puis `applyAuthenticity`. `rules_version` = `auth-1` quand l’appel a lieu.
- Drapeau `kyc.anti-spoof.enabled`, défaut `false`. Faux → stub, signal `authenticity_stub_pass`, aucun HTTP, même si le lab est allumé.
- Clé `ky_test_` → aucun HTTP, quels que soient les drapeaux. Scénarios `splicing`, `text_alteration`, `compression`, `screen_replay`, `printed`, `authenticity_review` du contrat §8.4.
- Drapeau vrai et service en panne → revue `authenticity_unavailable`. On ne revient pas au signal factice.
- Recto et verso : deux appels. Le pire l’emporte.
- Webhook : signaux et raisons. Les rectangles restent sur la lecture du dossier.
- Le banc S6–S7 reste en place.

**Preuve**

- Drapeau faux, clé live : le journal Python reste vide, le signal est `authenticity_stub_pass`.
- Drapeau vrai, clé live, en local : une ligne par face, `engine_version` = `anti-spoof-1`. La taille envoyée est celle de l’objet stocké, pas celle d’une copie ré-encodée par `VisionFrame` (JPEG dont le grand côté dépasse 1 280 px).
- Clé de test, drapeau vrai : scénario `printed` → refus `printed_copy_detected`, journal Python vide.
- Service coupé, drapeau vrai : revue `authenticity_unavailable`, pas `authenticity_stub_pass`.

**Hors palier :** ouvrir un code de refus en production. `decline-codes` reste vide tant que le jeu du contrat §12 n’est pas vert. Allumer le drapeau hors de la machine de développement est un geste d’exploitation, décrit dans la spécification détaillée §11, pas un palier de code.

---

## 7. Après S8

Ces gestes ne commencent pas avant que le flow local du S8 soit vert. Ils ne s’ajoutent pas au même commit que le branchement.

| Geste | Condition |
|---|---|
| Job CI : tests synthétiques S2–S5 et contrat HTTP S0–S1 | l’image Docker existe |
| `fusion-1.json` calibré, `fusion_version` quitte `identity` | jeu tenu à l’écart constitué |
| Un code ajouté à `decline-codes` | rappel ≥ 0,90 et faux positifs ≤ 0,05 pour ce code, seuils non déplacés |
| Drapeau vrai en production | S8 vert, `decline-codes` encore vide, sonde surveillée |

Le positionnement des éléments n’est pas un palier. Il dépend de boîtes mesurées que la lecture ne fournit pas aujourd’hui.
