# Guide d’implémentation — Sprint 2

**Plateforme :** Recogniz-Me  
**Produit :** Identity & Document Verification (IDV)  
**Sprint :** S2 — capture document, médias, qualité, dataset  
**Version :** 1.0  
**Date :** 31 août 2026  
**Statut :** à implémenter (S1 livré)  
**Modules :** M2 Capture & médias · amorçage M3 Dataset  

**Documents liés :**
- [`specification-fonctionnelle-idv.md`](./specification-fonctionnelle-idv.md) — spec métier (§8.4, §9.1)
- [`guide-implementation-s1.md`](./guide-implementation-s1.md) — fondation, ne pas casser
- [`roadmap-implementation-idv.md`](./roadmap-implementation-idv.md) — ordre de build

Ce document dit **comment construire S2**. Pas d’OCR, pas de SageMaker, pas de selfie.

---

## 1. Livrable démontrable

Après consentement, l’applicant photographie une **pièce (recto)** :

1. Qualité **client** (`@kyc/capture-sdk`) — flou / lumière / cadrage → recapture **sans** upload.
2. API flow : URL **signée** ; le navigateur **PUT** le fichier (jamais de binaire dans le JSON métier).
3. Qualité **serveur** (objet existant, MIME, taille, dimensions).
4. OK → session en attente selfie (écran d’attente, **pas** de caméra visage). KO → recapture **même session**. 3 échecs → *refusée*.

**Kill :** média d’un tenant lisible par un autre, token ou PII dans les logs, binaire dans l’API, heuristiques de coins, lancement OCR / SageMaker.

Si ce livrable n’est pas là, **ne pas** démarrer S3.

---

## 2. Périmètre

| In | Out |
|---|---|
| Caméra pièce, capture **manuelle** | Auto-détection des coins |
| Qualité client réelle (plus le stub `usable: true`) | Textract, MRZ, classification |
| `uploads` + PUT signé + `complete` sous `/v1/flow/{token}` | Upload par clé API (parcours V1 = hosted) |
| Qualité serveur : JPEG/PNG, ~10 Mo max, côté court ≥ 720 px | Selfie, liveness (S4) |
| Statuts `document`, `recapture_requested`, attente selfie | `processing`, `approved`, `review` |
| Recapture, plafond 3 essais | Verso obligatoire (type inconnu avant S3) |
| Adapter stockage local **fonctionnel** (filesystem ou MinIO/LocalStack) | Worker d’inférence |
| Protocole d’annotation + freeze corridor (décision) | Entraînement modèle |

S1 reste inchangé : isolation 404, consentement unique, erreurs `{ error }`, pas de PII dans le flow.

---

## 3. État de départ (S1)

- Consentement → `"next": "capture_unavailable"` ; flow : « capture au sprint 2 ».
- `ObjectStoragePort.createSignedUploadUrl` → URL `localhost:4566` **sans** PUT réel.
- `packages/capture-sdk` : `assessDocumentFrame` toujours utilisable.
- Statuts : pas de `DOCUMENT` / `RECAPTURE_REQUESTED`.

À brancher en fin de sprint : `next: capture_document`.

---

## 4. Contrats flow

Auth = **possession du token** (comme S1). Pas de clé API.

| Méthode | Chemin | Rôle |
|---|---|---|
| `POST` | `/v1/flow/{token}/document/uploads` | Crée une tentative ; retourne `upload_url`, `object_key`, `expires_at`, `attempt` |
| `PUT` | URL signée (stockage) | Binaire ; `Content-Type` figé à la signature |
| `POST` | `/v1/flow/{token}/document/complete` | Corps `{ "attempt": n }` : objet présent, qualité serveur, statut |

Garde-fous :

- Pas de consentement accepté / `declined` / `expired` / `cancelled` → **409** ou **404** (ne pas ouvrir l’upload).
- Token org A ne signe **jamais** une clé `org/{orgB}/…`.
- `complete` sans objet → 4xx, statut inchangé (pas « accepté »).

Écrans :

| Statut | UI |
|---|---|
| `document` | Caméra pièce |
| `recapture_requested` | Même caméra + message qualité + essai restant |
| Qualité OK (ex. `pending_selfie`) | Attente « selfie au sprint suivant » |
| Plafond d’essais | Refusée, plus d’upload |

---

## 5. Données

Flyway `V4__documents.sql` (noms indicatifs) :

- `documents` : `id`, `organization_id`, `verification_id`, `attempt` (1..3), `side` (`front` en S2), `object_key`, `content_type`, `byte_size`, `width`, `height`, `client_usable`, `server_quality` (`pending` / `pass` / `fail`), `fail_code`, `created_at`
- Unique `(verification_id, attempt)`
- Toutes les lectures filtrées par `organization_id`

Nouveaux `VerificationStatus` : `DOCUMENT`, `RECAPTURE_REQUESTED`. Après qualité OK : p.ex. `PENDING_SELFIE` (écran d’attente seulement).

Clé objet :

`org/{organization_id}/verifications/{verification_id}/document/{attempt}`

Audit (sans PII, sans token) : `document.upload_started`, `document.quality_failed`, `document.accepted`. Payload : `attempt`, codes qualité.

---

## 6. Qualité — deux couches

**Client — `packages/capture-sdk`**

Remplacer le placeholder. Sur `ImageData` : flou (variance), luminance (`too_dark` / `too_bright`), glare, `cropped` / `too_small`, `low_resolution`. `usable: false` → l’UI recapture, **zéro** `uploads`.

Messages applicant en langage courant, pas les codes (`blur_detected`, …).

**Serveur — après PUT, avant toute file d’analyse**

- L’objet existe
- `Content-Type` ∈ `image/jpeg`, `image/png`
- Taille entre un minimum et ~10 Mo
- Décodage → largeur / hauteur ; côté court ≥ 720
- Échec → `recapture_requested` + code (`image_unusable`, …) ; **pas** d’enqueue

S2 : `QueuePort.publish` peut rester no-op. **Interdit :** Canny, plus grand quad, OCR.

---

## 7. Stockage

Étendre `ObjectStoragePort` : `presignPut`, `exists`, métadonnées (taille). Adapter **local réel** (filesystem ou MinIO/LocalStack dans `infra/docker-compose.yml`). TTL upload court (ex. 5 min). CORS bucket pour `http://localhost:3001` si PUT direct S3.

Ne plus s’appuyer sur l’URL no-op `localhost:4566` sans serveur.

---

## 8. UI (`web/flow`)

Après acceptation du consentement :

1. Permission caméra (idéalement capteur arrière).
2. Overlay de cadrage, déclenchement manuel.
3. `assessDocumentFrame` → issues ou poursuite.
4. `uploads` → `PUT` → `complete`.
5. Recapture : même page, « Essai *n* sur 3 ».

Console : badges des nouveaux statuts. **Pas** d’aperçu média signé (S6). Should : indiquer « document reçu ».

`SecurityConfig` : CORS API inchangé pour le JSON flow. PUT fichier = CORS **stockage**, pas Spring, si PUT direct.

---

## 9. Dataset (M3 — amorçage)

Sans corpus annoté, S3 (détection SageMaker) est bloqué. En S2 on **n’entraîne pas**.

- Uniquement des images déjà couvertes par un consentement `accepted`.
- Protocole d’annotation (doc court dans `docs/` ou ce sprint) : 4 coins, pays × type, `fraud_type` / `tampering_region` le cas échéant.
- Préfixe séparé `dataset/…` ou export manuel — ne pas servir le dataset comme média de session.
- **Avant la fin S2 :** figer le corridor V1 (2–4 pays, passeport + carte d’identité). Liste écrite, même si le volume d’images est encore petit.

---

## 10. Tests go / no-go

Les tests S1 restent verts. Ajouter :

| Id | Cas |
|---|---|
| T01 | Consentement accepté → `uploads` 201 + URL |
| T02 | Sans consentement / refusé → pas d’upload |
| T03 | Token A ne produit pas une clé `org/{B}/…` |
| T04 | `complete` sans objet → 4xx, pas de statut accepté |
| T05 | JPEG trop petit → recapture, `attempt` +1 |
| T06 | 3e échec qualité → `declined`, plus d’upload |
| T07 | Recapture = **même** `verification_id` |
| T08 | SDK : frame floue → `usable: false` |
| T09 | Logs : pas de token brut |
| T10 | PUT réel vers l’adapter local (pas seulement le JSON MockMvc) |

---

## 11. Ordre de build dans le sprint

1. Migration + statuts + entité `Document`
2. Port stockage (`presignPut`, `exists`, méta) + adapter local
3. Endpoints `uploads` / `complete` + gardes statut / plafond
4. Qualité serveur
5. SDK + écran caméra
6. `next: capture_document` au consentement
7. Protocole dataset + freeze corridor
8. Tests T01–T10 + non-régression S1

---

## 12. Décisions figées S2

| Sujet | Décision |
|---|---|
| Face | Recto seulement |
| Capture | Manuelle |
| Max essais | 3, **même** session |
| Plafond | Refus (pas de revue : S6) |
| Binaire | Jamais dans le JSON métier |
| Analyse / coins / OCR | Interdits |
| Selfie | Interdit (écran d’attente seulement) |

---

## 13. Suite

S3 = inférence SageMaker **détection** + **classification**, lecture, MRZ déterministe. Interdit en prod : détecteur de contours heuristique. Prérequis : dataset S2 + corridor figé.
