# Spécification — Module Règles IDV

**Plateforme :** Recogniz-Me  
**Produit :** Identity & Document Verification (IDV)  
**Écran :** console, service Identité, onglet Règles (`/identity/rules`)  
**Version du document :** 1.0  
**Date :** 16 octobre 2026  
**Statut :** spécification cible du module — l’onglet est encore un placeholder

**Documents liés :**
- [`specification-fonctionnelle-idv.md`](./specification-fonctionnelle-idv.md) — métier IDV, options d’organisation (§18), règles de gestion (§17)
- [`../DocumentIA/cahier-des-charges-document-ia.md`](../DocumentIA/cahier-des-charges-document-ia.md) — ordre de décision documentaire (§16) et registre de schémas
- [`../DocumentIA/specification-detaillee-document-ia.md`](../DocumentIA/specification-detaillee-document-ia.md) — comportement livré du pipeline `vision-1`

Ce document décrit **uniquement** l’onglet Règles du service Identité : ce que l’organisation consulte, ce qu’elle peut publier, et comment une version publiée s’applique aux sessions. Il ne redéfinit ni le pipeline Document IA, ni le moteur biométrique.

---

## 1. Objet

L’onglet Règles est la **politique IDV de l’organisation**. Il répond à une question : *dans quelles conditions ce tenant approuve, refuse ou envoie en revue une vérification d’identité.*

Aujourd’hui la page affiche trois cartes sans donnée :

| Carte | Promesse déjà écrite dans l’interface |
|---|---|
| Profil de décision | Approuvé, refusé, revue. Les profils personnalisés ne sont pas encore là. |
| Documents acceptés | Passeport, carte, permis du corridor publié. La liste sera éditable ici. |
| Parcours hébergé | Texte de consentement et expiration du lien. Pas de branding du parcours. |

Le moteur qui tranche existe déjà, avec des constantes dans `IdvDecisionEngine` et `HostedFlowService`. L’onglet ne remplace pas ce moteur. Il **publie les choix laissés à l’organisation**, dans des bornes que la plateforme refuse de franchir.

Le texte d’introduction de l’onglet reste : les règles de ce service s’appliquent à **toutes ses intégrations**.

---

## 2. État actuel

| Sujet | Comportement livré |
|---|---|
| Page | `web/console/app/(app)/[product]/rules/page.tsx` — trois articles statiques pour Identité |
| Décision document | `decideVision`, version `vision-1`. Ordre fixe : image illisible, mauvais côté, expiration, document inconnu, champ requis manquant, MRZ, OCR faible, indicateur suspect, bande haute, sinon revue |
| Décision visage | `applyFace`, version `bio-1`. Liveness ≥ 0,80 ; similarité &lt; 0,75 refus ; &lt; 0,90 revue ; sinon approuvé |
| OCR faible | Confiance d’un champ requis &lt; 0,70 → revue |
| Essais | 3 photos par face de pièce, 3 essais de liveness facturés. Au plafond photo : refus `capture_attempts_exceeded` |
| Lien | Durée de vie 3600 secondes (`kyc.hosted-url-ttl-seconds`), identique pour toutes les organisations |
| Consentement | Version plateforme `consent-v1`. Le texte affiché est celui du parcours (`web/flow`) |
| Catalogue | Définitions actives : `PASSPORT_TD3`, `CANADA_PERMANENT_RESIDENT`, `QUEBEC_DRIVER_LICENSE`. Aucun filtre par organisation |
| Persistance | `verifications.rules_version` enregistre la version du **moteur** (`vision-1`, puis `bio-1` à la fin du visage). Aucune version de politique d’organisation |

Tant que l’organisation n’a rien publié, ce comportement reste la politique effective.

---

## 3. Périmètre

### 3.1 Inclus

- Lecture de la politique effective du service Identité
- Publication d’une nouvelle version par un administrateur
- Trois blocs : profil de décision, documents acceptés, parcours hébergé
- Bornes plateforme : une organisation peut **resserrer**, pas assouplir les seuils de sécurité
- Figé de la version sur chaque session créée ensuite
- Journal d’audit de la publication, sans donnée personnelle
- Lecture seule pour les rôles qui ne publient pas

### 3.2 Hors de ce module

| Sujet | Où il vit |
|---|---|
| Champs, expressions L3, activation d’un schéma documentaire | Registre Document IA. L’onglet n’édite pas un schéma |
| Webhooks, clés, mode test ou live | Fiche d’intégration |
| Branding, logo, couleur, nom du tenant sur le parcours | Hors MVP (spécification fonctionnelle §3.2) |
| Produit Biométrie (seuil d’un visage déjà enrôlé) | Onglet Règles de `/biometrics`, plus tard |
| Listes AML, politique « ignorer / revue / bloquer » | Après le produit AML |
| Enrôlement du selfie à l’approbation | Après la décision IDV |
| Réécriture d’une session déjà créée | Interdit |

Les onglets Règles de Biométrie et d’AML restent des annonces. Cette spécification ne les couvre pas.

---

## 4. Principes

1. **Java tranche.** L’onglet ne contient pas d’éditeur de formules, ni de prompt, ni de modèle qui déciderait à la place du moteur.
2. **Deux numéros distincts.** `rules_version` reste l’identifiant du moteur (`vision-1`, `bio-1`). `policy_version` est la version publiée par l’organisation. Les deux sont persistés sur la session.
3. **Une politique par organisation**, pour le produit Identité, partagée par toutes les intégrations. Sandbox et live lisent la même version tant qu’une séparation n’est pas spécifiée.
4. **Publication immuable.** Corriger une politique crée une version suivante. L’ancienne reste lisible.
5. **La session fige la version à sa création.** Un lien déjà émis ne change pas de seuils pendant que l’applicant est dans le parcours.
6. **Plancher de sécurité.** Sous le plancher, l’API refuse la publication. Le plancher est celui du code au 16 octobre 2026.
7. **Le corridor se choisit dans le catalogue actif.** On ne peut pas accepter un document que la plateforme n’a pas publié.

---

## 5. Acteurs et droits

| Rôle | Lecture | Publication |
|---|---|---|
| Lecteur, Membre, Développeur | Oui | Non |
| Administrateur, Propriétaire | Oui | Oui |

La lecture s’appuie sur l’ouverture de la console (session organisation). La publication exige une permission nouvelle, `POLICY_WRITE`, accordée à Administrateur et Propriétaire.

Un rôle sans `POLICY_WRITE` voit les mêmes valeurs, sans champ modifiable et sans bouton Publier. Le message indique que la publication est réservée à un administrateur.

---

## 6. Écran

Route : `/identity/rules`. L’ancien chemin `/identity/configuration` continue de rediriger ici.

En-tête inchangé : sourcil « Identity & Document Verification », titre « Règles », chapô « Règles de ce service. Elles s’appliquent à toutes ses intégrations. »

Sous le chapô, une ligne d’état :

- **Politique plateforme** tant qu’aucune version d’organisation n’existe. Mention des valeurs en vigueur (seuils, documents, durée du lien).
- **Version n · publiée le … par …** dès qu’une version d’organisation est active.

Le bouton **Publier** n’apparaît qu’avec `POLICY_WRITE`, et seulement si le formulaire diffère de la version active. La publication demande une confirmation : elle s’applique aux **prochaines** sessions, pas aux liens déjà émis.

Trois sections, dans cet ordre, remplacent les trois cartes vides.

---

## 7. Profil de décision

Le profil montre la chaîne, dans l’ordre réel du moteur. L’opérateur voit ce qui est figé et ce qu’il peut régler.

### 7.1 Chaîne figée

L’ordre ne se réordonne pas dans l’interface.

| Ordre | Condition | Issue | Réglable |
|---|---|---|---|
| 1 | Image illisible | Recapture (`IMAGE_TOO_BLURRY` ou flag dominant) | Non |
| 2 | Mauvais côté | Revue `DOCUMENT_SIDE_MISMATCH` | Non |
| 3 | Pièce expirée, date valide | Voir §7.2 | Issue seulement |
| 4 | Classe inconnue, ou confiance de classe &lt; 0,80 | Revue `DOCUMENT_UNKNOWN` | Non |
| 5 | Type connu mais retiré du corridor de l’organisation | Refus `unsupported_document` | Via §8 |
| 6 | Champ requis manquant | Revue `MISSING_REQUIRED_FIELD` | Non |
| 7 | MRZ en échec | Revue `MRZ_MISMATCH` | Non |
| 8 | Confiance d’un champ requis sous le seuil OCR | Revue `LOW_OCR_CONFIDENCE` | Seuil, au-dessus du plancher |
| 9 | Indicateur suspect | Revue `SUSPICIOUS_INDICATOR` | Non. Jamais un refus automatique, jamais un approuvé |
| 10 | Bande haute, aucun erreur, aucun avertissement | Approuvé | Non |
| 11 | Reste du document | Revue `LOW_CONFIDENCE` | Non |
| 12 | Liveness sous le seuil | Refus `liveness_fail` | Seuil, au-dessus du plancher |
| 13 | Similarité sous le seuil de refus | Refus `face_match_fail` | Seuil, au-dessus du plancher |
| 14 | Similarité sous le seuil d’approbation | Revue `face_match_borderline` | Seuil, au-dessus du plancher |
| 15 | Liveness et similarité au-dessus | Approuvé | Non |
| 16 | Plafond d’essais photo | Voir §7.2 | Issue seulement |
| 17 | Fournisseur indisponible | Revue `provider_unavailable` | Non |

Le consentement refusé reste un refus `consent_declined`, hors de ce profil.

### 7.2 Choix laissés à l’organisation

| Paramètre | Défaut (code actuel) | Valeurs autorisées |
|---|---|---|
| Issue d’une pièce expirée | Refus | Refus ou revue. Jamais approuvé |
| Seuil OCR faible | 0,70 | De 0,70 à 0,95. En dessous, la publication est refusée |
| Seuil de liveness | 0,80 | De 0,80 à 0,99 |
| Seuil de refus visage | 0,75 | De 0,75 à 0,98, et strictement inférieur au seuil d’approbation |
| Seuil d’approbation visage | 0,90 | De 0,90 à 0,99, et strictement supérieur au seuil de refus |
| Issue au plafond d’essais photo | Refus | Refus ou revue |

Relever un seuil envoie davantage de dossiers en revue ou au refus. L’abaisser sous le plancher augmenterait les approbations automatiques : l’API répond `422` avec le champ en cause.

La présence réelle reste le challenge actif déjà livré. Le mode passif n’est pas un interrupteur de cet onglet.

---

## 8. Documents acceptés

La liste est le **catalogue actif** de la plateforme, pas une saisie libre.

Chaque ligne affiche le code, le pays, le type, la version de schéma et les côtés (recto, verso si la définition l’a). L’opérateur coche les documents que l’organisation accepte.

| Code livré | Type |
|---|---|
| `PASSPORT_TD3` | Passeport |
| `CANADA_PERMANENT_RESIDENT` | Titre de résident permanent, recto et verso |
| `QUEBEC_DRIVER_LICENSE` | Permis du Québec, recto et verso |

Règles de la liste :

- Au moins un document reste coché. Une publication vide est refusée.
- Un document retiré du catalogue actif disparaît de l’écran et ne peut plus être coché. Les sessions déjà créées gardent la version de politique qui le contenait.
- Décocher un type connu produit, pour les **nouvelles** sessions, un refus `unsupported_document`. Ce n’est pas une revue.
- Le verso n’est pas une case à part. S’il existe dans la définition active, le parcours continue de le demander.
- Ajouter un pays ou un type absent du catalogue est un chantier plateforme (nouvelle définition Document IA), pas une action de cet onglet.

Tant qu’aucune politique d’organisation n’est publiée, les trois documents actifs sont acceptés.

---

## 9. Parcours hébergé

| Paramètre | Défaut | Borne | Effet |
|---|---|---|---|
| Durée de vie du lien | 3600 secondes | 900 à 259200 secondes (15 minutes à 72 heures) | Calculée à la création de la session |
| Essais photo par face | 3 | 1 à 5 | Recto, verso et selfie ont chacun ce plafond |
| Essais de liveness facturés | 3 | 1 à 5 | Au-delà, le parcours s’arrête comme aujourd’hui (`liveness_attempts_exceeded`) |
| Texte de consentement | `consent-v1`, lecture seule | — | Le texte français et anglais du parcours est affiché. Il n’est pas éditable dans cette version du module |
| Langue du parcours | Celle du navigateur, français par défaut | — | Pas de langue forcée dans cette version |
| URL de retour | Absente | — | Reste un champ de session futur, pas un réglage d’organisation ici |
| Marque de l’organisation | Absente | — | Hors MVP |

Le bloc consentement explique que la version acceptée, l’horodatage et l’empreinte d’adresse sont conservés avec le dossier. Changer le texte plus tard créera une version `consent-v2` : ce n’est pas une édition silencieuse de `consent-v1`.

---

## 10. Version et effet sur les sessions

### 10.1 Cycle

```
(aucune ligne)  →  politique plateforme, policy_version absente
       │  publier
       ▼
version 1 active
       │  publier encore
       ▼
version 1 remplacée · version 2 active
```

Une seule version est active par organisation. Publier retire l’activité de la précédente sans la supprimer.

### 10.2 Ce que fige une session

À la création, la session copie :

- `policy_version` (nul si la plateforme est encore la source) ;
- la durée de lien issue de cette version ;
- l’identifiant de politique, pour relire les seuils au moment de la décision.

La décision relit **cette** version, même si une version plus récente a été publiée entre-temps. `rules_version` continue d’indiquer le moteur qui a exécuté la chaîne (`vision-1` à la fin du document, `bio-1` à la fin du visage).

Les sessions créées avant ce module gardent `policy_version` nul : elles sont décidées avec les constantes du code, comme aujourd’hui.

### 10.3 Fiche vérification

La fiche dossier affiche `policy_version` à côté de `rules_version` quand la valeur existe. Elle ne propose pas de changer la politique du dossier.

---

## 11. Modèle de données

Deux tables. Pas de colonne fourre-tout sur `organizations`.

**`idv_policies`**

| Colonne | Type | Rôle |
|---|---|---|
| `id` | UUID | Clé |
| `organization_id` | UUID | Tenant |
| `version` | INT | 1, 2, 3… unique par organisation |
| `active` | BOOLEAN | Une seule ligne vraie par organisation |
| `expired_outcome` | VARCHAR(16) | `DECLINED` ou `REVIEW` |
| `attempts_outcome` | VARCHAR(16) | `DECLINED` ou `REVIEW` |
| `ocr_review_at` | NUMERIC(4,3) | Seuil OCR |
| `liveness_pass_at` | NUMERIC(4,3) | Seuil liveness |
| `face_match_fail_at` | NUMERIC(4,3) | Seuil de refus visage |
| `face_match_pass_at` | NUMERIC(4,3) | Seuil d’approbation visage |
| `link_ttl_seconds` | INT | Durée du lien |
| `max_capture_attempts` | INT | Plafond photo |
| `max_liveness_attempts` | INT | Plafond liveness |
| `published_at` | TIMESTAMPTZ | Horodatage |
| `published_by` | UUID | Membre, sans copie de son nom dans le journal métier |

**`idv_policy_documents`**

| Colonne | Type | Rôle |
|---|---|---|
| `policy_id` | UUID | Version |
| `definition_code` | VARCHAR(64) | Code du catalogue, ex. `QUEBEC_DRIVER_LICENSE` |

Contrainte : une version active a au moins une ligne de document.

`verifications` gagne `policy_id` (UUID, nullable) et `policy_version` (INT, nullable).

---

## 12. API console

Préfixe `/v1/console/identity/policy`. Isolé par l’organisation de la session. Un autre produit n’a pas cette route.

### 12.1 Lire

`GET /v1/console/identity/policy`

```json
{
  "source": "platform",
  "version": null,
  "published_at": null,
  "profile": {
    "expired_outcome": "DECLINED",
    "attempts_outcome": "DECLINED",
    "ocr_review_at": 0.70,
    "liveness_pass_at": 0.80,
    "face_match_fail_at": 0.75,
    "face_match_pass_at": 0.90
  },
  "documents": [
    {"code": "PASSPORT_TD3", "document_type": "PASSPORT", "country": "UT", "enabled": true}
  ],
  "flow": {
    "link_ttl_seconds": 3600,
    "max_capture_attempts": 3,
    "max_liveness_attempts": 3,
    "consent_text_version": "consent-v1"
  },
  "bounds": {
    "ocr_review_at": [0.70, 0.95],
    "liveness_pass_at": [0.80, 0.99],
    "face_match_fail_at": [0.75, 0.98],
    "face_match_pass_at": [0.90, 0.99],
    "link_ttl_seconds": [900, 259200],
    "max_capture_attempts": [1, 5],
    "max_liveness_attempts": [1, 5]
  }
}
```

`source` vaut `platform` ou `organization`. `bounds` est toujours renvoyé, pour que l’écran empêche une saisie hors plage avant l’envoi.

### 12.2 Publier

`POST /v1/console/identity/policy` avec `POLICY_WRITE`.

Corps : les champs de `profile`, la liste `document_codes`, et les trois entiers de `flow`. Pas de `consent_text_version` : le client ne le choisit pas.

Réponses :

| Cas | Résultat |
|---|---|
| Valide | `201`, corps identique au GET avec `source = organization` et `version` incrémentée |
| Hors borne, liste vide, code inconnu ou inactif, issue interdite | `422`, détail par champ |
| Sans permission | `403` |
| Session absente | `401` |

La publication écrit un événement d’audit `idv.policy.published` : identifiant d’organisation, numéro de version, identifiant du membre. Pas de seuil dans le message si cela duplique la ligne de politique ; la ligne fait foi. Pas de donnée personnelle.

### 12.3 Historique

`GET /v1/console/identity/policy/versions` retourne les versions de l’organisation, de la plus récente à la plus ancienne, sans les images ni les dossiers. Lecture ouverte à tout membre connecté de l’organisation. L’écran V1 montre la version active ; l’historique est consommé par la fiche et par un contrôle ultérieur.

---

## 13. Règles de gestion

| ID | Règle |
|---|---|
| **RG-POL-01** | Sans version d’organisation, la décision utilise les constantes du code et tout le catalogue actif. |
| **RG-POL-02** | Une publication crée une version immuable et désactive la précédente. |
| **RG-POL-03** | La session copie `policy_id` à la création. La décision n’utilise pas la version devenue active après. |
| **RG-POL-04** | Un seuil sous le plancher, ou un seuil de refus visage ≥ seuil d’approbation, rejette la publication. |
| **RG-POL-05** | L’issue d’expiration et l’issue de plafond d’essais sont `DECLINED` ou `REVIEW`. |
| **RG-POL-06** | Un indicateur suspect reste une revue. Le profil ne peut pas le transformer en approuvé ni en refus. |
| **RG-POL-07** | Un document coché doit être une définition active. Zéro document coché rejette la publication. |
| **RG-POL-08** | Un type actif non coché, reconnu sur une nouvelle session, donne `unsupported_document` et un refus. |
| **RG-POL-09** | `rules_version` et `policy_version` sont deux champs. Publier une politique ne renomme pas `vision-1` ni `bio-1`. |
| **RG-POL-10** | La politique d’une organisation est invisible pour une autre. L’API répond comme si la route n’avait rien à montrer d’un autre tenant : les données ne sortent pas. |
| **RG-POL-11** | Le texte `consent-v1` n’est pas modifiable par cette API. |
| **RG-POL-12** | Sandbox et live appliquent la même version active. |

---

## 14. Cas d’utilisation

**UC-POL-01 — Consulter**  
Un membre ouvre Règles. Il voit la source, les seuils, les documents cochés et la durée du lien. Sans droit de publication, aucun contrôle d’édition.

**UC-POL-02 — Resserrer le visage**  
Un administrateur passe le seuil d’approbation de 0,90 à 0,95 et publie. Les sessions créées ensuite exigent 0,95 pour un approuvé automatique. Un lien créé avant garde 0,90.

**UC-POL-03 — Retirer un document**  
Il décoche le permis du Québec et publie. Une nouvelle session dont la pièce est ce permis se termine en refus `unsupported_document`. Une session déjà ouverte avec cette pièce continue avec l’ancienne liste.

**UC-POL-04 — Allonger le lien**  
Il passe la durée à 24 heures. Seules les sessions créées après reçoivent cette durée.

**UC-POL-05 — Publication refusée**  
Il tente un seuil de liveness à 0,50, ou une liste de documents vide. L’écran reste sur le formulaire, le champ est signalé, aucune version n’est créée.

**UC-POL-06 — Lecteur**  
Un lecteur ouvre l’onglet. Les valeurs s’affichent. Publier est absent.

---

## 15. Critères d’acceptation

| ID | Critère |
|---|---|
| **AC-POL-01** | `/identity/rules` affiche les trois sections avec les valeurs effectives, y compris quand aucune ligne `idv_policies` n’existe. |
| **AC-POL-02** | Un membre sans `POLICY_WRITE` ne peut pas publier, ni par l’écran ni par l’API. |
| **AC-POL-03** | Publier des seuils égaux aux défauts crée la version 1 et passe `source` à `organization`. |
| **AC-POL-04** | Une seconde publication crée la version 2, laisse la version 1 en base, et ne laisse qu’une ligne active. |
| **AC-POL-05** | Une session créée après publication porte `policy_version`. Sa décision visage utilise les seuils de cette version. |
| **AC-POL-06** | Une session créée avant, ou pendant qu’une publication est en cours mais non encore faite, ne change pas de seuils. |
| **AC-POL-07** | Décocher un code du catalogue actif produit `unsupported_document` sur une nouvelle session de ce type, et ne modifie pas une session antérieure. |
| **AC-POL-08** | `ocr_review_at = 0,50`, liste vide, ou `face_match_fail_at` ≥ `face_match_pass_at` répondent `422` et n’écrivent rien. |
| **AC-POL-09** | L’audit `idv.policy.published` ne contient ni nom, ni image, ni numéro de pièce. |
| **AC-POL-10** | `/biometrics/rules` et `/aml/rules` restent les annonces actuelles. |

---

## 16. Découpage

| Palier | Livrable | L’onglet |
|---|---|---|
| **R1** | `GET` politique plateforme, écran en lecture | N’est plus vide : seuils, trois documents, durée du lien, texte de consentement affiché |
| **R2** | Tables, `POST`, copie sur la session, branchement durée de lien, essais, corridor | Publier documents, durée, plafonds |
| **R3** | Seuils et issues branchés dans `decideVision` et `applyFace` | Publier le profil de décision |
| **R4** | Historique visible, `policy_version` sur la fiche | Consultation d’une version antérieure |

R1 suffit à retirer les trois cartes placeholder. R2 et R3 sont le premier comportement que l’organisation peut changer. R4 ne bloque pas l’usage.

Le texte de consentement personnalisé, la langue forcée, l’URL de retour, la séparation sandbox / live, l’AML et l’enrôlement attendent leur propre spécification. Ils ne s’ajoutent pas en paramètre libre de R2.

---

## 17. Décisions de ce document

| Sujet | Décision |
|---|---|
| Granularité | Une politique Identité par organisation, toutes intégrations |
| Éditeur | Paramètres bornés. Pas de langage de règles saisi par l’opérateur |
| Seuils | Plancher = constantes actuelles. On ne descend pas en dessous |
| Expiration | Refus par défaut, revue autorisée, approuvé interdit |
| Document hors liste organisation | Refus `unsupported_document` |
| Document de classe inconnue | Revue, comme `vision-1` |
| Indicateur suspect | Revue, non réglable |
| Consentement | Affiché, version `consent-v1`, non éditable ici |
| Sessions ouvertes | Inchangées par une publication |
| Biométrie et AML | Hors de ce module |
