# Roadmap d’implémentation — MVP SaaS

**Plateforme :** Recogniz-Me  
**Livrable :** premier service vendable (marque, vitrine, compte, crédit d’organisation, IDV)  
**Version du document :** 1.4 — crédit d’organisation, recharge carte ; pas de facturation produit  
**Date :** 9 septembre 2026  
**Statut :** ordre de build du MVP  
**Documents liés :**
- [`cahier-des-charges-mvp.md`](./cahier-des-charges-mvp.md) — *quoi* (contrat métier)
- [`../cahier-des-charges.md`](../cahier-des-charges.md) — vision IA (hors calendrier MVP)
- [`../specs/specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md) — métier IDV
- [`../specs/guide-implementation-s1.md`](../specs/guide-implementation-s1.md) — fondation **livrée**
- [`../specs/guide-implementation-s2.md`](../specs/guide-implementation-s2.md) — capture (à reprendre dans M4, **sans** dataset)
- [`specification-m2-compte-client.md`](./specification-m2-compte-client.md) — M2 compte + équipe T (spécification as-built)
- [`guide-implementation-m2.md`](./guide-implementation-m2.md) — M2 compte (comment construire)
- [`roadmap-implementation-team.md`](./roadmap-implementation-team.md) — cycle T (équipe type Onfido / Veriff)
- [`charte-visuelle.md`](./charte-visuelle.md) — M0 **livré** (as-built)

Ce document dit **quand** et **dans quel ordre** on construit le MVP. Le *quoi* reste dans le CDC. Un sprint n’est pas vert sans son **livrable démontrable**.

La [roadmap IDV S3–S9](../specs/roadmap-implementation-idv.md) (SageMaker, dataset, modèles proprio) **n’est pas** le calendrier de ce cycle. Elle reprend **après M6**.

---

## 1. Cible

**Happy path commercial :** un visiteur comprend l’offre sur la vitrine → crée un compte → obtient une clé sandbox → l’applicant va au bout du lien (pièce + visage) → une **décision justifiée** apparaît en console → le client recharge par carte et crée une clé live.

Sans ce chemin, le MVP n’est pas livré — même si un morceau d’IDV ou de design est « presque fini ».

---

## 2. Principes d’ordre

1. **Marque avant vitrine** — pas de site au look actuel divergent (console navy / flow beige).
2. **Compte avant facturation** — Stripe n’a rien à rattacher sans utilisateur + organisation.
3. **Stub avant AWS** — le métier IDV (statuts, raisons, isolation) se prouve à 0 $ d’IA.
4. **Capture avant analyse** — pas d’OCR / Rekognition sur un flux sans média réel.
5. **Un livrable démontrable par sprint.** Si le critère n’est pas là, on n’ouvre pas le sprint suivant de la même chaîne.
6. **Vérité commerciale** — biométrie auth et AML restent des pages teaser ; jamais un bouton d’activation.

**Kill (tout le MVP) :** fuite tenant ; PII, mot de passe, clé brute ou CB dans les logs ; paiement live sans CGU/privacy ; `ky_live_` sans crédit suffisant ; vitrine qui vend AML / biométrie.

---

## 3. Vue d’ensemble

Durée indicative : **1–2 semaines par sprint**. Calendrier tendu : ~8 semaines. Calendrier confortable : ~14 semaines. S1 (fondation) est **déjà livré** et ne se rejoue pas.

```
S1 fondation (fait)
        │
        ▼
       M0 charte ─────────────────────────────────┐
        │                                         │
        ├──────────────► M1 vitrine               │
        │                     │                   │
        └──────────────► M2 comptes ──► M3 Stripe │
                              │                   │
                              ├────────► T équipe (T1 ∥ M4 ; T2 avec M3)
                              ▼                   │
                         M4 capture + IDV stub ◄──┘ (tokens M0)
                              │
                              ▼
                         M5 AWS live + webhooks
                              │
                              ▼
                         M6 durcissement / staging
```

| Sprint | Chaîne | Livrable démontrable | Statut |
|---|---|---|---|
| **S1** | IDV | Session, isolation, lien, consentement | **Livré** |
| **M0** | Marque | Charte + tokens sur console et flow existants | **Livré** ([charte](./charte-visuelle.md)) |
| **M1** | Vitrine | Site public, pages CDC §8.1, CTA compte | **Livré** (`web/site`, FR + EN) |
| **M2** | Compte | Signup → e-mail → login → org Sandbox → console derrière session | **Livré** ([spec](./specification-m2-compte-client.md)) |
| **T** | Équipe | Cycle de vie puis rôles type Onfido / Veriff | **T0–T4 livrés** ([roadmap](./roadmap-implementation-team.md)) |
| **M3** | Facturation | Checkout carte test → crédit → `ky_live_` → débit à l’unité | à faire |
| **M4** | IDV | Capture + pipeline stub → décision sans AWS | à faire |
| **M5** | IDV live | Textract + Rekognition + webhook résultat | à faire |
| **M6** | Go-live | Rate limit, rétention affichée, staging, 1 design partner sandbox | à faire |

### Parallélisme autorisé

| En même temps | Condition |
|---|---|
| **M1 ∥ M2** | Tokens M0 fusionnés (même logo, mêmes actions) |
| **M3** après M2 seulement | — |
| **T1 ∥ M4** | M2 vert ; [roadmap équipe](./roadmap-implementation-team.md) |
| **T2** avant ou avec **M3** | Helper de droits (`BILLING_*`) avant les routes Stripe |
| **M4** après M2 (console authentifiée) ; capture peut démarrer dès M0 si la session S1 suffit, mais la **démo M4** se fait en compte connecté | Cookies console en place |
| **M5** après **M4 et M3** | Stub métier vert **et** clés live existantes |
| Contenu légal / tarifs | Rédaction dès M1 ; **prix figés avant M3** |

Interdit : M5 sans M4 ; M3 sans M2 ; M1 sans M0.

---

## 4. Sprints

### M0 — Identité visuelle

**Durée :** 1 semaine.  
**CDC :** §7. **Objectif O1.**

**Livrable :** un développeur ouvre console et flow et voit **la même marque**. Un nouveau fichier CSS n’invente pas de hex.

| In | Out |
|---|---|
| Logo (symbole + wordmark), favicon | Refonte fonctionnelle de la console |
| Tokens CSS partagés (package ou fichier unique) | Illustration 3D, motion design |
| Palette, typo unique, radius, focus, badges statut | 2FA, nouvelles routes |
| Application `web/console` + `web/flow` | Site vitrine (M1) |
| Kit e-mail (maquette HTML minimale) | Mode sombre |

**As-built :** direction figée **thème clair, accent vert** ; tokens dans le workspace `packages/brand` (`tokens.css`, `base.css`, `console.css`, `flow.css`), composants `Logo` / `Wordmark` / `StatusBadge`, favicons SVG, gabarit e-mail. Détail : [`charte-visuelle.md`](./charte-visuelle.md).

Les exports PNG du logo ont suivi : générés par script, audités, décrits en [charte §4.2](./charte-visuelle.md). L’export Open Graph sert la vitrine M1.

**Démo :** side-by-side console + flow + le gabarit e-mail.  
**Kill :** deux logos, ou information de statut portée par la seule couleur.

---

### M1 — Site vitrine

**Durée :** 1–2 semaines.  
**CDC :** §8, §6. **Objectifs O2, O6.**  
**Prérequis :** M0.

**Livrable :** `web/site` tourne en local ; un inconnu comprend IDV vs « bientôt » et clique **Créer un compte**.

| In | Out |
|---|---|
| Pages §8.1 (accueil, 3 produits, tarifs, sécurité, docs, légal, contact) | Blog, CMS |
| **FR + EN** (arbitré en cours de sprint : l’anglais entre au périmètre, CDC v1.1 §8) | Troisième langue |
| CTA primaire → signup console (URL M2 ; placeholder `/signup` acceptable tant que M2 n’est pas fusionné) | Moyen de paiement Stripe |
| Teasers biométrie / AML **sans** essayer / acheter | Certifications inventées |
| SEO de base, OG, favicon charte | Appels API métier depuis la vitrine |

**Travaux**

- App Next.js `web/site`, SSG, tokens M0.
- Copy : corridor **court et vrai** ; pas d’IA propriétaire ; délai « quelques minutes ».
- `/pricing` : sandbox gratuit, live IDV à l’unité (montants **provisoires** jusqu’au freeze M3).
- Brouillons `/legal/terms`, `/privacy`, `/dpa` dans le repo.
- Contact : e-mail ou formulaire + honeypot.

**Démo :** parcours accueil → IDV → tarifs → teaser AML (pas de bouton d’achat) → CTA compte.  
**Kill :** page qui présente AML ou biométrie comme activable ; « 200+ documents ».

**As-built**

Workspace `web/site` (port 3002), 11 routes × 2 langues = **22 pages prégénérées**, aucune n’est rendue à la demande. Atmosphère `packages/brand/site.css` (RG-BRAND-07).

| Sujet | Choix retenu |
|---|---|
| i18n | Dictionnaires JSON `messages/{fr,en}.json`, **sans dépendance** : le type `Messages` est inféré de `fr.json`, donc une clé absente en anglais casse le typecheck |
| Routage | Segment `[locale]`, les deux langues préfixées ; middleware pour négocier `Accept-Language` sur une URL sans préfixe |
| URL | Segments identiques dans les deux langues (CDC RG-SITE-04) : changer de langue conserve la page lue |
| Tarifs | Prix unitaire IDV live dans `lib/pricing.ts`, formatés par `Intl` — le gel M3 ne touche qu’un fichier |
| Contact | Honeypot + validation serveur ; acheminement par `CONTACT_WEBHOOK_URL`. **Sans cette variable le formulaire refuse d’envoyer** et renvoie vers l’e-mail direct, plutôt que d’afficher un faux succès |
| Indexation | Refusée par défaut (`NEXT_PUBLIC_SITE_INDEXABLE`), pour qu’un aperçu ne soit pas indexé |
| Garde-fous | `check:messages` (parité des clés, arité des listes, chaînes vides) et `check:seo` (canonique, `hreflang`, OG existante sur les 22 pages) |

**Reste à faire hors M1 :** poser `CONTACT_WEBHOOK_URL` avant mise en ligne ; brancher le CTA sur le vrai `/signup` à la fusion de M2 ; figer les montants au M3 ; faire relire les trois brouillons juridiques (ils portent un bandeau « brouillon » jusque-là).

---

### M2 — Compte client

**Durée :** 2 semaines.  
**CDC :** §9. **Objectif O3.**  
**Prérequis :** S1, M0. Peut ∥ M1.

**Spécification :** [`specification-m2-compte-client.md`](./specification-m2-compte-client.md).  
**Comment construire :** [`guide-implementation-m2.md`](./guide-implementation-m2.md).

**Livrable :** inscription → e-mail vérifié → login → organisation Sandbox → clé `ky_test_` **affichée une fois** → console (création de vérif S1) **inaccessible sans session**.

| In | Out |
|---|---|
| `User`, `Membership`, tokens e-mail / reset | SSO, 2FA, multi-org |
| Signup, verify, login, logout, forgot password | Facturation (M3) |
| Création org + slug unique ; sandbox gratuit (pas un plan) | Clés `ky_live_` |
| Cookie session httpOnly (console) ; API `/v1` **reste** Bearer | Auth API par cookie |
| Rôles propriétaire / membre + invitation e-mail | SCIM, RBAC fin |
| Écrans : login, signup, clés API (test), équipe, compte | |

**Travaux API**

- Migration Flyway : users, memberships, tokens.
- Hash mot de passe ; e-mail unique ; audit `user.registered`, `user.email_verified`, `membership.invited`.
- Routes console (cookie) distinctes des routes `/v1` (clé).
- Émission de la première `ky_test_` à la vérification d’e-mail.
- Orgs seed S1 : garder pour les tests API ; la console interactive passe par un vrai user.

**Travaux console**

- Middleware : non authentifié → `/login`.
- Brancher le CTA vitrine (M1) sur `/signup`.
- Accueil : org + sélecteur de services (usage à 0 jusqu’à M3).

**Tests minimaux :** e-mail déjà pris ; session expirée ; membre ne révoque pas les clés ; isolation user A / org B.

**Démo :** compte neuf → mail (Mailhog / log dev) → clé test → nouvelle vérif S1.  
**Kill :** console anonyme encore ouverte ; secret de clé re-lisible après navigation.

---

### M3 — Crédit d’organisation (recharge carte)

**Durée :** 1–2 semaines.  
**CDC :** §10. **Objectif O4.**  
**Prérequis :** M2. **Freeze pricing** avant le premier Checkout (voir §6).

**Livrable :** Stripe Checkout **test** (carte) → crédit au ledger → `ky_live_` créable si solde ≥ une unité → solde / `GET /v1/usage` = écran `/settings/billing`. Solde insuffisant → plus de vérif live, sandbox OK.

| In | Out |
|---|---|
| Checkout Stripe : packs de recharge **carte** | Abonnement, meter, facture d’usage, multi-devises |
| Webhooks Stripe signés, miroir customer / session Checkout | Stockage de CB |
| Ledger interne (source de vérité du **solde**) | Portail factures, virement, avoir ops, recharge auto |
| `GET /v1/usage` par service (relevé, pas un encaissement) | Compteurs biométrie / AML |
| Blocage `ky_live_` si solde < une unité | SageMaker |

**Travaux**

- Entités `CreditAccount` / `CreditLedgerEntry` (pas de table « Plan »).
- RG-SUB-01…07.
- Comptage : **à la création** d’une vérif live (recommandation CDC) — figé ici.
- Sandbox : **jamais** débité.
- Console : `/settings/billing` seulement (solde, recharge carte, ledger, consommation). Pas d’onglet facturation produit.
- `/pricing` vitrine : montants **réels** après freeze. Canal MVP = carte.

**Tests :** webhook rejoué (idempotence) ; `ky_live_` refusée sans solde ; débit live ; org B ne voit pas le ledger de A.

**Démo :** Stripe CLI Checkout test → solde crédité → clé live → une `POST /v1/verifications` live débite le crédit.  
**Kill :** clé live sans crédit suffisant ; webhook Stripe non signé.

---

### M4 — Capture + IDV stub

**Durée :** 2 semaines.  
**CDC :** §11 (stub). **Objectif O5 (sandbox).**  
**Prérequis :** S1, M2 (démo en compte). Tokens M0 sur le flow.

Reprend le [guide S2](../specs/guide-implementation-s2.md) pour la **capture pièce**, puis **étend** (le guide S2 s’arrête avant selfie et OCR) :

1. Capture pièce (qualité client + PUT signé + qualité serveur + recapture, plafond 3).
2. Capture selfie + challenge liveness **stub** (`liveness_pass` / fail injectable).
3. Worker : adaptateur **stub** (champs d’identité, MRZ optionnelle, scores).
4. Règles de décision → `approved` / `rejected` / `review` + raisons **sans PII**.
5. Console : fiche (extraits, signaux, médias **URL signée courte**), file revue (analyste du client).

**Pas dans M4 :** dataset / Ground Truth / annotation (hors CDC MVP) ; Textract ; Rekognition ; Canny vendu comme moteur.

| In | Out |
|---|---|
| Stockage réel local (filesystem / MinIO) | Appels AWS IA |
| Statuts document, selfie, processing, décisions, recapture | Enrôlement, AML attaché |
| Stubs déterministes (fixtures sandbox) | Modèle authenticité |
| Corridor : mapping stub ; hors liste → `unsupported_document` | SDK natif |

**Démo :** compte sandbox → lien → consentement → pièce → selfie → statut terminal en console, raisons visibles, 0 appel AWS.  
**Kill :** binaire dans le JSON métier ; média cross-tenant ; décision sans raisons ; token / PII dans les logs.

Si M4 n’est pas démontrable, **ne pas** ouvrir M5.

---

### M5 — AWS live + webhooks

**Durée :** 2 semaines.  
**CDC :** §11 live, §11.6, critère §17.9. **Objectif O5 (production).**  
**Prérequis :** M4 **et** M3.

**Livrable :** une vérif **`ky_live_`** en staging parcourt Textract AnalyzeID + CompareFaces (+ Face Liveness **ou** liveness stub **documenté** si Liveness n’est pas prêt — alors mettre à jour le CDC §17.9 **avant** de déclarer M5 vert). Webhook `verification.completed` signé, **sans** médias dans le payload.

| In | Out |
|---|---|
| Adaptateurs `AwsDocumentAi` / `AwsBiometric` derrière les ports existants | SageMaker, Bedrock, Ground Truth |
| Choix stub vs AWS selon préfixe de clé (`ky_test_` vs `ky_live_`) | Heuristiques de coins en prod |
| Parseur MRZ déterministe si zone présente | Authenticité ML |
| `POST /v1/webhooks` + livraison signée + retry borné | Catalogue d’événements vision (liveness.started, etc.) |
| Hors corridor AnalyzeID → `unsupported_document` | |

**Travaux**

- Qualité **avant** tout appel payant (CDC / COGS).
- Journaliser `verification_id` + type d’appel AWS, **pas** les champs d’identité.
- Console : même fiche qu’en stub (extraits réels).
- Sandbox : **toujours** 0 Textract / Rekognition (test automatisé).

**Démo :** 1 passeport de test live → décision ; webhook reçu par un endpoint de test ; org B 404.  
**Kill :** sandbox qui facture AWS ; URL média permanente dans un webhook ; juge LLM.

---

### M6 — Durcissement et staging

**Durée :** 1–2 semaines.  
**CDC :** §15–§17.  
**Prérequis :** M1–M5.

**Livrable :** staging HTTPS ; 1 design partner **sandbox** (compte réel, pas seed) ; checklist §17 cochée.

| In | Out |
|---|---|
| Rate limit login + `POST /v1/verifications` | 2FA, KMS prod complet (vision S8) |
| Rétention documentée (`/security` + privacy) + purge manuelle ou job simple | SOC 2, ISO |
| Revue logs (zéro PII) | Lancement grand public |
| Processus clôture compte (manuel OK s’il est écrit) | |
| Relecture juridique **avant** Stripe **live** (pas test) | |

**Démo :** partenaire crée un compte depuis la vitrine staging, sandbox bout en bout ; paiement **test** seulement tant que le juridique n’a pas signé.  
**Kill :** les kill globaux §2.

---

## 5. Mapping critères d’acceptation CDC

Chaque critère CDC §17 est **couvert par un sprint**. Le MVP n’est clos qu’à M6, mais on ne reporte pas un critère « à plus tard » sans mettre à jour le CDC.

| Critère CDC | Sprint |
|---|---|
| 1 — Charte unique | M0, vérifié M1 |
| 2 — Offre comprise, IDV vs bientôt | M1 |
| 3 — `/pricing` = sandbox gratuit + crédit + live à l’unité | M1 (provisoire) → M3 (figé) |
| 4 — Compte + `ky_test_` | M2 |
| 5 — Recharge carte → crédit → `ky_live_` | M3 |
| 6 — Solde insuffisant | M3 |
| 7 — Solde / usage API = console | M3 |
| 8 — IDV sandbox sans AWS | M4 |
| 9 — IDV live AWS | M5 |
| 10 — Isolation 404 | S1, rejoué M2–M5 |
| 11 — Webhook signé | M5 |
| 12 — `unsupported_document` | M4 stub, M5 live |
| 13–14 — Vérité commerciale | M1, revue M6 |

---

## 6. Décisions à figer

Ne bloquent **pas** M0–M2.

### Avant M3 (paiement)

| Décision | Proposition | Bloque |
|---|---|---|
| Devise Stripe | EUR **ou** CAD | M3 |
| Prix unitaire IDV live | Ordre de grandeur : ~0,90 € | M3 |
| Comptage | À la **création** live | M3 |
| Packs de recharge | 50 / 100 / 250 / 500 (unité de devise) | M3 |
| Canal MVP | **Carte** seule (Checkout) | M3 |
| Plafond de dépense / recharge auto | Hors MVP | — |

### Avant M4 (corridor affiché = corridor réel)

| Décision | Proposition | Bloque |
|---|---|---|
| Pays × types | Liste courte testée (ex. passeport CA/FR + CNI FR) | M4 copy + mapping |
| Politique pièce expirée | Refus | M4 |
| Seuil face match stub / live | Versionné, pas « au feeling » | M4 / M5 |
| Face Liveness AWS au M5 | Oui **ou** stub documenté + CDC §17.9 amendé | M5 |

### Avant paiement Stripe *live* (pas le mode test)

CGU / privacy relus. Sinon M6 reste en **test cards only**.

---

## 7. Surfaces et modules (rappel)

| Surface | Chemin | Sprints |
|---|---|---|
| Tokens / charte | package partagé ou `web/*/app/tokens.css` | M0 |
| Vitrine | `web/site` | M1 |
| Console | `web/console` | M0, M2, M3, M4, M5 |
| Hosted flow | `web/flow` | M0, M4, M5 |
| Capture SDK | `packages/capture-sdk` | M4 |
| API + workers | `api/` | M2–M5 |
| Infra locale | `infra/` | S1, MinIO/S3 M4 |

---

## 8. Done / hors MVP

**Le MVP est livré** quand les 14 critères CDC §17 sont vrais **et** qu’un design partner sandbox a enchaîné vitrine → compte → décision.

**Hors ce cycle (reprise vision / roadmap IDV) :**

- SageMaker, dataset, authenticité ML, liveness / OCR proprio
- Produits Biometric Authentication et AML (au-delà du teaser)
- KYB, SDK natif, branding tenant, 2FA, liveness passif
- Revue opérée par Recogniz-Me

---

## 9. Après M6

Ordre conseillé, **pas** dans le MVP :

1. Paiement live (juridique OK) + 1–2 clients Production.
2. Face Liveness AWS si stubbée au M5.
3. Reprise [roadmap IDV](../specs/roadmap-implementation-idv.md) à partir du dataset / détection proprio **sans** casser l’API ni les ports.
4. Specs produits Authentification puis AML.

---

## 10. Suivi

- Changement de périmètre → version du **CDC**, pas un commentaire de PR.
- Glissement d’un critère (ex. Liveness) → CDC §17 **puis** cette roadmap.
- Prochain sprint à ouvrir : **M3 — crédit d’organisation, recharge carte**.

**Journal des changements de périmètre**

| Date | Changement | Trace |
|---|---|---|
| 9 sept. 2026 | Crédit d’organisation, recharge **carte** MVP (plus de meter / facture d’usage). Facturation org seulement. | CDC v1.3 §10 |
| 9 sept. 2026 | Facturation **à l’usage** (plus de plan mensuel / forfait). Console par service (sidebar compte + onglets produit). | CDC v1.2 §9.6, §10 |
| 2 sept. 2026 | Vitrine bilingue FR/EN : l’anglais passe de « hors M1 sauf si gratuit » à **dans le périmètre** | CDC v1.1 §8, §8.0 |
