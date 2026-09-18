# Guide — Intégration Stripe sandbox (Checkout carte test)

**Plateforme :** Recogniz-Me  
**Sprint :** M3 — crédit d’organisation, recharge carte  
**Version :** 1.0  
**Date :** 18 septembre 2026  
**Statut :** le code B2 est livré ; ce document dit **comment brancher le sandbox local**  
**CDC :** §10, RG-SUB-05/06, NF-MVP-09. Critère §17.5 (première moitié).

**Documents liés :**
- [`roadmap-implementation-m3.md`](./roadmap-implementation-m3.md) — ordre B1–B4 (*quand*)
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — contrat métier
- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — Stripe live = **M6**
- [`../../api/application-secrets.properties.example`](../../api/application-secrets.properties.example) — modèle de secrets (ne pas committer le fichier réel)

Le code Checkout / webhook **est déjà là**. Ce guide sert à passer de `kyc.stripe.mode=log` (faux Checkout) à un **vrai sandbox Stripe** (`sk_test_`). Pas d’abonnement, pas de Customer Portal, pas de clés `sk_live_`.

---

## 1. Ce que Recogniz-Me fait avec Stripe

Achat unique (Checkout `mode=payment`) pour recharger un pack de crédit d’organisation. Devise **USD**. Prix unitaire live : **0,90 $** (`900` cents).

| Pack affiché | `pack_minor` (cents) |
|---|---|
| 50 $ | `5000` |
| 100 $ | `10000` |
| 250 $ | `25000` |
| 500 $ | `50000` |

### Happy path

1. Owner connecté → `/settings/billing` → clic pack.
2. Console appelle `POST /v1/console/billing/checkout`.
3. L’API crée (ou réutilise) un Customer Stripe, puis une session Checkout.
4. Le navigateur va sur `https://checkout.stripe.com/…`.
5. Carte **test** → Stripe envoie `checkout.session.completed`.
6. **Seulement alors** le ledger crédite le solde.

Le retour `?checkout=success` est de l’UX. **Il ne crédite rien.** Sans webhook, le solde reste à 0.

Clés live (`sk_live_`) : **refusées** dans `StripeCheckoutAdapter`. Sandbox uniquement pour ce cycle.

**Kill :** webhook non signé accepté + crédit ; double crédit au retry ; PAN / secret dans un log ; crédit au chargement de `?checkout=success`.

---

## 2. Sandbox vs live

Chez Stripe, « test mode » = **sandbox**.

| | Sandbox | Live |
|---|---|---|
| Clé secrète | `sk_test_…` | `sk_live_…` |
| Clé publique | `pk_test_…` | `pk_live_…` |
| Argent réel | Non | Oui |
| Cartes | `4242…` etc. | Vraies CB |

Ici tu n’as **pas besoin de `pk_test_`**. Le Checkout est hébergé chez Stripe ; seule la **clé secrète serveur** parle à l’API.

Objets sandbox et live sont **isolés**. Un Customer `cus_…` créé en test n’existe pas en live.

---

## 3. Compte Dashboard

1. Crée un compte sur [dashboard.stripe.com](https://dashboard.stripe.com) (ou utilise le compte existant).
2. En haut à gauche, sélecteur de compte → **Sandboxes**.
3. Reste dans le sandbox par défaut (ou un sandbox dédié « Recogniz-Me local »).
4. **Developers → API keys**.
5. **Reveal** la Secret key → elle doit commencer par `sk_test_`.

À ne jamais faire :

- coller `pk_test_` dans `kyc.stripe.secret-key` (l’API refuse les `pk_`)
- coller `sk_live_` (refus explicite)
- committer `api/application-secrets.properties`

---

## 4. Secrets locaux

`api/src/main/resources/application.properties` charge :

```properties
spring.config.import=optional:file:./application-secrets.properties
```

Copie si besoin :

```text
api/application-secrets.properties.example  →  api/application-secrets.properties
```

Dans **`api/application-secrets.properties`** (racine du module `api/`, pas dans `src/`) :

```properties
kyc.stripe.mode=stripe
kyc.stripe.secret-key=sk_test_xxxxxxxx
kyc.stripe.webhook-secret=whsec_xxxxxxxx
```

Équivalent variables d’environnement :

| Variable | Propriété |
|---|---|
| `KYC_STRIPE_MODE=stripe` | `kyc.stripe.mode` |
| `STRIPE_SECRET_KEY=sk_test_…` | `kyc.stripe.secret-key` |
| `STRIPE_WEBHOOK_SECRET=whsec_…` | `kyc.stripe.webhook-secret` |

| Propriété | Rôle |
|---|---|
| `kyc.stripe.mode=log` | Faux Checkout (défaut actuel de `application.properties`) |
| `kyc.stripe.mode=stripe` | Vrai appel Stripe (`StripeCheckoutAdapter`) |
| `kyc.stripe.secret-key` | Auth API Stripe |
| `kyc.stripe.webhook-secret` | Vérif du header `Stripe-Signature` |

Sans `mode=stripe`, `LoggingStripeAdapter` gagne : URL `https://checkout.stripe.com/c/pay/cs_log_…` qui **ne paie rien**.

Redémarrer l’API après **chaque** changement de secret.

---

## 5. Webhooks en local — Stripe CLI (obligatoire)

Stripe ne peut pas POST-er sur `http://localhost:8080`. En local, le **Stripe CLI** ouvre un tunnel et forward les events.

### 5.1 Installer / login (Windows)

```powershell
winget install Stripe.StripeCLI
stripe login
```

Le navigateur autorise le CLI sur **le même sandbox** que tes clés `sk_test_`.

### 5.2 Écouter (laisser tourner)

API déjà sur le port **8080** :

```powershell
stripe listen --forward-to localhost:8080/v1/webhooks/stripe --events checkout.session.completed
```

Le CLI affiche :

```text
Ready! Your webhook signing secret is whsec_...
```

**Ce `whsec_` n’est pas celui du Dashboard.** Il est **propre à cette session `listen`**. Copie-le dans `kyc.stripe.webhook-secret`, **redémarre l’API**.

Si tu relances `stripe listen` et que le secret change → **400** `invalid_signature` jusqu’à resync.

Ne crée **pas** d’endpoint Dashboard `http://localhost:…` : ça ne marchera jamais. Le CLI suffit en local.

---

## 6. Démarrer la stack

Terminaux séparés :

1. MySQL + Redis : `docker compose -f infra/docker-compose.yml up -d`
2. API : `cd api` puis `mvn spring-boot:run`
3. Console : `web/console` (port **3000** = `kyc.public-console-base-url`)
4. `stripe listen …` (§5)

Health : `GET http://localhost:8080/v1/health`

Au boot, tu dois avoir **`StripeCheckoutAdapter`**, pas le warning `kyc.stripe.mode=log: fake Checkout URL`.

---

## 7. Happy path (démo)

1. Compte **owner** (seul rôle avec `BILLING_WRITE`). Admin / member → **403** sur le checkout.
2. Console → **Billing** (`/settings/billing`). Solde initial : **$0.00**.
3. Clic **Payer par carte** sur le pack **50 $**.
4. Redirect Checkout Stripe.
5. Carte test :

| Champ | Valeur |
|---|---|
| Numéro | `4242 4242 4242 4242` |
| Date | n’importe quelle date **future** |
| CVC | `123` |
| ZIP | `12345` |
| Nom | n’importe |

6. Paiement OK → redirect `http://localhost:3000/settings/billing?checkout=success`.
7. Dans le terminal CLI : `checkout.session.completed` → **200**.
8. **Recharger** la page billing (le GET success ne met pas à jour le ledger tout seul).
9. Solde **$50.00**, ledger : une ligne `topup` +5000 cents.

Ensuite (produit, pas Stripe) : solde ≥ 0,90 $ → création d’intégration **live** (`ky_live_`). Une vérif live débite 900 cents ; une vérif **test** ne débite jamais. Détail B3–B4 : [roadmap M3](./roadmap-implementation-m3.md).

---

## 8. Cartes de test

| Carte | Effet |
|---|---|
| `4242 4242 4242 4242` | Succès (Visa) |
| `4000 0000 0000 0002` | Refus générique |
| `4000 0000 0000 9995` | Fonds insuffisants |
| `4000 0000 0000 3220` | 3D Secure |

En sandbox, **aucune vraie CB**. Une vraie carte avec `sk_test_` est refusée.

---

## 9. Architecture (debug)

```
Owner console
  POST /v1/console/billing/checkout   cookie + BILLING_WRITE
       │
       ▼
  CreditService.createCheckout
       │  Customer get-or-create → table stripe_customers
       │  Session Checkout USD, 1 line item = pack
       │  metadata: organization_id, pack_minor, user_id
       ▼
  StripeCheckoutAdapter  (si mode=stripe)
       ▼
  redirect checkout.stripe.com

Stripe
  POST /v1/webhooks/stripe   public, auth = signature
       │
       ▼
  StripeWebhookService
       │  Webhook.constructEvent(payload brut, Stripe-Signature, whsec)
       │  ignore tout sauf checkout.session.completed + payment_status=paid
       ▼
  CreditService.topupFromCheckout
       │  idempotent: stripe_event_id + stripe_checkout_session_id
       │  customer Stripe doit matcher l’org des metadata
       ▼
  credit_accounts.balance_minor += pack
```

### Fichiers

| Fichier | Rôle |
|---|---|
| `api/src/main/java/com/kyc/adapters/StripeCheckoutAdapter.java` | Vrai Stripe (`mode=stripe`) |
| `api/src/main/java/com/kyc/adapters/LoggingStripeAdapter.java` | Faux Checkout (`mode=log`) |
| `api/src/main/java/com/kyc/controllers/ConsoleBillingController.java` | `GET` billing, `POST` checkout |
| `api/src/main/java/com/kyc/controllers/StripeWebhookController.java` | `POST /v1/webhooks/stripe` |
| `api/src/main/java/com/kyc/services/CreditService.java` | Ledger, Customer, topup |
| `api/src/main/java/com/kyc/services/StripeWebhookService.java` | Signature + dispatch |
| `web/console/app/(app)/settings/billing/` | UI |

### Règles HTTP

| Cas | Status |
|---|---|
| Pack hors liste | **400** |
| Webhook non signé / mauvais `whsec` | **400** `invalid_signature` |
| Event inconnu ou session non payée | **200** ignore (évite le retry infini) |
| Replay du même event | une seule ligne ledger |
| Checkout sans `BILLING_WRITE` | **403** |
| Cookie absent | **401** |

Org A ne peut pas être créditée avec le `organization_id` de B dans les metadata.

---

## 10. Dashboard (staging / URL publique)

Quand l’API a une URL **publique** (staging, tunnel `ngrok` / Cloudflare) :

1. **Developers → Webhooks → Add endpoint**
2. URL : `https://<domaine>/v1/webhooks/stripe`
3. Event : **`checkout.session.completed` seulement** (pas `payment_intent.succeeded` en plus → double crédit)
4. Copier le **Signing secret** (`whsec_…`) du **endpoint**, pas la clé API

Le `whsec` Dashboard et le `whsec` du CLI sont **différents**. Un seul à la fois dans la config : celui qui envoie réellement les POST.

---

## 11. Dépannage

| Symptôme | Cause probable |
|---|---|
| URL Checkout `cs_log_…` / warning dans les logs | `kyc.stripe.mode` encore `log` |
| `Stripe is unavailable` + « must be sk_test » | Tu as mis `pk_test_` |
| `Stripe live keys are not allowed yet` | `sk_live_` |
| Paiement OK, solde 0 | `stripe listen` éteint, ou `whsec` du CLI ≠ config, ou page non rechargée |
| 400 signature | `whsec` périmé après relance du CLI ; API pas redémarrée |
| 403 checkout | Pas owner (`BILLING_WRITE`) |
| Checkout créé, webhook `topup_ignored` | Customer / metadata org ne matchent pas (souvent un `cus_log_` resté en base après un clic en mode `log`) |
| Fausse URL Stripe 404 | Mode `log` : l’URL n’est pas une vraie session |

Si tu as d’abord testé en `log`, la table `stripe_customers` peut contenir `cus_log_…`. Au passage en `mode=stripe`, `CreditService` détecte ça (`cus_log_` n’est pas un vrai Customer) et **recrée** un Customer Stripe.

Logs utiles (sans PAN, c’est voulu) :

```text
stripe webhook event_id=evt_… type=checkout.session.completed code=processed
stripe webhook event_id=evt_… code=topup_ignored
```

Vérif manuelle de l’endpoint :

```powershell
curl -X POST http://localhost:8080/v1/webhooks/stripe
```

Sans header `Stripe-Signature` → **400**, pas 200. C’est le comportement attendu.

---

## 12. Checklist « sandbox OK »

- [ ] Dashboard en **sandbox**, clé `sk_test_`
- [ ] `kyc.stripe.mode=stripe`
- [ ] `stripe listen` vers `localhost:8080/v1/webhooks/stripe`
- [ ] `whsec_` du CLI collé, API **redémarrée**
- [ ] Owner, pack 50 $, carte `4242…`
- [ ] Billing = **$50.00**, une ligne topup
- [ ] Relancer le **même** event (replay webhook) ne double pas le solde

---

## 13. Hors cycle

Pas d’abonnement, pas de Customer Portal, pas de CB stockée chez nous, pas de `pk_` dans le navigateur, pas de clés live.

Stripe **live** (`pk_live_` / `sk_live_`) : **M6**, après relecture CGU / privacy. Voir [roadmap MVP](./roadmap-implementation-mvp.md).
