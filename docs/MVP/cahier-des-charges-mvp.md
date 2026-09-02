# Cahier des charges — MVP SaaS Recogniz-Me

**Produit :** Recogniz-Me  
**Livrable :** MVP métier — mise sur pied du service SaaS  
**Version :** 1.1 — vitrine bilingue FR/EN (§8), l’anglais passe d’optionnel à livré  
**Date :** 2 septembre 2026  
**Statut :** contrat d’implémentation du MVP  
**Public :** produit, design, ingénierie, go-to-market

**Documents liés**

- [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md) — *quand* (ordre de build)
- [`../cahier-des-charges.md`](../cahier-des-charges.md) — vision produit (moteurs IA propriétaires, hors MVP)
- [`../specs/specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md) — métier Identity & Document Verification
- [`../specs/guide-implementation-s1.md`](../specs/guide-implementation-s1.md) — fondation livrée (session, lien, consentement)
- [`../cout-unitaire-verification.md`](../cout-unitaire-verification.md) — COGS AWS

Ce document **prime** sur la vision et sur la roadmap IDV pour tout ce qui concerne le périmètre, l’ordre de build et les critères d’acceptation du MVP. La spec IDV reste la référence du *parcours de vérification* ; les écarts MVP (pas de modèle propriétaire, pas d’AML attaché, pas d’enrôlement) sont listés §9.5.

---

## 1. Objet

Construire le **premier service vendable** Recogniz-Me : une entreprise peut découvrir la plateforme, créer un compte, souscrire, et vérifier l’identité d’un utilisateur à distance via **Identity & Document Verification**.

Le MVP n’est pas un laboratoire IA. Il n’est pas non plus un agrégateur KYC. C’est une **offre SaaS opérable** :

1. une marque et une identité visuelle cohérentes ;
2. un site vitrine qui explique et convertit ;
3. un compte client (organisation + utilisateurs) ;
4. une souscription au service ;
5. le produit **Identity & Document Verification** de bout en bout, sans moteur IA propriétaire — **Amazon Textract / Rekognition** en production, **stubs déterministes** en sandbox et en local.

Les autres solutions de la gamme (**Biometric Authentication**, **AML Screening**) sont **présentées** sur le site. Elles ne sont ni souscriptibles, ni exposées en API, ni implémentées.

---

## 2. Pourquoi ce MVP

La vision ([`cahier-des-charges.md`](../cahier-des-charges.md)) vise un avantage documentaire propriétaire (SageMaker). Ce chantier est **reporté après le MVP**.

Raisons métier :

- Sans marque, vitrine, compte et facturation, il n’y a pas de client, donc pas de feedback ni de revenu.
- Un pipeline IDV **utilisable** (AWS ou stub) démontre la proposition de valeur plus tôt qu’un dataset + endpoints SageMaker.
- Les COGS V1 Textract + Rekognition restent largement sous le prix de marché (~0,04–0,08 $ d’IA par dossier vs 0,80–4 $ revendus). Le modèle propriétaire devient un levier de marge **après** le volume.

**Ce que le client achète au MVP :** un résultat d’identité justifié (approuvée / refusée / revue), pas un modèle ML.

---

## 3. Objectifs

| ID | Objectif | Mesure de succès |
|---|---|---|
| **O1** | Identité visuelle unique sur toutes les surfaces | Charte + tokens appliqués au site, à la console, au flow, aux e-mails |
| **O2** | Un visiteur comprend l’offre et peut s’inscrire | Parcours vitrine → inscription sans friction |
| **O3** | Un client possède un compte et une organisation | Signup, e-mail vérifié, login, une org, rôle propriétaire |
| **O4** | Le client souscrit et obtient l’accès production | Plan sandbox gratuit + plan production payant, quota, facture |
| **O5** | Le client fait vérifier une identité | Création → lien → consentement → pièce + selfie → décision relisible |
| **O6** | La gamme est lisible sans sur-promettre | IDV = produit live ; biométrie auth et AML = « bientôt » |

Hors objectifs MVP : FAR/FRR propriétaires, MLOps, dataset annoté, liveness passif, KYB.

---

## 4. Périmètre

### 4.1 Inclus

| Lot | Contenu |
|---|---|
| **Identité visuelle** | Nom, baseline, logo, palette, typo, tokens, ton, application aux 4 surfaces |
| **Site vitrine** | Accueil, produit IDV, teasers biométrie / AML, tarifs, confiance, légal, CTA compte |
| **Compte client** | Inscription, vérification e-mail, connexion, session, organisation, profil |
| **Souscription** | Plans, Checkout, portail facturation, quota, clés sandbox / live, usage |
| **IDV** | Session, lien, consentement, capture, analyse AWS ou stub, décision, console, webhooks de résultat |
| **Catalogue** | Pages marketing Biometric Authentication et AML Screening, sans backend |

### 4.2 Explicitement hors MVP

- Moteurs documentaires propriétaires (détection / classification / authenticité SageMaker)
- Dataset, Ground Truth, registry, A/B modèles
- Produit **Biometric Authentication** (recomparaison d’un visage déjà enrôlé)
- Produit **AML Screening** (listes, PEP, sanctions, adverse media)
- KYB / UBO, estimation d’âge, bases gouvernementales
- SDK natif iOS / Android
- Branding du parcours par organisation (logo client dans le flow)
- Liveness passif, deepfake / injection, device intelligence
- Multi-devises, marketplace, SSO entreprise, SCIM
- Équipe multi-rôles avancée (au-delà de propriétaire + membre invité)
- Revue manuelle opérée **par Recogniz-Me** (la revue MVP, si activée, est celle du **client**)

### 4.3 Déjà livré (à réutiliser, pas à reconstruire)

Fondation S1 : organisations et clés API (seed), `POST/GET /v1/verifications`, isolation tenant, lien hébergé, consentement versionné, audit, console minimale sans authentification utilisateur, flow jusqu’au consentement.

Le MVP **branche** comptes et souscription sur cette fondation, et **termine** le pipeline IDV.

---

## 5. Proposition de valeur (discours commercial)

**Pour** une entreprise qui doit connaître ses utilisateurs à distance  
**Recogniz-Me** est une plateforme SaaS de vérification d’identité  
**qui** envoie un lien sécurisé, capture la pièce et le visage, et rend une décision justifiée  
**contrairement à** un développement interne ou à un simple OCR  
**parce que** le client n’opère ni la capture, ni l’analyse, ni la conservation des médias.

Baseline (existante, à conserver) : *Une identité reconnue comme réelle.*

Ton : B2B, précis, sobre. On vend un **résultat de confiance**, pas un laboratoire IA. Ne pas afficher « IA propriétaire » sur le MVP. Ne pas présenter biométrie et AML comme disponibles.

---

## 6. Offre produit du MVP

```
                    SITE VITRINE
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
   IDV (live)     Biometric Auth     AML Screening
                   (mention)          (mention)
        │
        ▼
   COMPTE + SOUSCRIPTION
        │
        ▼
   CONSOLE + API + HOSTED FLOW
```

| Produit | Statut MVP | Ce que voit le visiteur | Ce que peut faire le client |
|---|---|---|---|
| Identity & Document Verification | **Disponible** | Pages produit + tarifs + docs | Créer des vérifs, lien, décision |
| Biometric Authentication | Annoncé | Page teaser, feuille de route | Rien |
| AML Screening | Annoncé | Page teaser, feuille de route | Rien |

Interdit : bouton « Essayer » ou « Souscrire » sur biométrie / AML. CTA unique de ces pages : liste d’attente ou « Être prévenu ».

---

## 7. Identité visuelle

### 7.1 Objectif

Une **seule marque** sur le site, la console, le hosted flow et les e-mails transactionnels. Avant M0 la console était navy / bleu et le flow beige / noir : c’était un défaut, pas deux marques. **Corrigé en M0** — voir [`charte-visuelle.md`](./charte-visuelle.md).

Deux **atmosphères**, un seul système :

| Surface | Atmosphère | Pourquoi |
|---|---|---|
| Vitrine + console + e-mails | Clair, chrome d’application | Confiance B2B, dashboard |
| Hosted flow (applicant) | Clair, calme, peu de chrome | Humain devant une caméra ; pas un back-office |

L’applicant ne doit pas se sentir dans un outil d’analyste. Le client SaaS doit se sentir dans un produit de confiance.

### 7.2 Livrables design

| Livrable | Contenu |
|---|---|
| Nom et baseline | Recogniz-Me — *Une identité reconnue comme réelle.* |
| Logo | Symbole + wordmark, versions claire / sombre, favicon, icône 512 |
| Palette | Couleurs sémantiques (fond, texte, action, succès, attention, danger, bordure) |
| Typographie | Une sans UI (titres + corps) ; pas de troisième fonte |
| Grille et rayons | Espacements, radius, ombres, états focus |
| Composants | Bouton, champ, carte, badge de statut, alerte, navigation |
| Tokens | Variables CSS (ou équivalent) partagées, pas de hex en dur dispersés |
| Ton éditorial | FR langue de référence ; EN sur le flow et sur la vitrine selon le navigateur. Console FR seule au MVP |
| Kit e-mail | En-tête, pied, CTA, même logo |

Critère : un designer ou un développeur peut produire une nouvelle page **sans inventer une couleur**.

### 7.3 Direction figée (M0)

Thème **clair**, accent **vert**. Palette complète et contrastes : [`charte-visuelle.md`](./charte-visuelle.md) ; source technique : `packages/brand/tokens.css`.

| Rôle | Valeur |
|---|---|
| Fond de page | `#F4F7F5` |
| Surface (cartes, nav, flow) | `#FFFFFF` |
| Texte | `#111A15` |
| Texte secondaire | `#5A6B62` |
| Action (et état approuvé) | `#0F7A4D` |
| Succès / attente / refus | tokens sémantiques distincts, en paires fond + texte |

Le vert n’habille que ce qui **agit** et l’état **approuvé** : jamais un fond de page ni un titre.

Le **symbole** évoque reconnaissance / visage / document **sans** biométrie caricaturale (pas d’empreinte générique, pas d’œil « surveillance »).

### 7.4 Accessibilité marque

- Contraste WCAG 2.2 AA sur textes et boutons
- Focus visible
- Logo lisible à 16 px de hauteur (favicon excepté)
- Pas d’information portée par la seule couleur (badges de statut = couleur + libellé)

---

## 8. Site vitrine

Application Next.js dédiée (`web/site`), publique, SEO, **bilingue FR / EN**.

L’anglais était optionnel en version 1.0 de ce document. Il est **dans le périmètre** depuis la version 1.1 : les deux langues sont servies, et le français reste la langue de référence — c’est en français que la copie s’écrit, et l’anglais la suit.

### 8.0 Langues

| Règle | Détail |
|---|---|
| **RG-SITE-01** | Toute page existe dans les deux langues. Un contenu publié dans une seule langue n’est pas publiable. |
| **RG-SITE-02** | Les textes vivent dans `web/site/messages/{fr,en}.json`. Aucune chaîne visible n’est écrite dans un composant. |
| **RG-SITE-03** | Le français porte le type : une clé absente de `en.json` **casse la compilation**, elle n’attend pas la production. |
| **RG-SITE-04** | Les segments d’URL sont communs aux deux langues ; seul le préfixe change (`/fr/pricing`, `/en/pricing`). Les routes du §8.1 restent donc littérales. |
| **RG-SITE-05** | Une URL sans préfixe est redirigée vers la langue négociée depuis `Accept-Language`, français par défaut. |
| **RG-SITE-06** | Chaque page déclare ses `hreflang` réciproques et sa canonique. |

### 8.1 Pages obligatoires

| Route | Rôle |
|---|---|
| `/` | Promesse, pour qui, comment ça marche (3 étapes), produit phare IDV, CTA inscription, mentions des 2 autres produits |
| `/products/identity-verification` | IDV : problème, parcours, décision, corridor documents, sandbox |
| `/products/biometric-authentication` | Teaser : ré-auth sans redemander la pièce ; **indisponible** |
| `/products/aml-screening` | Teaser : PEP / sanctions / adverse media ; **indisponible** |
| `/pricing` | Sandbox gratuit + plan production ; overage ; pas de tarif biométrie/AML |
| `/security` | Isolation tenant, chiffrement, rétention, pas de PII dans les journaux (discours, pas d’audit SOC 2 inventé) |
| `/docs` | Démarrage : compte → clé sandbox → première vérif ; lien OpenAPI |
| `/legal/terms` | CGU (brouillon juridique à faire relire) |
| `/legal/privacy` | Politique de confidentialité |
| `/legal/dpa` | Trame DPA (ou lien « sur demande ») |
| `/contact` | Formulaire ou e-mail ; pas un CRM obligatoire |

### 8.2 Parcours conversion

```
Visiteur → comprend IDV → tarifs → Créer un compte
                              ↘ docs (intégrateur)
```

CTA primaire partout : **Créer un compte**.  
CTA secondaire : **Documentation**.

Pas de « Book a demo » bloquant l’inscription. Un contact commercial peut exister, il n’est pas le chemin principal.

### 8.3 Contenu — règles de vérité

- Ne pas afficher de certifications (ISO, SOC 2, iBeta) non obtenues.
- Corridor documents : liste **courte et vraie** (pays × types du MVP), pas « 200+ documents ».
- Temps de décision : « quelques minutes » (aligné spec IDV), pas « instantané » si la file est asynchrone.
- Biométrie / AML : mentionnées comme **feuille de route**, jamais comme modules activables.

### 8.4 SEO et technique vitrine

- Métadonnées, Open Graph, favicon de la charte
- Pages statiques ou SSG ; pas d’appel API métier côté visiteur
- Formulaire contact : anti-abus (honeypot ou équivalent)
- Pied de page : produits, tarifs, sécurité, légal, langue
- `hreflang` réciproques + `x-default`, sitemap couvrant les deux langues
- Indexation **refusée par défaut** ; à ouvrir explicitement sur le domaine de production, pour qu’un aperçu ne soit jamais indexé

---

## 9. Compte client

Aujourd’hui : une organisation et des clés **seedées**, console **sans login**. Le MVP introduit l’identité de l’humain qui opère.

### 9.1 Concepts

| Concept | Définition |
|---|---|
| **Utilisateur** | Personne (e-mail) qui se connecte à la console |
| **Organisation** | Tenant facturé, isolé, déjà existant en base |
| **Appartenance** | Lien user ↔ org + rôle |
| **Clé API** | Identité machine ; distincte du login console |

Un utilisateur MVP appartient à **une** organisation. Créer un compte **crée** l’organisation (nom saisi à l’inscription).

### 9.2 Rôles MVP

| Rôle | Droits |
|---|---|
| **Propriétaire** | Tout : facturation, clés, membres, vérifications, revue |
| **Membre** | Vérifications et lecture usage ; pas de facturation ni révocation des clés |

Invitation par e-mail (lien à usage unique). Pas de SSO.

### 9.3 Authentification console

- E-mail + mot de passe (hash adapté, jamais en clair)
- Vérification d’e-mail **avant** la première clé live (sandbox autorisé dès e-mail vérifié)
- Session cookie httpOnly, Secure hors local
- Déconnexion, mot de passe oublié
- 2FA : **hors MVP** (prévu vision) ; le propriétaire est prévenu dans la console que ce sera exigé plus tard

API `/v1/**` : **toujours** la clé Bearer (`ky_test_` / `ky_live_`). Le cookie console n’authentifie pas l’API publique.

### 9.4 Parcours inscription

```
E-mail + mot de passe + nom d’organisation
        ↓
Compte créé, e-mail de vérification
        ↓
E-mail vérifié → organisation active, plan Sandbox
        ↓
Clé ky_test_ émise (affichée une fois)
        ↓
Console : première vérification sandbox
```

Règles :

- E-mail unique.
- Slug d’organisation dérivé du nom, unique.
- Pas d’accès console tant que l’e-mail n’est pas vérifié (page « vérifiez votre boîte »).
- Audit : `user.registered`, `user.email_verified`, `membership.invited`.

### 9.5 Écrans console (compte)

| Écran | Contenu |
|---|---|
| Login / signup / forgot | Charte |
| Vérification e-mail | Attente + renvoyer |
| Accueil | Usage du mois, quota, CTA nouvelle vérif |
| Vérifications | Liste, création, fiche (déjà ébauché) |
| Clés API | Créer, révoquer, préfixe visible, secret **une fois** |
| Équipe | Propriétaire + invitations |
| Facturation | Plan, quota, portail Stripe, factures |
| Compte | E-mail, mot de passe |

La console actuelle (création de vérif, copie du lien) est **derrière login**. Plus d’accès anonyme.

---

## 10. Souscription

Le client n’achète pas « l’IA ». Il achète un **droit d’usage** d’Identity & Document Verification.

### 10.1 Plans

| Plan | Prix (indicatif, à figer avant Stripe) | Quotas | Environnement |
|---|---|---|---|
| **Sandbox** | 0 | Quota bas (ex. 50 vérifs / mois), reset mensuel | `ky_test_` uniquement ; analyse **stub** |
| **Production** | Abonnement mensuel + forfait de vérifs inclus + overage unitaire | Forfait (ex. 200 / mois) puis prix / vérif | `ky_live_` ; analyse **AWS** |

Chiffres de catalogue (exemple de travail, **non contractuels** tant que le pricing n’est pas figé) :

- Abonnement Production : à définir (ordre de grandeur : dizaines d’€ / mois, pas un enterprise 4 zéros)
- Vérif incluse puis overage : au-dessus du COGS (~0,10–0,20 $ chargé à petit volume) et sous le marché (0,80–4 $)
- Devise d’affichage et de Stripe : **à figer** (EUR ou CAD) avant d’ouvrir Checkout
- Biométrie et AML : **pas de ligne tarifaire**

Un seul produit facturé : **IDV**. Annulation → clés live révoquées ou bloquées ; sandbox conserve l’accès lecture + test selon politique (défaut : sandbox reste).

### 10.2 Cycle de vie abonnement

```
Sandbox (défaut)
    ↓  Checkout Stripe (carte)
Production active
    ↓  quota inclus consommé
Overage facturé (Stripe metered ou facture mensuelle)
    ↓  échec de paiement
Période de grâce courte → live bloqué (429 / 402 métier) ; sandbox OK
    ↓  résiliation
Fin de période : plus de ky_live_ ; données selon rétention
```

### 10.3 Règles

| ID | Règle |
|---|---|
| **RG-SUB-01** | Toute org naît en Sandbox. |
| **RG-SUB-02** | `ky_live_` seulement si l’abonnement Production est `active` (ou `trialing` si essai activé). |
| **RG-SUB-03** | Une vérif **live** consomme le quota. Sandbox ne consomme pas le forfait payant. |
| **RG-SUB-04** | Quota dépassé en Production → overage, pas un silence. Si overage non configuré : refus explicite `quota_exceeded`. |
| **RG-SUB-05** | Le client gère moyen de paiement, factures et résiliation via le **portail Stripe** (pas de CB stockée chez nous). |
| **RG-SUB-06** | Webhooks Stripe signés ; source de vérité abonnement = Stripe, miroir en base (statut, `current_period_end`). |
| **RG-SUB-07** | Pas de souscription séparée par produit : un plan = accès IDV. |

Essai gratuit Production : optionnel. Si absent au MVP, le sandbox suffit à tester.

### 10.4 Usage

- Compteur mensuel : `sandbox_verifications`, `live_verifications`
- Console : consommé / inclus / overage
- API : `GET /v1/usage` (prévu vision, **à livrer** au MVP)

Une vérification **créée** compte dès la création (évite le spam de liens). Alternative acceptable : compter à la **décision**. **À figer** avant implémentation (recommandation : à la création live, plus simple et anti-abus).

---

## 11. Identity & Document Verification (MVP)

Le métier (questions, statuts, consentement, recapture, isolation, raisons de décision) est celui de [`specification-fonctionnelle-idv.md`](../specs/specification-fonctionnelle-idv.md), **avec les écarts ci-dessous**.

### 11.1 Happy path MVP

```
Client connecté (ou API ky_test_/ky_live_)
        ↓
Crée une vérification → lien
        ↓
Applicant : consentement → pièce (qualité) → selfie / présence
        ↓
Traitement asynchrone
        ↓
Décision APPROVED | REJECTED | REVIEW (+ raisons)
        ↓
Console + GET /v1/verifications/{id} + webhook verification.completed
```

### 11.2 Analyse documentaire — pas de moteur propriétaire

| Étape spec IDV | MVP |
|---|---|
| Qualité client + serveur | **Inclus** (filet avant tout appel payant) |
| Détection coins / perspective | Stub géométrie **ou** Rekognition DetectLabels / faces en appoint ; **pas** de modèle SageMaker ; **pas** d’heuristique Canny vendue comme moteur |
| Classification pays × type | Corridor fixe : si Textract AnalyzeID renvoie un type hors liste → `unsupported_document` ; sinon mapping du type AnalyzeID |
| Lecture (OCR) | **Amazon Textract AnalyzeID** (live) ; stub champs (sandbox) |
| MRZ | Parseur déterministe si zone présente ; sinon signal `mrz_unavailable` |
| Authenticité / fraude ML | **Hors MVP**. Signaux déterministes seulement : expiration, checksum MRZ, mismatch OCR/MRZ, type fichier. Score d’authenticité stubbable (`PASS` sandbox) |
| Liveness | **Rekognition Face Liveness** (live) ; stub `liveness_pass` (sandbox) |
| Face match | **Rekognition CompareFaces** (live) ; stub score (sandbox) |
| Risque + décision | **Règles Spring** versionnées, comme la spec. Pas de LLM juge |
| Revue | File console, analyste **du client** approuve ou refuse |
| Enrôlement biométrique | **Hors MVP** |
| AML attaché | **Hors MVP** |

Sandbox / local : **zéro appel AWS IA** (stubs). Live : Textract + Rekognition. Les ports `DocumentAiPort` / `BiometricAiPort` existent déjà : deux adaptateurs (`Aws*` / `Stub*`), choix par profil Spring (`sandbox` vs `live`) et par préfixe de clé.

### 11.3 Corridor documents MVP

Liste courte, affichée sur le site et refus hors liste :

- Passeport (ICAO) — au moins CA, FR, US **ou** le sous-ensemble réellement testé
- Carte d’identité nationale — uniquement les pays **effectivement** parsés

La liste publiée = la liste du classifieur / mapping AnalyzeID. Pas de « et le reste du monde ».

### 11.4 Décision (politique par défaut)

| Décision | Condition (calibrage provisoire) |
|---|---|
| Approuvée | Pièce supportée, non expirée, lectures cohérentes, liveness OK, face match ≥ seuil |
| Refusée | Consentement refusé, pièce non supportée, expiration hors politique, mismatch franc, plafond d’essais |
| Revue | Zone grise (match limite, MRZ partielle, liveness limite) |
| Recapture | Qualité, verso manquant, visage inutilisable |

Raisons persistées, codes anglais, **sans PII**.

### 11.5 Écarts figés vs spec IDV / vision

| Sujet | Vision / spec | MVP |
|---|---|---|
| Vision documentaire | Modèles proprio dès le 1er pipeline | AWS + règles + stubs |
| Dataset bloquant | Oui avant détection | Non |
| Biometric Authentication | Produit suivant | Page teaser |
| AML | Attachable à l’IDV | Teaser site |
| Webhooks | Catalogue large | Minimum : `verification.completed` (+ `verification.approved` / `rejected` / `review` si peu coûteux) |
| Dashboard analytics | Taux fraude, etc. | Usage + liste + fiche |

Dès qu’un adaptateur AWS est derrière le port, **remplacer par un endpoint SageMaker plus tard ne change pas l’API client**.

### 11.6 API MVP (organisation)

| Méthode | Chemin | Auth |
|---|---|---|
| `POST` | `/v1/verifications` | Clé |
| `GET` | `/v1/verifications` | Clé |
| `GET` | `/v1/verifications/{id}` | Clé |
| `POST` | `/v1/verifications/{id}/cancel` | Clé |
| `GET` | `/v1/usage` | Clé |
| `POST` | `/v1/webhooks` | Clé (endpoint résultat) |

Upload document / selfie : URLs signées + routes flow token, pas la clé applicant. Détail capture : aligné guide S2.

Flow applicant (token) : hydratation, consentement, upload, statut — **inchangé dans l’esprit S1**, étendu à la capture.

---

## 12. Acteurs

| Acteur | MVP |
|---|---|
| **Visiteur** | Site vitrine |
| **Utilisateur console** | Compte, souscription, vérifs, clés, revue |
| **Backend client** | API clé |
| **Applicant** | Lien, jamais de compte Recogniz-Me |
| **Système** | Quota, Stripe, pipeline, expiration |
| **Recogniz-Me (nous)** | Opère plateforme et facturation ; ne fait **pas** la revue KYC du client au MVP |

---

## 13. Cas d’utilisation

**UC-MKT-01 — Découvrir l’offre**  
Le visiteur parcourt accueil, IDV, tarifs, teasers. Il distingue disponible / bientôt.

**UC-ACC-01 — Créer un compte**  
Inscription → e-mail → org Sandbox → clé test. Alternative : e-mail déjà pris ; mot de passe trop faible.

**UC-ACC-02 — Se connecter et opérer**  
Login → console. Session expirée → login. Invitation membre → acceptation → rôle membre.

**UC-SUB-01 — Passer en Production**  
Checkout Stripe → `ky_live_` créable → vérifs live débitent le quota.

**UC-SUB-02 — Gérer la facturation**  
Portail : carte, factures, résiliation. Échec paiement → live bloqué, message console clair.

**UC-IDV-01 à 10**  
Ceux de la spec IDV, dans les limites §11 (pas d’AML, pas d’enrôlement, analyse AWS/stub).

**UC-ISO-01 — Isolation**  
Org B sur une ressource de A → identique à un id inexistant (404). Facturation et usage non fuités.

---

## 14. Architecture applicative MVP

```
Visiteur                         Client SaaS                    Applicant
   │                                  │                              │
   ▼                                  ▼                              ▼
web/site (vitrine)            web/console                      web/flow
   │                          cookie session                       token URL
   │                                  │                              │
   └──────────────────────────────────┼──────────────────────────────┘
                                      ▼
                         Spring Boot (API + workers)
                                      │
              ┌───────────────────────┼───────────────────────┐
              ▼                       ▼                       ▼
        PostgreSQL                  Redis                      S3
        (orgs, users,               (sessions,                 (médias)
         subs, vérifs)               tokens flow)
              │
              ▼
        Stripe (abonnement)
              │
              ▼
        Stub IA  ← sandbox / local
        Textract + Rekognition ← clés live (profil prod)
```

Pas de SageMaker, Ground Truth, ni Bedrock au MVP.

Stack inchangée : Java / Spring Boot, PostgreSQL, Next.js, Redis, S3. Ajouts MVP : module auth utilisateurs, intégration Stripe, app `web/site`, adaptateurs AWS IA réels + stubs.

### 14.1 Entités nouvelles (indicatif)

`User`, `Membership`, `EmailVerificationToken`, `PasswordResetToken`, `Subscription` (miroir Stripe), `UsagePeriod`, `WebhookEndpoint` — en plus des entités S1.

---

## 15. Exigences non fonctionnelles

| ID | Exigence |
|---|---|
| **NF-MVP-01** | Isolation tenant : fuite = arrêt livrable (inchangé) |
| **NF-MVP-02** | HTTPS hors local ; cookies Secure ; secrets hors git |
| **NF-MVP-03** | Journaux : identifiants techniques, **jamais** mot de passe, clé brute, CB, PII applicant |
| **NF-MVP-04** | Médias : bucket privé, URL signée courte ; pas d’URL permanente dans un webhook |
| **NF-MVP-05** | Sandbox : aucun appel Textract / Rekognition |
| **NF-MVP-06** | Happy path live : décision en **minutes** |
| **NF-MVP-07** | Pages vitrine : LCP raisonnable, mobile-first |
| **NF-MVP-08** | Flow + vitrine + console : AA contrastes, focus clavier |
| **NF-MVP-09** | Stripe : vérification de signature ; idempotence des webhooks |
| **NF-MVP-10** | Rate limit login et `POST /v1/verifications` |

---

## 16. Sécurité, données, légal (minimum go-live)

- Base légale applicant : consentement versionné **avant** toute photo (déjà S1)
- Rétention : durée par défaut documentée sur `/security` et `/legal/privacy` (ex. 30 jours MVP, configurable plus tard)
- Suppression : le propriétaire peut demander la clôture du compte (processus manuel acceptable au MVP s’il est décrit)
- CGU / privacy / DPA : **brouillons dans le repo**, relecture juridique **avant** paiement réel
- Interdit d’inventer un niveau de conformité (SOC 2, Hébergeur de données de santé, etc.)

Marchés cibles du discours : entreprises UE / Canada (PIPEDA / RGPD). Pas de ciblage US health / enfants.

---

## 17. Critères d’acceptation du MVP

Le MVP est **démontrable** quand **toutes** les conditions suivantes sont vraies.

### Marque et vitrine

1. Charte et tokens existent ; site, console, flow et e-mail de vérif utilisent le même logo et les mêmes actions.
2. Un inconnu comprend en moins de deux minutes : ce qu’est IDV, que biométrie et AML **ne sont pas** à vendre, comment créer un compte.
3. `/pricing` reflète les plans réels (sandbox + production).

### Compte et souscription

4. Inscription → e-mail → login → org Sandbox → clé `ky_test_` affichée une fois.
5. Checkout test Stripe → statut Production → création d’une clé `ky_live_`.
6. Résiliation ou échec de paiement → plus de vérif live ; message console explicite.
7. `GET /v1/usage` et l’écran facturation montrent le même compteur.

### IDV

8. Sandbox : pièce + selfie → décision **sans** AWS IA, raisons persistées.
9. Live (staging) : même parcours avec Textract + Rekognition (ou liveness stub **documenté** si Face Liveness n’est pas encore branché — dans ce cas le critère live liveness est reporté, le face match AWS reste exigé).
10. Org B ne lit pas le dossier de A (404 identique).
11. Webhook de décision signé, médias absents du payload.
12. Hors corridor → refus `unsupported_document`.

### Vérité commerciale

13. Aucune page ne présente biométrie auth ou AML comme un service activable.
14. Aucune mention de modèle propriétaire, de certification non obtenue, ou d’un corridor documents plus large que le réel.

**Kill (arrêt go-live) :** fuite tenant ; PII ou secret dans les logs ; paiement live sans CGU/privacy ; clés live sans abonnement actif ; vitrine qui vend AML/biométrie.

---

## 18. Phasage de construction

Lots **séquentiels**. Un lot n’est pas « vert » sans son critère démontrable. Durée indicative : 1–2 semaines chacun.

| Lot | Livrable démontrable | S’appuie sur |
|---|---|---|
| **M0** | Charte : logo, tokens, application console + flow existants | Design |
| **M1** | Site vitrine (pages §8.1) publié en local / preview | M0 |
| **M2** | Compte : signup, e-mail, login, org, console derrière session | S1, M0 |
| **M3** | Stripe : Sandbox → Production, clés live, usage | M2 |
| **M4** | Capture (S2) + pipeline IDV stub (qualité, « lecture », décision) | S1, M2 |
| **M5** | Adaptateurs AWS live + webhooks résultat | M4, M3 |
| **M6** | Durcissement : rétention affichée, rate limit, staging, 1 design partner sandbox | M1–M5 |

Ordre imposé : **M0 avant M1** (pas de vitrine au look actuel divergent). **M2 avant M3**. **M4 avant M5** (le stub prouve le métier sans facture AWS). M1 peut avancer en parallèle de M2 dès que les tokens M0 existent.

L’ordre détaillé (dépendances, In/Out, démos, freeze) est dans [`roadmap-implementation-mvp.md`](./roadmap-implementation-mvp.md). La roadmap IDV S3–S9 (SageMaker, dataset) **n’est pas** le calendrier de ce MVP. Elle reprend **après** M6, comme chantier « avantage IA », pas comme condition de première vente sandbox.

---

## 19. Décisions figées

| Sujet | Décision |
|---|---|
| Nom du livrable | MVP SaaS (marque, vitrine, compte, souscription, IDV) |
| Produit live | Identity & Document Verification uniquement |
| Biométrie auth / AML | Marketing seulement |
| IA documentaire MVP | Textract live + stub sandbox ; pas de SageMaker |
| Biométrie d’onboarding MVP | Rekognition live + stub sandbox |
| Juge | Règles + scores, pas un LLM |
| Auth console | E-mail / mot de passe ; 2FA plus tard |
| Auth API | Clés `ky_test_` / `ky_live_` |
| Facturation | Stripe ; un plan Production ; sandbox gratuit |
| Revue | Analyste du **client**, pas un service opéré par Recogniz-Me |
| Compte | Une org par utilisateur fondateur ; invitations membres |
| Régénération auto du lien KYC | Non (spec IDV) |
| Discours IA propriétaire | Interdit sur le MVP |

**À figer avant M3 (ne bloque pas M0–M2) :** devises, prix exacts, forfait inclus, overage, comptage à la création vs à la décision, liste corridor pays × types.

---

## 20. Glossaire MVP

| Terme | Sens |
|---|---|
| **Vitrine** | Site marketing public |
| **Console** | Application client connecté |
| **Sandbox** | Plan gratuit, stubs, clés test |
| **Production / live** | Plan payant, AWS IA, clés live |
| **Stub** | Adaptateur d’analyse déterministe, sans AWS |
| **Teaser** | Page produit sans souscription ni API |
| **Design partner** | Premier client réel en staging / sandbox, pas un lancement grand public |

Les termes IDV (session, applicant, lien hébergé, signal, revue) : spec IDV §4.

---

## 21. Suivi

Toute évolution de périmètre MVP se décide ici (version + date), pas dans un commentaire de sprint. Si un lot glisse (ex. Face Liveness reporté), le critère d’acceptation §17.9 est mis à jour **avant** de déclarer M5 terminé.

Prochaine étape d’implémentation : **sprint M0** (charte et tokens), puis M1 / M2 en parallèle — voir la [roadmap](./roadmap-implementation-mvp.md).
