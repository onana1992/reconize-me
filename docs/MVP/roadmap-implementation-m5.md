# Roadmap d’implémentation — M5 AWS live + webhooks

**Plateforme :** Recogniz-Me  
**Livrable :** session `live` (permis QC) → AnalyzeID + CompareFaces → décision `m5-1` → webhook `verification.completed` signé par intégration  
**Version du document :** 1.1  
**Date :** 4 octobre 2026  
**Statut :** cycle D **code livré** (D1–D4) ; **AWS réel non activé** en local (`kyc.aws.enabled=false`)  
**Documents liés :**
- [`specification-m5-aws-live-webhook.md`](./specification-m5-aws-live-webhook.md) — *quoi* (périmètre, architecture, UC, mapping, HMAC)
- [`guide-aws-textract.md`](./guide-aws-textract.md) — runbook **D1** (IAM, CLI, go / no-go AnalyzeID)
- [`guide-aws-iam-local.md`](./guide-aws-iam-local.md) — utilisateur IAM local (S3 / Textract / Rekognition Full Access) + flag D5
- [`mapping-analyzeid-qc.md`](./mapping-analyzeid-qc.md) — freeze mapping (go AnalyzeID, `ca-central-1`)
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — §11 live, critères §17.9 / §17.11 / §17.10 / §17.12
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — calendrier M0–M6 ; corridor live = permis QC
- [`specification-m4-capture-idv-stub.md`](./specification-m4-capture-idv-stub.md) — flow, statuts, ports stub — **réutilisés**
- [`roadmap-implementation-m4.md`](./roadmap-implementation-m4.md) — C1–C4 ; M5 après C4
- [`roadmap-implementation-m3.md`](./roadmap-implementation-m3.md) — débit live à la création ; webhook Stripe ≠ webhook client
- [`../cout-unitaire-verification.md`](../cout-unitaire-verification.md) — COGS AnalyzeID / CompareFaces

Ce document dit **quand** et **dans quel ordre** on construit M5. Un palier n’est pas vert sans son **livrable démontrable**. Le *quoi* reste dans la [spec M5](./specification-m5-aws-live-webhook.md).

La [roadmap IDV S3–S9](../specs/roadmap-implementation-idv.md) (SageMaker) **n’est pas** ce calendrier. Elle reprend après M6.

Si M4 (C4) **ou** M3 (B4) n’est pas démontrable, **ne pas** ouvrir D1.

---

## 1. Cible

**Happy path :** un owner crédité crée une vérif sur une intégration **`live`**, l’applicant photographie le **recto d’un permis de conduire du Québec** puis son visage, la console affiche une **décision + extraits réels**, et le backend client reçoit `verification.completed` **HMAC** — sans média dans le payload. Une session **`test` parallèle** reste à 0 $ d’IA.

Sans D1, le mapping est du feeling. Sans D2, les extraits live n’existent pas. Sans D3, le face match AWS (critère §17.9) manque. Sans D4, pas de critère §17.11. Sans **D5**, le chemin live ne parle **pas** à AWS (stub `provider_unavailable`).

---

## 2. Principes d’ordre

1. **Mesurer avant mapper** — un AnalyzeID staging sur un vrai recto QC **avant** `QcAnalyzeIdMapper`. Pas de catalogue « permis CA ».
2. **Qualité avant COGS** — filet M4 déjà là ; aucun Textract / Rekognition sur média non `accepted`, ni sur intégration `test`.
3. **Document avant visage** — CompareFaces seulement si la pièce est **supportée**. Hors corridor → `unsupported_document`, 0 Rekognition.
4. **Signaux avant webhook** — on ne livre pas `verification.completed` tant que `decideLive` n’écrit pas des raisons et des extraits relisibles.
5. **Stub et AWS coexistent** — routage par `integrations.mode` de la session, **pas** un profil Spring qui tue le stub en prod (NF-MVP-05).
6. **Flag avant credentials** — `kyc.aws.enabled=true` **et** IAM / profil AWS **après** que D2–D3 sont verts en mock. Pas de clés dans git.
7. **Un livrable démontrable par palier.** Pas de Face Liveness Amplify, pas de SQS, pas de juge LLM, pas de Bedrock.

**Kill (tout le cycle D) :** session `test` qui appelle AWS ; URL / `object_key` dans un webhook ; juge LLM ; corridor vitrine plus large que le permis QC **mesuré** ; secret webhook re-lisible après navigation ; champs d’identité dans les logs ; `ky_live_` sans débit (régression M3).

---

## 3. Vue d’ensemble

Durée indicative (build) : **~2 semaines tendu** (CDC), **~3 semaines confortable**. D1 est un freeze (comme B1). D4 (HMAC + SSRF + console) est le palier code le plus large. **D5** est un palier **ops / staging** (heures, pas un sprint).

```
M4 C4 (fait) ──┐
               ├──► D1 mesure AnalyzeID spécimen QC + freeze mapping / région
M3 B4 (fait) ──┘         │
                         ▼
                    D2 AwsDocumentAi + decideLive m5-1 + extraits console
                         │     (CompareFaces mock / skip en CI)
                         ▼
                    D3 AwsBiometric CompareFaces + seuils ; liveness stub documenté
                         │
                         ▼
                    D4 webhook HMAC + retry + console + isolation + vitrine + CDC
                         │
                         ▼
                    D5 brancher AWS réel (flag + IAM + démo staging)
                         │
                         ▼
                    M6 durcissement / staging partenaire
```

| Palier | Livrable démontrable | Statut |
|---|---|---|
| **D1** | JSON AnalyzeID d’un vrai recto QC (PII rédigée) ; tableau de mapping écrit ; go **ou** no-go DetectText + région notée | **fait** (`mapping-analyzeid-qc.md`, go AnalyzeID, `ca-central-1`) |
| **D2** | Session live (AWS **mocké**) → extraits QC en fiche ; `m5-1` ; session test = 0 client AWS | **fait** |
| **D3** | CompareFaces (mock CI) + seuils ; unsupported saute le match ; liveness = stub qualité | **fait** (code) ; **pas** d’appel Rekognition en local |
| **D4** | Endpoint de test reçoit un POST signé ; org B 404 ; vitrine = permis QC ; CDC amendé | **fait** (code + CDC §11.3 / §17.9) |
| **D5** | `kyc.aws.enabled=true` + credentials hors git ; 1 session live → AnalyzeID + CompareFaces **réels** ; sandbox toujours 0 AWS | **à faire** |

### Parallélisme autorisé

| En même temps | Condition |
|---|---|
| Compte AWS + IAM D1 ∥ relecture spec §10–§11 | Secrets hors git (pattern Stripe) |
| Dettes tests D4 | **fait** (4 oct. 2026) |
| Face Liveness Amplify, SQS, SageMaker | **Hors M5** |
| InContext JS SDK | Après M4, **pas** bloquant D |

Interdit : D2 sans D1 go **ou** no-go écrit ; D3 qui appelle CompareFaces sur `unsupported` ; D4 avec média dans le payload ; vert M5 sans amendement CDC ; Face Liveness « en attendant » dans le flow ; D5 avec clés AWS dans git.

---

## 4. As-built (4 octobre 2026)

Ne pas reconstruire D1–D4. Ne pas confondre **code branché** et **AWS allumé**.

| Surface | État |
|---|---|
| Compte, session, rôles, `API_KEY_*` / `VERIFICATION_*` | **Livré** (M2 + T) |
| `Integration` (`test` \| `live`), clés, `verifications.integration_id` | **Livré** (V15–V16) |
| Ledger, `ky_live_` si solde ≥ 900 ¢, débit à la création | **Livré** (M3) |
| Flow, capture, stub `m4-1`, fiche, revue, isolation médias | **Livré** (M4) |
| Freeze AnalyzeID QC | **Livré** — [`mapping-analyzeid-qc.md`](./mapping-analyzeid-qc.md) |
| `HostedFlowService.decide` | Route par `integrations.mode` : stub `m4-1` **ou** `Aws*` + `decideLive` `m5-1` |
| `AwsDocumentAi` / `QcAnalyzeIdMapper` / `AwsBiometricAi` | **Présents** |
| `RekognitionCompareFacesClient` | Créé **seulement si** `kyc.aws.enabled=true` ; sinon stub qui lève `provider_unavailable` |
| `kyc.aws.enabled` | Défaut **`false`** (`KYC_AWS_ENABLED`). **Pas** dans `application-secrets.properties` |
| Credentials AWS dans l’app | **Aucun** (pas de access key en properties). Le vrai client utilise la chaîne SDK (env / `~/.aws/credentials` / rôle) |
| Liveness | Stub qualité (selfie `accepted` → pass). **Pas** Face Liveness AWS |
| Flyway | V19 webhooks ; V21 noms d’enum SQL |
| `PUT/GET/DELETE /v1/webhooks` + console settings | **Livré** |
| Vitrine IDV | Corridor **permis QC recto** (FR/EN) |
| CDC §11.3 / §17.9 | **Amendés** (liveness stub documenté) |
| CI | Mocks / fixtures JSON — **0** compte AWS dans GitHub Actions |

**Conséquence locale :** une vérif **live** emprunte bien `AwsBiometricAi`, mais CompareFaces **ne part pas** vers AWS tant que D5 n’est pas fait → `review` + `provider_unavailable`.

---

## 5. Décisions figées (cycle D)

Détail : spec M5 §2.4. Si une valeur change, c’est la **spec + CDC** d’abord.

| Sujet | Choix |
|---|---|
| Corridor live | **Canada × permis QC × recto**. Pas « permis canadien ». |
| Sandbox | Fixture M4 (passeport FR + scénario). Pas la liste publiée. |
| Lecture | AnalyzeID d’abord ; DetectText **seulement** si D1 no-go (+ CDC §11.2). **Go D1** → pas de DetectText. |
| MRZ QC | `mrz_unavailable`, non bloquant |
| Face Liveness AWS | **Hors M5.** Stub qualité. |
| Routage IA | `verification.integration_id` → `integrations.mode` |
| `sandbox_scenario` | `null` en live ; ignoré par `Aws*` |
| Seuils `m5-1` | match &lt; 0,75 declined ; [0,75 ; 0,90[ review ; ≥ 0,90 approved |
| Webhook | Un URL / intégration ; `verification.completed` ; HMAC ; 5 retries |
| Worker | Table + scheduler in-process. **Pas** de SQS |
| Région | `kyc.aws.region`, défaut **`ca-central-1`** (figée D1) |
| Flyway | V19+ (V21 enums déjà là) |
| Juge | Règles, pas un LLM |

---

## 6. Paliers

### D1 — Mesure AnalyzeID (permis QC)

**Durée :** 1–2 jours.  
**Prérequis :** M4 C4, M3 B4.  
**Spec :** M5 §6.6, §10, §16.2. **Runbook :** [`guide-aws-textract.md`](./guide-aws-textract.md).  
**Statut :** **fait.**

**Livrable :** JSON AnalyzeID (identité **rédigée**) + tableau de mapping + go / no-go + région.

| In | Out |
|---|---|
| IAM `textract:AnalyzeID` seulement | `AmazonTextractFullAccess`, Bedrock |
| Région `ca-central-1` (ou repli documenté) | Catalogue permis CA / ON / passeport |
| 1 photo recto QC, qualité M4 OK | Verso, selfie, CompareFaces |
| Fixture `api/src/test/resources/fixtures/analyzeid-qc.json` | Permis réel / PII dans git |

**Kill :** mapper Ontario « au cas où » ; commit d’une photo / d’un nom réel.

---

### D2 — Lecture live + moteur `m5-1`

**Durée :** 3–4 jours.  
**Prérequis :** D1.  
**Spec :** M5 §5.4–5.5, §7.4, §9.1, UC-M5-01 A1–A3 / A6–A8, UC-M5-06.  
**Statut :** **fait.**

**Livrable :** session `live` (clients AWS **mockés**) → extraits `driving_license` / `CA` / `QC`. Session `test` → stub `m4-1`, **0** client AWS.

| In | Out |
|---|---|
| `AwsDocumentAi` + `QcAnalyzeIdMapper` (constantes D1) | Rekognition (D3), Bedrock |
| Routeur `HostedFlowService` sur `Integration.mode` | `@ConditionalOnProperty` qui retire le stub |
| `IdvDecisionEngine.decideLive` + `m5-1` | Juge LLM ; `decide(scenario)` en live |
| `provider_unavailable` → `review` si Textract 5xx | Retry AWS infini dans la requête flow |
| `SandboxNoAwsTest` = spy / compteur | Assert `ClassNotFoundException` sur `Aws*` |
| Hors mapping → `unsupported_document`, 0 biométrie | CompareFaces sur unknown |

**Tests livrés :** `LiveDecisionQcTest`, `QcAnalyzeIdMapperTest`, `SandboxNoAwsTest` (compteur), `UnsupportedLiveTest`, `ExpiredLiveTest`, `SandboxScenarioIgnoredLiveTest`, `ProviderUnavailableTest`.

**Kill :** live qui lit encore `sandbox_scenario` ; test qui touche Textract.

---

### D3 — CompareFaces + seuils

**Durée :** 2–3 jours.  
**Prérequis :** D2.  
**Spec :** M5 §9.1–9.2, UC-M5-01 A4–A5, RG-M5-04, critère §17.9.  
**Statut :** **fait** (code + tests de seuils). Appel **réel** = D5.

**Livrable :** `AwsBiometricAi` → `CompareFacesClient.compare` (selfie ↔ document **de cette** session). Seuils `m5-1`. Liveness = stub (média `accepted` → pass). Unsupported → **0** CompareFaces.

| In | Out |
|---|---|
| `RekognitionCompareFacesClient` derrière `kyc.aws.enabled` | Face Liveness, Amplify, DetectFaces comme moteur |
| Similarité 0–100 → score `[0, 1]` | Calibrage staging sans bumper de version |
| `face_match_fail` / `borderline` / `pass` | 1:N, galerie, cross-session |

**Seuils (constantes `m5-1`)**

| Score | Décision (si le reste OK) |
|---|---|
| &lt; 0,75 | `declined` + `face_match_fail` |
| [0,75 ; 0,90[ | `review` + `face_match_borderline` |
| ≥ 0,90 | `approved` + `face_match_pass` |

Changer un seuil = `m5-2`.

**Tests livrés :** `FaceMatchThresholdTest`.  
**Kill :** Amplify dans le viseur ; compare cross-tenant ; sandbox Rekognition.

---

### D4 — Webhooks, isolation, vitrine, CDC

**Durée :** 3–4 jours.  
**Prérequis :** D3.  
**Spec :** M5 §7.2–7.3, §11–§13, UC-M5-02 à 05.  
**Statut :** **fait** (code). Dettes de tests listées ci-dessous.

**Livrable :** URL HTTPS (localhost OK en `test`), secret **une fois**, POST `X-RecognizMe-Signature` **sans** média. Org B 404. Vitrine = permis QC. CDC amendé.

| In | Out |
|---|---|
| Flyway V19 `webhook_endpoints` / `webhook_deliveries` | Multi-endpoints, SQS, `liveness.started` |
| Bearer `/v1/webhooks*` scoped intégration | Confondre avec `/v1/webhooks/stripe` |
| Console `API_KEY_*` | Member sans `API_KEY_*` qui voit le secret |
| HMAC ; timeout 10 s ; pas de redirect ; 5 retries | File infinie |
| SSRF live = HTTPS public | `http://169.254.169.254/` en live |
| Enqueue après `decide` **et** après revue | Webhook cancel / expire |
| Formulaire settings + Renvoyer | PII extraits sur l’écran developer |

**Ordre enqueue :** persister décision → INSERT `pending` → commit → HTTP async.

**Tests livrés :** `WebhookSignatureTest`, `WebhookSsrfTest`, `WebhookDeliveryFlowTest` (no-média, isolation, retry 500→200, replay `event_id`).

**Kill :** URL média dans le webhook ; secret re-lisible ; corridor vitrine plus large que D1.

---

### D5 — Staging AWS réel

**Durée :** quelques heures (IAM + flag + 1 démo).  
**Prérequis :** D4.  
**Spec :** M5 §6.1, §14 (secrets hors git), §16 démo.  
**Statut :** **à ouvrir.**

**Livrable :** une session **`live`** en staging appelle **vraiment** Textract AnalyzeID et Rekognition CompareFaces. Une session **`test` du même JAR** reste à 0 AWS. Les logs montrent `AnalyzeID` / `CompareFaces` + `verification_id` + durée — **sans** PII.

| In | Out |
|---|---|
| `KYC_AWS_ENABLED=true` **ou** `kyc.aws.enabled=true` dans `application-secrets.properties` (gitignored) | Clés dans git, `application.properties` commité |
| Profil / env AWS (`AWS_ACCESS_KEY_ID` ou `~/.aws/credentials` ou rôle) | Access key en dur dans le code |
| IAM : `textract:AnalyzeID` + `rekognition:CompareFaces`, région `ca-central-1` | `AmazonRekognitionFullAccess` / Bedrock |
| 1 recto QC + 1 selfie de la **même** personne | Face Liveness Amplify |
| Relance de l’API **après** le flag | Croire que le code `AwsBiometricAi` suffit tout seul |

**Checklist**

- [ ] Compte AWS staging, IAM moindre privilège
- [ ] `kyc.aws.enabled=true` hors git
- [ ] Chaîne de credentials SDK visible (`aws sts get-caller-identity`)
- [ ] Intégration `live` + solde ≥ 0,90 $
- [ ] Flow : consentement → recto QC → selfie → fiche extraits réels, `rules_version = m5-1`
- [ ] Même org, intégration `test` : 0 appel AWS (compteur CloudTrail / logs)
- [ ] Webhook de test reçoit le POST signé
- [ ] Org B 404

**Kill :** sandbox qui facture AWS ; PII dans les logs ; `sk_live_` Stripe « pour finir M5 ».

Sans D5, **ne pas** vendre le live comme « branché AWS ». Le code l’est ; le runtime local **non**.

---

## 7. Surfaces et fichiers

| Zone | D1 | D2 | D3 | D4 | D5 |
|---|---|---|---|---|---|
| `docs/MVP/guide-aws-textract.md` / `mapping-analyzeid-qc.md` | freeze | — | — | — | — |
| Fixture `analyzeid-qc.json` | oui | tests mapper | — | — | — |
| `AwsDocumentAi` / `QcAnalyzeIdMapper` | — | oui | — | — | — |
| `IdvDecisionEngine.decideLive` | — | `m5-1` doc | seuils match | — | — |
| `HostedFlowService` routeur | — | mode | bio | enqueue | — |
| `AwsBiometricAi` / `RekognitionCompareFacesClient` | — | skip | code | — | **flag on** |
| Flyway V19 | — | — | — | `webhook_*` | — |
| `WebhooksController` + console settings | — | — | — | oui | — |
| `web/site` produit IDV | — | — | — | corridor QC | — |
| CDC §11.3 / §17.9 | — | — | — | amendé | — |
| `application-secrets.properties` | — | — | — | — | `kyc.aws.enabled` |

`web/flow` : **pas** de palier (liveness stub déjà C3). Interdit d’y poser Amplify en D3/D5 « pour avancer ».

---

## 8. Mapping critères CDC

| Critère | Palier |
|---|---|
| §17.9 live Textract + CompareFaces (liveness stub) | **D2** lecture, **D3** match (mock), **D4** amendement, **D5** appel réel |
| §17.11 webhook signé, médias absents | **D4** |
| §17.10 isolation 404 | **D2** session ; **D4** webhook |
| §17.12 `unsupported_document` live | **D2** (0 CompareFaces) |
| §17.8 sandbox 0 AWS | **D2** `SandboxNoAwsTest` ; rejeu **D5** |
| NF-MVP-04 / 05 | **D4** / **D2** |
| §11.3 corridor = liste publiée | **D1** freeze, **D4** vitrine + CDC |
| §17.5–17.7 débit / solde | Inchangé M3 ; `LiveDebitTest` |

---

## 9. Suite

| Après D5 vert | Sprint |
|---|---|
| Rate limit, rétention affichée, staging partenaire, Stripe `sk_live_` après juridique | **M6** |
| Face Liveness AWS (Amplify) | Après M5 (roadmap MVP §9) — **après** amendement inverse du §17.9 |
| Passeport / autre province | Nouvelle **mesure** type D1 + amendement §11.3 |
| SageMaker / dataset | Roadmap IDV, **après M6** |

M5 **ne change pas** le ledger. 402 M3 reste **avant** tout appel AWS. Remplacer plus tard `AwsDocumentAi` par SageMaker **ne change pas** l’API ni le webhook.

---

## 10. Suivi

- Changement de corridor / seuils / événement webhook → [spec M5](./specification-m5-aws-live-webhook.md) **et** CDC si le critère bouge.
- Glissement Face Liveness **dans** M5 → **interdit** sans CDC §17.9 **avant** le code Amplify.
- D1 no-go AnalyzeID → CDC §11.2 DetectText **avant** le vert (non retenu : freeze = go AnalyzeID).
- Prochain palier à ouvrir : **D5 — staging AWS réel** (flag + IAM). Face Liveness reste **hors** M5.

**Journal**

| Date | Changement |
|---|---|
| 20 sept. 2026 | Cycle D créé. Corridor = permis QC. Liveness AWS hors M5. Paliers D1–D4. |
| 21 sept. 2026 | D1–D4 implémentés : freeze AnalyzeID QC, `AwsDocumentAi` / `AwsBiometricAi`, `decideLive` m5-1, webhooks HMAC V19, vitrine + CDC §11.3 / §17.9. |
| 4 oct. 2026 | v1.1 : as-built vs AWS allumé. Palier **D5** ajouté — `kyc.aws.enabled` encore `false` en local, pas de credentials dans les secrets. |
| 4 oct. 2026 | Tests spec §16.1 : unsupported / expired / scenario ignoré / provider KO / webhook no-média, isolation, retry, replay. |
