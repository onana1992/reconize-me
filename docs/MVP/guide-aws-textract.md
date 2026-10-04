# Guide — mesure AnalyzeID (D1)

Runbook local pour le palier **D1** de M5. Objectif : un JSON AnalyzeID sur un **recto permis QC**, freeze du mapping, région notée. **Pas** encore de `AwsDocumentAi` en prod.

**Documents liés :**
- [`guide-aws-iam-local.md`](./guide-aws-iam-local.md) — utilisateur IAM, **clés par service** dans `application-secrets.properties` (pas AWS CLI)

Freeze écrit : [`mapping-analyzeid-qc.md`](./mapping-analyzeid-qc.md).

## Prérequis

- Compte AWS **staging** (pas prod client).
- IAM moindre privilège : `textract:AnalyzeID` seulement (pas `AmazonTextractFullAccess`, pas Bedrock).
- Photo nette du **recto** (carton seul, **sans** flèches / légendes du spécimen annoté).
- Qualité déjà OK côté SDK M4 (JPEG/PNG, côté court ≥ 720 px).

## Région

1. Essayer **`ca-central-1`**.
2. Si AnalyzeID y est indisponible : noter le repli (`us-east-1` ou `eu-west-1`) dans `mapping-analyzeid-qc.md` **et** `kyc.aws.region`.

## CLI (exemple)

```bash
aws textract analyze-id \
  --region ca-central-1 \
  --document-pages fileb://recto-qc.jpg \
  --output json > /tmp/analyzeid-qc-raw.json
```

Sanitiser avant git : remplacer noms / numéros / dates réels par des valeurs factices ; **garder** les `Type.Text`. Déposer le résultat dans `api/src/test/resources/fixtures/analyzeid-qc.json`.

## Checklist go / no-go

| Critère | Attendu |
|---|---|
| Type permis | `ID_TYPE` ≈ DRIVER LICENSE |
| Nom | `FIRST_NAME` + `LAST_NAME` |
| Naissance | `DATE_OF_BIRTH` parseable |
| Expiration | `EXPIRATION_DATE` (= SAAQ #4b) |
| Numéro | `DOCUMENT_NUMBER` (= SAAQ #4d, pas #5) |
| Juridiction QC | `STATE_NAME` / équivalent |

**Go** → compléter [`mapping-analyzeid-qc.md`](./mapping-analyzeid-qc.md), ouvrir D2.  
**No-go** → DetectText + mapping **de ce** spécimen seulement ; amender CDC §11.2 **avant** le vert M5.

## Interdits

- Commit d’une photo de permis ou d’un nom réel.
- Mapper Ontario « au cas où ».
- Appeler CompareFaces / Bedrock pendant D1.
