# Freeze mapping AnalyzeID — permis QC (D1)

**Date :** 21 septembre 2026  
**Décision :** **go AnalyzeID** (mapping figé sur clés ci-dessous ; pas de DetectText).  
**Région AWS :** `ca-central-1` (propriété `kyc.aws.region`).  
**Spécimen de référence :** recto SAAQ (layout public annoté) — **pas** commit de photo / PII réelle.  
**Fixture CI :** [`api/src/test/resources/fixtures/analyzeid-qc.json`](../../api/src/test/resources/fixtures/analyzeid-qc.json) (clés réelles, valeurs factices, expiration **future**).

## Corridor accepté

| Notre champ | Valeur |
|---|---|
| `document_type` | `driving_license` |
| `document_country` | `CA` |
| `issuing_jurisdiction` | `QC` |
| Face | recto seulement |

## Tableau figé (notre champ → clé AnalyzeID)

| Notre champ | Clé AnalyzeID | Notes SAAQ |
|---|---|---|
| `first_name` | `FIRST_NAME` | #2 |
| `last_name` | `LAST_NAME` | #1 |
| `birth_date` | `DATE_OF_BIRTH` | #3, A-M-J |
| `expiration_date` | `EXPIRATION_DATE` | #4b (pas 4a) |
| `document_number` | `DOCUMENT_NUMBER` | #4d (pas n° de référence #5) |
| Type | `ID_TYPE` | doit contenir `DRIVER LICENSE` / `DRIVER'S LICENSE` |
| Pays | `COUNTRY` | `CANADA` / `CA` / `CAN` (sinon implicite CA si juridiction QC) |
| Juridiction | `STATE_NAME` | `QUEBEC` / `QUÉBEC` / `QC` / `QUE.` |

Hors mapping : adresse (#8), classes, mentions, sexe, taille, yeux, n° de référence (#5), date de délivrance (#4a).

## Go / no-go

- **Go** : type permis + nom + naissance + expiration + numéro + juridiction QC identifiables dans le JSON AnalyzeID.
- **No-go DetectText** : **non retenu** pour ce freeze. Si un spécimen photo staging échoue plus tard, amender ce fichier **et** le CDC §11.2 avant d’ouvrir un repli.

## Hors périmètre

Ontario, autres provinces, passeports, verso, Bedrock, catalogue « permis CA ».
