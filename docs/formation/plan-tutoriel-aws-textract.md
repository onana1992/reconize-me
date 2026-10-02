# Plan tutoriel — Amazon Textract, de zéro à la production

**Sujet :** Amazon Textract (OCR / extraction documentaire AWS)
**Public :** développeur·se qui sait coder (Python ou Java) mais n'a jamais fait d'OCR ni touché à Textract
**Durée :** ~22 h de travail effectif (10 modules + projet final)
**Format :** chaque module = objectifs → théorie courte → lab guidé → pièges → auto-évaluation
**Portée :** document autonome, **indépendant de tout projet existant**. Aucun prérequis de code métier.

> Les chiffres de prix et de quotas cités viennent de la doc AWS (région **US West / Oregon** pour les prix). Ils bougent : la source de vérité reste [la page pricing](https://aws.amazon.com/textract/pricing/) et [Service Quotas](https://docs.aws.amazon.com/textract/latest/dg/limits.html).

---

## Prérequis et matériel

| Besoin | Détail |
|---|---|
| Compte AWS | personnel, avec droit de créer rôles IAM, buckets S3, topics SNS |
| CLI | AWS CLI v2 installée et configurée (`aws configure`) |
| Langage | Python 3.10+ avec `boto3` et `Pillow` (les labs sont donnés en Python ; équivalents Java SDK v2 signalés) |
| Région de travail | une seule, par ex. `us-east-1` ou `eu-west-3` (Paris) — toutes les fonctions ne sont pas partout |
| Jeu de documents | 15–20 pages : facture scannée, formulaire rempli à la main, tableau financier, pièce d'identité factice, PDF multipage, 1 photo floue volontairement mauvaise |

**Garde-fou coût, à faire avant tout appel :** AWS Budgets → budget mensuel 5 $ avec alerte e-mail. Le free tier Textract dure **3 mois** (1 000 pages/mois DetectDocumentText, 100 pages/mois Forms/Tables/Layout, 100 pages/mois Queries, 1 000 pages/mois Signatures, 100 pages/mois Expense, 100 pages/mois AnalyzeID). Après, chaque appel est facturé.

---

## Progression conseillée

| Rythme | Découpage |
|---|---|
| Intensif | 3 jours : M0–M3 / M4–M6 / M7–M10, projet le 4ᵉ jour |
| Soirées | 6 semaines, 2 modules par semaine |
| Minimum viable | M0, M1, M2, M3, M4, M6, M9 (le reste en lecture) |

---

## Module 0 — Environnement et garde-fous (1 h)

**Objectifs :** appeler l'API sans jamais committer de secret, et savoir ce qu'on dépense.

1. Créer un utilisateur/rôle IAM dédié à l'apprentissage. Politique de départ :

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow",
      "Action": ["textract:DetectDocumentText", "textract:AnalyzeDocument"],
      "Resource": "*" },
    { "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject"],
      "Resource": "arn:aws:s3:::mon-bucket-textract-lab/*" }
  ]
}
```

2. Vérifier l'identité effective : `aws sts get-caller-identity`.
3. Créer le bucket d'entraînement, dans **la même région** que l'endpoint Textract utilisé.
4. Uploader le jeu de documents.

**Pièges :** Textract ne lit pas un objet S3 d'une autre région ; les credentials en variables d'environnement l'emportent sur `~/.aws/credentials` ; un bucket public est une fuite de données, laisser le Block Public Access activé.

**Auto-évaluation :** je peux expliquer pourquoi `Resource: "*"` est acceptable pour `textract:*` mais pas pour `s3:*`.

---

## Module 1 — Ce qu'est Textract, et quand ne pas l'utiliser (1 h)

**Objectifs :** placer Textract dans le paysage, choisir la bonne API du premier coup.

- **OCR** = transformer des pixels en caractères. **IDP** (Intelligent Document Processing) = en plus, comprendre la structure (clés/valeurs, tableaux, totaux). Textract fait les deux.
- Textract est un service **managé, pré-entraîné, sans entraînement requis** (sauf Custom Queries). Pas de modèle à héberger, facturation à la page.

Les cinq familles d'API :

| API | Ce qu'elle rend | Cas typique |
|---|---|---|
| `DetectDocumentText` | texte brut : pages, lignes, mots | indexation, recherche plein texte, brouillon de RAG |
| `AnalyzeDocument` | + `FORMS`, `TABLES`, `QUERIES`, `SIGNATURES`, `LAYOUT` | formulaires, contrats, rapports |
| `AnalyzeExpense` | champs normalisés facture/reçu + lignes d'articles | comptabilité fournisseurs |
| `AnalyzeID` | champs normalisés de pièce d'identité | onboarding, vérification d'identité |
| `AnalyzeLending` | classification + découpage + extraction de dossiers de prêt immobilier (US) | crédit hypothécaire |

**Comparatif de voisinage :** Rekognition détecte du texte *dans des scènes* (panneaux, photos), pas des documents. Comprehend fait du NLP (entités, sentiment) *après* l'OCR. Un LLM multimodal (Bedrock) peut lire un document mais coûte, hallucine et ne rend pas de coordonnées ; Textract donne des `BoundingBox` et un score de confiance, ce qui est indispensable dès qu'un humain doit revoir. Tesseract auto-hébergé est gratuit mais sans structure ni SLA.

**Deux modes d'appel, à ne jamais confondre :**

| | Synchrone | Asynchrone |
|---|---|---|
| Appel | `DetectDocumentText`, `AnalyzeDocument`, `AnalyzeExpense`, `AnalyzeID` | `StartXxx` + `GetXxx` |
| Entrée | octets en base64 **ou** objet S3 | objet S3 uniquement |
| Taille | 10 Mo ; PDF/TIFF **1 page** | JPEG/PNG 10 Mo ; PDF/TIFF **500 Mo et 3 000 pages** |
| Latence | réponse immédiate | job + notification SNS |

**Quiz :** un PDF de 40 pages en synchrone ? (non — `UnsupportedDocumentException`/1 page max). Une facture où je veux juste le total et le SIRET : Forms ou Queries ? (Queries, moins cher et plus direct).

---

## Module 2 — Premier appel : `DetectDocumentText` (1 h 30)

**Objectifs :** obtenir un JSON de réponse et savoir ce qui est facturé.

Formats acceptés : **JPEG, PNG, PDF, TIFF** (JPEG 2000 encapsulé dans un PDF accepté). **Les PDF XFA ne sont pas supportés.** Une image = 1 page facturée ; un PDF = 1 page facturée par page.

Lab, depuis S3 :

```bash
aws textract detect-document-text \
  --document '{"S3Object":{"Bucket":"mon-bucket-textract-lab","Name":"facture.png"}}' \
  --region us-east-1 > out-detect.json
```

Lab, depuis un fichier local (la CLI ne sait pas passer d'octets, il faut un SDK) :

```python
import boto3

client = boto3.client("textract", region_name="us-east-1")
with open("facture.png", "rb") as f:
    resp = client.detect_document_text(Document={"Bytes": f.read()})

for b in resp["Blocks"]:
    if b["BlockType"] == "LINE":
        print(round(b["Confidence"], 1), b["Text"])
```

Équivalent Java (SDK v2) : `TextractClient.detectDocumentText(DetectDocumentTextRequest.builder().document(Document.builder().bytes(SdkBytes.fromByteArray(...)).build()).build())`.

**Exercices :** 1) comparer la sortie du scan propre et de la photo floue ; 2) mesurer le temps de réponse sur 5 appels ; 3) envoyer un fichier de 12 Mo et lire l'erreur (`DocumentTooLargeException`).

**Pièges :** ré-encoder ou downsampler l'image avant envoi **dégrade** le résultat — AWS recommande d'envoyer le fichier d'origine ; un PDF « scanné » de 1 page passe en synchrone, un PDF de 2 pages non.

---

## Module 3 — Le modèle de réponse : `Block`, relations, géométrie, confiance (2 h)

**C'est le module qui débloque tout le reste.** Tout Textract parle le même dialecte : une liste plate de `Block` reliés par identifiants.

- `DocumentMetadata.Pages` : nombre de pages traitées.
- Chaque `Block` a un `Id`, un `BlockType`, souvent `Text`, `Confidence` (0–100), `Geometry`, `Relationships`.
- Types de base : `PAGE`, `LINE`, `WORD`. Ajoutés par l'analyse : `KEY_VALUE_SET`, `TABLE`, `CELL`, `MERGED_CELL`, `SELECTION_ELEMENT`, `QUERY`, `QUERY_RESULT`, `SIGNATURE`, `LAYOUT_*`.
- `Relationships` : `CHILD` (une `LINE` liste ses `WORD`), `VALUE` (une clé pointe sa valeur), plus `ANSWER`, `MERGED_CELL`, `TABLE_TITLE`, `TABLE_FOOTER` selon la fonction.
- Un enfant **ne connaît pas** son parent : la seule navigation possible est parent → enfants.
- `Geometry.BoundingBox` est **normalisé** (`Left`, `Top`, `Width`, `Height` entre 0 et 1, relatifs à la page) ; `Polygon` donne 4 points et supporte les documents inclinés. Pour dessiner : `x_px = Left * largeur_image`.

Structure mentale à retenir :

```
PAGE
 ├── LINE ──CHILD──> WORD, WORD, WORD
 ├── TABLE ──CHILD──> CELL ──CHILD──> WORD | SELECTION_ELEMENT
 └── KEY_VALUE_SET(KEY) ──VALUE──> KEY_VALUE_SET(VALUE) ──CHILD──> WORD
```

**Lab obligatoire — la brique réutilisée partout :**

```python
def index_blocks(resp):
    return {b["Id"]: b for b in resp["Blocks"]}

def text_of(block, by_id):
    words = []
    for rel in block.get("Relationships", []):
        if rel["Type"] != "CHILD":
            continue
        for cid in rel["Ids"]:
            child = by_id[cid]
            if child["BlockType"] == "WORD":
                words.append(child["Text"])
            elif child["BlockType"] == "SELECTION_ELEMENT":
                words.append("[X]" if child["SelectionStatus"] == "SELECTED" else "[ ]")
    return " ".join(words)
```

**Exercices :** 1) reconstruire le texte page par page dans l'ordre de lecture ; 2) dessiner avec Pillow toutes les `LINE` en vert et celles sous 90 % de confiance en rouge — c'est l'outil de debug le plus utile de tout le cours ; 3) calculer la confiance moyenne par page.

**Pièges :** l'ordre des blocs dans le tableau n'est pas garanti sémantiquement sur les documents multi-colonnes (c'est le rôle de `LAYOUT`) ; en asynchrone les blocs arrivent **par pages paginées**, il faut concaténer avant de raisonner.

---

## Module 4 — `AnalyzeDocument` : FORMS et TABLES (2 h)

**Objectifs :** sortir un dictionnaire clé→valeur et un CSV depuis un formulaire réel.

```bash
aws textract analyze-document \
  --document '{"S3Object":{"Bucket":"mon-bucket-textract-lab","Name":"formulaire.png"}}' \
  --feature-types '["FORMS","TABLES"]' > out-analyze.json
```

**FORMS :** deux blocs `KEY_VALUE_SET` par champ, distingués par `EntityTypes` (`["KEY"]` ou `["VALUE"]`). La clé pointe sa valeur via une relation `VALUE`, puis chacune pointe ses `WORD` via `CHILD`. Les cases à cocher remontent en `SELECTION_ELEMENT` avec `SelectionStatus: SELECTED | NOT_SELECTED`.

**TABLES :** `TABLE` → `CELL` avec `RowIndex`, `ColumnIndex`, `RowSpan`, `ColumnSpan`. Les fusions apparaissent aussi en `MERGED_CELL`. Titres et notes de bas de tableau : relations `TABLE_TITLE` / `TABLE_FOOTER`.

**Exercices :** 1) produire `{"Nom": "Dupont", "Date de naissance": "12/03/1988"}` ; 2) exporter chaque `TABLE` en CSV en respectant les spans ; 3) lister les champs dont la confiance valeur < 95 %.

**Pièges classiques :**
- une clé peut exister **sans** valeur détectée (relation `VALUE` absente) — ne pas indexer sans garde ;
- la même clé apparaît deux fois sur un formulaire à deux colonnes ; garder la position pour désambiguïser ;
- les libellés varient d'un émetteur à l'autre : normaliser côté code (minuscules, accents, ponctuation) ou passer à Queries ;
- **coût :** une page avec `FORMS` est ~3× plus chère qu'avec `TABLES` (0,05 $ vs 0,015 $). Ne demandez jamais une feature dont vous ne lisez pas la sortie.

---

## Module 5 — QUERIES, LAYOUT, SIGNATURES (1 h 30)

**QUERIES** — vous posez la question en langage naturel, Textract répond. Idéal quand la mise en page varie mais que les données voulues sont connues.

```json
{
  "FeatureTypes": ["QUERIES"],
  "QueriesConfig": {
    "Queries": [
      { "Text": "What is the invoice total?", "Alias": "TOTAL" },
      { "Text": "What is the invoice date?", "Alias": "DATE", "Pages": ["1"] }
    ]
  }
}
```

Résultat : un bloc `QUERY` relié par `ANSWER` à un `QUERY_RESULT` (texte + confiance).

Limites à connaître : **15 requêtes/page en synchrone, 30 en asynchrone** ; réponse tronquée à **128 caractères** ; question ≤ 200 caractères ; `Pages` accepte `["1"]`, `["1-3"]`, `["4-*"]`, ou `["*"]` seul. Attention : `["*"]` compte la requête **sur chaque page**, donc 30 requêtes maximum même sur un document de 10 pages.

**LAYOUT** — rend la structure logique (`LAYOUT_TITLE`, `LAYOUT_SECTION_HEADER`, `LAYOUT_TEXT`, `LAYOUT_LIST`, `LAYOUT_TABLE`, `LAYOUT_FIGURE`, `LAYOUT_HEADER`, `LAYOUT_FOOTER`, `LAYOUT_PAGE_NUMBER`) et l'ordre de lecture. C'est la fonction à utiliser pour convertir un rapport en Markdown propre destiné à un LLM. Bonus : **facturée 0 $ quand elle est demandée avec `TABLES`**.

**SIGNATURES** — détecte signatures manuscrites, électroniques et paraphes, avec position. La moins chère des features d'analyse (0,0035 $/page).

**Custom Queries (adapters)** — si les Queries pré-entraînées se trompent sur *vos* documents, on entraîne un adaptateur depuis la console avec quelques dizaines d'exemples annotés (`CreateAdapter`, `CreateAdapterVersion`, puis `AdapterConfig` dans l'appel). Quotas : 10 adaptateurs, 10 versions réussies par mois, 3 entraînements simultanés, fichiers d'entraînement ≤ 10 Mo. Pas de free tier, 0,025 $/page.

**Exercice comparatif à faire absolument :** extraire les 6 mêmes champs de 5 factures de fournisseurs différents, une fois avec `FORMS`, une fois avec `QUERIES`. Comparer taux de réussite **et** coût. C'est la décision d'architecture la plus fréquente sur un projet Textract.

---

## Module 6 — Asynchrone : multipage, S3, SNS/SQS, pagination (2 h 30)

**Objectifs :** traiter un PDF de 50 pages sans bloquer un thread, et sans perdre de job.

Le flux canonique :

1. Document dans S3.
2. `StartDocumentTextDetection` / `StartDocumentAnalysis` / `StartExpenseAnalysis` / `StartLendingAnalysis` → renvoie un `JobId`.
3. Textract publie l'état final (`SUCCEEDED` / `FAILED`) sur un **topic SNS** (`NotificationChannel` = ARN du topic + ARN d'un rôle IAM que Textract assume pour publier).
4. SNS → SQS ou Lambda déclenche la suite.
5. `GetXxx(JobId)` et boucle sur `NextToken` jusqu'à épuisement.

```python
job = client.start_document_text_detection(
    DocumentLocation={"S3Object": {"Bucket": BUCKET, "Name": "dossier.pdf"}},
    ClientRequestToken="dossier.pdf#v1",          # idempotence, durée de vie 7 jours
    JobTag="formation",
    NotificationChannel={"SNSTopicArn": TOPIC_ARN, "RoleArn": ROLE_ARN},
)

blocks, token = [], None
while True:
    kwargs = {"JobId": job["JobId"], "MaxResults": 1000}
    if token:
        kwargs["NextToken"] = token
    page = client.get_document_text_detection(**kwargs)
    blocks += page["Blocks"]
    token = page.get("NextToken")
    if not token:
        break
```

Points à intégrer :

- **Idempotence :** même `ClientRequestToken` + mêmes paramètres = même `JobId`, job non rejoué, **et pas de nouvelle notification SNS**. Paramètres légèrement différents = `IdempotentParameterMismatchException`.
- **Rétention :** les résultats vivent **7 jours** chiffrés côté Textract. Pour les garder, passer `OutputConfig` (bucket + préfixe, `KMSKeyId` optionnel) — le rôle doit avoir `s3:PutObject` et, avec KMS, `Decrypt`/`ReEncrypt`/`GenerateDataKey`/`DescribeKey`.
- **Ne pas poller `GetXxx` en boucle** pour connaître l'état : AWS throttle. L'état vient de SNS.
- `LimitExceededException` au `Start` = trop de jobs simultanés sur le compte ; tamponner avec SQS.
- Le topic SNS doit être **dans la même région** que l'endpoint Textract.
- Sécurité : ajouter une condition `aws:SourceArn`/`aws:SourceAccount` dans la politique de confiance du rôle (prévention du *confused deputy*).

**Lab :** pipeline complet `upload S3 → Lambda (Start) → SNS → SQS → worker (Get + stockage JSON)`. Étape pédagogique intermédiaire autorisée : polling manuel toutes les 5 s pour un seul document, puis migration vers SNS pour comprendre le gain.

**Exercice :** provoquer un `FAILED` (PDF corrompu) et vérifier que la notification SNS le signale bien, avec `StatusMessage`.

---

## Module 7 — Les API métier : Expense, ID, Lending (2 h)

**`AnalyzeExpense`** (factures, reçus) : renvoie des `ExpenseDocuments`, chacun avec `SummaryFields` (en-tête) et `LineItemGroups` → `LineItems` → `LineItemExpenseFields`. Chaque champ porte trois choses : le libellé **détecté** sur le document (`LabelDetection`), la **valeur** (`ValueDetection`), et un **type normalisé** (`Type`, ex. `VENDOR_NAME`, `TOTAL`, `INVOICE_RECEIPT_DATE`, `ITEM`, `QUANTITY`, `PRICE`). C'est ce type normalisé qui vous évite d'écrire un mapping par fournisseur. Existe en synchrone et en asynchrone (`StartExpenseAnalysis`). 0,01 $/page.

**`AnalyzeID`** (pièces d'identité) : jusqu'à **2 images par appel** (recto/verso). Renvoie `IdentityDocumentFields` avec des types normalisés (`FIRST_NAME`, `LAST_NAME`, `DATE_OF_BIRTH`, `EXPIRATION_DATE`, `DOCUMENT_NUMBER`, `ADDRESS`, `MRZ_CODE`…) et, quand c'est pertinent, une valeur normalisée en plus de la valeur brute (dates au format ISO). Deux limites structurelles : la couverture est centrée sur les **documents d'identité américains** (permis de conduire, passeports) — à valider pour tout autre pays ; et **l'analyse d'identité ne renvoie pas de `Geometry`**, donc pas de surlignage de champ possible. Synchrone uniquement. 0,025 $/page.

**`AnalyzeLending`** : chaîne spécialisée crédit immobilier US — classifie chaque page, découpe le dossier, route vers la bonne extraction, et résume (`GetLendingAnalysisSummary`). Les pages de types non supportés ne sont pas facturées. À connaître, rarement à utiliser hors contexte hypothécaire américain.

**Exercices :** 1) transformer une facture en `{fournisseur, date, total_ht, tva, total_ttc, lignes[]}` en n'utilisant que les types normalisés ; 2) refaire la même extraction avec `QUERIES` et comparer ; 3) sur une pièce d'identité factice, vérifier ce qui se passe avec un verso flou.

---

## Module 8 — Qualité : confiance, seuils, validation humaine (1 h 30)

**Objectifs :** arrêter de dire « ça marche » et commencer à mesurer.

Qualité d'entrée, là où se gagne 80 % de la précision :
- viser ~**300 DPI**, document à plat, cadré, éclairage uniforme, pas d'ombre portée ;
- **ne pas** recompresser, redimensionner ou binariser avant l'envoi ;
- préférer le PDF natif au scan du PDF imprimé ;
- redresser les documents très inclinés en amont si nécessaire.

Exploiter la confiance :
- `Confidence` existe au niveau `WORD`, `LINE`, cellule, valeur de champ, réponse de query. Utiliser la **plus fine disponible** pour le champ concerné ;
- fixer un **seuil par champ, pas global** : un montant total exige plus de certitude qu'un libellé d'article ;
- doubler la confiance de **règles métier** déterministes : regex de format, cohérence de dates, clé de contrôle (IBAN, checksum MRZ), somme des lignes = total. Une extraction validée par arithmétique bat un score de 99 %.

**Revue humaine :** `AnalyzeDocument` accepte un `HumanLoopConfig` (Amazon A2I) pour router automatiquement les pages douteuses vers une file de relecture. Surveiller `HumanLoopQuotaExceededException`.

**Lab — harnais d'évaluation :** annoter à la main la vérité terrain de 20 documents dans un CSV, puis produire automatiquement : taux d'extraction par champ (trouvé / attendu), taux d'exactitude (valeur correcte / trouvée), et distribution des confiances des erreurs. Objectif pédagogique : découvrir qu'il existe des erreurs à confiance élevée, donc qu'un seuil seul ne suffit jamais.

---

## Module 9 — Robustesse et exploitation (2 h)

**Erreurs à gérer nommément :**

| Exception | Cause | Réaction |
|---|---|---|
| `ThrottlingException`, `ProvisionedThroughputExceededException` | TPS dépassé | retry exponentiel + jitter, lissage par file |
| `LimitExceededException` | trop de jobs async simultanés | tampon SQS, backpressure |
| `DocumentTooLargeException` | > 10 Mo sync / > 500 Mo async | découper, passer en async |
| `UnsupportedDocumentException` | format non géré, PDF XFA, PDF multipage en sync | convertir ou basculer en async |
| `BadDocumentException`, `InvalidS3ObjectException` | fichier illisible, clé/région S3 fausse | échec définitif, ne pas retenter |
| `IdempotentParameterMismatchException` | `ClientRequestToken` réutilisé avec d'autres paramètres | versionner le token |
| `InvalidJobIdException` | job > 7 jours ou inexistant | relancer le traitement |

**Quotas :** les TPS par opération sont des **quotas par défaut, ajustables**, et varient selon la région et le compte. Les opérations d'analyse riche sont nettement moins permissives que `DetectDocumentText`. Ne codez jamais une valeur en dur : lisez la console Service Quotas et demandez une augmentation avant la mise en production.

**À mettre dans tout client Textract sérieux :**
- mode retry SDK `adaptive` (boto3 : `Config(retries={"max_attempts": 5, "mode": "adaptive"})`) ;
- clé d'idempotence dérivée du **hash du document** + version de traitement, pour ne jamais payer deux fois la même page ;
- cache/persistance du JSON brut : c'est votre seule chance de re-post-traiter sans repayer ;
- file de rejeu (DLQ) pour les échecs transitoires ;
- métriques : pages traitées, coût estimé, latence p95, taux d'échec, confiance moyenne par type de document.

**Sécurité et conformité :**
- IAM au plus juste, par action Textract et par préfixe S3 ;
- chiffrement au repos (SSE-KMS sur les buckets d'entrée et de sortie) ;
- **VPC endpoint** d'interface pour Textract si le trafic ne doit pas sortir sur Internet ;
- ne jamais logger le JSON brut d'un document contenant des données personnelles — il contient tout le texte ;
- politique d'**opt-out des services d'IA** au niveau AWS Organizations si vous ne voulez pas que vos contenus soient utilisés pour l'amélioration du service ;
- définir la rétention : résultats async supprimés à 7 jours côté AWS, mais vos copies S3 ont besoin d'une lifecycle rule.

**Lab :** écrire un petit client `TextractClient` maison avec retry, idempotence, mesure du coût par appel et journalisation sans PII.

---

## Module 10 — Architecture de référence et coûts (1 h 30)

**Pipeline IDP typique :**

```
Ingestion (upload/S3/e-mail)
   → S3 brut (+ KMS, lifecycle)
   → SQS (lissage, retry, DLQ)
   → Worker : classification simple → API Textract adaptée
   → Post-traitement : normalisation, règles métier, seuils de confiance
   → Store structuré (base) + JSON brut archivé
   → File de revue humaine pour les cas douteux
   → Métriques coût / qualité
```

Deux décisions qui coûtent cher si elles sont prises trop tard : (1) **quelles features demander par type de document** — chaque feature ajoutée est facturée, (2) **où mettre la frontière automatique / humain**.

**Prix par page, région US West (Oregon), premier palier puis au-delà de 1 M pages/mois :**

| Appel | 1ᵉʳ million | Au-delà |
|---|---|---|
| DetectDocumentText | 0,0015 $ | 0,0006 $ |
| AnalyzeDocument — Signatures | 0,0035 $ | 0,0014 $ |
| AnalyzeDocument — Tables | 0,015 $ | 0,010 $ |
| AnalyzeDocument — Queries | 0,015 $ | — |
| AnalyzeDocument — Tables + Queries | 0,020 $ | 0,015 $ |
| AnalyzeDocument — Forms | 0,050 $ | 0,040 $ |
| AnalyzeDocument — Forms + Tables + Queries | 0,070 $ | 0,055 $ |
| AnalyzeDocument — Layout | inclus, gratuit avec Tables | — |
| AnalyzeDocument — Custom Queries | 0,025 $ | 0,015 $ |
| AnalyzeExpense | 0,010 $ | 0,008 $ |
| AnalyzeID | 0,025 $ (jusqu'à 100 k) | 0,010 $ |
| AnalyzeLending | 0,070 $ | 0,055 $ |

Deux ordres de grandeur à mémoriser : l'OCR brut coûte ~**33× moins** que Forms ; Tables coûte ~**3,3× moins** que Forms. Beaucoup de projets paient Forms sur toutes les pages alors que Queries sur 3 champs suffisait.

**Exercice de chiffrage :** calculer le coût mensuel de (a) 20 000 pages OCR indexées, (b) 5 000 factures via AnalyzeExpense, (c) 5 000 pages Forms+Tables — puis proposer une variante deux fois moins chère à qualité constante.

---

## Module 11 — Projet final (4 h)

**Énoncé :** construire un extracteur de factures fournisseurs de bout en bout, autonome.

Exigences :
1. dépôt d'un PDF (1 à 30 pages) dans S3 ;
2. traitement **asynchrone** avec notification SNS, aucun polling d'état ;
3. extraction : fournisseur, date, numéro, devise, total HT / TVA / TTC, lignes d'articles ;
4. deux stratégies implémentées et comparées (`AnalyzeExpense` vs `QUERIES`) avec un rapport chiffré coût/qualité ;
5. règles de validation : somme des lignes = total, date plausible, devise reconnue ;
6. tout champ sous seuil ou invalidé part dans une file « à revoir », avec l'image recadrée sur la `BoundingBox` du champ ;
7. idempotence : redéposer deux fois le même fichier ne relance pas d'appel payant ;
8. sortie : JSON normalisé + CSV, plus un log de métriques (pages, coût estimé, latence, taux de revue).

**Grille d'évaluation :** exactitude sur 10 factures inédites (30 %), robustesse aux erreurs et aux doublons (25 %), maîtrise des coûts et justification des features choisies (20 %), qualité du code et des tests (15 %), clarté du rapport comparatif (10 %).

---

## Annexe A — Aide-mémoire CLI

```bash
# OCR simple
aws textract detect-document-text --document '{"S3Object":{"Bucket":"B","Name":"K"}}'

# Formulaires + tableaux + mise en page (Layout gratuit avec Tables)
aws textract analyze-document --document '{"S3Object":{"Bucket":"B","Name":"K"}}' \
  --feature-types '["FORMS","TABLES","LAYOUT"]'

# Queries
aws textract analyze-document --document '{"S3Object":{"Bucket":"B","Name":"K"}}' \
  --feature-types '["QUERIES"]' \
  --queries-config '{"Queries":[{"Text":"What is the total?","Alias":"TOTAL"}]}'

# Facture / reçu
aws textract analyze-expense --document '{"S3Object":{"Bucket":"B","Name":"K"}}'

# Multipage asynchrone
aws textract start-document-analysis \
  --document-location '{"S3Object":{"Bucket":"B","Name":"dossier.pdf"}}' \
  --feature-types '["TABLES"]'
aws textract get-document-analysis --job-id JOBID --max-results 1000
```

## Annexe B — Limites à retenir par cœur

| Limite | Valeur |
|---|---|
| Formats | JPEG, PNG, PDF, TIFF — **pas de PDF XFA** |
| Synchrone | 10 Mo ; PDF/TIFF **1 page** |
| Asynchrone | JPEG/PNG 10 Mo ; PDF/TIFF **500 Mo et 3 000 pages** |
| Queries | 15/page sync, 30/page async ; question ≤ 200 car. ; réponse ≤ 128 car. |
| Rétention résultats async | 7 jours |
| `ClientRequestToken` | valide 7 jours |
| AnalyzeID | 2 images max par appel, pas de `Geometry` |
| Adapters (Custom Queries) | 10 adaptateurs, 10 versions/mois, 3 entraînements simultanés |
| TPS | quotas par défaut ajustables, variables par région — à lire dans Service Quotas |

## Annexe C — Écosystème et outils utiles

| Outil | Usage |
|---|---|
| Console → **Bulk Document Uploader** | tester Textract sur un lot sans écrire une ligne de code |
| `amazon-textract-textractor` (Python) | objets de haut niveau, export Markdown/CSV, visualisation |
| `amazon-textract-response-parser` (`trp2`) | parcours des `Block` sans réécrire l'indexation |
| `amazon-textract-helper` (CLI `amazon-textract`) | appels et rendu lisible en ligne de commande |
| `amazon-textract-prettyprinter` | sortie texte/CSV/Markdown propre |
| Textract Service Quota Calculator | dimensionner les TPS avant la prod |

À aborder seulement après le module 10 : Amazon Bedrock Data Automation et les modèles multimodaux, qui couvrent une partie des mêmes besoins avec un compromis coût/déterminisme différent.

## Annexe D — Ressources officielles

- [Developer Guide](https://docs.aws.amazon.com/textract/latest/dg/what-is.html)
- [API Reference](https://docs.aws.amazon.com/textract/latest/APIReference/Welcome.html)
- [Set Quotas](https://docs.aws.amazon.com/textract/latest/dg/limits-document.html) et [Quotas](https://docs.aws.amazon.com/textract/latest/dg/limits.html)
- [Objets de réponse `Block`](https://docs.aws.amazon.com/textract/latest/dg/how-it-works-document-layout.html)
- [Opérations asynchrones](https://docs.aws.amazon.com/textract/latest/dg/api-async.html)
- [Tarification](https://aws.amazon.com/textract/pricing/) · [FAQ](https://aws.amazon.com/textract/faqs/)
- [Endpoints et quotas par région](https://docs.aws.amazon.com/general/latest/gr/textract.html)

## Annexe E — Checklist de sortie de formation

Je sais, sans documentation sous les yeux :

- [ ] choisir entre les 5 API et justifier le surcoût de chaque feature ;
- [ ] dire pourquoi un PDF de 3 pages ne passe pas en synchrone ;
- [ ] reconstruire clés/valeurs et tableaux depuis une liste de `Block` ;
- [ ] convertir un `BoundingBox` normalisé en pixels ;
- [ ] monter un flux async S3 → SNS → SQS → `Get` avec pagination ;
- [ ] rendre un traitement idempotent et ne jamais payer deux fois une page ;
- [ ] citer 5 exceptions Textract et la bonne réaction pour chacune ;
- [ ] estimer le coût mensuel d'un volume donné et proposer une variante moins chère ;
- [ ] expliquer pourquoi un seuil de confiance ne remplace pas une règle métier.
