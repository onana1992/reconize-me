# Roadmap d’implémentation — M4 Capture + IDV stub

**Plateforme :** Recogniz-Me  
**Livrable :** parcours sandbox Identity & Document Verification, décision justifiée, 0 AWS  
**Version du document :** 1.0  
**Date :** 10 septembre 2026  
**Statut :** ordre de build (cycle C)  
**Documents liés :**
- [`specification-m4-capture-idv-stub.md`](./specification-m4-capture-idv-stub.md) — *quoi* (périmètre, données, UC, API, stub)
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — §11 stub, critère §17.8
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — calendrier M0–M6 ; M5 = AWS + webhooks
- [`../specs/specification-implementation-idv.md`](../specs/specification-implementation-idv.md) — contrats HTTP P1–P3 (Flyway **V12+**, pas V8)
- [`../specs/specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md) — métier
- [`specification-m2-compte-client.md`](./specification-m2-compte-client.md) — cookie, clés, `VERIFICATION_*`
- [`charte-visuelle.md`](./charte-visuelle.md) — `flow.css` vs `console.css`

Ce document dit **quand** et **dans quel ordre** on construit M4. Un palier n’est pas vert sans son **livrable démontrable**. Le *quoi* reste dans la [spec M4](./specification-m4-capture-idv-stub.md).

La [roadmap IDV S3–S9](../specs/roadmap-implementation-idv.md) (SageMaker) **n’est pas** ce calendrier. Elle reprend après M6.

---

## 1. Cible

**Happy path :** un opérateur sandbox crée une vérification, copie le lien, l’applicant consent, photographie une pièce puis son visage, et la console affiche une **décision + raisons** — sans Textract, sans Rekognition, sans webhook.

Sans C1, il n’y a pas de dossier ni de lien (tables droppées en V7, create console = **410**). Sans C2, le flow s’arrête à « capture bientôt ». Sans C3, pas de critère CDC §17.8. Sans C4, la revue et l’isolation médias restent des trous.

---

## 2. Principes d’ordre

1. **Contrat avant caméra** — session, isolation, lien, consentement. Pas d’upload tant que `next` n’est pas `capture_document`.
2. **Qualité avant stub** — recapture sans PUT ; filet serveur avant toute « lecture ».
3. **Pièce avant selfie** — pas de liveness sur une session sans document acceptable.
4. **Stub avant AWS** — les ports `DocumentAiPort` / `BiometricAiPort` n’ont que l’adaptateur stub en C3. `Aws*` = M5.
5. **Décision avant revue** — la file `review` n’a de sens que si les règles produisent déjà ce statut.
6. **Un livrable démontrable par palier.** Pas de dataset, pas de Canny vendu comme moteur, pas de webhook.

**Kill (tout le cycle C) :** fuite de dossier ou de média entre orgs ; binaire dans le JSON métier ; token ou PII applicant dans les logs / le JSON flow ; décision sans raisons ; lien optionnel à la création ; appel AWS IA depuis le sandbox.

---

## 3. Vue d’ensemble

Durée indicative : **~2 semaines tendu**, **~3 semaines confortable**. Le CDC place M4 à 2 semaines : C1–C3 enchaînés, C4 dans la foulée de C3 si la revue glisse.

```
M2 comptes (fait) ──► C1 contrat (session, lien, consentement)
                      │
                      ▼
                 C2 capture pièce (sdk + PUT signé + recapture)
                      │
                      ▼
                 C3 selfie + stub + décision
                      │
                      ▼
                 C4 revue + isolation médias + démo §17.8
                      │
                      ▼
                 M5 AWS + webhooks   (après C4 et M3)
```

| Palier | Livrable démontrable | Statut |
|---|---|---|
| **C1** | Créer (clé **ou** console) → lien → consentir / refuser → relire côté même tenant ; org B **404** | à faire |
| **C2** | Après acceptation : photo pièce, qualité client + serveur, recapture plafond 3, `next: capture_document` | à faire |
| **C3** | Selfie + liveness stub → `processing` → `approved` / `declined` / `review` + raisons en console | à faire |
| **C4** | Analyste du client tranche `review` ; médias URL signée ; 0 AWS ; isolation objet | à faire |

Les paliers C1 / C2 / C3 correspondent à P1 / P2 / P3 de la spec d’implémentation, **bornés au stub** (P3 vision AWS = M5).

### Parallélisme autorisé

| En même temps | Condition |
|---|---|
| **C1 ∥ T** (déjà livré) | — |
| **C1–C4 ∥ M3** | M3 n’est pas un prérequis de M4 ; M5 exige **les deux** |
| `web/flow` scaffold ∥ C1 API | CORS `:3001` dès le premier GET flow |
| `capture-sdk` ∥ fin C1 | Pas d’upload avant C2 |
| Textract / webhooks | **M5**, interdit avant C4 vert |

Interdit : C2 sans consentement persisté ; C3 sans objet réellement stocké ; C4 avec décision sans raisons ; M5 sans C4.

---

## 4. État de départ (ne pas reconstruire)

| Surface | État |
|---|---|
| Compte / session / clés `ky_test_` | **Livré** (M2 + T) |
| Droits `VERIFICATION_READ` / `_WRITE` | **Livré** (T2) ; métier encore stub |
| `POST /v1/console/verifications` | **410** `idv_unavailable` |
| `GET /v1/console/verifications` | **200** `[]` |
| `GET /v1/verifications` + Bearer | **401** sans clé ; **404** avec clé (pas de contrôleur) |
| Tables `verifications`, `consents`, `idempotency_keys` | **Droppées** (V7) |
| Flyway | V1–V11 pris (équipe jusqu’à V11) → IDV = **V12+** |
| `web/flow`, `packages/capture-sdk` | **Absents** |
| `packages/brand/flow.css` | **Présent** (M0) |
| Console `/identity` | Teaser / empty sessions, chrome stable |

S1 historique (session + consentement) a été **retiré**. C1 le reprend sur le compte actuel, pas à côté.

---

## 5. Décisions figées (rappel)

Détail : spec M4 §2.4.

| Sujet | Choix |
|---|---|
| Corridor | Passeport **CA, FR** ; CNI **FR** |
| Pièce expirée | Refus |
| Essais | 3 pièce, 3 selfie |
| CDC REJECTED | API `declined` |
| Worker M4 | In-process, pas de SQS |
| Stub | `metadata.sandbox_scenario` |
| Console IDV | Sous `/identity`, pas un second item de nav |

---

## 6. Paliers

### C1 — Contrat (session, lien, consentement)

**Durée :** 3–4 jours.  
**Prérequis :** M2.  
**Spec :** M4 §6.1–6.3, §6.5 (statuts `created` … `cancelled` / `expired` seulement), §10.1–10.2 (sans uploads), UC-IDV-01 / 02 / 07 / 08 / 09.

**Livrable :** un compte connecté (ou un Bearer `ky_test_`) crée une vérif, copie le lien, l’applicant accepte ou refuse ; la console relit le statut ; l’autre org voit 404.

| In | Out |
|---|---|
| Flyway V12 : `verifications`, `consents`, `idempotency_keys` | Statuts `document`, `selfie`, `processing`, `approved`, `review` |
| `HostedTokenStore` Redis + mémoire test | Capture, PUT, SDK |
| `POST/GET /v1/verifications` + console équivalent | `idv_unavailable` (à retirer du create) |
| `GET/POST /v1/flow/{token}` (+ `/consent`) | Uploads |
| `web/flow` : consentement, 404, 410 | Caméra |
| Console : `/identity` liste, `/identity/verifications/new`, fiche statut | Extraits, médias |
| CORS `:3000` + `:3001` | Webhook, AWS |

**Statuts autorisés C1 :** `created`, `pending_consent`, `pending_applicant`, `declined` (consentement), `expired`, `cancelled`.  
Après acceptation : `next: capture_unavailable` **temporaire** (écran d’attente « capture au palier suivant ») — remplacé en C2.

**API (org)**

| Méthode | Chemin | Succès |
|---|---|---|
| `POST` | `/v1/verifications` | **201** / **200** replay |
| `GET` | `/v1/verifications` | liste curseur |
| `GET` | `/v1/verifications/{id}` | fiche |
| `POST` | `/v1/verifications/{id}/cancel` | si `created` \| `pending_consent` |

Même sémantique `/v1/console/verifications*` (`VERIFICATION_*`). Cookie seul sur `/v1/verifications` → **401**.

**Flow :** JSON sans org, sans applicant, `Cache-Control: no-store`. Second consentement → **409**. Token mort → **410**.

**Audit :** `verification.created`, `hosted_link.issued`, `hosted_link.opened`, `consent.accepted` / `declined`, `verification.cancelled`, `verification.expired`.

**Tests :** `CreateVerificationTest`, `IsolationTest`, `HostedFlowTest`, `ConsoleIsolationTest`, `ApiKeyStillBearerTest`. Compte / équipe **verts**.

**Démo :** login → nouvelle vérif → lien → accepter → fiche `pending_applicant` ; autre onglet org B → 404.  
**Kill :** isolation cassée ; pas de lien à la création ; PII dans le GET flow ; create console encore 410.

Ne pas ouvrir C2 si C1 n’est pas vert.

---

### C2 — Capture pièce

**Durée :** 3–4 jours.  
**Prérequis :** C1.  
**Spec :** M4 UC-IDV-03, §5.4, §8.1, §10.2 document.

**Livrable :** après consentement, l’applicant photographie un **recto** ; qualité client bloque sans upload ; PUT réel ; filet serveur ; recapture ; 3 échecs → `declined`.

| In | Out |
|---|---|
| `packages/capture-sdk` (qualité **réelle**) | Selfie, liveness |
| `ObjectStoragePort` + adapter filesystem **ou** MinIO (PUT réel) | AWS S3 prod |
| `verification_media` | Décision, extraits |
| `POST .../document/uploads` + `complete` | OCR, Textract |
| Statuts `document`, `recapture_requested` | `processing`, `approved`, `review` |
| `next: capture_document` (plus `capture_unavailable`) | Verso CNI **complet** si le temps manque : recto d’abord, verso C3 au plus tard |
| Clé objet `org/{orgId}/verifications/{id}/document/{attempt}` | Binaire dans le JSON |

**Garde-fous :** token A ne signe jamais une clé org B ; complete sans objet → 4xx, statut inchangé ; MIME JPEG/PNG, ~10 Mo, côté court ≥ 720 px.

**Flow :** viseur + consignes + recapture. Pas de nom d’org.

**Tests :** `DocumentCaptureTest` (OK, qualité KO, plafond 3, 409 sans consentement). Isolation : objet A illisible avec token B.

**Démo :** consentement → photo → média accepté → écran « selfie bientôt » **ou** `next: capture_selfie` si C3 n’est pas encore branché (placeholder).  
**Kill :** binaire dans l’API ; média cross-tenant ; heuristique de coins vendue comme moteur.

Ne pas ouvrir C3 si le PUT n’est pas réel.

---

### C3 — Selfie, stub, décision

**Durée :** 3–4 jours.  
**Prérequis :** C2.  
**Spec :** M4 UC-IDV-04 / 05 / 10, §8.2–8.3, §12.

**Livrable :** pièce OK → selfie + liveness stub → `processing` (in-process) → statut terminal + raisons en console. Scénario `unsupported` → `unsupported_document`. **0 client AWS.**

| In | Out |
|---|---|
| Uploads / complete selfie | Rekognition, Face Liveness AWS |
| Liveness stub injectable | Enrôlement, AML |
| `DocumentAiPort` + `StubDocumentAi` | `AwsDocumentAi` |
| `BiometricAiPort` + `StubBiometric` | `AwsBiometric` |
| Règles versionnées `m4-1` | Juge LLM, SageMaker |
| Statuts `selfie`, `processing`, `approved`, `review` (+ `declined` déjà là) | Webhook |
| `sandbox_scenario` | Débit crédit |
| Fiche console : extraits, signaux, raisons | URL média permanente |
| Verso CNI si pas fait en C2 | |

**Scénarios minimaux :** `approved` (défaut), `unsupported`, `expired`, `liveness_fail`, `mismatch`, `review`.

**JSON GET org :** `extracted_identity`, `signals`, `decision`, `decision_reasons`. Toujours **absent** du flow.

**Tests :** `SelfieAndStubDecisionTest`, `UnsupportedDocumentTest`, `SandboxNoAwsTest`.

**Démo :** scénario défaut → `approved` + raisons ; scénario `unsupported` → refus code stable.  
**Kill :** décision sans raisons ; sandbox qui touche AWS ; SageMaker « en attendant ».

Ne pas ouvrir C4 (revue) si `review` n’est pas produite par les règles.

---

### C4 — Revue, médias signés, démo

**Durée :** ~2 jours.  
**Prérequis :** C3.  
**Spec :** M4 UC-IDV-06, RG-M4-11, critère §17.8.

**Livrable :** un member/admin ouvre un dossier `review`, voit les médias (GET signé ~5 min), approuve ou refuse ; org B 404 sur l’URL ; la démo C1→C3 tient d’un bout à l’autre.

| In | Out |
|---|---|
| `POST .../{id}/review` | Revue par Recogniz-Me |
| File console `status=review` | Export, webhook |
| `GET .../{id}/media/{kind}` URL signée | URL permanente |
| Audit `verification.reviewed` `{ "decision" }` | PII dans le payload |
| Isolation objet (rejeu C2) | |

**UI fiche :** `StatusBadge`, extraits, signaux, raisons, images, actions Annuler (fenêtre spec) / Trancher.

**Tests :** `ReviewTest` ; 409 hors `review` ; developer 403 ; cookie A ≠ média B.

**Démo (critère CDC §17.8) :** compte sandbox → lien → consentement → pièce → selfie → décision en console, raisons visibles, médias signés, 0 AWS. Puis un passage `review` tranché par l’analyste.

**Kill :** URL média encore valide hors TTL longue / publique ; revue cross-tenant ; `idv_unavailable` encore sur create.

Si C4 n’est pas démontrable, **ne pas** ouvrir M5.

---

## 7. Surfaces et fichiers (indicatif)

| Zone | C1 | C2 | C3 | C4 |
|---|---|---|---|---|
| `api/` Flyway V12+ | session | `verification_media` | signaux, décision | — |
| `VerificationsController` / `FlowController` | create, flow, consent | document uploads | selfie + stub | review, media GET |
| `ConsoleVerificationController` | plus 410 | liste réelle | fiche enrichie | revue |
| `web/flow` | consentement | capture pièce | capture visage, wait, fin | — |
| `packages/capture-sdk` | scaffold | qualité pièce | qualité selfie | — |
| `web/console` `/identity*` | liste, new, fiche statut | — | extraits / raisons | médias, bouton revue |
| Tests Java | auth, isolation, flow | capture | stub, no-AWS | review |

`package.json` racine : workspaces `web/flow` + `packages/capture-sdk`, script `dev:flow`.

---

## 8. Mapping critères CDC

| Critère | Palier |
|---|---|
| §17.8 sandbox pièce + selfie → décision sans AWS, raisons | **C3** démontré, **C4** démo bout-en-bout |
| §17.10 isolation 404 | **C1**, rejoué C2 (média) et C4 |
| §17.12 `unsupported_document` | **C3** |
| §17.9 live AWS | **M5** |
| §17.11 webhook signé | **M5** |

---

## 9. Suite

| Après C4 vert | Sprint |
|---|---|
| Crédit, Checkout, `ky_live_` | **M3** s’il n’est pas déjà livré |
| Adaptateurs AWS + webhook, **même** API et fiche | **M5** |
| Rate limit `POST /verifications`, rétention, staging | **M6** |

M5 ne change pas les routes flow ni les statuts. Il ajoute `Aws*` derrière les ports déjà utilisés par le stub, et la livraison `verification.completed`.

---

## 10. Suivi

- Changement de métier (corridor, seuils, statuts) → [spec M4](./specification-m4-capture-idv-stub.md) **et** CDC si le critère bouge.
- Glissement liveness AWS → CDC §17.9, pas un commentaire de PR.
- Prochain palier à ouvrir : **C1 — contrat**.
