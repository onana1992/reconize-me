# Guide — compte AWS local (IAM + Textract + Rekognition + S3)

**Plateforme :** Recogniz-Me  
**Objectif :** brancher l’API live sur AWS **sans AWS CLI**. Chaque service a ses propres clés dans l’application.  
**Date :** 4 octobre 2026  
**Région par défaut du projet :** `ca-central-1`

Le mapping permis QC et la mesure AnalyzeID restent dans [`guide-aws-textract.md`](./guide-aws-textract.md) et [`mapping-analyzeid-qc.md`](./mapping-analyzeid-qc.md).

---

## 1. Principe

Tu ne dois **pas** coller les clés du compte **root** (e-mail de connexion au compte). Root = facturation, IAM, tout le compte.

Les clients Java (**S3**, **Textract**, **Rekognition**) n’utilisent **pas** `~/.aws/credentials`, **pas** `aws configure`, **pas** `AWS_ACCESS_KEY_ID` d’environnement. Chaque client lit **uniquement** les propriétés du service dans `api/application-secrets.properties` (gitignored).

Les JPEG passent **en bytes** dans AnalyzeID et CompareFaces. Le **stockage** des captures (recto, verso, selfie) va dans **S3** si `kyc.aws.s3.enabled=true` — le navigateur continue de PUT/GET sur `/v1/objects` (HMAC) ; l’API écrit le bucket en privé.

---

## 2. Ce que Recogniz-Me utilise

| Service | Politique AWS (nom exact) | Propriétés d’accès (secrets) | Maintenant (M5) |
|---|---|---|---|
| **Textract** | `AmazonTextractFullAccess` | `kyc.aws.textract.access-key-id` / `secret-access-key` | **AnalyzeID** (recto permis QC) |
| **Rekognition** | `AmazonRekognitionFullAccess` | `kyc.aws.rekognition.access-key-id` / `secret-access-key` | **CompareFaces** (selfie ↔ pièce de **cette** session) |
| **S3** | `AmazonS3FullAccess` | `kyc.aws.s3.access-key-id` / `secret-access-key` | Médias si `kyc.aws.s3.enabled=true` (Put/Get/Head, SSE-S3) |

**Face Liveness** n’est **pas** branché. `AmazonRekognitionFullAccess` l’autorise déjà : ça ne l’active pas tout seul.

Les politiques **Full Access** sont plus larges que le code (qui n’a besoin que de `textract:AnalyzeID` + `rekognition:CompareFaces` + Put/Get/Head sur le bucket médias). C’est un choix **dev / staging**. En **production**, on resserrera.

En local tu peux coller **la même paire de clés** dans les trois blocs (un seul utilisateur IAM). Tu peux aussi créer **trois utilisateurs** et trois paires distinctes — l’app les traite déjà comme des identités séparées.

---

## 3. Avant de commencer

- Un compte AWS (idéalement **staging**, pas le compte facturation client).
- Tu es connecté en **administrateur** (root ou IAM admin) pour créer l’utilisateur et le bucket.
- **Pas besoin d’AWS CLI.**
- Budget : AnalyzeID ≈ **0,025 $** par page. Pose une alerte **AWS Budgets** (ex. 5–10 $/mois).

Si AnalyzeID n’existe pas dans `ca-central-1`, mets `kyc.aws.region=us-east-1` (ou la région de repli notée au freeze), **et** la même région sur chaque service si tu la surcharges.

---

## 4. Créer l’utilisateur IAM (console)

1. Ouvre [IAM → Users](https://console.aws.amazon.com/iam/home#/users).
2. **Create user**.
3. **User name** : ex. `recognizme-local-dev` (pas d’espaces).
4. **Provide user access to the AWS Management Console** : **laisser décoché**. Cet utilisateur sert à l’**API** (clés d’accès).
5. **Next**.

### 4.1 Attacher les trois politiques

1. **Attach policies directly**.
2. Coche **exactement** :

   | Politique | Nom AWS |
   |---|---|
   | S3 | `AmazonS3FullAccess` |
   | Rekognition | `AmazonRekognitionFullAccess` |
   | Textract | `AmazonTextractFullAccess` |

3. **Next**, **Create user**.

Pas besoin de `AdministratorAccess`, ni de Bedrock, ni de SageMaker pour M5.

Pour **trois utilisateurs** (une identité par service) : répète cette étape avec un nom du type `recognizme-textract-dev`, `recognizme-rekognition-dev`, `recognizme-s3-dev`, et n’attache **que** la politique du service.

---

## 5. Créer les clés d’accès (console)

Sans cette étape, l’API n’a **aucun** mot de passe AWS.

1. Clique sur l’utilisateur.
2. Onglet **Security credentials**.
3. **Create access key**.
4. Cas d’usage : **Application running outside AWS** (application locale). **Pas** Command Line Interface.
5. Coche la confirmation, **Create access key**.
6. Tu vois deux valeurs :

   | Champ | Exemple | Rôle |
   |---|---|---|
   | **Access key ID** | `AKIA…` | identifiant public |
   | **Secret access key** | longue chaîne | **mot de passe** — affiché **une seule fois** |

7. Copie-les tout de suite dans `api/application-secrets.properties` (voir §6).  
   Si tu fermes la page sans copier le secret, tu dois **créer une nouvelle clé**.

Ne colle **jamais** ces valeurs dans un fichier suivi par git (`application.properties`, README, chat public).

---

## 6. Configurer l’application (un bloc par service)

1. Ouvre `api/application-secrets.properties` (déjà **gitignored**).  
   S’il n’existe pas : copie `api/application-secrets.properties.example`.
2. Colle (adapte bucket et clés) :

```properties
kyc.aws.enabled=true
kyc.aws.region=ca-central-1

# Textract — AnalyzeID
kyc.aws.textract.access-key-id=AKIA...
kyc.aws.textract.secret-access-key=...
# kyc.aws.textract.region=ca-central-1

# Rekognition — CompareFaces
kyc.aws.rekognition.access-key-id=AKIA...
kyc.aws.rekognition.secret-access-key=...
# kyc.aws.rekognition.region=ca-central-1

# S3 — images
kyc.aws.s3.enabled=true
kyc.aws.s3.bucket=recognizme-media-dev-<ton-compte>
kyc.aws.s3.key-prefix=kyc/
kyc.aws.s3.access-key-id=AKIA...
kyc.aws.s3.secret-access-key=...
# kyc.aws.s3.region=ca-central-1
```

3. **Relance** `RecognizMeApplication` (un hot-reload ne charge pas toujours ce fichier).

Règles :

| Flag | Clés exigées au démarrage |
|---|---|
| `kyc.aws.enabled=true` | Textract **et** Rekognition (`access-key-id` + `secret-access-key`) |
| `kyc.aws.s3.enabled=true` | S3 : `bucket` + `access-key-id` + `secret-access-key` |

Région d’un service : si `kyc.aws.<service>.region` est vide, l’app utilise `kyc.aws.region`.

Sans `kyc.aws.enabled=true`, le live reste en `provider_unavailable` (pas d’appel Textract/Rekognition).  
Sans `kyc.aws.s3.enabled=true`, les images restent sur le disque `kyc.object-storage-root`.

---

## 7. Bucket S3 (console)

Sans ces flags, les captures restent sur `./data/media`. Les tests Maven **restent** sur le filesystem.

1. Console AWS → **S3** → **Create bucket**.
2. Nom unique mondialement, ex. `recognizme-media-dev-<ton-compte>`.
3. **Région** = `kyc.aws.region` (ou `kyc.aws.s3.region` si tu l’as fixée).
4. **Block all public access** : **laisser coché**.
5. Chiffrement par défaut : SSE-S3 (AES-256). L’API pose aussi `AES256` à chaque Put.
6. Relance l’API. Au démarrage : `Object storage using S3 bucket=… prefix=kyc/`.
7. Après une capture, objet du type  
   `kyc/org/{organizationId}/verifications/{verificationId}/document/1`  
   (et `selfie/`, éventuellement `document_back/`).

Le flow et la console **ne changent pas** : PUT/GET restent `/v1/objects?key=…&exp=…&sig=…`. Pas de CORS bucket.

---

## 8. Vérifier que le live parle vraiment à AWS

1. Console Recogniz-Me : org avec **solde ≥ 0,90 $**, intégration **`live`**, clé `ky_live_`.
2. Crée une vérification, ouvre le flow, consentement, **recto permis QC**, selfie de la **même** personne.
3. Logs API : `AnalyzeID completed` puis `CompareFaces completed` (durée, **pas** de nom ni numéro de permis).
4. Fiche console : extraits réels, `rules_version` = `m5-1`.
5. Une vérif **`test`** (`ky_test_`) du **même** JAR ne doit **pas** appeler Textract ni Rekognition.

Si AnalyzeID / CompareFaces / S3 **AccessDenied** : la politique n’est pas sur **l’utilisateur de ce bloc** de clés, ou tu as collé la mauvaise paire dans Textract / Rekognition / S3.

Si `provider_unavailable` sans appel réseau : `kyc.aws.enabled` n’est pas pris en compte (mauvais fichier secrets, API pas relancée).

Si l’API **refuse de démarrer** : une clé ou le bucket manque alors que le flag du service est `true`.

---

## 9. Ce qu’il ne faut pas faire

| Interdit | Pourquoi |
|---|---|
| Committer `application-secrets.properties`, le CSV, ou `AKIA` | Fuite = facture + lecture de tes buckets |
| Utiliser le compte **root** comme clé d’app | Impossible à limiter |
| `aws configure` / `~/.aws/credentials` pour cette API | L’app **ignore** la chaîne SDK ; les clés doivent être dans les secrets |
| Activer Face Liveness « pour tester » dans le flow | Hors M5 ; il faut du code Amplify |
| Bucket S3 public / ACL public-read | Les médias IDV ne doivent jamais être listables sans HMAC API |
| Laisser `kyc.aws.enabled=true` sans Budget | Un bug de recapture peut multiplier AnalyzeID |

Pour révoquer : IAM → utilisateur → Access keys → **Deactivate** / **Delete**. L’app cessera d’authentifier immédiatement.

---

## 10. Rappel des trois politiques (recherche IAM)

```
AmazonS3FullAccess
AmazonRekognitionFullAccess
AmazonTextractFullAccess
```

(`AmazonRekognitionFullAccess` : une lettre `s` à « Access ».)

---

## 11. Suite

- Mesure AnalyzeID / freeze QC : [`guide-aws-textract.md`](./guide-aws-textract.md).
- Ordre M5 (dont palier D5) : [`roadmap-implementation-m5.md`](./roadmap-implementation-m5.md).
- En prod : rôle IAM sur l’instance / ECS, politiques **resserrées**, secrets hors git (pas Full Access).
