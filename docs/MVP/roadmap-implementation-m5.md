# Roadmap d’implémentation — M5 AWS live + webhooks

**Plateforme :** Recogniz-Me  
**Livrable :** session `live` (permis QC) → AnalyzeID + CompareFaces → décision `m5-1` → webhook `verification.completed` signé par intégration  
**Version du document :** 1.0  
**Date :** 20 septembre 2026  
**Statut :** ordre de build (cycle D)  
**Documents liés :**
- [`specification-m5-aws-live-webhook.md`](./specification-m5-aws-live-webhook.md) — *quoi* (périmètre, architecture, UC, mapping, HMAC)
- [`guide-aws-textract.md`](./guide-aws-textract.md) — runbook **D1** (IAM, CLI, go / no-go AnalyzeID)
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

Sans D1, le mapping est du feeling. Sans D2, les extraits live n’existent pas. Sans D3, le face match AWS (critère §17.9) manque. Sans D4, pas de critère §17.11.

---

## 2. Principes d’ordre

1. **Mesurer avant mapper** — un AnalyzeID staging sur un vrai recto QC **avant** `QcAnalyzeIdMapper`. Pas de catalogue « permis CA ».
2. **Qualité avant COGS** — filet M4 déjà là ; aucun Textract / Rekognition sur média non `accepted`, ni sur intégration `test`.
3. **Document avant visage** — CompareFaces seulement si la pièce est **supportée**. Hors corridor → `unsupported_document`, 0 Rekognition.
4. **Signaux avant webhook** — on ne livre pas `verification.completed` tant que `decideLive` n’écrit pas des raisons et des extraits relisibles.
5. **Stub et AWS coexistent** — routage par `integrations.mode` de la session, **pas** un profil Spring qui tue le stub en prod (NF-MVP-05).
6. **Un livrable démontrable par palier.** Pas de Face Liveness Amplify, pas de SQS, pas de juge LLM, pas de Bedrock.

**Kill (tout le cycle D) :** session `test` qui appelle AWS ; URL / `object_key` dans un webhook ; juge LLM ; corridor vitrine plus large que le permis QC **mesuré** ; secret webhook re-lisible après navigation ; champs d’identité dans les logs ; `ky_live_` sans débit (régression M3).

---

## 3. Vue d’ensemble

Durée indicative : **~2 semaines tendu** (CDC), **~3 semaines confortable**. D1 est un freeze (comme B1), pas un sprint de code. D4 (HMAC + SSRF + console) est le palier le plus large.

```
M4 C4 (fait) ──┐
               ├──► D1 mesure AnalyzeID spécimen QC + freeze mapping / région
M3 B4 (fait) ──┘         │
                         ▼
                    D2 AwsDocumentAi + decideLive m5-1 + extraits console
                         │     (CompareFaces encore mock / skip)
                         ▼
                    D3 AwsBiometric CompareFaces + seuils ; liveness stub documenté
                         │
                         ▼
                    D4 webhook HMAC + retry + console + isolation + vitrine
                         │
                         ▼
                    Amendements CDC §11.3 / §17.9 (+ §11.2 si DetectText)
                         │
                         ▼
                    M6 durcissement / staging
```

| Palier | Livrable démontrable | Statut |
|---|---|---|
| **D1** | JSON AnalyzeID d’un vrai recto QC (PII rédigée) ; tableau de mapping écrit ; go **ou** no-go DetectText + région notée | **fait** (go AnalyzeID, `mapping-analyzeid-qc.md`) |
| **D2** | Session live (AWS mocké) → extraits QC en fiche ; `m5-1` ; session test = 0 client AWS | **fait** |
| **D3** | CompareFaces (mock CI / réel staging) + seuils ; unsupported saute le match ; liveness = stub qualité | **fait** |
| **D4** | Endpoint de test reçoit un POST signé ; org B 404 ; vitrine = permis QC ; CDC amendé | **fait** |

### Parallélisme autorisé

| En même temps | Condition |
|---|---|
| Compte AWS + IAM D1 ∥ relecture spec §10–§11 | Secrets hors git (pattern Stripe) |
| Scaffold console webhook (placeholder déjà là) ∥ D2 | Pas d’enqueue tant que D3 n’est pas vert |
| Copy vitrine corridor QC ∥ D3 | **Après** freeze D1 (pas avant : on ne publie pas un corridor non mesuré) |
| Fixture JSON AnalyzeID + tests mapper ∥ D2 code `AwsDocumentAi` | Fixture **rédigée**, pas un permis réel dans git |
| Face Liveness Amplify, SQS, SageMaker | **Hors M5** |
| InContext JS SDK | Après M4, **pas** bloquant D |

Interdit : D2 sans D1 go **ou** no-go écrit ; D3 qui appelle CompareFaces sur `unsupported` ; D4 avec média dans le payload ; vert M5 sans amendement CDC ; Face Liveness « en attendant » dans le flow.

---

## 4. État de départ (ne pas reconstruire)

| Surface | État |
|---|---|
| Compte, session, cinq rôles, `API_KEY_*` / `VERIFICATION_*` | **Livré** (M2 + T) |
| `Integration` (`test` \| `live`), clés rattachées, `verifications.integration_id` | **Livré** (V15–V16) |
| Ledger, Checkout test, `ky_live_` si solde ≥ 900 ¢, débit à la création | **Livré** (M3 B1–B4) |
| Flow, capture, stub, décision `m4-1`, fiche, revue, isolation médias | **Livré** (M4 C1–C4) |
| `HostedFlowService.decide` | Appelle les ports **puis ignore** le retour : `engine.decide(scenario)` |
| `DocumentAiPort` / `BiometricAiPort` | Records courts ; beans = **stub only** (`IdvStoreConfig`) |
| `SandboxNoAwsTest` | Assert **classes** `Aws*` absentes — **à réécrire** en D2 (spy d’appels, pas d’absence de classe) |
| SDK `software.amazon.awssdk` `textract` + `rekognition` | **Dans** `api/pom.xml`, `optional` — aucun adaptateur |
| Flyway | V1–V18 pris → webhook = **V19+** |
| `POST /v1/webhooks` client | **Absent** (`/v1/webhooks/stripe` = M3, **autre** contrat) |
| Console `/identity/integrations/{id}/settings` | Placeholder `integrationsWebhooks` |
| Vitrine `/products/identity-verification` | Copy M1 (corridor plus large que le mesuré) — **à resserrer** en D4 |
| Runbook AnalyzeID | [`guide-aws-textract.md`](./guide-aws-textract.md) **écrit** ; mesure **pas** encore faite |

Le flow applicant et la machine à états **ne se retouchent pas**. M5 s’accroche dans `decide` + une file HTTP.

---

## 5. Décisions figées (cycle D)

Détail : spec M5 §2.4. Si une valeur change, c’est la **spec + CDC** d’abord.

| Sujet | Choix |
|---|---|
| Corridor live | **Canada × permis QC × recto**. Pas « permis canadien ». |
| Sandbox | Fixture M4 (passeport FR + scénario). Pas la liste publiée. |
| Lecture | AnalyzeID d’abord ; DetectText **seulement** si D1 no-go (+ CDC §11.2). |
| MRZ QC | `mrz_unavailable`, non bloquant |
| Face Liveness AWS | **Hors M5.** Stub qualité. Amendement §17.9 **avant** le vert. |
| Routage IA | `verification.integration_id` → `integrations.mode` |
| `sandbox_scenario` | `null` en live (déjà M3/M4) ; ignoré par `Aws*` |
| Seuils `m5-1` | match &lt; 0,75 declined ; [0,75 ; 0,90[ review ; ≥ 0,90 approved |
| Webhook | Un URL / intégration ; `verification.completed` ; HMAC ; 5 retries |
| Worker | Table + scheduler in-process. **Pas** de SQS |
| Région | `kyc.aws.region`, défaut `ca-central-1` — **figée en D1** |
| Flyway | V19+ |
| Juge | Règles, pas un LLM |

---

## 6. Paliers

### D1 — Mesure AnalyzeID (permis QC)

**Durée :** 1–2 jours (compte AWS + un appel + freeze).  
**Prérequis :** M4 C4, M3 B4, photo nette recto QC (qualité SDK déjà OK).  
**Spec :** M5 §6.6, §10, §16.2. **Runbook :** [`guide-aws-textract.md`](./guide-aws-textract.md).

**Livrable :** un JSON AnalyzeID (champs d’identité **rédigés** dans git) + un tableau de mapping écrit + une ligne **go** ou **no-go DetectText** + région AWS notée. **Pas** encore de `AwsDocumentAi` en prod.

| In | Out |
|---|---|
| Compte AWS staging, IAM moindre privilège (`textract:AnalyzeID` seulement) | `AmazonTextractFullAccess`, Bedrock |
| Région `ca-central-1` (ou repli documenté si API absente) | Catalogue permis CA / ON / passeport |
| 1 photo recto QC, qualité M4 OK | Verso, selfie, CompareFaces |
| Fixture `api/src/test/resources/fixtures/analyzeid-qc.json` (clés réelles, valeurs factices) | Permis réel / PII dans git |
| Tableau spec §10.2 **complété** (clés AnalyzeID réellement vues) | Mapper « au feeling » |

**Go si** type permis **et** nom **et** naissance **et** expiration **et** numéro **et** juridiction QC identifiable.  
**No-go si** JSON vide / `OTHER` / champs requis absents → DetectText + mapping **de ce spécimen** ; CDC §11.2 **avant** le vert M5 (peut attendre D4 pour le texte CDC, mais la **décision** no-go est écrite ici).

**Démo :** coller le JSON rédigé + le tableau « notre champ → clé AnalyzeID » dans la spec ou un `mapping-analyzeid-qc.md` court.  
**Kill :** commencer D2 avec un mapper Ontario « au cas où » ; commit d’une photo / d’un nom réel de permis.

Ne pas ouvrir D2 si le freeze mapping / région n’est pas écrit.

---

### D2 — Lecture live + moteur `m5-1`

**Durée :** 3–4 jours.  
**Prérequis :** D1.  
**Spec :** M5 §5.4–5.5, §7.4, §9.1, UC-M5-01 A1–A3 / A6–A8, UC-M5-06.

**Livrable :** une session **`live`** (clients AWS **mockés** en test) produit des extraits `driving_license` / `CA` / `QC` en fiche console. Une session **`test`** emprunte toujours le stub `m4-1` et **n’invoque aucun** client AWS. CompareFaces peut encore être skip / mock (scores synthétiques si pièce supportée).

| In | Out |
|---|---|
| Enrichir `DocumentSignals` (champs identité, juridiction, provider, MRZ) | Changer les routes flow / statuts |
| `AwsDocumentAi` + `QcAnalyzeIdMapper` (constantes D1) | Rekognition (D3), Bedrock |
| Routeur : `HostedFlowService.decide` lit `Integration.mode` | `@ConditionalOnProperty` qui retire le stub |
| `IdvDecisionEngine.decideLive` + `RULES_VERSION = m5-1` | Juge LLM ; encore `decide(scenario)` en live |
| Extraire depuis le port → `extracted_identity` (plus Marie Dupont en live) | Fixture sandbox cassée |
| `provider_unavailable` → `review` si Textract 5xx | Retry AWS infini dans la requête flow |
| Réécrire `SandboxNoAwsTest` (spy / compteur, classes `Aws*` **autorisées**) | Assert `ClassNotFoundException` sur `AwsDocumentAi` |
| Hors mapping → `unsupported_document`, **0** appel biométrie | CompareFaces sur unknown |

**Routage (ne pas inverser)**

1. Charger `Integration` via `verification.integration_id`.
2. `test` → stub + `engine.decide(scenario)` (`m4-1`).
3. `live` → `AwsDocumentAi` + `decideLive` (`m5-1`). Qualité déjà `accepted` sinon on n’est pas dans `decide`.
4. Logs : `verification_id` + `AnalyzeID` + durée. **Pas** les champs.

**Tests :** `LiveDecisionQcTest` (fixture D1) ; `UnsupportedLiveTest` ; `ExpiredLiveTest` ; `SandboxScenarioIgnoredLiveTest` ; `ProviderUnavailableTest` (Textract 500) ; `SandboxNoAwsTest` réécrit + happy path stub M4 encore vert (`SelfieAndStubDecisionTest`).

**Démo :** create live (clé test AWS mock **ou** staging) → flow pièce QC (fichier) + selfie → fiche : extraits réels / fixture, raisons, `rules_version = m5-1`. Puis create **test** : Marie Dupont, 0 AWS.  
**Kill :** live qui lit encore `sandbox_scenario` ; test qui touche Textract ; extraits live = fixture FR.

Ne pas ouvrir D3 si le mapper QC n’est pas couvert par la fixture D1.

---

### D3 — CompareFaces + seuils

**Durée :** 2–3 jours.  
**Prérequis :** D2.  
**Spec :** M5 §9.1–9.2, UC-M5-01 A4–A5, RG-M5-04, critère §17.9 (face match AWS ; liveness stub).

**Livrable :** `AwsBiometric` appelle **CompareFaces** (selfie ↔ document **de cette** session). Seuils `m5-1` versionnés. Liveness live = **stub** (média `accepted` → `liveness_pass`). Hors corridor : **0** CompareFaces (déjà D2, rejouer le test).

| In | Out |
|---|---|
| `AwsBiometric` + client Rekognition (IAM `rekognition:CompareFaces`) | Face Liveness, Amplify, DetectFaces vendu comme moteur |
| Similarité AWS 0–100 → score `[0, 1]` | Calibrage « au feeling » en staging sans bumper de version |
| `face_match_fail` / `face_match_borderline` / `face_match_pass` | 1:N, galerie, cross-session |
| Liveness stub documenté (commentaire + spec + prochain amendement CDC) | Challenge Amplify dans `web/flow` |
| Skip CompareFaces si `supported = false` | Appel « pour voir » |

**Seuils (code, une seule classe / constantes `m5-1`)**

| Score | Décision (si le reste OK) |
|---|---|
| &lt; 0,75 | `declined` + `face_match_fail` |
| [0,75 ; 0,90[ | `review` + `face_match_borderline` |
| ≥ 0,90 | `approved` + `face_match_pass` |

Changer un seuil = `m5-2`, pas un hotfix silencieux.

**Tests :** `FaceMatchThresholdTest` (0,74 / 0,80 / 0,91) ; `UnsupportedLiveTest` assert **0** CompareFaces ; isolation : bytes de l’org B jamais envoyés (déjà clé objet préfixée). CI = réponses Rekognition **enregistrées**, pas de compte AWS dans GitHub Actions.

**Démo staging (optionnelle mais recommandée) :** 1 permis QC + 1 selfie de la **même** personne → similarité haute ; selfie d’un autre visage → fail / review selon score.  
**Kill :** Face Liveness « presque » branché dans le viseur ; compare cross-tenant ; sandbox Rekognition.

Ne pas ouvrir D4 si `review` n’est plus produite (zone grise match) — le webhook doit pouvoir porter `decision: review`.

---

### D4 — Webhooks, isolation, vitrine, CDC

**Durée :** 3–4 jours.  
**Prérequis :** D3.  
**Spec :** M5 §7.2–7.3, §11–§13, UC-M5-02 à 05, critères §17.11 / §17.10 / vérité commerciale §17.14.

**Livrable :** un developer colle une URL HTTPS (localhost OK en `test`), copie le secret **une fois**, termine une vérif, et voit arriver un POST `X-RecognizMe-Signature` **sans** média. Org B 404. Vitrine = **permis QC**. CDC §11.3 + §17.9 (et §11.2 si D1 no-go) **amendés**.

| In | Out |
|---|---|
| Flyway V19 : `webhook_endpoints`, `webhook_deliveries` | Liste multi-endpoints, SQS, catalogue `liveness.started` |
| `PUT/GET/DELETE /v1/webhooks` (+ alias `POST` upsert) Bearer **scoped** intégration | Confondre avec `/v1/webhooks/stripe` |
| Console `/v1/console/integrations/{id}/webhook*` (`API_KEY_*`) | Member sans `API_KEY_*` qui voit le secret |
| HMAC `t={unix},v1={hex}` ; timeout 10 s ; pas de redirect | Body Stripe, `Stripe-Signature` |
| Retry 5 : 0 / 30 s / 2 min / 10 min / 1 h | File infinie |
| SSRF : live = HTTPS public ; deny metadata / RFC1918 / link-local | `http://169.254.169.254/` accepté en live |
| Enqueue après `decide` **et** après `POST .../review` | Webhook cancel / expire (hors M5) |
| Formulaire settings (placeholder → réel) + liste livraisons + Renvoyer | PII extraits sur l’écran developer |
| Vitrine `/products/identity-verification` FR+EN : 1 ligne QC, pas de MRZ | Copy « passeports CA/FR/US » |
| Amendements CDC §11.3 / §17.9 | Déclarer M5 vert **sans** toucher le CDC |

**Ordre enqueue (ne pas inverser)**

1. Persister décision + signaux + extraits **dans la même transaction**.
2. Si endpoint `active` : INSERT `webhook_deliveries` `pending`.
3. Commit, **puis** tentative HTTP async (la requête flow ne bloque pas 10 s × n).
4. 2xx → `delivered` ; sinon `next_attempt_at`.

**Payload :** spec §11.3. Test `WebhookNoMediaTest` = grep JSON (`url`, `object_key`, `hosted_url` absents). `extracted_identity` **autorisé** (c’est le résultat vendu).

**Tests :** `WebhookSignatureTest` ; `WebhookRetryTest` ; `WebhookReplayIdempotenceTest` ; `WebhookNoMediaTest` ; `WebhookIsolationTest` (org B + clé intégration 1 ≠ webhook 2) ; `WebhookSsrfTest` ; developer PUT OK / member sans `API_KEY_WRITE` 403 ; GET secret absent.

**UI :** i18n FR/EN ; `StatusBadge` livraison ; secret une fois + `secret_prefix` ; bouton Régénérer / Renvoyer. Readonly : pas de PUT.

**Démo (critères §17.9 + §17.11 + §17.10) :**

1. Recharge test (déjà M3) → intégration live → `ky_live_`.
2. Configurer webhook (RequestBin / `nc` / petit serveur local).
3. Applicant : consentement → **recto QC** → selfie.
4. Console : décision + extraits réels.
5. Endpoint : POST signé, HMAC vérifié à la main (`t` + secret).
6. Onglet org B : 404 sur l’id et sur le webhook.
7. Intégration **test** du même compte : scénario stub, **0** AWS, webhook test optionnel.

**Kill :** sandbox qui facture AWS ; URL média dans le webhook ; secret re-lisible ; corridor vitrine plus large que D1 ; M5 déclaré vert avec encore « passeport ICAO » au §11.3 CDC.

Si D4 n’est pas démontrable, **ne pas** ouvrir M6.

---

## 7. Surfaces et fichiers (indicatif)

| Zone | D1 | D2 | D3 | D4 |
|---|---|---|---|---|
| `docs/MVP/guide-aws-textract.md` | runbook + freeze | — | — | — |
| Fixture `analyzeid-qc.json` (rédigée) | oui | tests mapper | — | — |
| `DocumentAiPort` / `StubDocumentAi` | — | record enrichi | — | — |
| `AwsDocumentAi` / `QcAnalyzeIdMapper` | — | oui | — | — |
| `IdvDecisionEngine.decideLive` | — | `m5-1` doc | seuils match | — |
| `HostedFlowService.decide` routeur | — | mode test/live | bio AWS | enqueue |
| `AwsBiometric` | — | mock / skip | CompareFaces | — |
| `SandboxNoAwsTest` | — | réécrire | spy Rekognition = 0 en test | — |
| Flyway V19+ | — | — | — | `webhook_*` |
| `WebhooksController` + console | — | — | — | CRUD + retry |
| `WebhookDispatcher` | — | — | — | HMAC + SSRF |
| `web/console` settings intégration | — | — | — | formulaire |
| `web/site` produit IDV | — | — | copy possible | **publié** |
| CDC §11.3 / §17.9 | décision liveness (rappel) | — | — | **amendé** |
| Tests Java | — | live OCR | face match | webhook |

`kyc.aws.region` / credentials : `application-secrets.properties` (gitignored), même pattern que Stripe. Jamais dans Next.

`web/flow` : **pas** de palier (liveness stub déjà C3). Interdit d’y poser Amplify en D3 « pour avancer ».

---

## 8. Mapping critères CDC

| Critère | Palier |
|---|---|
| §17.9 live Textract + CompareFaces (liveness stub documenté) | **D2** lecture, **D3** match, **D4** amendement §17.9 |
| §17.11 webhook signé, médias absents | **D4** |
| §17.10 isolation 404 | Rejeu D2 (session) ; **D4** webhook / livraisons |
| §17.12 `unsupported_document` live | **D2** (rejeu D3 : 0 CompareFaces) |
| §17.8 sandbox 0 AWS | **D2** `SandboxNoAwsTest` réécrit (ne pas casser M4) |
| NF-MVP-04 / 05 | **D4** / **D2** |
| §11.3 corridor = liste publiée | **D1** freeze, **D4** vitrine + CDC |
| §17.5–17.7 débit / solde | Inchangé M3 ; régression `LiveDebitTest` à chaque palier |

---

## 9. Suite

| Après D4 vert | Sprint |
|---|---|
| Rate limit, rétention affichée, staging, 1 design partner sandbox | **M6** |
| Face Liveness AWS (Amplify) | Après M5 (roadmap MVP §9) — **après** amendement inverse du §17.9 |
| Passeport / autre province | Nouvelle **mesure** type D1 + amendement §11.3 |
| SageMaker / dataset | Roadmap IDV, **après M6** |
| Stripe `sk_live_` | M6 + juridique |

M5 **ne change pas** le ledger. 402 M3 reste **avant** tout appel AWS. Remplacer plus tard `AwsDocumentAi` par SageMaker **ne change pas** l’API ni le webhook.

---

## 10. Suivi

- Changement de corridor / seuils / événement webhook → [spec M5](./specification-m5-aws-live-webhook.md) **et** CDC si le critère bouge.
- Glissement Face Liveness **dans** M5 → **interdit** sans CDC §17.9 **avant** le code Amplify (le cycle D l’a déjà reporté).
- D1 no-go AnalyzeID → CDC §11.2 DetectText **avant** le vert, pas un if dans le mapper « on verra ».
- Prochain palier à ouvrir : **D1 — mesure AnalyzeID** ([guide](./guide-aws-textract.md)).

**Journal**

| Date | Changement |
|---|---|
| 20 sept. 2026 | Cycle D créé. Corridor = permis QC. Liveness AWS hors M5. Paliers D1–D4. |
| 21 sept. 2026 | D1–D4 implémentés : freeze AnalyzeID QC, `AwsDocumentAi` / `AwsBiometricAi`, `decideLive` m5-1, webhooks HMAC V19, vitrine + CDC §11.3 / §17.9. |
