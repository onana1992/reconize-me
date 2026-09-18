# Roadmap d’implémentation — M3 Crédit d’organisation (recharge carte)

**Plateforme :** Recogniz-Me  
**Livrable :** Checkout carte test → crédit au ledger → intégration live (`ky_live_`) → débit à l’unité  
**Version du document :** 1.2 — B1–B4 livrés  
**Date :** 17 septembre 2026  
**Statut :** livré (cycle B)  
**Documents liés :**
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — *quoi* : §10, RG-SUB-01…07, UC-SUB-01/02, critères §17.5–17.7, NF-MVP-09
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — calendrier M0–M6 ; freeze §6 ; M5 exige M3 **et** M4
- [`specification-m2-compte-client.md`](./specification-m2-compte-client.md) — session, `BILLING_*`, `Integration`
- [`roadmap-implementation-team.md`](./roadmap-implementation-team.md) — T2 : routes Stripe = `BILLING_WRITE`, pas `if ("owner")`
- [`roadmap-implementation-m4.md`](./roadmap-implementation-m4.md) — `POST /v1/verifications` déjà là ; M3 y accroche le débit
- [`../cout-unitaire-verification.md`](../cout-unitaire-verification.md) — COGS (~0,10–0,20 $ chargé) ; le prix client reste au-dessus
- [`guide-stripe-sandbox.md`](./guide-stripe-sandbox.md) — brancher Checkout test en local (`sk_test_`, Stripe CLI)

Ce document dit **quand** et **dans quel ordre** on construit M3. Un palier n’est pas vert sans son **livrable démontrable**. Le *quoi* reste dans le CDC §10.

---

## 1. Cible

**Happy path commercial :** un owner recharge un pack par **carte test** Stripe → le ledger crédite le solde → il crée une intégration **live** et copie `ky_live_` **une fois** → une `POST /v1/verifications` (Bearer live **ou** console sur cette intégration) **débite** une unité → `/settings/billing` et `GET /v1/usage` montrent les **mêmes** chiffres.

Sans B1, Stripe n’a rien à créditer. Sans B2, le solde reste à 0. Sans B3, `live_locked` empêche le critère §17.5. Sans B4, on peut créer du live sans le payer.

---

## 2. Principes d’ordre

1. **Freeze avant Checkout** — devise, prix unitaire, packs et moment du débit sont du code, pas un commentaire. B2 n’ouvre pas tant que B1 n’a pas écrit ces constantes **une seule fois**.
2. **Ledger avant Stripe** — Stripe encaisse la carte ; le **solde** se lit dans `credit_accounts`. Un `success_url` ne crédite jamais.
3. **Crédit avant `ky_live_`** — RG-SUB-02. On ne desserre pas `live_locked` « pour tester le formulaire ».
4. **Débit à la création live** — figé ici (recommandation CDC §10.4). Une vérif test ne débite **jamais**.
5. **Droits nommés** — `BILLING_READ` / `BILLING_WRITE` (owner). Pas de `if ("owner")` dans un contrôleur Stripe.
6. **Un livrable démontrable par palier.** Pas d’abonnement, pas de meter, pas de facture d’usage, pas de CB chez nous, pas de Stripe *live* (clés `sk_live_`) dans ce cycle.

**Kill (tout le cycle B) :** `ky_live_` sans solde ≥ une unité ; webhook Stripe **non signé** accepté ; CB / PAN / secret de clé dans les logs ; org B lit le ledger de A ; intégration **test** débitée ; crédit déclenché par l’URL de succès plutôt que par le webhook.

---

## 3. Vue d’ensemble

Durée indicative : **~1 semaine tendu**, **~2 semaines confortable** (CDC). B2 (Stripe) est le palier le plus risqué.

```
I intégrations (fait) ──► B1 ledger + freeze
                          │
                          ▼
                     B2 Checkout carte test + webhook signé
                          │
                          ▼
                     B3 intégration live (RG-SUB-02)
                          │
                          ▼
                     B4 débit à la création + GET /v1/usage
                          │
                          ▼
                     M5 AWS + webhooks client   (après B4 et M4)
```

| Palier | Livrable démontrable | Statut |
|---|---|---|
| **B1** | Une org a un compte crédit à 0 ; `GET /v1/console/billing` isolé ; montants figés (USD, 0,90 $, packs) ; vitrine + console **même** devise | **livré** |
| **B2** | Owner clique un pack → Checkout test → webhook signé → solde = pack ; replay = pas de double crédit | **livré** |
| **B3** | Solde ≥ une unité → intégration `live` + `ky_live_` une fois ; solde 0 → toujours refusé | **livré** |
| **B4** | `POST` live débite ; test ne débite pas ; solde insuffisant → plus de vérif live, test OK ; usage API = écran billing | **livré** |

### Parallélisme autorisé

| En même temps | Condition |
|---|---|
| **B1–B4 ∥ fin M4** | M3 n’est pas un prérequis de M4 ; M5 exige **les deux** |
| UI `/settings/billing` (déjà scaffold) ∥ B1 API | Boutons restent disabled tant que B2 n’existe pas |
| Compte Stripe (Dashboard test) ∥ B1 | Clés `sk_test_` / `whsec_` dans `application-secrets.properties`, **pas** dans git |
| Portail factures, virement, avoir ops, recharge auto | **Hors MVP** |
| Stripe **live** (`pk_live_` / `sk_live_`) | **M6**, après relecture CGU / privacy |

Interdit : B2 sans freeze écrit ; B3 sans solde réellement crédité par B2 (un `UPDATE` SQL de démo ne compte pas) ; B4 qui débite le test ; M5 sans B4.

---

## 4. État de départ (ne pas reconstruire)

| Surface | État |
|---|---|
| Compte, session, cinq rôles, `BILLING_*` (owner only) | **Livré** (M2 + T2) |
| Entité `Integration` (`test` \| `live`), clés rattachées, chrome liste / fiche | **Livré** (V15–V16). Reliquat `lib/environment.ts` (`?env=`, cookie `rm_console_env`) : cosmétique, pas un prérequis B |
| `POST /v1/console/integrations` `mode=live` | **403** `live_locked` (dur). UI : option live **disabled** |
| `ApiKeyIssuer` / `CryptoTokens.randomApiKey(live)` | **Prêt** : préfixe `ky_live_` déjà implémenté |
| `POST /v1/verifications` (Bearer + console) | **Livré** (M4) ; **aucun** débit |
| `/settings/billing` | Scaffold : solde **0**, packs affichés, bouton **disabled** |
| Accueil console | Compteurs sandbox / live / solde **hardcodés 0** |
| `web/site/lib/pricing.ts` | **`EUR`**, `liveUnit: 0.9`, `PROVISIONAL: true` — **à passer en USD en B1** |
| `web/console/lib/pricing.ts` | `USD`, packs 50 / 100 / 250 / 500 — déjà aligné |
| Flyway | V1–V16 pris → crédit = **V17+** |
| Dépendance Stripe | **Absente** (`pom.xml`) |
| `GET /v1/usage` | **Absent** |
| Tables `credit_*` / `stripe_customers` | **Absentes** |

T2 a déjà posé le filet : admin / member / developer → **403** sur `BILLING_WRITE`. B2 s’y branche, il ne réinvente pas la matrice.

---

## 5. Décisions figées (cycle B)

À écrire dans le code **avant** le premier Checkout (B1). Si une valeur change, c’est le **CDC §10.1** d’abord.

| Sujet | Choix |
|---|---|
| Devise | **USD** (décision 17 sept. 2026). La vitrine encore en EUR s’aligne en B1. |
| Prix unitaire IDV live | **0,90 $** (`900` cents). Au-dessus du COGS, sous le marché. |
| Packs de recharge | **50 / 100 / 250 / 500 $** (5000 / 10000 / 25000 / 50000 cents) |
| Canal | **Carte** seule, Stripe Checkout `mode=payment`. Pas de CB stockée. |
| Comptage | À la **création** d’une vérif sur intégration `live` |
| Source de vérité solde | Ledger interne. Stripe = vérité du **paiement carte**. |
| Unité monétaire API / SQL | **Entier cents** (`BIGINT`). Jamais de `double`. |
| Mode Stripe M3 | **Test uniquement** (`sk_test_`, cartes `4242…`) |
| HTTP solde insuffisant | Intégration live : **403** `insufficient_credit` (remplace `live_locked`). Vérif live : **402** `insufficient_credit`. |
| Droits | Checkout + lecture ledger détaillé : `BILLING_*`. Accueil (totaux) : tout rôle authentifié. `GET /v1/usage` : Bearer. |

Hors cycle (ne pas « glisser » dans B) : multi-devises, abonnement, meter Stripe, facture d’usage, virement, avoir ops, recharge auto, plafond de dépense, essai gratuit live, portail Customer Billing.

---

## 6. Paliers

### B1 — Ledger + freeze

**Durée :** ~2 jours.  
**Prérequis :** M2, I (intégrations). Peut ∥ M4.  
**CDC :** RG-SUB-01, RG-SUB-07 ; freeze §6 de la roadmap MVP.

**Livrable :** toute org a **un** `credit_account` à 0 USD. Un owner ouvre `/settings/billing` et voit solde 0 + ledger vide **depuis l’API**, plus un `0` collé dans le JSX. Org B → **404**. Vitrine et console affichent **USD** et **0,90 $**.

| In | Out |
|---|---|
| Flyway V17 : `credit_accounts`, `credit_ledger_entries`, backfill orgs existantes | Stripe, Checkout, `ky_live_` |
| `CreditAccount` / `CreditLedgerEntry` | Table `Plan`, colonne `organizations.plan` |
| Création du compte crédit **au signup** de l’org | Compte crédit par produit |
| `GET /v1/console/billing` (`BILLING_READ`) | Webhook Stripe |
| `kyc.billing.currency` / `unit-amount-minor` / `packs` | Devise lue depuis Stripe |
| Site : `PROVISIONAL = false`, `CURRENCY = USD` ; console déjà USD | |

**Schéma (indicatif)**

```sql
CREATE TABLE credit_accounts (
    organization_id      CHAR(36)     NOT NULL,
    currency             CHAR(3)      NOT NULL,
    balance_minor        BIGINT       NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (organization_id),
    CONSTRAINT fk_credit_accounts_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE credit_ledger_entries (
    id                   CHAR(36)     NOT NULL,
    organization_id      CHAR(36)     NOT NULL,
    entry_type           VARCHAR(16)  NOT NULL,  -- topup | debit
    amount_minor         BIGINT       NOT NULL,  -- signé : + topup, − debit
    balance_after_minor  BIGINT       NOT NULL,
    product              VARCHAR(32)  NULL,      -- identity sur debit
    resource_type        VARCHAR(32)  NULL,      -- verification
    resource_id          CHAR(36)     NULL,
    stripe_event_id      VARCHAR(64)  NULL,
    stripe_checkout_session_id VARCHAR(128) NULL,
    created_by_user_id   CHAR(36)     NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ledger_stripe_event (stripe_event_id),
    UNIQUE KEY uq_ledger_checkout (stripe_checkout_session_id),
    UNIQUE KEY uq_ledger_resource (resource_type, resource_id),
    KEY idx_ledger_org_created (organization_id, created_at),
    CONSTRAINT fk_ledger_org FOREIGN KEY (organization_id) REFERENCES credit_accounts (organization_id)
);
```

`SELECT FOR UPDATE` sur `credit_accounts` à chaque écriture. Une vérif ne peut pas être débitée deux fois (`uq_ledger_resource`). Un event Stripe ne peut pas créditer deux fois.

**API**

| Méthode | Chemin | Auth | Succès |
|---|---|---|---|
| `GET` | `/v1/console/billing` | cookie + `BILLING_READ` | solde, packs, ledger (curseur), conso par service (0) |

Payload indicatif :

```json
{
  "currency": "usd",
  "balance_minor": 0,
  "unit_amount_minor": 900,
  "live_unlocked": false,
  "packs": [5000, 10000, 25000, 50000],
  "usage": [
    { "product": "identity", "sandbox_count": 0, "live_count": 0, "live_debit_minor": 0 }
  ],
  "ledger": { "entries": [], "next_cursor": null }
}
```

Member / admin → **403** `forbidden` (pas 404 : ce n’est pas une ressource d’une autre org). Org B sur un id A → **404** (rien à id ici : isolation par session, jamais d’`organization_id` client).

**UI :** `/settings/billing` consomme `GET /v1/console/billing`. Boutons packs **toujours disabled**. Accueil : solde lu (même endpoint **ou** champs ajoutés à `GET /me` — un seul des deux, pas deux sources). Copy : retirer « Stripe n’est pas encore branché » **seulement en B2**.

**Audit :** aucun event métier obligatoire en B1 (pas encore de mouvement). Interdit : e-mail, PAN, clé brute dans un payload futur.

**Tests :** `BillingIsolationTest` — owner A 200 solde 0 ; member 403 ; cookie B ne voit pas A ; signup crée le compte crédit ; pas de seconde ligne `credit_accounts` pour la même org.

**Démo :** compte neuf → `/settings/billing` → $0.00, packs $50–$500, ledger vide.  
**Kill :** deux devises affichées ; solde en `double` ; `BILLING_READ` accordé à admin « pour aller plus vite ».

Ne pas ouvrir B2 si le freeze n’est pas dans `kyc.billing.*` **et** les deux `lib/pricing.ts`.

---

### B2 — Checkout carte test + webhook

**Durée :** 3–4 jours.  
**Prérequis :** B1.  
**CDC :** RG-SUB-05, RG-SUB-06, NF-MVP-09, critère §17.5 (première moitié).

**Livrable :** un owner clique **50 $** → Stripe Checkout (carte `4242…`) → au retour, après webhook, le solde vaut **$50.00** et le ledger montre un `topup`. Rejouer le même event ne recrédite pas.

| In | Out |
|---|---|
| `com.stripe:stripe-java` | Abonnement, meter, Customer Portal, SetupIntent |
| Port `StripePort` + adaptateur réel ; tests = stub **ou** payload signé local | Crédit via `success_url` |
| Table `stripe_customers` (1 / org) | Stockage `pm_…` / CB |
| `POST /v1/console/billing/checkout` (`BILLING_WRITE`) | Facture d’usage |
| `POST /v1/webhooks/stripe` **public**, signature `Stripe-Signature` | `POST /v1/webhooks` client (M5) |
| Event `checkout.session.completed` (`payment_status=paid`) | `payment_intent.succeeded` **en plus** (double crédit) |
| Console : bouton actif ; redirect Checkout ; retour `/settings/billing?checkout=success\|cancel` | Stripe live |

**`success_url` / `cancel_url` :** UX seulement (`?checkout=success` peut afficher « le solde se met à jour »). **Zéro** écriture ledger sur ce GET.

**Checkout (figé)**

- `mode=payment`, `currency=usd`, un seul line item = le pack.
- `metadata` : `organization_id`, `pack_minor`, `user_id`.
- Customer Stripe get-or-create, `metadata.organization_id`, id persisté. Pas de `payment_method` sauvegardé chez nous.
- Pack inconnu → **400** `validation_error`.

**Webhook**

1. `SecurityConfig` : `POST /v1/webhooks/stripe` `permitAll` (la signature **est** l’auth).
2. Corps brut + header → `Webhook.constructEvent` ; échec → **400** `invalid_signature`.
3. Event inconnu → **200** ignore (Stripe retryait sinon).
4. `checkout.session.completed` payé → `CreditService.topup` idempotent sur `stripe_event_id` **et** `stripe_checkout_session_id`.
5. Session d’une org inconnue → log `event_id` + code, **pas** le payload carte, **200** (éviter le retry infini) ou **400** documenté — choisir **200** + audit `credit.topup_ignored`.

**API**

| Méthode | Chemin | Auth | Succès |
|---|---|---|---|
| `POST` | `/v1/console/billing/checkout` | cookie + `BILLING_WRITE` | **201** `{ "url": "https://checkout.stripe.com/…" }` |
| `POST` | `/v1/webhooks/stripe` | signature Stripe | **200** `{ "received": true }` |

Admin / member → checkout **403**. Cookie sans session → **401**.

**Secrets :** `kyc.stripe.secret-key`, `kyc.stripe.webhook-secret` dans `application-secrets.properties` (déjà le pattern SMTP). Jamais dans les logs. Tests : `whsec_test` + payload signé avec la lib.

**Audit (sans PII) :** `billing.checkout_created` `{ "pack_minor": 5000 }` ; `credit.topped_up` `{ "amount_minor": 5000 }`. Pas de `customer_email`, pas de last4.

**UI :** activer « Payer par carte » ; copy `topupOwner` → le vrai canal carte. Gérer `insufficient_credit` n’arrive pas encore ici.

**Tests :** `StripeCheckoutTest` — unsigned → 400 ; event rejoué → une seule ligne ledger ; pack hors liste → 400 ; member 403 ; org A n’est pas créditée par un metadata `organization_id` de B (le customer / metadata doit matcher le customer persisté, pas un id libre dans le JSON). `BillingIsolationTest` reste vert.

**Démo :** Stripe CLI `listen --forward-to localhost:8080/v1/webhooks/stripe` → pack 50 $ → carte test → `/settings/billing` = $50.00. Procédure locale : [`guide-stripe-sandbox.md`](./guide-stripe-sandbox.md).  
**Kill :** webhook non signé 200 + crédit ; double crédit au retry ; PAN dans un log ; crédit au chargement de `?checkout=success`.

Ne pas ouvrir B3 si le solde ne bouge **que** via le webhook signé.

---

### B3 — Intégration live (RG-SUB-02)

**Durée :** 1–2 jours.  
**Prérequis :** B2 (un vrai solde > 0).  
**CDC :** RG-SUB-02, RG-INT-02, critère §17.5 (seconde moitié).

**Livrable :** avec solde ≥ 0,90 $, un developer/admin/owner crée une intégration `live`, voit `ky_live_…` **une fois**. À solde 0, l’option live reste refusée. Le GET ultérieur de la fiche **n’a plus** le secret.

| In | Out |
|---|---|
| `IntegrationService.create` : si `live`, `CreditService.coversUnit()` sinon **403** `insufficient_credit` | SageMaker, AWS |
| UI : option `live` **enabled** si `live_unlocked` | Publishable key navigateur |
| SecretBlock titre = mode live | Seconde clé sur la même intégration (unicité V16) |
| Copy : plus « recharge pas encore branchée » | Débit (B4) |

`issueKey` sur une intégration **déjà** live : autorisé si `API_KEY_WRITE` (la garde RG-SUB-02 est à la **création** de l’intégration). V16 : une clé max / intégration → `key_exists` inchangé.

Ne **pas** détruire une intégration live si le solde retombe sous une unité : elle reste ; B4 refusera les **nouvelles** vérifs.

**Tests :** `LiveIntegrationGateTest` — solde 0 → 403 `insufficient_credit` (plus `live_locked`) ; après topup test → 201 `mode=live`, `key` préfixe `ky_live_` ; GET fiche sans `key` ; member 403 create ; org B 404 sur la fiche A. `IntegrationLifecycleTest` : remplacer l’assert `live_locked` par le nouveau code.

**UI :** `create-form.tsx` — `live` plus `disabled` quand `live_unlocked` ; erreur `insufficient_credit` → lien `/settings/billing`. SecretBlock live : label `ky_live_`, pas « mode test ».

**Démo :** B2 (50 $) → créer « Production » live → copier `ky_live_` → recharger la fiche : préfixe seulement.  
**Kill :** `ky_live_` émise à solde 0 ; secret re-lisible ; developer sans `API_KEY_WRITE` qui passe.

Ne pas ouvrir B4 si on peut obtenir une `ky_live_` sans passer par un crédit B2.

---

### B4 — Débit live + usage

**Durée :** 2–3 jours.  
**Prérequis :** B3.  
**CDC :** RG-SUB-03, RG-SUB-04, UC-SUB-02, critères §17.6 et §17.7.

**Livrable :** une `POST /v1/verifications` (Bearer `ky_live_` **ou** console + `integration_id` live) débite **900** cents **dans la même transaction** que l’insert. Une vérif **test** ne touche pas le solde. Solde 0,50 $ → **402** `insufficient_credit`, pas de session, intégrations test encore OK. `GET /v1/usage` (Bearer) = totaux de `/settings/billing`.

| In | Out |
|---|---|
| `CreditService.debitForLiveVerification` appelé depuis `VerificationService.create` **après** les gardes d’idempotence, **avant** le persist si on veut échouer proprement — ordre : idempotency replay **sans** redébit ; puis `FOR UPDATE` + débit ; puis insert vérif | Débit à la décision / au webhook M5 |
| Replay `Idempotency-Key` → **200** déjà créé, **0** second débit | Compteurs biométrie / AML |
| `GET /v1/usage` Bearer | Meter Stripe |
| Accueil + billing : vrais `sandbox_count` / `live_count` | Plafond silencieux, alerte solde |
| Message console 402 : recharge + test toujours possible | |

**Ordre dans `create` (ne pas inverser)**

1. Idempotency existante + même hash → return 200, **pas** de débit.
2. `external_id` conflict → 409, pas de débit.
3. Si `integration.isLive()` : `debit` 900 cents, `resource=verification:{id}` (id déjà tiré). Solde < 900 → **402**, aucun persist.
4. Insert vérif + token hébergé + audit `verification.created` + `credit.debited`.

Course : deux `POST` live parallèles, solde = une unité → **une** 201, **une** 402. Couvert par `SELECT FOR UPDATE`.

**`GET /v1/usage`**

| Méthode | Chemin | Auth | Succès |
|---|---|---|---|
| `GET` | `/v1/usage` | Bearer `ky_test_` **ou** `ky_live_` | usage **de l’org** (le crédit n’est pas par intégration) |

Cookie seul → **401** (plan machine, comme `/v1/verifications`). Même JSON `currency` / `balance_minor` / `usage[]` que le sous-objet billing console.

**Tests :** `LiveDebitTest` — test create × N, solde inchangé ; live create, solde −900, ledger `debit` ; deuxième live jusqu’à épuisement → 402, `verifications` count inchangé ; replay idempotency live → pas de double débit ; Bearer org B 404 sur usage de A (usage est *l’org de la clé*, pas un id) ; `GET /v1/usage` chiffres = `GET /v1/console/billing`. `SandboxNoAwsTest` / capture restent verts (pas de régression test).

**UI :** bandeau / toast sur create live 402 ; billing : section consommation IDV (count + débit) ; badges live Open / Bloqué selon `live_unlocked` ; accueil n’est plus à 0.

**Audit :** `credit.debited` `{ "amount_minor": 900, "product": "identity" }` — l’id de ressource est la colonne, pas de PII applicant.

**Démo (critères §17.5–17.7) :** Checkout test → solde 50 $ → intégration live → `POST /v1/verifications` live → solde 49,10 $ → `GET /v1/usage` identique à l’écran → vider presque le solde → create live 402, create test 201.  
**Kill :** test débité ; live créée sans débit ; 402 après insert orphelin ; usage console ≠ API ; AWS appelé (M5 n’est pas ouvert).

Si B4 n’est pas démontrable, **ne pas** ouvrir M5.

---

## 7. Surfaces et fichiers (indicatif)

| Zone | B1 | B2 | B3 | B4 |
|---|---|---|---|---|
| `api/` Flyway V17+ | `credit_*` | `stripe_customers` | — | — |
| `CreditService` | account + GET | `topup` idempotent | `coversUnit` | `debit` + lock |
| `StripePort` / webhook | — | Checkout + signature | — | — |
| `IntegrationService` | — | — | garde solde | — |
| `VerificationService.create` | — | — | — | débit live |
| `SecurityConfig` | `/v1/console/billing` | `/v1/webhooks/stripe` public | — | `/v1/usage` Bearer |
| `web/console` billing + home | lecture API | bouton Checkout | option live | 402 + conso |
| `web/site/lib/pricing.ts` | freeze, `PROVISIONAL=false` | — | — | — |
| Tests Java | isolation billing | webhook replay | `ky_live_` gate | débit / usage |

Console : Server Action `createCheckoutAction` (même pattern que `createIntegrationAction`). Pas de `sk_test_` dans Next — l’API crée la session.

---

## 8. Mapping critères CDC

| Critère | Palier |
|---|---|
| §17.3 `/pricing` = sandbox gratuit + crédit + live à l’unité + carte | **B1** (montants figés ; copy déjà M1) |
| §17.5 recharge carte test → solde → intégration live | **B2** + **B3** |
| §17.6 solde insuffisant → plus de vérif live, test OK, message clair | **B4** |
| §17.7 `GET /v1/usage` / solde = `/settings/billing` | **B4** |
| §17.10 isolation 404 | **B1**, rejoué B2 (metadata) |
| NF-MVP-09 signature + idempotence webhooks Stripe | **B2** |
| §17.9 live AWS | **M5** (après B4) |

---

## 9. Suite

| Après B4 vert | Sprint |
|---|---|
| Capture + stub si pas déjà démontrés | **M4** (peut déjà être vert en parallèle) |
| Textract + Rekognition + webhook **client** par intégration | **M5** |
| Rate limit, rétention, staging, Stripe **live** après juridique | **M6** |

M5 ne change pas le ledger. Il refuse toujours le live si B4 renvoie 402, **avant** tout appel AWS (COGS).

---

## 10. Suivi

- Changement de prix / devise / packs / moment de débit → **CDC §10** puis cette roadmap §5.
- Glissement « on débite à la décision » → CDC §10.4 **avant** B4.
- Paiement Stripe live (vrai argent) → M6 + relecture CGU / privacy ; **interdit** de poser `sk_live_` pour « finir M3 ».
- Prochain palier à ouvrir : **M5** seulement après B4 **et** M4. Stripe **live** (`sk_live_`) reste **M6**.

**Journal**

| Date | Changement |
|---|---|
| 17 sept. 2026 | Freeze : **USD**, **0,90 $** / vérif live, packs **50 / 100 / 250 / 500 $**. |
| 17 sept. 2026 | B1–B4 livrés : ledger V17, Checkout test + webhook signé, `ky_live_` si solde ≥ 900 ¢, débit à `POST /v1/verifications` live, `GET /v1/usage`. |
